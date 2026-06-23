package com.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.common.OrderNoGenerator;
import com.seckill.common.RedisKey;
import com.seckill.common.ResultCode;
import com.seckill.common.SeckillException;
import com.seckill.entity.SeckillOrder;
import com.seckill.entity.SeckillProduct;
import com.seckill.entity.TicketType;
import com.seckill.entity.UserTicket;
import com.seckill.mapper.ProductMapper;
import com.seckill.mapper.TicketTypeMapper;
import com.seckill.mapper.UserTicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserTicketService {

    private final UserTicketMapper userTicketMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final WaitlistService waitlistService;
    private final MessageService messageService;
    private final TicketTypeMapper ticketTypeMapper;
    private final ProductMapper productMapper;

    /** 支付成功后生成用户票：Redis 原子分配座位号，逐张写入 DB。 */
    @Transactional
    public List<UserTicket> generateTickets(SeckillOrder order, TicketType ticketType) {
        List<UserTicket> tickets = new ArrayList<>();
        String seatCounterKey = "seat:" + order.getTicketTypeId();
        Long currentSeat = stringRedisTemplate.opsForValue().increment(seatCounterKey);
        for (int i = 0; i < order.getQuantity(); i++) {
            long seatNo = currentSeat - i;
            UserTicket ticket = new UserTicket();
            ticket.setTicketNo(OrderNoGenerator.generate("TK"));
            ticket.setUserId(order.getUserId());
            ticket.setOrderId(order.getId());
            ticket.setProductId(order.getProductId());
            ticket.setTicketTypeId(order.getTicketTypeId());
            ticket.setTicketTypeName(ticketType.getTypeName());
            ticket.setSeatInfo(ticketType.getTypeName() + " 第" + ((seatNo / 20) + 1) + "排 " + ((seatNo % 20) + 1) + "号");
            ticket.setPrice(order.getUnitPrice());
            ticket.setStatus(0);
            ticket.setCreateTime(new Date());
            ticket.setUpdateTime(new Date());
            userTicketMapper.insert(ticket);
            tickets.add(ticket);
        }
        return tickets;
    }

    /** 查询用户所有已购票（含演出名称）。 */
    public List<UserTicket> listUserTickets(Long userId) {
        List<UserTicket> tickets = userTicketMapper.selectList(new LambdaQueryWrapper<UserTicket>()
                .eq(UserTicket::getUserId, userId)
                .orderByDesc(UserTicket::getCreateTime));
        for (UserTicket t : tickets) {
            if (t.getProductId() != null) {
                SeckillProduct p = productMapper.selectById(t.getProductId());
                if (p != null) t.setProductName(p.getName());
            }
        }
        return tickets;
    }

    /** 根据票号查询单张票。 */
    public UserTicket getTicket(String ticketNo) {
        return userTicketMapper.selectOne(new LambdaQueryWrapper<UserTicket>()
                .eq(UserTicket::getTicketNo, ticketNo));
    }

    /** 用户退票：更新状态 → 回滚库存 → 通知候补 → 发消息。 */
    @Transactional
    public void refundTicket(String ticketNo, Long userId) {
        UserTicket ticket = getTicket(ticketNo);
        if (ticket == null || !ticket.getUserId().equals(userId)) throw new SeckillException(ResultCode.ORDER_NOT_FOUND);
        if (ticket.getStatus() != 0) throw new SeckillException(ResultCode.ORDER_CANCELLED);
        ticket.setStatus(2);
        ticket.setRefundTime(new Date());
        ticket.setUpdateTime(new Date());
        userTicketMapper.updateById(ticket);
        ticketTypeMapper.increaseStock(ticket.getTicketTypeId(), 1);
        String stockKey = RedisKey.stockKey(ticket.getTicketTypeId());
        String userBoughtKey = RedisKey.userBoughtKey(userId, ticket.getTicketTypeId());
        try {
            stringRedisTemplate.execute(new DefaultRedisScript<>(RedisKey.STOCK_INCR_SCRIPT, Long.class),
                    Arrays.asList(stockKey, userBoughtKey), "1");
        } catch (Exception e) {
            log.warn("STOCK_INCR_SCRIPT 执行异常，降级为 incr stock", e);
            stringRedisTemplate.opsForValue().increment(stockKey, 1);
        }
        waitlistService.notifyWaitlist(ticket.getTicketTypeId(), 1);
        try { messageService.sendTicketRefundMsg(userId, ticketNo, ticket.getTicketTypeId()); } catch (Exception e) { log.warn("发送退票成功消息失败，不影响主流程", e); }
        log.info("用户票退票成功：ticketNo={}, ticketTypeId={}", ticketNo, ticket.getTicketTypeId());
    }

    /** 验票：将票状态标记为已使用。 */
    public void useTicket(String ticketNo) {
        UserTicket ticket = getTicket(ticketNo);
        if (ticket == null) throw new SeckillException(ResultCode.ORDER_NOT_FOUND);
        if (ticket.getStatus() == 1) return;
        if (ticket.getStatus() != 0) throw new SeckillException(ResultCode.ORDER_CANCELLED);
        ticket.setStatus(1);
        ticket.setUsedTime(new Date());
        ticket.setUpdateTime(new Date());
        userTicketMapper.updateById(ticket);
    }
}
