-- Run after schema.sql when using MySQL (local H2 creates these tables automatically).
USE lianzhi_attendance;

CREATE TABLE IF NOT EXISTS office (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL UNIQUE,
  company_name VARCHAR(100) NOT NULL,
  name VARCHAR(100) NOT NULL,
  address VARCHAR(255) NOT NULL,
  latitude DECIMAL(10,7),
  longitude DECIMAL(10,7),
  status TINYINT DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS employee_office (
  user_id BIGINT PRIMARY KEY,
  office_id BIGINT NOT NULL,
  CONSTRAINT fk_enterprise_employee FOREIGN KEY (user_id) REFERENCES user(id),
  CONSTRAINT fk_enterprise_office FOREIGN KEY (office_id) REFERENCES office(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS work_attendance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  office_id BIGINT NOT NULL,
  work_date DATE NOT NULL,
  check_in_time DATETIME NOT NULL,
  check_out_time DATETIME,
  method VARCHAR(12) NOT NULL,
  status VARCHAR(12) NOT NULL,
  confidence DECIMAL(6,4),
  KEY idx_work_attendance_user_date (user_id, work_date),
  KEY idx_work_attendance_office_date (office_id, work_date),
  CONSTRAINT fk_enterprise_attendance_user FOREIGN KEY (user_id) REFERENCES user(id),
  CONSTRAINT fk_enterprise_attendance_office FOREIGN KEY (office_id) REFERENCES office(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
