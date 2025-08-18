package com.seckill.dto;

import lombok.Data;

/**
 * 登录请求参数
 * 前端提交的登录信息
 */
@Data
public class LoginRequest {
    /**
     * 用户名（支持用户名、手机号、邮箱登录）
     */
    private String username;

    /**
     * 密码
     */
    private String password;
}
