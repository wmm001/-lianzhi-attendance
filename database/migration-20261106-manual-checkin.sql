-- Administrator backdated check-ins are separate records with an immutable audit row.
-- Run once after the enterprise tables exist. Existing attendance rows are untouched.
CREATE TABLE IF NOT EXISTS manual_attendance_audit (
  attendance_id BIGINT PRIMARY KEY,
  operator_id BIGINT NOT NULL,
  reason VARCHAR(500) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_manual_attendance FOREIGN KEY (attendance_id) REFERENCES work_attendance(id),
  CONSTRAINT fk_manual_operator FOREIGN KEY (operator_id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
