package com.attendance.service;

import com.attendance.common.PageResult;
import com.attendance.dto.CourseDTO;
import com.attendance.vo.CourseVO;
import com.attendance.vo.UserVO;

import java.util.List;

/**
 * 课程服务
 */
public interface CourseService {

    /**
     * 获取课程详情
     */
    CourseVO getCourseById(Long id);

    /**
     * 分页查询课程列表
     */
    PageResult<CourseVO> getCourseList(Integer page, Integer size, String keyword, Long teacherId, Integer status);

    /**
     * 创建课程
     */
    Long createCourse(CourseDTO courseDTO, Long teacherId);

    /**
     * 更新课程
     */
    void updateCourse(Long id, CourseDTO courseDTO);

    /**
     * 删除课程
     */
    void deleteCourse(Long id);

    void updateCourseStatus(Long id, Integer status);

    void permanentlyDeleteCourse(Long id);

    /**
     * 获取课程的学生列表
     */
    List<UserVO> getCourseStudents(Long courseId);

    void addCourseStudent(Long courseId, String studentNumber);

    /**
     * 获取学校为学生安排的课程列表
     */
    List<CourseVO> getEnrolledCourses(Long studentId);
}
