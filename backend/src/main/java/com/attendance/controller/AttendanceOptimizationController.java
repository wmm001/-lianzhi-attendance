package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.entity.*;
import com.attendance.mapper.*;
import com.attendance.service.*;
import com.attendance.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import static com.attendance.security.RequestAuthorization.*;

@RestController
@RequestMapping("/attendance")
public class AttendanceOptimizationController {
    @Autowired private AttendanceSessionService sessions;
    @Autowired private AttendanceReportService reports;
    @Autowired private CourseMapper courses;
    @Autowired private StudentCourseMapper enrollments;
    @Autowired private UserMapper users;
    @Autowired private JdbcTemplate jdbc;

    @GetMapping("/summary")
    @SuppressWarnings("unchecked")
    public Result<?> summary(HttpServletRequest request) {
        Long currentUserId=userId(request);String currentRole=role(request);
        List<Long> ids=new ArrayList<>();
        if("STUDENT".equals(currentRole)) {
            for(StudentCourse enrollment:enrollments.selectList(new LambdaQueryWrapper<StudentCourse>().eq(StudentCourse::getStudentId,currentUserId).eq(StudentCourse::getStatus,1))) ids.add(enrollment.getCourseId());
        } else {
            requireRole(request,"TEACHER","ADMIN");
            LambdaQueryWrapper<Course> query=new LambdaQueryWrapper<>();
            if("TEACHER".equals(currentRole))query.eq(Course::getTeacherId,currentUserId);
            for(Course c:courses.selectList(query))ids.add(c.getId());
        }
        int present=0,total=0,absent=0,late=0,leave=0;
        LocalDate monthStart=LocalDate.now().withDayOfMonth(1),today=LocalDate.now();
        List<Course> managedCourses=new ArrayList<>();
        for(Long id:ids) {
            Course course=courses.selectById(id);if(course!=null)managedCourses.add(course);
            Map<String,Object> report=reports.report(id,monthStart,today,null,null);
            if("STUDENT".equals(currentRole)) {
                Map<String,Object> own=null;
                for(Map<String,Object> student:(Collection<Map<String,Object>>)report.get("students"))if(currentUserId.equals(((Number)student.get("id")).longValue()))own=student;
                if(own==null)continue;
                report=own;
            }
            present+=((Number)report.get("present")).intValue();late+=((Number)report.get("late")).intValue();
            leave+=((Number)report.get("leave")).intValue();
            absent+=((Number)report.get("absent")).intValue();total+=((Number)report.get("total")).intValue();
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("role",currentRole);result.put("courses",ids.size());
        result.put("present",present+late);result.put("total",total);result.put("late",late);result.put("absent",absent);result.put("leave",leave);
        result.put("rate",present+late+absent==0?null:Math.round((present+late)*1000.0/(present+late+absent))/10.0);
        if("STUDENT".equals(currentRole)) fillStudentDashboard(result,currentUserId,managedCourses,today,monthStart);
        else fillTeacherDashboard(result,managedCourses,today,monthStart);
        return Result.success(result);
    }

    private void fillStudentDashboard(Map<String,Object> result,Long studentId,List<Course> courseList,LocalDate today,LocalDate monthStart) {
        List<Map<String,Object>> todayCourses=new ArrayList<>();List<Map<String,Object>> activeSessions=new ArrayList<>();
        for(Course course:courseList) if(Integer.valueOf(1).equals(course.getStatus())&&course.getWeekDay()==today.getDayOfWeek().getValue()) {
            Map<String,Object> item=courseItem(course);Map<String,Object> session=sessions.current(course.getId());
            item.put("session",session);item.put("attendanceStatus",singleValue("SELECT status FROM attendance WHERE course_id=? AND student_id=? AND attendance_date=?",course.getId(),studentId,today));
            todayCourses.add(item);if(Boolean.TRUE.equals(session.get("active")))activeSessions.add(item);
        }
        List<Map<String,Object>> recent=jdbc.queryForList("SELECT a.id,a.attendance_date,a.check_in_time,a.status,c.course_name FROM attendance a JOIN course c ON c.id=a.course_id WHERE a.student_id=? ORDER BY a.attendance_date DESC,a.check_in_time DESC LIMIT 3",studentId);
        List<Map<String,Object>> trend=jdbc.queryForList("SELECT attendance_date, SUM(CASE WHEN status IN ('PRESENT','LATE') THEN 1 ELSE 0 END) attended, SUM(CASE WHEN status='ABSENT' THEN 1 ELSE 0 END) absent FROM attendance WHERE student_id=? AND attendance_date>=? GROUP BY attendance_date ORDER BY attendance_date",studentId,today.minusDays(13));
        for(Map<String,Object> row:trend){int attended=((Number)row.get("attended")).intValue(),missed=((Number)row.get("absent")).intValue();row.put("rate",attended+missed==0?null:Math.round(attended*1000.0/(attended+missed))/10.0);}
        result.put("todayCourses",todayCourses);result.put("activeSessions",activeSessions);result.put("recentRecords",recent);result.put("trend",trend);
        result.put("monthLate",count("SELECT COUNT(*) FROM attendance WHERE student_id=? AND attendance_date>=? AND status='LATE'",studentId,monthStart));
        result.put("monthAbsent",count("SELECT COUNT(*) FROM attendance WHERE student_id=? AND attendance_date>=? AND status='ABSENT'",studentId,monthStart));
        result.put("monthLeave",count("SELECT COUNT(*) FROM attendance WHERE student_id=? AND attendance_date>=? AND status='LEAVE'",studentId,monthStart));
    }

    @SuppressWarnings("unchecked")
    private void fillTeacherDashboard(Map<String,Object> result,List<Course> courseList,LocalDate today,LocalDate monthStart) {
        int todayCount=0,pending=0,p=0,l=0,a=0;Set<Long> abnormal=new HashSet<>();List<Map<String,Object>> active=new ArrayList<>();
        for(Course course:courseList){
            if(Integer.valueOf(1).equals(course.getStatus())&&course.getWeekDay()==today.getDayOfWeek().getValue())todayCount++;
            Map<String,Object> session=sessions.current(course.getId());
            if(Boolean.TRUE.equals(session.get("active"))){Map<String,Object> item=courseItem(course);item.putAll(session);int roster=count("SELECT COUNT(*) FROM attendance_session_member WHERE course_id=? AND attendance_date=?",course.getId(),today),checked=count("SELECT COUNT(*) FROM attendance WHERE course_id=? AND attendance_date=? AND check_in_time IS NOT NULL",course.getId(),today);item.put("studentCount",roster);item.put("checkedIn",checked);item.put("progress",roster==0?0:Math.round(checked*1000.0/roster)/10.0);active.add(item);}
            pending+=count("SELECT COUNT(*) FROM leave_request WHERE course_id=? AND status='PENDING'",course.getId());
            Map<String,Object> report=reports.report(course.getId(),monthStart,today,null,null);p+=((Number)report.get("present")).intValue();l+=((Number)report.get("late")).intValue();a+=((Number)report.get("absent")).intValue();
            for(Map<String,Object> student:(Collection<Map<String,Object>>)report.get("students")){Object rate=student.get("attendanceRate");if(((Number)student.get("absent")).intValue()>0||(rate!=null&&((Number)rate).doubleValue()<80))abnormal.add(((Number)student.get("id")).longValue());}
        }
        result.put("todayCourseCount",todayCount);result.put("activeSessions",active);result.put("pendingLeaves",pending);result.put("monthRate",p+l+a==0?null:Math.round((p+l)*1000.0/(p+l+a))/10.0);result.put("abnormalStudents",abnormal.size());
    }

    private Map<String,Object> courseItem(Course course){Map<String,Object> item=new LinkedHashMap<>();item.put("id",course.getId());item.put("courseName",course.getCourseName());item.put("courseCode",course.getCourseCode());item.put("classroom",course.getClassroom());item.put("startTime",course.getStartTime());item.put("endTime",course.getEndTime());return item;}
    private int count(String sql,Object...args){return jdbc.queryForObject(sql,Integer.class,args);}
    private Object singleValue(String sql,Object...args){List<Map<String,Object>> rows=jdbc.queryForList(sql,args);return rows.isEmpty()?null:rows.get(0).values().iterator().next();}

    @PostMapping("/sessions/{courseId}")
    public Result<?> start(@PathVariable Long courseId,@RequestParam int minutes,@RequestParam(required=false) Integer lateMinutes,HttpServletRequest request) {
        Course course=manager(request,courseId);
        if(!Integer.valueOf(1).equals(course.getStatus()))throw new BusinessException("课程已停用");
        return Result.success(sessions.start(courseId,userId(request),minutes,lateMinutes));
    }
    @PutMapping("/sessions/{courseId}")
    public Result<?> updateSession(@PathVariable Long courseId,@RequestParam int minutes,@RequestParam(required=false) Integer lateMinutes,HttpServletRequest request) {
        manager(request,courseId);
        return Result.success("签到时间已修改", sessions.update(courseId,minutes,lateMinutes));
    }
    @DeleteMapping("/sessions/{courseId}")
    public Result<?> cancelSession(@PathVariable Long courseId,HttpServletRequest request) {
        manager(request,courseId);sessions.cancel(courseId);
        return Result.success("本次签到已取消",null);
    }
    @GetMapping("/sessions/{courseId}")
    public Result<?> current(@PathVariable Long courseId,HttpServletRequest request) {
        if("STUDENT".equals(role(request))) {
            if(enrollments.selectCount(new LambdaQueryWrapper<StudentCourse>().eq(StudentCourse::getStudentId,userId(request)).eq(StudentCourse::getCourseId,courseId).eq(StudentCourse::getStatus,1))==0)throw new BusinessException(403,"只能查看本人课程的签到");
        } else manager(request,courseId);
        return Result.success(sessions.current(courseId));
    }
    @GetMapping("/report/{courseId}")
    public Result<?> report(@PathVariable Long courseId,
        @RequestParam(required=false) @DateTimeFormat(pattern="yyyy-MM-dd") LocalDate startDate,
        @RequestParam(required=false) @DateTimeFormat(pattern="yyyy-MM-dd") LocalDate endDate,
        @RequestParam(required=false) String className,@RequestParam(required=false) String student,HttpServletRequest request) {
        manager(request,courseId);Map<String,Object> report=reports.report(courseId,startDate,endDate,className,student);
        report.remove("records");return Result.success(report);
    }
    @GetMapping("/export-xlsx")
    public ResponseEntity<byte[]> export(@RequestParam Long courseId,
        @RequestParam(required=false) @DateTimeFormat(pattern="yyyy-MM-dd") LocalDate startDate,
        @RequestParam(required=false) @DateTimeFormat(pattern="yyyy-MM-dd") LocalDate endDate,
        @RequestParam(required=false) String className,@RequestParam(required=false) String student,
        @RequestParam(required=false) List<Long> studentIds,HttpServletRequest request) {
        manager(request,courseId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=attendance-"+courseId+".xlsx")
            .body(reports.export(courseId,startDate,endDate,className,student,studentIds));
    }
    @PutMapping("/student-class/{courseId}/{studentId}")
    public Result<?> setClass(@PathVariable Long courseId,@PathVariable Long studentId,@RequestParam String className,HttpServletRequest request) {
        manager(request,courseId);
        if(className.trim().isEmpty()||className.length()>80)throw new BusinessException("班级名称须为1至80字");
        if(enrollments.selectCount(new LambdaQueryWrapper<StudentCourse>().eq(StudentCourse::getCourseId,courseId).eq(StudentCourse::getStudentId,studentId).eq(StudentCourse::getStatus,1))==0)throw new BusinessException(403,"该学生不在课程名单中");
        User user=users.selectById(studentId);user.setClassName(className.trim());users.updateById(user);return Result.success("班级已更新",null);
    }
    private Course manager(HttpServletRequest request,Long courseId) {
        requireRole(request,"TEACHER","ADMIN");Course c=courses.selectById(courseId);
        if(c==null)throw new BusinessException(404,"课程不存在");
        if("TEACHER".equals(role(request))&&!userId(request).equals(c.getTeacherId()))throw new BusinessException(403,"只能管理自己课程的考勤");
        return c;
    }
}
