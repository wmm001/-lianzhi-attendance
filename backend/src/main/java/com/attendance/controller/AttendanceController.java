package com.attendance.controller;

import com.attendance.common.PageResult;
import com.attendance.common.Result;
import com.attendance.dto.AttendanceRequest;
import com.attendance.vo.AttendanceVO;
import com.attendance.service.AttendanceService;
import com.attendance.entity.Attendance;
import com.attendance.entity.Course;
import com.attendance.exception.BusinessException;
import com.attendance.mapper.AttendanceMapper;
import com.attendance.mapper.CourseMapper;
import com.attendance.service.StudentGroupAccessService;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static com.attendance.security.RequestAuthorization.*;

/**
 * 考勤控制器
 */
@RestController
@RequestMapping("/attendance")
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private StudentGroupAccessService groupAccessService;

    private static final Set<String> ALLOWED_STATUSES = Set.of("PRESENT", "LATE", "ABSENT", "LEAVE");

    /**
     * 学生签到
     */
    @PostMapping("/check-in")
    public Result<AttendanceVO> checkIn(HttpServletRequest request,
                                          @Valid @RequestBody AttendanceRequest attendanceRequest) {
        requireRole(request, "STUDENT");
        Long studentId = userId(request);
        if (attendanceRequest.getTargetStudentId() != null && !studentId.equals(attendanceRequest.getTargetStudentId())) {
            if (!groupAccessService.canLeaderManage(studentId, attendanceRequest.getTargetStudentId(), attendanceRequest.getCourseId())) {
                throw new BusinessException(403, "只能为本组成员签到");
            }
            studentId = attendanceRequest.getTargetStudentId();
        }
        AttendanceVO attendance = attendanceService.checkIn(studentId, attendanceRequest);
        return Result.success("签到成功", attendance);
    }

    /**
     * 学生签退
     */
    @PostMapping("/check-out")
    public Result<?> checkOut(HttpServletRequest request,
                               @RequestParam Long attendanceId) {
        requireRole(request, "STUDENT");
        Long studentId = userId(request);
        attendanceService.checkOut(studentId, attendanceId);
        return Result.success("签退成功", null);
    }

    /**
     * 获取我的考勤记录
     */
    @GetMapping("/my")
    public Result<PageResult<AttendanceVO>> getMyAttendance(
            HttpServletRequest request,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        requireRole(request, "STUDENT");
        Long studentId = userId(request);
        PageResult<AttendanceVO> result = attendanceService.getStudentAttendance(
                studentId, courseId, startDate, endDate, page, size);
        return Result.success(result);
    }

    /**
     * 获取当前角色可见的考勤记录
     */
    @GetMapping("/records")
    public Result<PageResult<AttendanceVO>> getAttendanceRecords(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Long currentUserId = userId(request);
        String currentRole = role(request);
        return Result.success(attendanceService.getAttendanceRecords(currentUserId, currentRole, page, size));
    }

    /**
     * 获取课程考勤记录（教师）
     */
    @GetMapping("/course/{courseId}")
    public Result<PageResult<AttendanceVO>> getCourseAttendance(
            @PathVariable Long courseId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        requireCourseManager(request, courseId);
        PageResult<AttendanceVO> result = attendanceService.getCourseAttendance(
                courseId, date, status, page, size);
        return Result.success(result);
    }

    /**
     * 获取考勤详情
     */
    @GetMapping("/{id}")
    public Result<AttendanceVO> getAttendanceById(@PathVariable Long id, HttpServletRequest request) {
        requireAttendanceAccess(request, id, false);
        AttendanceVO attendance = attendanceService.getAttendanceById(id);
        return Result.success(attendance);
    }

    /**
     * 修改考勤状态（教师）
     */
    @PutMapping("/{id}")
    public Result<?> updateAttendanceStatus(
            @PathVariable Long id,
            @RequestParam String status,
            HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        requireAttendanceAccess(request, id, true);
        if (!ALLOWED_STATUSES.contains(status)) return Result.error(400, "无效的考勤状态");
        attendanceService.updateAttendanceStatus(id, status, operatorId);
        return Result.success("修改成功", null);
    }

    /**
     * 获取考勤统计
     */
    @GetMapping("/statistics/{courseId}")
    public Result<Map<String, Object>> getAttendanceStatistics(@PathVariable Long courseId,
                                                                HttpServletRequest request) {
        requireCourseManager(request, courseId);
        Map<String, Object> statistics = attendanceService.getAttendanceStatistics(courseId);
        return Result.success(statistics);
    }

    /**
     * 导出考勤记录
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportAttendance(
            @RequestParam Long courseId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            HttpServletRequest request) {
        requireCourseManager(request, courseId);
        byte[] workbook = attendanceService.exportAttendance(courseId, startDate, endDate);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=attendance-" + courseId + ".xlsx")
                .body(workbook);
    }

    private void requireCourseManager(HttpServletRequest request, Long courseId) {
        requireRole(request, "TEACHER", "ADMIN");
        Course course = courseMapper.selectById(courseId);
        if (course == null) throw new BusinessException(404, "课程不存在");
        if ("TEACHER".equals(role(request)) && !userId(request).equals(course.getTeacherId())) {
            throw new BusinessException(403, "只能查看和管理自己课程的考勤");
        }
    }

    private void requireAttendanceAccess(HttpServletRequest request, Long attendanceId, boolean managerOnly) {
        Attendance attendance = attendanceMapper.selectById(attendanceId);
        if (attendance == null) throw new BusinessException(404, "考勤记录不存在");
        if (!managerOnly && "STUDENT".equals(role(request)) && userId(request).equals(attendance.getStudentId())) return;
        requireCourseManager(request, attendance.getCourseId());
    }
}
