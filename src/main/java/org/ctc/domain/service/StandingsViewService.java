package org.ctc.domain.service;

import static org.springframework.util.StringUtils.hasText;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.StandingsView;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.model.SeasonPhase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the {@link StandingsView} contract for the {@code admin/standings.html} template.
 *
 * <p>Encapsulates the resolution flow (alltime / explicit phase / legacy seasonId / no-params
 * fallback) and every lazy-collection access inside a single {@code @Transactional(readOnly = true)}
 * boundary. The controller becomes a thin delegate that unfurls the resulting record to flat
 * model attributes (D-23 Option a) so existing {@code StandingsControllerTest} attribute
 * assertions stay green.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StandingsViewService {

	private final StandingsService standingsService;
	private final DriverRankingService driverRankingService;
	private final SeasonManagementService seasonManagementService;
	private final SeasonPhaseService seasonPhaseService;

	@Transactional(readOnly = true)
	public StandingsView buildView(UUID phase, UUID group, String seasonId) {
		var seasons = seasonManagementService.findAll();

		if ("alltime".equals(seasonId)) {
			return new StandingsView(
					seasons,
					true,
					null,
					seasonId,
					List.of(),
					null,
					List.of(),
					null,
					standingsService.calculateAlltimeStandings(),
					driverRankingService.calculateAlltimeRanking(),
					false,
					false,
					false,
					false);
		}

		// Resolution priority: explicit phase param > legacy seasonId param > active season fallback
		SeasonPhase resolvedPhase = null;
		UUID resolvedSeasonId = null;

		if (phase != null) {
			resolvedPhase = seasonPhaseService.findById(phase);
			resolvedSeasonId = resolvedPhase.getSeason().getId();
		} else if (hasText(seasonId)) {
			try {
				resolvedSeasonId = UUID.fromString(seasonId);
				resolvedPhase = seasonPhaseService.findByType(resolvedSeasonId, PhaseType.REGULAR).orElse(null);
			} catch (IllegalArgumentException ignored) {
				log.debug("Invalid season ID format: {}", seasonId);
			}
		} else {
			var activeSeason = seasonManagementService.findActiveSeason().orElse(null);
			if (activeSeason != null) {
				resolvedSeasonId = activeSeason.getId();
				resolvedPhase = seasonPhaseService.findByType(resolvedSeasonId, PhaseType.REGULAR).orElse(null);
			}
		}

		if (resolvedSeasonId == null) {
			// No selection — bare page with dropdown only.
			return new StandingsView(
					seasons, false, null, seasonId, List.of(), null, List.of(), null,
					null, null, false, false, false, false);
		}

		final UUID finalResolvedSeasonId = resolvedSeasonId;
		var selectedSeason = seasonManagementService.findByIdOptional(finalResolvedSeasonId)
				.orElseThrow(() -> new EntityNotFoundException("Season", finalResolvedSeasonId));
		var allPhases = seasonPhaseService.findAllPhases(finalResolvedSeasonId);

		if (resolvedPhase == null) {
			// Legacy ?seasonId= but season has no REGULAR phase.
			return new StandingsView(
					seasons, false, selectedSeason, seasonId, allPhases, null, List.of(), null,
					List.of(), null, false, false, false, false);
		}

		// Eager-fetch groups now that the phase is resolved (inside the readOnly transaction).
		var groups = List.copyOf(resolvedPhase.getGroups());
		boolean isGroupsLayout = resolvedPhase.getLayout() == PhaseLayout.GROUPS;
		boolean groupSelected = group != null;
		boolean combinedView = isGroupsLayout && !groupSelected;
		boolean showGroupColumn = combinedView;
		boolean showBuchholz = resolvedPhase.getFormat() == SeasonFormat.SWISS && groupSelected;

		var standings = showBuchholz
				? standingsService.calculateStandingsWithBuchholz(resolvedPhase.getId(), group)
				: standingsService.calculateStandings(resolvedPhase.getId(), group);
		var driverRanking = driverRankingService.calculateRankingForPhase(resolvedPhase.getId());

		log.debug("Standings for phase={} group={}: combinedView={} showBuchholz={} showGroupColumn={}",
				resolvedPhase.getId(), group, combinedView, showBuchholz, showGroupColumn);

		return new StandingsView(
				seasons, false, selectedSeason, seasonId, allPhases, resolvedPhase, groups, group,
				standings, driverRanking, true, combinedView, showGroupColumn, showBuchholz);
	}
}
