package com.attendance.service.impl;

import com.attendance.common.PageResult;
import com.attendance.common.ResultCode;
import com.attendance.dto.CourseDTO;
import com.attendance.entity.Course;
import com.attendance.entity.StudentCourse;
import com.attendance.entity.User;
import com.attendance.exception.BusinessException;
import com.attendance.mapper.CourseMapper;
import com.attendance.mapper.StudentCourseMapper;
import com.attendance.mapper.UserMapper;
import com.attendance.service.CourseService;
import com.attendance.vo.CourseVO;
import com.attendance.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CourseServiceImpl implements CourseService {

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private StudentCourseMapper studentCourseMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String[] WEEK_DAYS = {"", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};

    @Override
    public CourseVO getCourseById(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(ResultCode.COURSE_NOT_FOUND);
        }
        return convertToVO(course);
    }

    @Override
    public PageResult<CourseVO> getCourseList(Integer page, Integer size, String keyword, Long teacherId, Integer status) {
        Page<Course> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Course::getCourseName, keyword)
                    .or().like(Course::getCourseCode, keyword));
        }
        if (teacherId != null) {
            wrapper.eq(Course::getTeacherId, teacherId);
        }
        if (status != null) wrapper.eq(Course::getStatus, status);
        wrapper.orderByDesc(Course::getCreatedAt);
        
        Page<Course> coursePage = courseMapper.selectPage(pageParam, wrapper);
        List<CourseVO> voList = coursePage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        
        return PageResult.of(coursePage.getTotal(), voList, (long) page, (long) size);
    }

    @Override
    @Transactional
    public Long createCourse(CourseDTO courseDTO, Long teacherId) {
        requireActiveTeacher(teacherId);
        Course course = new Course();
        BeanUtils.copyProperties(courseDTO, course);
        course.setTeacherId(teacherId);
        course.setStartTime(LocalTime.parse(courseDTO.getStartTime()));
        course.setEndTime(LocalTime.parse(courseDTO.getEndTime()));
        course.setStatus(1);
        
        courseMapper.insert(course);
        log.info("创建课程成功: {}", course.getCourseName());
        return course.getId();
    }

    @Override
    @Transactional
    public void updateCourse(Long id, CourseDTO courseDTO) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(ResultCode.COURSE_NOT_FOUND);
        }
        
        if (courseDTO.getTeacherId() != null) {
            requireActiveTeacher(courseDTO.getTeacherId());
            course.setTeacherId(courseDTO.getTeacherId());
        }
        course.setCourseName(courseDTO.getCourseName());
        course.setCourseCode(courseDTO.getCourseCode());
        course.setClassroom(courseDTO.getClassroom());
        course.setStartTime(LocalTime.parse(courseDTO.getStartTime()));
        course.setEndTime(LocalTime.parse(courseDTO.getEndTime()));
        course.setWeekDay(courseDTO.getWeekDay());
        course.setSemester(courseDTO.getSemester());
        course.setMaxStudents(courseDTO.getMaxStudents());
        
        courseMapper.updateById(course);
        log.info("更新课程成功: {}", id);
    }

    @Override
    @Transactional
    public void deleteCourse(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(ResultCode.COURSE_NOT_FOUND);
        }
        
        course.setStatus(0);
        courseMapper.updateById(course);
        log.info("停用课程成功: {}", id);
    }

    @Override
    @Transactional
    public void updateCourseStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) throw new BusinessException("状态值只能是0或1");
        Course course = courseMapper.selectById(id);
        if (course == null) throw new BusinessException(ResultCode.COURSE_NOT_FOUND);
        course.setStatus(status);
        courseMapper.updateById(course);
    }

    @Override
    @Transactional
    public void permanentlyDeleteCourse(Long id) {
        if (courseMapper.selectById(id) == null) throw new BusinessException(ResultCode.COURSE_NOT_FOUND);
        jdbcTemplate.update("DELETE FROM attendance_session_member WHERE course_id=?", id);
        jdbcTemplate.update("DELETE FROM attendance_session WHERE course_id=?", id);
        jdbcTemplate.update("DELETE FROM leave_request WHERE course_id=?", id);
        jdbcTemplate.update("DELETE FROM attendance WHERE course_id=?", id);
        jdbcTemplate.update("DELETE FROM student_course WHERE course_id=?", id);
        jdbcTemplate.update("DELETE FROM student_group_member WHERE group_id IN (SELECT id FROM student_group WHERE course_id=?)", id);
        jdbcTemplate.update("DELETE FROM student_group WHERE course_id=?", id);
        courseMapper.deleteById(id);
    }

    @Override
    public List<UserVO> getCourseStudents(Long courseId) {
        LambdaQueryWrapper<StudentCourse> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentCourse::getCourseId, courseId)
                .eq(StudentCourse::getStatus, 1);
        
        List<StudentCourse> studentCourses = studentCourseMapper.selectList(wrapper);
        List<UserVO> studentList = new ArrayList<>();
        
        for (StudentCourse sc : studentCourses) {
            User user = userMapper.selectById(sc.getStudentId());
            if (user != null) {
                UserVO vo = new UserVO();
                BeanUtils.copyProperties(user, vo);
                vo.setFaceRegistered(user.getFaceFeatures() != null && !user.getFaceFeatures().isEmpty());
                studentList.add(vo);
            }
        }
        
        return studentList;
    }

    @Override
    @Transactional
    public void addCourseStudent(Long courseId, String studentNumber) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) throw new BusinessException(ResultCode.COURSE_NOT_FOUND);
        if (studentNumber == null || studentNumber.trim().isEmpty()) throw new BusinessException("请输入学生学号");
        User student = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getStudentId, studentNumber.trim()).eq(User::getRole, "STUDENT").last("LIMIT 1"));
        if (student == null) throw new BusinessException("未找到该学号对应的学生");
        if (!Integer.valueOf(1).equals(student.getStatus())) throw new BusinessException("该学生账号已停用");
        StudentCourse relation = studentCourseMapper.selectOne(new LambdaQueryWrapper<StudentCourse>()
                .eq(StudentCourse::getStudentId, student.getId()).eq(StudentCourse::getCourseId, courseId).last("LIMIT 1"));
        if (relation == null) {
            relation = new StudentCourse();
            relation.setStudentId(student.getId());
            relation.setCourseId(courseId);
            relation.setStatus(1);
            studentCourseMapper.insert(relation);
        } else if (!Integer.valueOf(1).equals(relation.getStatus())) {
            relation.setStatus(1);
            studentCourseMapper.updateById(relation);
        } else {
            throw new BusinessException("该学生已在课程名单中");
        }
    }

    @Override
    public List<CourseVO> getEnrolledCourses(Long studentId) {
        LambdaQueryWrapper<StudentCourse> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentCourse::getStudentId, studentId)
                .eq(StudentCourse::getStatus, 1);
        
        List<StudentCourse> studentCourses = studentCourseMapper.selectList(wrapper);
        List<CourseVO> courseList = new ArrayList<>();
        
        for (StudentCourse sc : studentCourses) {
            Course course = courseMapper.selectById(sc.getCourseId());
            if (course != null && Integer.valueOf(1).equals(course.getStatus())) {
                courseList.add(convertToVO(course));
            }
        }
        
        return courseList;
    }

    private CourseVO convertToVO(Course course) {
        CourseVO vo = new CourseVO();
        BeanUtils.copyProperties(course, vo);
        
        // 设置星期名称
        if (course.getWeekDay() != null && course.getWeekDay() >= 1 && course.getWeekDay() <= 7) {
            vo.setWeekDayName(WEEK_DAYS[course.getWeekDay()]);
        }
        
        // 设置教师姓名
        if (course.getTeacherId() != null) {
            User teacher = userMapper.selectById(course.getTeacherId());
            if (teacher != null) {
                vo.setTeacherName(teacher.getRealName());
            }
        }
        
        // 设置课程人数
        LambdaQueryWrapper<StudentCourse> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentCourse::getCourseId, course.getId())
                .eq(StudentCourse::getStatus, 1);
        vo.setEnrolledCount(studentCourseMapper.selectCount(wrapper).intValue());
        
        return vo;
    }

    private void requireActiveTeacher(Long teacherId) {
        User teacher = userMapper.selectById(teacherId);
        if (teacher == null || !"TEACHER".equals(teacher.getRole())) throw new BusinessException("授课教师不存在");
        if (!Integer.valueOf(1).equals(teacher.getStatus())) throw new BusinessException("授课教师账号已停用");
    }
}
