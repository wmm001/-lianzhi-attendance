package com.attendance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程学生关系实体
 */
@Data
@TableName("student_course")
public class StudentCourse {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    private Long courseId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime enrollDate;

    private Integer status;  // 1:有效 0:停用
}
