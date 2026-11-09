package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.exception.BusinessException;
import com.attendance.service.EnterpriseAttendanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.attendance.security.RequestAuthorization.*;

/** Bridges the existing OpenCV service to employee enrollment and check-in. */
@RestController
@RequestMapping("/enterprise/face")
public class EnterpriseFaceController {
    private final JdbcTemplate jdbc;
    private final EnterpriseAttendanceService attendance;
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper json;

    @Value("${face.recognition.url}")
    private String faceServiceUrl;

    public EnterpriseFaceController(JdbcTemplate jdbc, EnterpriseAttendanceService attendance, ObjectMapper json) {
        this.jdbc = jdbc;
        this.attendance = attendance;
        this.json = json;
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestParam("faceImage") MultipartFile photo,
                                                 HttpServletRequest request) throws IOException {
        requireRole(request, "STUDENT");
        long user = userId(request);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND status=1", Integer.class, user) == 0) {
            throw new BusinessException(403, "账号已停用");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND face_features IS NOT NULL", Integer.class, user) > 0) {
            throw new BusinessException("人脸已注册。如需重录，请联系管理员重置");
        }
        MultiValueMap<String, Object> body = imageBody(photo);
        body.add("studentId", user);
        Map<String, Object> response = post("/face/register", body);
        jdbc.update("UPDATE user SET face_features='SFACE_REGISTERED',face_image_path=NULL WHERE id=?", user);
        return Result.success("人脸注册成功", response);
    }

    @PostMapping("/check-in")
    public Result<Map<String, Object>> checkIn(@RequestParam("faceImage") MultipartFile photo,
        @RequestParam(value = "livenessFrames", required = false) java.util.List<MultipartFile> frames,
        @RequestParam(value = "requireLiveness", defaultValue = "false") boolean requireLiveness,
        HttpServletRequest request) throws IOException {
        requireRole(request, "STUDENT");
        long user = userId(request);
        Long officeId = jdbc.query("SELECT office_id FROM employee_office WHERE user_id=?",
                rs -> rs.next() ? rs.getLong(1) : null, user);
        if (officeId == null) throw new BusinessException("请先让管理员分配办公场所");
        if (requireLiveness && (frames == null || frames.size() < 8 || frames.size() > 50)) {
            throw new BusinessException("活体检测需要 8 至 50 帧连续摄像头画面");
        }
        MultiValueMap<String, Object> body = imageBody(photo);
        body.add("requireLiveness", requireLiveness);
        if (requireLiveness) {
            for (int i = 0; i < frames.size(); i++) body.add("livenessFrames", filePart(frames.get(i), "frame-" + i + ".jpg"));
        }
        Map<String, Object> response = post("/face/verify", body);
        Object matched = response.get("studentId");
        if (matched == null || !Long.toString(user).equals(matched.toString())) {
            throw new BusinessException(403, "人脸与当前登录员工不匹配");
        }
        Object score = response.get("confidence");
        BigDecimal confidence = score == null ? null : new BigDecimal(score.toString());
        Map<String, Object> result = new LinkedHashMap<>(attendance.checkIn(user, officeId, "FACE", confidence));
        result.put("faceConfidence", confidence);
        return Result.success("人脸签到成功", result);
    }

    @DeleteMapping("/{employeeId}")
    public Result<?> reset(@PathVariable long employeeId, HttpServletRequest request) {
        requireRole(request, "ADMIN");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='STUDENT'", Integer.class, employeeId) == 0) {
            throw new BusinessException(404, "员工不存在");
        }
        try { http.delete(faceServiceUrl + "/face/" + employeeId); }
        catch (HttpStatusCodeException error) { throw remoteError(error); }
        catch (Exception error) { throw new BusinessException("人脸识别服务不可用，请启动 Python 服务"); }
        jdbc.update("UPDATE user SET face_features=NULL,face_image_path=NULL WHERE id=?", employeeId);
        return Result.success("人脸资料已重置", null);
    }

    private MultiValueMap<String, Object> imageBody(MultipartFile photo) throws IOException {
        if (photo == null || photo.isEmpty() || photo.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException("请使用摄像头拍摄不超过 10MB 的照片");
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("faceImage", filePart(photo, "face.jpg"));
        return body;
    }

    private ByteArrayResource filePart(MultipartFile file, String filename) throws IOException {
        byte[] bytes = file.getBytes();
        return new ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, MultiValueMap<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        try {
            ResponseEntity<Map> response = http.exchange(faceServiceUrl + path, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> result = response.getBody();
            if (result == null || !Boolean.TRUE.equals(result.get("success"))) throw new BusinessException("人脸识别未通过");
            return result;
        } catch (HttpStatusCodeException error) { throw remoteError(error); }
        catch (BusinessException error) { throw error; }
        catch (Exception error) { throw new BusinessException("人脸识别服务不可用，请启动 Python 服务"); }
    }

    private BusinessException remoteError(HttpStatusCodeException error) {
        try {
            Map<?, ?> result = json.readValue(error.getResponseBodyAsString(), Map.class);
            return new BusinessException(result.get("message") == null ? "人脸识别未通过" : result.get("message").toString());
        } catch (Exception ignored) { return new BusinessException("人脸识别未通过，请保持正脸并重试"); }
    }
}
