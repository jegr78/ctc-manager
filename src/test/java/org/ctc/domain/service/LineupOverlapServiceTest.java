package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LineupOverlapServiceTest {
    @Mock RaceLineupRepository lineups;
    @Mock LineupSubmissionRepository submissions;
    @Mock LineupSubmissionRevisionRepository revisions;
    @Mock SeasonDriverRepository seasonDrivers;
    @Mock DriverRepository drivers;
    @InjectMocks LineupOverlapService service;

    private final Season season = new Season();
    private final Matchday day = new Matchday();
    private final Team parent = team("ORG", null);
    private final Team first = team("ORG1", parent);
    private final Team second = team("ORG2", parent);
    private final Team foreign = team("FOR", null);
    private final Driver driver = driver("Shared_Driver");
    private Race firstLeg;
    private Race secondLeg;

    @BeforeEach
    void setUp() {
        // given
        season.setId(UUID.randomUUID());
        day.setId(UUID.randomUUID());
        day.setPhase(new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0));
        firstLeg = race(first, foreign);
        secondLeg = race(second, foreign);
    }

    @Test
    void givenSisterTeamRowOnSameMatchday_whenFieldingDriver_thenReportOverlap() {
        // given
        when(lineups.findByRaceMatchdayId(day.getId())).thenReturn(List.of(new RaceLineup(secondLeg, driver, second)));
        when(submissions.findByRaceIdIn(List.of(firstLeg.getId(), secondLeg.getId()))).thenReturn(List.of());
        // when / then
        assertThatThrownBy(() -> service.requireNone(firstLeg, Map.of(driver.getId(), first), row -> false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A driver may field for only one team of an organisation on a matchday: Shared_Driver is already in the lineup of ORG2");
    }

    @Test
    void givenSisterTeamSubmission_whenFieldingDriver_thenReportOverlap() {
        // given
        var submission = new LineupSubmission(secondLeg, second);
        submission.submit();
        var revision = new LineupSubmissionRevision(submission, 1, new LineupPayload(6, List.of(LineupPayload.Entry.known(driver))),
                java.time.Instant.EPOCH, LineupActorKind.TEAM, "1", null, UUID.randomUUID());
        when(lineups.findByRaceMatchdayId(day.getId())).thenReturn(List.of());
        when(submissions.findByRaceIdIn(List.of(firstLeg.getId(), secondLeg.getId()))).thenReturn(List.of(submission));
        when(revisions.findBySubmissionIdAndRevisionNumber(submission.getId(), 1)).thenReturn(Optional.of(revision));
        // when
        var conflicts = service.conflicts(firstLeg, Map.of(driver.getId(), first), row -> false);
        // then
        assertThat(conflicts).extracting(c -> c.otherTeam().getShortName()).containsExactly("ORG2");
    }

    @Test
    void givenSameTeamOrForeignRowsOrReplacedRows_whenFieldingDriver_thenNoOverlap() {
        // given
        var foreignRace = race(foreign, second);
        when(lineups.findByRaceMatchdayId(day.getId())).thenReturn(List.of(
                new RaceLineup(firstLeg, driver, second), new RaceLineup(secondLeg, driver, first), new RaceLineup(foreignRace, driver, foreign)));
        when(submissions.findByRaceIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        // when
        var conflicts = service.conflicts(firstLeg, Map.of(driver.getId(), first), row -> row.getRace() == firstLeg);
        // then
        assertThat(conflicts).as("the same team on another leg, another organisation and replaced rows are allowed").isEmpty();
    }

    @Test
    void givenDriverRegisteredForAnotherOrganisation_whenWarning_thenNameTheTeam() {
        // given
        when(seasonDrivers.findBySeasonIdAndDriverId(season.getId(), driver.getId())).thenReturn(Optional.of(new SeasonDriver(season, driver, foreign)));
        when(drivers.findById(driver.getId())).thenReturn(Optional.of(driver));
        // when / then
        assertThat(service.foreignWarnings(firstLeg, first, List.of(driver.getId())))
                .containsExactly("Shared_Driver is registered for FOR this season");
    }

    private Race race(Team home, Team away) {
        var match = new Match();
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(day);
        race.setMatch(match);
        day.getRaces().add(race);
        return race;
    }

    private static Team team(String name, Team parent) {
        var team = new Team(name, name, parent);
        team.setId(UUID.randomUUID());
        return team;
    }

    private static Driver driver(String psn) {
        var driver = new Driver(psn, null);
        driver.setId(UUID.randomUUID());
        return driver;
    }
}
