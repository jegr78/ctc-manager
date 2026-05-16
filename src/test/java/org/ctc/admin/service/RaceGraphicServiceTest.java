package org.ctc.admin.service;

import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceAttachmentRepository;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceGraphicServiceTest {

	@Mock
	private RaceRepository raceRepository;
	@Mock
	private RaceAttachmentRepository raceAttachmentRepository;
	@Mock
	private LineupGraphicService lineupGraphicService;
	@Mock
	private ResultsGraphicService resultsGraphicService;
	@Mock
	private SettingsGraphicService settingsGraphicService;
	@Mock
	private OverlayGraphicService overlayGraphicService;

	@InjectMocks
	private RaceGraphicService service;


	@Test
	void givenRace_whenGenerateResults_thenCreatesAttachment() throws Exception {
		// given
		var homeTeam = createTeam("HOM", "Home");
		var awayTeam = createTeam("AWY", "Away");
		var matchday = new Matchday();
		matchday.setId(UUID.randomUUID());
		matchday.setLabel("MD 1");
		// Matchday is bound via SeasonPhase (Convenience-Getter exposes the season).
		matchday.setPhase(new SeasonPhase(new Season("S"), PhaseType.REGULAR, PhaseLayout.LEAGUE, 0));
		var match = new Match(matchday, homeTeam, awayTeam);
		var race = new Race();
		race.setId(UUID.randomUUID());
		race.setMatchday(matchday);
		race.setMatch(match);

		when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
		when(resultsGraphicService.generateResults(race)).thenReturn("/uploads/races/" + race.getId() + "/results.png");

		// when
		service.generateResults(race.getId());

		// then
		verify(raceAttachmentRepository).save(argThat(att ->
				"MD 1-HOM-AWY-Results".equals(att.getName())
						&& att.getUrl().endsWith("/results.png")
						&& att.getType() == AttachmentType.FILE));
	}

	@Test
	void givenGraphicServiceFailure_whenGenerateResults_thenRethrowsException() throws Exception {
		// given
		var race = new Race();
		race.setId(UUID.randomUUID());

		when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
		when(resultsGraphicService.generateResults(race)).thenThrow(new RuntimeException("Playwright failed"));

		// when / then
		assertThatThrownBy(() -> service.generateResults(race.getId()))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Playwright failed");
	}


	@Test
	void givenRace_whenGenerateSettings_thenCreatesAttachment() throws Exception {
		// given
		var homeTeam = createTeam("HOM", "Home");
		var awayTeam = createTeam("AWY", "Away");
		var matchday = new Matchday();
		matchday.setId(UUID.randomUUID());
		matchday.setLabel("MD 1");
		// Matchday is bound via SeasonPhase (Convenience-Getter exposes the season).
		matchday.setPhase(new SeasonPhase(new Season("S"), PhaseType.REGULAR, PhaseLayout.LEAGUE, 0));
		var match = new Match(matchday, homeTeam, awayTeam);
		var race = new Race();
		race.setId(UUID.randomUUID());
		race.setMatchday(matchday);
		race.setMatch(match);

		when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
		when(settingsGraphicService.generateSettings(race)).thenReturn("/uploads/races/" + race.getId() + "/settings.png");

		// when
		service.generateSettings(race.getId());

		// then
		verify(raceAttachmentRepository).save(argThat(att ->
				"MD 1-HOM-AWY-Settings".equals(att.getName())
						&& att.getUrl().endsWith("/settings.png")
						&& att.getType() == AttachmentType.FILE));
	}

	@Test
	void givenGraphicServiceFailure_whenGenerateSettings_thenRethrowsException() throws Exception {
		// given
		var race = new Race();
		race.setId(UUID.randomUUID());

		when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
		when(settingsGraphicService.generateSettings(race)).thenThrow(new RuntimeException("Playwright failed"));

		// when / then
		assertThatThrownBy(() -> service.generateSettings(race.getId()))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Playwright failed");
	}


	@Test
	void givenRace_whenGenerateLineup_thenCreatesAttachment() throws Exception {
		// given
		var homeTeam = createTeam("HOM", "Home");
		var awayTeam = createTeam("AWY", "Away");
		var matchday = new Matchday();
		matchday.setId(UUID.randomUUID());
		matchday.setLabel("MD 1");
		// Matchday is bound via SeasonPhase (Convenience-Getter exposes the season).
		matchday.setPhase(new SeasonPhase(new Season("S"), PhaseType.REGULAR, PhaseLayout.LEAGUE, 0));
		var match = new Match(matchday, homeTeam, awayTeam);
		var race = new Race();
		race.setId(UUID.randomUUID());
		race.setMatchday(matchday);
		race.setMatch(match);

		when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
		when(lineupGraphicService.generateLineup(race)).thenReturn("/uploads/races/" + race.getId() + "/lineup.png");

		// when
		service.generateLineup(race.getId());

		// then
		verify(raceAttachmentRepository).save(argThat(att ->
				"MD 1-HOM-AWY-Lineups".equals(att.getName())
						&& att.getUrl().endsWith("/lineup.png")
						&& att.getType() == AttachmentType.FILE));
	}


	private Team createTeam(String shortName, String name) {
		var team = new Team(name, shortName);
		team.setId(UUID.randomUUID());
		return team;
	}
}
