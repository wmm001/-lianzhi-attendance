-- Run once on an existing lianzhi_attendance MySQL database.
-- Keep an ordinary lookup index while allowing multiple records per employee and day.
USE lianzhi_attendance;

ALTER TABLE work_attendance
  DROP INDEX uk_work_attendance_user_date,
  ADD INDEX idx_work_attendance_user_date (user_id, work_date);
