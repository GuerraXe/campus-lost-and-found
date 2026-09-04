package com.campuslostfound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Campus Lost &amp; Found - centralized lost-and-found platform for a university campus.
 *
 * <p>Layering (dependencies point one way only):
 * {@code web -> service -> repo -> domain}, with {@code matching} used by {@code service}
 * and {@code security}/{@code config} as cross-cutting concerns. Business rules live only
 * in {@code service}; controllers do transport and authorization gating.
 */
@SpringBootApplication
@EnableJpaAuditing
public class CampusLostFoundApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusLostFoundApplication.class, args);
    }
}
