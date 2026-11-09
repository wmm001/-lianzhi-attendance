package com.attendance.service;

import com.attendance.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EnterpriseAttendanceService {
    private final JdbcTemplate jdbc;

    public EnterpriseAttendanceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> checkIn(long userId, long officeId, String method, BigDecimal confidence) {
        Integer eligible = jdbc.queryForObject("SELECT COUNT(*) FROM user u JOIN employee_office eo ON eo.user_id=u.id " +
                        "JOIN office o ON o.id=eo.office_id WHERE u.id=? AND u.role='STUDENT' AND u.status=1 " +
                        "AND eo.office_id=? AND o.status=1", Integer.class, userId, officeId);
        if (eligible == null || eligible == 0) throw new BusinessException(403, "当前员工未分配到该办公场所，或场所已停用");
        // Serialize this employee's sign-ins so two concurrent QR requests cannot both pass the daily check.
        jdbc.queryForObject("SELECT id FROM user WHERE id=? FOR UPDATE", Long.class, userId);
        LocalDate today = LocalDate.now();
        if (!"FACE".equals(method) && jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_attendance WHERE user_id=? AND work_date=?",
                Integer.class, userId, today) > 0) throw new BusinessException("今天已经签到，请勿重复打卡");
        LocalDateTime now = LocalDateTime.now();
        // No shift or start time is configured for this project; a clock time alone cannot prove lateness.
        String status = "PRESENT";
        if ("FACE".equals(method)) jdbc.update("UPDATE work_attendance SET check_out_time=? " +
                "WHERE user_id=? AND work_date=? AND check_out_time IS NULL", now, userId, today);
        jdbc.update("INSERT INTO work_attendance(user_id,office_id,work_date,check_in_time,method,status,confidence) " +
                "VALUES(?,?,?,?,?,?,?)", userId, officeId, today, now, method, status, confidence);
        return todayRecord(userId);
    }

    @Transactional
    public Map<String, Object> checkOut(long userId) {
        Map<String, Object> record = todayRecord(userId);
        if (record == null) throw new BusinessException("今天尚未签到");
        if (record.get("checkOutTime") != null) throw new BusinessException("今天已经签退");
        jdbc.update("UPDATE work_attendance SET check_out_time=? WHERE id=? AND user_id=?",
                LocalDateTime.now(), record.get("id"), userId);
        return todayRecord(userId);
    }

    public Map<String, Object> todayRecord(long userId) {
        List<Map<String, Object>> records = jdbc.query(recordSql() + " WHERE a.user_id=? AND a.work_date=? ORDER BY a.check_in_time DESC,a.id DESC LIMIT 1",
                (rs, row) -> record(rs), userId, LocalDate.now());
        return records.isEmpty() ? null : records.get(0);
    }

    /** Add a separate historical row and audit entry; never rewrite an employee's actual punches. */
    @Transactional
    public Map<String, Object> manualCheckIn(long operatorId, long employeeId, long officeId,
                                              LocalDateTime checkInTime, String reason) {
        if (checkInTime == null || !checkInTime.isBefore(LocalDateTime.now()))
            throw new BusinessException("补签时间必须早于当前时间");
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.length() < 4 || normalizedReason.length() > 500)
            throw new BusinessException("补签原因需填写 4 至 500 个字");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='STUDENT' AND status=1",
                Integer.class, employeeId) != 1) throw new BusinessException("请选择启用中的员工");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM office WHERE id=? AND status=1",
                Integer.class, officeId) != 1) throw new BusinessException("请选择启用中的办公场所");

        String status = "PRESENT";
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO work_attendance(user_id,office_id,work_date,check_in_time,method,status) VALUES(?,?,?,?,?,?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, employeeId);
            statement.setLong(2, officeId);
            statement.setDate(3, Date.valueOf(checkInTime.toLocalDate()));
            statement.setTimestamp(4, Timestamp.valueOf(checkInTime));
            statement.setString(5, "MANUAL");
            statement.setString(6, status);
            return statement;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("补签记录未生成编号");
        long attendanceId = key.getKey().longValue();
        jdbc.update("INSERT INTO manual_attendance_audit(attendance_id,operator_id,reason) VALUES(?,?,?)",
                attendanceId, operatorId, normalizedReason);
        return jdbc.queryForObject(recordSql() + " WHERE a.id=?", (rs, row) -> record(rs), attendanceId);
    }

    public List<Map<String, Object>> records(Long userId, Long officeId, LocalDate start, LocalDate end) {
        StringBuilder sql = new StringBuilder(recordSql()).append(" WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (userId != null) { sql.append(" AND a.user_id=?"); args.add(userId); }
        if (officeId != null) { sql.append(" AND a.office_id=?"); args.add(officeId); }
        if (start != null) { sql.append(" AND a.work_date>=?"); args.add(start); }
        if (end != null) { sql.append(" AND a.work_date<=?"); args.add(end); }
        sql.append(" ORDER BY a.work_date DESC,a.check_in_time DESC,a.id DESC LIMIT 500");
        return jdbc.query(sql.toString(), (rs, row) -> record(rs), args.toArray());
    }

    public Map<String, Object> dashboard(long userId, boolean manager) {
        Map<String, Object> result = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        if (manager) {
            result.put("totalEmployees", count("SELECT COUNT(*) FROM user WHERE role='STUDENT' AND status=1"));
            result.put("totalOffices", count("SELECT COUNT(*) FROM office WHERE status=1"));
            result.put("todayCheckedIn", jdbc.queryForObject("SELECT COUNT(DISTINCT user_id) FROM work_attendance WHERE work_date=?", Integer.class, today));
            result.put("todayCheckedOut", jdbc.queryForObject("SELECT COUNT(*) FROM work_attendance a JOIN " +
                    "(SELECT MAX(id) id FROM work_attendance WHERE work_date=? GROUP BY user_id) latest ON latest.id=a.id " +
                    "WHERE a.check_out_time IS NOT NULL", Integer.class, today));
            result.put("todayOnSite", jdbc.queryForObject("SELECT COUNT(*) FROM work_attendance a JOIN " +
                    "(SELECT MAX(id) id FROM work_attendance WHERE work_date=? GROUP BY user_id) latest ON latest.id=a.id " +
                    "WHERE a.check_out_time IS NULL", Integer.class, today));
            result.put("todayLate", jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT user_id," +
                    "MIN(CASE WHEN status='LATE' THEN 1 ELSE 0 END) AS late FROM work_attendance " +
                    "WHERE work_date=? GROUP BY user_id) firsts WHERE firsts.late=1", Integer.class, today));
            result.put("byOffice", jdbc.query("SELECT o.name,COUNT(a.id) AS attendance_count," +
                    "SUM(CASE WHEN a.check_out_time IS NOT NULL THEN 1 ELSE 0 END) AS checked_out_count FROM office o LEFT JOIN " +
                    "(SELECT w.* FROM work_attendance w JOIN (SELECT MAX(id) id FROM work_attendance WHERE work_date=? " +
                    "GROUP BY user_id) latest ON latest.id=w.id) a ON a.office_id=o.id " +
                    "WHERE o.status=1 GROUP BY o.id,o.name ORDER BY o.id",
                    (rs, row) -> Map.of("name", rs.getString("name"), "count", rs.getInt("attendance_count"),
                            "checkedOut", rs.getInt("checked_out_count")), today));
            int[] hourly = new int[24];
            jdbc.query("SELECT MIN(check_in_time) AS check_in_time FROM work_attendance WHERE work_date=? GROUP BY user_id", rs -> {
                hourly[rs.getTimestamp("check_in_time").toLocalDateTime().getHour()]++;
            }, today);
            java.util.List<Map<String, Object>> hourlyTrend = new java.util.ArrayList<>();
            for (int hour = 0; hour < hourly.length; hour++) {
                hourlyTrend.add(Map.of("hour", String.format("%02d:00", hour), "count", hourly[hour]));
            }
            result.put("hourlyTrend", hourlyTrend);
            result.put("officeTrend", officeTrend(today.minusDays(6), today));
            result.put("officeHourHeatmap", officeHourHeatmap(today.minusDays(29)));
            result.put("workHours", workHours(today.minusDays(29)));
        } else {
            result.put("myToday", todayRecord(userId));
            result.put("todayCheckInCount", jdbc.queryForObject("SELECT COUNT(*) FROM work_attendance WHERE user_id=? AND work_date=?", Integer.class, userId, today));
            result.put("monthCount", jdbc.queryForObject("SELECT COUNT(DISTINCT work_date) FROM work_attendance WHERE user_id=? " +
                    "AND work_date>=? AND work_date<=?", Integer.class, userId, today.withDayOfMonth(1), today));
            result.put("monthLate", jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT work_date," +
                    "MIN(CASE WHEN status='LATE' THEN 1 ELSE 0 END) AS late FROM work_attendance WHERE user_id=? " +
                    "AND work_date>=? AND work_date<=? GROUP BY work_date) days WHERE days.late=1",
                    Integer.class, userId, today.withDayOfMonth(1), today));
        }
        Map<LocalDate, Integer> values = new LinkedHashMap<>();
        for (int offset = 6; offset >= 0; offset--) values.put(today.minusDays(offset), 0);
        String trendSql = "SELECT work_date,COUNT(DISTINCT user_id) AS total FROM work_attendance WHERE work_date>=?" +
                (manager ? "" : " AND user_id=?") + " GROUP BY work_date";
        Object[] args = manager ? new Object[]{today.minusDays(6)} : new Object[]{today.minusDays(6), userId};
        jdbc.query(trendSql, rs -> { values.put(rs.getDate("work_date").toLocalDate(), rs.getInt("total")); }, args);
        result.put("trend", values.entrySet().stream().map(entry -> Map.of("date", entry.getKey().toString(),
                "count", entry.getValue())).collect(java.util.stream.Collectors.toList()));
        return result;
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private List<Map<String, Object>> officeTrend(LocalDate start, LocalDate end) {
        Map<Long, Map<LocalDate, Integer>> counts = new LinkedHashMap<>();
        jdbc.query("SELECT office_id,work_date,COUNT(DISTINCT user_id) AS people FROM work_attendance " +
                "WHERE work_date>=? AND work_date<=? GROUP BY office_id,work_date", rs -> {
            long officeId = rs.getLong("office_id");
            counts.computeIfAbsent(officeId, ignored -> new LinkedHashMap<>())
                    .put(rs.getDate("work_date").toLocalDate(), rs.getInt("people"));
        }, start, end);
        return jdbc.query("SELECT id,name FROM office WHERE status=1 ORDER BY id", (rs, row) -> {
            Map<LocalDate, Integer> byDay = counts.getOrDefault(rs.getLong("id"), Map.of());
            java.util.List<Integer> values = new java.util.ArrayList<>();
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                values.add(byDay.getOrDefault(date, 0));
            }
            return Map.<String, Object>of("name", rs.getString("name"), "counts", values);
        });
    }

    private List<Map<String, Object>> workHours(LocalDate start) {
        Map<Long, WorkHourSummary> summaries = new LinkedHashMap<>();
        jdbc.query("SELECT a.user_id,u.real_name,u.student_id,a.work_date,a.check_in_time,a.check_out_time " +
                "FROM work_attendance a JOIN user u ON u.id=a.user_id " +
                "WHERE a.work_date>=? AND a.check_out_time IS NOT NULL", rs -> {
            long employeeId = rs.getLong("user_id");
            String employeeName = rs.getString("real_name");
            String employeeNumber = rs.getString("student_id");
            WorkHourSummary summary = summaries.computeIfAbsent(employeeId,
                    ignored -> new WorkHourSummary(employeeName, employeeNumber));
            summary.days.add(rs.getDate("work_date").toLocalDate());
            summary.sessions++;
            summary.minutes += Math.max(0, Duration.between(
                    rs.getTimestamp("check_in_time").toLocalDateTime(),
                    rs.getTimestamp("check_out_time").toLocalDateTime()).toMinutes());
        }, start);
        return summaries.values().stream()
                .sorted((a, b) -> Long.compare(b.minutes, a.minutes))
                .map(s -> Map.<String, Object>of("employeeName", s.name, "employeeNumber", s.number,
                        "days", s.days.size(), "sessions", s.sessions, "minutes", s.minutes))
                .collect(java.util.stream.Collectors.toList());
    }

    private Map<String, Object> officeHourHeatmap(LocalDate start) {
        Map<Long, Integer> officeIndexes = new LinkedHashMap<>();
        List<String> names = jdbc.query("SELECT id,name FROM office WHERE status=1 ORDER BY id", (rs, row) -> {
            officeIndexes.put(rs.getLong("id"), row);
            return rs.getString("name");
        });
        int[][] counts = new int[names.size()][24];
        jdbc.query("SELECT office_id,work_date,user_id,MIN(check_in_time) AS first_check_in " +
                "FROM work_attendance WHERE work_date>=? GROUP BY office_id,work_date,user_id", rs -> {
            Integer index = officeIndexes.get(rs.getLong("office_id"));
            if (index != null) {
                int hour = rs.getTimestamp("first_check_in").toLocalDateTime().getHour();
                counts[index][hour]++;
            }
        }, start);
        List<List<Integer>> points = new java.util.ArrayList<>();
        for (int office = 0; office < names.size(); office++) {
            for (int hour = 0; hour < 24; hour++) {
                points.add(List.of(hour, office, counts[office][hour]));
            }
        }
        return Map.of("offices", names, "points", points);
    }

    private static class WorkHourSummary {
        private final String name;
        private final String number;
        private final java.util.Set<LocalDate> days = new java.util.HashSet<>();
        private int sessions;
        private long minutes;

        private WorkHourSummary(String name, String number) {
            this.name = name == null ? "" : name;
            this.number = number == null ? "" : number;
        }
    }

    private String recordSql() {
        return "SELECT a.id,a.user_id,a.office_id,a.work_date,a.check_in_time,a.check_out_time," +
                "a.method,a.status,a.confidence,u.real_name,u.student_id,o.name AS office_name," +
                "m.reason AS manual_reason,m.created_at AS manual_created_at,op.real_name AS manual_operator " +
                "FROM work_attendance a JOIN user u ON u.id=a.user_id JOIN office o ON o.id=a.office_id " +
                "LEFT JOIN manual_attendance_audit m ON m.attendance_id=a.id " +
                "LEFT JOIN user op ON op.id=m.operator_id";
    }

    private Map<String, Object> record(ResultSet rs) throws SQLException {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", rs.getLong("id"));
        value.put("userId", rs.getLong("user_id"));
        value.put("employeeName", rs.getString("real_name"));
        value.put("employeeNumber", rs.getString("student_id"));
        value.put("officeId", rs.getLong("office_id"));
        value.put("officeName", rs.getString("office_name"));
        value.put("workDate", rs.getDate("work_date").toLocalDate());
        LocalDateTime checkInTime = rs.getTimestamp("check_in_time").toLocalDateTime();
        LocalDateTime checkOutTime = rs.getTimestamp("check_out_time") == null ? null : rs.getTimestamp("check_out_time").toLocalDateTime();
        value.put("checkInTime", checkInTime);
        value.put("checkOutTime", checkOutTime);
        value.put("workMinutes", checkOutTime == null ? null : Math.max(0, Duration.between(checkInTime, checkOutTime).toMinutes()));
        value.put("method", rs.getString("method"));
        value.put("status", rs.getString("status"));
        value.put("confidence", rs.getBigDecimal("confidence"));
        value.put("manualReason", rs.getString("manual_reason"));
        value.put("manualOperator", rs.getString("manual_operator"));
        value.put("manualCreatedAt", rs.getTimestamp("manual_created_at") == null ? null :
                rs.getTimestamp("manual_created_at").toLocalDateTime());
        return value;
    }
}
