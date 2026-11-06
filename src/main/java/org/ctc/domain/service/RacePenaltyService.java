package org.ctc.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RacePenaltyRevisionRepository;
import org.ctc.domain.repository.RaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RacePenaltyService {
    private final RaceRepository races;
    private final ScoringService scoring;
    private final PickSessionEpoch epoch;
    private final PickWriteExecutor writes;
    private final ScoreWriteLockService locks;
    private final RacePenaltyRevisionRepository revisions;

    public record Correction(UUID homeTeamId, UUID awayTeamId, Integer homePenalty, Integer awayPenalty,
                             Integer expectedRevision, String fingerprint, String generation, String reason) {}
    public record View(Race race, RacePenaltyTotals totals, int revision, String fingerprint,
                       String generation, List<RacePenaltyRevision> history) {
        public List<TeamScoreView> teamScores() {
            return List.of(new TeamScoreView(race.getHomeTeam().getShortName(), totals.home()),
                    new TeamScoreView(race.getAwayTeam().getShortName(), totals.away()));
        }
    }
    public record TeamScoreView(String team, RacePenaltyTotals.TeamScore score) {}

    @Transactional(readOnly = true)
    public View view(UUID raceId) {
        String generation = epoch.managementGeneration();
        var race = races.findById(raceId).orElseThrow(() -> new EntityNotFoundException("Race", raceId));
        requireTeams(race);
        var snapshot = scoring.calculateRaceScoreSnapshot(race, race.getHomeTeam().getId());
        var history = List.copyOf(race.getPenaltyRevisions());
        int revision = history.isEmpty() ? 0 : history.getLast().getRevisionNumber();
        return new View(race, snapshot.totals(), revision, fingerprint(race, snapshot), generation, history);
    }

    public int save(UUID raceId, Correction correction, String actorId) {
        return writes.execute(() -> saveAdmitted(raceId, correction, actorId));
    }

    private int saveAdmitted(UUID raceId, Correction correction, String actorId) {
        if (actorId == null || actorId.isBlank() || actorId.length() > 200) {
            throw new BusinessRuleException("A trusted manager identity is required");
        }
        if (correction == null || correction.expectedRevision() == null || correction.expectedRevision() < 0) {
            throw new BusinessRuleException("A current penalty revision is required");
        }
        if (!Objects.equals(epoch.managementGeneration(), correction.generation())) {
            throw new BusinessRuleException("This management form is no longer current; reload before saving penalties");
        }
        if (correction.homePenalty() == null || correction.awayPenalty() == null
                || correction.homePenalty() < 0 || correction.awayPenalty() < 0) {
            throw new BusinessRuleException("Team penalty totals must be nonnegative integers");
        }
        if (correction.reason() == null || correction.reason().isBlank() || correction.reason().length() > 2000) {
            throw new BusinessRuleException("A correction reason of 1 to 2000 characters is required");
        }
        locks.lockRace(raceId);
        var race = races.findById(raceId).orElseThrow(() -> new EntityNotFoundException("Race", raceId));
        requireTeams(race);
        if (race.getResults().isEmpty() || race.isBye()
                || race.getMatch() != null && race.getMatch().getWalkoverTeam() != null
                || race.getPlayoffMatchup() != null && (race.getPlayoffMatchup().isBye() || race.getPlayoffMatchup().getWalkoverTeam() != null)) {
            throw new BusinessRuleException("Penalty corrections require a played race with driver results");
        }
        PlayoffDecisionGuard.requireOpen(race);
        var contextProblems = RacePenaltyHistory.contextProblems(race);
        if (!contextProblems.isEmpty()) throw new BusinessRuleException(String.join("; ", contextProblems));
        if (!Objects.equals(race.getHomeTeam().getId(), correction.homeTeamId())
                || !Objects.equals(race.getAwayTeam().getId(), correction.awayTeamId())) {
            throw new BusinessRuleException("Race participants changed; reload before saving penalties");
        }
        var history = revisions.findHistoryForUpdate(raceId);
        var invalid = RacePenaltyHistory.problems(RacePenaltyHistory.identity(race), history.stream().map(RacePenaltyHistory::entry).toList());
        if (!invalid.isEmpty()) throw new BusinessRuleException(String.join("; ", invalid));
        int current = history.isEmpty() ? 0 : history.getLast().getRevisionNumber();
        if (current != correction.expectedRevision()) {
            throw new BusinessRuleException("Penalty history changed; reload before saving another correction");
        }
        var snapshot = scoring.calculateRaceScoreSnapshot(race, race.getHomeTeam().getId());
        if (!Objects.equals(fingerprint(race, snapshot), correction.fingerprint())) {
            throw new BusinessRuleException("Race results changed; reload before saving penalties");
        }
        if (history.size() >= RacePenaltyHistory.MAX_REVISIONS) {
            throw new BusinessRuleException("Penalty history reached its revision budget");
        }
        var loadedIds = new HashSet<>(race.getPenaltyRevisions().stream().map(RacePenaltyRevision::getId).toList());
        var currentIds = new HashSet<>(history.stream().map(RacePenaltyRevision::getId).toList());
        if (!loadedIds.equals(currentIds)) throw new BusinessRuleException("Penalty history changed; reload before saving");
        var totals = snapshot.totals();
        var row = revisions.saveAndFlush(new RacePenaltyRevision(race, current + 1,
                correction.homePenalty(), correction.awayPenalty(), totals.home().gross(), totals.away().gross(),
                correction.reason().trim(), actorId, correction.fingerprint()));
        race.getPenaltyRevisions().add(row);
        scoring.recomputeMatchScoresFromAllLegs(race);
        return row.getRevisionNumber();
    }

    static String fingerprint(Race race, ScoringService.RaceScoreSnapshot snapshot) {
        var content = new StringBuilder(RacePenaltyHistory.identity(race).toString());
        snapshot.drivers().stream().sorted(Comparator.comparing(result -> result.driverId().toString()))
                .forEach(result -> content.append('|').append(result.resultId()).append(':').append(result.driverId())
                        .append(':').append(result.position()).append(':').append(result.qualifyingPosition())
                        .append(':').append(result.fastestLap()).append(':').append(result.racePoints())
                        .append(':').append(result.qualifyingPoints()).append(':').append(result.fastestLapPoints())
                        .append(':').append(result.totalPoints()).append(':').append(result.firstTeam())
                        .append(result.disqualified() ? ":DSQ" : ""));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static void requireTeams(Race race) {
        if (race.getHomeTeam() == null || race.getAwayTeam() == null
                || Objects.equals(race.getHomeTeam().getId(), race.getAwayTeam().getId())) {
            throw new BusinessRuleException("Penalty corrections require two distinct race participants");
        }
    }
}
