package com.attendance.service;

import com.attendance.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

/** A course has one attendance occurrence per day, matching the existing unique key. */
@Service
public class AttendanceSessionService {
    @Autowired private JdbcTemplate jdbc;

    public void lockCourse(Long courseId) {
        jdbc.queryForList("SELECT id FROM course WHERE id=? FOR UPDATE", courseId);
    }

    public Map<String,Object> find(Long courseId, LocalDate date) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM attendance_session WHERE course_id=? AND attendance_date=?", courseId,date);
        return rows.isEmpty()?null:rows.get(0);
    }
    public LocalDateTime time(Map<String,Object> row,String field) {
        Object value=row.get(field);
        return value instanceof Timestamp?((Timestamp)value).toLocalDateTime():LocalDateTime.parse(value.toString().replace(' ','T'));
    }
    public Map<String,Object> current(Long courseId) {
        Map<String,Object> row=find(courseId,LocalDate.now());
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("serverTime",LocalDateTime.now());
        result.put("active",row!=null&&LocalDateTime.now().isBefore(time(row,"ends_at")));
        if(row!=null){result.put("startsAt",time(row,"starts_at"));result.put("endsAt",time(row,"ends_at"));result.put("lateAt",row.get("late_at"));}
        return result;
    }
    public boolean contains(Long courseId,LocalDate date,Long studentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM attendance_session_member WHERE course_id=? AND attendance_date=? AND student_id=?",Integer.class,courseId,date,studentId)>0;
    }
    @Transactional
    public Map<String,Object> start(Long courseId,Long teacherId,int minutes,Integer lateMinutes) {
        if(minutes<1||minutes>240) throw new BusinessException("签到时长须为1至240分钟");
        if(lateMinutes!=null&&(lateMinutes<1||lateMinutes>=minutes)) throw new BusinessException("迟到阈值须大于0且小于签到时长");
        lockCourse(courseId);
        if(find(courseId,LocalDate.now())!=null) throw new BusinessException("本课程今日已发起签到，不可重复发起");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM attendance WHERE course_id=? AND attendance_date=? AND status<>'LEAVE'",Integer.class,courseId,LocalDate.now())>0)
            throw new BusinessException("今日已有考勤记录，不能重新发起签到");
        LocalDateTime now=LocalDateTime.now(), end=now.plusMinutes(minutes);
        if(!end.toLocalDate().equals(now.toLocalDate())) throw new BusinessException("签到结束时间不能跨越当天");
        jdbc.update("INSERT INTO attendance_session(course_id,attendance_date,starts_at,ends_at,late_at,teacher_id) VALUES(?,?,?,?,?,?)",courseId,now.toLocalDate(),now,end,lateMinutes==null?null:now.plusMinutes(lateMinutes),teacherId);
        jdbc.update("INSERT INTO attendance_session_member(course_id,attendance_date,student_id) SELECT course_id,?,student_id FROM student_course WHERE course_id=? AND status=1",now.toLocalDate(),courseId);
        return current(courseId);
    }

    @Transactional
    public Map<String,Object> update(Long courseId, int minutes, Integer lateMinutes) {
        if (minutes < 1 || minutes > 240) throw new BusinessException("剩余签到时长须为1至240分钟");
        if (lateMinutes != null && (lateMinutes < 1 || lateMinutes >= minutes)) throw new BusinessException("迟到阈值须大于0且小于剩余时长");
        lockCourse(courseId);
        Map<String,Object> session = find(courseId, LocalDate.now());
        if (session == null) throw new BusinessException("本课程今日尚未发起签到");
        if (Boolean.TRUE.equals(session.get("settled"))) throw new BusinessException("已结算的签到不能修改");
        LocalDateTime now = LocalDateTime.now(), end = now.plusMinutes(minutes);
        if (!end.toLocalDate().equals(now.toLocalDate())) throw new BusinessException("签到结束时间不能跨越当天");
        jdbc.update("UPDATE attendance_session SET ends_at=?,late_at=? WHERE course_id=? AND attendance_date=?",
                end, lateMinutes == null ? null : now.plusMinutes(lateMinutes), courseId, now.toLocalDate());
        return current(courseId);
    }

    @Transactional
    public void cancel(Long courseId) {
        lockCourse(courseId);
        LocalDate today = LocalDate.now();
        Map<String,Object> session = find(courseId, today);
        if (session == null) throw new BusinessException("本课程今日没有可取消的签到");
        if (Boolean.TRUE.equals(session.get("settled"))) throw new BusinessException("已结算的签到不能取消");
        jdbc.update("DELETE FROM attendance WHERE course_id=? AND attendance_date=? AND status<>'LEAVE'", courseId, today);
        jdbc.update("DELETE FROM attendance_session_member WHERE course_id=? AND attendance_date=?", courseId, today);
        jdbc.update("DELETE FROM attendance_session WHERE course_id=? AND attendance_date=?", courseId, today);
    }
    @Scheduled(fixedDelay=15000)
    @Transactional
    public void settleExpired() {
        for(Map<String,Object> session:jdbc.queryForList("SELECT * FROM attendance_session WHERE ends_at<=? AND settled=FALSE",LocalDateTime.now())) {
            Long courseId=((Number)session.get("course_id")).longValue();
            lockCourse(courseId);
            LocalDate date=LocalDate.parse(session.get("attendance_date").toString());
            for(Map<String,Object> member:jdbc.queryForList("SELECT student_id FROM attendance_session_member WHERE course_id=? AND attendance_date=?",courseId,date)) {
                Long studentId=((Number)member.get("student_id")).longValue();
                if(jdbc.queryForObject("SELECT COUNT(*) FROM attendance WHERE course_id=? AND student_id=? AND attendance_date=?",Integer.class,courseId,studentId,date)==0) {
                    int leave=jdbc.queryForObject("SELECT COUNT(*) FROM leave_request WHERE course_id=? AND student_id=? AND leave_date=? AND status='APPROVED'",Integer.class,courseId,studentId,date);
                    jdbc.update("INSERT INTO attendance(course_id,student_id,attendance_date,status) VALUES(?,?,?,?)",courseId,studentId,date,leave>0?"LEAVE":"ABSENT");
                }
            }
            jdbc.update("UPDATE attendance_session SET settled=TRUE WHERE course_id=? AND attendance_date=?",courseId,date);
        }
    }
}
