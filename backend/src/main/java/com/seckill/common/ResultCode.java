package com.seckill.common;

public enum ResultCode {
    SUCCESS(200, "操作成功"),
    ERROR(500, "系统异常"),
    SERVICE_BUSY(501, "服务繁忙，请稍后再试"),

    PRODUCT_NOT_FOUND(1001, "商品不存在"),
    PRODUCT_OFF_SALE(1002, "商品已下架"),
    ACTIVITY_NOT_START(1003, "秒杀活动还未开始"),
    ACTIVITY_ENDED(1004, "秒杀活动已结束"),

    STOCK_NOT_ENOUGH(2001, "库存不足"),
    ALREADY_PURCHASED(2002, "您已购买过该商品，每人限购"),
    PURCHASE_LIMIT(2003, "超过购买数量限制"),
    JOINED_WAITLIST(2004, "已加入候补，有票后第一时间通知您"),

    ORDER_NOT_FOUND(3001, "订单不存在"),
    ORDER_TIMEOUT(3002, "订单已超时，请重新下单"),
    ORDER_PAID(3003, "订单已支付"),
    ORDER_CANCELLED(3004, "订单已取消"),

    RATE_LIMIT(4001, "请求过于频繁，请稍后再试"),
    MQ_SEND_FAILED(4002, "消息发送失败"),
    PARAM_ERROR(4003, "参数错误");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() { return code; }
    public String getMsg() { return msg; }
}
