package com.campuslostfound.web.dto;

import com.campuslostfound.web.validation.SafeText;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Request/response shapes for authentication and the current-user endpoint. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 100) String password,
            @NotBlank @SafeText @Size(min = 2, max = 60) String displayName) {
    }

    public record RegisterResponse(Long userId, String email, String displayName,
                                   /* present only when no mailer is configured */ String verificationToken) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String password) {
    }

    public record VerifyEmailRequest(@NotBlank @Size(max = 200) String token) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, boolean emailVerified) {
    }

    public record UserResponse(Long id, String email, String displayName, String role,
                               boolean emailVerified, Instant createdAt) {
    }
}
