package com.attendance;

import com.attendance.service.PredictionCachePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PredictionCachePolicyTest {
    @Test
    void nacosCanChangeTtlButInvalidValuesAreRejected() {
        PredictionCachePolicy policy = new PredictionCachePolicy(30);
        policy.applyNacosProperties("attendance.prediction.cache-ttl-seconds=45\n");
        assertEquals(45, policy.ttlSeconds());
        assertThrows(IllegalArgumentException.class,
                () -> policy.applyNacosProperties("attendance.prediction.cache-ttl-seconds=0\n"));
        assertEquals(45, policy.ttlSeconds());
    }
}
