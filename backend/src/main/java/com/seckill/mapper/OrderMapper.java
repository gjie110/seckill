package com.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper extends BaseMapper<SeckillOrder> {

    @Select("SELECT COALESCE(SUM(quantity), 0) FROM seckill_order WHERE user_id = #{userId} AND product_id = #{productId} AND ticket_type_id = #{ticketTypeId} AND status != 2")
    Integer countUserPurchased(@Param("userId") Long userId, @Param("productId") Long productId, @Param("ticketTypeId") Long ticketTypeId);

    @Select("SELECT COUNT(*) FROM seckill_order WHERE user_id = #{userId} AND ticket_type_id = #{ticketTypeId} AND status = 0 AND timeout_time > NOW()")
    Integer countUserPending(@Param("userId") Long userId, @Param("ticketTypeId") Long ticketTypeId);
}