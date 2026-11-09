USE lianzhi_attendance;

CREATE TABLE IF NOT EXISTS `student_group` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `group_name` VARCHAR(50) NOT NULL,
  `leader_id` BIGINT NOT NULL UNIQUE,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT `fk_group_leader` FOREIGN KEY (`leader_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生小组';

CREATE TABLE IF NOT EXISTS `student_group_member` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `group_id` BIGINT NOT NULL,
  `student_id` BIGINT NOT NULL UNIQUE,
  `joined_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_group_member` (`group_id`, `student_id`),
  CONSTRAINT `fk_group_member_group` FOREIGN KEY (`group_id`) REFERENCES `student_group` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_group_member_student` FOREIGN KEY (`student_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小组成员';
