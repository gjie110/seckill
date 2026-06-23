package com.seckill.service;

import com.alibaba.fastjson.JSON;
import com.seckill.common.RedisKey;
import com.seckill.dto.SeckillMessage;
import com.seckill.entity.SeckillOrder;
import com.seckill.mapper.OrderMapper;
import com.seckill.mapper.TicketTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@RocketMQMessageListener(consumerGroup = "seckill-consumer-group", topic = "seckill-order-topic")
public class SeckillConsumer implements RocketMQListener<String> {

    private final OrderMapper orderMapper;
    private final TicketTypeMapper ticketTypeMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final WaitlistService waitlistService;

    /** 消费 MQ 消息：库存扣减、订单入库，通过 doneKey 保证幂等。 */
    @Override
    public void onMessage(String message) {
        SeckillMessage msg = JSON.parseObject(message, SeckillMessage.class);
        String orderNo = msg.getOrderNo();
        String doneKey = RedisKey.doneKey(orderNo);
        Boolean done = stringRedisTemplate.hasKey(doneKey);
        if (Boolean.TRUE.equals(done)) {
            log.warn("订单已处理过，跳过：orderNo={}", orderNo);
            return;
        }
        try {
            int rows = ticketTypeMapper.decreaseStock(msg.getTicketTypeId(), msg.getQuantity());
            if (rows == 0) {
                log.warn("数据库扣减库存失败，回滚Redis：orderNo={}", orderNo);
                rollbackStockInRedis(msg.getUserId(), msg.getTicketTypeId(), msg.getQuantity());
                return;
            }
            SeckillOrder order = new SeckillOrder();
            order.setOrderNo(orderNo);
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
            log.info("订单创建成功：orderNo={}, userId={}", orderNo, msg.getUserId());
        } catch (Exception e) {
            log.error("处理订单消息异常：orderNo={}", orderNo, e);
            throw e;
        }
    }

    /** MQ处理失败时回滚 Redis 预扣库存和用户计数器。 */
    private void rollbackStockInRedis(Long userId, Long ticketTypeId, Integer quantity) {
        String stockKey = RedisKey.stockKey(ticketTypeId);
        String userBoughtKey = RedisKey.userBoughtKey(userId, ticketTypeId);
        stringRedisTemplate.opsForValue().increment(stockKey, quantity);
        stringRedisTemplate.delete(userBoughtKey);
    }
}
