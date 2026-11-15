package org.ctc.domain.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.lineups.LineupReceipt;
import org.ctc.domain.model.LineupActorKind;
import org.ctc.domain.model.LineupPayload;
import org.ctc.domain.model.LineupSubmission;
import org.ctc.domain.model.LineupSubmissionRevision;
import org.ctc.domain.model.LineupTargetRules;
import org.ctc.domain.model.LineupWindowEvent;
import org.ctc.domain.model.LineupWindowKind;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Team;
import org.ctc.domain.picks.AuthorizedPickActor;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.LineupSubmissionRepository;
import org.ctc.domain.repository.LineupSubmissionRevisionRepository;
import org.ctc.domain.repository.LineupWindowEventRepository;
import org.ctc.domain.repository.RaceRepository;
import org.springframework.stereotype.Service;

/**
 * Stores lineup revisions from private team channels until the leg's deadline, and management corrections and window
 * decisions at any time. Every write admits a restore writer before its own READ COMMITTED transaction and locks the
 * owning season, so the overlap and window checks see commits made while waiting for that lock.
 */
@Service
@RequiredArgsConstructor
public class LineupSubmissionService {
    public static final int MAX_REASON = 500;
    private final PickWriteExecutor writes;
    private final ScoreWriteLockService locks;
    private final EntityManager entityManager;
    private final RaceRepository races;
    private final DriverRepository drivers;
    private final LineupSubmissionRepository submissions;
    private final LineupSubmissionRevisionRepository revisions;
    private final LineupWindowEventRepository windows;
    private final LineupTargetService targets;
    private final LineupOverlapService overlaps;
    private final PickAccessService access;
    private final PickSessionEpoch epoch;
    private final Clock clock;

    /**
     * @param shortfallConfirmed the submitter explicitly accepted fewer drivers than the season's lineup size
     */
    public LineupReceipt submit(UUID raceId, UUID teamId, long expectedVersion, UUID requestId, AuthorizedPickActor actor,
                                LineupPayload payload, boolean shortfallConfirmed) {
        if (actor == null || actor.channel() == null || !raceId.equals(actor.raceId()) || !teamId.equals(actor.teamId())) {
            throw new BusinessRuleException("Private channel access is unavailable");
        }
        return writes.executeReadCommitted(() -> {
            var replay = replay(requestId, raceId, teamId, actor.userId());
            if (replay != null) return replay;
            var race = lock(raceId);
            if (!actor.seasonId().equals(race.getMatchday().getSeason().getId())) throw unavailable();
            access.recheck(actor);
            var team = PickAccessService.participant(race, teamId);
            if (!LineupTargetRules.serves(actor.channel().bindingTeamId(), team)) throw unavailable();
            var decision = targets.assess(race, team);
            if (!decision.editable()) throw new BusinessRuleException(decision.reason());
            var lineup = canonical(race, team, payload, false);
            if (!lineup.complete() && !shortfallConfirmed) {
                throw new BusinessRuleException("Confirm explicitly that this lineup has fewer than " + lineup.requiredSize() + " drivers");
            }
            return store(race, team, expectedVersion, requestId, lineup, LineupActorKind.TEAM, actor.userId(), null);
        });
    }

    /** A management correction, allowed after the deadline and while the window is closed, recorded with its reason. */
    public LineupReceipt correct(UUID raceId, UUID teamId, long expectedVersion, UUID requestId, ManagementPickActor actor,
                                 LineupPayload payload, String reason) {
        String actorId = managementActor(actor);
        String why = reason(reason);
        return writes.executeReadCommitted(() -> {
            var replay = replay(requestId, raceId, teamId, actorId);
            if (replay != null) return replay;
            requireGeneration(actor);
            var race = lock(raceId);
            var team = PickAccessService.participant(race, teamId);
            if (!LineupTargetRules.isOpenTarget(race, team) && submissions.findByRaceIdAndTeamId(raceId, teamId).isEmpty()) {
                throw new BusinessRuleException("This leg no longer accepts lineups");
            }
            return store(race, team, expectedVersion, requestId, canonical(race, team, payload, true), LineupActorKind.MANAGEMENT, actorId, why);
        });
    }

    /** Opens or closes intake for one leg regardless of its settings publication; the deadline still applies to teams. */
    public void decideWindow(UUID raceId, LineupWindowKind kind, int expectedSequence, ManagementPickActor actor, String reason) {
        String actorId = managementActor(actor);
        String why = reason(reason);
        if (kind == null) throw new BusinessRuleException("Choose whether to open or close lineups");
        writes.executeReadCommitted(() -> {
            requireGeneration(actor);
            var race = lock(raceId);
            int current = windows.findFirstByRaceIdOrderBySequenceNumberDesc(raceId).map(LineupWindowEvent::getSequenceNumber).orElse(0);
            if (current != expectedSequence) throw new BusinessRuleException("The lineup window changed; reload before deciding");
            windows.save(new LineupWindowEvent(race, current + 1, kind, why, actorId, clock.instant()));
            return null;
        });
    }

    private LineupReceipt store(Race race, Team team, long expectedVersion, UUID requestId, LineupPayload lineup,
                                LineupActorKind kind, String actorId, String reason) {
        var fielded = new HashMap<UUID, Team>();
        lineup.driverIds().forEach(id -> fielded.put(id, team));
        var conflicts = overlaps.conflicts(race, fielded, row -> LineupTargetRules.fieldingSide(row, race, team));
        if (!conflicts.isEmpty()) {
            var opponent = team.getId().equals(race.getHomeTeam().getId()) ? race.getAwayTeam() : race.getHomeTeam();
            // A sister team playing this leg must not learn its opponent's unpublished lineup from the rejection.
            if (kind == LineupActorKind.TEAM && conflicts.stream().anyMatch(conflict -> conflict.otherTeam().getId().equals(opponent.getId()))) {
                throw new BusinessRuleException("A selected driver already fields for another team of your organisation on this matchday");
            }
            throw new BusinessRuleException(LineupOverlapService.message(conflicts));
        }
        var submission = submissions.findByRaceIdAndTeamId(race.getId(), team.getId()).orElse(null);
        if (submission == null) {
            submission = new LineupSubmission(race, team);
        } else {
            entityManager.refresh(submission, LockModeType.PESSIMISTIC_WRITE);
        }
        submission.requireVersion(expectedVersion);
        int number = submission.submit();
        submissions.save(submission);
        var revision = revisions.save(new LineupSubmissionRevision(submission, number, lineup, clock.instant(), kind, actorId, reason, requestId));
        return receipt(revision, overlaps.foreignWarnings(race, team, lineup.driverIds()));
    }

    /** Rebuilds known entries from current drivers and turns a proposal matching a known PSN ID into that driver. */
    LineupPayload canonical(Race race, Team team, LineupPayload payload, boolean management) {
        if (payload == null) throw new BusinessRuleException("A lineup is required");
        int size = race.getMatchday().getSeason().getLineupSize();
        if (payload.entries().size() > size) throw new BusinessRuleException("A lineup has at most " + size + " drivers");
        var roster = new HashMap<UUID, org.ctc.domain.lineups.LineupChoice>();
        var byPsn = new HashMap<String, UUID>();
        for (var choice : targets.choices(race, team)) {
            roster.put(choice.driverId(), choice);
            byPsn.put(choice.psnId().toLowerCase(Locale.ROOT), choice.driverId());
        }
        var entries = new ArrayList<LineupPayload.Entry>();
        for (var entry : payload.entries()) {
            UUID id = entry.driverId() != null ? entry.driverId() : byPsn.get(entry.psnId().toLowerCase(Locale.ROOT));
            if (id == null) { entries.add(entry); continue; }
            if (!management && !roster.containsKey(id)) throw new BusinessRuleException("A selected driver is not registered for this organisation");
            var driver = drivers.findById(id).orElseThrow(() -> new BusinessRuleException("A selected driver no longer exists; reload the lineup"));
            entries.add(LineupPayload.Entry.known(driver));
        }
        try {
            return new LineupPayload(size, entries);
        } catch (IllegalArgumentException invalid) {
            throw new BusinessRuleException(invalid.getMessage());
        }
    }

    private LineupReceipt replay(UUID requestId, UUID raceId, UUID teamId, String actorId) {
        if (requestId == null) throw new BusinessRuleException("A request identity is required");
        var existing = revisions.findByRequestId(requestId).orElse(null);
        if (existing == null) return null;
        var submission = existing.getSubmission();
        if (!raceId.equals(submission.getRace().getId()) || !teamId.equals(submission.getTeam().getId()) || !actorId.equals(existing.getActorId())) {
            throw new BusinessRuleException("This request belongs to another submission");
        }
        return receipt(existing, List.of());
    }

    private Race lock(UUID raceId) {
        locks.lockRace(raceId);
        var race = races.findById(raceId).orElseThrow(LineupSubmissionService::unavailable);
        entityManager.refresh(race.getMatchday(), LockModeType.PESSIMISTIC_WRITE);
        return race;
    }

    private void requireGeneration(ManagementPickActor actor) {
        if (actor.generation() != null && !actor.generation().equals(epoch.managementGeneration())) {
            throw new BusinessRuleException("This management form was reset by a restore or application restart; reload its current version");
        }
    }

    private static LineupReceipt receipt(LineupSubmissionRevision revision, List<String> warnings) {
        var submission = revision.getSubmission();
        return new LineupReceipt(submission.getRace().getId(), submission.getTeam().getId(), revision.getRequestId(),
                revision.getRevisionNumber(), revision.getAggregateVersion(), revision.getSubmittedAt(), revision.getActorId(), warnings);
    }

    static String managementActor(ManagementPickActor actor) {
        if (actor == null || actor.userId() == null || actor.userId().isBlank() || actor.userId().length() > 128) {
            throw new BusinessRuleException("An authenticated management actor is required");
        }
        return actor.userId();
    }

    static String reason(String reason) {
        String value = reason == null ? "" : reason.strip();
        if (value.isEmpty() || value.length() > MAX_REASON) throw new BusinessRuleException("Enter a reason of at most " + MAX_REASON + " characters");
        return value;
    }

    private static BusinessRuleException unavailable() { return new BusinessRuleException("Lineup target is unavailable"); }
}
