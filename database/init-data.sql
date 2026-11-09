-- ============================================
-- 人脸识别考勤系统 - 初始数据
-- ============================================

USE lianzhi_attendance;

-- ============================================
-- 插入默认管理员账号
-- ============================================
-- 用户名: admin
-- 密码: admin123 (BCrypt加密后)
INSERT INTO `user` (`username`, `password`, `real_name`, `role`, `status`)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 'ADMIN', 1);

-- ============================================
-- 插入测试教师账号
-- ============================================
-- 用户名: teacher1
-- 密码: password123
INSERT INTO `user` (`username`, `password`, `real_name`, `role`, `status`)
VALUES ('teacher1', '$2a$10$rE.LRjLZxZ9BvGZQ.x8Nnu.uRyZXhYZTfJHXk9XzB0BwYjGM5x', '张老师', 'TEACHER', 1);

-- 用户名: teacher2
-- 密码: password123
INSERT INTO `user` (`username`, `password`, `real_name`, `role`, `status`)
VALUES ('teacher2', '$2a$10$rE.LRjLZxZ9BvGZQ.x8Nnu.uRyZXhYZTfJHXk9XzB0BwYjGM5x', '李老师', 'TEACHER', 1);

-- ============================================
-- 插入测试学生账号
-- ============================================
-- 用户名: student1, 学号: 2024001
-- 密码: password123
INSERT INTO `user` (`username`, `password`, `real_name`, `student_id`, `role`, `status`)
VALUES ('student1', '$2a$10$rE.LRjLZxZ9BvGZQ.x8Nnu.uRyZXhYZTfJHXk9XzB0BwYjGM5x', '王同学', '2024001', 'STUDENT', 1);

-- 用户名: student2, 学号: 2024002
-- 密码: password123
INSERT INTO `user` (`username`, `password`, `real_name`, `student_id`, `role`, `status`)
VALUES ('student2', '$2a$10$rE.LRjLZxZ9BvGZQ.x8Nnu.uRyZXhYZTfJHXk9XzB0BwYjGM5x', '李同学', '2024002', 'STUDENT', 1);

-- 用户名: student3, 学号: 2024003
-- 密码: password123
INSERT INTO `user` (`username`, `password`, `real_name`, `student_id`, `role`, `status`)
VALUES ('student3', '$2a$10$rE.LRjLZxZ9BvGZQ.x8Nnu.uRyZXhYZTfJHXk9XzB0BwYjGM5x', '张同学', '2024003', 'STUDENT', 1);

-- 用户名: student4, 学号: 2024004
-- 密码: password123
INSERT INTO `user` (`username`, `password`, `real_name`, `student_id`, `role`, `status`)
VALUES ('student4', '$2a$10$rE.LRjLZxZ9BvGZQ.x8Nnu.uRyZXhYZTfJHXk9XzB0BwYjGM5x', '刘同学', '2024004', 'STUDENT', 1);

-- 用户名: student5, 学号: 2024005
-- 密码: password123
INSERT INTO `user` (`username`, `password`, `real_name`, `student_id`, `role`, `status`)
VALUES ('student5', '$2a$10$rE.LRjLZxZ9BvGZQ.x8Nnu.uRyZXhYZTfJHXk9XzB0BwYjGM5x', '陈同学', '2024005', 'STUDENT', 1);

-- ============================================
-- 插入测试课程
-- ============================================
-- 课程1: 高等数学 (教师: 张老师)
INSERT INTO `course` (`course_name`, `course_code`, `teacher_id`, `classroom`, `start_time`, `end_time`, `week_day`, `semester`, `max_students`)
VALUES ('高等数学', 'CS101', 2, '教学楼A101', '08:00:00', '09:40:00', 1, '2024-2025-1', 50);

-- 课程2: 数据结构 (教师: 李老师)
INSERT INTO `course` (`course_name`, `course_code`, `teacher_id`, `classroom`, `start_time`, `end_time`, `week_day`, `semester`, `max_students`)
VALUES ('数据结构', 'CS102', 3, '教学楼B202', '10:00:00', '11:40:00', 2, '2024-2025-1', 50);

-- 课程3: 操作系统 (教师: 李老师)
INSERT INTO `course` (`course_name`, `course_code`, `teacher_id`, `classroom`, `start_time`, `end_time`, `week_day`, `semester`, `max_students`)
VALUES ('操作系统', 'CS103', 3, '教学楼C303', '14:00:00', '15:40:00', 3, '2024-2025-1', 50);

-- 课程4: 计算机网络 (教师: 张老师)
INSERT INTO `course` (`course_name`, `course_code`, `teacher_id`, `classroom`, `start_time`, `end_time`, `week_day`, `semester`, `max_students`)
VALUES ('计算机网络', 'CS104', 2, '教学楼D404', '16:00:00', '17:40:00', 4, '2024-2025-1', 50);

-- ============================================
-- 学校预先安排的课程学生关系（测试数据）
-- ============================================
-- 学生1课程
INSERT INTO `student_course` (`student_id`, `course_id`)
VALUES (4, 1), (4, 2), (4, 3);

-- 学生2课程
INSERT INTO `student_course` (`student_id`, `course_id`)
VALUES (5, 1), (5, 2);

-- 学生3课程
INSERT INTO `student_course` (`student_id`, `course_id`)
VALUES (6, 2), (6, 3), (6, 4);

-- 学生4课程
INSERT INTO `student_course` (`student_id`, `course_id`)
VALUES (7, 1), (7, 3);

-- 学生5课程
INSERT INTO `student_course` (`student_id`, `course_id`)
VALUES (8, 2), (8, 4);

-- ============================================
-- 插入测试考勤记录
-- ============================================
-- 为课程1插入今天的考勤记录 (模拟部分学生签到)
INSERT INTO `attendance` (`course_id`, `student_id`, `attendance_date`, `status`, `check_in_time`, `is_liveness`, `confidence`)
VALUES
  (1, 4, CURDATE(), 'PRESENT', DATE_SUB(NOW(), INTERVAL 2 HOUR), 1, 0.9234),
  (1, 5, CURDATE(), 'LATE', DATE_SUB(NOW(), INTERVAL 1 HOUR), 1, 0.9156),
  (1, 7, CURDATE(), 'PRESENT', DATE_SUB(NOW(), INTERVAL 90 MINUTE), 1, 0.8978);

-- 为课程2插入今天的考勤记录
INSERT INTO `attendance` (`course_id`, `student_id`, `attendance_date`, `status`, `check_in_time`, `is_liveness`, `confidence`)
VALUES
  (2, 4, CURDATE(), 'PRESENT', DATE_SUB(NOW(), INTERVAL 3 HOUR), 1, 0.9345),
  (2, 5, CURDATE(), 'PRESENT', DATE_SUB(NOW(), INTERVAL 2.5 HOUR), 1, 0.9123),
  (2, 6, CURDATE(), 'PRESENT', DATE_SUB(NOW(), INTERVAL 2.8 HOUR), 1, 0.9289),
  (2, 8, CURDATE(), 'ABSENT', NULL, 0, NULL);

-- ============================================
-- 完成提示
-- ============================================
-- 初始数据插入完成

-- 默认账号信息:
-- 管理员: admin / admin123
-- 教师1: teacher1 / password123
-- 教师2: teacher2 / password123
-- 学生1: student1 / password123
-- 学生2: student2 / password123
-- 学生3: student3 / password123
-- 学生4: student4 / password123
-- 学生5: student5 / password123

-- 测试课程:
-- CS101 - 高等数学 (周一 08:00-09:40)
-- CS102 - 数据结构 (周二 10:00-11:40)
-- CS103 - 操作系统 (周三 14:00-15:40)
-- CS104 - 计算机网络 (周四 16:00-17:40)
