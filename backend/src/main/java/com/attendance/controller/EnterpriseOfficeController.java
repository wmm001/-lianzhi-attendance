package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.attendance.security.RequestAuthorization.*;

/** Office registry and employee assignments for the enterprise demonstration. */
@RestController
@RequestMapping("/enterprise")
public class EnterpriseOfficeController {
    private final JdbcTemplate jdbc;

    public EnterpriseOfficeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/offices")
    public Result<List<Map<String, Object>>> offices(HttpServletRequest request) {
        userId(request);
        return Result.success(jdbc.query("SELECT id, code, company_name, name, address, latitude, longitude, status " +
                "FROM office ORDER BY id", (rs, row) -> office(rs)));
    }

    @PostMapping("/offices")
    public Result<Map<String, Object>> createOffice(@RequestBody Map<String, Object> input, HttpServletRequest request) {
        requireRole(request, "ADMIN");
        String code = required(input, "code", 32).toUpperCase();
        if (!code.matches("[A-Z0-9_-]+")) throw new BusinessException("场所编码只能使用字母、数字、下划线和连字符");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM office WHERE code=?", Integer.class, code) > 0) {
            throw new BusinessException("场所编码已存在");
        }
        jdbc.update("INSERT INTO office(code,company_name,name,address,latitude,longitude,status) VALUES(?,?,?,?,?,?,1)",
                code, required(input, "companyName", 100), required(input, "name", 100),
                required(input, "address", 255), coordinate(input, "latitude", -90, 90),
                coordinate(input, "longitude", -180, 180));
        return Result.success("场所已创建", officeByCode(code));
    }

    @PutMapping("/offices/{id}")
    public Result<Map<String, Object>> updateOffice(@PathVariable long id, @RequestBody Map<String, Object> input,
                                                     HttpServletRequest request) {
        requireRole(request, "ADMIN");
        officeById(id);
        int status = number(input, "status", 1).intValue();
        if (status != 0 && status != 1) throw new BusinessException("状态只能是启用或停用");
        jdbc.update("UPDATE office SET company_name=?,name=?,address=?,latitude=?,longitude=?,status=? WHERE id=?",
                required(input, "companyName", 100), required(input, "name", 100),
                required(input, "address", 255), coordinate(input, "latitude", -90, 90),
                coordinate(input, "longitude", -180, 180), status, id);
        return Result.success("场所已更新", officeById(id));
    }

    @GetMapping("/employees")
    public Result<List<Map<String, Object>>> employees(HttpServletRequest request) {
        requireRole(request, "ADMIN", "TEACHER");
        return Result.success(jdbc.query("SELECT u.id,u.username,u.real_name,u.student_id,u.status,u.face_features," +
                "eo.office_id,o.name AS office_name FROM user u LEFT JOIN employee_office eo ON eo.user_id=u.id " +
                "LEFT JOIN office o ON o.id=eo.office_id WHERE u.role='STUDENT' ORDER BY u.id", (rs, row) -> {
            Map<String, Object> employee = new LinkedHashMap<>();
            employee.put("id", rs.getLong("id"));
            employee.put("username", rs.getString("username"));
            employee.put("realName", rs.getString("real_name"));
            employee.put("employeeNumber", rs.getString("student_id"));
            employee.put("status", rs.getInt("status"));
            employee.put("faceRegistered", rs.getString("face_features") != null);
            Object officeId = rs.getObject("office_id");
            employee.put("officeId", officeId == null ? null : ((Number) officeId).longValue());
            employee.put("officeName", rs.getString("office_name"));
            return employee;
        }));
    }

    @PutMapping("/employees/{userId}/office")
    public Result<?> assignOffice(@PathVariable long userId, @RequestBody Map<String, Object> input,
                                  HttpServletRequest request) {
        requireRole(request, "ADMIN");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='STUDENT'", Integer.class, userId) == 0) {
            throw new BusinessException(404, "员工不存在");
        }
        long officeId = number(input, "officeId", 0).longValue();
        Map<String, Object> office = officeById(officeId);
        if (((Number) office.get("status")).intValue() != 1) throw new BusinessException("不能分配已停用的场所");
        jdbc.update("DELETE FROM employee_office WHERE user_id=?", userId);
        jdbc.update("INSERT INTO employee_office(user_id,office_id) VALUES(?,?)", userId, officeId);
        return Result.success("员工办公场所已更新", null);
    }

    Map<String, Object> officeById(long id) {
        List<Map<String, Object>> matches = jdbc.query("SELECT id,code,company_name,name,address,latitude,longitude,status " +
                "FROM office WHERE id=?", (rs, row) -> office(rs), id);
        if (matches.isEmpty()) throw new BusinessException(404, "办公场所不存在");
        return matches.get(0);
    }

    private Map<String, Object> officeByCode(String code) {
        return jdbc.query("SELECT id,code,company_name,name,address,latitude,longitude,status FROM office WHERE code=?",
                (rs, row) -> office(rs), code).get(0);
    }

    private Map<String, Object> office(ResultSet rs) throws SQLException {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", rs.getLong("id"));
        value.put("code", rs.getString("code"));
        value.put("companyName", rs.getString("company_name"));
        value.put("name", rs.getString("name"));
        value.put("address", rs.getString("address"));
        value.put("latitude", rs.getBigDecimal("latitude"));
        value.put("longitude", rs.getBigDecimal("longitude"));
        value.put("status", rs.getInt("status"));
        return value;
    }

    static String required(Map<String, Object> input, String key, int maxLength) {
        String value = input.get(key) == null ? "" : input.get(key).toString().trim();
        if (value.isEmpty() || value.length() > maxLength) throw new BusinessException(key + "不能为空且长度不能超过" + maxLength);
        return value;
    }

    static Number number(Map<String, Object> input, String key, int fallback) {
        Object value = input.get(key);
        if (value == null) return fallback;
        try { return new BigDecimal(value.toString()); }
        catch (NumberFormatException error) { throw new BusinessException(key + "必须是数字"); }
    }

    private static BigDecimal coordinate(Map<String, Object> input, String key, int min, int max) {
        Object raw = input.get(key);
        if (raw == null || raw.toString().trim().isEmpty()) return null;
        BigDecimal value;
        try { value = new BigDecimal(raw.toString()); }
        catch (NumberFormatException error) { throw new BusinessException("经纬度格式不正确"); }
        if (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw new BusinessException("经纬度超出范围");
        }
        return value;
    }
}
