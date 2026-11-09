package com.attendance.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/** The only runtime-tunable forecast setting; never load secrets from Nacos. */
@Component
public class PredictionCachePolicy {
    private final AtomicInteger ttlSeconds;

    public PredictionCachePolicy(@Value("${attendance.prediction.cache-ttl-seconds:30}") int initialTtlSeconds) {
        ttlSeconds = new AtomicInteger(validTtl(initialTtlSeconds));
    }

    public Duration ttl() {
        return Duration.ofSeconds(ttlSeconds.get());
    }

    public int ttlSeconds() {
        return ttlSeconds.get();
    }

    public void applyNacosProperties(String content) {
        if (content == null || content.isBlank()) return;
        try {
            Properties properties = new Properties();
            properties.load(new StringReader(content));
            String value = properties.getProperty("attendance.prediction.cache-ttl-seconds");
            if (value != null) ttlSeconds.set(validTtl(Integer.parseInt(value.trim())));
        } catch (Exception error) {
            throw new IllegalArgumentException("Nacos 缺勤预测缓存配置无效", error);
        }
    }

    private int validTtl(int seconds) {
        if (seconds < 1 || seconds > 3600) throw new IllegalArgumentException("预测缓存 TTL 应在 1 至 3600 秒之间");
        return seconds;
    }
}
