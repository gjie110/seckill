package com.seckill.service;

import com.alibaba.fastjson.JSON;
import com.seckill.dto.SeckillMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private static final String TOPIC = "seckill-order-topic";

    public void sendOrderMessage(SeckillMessage message) {
        String payload = JSON.toJSONString(message);
        Message<String> msg = MessageBuilder.withPayload(payload).build();
        rocketMQTemplate.syncSend(TOPIC, msg);
        log.info("MQ消息发送成功：orderNo={}", message.getOrderNo());
    }
}