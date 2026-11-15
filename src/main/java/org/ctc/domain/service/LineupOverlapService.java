package org.ctc.domain.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.LineupTargetRules;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.LineupSubmissionRepository;
import org.ctc.domain.repository.LineupSubmissionRevisionRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A driver fields for only one team of an organisation per matchday. The rule compares stored lineup rows and the
 * latest submission of every sister team on the same matchday.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LineupOverlapService {
    private final RaceLineupRepository lineups;
    private final LineupSubmissionRepository submissions;
    private final LineupSubmissionRevisionRepository revisions;
    private final SeasonDriverRepository seasonDrivers;
    private final DriverRepository drivers;

    /**
     * @param assignments driver ID to the team the write fields it for
     * @param replaced    rows on {@code race} that the write replaces and therefore do not conflict
     */
    public List<Conflict> conflicts(Race race, Map<UUID, Team> assignments, Predicate<RaceLineup> replaced) {
        if (assignments.isEmpty() || race.getMatchday() == null) return List.of();
        var found = new LinkedHashMap<String, Conflict>();
        for (var row : lineups.findByRaceMatchdayId(race.getMatchday().getId())) {
            if (row.getRace().getId().equals(race.getId()) && replaced.test(row)) continue;
            var assigned = assignments.get(row.getDriver().getId());
            if (overlaps(assigned, row.getTeam())) {
                found.putIfAbsent(row.getDriver().getId() + ":" + row.getTeam().getId(),
                        new Conflict(row.getDriver().getId(), row.getDriver().getPsnId(), assigned, row.getTeam()));
            }
        }
        var raceIds = race.getMatchday().getRaces().stream().map(Race::getId).toList();
        for (var submission : submissions.findByRaceIdIn(raceIds)) {
            var revision = revisions.findBySubmissionIdAndRevisionNumber(submission.getId(), submission.getLatestRevisionNumber()).orElse(null);
            if (revision == null) continue;
            for (var entry : revision.lineup().entries()) {
                if (entry.driverId() == null) continue;
                var assigned = assignments.get(entry.driverId());
                if (overlaps(assigned, submission.getTeam())) {
                    found.putIfAbsent(entry.driverId() + ":" + submission.getTeam().getId(),
                            new Conflict(entry.driverId(), entry.psnId(), assigned, submission.getTeam()));
                }
            }
        }
        return List.copyOf(found.values());
    }

    public void requireNone(Race race, Map<UUID, Team> assignments, Predicate<RaceLineup> replaced) {
        var found = conflicts(race, assignments, replaced);
        if (!found.isEmpty()) throw new BusinessRuleException(message(found));
    }

    /** Drivers registered this season for a team outside the fielding organisation; this is allowed but worth a look. */
    public List<String> foreignWarnings(Race race, Team fielding, Collection<UUID> driverIds) {
        var season = race.getMatchday().getSeason();
        var warnings = new ArrayList<String>();
        for (var driverId : driverIds) {
            seasonDrivers.findBySeasonIdAndDriverId(season.getId(), driverId)
                    .filter(registration -> !LineupTargetRules.sameOrganisation(registration.getTeam(), fielding))
                    .ifPresent(registration -> warnings.add(drivers.findById(driverId).map(d -> d.getPsnId()).orElse(driverId.toString())
                            + " is registered for " + registration.getTeam().getShortName() + " this season"));
        }
        return List.copyOf(warnings);
    }

    public static String message(List<Conflict> conflicts) {
        var text = new StringBuilder("A driver may field for only one team of an organisation on a matchday: ");
        for (int i = 0; i < conflicts.size(); i++) {
            var conflict = conflicts.get(i);
            if (i > 0) text.append("; ");
            text.append(conflict.psnId()).append(" is already in the lineup of ").append(conflict.otherTeam().getShortName());
        }
        return text.toString();
    }

    private static boolean overlaps(Team assigned, Team other) {
        return assigned != null && LineupTargetRules.sameOrganisation(assigned, other) && !assigned.getId().equals(other.getId());
    }

    public record Conflict(UUID driverId, String psnId, Team fieldingTeam, Team otherTeam) { }
}
