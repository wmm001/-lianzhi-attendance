package com.attendance.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FaceVerificationProofService {
    private final ConcurrentHashMap<String, Long> proofs = new ConcurrentHashMap<>();

    @Value("${attendance.face-proof-valid-seconds:120}")
    private long validSeconds;

    public void issue(Long studentId, Long courseId) {
        proofs.put(key(studentId, courseId), Instant.now().plusSeconds(validSeconds).toEpochMilli());
    }

    public boolean consume(Long studentId, Long courseId) {
        Long expiresAt = proofs.remove(key(studentId, courseId));
        return expiresAt != null && expiresAt >= System.currentTimeMillis();
    }

    private String key(Long studentId, Long courseId) {
        return studentId + ":" + courseId;
    }
}
