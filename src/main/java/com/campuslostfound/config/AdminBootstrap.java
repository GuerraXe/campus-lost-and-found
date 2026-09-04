package com.campuslostfound.config;

import com.campuslostfound.domain.Role;
import com.campuslostfound.domain.User;
import com.campuslostfound.repo.UserRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent first-admin bootstrap. Runs on startup only when
 * {@code campus.auth.bootstrap-admin=true} and an email + password are supplied via
 * environment; creates the account only if that email is not already present. This is
 * deliberately not a Flyway migration - migrations are checksummed and must not carry
 * per-environment secrets (see docs/design-decisions.md DD-12).
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AuthProperties props;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(AuthProperties props, UserRepository users, PasswordEncoder passwordEncoder) {
        this.props = props;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.isBootstrapAdmin()) {
            return;
        }
        String email = props.getAdminEmail() == null ? "" : props.getAdminEmail().trim().toLowerCase(Locale.ROOT);
        String password = props.getAdminPassword();
        if (email.isBlank() || password == null || password.isBlank()) {
            log.warn("bootstrap-admin is enabled but admin-email / admin-password are not set; skipping");
            return;
        }
        if (users.existsByEmail(email)) {
            return;
        }
        User admin = new User(email, props.getAdminDisplayName(), passwordEncoder.encode(password), Role.ADMIN);
        admin.markEmailVerified();
        users.save(admin);
        log.info("Bootstrapped ADMIN account {}", email);
    }
}
