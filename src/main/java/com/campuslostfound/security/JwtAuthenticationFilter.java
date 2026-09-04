package com.campuslostfound.security;

import com.campuslostfound.domain.Role;
import com.campuslostfound.domain.User;
import com.campuslostfound.repo.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns a valid {@code Authorization: Bearer <jwt>} header into an authenticated
 * {@link org.springframework.security.core.context.SecurityContext}. A missing or bad
 * token is simply left unauthenticated - downstream authorization rules decide the
 * response. Deleted accounts and tokens older than the user's last password change are
 * rejected.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository users;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                JwtService.ParsedToken parsed = jwtService.parse(token);
                User user = users.findById(parsed.userId()).orElse(null);
                if (user != null
                        && !user.isDeleted()
                        && parsed.passwordChangedAtMillis() >= user.getPasswordChangedAt().toEpochMilli()) {
                    var principal = AppPrincipal.of(user);
                    var authn = new UsernamePasswordAuthenticationToken(
                            principal, null, authorities(user.getRole()));
                    authn.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authn);
                }
            } catch (Exception ignored) {
                // invalid token -> stay anonymous
            }
        }
        chain.doFilter(request, response);
    }

    private static List<SimpleGrantedAuthority> authorities(Role role) {
        return switch (role) {
            case ADMIN -> List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_MODERATOR"),
                    new SimpleGrantedAuthority("ROLE_USER"));
            case MODERATOR -> List.of(new SimpleGrantedAuthority("ROLE_MODERATOR"),
                    new SimpleGrantedAuthority("ROLE_USER"));
            case USER -> List.of(new SimpleGrantedAuthority("ROLE_USER"));
        };
    }
}
