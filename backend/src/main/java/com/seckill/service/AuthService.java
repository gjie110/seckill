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

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;

    /**
     * 用户登录，支持用户名/手机号/邮箱登录，验证密码并返回用户信息及角色。
     */
    public LoginResponse login(LoginRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
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
        if (user.getStatus() != null && user.getStatus() == 1) {
            log.warn("登录失败：账户已禁用 - {}", request.getUsername());
            throw new IllegalArgumentException("账户已被禁用，请联系管理员");
        }
        if (!request.getPassword().equals(user.getPassword())) {
            log.warn("登录失败：密码错误 - {}", request.getUsername());
            throw new IllegalArgumentException("用户名或密码错误");
        }
        SeckillUser updateUser = new SeckillUser();
        updateUser.setUserId(user.getUserId());
        updateUser.setLastLoginTime(new Date());
        userMapper.updateById(updateUser);
        String token = UUID.randomUUID().toString().replace("-", "");
        log.info("登录成功：用户={}, 角色={}", user.getUsername(), user.getRole());
        return new LoginResponse(user.getUserId(), user.getUsername(), user.getRole(), token);
    }

    /** 判断指定用户是否为管理员。 */
    public boolean isAdmin(Long userId) {
        if (userId == null) return false;
        SeckillUser user = userMapper.selectById(userId);
        return user != null && "admin".equals(user.getRole());
    }

    /** 获取指定用户的角色字符串。 */
    public String getUserRole(Long userId) {
        if (userId == null) return null;
        SeckillUser user = userMapper.selectById(userId);
        return user != null ? user.getRole() : null;
    }

    /** 查询所有用户列表（按创建时间升序）。 */
    public List<SeckillUser> listAllUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<SeckillUser>()
                .orderByAsc(SeckillUser::getCreateTime));
    }

    /** 分页查询用户列表，支持按用户名/手机号/邮箱关键词搜索。 */
    public Page<SeckillUser> listUsersPage(String keyword, Integer page, Integer size) {
        LambdaQueryWrapper<SeckillUser> wrapper = new LambdaQueryWrapper<SeckillUser>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            wrapper.and(w -> w.like(SeckillUser::getUsername, kw)
                    .or().like(SeckillUser::getPhone, kw)
                    .or().like(SeckillUser::getEmail, kw));
        }
        wrapper.orderByAsc(SeckillUser::getCreateTime);
        return userMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 管理员手动注册新用户，设置用户名、密码、角色。 */
    public void registerUser(String username, String password, String phone, String email, String role) {
        if (username == null || username.trim().isEmpty()) throw new IllegalArgumentException("用户名不能为空");
        if (password == null || password.trim().isEmpty()) throw new IllegalArgumentException("密码不能为空");
        SeckillUser existing = userMapper.selectByUsername(username.trim());
        if (existing != null) throw new IllegalArgumentException("用户名已存在");
        if (!"user".equals(role) && !"admin".equals(role)) role = "user";
        SeckillUser user = new SeckillUser();
        user.setUsername(username.trim());
        user.setPassword(password);
        user.setPhone(phone);
        user.setEmail(email);
        user.setRole(role);
        user.setStatus(0);
        user.setCreateTime(new Date());
        userMapper.insert(user);
        log.info("[管理员] 新增用户：username={}, role={}", username, role);
    }

    /** 启用或禁用用户账户（status=0正常，status=1禁用）。 */
    public void setUserStatus(Long userId, Integer status) {
        if (userId == null) return;
        SeckillUser user = userMapper.selectById(userId);
        if (user == null) return;
        user.setStatus(status);
        userMapper.updateById(user);
        log.info("[管理员] 设置用户状态：userId={}, status={}", userId, status);
    }

    /** 修改用户角色，在普通用户和管理员之间切换。 */
    public void setUserRole(Long userId, String role) {
        if (userId == null || role == null) return;
        SeckillUser user = userMapper.selectById(userId);
        if (user == null) return;
        user.setRole(role);
        userMapper.updateById(user);
        log.info("[管理员] 设置用户角色：userId={}, role={}", userId, role);
    }

    /** 删除指定用户。 */
    public void deleteUser(Long userId) {
        if (userId == null) return;
        SeckillUser user = userMapper.selectById(userId);
        if (user == null) return;
        userMapper.deleteById(userId);
        log.info("[管理员] 删除用户：userId={}, username={}", userId, user.getUsername());
    }

    /** 修改用户基本信息，不传的字段保持原值不变。 */
    public void updateUser(Long userId, String username, String phone, String email, String password) {
        if (userId == null) return;
        SeckillUser user = userMapper.selectById(userId);
        if (user == null) return;
        boolean changed = false;
        if (username != null && !username.trim().isEmpty() && !username.trim().equals(user.getUsername())) {
            SeckillUser exist = userMapper.selectByUsername(username.trim());
            if (exist != null && !exist.getUserId().equals(userId)) throw new IllegalArgumentException("用户名已被使用");
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
