package com.attendance.controller;

import com.attendance.common.PageResult;
import com.attendance.common.Result;
import com.attendance.dto.CourseDTO;
import com.attendance.service.CourseService;
import com.attendance.vo.CourseVO;
import com.attendance.vo.UserVO;
import com.attendance.entity.Course;
import com.attendance.mapper.CourseMapper;
import com.attendance.exception.BusinessException;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.attendance.security.RequestAuthorization.*;

/**
 * 课程控制器
 */
@RestController
@RequestMapping("/courses")
public class CourseController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseMapper courseMapper;

    /**
     * 获取课程列表
     */
    @GetMapping
    public Result<PageResult<CourseVO>> getCourseList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long teacherId,
            HttpServletRequest request) {
        requireRole(request, "ADMIN", "TEACHER");
        Long effectiveTeacherId = "TEACHER".equals(role(request)) ? userId(request) : teacherId;
        PageResult<CourseVO> result = courseService.getCourseList(page, size, keyword, effectiveTeacherId,
                "ADMIN".equals(role(request)) ? 1 : null);
        return Result.success(result);
    }

    /**
     * 获取课程详情
     */
    @GetMapping("/{id}")
    public Result<CourseVO> getCourseById(@PathVariable Long id) {
        CourseVO course = courseService.getCourseById(id);
        return Result.success(course);
    }

    /** 任课老师创建自己的课程。 */
    @PostMapping
    public Result<?> createCourse(@Valid @RequestBody CourseDTO courseDTO,
                                   HttpServletRequest request) {
        requireRole(request, "TEACHER");
        Long courseId = courseService.createCourse(courseDTO, userId(request));
        return Result.success("创建成功", courseId);
    }

    /** 老师更新自己的课程。 */
    @PutMapping("/{id}")
    public Result<?> updateCourse(@PathVariable Long id,
                                   @Valid @RequestBody CourseDTO courseDTO,
                                   HttpServletRequest request) {
        requireTeacherOwner(request, id);
        courseDTO.setTeacherId(userId(request));
        courseService.updateCourse(id, courseDTO);
        return Result.success("更新成功", null);
    }

    /** 永久删除老师自己的课程。 */
    @DeleteMapping("/{id}")
    public Result<?> deleteCourse(@PathVariable Long id, HttpServletRequest request) {
        requireTeacherOwner(request, id);
        courseService.permanentlyDeleteCourse(id);
        return Result.success("删除成功", null);
    }

    @PutMapping("/{id}/status")
    public Result<?> updateStatus(@PathVariable Long id, @RequestParam Integer status, HttpServletRequest request) {
        requireTeacherOwner(request, id);
        courseService.updateCourseStatus(id, status);
        return Result.success(status == 1 ? "课程已恢复" : "课程已停用", null);
    }

    /**
     * 获取课程学生列表（教师）
     */
    @GetMapping("/{id}/students")
    public Result<List<UserVO>> getCourseStudents(@PathVariable Long id, HttpServletRequest request) {
        requireCourseManager(request, id);
        List<UserVO> students = courseService.getCourseStudents(id);
        return Result.success(students);
    }

    /** 教师可给自己的课程添加学生，管理员可给任意课程添加学生。 */
    @PostMapping("/{id}/students")
    public Result<?> addCourseStudent(@PathVariable Long id,
                                      @RequestParam String studentNumber,
                                      HttpServletRequest request) {
        requireCourseManager(request, id);
        courseService.addCourseStudent(id, studentNumber);
        return Result.success("学生已加入课程名单", null);
    }

    /**
     * 获取学校为当前学生安排的课程
     */
    @GetMapping("/my/enrolled")
    public Result<List<CourseVO>> getEnrolledCourses(HttpServletRequest request) {
        requireRole(request, "STUDENT");
        Long studentId = userId(request);
        List<CourseVO> courses = courseService.getEnrolledCourses(studentId);
        return Result.success(courses);
    }

    private void requireCourseManager(HttpServletRequest request, Long courseId) {
        requireRole(request, "TEACHER", "ADMIN");
        Course course = courseMapper.selectById(courseId);
        if (course == null) throw new BusinessException(404, "课程不存在");
        if ("TEACHER".equals(role(request)) && !userId(request).equals(course.getTeacherId())) {
            throw new BusinessException(403, "只能管理自己创建的课程");
        }
    }


    private void requireTeacherOwner(HttpServletRequest request, Long courseId) {
        requireRole(request, "TEACHER");
        Course course = courseMapper.selectById(courseId);
        if (course == null) throw new BusinessException(404, "课程不存在");
        if (!userId(request).equals(course.getTeacherId())) throw new BusinessException(403, "只能管理自己创建的课程");
    }
}
