package com.orbit;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.AfterAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Tests an isolated PostgreSQL database, never silently substitutes H2. */
@EnabledIfSystemProperty(named = "orbit.postgres.tests", matches = "true")
class WorkflowPostgresIT extends WorkflowContract {
    private static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String dedicatedUrl = System.getProperty("orbit.test.db.url");
        if (dedicatedUrl != null) {
            String username = System.getProperty("orbit.test.db.username");
            String password = System.getProperty("orbit.test.db.password");
            if (username == null || password == null) throw new IllegalArgumentException("Explicit PostgreSQL tests require dedicated database username and password properties.");
            registry.add("spring.datasource.url", () -> dedicatedUrl);
            registry.add("spring.datasource.username", () -> username);
            registry.add("spring.datasource.password", () -> password);
        } else {
            postgres = new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("orbit_contract").withUsername("orbit_test").withPassword("test-only-password");
            postgres.start();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) postgres.stop();
    }
}
