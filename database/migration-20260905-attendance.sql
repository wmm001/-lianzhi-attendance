
CREATE TABLE IF NOT EXISTS attendance_session (
 course_id BIGINT NOT NULL, attendance_date DATE NOT NULL, starts_at TIMESTAMP NOT NULL,
 ends_at TIMESTAMP NOT NULL, late_at TIMESTAMP, teacher_id BIGINT NOT NULL,
 settled BOOLEAN DEFAULT FALSE, PRIMARY KEY(course_id,attendance_date)
);
CREATE TABLE IF NOT EXISTS attendance_session_member (
 course_id BIGINT NOT NULL, attendance_date DATE NOT NULL, student_id BIGINT NOT NULL,
 PRIMARY KEY(course_id,attendance_date,student_id)
);

ALTER TABLE user ADD COLUMN class_name VARCHAR(80);
ALTER TABLE leave_request ADD COLUMN start_time TIMESTAMP NULL;
ALTER TABLE leave_request ADD COLUMN end_time TIMESTAMP NULL;
CREATE TABLE IF NOT EXISTS attendance_audit (
 id VARCHAR(36) PRIMARY KEY, category VARCHAR(40) NOT NULL, entity_id BIGINT NOT NULL,
 before_status VARCHAR(30), after_status VARCHAR(30), operator_id BIGINT NOT NULL,
 reason VARCHAR(500), created_at TIMESTAMP NOT NULL
);
