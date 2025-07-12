package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("seckill_product")
public class SeckillProduct {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String venue;
    private Date showTime;
    private String posterUrl;
    private Integer isBanner;
    private Integer status;
    private Integer deleted;
    private Date createTime;
    private Date updateTime;
}