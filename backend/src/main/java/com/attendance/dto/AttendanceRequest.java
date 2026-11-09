package com.attendance.dto;

import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * 考勤请求
 */
@Data
public class AttendanceRequest {

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    private Long targetStudentId; // 组长代为组员签到时使用

    private String faceImage;  // Base64编码的人脸图片

    private String location;   // 签到位置

    private String deviceInfo; // 设备信息
}
