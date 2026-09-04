package com.campuslostfound.config;

import com.campuslostfound.security.JwtAuthenticationFilter;
import com.campuslostfound.web.error.ProblemJsonHandlers;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT security.
 *
 * <p>CSRF protection is disabled deliberately: the API is called with a Bearer token in
 * the {@code Authorization} header, never with an ambient cookie, so there is no
 * cross-site request forgery surface (see docs/security.md).
 *
 * <p>Public: registration/login/verify, {@code GET} on listings and categories, the
 * OpenAPI docs, and {@code /actuator/health}. Everything else needs a valid token; the
 * per-listing {@code /matches} view is authenticated even though listing reads are not
 * (see docs/design-decisions.md DD-10). All of {@code /actuator/**} beyond health is
 * denied outright.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final ProblemJsonHandlers.EntryPoint entryPoint;
    private final ProblemJsonHandlers.DeniedHandler deniedHandler;
    private final CorsProperties corsProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          ProblemJsonHandlers.EntryPoint entryPoint,
                          ProblemJsonHandlers.DeniedHandler deniedHandler,
                          CorsProperties corsProperties) {
        this.jwtFilter = jwtFilter;
        this.entryPoint = entryPoint;
        this.deniedHandler = deniedHandler;
        this.corsProperties = corsProperties;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/verify")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories").permitAll()
                        // these listing GETs are NOT public even though plain reads are
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/listings/mine", "/api/v1/listings/*/matches",
                                "/api/v1/listings/*/claims")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/listings", "/api/v1/listings/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> origins = corsProperties.getAllowedOrigins();
        if (origins != null && !origins.isEmpty()) {
            cfg.setAllowedOrigins(origins);
            cfg.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
            cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            cfg.setMaxAge(3600L);
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    /** Binds {@code campus.cors.*}. Empty by default: no browser origin is allowed. */
    @ConfigurationProperties(prefix = "campus.cors")
    public static class CorsProperties {
        private List<String> allowedOrigins = List.of();

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }
}
