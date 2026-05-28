package org.ctc.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.service.PhaseTestFixtures;
import org.ctc.domain.service.ScoringService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

class ProvisionalScoresGraphicServiceTest {

	private final ScoringService scoringService = mock(ScoringService.class);
	private final TemplateEngine templateEngine = mock(TemplateEngine.class);
	private final PlaywrightScreenshotter screenshotter = mock(PlaywrightScreenshotter.class);

	@TempDir
	Path tempDir;

	private ProvisionalScoresGraphicService createService() {
		when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html><body>provisional</body></html>");
		when(screenshotter.apply(anyString())).thenReturn(new byte[]{0x01, 0x02, 0x03});
		return new ProvisionalScoresGraphicService(templateEngine, scoringService, tempDir.toString(), screenshotter);
	}

	private Race createValidRace(UUID seasonId, Team homeTeam, Team awayTeam, int homeDrivers, int awayDrivers) throws IOException {
		Season season = new Season(String.valueOf(seasonId));
		season.setId(seasonId);
		Matchday matchday = PhaseTestFixtures.matchdayInRegularPhase(season, "MD 1", 1);
		Match match = new Match(matchday, homeTeam, awayTeam);
		match.setRaces(new ArrayList<>());

		Race race = new Race();
		race.setId(UUID.randomUUID());
		race.setMatchday(matchday);
		race.setMatch(match);

		for (int i = 0; i < homeDrivers; i++) {
			RaceResult r = newResult(race, i + 1, false, 25 - i, 5 - i, i == 0 ? 1 : 0);
			race.getResults().add(r);
			when(scoringService.isDriverInTeam(eq(r), any(), eq(homeTeam.getId()))).thenReturn(true);
		}
		for (int i = 0; i < awayDrivers; i++) {
			RaceResult r = newResult(race, homeDrivers + i + 1, false, 18 - i, 4 - i, 0);
			race.getResults().add(r);
			when(scoringService.isDriverInTeam(eq(r), any(), eq(homeTeam.getId()))).thenReturn(false);
		}

		writeFakeCard(seasonId, homeTeam.getShortName());
		writeFakeCard(seasonId, awayTeam.getShortName());
		return race;
	}

	private RaceResult newResult(Race race, int position, boolean fastestLap, int ptsRace, int ptsQuali, int ptsFl) {
		Driver driver = new Driver();
		driver.setId(UUID.randomUUID());
		driver.setPsnId("Driver" + position);
		RaceResult result = new RaceResult(race, driver, position, position, fastestLap);
		result.setPointsRace(ptsRace);
		result.setPointsQuali(ptsQuali);
		result.setPointsFl(ptsFl);
		result.setPointsTotal(ptsRace + ptsQuali + ptsFl);
		return result;
	}

	private void writeFakeCard(UUID seasonId, String shortName) throws IOException {
		Path cardDir = tempDir.resolve("team-cards").resolve(seasonId.toString());
		Files.createDirectories(cardDir);
		Files.write(cardDir.resolve(shortName + ".png"), new byte[]{(byte) 0x89, 'P', 'N', 'G'});
	}

	private Team team(String name, String shortName) {
		Team t = new Team(name, shortName);
		t.setId(UUID.randomUUID());
		return t;
	}

	@Test
	void whenRaceHasNoResults_thenThrowsIllegalStateException() {
		// given
		var service = createService();
		Race race = new Race();
		race.setId(UUID.randomUUID());

		// when / then
		assertThatThrownBy(() -> service.generateProvisional(race, 1))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("No results for this race");
	}

	@Test
	void whenRaceHasNoHomeTeam_thenThrowsIllegalStateException() throws IOException {
		// given
		var service = createService();
		Team awayTeam = team("Away", "AWY");
		Race race = new Race();
		race.setId(UUID.randomUUID());
		Season season = new Season("S");
		season.setId(UUID.randomUUID());
		Matchday matchday = PhaseTestFixtures.matchdayInRegularPhase(season, "MD 1", 1);
		race.setMatchday(matchday);
		race.setAwayTeamOverride(awayTeam);
		race.getResults().add(newResult(race, 1, false, 25, 5, 1));

		// when / then
		assertThatThrownBy(() -> service.generateProvisional(race, 1))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Race has no home team");
	}

	@Test
	void whenRaceHasNoAwayTeam_thenThrowsIllegalStateException() {
		// given
		var service = createService();
		Team homeTeam = team("Home", "HOM");
		Race race = new Race();
		race.setId(UUID.randomUUID());
		Season season = new Season("S");
		season.setId(UUID.randomUUID());
		Matchday matchday = PhaseTestFixtures.matchdayInRegularPhase(season, "MD 1", 1);
		race.setMatchday(matchday);
		race.setHomeTeamOverride(homeTeam);
		race.getResults().add(newResult(race, 1, false, 25, 5, 1));

		// when / then
		assertThatThrownBy(() -> service.generateProvisional(race, 1))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Race has no away team");
	}

	@Test
	void whenValidRace_thenReturnsNonEmptyByteArray() throws IOException {
		// given
		var service = createService();
		Team homeTeam = team("Home", "HOM");
		Team awayTeam = team("Away", "AWY");
		Race race = createValidRace(UUID.randomUUID(), homeTeam, awayTeam, 2, 2);

		// when
		byte[] result = service.generateProvisional(race, 1);

		// then
		assertThat(result).isNotEmpty();
		verify(screenshotter, atLeastOnce()).apply(anyString());
	}

	@Test
	void whenValidRace_thenTemplateContextIncludesRaceLabelAndExpectedVariables() throws IOException {
		// given
		var service = createService();
		Team homeTeam = team("Home", "HOM");
		Team awayTeam = team("Away", "AWY");
		Race race = createValidRace(UUID.randomUUID(), homeTeam, awayTeam, 2, 2);

		// when
		service.generateProvisional(race, 3);

		// then
		ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
		verify(templateEngine).process(eq("admin/provisional-scores-render"), captor.capture());
		Context ctx = captor.getValue();
		assertThat(ctx.getVariable("raceLabel")).isEqualTo("Race 3");
		assertThat(ctx.getVariable("homeRows")).asInstanceOf(list(ProvisionalScoresGraphicService.ProvisionalRow.class)).hasSize(2);
		assertThat(ctx.getVariable("awayRows")).asInstanceOf(list(ProvisionalScoresGraphicService.ProvisionalRow.class)).hasSize(2);
		assertThat(ctx.getVariable("homeTeamName")).isEqualTo("Home");
		assertThat(ctx.getVariable("awayTeamName")).isEqualTo("Away");
		assertThat(ctx.getVariable("homeCardBase64")).isNotNull();
		assertThat(ctx.getVariable("awayCardBase64")).isNotNull();
		assertThat(ctx.getVariable("ctcLogoBase64")).isNotNull();
		assertThat(ctx.getVariable("fontBase64")).isNotNull();
		assertThat(ctx.getVariable("seasonYear")).isNotNull();
		assertThat(ctx.getVariable("matchdayName")).isEqualTo("MD 1");
	}

	@Test
	void discordPostType_containsProvisionalScores() {
		// when / then
		assertThat(DiscordPostType.valueOf("PROVISIONAL_SCORES")).isEqualTo(DiscordPostType.PROVISIONAL_SCORES);
	}

	@Test
	void givenSameRaceTwice_whenGenerateProvisionalWithSameIndex_thenSameRaceLabel() throws IOException {
		// given
		var service = createService();
		Team homeTeam = team("Home", "HOM");
		Team awayTeam = team("Away", "AWY");
		Race race = createValidRace(UUID.randomUUID(), homeTeam, awayTeam, 2, 2);

		// when
		service.generateProvisional(race, 2);
		service.generateProvisional(race, 2);

		// then
		ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
		verify(templateEngine, times(2)).process(eq("admin/provisional-scores-render"), captor.capture());
		List<Context> contexts = captor.getAllValues();
		assertThat(contexts).hasSize(2);
		assertThat(contexts.get(0).getVariable("raceLabel")).isEqualTo("Race 2");
		assertThat(contexts.get(1).getVariable("raceLabel")).isEqualTo("Race 2");
	}
}
