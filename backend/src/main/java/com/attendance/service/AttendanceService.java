package com.attendance.service;

import com.attendance.common.PageResult;
import com.attendance.dto.AttendanceRequest;
import com.attendance.vo.AttendanceVO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 考勤服务
 */
public interface AttendanceService {

    /**
     * 学生签到
     */
    AttendanceVO checkIn(Long studentId, AttendanceRequest request);

    /**
     * 学生签退
     */
    void checkOut(Long studentId, Long attendanceId);

    /**
     * 获取学生考勤记录
     */
    PageResult<AttendanceVO> getStudentAttendance(Long studentId, Long courseId,
                                                   LocalDate startDate, LocalDate endDate,
                                                   Integer page, Integer size);

    /**
     * 获取课程考勤记录
     */
    PageResult<AttendanceVO> getCourseAttendance(Long courseId, LocalDate date,
                                                   String status, Integer page, Integer size);

    /**
     * 按当前用户角色获取考勤记录
     */
    PageResult<AttendanceVO> getAttendanceRecords(Long userId, String role,
                                                   Integer page, Integer size);

    /**
     * 获取考勤详情
     */
    AttendanceVO getAttendanceById(Long id);

    /**
     * 修改考勤状态
     */
    void updateAttendanceStatus(Long id, String status, Long operatorId);

    /**
     * 获取考勤统计
     */
    Map<String, Object> getAttendanceStatistics(Long courseId);

    /**
     * 导出考勤记录
     */
    byte[] exportAttendance(Long courseId, LocalDate startDate, LocalDate endDate);
}
