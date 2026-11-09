package com.attendance.config;

import com.attendance.security.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Creates the first administrator only for a fresh production database. */
@Component
@Profile("prod")
public class ProductionAdminBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") private String initialPassword;

    public ProductionAdminBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE role='ADMIN'", Integer.class);
        if (count != null && count > 0) return;
        if (initialPassword == null || initialPassword.length() < 12)
            throw new IllegalStateException("Set BOOTSTRAP_ADMIN_PASSWORD to at least 12 characters for the first deployment");
        jdbc.update("INSERT INTO user(username,password,real_name,role,status) VALUES(?,?,?,?,1)",
                "admin", encoder.encode(initialPassword), "系统管理员", "ADMIN");
    }
}
