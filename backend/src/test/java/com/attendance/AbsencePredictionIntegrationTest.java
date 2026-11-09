package com.attendance;

import com.attendance.service.AbsencePredictionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AbsencePredictionIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AbsencePredictionService predictions;

    @Test
    void repeatedPunchesCountOnceAndForecastUsesOnlyCompletedWeekdays() {
        jdbc.update("UPDATE user SET created_at=NULL WHERE id=5");
        LocalDate start = LocalDate.now().minusDays(18);
        jdbc.update("UPDATE user SET created_at=? WHERE id=4", start.atStartOfDay());
        LocalDate attended = LocalDate.now().minusDays(1);
        while (attended.getDayOfWeek() == DayOfWeek.SATURDAY || attended.getDayOfWeek() == DayOfWeek.SUNDAY)
            attended = attended.minusDays(1);
        Long office = jdbc.queryForObject("SELECT office_id FROM employee_office WHERE user_id=4", Long.class);
        LocalDateTime first = attended.atTime(8, 50);
        jdbc.update("INSERT INTO work_attendance(user_id,office_id,work_date,check_in_time,method,status) VALUES(?,?,?,?,?,?)",
                4, office, attended, first, "FACE", "PRESENT");
        jdbc.update("INSERT INTO work_attendance(user_id,office_id,work_date,check_in_time,method,status) VALUES(?,?,?,?,?,?)",
                4, office, attended, first.plusMinutes(10), "FACE", "PRESENT");

        Map<String, Object> result = predictions.predict();
        List<?> employees = (List<?>) result.get("employees");
        Map<?, ?> student = employees.stream().map(value -> (Map<?, ?>) value)
                .filter(value -> Long.valueOf(4).equals(((Number) value.get("employeeId")).longValue()))
                .findFirst().orElseThrow();
        assertEquals(1, student.get("attendedWorkdays"));
        assertTrue(((Number) student.get("observedWorkdays")).intValue() >= 5);
        assertNotNull(student.get("estimatedAbsencePercent"));
        assertEquals(LocalDate.now().minusDays(1), result.get("completedThrough"));
        Map<?, ?> unknownTenure = employees.stream().map(value -> (Map<?, ?>) value)
                .filter(value -> Long.valueOf(5).equals(((Number) value.get("employeeId")).longValue()))
                .findFirst().orElseThrow();
        assertEquals("INSUFFICIENT", unknownTenure.get("risk"));
    }
}
