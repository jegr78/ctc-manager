package org.ctc.admin.service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.ctc.domain.service.PhaseTestFixtures;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.service.StandingsService;
import org.ctc.domain.service.StandingsService.TeamStanding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TeamCardServiceTest {

	@TempDir
	Path tempDir;

	private final TeamCardService service = new TeamCardService(null, null, null, "uploads");

	@Test
	void givenTeamWithSimpleShortName_whenGetCardPath_thenReturnsExpectedPath() {
		// given
		var season = new Season("Season 4 - 2026");
		season.setId(UUID.randomUUID());
		var team = new Team("Test Team", "TST");
		team.setPrimaryColor("#ff0000");

		var seasonTeam = new SeasonTeam(season, team);

		// when / then
		assertThat(service.getCardPath(seasonTeam))
				.isEqualTo("/uploads/team-cards/" + season.getId() + "/TST.png");
	}

	@Test
	void givenTeamShortNameWithSpaces_whenGetCardPath_thenSpacesAreSanitized() {
		// given
		var season = new Season("Season 4");
		season.setId(UUID.randomUUID());
		var team = new Team("Community League Racing 1", "CLR 1");
		team.setPrimaryColor("#2196f3");

		var seasonTeam = new SeasonTeam(season, team);

		// when / then
		assertThat(service.getCardPath(seasonTeam))
				.contains("CLR_1.png");
	}

	@Test
	void givenThreeColorsWithOneDark_whenComputeGradientColor_thenPicksDarkestColor() {
		// given
		// Dark blue is darkest (lowest luminance)

		// when / then
		assertThat(service.computeGradientColor("#1a1a2e", "#e0e0e0", "#ff6600"))
				.isEqualTo("#1a1a2e");
	}

	@Test
	void givenThreeBrightColors_whenComputeGradientColor_thenPicksLowestLuminance() {
		// given
		// Red (#ff0000) has lower luminance than yellow (#ffff00) and cyan (#00ffff)

		// when / then
		assertThat(service.computeGradientColor("#ffff00", "#00ffff", "#ff0000"))
				.isEqualTo("#ff0000");
	}

	@Test
	void givenTwoNullColors_whenComputeGradientColor_thenReturnsOnlyNonNullColor() {
		// when / then
		assertThat(service.computeGradientColor("#336699", null, null))
				.isEqualTo("#336699");
	}

	@Test
	void givenNoCardFileOnDisk_whenCardExists_thenReturnsFalse() {
		// given
		var season = new Season("Season 4");
		season.setId(UUID.randomUUID());
		var team = new Team("Test", "TST");

		var seasonTeam = new SeasonTeam(season, team);

		// when / then
		assertThat(service.cardExists(seasonTeam)).isFalse();
	}

	@Test
	void givenLeagueSeason_whenGenerateCard_thenStandingsCalledWithRegularPhaseIdAndNullGroup() throws Exception {
		// given
		var standingsService = mock(StandingsService.class);
		var seasonPhaseService = mock(SeasonPhaseService.class);
		var templateEngine = mock(TemplateEngine.class);
		when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html></html>");
		var testService = new TeamCardService(templateEngine, standingsService, seasonPhaseService, tempDir.toString());

		var season = new Season("Test", 2026, 1);
		season.setId(UUID.randomUUID());
		var team = new Team("TestTeam", "TT");
		team.setId(UUID.randomUUID());
		var seasonTeam = new SeasonTeam(season, team);

		var regularPhase = PhaseTestFixtures.regularPhase(season, null, null);
		when(seasonPhaseService.findRegularPhase(season.getId())).thenReturn(regularPhase);
		var standing = new TeamStanding(team);
		when(standingsService.calculateStandings(eq(regularPhase.getId()), isNull()))
				.thenReturn(List.of(standing));

		// when
		testService.generateCard(seasonTeam);

		// then
		verify(seasonPhaseService).findRegularPhase(season.getId());
		verify(standingsService).calculateStandings(eq(regularPhase.getId()), isNull());
	}

	@Test
	void givenNullAccent_whenComputeAccentVisColor_thenReturnsPrimary() {
		assertThat(service.computeAccentVisColor(null, "#336699")).isEqualTo("#336699");
	}

	@Test
	void givenVeryDarkAccent_whenComputeAccentVisColor_thenReturnsPrimary() {
		// #0a0a0a luminance ~10, below the 28 visibility floor
		assertThat(service.computeAccentVisColor("#0a0a0a", "#336699")).isEqualTo("#336699");
	}

	@Test
	void givenNormalAccent_whenComputeAccentVisColor_thenReturnsAccent() {
		// #ff6600 luminance ~127, above the floor
		assertThat(service.computeAccentVisColor("#ff6600", "#336699")).isEqualTo("#ff6600");
	}

	@Test
	void givenBrightPrimary_whenContrastColor_thenReturnsDarkText() {
		// #f5c542 luminance ~198, above 140
		assertThat(service.contrastColor("#f5c542")).isEqualTo("#0b0b10");
	}

	@Test
	void givenDarkPrimary_whenContrastColor_thenReturnsWhiteText() {
		// #1a1a2e luminance ~27, below 140
		assertThat(service.contrastColor("#1a1a2e")).isEqualTo("#ffffff");
	}
}
