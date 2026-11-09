package com.attendance.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * 课程DTO
 */
@Data
public class CourseDTO {

    private Long id;

    private Long teacherId;

    @NotBlank(message = "课程名称不能为空")
    private String courseName;

    @NotBlank(message = "课程代码不能为空")
    private String courseCode;

    private String classroom;

    @NotNull(message = "开始时间不能为空")
    private String startTime;

    @NotNull(message = "结束时间不能为空")
    private String endTime;

    @NotNull(message = "上课星期不能为空")
    private Integer weekDay;

    private String semester;

    private Integer maxStudents;
}
