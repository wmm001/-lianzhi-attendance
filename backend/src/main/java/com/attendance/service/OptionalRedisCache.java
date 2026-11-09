package com.attendance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Service
public class OptionalRedisCache {
    private static final Logger log = LoggerFactory.getLogger(OptionalRedisCache.class);
    private final ObjectProvider<StringRedisTemplate> redis;
    private final ObjectMapper json;
    @Value("${attendance.redis.enabled:false}") private boolean enabled;

    public OptionalRedisCache(ObjectProvider<StringRedisTemplate> redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public Optional<Map<String, Object>> get(String key) {
        if (!enabled) return Optional.empty();
        try {
            String value = redis.getObject().opsForValue().get(key);
            return value == null ? Optional.empty() : Optional.of(json.readValue(value,
                    new TypeReference<Map<String, Object>>() {}));
        } catch (Exception error) {
            log.debug("Redis unavailable; querying database instead: {}", error.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public void put(String key, Map<String, Object> value, Duration ttl) {
        if (!enabled) return;
        try {
            redis.getObject().opsForValue().set(key, json.writeValueAsString(value), ttl);
        } catch (Exception error) {
            log.debug("Redis unavailable; forecast was returned without cache: {}", error.getClass().getSimpleName());
        }
    }
}
