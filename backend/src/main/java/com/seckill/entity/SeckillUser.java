package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 用户实体类
 * 对应数据库表 seckill_user
 */
@Data
@TableName("seckill_user")
public class SeckillUser {
    /**
     * 用户ID - 主键，由用户注册时指定或系统自动生成
     */
    @TableId(type = IdType.INPUT)
    private Long userId;

    /**
     * 用户名/昵称
     */
    private String username;

    /**
     * 密码（加密存储，实际项目中应使用 BCrypt）
     */
    private String password;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 用户角色：user-普通用户，admin-管理员
     */
    private String role;

    /**
     * 账户状态：0-正常，1-禁用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 最后登录时间
     */
    private Date lastLoginTime;
}
