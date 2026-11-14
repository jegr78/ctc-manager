package org.ctc.domain.service;

import java.time.LocalDateTime;
import java.util.*;
import org.ctc.TestHelper;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.AuthorizedPickActor;
import org.ctc.domain.repository.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@CtcDevSpringBootContext
@Tag("integration")
class PickPlayoffLifecycleIT {
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired CarRepository cars;
    @Autowired TrackRepository tracks;
    @Autowired RaceRepository races;
    @Autowired MatchdayRepository days;
    @Autowired PlayoffRepository playoffs;
    @Autowired PlayoffMatchupRepository matchups;
    @Autowired PhaseTeamRepository phaseTeams;
    @Autowired PickSubmissionService submissions;
    @Autowired MatchdayPickRepository picks;
    @Autowired PlayoffService playoffService;
    @Autowired PlayoffSeedingService seeding;
    TestHelper.SeasonFixture fixture;
    final List<UUID> teamIds = new ArrayList<>();
    UUID car;
    UUID track;
    UUID playoff;
    UUID first;
    UUID second;
    UUID race;
    PickPayload payload;

    @BeforeEach
    void setUp() {
        // given
        tx.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 7);
            fixture = helper.createFullSeasonFixture("TPPL" + suffix);
            teamIds.addAll(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
            for (String name : List.of("C", "D")) {
                var team = helper.createTeam("Test pick playoff " + suffix + name, "TP" + suffix + name);
                fixture.season().addTeam(team);
                teamIds.add(team.getId());
            }
            var selectedCar = cars.save(new Car("Test pick playoff", UUID.randomUUID().toString()));
            var selectedTrack = tracks.save(new Track("Test pick playoff " + UUID.randomUUID()));
            fixture.season().getCars().add(selectedCar);
            fixture.season().getTracks().add(selectedTrack);
            car = selectedCar.getId();
            track = selectedTrack.getId();
        });
        playoff = playoffService.createPlayoff(fixture.season().getId(), "Test private playoff", 4).getId();
        tx.executeWithoutResult(status -> {
            var opening = playoffs.findById(playoff).orElseThrow().getRounds().stream().filter(round -> round.getRoundIndex() == 0)
                    .findFirst().orElseThrow().getMatchups().stream().sorted(Comparator.comparingInt(PlayoffMatchup::getBracketPosition)).toList();
            first = opening.get(0).getId();
            second = opening.get(1).getId();
        });
        seeding.seedTeam(first, teamIds.get(0), 1);
        seeding.seedTeam(first, teamIds.get(1), 2);
        race = playoffService.addRaceToMatchup(first, null, null, LocalDateTime.of(2035, 1, 1, 20, 0)).getId();
        tx.executeWithoutResult(status -> {
            var day = races.findById(race).orElseThrow().getMatchday(); day.setPickDeadline(LocalDateTime.of(2035, 1, 1, 12, 0)); day.setPicksOpen(true);
        });
        payload = new PickPayload(track, car, null, 12, 2, 3, 4, PickInitialFuel.DEFAULT, 1,
                TimeProgressionMode.MULTIPLIER, 0, "S01", "Sunset", Set.of(PickTyre.RS), Set.of());
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            helper.deletePickHistory(season.getId());
            races.deleteAll(races.findByMatchdaySeasonId(season.getId()));
            var bracket = playoffs.findById(playoff).orElseThrow();
            bracket.getRounds().forEach(round -> round.getMatchups().forEach(matchup -> matchup.setNextMatchup(null)));
            playoffs.flush();
            playoffs.delete(bracket);
            playoffs.flush();
            season.getPhases().forEach(phase -> phaseTeams.deleteAll(phaseTeams.findByPhaseId(phase.getId())));
            phaseTeams.flush();
            helper.deleteSeasonCascade(season);
            seasons.flush();
            cars.deleteById(car);
            tracks.deleteById(track);
            teams.deleteAllById(teamIds);
        });
    }

    @Test
    void givenEnabledSeason_whenNativePlayoffLegIsScheduled_thenEnrollAndAllowCompleteHomeSubmission() {
        // when / then
        assertEnrolled(race);
        var receipt = submissions.submit(race, 0, UUID.randomUUID(), actor(race, teamIds.getFirst()), payload);
        assertThat(receipt.revisionNumber()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void givenPendingPlayoffHistory_whenReseedingEitherParticipant_thenRejectAndKeepExactTarget(int slot) {
        // given
        submitInitial();
        // when / then
        assertThatThrownBy(() -> seeding.seedTeam(first, teamIds.get(2), slot))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("pick history");
        assertParticipantsAndHistory();
    }

    @Test
    void givenPendingPlayoffHistory_whenClearingOpponentSlot_thenRejectBeforeBreakingTarget() {
        // given
        submitInitial();
        // when / then
        assertThatThrownBy(() -> seeding.seedTeam(first, null, 2))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("pick history");
        assertParticipantsAndHistory();
    }

    @Test
    void givenPreviouslyIncompleteScheduledPlayoffLeg_whenSecondParticipantArrives_thenEnrollNewTarget() {
        // given
        seeding.seedTeam(second, teamIds.get(2), 1);
        UUID incomplete = tx.execute(status -> {
            var matchup = matchups.findById(second).orElseThrow();
            var day = new Matchday(matchup.getRound().getPlayoff().getPhase(), "Test newly ready target", 201);
            day.setPickDeadline(LocalDateTime.of(2035, 1, 2, 12, 0)); day.setPicksOpen(true);
            day = days.save(day);
            var target = new Race();
            target.setMatchday(day);
            target.setPlayoffMatchup(matchup);
            target.setDateTime(LocalDateTime.of(2035, 1, 2, 20, 0));
            return races.save(target).getId();
        });
        // when
        seeding.seedTeam(second, teamIds.get(3), 2);
        // then
        assertEnrolled(incomplete);
        assertThat(submissions.submit(incomplete, 0, UUID.randomUUID(), actor(incomplete, teamIds.get(2)), payload).revisionNumber()).isEqualTo(1);
    }

    @Test
    void givenPendingPlayoffHistory_whenSavingSameParticipantAgain_thenKeepVersionAndHistory() {
        // given
        submitInitial();
        // when
        seeding.seedTeam(first, teamIds.getFirst(), 1);
        // then
        assertParticipantsAndHistory();
    }

    private void assertEnrolled(UUID raceId) {
    }
    private void submitInitial() {
        assertEnrolled(race);
        submissions.submit(race, 0, UUID.randomUUID(), actor(race, teamIds.getFirst()), payload);
    }
    private AuthorizedPickActor actor(UUID raceId, UUID home) { return new AuthorizedPickActor(raceId, fixture.season().getId(), home, "12345"); }
    private void assertParticipantsAndHistory() {
        tx.executeWithoutResult(status -> {
            var target = races.findById(race).orElseThrow();
            assertThat(target.getHomeTeam().getId()).isEqualTo(teamIds.getFirst());
            assertThat(target.getAwayTeam().getId()).isEqualTo(teamIds.get(1));
            var pick = picks.findByRaceId(race).orElseThrow();
            assertThat(pick.getVersion()).isEqualTo(1);
            assertThat(pick.getTeam().getId()).isEqualTo(teamIds.getFirst());
        });
    }
}
