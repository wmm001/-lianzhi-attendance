package com.attendance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 考勤视图对象
 */
@Data
public class AttendanceVO {

    private Long id;
    private Long courseId;
    private String courseName;
    private String courseCode;
    private LocalTime scheduledStartTime;
    private LocalTime scheduledEndTime;
    private Long studentId;
    private String studentName;
    private String studentNumber;
    private LocalDate attendanceDate;
    private String status;  // PRESENT, ABSENT, LATE, LEAVE
    private String statusName;  // 出勤、缺勤、迟到、请假
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private String faceImage;
    private BigDecimal confidence;
    private Integer isLiveness;
    private LocalDateTime createdAt;
}
