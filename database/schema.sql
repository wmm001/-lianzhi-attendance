-- ============================================
-- 人脸识别考勤系统 - 数据库建表脚本
-- ============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS lianzhi_attendance DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE lianzhi_attendance;

-- ============================================
-- 用户表
-- ============================================
CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
  `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名',
  `student_id` VARCHAR(20) UNIQUE COMMENT '学号',
  `role` ENUM('STUDENT', 'TEACHER', 'ADMIN') DEFAULT 'STUDENT' COMMENT '角色',
  `face_features` TEXT COMMENT '人脸特征向量（JSON格式）',
  `face_image_path` VARCHAR(255) COMMENT '人脸图片存储路径',
  `status` TINYINT DEFAULT 1 COMMENT '状态（1:正常 0:禁用）',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_username` (`username`),
  INDEX `idx_student_id` (`student_id`),
  INDEX `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================
-- 课程表
-- ============================================
CREATE TABLE IF NOT EXISTS `course` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '课程ID',
  `course_name` VARCHAR(100) NOT NULL COMMENT '课程名称',
  `course_code` VARCHAR(20) UNIQUE NOT NULL COMMENT '课程代码',
  `teacher_id` BIGINT NOT NULL COMMENT '教师ID',
  `classroom` VARCHAR(50) COMMENT '教室',
  `start_time` TIME NOT NULL COMMENT '开始时间',
  `end_time` TIME NOT NULL COMMENT '结束时间',
  `week_day` TINYINT NOT NULL COMMENT '上课星期（1-7）',
  `semester` VARCHAR(20) COMMENT '学期',
  `max_students` INT DEFAULT 100 COMMENT '最大学生数',
  `status` TINYINT DEFAULT 1 COMMENT '状态（1:正常 0:停用）',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_course_code` (`course_code`),
  INDEX `idx_teacher_id` (`teacher_id`),
  INDEX `idx_week_day` (`week_day`),
  CONSTRAINT `fk_course_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表';

-- ============================================
-- 考勤记录表
-- ============================================
CREATE TABLE IF NOT EXISTS `attendance` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
  `course_id` BIGINT NOT NULL COMMENT '课程ID',
  `student_id` BIGINT NOT NULL COMMENT '学生ID',
  `attendance_date` DATE NOT NULL COMMENT '考勤日期',
  `status` ENUM('PRESENT', 'ABSENT', 'LATE', 'LEAVE') DEFAULT 'PRESENT' COMMENT '考勤状态',
  `check_in_time` DATETIME COMMENT '签到时间',
  `check_out_time` DATETIME COMMENT '签退时间',
  `face_image` VARCHAR(255) COMMENT '人脸图片路径',
  `confidence` DECIMAL(5,4) COMMENT '识别置信度',
  `is_liveness` TINYINT DEFAULT 0 COMMENT '是否通过活体检测',
  `location` VARCHAR(100) COMMENT '签到位置',
  `device_info` VARCHAR(255) COMMENT '设备信息',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_attendance` (`course_id`, `student_id`, `attendance_date`),
  INDEX `idx_course_date` (`course_id`, `attendance_date`),
  INDEX `idx_student_date` (`student_id`, `attendance_date`),
  CONSTRAINT `fk_attendance_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_attendance_student` FOREIGN KEY (`student_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考勤记录表';

-- ============================================
-- 课程学生关系表
-- ============================================
CREATE TABLE IF NOT EXISTS `student_course` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
  `student_id` BIGINT NOT NULL COMMENT '学生ID',
  `course_id` BIGINT NOT NULL COMMENT '课程ID',
  `enroll_date` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入课程时间',
  `status` TINYINT DEFAULT 1 COMMENT '关系状态（1:有效 0:停用）',
  INDEX `idx_student_course` (`student_id`, `course_id`),
  CONSTRAINT `fk_sc_student` FOREIGN KEY (`student_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_sc_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程学生关系表';

CREATE TABLE IF NOT EXISTS `student_group` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `group_name` VARCHAR(50) NOT NULL,
  `course_id` BIGINT NOT NULL,
  `leader_id` BIGINT NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT `fk_group_leader` FOREIGN KEY (`leader_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生小组';

CREATE TABLE IF NOT EXISTS `student_group_member` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `group_id` BIGINT NOT NULL,
  `student_id` BIGINT NOT NULL,
  `joined_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_group_member` (`group_id`, `student_id`),
  CONSTRAINT `fk_group_member_group` FOREIGN KEY (`group_id`) REFERENCES `student_group` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_group_member_student` FOREIGN KEY (`student_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小组成员';

-- ============================================
-- 表结构说明
-- ============================================
-- 1. user表：存储所有用户信息，包括学生、教师和管理员
--    - role字段区分用户角色：STUDENT（学生）、TEACHER（教师）、ADMIN（管理员）
--    - face_features字段存储人脸特征向量（JSON格式）
--    - face_image_path存储人脸图片路径

-- 2. course表：存储课程信息
--    - teacher_id关联用户表，指向授课教师
--    - week_day表示星期几上课（1=星期一，7=星期日）
--    - start_time和end_time定义上课时间段

-- 3. attendance表：存储考勤记录
--    - status字段：PRESENT（出勤）、ABSENT（缺勤）、LATE（迟到）、LEAVE（请假）
--    - is_liveness字段：0=未检测，1=通过活体检测
--    - confidence字段：人脸识别置信度
--    - 唯一约束：同一学生同一课程同一天只能有一条记录

-- 4. student_course表：课程学生关联表
--    - 多对多关系中间表
--    - status字段：1=有效，0=停用

-- ============================================
-- 完成提示
-- ============================================
-- 建表脚本执行完成
-- 请执行 init-data.sql 插入初始数据

CREATE TABLE IF NOT EXISTS attendance_session (
 course_id BIGINT NOT NULL, attendance_date DATE NOT NULL, starts_at DATETIME NOT NULL,
 ends_at DATETIME NOT NULL, late_at DATETIME, teacher_id BIGINT NOT NULL,
 settled BOOLEAN DEFAULT FALSE, PRIMARY KEY(course_id,attendance_date)
);
CREATE TABLE IF NOT EXISTS attendance_session_member (
 course_id BIGINT NOT NULL, attendance_date DATE NOT NULL, student_id BIGINT NOT NULL,
 PRIMARY KEY(course_id,attendance_date,student_id)
);

CREATE TABLE IF NOT EXISTS leave_request (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 student_id BIGINT NOT NULL,
 course_id BIGINT NOT NULL,
 leave_date DATE NOT NULL,
 reason VARCHAR(500) NOT NULL,
 status VARCHAR(20) DEFAULT 'PENDING',
 reviewer_id BIGINT,
 review_comment VARCHAR(500),
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 reviewed_at DATETIME NULL,
 UNIQUE KEY uk_leave_request (student_id,course_id,leave_date),
 CONSTRAINT fk_leave_request_student FOREIGN KEY (student_id) REFERENCES user(id),
 CONSTRAINT fk_leave_request_course FOREIGN KEY (course_id) REFERENCES course(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE user ADD COLUMN class_name VARCHAR(80);
ALTER TABLE user ADD COLUMN face_reenroll_allowed TINYINT DEFAULT 0;
ALTER TABLE leave_request ADD COLUMN start_time TIMESTAMP NULL;
ALTER TABLE leave_request ADD COLUMN end_time TIMESTAMP NULL;
CREATE TABLE IF NOT EXISTS attendance_audit (
 id VARCHAR(36) PRIMARY KEY, category VARCHAR(40) NOT NULL, entity_id BIGINT NOT NULL,
 before_status VARCHAR(30), after_status VARCHAR(30), operator_id BIGINT NOT NULL,
 reason VARCHAR(500), created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS face_reenroll_request (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, student_id BIGINT NOT NULL, reason VARCHAR(500),
 status VARCHAR(20) DEFAULT 'PENDING', reviewer_id BIGINT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 reviewed_at DATETIME NULL
);
