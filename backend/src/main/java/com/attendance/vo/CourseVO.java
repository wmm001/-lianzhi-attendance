package com.attendance.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 课程视图对象
 */
@Data
public class CourseVO {

    private Long id;
    private String courseName;
    private String courseCode;
    private Long teacherId;
    private String teacherName;
    private String classroom;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer weekDay;
    private String weekDayName;  // 星期一、星期二...
    private String semester;
    private Integer maxStudents;
    private Integer enrolledCount;  // 课程人数
    private Integer status;
    private LocalDateTime createdAt;
}
