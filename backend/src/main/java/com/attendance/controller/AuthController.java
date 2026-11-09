package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.dto.LoginRequest;
import com.attendance.dto.LoginResponse;
import com.attendance.service.AuthService;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }

    /**
     * 刷新Token
     */
    @PostMapping("/refresh")
    public Result<String> refreshToken(@RequestParam String refreshToken) {
        String token = authService.refreshToken(refreshToken);
        return Result.success(token);
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public Result<?> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            authService.logout(token);
        }
        return Result.success();
    }
}
