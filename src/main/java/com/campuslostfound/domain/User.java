package com.campuslostfound.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * A campus account. Personal data is deliberately minimal: an email (used only for login
 * and account recovery, never shown to other users) and a chosen display name. No phone,
 * address, student ID, or photo is collected (see docs/security.md).
 */
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    /** Any access token issued before this instant is rejected (password change / logout-all). */
    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt = Instant.now();

    /** Set when the account is anonymized; such accounts cannot log in. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected User() {
        // for JPA
    }

    public User(String email, String displayName, String passwordHash, Role role) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.passwordChangedAt = Instant.now();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.passwordChangedAt = Instant.now();
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void markEmailVerified() {
        this.emailVerified = true;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    /** Bump the token cutoff without changing the password (used by logout-all). */
    public void invalidateExistingTokens() {
        this.passwordChangedAt = Instant.now();
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void anonymize(String tombstoneEmail, String randomHash) {
        this.email = tombstoneEmail;
        this.displayName = "Deleted user";
        this.passwordHash = randomHash;
        this.role = Role.USER;
        this.emailVerified = false;
        this.deletedAt = Instant.now();
        this.passwordChangedAt = Instant.now();
    }

    public long getVersion() {
        return version;
    }
}
