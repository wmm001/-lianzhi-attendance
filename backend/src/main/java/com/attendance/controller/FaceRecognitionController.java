package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.entity.User;
import com.attendance.mapper.UserMapper;
import com.attendance.service.FaceVerificationProofService;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.time.LocalDateTime;

/**
 * 人脸识别控制器
 */
@RestController
@RequestMapping("/face")
public class FaceRecognitionController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FaceVerificationProofService proofService;

    @Autowired
    private com.attendance.service.StudentGroupAccessService groupAccessService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${face.recognition.url}")
    private String faceRecognitionUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/liveness/step")
    public Result<?> validateLivenessStep(@RequestParam("frames") List<MultipartFile> frames,
                                          @RequestParam("step") String step,
                                          HttpServletRequest servletRequest) {
        Long currentUserId = (Long) servletRequest.getAttribute("userId");
        String role = (String) servletRequest.getAttribute("role");
        if (currentUserId == null || !"STUDENT".equals(role)) {
            return Result.error(403, "只有已登录学生可以进行活体检测");
        }
        if (!List.of("align", "blink", "turn_left", "turn_right").contains(step)) {
            return Result.error(400, "无效的活体检测步骤");
        }
        if (frames == null || frames.isEmpty() || frames.size() > 50) {
            return Result.error(400, "该步骤没有采集到有效画面");
        }
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("step", step);
            for (int index = 0; index < frames.size(); index++) {
                MultipartFile frame = frames.get(index);
                if (frame != null && !frame.isEmpty()) {
                    body.add("frames", new MultipartByteArrayResource(frame.getBytes(), "step-" + index + ".jpg"));
                }
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            ResponseEntity<Map> response = restTemplate.exchange(
                    faceRecognitionUrl + "/face/liveness/step",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            Map result = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && result != null
                    && Boolean.TRUE.equals(result.get("passed"))) {
                return Result.success(result);
            }
            return Result.error(400, "该步骤没有正确检测到");
        } catch (Exception error) {
            return faceServiceError(error, "该步骤没有正确检测到");
        }
    }

    /**
     * 人脸注册
     */
    @PostMapping("/register")
    public Result<?> registerFace(@RequestParam("faceImage") MultipartFile faceImage,
                                   @RequestParam("studentId") Long studentId,
                                   HttpServletRequest servletRequest) {
        try {
            Long currentUserId = (Long) servletRequest.getAttribute("userId");
            String role = (String) servletRequest.getAttribute("role");
            if (currentUserId == null || !currentUserId.equals(studentId) || !"STUDENT".equals(role)) return Result.error(403, "只有学生本人可以注册人脸");
            User registeringUser = userMapper.selectById(studentId);
            if (registeringUser == null) return Result.error(404,"学生账号不存在");
            if (registeringUser.getFaceFeatures() != null && !registeringUser.getFaceFeatures().isEmpty()
                    && !Boolean.TRUE.equals(registeringUser.getFaceReenrollAllowed())) return Result.error(403,"人脸已锁定，请先提交重新录入申请并等待老师批准");
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("faceImage", new MultipartByteArrayResource(faceImage.getBytes(), faceImage.getOriginalFilename()));
            body.add("studentId", studentId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    faceRecognitionUrl + "/face/register",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                User user = userMapper.selectById(studentId);
                if (user != null) {
                    user.setFaceFeatures("SFACE_REGISTERED");
                    user.setFaceImagePath(null);
                    user.setFaceReenrollAllowed(false);
                    userMapper.updateById(user);
                }
                return Result.success("人脸注册成功", response.getBody());
            } else {
                return Result.error("人脸注册失败");
            }
        } catch (Exception e) {
            return faceServiceError(e, "人脸注册失败");
        }
    }

    /**
     * 人脸验证
     */
    @PostMapping("/verify")
    public Result<?> verifyFace(@RequestParam("faceImage") MultipartFile faceImage,
                                  @RequestParam(value = "livenessFrames", required = false) List<MultipartFile> livenessFrames,
                                  @RequestParam(value = "groupMode", defaultValue = "false") boolean groupMode,
                                  @RequestParam(value = "requireLiveness", defaultValue = "true") boolean requireLiveness,
                                  @RequestParam(value="selectedMemberIds", required=false) List<Long> selectedMemberIds,
                                  @RequestParam("courseId") Long courseId,
                                  HttpServletRequest servletRequest) {
        try {
            Long currentUserId = (Long) servletRequest.getAttribute("userId");
            String role = (String) servletRequest.getAttribute("role");
            if (currentUserId == null || !"STUDENT".equals(role)) {
                return Result.error(403, "只有已登录学生可以进行人脸签到");
            }
            if (groupMode && !groupAccessService.isLeader(currentUserId)) {
                return Result.error(403, "只有小组组长可以开启小组快速检测");
            }
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("faceImage", new MultipartByteArrayResource(faceImage.getBytes(), faceImage.getOriginalFilename()));
            if (livenessFrames != null) {
                for (int index = 0; index < livenessFrames.size(); index++) {
                    MultipartFile frame = livenessFrames.get(index);
                    if (frame != null && !frame.isEmpty()) {
                        String filename = frame.getOriginalFilename() == null
                                ? "live-" + index + ".jpg"
                                : frame.getOriginalFilename();
                        body.add("livenessFrames", new MultipartByteArrayResource(frame.getBytes(), filename));
                    }
                }
            }
            body.add("courseId", courseId);
            // 个人签到可由学生选择快速识别或活体识别；小组代签场景始终强制活体。
            boolean effectiveLiveness = groupMode || requireLiveness;
            body.add("requireLiveness", effectiveLiveness);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    faceRecognitionUrl + "/face/verify",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> recognition = new LinkedHashMap<>();
                if (response.getBody() != null) {
                    recognition.putAll(response.getBody());
                }

                if (!Boolean.TRUE.equals(recognition.get("success"))) return Result.error(400, "人脸识别未通过");
                if (effectiveLiveness && (!(recognition.get("liveness") instanceof Map)
                        || !Boolean.TRUE.equals(((Map) recognition.get("liveness")).get("passed")))) {
                    return Result.error(400, "活体检测未通过");
                }
                Object matchedId = recognition.get("studentId");
                if (matchedId == null) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("errorType", "FACE_NOT_MATCHED");
                    return Result.error(400, "身份核验失败：检测到的是非注册人脸，与当前账号已注册人脸不匹配", details);
                } else {
                    Long userId = Long.valueOf(matchedId.toString());
                    if (!currentUserId.equals(userId) && (!groupMode || !groupAccessService.canLeaderManage(currentUserId, userId, courseId))) {
                        Map<String, Object> details = new LinkedHashMap<>();
                        details.put("errorType", "FACE_IDENTITY_MISMATCH");
                        return Result.error(403, "身份核验失败：检测到的是非注册人脸，与当前账号已注册人脸不匹配", details);
                    }
                    if (groupMode && (selectedMemberIds == null || !selectedMemberIds.contains(userId))) return Result.error(403,"该成员未勾选，不能签到");
                    User user = userMapper.selectById(userId);
                    if (user == null) return Result.error(404, "识别到的学生账号已不存在");
                    recognition.put("userId", user.getId());
                    recognition.put("studentId", user.getStudentId());
                    recognition.put("realName", user.getRealName());
                }
                Long verifiedStudentId = Long.valueOf(recognition.get("userId").toString());
                proofService.issue(verifiedStudentId, courseId);
                recognition.put("recognitionTime", LocalDateTime.now());
                return Result.success(recognition);
            } else {
                return Result.error("人脸验证失败");
            }
        } catch (Exception e) {
            return faceServiceError(e, "人脸验证失败");
        }
    }

    /**
     * 获取人脸特征
     */
    @GetMapping("/features/{userId}")
    public Result<?> getFaceFeatures(@PathVariable Long userId,
                                       HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");

        // 只能查看自己或管理员可以查看所有人
        if (!currentUserId.equals(userId) && !"ADMIN".equals(role)) {
            return Result.error(403, "无权访问");
        }

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    faceRecognitionUrl + "/face/features/" + userId,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                Map body = response.getBody() == null ? new LinkedHashMap() : new LinkedHashMap(response.getBody());
                User user = userMapper.selectById(userId);
                if (user != null && body != null && body.get("registered") instanceof Boolean) {
                    boolean registered = Boolean.TRUE.equals(body.get("registered"));
                    user.setFaceFeatures(registered ? "SFACE_REGISTERED" : null);
                    user.setFaceImagePath(null);
                    userMapper.updateById(user);
                }
                body.put("reEnrollAllowed", user != null && Boolean.TRUE.equals(user.getFaceReenrollAllowed()));
                body.put("requestPending", jdbc.queryForObject("SELECT COUNT(*) FROM face_reenroll_request WHERE student_id=? AND status='PENDING'",Integer.class,userId)>0);
                return Result.success(body);
            } else {
                return Result.error("获取特征失败");
            }
        } catch (Exception e) {
            User user=userMapper.selectById(userId);
            if(user==null)return Result.error(404,"用户不存在");
            Map<String,Object> fallback=new LinkedHashMap<>();
            fallback.put("registered",user.getFaceFeatures()!=null&&!user.getFaceFeatures().isEmpty());
            fallback.put("reEnrollAllowed",Boolean.TRUE.equals(user.getFaceReenrollAllowed()));
            fallback.put("requestPending",jdbc.queryForObject("SELECT COUNT(*) FROM face_reenroll_request WHERE student_id=? AND status='PENDING'",Integer.class,userId)>0);
            return Result.success(fallback);
        }
    }

    /**
     * 删除人脸
     */
    @DeleteMapping("/{userId}")
    public Result<?> deleteFace(@PathVariable Long userId,
                                  HttpServletRequest request) {
        return Result.error(403,"人脸资料不能自行删除，请提交重新录入申请");
    }

    @PostMapping("/re-enroll/request")
    public Result<?> requestReEnroll(@RequestParam(required=false) String reason,HttpServletRequest request){
        if(!"STUDENT".equals(request.getAttribute("role")))return Result.error(403,"只有学生可以提交申请");
        Long studentId=(Long)request.getAttribute("userId");
        User student=userMapper.selectById(studentId);
        if(student==null||student.getFaceFeatures()==null||student.getFaceFeatures().isEmpty())return Result.error("尚未录入人脸，无需提交重录申请");
        if(Boolean.TRUE.equals(student.getFaceReenrollAllowed()))return Result.error("老师已批准，请直接重新录入");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM face_reenroll_request WHERE student_id=? AND status='PENDING'",Integer.class,studentId)>0)return Result.error("已有待处理申请，请勿重复提交");
        jdbc.update("INSERT INTO face_reenroll_request(student_id,reason,status,created_at) VALUES(?,?,'PENDING',?)",studentId,reason==null?null:reason.trim(),LocalDateTime.now());
        return Result.success("重新录入申请已提交",null);
    }

    @GetMapping("/re-enroll/requests")
    public Result<?> reEnrollRequests(HttpServletRequest request){
        if(!"TEACHER".equals(request.getAttribute("role")))return Result.error(403,"只有老师可以查看申请");
        Long teacherId=(Long)request.getAttribute("userId");
        return Result.success(jdbc.queryForList("SELECT r.id,r.student_id,u.real_name,u.student_id AS student_number,r.reason,r.status,r.created_at FROM face_reenroll_request r JOIN user u ON u.id=r.student_id WHERE EXISTS (SELECT 1 FROM student_course sc JOIN course c ON c.id=sc.course_id WHERE sc.student_id=r.student_id AND sc.status=1 AND c.teacher_id=?) ORDER BY r.created_at DESC",teacherId));
    }

    @PutMapping("/re-enroll/{studentId}/approve")
    public Result<?> approveReEnroll(@PathVariable Long studentId,HttpServletRequest request){
        if(!"TEACHER".equals(request.getAttribute("role")))return Result.error(403,"只有老师可以批准申请");
        Long teacherId=(Long)request.getAttribute("userId");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM student_course sc JOIN course c ON c.id=sc.course_id WHERE sc.student_id=? AND sc.status=1 AND c.teacher_id=?",Integer.class,studentId,teacherId)==0)return Result.error(403,"该学生不在你的课程中");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM face_reenroll_request WHERE student_id=? AND status='PENDING'",Integer.class,studentId)==0)return Result.error("没有待处理的重新录入申请");
        User user=userMapper.selectById(studentId);if(user==null)return Result.error(404,"学生不存在");user.setFaceReenrollAllowed(true);userMapper.updateById(user);
        jdbc.update("UPDATE face_reenroll_request SET status='APPROVED',reviewer_id=?,reviewed_at=? WHERE student_id=? AND status='PENDING'",teacherId,LocalDateTime.now(),studentId);
        return Result.success("已允许该学生重新录入人脸",null);
    }

    /**
     * MultipartByteArrayResource用于文件上传
     */
    private static class MultipartByteArrayResource extends ByteArrayResource {
        private final String filename;

        public MultipartByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    @SuppressWarnings("unchecked")
    private Result<?> faceServiceError(Exception error, String fallbackMessage) {
        if (error instanceof RestClientResponseException) {
            RestClientResponseException responseError = (RestClientResponseException) error;
            try {
                Map<String, Object> details = objectMapper.readValue(
                        responseError.getResponseBodyAsString(), Map.class);
                Object downstreamMessage = details.get("message");
                String message = downstreamMessage == null
                        ? fallbackMessage
                        : downstreamMessage.toString();
                return Result.error(responseError.getRawStatusCode(), message, details);
            } catch (Exception ignored) {
                return Result.error(responseError.getRawStatusCode(), fallbackMessage);
            }
        }

        return Result.error(503, "人脸识别服务暂不可用，请稍后重试");
    }
}
