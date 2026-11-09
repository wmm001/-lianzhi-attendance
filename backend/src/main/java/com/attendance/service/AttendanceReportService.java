package com.attendance.service;

import com.attendance.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendanceReportService {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AttendanceSessionService sessions;
    @Autowired private AttendanceSettlementService settlement;
    public Map<String,Object> report(Long courseId,LocalDate start,LocalDate end,String className,String student) {
        if(start!=null&&end!=null&&start.isAfter(end)) throw new BusinessException("开始日期不能晚于结束日期");
        sessions.settleExpired();
        settlement.settleDate(LocalDate.now(),courseId);
        List<Map<String,Object>> records=jdbc.queryForList("SELECT a.*,u.real_name,u.student_id AS student_number,u.class_name,c.course_name,c.course_code,l.reason AS leave_reason,l.status AS leave_status FROM attendance a JOIN user u ON u.id=a.student_id JOIN course c ON c.id=a.course_id LEFT JOIN leave_request l ON l.course_id=a.course_id AND l.student_id=a.student_id AND l.leave_date=a.attendance_date WHERE a.course_id=? ORDER BY a.attendance_date,a.student_id",courseId);
        records=records.stream().filter(r->{
            LocalDate date=LocalDate.parse(r.get("attendance_date").toString());
            if(start!=null&&date.isBefore(start)||end!=null&&date.isAfter(end)) return false;
            return matches(r,className,student);
        }).collect(Collectors.toList());
        List<Map<String,Object>> roster=jdbc.queryForList("SELECT DISTINCT u.id AS student_id,u.real_name,u.student_id AS student_number,u.class_name FROM user u WHERE u.id IN (SELECT student_id FROM student_course WHERE course_id=? AND status=1 UNION SELECT student_id FROM attendance WHERE course_id=?)",courseId,courseId);
        Set<String> classes=new TreeSet<>();
        for(Map<String,Object> r:roster) classes.add(Objects.toString(r.get("class_name"),"未分班"));
        Map<Long,Map<String,Object>> students=new LinkedHashMap<>();
        for(Map<String,Object> r:roster) if(matches(r,className,student)) {
            Map<String,Object> row=counts();row.put("id",r.get("student_id"));row.put("name",r.get("real_name"));row.put("studentNumber",r.get("student_number"));row.put("className",Objects.toString(r.get("class_name"),"未分班"));
            students.put(((Number)r.get("student_id")).longValue(),row);
        }
        Map<String,Object> total=counts();Map<String,Map<String,Object>> daily=new TreeMap<>();
        for(Map<String,Object> record:records) {
            String status=Objects.toString(record.get("status"),"").toLowerCase(Locale.ROOT);
            if(!Arrays.asList("present","late","absent","leave").contains(status)) continue;
            increment(total,status);
            Map<String,Object> row=students.get(((Number)record.get("student_id")).longValue());if(row!=null) increment(row,status);
            increment(daily.computeIfAbsent(record.get("attendance_date").toString(),key->counts()),status);
        }
        finish(total); students.values().forEach(this::finish);
        List<Map<String,Object>> trend=new ArrayList<>();
        if (!daily.isEmpty()) {
            LocalDate first=start==null?LocalDate.parse(daily.keySet().iterator().next()):start;
            LocalDate last=end==null?LocalDate.parse(((TreeMap<String,Map<String,Object>>)daily).lastKey()):end;
            if(java.time.temporal.ChronoUnit.DAYS.between(first,last)>3660) throw new BusinessException("日期范围不能超过10年");
            for(LocalDate d=first;!d.isAfter(last);d=d.plusDays(1)) {
                Map<String,Object> row=daily.getOrDefault(d.toString(),counts());finish(row);row.put("date",d.toString());trend.add(row);
            }
        }
        total.put("studentCount",students.size());total.put("students",students.values());total.put("trend",trend);total.put("classes",classes);total.put("records",records);
        return total;
    }
    private boolean matches(Map<String,Object> r,String className,String student) {
        if(className!=null&&!className.isEmpty()&&!className.equals(Objects.toString(r.get("class_name"),"未分班"))) return false;
        return student==null||student.isEmpty()||(Objects.toString(r.get("real_name"),"")+" "+Objects.toString(r.get("student_number"),"")).contains(student.trim());
    }
    private Map<String,Object> counts(){Map<String,Object> r=new LinkedHashMap<>();for(String k:Arrays.asList("present","late","absent","leave"))r.put(k,0);return r;}
    private void increment(Map<String,Object> row,String key){row.put(key,((Number)row.get(key)).intValue()+1);}
    private void finish(Map<String,Object> row){int p=(int)row.get("present"),l=(int)row.get("late"),a=(int)row.get("absent"),v=(int)row.get("leave");row.put("total",p+l+a+v);row.put("attendanceRate",p+l+a==0?null:Math.round((p+l)*1000.0/(p+l+a))/10.0);}

    @SuppressWarnings("unchecked")
    public byte[] export(Long courseId,LocalDate start,LocalDate end,String className,String student,List<Long> studentIds) {
        Map<String,Object> report=report(courseId,start,end,className,student);
        List<List<String>> rows=new ArrayList<>();
        rows.add(Arrays.asList("日期","课程代码","课程","班级","姓名","学号","签到时间","考勤状态","请假状态","请假原因"));
        Map<String,String> labels=new HashMap<>();labels.put("PRESENT","正常");labels.put("LATE","迟到");labels.put("LEAVE","请假");labels.put("ABSENT","缺勤");labels.put("PENDING","待审批");labels.put("APPROVED","已批准");labels.put("REJECTED","已驳回");
        for(Map<String,Object> r:(List<Map<String,Object>>)report.get("records")) {
            if(studentIds!=null&&!studentIds.contains(((Number)r.get("student_id")).longValue()))continue;
            rows.add(Arrays.asList(str(r,"attendance_date"),str(r,"course_code"),str(r,"course_name"),Objects.toString(r.get("class_name"),"未分班"),str(r,"real_name"),str(r,"student_number"),str(r,"check_in_time"),labels.getOrDefault(str(r,"status"),str(r,"status")),labels.getOrDefault(str(r,"leave_status"),"无"),str(r,"leave_reason")));
        }
        if(rows.size()==1)throw new BusinessException("当前条件下没有可导出的考勤记录");
        return SimpleXlsx.write(rows);
    }
    private String str(Map<String,Object> r,String k){return Objects.toString(r.get(k),"");}
}
