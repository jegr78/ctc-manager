package org.ctc.domain.service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central phase resolver and CRUD service for {@link SeasonPhase}, {@link SeasonPhaseGroup},
 * and {@link PhaseTeam}.
 *
 * <p>Contract highlights:
 * <ul>
 *   <li>{@link #findRegularPhase(UUID)} is the single resolution point for the REGULAR phase
 *       and fails loud ({@link EntityNotFoundException}) if none exists.</li>
 *   <li>{@link #create} enforces the "max 1 phase per type per season" rule before INSERT.
 *       The DB {@code UNIQUE(season_id, phase_type)} is the belt; this service guard is the suspenders.</li>
 *   <li>{@link #create} for REGULAR+LEAGUE auto-derives {@link PhaseTeam} from existing {@link SeasonTeam}
 *       rows. PLAYOFF, PLACEMENT, and REGULAR+GROUPS leave the roster empty.</li>
 *   <li>{@link #update} blocks layout changes when matchdays exist.</li>
 *   <li>{@link #delete} blocks deletion when matchdays, phase teams, or a playoff bracket exist.</li>
 *   <li>{@link #deleteGroup} blocks deletion when the group has teams or matchdays.</li>
 *   <li>{@link #createBootstrap} is idempotent REGULAR-phase auto-bootstrap for new seasons.</li>
 *   <li>{@link #getRosterEditorState} provides single-query service-side data prep for the roster editor.</li>
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
    private final MatchdayRepository matchdayRepository;
    private final TeamRepository teamRepository;
    private final RaceScoringRepository raceScoringRepository;
    private final MatchScoringRepository matchScoringRepository;
    private final PlayoffRepository playoffRepository;

    /**
     * Input record for {@link #assignTeamsToPhase} bulk diff logic.
     */
    public record Assignment(UUID teamId, boolean included, UUID groupId) {}

    /**
     * Service-side data preparation for the roster editor — single repo call per Lean-Templates rule.
     */
    public record RosterEditorState(Set<UUID> assignedTeamIds,
                                    Map<UUID, UUID> currentGroupByTeamId) {}

    /**
     * Returns the REGULAR phase for the given season, or throws {@link EntityNotFoundException}
     * if none exists.
     */
    @Transactional(readOnly = true)
    public SeasonPhase findRegularPhase(UUID seasonId) {
        return seasonPhaseRepository.findBySeasonIdAndPhaseType(seasonId, PhaseType.REGULAR)
                .orElseThrow(() -> new EntityNotFoundException("Regular SeasonPhase for season", seasonId));
    }

    /**
     * Returns the phase of the given type for the season, or {@link Optional#empty()} if absent.
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
     */
    @Transactional(readOnly = true)
    public List<SeasonPhase> findAllPhases(UUID seasonId) {
        return seasonPhaseRepository.findBySeasonIdOrderBySortIndex(seasonId);
    }

    /**
     * Service-side data preparation for the roster editor — single repo call per CLAUDE.md
     * "Keep Thymeleaf Templates Lean".
     */
    @Transactional(readOnly = true)
    public RosterEditorState getRosterEditorState(UUID phaseId) {
        var pts = phaseTeamRepository.findByPhaseId(phaseId);
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

    /**
     * Creates a new {@link SeasonPhase} for the given season.
     *
     * <p>Throws {@link BusinessRuleException} if a phase of this type already exists for the
     * season. DB UNIQUE is the belt; this guard is the suspenders for meaningful error messages.
     *
     * <p>Roster init: REGULAR+LEAGUE auto-derives {@link PhaseTeam} rows from existing
     * {@link SeasonTeam} entries. All other phase types / layouts start with an empty roster.
     */
    @Transactional
    public SeasonPhase create(UUID seasonId, PhaseType type, PhaseLayout layout, int sortIndex,
                              String label, RaceScoring raceScoring, MatchScoring matchScoring,
                              SeasonFormat format, LocalDate startDate, LocalDate endDate,
                              Integer totalRounds, int legs, Integer eventDurationMinutes) {

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

        if (type == PhaseType.REGULAR && layout == PhaseLayout.LEAGUE) {
            for (SeasonTeam st : season.getSeasonTeams()) {
                phaseTeamRepository.save(new PhaseTeam(phase, st.getTeam()));
            }
        }

        log.info("Created {} phase (layout={}) for season {}", type, layout, season.getName());
        return phase;
    }

    /**
     * Idempotent REGULAR-phase auto-bootstrap for newly-created seasons.
     *
     * <p>Null-tolerant; reuses the duplicate-check path from {@link #create} so invariants
     * are never bypassed. If a REGULAR phase already exists, returns it (idempotent — safe
     * to call after a partial save).
     */
    @Transactional
    public SeasonPhase createBootstrap(Season season) {
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
     * Updates mutable fields of an existing {@link SeasonPhase}.
     *
     * <p>Layout changes are blocked when matchdays exist; BRACKET layout is only valid for
     * PLAYOFF phases. {@code phaseType} is immutable post-create — the update signature
     * intentionally omits it.
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

        var matchdays = matchdayRepository.findByPhaseId(phaseId);
        if (!matchdays.isEmpty() && phase.getLayout() != layout) {
            throw new BusinessRuleException(
                    "Phase has " + matchdays.size() + " matchdays — changing layout requires deleting them first.");
        }
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
		if (sortIndex != null) {
			phase.setSortIndex(sortIndex);
		}
        log.info("Updated phase {} (type={}, layout={})", phaseId, phase.getPhaseType(), layout);
        return seasonPhaseRepository.save(phase);
    }

    /**
     * Deletes a {@link SeasonPhase} after strict guards.
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
     * Updates name and/or sortIndex of an existing {@link SeasonPhaseGroup}.
     */
    @Transactional
    public SeasonPhaseGroup updateGroup(UUID groupId, String name, Integer sortIndex) {
        var group = seasonPhaseGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("SeasonPhaseGroup", groupId));
        group.setName(name);
		if (sortIndex != null) {
			group.setSortIndex(sortIndex);
		}
        log.info("Updated group {} ({})", groupId, name);
        return seasonPhaseGroupRepository.save(group);
    }

    /**
     * Deletes a {@link SeasonPhaseGroup}. Refuses deletion when the group has team
     * assignments or matchdays.
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
     * Bulk diff-save for the roster editor.
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
			if (!a.included()) {
				continue;
			}
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
