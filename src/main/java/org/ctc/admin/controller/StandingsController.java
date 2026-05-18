package org.ctc.admin.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.service.StandingsViewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/admin/standings")
@RequiredArgsConstructor
public class StandingsController {

	private final StandingsViewService standingsViewService;

	/**
	 * GET {@code /admin/standings} — renders the standings + driver ranking page.
	 *
	 * <p>All resolution + lazy-collection work happens inside {@link StandingsViewService#buildView}
	 * under a readOnly transaction (QUAL-04 — no OSIV-only traversal in controller code). The
	 * controller unfurls the resulting {@code StandingsView} to flat model attributes so the
	 * Thymeleaf template + existing {@code StandingsControllerTest} attribute assertions stay
	 * unchanged.
	 */
	@GetMapping
	public String standings(@RequestParam(required = false) UUID phase,
	                        @RequestParam(required = false) UUID group,
	                        @RequestParam(required = false) String seasonId,
	                        Model model) {
		var view = standingsViewService.buildView(phase, group, seasonId);

		model.addAttribute("seasons", view.seasons());
		model.addAttribute("isAlltime", view.isAlltime());
		model.addAttribute("hasRegularPhase", view.hasRegularPhase());

		if (view.selectedSeason() != null) {
			model.addAttribute("selectedSeason", view.selectedSeason());
		}
		if (view.selectedSeasonId() != null) {
			model.addAttribute("selectedSeasonId", view.selectedSeasonId());
		}
		if (!view.allPhases().isEmpty()) {
			model.addAttribute("allPhases", view.allPhases());
		}
		if (view.standings() != null) {
			model.addAttribute("standings", view.standings());
		}
		if (view.driverRanking() != null) {
			model.addAttribute("driverRanking", view.driverRanking());
		}

		// Phase-only branch flags + groups list — only emitted when a phase was resolved or
		// the legacy seasonId branch needs the empty defaults.
		if (view.isAlltime() || view.phase() != null || (view.selectedSeason() != null && !view.hasRegularPhase())) {
			model.addAttribute("phase", view.phase());
			model.addAttribute("groups", view.groups());
			model.addAttribute("combinedView", view.combinedView());
			model.addAttribute("showGroupColumn", view.showGroupColumn());
			model.addAttribute("showBuchholz", view.showBuchholz());
		}
		if (view.phase() != null) {
			model.addAttribute("selectedGroupId", view.selectedGroupId());
		}

		return "admin/standings";
	}
}
