package org.ctc.admin.service;

import static org.ctc.admin.dto.DataAuditFinding.Category.LINEUPS;
import static org.ctc.admin.dto.DataAuditFinding.Resolution.AMBIGUOUS;
import static org.ctc.admin.dto.DataAuditFinding.Resolution.UNDETERMINABLE;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.DataAuditFinding;
import org.ctc.domain.model.LineupPayload;
import org.ctc.domain.model.LineupSubmissionRevision;
import org.ctc.domain.model.LineupWindowEvent;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.LineupSubmissionRepository;
import org.ctc.domain.repository.LineupSubmissionRevisionRepository;
import org.ctc.domain.repository.LineupWindowEventRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reports lineup overlaps within an organisation and inconsistent lineup submission history without repairing them. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LineupIntegrityAuditService {
    private final RaceLineupRepository lineups;
    private final LineupSubmissionRepository submissions;
    private final LineupSubmissionRevisionRepository revisions;
    private final LineupWindowEventRepository windows;

    public List<DataAuditFinding> inspect() {
        var findings = new ArrayList<DataAuditFinding>();
        overlaps(findings);
        history(findings);
        return findings;
    }

    private void overlaps(List<DataAuditFinding> findings) {
        var byMatchdayDriver = new TreeMap<String, Map<UUID, Team>>();
        var labels = new LinkedHashMap<String, String>();
        for (RaceLineup row : lineups.findAllForBackup()) {
            var day = row.getRace().getMatchday();
            if (day == null) continue;
            String key = day.getId() + ":" + row.getDriver().getId();
            byMatchdayDriver.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).putIfAbsent(row.getTeam().getId(), row.getTeam());
            labels.putIfAbsent(key, row.getDriver().getPsnId() + " on matchday " + day.getLabel() + " (" + day.getId() + ")");
        }
        for (var entry : byMatchdayDriver.entrySet()) {
            var byOrganisation = entry.getValue().values().stream().collect(Collectors.groupingBy(team -> team.getParentOrSelf().getId()));
            for (var teams : byOrganisation.values()) {
                if (teams.size() < 2) continue;
                String names = teams.stream().map(Team::getShortName).sorted().collect(Collectors.joining(", "));
                findings.add(new DataAuditFinding(LINEUPS, AMBIGUOUS, "Driver " + labels.get(entry.getKey()),
                        "The driver fields for several teams of one organisation on this matchday: " + names + ".",
                        "Review the race lineups of this matchday and keep the driver in one team's lineup. Results are not changed automatically."));
            }
        }
    }

    private void history(List<DataAuditFinding> findings) {
        var bySubmission = revisions.findAllForBackup().stream()
                .collect(Collectors.groupingBy(revision -> revision.getSubmission().getId()));
        for (var submission : submissions.findAllForBackup()) {
            var race = submission.getRace();
            String subject = "Lineup submission " + submission.getId();
            var numbers = new TreeSet<Integer>();
            for (LineupSubmissionRevision revision : bySubmission.getOrDefault(submission.getId(), List.of())) {
                numbers.add(revision.getRevisionNumber());
                try {
                    LineupPayload.fromJson(revision.getPayload());
                } catch (IllegalArgumentException invalid) {
                    findings.add(new DataAuditFinding(LINEUPS, UNDETERMINABLE, subject,
                            "Revision " + revision.getRevisionNumber() + " has an unreadable lineup.", "Keep the stored revision; record a management correction instead."));
                }
            }
            if (numbers.size() != submission.getLatestRevisionNumber() || !numbers.isEmpty() && numbers.last() != submission.getLatestRevisionNumber()) {
                findings.add(new DataAuditFinding(LINEUPS, UNDETERMINABLE, subject,
                        "Revisions do not number 1 to the latest revision " + submission.getLatestRevisionNumber() + ".",
                        "Restore the complete history from a backup; do not renumber revisions."));
            }
            boolean participant = race.getHomeTeam() != null && race.getHomeTeam().getId().equals(submission.getTeam().getId())
                    || race.getAwayTeam() != null && race.getAwayTeam().getId().equals(submission.getTeam().getId());
            if (!participant) {
                findings.add(new DataAuditFinding(LINEUPS, AMBIGUOUS, subject,
                        "Team " + submission.getTeam().getShortName() + " no longer plays race " + race.getId() + ".",
                        "Review the pairing; the submission stays as history and cannot be applied."));
            }
        }
        var byRace = windows.findAllForBackup().stream().collect(Collectors.groupingBy(event -> event.getRace().getId()));
        for (var entry : byRace.entrySet()) {
            var sequence = entry.getValue().stream().map(LineupWindowEvent::getSequenceNumber).sorted(Comparator.naturalOrder()).toList();
            for (int i = 0; i < sequence.size(); i++) {
                if (sequence.get(i) != i + 1) {
                    findings.add(new DataAuditFinding(LINEUPS, UNDETERMINABLE, "Race " + entry.getKey(),
                            "Lineup window decisions do not number 1 to " + sequence.size() + ".", "Restore the complete history from a backup."));
                    break;
                }
            }
        }
    }
}
