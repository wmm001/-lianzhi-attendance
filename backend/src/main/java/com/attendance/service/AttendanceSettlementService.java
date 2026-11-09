package com.attendance.service;

import com.attendance.entity.Attendance;
import com.attendance.entity.Course;
import com.attendance.entity.StudentCourse;
import com.attendance.mapper.AttendanceMapper;
import com.attendance.mapper.CourseMapper;
import com.attendance.mapper.StudentCourseMapper;
import com.attendance.mapper.LeaveRequestMapper;
import com.attendance.entity.LeaveRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AttendanceSettlementService {
    @Autowired private CourseMapper courseMapper;
    @Autowired private AttendanceSessionService sessions;
    @Autowired private StudentCourseMapper studentCourseMapper;
    @Autowired private AttendanceMapper attendanceMapper;
    @Autowired private LeaveRequestMapper leaveRequestMapper;
    @Value("${attendance.check-in-after-minutes:30}") private long afterMinutes;

    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional
    public void scheduledSettle() { settleDate(LocalDate.now()); }

    @Transactional
    public void settleDate(LocalDate date) {
        settleDate(date,null);
    }

    @Transactional
    public void settleDate(LocalDate date,Long courseId) {
        LambdaQueryWrapper<Course> query=new LambdaQueryWrapper<Course>()
                .eq(Course::getStatus, 1).eq(Course::getWeekDay, date.getDayOfWeek().getValue());
        if(courseId!=null)query.eq(Course::getId,courseId);
        List<Course> courses = courseMapper.selectList(query);
        LocalDateTime now = LocalDateTime.now();
        for (Course course : courses) {
            sessions.lockCourse(course.getId());
            if ("TEST-UNLIMITED".equals(course.getCourseCode()) || sessions.find(course.getId(),date)!=null) continue;
            LocalDateTime deadline = LocalDateTime.of(date, course.getEndTime()).plusMinutes(afterMinutes);
            if (now.isBefore(deadline)) continue;
            List<StudentCourse> enrollments = studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>()
                    .eq(StudentCourse::getCourseId, course.getId()).eq(StudentCourse::getStatus, 1));
            for (StudentCourse enrollment : enrollments) {
                Long approvedLeave = leaveRequestMapper.selectCount(new LambdaQueryWrapper<LeaveRequest>()
                        .eq(LeaveRequest::getCourseId, course.getId())
                        .eq(LeaveRequest::getStudentId, enrollment.getStudentId())
                        .eq(LeaveRequest::getLeaveDate, date)
                        .eq(LeaveRequest::getStatus, "APPROVED"));
                if (approvedLeave > 0) continue;
                Long count = attendanceMapper.selectCount(new LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getCourseId, course.getId())
                        .eq(Attendance::getStudentId, enrollment.getStudentId())
                        .eq(Attendance::getAttendanceDate, date));
                if (count == 0) {
                    Attendance attendance = new Attendance();
                    attendance.setCourseId(course.getId());
                    attendance.setStudentId(enrollment.getStudentId());
                    attendance.setAttendanceDate(date);
                    attendance.setStatus("ABSENT");
                    attendanceMapper.insert(attendance);
                }
            }
        }
    }
}
