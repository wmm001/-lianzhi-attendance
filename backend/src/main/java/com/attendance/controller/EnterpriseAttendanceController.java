package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.exception.BusinessException;
import com.attendance.service.EnterpriseAttendanceService;
import com.attendance.service.AbsencePredictionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.attendance.security.RequestAuthorization.*;

@RestController
@RequestMapping("/enterprise")
public class EnterpriseAttendanceController {
    private final EnterpriseAttendanceService attendance;
    private final EnterpriseOfficeController offices;
    private final JdbcTemplate jdbc;
    private final AbsencePredictionService predictions;

    @Value("${jwt.secret}")
    private String signingSecret;

    public EnterpriseAttendanceController(EnterpriseAttendanceService attendance, EnterpriseOfficeController offices,
                                          JdbcTemplate jdbc, AbsencePredictionService predictions) {
        this.attendance = attendance;
        this.offices = offices;
        this.jdbc = jdbc;
        this.predictions = predictions;
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(HttpServletRequest request) {
        long currentUserId = userId(request);
        List<Map<String, Object>> rows = jdbc.query("SELECT u.id,u.real_name,u.student_id,u.role,u.face_features," +
                        "eo.office_id,o.name AS office_name FROM user u LEFT JOIN employee_office eo ON eo.user_id=u.id " +
                        "LEFT JOIN office o ON o.id=eo.office_id WHERE u.id=?", (rs, row) -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getLong("id"));
            value.put("realName", rs.getString("real_name"));
            value.put("employeeNumber", rs.getString("student_id"));
            value.put("role", rs.getString("role"));
            value.put("faceRegistered", rs.getString("face_features") != null);
            Object officeId = rs.getObject("office_id");
            value.put("officeId", officeId == null ? null : ((Number) officeId).longValue());
            value.put("officeName", rs.getString("office_name"));
            return value;
        }, currentUserId);
        if (rows.isEmpty()) throw new BusinessException(404, "账号不存在");
        return Result.success(rows.get(0));
    }

    /** A signed QR token remains usable for at most two minute buckets. */
    @GetMapping("/qr/{officeId}")
    public Result<Map<String, Object>> qr(@PathVariable long officeId, HttpServletRequest request) {
        requireRole(request, "ADMIN", "TEACHER");
        Map<String, Object> office = offices.officeById(officeId);
        if (((Number) office.get("status")).intValue() != 1) throw new BusinessException("该场所已停用");
        long minute = Instant.now().getEpochSecond() / 60;
        String payload = officeId + ":" + minute;
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(sign(body));
        return Result.success(Map.of("officeId", officeId, "officeName", office.get("name"),
                "token", body + "." + signature, "expiresAt", Instant.ofEpochSecond((minute + 2) * 60).toString()));
    }

    @PostMapping("/qr/check-in")
    public Result<Map<String, Object>> qrCheckIn(@RequestBody Map<String, Object> input, HttpServletRequest request) {
        requireRole(request, "STUDENT");
        long officeId = verifyQr(input.get("token"));
        return Result.success("二维码签到成功", attendance.checkIn(userId(request), officeId, "QR", null));
    }

    @PostMapping("/check-out")
    public Result<Map<String, Object>> checkOut(HttpServletRequest request) {
        requireRole(request, "STUDENT");
        return Result.success("签退成功", attendance.checkOut(userId(request)));
    }

    @PostMapping("/manual-check-in")
    public Result<Map<String, Object>> manualCheckIn(@RequestBody Map<String, Object> input,
                                                       HttpServletRequest request) {
        requireRole(request, "ADMIN");
        try {
            long employeeId = Long.parseLong(String.valueOf(input.get("employeeId")));
            long officeId = Long.parseLong(String.valueOf(input.get("officeId")));
            LocalDateTime time = LocalDateTime.parse(String.valueOf(input.get("checkInTime")));
            String reason = input.get("reason") == null ? "" : input.get("reason").toString();
            return Result.success("补签成功", attendance.manualCheckIn(userId(request), employeeId, officeId, time, reason));
        } catch (NumberFormatException | java.time.format.DateTimeParseException error) {
            throw new BusinessException("请填写有效的员工、场所和补签时间");
        }
    }

    @GetMapping("/records")
    public Result<List<Map<String, Object>>> records(HttpServletRequest request,
          @RequestParam(required = false) Long officeId,
          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        String currentRole = role(request);
        Long targetUserId = "STUDENT".equals(currentRole) ? userId(request) : null;
        if (start != null && end != null && start.isAfter(end)) throw new BusinessException("起始日期不能晚于结束日期");
        return Result.success(attendance.records(targetUserId, officeId, start, end));
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(HttpServletRequest request) {
        return Result.success(attendance.dashboard(userId(request), !"STUDENT".equals(role(request))));
    }

    @GetMapping("/absence-predictions")
    public Result<Map<String, Object>> absencePredictions(HttpServletRequest request) {
        requireRole(request, "ADMIN", "TEACHER");
        return Result.success(predictions.predict());
    }

    private long verifyQr(Object rawToken) {
        if (rawToken == null || rawToken.toString().length() > 300) throw new BusinessException("二维码无效");
        String[] pieces = rawToken.toString().split("\\.");
        if (pieces.length != 2) throw new BusinessException("二维码无效");
        byte[] expected = sign(pieces[0]);
        byte[] actual;
        String payload;
        try {
            actual = Base64.getUrlDecoder().decode(pieces[1]);
            payload = new String(Base64.getUrlDecoder().decode(pieces[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) { throw new BusinessException("二维码无效"); }
        if (!MessageDigest.isEqual(expected, actual)) throw new BusinessException("二维码签名校验失败");
        String[] values = payload.split(":");
        if (values.length != 2) throw new BusinessException("二维码无效");
        try {
            long officeId = Long.parseLong(values[0]);
            long issuedMinute = Long.parseLong(values[1]);
            long currentMinute = Instant.now().getEpochSecond() / 60;
            if (officeId <= 0 || issuedMinute > currentMinute || currentMinute - issuedMinute > 1) {
                throw new BusinessException("二维码已过期，请重新扫描");
            }
            return officeId;
        } catch (NumberFormatException error) { throw new BusinessException("二维码无效"); }
    }

    private byte[] sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) { throw new IllegalStateException("二维码签名服务不可用", error); }
    }
}
