package com.f1bets.integration;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestF1DataProviderConfig.class)
public abstract class BaseIntegrationTest {

    private static final boolean USE_EXTERNAL_DB = Boolean.getBoolean("useExternalDb");

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startContainerIfNeeded() {
        if (!USE_EXTERNAL_DB && postgres == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("f1bets_test")
                .withUsername("test")
                .withPassword("test");
            postgres.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (USE_EXTERNAL_DB) {
            registry.add("spring.datasource.url", () -> System.getProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/f1bets_test"));
            registry.add("spring.datasource.username", () -> System.getProperty("spring.datasource.username", "postgres"));
            registry.add("spring.datasource.password", () -> System.getProperty("spring.datasource.password", "postgres"));
        } else {
            startContainerIfNeeded();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }
}
