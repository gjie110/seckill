package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("waitlist")
public class Waitlist {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long productId;
    private Long ticketTypeId;
    private Integer quantity;
    private Integer status;
    private Date notifyTime;
    private Date createTime;
    private Date updateTime;
}