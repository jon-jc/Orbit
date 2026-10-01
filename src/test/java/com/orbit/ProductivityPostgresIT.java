package com.orbit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@EnabledIfSystemProperty(named = "orbit.postgres.tests", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProductivityPostgresIT extends ProductivityContract {
    private static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getProperty("orbit.test.db.url");
        if (url != null) {
            String username = System.getProperty("orbit.test.db.username"), password = System.getProperty("orbit.test.db.password");
            if (username == null || password == null)
                throw new IllegalArgumentException("Explicit PostgreSQL tests require dedicated database username and password properties.");
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> username);
            registry.add("spring.datasource.password", () -> password);
        } else {
            postgres = new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("orbit_productivity")
                    .withUsername("orbit_test").withPassword("test-only-password");
            postgres.start();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
    }

    @AfterAll static void stop() { if (postgres != null) postgres.stop(); }
}
