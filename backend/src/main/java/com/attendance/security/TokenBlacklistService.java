package com.attendance.security;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps logged-out access tokens invalid until their original expiration time. */
@Service
public class TokenBlacklistService {
    private final Map<String, Long> revokedTokens = new ConcurrentHashMap<>();

    public void revoke(String token, long expiresAt) {
        revokedTokens.put(fingerprint(token), expiresAt);
    }

    public boolean isRevoked(String token) {
        long now = System.currentTimeMillis();
        revokedTokens.entrySet().removeIf(entry -> entry.getValue() < now);
        Long expiresAt = revokedTokens.get(fingerprint(token));
        return expiresAt != null && expiresAt >= now;
    }

    private String fingerprint(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception error) {
            throw new IllegalStateException("无法处理登录令牌", error);
        }
    }
}
