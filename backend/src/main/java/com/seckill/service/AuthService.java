package com.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seckill.dto.LoginRequest;
import com.seckill.dto.LoginResponse;
import com.seckill.entity.SeckillUser;
import com.seckill.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 认证服务
 * 处理用户登录、权限校验等认证相关逻辑
 * 核心功能：用户名密码验证、角色区分（user/admin）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;

    /**
     * 用户登录方法
     *
     * 功能描述：
     * 1. 根据用户名从数据库查询用户信息
     * 2. 验证用户是否存在
     * 3. 验证账户状态（是否被禁用）
     * 4. 验证密码是否正确（演示项目使用明文比较，实际项目应使用BCrypt加密）
     * 5. 登录成功后更新最后登录时间
     * 6. 返回用户基本信息和角色
     *
     * @param request 登录请求参数（用户名、密码）
     * @return 登录成功返回用户信息，失败抛出异常
     */
    public LoginResponse login(LoginRequest request) {
        // 参数校验
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }

        // 1. 查询用户信息（支持用户名、手机号、邮箱登录）
        LambdaQueryWrapper<SeckillUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(w -> w.eq(SeckillUser::getUsername, request.getUsername().trim())
                .or().eq(SeckillUser::getPhone, request.getUsername().trim())
                .or().eq(SeckillUser::getEmail, request.getUsername().trim()));
        List<SeckillUser> userList = userMapper.selectList(queryWrapper);
        SeckillUser user = (userList != null && !userList.isEmpty()) ? userList.get(0) : null;
        if (user == null) {
            log.warn("登录失败：用户不存在 - {}", request.getUsername());
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 2. 检查账户状态
        if (user.getStatus() != null && user.getStatus() == 1) {
            log.warn("登录失败：账户已禁用 - {}", request.getUsername());
            throw new IllegalArgumentException("账户已被禁用，请联系管理员");
        }

        // 3. 密码验证（演示使用明文比较，实际项目应使用 BCryptPasswordEncoder）
        if (!request.getPassword().equals(user.getPassword())) {
            log.warn("登录失败：密码错误 - {}", request.getUsername());
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 4. 更新最后登录时间
        SeckillUser updateUser = new SeckillUser();
        updateUser.setUserId(user.getUserId());
        updateUser.setLastLoginTime(new Date());
        userMapper.updateById(updateUser);

        // 5. 生成简单的token（实际项目应使用JWT）
        String token = UUID.randomUUID().toString().replace("-", "");

        log.info("登录成功：用户={}, 角色={}", user.getUsername(), user.getRole());

        // 6. 返回登录响应信息
        return new LoginResponse(
                user.getUserId(),
                user.getUsername(),
                user.getRole(),
                token
        );
    }

    /**
     * 校验用户是否为管理员
     *
     * @param userId 用户ID
     * @return true-管理员，false-普通用户
     */
    public boolean isAdmin(Long userId) {
        if (userId == null) return false;
        SeckillUser user = userMapper.selectById(userId);
        return user != null && "admin".equals(user.getRole());
    }

    /**
     * 获取用户角色
     *
     * @param userId 用户ID
     * @return 角色：user/admin，用户不存在返回null
     */
    public String getUserRole(Long userId) {
        if (userId == null) return null;
        SeckillUser user = userMapper.selectById(userId);
        return user != null ? user.getRole() : null;
    }

    /**
     * 查询所有用户（管理员用）
     */
    public List<SeckillUser> listAllUsers() {
        return userMapper.selectList(
            new LambdaQueryWrapper<SeckillUser>()
                .orderByAsc(SeckillUser::getCreateTime)
        );
    }

    public Page<SeckillUser> listUsersPage(String keyword, Integer page, Integer size) {
        LambdaQueryWrapper<SeckillUser> wrapper = new LambdaQueryWrapper<SeckillUser>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            wrapper.and(w -> w.like(SeckillUser::getUsername, kw)
                    .or().like(SeckillUser::getPhone, kw)
                    .or().like(SeckillUser::getEmail, kw));
        }
        wrapper.orderByAsc(SeckillUser::getCreateTime);
        Page<SeckillUser> pageRequest = new Page<>(page, size);
        return userMapper.selectPage(pageRequest, wrapper);
    }

    /**
     * 注册新用户（管理员用）
     */
    public void registerUser(String username, String password, String phone, String email, String role) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        // 检查用户名是否已存在
        SeckillUser existing = userMapper.selectByUsername(username.trim());
        if (existing != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (!"user".equals(role) && !"admin".equals(role)) {
            role = "user";
        }
        SeckillUser user = new SeckillUser();
        user.setUsername(username.trim());
        user.setPassword(password); // 明文存储（演示项目）
        user.setPhone(phone);
        user.setEmail(email);
        user.setRole(role);
        user.setStatus(0); // 正常
        user.setCreateTime(new Date());
        userMapper.insert(user);
        log.info("[管理员] 新增用户：username={}, role={}", username, role);
    }

    /**
     * 设置用户状态（启用/禁用）
     */
    public void setUserStatus(Long userId, Integer status) {
        if (userId == null) return;
        SeckillUser user = userMapper.selectById(userId);
        if (user == null) return;
        user.setStatus(status);
        userMapper.updateById(user);
        log.info("[管理员] 设置用户状态：userId={}, status={}", userId, status);
    }

    /**
     * 设置用户角色
     */
    public void setUserRole(Long userId, String role) {
        if (userId == null || role == null) return;
        SeckillUser user = userMapper.selectById(userId);
        if (user == null) return;
        user.setRole(role);
        userMapper.updateById(user);
        log.info("[管理员] 设置用户角色：userId={}, role={}", userId, role);
    }

    /**
     * 删除用户（物理删除）
     */
    public void deleteUser(Long userId) {
        if (userId == null) return;
        SeckillUser user = userMapper.selectById(userId);
        if (user == null) return;
        userMapper.deleteById(userId);
        log.info("[管理员] 删除用户：userId={}, username={}", userId, user.getUsername());
    }

    /**
     * 修改用户基本信息（用户名、手机号、邮箱、密码）
     * 不传的字段保持不变
     */
    public void updateUser(Long userId, String username, String phone, String email, String password) {
        if (userId == null) return;
        SeckillUser user = userMapper.selectById(userId);
        if (user == null) return;
        boolean changed = false;
        if (username != null && !username.trim().isEmpty() && !username.trim().equals(user.getUsername())) {
            // 检查新用户名是否被占用（排除自己）
            SeckillUser exist = userMapper.selectByUsername(username.trim());
            if (exist != null && !exist.getUserId().equals(userId)) {
                throw new IllegalArgumentException("用户名已被使用");
            }
            user.setUsername(username.trim());
            changed = true;
        }
        if (phone != null) {
            if ((phone.isEmpty() && user.getPhone() != null) || !phone.equals(user.getPhone())) {
                user.setPhone(phone.isEmpty() ? null : phone);
                changed = true;
            }
        }
        if (email != null) {
            if ((email.isEmpty() && user.getEmail() != null) || !email.equals(user.getEmail())) {
                user.setEmail(email.isEmpty() ? null : email);
                changed = true;
            }
        }
        if (password != null && !password.trim().isEmpty() && !password.equals(user.getPassword())) {
            user.setPassword(password);
            changed = true;
        }
        if (changed) {
            userMapper.updateById(user);
            log.info("[管理员] 修改用户信息：userId={}", userId);
        }
    }
}
