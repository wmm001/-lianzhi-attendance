package com.attendance.service;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AttendanceAuditService {
    @Autowired private JdbcTemplate jdbc;
    public void record(String category,Long entityId,String before,String after,Long operatorId,String reason) {
        jdbc.update("INSERT INTO attendance_audit(id,category,entity_id,before_status,after_status,operator_id,reason,created_at) VALUES(?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),category,entityId,before,after,operatorId,reason,LocalDateTime.now());
    }
}
