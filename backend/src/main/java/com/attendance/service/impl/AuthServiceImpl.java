package com.attendance.service.impl;

import com.attendance.common.ResultCode;
import com.attendance.dto.LoginRequest;
import com.attendance.dto.LoginResponse;
import com.attendance.dto.RegisterRequest;
import com.attendance.entity.User;
import com.attendance.exception.BusinessException;
import com.attendance.mapper.UserMapper;
import com.attendance.security.JwtUtil;
import com.attendance.security.PasswordEncoder;
import com.attendance.security.TokenBlacklistService;
import com.attendance.service.AuthService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Override
    public LoginResponse login(LoginRequest request) {
        // 查询用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));

        if (user == null) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 检查用户状态
        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 生成Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // 构建响应
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getStudentId(),
                user.getRole(),
                user.getStatus()
        );

        return new LoginResponse(token, refreshToken, userInfo);
    }

    @Override
    public void register(RegisterRequest request) {
        if (!"STUDENT".equals(request.getRole())) {
            throw new BusinessException(403, "公开注册仅允许创建学生账号，教师和管理员账号请由管理员创建");
        }
        // 检查用户名是否存在
        User existUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));

        if (existUser != null) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
        }

        // 检查学号是否存在（如果是学生）
        if ("STUDENT".equals(request.getRole()) && request.getStudentId() != null) {
            User existStudent = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getStudentId, request.getStudentId()));
            if (existStudent != null) {
                throw new BusinessException("学号已存在");
            }
        }

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setStudentId(request.getStudentId());
        user.setRole(request.getRole());
        user.setStatus(1);

        userMapper.insert(user);
        log.info("用户注册成功: {}", request.getUsername());
    }

    @Override
    public String refreshToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        User user = userMapper.selectById(userId);

        if (user == null || user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        return jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
    }

    @Override
    public void logout(String token) {
        tokenBlacklistService.revoke(token, jwtUtil.getExpirationFromToken(token).getTime());
        log.info("用户登出: {}", jwtUtil.getUsernameFromToken(token));
    }
}
