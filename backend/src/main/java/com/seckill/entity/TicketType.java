package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("ticket_type")
public class TicketType {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String typeName;
    private String seatArea;
    private String channel;
    private BigDecimal price;
    private Integer totalStock;
    private Integer seckillStock;
    private Integer availableStock;
    private Integer soldStock;
    private Integer seatCount;
    private Integer status;
    private Integer deleted;
    private Date createTime;
    private Date updateTime;
}