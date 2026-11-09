package com.attendance.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 人脸注册请求
 */
@Data
public class FaceRegisterRequest {

    @NotBlank(message = "人脸图片不能为空")
    private String faceImage;  // Base64编码的人脸图片
}