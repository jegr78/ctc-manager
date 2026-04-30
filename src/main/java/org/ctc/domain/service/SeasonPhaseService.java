package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

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
 *   <li>D-21: {@link #update} blocks layout changes when matchdays exist (strict guard).</li>
 *   <li>D-23: {@link #delete} blocks deletion when matchdays, phase teams, or playoff bracket exist.</li>
 *   <li>D-28: {@link #deleteGroup} blocks deletion when group has teams or matchdays.</li>
 *   <li>W-6: {@link #createBootstrap} idempotent REGULAR-phase auto-bootstrap for new seasons.</li>
 *   <li>W-8: {@link #getRosterEditorState} single-query service-side data prep for roster editor.</li>
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
    // Phase 60 D-21/D-23: guard checks require matchday + phase-team counts
    private final MatchdayRepository matchdayRepository;
    // Phase 60 D-20 assignTeamsToPhase: need Team entity lookup
    private final TeamRepository teamRepository;
    // Phase 60 update: resolve scoring entities by ID
    private final RaceScoringRepository raceScoringRepository;
    private final MatchScoringRepository matchScoringRepository;
    // Phase 60 D-23: PLAYOFF phase guard — check if a playoff bracket exists
    private final PlayoffRepository playoffRepository;

    // ---------------------------------------------------------------------------
    // Records
    // ---------------------------------------------------------------------------

    /**
     * Input record for {@link #assignTeamsToPhase} bulk diff logic (D-20, RESEARCH Pitfall 8).
     */
    public record Assignment(UUID teamId, boolean included, UUID groupId) {}

    /**
     * Service-side data preparation for the roster editor (W-8 — single repo call per Lean-Templates rule).
     */
    public record RosterEditorState(Set<UUID> assignedTeamIds,
                                    Map<UUID, UUID> currentGroupByTeamId) {}

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

    /**
     * Service-side data preparation for the roster editor — single repo call per CLAUDE.md
     * "Keep Thymeleaf Templates Lean" (W-8).
     */
    @Transactional(readOnly = true)
    public RosterEditorState getRosterEditorState(UUID phaseId) {
        var pts = phaseTeamRepository.findByPhaseId(phaseId);   // single repo call (W-8)
        var assignedTeamIds = pts.stream()
                .map(pt -> pt.getTeam().getId())
                .collect(Collectors.toSet());
        var currentGroupByTeamId = pts.stream()
                .filter(pt -> pt.getGroup() != null)
                .collect(Collectors.toMap(
                        pt -> pt.getTeam().getId(),
                        pt -> pt.getGroup().getId()));
        return new RosterEditorState(assignedTeamIds, currentGroupByTeamId);
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
     * Idempotent REGULAR-phase auto-bootstrap for newly-created seasons (W-6).
     *
     * <p>Null-tolerant create that re-uses Phase 58 D-14 UNIQUE pre-check path so we never
     * bypass invariants. If a REGULAR phase already exists, returns it (idempotent — safe
     * to call after a partial save).
     */
    @Transactional
    public SeasonPhase createBootstrap(Season season) {
        // W-6: idempotent — return existing REGULAR phase if present
        var existing = seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR);
        if (existing.isPresent()) {
            return existing.get();
        }
        return create(season.getId(), PhaseType.REGULAR, PhaseLayout.LEAGUE,
                /* sortIndex */ 0, /* label */ null,
                /* raceScoring */ null, /* matchScoring */ null,
                /* format */ null,
                /* startDate */ null, /* endDate */ null,
                /* totalRounds */ null, /* legs */ 1, /* eventDurationMinutes */ null);
    }

    /**
     * Updates mutable fields of an existing {@link SeasonPhase} (D-21, D-22).
     *
     * <p>D-21 strict guard: blocks layout changes when matchdays exist.
     * D-22 compatibility: BRACKET layout only valid for PLAYOFF phases.
     * Note: phaseType is immutable post-create (W-11) — update signature intentionally omits it.
     */
    @Transactional
    public SeasonPhase update(UUID phaseId,
                              PhaseLayout layout,
                              SeasonFormat format,
                              UUID raceScoringId,
                              UUID matchScoringId,
                              LocalDate startDate,
                              LocalDate endDate,
                              Integer totalRounds,
                              int legs,
                              Integer eventDurationMinutes,
                              String label,
                              Integer sortIndex) {
        var phase = seasonPhaseRepository.findById(phaseId)
                .orElseThrow(() -> new EntityNotFoundException("SeasonPhase", phaseId));

        // D-21 strict guard: layout change blocked if matchdays exist
        var matchdays = matchdayRepository.findByPhaseId(phaseId);
        if (!matchdays.isEmpty() && phase.getLayout() != layout) {
            throw new BusinessRuleException(
                    "Phase has " + matchdays.size() + " matchdays — changing layout requires deleting them first.");
        }
        // D-22 layout/format compatibility: BRACKET only allowed for PLAYOFF
        if (layout == PhaseLayout.BRACKET && phase.getPhaseType() != PhaseType.PLAYOFF) {
            throw new BusinessRuleException("BRACKET layout is only valid for PLAYOFF phases.");
        }

        phase.setLayout(layout);
        phase.setFormat(format);
        phase.setRaceScoring(raceScoringId != null
                ? raceScoringRepository.findById(raceScoringId)
                        .orElseThrow(() -> new EntityNotFoundException("RaceScoring", raceScoringId))
                : null);
        phase.setMatchScoring(matchScoringId != null
                ? matchScoringRepository.findById(matchScoringId)
                        .orElseThrow(() -> new EntityNotFoundException("MatchScoring", matchScoringId))
                : null);
        phase.setStartDate(startDate);
        phase.setEndDate(endDate);
        phase.setTotalRounds(totalRounds);
        phase.setLegs(legs);
        phase.setEventDurationMinutes(eventDurationMinutes);
        phase.setLabel(label);
        if (sortIndex != null) phase.setSortIndex(sortIndex);
        log.info("Updated phase {} (type={}, layout={})", phaseId, phase.getPhaseType(), layout);
        return seasonPhaseRepository.save(phase);
    }

    /**
     * Deletes a {@link SeasonPhase} after strict guards (D-23).
     *
     * <p>Refuses deletion when the phase has matchdays, phase-team roster entries, or a
     * playoff bracket. User must clear those first — no silent cascade per CLAUDE.md
     * "No Fallback Calculations".
     */
    @Transactional
    public void delete(UUID phaseId) {
        var phase = seasonPhaseRepository.findById(phaseId)
                .orElseThrow(() -> new EntityNotFoundException("SeasonPhase", phaseId));

        var matchdayCount = matchdayRepository.findByPhaseId(phaseId).size();
        if (matchdayCount > 0) {
            throw new BusinessRuleException("Phase has " + matchdayCount + " matchdays — clear them first.");
        }
        var phaseTeamCount = phaseTeamRepository.findByPhaseId(phaseId).size();
        if (phaseTeamCount > 0) {
            throw new BusinessRuleException("Phase has " + phaseTeamCount + " teams in roster — clear them first.");
        }
        // PLAYOFF phases additionally guard against playoff bracket via Playoff entity
        if (phase.getPhaseType() == PhaseType.PLAYOFF) {
            var playoffOpt = playoffRepository.findByPhaseId(phaseId);
            if (playoffOpt.isPresent()) {
                throw new BusinessRuleException("Phase has a playoff bracket — delete the playoff first.");
            }
        }
        log.info("Deleting phase {} ({})", phaseId, phase.getPhaseType());
        seasonPhaseRepository.delete(phase);
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
     * Updates name and/or sortIndex of an existing {@link SeasonPhaseGroup} (D-24).
     */
    @Transactional
    public SeasonPhaseGroup updateGroup(UUID groupId, String name, Integer sortIndex) {
        var group = seasonPhaseGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("SeasonPhaseGroup", groupId));
        group.setName(name);
        if (sortIndex != null) group.setSortIndex(sortIndex);
        log.info("Updated group {} ({})", groupId, name);
        return seasonPhaseGroupRepository.save(group);
    }

    /**
     * Deletes a {@link SeasonPhaseGroup} after strict guards (D-28).
     *
     * <p>Refuses deletion when the group has team assignments or matchdays.
     */
    @Transactional
    public void deleteGroup(UUID groupId) {
        var group = seasonPhaseGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("SeasonPhaseGroup", groupId));

        var teamCount = phaseTeamRepository.findByPhaseIdAndGroupId(group.getPhase().getId(), groupId).size();
        if (teamCount > 0) {
            throw new BusinessRuleException("Group has " + teamCount + " teams — reassign them first.");
        }
        var matchdayCount = matchdayRepository.findByGroupId(groupId).size();
        if (matchdayCount > 0) {
            throw new BusinessRuleException("Group has " + matchdayCount + " matchdays — clear them first.");
        }
        log.info("Deleting group {} ({})", groupId, group.getName());
        seasonPhaseGroupRepository.delete(group);
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

    /**
     * Bulk diff-save for the roster editor (D-20, RESEARCH Pitfall 8).
     *
     * <p>Compares the submitted {@link Assignment} list against the current {@link PhaseTeam} rows
     * and applies minimal inserts, updates (group change), and deletes in a single transaction.
     */
    @Transactional
    public void assignTeamsToPhase(UUID phaseId, List<Assignment> assignments) {
        var phase = seasonPhaseRepository.findById(phaseId)
                .orElseThrow(() -> new EntityNotFoundException("SeasonPhase", phaseId));

        var existing = phaseTeamRepository.findByPhaseId(phaseId);
        var existingByTeamId = existing.stream()
                .collect(Collectors.toMap(pt -> pt.getTeam().getId(), pt -> pt));

        Set<UUID> includedTeamIds = new HashSet<>();
        for (var a : assignments) {
            if (!a.included()) continue;
            includedTeamIds.add(a.teamId());

            var pt = existingByTeamId.get(a.teamId());
            if (pt == null) {
                // INSERT
                var team = teamRepository.findById(a.teamId())
                        .orElseThrow(() -> new EntityNotFoundException("Team", a.teamId()));
                var newPt = new PhaseTeam(phase, team);
                if (a.groupId() != null) {
                    newPt.setGroup(seasonPhaseGroupRepository.findById(a.groupId())
                            .orElseThrow(() -> new EntityNotFoundException("SeasonPhaseGroup", a.groupId())));
                }
                phaseTeamRepository.save(newPt);
            } else {
                // UPDATE only if group changed
                UUID currentGroupId = pt.getGroup() != null ? pt.getGroup().getId() : null;
                if (!Objects.equals(currentGroupId, a.groupId())) {
                    pt.setGroup(a.groupId() != null
                            ? seasonPhaseGroupRepository.findById(a.groupId())
                                    .orElseThrow(() -> new EntityNotFoundException("SeasonPhaseGroup", a.groupId()))
                            : null);
                    phaseTeamRepository.save(pt);
                }
            }
        }
        // DELETE removed
        for (var pt : existing) {
            if (!includedTeamIds.contains(pt.getTeam().getId())) {
                phaseTeamRepository.delete(pt);
            }
        }
        log.info("Assigned {} teams to phase {} (existing: {})", includedTeamIds.size(), phaseId, existing.size());
    }
}
