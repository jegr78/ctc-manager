package org.ctc.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.service.StandingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@ExtendWith(MockitoExtension.class)
class StandingsGraphicServiceContractTest {

	@Mock
	TemplateEngine templateEngine;

	@Mock
	StandingsService standingsService;

	@Mock
	SeasonTeamRepository seasonTeamRepository;

	StandingsGraphicService service;

	@BeforeEach
	void setUp() throws IOException {
		Path tempUpload = Files.createTempDirectory("standings-graphic-test-");
		service = spy(new StandingsGraphicService(templateEngine, standingsService, seasonTeamRepository,
				tempUpload.toString()));
		when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html></html>");
		try {
			org.mockito.Mockito.doNothing().when((AbstractGraphicService) service)
					.renderScreenshot(anyString(), any(Path.class));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		when(seasonTeamRepository.findBySeasonId(any(UUID.class))).thenReturn(List.of());
	}

	private Season seasonMock(UUID id) {
		Season s = org.mockito.Mockito.mock(Season.class);
		when(s.getId()).thenReturn(id);
		return s;
	}

	private SeasonPhase phaseMock(UUID id, PhaseType type, PhaseLayout layout) {
		SeasonPhase p = org.mockito.Mockito.mock(SeasonPhase.class);
		when(p.getId()).thenReturn(id);
		when(p.getPhaseType()).thenReturn(type);
		when(p.getLayout()).thenReturn(layout);
		return p;
	}

	private SeasonPhaseGroup groupMock(UUID id, String name, int sortIndex) {
		SeasonPhaseGroup g = org.mockito.Mockito.mock(SeasonPhaseGroup.class);
		when(g.getId()).thenReturn(id);
		when(g.getName()).thenReturn(name);
		when(g.getSortIndex()).thenReturn(sortIndex);
		return g;
	}

	@Test
	void givenLeagueLayout_whenGenerateStandingsBytes_thenReturnsSinglePngAndCallsCalculateOnce() throws Exception {
		UUID phaseId = UUID.randomUUID();
		Season season = seasonMock(UUID.randomUUID());
		SeasonPhase phase = phaseMock(phaseId, PhaseType.REGULAR, PhaseLayout.LEAGUE);
		when(standingsService.calculateStandings(phaseId, null)).thenReturn(List.of());

		List<byte[]> pngs = service.generateStandingsBytes(season, phase);

		assertThat(pngs).hasSize(1);
		verify(standingsService).calculateStandings(phaseId, null);
		verifyNoMoreInteractions(standingsService);
	}

	@Test
	void givenPlayoffLayoutBracket_whenGenerateStandingsBytes_thenReturnsSinglePng() throws Exception {
		UUID phaseId = UUID.randomUUID();
		Season season = seasonMock(UUID.randomUUID());
		SeasonPhase phase = phaseMock(phaseId, PhaseType.PLAYOFF, PhaseLayout.BRACKET);
		when(standingsService.calculateStandings(phaseId, null)).thenReturn(List.of());

		List<byte[]> pngs = service.generateStandingsBytes(season, phase);

		assertThat(pngs).hasSize(1);
		verify(standingsService).calculateStandings(phaseId, null);
	}

	@Test
	void givenGroupsLayout_whenGenerateStandingsBytes_thenReturnsOnePngPerGroupInSortOrder() throws Exception {
		UUID phaseId = UUID.randomUUID();
		UUID gAId = UUID.randomUUID();
		UUID gBId = UUID.randomUUID();
		Season season = seasonMock(UUID.randomUUID());
		SeasonPhase phase = phaseMock(phaseId, PhaseType.REGULAR, PhaseLayout.GROUPS);
		SeasonPhaseGroup groupB = groupMock(gBId, "Group B", 2);
		SeasonPhaseGroup groupA = groupMock(gAId, "Group A", 1);
		when(phase.getGroups()).thenReturn(List.of(groupB, groupA));
		when(standingsService.calculateStandings(eq(phaseId), any(UUID.class))).thenReturn(List.of());

		List<byte[]> pngs = service.generateStandingsBytes(season, phase);

		assertThat(pngs).hasSize(2);
		org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(standingsService);
		inOrder.verify(standingsService).calculateStandings(phaseId, gAId);
		inOrder.verify(standingsService).calculateStandings(phaseId, gBId);
	}

	@Test
	void givenPlacementLayoutLeague_whenGenerateStandingsBytes_thenReturnsSinglePng() throws Exception {
		UUID phaseId = UUID.randomUUID();
		Season season = seasonMock(UUID.randomUUID());
		SeasonPhase phase = phaseMock(phaseId, PhaseType.PLACEMENT, PhaseLayout.LEAGUE);
		when(standingsService.calculateStandings(phaseId, null)).thenReturn(List.of());

		List<byte[]> pngs = service.generateStandingsBytes(season, phase);

		assertThat(pngs).hasSize(1);
		verify(standingsService).calculateStandings(eq(phaseId), isNull());
	}
}
