USE attendance_system;

-- 更新管理员密码 admin123
UPDATE user SET password = '$2b$10$IUl32ayOlyDjf0MbPjKztOB.m3Mgtn2G8XeqF1U3RDtX67RPwnVnK' WHERE username = 'admin';

-- 更新所有其他用户的密码为 password123
UPDATE user SET password = '$2b$10$AuMjWDuWvQxQGFHGycj/VObi9Zxe8NDxRHuOLMktsrL5mzy12ZkPq' WHERE username != 'admin';

SELECT username, real_name, role FROM user;
