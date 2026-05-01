package org.ctc.admin.service;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.service.StandingsService;
import org.ctc.domain.service.StandingsService.TeamStanding;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractMatchdayGraphicServiceTest {

	@TempDir
	Path tempDir;

	private StandingsService standingsService;
	private SeasonTeamRepository seasonTeamRepository;
	private TestableMatchdayGraphicService service;

	@BeforeEach
	void setUp() {
		standingsService = mock(StandingsService.class);
		seasonTeamRepository = mock(SeasonTeamRepository.class);
		service = new TestableMatchdayGraphicService(standingsService, seasonTeamRepository, tempDir.toString());
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

	private Matchday createMatchdayWithMatches(Season season, Team homeTeam, Team awayTeam) {
		var matchday = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "Match Day 3", 3);
		matchday.setId(UUID.randomUUID());
		var match = new Match(matchday, homeTeam, awayTeam);
		match.setId(UUID.randomUUID());
		matchday.getMatches().add(match);
		when(seasonTeamRepository.findBySeasonId(season.getId()))
				.thenReturn(List.copyOf(season.getSeasonTeams()));
		return matchday;
	}

	@Test
	void givenMatchdayWithOneMatch_whenPrepareBaseContext_thenContainsMatchRow() {
		// given
		var season = createSeason();
		var homeTeam = createTeam("The Neutrals Racing A", "TNR-A", "#ffffff", "#000000", "#ff0000");
		var awayTeam = createTeam("Community League Racing 1", "CLR1", "#0000ff", "#333333", "#00ff00");
		season.addTeam(homeTeam);
		season.addTeam(awayTeam);
		var matchday = createMatchdayWithMatches(season, homeTeam, awayTeam);

		var homeStanding = new TeamStanding(homeTeam);
		homeStanding.addWin();
		var awayStanding = new TeamStanding(awayTeam);
		awayStanding.addLoss();
		when(standingsService.calculateStandings(season.getId())).thenReturn(List.of(homeStanding, awayStanding));

		// when
		var data = service.prepareBaseContext(matchday);

		// then
		assertThat(data.matchdayLabel()).isEqualTo("Match Day 3");
		assertThat(data.seasonName()).isEqualTo("Community Team Cup");
		assertThat(data.seasonYear()).isEqualTo("2026");
		assertThat(data.matches()).hasSize(1);

		var row = data.matches().getFirst();
		assertThat(row.homeTeamName()).isEqualTo("The Neutrals Racing A");
		assertThat(row.homeTeamShortName()).isEqualTo("TNR-A");
		assertThat(row.homePrimaryColor()).isEqualTo("#ffffff");
		assertThat(row.awayTeamName()).isEqualTo("Community League Racing 1");
		assertThat(row.awayTeamShortName()).isEqualTo("CLR1");
		assertThat(row.awayPrimaryColor()).isEqualTo("#0000ff");
	}

	@Test
	void givenMatchdayWithByeMatch_whenPrepareBaseContext_thenByeMatchExcluded() {
		// given
		var season = createSeason();
		var homeTeam = createTeam("Team A", "TA", "#fff", "#000", "#f00");
		var awayTeam = createTeam("Team B", "TB", "#00f", "#333", "#0f0");
		var byeTeam = createTeam("Team C", "TC", "#0f0", "#111", "#ff0");
		season.addTeam(homeTeam);
		season.addTeam(awayTeam);
		season.addTeam(byeTeam);
		var matchday = createMatchdayWithMatches(season, homeTeam, awayTeam);

		var byeMatch = new Match(matchday, byeTeam, null);
		byeMatch.setId(UUID.randomUUID());
		byeMatch.setBye(true);
		matchday.getMatches().add(byeMatch);

		when(standingsService.calculateStandings(season.getId())).thenReturn(List.of(
				new TeamStanding(homeTeam), new TeamStanding(awayTeam), new TeamStanding(byeTeam)));

		// when
		var data = service.prepareBaseContext(matchday);

		// then
		assertThat(data.matches()).hasSize(1);
		assertThat(data.matches().getFirst().homeTeamShortName()).isEqualTo("TA");
	}

	@Test
	void givenStandings_whenPrepareBaseContext_thenSeedAndRecordPopulated() {
		// given
		var season = createSeason();
		var homeTeam = createTeam("Home", "HOM", "#fff", "#000", "#f00");
		var awayTeam = createTeam("Away", "AWY", "#00f", "#333", "#0f0");
		season.addTeam(homeTeam);
		season.addTeam(awayTeam);
		var matchday = createMatchdayWithMatches(season, homeTeam, awayTeam);

		var homeStanding = new TeamStanding(homeTeam);
		homeStanding.addWin();
		homeStanding.addWin();
		homeStanding.addLoss();
		var awayStanding = new TeamStanding(awayTeam);
		awayStanding.addDraw();
		when(standingsService.calculateStandings(season.getId())).thenReturn(List.of(homeStanding, awayStanding));

		// when
		var data = service.prepareBaseContext(matchday);

		// then
		var row = data.matches().getFirst();
		assertThat(row.homeSeed()).isEqualTo(1);
		assertThat(row.homeRecord()).isEqualTo("2-1-0");
		assertThat(row.awaySeed()).isEqualTo(2);
		assertThat(row.awayRecord()).isEqualTo("0-0-1");
	}

	@Test
	void givenTeamNotInStandings_whenPrepareBaseContext_thenSeedZeroAndEmptyRecord() {
		// given
		var season = createSeason();
		var homeTeam = createTeam("Home", "HOM", "#fff", "#000", "#f00");
		var awayTeam = createTeam("Away", "AWY", "#00f", "#333", "#0f0");
		season.addTeam(homeTeam);
		season.addTeam(awayTeam);
		var matchday = createMatchdayWithMatches(season, homeTeam, awayTeam);

		when(standingsService.calculateStandings(season.getId())).thenReturn(List.of());

		// when
		var data = service.prepareBaseContext(matchday);

		// then
		var row = data.matches().getFirst();
		assertThat(row.homeSeed()).isZero();
		assertThat(row.homeRecord()).isEqualTo("0-0-0");
		assertThat(row.awaySeed()).isZero();
	}

	@Test
	void givenMatchWithScores_whenPrepareBaseContext_thenScoresIncluded() {
		// given
		var season = createSeason();
		var homeTeam = createTeam("Home", "HOM", "#fff", "#000", "#f00");
		var awayTeam = createTeam("Away", "AWY", "#00f", "#333", "#0f0");
		season.addTeam(homeTeam);
		season.addTeam(awayTeam);
		var matchday = createMatchdayWithMatches(season, homeTeam, awayTeam);

		var match = matchday.getMatches().getFirst();
		match.setHomeScore(70);
		match.setAwayScore(46);

		when(standingsService.calculateStandings(season.getId())).thenReturn(List.of());

		// when
		var data = service.prepareBaseContext(matchday);

		// then
		var row = data.matches().getFirst();
		assertThat(row.homeScore()).isEqualTo(70);
		assertThat(row.awayScore()).isEqualTo(46);
	}

	@Test
	void givenMatchWithRaceDateTime_whenPrepareBaseContext_thenDateTimeFromEarliestRace() {
		// given
		var season = createSeason();
		var homeTeam = createTeam("Home", "HOM", "#fff", "#000", "#f00");
		var awayTeam = createTeam("Away", "AWY", "#00f", "#333", "#0f0");
		season.addTeam(homeTeam);
		season.addTeam(awayTeam);
		var matchday = createMatchdayWithMatches(season, homeTeam, awayTeam);

		var match = matchday.getMatches().getFirst();
		var race1 = new Race();
		race1.setId(UUID.randomUUID());
		race1.setDateTime(LocalDateTime.of(2026, 3, 20, 19, 30));
		var race2 = new Race();
		race2.setId(UUID.randomUUID());
		race2.setDateTime(LocalDateTime.of(2026, 3, 21, 20, 0));
		match.getRaces().add(race1);
		match.getRaces().add(race2);

		when(standingsService.calculateStandings(season.getId())).thenReturn(List.of());

		// when
		var data = service.prepareBaseContext(matchday);

		// then — earliest race (19:30 system time) converted to Europe/London
		var row = data.matches().getFirst();
		var expectedLondon = LocalDateTime.of(2026, 3, 20, 19, 30)
				.atZone(ZoneId.systemDefault())
				.withZoneSameInstant(ZoneId.of("Europe/London"));
		var expectedFormatted = expectedLondon.format(
				DateTimeFormatter.ofPattern("EEE, dd MMM. HH:mm z", Locale.ENGLISH));
		assertThat(row.scheduledDateTime()).isEqualTo(expectedFormatted);
	}

	@Test
	void givenMatchWithNoRaces_whenPrepareBaseContext_thenScheduledDateTimeNull() {
		// given
		var season = createSeason();
		var homeTeam = createTeam("Home", "HOM", "#fff", "#000", "#f00");
		var awayTeam = createTeam("Away", "AWY", "#00f", "#333", "#0f0");
		season.addTeam(homeTeam);
		season.addTeam(awayTeam);
		var matchday = createMatchdayWithMatches(season, homeTeam, awayTeam);

		when(standingsService.calculateStandings(season.getId())).thenReturn(List.of());

		// when
		var data = service.prepareBaseContext(matchday);

		// then
		assertThat(data.matches().getFirst().scheduledDateTime()).isNull();
	}

	@Test
	void givenSeasonTeamWithOverriddenColors_whenPrepareBaseContext_thenUsesSeasonColors() {
		// given
		var season = createSeason();
		var homeTeam = createTeam("Home Team", "HOM", "#111111", "#222222", "#333333");
		var awayTeam = createTeam("Away Team", "AWY", "#444444", "#555555", "#666666");
		season.addTeam(homeTeam);
		season.addTeam(awayTeam);

		// Override season-specific colors for home team
		var homeSeasonTeam = season.getSeasonTeams().stream()
				.filter(st -> st.getTeam().getId().equals(homeTeam.getId()))
				.findFirst().orElseThrow();
		homeSeasonTeam.setPrimaryColor("#aa0000");
		homeSeasonTeam.setSecondaryColor("#bb0000");

		var matchday = createMatchdayWithMatches(season, homeTeam, awayTeam);
		when(standingsService.calculateStandings(season.getId())).thenReturn(List.of());

		// when
		var data = service.prepareBaseContext(matchday);

		// then — home uses season-specific colors, away falls back to team colors
		var row = data.matches().getFirst();
		assertThat(row.homePrimaryColor()).isEqualTo("#aa0000");
		assertThat(row.homeSecondaryColor()).isEqualTo("#bb0000");
		assertThat(row.homeAccentColor()).isEqualTo("#333333"); // no override, falls back
		assertThat(row.awayPrimaryColor()).isEqualTo("#444444"); // no override
		assertThat(row.awaySecondaryColor()).isEqualTo("#555555");
	}

	@Test
	void givenNoCustomTemplate_whenHasCustomTemplate_thenReturnsFalse() {
		// when / then
		assertThat(service.hasCustomTemplate()).isFalse();
	}

	// Template management tests

	@Test
	void givenSavedTemplate_whenLoadTemplate_thenReturnsCustomContent() throws IOException {
		// given
		service.saveTemplate("<html>custom matchday</html>");

		// when / then
		assertThat(service.hasCustomTemplate()).isTrue();
		assertThat(service.loadTemplate()).isEqualTo("<html>custom matchday</html>");
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

	// Concrete subclass for testing the abstract class
	static class TestableMatchdayGraphicService extends AbstractMatchdayGraphicService {

		TestableMatchdayGraphicService(StandingsService standingsService,
		                               SeasonTeamRepository seasonTeamRepository,
		                               String uploadDir) {
			super(null, standingsService, seasonTeamRepository, uploadDir);
		}

		@Override
		protected String getTemplateFileName() {
			return "test-template.html";
		}

		@Override
		protected String getDefaultTemplatePath() {
			return "templates/admin/test-render.html";
		}
	}
}
