package org.ctc.domain.service;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Tag("integration")
@ActiveProfiles(profiles = {"dev", "local"}, inheritProfiles = false)
@Testcontainers
@EnabledIfSystemProperty(named = "docker.available", matches = "true")
class MatchdayPickWindowConcurrencyMariaDbIT extends MatchdayPickWindowConcurrencyIT {
    @Container
    static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
        registry.add("spring.datasource.driver-class-name", DATABASE::getDriverClassName);
    }
}
