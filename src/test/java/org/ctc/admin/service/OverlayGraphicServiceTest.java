package org.ctc.admin.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.service.PhaseTestFixtures;
import org.ctc.domain.service.StandingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OverlayGraphicServiceTest {

	@TempDir
	Path tempDir;

	private OverlayGraphicService createService() {
		return new OverlayGraphicService(null, null, null, tempDir.toString());
	}

	@Test
	void givenRaceWithNoTeams_whenGenerateOverlay_thenThrowsIllegalState() {
		// given
		var service = createService();
		var race = new Race();

		// when / then
		assertThatThrownBy(() -> service.generateOverlay(race))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("no teams");
	}

	@Test
	void givenNoCustomTemplate_whenHasCustomTemplate_thenReturnsFalse() {
		// given
		var service = createService();

		// when / then
		assertThat(service.hasCustomTemplate()).isFalse();
	}

	@Test
	void givenNoCustomTemplate_whenSaveTemplate_thenCustomTemplateExistsAndCanBeLoaded() throws IOException {
		// given
		var service = createService();

		// when
		service.saveTemplate("<html>custom overlay</html>");

		// then
		assertThat(service.hasCustomTemplate()).isTrue();
		assertThat(service.loadTemplate()).isEqualTo("<html>custom overlay</html>");
	}

	@Test
	void givenSavedCustomTemplate_whenResetTemplate_thenNoCustomTemplateExists() throws IOException {
		// given
		var service = createService();
		service.saveTemplate("<html>custom overlay</html>");

		// when
		service.resetTemplate();

		// then
		assertThat(service.hasCustomTemplate()).isFalse();
	}

	@Test
	void whenLoadDefaultTemplate_thenReturnsNonEmptyHtml() throws IOException {
		// given
		var service = createService();

		// when
		String template = service.loadDefaultTemplate();

		// then
		assertThat(template).isNotEmpty();
		assertThat(template).contains("transparent");
		assertThat(template).contains("1920px");
	}

	@Test
	void givenLeagueRace_whenGenerateOverlay_thenStandingsCalledWithPhaseIdAndNullGroup() throws Exception {
		// given
		var standingsService = mock(StandingsService.class);
		var seasonTeamRepository = mock(SeasonTeamRepository.class);
		var templateEngine = mock(TemplateEngine.class);
		when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html></html>");
		var service = new OverlayGraphicService(templateEngine, standingsService, seasonTeamRepository, tempDir.toString());

		var season = new Season("Test", 2026, 1);
		season.setId(UUID.randomUUID());
		var home = new Team("H", "H"); home.setId(UUID.randomUUID());
		var away = new Team("A", "A"); away.setId(UUID.randomUUID());
		season.addTeam(home); season.addTeam(away);
		var matchday = PhaseTestFixtures.matchdayInRegularPhase(season, "MD1", 1);
		matchday.setId(UUID.randomUUID());
		var race = new Race();
		race.setId(UUID.randomUUID());
		race.setMatchday(matchday);
		race.setHomeTeamOverride(home);
		race.setAwayTeamOverride(away);
		when(seasonTeamRepository.findBySeasonId(season.getId())).thenReturn(List.copyOf(season.getSeasonTeams()));
		when(standingsService.calculateStandings(eq(matchday.getPhase().getId()), isNull())).thenReturn(List.of());

		// when
		service.generateOverlay(race);

		// then
		verify(standingsService).calculateStandings(eq(matchday.getPhase().getId()), isNull());
	}

	@Test
	void givenGroupsLayoutRace_whenGenerateOverlay_thenStandingsCalledWithPhaseAndMatchdayGroup() throws Exception {
		// given
		var standingsService = mock(StandingsService.class);
		var seasonTeamRepository = mock(SeasonTeamRepository.class);
		var templateEngine = mock(TemplateEngine.class);
		when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html></html>");
		var service = new OverlayGraphicService(templateEngine, standingsService, seasonTeamRepository, tempDir.toString());

		var season = new Season("Test", 2026, 1);
		season.setId(UUID.randomUUID());
		var home = new Team("H", "H"); home.setId(UUID.randomUUID());
		var away = new Team("A", "A"); away.setId(UUID.randomUUID());
		season.addTeam(home); season.addTeam(away);
		var matchday = PhaseTestFixtures.matchdayInRegularPhase(season, "MD1", 1);
		matchday.setId(UUID.randomUUID());
		var group = new SeasonPhaseGroup(matchday.getPhase(), "Group A", 0);
		group.setId(UUID.randomUUID());
		matchday.setGroup(group);
		var race = new Race();
		race.setId(UUID.randomUUID());
		race.setMatchday(matchday);
		race.setHomeTeamOverride(home);
		race.setAwayTeamOverride(away);
		when(seasonTeamRepository.findBySeasonId(season.getId())).thenReturn(List.copyOf(season.getSeasonTeams()));
		when(standingsService.calculateStandings(eq(matchday.getPhase().getId()), eq(group.getId()))).thenReturn(List.of());

		// when
		service.generateOverlay(race);

		// then
		verify(standingsService).calculateStandings(eq(matchday.getPhase().getId()), eq(group.getId()));
	}
}
