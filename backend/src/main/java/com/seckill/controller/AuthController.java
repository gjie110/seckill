package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.dto.LoginRequest;
import com.seckill.dto.LoginResponse;
import com.seckill.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
        description = "用户通过用户名/手机号/邮箱和密码登录系统，系统根据用户角色返回不同的登录信息。" +
                      "管理员角色(role=admin)登录后跳转管理员控制台，普通用户(role=user)跳转演出列表页面。" +
                      "支持三种登录方式：用户名、手机号、邮箱。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "登录成功，返回用户信息和角色"),
        @ApiResponse(responseCode = "400", description = "用户名或密码错误"),
        @ApiResponse(responseCode = "403", description = "账户已被禁用")
    })
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
        description = "根据用户ID查询用户角色，返回 'user' 或 'admin'。" +
                      "前端可据此判断是否有权限访问管理员页面。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功，返回用户角色"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
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
        description = "检查指定用户ID是否拥有管理员权限。" +
                      "返回 true 表示是管理员，false 表示是普通用户。"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查询成功，返回是否为管理员")
    })
    @GetMapping("/is-admin")
    public Result<Boolean> isAdmin(
            @Parameter(description = "用户ID", required = true, example = "1")
            @RequestParam Long userId) {
        return Result.success(authService.isAdmin(userId));
    }
}