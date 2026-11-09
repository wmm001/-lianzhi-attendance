package com.attendance.service;

import com.attendance.dto.LoginRequest;
import com.attendance.dto.LoginResponse;
import com.attendance.dto.RegisterRequest;

/**
 * 认证服务
 */
public interface AuthService {

    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);

    /**
     * 用户注册
     */
    void register(RegisterRequest request);

    /**
     * 刷新Token
     */
    String refreshToken(String refreshToken);

    /**
     * 用户登出
     */
    void logout(String token);
}