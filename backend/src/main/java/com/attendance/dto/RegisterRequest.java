package com.attendance.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import lombok.Data;

/**
 * 注册请求
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "用户名必须是4-20位的字母、数字或下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d)[a-zA-Z\\d@$!%*?&]{8,20}$",
             message = "密码必须包含字母和数字，长度8-20位")
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    @NotBlank(message = "学号不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,20}$", message = "学号须为3-20位字母、数字、下划线或短横线")
    private String studentId;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^STUDENT$", message = "公开注册仅允许创建学生账号")
    private String role;  // STUDENT, TEACHER, ADMIN
}
