package com.attendance.security;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码加密工具类（使用BCrypt）
 */
@Component
public class PasswordEncoder {

    private static final int BCRYPT_ROUNDS = 10;

    /**
     * 加密密码
     */
    public String encode(String rawPassword) {
        return org.springframework.security.crypto.bcrypt.BCrypt.hashpw(rawPassword, org.springframework.security.crypto.bcrypt.BCrypt.gensalt(BCRYPT_ROUNDS));
    }

    /**
     * 验证密码
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        return org.springframework.security.crypto.bcrypt.BCrypt.checkpw(rawPassword, encodedPassword);
    }
}