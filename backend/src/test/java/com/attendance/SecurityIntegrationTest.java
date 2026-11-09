package com.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void protectedApiRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void publicSelfRegistrationIsDisabled() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"teacherhack\",\"password\":\"Password123\",\"realName\":\"测试\",\"role\":\"TEACHER\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void studentCannotCreateCourse() throws Exception {
        String token = login("student1", "password123");
        mockMvc.perform(post("/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseName\":\"越权课程\",\"courseCode\":\"HACK-1\",\"startTime\":\"08:00\",\"endTime\":\"09:00\",\"weekDay\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void logoutImmediatelyRevokesAccessToken() throws Exception {
        String token = login("student1", "password123");
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/users/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        String replacement=login("student1","password123");
        mockMvc.perform(get("/users/profile").header("Authorization","Bearer "+replacement))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Transactional
    void changePasswordUsesTheRealEndpointAndValidatesTheNewPassword() throws Exception {
        String token = login("student1", "password123");
        mockMvc.perform(put("/users/4/password")
                        .header("Authorization", "Bearer " + token)
                        .param("oldPassword", "password123")
                        .param("newPassword", "too-simple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(put("/users/4/password")
                        .header("Authorization", "Bearer " + token)
                        .param("oldPassword", "password123")
                        .param("newPassword", "NewPassword123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        login("student1", "NewPassword123");
    }

    @Test
    void studentCourseEndpointIsNotCapturedByTheCourseIdRoute() throws Exception {
        String token = login("student1", "password123");
        mockMvc.perform(get("/courses/my/enrolled")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.path("data").path("token").asText();
    }

    @Test
    void reportExportAndSessionRespectCoursePermissions() throws Exception {
        String student=login("student1","password123"),otherTeacher=login("teacher2","password123"),teacher=login("teacher1","password123");
        mockMvc.perform(get("/attendance/report/1").header("Authorization","Bearer "+student)).andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/attendance/export-xlsx").param("courseId","1").header("Authorization","Bearer "+otherTeacher)).andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(post("/attendance/sessions/1").param("minutes","5").header("Authorization","Bearer "+student)).andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(post("/attendance/sessions/1").param("minutes","5").header("Authorization","Bearer "+otherTeacher)).andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/attendance/report/1").header("Authorization","Bearer "+teacher)).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.records").doesNotExist());
    }
}
