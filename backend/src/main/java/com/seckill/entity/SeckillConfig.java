package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("seckill_config")
public class SeckillConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Long ticketTypeId;
    private Date startTime;
    private Date endTime;
    private Integer maxPerUser;
    private Integer orderTimeoutMinutes;
    private Integer status;
    private Integer deleted;
    private Date createTime;
    private Date updateTime;
}