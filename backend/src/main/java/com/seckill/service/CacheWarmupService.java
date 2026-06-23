package com.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.common.RedisKey;
import com.seckill.entity.TicketType;
import com.seckill.mapper.TicketTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheWarmupService {

    private final StringRedisTemplate stringRedisTemplate;
    private final TicketTypeMapper ticketTypeMapper;

    /** 系统启动时将所有已发布票种库存批量写入 Redis，避免冷启动穿透 DB。 */
    @PostConstruct
    public void warmup() {
        try {
            List<TicketType> types = ticketTypeMapper.selectList(
                    new LambdaQueryWrapper<TicketType>().eq(TicketType::getDeleted, 0));
            int published = 0;
            for (TicketType t : types) {
                String key = RedisKey.stockKey(t.getId());
                if (t.getStatus() != null && t.getStatus() == 1) {
                    stringRedisTemplate.opsForValue().set(key, String.valueOf(t.getAvailableStock()));
                    stringRedisTemplate.opsForSet().add(RedisKey.VALID_TICKETS, String.valueOf(t.getId()));
                    published++;
                } else {
                    stringRedisTemplate.delete(key);
                    stringRedisTemplate.opsForSet().remove(RedisKey.VALID_TICKETS, String.valueOf(t.getId()));
                }
            }
            log.info("[缓存预热] 完成：共 {} 个票种，已发布 {} 个（库存已写入Redis）", types.size(), published);
        } catch (Exception e) {
            log.error("[缓存预热] 失败，但不阻止应用启动", e);
        }
    }

    /** 单个票种发布/下架时更新其 Redis 库存缓存。 */
    public void warmupSingle(Long ticketTypeId) {
        if (ticketTypeId == null) return;
        TicketType t = ticketTypeMapper.selectById(ticketTypeId);
        if (t == null) return;
        String key = RedisKey.stockKey(t.getId());
        if (t.getStatus() != null && t.getStatus() == 1) {
            stringRedisTemplate.opsForValue().set(key, String.valueOf(t.getAvailableStock()));
            stringRedisTemplate.opsForSet().add(RedisKey.VALID_TICKETS, String.valueOf(t.getId()));
            log.info("[缓存预热] 单票种已发布：{} - {}, 库存：{}", t.getId(), t.getTypeName(), t.getAvailableStock());
        } else {
            stringRedisTemplate.delete(key);
            stringRedisTemplate.opsForSet().remove(RedisKey.VALID_TICKETS, String.valueOf(t.getId()));
            log.info("[缓存预热] 单票种已下架：{} - {}", t.getId(), t.getTypeName());
        }
    }
}
