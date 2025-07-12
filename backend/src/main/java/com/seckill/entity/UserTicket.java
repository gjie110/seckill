package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("user_ticket")
public class UserTicket {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ticketNo;
    private Long userId;
    private Long orderId;
    private Long productId;
    @TableField(exist = false)
    private String productName;
    private Long ticketTypeId;
    private String ticketTypeName;
    private String seatInfo;
    private BigDecimal price;
    private Integer status;
    private Date usedTime;
    private Date refundTime;
    private Date createTime;
    private Date updateTime;
}