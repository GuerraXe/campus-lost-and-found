package com.campuslostfound.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuslostfound.config.AuthProperties;
import com.campuslostfound.domain.Role;
import com.campuslostfound.domain.User;
import io.jsonwebtoken.JwtException;
import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtService service = newService(1800);

    private static JwtService newService(long ttl) {
        AuthProperties p = new AuthProperties();
        p.setJwtSecret("test-secret-test-secret-test-secret-0123456789");
        p.setJwtTtlSeconds(ttl);
        return new JwtService(p);
    }

    private static User user(long id, Role role, Instant pwdChangedAt) {
        User u = new User("a@b.edu", "Alex", "hash", role);
        setField(u, "id", id);
        setField(u, "passwordChangedAt", pwdChangedAt);
        return u;
    }

    @Test
    void issuesAndParsesRoundTrip() {
        User u = user(42L, Role.MODERATOR, Instant.parse("2026-01-01T00:00:00Z"));

        JwtService.IssuedToken issued = service.issue(u);
        JwtService.ParsedToken parsed = service.parse(issued.token());

        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.role()).isEqualTo("MODERATOR");
        assertThat(parsed.passwordChangedAtMillis())
                .isEqualTo(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli());
        assertThat(issued.expiresInSeconds()).isEqualTo(1800);
    }

    @Test
    void rejectsTamperedToken() {
        String token = service.issue(user(1L, Role.USER, Instant.now())).token();
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() throws InterruptedException {
        JwtService shortLived = newService(1);
        String token = shortLived.issue(user(1L, Role.USER, Instant.now())).token();
        Thread.sleep(1200);

        assertThatThrownBy(() -> shortLived.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithADifferentSecret() {
        String token = service.issue(user(1L, Role.USER, Instant.now())).token();
        JwtService other = newService(1800);
        setSecret(other, "another-secret-another-secret-another-xyz-99");

        assertThatThrownBy(() -> other.parse(token)).isInstanceOf(JwtException.class);
    }

    private static void setSecret(JwtService service, String secret) {
        try {
            Field keyField = JwtService.class.getDeclaredField("key");
            keyField.setAccessible(true);
            keyField.set(service, io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                    secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        throw new IllegalArgumentException("no field " + name);
    }
}
