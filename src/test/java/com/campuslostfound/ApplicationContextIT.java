package com.campuslostfound;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuslostfound.support.AbstractApiIT;
import org.junit.jupiter.api.Test;

/**
 * Boots the whole application against Flyway-migrated H2. A green run proves the V1
 * migration is valid on a PostgreSQL-mode database and that every JPA entity mapping
 * matches the created schema (Hibernate runs with ddl-auto=validate).
 */
class ApplicationContextIT extends AbstractApiIT {

    @Test
    void contextLoadsAndSchemaValidates() {
        assertThat(mvc).isNotNull();
    }

    @Test
    void healthEndpointIsPublicButDetailsAreHidden() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("UP"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.components").doesNotExist());
    }

    @Test
    void otherActuatorEndpointsAreNotAccessible() throws Exception {
        // denyAll: anonymous callers are challenged (401); an authenticated non-admin is
        // forbidden (403). Either way, no env/beans data is exposed.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/actuator/env"))
                .andExpect(status().isUnauthorized());

        String userToken = newVerifiedUser("actuator-probe@campus.edu");
        mvc.perform(authGet("/actuator/beans", userToken))
                .andExpect(status().isForbidden());
    }
}
