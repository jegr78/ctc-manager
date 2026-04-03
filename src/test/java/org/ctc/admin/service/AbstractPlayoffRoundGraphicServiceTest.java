package org.ctc.admin.service;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.PlayoffSeedRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AbstractPlayoffRoundGraphicServiceTest {

    @TempDir
    Path tempDir;

    private PlayoffSeedRepository playoffSeedRepository;
    private SeasonTeamRepository seasonTeamRepository;
    private TestablePlayoffRoundGraphicService service;

    static class TestablePlayoffRoundGraphicService extends AbstractPlayoffRoundGraphicService {

        TestablePlayoffRoundGraphicService(PlayoffSeedRepository playoffSeedRepository,
                                            SeasonTeamRepository seasonTeamRepository,
                                            String uploadDir) {
            super(null, playoffSeedRepository, seasonTeamRepository, uploadDir);
        }

        @Override
        protected String getTemplateFileName() {
            return "test-playoff-round-template.html";
        }

        @Override
        protected String getDefaultTemplatePath() {
            return "templates/admin/test-playoff-round-render.html";
        }
    }

    @BeforeEach
    void setUp() {
        playoffSeedRepository = mock(PlayoffSeedRepository.class);
        seasonTeamRepository = mock(SeasonTeamRepository.class);
        service = new TestablePlayoffRoundGraphicService(playoffSeedRepository, seasonTeamRepository, tempDir.toString());
    }

    private Season createSeason() {
        var season = new Season("Community Team Cup", 2026, 1);
        season.setId(UUID.randomUUID());
        return season;
    }

    private Team createTeam(String name, String shortName, String primary, String secondary, String accent) {
        var team = new Team(name, shortName);
        team.setId(UUID.randomUUID());
        team.setPrimaryColor(primary);
        team.setSecondaryColor(secondary);
        team.setAccentColor(accent);
        return team;
    }

    private Playoff createPlayoff(Season season) {
        var playoff = new Playoff(season, "CTC Playoffs 2026");
        playoff.setId(UUID.randomUUID());
        return playoff;
    }

    private PlayoffRound createRoundWithMatchup(Playoff playoff, Team team1, Team team2) {
        var round = new PlayoffRound(playoff, "Semifinal", 0);
        round.setId(UUID.randomUUID());
        var matchup = new PlayoffMatchup(round, 0);
        matchup.setId(UUID.randomUUID());
        matchup.setTeam1(team1);
        matchup.setTeam2(team2);
        round.getMatchups().add(matchup);
        when(seasonTeamRepository.findBySeasonId(playoff.getSeason().getId()))
                .thenReturn(List.copyOf(playoff.getSeason().getSeasonTeams()));
        when(playoffSeedRepository.findByPlayoffId(playoff.getId())).thenReturn(List.of());
        return round;
    }

    @Test
    void givenRoundWithOneMatchup_whenPrepareBaseContext_thenContainsMatchupRow() {
        // given
        var season = createSeason();
        var team1 = createTeam("Alpha Racing", "ALF", "#ff0000", "#000000", "#ffffff");
        var team2 = createTeam("Beta Racing", "BET", "#0000ff", "#333333", "#00ff00");
        season.addTeam(team1);
        season.addTeam(team2);
        var playoff = createPlayoff(season);
        var round = createRoundWithMatchup(playoff, team1, team2);

        // when
        var data = service.prepareBaseContext(round);

        // then
        assertThat(data.matchdayLabel()).isEqualTo("Semifinal");
        assertThat(data.seasonName()).isEqualTo("CTC Playoffs 2026");
        assertThat(data.seasonYear()).isEqualTo("2026");
        assertThat(data.matches()).hasSize(1);

        var row = data.matches().getFirst();
        assertThat(row.homeTeamName()).isEqualTo("Alpha Racing");
        assertThat(row.homeTeamShortName()).isEqualTo("ALF");
        assertThat(row.homePrimaryColor()).isEqualTo("#ff0000");
        assertThat(row.awayTeamName()).isEqualTo("Beta Racing");
        assertThat(row.awayTeamShortName()).isEqualTo("BET");
        assertThat(row.awayPrimaryColor()).isEqualTo("#0000ff");
    }

    @Test
    void givenSeededPlayoff_whenPrepareBaseContext_thenSeedsPopulated() {
        // given
        var season = createSeason();
        var team1 = createTeam("Alpha", "ALF", "#fff", "#000", "#f00");
        var team2 = createTeam("Beta", "BET", "#00f", "#333", "#0f0");
        season.addTeam(team1);
        season.addTeam(team2);
        var playoff = createPlayoff(season);
        var round = createRoundWithMatchup(playoff, team1, team2);

        var seed1 = new PlayoffSeed(playoff, team1, 1);
        var seed2 = new PlayoffSeed(playoff, team2, 4);
        when(playoffSeedRepository.findByPlayoffId(playoff.getId())).thenReturn(List.of(seed1, seed2));

        // when
        var data = service.prepareBaseContext(round);

        // then
        var row = data.matches().getFirst();
        assertThat(row.homeSeed()).isEqualTo(1);
        assertThat(row.awaySeed()).isEqualTo(4);
    }

    @Test
    void givenUnseededPlayoff_whenPrepareBaseContext_thenSeedsAreZero() {
        // given
        var season = createSeason();
        var team1 = createTeam("Alpha", "ALF", "#fff", "#000", "#f00");
        var team2 = createTeam("Beta", "BET", "#00f", "#333", "#0f0");
        season.addTeam(team1);
        season.addTeam(team2);
        var playoff = createPlayoff(season);
        var round = createRoundWithMatchup(playoff, team1, team2);

        // when
        var data = service.prepareBaseContext(round);

        // then
        var row = data.matches().getFirst();
        assertThat(row.homeSeed()).isZero();
        assertThat(row.awaySeed()).isZero();
    }

    @Test
    void givenMatchupWithScores_whenPrepareBaseContext_thenScoresIncluded() {
        // given
        var season = createSeason();
        var team1 = createTeam("Alpha", "ALF", "#fff", "#000", "#f00");
        var team2 = createTeam("Beta", "BET", "#00f", "#333", "#0f0");
        season.addTeam(team1);
        season.addTeam(team2);
        var playoff = createPlayoff(season);
        var round = createRoundWithMatchup(playoff, team1, team2);

        var matchup = round.getMatchups().getFirst();
        matchup.setHomeScore(42);
        matchup.setAwayScore(27);

        // when
        var data = service.prepareBaseContext(round);

        // then
        var row = data.matches().getFirst();
        assertThat(row.homeScore()).isEqualTo(42);
        assertThat(row.awayScore()).isEqualTo(27);
    }

    @Test
    void givenMatchupWithRaces_whenPrepareBaseContext_thenScheduledDateTimeFromEarliestRace() {
        // given
        var season = createSeason();
        var team1 = createTeam("Alpha", "ALF", "#fff", "#000", "#f00");
        var team2 = createTeam("Beta", "BET", "#00f", "#333", "#0f0");
        season.addTeam(team1);
        season.addTeam(team2);
        var playoff = createPlayoff(season);
        var round = createRoundWithMatchup(playoff, team1, team2);

        var matchup = round.getMatchups().getFirst();
        var race1 = new Race();
        race1.setId(UUID.randomUUID());
        race1.setDateTime(LocalDateTime.of(2026, 4, 10, 19, 30));
        var race2 = new Race();
        race2.setId(UUID.randomUUID());
        race2.setDateTime(LocalDateTime.of(2026, 4, 11, 20, 0));
        matchup.getRaces().add(race1);
        matchup.getRaces().add(race2);

        // when
        var data = service.prepareBaseContext(round);

        // then — earliest race converted to Europe/London
        var row = data.matches().getFirst();
        var expectedLondon = LocalDateTime.of(2026, 4, 10, 19, 30)
                .atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneId.of("Europe/London"));
        var expectedFormatted = expectedLondon.format(
                DateTimeFormatter.ofPattern("EEE, dd MMM. HH:mm z", Locale.ENGLISH));
        assertThat(row.scheduledDateTime()).isEqualTo(expectedFormatted);
    }

    @Test
    void givenMatchupWithoutTeams_whenPrepareBaseContext_thenMatchupExcluded() {
        // given
        var season = createSeason();
        var playoff = createPlayoff(season);
        var round = new PlayoffRound(playoff, "Final", 1);
        round.setId(UUID.randomUUID());
        var matchup = new PlayoffMatchup(round, 0);
        matchup.setId(UUID.randomUUID());
        // team1 and team2 are null (TBD)
        round.getMatchups().add(matchup);
        when(seasonTeamRepository.findBySeasonId(season.getId())).thenReturn(List.of());
        when(playoffSeedRepository.findByPlayoffId(playoff.getId())).thenReturn(List.of());

        // when
        var data = service.prepareBaseContext(round);

        // then
        assertThat(data.matches()).isEmpty();
    }

    @Test
    void givenRecordField_whenPrepareBaseContext_thenRecordIsDash() {
        // given
        var season = createSeason();
        var team1 = createTeam("Alpha", "ALF", "#fff", "#000", "#f00");
        var team2 = createTeam("Beta", "BET", "#00f", "#333", "#0f0");
        season.addTeam(team1);
        season.addTeam(team2);
        var playoff = createPlayoff(season);
        var round = createRoundWithMatchup(playoff, team1, team2);

        // when
        var data = service.prepareBaseContext(round);

        // then — playoff round graphics show dash instead of W-L-D record
        var row = data.matches().getFirst();
        assertThat(row.homeRecord()).isEqualTo("—");
        assertThat(row.awayRecord()).isEqualTo("—");
    }

    // Template management tests

    @Test
    void givenNoCustomTemplate_whenHasCustomTemplate_thenReturnsFalse() {
        // when / then
        assertThat(service.hasCustomTemplate()).isFalse();
    }

    @Test
    void givenSavedTemplate_whenLoadTemplate_thenReturnsCustomContent() throws IOException {
        // given
        service.saveTemplate("<html>custom playoff round</html>");

        // when / then
        assertThat(service.hasCustomTemplate()).isTrue();
        assertThat(service.loadTemplate()).isEqualTo("<html>custom playoff round</html>");
    }

    @Test
    void givenCustomTemplate_whenResetTemplate_thenCustomTemplateRemoved() throws IOException {
        // given
        service.saveTemplate("<html>custom</html>");

        // when
        service.resetTemplate();

        // then
        assertThat(service.hasCustomTemplate()).isFalse();
    }
}
