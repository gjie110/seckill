package com.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.entity.SeckillUser;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户数据访问层
 * 提供用户信息的查询操作
 */
@Mapper
public interface UserMapper extends BaseMapper<SeckillUser> {

    /**
     * 根据用户名查询用户
     * 支持用户名、手机号、邮箱作为登录名
     *
     * @param username 登录名（用户名/手机号/邮箱）
     * @return 用户信息，不存在则返回null
     */
    default SeckillUser selectByUsername(String username) {
        List<SeckillUser> list = this.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SeckillUser>()
                        .eq(SeckillUser::getUsername, username)
                        .or()
                        .eq(SeckillUser::getPhone, username)
                        .or()
                        .eq(SeckillUser::getEmail, username)
                        .last("LIMIT 1")
        );
        return list == null || list.isEmpty() ? null : list.get(0);
    }
}
