package com.seckill.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 秒杀业务配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "seckill")
public class SeckillProperties {

    /**
     * 限流配置
     */
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class RateLimit {
        /**
         * 每用户每分钟最多请求次数
         */
        private int requestsPerMinute = 10;
    }

    /**
     * 订单超时时间（分钟）
     */
    private int orderTimeoutMinutes = 15;

    /**
     * MQ Topic 配置
     */
    private Topic topic = new Topic();

    @Data
    public static class Topic {
        /**
         * 订单消息 Topic
         */
        private String order = "seckill-order-topic";
        /**
         * 延迟消息 Topic（用于订单超时取消）
         */
        private String delay = "seckill-order-topic-delay";
    }
}
