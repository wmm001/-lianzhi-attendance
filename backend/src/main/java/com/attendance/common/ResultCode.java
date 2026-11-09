package com.attendance.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应状态码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    ERROR(500, "操作失败"),

    // 认证相关 4xx
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "无权限访问"),
    TOKEN_EXPIRED(401, "Token已过期"),
    TOKEN_INVALID(401, "Token无效"),
    USERNAME_OR_PASSWORD_ERROR(401, "用户名或密码错误"),
    USER_NOT_FOUND(404, "用户不存在"),
    USER_DISABLED(403, "用户已被禁用"),

    // 业务相关 5xx
    USER_ALREADY_EXISTS(400, "用户已存在"),
    PASSWORD_ERROR(400, "密码错误"),
    OLD_PASSWORD_ERROR(400, "旧密码错误"),
    COURSE_NOT_FOUND(404, "课程不存在"),
    ATTENDANCE_NOT_FOUND(404, "考勤记录不存在"),
    ALREADY_CHECKED_IN(400, "今日已签到"),
    NOT_CHECKED_IN(400, "尚未签到"),
    FACE_NOT_REGISTERED(400, "未注册人脸信息"),
    FACE_RECOGNITION_FAILED(400, "人脸识别失败"),
    LIVENESS_DETECTION_FAILED(400, "活体检测失败"),
    MULTIPLE_FACES_DETECTED(400, "检测到多张人脸"),
    NO_FACE_DETECTED(400, "未检测到人脸");

    private final Integer code;
    private final String message;
}
