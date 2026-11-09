USE lianzhi_attendance;

ALTER TABLE `user` ADD COLUMN `face_reenroll_allowed` TINYINT DEFAULT 0;
ALTER TABLE `student_group` ADD COLUMN `course_id` BIGINT NULL;
ALTER TABLE `student_group` DROP INDEX `leader_id`;
ALTER TABLE `student_group_member` DROP INDEX `student_id`;

CREATE TABLE IF NOT EXISTS `face_reenroll_request` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `student_id` BIGINT NOT NULL,
  `reason` VARCHAR(500),
  `status` VARCHAR(20) DEFAULT 'PENDING',
  `reviewer_id` BIGINT,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `reviewed_at` DATETIME NULL,
  INDEX `idx_face_reenroll_student_status` (`student_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
