package org.ctc.sitegen;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.*;
import org.ctc.sitegen.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.context.Context;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalkoverPublicProjectionTest {
    @TempDir Path out;
    final TemplateWriter writer = mock(TemplateWriter.class);
    final SiteSlugger slugger = new SiteSlugger();
    final SeasonDriverRepository roster = mock(SeasonDriverRepository.class);
    final RaceResultRepository results = mock(RaceResultRepository.class);
    final RaceLineupRepository lineups = mock(RaceLineupRepository.class);
    final SeasonPhaseService phases = mock(SeasonPhaseService.class);
    final SharedProfilePageGenerator shared = mock(SharedProfilePageGenerator.class);
    final PickDisclosureService disclosure = mock(PickDisclosureService.class, RETURNS_DEEP_STUBS);
    final Season season = new Season("Test walkover publication", 2026, 1);
    final SeasonPhase phase = new SeasonPhase();
    final Team home = new Team("Test home", "T-WOH"), away = new Team("Test away", "T-WOA");
    final Driver driver = new Driver("Test_WO_driver", "Test driver");
    final Matchday day;
    final Match match;
    final Race walkover = new Race(), played = new Race();
    final RaceResult retained, legitimate;

    WalkoverPublicProjectionTest() {
        season.setId(UUID.randomUUID()); home.setId(UUID.randomUUID()); away.setId(UUID.randomUUID()); driver.setId(UUID.randomUUID());
        phase.setId(UUID.randomUUID()); phase.setSeason(season); phase.setPhaseType(PhaseType.REGULAR);
        phase.setRaceScoring(new RaceScoring("Test standard", "20,17,14,12,10,8", "3,2,1", 2));
        day = new Matchday(phase, "Test round", 1); day.setId(UUID.randomUUID());
        match = new Match(day, home, away); match.setId(UUID.randomUUID()); match.setWalkoverTeam(away);
        walkover.setId(UUID.randomUUID()); walkover.setMatchday(day); walkover.setMatch(match);
        played.setId(UUID.randomUUID()); played.setMatchday(day);
        retained = new RaceResult(walkover, driver, 1, 1, true); retained.setPointsTotal(25); walkover.getResults().add(retained);
        legitimate = new RaceResult(played, driver, 2, 2, false); legitimate.setPointsTotal(13); played.getResults().add(legitimate);
        when(roster.findBySeasonId(season.getId())).thenReturn(List.of(new SeasonDriver(season, driver, home)));
        when(results.findByDriverId(driver.getId())).thenReturn(List.of(retained, legitimate));
        when(phases.findAllPhases(season.getId())).thenReturn(List.of(phase));
    }

    GenerationContext context() {
        return new GenerationContext(out, season, "test-active", "Test active", false, null,
                new SiteSlugs(Map.of(home.getId(), "test-home"), Map.of(driver.getId(), "test-driver"), List.of()));
    }

    @Test void givenRetainedWalkoverResults_whenGeneratingDriverProfile_thenExcludePointsAndAppearancesWithoutDeletingRows() throws Exception {
        var generator = new DriverProfilePageGenerator(writer, slugger, roster, results, lineups, phases, shared, disclosure);
        generator.generate(context(), new SiteGeneratorService.GenerationResult());
        var rendered = ArgumentCaptor.forClass(Context.class);
        verify(writer).write(eq("site/driver-profile"), rendered.capture(), any(), eq(out), anyString(), anyString());
        assertThat(rendered.getValue().getVariable("totalRaces")).isEqualTo(1);
        assertThat(rendered.getValue().getVariable("totalPoints")).isEqualTo(13);
        assertThat(rendered.getValue().getVariable("profileRows")).asList().hasSize(1);
        assertThat(retained.getPointsTotal()).isEqualTo(25); assertThat(walkover.getResults()).containsExactly(retained);
    }

    @Test void givenTeamDriverWithWalkoverHistory_whenGeneratingTeamProfile_thenDriverPointsExcludeTheWalkover() throws Exception {
        var teams = mock(TeamRepository.class); var standings = mock(StandingsService.class);
        when(teams.findAll()).thenReturn(List.of(home)); when(phases.findRegularPhase(season.getId())).thenReturn(phase);
        when(standings.calculateStandings(phase.getId(), null)).thenReturn(List.of(new StandingsService.TeamStanding(home)));
        var generator = new TeamProfilePageGenerator(writer, slugger, teams, lineups, roster, results, standings, phases, mock(PhaseTeamRepository.class), shared);
        generator.setUploadDir(out.toString()); generator.generate(context(), new SiteGeneratorService.GenerationResult());
        var rendered = ArgumentCaptor.forClass(Context.class);
        verify(writer).write(eq("site/team-profile"), rendered.capture(), any(), eq(out), anyString(), anyString());
        assertThat(rendered.getValue().getVariable("drivers")).asList().singleElement()
                .isInstanceOfSatisfying(SiteGeneratorService.DriverEntry.class, row -> assertThat(row.totalPoints()).isEqualTo(13));
    }

    @Test void givenTwoWalkoverLegsWithRetainedResults_whenGeneratingMatchday_thenPublishOneTeamResultWithoutDrivers() throws Exception {
        var days = mock(MatchdayRepository.class); var races = mock(RaceRepository.class); var scoring = mock(ScoringService.class);
        var second = new Race(); second.setId(UUID.randomUUID()); second.setMatchday(day); second.setMatch(match);
        when(days.findBySeasonIdOrderBySortIndexAsc(season.getId())).thenReturn(List.of(day));
        when(races.findByMatchdayId(day.getId())).thenReturn(List.of(walkover, second));
        when(scoring.calculateRaceTotals(walkover, home.getId())).thenReturn(RacePenaltyTotals.calculate(81, 0, 0, 0));
        when(scoring.calculateRaceTotals(second, home.getId())).thenReturn(RacePenaltyTotals.calculate(81, 0, 0, 0));
        var generator = new MatchdaysPageGenerator(writer, slugger, days, races, lineups, phases, mock(SeasonPhaseGroupRepository.class), disclosure, scoring);
        generator.generateDetails(context(), new SiteGeneratorService.GenerationResult());
        var rendered = ArgumentCaptor.forClass(Context.class);
        verify(writer).write(eq("site/matchday"), rendered.capture(), any(), eq(out), anyString(), anyString());
        assertThat(rendered.getValue().getVariable("races")).asList().singleElement().isInstanceOfSatisfying(RaceView.class, row -> {
            assertThat(row.getScore()).isEqualTo("81 : 0"); assertThat(row.results()).isEmpty();
            assertThat(row.awayTeamShortName()).contains("w.o."); assertThat(row.isTeamOnlyResult()).isTrue();
        });
        assertThat(walkover.getResults()).containsExactly(retained);
    }
    @Test void givenWalkoverWithoutLegs_whenPublishingMatchday_thenIncludeTeamResult() throws Exception {
        var days = mock(MatchdayRepository.class); var races = mock(RaceRepository.class);
        day.getMatches().add(match);
        when(days.findBySeasonIdOrderBySortIndexAsc(season.getId())).thenReturn(List.of(day));
        when(races.findByMatchdayId(day.getId())).thenReturn(List.of());
        var generator = new MatchdaysPageGenerator(writer, slugger, days, races, lineups, phases,
                mock(SeasonPhaseGroupRepository.class), disclosure, mock(ScoringService.class));
        generator.generateDetails(context(), new SiteGeneratorService.GenerationResult());
        var rendered = ArgumentCaptor.forClass(Context.class);
        verify(writer).write(eq("site/matchday"), rendered.capture(), any(), eq(out), anyString(), anyString());
        assertThat(rendered.getValue().getVariable("races")).asList().singleElement().isInstanceOfSatisfying(RaceView.class, row -> {
            assertThat(row.getScore()).isEqualTo("81 : 0"); assertThat(row.results()).isEmpty();
            assertThat(row.isTeamOnlyResult()).isTrue();
        });
    }

    @Test void givenPlayoffWalkoverAndRegularAnchor_whenPublishingMatchday_thenUsePlayoffOwnerAndForfeiter() throws Exception {
        match.setWalkoverTeam(home); day.getMatches().add(match);
        var playoff = new Playoff(); playoff.setPhase(phase);
        var matchup = new PlayoffMatchup(new PlayoffRound(playoff, "Test final", 1), 0); matchup.setId(UUID.randomUUID());
        matchup.setTeam1(home); matchup.setTeam2(away); matchup.setWalkoverTeam(away); walkover.setPlayoffMatchup(matchup);
        var days = mock(MatchdayRepository.class); var races = mock(RaceRepository.class); var scoring = mock(ScoringService.class);
        when(days.findBySeasonIdOrderBySortIndexAsc(season.getId())).thenReturn(List.of(day));
        when(races.findByMatchdayId(day.getId())).thenReturn(List.of(walkover));
        when(scoring.calculateRaceTotals(walkover, home.getId())).thenReturn(RacePenaltyTotals.calculate(81,0,0,0));
        new MatchdaysPageGenerator(writer, slugger, days, races, lineups, phases, mock(SeasonPhaseGroupRepository.class), disclosure, scoring)
                .generateDetails(context(), new SiteGeneratorService.GenerationResult());
        var rendered = ArgumentCaptor.forClass(Context.class);
        verify(writer).write(eq("site/matchday"), rendered.capture(), any(), eq(out), anyString(), anyString());
        assertThat(rendered.getValue().getVariable("races")).asList().singleElement().isInstanceOfSatisfying(RaceView.class, row -> {
            assertThat(row.getScore()).isEqualTo("81 : 0"); assertThat(row.awayTeamShortName()).contains("w.o.");
            assertThat(row.results()).isEmpty();
        });
    }

}
