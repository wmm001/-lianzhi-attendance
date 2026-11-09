INSERT INTO user (id, username, password, real_name, student_id, role, status)
  SELECT 1, 'admin', '$2b$10$IUl32ayOlyDjf0MbPjKztOB.m3Mgtn2G8XeqF1U3RDtX67RPwnVnK', '系统管理员', NULL, 'ADMIN', 1
  WHERE NOT EXISTS (SELECT 1 FROM user WHERE id=1);
INSERT INTO user (id, username, password, real_name, student_id, role, status)
  SELECT 2, 'teacher1', '$2b$10$AuMjWDuWvQxQGFHGycj/VObi9Zxe8NDxRHuOLMktsrL5mzy12ZkPq', '张老师', NULL, 'TEACHER', 1
  WHERE NOT EXISTS (SELECT 1 FROM user WHERE id=2);
INSERT INTO user (id, username, password, real_name, student_id, role, status)
  SELECT 3, 'teacher2', '$2b$10$AuMjWDuWvQxQGFHGycj/VObi9Zxe8NDxRHuOLMktsrL5mzy12ZkPq', '李老师', NULL, 'TEACHER', 1
  WHERE NOT EXISTS (SELECT 1 FROM user WHERE id=3);
INSERT INTO user (id, username, password, real_name, student_id, role, status)
  SELECT 4, 'student1', '$2b$10$AuMjWDuWvQxQGFHGycj/VObi9Zxe8NDxRHuOLMktsrL5mzy12ZkPq', '王晨', 'LZ1001', 'STUDENT', 1
  WHERE NOT EXISTS (SELECT 1 FROM user WHERE id=4);
INSERT INTO user (id, username, password, real_name, student_id, role, status)
  SELECT 5, 'student2', '$2b$10$AuMjWDuWvQxQGFHGycj/VObi9Zxe8NDxRHuOLMktsrL5mzy12ZkPq', '李萌', 'LZ1002', 'STUDENT', 1
  WHERE NOT EXISTS (SELECT 1 FROM user WHERE id=5);

MERGE INTO course (id, course_name, course_code, teacher_id, classroom, start_time, end_time, week_day, semester, max_students, status) KEY(id) VALUES
  (1, '高等数学', 'CS101', 2, '教学楼A101', '08:00:00', '09:40:00', 1, '2024-2025-1', 50, 1),
  (2, '数据结构', 'CS102', 3, '教学楼B202', '10:00:00', '11:40:00', 2, '2024-2025-1', 50, 1),
  (100, '无限制签到测试课（可重复签到）', 'TEST-UNLIMITED', 2, '测试教室', '00:00:00', '23:59:59', 1, '功能测试', 100, 1),
  (101, '全天测试课（星期一）', 'TEST-DAY-1', 2, '测试教室', '00:15:00', '23:29:00', 1, '功能测试', 100, 1),
  (102, '全天测试课（星期二）', 'TEST-DAY-2', 2, '测试教室', '00:15:00', '23:29:00', 2, '功能测试', 100, 1),
  (103, '全天测试课（星期三）', 'TEST-DAY-3', 2, '测试教室', '00:15:00', '23:29:00', 3, '功能测试', 100, 1),
  (104, '全天测试课（星期四）', 'TEST-DAY-4', 2, '测试教室', '00:15:00', '23:29:00', 4, '功能测试', 100, 1),
  (105, '全天测试课（星期五）', 'TEST-DAY-5', 2, '测试教室', '00:15:00', '23:29:00', 5, '功能测试', 100, 1),
  (106, '全天测试课（星期六）', 'TEST-DAY-6', 2, '测试教室', '00:15:00', '23:29:00', 6, '功能测试', 100, 1),
  (107, '全天测试课（星期日）', 'TEST-DAY-7', 2, '测试教室', '00:15:00', '23:29:00', 7, '功能测试', 100, 1);

MERGE INTO student_course (id, student_id, course_id, status) KEY(id) VALUES
  (1, 4, 1, 1), (2, 4, 2, 1), (3, 5, 1, 1),
  (100, 4, 100, 1), (110, 5, 100, 1),
  (101, 4, 101, 1), (102, 4, 102, 1), (103, 4, 103, 1), (104, 4, 104, 1),
  (105, 4, 105, 1), (106, 4, 106, 1), (107, 4, 107, 1),
  (111, 5, 101, 1), (112, 5, 102, 1), (113, 5, 103, 1), (114, 5, 104, 1),
  (115, 5, 105, 1), (116, 5, 106, 1), (117, 5, 107, 1);

INSERT INTO office (code, company_name, name, address, latitude, longitude, status)
  SELECT 'HQ', '郑州轻工业大学', '郑州轻工业大学东风校区', '河南省郑州市金水区东风路5号（东一楼）', 34.796731, 113.656074, 1
  WHERE NOT EXISTS (SELECT 1 FROM office WHERE code = 'HQ');
INSERT INTO office (code, company_name, name, address, latitude, longitude, status)
  SELECT 'BRANCH', '郑州轻工业大学', '郑州轻工业大学学生第二生活园区', '河南省郑州市金水区东风路与勤工路交叉口西南50米（5号楼）', 34.798712, 113.660983, 1
  WHERE NOT EXISTS (SELECT 1 FROM office WHERE code = 'BRANCH');
INSERT INTO employee_office (user_id, office_id) SELECT 4, id FROM office WHERE code = 'HQ' AND NOT EXISTS
  (SELECT 1 FROM employee_office WHERE user_id = 4);
INSERT INTO employee_office (user_id, office_id) SELECT 5, id FROM office WHERE code = 'BRANCH' AND NOT EXISTS
  (SELECT 1 FROM employee_office WHERE user_id = 5);
