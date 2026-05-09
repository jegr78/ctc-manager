package org.ctc.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.service.DriverRankingService;
import org.ctc.domain.service.SeasonManagementService;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.service.StandingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/standings")
@RequiredArgsConstructor
public class StandingsController {

	private final StandingsService standingsService;
	private final DriverRankingService driverRankingService;
	private final SeasonManagementService seasonManagementService;
	private final SeasonPhaseService seasonPhaseService;

	/**
	 * GET {@code /admin/standings} — renders the standings + driver ranking page.
	 *
	 * <p>Resolution priority for {@code phase}, {@code seasonId}, and {@code group}:
	 * <ol>
	 *   <li>{@code seasonId=alltime} → all-time aggregation across every season.</li>
	 *   <li>Explicit {@code phase} UUID → the canonical phase-keyed view.</li>
	 *   <li>Legacy {@code seasonId=<uuid>} → auto-resolves to the season's REGULAR phase
	 *       (no 404 if the season has no REGULAR phase; the page renders with empty data).</li>
	 *   <li>No params → falls back to the active season's REGULAR phase, or an empty page
	 *       if no active season exists.</li>
	 * </ol>
	 *
	 * <p>{@code group} narrows the view to a single sub-group when the resolved phase has
	 * GROUPS layout. {@code phase} takes precedence if both {@code phase} and
	 * {@code seasonId} are present.
	 */
	@GetMapping
	public String standings(@RequestParam(required = false) UUID phase,
	                        @RequestParam(required = false) UUID group,
	                        @RequestParam(required = false) String seasonId,
	                        Model model) {

		model.addAttribute("seasons", seasonManagementService.findAll());

		// Alltime branch — preserved unchanged
		boolean isAlltime = "alltime".equals(seasonId);
		if (isAlltime) {
			model.addAttribute("isAlltime", true);
			model.addAttribute("standings", standingsService.calculateAlltimeStandings());
			model.addAttribute("driverRanking", driverRankingService.calculateAlltimeRanking());
			model.addAttribute("selectedSeasonId", seasonId);
			model.addAttribute("phase", null);
			model.addAttribute("hasRegularPhase", false);
			model.addAttribute("combinedView", false);
			model.addAttribute("showBuchholz", false);
			model.addAttribute("showGroupColumn", false);
			model.addAttribute("allPhases", List.of());
			model.addAttribute("groups", List.of());
			return "admin/standings";
		}
		model.addAttribute("isAlltime", false);

		// Resolution priority: explicit phase param > legacy seasonId param > active season fallback
		SeasonPhase resolvedPhase = null;
		UUID resolvedSeasonId = null;

		if (phase != null) {
			// Canonical: explicit phase UUID provided
			resolvedPhase = seasonPhaseService.findById(phase);
			resolvedSeasonId = resolvedPhase.getSeason().getId();
		} else if (seasonId != null && !seasonId.isBlank()) {
			// Legacy bridge: ?seasonId={uuid} → auto-resolve to REGULAR phase (no 404).
			try {
				resolvedSeasonId = UUID.fromString(seasonId);
				// Use findByType (Optional) not findRegularPhase (which throws).
				var regularOpt = seasonPhaseService.findByType(resolvedSeasonId, PhaseType.REGULAR);
				if (regularOpt.isPresent()) {
					resolvedPhase = regularOpt.get();
				}
			} catch (IllegalArgumentException ignored) {
				log.debug("Invalid season ID format: {}", seasonId);
			}
		} else {
			// No params: fall back to active season for backward compat with existing tests
			var activeSeason = seasonManagementService.findActiveSeason().orElse(null);
			if (activeSeason != null) {
				resolvedSeasonId = activeSeason.getId();
				var regularOpt = seasonPhaseService.findByType(resolvedSeasonId, PhaseType.REGULAR);
				if (regularOpt.isPresent()) {
					resolvedPhase = regularOpt.get();
				}
			}
		}

		if (resolvedSeasonId == null) {
			// No selection — render bare page with dropdown only.
			// Do NOT add selectedSeason/standings/driverRanking — tests assert these don't exist.
			model.addAttribute("hasRegularPhase", false);
			return "admin/standings";
		}

		// Selected season — capture effectively-final copy for lambda
		final UUID finalResolvedSeasonId = resolvedSeasonId;
		var selectedSeason = seasonManagementService.findByIdOptional(finalResolvedSeasonId)
				.orElseThrow(() -> new EntityNotFoundException("Season", finalResolvedSeasonId));
		model.addAttribute("selectedSeason", selectedSeason);
		model.addAttribute("selectedSeasonId", seasonId);
		model.addAttribute("allPhases", seasonPhaseService.findAllPhases(finalResolvedSeasonId));

		if (resolvedPhase == null) {
			// Legacy ?seasonId= but season has no REGULAR phase.
			model.addAttribute("phase", null);
			model.addAttribute("hasRegularPhase", false);
			model.addAttribute("combinedView", false);
			model.addAttribute("showBuchholz", false);
			model.addAttribute("showGroupColumn", false);
			model.addAttribute("standings", List.of());
			model.addAttribute("groups", List.of());
			return "admin/standings";
		}

		// Phase resolved — load groups + standings
		model.addAttribute("phase", resolvedPhase);
		model.addAttribute("hasRegularPhase", true);
		model.addAttribute("groups", resolvedPhase.getGroups());
		model.addAttribute("selectedGroupId", group);

		boolean isGroupsLayout = resolvedPhase.getLayout() == PhaseLayout.GROUPS;
		boolean groupSelected = group != null;

		boolean combinedView = isGroupsLayout && !groupSelected;
		boolean showGroupColumn = combinedView;
		boolean showBuchholz = resolvedPhase.getFormat() == SeasonFormat.SWISS && groupSelected;

		model.addAttribute("combinedView", combinedView);
		model.addAttribute("showGroupColumn", showGroupColumn);
		model.addAttribute("showBuchholz", showBuchholz);

		var standings = showBuchholz
				? standingsService.calculateStandingsWithBuchholz(resolvedPhase.getId(), group)
				: standingsService.calculateStandings(resolvedPhase.getId(), group);
		model.addAttribute("standings", standings);

		model.addAttribute("driverRanking", driverRankingService.calculateRankingForPhase(resolvedPhase.getId()));

		log.debug("Standings for phase={} group={}: combinedView={} showBuchholz={} showGroupColumn={}",
				resolvedPhase.getId(), group, combinedView, showBuchholz, showGroupColumn);

		return "admin/standings";
	}
}
