package com.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.entity.Message;
import com.seckill.entity.SeckillProduct;
import com.seckill.entity.TicketType;
import com.seckill.mapper.MessageMapper;
import com.seckill.mapper.ProductMapper;
import com.seckill.mapper.TicketTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;
    private final TicketTypeMapper ticketTypeMapper;
    private final ProductMapper productMapper;

    public void sendMessage(Long userId, String type, String title, String content, Long relatedId) {
        Message msg = new Message();
        msg.setUserId(userId);
        msg.setType(type);
        msg.setTitle(title);
        msg.setContent(content);
        msg.setRelatedId(relatedId);
        msg.setStatus(0);
        msg.setCreateTime(new Date());
        messageMapper.insert(msg);
        log.debug("发送消息 -> userId:{}, type:{}, title:{}", userId, type, title);
    }

    public void sendWaitlistJoinedMsg(Long userId, Long ticketTypeId, int qty) {
        TicketType tt = ticketTypeMapper.selectById(ticketTypeId);
        SeckillProduct sp = (tt != null) ? productMapper.selectById(tt.getProductId()) : null;
        String pName = (sp != null) ? sp.getName() : "演出";
        String tName = (tt != null) ? tt.getTypeName() : "票";
        String title = "✅ 已加入候补队列";
        String content = "您已成功加入【" + pName + "·" + tName + "】候补队列（" + qty + "张），" +
                "一旦有票释放，系统将第一时间通知您，请留意消息中心。";
        sendMessage(userId, "waitlist_joined", title, content, ticketTypeId);
    }

    public void sendOrderPaidMsg(Long userId, String orderNo, Long ticketTypeId, int ticketCount) {
        TicketType tt = ticketTypeMapper.selectById(ticketTypeId);
        SeckillProduct sp = (tt != null) ? productMapper.selectById(tt.getProductId()) : null;
        String pName = (sp != null) ? sp.getName() : "演出";
        String tName = (tt != null) ? tt.getTypeName() : "票";
        String title = "🎉 购票成功！共" + ticketCount + "张【" + tName + "】";
        String content = "您的订单 " + orderNo + " 已支付成功，共获得" + ticketCount + "张【" + pName + "·" + tName + "】。" +
                "请前往「我的票」查看座位信息并保存票号，进场时出示。";
        sendMessage(userId, "order_paid", title, content, ticketTypeId);
    }

    public void sendOrderCancelledMsg(Long userId, String orderNo, Long ticketTypeId) {
        TicketType tt = ticketTypeMapper.selectById(ticketTypeId);
        SeckillProduct sp = (tt != null) ? productMapper.selectById(tt.getProductId()) : null;
        String pName = (sp != null) ? sp.getName() : "演出";
        String tName = (tt != null) ? tt.getTypeName() : "票";
        String title = "订单已取消";
        String content = "订单 " + orderNo + "（" + pName + "·" + tName + "）已成功取消，库存已释放。";
        sendMessage(userId, "order_cancelled", title, content, ticketTypeId);
    }

    public void sendOrderTimeoutMsg(Long userId, String orderNo, Long ticketTypeId) {
        TicketType tt = ticketTypeMapper.selectById(ticketTypeId);
        SeckillProduct sp = (tt != null) ? productMapper.selectById(tt.getProductId()) : null;
        String pName = (sp != null) ? sp.getName() : "演出";
        String tName = (tt != null) ? tt.getTypeName() : "票";
        String title = "⏰ 订单超时已自动取消";
        String content = "订单 " + orderNo + "（" + pName + "·" + tName + "）因超时未支付，已自动取消，库存已释放。";
        sendMessage(userId, "order_timeout", title, content, ticketTypeId);
    }

    public void sendTicketRefundMsg(Long userId, String ticketNo, Long ticketTypeId) {
        TicketType tt = ticketTypeMapper.selectById(ticketTypeId);
        SeckillProduct sp = (tt != null) ? productMapper.selectById(tt.getProductId()) : null;
        String pName = (sp != null) ? sp.getName() : "演出";
        String tName = (tt != null) ? tt.getTypeName() : "票";
        String title = "🔙 退票成功";
        String content = "票号 " + ticketNo + "（" + pName + "·" + tName + "）已成功办理退票，库存已释放，已通知候补用户。";
        sendMessage(userId, "ticket_refund", title, content, ticketTypeId);
    }

    public List<Message> listMessages(Long userId) {
        return messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getUserId, userId)
                        .orderByDesc(Message::getCreateTime)
        );
    }

    public long getUnreadCount(Long userId) {
        return messageMapper.selectCount(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getUserId, userId)
                        .eq(Message::getStatus, 0)
        );
    }

    public void markAllRead(Long userId) {
        messageMapper.markAllRead(userId);
    }

    public Message getMessageById(Long id) {
        return messageMapper.selectById(id);
    }

    public void markRead(Long id) {
        messageMapper.markRead(id);
    }
}