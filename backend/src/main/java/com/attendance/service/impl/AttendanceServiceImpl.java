package com.attendance.service.impl;

import com.attendance.common.PageResult;
import com.attendance.common.ResultCode;
import com.attendance.dto.AttendanceRequest;
import com.attendance.entity.Attendance;
import com.attendance.entity.Course;
import com.attendance.entity.User;
import com.attendance.exception.BusinessException;
import com.attendance.mapper.AttendanceMapper;
import com.attendance.mapper.CourseMapper;
import com.attendance.mapper.UserMapper;
import com.attendance.mapper.StudentCourseMapper;
import com.attendance.mapper.LeaveRequestMapper;
import com.attendance.entity.LeaveRequest;
import com.attendance.entity.StudentCourse;
import com.attendance.service.FaceVerificationProofService;
import com.attendance.service.AttendanceSettlementService;
import com.attendance.service.AttendanceService;
import com.attendance.vo.AttendanceVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class AttendanceServiceImpl implements AttendanceService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String[] WEEK_NAMES = {"", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
    private static final String UNLIMITED_TEST_COURSE_CODE = "TEST-UNLIMITED";

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StudentCourseMapper studentCourseMapper;

    @Autowired
    private LeaveRequestMapper leaveRequestMapper;

    @Autowired
    private FaceVerificationProofService proofService;

    @Autowired
    private AttendanceSettlementService settlementService;
    @Autowired private com.attendance.service.AttendanceReportService reports;
    @Autowired private com.attendance.service.AttendanceAuditService audit;

    @Autowired private com.attendance.service.AttendanceSessionService sessions;

    @org.springframework.beans.factory.annotation.Value("${attendance.check-in-before-minutes:15}")
    private long checkInBeforeMinutes;

    @org.springframework.beans.factory.annotation.Value("${attendance.check-in-after-minutes:30}")
    private long checkInAfterMinutes;

    @org.springframework.beans.factory.annotation.Value("${attendance.late-grace-minutes:10}")
    private long lateGraceMinutes;

    private static final Map<String, String> STATUS_NAMES = new HashMap<>();
    static {
        STATUS_NAMES.put("PRESENT", "正常");
        STATUS_NAMES.put("ABSENT", "缺勤");
        STATUS_NAMES.put("LATE", "迟到");
        STATUS_NAMES.put("LEAVE", "请假");
    }

    @Override
    @Transactional
    public AttendanceVO checkIn(Long studentId, AttendanceRequest request) {
        sessions.lockCourse(request.getCourseId());
        Course course = courseMapper.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(ResultCode.COURSE_NOT_FOUND);
        }
        if (course.getStatus() == null || course.getStatus() != 1) {
            throw new BusinessException("该课程已停用，不能签到");
        }
        Long approvedLeave = leaveRequestMapper.selectCount(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getStudentId, studentId)
                .eq(LeaveRequest::getCourseId, request.getCourseId())
                .eq(LeaveRequest::getLeaveDate, LocalDate.now())
                .eq(LeaveRequest::getStatus, "APPROVED"));
        if (approvedLeave > 0) throw new BusinessException("该学生今日已请假，无需考勤");
        StudentCourse enrollment = studentCourseMapper.selectOne(new LambdaQueryWrapper<StudentCourse>()
                .eq(StudentCourse::getStudentId, studentId)
                .eq(StudentCourse::getCourseId, request.getCourseId())
                .eq(StudentCourse::getStatus, 1));
        if (enrollment == null) {
            throw new BusinessException("该课程不在你的课程安排中，不能签到");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean unlimitedTestCourse = UNLIMITED_TEST_COURSE_CODE.equals(course.getCourseCode());
        Map<String,Object> session = sessions.find(course.getId(), now.toLocalDate());
        unlimitedTestCourse = unlimitedTestCourse && session == null;
        if (session != null && !sessions.contains(course.getId(),now.toLocalDate(),studentId)) throw new BusinessException("你不在本次签到名单中，请联系教师");
        if (session != null && !now.isBefore(sessions.time(session,"ends_at"))) throw new BusinessException("签到已经结束");
        if (!unlimitedTestCourse && session == null) {
            if (course.getWeekDay() == null || course.getWeekDay() != now.getDayOfWeek().getValue()) {
                String scheduledDay = course.getWeekDay() != null && course.getWeekDay() >= 1 && course.getWeekDay() <= 7
                        ? WEEK_NAMES[course.getWeekDay()] : "未知";
                String today = WEEK_NAMES[now.getDayOfWeek().getValue()];
                throw new BusinessException("无法签到：课程“" + course.getCourseName() + "”安排在" + scheduledDay
                        + "，今天是" + today + "（" + now.toLocalDate() + "）。请选择今天正在进行的课程后重试。");
            }
            LocalTime openTime = course.getStartTime().minusMinutes(checkInBeforeMinutes);
            LocalTime closeTime = course.getEndTime().plusMinutes(checkInAfterMinutes);
            if (now.toLocalTime().isBefore(openTime) || now.toLocalTime().isAfter(closeTime)) {
                String reason;
                if (now.toLocalTime().isBefore(openTime)) {
                    long minutes = Math.max(1, Duration.between(now.toLocalTime(), openTime).toMinutes());
                    reason = "签到尚未开放，约 " + minutes + " 分钟后开放";
                } else {
                    reason = "本节课签到已经结束";
                }
                throw new BusinessException("无法签到：" + reason + "。课程“" + course.getCourseName() + "”上课时间为 "
                        + course.getStartTime().format(TIME_FORMAT) + "–" + course.getEndTime().format(TIME_FORMAT)
                        + "，允许签到时间为 " + openTime.format(TIME_FORMAT) + "–" + closeTime.format(TIME_FORMAT)
                        + "，当前时间为 " + now.format(DATE_TIME_FORMAT) + "。请在允许时间内重试，或选择其他正在进行的课程。");
            }
        }
        
        // 检查是否已签到
        Attendance todayAttendance = attendanceMapper.getTodayAttendance(studentId, request.getCourseId());
        if (!unlimitedTestCourse && todayAttendance != null && todayAttendance.getCheckInTime() != null) {
            throw new BusinessException(ResultCode.ALREADY_CHECKED_IN);
        }
        if (!unlimitedTestCourse && todayAttendance != null && ("LEAVE".equals(todayAttendance.getStatus()) || "ABSENT".equals(todayAttendance.getStatus()))) {
            throw new BusinessException("本次考勤已结算为" + STATUS_NAMES.get(todayAttendance.getStatus()) + "，请联系教师处理");
        }
        if (!proofService.consume(studentId, request.getCourseId())) {
            throw new BusinessException("请先完成人脸识别；识别结果仅在2分钟内且只能使用一次");
        }

        if (unlimitedTestCourse && todayAttendance != null) {
            todayAttendance.setAttendanceDate(LocalDate.now());
            todayAttendance.setCheckInTime(now);
            todayAttendance.setCheckOutTime(null);
            todayAttendance.setStatus("PRESENT");
            todayAttendance.setLocation(request.getLocation());
            todayAttendance.setFaceImage(request.getFaceImage());
            todayAttendance.setIsLiveness(1);
            attendanceMapper.updateById(todayAttendance);
            log.info("学生重复测试签到成功: studentId={}, courseId={}", studentId, request.getCourseId());
            return convertToVO(todayAttendance);
        }
        
        Attendance attendance = new Attendance();
        attendance.setStudentId(studentId);
        attendance.setCourseId(request.getCourseId());
        attendance.setAttendanceDate(LocalDate.now());
        attendance.setCheckInTime(now);
        attendance.setStatus("PRESENT");
        attendance.setLocation(request.getLocation());
        attendance.setFaceImage(request.getFaceImage());
        attendance.setIsLiveness(1);
        
        // 判断是否迟到
        if ((session != null && session.get("late_at") != null && now.isAfter(sessions.time(session,"late_at"))) || (session == null && !unlimitedTestCourse && now.toLocalTime().isAfter(course.getStartTime().plusMinutes(lateGraceMinutes)))) {
            attendance.setStatus("LATE");
        }
        
        attendanceMapper.insert(attendance);
        log.info("学生签到成功: studentId={}, courseId={}", studentId, request.getCourseId());
        
        return convertToVO(attendance);
    }

    @Override
    @Transactional
    public void checkOut(Long studentId, Long attendanceId) {
        Attendance attendance = attendanceMapper.selectById(attendanceId);
        if (attendance == null) {
            throw new BusinessException(ResultCode.ATTENDANCE_NOT_FOUND);
        }
        
        if (!attendance.getStudentId().equals(studentId)) {
            throw new BusinessException(ResultCode.ERROR);
        }
        
        if (attendance.getCheckInTime() == null) {
            throw new BusinessException(ResultCode.NOT_CHECKED_IN);
        }
        
        attendance.setCheckOutTime(LocalDateTime.now());
        attendanceMapper.updateById(attendance);
        log.info("学生签退成功: studentId={}, attendanceId={}", studentId, attendanceId);
    }

    @Override
    public PageResult<AttendanceVO> getStudentAttendance(Long studentId, Long courseId,
                                                         LocalDate startDate, LocalDate endDate,
                                                         Integer page, Integer size) {
        Page<Attendance> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Attendance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Attendance::getStudentId, studentId);
        
        if (courseId != null) {
            wrapper.eq(Attendance::getCourseId, courseId);
        }
        if (startDate != null) {
            wrapper.ge(Attendance::getAttendanceDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(Attendance::getAttendanceDate, endDate);
        }
        wrapper.orderByDesc(Attendance::getAttendanceDate);
        
        Page<Attendance> attendancePage = attendanceMapper.selectPage(pageParam, wrapper);
        List<AttendanceVO> voList = attendancePage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        
        return PageResult.of(attendancePage.getTotal(), voList, (long) page, (long) size);
    }

    @Override
    public PageResult<AttendanceVO> getCourseAttendance(Long courseId, LocalDate date,
                                                         String status, Integer page, Integer size) {
        Page<Attendance> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Attendance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Attendance::getCourseId, courseId);
        
        if (date != null) {
            wrapper.eq(Attendance::getAttendanceDate, date);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Attendance::getStatus, status);
        }
        wrapper.orderByDesc(Attendance::getAttendanceDate);
        
        Page<Attendance> attendancePage = attendanceMapper.selectPage(pageParam, wrapper);
        List<AttendanceVO> voList = attendancePage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        
        return PageResult.of(attendancePage.getTotal(), voList, (long) page, (long) size);
    }

    @Override
    public PageResult<AttendanceVO> getAttendanceRecords(Long userId, String role,
                                                          Integer page, Integer size) {
        Page<Attendance> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Attendance> wrapper = new LambdaQueryWrapper<>();

        if ("STUDENT".equals(role)) {
            wrapper.eq(Attendance::getStudentId, userId);
        } else if ("TEACHER".equals(role)) {
            List<Long> courseIds = courseMapper.selectList(
                            new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, userId))
                    .stream()
                    .map(Course::getId)
                    .collect(Collectors.toList());
            if (courseIds.isEmpty()) {
                return PageResult.of(0L, java.util.Collections.emptyList(),
                        (long) page, (long) size);
            }
            wrapper.in(Attendance::getCourseId, courseIds);
        }

        wrapper.orderByDesc(Attendance::getAttendanceDate)
                .orderByDesc(Attendance::getCheckInTime);

        Page<Attendance> attendancePage = attendanceMapper.selectPage(pageParam, wrapper);
        List<AttendanceVO> voList = attendancePage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return PageResult.of(attendancePage.getTotal(), voList, (long) page, (long) size);
    }

    @Override
    public AttendanceVO getAttendanceById(Long id) {
        Attendance attendance = attendanceMapper.selectById(id);
        if (attendance == null) {
            throw new BusinessException(ResultCode.ATTENDANCE_NOT_FOUND);
        }
        return convertToVO(attendance);
    }

    @Override
    @Transactional
    public void updateAttendanceStatus(Long id, String status, Long operatorId) {
        Attendance attendance = attendanceMapper.selectById(id);
        if (attendance == null) {
            throw new BusinessException(ResultCode.ATTENDANCE_NOT_FOUND);
        }
        
        sessions.lockCourse(attendance.getCourseId());
        attendance = attendanceMapper.selectById(id);
        audit.record("ATTENDANCE_STATUS",id,attendance.getStatus(),status,operatorId,"教师手动调整考勤");
        attendance.setStatus(status);
        attendanceMapper.updateById(attendance);
        log.info("更新考勤状态成功: id={}, status={}", id, status);
    }

    @Override
    public Map<String, Object> getAttendanceStatistics(Long courseId) {
        Map<String,Object> result=reports.report(courseId,null,null,null,null);
        result.remove("records"); return result;
    }

    @Override
    public byte[] exportAttendance(Long courseId,LocalDate startDate,LocalDate endDate) {
        return reports.export(courseId,startDate,endDate,null,null,null);
    }

    private AttendanceVO convertToVO(Attendance attendance) {
        AttendanceVO vo = new AttendanceVO();
        BeanUtils.copyProperties(attendance, vo);
        
        // 设置状态名称
        vo.setStatusName(STATUS_NAMES.getOrDefault(attendance.getStatus(), attendance.getStatus()));
        
        // 设置课程名称
        if (attendance.getCourseId() != null) {
            Course course = courseMapper.selectById(attendance.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getCourseName());
                vo.setCourseCode(course.getCourseCode());
                vo.setScheduledStartTime(course.getStartTime());
                vo.setScheduledEndTime(course.getEndTime());
            }
        }
        
        // 设置学生姓名
        if (attendance.getStudentId() != null) {
            User student = userMapper.selectById(attendance.getStudentId());
            if (student != null) {
                vo.setStudentName(student.getRealName());
                vo.setStudentNumber(student.getStudentId());
            }
        }
        
        return vo;
    }
}
