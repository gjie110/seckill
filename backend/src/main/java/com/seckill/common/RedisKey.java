package com.seckill.common;

public final class RedisKey {

    private RedisKey() {}

    // 票种库存 Key（按票种维度，不是按演出维度）
    public static String stockKey(long ticketTypeId) {
        return "stock:" + ticketTypeId;
    }

    // 有效票种 ID 集合
    public static final String VALID_TICKETS = "valid:tickets";

    // 演出（产品）信息缓存 Key
    public static String productKey(long productId) {
        return "product:" + productId;
    }

    // 票种信息缓存 Key
    public static String ticketTypeKey(long ticketTypeId) {
        return "ticket:type:" + ticketTypeId;
    }

    // 演出下的票种列表缓存 Key
    public static String ticketTypesByProductKey(long productId) {
        return "ticket:types:" + productId;
    }

    // 秒杀/特价活动配置缓存 Key
    public static String configKey(long ticketTypeId) {
        return "config:" + ticketTypeId;
    }

    // 用户购买标记 Key（按票种维度）：user:bought:{userId}:{ticketTypeId}
    public static String userBoughtKey(long userId, long ticketTypeId) {
        return "user:bought:" + userId + ":" + ticketTypeId;
    }

    // 用户限流计数器 Key
    public static String limitKey(long userId, long minute) {
        return "limit:" + userId + ":" + minute;
    }

    // 订单处理完成标记 Key（幂等）
    public static String doneKey(String orderNo) {
        return "done:" + orderNo;
    }

    // 候补队列 Key（Set，存 userId）
    public static String waitlistKey(long ticketTypeId) {
        return "waitlist:" + ticketTypeId;
    }

    // ============= 列表页缓存 Key =============
    // 首页演出列表（全量，带最低价等扩展字段）
    public static final String PRODUCT_LIST = "product:list";

    // 按渠道筛选的列表（channel = seckill/special/regular，null 用 PRODUCT_LIST）
    public static String productListByChannelKey(String channel) {
        return "product:list:" + channel;
    }

    // 轮播图（banner）列表
    public static final String BANNER_LIST = "product:banner";

    // 即将开始的秒杀活动列表（首页活动卡片列表）
    public static final String UPCOMING_CONFIGS = "config:upcoming";

    // 列表缓存 TTL（相对较短，以便库存/价格变化更快反映）
    public static final long LIST_CACHE_TTL = 300L;   // 5 分钟

    // 缓存过期时间
    public static final long PRODUCT_CACHE_TTL = 3600L;
    public static final long LIMIT_KEY_TTL = 60L;
    public static final long DONE_KEY_TTL = 86400L;
    // 读缓存的 TTL（穿透保护 + 短 TTL 保持基本新鲜度）
    public static final long READ_CACHE_TTL = 600L;     // 10 分钟
    public static final long READ_CACHE_NULL_TTL = 60L; // 空值缓存 1 分钟（防穿透）

    /**
     * Redis Lua 脚本：库存扣减+防重复下单（按张数计数）
     * KEYS[1]: 库存 key
     * KEYS[2]: 用户购买张数计数器 key（值为已购买的总张数）
     * ARGV[1]: 用户 ID（预留）
     * ARGV[2]: 扣减数量
     * ARGV[3]: 单用户最多可购买总张数
     *
     * 返回值:
     *   1: 扣减成功
     *   0: 库存不足
     *  -1: 用户已达到单用户购买上限（累计张数已达上限）
     *  -2: 库存 key 不存在
     */
    public static final String STOCK_DECR_SCRIPT =
            "if redis.call('exists', KEYS[1]) == 0 then " +
            "    return -2 " +
            "end " +
            "local stock = tonumber(redis.call('get', KEYS[1])) " +
            "if stock < tonumber(ARGV[2]) then " +
            "    return 0 " +
            "end " +
            "local bought = 0 " +
            "if redis.call('exists', KEYS[2]) == 1 then " +
            "    bought = tonumber(redis.call('get', KEYS[2])) " +
            "end " +
            "if (bought + tonumber(ARGV[2])) > tonumber(ARGV[3]) then " +
            "    return -1 " +
            "end " +
            "redis.call('decrby', KEYS[1], ARGV[2]) " +
            "redis.call('incrby', KEYS[2], ARGV[2]) " +
            "redis.call('expire', KEYS[2], 86400) " +
            "return 1";

    /**
     * Lua 脚本：库存回滚 / 退票（把用户购买计数器减去相应数量）
     * KEYS[1]: 库存 key
     * KEYS[2]: 用户购买张数计数器 key
     * ARGV[1]: 回滚/退票数量
     */
    public static final String STOCK_INCR_SCRIPT =
            "if redis.call('exists', KEYS[1]) == 1 then " +
            "    redis.call('incrby', KEYS[1], ARGV[1]) " +
            "end " +
            "if redis.call('exists', KEYS[2]) == 1 then " +
            "    local cur = tonumber(redis.call('get', KEYS[2])) " +
            "    local left = cur - tonumber(ARGV[1]) " +
            "    if left <= 0 then " +
            "        redis.call('del', KEYS[2]) " +
            "    else " +
            "        redis.call('set', KEYS[2], left) " +
            "        redis.call('expire', KEYS[2], 86400) " +
            "    end " +
            "end " +
            "return 1";
}
