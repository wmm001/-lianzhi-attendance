package com.attendance.mapper;

import com.attendance.entity.Attendance;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 考勤Mapper
 */
@Mapper
public interface AttendanceMapper extends BaseMapper<Attendance> {

    /**
     * 查询学生某课程的今日考勤记录
     */
    @Select("SELECT * FROM attendance WHERE student_id = #{studentId} " +
            "AND course_id = #{courseId} AND attendance_date = CURDATE()")
    Attendance getTodayAttendance(@Param("studentId") Long studentId,
                                   @Param("courseId") Long courseId);
}