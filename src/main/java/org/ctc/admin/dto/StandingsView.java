package org.ctc.admin.dto;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.service.DriverRankingService.DriverRanking;
import org.ctc.domain.service.StandingsService.TeamStanding;

/**
 * Read-only view contract consumed by {@code admin/standings.html}. Pre-resolves every value
 * the template needs so the controller never accesses a JPA-lazy collection (QUAL-04 — no
 * OSIV-only traversal in controller code).
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code seasons} — dropdown source for the season picker.</li>
 *   <li>{@code isAlltime} — {@code true} when {@code seasonId=alltime} was selected.</li>
 *   <li>{@code selectedSeason} / {@code selectedSeasonId} — the resolved season (or {@code null}
 *       on the no-selection landing).</li>
 *   <li>{@code allPhases} — phase tab list for the selected season.</li>
 *   <li>{@code phase} — the resolved phase (or {@code null} when no REGULAR phase exists).</li>
 *   <li>{@code groups} — the phase's groups, eagerly loaded inside the view service's
 *       {@code @Transactional(readOnly = true)} boundary.</li>
 *   <li>{@code selectedGroupId} — narrows the standings view to a single sub-group.</li>
 *   <li>{@code standings} / {@code driverRanking} — the rendered tables.</li>
 *   <li>{@code hasRegularPhase} / {@code combinedView} / {@code showGroupColumn} /
 *       {@code showBuchholz} — template-rendering flags.</li>
 * </ul>
 */
public record StandingsView(
		List<Season> seasons,
		boolean isAlltime,
		Season selectedSeason,
		String selectedSeasonId,
		List<SeasonPhase> allPhases,
		SeasonPhase phase,
		List<SeasonPhaseGroup> groups,
		UUID selectedGroupId,
		List<TeamStanding> standings,
		List<DriverRanking> driverRanking,
		boolean hasRegularPhase,
		boolean combinedView,
		boolean showGroupColumn,
		boolean showBuchholz) {
}
