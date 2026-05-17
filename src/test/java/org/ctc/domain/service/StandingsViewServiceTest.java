package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StandingsViewServiceTest {

	@Mock private StandingsService standingsService;
	@Mock private DriverRankingService driverRankingService;
	@Mock private SeasonManagementService seasonManagementService;
	@Mock private SeasonPhaseService seasonPhaseService;

	@InjectMocks private StandingsViewService service;

	private List<Season> allSeasons;

	@BeforeEach
	void setUp() {
		allSeasons = List.of();
		when(seasonManagementService.findAll()).thenReturn(allSeasons);
	}

	@Test
	void givenAlltimeSeasonId_whenBuildView_thenReturnsAlltimeView() {
		// given
		when(standingsService.calculateAlltimeStandings()).thenReturn(List.of());
		when(driverRankingService.calculateAlltimeRanking()).thenReturn(List.of());

		// when
		var view = service.buildView(null, null, "alltime");

		// then
		assertThat(view.isAlltime()).isTrue();
		assertThat(view.selectedSeasonId()).isEqualTo("alltime");
		assertThat(view.hasRegularPhase()).isFalse();
		assertThat(view.selectedSeason()).isNull();
		assertThat(view.phase()).isNull();
		assertThat(view.groups()).isEmpty();
		assertThat(view.standings()).isNotNull();
		assertThat(view.driverRanking()).isNotNull();
		verify(seasonPhaseService, never()).findById(any());
		verify(seasonPhaseService, never()).findByType(any(), any());
	}

	@Test
	void givenExplicitPhaseId_whenBuildView_thenResolvesViaFindById() {
		// given
		UUID phaseId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		var season = new Season("Test-S", 2099, 1);
		setId(season, UUID.randomUUID());
		var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.GROUPS, 0);
		phase.setFormat(SeasonFormat.LEAGUE);
		when(seasonPhaseService.findById(phaseId)).thenReturn(phase);
		when(seasonManagementService.findByIdOptional(season.getId())).thenReturn(Optional.of(season));
		when(seasonPhaseService.findAllPhases(season.getId())).thenReturn(List.of(phase));
		when(standingsService.calculateStandings(phase.getId(), groupId)).thenReturn(List.of());
		when(driverRankingService.calculateRankingForPhase(phase.getId())).thenReturn(List.of());

		// when
		var view = service.buildView(phaseId, groupId, null);

		// then
		assertThat(view.hasRegularPhase()).isTrue();
		assertThat(view.phase()).isSameAs(phase);
		assertThat(view.selectedSeason()).isSameAs(season);
		assertThat(view.selectedGroupId()).isEqualTo(groupId);
		assertThat(view.combinedView()).isFalse(); // group selected
		assertThat(view.showBuchholz()).isFalse(); // LEAGUE not SWISS
	}

	@Test
	void givenLegacySeasonIdWithRegularPhase_whenBuildView_thenResolvesViaFindByType() {
		// given
		var season = new Season("Test-Legacy", 2099, 2);
		UUID seasonId = UUID.randomUUID();
		setId(season, seasonId);
		var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
		phase.setFormat(SeasonFormat.LEAGUE);
		when(seasonPhaseService.findByType(seasonId, PhaseType.REGULAR)).thenReturn(Optional.of(phase));
		when(seasonManagementService.findByIdOptional(seasonId)).thenReturn(Optional.of(season));
		when(seasonPhaseService.findAllPhases(seasonId)).thenReturn(List.of(phase));
		when(standingsService.calculateStandings(phase.getId(), null)).thenReturn(List.of());
		when(driverRankingService.calculateRankingForPhase(phase.getId())).thenReturn(List.of());

		// when
		var view = service.buildView(null, null, seasonId.toString());

		// then
		assertThat(view.hasRegularPhase()).isTrue();
		assertThat(view.phase()).isSameAs(phase);
		assertThat(view.combinedView()).isFalse(); // LEAGUE layout
	}

	@Test
	void givenLegacySeasonIdWithoutRegularPhase_whenBuildView_thenReturnsBarePageWithSeason() {
		// given
		var season = new Season("Test-NoPhase", 2099, 3);
		UUID seasonId = UUID.randomUUID();
		setId(season, seasonId);
		when(seasonPhaseService.findByType(seasonId, PhaseType.REGULAR)).thenReturn(Optional.empty());
		when(seasonManagementService.findByIdOptional(seasonId)).thenReturn(Optional.of(season));
		when(seasonPhaseService.findAllPhases(seasonId)).thenReturn(List.of());

		// when
		var view = service.buildView(null, null, seasonId.toString());

		// then
		assertThat(view.hasRegularPhase()).isFalse();
		assertThat(view.phase()).isNull();
		assertThat(view.selectedSeason()).isSameAs(season);
		assertThat(view.standings()).isEmpty();
		assertThat(view.driverRanking()).isNull();
	}

	@Test
	void givenMalformedLegacySeasonId_whenBuildView_thenReturnsBarePageWithoutSelection() {
		// given — string that fails UUID parsing
		// when
		var view = service.buildView(null, null, "not-a-uuid");

		// then
		assertThat(view.selectedSeason()).isNull();
		assertThat(view.selectedSeasonId()).isEqualTo("not-a-uuid");
		assertThat(view.hasRegularPhase()).isFalse();
		assertThat(view.standings()).isNull();
		verify(seasonPhaseService, never()).findByType(any(), any());
	}

	@Test
	void givenNoParamsAndActiveSeason_whenBuildView_thenFallsBackToActive() {
		// given
		var season = new Season("Test-Active", 2099, 4);
		UUID seasonId = UUID.randomUUID();
		setId(season, seasonId);
		var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
		phase.setFormat(SeasonFormat.LEAGUE);
		when(seasonManagementService.findActiveSeason()).thenReturn(Optional.of(season));
		when(seasonPhaseService.findByType(seasonId, PhaseType.REGULAR)).thenReturn(Optional.of(phase));
		when(seasonManagementService.findByIdOptional(seasonId)).thenReturn(Optional.of(season));
		when(seasonPhaseService.findAllPhases(seasonId)).thenReturn(List.of(phase));
		when(standingsService.calculateStandings(phase.getId(), null)).thenReturn(List.of());
		when(driverRankingService.calculateRankingForPhase(phase.getId())).thenReturn(List.of());

		// when
		var view = service.buildView(null, null, null);

		// then
		assertThat(view.hasRegularPhase()).isTrue();
		assertThat(view.phase()).isSameAs(phase);
	}

	@Test
	void givenNoParamsAndNoActiveSeason_whenBuildView_thenReturnsBarePage() {
		// given
		when(seasonManagementService.findActiveSeason()).thenReturn(Optional.empty());

		// when
		var view = service.buildView(null, null, null);

		// then
		assertThat(view.hasRegularPhase()).isFalse();
		assertThat(view.phase()).isNull();
		assertThat(view.selectedSeason()).isNull();
		assertThat(view.standings()).isNull();
	}

	@Test
	void givenGroupsLayoutWithNoGroupSelected_whenBuildView_thenCombinedView() {
		// given
		UUID phaseId = UUID.randomUUID();
		var season = new Season("Test-Groups-Combined", 2099, 5);
		setId(season, UUID.randomUUID());
		var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.GROUPS, 0);
		phase.setFormat(SeasonFormat.LEAGUE);
		var groupA = new SeasonPhaseGroup(phase, "Group A", 0);
		var groupB = new SeasonPhaseGroup(phase, "Group B", 1);
		phase.getGroups().add(groupA);
		phase.getGroups().add(groupB);
		when(seasonPhaseService.findById(phaseId)).thenReturn(phase);
		when(seasonManagementService.findByIdOptional(season.getId())).thenReturn(Optional.of(season));
		when(seasonPhaseService.findAllPhases(season.getId())).thenReturn(List.of(phase));
		when(standingsService.calculateStandings(phase.getId(), null)).thenReturn(List.of());
		when(driverRankingService.calculateRankingForPhase(phase.getId())).thenReturn(List.of());

		// when
		var view = service.buildView(phaseId, null, null);

		// then
		assertThat(view.combinedView()).isTrue();
		assertThat(view.showGroupColumn()).isTrue();
		assertThat(view.groups()).hasSize(2);
	}

	@Test
	void givenSwissFormatWithGroupSelected_whenBuildView_thenUsesBuchholz() {
		// given
		UUID phaseId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		var season = new Season("Test-Swiss", 2099, 6);
		setId(season, UUID.randomUUID());
		var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.GROUPS, 0);
		phase.setFormat(SeasonFormat.SWISS);
		when(seasonPhaseService.findById(phaseId)).thenReturn(phase);
		when(seasonManagementService.findByIdOptional(season.getId())).thenReturn(Optional.of(season));
		when(seasonPhaseService.findAllPhases(season.getId())).thenReturn(List.of(phase));
		when(standingsService.calculateStandingsWithBuchholz(phase.getId(), groupId)).thenReturn(List.of());
		when(driverRankingService.calculateRankingForPhase(phase.getId())).thenReturn(List.of());

		// when
		var view = service.buildView(phaseId, groupId, null);

		// then
		assertThat(view.showBuchholz()).isTrue();
		verify(standingsService).calculateStandingsWithBuchholz(phase.getId(), groupId);
		verify(standingsService, never()).calculateStandings(eq(phase.getId()), any());
	}

	/** Reflection helper — JPA entities have UUID id field generated by Hibernate; tests need to set it. */
	private static void setId(Object entity, UUID id) {
		try {
			var field = entity.getClass().getDeclaredField("id");
			field.setAccessible(true);
			field.set(entity, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Cannot set id on " + entity.getClass(), e);
		}
	}
}
