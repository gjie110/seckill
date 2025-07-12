package com.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.entity.Waitlist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface WaitlistMapper extends BaseMapper<Waitlist> {

    @Select("SELECT * FROM waitlist WHERE ticket_type_id = #{ticketTypeId} AND status IN (0, 1) ORDER BY create_time ASC")
    List<Waitlist> findActiveByTicketType(Long ticketTypeId);

    @Update("UPDATE waitlist SET status = 1, notify_time = NOW() WHERE ticket_type_id = #{ticketTypeId} AND status IN (0, 1)")
    int markAllNotified(Long ticketTypeId);
}