package com.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    @Update("UPDATE message SET status = 1 WHERE user_id = #{userId} AND status = 0")
    int markAllRead(Long userId);

    @Update("UPDATE message SET status = 1 WHERE id = #{id}")
    int markRead(Long id);
}