package com.campuslostfound.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code campus.auth.*}. */
@ConfigurationProperties(prefix = "campus.auth")
public class AuthProperties {

    /** HS256 signing secret. Must be at least 32 bytes; startup fails otherwise. */
    private String jwtSecret;

    private long jwtTtlSeconds = 1800;

    /**
     * When true, {@code POST /auth/register} returns the email-verification token in its
     * response so the flow works without a mail server. Set false once SMTP is wired.
     */
    private boolean exposeVerificationToken = true;

    private int verificationTtlHours = 48;

    private boolean bootstrapAdmin = false;
    private String adminEmail = "";
    private String adminPassword = "";
    private String adminDisplayName = "Administrator";

    private int loginMaxFailures = 5;
    private int loginLockoutMinutes = 15;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getJwtTtlSeconds() {
        return jwtTtlSeconds;
    }

    public void setJwtTtlSeconds(long jwtTtlSeconds) {
        this.jwtTtlSeconds = jwtTtlSeconds;
    }

    public boolean isExposeVerificationToken() {
        return exposeVerificationToken;
    }

    public void setExposeVerificationToken(boolean exposeVerificationToken) {
        this.exposeVerificationToken = exposeVerificationToken;
    }

    public int getVerificationTtlHours() {
        return verificationTtlHours;
    }

    public void setVerificationTtlHours(int verificationTtlHours) {
        this.verificationTtlHours = verificationTtlHours;
    }

    public boolean isBootstrapAdmin() {
        return bootstrapAdmin;
    }

    public void setBootstrapAdmin(boolean bootstrapAdmin) {
        this.bootstrapAdmin = bootstrapAdmin;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getAdminDisplayName() {
        return adminDisplayName;
    }

    public void setAdminDisplayName(String adminDisplayName) {
        this.adminDisplayName = adminDisplayName;
    }

    public int getLoginMaxFailures() {
        return loginMaxFailures;
    }

    public void setLoginMaxFailures(int loginMaxFailures) {
        this.loginMaxFailures = loginMaxFailures;
    }

    public int getLoginLockoutMinutes() {
        return loginLockoutMinutes;
    }

    public void setLoginLockoutMinutes(int loginLockoutMinutes) {
        this.loginLockoutMinutes = loginLockoutMinutes;
    }
}
