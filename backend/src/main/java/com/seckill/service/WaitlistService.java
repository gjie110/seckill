package com.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.common.ResultCode;
import com.seckill.common.SeckillException;
import com.seckill.entity.Message;
import com.seckill.entity.SeckillProduct;
import com.seckill.entity.TicketType;
import com.seckill.entity.Waitlist;
import com.seckill.mapper.MessageMapper;
import com.seckill.mapper.ProductMapper;
import com.seckill.mapper.TicketTypeMapper;
import com.seckill.mapper.WaitlistMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistMapper waitlistMapper;
    private final MessageMapper messageMapper;
    private final TicketTypeMapper ticketTypeMapper;
    private final ProductMapper productMapper;

    /**
     * 用户提交候补登记。
     * 规则：
     *   - userId / ticketTypeId 必须提供
     *   - 票种必须存在且上架
     *   - 同一用户对同一票种不允许重复进行中的候补
     */
    public Waitlist joinWaitlist(Long userId, Long productId, Long ticketTypeId) {
        if (userId == null) {
            throw new SeckillException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        if (ticketTypeId == null) {
            throw new SeckillException(ResultCode.PARAM_ERROR, "票种ID不能为空");
        }
        TicketType ticket = ticketTypeMapper.selectById(ticketTypeId);
        if (ticket == null) {
            throw new SeckillException(ResultCode.PARAM_ERROR, "票种不存在");
        }
        if (ticket.getStatus() == null || ticket.getStatus() != 1) {
            throw new SeckillException(ResultCode.PARAM_ERROR, "该票种未上架");
        }
        if (productId == null) {
            productId = ticket.getProductId();
        }

        java.util.List<Waitlist> existingList = waitlistMapper.selectList(
                new LambdaQueryWrapper<Waitlist>()
                        .eq(Waitlist::getUserId, userId)
                        .eq(Waitlist::getTicketTypeId, ticketTypeId)
                        .in(Waitlist::getStatus, 0, 1)
                        .last("LIMIT 1")
        );
        if (existingList != null && !existingList.isEmpty()) {
            throw new SeckillException(ResultCode.PARAM_ERROR, "您已登记候补，无需重复提交");
        }

        Waitlist w = new Waitlist();
        w.setUserId(userId);
        w.setProductId(productId);
        w.setTicketTypeId(ticketTypeId);
        w.setQuantity(1);
        w.setStatus(0);
        w.setCreateTime(new Date());
        w.setUpdateTime(new Date());
        waitlistMapper.insert(w);

        try {
            Message msg = new Message();
            msg.setUserId(userId);
            msg.setType("waitlist_join");
            msg.setTitle("🎫 候补登记成功");
            msg.setContent("您已成功候补【" + ticket.getTypeName() + "】，有票时将第一时间通知您。");
            msg.setRelatedId(ticketTypeId);
            msg.setStatus(0);
            msg.setCreateTime(new Date());
            messageMapper.insert(msg);
        } catch (Exception e) {
            log.warn("发送候补成功通知失败，不影响主流程", e);
        }

        log.info("用户候补登记成功：userId={}, ticketTypeId={}", userId, ticketTypeId);
        return w;
    }

    public void notifyWaitlist(Long ticketTypeId, Integer releaseCount) {
        int limit = (releaseCount == null || releaseCount < 1) ? 1 : releaseCount;
        List<Waitlist> waitlists = waitlistMapper.selectList(
                new LambdaQueryWrapper<Waitlist>()
                        .eq(Waitlist::getTicketTypeId, ticketTypeId)
                        .eq(Waitlist::getStatus, 0)
                        .orderByAsc(Waitlist::getCreateTime)
                        .last("LIMIT " + limit)
        );
        if (waitlists.isEmpty()) {
            return;
        }

        TicketType ticketType = ticketTypeMapper.selectById(ticketTypeId);
        SeckillProduct product = (ticketType != null) ? productMapper.selectById(ticketType.getProductId()) : null;
        String productName = (product != null) ? product.getName() : "";
        String typeName = (ticketType != null) ? ticketType.getTypeName() : "演出票";

        String title = "🎫 您候补的【" + productName + "·" + typeName + "】有票了！";
        String content = "有 " + releaseCount + " 张【" + typeName + "】刚刚释放出来，" +
                "请尽快前往演出详情页下单（通知仅基于候补顺序，库存先到先得）。";

        int notified = 0;
        for (Waitlist w : waitlists) {
            w.setStatus(1);
            w.setNotifyTime(new Date());
            w.setUpdateTime(new Date());
            waitlistMapper.updateById(w);

            Message msg = new Message();
            msg.setUserId(w.getUserId());
            msg.setType("waitlist_notify");
            msg.setTitle(title);
            msg.setContent(content);
            msg.setRelatedId(ticketTypeId);
            msg.setStatus(0);
            msg.setCreateTime(new Date());
            messageMapper.insert(msg);
            notified++;
        }
        log.info("候补通知发送完毕：ticketTypeId={}, 释放{}张, 通知{}人", ticketTypeId, releaseCount, notified);
    }

    public List<Waitlist> listMyWaitlists(Long userId) {
        return waitlistMapper.selectList(
                new LambdaQueryWrapper<Waitlist>()
                        .eq(Waitlist::getUserId, userId)
                        .orderByDesc(Waitlist::getCreateTime)
        );
    }
}