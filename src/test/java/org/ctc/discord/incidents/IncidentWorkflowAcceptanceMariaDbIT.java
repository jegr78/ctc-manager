package org.ctc.discord.incidents;

import org.ctc.CtcManagerApplication;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = CtcManagerApplication.class)
@ActiveProfiles({"local", "dev"})
@Testcontainers
@EnabledIfSystemProperty(named = "docker.available", matches = "true")
@Tag("integration")
class IncidentWorkflowAcceptanceMariaDbIT extends IncidentWorkflowAcceptanceIT {
    @Container static MariaDBContainer<?> database = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("ctc_incident_acceptance").withUsername("ctc").withPassword("test");
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", database::getJdbcUrl);
        properties.add("spring.datasource.username", database::getUsername);
        properties.add("spring.datasource.password", database::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
    }
}
