package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.SeasonPhaseGroupRepository;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Central phase resolver and CRUD service for {@link SeasonPhase}, {@link SeasonPhaseGroup},
 * and {@link PhaseTeam}.
 *
 * <p>Key decisions implemented:
 * <ul>
 *   <li>D-02: {@link #findRegularPhase(UUID)} is the single resolution point for the REGULAR phase.
 *       Fails loud ({@link EntityNotFoundException}) if no REGULAR phase exists.</li>
 *   <li>D-14: {@link #create} enforces the "max 1 phase per type per season" rule before INSERT.
 *       DB {@code UNIQUE(season_id, phase_type)} is the belt; this service guard is the suspenders.</li>
 *   <li>D-20: REGULAR+LEAGUE creation auto-derives {@link PhaseTeam} from existing {@link SeasonTeam}
 *       rows. PLAYOFF, PLACEMENT, and REGULAR+GROUPS leave the roster empty.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonPhaseService {

    private final SeasonPhaseRepository seasonPhaseRepository;
    private final SeasonPhaseGroupRepository seasonPhaseGroupRepository;
    private final PhaseTeamRepository phaseTeamRepository;
    private final SeasonRepository seasonRepository;

    // ---------------------------------------------------------------------------
    // Read methods
    // ---------------------------------------------------------------------------

    /**
     * Returns the REGULAR phase for the given season, or throws {@link EntityNotFoundException}
     * if none exists (D-02 fail-loud — should never happen post-V4 migration).
     */
    @Transactional(readOnly = true)
    public SeasonPhase findRegularPhase(UUID seasonId) {
        return seasonPhaseRepository.findBySeasonIdAndPhaseType(seasonId, PhaseType.REGULAR)
                .orElseThrow(() -> new EntityNotFoundException("Regular SeasonPhase for season", seasonId)); // D-02
    }

    /**
     * Returns the phase of the given type for the season, or {@link Optional#empty()} if absent.
     * Used by D-19 (PlayoffService.createPlayoff) and D-25 (SeasonManagementService.save).
     */
    @Transactional(readOnly = true)
    public Optional<SeasonPhase> findByType(UUID seasonId, PhaseType type) {
        return seasonPhaseRepository.findBySeasonIdAndPhaseType(seasonId, type);
    }

    /**
     * Returns the phase by id, or throws {@link EntityNotFoundException}.
     */
    @Transactional(readOnly = true)
    public SeasonPhase findById(UUID phaseId) {
        return seasonPhaseRepository.findById(phaseId)
                .orElseThrow(() -> new EntityNotFoundException("SeasonPhase", phaseId));
    }

    /**
     * Returns all phases for the season ordered by {@code sortIndex} ascending.
     * Used by D-09 (DriverRankingService.aggregateAcrossPhases bridge) and
     * D-26 (MatchdayService.findBySeasonId bridge).
     */
    @Transactional(readOnly = true)
    public List<SeasonPhase> findAllPhases(UUID seasonId) {
        return seasonPhaseRepository.findBySeasonIdOrderBySortIndex(seasonId);
    }

    // ---------------------------------------------------------------------------
    // Write methods
    // ---------------------------------------------------------------------------

    /**
     * Creates a new {@link SeasonPhase} for the given season.
     *
     * <p>D-14 duplicate guard: throws {@link BusinessRuleException} if a phase of this type
     * already exists for the season. DB UNIQUE is the belt; this guard is the suspenders
     * for meaningful error messages.
     *
     * <p>D-20 roster init: REGULAR+LEAGUE auto-derives {@link PhaseTeam} rows from existing
     * {@link SeasonTeam} entries. All other phase types / layouts start with an empty roster.
     */
    @Transactional
    public SeasonPhase create(UUID seasonId, PhaseType type, PhaseLayout layout, int sortIndex,
                              String label, RaceScoring raceScoring, MatchScoring matchScoring,
                              SeasonFormat format, LocalDate startDate, LocalDate endDate,
                              Integer totalRounds, int legs, Integer eventDurationMinutes) {

        // D-14: belt-and-suspenders duplicate guard
        if (seasonPhaseRepository.findBySeasonIdAndPhaseType(seasonId, type).isPresent()) {
            throw new BusinessRuleException("Season already has " + type + " phase");
        }

        var season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));

        var phase = new SeasonPhase(season, type, layout, sortIndex);
        phase.setLabel(label);
        phase.setRaceScoring(raceScoring);
        phase.setMatchScoring(matchScoring);
        if (format != null) {
            phase.setFormat(format);
        }
        phase.setStartDate(startDate);
        phase.setEndDate(endDate);
        phase.setTotalRounds(totalRounds);
        phase.setLegs(legs);
        phase.setEventDurationMinutes(eventDurationMinutes);

        phase = seasonPhaseRepository.save(phase);

        // D-20: REGULAR+LEAGUE auto-derives PhaseTeam from SeasonTeam; other types leave roster empty
        if (type == PhaseType.REGULAR && layout == PhaseLayout.LEAGUE) {
            for (SeasonTeam st : season.getSeasonTeams()) {
                phaseTeamRepository.save(new PhaseTeam(phase, st.getTeam()));
            }
        }

        log.info("Created {} phase (layout={}) for season {}", type, layout, season.getName());
        return phase;
    }

    /**
     * Creates a new {@link SeasonPhaseGroup} for the given phase.
     */
    @Transactional
    public SeasonPhaseGroup createGroup(UUID phaseId, String name, int sortIndex) {
        var phase = findById(phaseId);
        var group = new SeasonPhaseGroup(phase, name, sortIndex);
        var saved = seasonPhaseGroupRepository.save(group);
        log.info("Created group '{}' (sortIndex={}) for phase {}", name, sortIndex, phaseId);
        return saved;
    }

    /**
     * Assigns a team to a phase (and optionally a group within that phase).
     *
     * @param groupId nullable — null for LEAGUE-layout phases, non-null for GROUPS-layout
     */
    @Transactional
    public PhaseTeam assignTeamToPhase(UUID phaseId, UUID teamId, UUID groupId) {
        var phase = findById(phaseId);
        var team = new Team();
        team.setId(teamId);
        var pt = new PhaseTeam(phase, team);
        if (groupId != null) {
            var group = seasonPhaseGroupRepository.findById(groupId)
                    .orElseThrow(() -> new EntityNotFoundException("SeasonPhaseGroup", groupId));
            pt.setGroup(group);
        }
        var saved = phaseTeamRepository.save(pt);
        log.info("Assigned team {} to phase {} (group={})", teamId, phaseId, groupId);
        return saved;
    }
}
