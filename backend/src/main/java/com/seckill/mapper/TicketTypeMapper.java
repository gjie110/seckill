package com.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.entity.TicketType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TicketTypeMapper extends BaseMapper<TicketType> {

    @Update("UPDATE ticket_type SET available_stock = available_stock - #{quantity}, sold_stock = sold_stock + #{quantity} WHERE id = #{ticketTypeId} AND available_stock >= #{quantity}")
    int decreaseStock(Long ticketTypeId, Integer quantity);

    @Update("UPDATE ticket_type SET available_stock = available_stock + #{quantity}, sold_stock = sold_stock - #{quantity} WHERE id = #{ticketTypeId}")
    int increaseStock(Long ticketTypeId, Integer quantity);

    @Update("UPDATE ticket_type SET available_stock = #{newStock}, total_stock = #{newStock}, update_time = NOW() WHERE id = #{ticketTypeId}")
    int setAvailableStock(Long ticketTypeId, Integer newStock);

    @Update("UPDATE ticket_type SET status = #{status}, update_time = NOW() WHERE id = #{ticketTypeId}")
    int setStatus(Long ticketTypeId, Integer status);
}