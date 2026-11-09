package com.attendance.service;

import com.attendance.common.PageResult;
import com.attendance.vo.UserImportVO;
import com.attendance.vo.UserVO;
import com.attendance.dto.AdminUserRequest;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户服务
 */
public interface UserService {

    /**
     * 根据ID获取用户信息
     */
    UserVO getUserById(Long id);

    /**
     * 获取当前登录用户信息
     */
    UserVO getCurrentUser(Long userId);

    /**
     * 分页查询用户列表
     */
    PageResult<UserVO> getUserList(Integer page, Integer size, String role, String keyword);

    /**
     * 更新用户信息
     */
    void updateUser(Long id, String realName, String studentId);

    /**
     * 修改密码
     */
    void changePassword(Long id, String oldPassword, String newPassword);

    /**
     * 禁用/启用用户
     */
    void updateUserStatus(Long id, Integer status);

    /**
     * 删除用户
     */
    void deleteUser(Long id);

    UserVO createManagedUser(AdminUserRequest request);

    void updateManagedUser(Long id, AdminUserRequest request);

    /** 管理员通过 Excel 批量导入学生或教师，存在任意失败时整批回滚不入库。 */
    UserImportVO batchImportUsers(String role, MultipartFile file);

}
