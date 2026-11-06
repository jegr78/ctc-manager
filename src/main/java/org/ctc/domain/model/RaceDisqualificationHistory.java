package org.ctc.domain.model;

import java.time.LocalDateTime;
import java.util.*;

public final class RaceDisqualificationHistory {
    public static final int MAX_REVISIONS = 100_000;
    private RaceDisqualificationHistory() {}

    public record Identity(UUID result, UUID driver, RacePenaltyHistory.Identity race) {}
    public record Entry(UUID id, Identity identity, int revision, boolean previous, boolean disqualified, int recordedPosition,
                        String reason, String actor, String fingerprint, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public static Identity identity(RaceResult result) {
        return new Identity(result.getId(), result.getDriver().getId(), RacePenaltyHistory.identity(result.getRace()));
    }

    public static Entry entry(RaceDisqualificationRevision row) {
        var race = new RacePenaltyHistory.Identity(row.getRace().getId(), row.getSeason().getId(), row.getPhase().getId(),
                row.getMatchday().getId(), row.getMatch() == null ? null : row.getMatch().getId(),
                row.getPlayoffMatchup() == null ? null : row.getPlayoffMatchup().getId(), row.getHomeTeam().getId(), row.getAwayTeam().getId());
        return new Entry(row.getId(), new Identity(row.getResult().getId(), row.getDriver().getId(), race), row.getRevisionNumber(),
                row.isPreviousDisqualified(), row.isDisqualified(), row.getRecordedPosition(), row.getReason(), row.getActorId(),
                row.getResultFingerprint(), row.getCreatedAt(), row.getUpdatedAt());
    }

    public static List<String> problems(Identity expected, boolean disqualified, List<Entry> entries) {
        var problems = new ArrayList<String>();
        if (entries.isEmpty()) return disqualified ? List.of("DSQ state has no permanent history") : List.of();
        if (entries.size() > MAX_REVISIONS) return List.of("DSQ history exceeds its revision budget");
        var race = expected.race();
        if (expected.result() == null || expected.driver() == null || race == null || race.race() == null || race.season() == null
                || race.phase() == null || race.matchday() == null || race.home() == null || race.away() == null
                || Objects.equals(race.home(), race.away()) || race.match() == null && race.playoff() == null) {
            problems.add("DSQ history requires a complete result and race identity");
        }
        var ids = new HashSet<UUID>();
        var sorted = entries.stream().sorted(Comparator.comparingInt(Entry::revision)).toList();
        boolean previous = false;
        for (int i = 0; i < sorted.size(); i++) {
            var row = sorted.get(i);
            if (row.revision() != i + 1) problems.add("DSQ revision sequence must start at one without gaps or duplicates");
            if (row.id() == null || !ids.add(row.id())) problems.add("DSQ revision IDs must be present and unique");
            if (!Objects.equals(expected, row.identity())) problems.add("DSQ history identity differs from the current result");
            if (row.previous() != previous || i == 0 && !row.disqualified()) problems.add("DSQ history state chain is inconsistent");
            previous = row.disqualified();
            if (row.recordedPosition() < 1 || row.recordedPosition() > 12) problems.add("DSQ recorded position is invalid");
            if (row.reason() == null || row.reason().isBlank() || row.reason().length() > 2000) problems.add("DSQ reason is missing or too long");
            if (row.actor() == null || row.actor().isBlank() || row.actor().length() > 200) problems.add("DSQ actor is missing or too long");
            if (row.fingerprint() == null || !row.fingerprint().matches("[0-9a-f]{64}")) problems.add("DSQ result fingerprint is invalid");
            if (!validTime(row.createdAt()) || !validTime(row.updatedAt())) problems.add("DSQ audit timestamps are invalid");
        }
        if (previous != disqualified) problems.add("DSQ state differs from its latest revision");
        return problems.stream().distinct().toList();
    }

    public static List<String> problems(RaceResult result) {
        var problems = new ArrayList<>(problems(identity(result), result.isDisqualified(),
                result.getDisqualificationRevisions().stream().map(RaceDisqualificationHistory::entry).toList()));
        if (!result.getDisqualificationRevisions().isEmpty()) {
            problems.addAll(RacePenaltyHistory.contextProblems(result.getRace()).stream().map(p -> p.replace("Penalty", "DSQ")).toList());
            if (!played(result.getRace())) problems.add("DSQ history belongs to a bye or walkover");
        }
        if (result.isDisqualified() && result.getPointsRace() != 0) problems.add("DSQ result has race-position points");
        return problems.stream().distinct().toList();
    }

    public static boolean played(Race race) {
        return !race.getResults().isEmpty() && !race.isBye()
                && (race.getMatch() == null || race.getMatch().getWalkoverTeam() == null)
                && (race.getPlayoffMatchup() == null || !race.getPlayoffMatchup().isBye() && race.getPlayoffMatchup().getWalkoverTeam() == null);
    }

    private static boolean validTime(LocalDateTime value) {
        return value != null && value.getYear() >= 1000 && value.getYear() <= 9999 && value.getNano() % 1000 == 0;
    }
}
