package com.attendance.controller;

import com.attendance.common.PageResult;
import com.attendance.common.Result;
import com.attendance.dto.FaceRegisterRequest;
import com.attendance.dto.AdminUserRequest;
import com.attendance.exception.BusinessException;
import com.attendance.vo.UserImportVO;
import com.attendance.vo.UserVO;
import com.attendance.service.UserService;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static com.attendance.security.RequestAuthorization.*;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    /** 管理员新增学生或教师账号。 */
    @PostMapping
    public Result<UserVO> createUser(@Valid @RequestBody AdminUserRequest payload,
                                     HttpServletRequest request) {
        requireRole(request, "ADMIN");
        return Result.success("人员添加成功", userService.createManagedUser(payload));
    }

    /** 管理员维护学生或教师信息。 */
    @PutMapping("/{id}/admin")
    public Result<?> updateManagedUser(@PathVariable Long id,
                                        @Valid @RequestBody AdminUserRequest payload,
                                        HttpServletRequest request) {
        requireRole(request, "ADMIN");
        userService.updateManagedUser(id, payload);
        return Result.success("人员信息已更新", userService.getUserById(id));
    }

    /**
     * 管理员通过 Excel 批量导入学生或教师名单。
     * 存在任意失败行时整批回滚，返回逐行失败原因。
     */
    @PostMapping("/import")
    public Result<UserImportVO> importUsers(@RequestParam String role,
                                            @RequestParam("file") MultipartFile file,
                                            HttpServletRequest request) {
        requireRole(request, "ADMIN");
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的Excel文件");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsx")) {
            throw new BusinessException("仅支持 .xlsx 格式的Excel文件");
        }
        UserImportVO result = userService.batchImportUsers(role, file);
        String message = Boolean.TRUE.equals(result.getRolledBack())
                ? "导入未成功：存在" + result.getFailCount() + "条失败记录，整批未导入，请修正后重新上传"
                : "成功导入" + result.getSuccessCount() + "条" + ("TEACHER".equals(role) ? "教师" : "学生") + "记录";
        return Result.success(message, result);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/profile")
    public Result<UserVO> getCurrentUser(HttpServletRequest request) {
        Long userId = userId(request);
        UserVO user = userService.getCurrentUser(userId);
        return Result.success(user);
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/{id}")
    public Result<UserVO> getUserById(@PathVariable Long id, HttpServletRequest request) {
        requireSelfOrAdmin(request, id);
        UserVO user = userService.getUserById(id);
        return Result.success(user);
    }

    /**
     * 获取用户列表（分页）
     */
    @GetMapping
    public Result<PageResult<UserVO>> getUserList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        requireRole(request, "ADMIN");
        PageResult<UserVO> result = userService.getUserList(page, size, role, keyword);
        return Result.success(result);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/{id}")
    public Result<?> updateUser(@PathVariable Long id,
                                 @RequestParam(required = false) String realName,
                                 @RequestParam(required = false) String studentId,
                                 HttpServletRequest request) {
        requireSelfOrAdmin(request, id);
        userService.updateUser(id, realName, studentId);
        return Result.success("更新成功", null);
    }

    /**
     * 修改密码
     */
    @PutMapping("/{id}/password")
    public Result<?> changePassword(@PathVariable Long id,
                                     @RequestParam String oldPassword,
                                     @RequestParam String newPassword,
                                     HttpServletRequest request) {
        if (!userId(request).equals(id)) return Result.error(403, "只能修改当前账号的密码");
        userService.changePassword(id, oldPassword, newPassword);
        return Result.success("密码修改成功", null);
    }

    /**
     * 禁用/启用用户
     */
    @PutMapping("/{id}/status")
    public Result<?> updateUserStatus(@PathVariable Long id,
                                       @RequestParam Integer status,
                                       HttpServletRequest request) {
        requireRole(request, "ADMIN");
        userService.updateUserStatus(id, status);
        return Result.success("状态更新成功", null);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteUser(@PathVariable Long id, HttpServletRequest request) {
        requireRole(request, "ADMIN");
        userService.deleteUser(id);
        return Result.success("删除成功", null);
    }

    /**
     * 注册人脸
     */
    @PostMapping("/{id}/face")
    public Result<?> registerFace(@PathVariable Long id,
                                   @Valid @RequestBody FaceRegisterRequest payload,
                                   HttpServletRequest request) {
        requireSelfOrAdmin(request, id);
        return Result.error(410, "该接口已停用，请使用 /face/register 上传并注册真实人脸");
    }

    /**
     * 删除人脸
     */
    @DeleteMapping("/{id}/face")
    public Result<?> deleteFace(@PathVariable Long id, HttpServletRequest request) {
        requireSelfOrAdmin(request, id);
        return Result.error(410, "该接口已停用，请使用 /face/{userId} 删除人脸");
    }
}
