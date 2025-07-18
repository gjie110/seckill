package com.seckill.service;

import com.seckill.common.OrderNoGenerator;
import com.seckill.common.RedisKey;
import com.seckill.common.ResultCode;
import com.seckill.common.SeckillException;
import com.seckill.config.SeckillProperties;
import com.seckill.dto.SeckillMessage;
import com.seckill.dto.SeckillRequest;
import com.seckill.entity.SeckillOrder;
import com.seckill.entity.SeckillProduct;
import com.seckill.entity.SeckillConfig;
import com.seckill.entity.TicketType;
import com.seckill.entity.Waitlist;
import com.seckill.mapper.OrderMapper;
import com.seckill.mapper.TicketTypeMapper;
import com.seckill.mapper.WaitlistMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ProductService productService;
    private final SeckillProducer seckillProducer;
    private final SeckillProperties seckillProperties;
    private final OrderMapper orderMapper;
    private final TicketTypeMapper ticketTypeMapper;
    private final WaitlistMapper waitlistMapper;
    private final MessageService messageService;

    public String executeOrder(SeckillRequest request) {
        Long userId = request.getUserId();
        Long productId = request.getProductId();
        Long ticketTypeId = request.getTicketTypeId();
        Integer quantity = request.getQuantity();
        if (quantity == null || quantity < 1) quantity = 1;
        if (quantity > 3) {
            throw new SeckillException(ResultCode.PURCHASE_LIMIT);
        }

        checkRateLimit(userId);

        SeckillProduct product = productService.getProduct(productId);
        TicketType ticketType = productService.getTicketType(ticketTypeId);

        productService.checkActivityTime(ticketType);

        int maxPerUser = 1000000;
        if (!"regular".equals(ticketType.getChannel())) {
            SeckillConfig cfg = productService.getSeckillConfig(ticketType.getId());
            if (cfg != null && cfg.getMaxPerUser() != null && cfg.getMaxPerUser() > 0) {
                maxPerUser = cfg.getMaxPerUser();
            }
            checkPurchaseLimit(userId, productId, ticketType, maxPerUser);
        }

        String orderNo = null;
        int result = doStockDeduction(userId, ticketTypeId, quantity, maxPerUser);

        if (result == 1) {
            orderNo = OrderNoGenerator.generate("T");
            sendOrderMessage(orderNo, userId, productId, ticketTypeId, quantity, ticketType.getPrice(), ticketType.getChannel());
            return orderNo;
        } else if (result == 0) {
            joinWaitlist(userId, productId, ticketTypeId, quantity);
            throw new SeckillException(ResultCode.JOINED_WAITLIST);
        } else if (result == -1) {
            throw new SeckillException(ResultCode.ALREADY_PURCHASED);
        } else {
            throw new SeckillException(ResultCode.STOCK_NOT_ENOUGH);
        }
    }

    private void checkRateLimit(Long userId) {
        String limitKey = RedisKey.limitKey(userId, System.currentTimeMillis() / 60000);
        // Lua 脚本确保 INCR + EXPIRE 原子执行，避免 key 无过期时间的竞态
        String script =
                "local c = redis.call('incr', KEYS[1]) " +
                "if c == 1 then redis.call('expire', KEYS[1], ARGV[2]) end " +
                "if c > tonumber(ARGV[1]) then return 0 end " +
                "return 1";
        Long result = stringRedisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(script, Long.class),
                java.util.Collections.singletonList(limitKey),
                String.valueOf(seckillProperties.getRateLimit().getRequestsPerMinute()),
                String.valueOf(RedisKey.LIMIT_KEY_TTL)
        );
        if (result == null || result == 0) {
            throw new SeckillException(ResultCode.RATE_LIMIT);
        }
    }

    private void checkPurchaseLimit(Long userId, Long productId, TicketType ticketType, int maxPerUser) {
        // 直接从 Redis 的 user:bought:{userId}:{ticketTypeId} 读取累计购买数
        // 该计数器在库存扣减（doStockDeduction）时被 Lua 脚本递增；
        // 退票/取消订单时由 STOCK_INCR_SCRIPT 递减；避免了每次下单都查 MySQL 的 COUNT。
        String boughtKey = RedisKey.userBoughtKey(userId, ticketType.getId());
        String value = stringRedisTemplate.opsForValue().get(boughtKey);
        int alreadyBought = (value == null) ? 0 : Integer.parseInt(value);
        if (alreadyBought >= maxPerUser) {
            throw new SeckillException(ResultCode.ALREADY_PURCHASED);
        }
    }

    private int doStockDeduction(Long userId, Long ticketTypeId, Integer quantity, int maxPerUser) {
        String stockKey = RedisKey.stockKey(ticketTypeId);
        String userBoughtKey = RedisKey.userBoughtKey(userId, ticketTypeId);

        try {
            Long result = stringRedisTemplate.execute(
                    new DefaultRedisScript<>(RedisKey.STOCK_DECR_SCRIPT, Long.class),
                    Arrays.asList(stockKey, userBoughtKey),
                    String.valueOf(userId),
                    String.valueOf(quantity),
                    String.valueOf(maxPerUser)
            );
            return result == null ? -2 : result.intValue();
        } catch (RedisConnectionFailureException e) {
            log.error("Redis 连接失败，服务降级", e);
            throw new SeckillException(ResultCode.SERVICE_BUSY);
        }
    }

    private void sendOrderMessage(String orderNo, Long userId, Long productId, Long ticketTypeId,
                                   Integer quantity, BigDecimal unitPrice, String channel) {
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));
        SeckillMessage message = SeckillMessage.builder()
                .orderNo(orderNo)
                .userId(userId)
                .productId(productId)
                .ticketTypeId(ticketTypeId)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .totalAmount(total)
                .channel(channel)
                .timestamp(System.currentTimeMillis())
                .build();
        try {
            seckillProducer.sendOrderMessage(message);
        } catch (Exception e) {
            log.warn("MQ 消息发送失败，降级为同步处理 orderNo={}，error={}", orderNo, e.getMessage());
            // MQ 不可用时，同步直接保存订单（保持幂等）
            saveOrderDirectly(message);
        }
    }

    /**
     * MQ 不可用时的同步降级：直接创建订单到数据库
     * 使用 Redis doneKey 保证幂等（与 Consumer 共用同一幂等 key）
     */
    private void saveOrderDirectly(SeckillMessage msg) {
        String doneKey = RedisKey.doneKey(msg.getOrderNo());
        Boolean alreadyDone = stringRedisTemplate.hasKey(doneKey);
        if (Boolean.TRUE.equals(alreadyDone)) {
            log.info("订单已处理过（幂等跳过）：orderNo={}", msg.getOrderNo());
            return;
        }

        int rows = ticketTypeMapper.decreaseStock(msg.getTicketTypeId(), msg.getQuantity());
        if (rows == 0) {
            log.warn("同步降级：数据库扣减库存失败，orderNo={}", msg.getOrderNo());
            rollbackStock(msg.getUserId(), msg.getTicketTypeId(), msg.getQuantity());
            throw new SeckillException(ResultCode.STOCK_NOT_ENOUGH);
        }

        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(msg.getOrderNo());
        order.setUserId(msg.getUserId());
        order.setProductId(msg.getProductId());
        order.setTicketTypeId(msg.getTicketTypeId());
        order.setQuantity(msg.getQuantity());
        order.setUnitPrice(msg.getUnitPrice());
        order.setTotalAmount(msg.getTotalAmount());
        order.setChannel(msg.getChannel());
        order.setStatus(0);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, 5);
        order.setTimeoutTime(cal.getTime());
        order.setCreateTime(new Date());
        order.setUpdateTime(new Date());

        orderMapper.insert(order);
        stringRedisTemplate.opsForValue().set(doneKey, "1", RedisKey.DONE_KEY_TTL, TimeUnit.SECONDS);
        log.info("同步降级订单创建成功：orderNo={}", msg.getOrderNo());
    }

    public void rollbackStock(Long userId, Long ticketTypeId, Integer quantity) {
        String stockKey = RedisKey.stockKey(ticketTypeId);
        String userBoughtKey = RedisKey.userBoughtKey(userId, ticketTypeId);
        try {
            stringRedisTemplate.execute(
                    new DefaultRedisScript<>(RedisKey.STOCK_INCR_SCRIPT, Long.class),
                    Arrays.asList(stockKey, userBoughtKey),
                    String.valueOf(quantity)
            );
        } catch (Exception e) {
            log.warn("rollbackStock: STOCK_INCR_SCRIPT 异常，降级处理", e);
            stringRedisTemplate.opsForValue().increment(stockKey, quantity);
        }
    }

    private void joinWaitlist(Long userId, Long productId, Long ticketTypeId, Integer quantity) {
        java.util.List<Waitlist> existingList = waitlistMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Waitlist>()
                        .eq(Waitlist::getUserId, userId)
                        .eq(Waitlist::getTicketTypeId, ticketTypeId)
                        .in(Waitlist::getStatus, 0, 1)
                        .last("LIMIT 1")
        );
        if (existingList != null && !existingList.isEmpty()) {
            return;
        }

        Waitlist waitlist = new Waitlist();
        waitlist.setUserId(userId);
        waitlist.setProductId(productId);
        waitlist.setTicketTypeId(ticketTypeId);
        waitlist.setQuantity(quantity);
        waitlist.setStatus(0);
        waitlist.setCreateTime(new Date());
        waitlistMapper.insert(waitlist);

        try {
            messageService.sendWaitlistJoinedMsg(userId, ticketTypeId, quantity);
        } catch (Exception e) {
            log.warn("发送候补确认消息失败，不影响主流程", e);
        }
    }

    public Integer getRedisStock(Long ticketTypeId) {
        String stock = stringRedisTemplate.opsForValue().get(RedisKey.stockKey(ticketTypeId));
        return stock == null ? 0 : Integer.parseInt(stock);
    }
}