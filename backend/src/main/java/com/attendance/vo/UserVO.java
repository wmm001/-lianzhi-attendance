package com.attendance.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户视图对象
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String realName;
    private String className;
    private String studentId;
    private String role;
    private Integer status;
    private Boolean faceRegistered;
    private LocalDateTime createdAt;
}