package org.ctc.backup.service;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.PickSubmissionService;
import org.ctc.domain.service.PickCollectionStatus;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.AuthorizedPickActor;
import org.ctc.domain.repository.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@CtcDevSpringBootContext
@Tag("integration")
class PickCollectionDisabledIT {
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired CarRepository cars;
    @Autowired TrackRepository tracks;
    @Autowired RaceRepository races;
    @Autowired MatchdayPickRepository picks;
    @Autowired PickSubmissionService submissions;
    @Autowired BackupArchiveService archives;
    @MockitoSpyBean BackupImportService imports;
    @MockitoBean PickCollectionStatus collection;
    TestHelper.SeasonFixture fixture;
    PickPayload payload;

    @BeforeEach
    void setUp() {
        // given
        when(collection.isAvailable()).thenReturn(false);
        when(collection.isConfidentialityReady()).thenReturn(false);
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TPD" + UUID.randomUUID().toString().substring(0, 7));
            fixture.season().setPicksCollectionEnabled(true);
            fixture.race().setPicksManaged(true);
            fixture.race().setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0));
            fixture.matchday().setPickDeadline(LocalDateTime.of(2035, 1, 1, 12, 0));
            var car = cars.save(new Car("Test disabled collector", UUID.randomUUID().toString()));
            var track = tracks.save(new Track("Test disabled collector " + UUID.randomUUID()));
            fixture.season().getCars().add(car);
            fixture.season().getTracks().add(track);
            payload = new PickPayload(track.getId(), car.getId(), null, 12, 2, 3, 4, PickInitialFuel.DEFAULT, 1,
                    TimeProgressionMode.MULTIPLIER, 0, "S01", "Sunset", Set.of(PickTyre.RS), Set.of());
        });
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow());
            seasons.flush();
            cars.deleteById(payload.carId());
            tracks.deleteById(payload.trackId());
            teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
        });
    }

    @Test
    void givenUnavailableCollectionCapability_whenConfirmingCompletePick_thenCreateNoHistory() {
        // given
        var actor = new AuthorizedPickActor(fixture.race().getId(), fixture.season().getId(), fixture.homeTeam().getId(), "12345");
        // when / then
        assertThatThrownBy(() -> submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("unavailable");
        tx.executeWithoutResult(status -> {
            assertThat(picks.findBySeasonId(fixture.season().getId())).isEmpty();
            assertThat(races.findById(fixture.race().getId()).orElseThrow().isPicksManaged()).isTrue();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"collection", "marker"})
    void givenUnavailableConfidentialityCapabilityAndPrivateArchive_whenStaging_thenRejectBeforeWiping(String flag) throws Exception {
        // given
        tx.executeWithoutResult(status -> {
            seasons.findById(fixture.season().getId()).orElseThrow().setPicksCollectionEnabled(flag.equals("collection"));
            races.findById(fixture.race().getId()).orElseThrow().setPicksManaged(flag.equals("marker"));
        });
        var output = new ByteArrayOutputStream();
        archives.writeZip(output, Instant.now());
        clearInvocations(imports);
        // when / then
        assertThatThrownBy(() -> imports.stage(new MockMultipartFile("file", "test-disabled-collector.zip", "application/zip", output.toByteArray())))
                .isInstanceOfSatisfying(BackupArchiveException.class, failure -> {
                    assertThat(failure.reason()).isEqualTo(BackupArchiveException.Reason.SCHEMA_MISMATCH);
                    assertThat(failure.getMessage()).contains("confidentiality");
                });
        verify(imports, never()).wipeAllTables(anyMap());
    }
}
