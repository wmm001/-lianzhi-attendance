package com.attendance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("student_group")
public class StudentGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String groupName;
    private Long courseId;
    private Long leaderId;
    private LocalDateTime createdAt;
}
