package com.attendance.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A transparent short-history estimate, not a trained model or a personnel decision. */
@Service
public class AbsencePredictionService {
    private final JdbcTemplate jdbc;
    private final OptionalRedisCache cache;
    private final PredictionCachePolicy cachePolicy;

    public AbsencePredictionService(JdbcTemplate jdbc, OptionalRedisCache cache, PredictionCachePolicy cachePolicy) {
        this.jdbc = jdbc;
        this.cache = cache;
        this.cachePolicy = cachePolicy;
    }

    public Map<String, Object> predict() {
        String cacheKey = "lianzhi:absence:" + LocalDate.now();
        var cached = cache.get(cacheKey);
        if (cached.isPresent()) return cached.get();
        LocalDate completedThrough = LocalDate.now().minusDays(1);
        LocalDate windowStart = completedThrough.minusDays(41);
        LocalDate nextWorkday = LocalDate.now().plusDays(1);
        while (isWeekend(nextWorkday)) nextWorkday = nextWorkday.plusDays(1);

        Map<Long, Set<LocalDate>> attended = new HashMap<>();
        jdbc.query("SELECT user_id,work_date FROM work_attendance WHERE work_date>=? AND work_date<=? " +
                "GROUP BY user_id,work_date", rs -> {
            attended.computeIfAbsent(rs.getLong("user_id"), ignored -> new HashSet<>())
                    .add(rs.getDate("work_date").toLocalDate());
        }, windowStart, completedThrough);
        List<Map<String, Object>> rows = jdbc.query("SELECT u.id,u.real_name,u.student_id,u.created_at,o.name AS office_name " +
                "FROM user u LEFT JOIN employee_office eo ON eo.user_id=u.id " +
                "LEFT JOIN office o ON o.id=eo.office_id WHERE u.role='STUDENT' AND u.status=1 ORDER BY u.id", (rs, index) -> {
            long employeeId = rs.getLong("id");
            var createdAt = rs.getTimestamp("created_at");
            // Imported employees may have no creation timestamp. Avoid treating unknown tenure as absence.
            LocalDate joined = createdAt == null ? completedThrough.plusDays(1) : createdAt.toLocalDateTime().toLocalDate();
            LocalDate start = joined.isAfter(windowStart) ? joined : windowStart;
            int observed = 0, present = 0;
            for (LocalDate day = start; !day.isAfter(completedThrough); day = day.plusDays(1)) {
                if (isWeekend(day)) continue;
                observed++;
                if (attended.getOrDefault(employeeId, Set.of()).contains(day)) present++;
            }
            Integer probability = observed < 5 ? null :
                    (int) Math.round(100.0 * (observed - present + 1) / (observed + 2));
            String risk = probability == null ? "INSUFFICIENT" : probability >= 60 ? "HIGH" :
                    probability >= 30 ? "MEDIUM" : "LOW";
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("employeeId", employeeId);
            value.put("employeeName", rs.getString("real_name"));
            value.put("employeeNumber", rs.getString("student_id"));
            value.put("officeName", rs.getString("office_name"));
            value.put("observedWorkdays", observed);
            value.put("attendedWorkdays", present);
            value.put("estimatedAbsencePercent", probability);
            value.put("risk", risk);
            return value;
        });
        rows.sort((a, b) -> {
            int first = a.get("estimatedAbsencePercent") == null ? -1 : (int) a.get("estimatedAbsencePercent");
            int second = b.get("estimatedAbsencePercent") == null ? -1 : (int) b.get("estimatedAbsencePercent");
            return Integer.compare(second, first);
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetDate", nextWorkday);
        result.put("completedThrough", completedThrough);
        result.put("method", "近 42 个自然日内已完成的工作日，按员工入职后签到天数估算；重复签到只计一天，使用平滑处理。未计法定节假日、请假或排班，不作为考核依据。");
        result.put("employees", rows);
        cache.put(cacheKey, result, cachePolicy.ttl());
        return result;
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
