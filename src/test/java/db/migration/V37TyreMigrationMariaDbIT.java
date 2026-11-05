package db.migration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Tag("integration")
@EnabledIfSystemProperty(named = "docker.available", matches = "true")
class V37TyreMigrationMariaDbIT {
    @Container static MariaDBContainer<?> database = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("ctc_tyre_migration").withUsername("ctc").withPassword("test");

    @Test
    void givenClosedMigrationConnection_whenSavingOnMariaDb_thenEnforceExactTyreContracts() throws Exception {
        // given / when / then
        V37TyreConnectionMigrationTest.verifyMigration(database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }
}
