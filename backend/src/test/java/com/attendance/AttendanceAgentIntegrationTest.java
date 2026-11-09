package com.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.attendance.service.DeepSeekAgentClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AttendanceAgentIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private DeepSeekAgentClient deepSeek;

    @Test
    void generalQuestionsGoDirectlyToModelWithoutAttendanceFacts() throws Exception {
        when(deepSeek.explain(eq("人脸识别的原理是什么？"), eq("")))
                .thenReturn(java.util.Optional.of("通过图像特征比较确认身份。"));
        JsonNode result = ask(login("student1", "password123"), "人脸识别的原理是什么？", "", "");
        assertEquals("unknown", result.path("data").path("intent").asText());
        assertEquals("deepseek", result.path("data").path("mode").asText());
        assertEquals("通过图像特征比较确认身份。", result.path("data").path("reply").asText());
        verify(deepSeek).explain("人脸识别的原理是什么？", "");
    }

    @Test
    void studentQueryCannotSeeAnotherEmployeesAttendanceButManagerCanSeeAggregate() throws Exception {
        long branch = jdbc.queryForObject("SELECT id FROM office WHERE code='BRANCH'", Long.class);
        jdbc.update("INSERT INTO work_attendance(user_id,office_id,work_date,check_in_time,method,status) VALUES(?,?,?,?,?,?)",
                5, branch, LocalDate.now(), LocalDateTime.of(LocalDate.now(), LocalTime.of(9, 30)), "QR", "LATE");

        String student = login("student1", "password123");
        JsonNode personal = ask(student, "我今天签到了吗", "", "");
        assertEquals("status", personal.path("data").path("intent").asText());
        assertTrue(personal.path("data").path("reply").asText().contains("暂无你的签到记录"));
        assertFalse(personal.toString().contains("李萌"));

        String admin = login("admin", "admin123");
        JsonNode summary = ask(admin, "第二生活园区今天签到情况", "", "");
        assertEquals("BRANCH", summary.path("data").path("officeCode").asText());
        assertTrue(summary.path("data").path("reply").asText().contains("已签到 1 人"));
        assertTrue(summary.path("data").path("reply").asText().contains("迟到 1 人"));
    }

    @Test
    void followUpKeepsStatusIntentAndSwitchesToYesterday() throws Exception {
        long hq = jdbc.queryForObject("SELECT id FROM office WHERE code='HQ'", Long.class);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        jdbc.update("INSERT INTO work_attendance(user_id,office_id,work_date,check_in_time,method,status) VALUES(?,?,?,?,?,?)",
                4, hq, yesterday, LocalDateTime.of(yesterday, LocalTime.of(8, 50)), "FACE", "PRESENT");

        JsonNode answer = ask(login("student1", "password123"), "那昨天呢", "status", "");
        assertTrue(answer.path("data").path("reply").asText().contains("已签到"));
        assertTrue(answer.path("data").path("reply").asText().contains("人脸"));
    }

    @Test
    void endpointRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(post("/enterprise/agent/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"今天签到情况\"}"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode ask(String token, String message, String previousIntent, String previousOffice) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("message", message,
                "previousIntent", previousIntent, "previousOffice", previousOffice));
        byte[] response = mockMvc.perform(post("/enterprise/agent/chat")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        JsonNode result = json.readTree(response);
        assertEquals(200, result.path("code").asInt());
        return result;
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("token").asText();
    }
}
