package com.attendance.mapper;

import com.attendance.entity.StudentCourse;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程学生关系 Mapper
 */
@Mapper
public interface StudentCourseMapper extends BaseMapper<StudentCourse> {

}
