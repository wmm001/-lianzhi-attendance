package com.attendance;

import com.attendance.service.*;
import com.attendance.dto.AttendanceRequest;
import com.attendance.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendanceOptimizationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AttendanceSessionService sessions;
    @Autowired AttendanceReportService reports;
    @Autowired AttendanceService attendance;
    @Autowired FaceVerificationProofService proofs;
    private final Long course=9001L;

    @BeforeEach void setup(){
        jdbc.update("INSERT INTO course(id,course_name,course_code,teacher_id,start_time,end_time,week_day,status) VALUES(9001,'优化测试','QA-9001',2,'08:00:00','23:59:00',?,1)",LocalDate.now().getDayOfWeek().getValue());
        jdbc.update("INSERT INTO student_course(course_id,student_id,status) VALUES(9001,4,1),(9001,5,1)");
        jdbc.update("UPDATE user SET class_name='测试一班' WHERE id=4");jdbc.update("UPDATE user SET class_name='测试二班' WHERE id=5");
    }
    @Test void validatesDurationAndPreventsRestart(){
        assertThrows(BusinessException.class,()->sessions.start(course,2L,0,null));
        assertThrows(BusinessException.class,()->sessions.start(course,2L,5,5));
        assertEquals(true,sessions.start(course,2L,5,null).get("active"));
        assertThrows(BusinessException.class,()->sessions.start(course,2L,10,null));
    }
    @Test void expirationSettlesMissingStudentsOnceAndRejectsCheckIn(){
        sessions.start(course,2L,5,null);
        jdbc.update("UPDATE attendance_session SET ends_at=? WHERE course_id=?",LocalDateTime.now().minusSeconds(1),course);
        sessions.settleExpired();sessions.settleExpired();
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM attendance WHERE course_id=9001 AND status='ABSENT'",Integer.class));
        AttendanceRequest request=new AttendanceRequest();request.setCourseId(course);
        assertThrows(BusinessException.class,()->attendance.checkIn(4L,request));
    }
    @Test void checkInRequiresProofAndCannotDuplicate(){
        sessions.start(course,2L,5,null);
        AttendanceRequest request=new AttendanceRequest();request.setCourseId(course);
        assertThrows(BusinessException.class,()->attendance.checkIn(4L,request));
        proofs.issue(4L,course);assertEquals("PRESENT",attendance.checkIn(4L,request).getStatus());
        proofs.issue(4L,course);assertThrows(BusinessException.class,()->attendance.checkIn(4L,request));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM attendance WHERE course_id=9001",Integer.class));
    }
    @Test void filtersDatesClassAndExcludesLeaveFromDenominator(){
        LocalDate day=LocalDate.now().minusDays(2);
        for(String status:Arrays.asList("PRESENT","LATE","LEAVE","ABSENT")){
            jdbc.update("INSERT INTO attendance(course_id,student_id,attendance_date,status) VALUES(9001,4,?,?)",day,status);day=day.minusDays(1);
        }
        Map<String,Object> result=reports.report(course,null,null,"测试一班",null);
        assertEquals(66.7,result.get("attendanceRate"));assertEquals(4,result.get("total"));assertEquals(1,result.get("studentCount"));
        assertNull(reports.report(course,null,null,"测试二班",null).get("attendanceRate"));
        assertEquals(1,reports.report(course,LocalDate.now().minusDays(2),LocalDate.now().minusDays(2),null,null).get("total"));
        assertThrows(BusinessException.class,()->reports.report(course,LocalDate.now(),LocalDate.now().minusDays(1),null,null));
    }
    @Test void existingRecordsAreReportedBeforeSessionEndsAndAllLeaveHasNoRate(){
        sessions.start(course,2L,5,null);
        jdbc.update("INSERT INTO attendance(course_id,student_id,attendance_date,status) VALUES(9001,4,?,'LEAVE')",LocalDate.now());
        Map<String,Object> result=reports.report(course,null,null,"测试一班",null);
        assertEquals(1,result.get("total"));assertNull(result.get("attendanceRate"));
    }

    @Test void unlimitedCourseRecordsAreReportedImmediately(){
        jdbc.update("UPDATE course SET course_code='TEST-UNLIMITED-SEED' WHERE course_code='TEST-UNLIMITED'");
        jdbc.update("UPDATE course SET course_code='TEST-UNLIMITED' WHERE id=9001");
        jdbc.update("INSERT INTO attendance(course_id,student_id,attendance_date,status,check_in_time) VALUES(9001,4,?,'PRESENT',?)",LocalDate.now(),LocalDateTime.now());
        Map<String,Object> result=reports.report(course,null,null,null,null);
        assertEquals(1,result.get("present"));assertEquals(100.0,result.get("attendanceRate"));
    }
    @Test void xlsxIsZipWithLiteralCellsAndNoDataIsRejected()throws Exception{
        assertThrows(BusinessException.class,()->reports.export(course,null,null,null,null,null));
        byte[] bytes=SimpleXlsx.write(Arrays.asList(Arrays.asList("学号","姓名"),Arrays.asList("00001","=1+1 & <张>")));
        String sheet=null;int entries=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){entries++;if(entry.getName().equals("xl/worksheets/sheet1.xml"))sheet=new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
        assertEquals(5,entries);assertNotNull(sheet);assertTrue(sheet.contains("00001"));assertTrue(sheet.contains("=1+1 &amp; &lt;张&gt;"));assertFalse(sheet.contains("<f>"));
    }
}
