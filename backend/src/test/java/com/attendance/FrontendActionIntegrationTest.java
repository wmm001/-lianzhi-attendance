package com.attendance;

import com.attendance.service.EnterpriseAttendanceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FrontendActionIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EnterpriseAttendanceService enterpriseAttendance;

    @Test
    void repeatedFaceSignInsKeepSeparateRecordsWithoutDoubleCountingEmployees() {
        long officeId = jdbc.queryForObject("SELECT office_id FROM employee_office WHERE user_id=4", Long.class);
        Map<String, Object> first = enterpriseAttendance.checkIn(4, officeId, "FACE", null);
        org.junit.jupiter.api.Assertions.assertEquals("PRESENT", first.get("status"));
        Map<String, Object> second = enterpriseAttendance.checkIn(4, officeId, "FACE", null);
        org.junit.jupiter.api.Assertions.assertNotEquals(first.get("id"), second.get("id"));
        org.junit.jupiter.api.Assertions.assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_attendance WHERE user_id=4 AND work_date=?", Integer.class, LocalDate.now()));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_attendance WHERE id=? AND check_out_time IS NOT NULL", Integer.class, first.get("id")));
        Map<String, Object> dashboard = enterpriseAttendance.dashboard(4, false);
        org.junit.jupiter.api.Assertions.assertEquals(2, dashboard.get("todayCheckInCount"));
        org.junit.jupiter.api.Assertions.assertEquals(1, dashboard.get("monthCount"));
        Map<String, Object> managerDashboard = enterpriseAttendance.dashboard(1, true);
        org.junit.jupiter.api.Assertions.assertEquals(1, managerDashboard.get("todayCheckedIn"));
        org.junit.jupiter.api.Assertions.assertEquals(2, ((java.util.List<?>) managerDashboard.get("officeTrend")).size());
        org.junit.jupiter.api.Assertions.assertNotNull(managerDashboard.get("officeHourHeatmap"));
        enterpriseAttendance.checkOut(4);
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_attendance WHERE id=? AND check_out_time IS NOT NULL", Integer.class, second.get("id")));
    }

    @Test
    void adminManualCheckInCreatesSeparateAuditedRecordAndEmployeeCannotForgeIt() throws Exception {
        String admin = login("admin", "admin123");
        String employee = login("student1", "password123");
        LocalDateTime historical = LocalDateTime.now().minusDays(2).withHour(14).withMinute(45).withSecond(0).withNano(0);
        String payload = objectMapper.writeValueAsString(Map.of("employeeId", 4, "officeId", 1,
                "checkInTime", historical.toString(), "reason", "上班时设备故障，管理员核实后补签"));
        mockMvc.perform(post("/enterprise/manual-check-in").header("Authorization", "Bearer " + employee)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(jsonPath("$.code").value(403));
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM work_attendance WHERE user_id=4", Integer.class);
        String response = mockMvc.perform(post("/enterprise/manual-check-in").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.method").value("MANUAL"))
                .andExpect(jsonPath("$.data.status").value("PRESENT"))
                .andExpect(jsonPath("$.data.manualOperator").value("系统管理员"))
                .andReturn().getResponse().getContentAsString();
        long attendanceId = objectMapper.readTree(response).path("data").path("id").asLong();
        org.junit.jupiter.api.Assertions.assertEquals(before + 1,
                jdbc.queryForObject("SELECT COUNT(*) FROM work_attendance WHERE user_id=4", Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbc.queryForObject("SELECT COUNT(*) FROM manual_attendance_audit WHERE attendance_id=? AND operator_id=1", Integer.class, attendanceId));
    }

    @Test
    void teacherCreatesEditsDisablesRestoresAndDeletesOwnCourse() throws Exception {
        String token = login("teacher1", "password123");
        String created = mockMvc.perform(post("/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseJson("前端操作测试课", "UI-ACTION-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long courseId = objectMapper.readTree(created).path("data").asLong();

        mockMvc.perform(put("/courses/" + courseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseJson("前端操作测试课（已编辑）", "UI-ACTION-1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(put("/courses/" + courseId + "/status").header("Authorization", "Bearer " + token).param("status","0"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(put("/courses/" + courseId + "/status").header("Authorization", "Bearer " + token).param("status","1"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(delete("/courses/" + courseId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        String admin=login("admin","admin123");
        mockMvc.perform(post("/courses").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(courseJson("越权课程","ADMIN-NO")))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void adminCanCreateUpdateDisableAndDeleteManagedUsers() throws Exception {
        String admin = login("admin", "admin123");
        String created = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("ui_student", "界面学生", "UI20260909", "STUDENT", "Password123")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long userId = objectMapper.readTree(created).path("data").path("id").asLong();

        mockMvc.perform(put("/users/" + userId + "/admin")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("ui_student", "界面学生已修改", "UI20260909", "STUDENT", "")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(put("/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + admin).param("status", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(delete("/users/" + userId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void adminCanSetTheDemoPasswordWithoutAnUppercaseLetter() throws Exception {
        String admin = login("admin", "admin123");
        String created = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("demo_password_user", "密码演示员工", "DEMO-PWD-1", "STUDENT", "Password123")))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).path("data").path("id").asLong();

        mockMvc.perform(put("/users/" + id + "/admin")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("demo_password_user", "密码演示员工", "DEMO-PWD-1", "STUDENT", "password123")))
                .andExpect(jsonPath("$.code").value(200));
        login("demo_password_user", "password123");
    }

    @Test
    void teacherCanAddAStudentAndEditOwnCourseInfo() throws Exception {
        String admin = login("admin", "admin123");
        String teacher = login("teacher1", "password123");
        String userBody = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("class_student", "课堂学生", "CLASS20260909", "STUDENT", "Password123")))
                .andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString();
        long studentId = objectMapper.readTree(userBody).path("data").path("id").asLong();

        mockMvc.perform(post("/courses/1/students").header("Authorization", "Bearer " + teacher)
                        .param("studentNumber", "CLASS20260909"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/courses/1/students").header("Authorization", "Bearer " + teacher))
                .andExpect(jsonPath("$.data[?(@.id == " + studentId + ")]").exists());
        mockMvc.perform(put("/courses/1").header("Authorization", "Bearer " + teacher)
                        .contentType(MediaType.APPLICATION_JSON).content(courseJson("教师越权修改", "CS101")))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void teacherAssignsGroupMembersAndLeader() throws Exception {
        String token = login("teacher1", "password123");
        String created = mockMvc.perform(post("/groups")
                        .header("Authorization", "Bearer " + token).param("courseId","1").param("name", "接口自检小组").param("leaderId","4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long groupId = objectMapper.readTree(created).path("data").path("id").asLong();

        mockMvc.perform(post("/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + token).param("studentId", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.members.length()").value(2));
        mockMvc.perform(put("/groups/" + groupId + "/leader/5").header("Authorization","Bearer "+token))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(put("/groups/" + groupId + "/leader/4").header("Authorization","Bearer "+token))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(delete("/groups/" + groupId + "/members/5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void leaveSubmitAndReviewButtonsPersistTheWorkflow() throws Exception {
        String student = login("student1", "password123");
        String teacher = login("teacher1", "password123");
        LocalDate leaveDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        String submitted = mockMvc.perform(post("/attendance/leaves")
                        .header("Authorization", "Bearer " + student)
                        .param("courseId", "1")
                        .param("leaveDate", leaveDate.toString())
                        .param("reason", "前端操作自检"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long leaveId = objectMapper.readTree(submitted).path("data").path("id").asLong();

        mockMvc.perform(put("/attendance/leaves/" + leaveId + "/review")
                        .header("Authorization", "Bearer " + teacher)
                        .param("approved", "true").param("comment", "同意"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/attendance/leaves").header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("APPROVED"));
    }

    @Test
    void teacherCanModifyAndCancelAttendanceSession() throws Exception {
        String teacher=login("teacher1","password123");
        jdbc.update("DELETE FROM attendance_session_member WHERE course_id=? AND attendance_date=?", 1, LocalDate.now());
        jdbc.update("DELETE FROM attendance_session WHERE course_id=? AND attendance_date=?", 1, LocalDate.now());
        jdbc.update("DELETE FROM attendance WHERE course_id=? AND attendance_date=? AND status<>'LEAVE'", 1, LocalDate.now());
        mockMvc.perform(post("/attendance/sessions/1").header("Authorization","Bearer "+teacher).param("minutes","10"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(put("/attendance/sessions/1").header("Authorization","Bearer "+teacher).param("minutes","15").param("lateMinutes","5"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.active").value(true));
        mockMvc.perform(delete("/attendance/sessions/1").header("Authorization","Bearer "+teacher))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void enterpriseQrCheckInRejectsTamperingAndDuplicates() throws Exception {
        String admin = login("admin", "admin123");
        String employee = login("student1", "password123");
        String qrResponse = mockMvc.perform(get("/enterprise/qr/1")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        String qr = objectMapper.readTree(qrResponse).path("data").path("token").asText();
        mockMvc.perform(post("/enterprise/qr/check-in")
                        .header("Authorization", "Bearer " + employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"A" + qr.substring(1) + "\"}"))
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/enterprise/qr/check-in")
                        .header("Authorization", "Bearer " + employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + qr + "\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.method").value("QR"));
        mockMvc.perform(post("/enterprise/qr/check-in")
                        .header("Authorization", "Bearer " + employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + qr + "\"}"))
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/enterprise/check-out")
                        .header("Authorization", "Bearer " + employee))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.checkOutTime").exists());
        mockMvc.perform(get("/enterprise/dashboard").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.todayCheckedOut").value(1))
                .andExpect(jsonPath("$.data.byOffice[0].checkedOut").value(1));
    }

    @Test
    void enhancedFaceCheckInRequiresLivenessFrames() throws Exception {
        String employee = login("student1", "password123");
        MockMultipartFile photo = new MockMultipartFile("faceImage", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/enterprise/face/check-in").file(photo)
                        .param("requireLiveness", "true")
                        .header("Authorization", "Bearer " + employee))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void adminCanCreateOfficeAndAssignEmployee() throws Exception {
        String admin = login("admin", "admin123");
        String createdOffice = mockMvc.perform(post("/enterprise/offices")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"TEST_OFFICE\",\"companyName\":\"测试公司\",\"name\":\"测试办公区\",\"address\":\"郑州市\",\"latitude\":34.75,\"longitude\":113.62}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long officeId = objectMapper.readTree(createdOffice).path("data").path("id").asLong();
        String createdUser = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("demo_worker", "测试员工", "TEST-001", "STUDENT", "Password123")))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long employeeId = objectMapper.readTree(createdUser).path("data").path("id").asLong();
        mockMvc.perform(put("/enterprise/employees/" + employeeId + "/office")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"officeId\":" + officeId + "}"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/enterprise/employees").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.code").value(200));
        org.junit.jupiter.api.Assertions.assertEquals(officeId, jdbc.queryForObject(
                "SELECT office_id FROM employee_office WHERE user_id=?", Long.class, employeeId));
    }

    @Test
    void studentRequestsAndTeacherApprovesOneFaceReEnrollment() throws Exception {
        jdbc.update("UPDATE user SET face_features='REGISTERED' WHERE id=4");
        String student=login("student1","password123"),teacher=login("teacher1","password123");
        mockMvc.perform(post("/face/re-enroll/request").header("Authorization","Bearer "+student).param("reason","原照片不清晰"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/face/re-enroll/requests").header("Authorization","Bearer "+teacher))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data[0].status").value("PENDING"));
        mockMvc.perform(put("/face/re-enroll/4/approve").header("Authorization","Bearer "+teacher))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/face/features/4").header("Authorization","Bearer "+student))
                .andExpect(jsonPath("$.data.reEnrollAllowed").value(true));
    }

    private String courseJson(String name, String code) {
        return "{\"courseName\":\"" + name + "\",\"courseCode\":\"" + code
                + "\",\"classroom\":\"自检教室\",\"startTime\":\"08:00\",\"endTime\":\"09:40\""
                + ",\"teacherId\":2,\"weekDay\":1,\"semester\":\"2026-2027-1\",\"maxStudents\":50}";
    }

    private String userJson(String username, String realName, String number, String role, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password
                + "\",\"realName\":\"" + realName + "\",\"studentId\":\"" + number
                + "\",\"className\":\"计算机2401班\",\"role\":\"" + role + "\",\"status\":1}";
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.path("data").path("token").asText();
    }
}
