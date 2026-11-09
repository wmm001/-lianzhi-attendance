package com.attendance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String realName;
    private String className;

    private String studentId;

    private String role;  // STUDENT, TEACHER, ADMIN

    private String faceFeatures;  // JSON格式的人脸特征向量

    private String faceImagePath;

    private Boolean faceReenrollAllowed;

    private Integer status;  // 1:正常 0:禁用

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
