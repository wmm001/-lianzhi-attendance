package com.attendance.service.impl;

import com.attendance.common.PageResult;
import com.attendance.common.ResultCode;
import com.attendance.entity.Attendance;
import com.attendance.entity.Course;
import com.attendance.entity.LeaveRequest;
import com.attendance.entity.StudentCourse;
import com.attendance.entity.StudentGroup;
import com.attendance.entity.StudentGroupMember;
import com.attendance.entity.User;
import com.attendance.exception.BusinessException;
import com.attendance.mapper.AttendanceMapper;
import com.attendance.mapper.CourseMapper;
import com.attendance.mapper.LeaveRequestMapper;
import com.attendance.mapper.StudentCourseMapper;
import com.attendance.mapper.StudentGroupMapper;
import com.attendance.mapper.StudentGroupMemberMapper;
import com.attendance.mapper.UserMapper;
import com.attendance.security.PasswordEncoder;
import com.attendance.service.SimpleXlsx;
import com.attendance.service.UserService;
import com.attendance.vo.UserImportVO;
import com.attendance.vo.UserVO;
import com.attendance.dto.AdminUserRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private static final String PASSWORD_PATTERN = "^(?=.*[a-zA-Z])(?=.*\\d)[a-zA-Z\\d@$!%*?&]{8,20}$";

    /** 导入文件密码列留空时使用的演示密码。 */
    private static final String DEFAULT_IMPORT_PASSWORD = "password123";

    /** 单次批量导入最大数据行数。 */
    private static final int MAX_IMPORT_ROWS = 2000;

    /** IN 查询分批大小。 */
    private static final int QUERY_BATCH = 900;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Autowired
    private StudentCourseMapper studentCourseMapper;

    @Autowired
    private LeaveRequestMapper leaveRequestMapper;

    @Autowired
    private StudentGroupMapper studentGroupMapper;

    @Autowired
    private StudentGroupMemberMapper studentGroupMemberMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final TransactionTemplate transactionTemplate;

    public UserServiceImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public UserVO getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return convertToVO(user);
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return convertToVO(user);
    }

    @Override
    public PageResult<UserVO> getUserList(Integer page, Integer size, String role, String keyword) {
        Page<User> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        
        if (role != null && !role.isEmpty()) {
            wrapper.eq(User::getRole, role);
        } else {
            wrapper.ne(User::getRole, "ADMIN");
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getRealName, keyword)
                    .or().like(User::getStudentId, keyword));
        }
        wrapper.orderByDesc(User::getCreatedAt);
        
        Page<User> userPage = userMapper.selectPage(pageParam, wrapper);
        java.util.List<UserVO> voList = userPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(java.util.stream.Collectors.toList());
        
        return PageResult.of(userPage.getTotal(), voList, (long) page, (long) size);
    }

    @Override
    @Transactional
    public void updateUser(Long id, String realName, String studentId) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        
        if (realName != null) {
            user.setRealName(realName);
        }
        if (studentId != null) {
            user.setStudentId(studentId);
        }
        
        userMapper.updateById(user);
        log.info("更新用户信息成功: {}", id);
    }

    @Override
    @Transactional
    public void changePassword(Long id, String oldPassword, String newPassword) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.OLD_PASSWORD_ERROR);
        }
        if (newPassword == null || !newPassword.matches(PASSWORD_PATTERN)) {
            throw new BusinessException("新密码需为8至20位，并同时包含字母和数字");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }
        
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        log.info("修改密码成功: {}", id);
    }

    @Override
    @Transactional
    public void updateUserStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        
        ensureManagedRole(user);
        if (status == null || (status != 0 && status != 1)) throw new BusinessException("状态值只能是0或1");
        user.setStatus(status);
        userMapper.updateById(user);
        log.info("更新用户状态成功: {}, status: {}", id, status);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        ensureManagedRole(user);
        if ("TEACHER".equals(user.getRole())) {
            // 课程对教师是 RESTRICT 约束，级联删课程会连带全班考勤，故先阻止并给出明确提示
            Long courseCount = courseMapper.selectCount(new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, id));
            if (courseCount != null && courseCount > 0) {
                throw new BusinessException("该教师名下仍有" + courseCount + "门课程，请先删除或转移课程后再删除账号，也可以直接停用账号");
            }
        } else {
            deleteStudentRelations(id);
        }
        try {
            userMapper.deleteById(id);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("删除用户{}失败，仍存在关联数据: {}", id, e.getMessage());
            throw new BusinessException("该用户仍有关联业务数据，无法删除，请先处理相关记录或改为停用账号");
        }
        log.info("删除用户成功: {}", id);
    }

    /**
     * 删除学生的全部关联数据（同一事务内）。
     * 顺序需满足外键依赖：先删小组成员，再删小组；最后删考勤、选课、请假记录。
     */
    private void deleteStudentRelations(Long studentId) {
        // 该学生担任组长的小组：先清空这些小组的成员（group→member 外键），再删小组（leader 外键）
        List<Long> ledGroupIds = studentGroupMapper.selectList(
                        new LambdaQueryWrapper<StudentGroup>().eq(StudentGroup::getLeaderId, studentId)
                                .select(StudentGroup::getId))
                .stream().map(StudentGroup::getId).collect(Collectors.toList());
        if (!ledGroupIds.isEmpty()) {
            studentGroupMemberMapper.delete(new LambdaQueryWrapper<StudentGroupMember>()
                    .in(StudentGroupMember::getGroupId, ledGroupIds));
            studentGroupMapper.deleteBatchIds(ledGroupIds);
        }
        // 该学生加入其他小组的成员关系
        studentGroupMemberMapper.delete(new LambdaQueryWrapper<StudentGroupMember>()
                .eq(StudentGroupMember::getStudentId, studentId));
        // 考勤记录
        attendanceMapper.delete(new LambdaQueryWrapper<Attendance>().eq(Attendance::getStudentId, studentId));
        // 选课关系
        studentCourseMapper.delete(new LambdaQueryWrapper<StudentCourse>().eq(StudentCourse::getStudentId, studentId));
        // 请假记录
        leaveRequestMapper.delete(new LambdaQueryWrapper<LeaveRequest>().eq(LeaveRequest::getStudentId, studentId));
    }

    @Override
    @Transactional
    public UserVO createManagedUser(AdminUserRequest request) {
        validateManagedRequest(request, true, null);
        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName().trim());
        user.setStudentId(trimToNull(request.getStudentId()));
        user.setClassName(trimToNull(request.getClassName()));
        user.setRole(request.getRole());
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        userMapper.insert(user);
        return convertToVO(user);
    }

    @Override
    @Transactional
    public void updateManagedUser(Long id, AdminUserRequest request) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        ensureManagedRole(user);
        validateManagedRequest(request, false, id);
        String password = user.getPassword();
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            if (!request.getPassword().matches(PASSWORD_PATTERN)) throw new BusinessException("新密码需为8至20位，并同时包含字母和数字");
            password = passwordEncoder.encode(request.getPassword());
        }
        int changed=userMapper.update(null,new LambdaUpdateWrapper<User>().eq(User::getId,id)
                .set(User::getUsername,request.getUsername().trim())
                .set(User::getRealName,request.getRealName().trim())
                .set(User::getStudentId,trimToNull(request.getStudentId()))
                .set(User::getClassName,"STUDENT".equals(request.getRole())?trimToNull(request.getClassName()):null)
                .set(User::getRole,request.getRole())
                .set(User::getStatus,request.getStatus()==null?user.getStatus():request.getStatus())
                .set(User::getPassword,password));
        if(changed!=1)throw new BusinessException("人员信息保存失败，请刷新后重试");
    }

    @Override
    public UserImportVO batchImportUsers(String role, MultipartFile file) {
        if (!"STUDENT".equals(role) && !"TEACHER".equals(role)) {
            throw new BusinessException("只支持导入学生或教师名单");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的Excel文件");
        }
        List<List<String>> rows;
        try {
            rows = SimpleXlsx.read(file.getInputStream());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        } catch (IOException e) {
            log.warn("读取导入Excel失败", e);
            throw new BusinessException("Excel文件读取失败，请重新上传");
        }
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException("Excel文件内容为空，请填写数据后上传");
        }

        boolean student = "STUDENT".equals(role);
        String numberLabel = student ? "学号" : "工号";
        List<String> header = rows.get(0);
        int userIdx = headerIndex(header, "用户名");
        int nameIdx = headerIndex(header, "姓名");
        int numberIdx = headerNumberIndex(header, numberLabel);
        int classIdx = student ? headerIndex(header, "班级") : -1;
        int pwdIdx = headerIndex(header, "密码");

        List<String> missing = new ArrayList<>();
        if (userIdx < 0) missing.add("用户名");
        if (nameIdx < 0) missing.add("姓名");
        if (numberIdx < 0) missing.add(numberLabel);
        if (student && classIdx < 0) missing.add("班级");
        if (!missing.isEmpty()) {
            throw new BusinessException("Excel缺少必填列：" + String.join("、", missing) + "，请检查表头后重新上传");
        }

        // 先解析所有数据行（空行跳过）
        List<PendingUser> parsed = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            PendingUser pending = new PendingUser();
            pending.row = i + 1;
            pending.username = cell(row, userIdx);
            pending.realName = cell(row, nameIdx);
            pending.studentId = cell(row, numberIdx);
            pending.className = student ? cell(row, classIdx) : null;
            pending.password = pwdIdx >= 0 ? cell(row, pwdIdx) : "";
            if (pending.isBlank()) continue;
            parsed.add(pending);
        }
        if (parsed.isEmpty()) {
            throw new BusinessException("Excel中没有可导入的数据行，请填写后再上传");
        }

        // 一次性查出数据库中已占用的用户名和学号/工号，避免逐行查库
        Set<String> existingUsernames = loadExistingValues(
                parsed.stream().map(p -> p.username).collect(Collectors.toList()), true);
        Set<String> existingNumbers = loadExistingValues(
                parsed.stream().map(p -> p.studentId).collect(Collectors.toList()), false);

        UserImportVO result = new UserImportVO();
        result.setRole(role);
        result.setTotalCount(parsed.size());
        Map<String, Integer> seenUsernames = new HashMap<>();
        Map<String, Integer> seenNumbers = new HashMap<>();
        List<PendingUser> valid = new ArrayList<>();

        for (PendingUser p : parsed) {
            List<String> reasons = new ArrayList<>();
            boolean countOverflow = valid.size() + result.getFailures().size() >= MAX_IMPORT_ROWS;
            if (countOverflow) {
                result.getFailures().add(new UserImportVO.Failure(p.row, p.username, p.realName,
                        "单次最多导入" + MAX_IMPORT_ROWS + "条，请拆分文件后上传"));
                continue;
            }
            if (p.username.isEmpty()) reasons.add("用户名不能为空");
            else if (!p.username.matches("^[a-zA-Z0-9_]{4,20}$")) reasons.add("用户名须为4-20位字母、数字或下划线");
            if (p.realName.isEmpty()) reasons.add("姓名不能为空");
            else if (p.realName.length() > 50) reasons.add("姓名长度不能超过50个字符");
            if (student) {
                if (p.studentId.isEmpty()) reasons.add("学号不能为空");
                else if (p.studentId.length() > 20) reasons.add("学号长度不能超过20个字符");
                if (p.className == null || p.className.isEmpty()) reasons.add("班级不能为空");
                else if (p.className.length() > 80) reasons.add("班级长度不能超过80个字符");
            } else if (p.studentId.length() > 20) {
                reasons.add("工号长度不能超过20个字符");
            }
            if (!p.password.isEmpty() && !p.password.matches(PASSWORD_PATTERN)) {
                reasons.add("密码须为8-20位并同时包含字母和数字（留空则使用默认密码）");
            }
            Integer firstUserRow = seenUsernames.get(p.username);
            if (!p.username.isEmpty() && firstUserRow != null) reasons.add("用户名与第" + firstUserRow + "行重复");
            if (!p.username.isEmpty() && existingUsernames.contains(p.username)) reasons.add("用户名已存在");
            // 教师工号留空时默认与用户名一致（模板中用户名即工号）
            if (!student && p.studentId.isEmpty()) p.studentId = p.username;
            Integer firstNumberRow = seenNumbers.get(p.studentId);
            if (!p.studentId.isEmpty() && firstNumberRow != null) reasons.add(numberLabel + "与第" + firstNumberRow + "行重复");
            if (!p.studentId.isEmpty() && existingNumbers.contains(p.studentId)) reasons.add(numberLabel + "已存在");

            if (reasons.isEmpty()) {
                seenUsernames.put(p.username, p.row);
                seenNumbers.put(p.studentId, p.row);
                if (p.password.isEmpty()) p.password = DEFAULT_IMPORT_PASSWORD;
                valid.add(p);
            } else {
                result.getFailures().add(new UserImportVO.Failure(p.row, p.username, p.realName, String.join("；", reasons)));
            }
        }

        result.setSuccessCount(valid.size());
        result.setFailCount(result.getFailures().size());
        if (!result.getFailures().isEmpty()) {
            // 存在校验失败：整批不入库，保证事务原子性
            result.setRolledBack(true);
            log.info("批量导入{}校验未通过：成功{}条，失败{}条，整批回滚", role, result.getSuccessCount(), result.getFailCount());
            return result;
        }

        // 全部合法：事务内批量写入，任一写入异常则整批回滚
        Map<String, String> encodedPasswords = new HashMap<>();
        int[] inserted = {0};
        try {
            transactionTemplate.executeWithoutResult(status -> {
                for (PendingUser p : valid) {
                    User user = new User();
                    user.setUsername(p.username);
                    user.setRealName(p.realName);
                    user.setStudentId(p.studentId);
                    user.setClassName(student ? p.className : null);
                    user.setRole(role);
                    user.setStatus(1);
                    user.setPassword(encodedPasswords.computeIfAbsent(p.password, passwordEncoder::encode));
                    try {
                        userMapper.insert(user);
                        inserted[0]++;
                    } catch (Exception ex) {
                        log.warn("批量导入写入数据库失败，整批回滚: {}", ex.getMessage());
                        result.getFailures().add(new UserImportVO.Failure(p.row, p.username, p.realName,
                                "写入数据库失败：数据可能超长或与已有记录冲突"));
                        status.setRollbackOnly();
                        break;
                    }
                }
            });
        } catch (TransactionSystemException e) {
            // 事务标记回滚后提交抛出，属预期行为
            log.info("批量导入{}已整批回滚", role);
        }
        boolean rolledBack = !result.getFailures().isEmpty();
        result.setRolledBack(rolledBack);
        result.setSuccessCount(rolledBack ? 0 : inserted[0]);
        result.setFailCount(result.getFailures().size());
        log.info("批量导入{}完成：成功{}条，失败{}条，回滚={}", role, result.getSuccessCount(), result.getFailCount(), rolledBack);
        return result;
    }

    /** 表头定位：匹配包含关键字的列。 */
    private int headerIndex(List<String> header, String key) {
        for (int i = 0; i < header.size(); i++) {
            if (normalizeHeader(header.get(i)).contains(key)) return i;
        }
        return -1;
    }

    /** 学号/工号列定位：排除“用户名（学号/工号）”列本身。 */
    private int headerNumberIndex(List<String> header, String key) {
        for (int i = 0; i < header.size(); i++) {
            String text = normalizeHeader(header.get(i));
            if (text.contains(key) && !text.contains("用户名")) return i;
        }
        return -1;
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.replaceAll("[\\s*＊:：]", "");
    }

    private String cell(List<String> row, int index) {
        if (index < 0 || index >= row.size() || row.get(index) == null) return "";
        return row.get(index).trim();
    }

    private Set<String> loadExistingValues(List<String> values, boolean usernameColumn) {
        Set<String> result = new HashSet<>();
        List<String> distinct = values.stream()
                .filter(v -> v != null && !v.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        for (int i = 0; i < distinct.size(); i += QUERY_BATCH) {
            List<String> chunk = distinct.subList(i, Math.min(i + QUERY_BATCH, distinct.size()));
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            if (usernameColumn) {
                wrapper.select(User::getUsername).in(User::getUsername, chunk);
                userMapper.selectList(wrapper).forEach(u -> result.add(u.getUsername()));
            } else {
                wrapper.select(User::getStudentId).isNotNull(User::getStudentId).in(User::getStudentId, chunk);
                userMapper.selectList(wrapper).forEach(u -> result.add(u.getStudentId()));
            }
        }
        return result;
    }

    /** 导入解析中的待入库用户。 */
    private static class PendingUser {
        private int row;
        private String username = "";
        private String realName = "";
        private String studentId = "";
        private String className = "";
        private String password = "";

        private boolean isBlank() {
            return username.isEmpty() && realName.isEmpty() && studentId.isEmpty()
                    && (className == null || className.isEmpty()) && password.isEmpty();
        }
    }

    private void validateManagedRequest(AdminUserRequest request, boolean creating, Long currentId) {
        if (creating && (request.getPassword() == null || !request.getPassword().matches(PASSWORD_PATTERN))) {
            throw new BusinessException("初始密码需为8至20位，并同时包含字母和数字");
        }
        if (request.getStatus() != null && request.getStatus() != 0 && request.getStatus() != 1) {
            throw new BusinessException("状态值只能是0或1");
        }
        if ("STUDENT".equals(request.getRole()) && trimToNull(request.getStudentId()) == null) {
            throw new BusinessException("学生必须填写学号");
        }
        User sameUsername = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername().trim()).last("LIMIT 1"));
        if (sameUsername != null && !sameUsername.getId().equals(currentId)) throw new BusinessException("用户名已存在");
        String number = trimToNull(request.getStudentId());
        if (number != null) {
            User sameNumber = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getStudentId, number).last("LIMIT 1"));
            if (sameNumber != null && !sameNumber.getId().equals(currentId)) throw new BusinessException("学号或工号已存在");
        }
    }

    private void ensureManagedRole(User user) {
        if ("ADMIN".equals(user.getRole())) throw new BusinessException(403, "此处只允许管理学生和教师账号");
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private UserVO convertToVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        vo.setFaceRegistered(user.getFaceFeatures() != null && !user.getFaceFeatures().isEmpty());
        return vo;
    }
}
