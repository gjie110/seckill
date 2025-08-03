package com.seckill.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.common.RedisKey;
import com.seckill.common.ResultCode;
import com.seckill.common.SeckillException;
import com.seckill.entity.SeckillConfig;
import com.seckill.entity.SeckillProduct;
import com.seckill.entity.TicketType;
import com.seckill.mapper.ProductMapper;
import com.seckill.mapper.SeckillConfigMapper;
import com.seckill.mapper.TicketTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final TicketTypeMapper ticketTypeMapper;
    private final SeckillConfigMapper seckillConfigMapper;
    private final CacheWarmupService cacheWarmupService;
    private final StringRedisTemplate stringRedisTemplate;

    // ========= Redis 空值标记（防止缓存穿透保护）
    private static final String CACHE_NULL = "__NULL__";

    // ========= 缓存清除工具方法（写操作后调用，保持与 DB 一致）
    private void evictProductCache(Long productId) {
        if (productId != null) stringRedisTemplate.delete(RedisKey.productKey(productId));
    }

    private void evictTicketTypeCache(Long ticketTypeId, Long productId) {
        if (ticketTypeId != null) stringRedisTemplate.delete(RedisKey.ticketTypeKey(ticketTypeId));
        if (productId != null) stringRedisTemplate.delete(RedisKey.ticketTypesByProductKey(productId));
    }

    private void evictConfigCache(Long ticketTypeId) {
        if (ticketTypeId != null) stringRedisTemplate.delete(RedisKey.configKey(ticketTypeId));
    }

    // 清除所有列表缓存（任何演出/票种变动时调用）
    private void evictAllListCaches() {
        stringRedisTemplate.delete(RedisKey.PRODUCT_LIST);
        stringRedisTemplate.delete(RedisKey.BANNER_LIST);
        stringRedisTemplate.delete(RedisKey.UPCOMING_CONFIGS);
        // 清掉按渠道缓存的列表（常见三种渠道）
        stringRedisTemplate.delete(RedisKey.productListByChannelKey("seckill"));
        stringRedisTemplate.delete(RedisKey.productListByChannelKey("special"));
        stringRedisTemplate.delete(RedisKey.productListByChannelKey("regular"));
        // 清掉带 channelFilter 的缓存（listProductsWithInfo 用 key）
        stringRedisTemplate.delete(RedisKey.productListByChannelKey("info:seckill"));
        stringRedisTemplate.delete(RedisKey.productListByChannelKey("info:special"));
        stringRedisTemplate.delete(RedisKey.productListByChannelKey("info:regular"));
        stringRedisTemplate.delete(RedisKey.productListByChannelKey("info:null"));
    }

    public List<SeckillProduct> listProducts() {
        return listProducts(null);
    }

    public List<Map<String, Object>> listProductsWithInfo(String channel) {
        String channelKey = (channel == null || channel.trim().isEmpty())
                ? RedisKey.productListByChannelKey("info:null")
                : RedisKey.productListByChannelKey("info:" + channel.trim());

        String cached = stringRedisTemplate.opsForValue().get(channelKey);
        if (cached != null && !CACHE_NULL.equals(cached)) {
            List<Map> parsed = JSONArray.parseArray(cached, Map.class);
            if (parsed != null) {
                List<Map<String, Object>> casted = new java.util.ArrayList<>();
                for (Map m : parsed) casted.add((Map<String, Object>) m);
                return casted;
            }
        }

        List<SeckillProduct> products = productMapper.selectList(new LambdaQueryWrapper<SeckillProduct>()
                .eq(SeckillProduct::getStatus, 1)
                .eq(SeckillProduct::getDeleted, 0)
                .orderByAsc(SeckillProduct::getShowTime));

        final String channelFilter = (channel == null || channel.trim().isEmpty()) ? null : channel.trim();

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (SeckillProduct p : products) {
            List<TicketType> allTickets = getTicketTypes(p.getId());
            if (allTickets == null || allTickets.isEmpty()) continue;

            List<TicketType> scopeTickets;
            if (channelFilter != null) {
                scopeTickets = allTickets.stream()
                        .filter(t -> channelFilter.equals(t.getChannel()))
                        .collect(java.util.stream.Collectors.toList());
                if (scopeTickets.isEmpty()) continue;
            } else {
                scopeTickets = allTickets;
            }

            TicketType lowestTicket = scopeTickets.stream()
                    .filter(t -> t.getPrice() != null)
                    .min((a, b) -> a.getPrice().compareTo(b.getPrice()))
                    .orElse(null);
            java.math.BigDecimal lowest = lowestTicket != null
                    ? lowestTicket.getPrice()
                    : java.math.BigDecimal.ZERO;

            List<String> channels = allTickets.stream()
                    .map(TicketType::getChannel)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());

            Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", p.getId());
            item.put("name", p.getName());
            item.put("description", p.getDescription());
            item.put("venue", p.getVenue());
            item.put("showTime", p.getShowTime());
            item.put("posterUrl", p.getPosterUrl());
            item.put("status", p.getStatus());
            item.put("isBanner", p.getIsBanner());
            item.put("lowestPrice", lowest);
            item.put("ticketCount", scopeTickets.size());
            item.put("channels", channels);
            item.put("channelFilter", channelFilter);
            item.put("hasSeckill", channels.contains("seckill"));
            item.put("hasSpecial", channels.contains("special"));
            item.put("hasRegular", channels.contains("regular"));
            if (lowestTicket != null) {
                item.put("ticketTypeId", lowestTicket.getId());
                item.put("ticketTypeName", lowestTicket.getTypeName());
                item.put("ticketChannel", lowestTicket.getChannel());
            }

            Date now2 = new Date();
            Date nearestStartTime = null;
            Date nearestEndTime = null;
            Integer nearestStatus = null;
            long minDiffPending = Long.MAX_VALUE;
            boolean hasActive = false;
            for (TicketType tt : scopeTickets) {
                SeckillConfig cfg = getSeckillConfig(tt.getId());
                Date st;
                Date et;
                if (cfg != null && cfg.getStartTime() != null && cfg.getEndTime() != null) {
                    st = cfg.getStartTime();
                    et = cfg.getEndTime();
                } else if ("regular".equals(tt.getChannel())) {
                    st = now2;
                    et = p.getShowTime();
                    if (et == null) continue;
                } else {
                    continue;
                }
                if (now2.before(st)) {
                    long diff = st.getTime() - now2.getTime();
                    if (diff < minDiffPending && !hasActive) {
                        minDiffPending = diff;
                        nearestStartTime = st;
                        nearestEndTime = et;
                        nearestStatus = 0;
                    }
                } else if (!now2.after(et)) {
                    if (!hasActive) {
                        hasActive = true;
                        nearestStartTime = st;
                        nearestEndTime = et;
                        nearestStatus = 1;
                        minDiffPending = -1;
                    }
                }
            }
            if (nearestStatus == null && !scopeTickets.isEmpty()) {
                nearestStatus = 2;
                nearestStartTime = p.getShowTime();
                nearestEndTime = p.getShowTime();
            }
            if (nearestStatus != null) {
                item.put("activityStatus", nearestStatus);
                item.put("startTime", nearestStartTime);
                item.put("endTime", nearestEndTime);
            }
            result.add(item);
        }

        if (result.isEmpty()) {
            stringRedisTemplate.opsForValue().set(channelKey, CACHE_NULL, RedisKey.READ_CACHE_NULL_TTL, TimeUnit.SECONDS);
        } else {
            stringRedisTemplate.opsForValue().set(channelKey, JSON.toJSONString(result), RedisKey.LIST_CACHE_TTL, TimeUnit.SECONDS);
        }
        return result;
    }

    public List<SeckillProduct> listProducts(String channel) {
        String channelKey = (channel == null || channel.trim().isEmpty())
                ? RedisKey.PRODUCT_LIST
                : RedisKey.productListByChannelKey(channel.trim());

        String cached = stringRedisTemplate.opsForValue().get(channelKey);
        if (cached != null && !CACHE_NULL.equals(cached)) {
            List<SeckillProduct> list = JSONArray.parseArray(cached, SeckillProduct.class);
            if (list != null) return list;
        }

        List<SeckillProduct> products = productMapper.selectList(new LambdaQueryWrapper<SeckillProduct>()
                .eq(SeckillProduct::getStatus, 1)
                .eq(SeckillProduct::getDeleted, 0)
                .orderByAsc(SeckillProduct::getShowTime));

        if (channel == null || channel.isEmpty()) {
            if (products == null || products.isEmpty()) {
                stringRedisTemplate.opsForValue().set(channelKey, CACHE_NULL, RedisKey.READ_CACHE_NULL_TTL, TimeUnit.SECONDS);
            } else {
                stringRedisTemplate.opsForValue().set(channelKey, JSON.toJSONString(products), RedisKey.LIST_CACHE_TTL, TimeUnit.SECONDS);
            }
            return products;
        }
        products.removeIf(p -> {
            List<TicketType> tickets = getTicketTypes(p.getId());
            return tickets.stream().noneMatch(t -> channel.equals(t.getChannel()));
        });
        if (products == null || products.isEmpty()) {
            stringRedisTemplate.opsForValue().set(channelKey, CACHE_NULL, RedisKey.READ_CACHE_NULL_TTL, TimeUnit.SECONDS);
        } else {
            stringRedisTemplate.opsForValue().set(channelKey, JSON.toJSONString(products), RedisKey.LIST_CACHE_TTL, TimeUnit.SECONDS);
        }
        return products;
    }

    public List<SeckillProduct> listBannerProducts() {
        String cached = stringRedisTemplate.opsForValue().get(RedisKey.BANNER_LIST);
        if (cached != null && !CACHE_NULL.equals(cached)) {
            List<SeckillProduct> list = JSONArray.parseArray(cached, SeckillProduct.class);
            if (list != null) return list;
        }
        List<SeckillProduct> banners = productMapper.selectList(new LambdaQueryWrapper<SeckillProduct>()
                .eq(SeckillProduct::getStatus, 1)
                .eq(SeckillProduct::getIsBanner, 1)
                .eq(SeckillProduct::getDeleted, 0)
                .orderByDesc(SeckillProduct::getCreateTime));
        if (banners == null || banners.isEmpty()) {
            stringRedisTemplate.opsForValue().set(RedisKey.BANNER_LIST, CACHE_NULL, RedisKey.READ_CACHE_NULL_TTL, TimeUnit.SECONDS);
        } else {
            stringRedisTemplate.opsForValue().set(RedisKey.BANNER_LIST, JSON.toJSONString(banners), RedisKey.LIST_CACHE_TTL, TimeUnit.SECONDS);
        }
        return banners;
    }

    public SeckillProduct getProduct(Long productId) {
        if (productId == null) {
            throw new SeckillException(ResultCode.PRODUCT_NOT_FOUND);
        }
        String key = RedisKey.productKey(productId);
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (CACHE_NULL.equals(cached)) {
                throw new SeckillException(ResultCode.PRODUCT_NOT_FOUND);
            }
            SeckillProduct p = JSON.parseObject(cached, SeckillProduct.class);
            if (p != null) return p;
        }
        SeckillProduct product = productMapper.selectById(productId);
        if (product == null) {
            // 空值缓存，防止缓存穿透
            stringRedisTemplate.opsForValue().set(key, CACHE_NULL, RedisKey.READ_CACHE_NULL_TTL, TimeUnit.SECONDS);
            throw new SeckillException(ResultCode.PRODUCT_NOT_FOUND);
        }
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(product), RedisKey.READ_CACHE_TTL, TimeUnit.SECONDS);
        return product;
    }

    public List<TicketType> getTicketTypes(Long productId) {
        if (productId == null) {
            return new ArrayList<>();
        }
        String key = RedisKey.ticketTypesByProductKey(productId);
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (CACHE_NULL.equals(cached)) {
                return new ArrayList<>();
            }
            List<TicketType> list = JSONArray.parseArray(cached, TicketType.class);
            if (list != null) return list;
        }
        List<TicketType> list = ticketTypeMapper.selectList(new LambdaQueryWrapper<TicketType>()
                .eq(TicketType::getProductId, productId)
                .eq(TicketType::getStatus, 1)
                .eq(TicketType::getDeleted, 0));
        if (list == null || list.isEmpty()) {
            stringRedisTemplate.opsForValue().set(key, CACHE_NULL, RedisKey.READ_CACHE_NULL_TTL, TimeUnit.SECONDS);
            return new ArrayList<>();
        }
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(list), RedisKey.READ_CACHE_TTL, TimeUnit.SECONDS);
        return list;
    }

    public TicketType getTicketType(Long ticketTypeId) {
        if (ticketTypeId == null) {
            throw new SeckillException(ResultCode.PRODUCT_NOT_FOUND);
        }
        String key = RedisKey.ticketTypeKey(ticketTypeId);
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (CACHE_NULL.equals(cached)) {
                throw new SeckillException(ResultCode.PRODUCT_NOT_FOUND);
            }
            TicketType t = JSON.parseObject(cached, TicketType.class);
            if (t != null) return t;
        }
        TicketType tt = ticketTypeMapper.selectById(ticketTypeId);
        if (tt == null) {
            stringRedisTemplate.opsForValue().set(key, CACHE_NULL, RedisKey.READ_CACHE_NULL_TTL, TimeUnit.SECONDS);
            throw new SeckillException(ResultCode.PRODUCT_NOT_FOUND);
        }
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(tt), RedisKey.READ_CACHE_TTL, TimeUnit.SECONDS);
        return tt;
    }

    public SeckillConfig getSeckillConfig(Long ticketTypeId) {
        if (ticketTypeId == null) {
            return null;
        }
        String key = RedisKey.configKey(ticketTypeId);
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (CACHE_NULL.equals(cached)) {
                return null;
            }
            SeckillConfig cfg = JSON.parseObject(cached, SeckillConfig.class);
            if (cfg != null) return cfg;
        }
        java.util.List<SeckillConfig> cfgList = seckillConfigMapper.selectList(new LambdaQueryWrapper<SeckillConfig>()
                .eq(SeckillConfig::getTicketTypeId, ticketTypeId)
                .eq(SeckillConfig::getDeleted, 0)
                .orderByDesc(SeckillConfig::getId)
                .last("LIMIT 1"));
        SeckillConfig cfg = (cfgList != null && !cfgList.isEmpty()) ? cfgList.get(0) : null;
        if (cfg == null) {
            stringRedisTemplate.opsForValue().set(key, CACHE_NULL, RedisKey.READ_CACHE_NULL_TTL, TimeUnit.SECONDS);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(cfg), RedisKey.READ_CACHE_TTL, TimeUnit.SECONDS);
        return cfg;
    }

    /**
     * 判断某票种是否可购买。
     * 规则：
     *  - 票种本身必须 status=1 且 availableStock>0
     *  - 常规票 (regular)：直接可购买
     *  - 秒杀/特价票：必须有配置且在活动时间内
     */
    public Map<String, Object> getPurchaseStatus(Long ticketTypeId) {
        Map<String, Object> res = new java.util.LinkedHashMap<>();
        TicketType ticket = getTicketType(ticketTypeId);
        SeckillProduct product = getProduct(ticket.getProductId());
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            res.put("canPurchase", false);
            res.put("reason", "演出不存在或已下架");
            return res;
        }

        boolean ticketOpen = ticket.getStatus() != null && ticket.getStatus() == 1;
        boolean hasStock = ticket.getAvailableStock() != null && ticket.getAvailableStock() > 0;
        res.put("ticketStatus", ticket.getStatus());
        res.put("availableStock", ticket.getAvailableStock());
        res.put("price", ticket.getPrice());
        res.put("channel", ticket.getChannel());
        res.put("typeName", ticket.getTypeName());
        res.put("productStatus", product.getStatus());
        // 暴露 showTime 给前端，作为无 endTime 时的兜底
        res.put("showTime", product.getShowTime());

        if (!ticketOpen) {
            res.put("canPurchase", false);
            res.put("reason", "票种未开售");
            return res;
        }

        if (!hasStock) {
            res.put("canPurchase", false);
            res.put("reason", "票种已售罄，可候补");
            return res;
        }

        // 检查演出是否已结束：演出时间已过则不可购买
        Date showTime = product.getShowTime();
        Date now = new Date();
        boolean showEnded = showTime != null && now.after(showTime);
        if (showEnded) {
            res.put("canPurchase", false);
            res.put("activityStatus", 2);
            res.put("reason", "演出已结束");
            return res;
        }

        if ("regular".equals(ticket.getChannel())) {
            // 常规票：上架 + 有库存 + 演出未结束 → 可购买；倒计时到 showTime
            res.put("canPurchase", true);
            res.put("activityStatus", 1);
            res.put("startTime", now);
            res.put("endTime", showTime);
            res.put("reason", "可购买");
            return res;
        }

        // seckill / special：必须配置且在活动时间内
        SeckillConfig cfg = getSeckillConfig(ticketTypeId);
        if (cfg == null) {
            // 没有配置的特价/秒杀票：视为常规票，演出未结束即可购买；倒计时到 showTime
            res.put("canPurchase", true);
            res.put("activityStatus", 1);
            res.put("startTime", now);
            res.put("endTime", showTime);
            res.put("reason", "可购买");
            return res;
        }

        res.put("startTime", cfg.getStartTime());
        res.put("endTime", cfg.getEndTime());
        res.put("maxPerUser", cfg.getMaxPerUser());
        // 管理员手动结束活动（status=2）具有最高优先级
        if (cfg.getStatus() != null && cfg.getStatus() == 2) {
            res.put("canPurchase", false);
            res.put("activityStatus", 2);
            res.put("reason", "活动已结束");
            return res;
        }
        if (now.before(cfg.getStartTime())) {
            res.put("canPurchase", false);
            res.put("activityStatus", 0);
            res.put("reason", "活动尚未开始");
            return res;
        }
        if (now.after(cfg.getEndTime())) {
            res.put("canPurchase", false);
            res.put("activityStatus", 2);
            res.put("reason", "活动已结束");
            return res;
        }
        res.put("activityStatus", 1);
        res.put("canPurchase", true);
        res.put("reason", "可购买");
        return res;
    }

    /**
     * 供前端商品详情页使用：返回商品 + 所有票种及其实时状态。
     */
    public Map<String, Object> getProductDetailWithTickets(Long productId) {
        SeckillProduct product = getProduct(productId);
        List<TicketType> tickets = getTicketTypes(productId);
        java.math.BigDecimal lowest = tickets.stream()
                .map(TicketType::getPrice)
                .filter(java.util.Objects::nonNull)
                .min(java.math.BigDecimal::compareTo)
                .orElse(java.math.BigDecimal.ZERO);

        Map<String, Object> res = new java.util.HashMap<>();
        res.put("product", product);
        res.put("ticketTypes", tickets);
        res.put("lowestPrice", lowest);
        res.put("ticketCount", tickets.size());
        List<String> channels = tickets.stream()
                .map(TicketType::getChannel)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        res.put("channels", channels);
        // 每个票种带上配置信息
        List<Map<String, Object>> ticketsWithStatus = new java.util.ArrayList<>();
        for (TicketType t : tickets) {
            Map<String, Object> status = getPurchaseStatus(t.getId());
            Map<String, Object> merged = new java.util.HashMap<>();
            merged.put("id", t.getId());
            merged.put("productId", t.getProductId());
            merged.put("typeName", t.getTypeName());
            merged.put("seatArea", t.getSeatArea());
            merged.put("channel", t.getChannel());
            merged.put("price", t.getPrice());
            merged.put("totalStock", t.getTotalStock());
            merged.put("availableStock", t.getAvailableStock());
            merged.put("status", t.getStatus());
            merged.put("config", status);
            ticketsWithStatus.add(merged);
        }
        res.put("ticketTypes", ticketsWithStatus);
        return res;
    }

    public void checkActivityTime(TicketType ticketType) {
        if ("regular".equals(ticketType.getChannel())) {
            return;
        }
        SeckillConfig config = getSeckillConfig(ticketType.getId());
        if (config == null) {
            return;
        }
        Date now = new Date();
        if (now.before(config.getStartTime())) {
            throw new SeckillException(ResultCode.ACTIVITY_NOT_START);
        }
        if (now.after(config.getEndTime())) {
            throw new SeckillException(ResultCode.ACTIVITY_ENDED);
        }
    }

    /**
     * 管理员强制结束某个票种的活动。
     * - 找到该票种的 seckill_config（如有）；
     * - 把 endTime 设为当前时间；
     * - 把 status 设为 2（已结束）。
     * 完成后，前端会立即显示"已结束"且不可购买。
     */
    @Transactional
    public void forceEndActivity(Long ticketTypeId) {
        TicketType tt = getTicketType(ticketTypeId);
        SeckillConfig cfg = getSeckillConfig(ticketTypeId);
        if (cfg == null) {
            throw new SeckillException(ResultCode.PRODUCT_NOT_FOUND, "该票种没有活动配置");
        }
        Date now = new Date();
        cfg.setEndTime(now);
        cfg.setStatus(2);
        cfg.setUpdateTime(now);
        seckillConfigMapper.updateById(cfg);
        evictConfigCache(ticketTypeId);
        evictTicketTypeCache(ticketTypeId, tt.getProductId());
        evictAllListCaches();
        log.info("[管理员] 强制结束活动：ticketTypeId={}, endTime=now, status=2", ticketTypeId);
    }

    /**
     * 一键修复五月天C票（ticket_type_id=7）的活动数据为已结束状态。
     * - 如果该票种有 seckill_config → 改为 status=2, end_time=NOW()；
     * - 如果没有 → 新增一条 status=2 的记录。
     * 返回影响的行数。
     */
    @Transactional
    public int fixWuyutianCTicketAsEnded() {
        final Long ticketTypeId = 7L;
        // 安全检查：确认是五月天的C票
        TicketType tt = ticketTypeMapper.selectById(ticketTypeId);
        if (tt == null || tt.getDeleted() != 0) {
            throw new SeckillException(ResultCode.PRODUCT_NOT_FOUND, "未找到C票（id=7）");
        }
        Date now = new Date();
        SeckillConfig cfg = getSeckillConfig(ticketTypeId);
        if (cfg != null) {
            cfg.setEndTime(now);
            cfg.setStatus(2);
            cfg.setUpdateTime(now);
            int rows = seckillConfigMapper.updateById(cfg);
            evictConfigCache(ticketTypeId);
            evictTicketTypeCache(ticketTypeId, tt.getProductId());
            evictAllListCaches();
            log.info("[修复] 更新五月天C票 cfg 为已结束，影响行数={}", rows);
            return rows;
        } else {
            SeckillConfig newCfg = new SeckillConfig();
            newCfg.setProductId(tt.getProductId());
            newCfg.setTicketTypeId(ticketTypeId);
            newCfg.setStartTime(tt.getCreateTime() != null ? tt.getCreateTime() : now);
            newCfg.setEndTime(now);
            newCfg.setMaxPerUser(3);
            newCfg.setOrderTimeoutMinutes(5);
            newCfg.setStatus(2);
            newCfg.setDeleted(0);
            newCfg.setCreateTime(now);
            newCfg.setUpdateTime(now);
            seckillConfigMapper.insert(newCfg);
            evictConfigCache(ticketTypeId);
            evictTicketTypeCache(ticketTypeId, tt.getProductId());
            evictAllListCaches();
            log.info("[修复] 新增五月天C票 cfg 状态为已结束，id={}", newCfg.getId());
            return 1;
        }
    }

    public List<SeckillProduct> listAllProducts() {
        return productMapper.selectList(new LambdaQueryWrapper<SeckillProduct>()
                .eq(SeckillProduct::getDeleted, 0)
                .orderByDesc(SeckillProduct::getCreateTime));
    }

    public List<TicketType> listAllTicketTypes(Long productId) {
        return ticketTypeMapper.selectList(new LambdaQueryWrapper<TicketType>()
                .eq(TicketType::getProductId, productId)
                .eq(TicketType::getDeleted, 0)
                .orderByAsc(TicketType::getId));
    }

    @Transactional
    public void setStock(Long ticketTypeId, Integer stock) {
        if (stock == null || stock < 0) {
            throw new SeckillException(ResultCode.PARAM_ERROR);
        }
        TicketType tt = getTicketType(ticketTypeId);
        int rows = ticketTypeMapper.setAvailableStock(ticketTypeId, stock);
        if (rows <= 0) {
            throw new SeckillException(ResultCode.SERVICE_BUSY);
        }
        if (tt.getStatus() != null && tt.getStatus() == 1) {
            cacheWarmupService.warmupSingle(ticketTypeId);
        }
        evictTicketTypeCache(ticketTypeId, tt.getProductId());
        evictAllListCaches();
        log.info("[管理员] 设置库存：ticketTypeId={}, 旧库存={}, 新库存={}", ticketTypeId, tt.getAvailableStock(), stock);
    }

    @Transactional
    public void publishTicket(Long ticketTypeId) {
        publishTicket(ticketTypeId, null);
    }

    /**
     * 发布票种（上架）。
     * @param publishStock 本次发布的库存数量；传 null 表示发布全部 availableStock。
     *                     逻辑：totalStock 设为 availableStock（原始），availableStock 设为 publishStock，
     *                     soldStock 归零，status 设为 1。
     *                     之后管理员可通过 adjustStock 继续追加库存。
     */
    @Transactional
    public void publishTicket(Long ticketTypeId, Integer publishStock) {
        TicketType tt = getTicketType(ticketTypeId);
        Integer available = tt.getAvailableStock();
        if (available == null || available <= 0) {
            throw new SeckillException(ResultCode.STOCK_NOT_ENOUGH, "可用库存为0，无法发布");
        }
        int toPublish = (publishStock != null && publishStock > 0)
                ? Math.min(publishStock, available)
                : available;

        tt.setStatus(1);
        tt.setAvailableStock(toPublish);
        tt.setSoldStock(0);
        tt.setUpdateTime(new Date());
        ticketTypeMapper.updateById(tt);

        cacheWarmupService.warmupSingle(ticketTypeId);
        evictTicketTypeCache(ticketTypeId, tt.getProductId());
        evictAllListCaches();
        log.info("[管理员] 发布票：ticketTypeId={}, 名称={}, 本次发布库存={}, 剩余未发布={}",
                ticketTypeId, tt.getTypeName(), toPublish, available - toPublish);
    }

    @Transactional
    public void unpublishTicket(Long ticketTypeId) {
        TicketType tt = getTicketType(ticketTypeId);
        ticketTypeMapper.setStatus(ticketTypeId, 0);
        cacheWarmupService.warmupSingle(ticketTypeId);
        evictTicketTypeCache(ticketTypeId, tt.getProductId());
        evictAllListCaches();
        log.info("[管理员] 下架票：ticketTypeId={}", ticketTypeId);
    }

    @Transactional
    public TicketType addTicketType(Long productId, String typeName, String seatArea,
                                    BigDecimal price, String channel,
                                    Integer totalStock, Integer seckillStock) {
        if (productId == null || typeName == null || price == null || channel == null
                || totalStock == null || seckillStock == null) {
            throw new SeckillException(ResultCode.PARAM_ERROR);
        }
        if (totalStock < 0 || seckillStock < 0) {
            throw new SeckillException(ResultCode.PARAM_ERROR);
        }
        if (seckillStock > totalStock) {
            throw new SeckillException(ResultCode.PARAM_ERROR);
        }
        getProduct(productId);
        TicketType tt = new TicketType();
        tt.setProductId(productId);
        tt.setTypeName(typeName);
        tt.setSeatArea(seatArea);
        tt.setPrice(price);
        tt.setChannel(channel);
        tt.setTotalStock(totalStock);
        tt.setSeckillStock(seckillStock);
        tt.setAvailableStock(totalStock);
        tt.setSoldStock(0);
        tt.setSeatCount(totalStock);
        tt.setStatus(0);
        tt.setDeleted(0);
        tt.setCreateTime(new Date());
        tt.setUpdateTime(new Date());
        ticketTypeMapper.insert(tt);
        evictTicketTypeCache(null, productId);
        evictAllListCaches();
        log.info("[管理员] 新增票种：productId={}, 名称={}, 渠道={}, 总库存={}, 抢购={}",
                productId, typeName, channel, totalStock, seckillStock);
        return tt;
    }

    @Transactional
    public void setSeckillConfig(Long ticketTypeId, Date startTime, Date endTime, Integer maxPerUser) {
        if (startTime == null || endTime == null || maxPerUser == null || maxPerUser < 1) {
            throw new SeckillException(ResultCode.PARAM_ERROR);
        }
        if (endTime.before(startTime)) {
            throw new SeckillException(ResultCode.PARAM_ERROR);
        }
        TicketType tt = getTicketType(ticketTypeId);
        SeckillConfig existing = getSeckillConfig(ticketTypeId);
        Date now = new Date();
        int autoStatus = now.after(endTime) ? 2 : (now.before(startTime) ? 0 : 1);
        if (existing == null) {
            SeckillConfig cfg = new SeckillConfig();
            cfg.setProductId(tt.getProductId());
            cfg.setTicketTypeId(ticketTypeId);
            cfg.setStartTime(startTime);
            cfg.setEndTime(endTime);
            cfg.setMaxPerUser(maxPerUser);
            cfg.setOrderTimeoutMinutes(5);
            cfg.setStatus(autoStatus);
            cfg.setDeleted(0);
            cfg.setCreateTime(new Date());
            cfg.setUpdateTime(new Date());
            seckillConfigMapper.insert(cfg);
        } else {
            existing.setStartTime(startTime);
            existing.setEndTime(endTime);
            existing.setMaxPerUser(maxPerUser);
            existing.setStatus(autoStatus);
            existing.setUpdateTime(new Date());
            seckillConfigMapper.updateById(existing);
        }
        evictConfigCache(ticketTypeId);
        evictAllListCaches();
        log.info("[管理员] 设置秒杀配置：ticketTypeId={}, start={}, end={}, max={}, autoStatus={}",
                ticketTypeId, startTime, endTime, maxPerUser, autoStatus);
    }

    /**
     * 仅修改票种的每人限购数量，不改动时间
     */
    @Transactional
    public void setMaxPerUser(Long ticketTypeId, Integer maxPerUser) {
        if (ticketTypeId == null || maxPerUser == null || maxPerUser < 1) {
            throw new SeckillException(ResultCode.PARAM_ERROR);
        }
        getTicketType(ticketTypeId);
        SeckillConfig existing = getSeckillConfig(ticketTypeId);
        if (existing == null) {
            throw new SeckillException(ResultCode.PRODUCT_NOT_FOUND, "该票种尚无活动配置，请先配置抢购时间");
        }
        existing.setMaxPerUser(maxPerUser);
        existing.setUpdateTime(new Date());
        seckillConfigMapper.updateById(existing);
        evictConfigCache(ticketTypeId);
        evictAllListCaches();
        log.info("[管理员] 修改限购数量：ticketTypeId={}, maxPerUser={}", ticketTypeId, maxPerUser);
    }

    @Transactional
    public SeckillProduct addProduct(String name, String description, String venue,
                                     Date showTime, String posterUrl, Integer isBanner) {
        if (name == null || venue == null || showTime == null) {
            throw new SeckillException(ResultCode.PARAM_ERROR);
        }
        SeckillProduct product = new SeckillProduct();
        product.setName(name);
        product.setDescription(description);
        product.setVenue(venue);
        product.setShowTime(showTime);
        product.setPosterUrl(posterUrl);
        product.setIsBanner(isBanner != null ? isBanner : 0);
        product.setStatus(0);
        product.setDeleted(0);
        product.setCreateTime(new Date());
        product.setUpdateTime(new Date());
        productMapper.insert(product);
        evictAllListCaches();
        log.info("[管理员] 新增演出：id={}, 名称={}, 加入轮播={}", product.getId(), name, product.getIsBanner());
        return product;
    }

    @Transactional
    public void updateProduct(Long productId, String name, String description, String venue,
                              Date showTime, String posterUrl, Integer status, Integer isBanner) {
        SeckillProduct product = getProduct(productId);
        if (name != null) {
            product.setName(name);
        }
        if (description != null) {
            product.setDescription(description);
        }
        if (venue != null) {
            product.setVenue(venue);
        }
        if (showTime != null) {
            product.setShowTime(showTime);
        }
        if (posterUrl != null) {
            product.setPosterUrl(posterUrl);
        }
        if (status != null) {
            product.setStatus(status);
        }
        if (isBanner != null) {
            product.setIsBanner(isBanner);
        }
        product.setUpdateTime(new Date());
        productMapper.updateById(product);
        evictProductCache(productId);
        evictAllListCaches();
        log.info("[管理员] 更新演出：id={}, 名称={}, 轮播={}", productId, name, product.getIsBanner());
    }

    @Transactional
    public void deleteProduct(Long productId) {
        getProduct(productId);
        productMapper.deleteById(productId);
        evictProductCache(productId);
        evictTicketTypeCache(null, productId);
        evictAllListCaches();
        log.info("[管理员] 删除演出：id={}", productId);
    }

    @Transactional
    public void publishProduct(Long productId) {
        SeckillProduct product = getProduct(productId);
        product.setStatus(1);
        product.setUpdateTime(new Date());
        productMapper.updateById(product);
        List<TicketType> tickets = listAllTicketTypes(productId);
        for (TicketType tt : tickets) {
            if (tt.getAvailableStock() != null && tt.getAvailableStock() > 0) {
                cacheWarmupService.warmupSingle(tt.getId());
            }
        }
        evictProductCache(productId);
        evictTicketTypeCache(null, productId);
        evictAllListCaches();
        log.info("[管理员] 发布演出：id={}, 名称={}", productId, product.getName());
    }

    @Transactional
    public void unpublishProduct(Long productId) {
        SeckillProduct product = getProduct(productId);
        product.setStatus(0);
        product.setUpdateTime(new Date());
        productMapper.updateById(product);
        evictProductCache(productId);
        evictAllListCaches();
        log.info("[管理员] 下架演出：id={}, 名称={}", productId, product.getName());
    }

    public List<SeckillProduct> searchProducts(String keyword, Integer status) {
        LambdaQueryWrapper<SeckillProduct> wrapper = new LambdaQueryWrapper<SeckillProduct>()
                .eq(SeckillProduct::getDeleted, 0);
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(SeckillProduct::getName, keyword.trim());
        }
        if (status != null) {
            wrapper.eq(SeckillProduct::getStatus, status);
        }
        wrapper.orderByDesc(SeckillProduct::getCreateTime);
        return productMapper.selectList(wrapper);
    }

    @Transactional
    public void updateTicketType(Long ticketTypeId, String typeName, String seatArea,
                                 BigDecimal price, String channel) {
        TicketType tt = getTicketType(ticketTypeId);
        if (typeName != null) {
            tt.setTypeName(typeName);
        }
        if (seatArea != null) {
            tt.setSeatArea(seatArea);
        }
        if (price != null) {
            tt.setPrice(price);
        }
        if (channel != null) {
            tt.setChannel(channel);
        }
        tt.setUpdateTime(new Date());
        ticketTypeMapper.updateById(tt);
        evictTicketTypeCache(ticketTypeId, tt.getProductId());
        evictAllListCaches();
        log.info("[管理员] 更新票种：ticketTypeId={}, 名称={}", ticketTypeId, typeName);
    }

    @Transactional
    public void deleteTicketType(Long ticketTypeId) {
        TicketType tt = getTicketType(ticketTypeId);
        if (tt.getStatus() != null && tt.getStatus() == 1) {
            throw new SeckillException(ResultCode.PARAM_ERROR, "该票种已发布，请先下架后再删除");
        }
        if (tt.getSoldStock() != null && tt.getSoldStock() > 0) {
            throw new SeckillException(ResultCode.PARAM_ERROR, "该票种已有订单，无法删除");
        }
        ticketTypeMapper.deleteById(ticketTypeId);
        evictTicketTypeCache(ticketTypeId, tt.getProductId());
        evictAllListCaches();
        log.info("[管理员] 删除票种：ticketTypeId={}, 名称={}", ticketTypeId, tt.getTypeName());
    }

    @Transactional
    public void adjustStock(Long ticketTypeId, Integer adjustment, String operation) {
        if (adjustment == null || adjustment <= 0) {
            throw new SeckillException(ResultCode.PARAM_ERROR, "调整数量必须大于0");
        }
        TicketType tt = getTicketType(ticketTypeId);
        if ("increase".equals(operation)) {
            tt.setAvailableStock(tt.getAvailableStock() + adjustment);
            tt.setTotalStock(tt.getTotalStock() + adjustment);
        } else if ("decrease".equals(operation)) {
            if (tt.getAvailableStock() < adjustment) {
                throw new SeckillException(ResultCode.STOCK_NOT_ENOUGH, "可用库存不足，无法扣减");
            }
            tt.setAvailableStock(tt.getAvailableStock() - adjustment);
            tt.setTotalStock(tt.getTotalStock() - adjustment);
        } else {
            throw new SeckillException(ResultCode.PARAM_ERROR, "操作类型错误");
        }
        tt.setUpdateTime(new Date());
        ticketTypeMapper.updateById(tt);
        if (tt.getStatus() != null && tt.getStatus() == 1) {
            cacheWarmupService.warmupSingle(ticketTypeId);
        }
        evictTicketTypeCache(ticketTypeId, tt.getProductId());
        evictAllListCaches();
        log.info("[管理员] 库存调整：ticketTypeId={}, 操作={}, 数量={}, 新库存={}",
                ticketTypeId, operation, adjustment, tt.getAvailableStock());
    }

    /**
     * 根据 ticketTypeId 获取单张票的完整详情（含所属演出信息、实时购买状态、活动配置）。
     * 这是"点击某张票后只显示该票"的独立页面数据来源。
     */
    public Map<String, Object> getTicketDetail(Long ticketTypeId) {
        // 通过已缓存的 getTicketType/getProduct 读，避免直连 DB
        TicketType tt = getTicketType(ticketTypeId);
        if (tt.getStatus() == null) {
            throw new SeckillException(ResultCode.PRODUCT_NOT_FOUND);
        }
        SeckillProduct product = getProduct(tt.getProductId());

        Map<String, Object> res = new java.util.LinkedHashMap<>();
        res.put("id", tt.getId());
        res.put("productId", tt.getProductId());
        res.put("typeName", tt.getTypeName());
        res.put("seatArea", tt.getSeatArea());
        res.put("channel", tt.getChannel());
        res.put("price", tt.getPrice());
        res.put("totalStock", tt.getTotalStock());
        res.put("availableStock", tt.getAvailableStock());
        res.put("soldStock", tt.getSoldStock());
        res.put("status", tt.getStatus());

        Map<String, Object> productMap = new java.util.LinkedHashMap<>();
        productMap.put("id", product.getId());
        productMap.put("name", product.getName());
        productMap.put("description", product.getDescription());
        productMap.put("venue", product.getVenue());
        productMap.put("showTime", product.getShowTime());
        productMap.put("posterUrl", product.getPosterUrl());
        productMap.put("status", product.getStatus());
        res.put("product", productMap);

        Map<String, Object> purchase = getPurchaseStatus(ticketTypeId);
        res.put("config", purchase);

        return res;
    }

    public List<Map<String, Object>> listUpcomingSeckillConfigs() {
        String cached = stringRedisTemplate.opsForValue().get(RedisKey.UPCOMING_CONFIGS);
        if (cached != null && !CACHE_NULL.equals(cached)) {
            List<Map> parsed = JSONArray.parseArray(cached, Map.class);
            if (parsed != null) {
                List<Map<String, Object>> casted = new java.util.ArrayList<>();
                for (Map m : parsed) casted.add((Map<String, Object>) m);
                return casted;
            }
        }

        List<SeckillConfig> configs = seckillConfigMapper.selectList(
                new LambdaQueryWrapper<SeckillConfig>()
                        .eq(SeckillConfig::getDeleted, 0)
                        .eq(SeckillConfig::getStatus, 1)
                        .ge(SeckillConfig::getEndTime, new Date())
                        .orderByAsc(SeckillConfig::getStartTime)
        );

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        Date now = new Date();
        for (SeckillConfig cfg : configs) {
            // 通过已缓存的 getTicketType/getProduct 读取，避免每个 config 都打一次 DB
            TicketType tt;
            try { tt = getTicketType(cfg.getTicketTypeId()); }
            catch (Exception e) { continue; }
            if (tt.getStatus() == null || tt.getStatus() != 1) continue;

            SeckillProduct product;
            try { product = getProduct(tt.getProductId()); }
            catch (Exception e) { continue; }
            if (product.getStatus() == null || product.getStatus() != 1) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("ticketTypeId", tt.getId());
            item.put("productId", product.getId());
            item.put("productName", product.getName());
            item.put("venue", product.getVenue());
            item.put("posterUrl", product.getPosterUrl());
            item.put("typeName", tt.getTypeName());
            item.put("price", tt.getPrice());
            item.put("channel", tt.getChannel());
            item.put("startTime", cfg.getStartTime());
            item.put("endTime", cfg.getEndTime());
            item.put("maxPerUser", cfg.getMaxPerUser());
            item.put("availableStock", tt.getAvailableStock());
            item.put("totalStock", tt.getTotalStock());
            if (now.before(cfg.getStartTime())) {
                item.put("activityStatus", 0);
            } else if (now.after(cfg.getEndTime())) {
                item.put("activityStatus", 2);
            } else {
                item.put("activityStatus", 1);
            }
            result.add(item);
        }

        if (result.isEmpty()) {
            stringRedisTemplate.opsForValue().set(RedisKey.UPCOMING_CONFIGS, CACHE_NULL, RedisKey.READ_CACHE_NULL_TTL, TimeUnit.SECONDS);
        } else {
            stringRedisTemplate.opsForValue().set(RedisKey.UPCOMING_CONFIGS, JSON.toJSONString(result), RedisKey.LIST_CACHE_TTL, TimeUnit.SECONDS);
        }
        return result;
    }
}