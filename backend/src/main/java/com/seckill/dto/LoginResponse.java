package com.seckill.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应信息
 * 登录成功后返回给前端的用户信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户角色：user-普通用户，admin-管理员
     */
    private String role;

    /**
     * 简单的登录标识（实际项目中可改为JWT Token）
     */
    private String token;
}
