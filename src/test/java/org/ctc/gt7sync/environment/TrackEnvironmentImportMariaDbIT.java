package org.ctc.gt7sync.environment;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers @Tag("integration")
@EnabledIfSystemProperty(named="docker.available",matches="true")
class TrackEnvironmentImportMariaDbIT extends TrackEnvironmentImportIT {
    @Container static MariaDBContainer<?> database=new MariaDBContainer<>("mariadb:13")
            .withDatabaseName("ctc_environment_import").withUsername("ctc").withPassword("test");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",database::getJdbcUrl);r.add("spring.datasource.username",database::getUsername);
        r.add("spring.datasource.password",database::getPassword);r.add("spring.datasource.driver-class-name",()->"org.mariadb.jdbc.Driver");
    }
}
