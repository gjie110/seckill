package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.dto.LoginRequest;
import com.seckill.dto.LoginResponse;
import com.seckill.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证管理", description = "用户登录、角色查询、权限校验相关接口")
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
        summary = "用户登录",
        description = "用户使用用户名/手机号/邮箱加密码登录系统。"
    )
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            String roleMsg = "admin".equals(response.getRole()) ? "管理员登录成功" : "用户登录成功";
            log.info("{}: userId={}, username={}", roleMsg, response.getUserId(), response.getUsername());
            return Result.success(roleMsg, response);
        } catch (IllegalArgumentException e) {
            log.warn("登录失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("登录系统异常", e);
            return Result.error("系统繁忙，请稍后再试");
        }
    }

    @Operation(
        summary = "查询用户角色",
        description = "根据用户ID查询用户角色，返回 user 或 admin。"
    )
    @GetMapping("/role")
    public Result<String> getRole(
            @Parameter(description = "用户ID", required = true, example = "1")
            @RequestParam Long userId) {
        String role = authService.getUserRole(userId);
        if (role == null) {
            return Result.error("用户不存在");
        }
        return Result.success(role);
    }

    @Operation(
        summary = "校验是否为管理员",
        description = "检查指定用户ID是否拥有管理员权限。"
    )
    @GetMapping("/is-admin")
    public Result<Boolean> isAdmin(
            @Parameter(description = "用户ID", required = true, example = "1")
            @RequestParam Long userId) {
        return Result.success(authService.isAdmin(userId));
    }
}