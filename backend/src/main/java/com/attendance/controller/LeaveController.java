package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.entity.*;
import com.attendance.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/attendance/leaves")
public class LeaveController {
    @Autowired private LeaveRequestMapper leaveMapper;
    @Autowired private StudentCourseMapper studentCourseMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private AttendanceMapper attendanceMapper;
    @Autowired private com.attendance.service.AttendanceSessionService sessions;
    @Autowired private com.attendance.service.AttendanceAuditService audit;

    @PostMapping
    public Result<?> submit(HttpServletRequest request, @RequestParam Long courseId,
                            @RequestParam @DateTimeFormat(pattern="yyyy-MM-dd") LocalDate leaveDate,
                            @RequestParam String reason) {
        Long userId = (Long) request.getAttribute("userId");
        if (!"STUDENT".equals(request.getAttribute("role"))) return Result.error(403, "只有学生可以申请请假");
        Course course = courseMapper.selectById(courseId);
        if (course == null) return Result.error("课程不存在");
        Long enrolled = studentCourseMapper.selectCount(new LambdaQueryWrapper<StudentCourse>()
                .eq(StudentCourse::getStudentId, userId).eq(StudentCourse::getCourseId, courseId).eq(StudentCourse::getStatus, 1));
        if (enrolled == 0) return Result.error("该课程不在你的课程安排中");
        if (leaveDate.isBefore(LocalDate.now())) return Result.error("不能补交过去日期的请假申请");
        if (leaveDate.getDayOfWeek().getValue() != course.getWeekDay()) return Result.error("所选日期不是该课程上课日");
        Long exists = leaveMapper.selectCount(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getStudentId, userId).eq(LeaveRequest::getCourseId, courseId).eq(LeaveRequest::getLeaveDate, leaveDate));
        if (exists > 0) return Result.error("该课程当天已有请假申请，请勿重复提交");
        LeaveRequest item = new LeaveRequest();
        item.setStudentId(userId); item.setCourseId(courseId); item.setLeaveDate(leaveDate);
        item.setStartTime(leaveDate.atTime(course.getStartTime()));
        item.setEndTime(leaveDate.atTime(course.getEndTime()));
        if(!item.getEndTime().isAfter(item.getStartTime()))item.setEndTime(item.getEndTime().plusDays(1));
        if (reason == null || reason.trim().isEmpty() || reason.length()>500) return Result.error("请填写1至500字的请假原因");
        item.setReason(reason.trim()); item.setStatus("PENDING"); item.setCreatedAt(LocalDateTime.now());
        leaveMapper.insert(item);
        return Result.success("请假申请已提交，等待审批", item);
    }

    @GetMapping
    public Result<?> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        LambdaQueryWrapper<LeaveRequest> query = new LambdaQueryWrapper<>();
        if ("STUDENT".equals(role)) query.eq(LeaveRequest::getStudentId, userId);
        if ("TEACHER".equals(role)) {
            List<Long> ids = courseMapper.selectList(new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, userId))
                    .stream().map(Course::getId).collect(Collectors.toList());
            if (ids.isEmpty()) return Result.success(Collections.emptyList());
            query.in(LeaveRequest::getCourseId, ids);
        }
        query.orderByDesc(LeaveRequest::getCreatedAt);
        List<Map<String,Object>> result = new ArrayList<>();
        for (LeaveRequest item : leaveMapper.selectList(query)) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id", item.getId()); row.put("leaveDate", item.getLeaveDate()); row.put("reason", item.getReason());
            row.put("status", item.getStatus()); row.put("reviewComment", item.getReviewComment()); row.put("createdAt", item.getCreatedAt());
            Course c = courseMapper.selectById(item.getCourseId()); User u = userMapper.selectById(item.getStudentId());
            row.put("courseName", c == null ? "-" : c.getCourseName()); row.put("studentName", u == null ? "-" : u.getRealName());
            row.put("studentNumber", u == null ? "-" : u.getStudentId());
            User reviewer=item.getReviewerId()==null?null:userMapper.selectById(item.getReviewerId());
            row.put("reviewerName",reviewer==null?null:reviewer.getRealName()); row.put("reviewedAt",item.getReviewedAt());
            if(c!=null) {
                LocalDateTime start=item.getStartTime()==null?item.getLeaveDate().atTime(c.getStartTime()):item.getStartTime(), end=item.getEndTime()==null?item.getLeaveDate().atTime(c.getEndTime()):item.getEndTime();
                if(!end.isAfter(start)) end=end.plusDays(1);
                row.put("startTime",start); row.put("endTime",end); row.put("durationMinutes",java.time.Duration.between(start,end).toMinutes());
            }
            result.add(row);
        }
        return Result.success(result);
    }

    @PutMapping("/{id}/review")
    @Transactional
    public Result<?> review(HttpServletRequest request, @PathVariable Long id, @RequestParam boolean approved,
                            @RequestParam(required=false) String comment) {
        Long userId = (Long) request.getAttribute("userId"); String role = (String) request.getAttribute("role");
        LeaveRequest item = leaveMapper.selectById(id);
        if (item == null) return Result.error("请假申请不存在");
        Course course = courseMapper.selectById(item.getCourseId());
        if (!("ADMIN".equals(role) || ("TEACHER".equals(role) && course != null && userId.equals(course.getTeacherId()))))
            return Result.error(403, "无权审批该申请");
        sessions.lockCourse(item.getCourseId());
        item = leaveMapper.selectById(id);
        if (!"PENDING".equals(item.getStatus())) return Result.error("该申请已经审批，不能重复操作");
        if (!approved && (comment==null || comment.trim().isEmpty())) return Result.error("驳回时必须填写原因");
        if(comment!=null && comment.length()>500) return Result.error("审批意见不能超过500字");
        item.setStatus(approved ? "APPROVED" : "REJECTED"); item.setReviewerId(userId);
        item.setReviewComment(comment); item.setReviewedAt(LocalDateTime.now()); leaveMapper.updateById(item);
        audit.record("LEAVE_REVIEW",id,"PENDING",item.getStatus(),userId,comment);
        if (approved) {
            Attendance attendance = attendanceMapper.selectOne(new LambdaQueryWrapper<Attendance>()
                    .eq(Attendance::getCourseId, item.getCourseId()).eq(Attendance::getStudentId, item.getStudentId())
                    .eq(Attendance::getAttendanceDate, item.getLeaveDate()));
            if (attendance == null) { attendance = new Attendance(); attendance.setCourseId(item.getCourseId());
                attendance.setStudentId(item.getStudentId()); attendance.setAttendanceDate(item.getLeaveDate()); }
            // Preserve a real completed check-in instead of replacing its business result.
            if (attendance.getCheckInTime() != null) throw new com.attendance.exception.BusinessException("学生已签到，不能改为请假，请先处理考勤记录");
            attendance.setStatus("LEAVE");
            if (attendance.getId() == null) attendanceMapper.insert(attendance); else attendanceMapper.updateById(attendance);
        }
        return Result.success(approved ? "已批准请假" : "已驳回请假", null);
    }
}
