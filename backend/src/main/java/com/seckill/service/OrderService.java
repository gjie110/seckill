package com.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seckill.common.RedisKey;
import com.seckill.common.ResultCode;
import com.seckill.common.SeckillException;
import com.seckill.dto.OrderDetailDTO;
import com.seckill.entity.SeckillOrder;
import com.seckill.entity.SeckillProduct;
import com.seckill.entity.SeckillUser;
import com.seckill.entity.TicketType;
import com.seckill.entity.UserTicket;
import com.seckill.mapper.OrderMapper;
import com.seckill.mapper.ProductMapper;
import com.seckill.mapper.TicketTypeMapper;
import com.seckill.mapper.UserMapper;
import com.seckill.mapper.UserTicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final UserTicketMapper userTicketMapper;
    private final TicketTypeMapper ticketTypeMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserTicketService userTicketService;
    private final WaitlistService waitlistService;
    private final MessageService messageService;

    public SeckillOrder getOrder(String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getOrderNo, orderNo));
    }

    public List<SeckillOrder> listOrders(Long userId) {
        return orderMapper.selectList(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getUserId, userId)
                .orderByDesc(SeckillOrder::getCreateTime));
    }

    public OrderDetailDTO getOrderDetail(String orderNo) {
        SeckillOrder order = getOrder(orderNo);
        if (order == null) {
            return null;
        }
        OrderDetailDTO dto = new OrderDetailDTO();
        dto.setOrderId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setProductId(order.getProductId());
        dto.setTicketTypeId(order.getTicketTypeId());
        dto.setQuantity(order.getQuantity());
        dto.setUnitPrice(order.getUnitPrice());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setChannel(order.getChannel());
        dto.setStatus(order.getStatus());
        dto.setPayTime(order.getPayTime());
        dto.setTimeoutTime(order.getTimeoutTime());
        dto.setCreateTime(order.getCreateTime());
        dto.setUpdateTime(order.getUpdateTime());

        SeckillUser user = userMapper.selectById(order.getUserId());
        if (user != null) {
            dto.setUsername(user.getUsername());
            dto.setPhone(user.getPhone());
        }

        SeckillProduct product = productMapper.selectById(order.getProductId());
        if (product != null) {
            dto.setProductName(product.getName());
            dto.setVenue(product.getVenue());
            dto.setShowTime(product.getShowTime());
        }

        TicketType ticket = ticketTypeMapper.selectById(order.getTicketTypeId());
        if (ticket != null) {
            dto.setTicketTypeName(ticket.getTypeName());
            dto.setSeatArea(ticket.getSeatArea());
        }

        return dto;
    }

    @Transactional
    public List<UserTicket> payOrder(String orderNo, Long userId) {
        SeckillOrder order = getOrder(orderNo);
        if (order == null) {
            throw new SeckillException(ResultCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new SeckillException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() == 1) {
            throw new SeckillException(ResultCode.ORDER_PAID);
        }
        if (order.getStatus() == 2) {
            throw new SeckillException(ResultCode.ORDER_CANCELLED);
        }
        if (order.getStatus() == 3 || new Date().after(order.getTimeoutTime())) {
            throw new SeckillException(ResultCode.ORDER_TIMEOUT);
        }

        order.setStatus(1);
        order.setPayTime(new Date());
        order.setUpdateTime(new Date());
        orderMapper.updateById(order);

        TicketType ticketType = ticketTypeMapper.selectById(order.getTicketTypeId());
        List<UserTicket> tickets = userTicketService.generateTickets(order, ticketType);

        try {
            messageService.sendOrderPaidMsg(userId, orderNo, order.getTicketTypeId(), tickets.size());
        } catch (Exception e) {
            log.warn("发送支付成功消息失败，不影响主流程", e);
        }

        log.info("订单支付成功：orderNo={}, 生成票{}张", orderNo, tickets.size());
        return tickets;
    }

    private void rollbackStock(Long userId, Long ticketTypeId, Integer quantity) {
        String stockKey = RedisKey.stockKey(ticketTypeId);
        String userBoughtKey = RedisKey.userBoughtKey(userId, ticketTypeId);
        try {
            stringRedisTemplate.execute(
                    new DefaultRedisScript<>(RedisKey.STOCK_INCR_SCRIPT, Long.class),
                    Arrays.asList(stockKey, userBoughtKey),
                    String.valueOf(quantity)
            );
        } catch (Exception e) {
            log.warn("STOCK_INCR_SCRIPT 执行异常，降级处理", e);
            stringRedisTemplate.opsForValue().increment(stockKey, quantity);
        }
    }

    @Transactional
    public void cancelOrder(String orderNo, Long userId) {
        SeckillOrder order = getOrder(orderNo);
        if (order == null) {
            throw new SeckillException(ResultCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new SeckillException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != 0) {
            return;
        }

        order.setStatus(2);
        order.setUpdateTime(new Date());
        orderMapper.updateById(order);

        ticketTypeMapper.increaseStock(order.getTicketTypeId(), order.getQuantity());

        rollbackStock(userId, order.getTicketTypeId(), order.getQuantity());

        waitlistService.notifyWaitlist(order.getTicketTypeId(), order.getQuantity());

        try {
            messageService.sendOrderCancelledMsg(userId, orderNo, order.getTicketTypeId());
        } catch (Exception e) {
            log.warn("发送订单取消消息失败，不影响主流程", e);
        }

        log.info("订单取消成功：orderNo={}, 释放库存{}", orderNo, order.getQuantity());
    }

    @Scheduled(cron = "0 */1 * * * ?")
    @Transactional
    public void checkTimeoutOrders() {
        List<SeckillOrder> timeoutOrders = orderMapper.selectList(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getStatus, 0)
                        .lt(SeckillOrder::getTimeoutTime, new Date())
        );

        for (SeckillOrder order : timeoutOrders) {
            try {
                order.setStatus(3);
                order.setUpdateTime(new Date());
                orderMapper.updateById(order);

                ticketTypeMapper.increaseStock(order.getTicketTypeId(), order.getQuantity());
                rollbackStock(order.getUserId(), order.getTicketTypeId(), order.getQuantity());

                waitlistService.notifyWaitlist(order.getTicketTypeId(), order.getQuantity());

                try {
                    messageService.sendOrderTimeoutMsg(order.getUserId(), order.getOrderNo(), order.getTicketTypeId());
                } catch (Exception e) {
                    log.warn("发送订单超时消息失败，不影响主流程", e);
                }

                log.info("超时订单自动取消：orderNo={}", order.getOrderNo());
            } catch (Exception e) {
                log.error("处理超时订单失败：orderNo={}", order.getOrderNo(), e);
            }
        }
    }

    public List<SeckillOrder> listAllOrders() {
        return orderMapper.selectList(new LambdaQueryWrapper<SeckillOrder>()
                .orderByDesc(SeckillOrder::getCreateTime));
    }

    public List<SeckillOrder> searchOrders(String keyword, Integer status) {
        LambdaQueryWrapper<SeckillOrder> wrapper = new LambdaQueryWrapper<SeckillOrder>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(SeckillOrder::getOrderNo, keyword.trim())
                    .or().like(SeckillOrder::getUserId, keyword.trim()));
        }
        if (status != null) {
            wrapper.eq(SeckillOrder::getStatus, status);
        }
        wrapper.orderByDesc(SeckillOrder::getCreateTime);
        return orderMapper.selectList(wrapper);
    }

    public Page<SeckillOrder> searchOrdersPage(String keyword, Integer status, Integer page, Integer size) {
        LambdaQueryWrapper<SeckillOrder> wrapper = new LambdaQueryWrapper<SeckillOrder>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(SeckillOrder::getOrderNo, keyword.trim())
                    .or().like(SeckillOrder::getUserId, keyword.trim()));
        }
        if (status != null) {
            wrapper.eq(SeckillOrder::getStatus, status);
        }
        wrapper.orderByDesc(SeckillOrder::getCreateTime);
        Page<SeckillOrder> pageRequest = new Page<>(page, size);
        return orderMapper.selectPage(pageRequest, wrapper);
    }

    @Transactional
    public void adminForceCancelOrder(String orderNo, String reason) {
        SeckillOrder order = getOrder(orderNo);
        if (order == null) {
            throw new SeckillException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() == 2 || order.getStatus() == 3) {
            return;
        }

        Integer oldStatus = order.getStatus();
        order.setStatus(2);
        order.setUpdateTime(new Date());
        orderMapper.updateById(order);

        if (oldStatus == 1) {
            ticketTypeMapper.increaseStock(order.getTicketTypeId(), order.getQuantity());
            rollbackStock(order.getUserId(), order.getTicketTypeId(), order.getQuantity());
        } else {
            // 未支付被取消，同时释放库存与计数器
            rollbackStock(order.getUserId(), order.getTicketTypeId(), order.getQuantity());
        }

        waitlistService.notifyWaitlist(order.getTicketTypeId(), order.getQuantity());

        try {
            messageService.sendOrderCancelledMsg(order.getUserId(), orderNo, order.getTicketTypeId());
        } catch (Exception e) {
            log.warn("发送强制取消消息失败，不影响主流程", e);
        }

        log.info("[管理员] 强制取消订单：orderNo={}, 原状态={}, 原因={}", orderNo, oldStatus, reason);
    }
}