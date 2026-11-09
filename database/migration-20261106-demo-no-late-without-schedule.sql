-- Demo database only: no shift/start-time is configured, so previous 09:10-based LATE labels were unsupported.
-- Preserve every record and its real check-in/check-out timestamps and manual audit entries.
UPDATE work_attendance SET status='PRESENT' WHERE status='LATE';
