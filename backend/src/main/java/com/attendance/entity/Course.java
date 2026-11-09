package com.attendance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 课程实体
 */
@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String courseName;

    private String courseCode;

    private Long teacherId;

    private String classroom;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer weekDay;  // 1-7

    private String semester;

    private Integer maxStudents;

    private Integer status;  // 1:正常 0:停用

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}