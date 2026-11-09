package com.attendance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤记录实体
 */
@Data
@TableName("attendance")
public class Attendance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private Long studentId;

    private LocalDate attendanceDate;

    private String status;  // PRESENT, ABSENT, LATE, LEAVE

    private LocalDateTime checkInTime;

    private LocalDateTime checkOutTime;

    private String faceImage;

    private BigDecimal confidence;

    private Integer isLiveness;  // 0:未检测 1:通过

    private String location;

    private String deviceInfo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}