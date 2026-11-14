package org.ctc.dataimport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import org.ctc.TestHelper;
import org.ctc.dataimport.exception.ImportRejectedException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@CtcDevSpringBootContext
@Tag("integration")
class PickCsvImportIT {
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired CarRepository cars;
    @Autowired TrackRepository tracks;
    @Autowired MatchdayPickRepository picks;
    @Autowired MatchdayPickRevisionRepository revisions;
    @Autowired PickSubmissionService submissions;
    @Autowired PickReviewService reviews;
    @Autowired CsvImportService imports;
    @MockitoSpyBean RaceRepository races;
    @MockitoSpyBean DriverRepository drivers;
    TestHelper.SeasonFixture fixture;
    List<UUID> carIds;
    List<UUID> trackIds;
    List<UUID> teamIds = new ArrayList<>();
    List<UUID> driverIds = new ArrayList<>();
    Driver firstDriver;
    Driver secondDriver;
    PickPayload payload;
    SubmissionReceipt receipt;

    @BeforeEach
    void setUp() {
        // given
        tx.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 7);
            fixture = helper.createFullSeasonFixture("TPCSV" + suffix);
            teamIds.addAll(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
            
            
            fixture.race().setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0));
            fixture.matchday().setPickDeadline(LocalDateTime.of(2035, 1, 1, 12, 0));
            fixture.matchday().setPicksOpen(true);
            var car1 = cars.save(new Car("Test pick CSV", UUID.randomUUID().toString()));
            var car2 = cars.save(new Car("Test pick CSV", UUID.randomUUID().toString()));
            var track1 = tracks.save(new Track("Test pick CSV " + UUID.randomUUID()));
            var track2 = tracks.save(new Track("Test pick CSV " + UUID.randomUUID()));
            fixture.season().getCars().addAll(List.of(car1, car2));
            fixture.season().getTracks().addAll(List.of(track1, track2));
            carIds = List.of(car1.getId(), car2.getId());
            trackIds = List.of(track1.getId(), track2.getId());
            firstDriver = helper.createDriver("Test_pick_csv_" + suffix + "_a", "Test pick CSV A");
            secondDriver = helper.createDriver("Test_pick_csv_" + suffix + "_b", "Test pick CSV B");
            driverIds.addAll(List.of(firstDriver.getId(), secondDriver.getId()));
        });
        payload = payload(0);
        receipt = submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor(fixture.race().getId()), payload);
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow());
            seasons.flush();
            cars.deleteAllById(carIds);
            tracks.deleteAllById(trackIds);
            teams.deleteAllById(teamIds);
            drivers.deleteAllById(driverIds);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void givenPendingOrApprovedFixedPoolHistory_whenOverwritingResults_thenPreserveOriginalRaceAndHistory(boolean approved) {
        // given
        if (approved) reviews.approve(fixture.race().getId(), 1, 1, new ManagementPickActor("test-admin"), "Complete setup checked");
        var preview = preview(scorecard(fixture.awayTeam().getShortName(), secondDriver.getPsnId(), fixture.homeTeam().getShortName(), firstDriver.getPsnId()));
        // when / then
        assertThatCode(() -> imports.executeMultiRaceImport(List.of(preview), Map.of(), Set.of(), true)).doesNotThrowAnyException();
        tx.executeWithoutResult(status -> {
            assertThat(races.findByMatchId(fixture.match().getId())).extracting(Race::getId).containsExactly(fixture.race().getId());
            var race = races.findById(fixture.race().getId()).orElseThrow();
            assertThat(race.getResults()).hasSize(2);
            assertThat(race.getHomeTeam().getId()).isEqualTo(fixture.homeTeam().getId());
            var pick = picks.findByRaceId(race.getId()).orElseThrow();
            assertThat(pick.getVersion()).isEqualTo(approved ? 2 : 1);
            assertThat(pick.getState()).isEqualTo(approved ? PickState.APPROVED : PickState.SUBMITTED);
            assertThat(revisions.findByRequestId(receipt.requestId()).orElseThrow().receipt()).isEqualTo(receipt);
            if (approved) {
                assertThat(race.getCar().getId()).isEqualTo(payload.carId());
                assertThat(race.getTrack().getId()).isEqualTo(payload.trackId());
                assertThat(race.getSettings().getWeather()).isEqualTo(payload.weather());
            }
        });
    }

    @Test
    void givenAmbiguousPrivateLegsAfterEarlierBlankPairing_whenImportingAll_thenRejectBeforeAnyRaceOrDriverWrite() {
        // given
        UUID secondRace = tx.execute(status -> {
            var original = races.findById(fixture.race().getId()).orElseThrow();
            var race = helper.createRace(original.getMatchday(), original.getMatch());
            race.setDateTime(original.getDateTime().plusHours(1));
            
            return race.getId();
        });
        submissions.submit(secondRace, 0, UUID.randomUUID(), actor(secondRace), payload(1));
        UUID blank = tx.execute(status -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            String suffix = UUID.randomUUID().toString().substring(0, 7);
            var home = helper.createTeam("Test CSV blank " + suffix + " A", "TPC" + suffix + "A");
            var away = helper.createTeam("Test CSV blank " + suffix + " B", "TPC" + suffix + "B");
            teamIds.addAll(List.of(home.getId(), away.getId()));
            season.addTeam(home);
            season.addTeam(away);
            var original = races.findById(fixture.race().getId()).orElseThrow();
            return helper.createRace(original.getMatchday(), helper.createMatch(original.getMatchday(), home, away)).getId();
        });
        String newPsn = "Test_new_pick_csv_" + UUID.randomUUID().toString().substring(0, 12);
        var earlier = tx.execute(status -> {
            var race = races.findById(blank).orElseThrow();
            return preview(scorecard(race.getHomeTeam().getShortName(), newPsn, race.getAwayTeam().getShortName(), secondDriver.getPsnId()));
        });
        var repeated = preview(scorecard(fixture.homeTeam().getShortName(), firstDriver.getPsnId(), fixture.awayTeam().getShortName(), secondDriver.getPsnId()));
        clearInvocations(races, drivers);
        // when / then
        assertThatThrownBy(() -> imports.executeMultiRaceImport(List.of(earlier, repeated, repeated), Map.of(), Set.of(newPsn), true))
                .isInstanceOf(ImportRejectedException.class).hasMessageContaining("ambiguous");
        verify(races, never()).delete(argThat(race -> blank.equals(race.getId())));
        verify(drivers, never()).save(argThat(driver -> newPsn.equals(driver.getPsnId())));
        assertThat(drivers.findByPsnId(newPsn)).isEmpty();
        tx.executeWithoutResult(status -> {
            assertThat(races.findByMatchId(fixture.match().getId())).extracting(Race::getId)
                    .containsExactlyInAnyOrder(fixture.race().getId(), secondRace);
            assertThat(races.findById(blank).orElseThrow().getResults()).isEmpty();
            assertThat(picks.findByRaceId(fixture.race().getId()).orElseThrow().getVersion()).isEqualTo(1);
            assertThat(picks.findByRaceId(secondRace).orElseThrow().getVersion()).isEqualTo(1);
        });
    }

    private CsvImportService.ImportPreview preview(String csv) {
        return tx.execute(status -> {
            try {
                return imports.parseAndPreview(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                        new CsvImportService.ImportMetadata(fixture.season().getId(), null, null, null, null, fixture.matchday().getId()));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }
    private static String scorecard(String home, String driver, String away, String otherDriver) {
        return "Team,PSN ID,Position,Quali,FL\n%s,%s,1,1,true\n%s,%s,2,2,false\n".formatted(home, driver, away, otherDriver);
    }
    private AuthorizedPickActor actor(UUID race) { return new AuthorizedPickActor(race, fixture.season().getId(), fixture.homeTeam().getId(), "12345"); }
    private PickPayload payload(int index) {
        return new PickPayload(trackIds.get(index), carIds.get(index), null, 12, 2, 3, 4, PickInitialFuel.DEFAULT, 1,
                TimeProgressionMode.MULTIPLIER, 0, "S01", "Sunset", Set.of(PickTyre.RS), Set.of());
    }
}
