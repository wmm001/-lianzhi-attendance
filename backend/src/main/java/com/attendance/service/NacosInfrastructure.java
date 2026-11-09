package com.attendance.service;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/** Optional Nacos config listener and service registration for multi-service deployments. */
@Component
@ConditionalOnProperty(prefix = "attendance.nacos", name = "enabled", havingValue = "true")
public class NacosInfrastructure implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(NacosInfrastructure.class);
    private static final String DATA_ID = "attendance-system.properties";
    private static final String GROUP = "DEFAULT_GROUP";
    private final PredictionCachePolicy cachePolicy;

    @Value("${attendance.nacos.server-addr:127.0.0.1:8848}") private String serverAddress;
    @Value("${attendance.nacos.register:false}") private boolean register;
    @Value("${attendance.nacos.service-name:attendance-backend}") private String serviceName;
    @Value("${attendance.nacos.username:}") private String username;
    @Value("${attendance.nacos.password:}") private String password;
    @Value("${server.port:8080}") private int port;

    private volatile ConfigService configService;
    private volatile NamingService namingService;
    private volatile Instance registeredInstance;

    public NacosInfrastructure(PredictionCachePolicy cachePolicy) {
        this.cachePolicy = cachePolicy;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        connectIfNeeded();
    }

    @Scheduled(initialDelay = 15000, fixedDelay = 15000)
    public void retryIfNeeded() {
        connectIfNeeded();
    }

    private synchronized void connectIfNeeded() {
        if (configService != null) return;
        ConfigService config = null;
        NamingService naming = null;
        try {
            Properties client = new Properties();
            client.setProperty(PropertyKeyConst.SERVER_ADDR, serverAddress);
            if (!username.isBlank()) client.setProperty(PropertyKeyConst.USERNAME, username);
            if (!password.isBlank()) client.setProperty(PropertyKeyConst.PASSWORD, password);
            config = NacosFactory.createConfigService(client);
            String content = config.getConfig(DATA_ID, GROUP, 3000);
            if (content == null || content.isBlank()) {
                content = "attendance.prediction.cache-ttl-seconds=30\n";
                if (!config.publishConfig(DATA_ID, GROUP, content))
                    throw new IllegalStateException("Nacos 默认配置发布失败");
            }
            applyConfig(content);
            config.addListener(DATA_ID, GROUP, new Listener() {
                @Override public Executor getExecutor() { return null; }
                @Override public void receiveConfigInfo(String updated) { applyConfig(updated); }
            });
            if (register) {
                naming = NacosFactory.createNamingService(client);
                Instance instance = new Instance();
                instance.setIp(InetAddress.getLocalHost().getHostAddress());
                instance.setPort(port);
                instance.setEphemeral(true);
                instance.setMetadata(Map.of("contextPath", "/api"));
                naming.registerInstance(serviceName, instance);
                registeredInstance = instance;
            }
            namingService = naming;
            configService = config;
            log.info("Nacos connected at {}; config={} (TTL {}s), serviceRegistration={}",
                    serverAddress, DATA_ID, cachePolicy.ttlSeconds(), register);
        } catch (Exception error) {
            log.warn("Nacos unavailable; continuing with local configuration: {}", error.toString());
            closeQuietly(naming, config);
            registeredInstance = null;
        }
    }

    private void applyConfig(String content) {
        try {
            cachePolicy.applyNacosProperties(content);
            log.info("Nacos forecast cache TTL is now {}s", cachePolicy.ttlSeconds());
        } catch (IllegalArgumentException error) {
            log.warn("Ignoring invalid Nacos configuration: {}", error.getMessage());
        }
    }

    @Override
    public synchronized void destroy() {
        if (namingService != null && registeredInstance != null) {
            try { namingService.deregisterInstance(serviceName, registeredInstance); }
            catch (Exception error) { log.debug("Nacos deregistration failed: {}", error.toString()); }
        }
        closeQuietly(namingService, configService);
        namingService = null;
        configService = null;
    }

    private void closeQuietly(NamingService naming, ConfigService config) {
        try { if (naming != null) naming.shutDown(); }
        catch (Exception error) { log.debug("Nacos naming shutdown failed: {}", error.toString()); }
        try { if (config != null) config.shutDown(); }
        catch (Exception error) { log.debug("Nacos config shutdown failed: {}", error.toString()); }
    }
}
