package com.seckill.common;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

/**
 * 订单号生成器（雪花算法）
 */
public class OrderNoGenerator {

    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(1, 1);

    private OrderNoGenerator() {}

    /**
     * 生成订单号
     * 格式: 时间戳(41bit) + 数据中心(5bit) + 机器ID(5bit) + 序列号(12bit)
     *
     * @return 订单号字符串
     */
    public static String generate() {
        return String.valueOf(SNOWFLAKE.nextId());
    }

    /**
     * 生成带前缀的订单号
     *
     * @param prefix 前缀，如 "T" 表示 Ticket
     * @return 订单号
     */
    public static String generate(String prefix) {
        return prefix + SNOWFLAKE.nextId();
    }
}
