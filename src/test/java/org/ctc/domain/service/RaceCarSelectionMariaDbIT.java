package org.ctc.domain.service;

import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "ctc.site.output-dir=target/site-selection-mariadb")
@ActiveProfiles({"local", "dev"})
@Testcontainers
@EnabledIfSystemProperty(named = "docker.available", matches = "true")
@Tag("integration")
class RaceCarSelectionMariaDbIT extends RaceCarSelectionIT {
    @Container
    static MariaDBContainer<?> database = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("ctc_selection_test").withUsername("ctc").withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
    }

    @Override
    void setLockTimeout(int milliseconds) {
        jdbc.execute("SET SESSION innodb_lock_wait_timeout = " + (milliseconds == 250 ? 1 : 50));
    }

    @Override
    @Test
    void givenCachedBlankRaceAndCommittedSelection_whenValidatingAnotherMember_thenRefreshUsageBeforeAccepting() {
        // given / when / then
        assertThatThrownBy(super::givenCachedBlankRaceAndCommittedSelection_whenValidatingAnotherMember_thenRefreshUsageBeforeAccepting)
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
        assertThat(selections.availability(fixture.season(), fixture.home(), null).consumedEntryIds()).containsExactly(fixture.entry());
    }

    @Override
    @Test
    void givenCachedPairingAndCommittedReorientation_whenValidating_thenRefreshEffectiveHomeOwnership() {
        // given / when / then
        assertThatThrownBy(super::givenCachedPairingAndCommittedReorientation_whenValidating_thenRefreshEffectiveHomeOwnership)
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
        assertThat(selections.availability(fixture.season(), fixture.away(), null).consumedEntryIds()).containsExactly(fixture.entry());
        assertThat(selections.availability(fixture.season(), fixture.home(), null).consumedEntryIds()).isEmpty();
    }

    @Override
    @Test
    void givenCachedRaceEntity_whenUnrelatedMetadataSavesAfterNativeSelection_thenMappingPreservesConcurrentSelection() {
        // given / when / then
        assertThatThrownBy(super::givenCachedRaceEntity_whenUnrelatedMetadataSavesAfterNativeSelection_thenMappingPreservesConcurrentSelection)
                .isInstanceOf(org.springframework.orm.jpa.JpaSystemException.class);
        tx.executeWithoutResult(status -> {
            var saved = races.findById(fixture.race()).orElseThrow();
            assertThat(saved.getCarPoolEntry().getId()).isEqualTo(fixture.entry());
            assertThat(saved.getCar().getId()).isEqualTo(fixture.mr());
            assertThat(saved.getCalendarEventId()).isNull();
        });
    }

    @Test
    void givenNativeDatabase_whenReadingTransactionDefaults_thenExerciseProductionSnapshotIsolation() {
        // given / when / then
        assertThat(jdbc.queryForObject("SELECT @@transaction_isolation", String.class)).isEqualTo("REPEATABLE-READ");
        assertThat(jdbc.queryForObject("SELECT @@innodb_snapshot_isolation", Integer.class)).isEqualTo(1);
    }
}
