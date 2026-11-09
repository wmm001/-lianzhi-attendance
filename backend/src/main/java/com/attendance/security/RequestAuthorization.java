package com.attendance.security;

import com.attendance.exception.BusinessException;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

/** Small controller-level authorization helpers for the custom JWT request attributes. */
public final class RequestAuthorization {
    private RequestAuthorization() {}

    public static Long userId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new BusinessException(401, "请先登录");
        return userId;
    }

    public static String role(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (role == null) throw new BusinessException(401, "请先登录");
        return role;
    }

    public static void requireRole(HttpServletRequest request, String... allowedRoles) {
        String currentRole = role(request);
        if (Arrays.stream(allowedRoles).noneMatch(currentRole::equals)) {
            throw new BusinessException(403, "无权执行此操作");
        }
    }

    public static void requireSelfOrAdmin(HttpServletRequest request, Long targetUserId) {
        if (!userId(request).equals(targetUserId) && !"ADMIN".equals(role(request))) {
            throw new BusinessException(403, "无权操作其他用户的数据");
        }
    }
}
