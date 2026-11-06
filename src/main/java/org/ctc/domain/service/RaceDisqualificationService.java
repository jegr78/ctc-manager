package org.ctc.domain.service;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceDisqualificationRevisionRepository;
import org.ctc.domain.repository.RaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RaceDisqualificationService {
    private final RaceRepository races;
    private final RaceDisqualificationRevisionRepository revisions;
    private final ScoringService scoring;
    private final PickSessionEpoch epoch;
    private final PickWriteExecutor writes;
    private final ScoreWriteLockService locks;

    public record Correction(Boolean disqualified, Integer expectedRevision, String fingerprint, String generation, String reason) {}
    public record View(Race race, RaceResult result, boolean disqualified, int revision, String fingerprint, String generation,
                       List<RaceDisqualificationRevision> history, boolean correctable) {}

    @Transactional(readOnly = true)
    public View view(UUID raceId, UUID resultId) {
        String generation = epoch.managementGeneration();
        var race = findRace(raceId); var result = findResult(race, resultId);
        var history = List.copyOf(result.getDisqualificationRevisions());
        int revision = history.isEmpty() ? 0 : history.getLast().getRevisionNumber();
        var snapshot = scoring.calculateRaceScoreSnapshot(race, race.getHomeTeam().getId());
        boolean disqualified = snapshot.drivers().stream().filter(r -> r.resultId().equals(resultId)).findFirst().orElseThrow().disqualified();
        return new View(race, result, disqualified, revision, RacePenaltyService.fingerprint(race, snapshot), generation, history,
                RaceDisqualificationHistory.played(race) && (race.getPlayoffMatchup() == null || !race.getPlayoffMatchup().isComplete()));
    }

    public int save(UUID raceId, UUID resultId, Correction correction, String actor) {
        return writes.execute(() -> saveAdmitted(raceId, resultId, correction, actor));
    }

    private int saveAdmitted(UUID raceId, UUID resultId, Correction correction, String actor) {
        if (actor == null || actor.isBlank() || actor.length() > 200) throw new BusinessRuleException("A trusted manager identity is required");
        if (correction == null || correction.disqualified() == null || correction.expectedRevision() == null || correction.expectedRevision() < 0)
            throw new BusinessRuleException("A DSQ status and current revision are required");
        if (!Objects.equals(epoch.managementGeneration(), correction.generation()))
            throw new BusinessRuleException("This management form is no longer current; reload before saving DSQ");
        if (correction.reason() == null || correction.reason().isBlank() || correction.reason().length() > 2000)
            throw new BusinessRuleException("A DSQ reason of 1 to 2000 characters is required");
        locks.lockRace(raceId);
        var race = findRace(raceId); var result = findResult(race, resultId);
        if (!RaceDisqualificationHistory.played(race)) throw new BusinessRuleException("DSQ corrections require a played race with driver results");
        PlayoffDecisionGuard.requireOpen(race);
        if (!RaceClassification.hasUniqueFinishingPositions(race.getResults()))
            throw new BusinessRuleException("DSQ corrections require unique finishing positions between 1 and 12; correct the original classification first");
        if (race.getMatchday().getPhase().getRaceScoring() == null || !race.getMatchday().getPhase().getRaceScoring().canParse())
            throw new BusinessRuleException("DSQ corrections require a valid phase race scoring");
        var contextProblems = RacePenaltyHistory.contextProblems(race);
        if (!contextProblems.isEmpty()) throw new BusinessRuleException("DSQ race context is inconsistent; review its recorded pairing");
        var history = revisions.findResultHistoryForUpdate(resultId);
        var problems = RaceDisqualificationHistory.problems(RaceDisqualificationHistory.identity(result), result.isDisqualified(),
                history.stream().map(RaceDisqualificationHistory::entry).toList());
        if (!problems.isEmpty()) throw new BusinessRuleException(String.join("; ", problems));
        int current = history.isEmpty() ? 0 : history.getLast().getRevisionNumber();
        if (current != correction.expectedRevision()) throw new BusinessRuleException("DSQ history changed; reload before saving another correction");
        var snapshot = scoring.calculateRaceScoreSnapshot(race, race.getHomeTeam().getId());
        if (!Objects.equals(RacePenaltyService.fingerprint(race, snapshot), correction.fingerprint()))
            throw new BusinessRuleException("Race results changed; reload before saving DSQ");
        if (history.size() >= RaceDisqualificationHistory.MAX_REVISIONS) throw new BusinessRuleException("DSQ history reached its revision budget");
        if (!new HashSet<>(history.stream().map(RaceDisqualificationRevision::getId).toList()).equals(
                new HashSet<>(result.getDisqualificationRevisions().stream().map(RaceDisqualificationRevision::getId).toList())))
            throw new BusinessRuleException("DSQ history changed; reload before saving");
        if (current == 0 && !correction.disqualified()) throw new BusinessRuleException("The driver has no DSQ to clear");
        var row = new RaceDisqualificationRevision(result, current + 1, result.isDisqualified(), correction.disqualified(),
                correction.reason().trim(), actor, correction.fingerprint());
        result.setDisqualified(correction.disqualified());
        // Recalculate the complete classification before any SQL flush can enforce zero DSQ race points.
        scoring.calculatePoints(race.getResults(), race.getMatchday().getPhase().getRaceScoring());
        revisions.saveAndFlush(row);
        result.getDisqualificationRevisions().add(row);
        scoring.aggregateMatchScores(race);
        return row.getRevisionNumber();
    }

    private Race findRace(UUID id) { return races.findById(id).orElseThrow(() -> new EntityNotFoundException("Race", id)); }
    private static RaceResult findResult(Race race, UUID id) {
        return race.getResults().stream().filter(r -> Objects.equals(r.getId(), id)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Race result", id));
    }
}
