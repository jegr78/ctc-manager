package org.ctc.domain.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.picks.PreparedPickPublication;
import org.ctc.domain.picks.PreparedPickReconciliation;
import org.ctc.domain.picks.PublicPickProjection;
import org.ctc.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Commits a prepared physical output before bounded HTTP, then records its independently confirmed outcome. */
@Service
public class PickPublicationService {
    private final ImportLockService imports;
    private final TransactionTemplate transactions;
    private final SeasonRepository seasons;
    private final RaceRepository races;
    private final PickPublicationAttemptRepository attempts;
    private final PickPublicationSnapshotRepository snapshots;
    private final PickSessionEpoch epoch;
    private final EntityManager entities;
    private final Clock clock;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring injects the shared transaction-scoped EntityManager proxy; copying it would break persistence context identity.")
    public PickPublicationService(ImportLockService imports, PlatformTransactionManager manager, SeasonRepository seasons,
            RaceRepository races, PickPublicationAttemptRepository attempts, PickPublicationSnapshotRepository snapshots,
            PickSessionEpoch epoch, EntityManager entities, Clock clock) {
        this.imports = imports; transactions = new TransactionTemplate(manager);
        this.seasons = seasons; this.races = races; this.attempts = attempts; this.snapshots = snapshots;
        this.epoch = epoch; this.entities = entities; this.clock = clock;
    }

    public <T> T publish(List<UUID> raceIds, PickPublicationOutput output, ManagementPickActor actor,
                        Function<List<Race>, PreparedPickPublication<T>> render) throws IOException {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new BusinessRuleException("Publication must start outside an existing transaction");
        }
        if (raceIds == null || raceIds.isEmpty() || raceIds.size() > 10 || raceIds.stream().anyMatch(id -> id == null)
                || Set.copyOf(raceIds).size() != raceIds.size() || output == null || render == null) {
            throw new BusinessRuleException("Publication requires one exact physical bundle of target races");
        }
        if (actor == null || actor.userId() == null || actor.userId().isBlank() || actor.userId().length() > 128
                || actor.generation() != null && !actor.generation().equals(epoch.managementGeneration())) {
            throw new BusinessRuleException("Publication requires a current management actor");
        }
        if (!imports.tryEnterWriter()) throw new BusinessRuleException("A backup restore is in progress; retry later");
        UUID batchId = UUID.randomUUID(); inFlight.add(batchId);
        try {
            Captured<T> captured = transactions.execute(status -> capture(raceIds, output, actor, render, batchId));
            if (captured == null) throw new IllegalStateException("Publication capture did not complete");
            String messageId;
            try {
                messageId = captured.bundle().delivery().send();
            } catch (IOException failure) {
                boolean known = failure instanceof DiscordApiException api && api.category() != DiscordApiException.Category.TRANSIENT;
                recordFailure(captured.ids(), known, known ? "REMOTE_REJECTED" : "UNKNOWN_REMOTE_OUTCOME", null);
                throw failure;
            } catch (RuntimeException failure) {
                recordFailure(captured.ids(), false, "UNKNOWN_REMOTE_OUTCOME", null);
                throw new BusinessRuleException("Publication outcome requires management reconciliation");
            }
            try {
                return transactions.execute(status -> {
                    for (UUID seasonId : captured.seasonIds()) seasons.findByIdForUpdate(seasonId).orElseThrow(PickPublicationService::unavailable);
                    T result = captured.bundle().persistence().apply(messageId);
                    for (UUID id : captured.ids()) {
                        var attempt = attempts.findByIdForUpdate(id).orElseThrow();
                        attempt.confirm(messageId, clock.instant());
                        if (!attempt.fields().isEmpty()) snapshots.save(new PickPublicationSnapshot(attempt));
                    }
                    attempts.flush(); snapshots.flush();
                    return result;
                });
            } catch (RuntimeException failure) {
                recordFailure(captured.ids(), false, "LOCAL_CONFIRMATION_FAILURE", messageId);
                throw new BusinessRuleException("Discord may have accepted this publication; management reconciliation is required before retrying");
            }
        } finally {
            inFlight.remove(batchId); imports.exitWriter();
        }
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<Race> lockForAutomatic(UUID matchId) {
        var seasonIds = races.findByMatchId(matchId).stream().map(race -> race.getMatchday().getSeason().getId()).distinct().sorted().toList();
        var selected = new ArrayList<Race>();
        for (UUID seasonId : seasonIds) {
            seasons.findByIdForUpdate(seasonId).orElseThrow(PickPublicationService::unavailable);
            for (var race : races.findBySeasonForSelectionUpdate(seasonId)) {
                if (race.getMatch() == null || !matchId.equals(race.getMatch().getId())) continue;
                if (race.getSettings() != null) entities.refresh(race.getSettings(), LockModeType.PESSIMISTIC_WRITE);
                selected.add(race);
            }
        }
        selected.sort(java.util.Comparator.comparing(Race::getDateTime, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparing(Race::getId));
        return List.copyOf(selected);
    }

    public boolean isInFlight(UUID batchId) { return inFlight.contains(batchId); }

    public String reconciliationFingerprint(UUID batchId) {
        return transactions.execute(status -> fingerprint(batchRows(batchId)));
    }

    public <T> T reconcile(UUID batchId, String fingerprint, String reason, ManagementPickActor actor,
            Function<List<PickPublicationAttempt>, PreparedPickReconciliation<T>> prepare) throws IOException {
        enterResolution(batchId, reason, actor);
        try {
            var prepared = transactions.execute(status -> prepare.apply(lockPendingBatch(batchId, fingerprint, actor)));
            if (prepared == null || prepared.verification() == null || prepared.persistence() == null) throw unavailable();
            String message = prepared.verification().verify();
            return transactions.execute(status -> {
                var rows = lockPendingBatch(batchId, fingerprint, actor);
                T result = prepared.persistence().apply(message);
                for (var row : rows) {
                    row.reconcile(message, actor.userId(), reason, clock.instant());
                    if (!row.fields().isEmpty()) snapshots.save(new PickPublicationSnapshot(row));
                }
                attempts.flush(); snapshots.flush(); return result;
            });
        } finally { inFlight.remove(batchId); imports.exitWriter(); }
    }

    public void abandon(UUID batchId, String fingerprint, String reason, boolean remoteHandled, ManagementPickActor actor,
            java.util.function.Consumer<List<PickPublicationAttempt>> quarantine) {
        if (!remoteHandled) throw new BusinessRuleException("Confirm that the uncertain remote output has been removed or otherwise retired before abandoning it");
        enterResolution(batchId, reason, actor);
        try {
            transactions.executeWithoutResult(status -> {
                var rows = lockPendingBatch(batchId, fingerprint, actor);
                quarantine.accept(rows);
                for (var row : rows) row.abandon(actor.userId(), reason, clock.instant());
                attempts.flush();
            });
        } finally { inFlight.remove(batchId); imports.exitWriter(); }
    }

    private void enterResolution(UUID batchId, String reason, ManagementPickActor actor) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new BusinessRuleException("Reconciliation must start outside an existing transaction");
        if (batchId == null || reason == null || reason.isBlank() || reason.length() > 1000) throw new BusinessRuleException("Reconciliation requires a target and a management reason of at most 1000 characters");
        requireCurrentActor(actor);
        if (!imports.tryEnterWriter()) throw new BusinessRuleException("A backup restore is in progress; retry later");
        if (!inFlight.add(batchId)) {
            imports.exitWriter(); throw new BusinessRuleException("This publication is still being sent or checked; wait for it to finish");
        }
    }

    private List<PickPublicationAttempt> lockPendingBatch(UUID batchId, String expectedFingerprint, ManagementPickActor actor) {
        requireCurrentActor(actor);
        var before = batchRows(batchId);
        for (var seasonId : before.stream().map(PickPublicationAttempt::getSeasonId).distinct().sorted().toList()) {
            seasons.findByIdForUpdate(seasonId).orElseThrow(PickPublicationService::unavailable);
        }
        var rows = new ArrayList<PickPublicationAttempt>();
        for (var id : before.stream().map(PickPublicationAttempt::getId).sorted().toList()) {
            var row = attempts.findByIdForUpdate(id).orElseThrow(PickPublicationService::unavailable);
            entities.refresh(row, LockModeType.PESSIMISTIC_WRITE); rows.add(row);
        }
        if (!fingerprint(rows).equals(expectedFingerprint) || rows.stream().anyMatch(row -> row.getState() != PickPublicationState.PREPARED && row.getState() != PickPublicationState.UNCERTAIN)) {
            throw new BusinessRuleException("The publication review changed; reload before resolving it");
        }
        return List.copyOf(rows);
    }

    private void requireCurrentActor(ManagementPickActor actor) {
        if (actor == null || actor.userId() == null || actor.userId().isBlank() || actor.userId().length() > 128
                || !epoch.managementGeneration().equals(actor.generation())) throw new BusinessRuleException("Reconciliation requires a current management session; reload the review");
    }

    private List<PickPublicationAttempt> batchRows(UUID batchId) {
        if (batchId == null) throw unavailable();
        var rows = attempts.findByBatchId(batchId);
        if (rows.isEmpty()) throw new BusinessRuleException("The publication record is unavailable; reload the list");
        return rows;
    }

    /**
     * Public view of a race setup. A finished leg shows its stored setup. An unfinished leg shows the content of its
     * latest confirmed publication: a lobby graphic releases everything except the car, every other output all fields.
     */
    public PublicPickProjection publicProjection(Race race) {
        if (PickTargetRules.isFinished(race)) return PickPublicationContent.from(race).project(race.getId(), PickDisclosureField.all());
        var confirmed = confirmed(race);
        long version = confirmed.stream().mapToLong(PickPublicationAttempt::getContentVersion).max().orElse(0);
        var latest = confirmed.stream().filter(attempt -> attempt.getContentVersion() == version).toList();
        if (latest.isEmpty()) return new PublicPickProjection(race.getId(), null, null, null, null);
        var first = latest.getFirst();
        for (var attempt : latest) {
            if (!first.getContentHash().equals(attempt.getContentHash())
                    || first.getOrigin() != attempt.getOrigin() || first.getSourceVersion() != attempt.getSourceVersion()
                    || !attempt.getContentHash().equals(attempt.getContent().fingerprint())) {
                throw new BusinessRuleException("Published pick content has inconsistent confirmation evidence");
            }
        }
        var fields = java.util.EnumSet.noneOf(PickDisclosureField.class);
        confirmed.stream().filter(attempt -> attempt.getContentHash().equals(first.getContentHash()))
                .forEach(attempt -> fields.addAll(released(attempt.getOutput())));
        return first.getContent().project(race.getId(), fields);
    }

    /** Whether automatic edits may attach the complete settings graphic without disclosing unpublished choices. */
    public boolean settingsReleased(Race race) {
        if (PickTargetRules.isFinished(race)) return true;
        var current = PickPublicationContent.from(race).fingerprint();
        return confirmed(race).stream().anyMatch(attempt -> attempt.getContentHash().equals(current)
                && released(attempt.getOutput()).containsAll(PickDisclosureField.all()));
    }

    private List<PickPublicationAttempt> confirmed(Race race) {
        return snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(race.getId()).stream()
                .map(PickPublicationSnapshot::getAttempt)
                .filter(attempt -> attempt.getState() == PickPublicationState.SUCCEEDED && attempt.matchesTarget(race)).toList();
    }

    private static Set<PickDisclosureField> released(PickPublicationOutput output) {
        return output == PickPublicationOutput.LOBBY_SETTINGS ? PickDisclosureField.lobby() : PickDisclosureField.all();
    }

    private static String fingerprint(List<PickPublicationAttempt> rows) {
        var value = new StringBuilder();
        for (var row : rows.stream().sorted(java.util.Comparator.comparing(PickPublicationAttempt::getId)).toList()) {
            value.append(row.getId()).append(':').append(row.getBatchId()).append(':').append(row.getState()).append(':')
                    .append(row.getRequestHash()).append(':').append(row.getContentHash()).append(':').append(row.getContentVersion()).append(':')
                    .append(row.getMessageId()).append(':').append(row.getRuntimeGeneration()).append(';');
        }
        return PickPublicationAttempt.manifestHash(value.toString());
    }

    private <T> Captured<T> capture(List<UUID> raceIds, PickPublicationOutput output, ManagementPickActor actor,
                                  Function<List<Race>, PreparedPickPublication<T>> render, UUID batchId) {
        var seasonIds = raceIds.stream().map(id -> races.findById(id).orElseThrow(PickPublicationService::unavailable)
                .getMatchday().getSeason().getId()).distinct().sorted().toList();
        var locked = new HashMap<UUID, Race>();
        for (UUID seasonId : seasonIds) {
            seasons.findByIdForUpdate(seasonId).orElseThrow(PickPublicationService::unavailable);
            for (var race : races.findBySeasonForSelectionUpdate(seasonId)) locked.put(race.getId(), race);
        }
        var selected = new ArrayList<Race>();
        var contents = new HashMap<UUID, PickPublicationContent>();
        for (UUID id : raceIds) {
            var race = locked.get(id);
            if (race == null || !seasonIds.contains(race.getMatchday().getSeason().getId())) throw unavailable();
            entities.refresh(race.getMatchday(), LockModeType.PESSIMISTIC_WRITE);
            entities.refresh(race.getMatchday().getPhase(), LockModeType.PESSIMISTIC_WRITE);
            if (race.getSettings() != null) entities.refresh(race.getSettings(), LockModeType.PESSIMISTIC_WRITE);
            if (!seasonIds.contains(race.getMatchday().getSeason().getId())) throw unavailable();
            if (attempts.findByActiveRaceId(id).isPresent()) {
                throw new BusinessRuleException("A prior publication is pending or uncertain; reconcile it before another publication");
            }
            var values = RaceSettingsValues.from(race.getSettings());
            LobbyRulesPolicy.requireCurrent(race.getMatchday().getSeason(), LobbyRulesProfile.from(race), values.startType(), values.slipstream());
            RaceTyrePolicy.require(race.getMatchday().getSeason(), race.getCarPoolEntry(), race.getCar(), values);
            contents.put(id, PickPublicationContent.from(race)); selected.add(race);
        }
        var bundle = render.apply(List.copyOf(selected));
        if (bundle == null || !bundle.fields().keySet().equals(Set.copyOf(raceIds))) {
            throw new BusinessRuleException("Publication requires exact field evidence for every target in its physical bundle");
        }
        var ids = new ArrayList<UUID>();
        for (var race : selected) {
            var content = contents.get(race.getId());
            if (!content.fingerprint().equals(PickPublicationContent.from(race).fingerprint())) {
                throw new BusinessRuleException("Publication render changed its captured settings; reload before retrying");
            }
            var previous = attempts.findFirstByRaceIdOrderByContentVersionDescCapturedAtDesc(race.getId()).orElse(null);
            boolean same = previous != null && previous.matchesTarget(race) && previous.getContentHash().equals(content.fingerprint())
                    && previous.getOrigin() == PickPublicationOrigin.LEGACY && previous.getSourceVersion() == 0;
            long version = previous == null ? 1 : same ? previous.getContentVersion() : Math.addExact(previous.getContentVersion(), 1);
            var attempt = new PickPublicationAttempt(new PickPublicationAttempt.Capture(batchId, race, version, 0, PickPublicationOrigin.LEGACY,
                    output, content, bundle.fields().get(race.getId()), actor.userId(), bundle.channelId(), bundle.webhookId(), bundle.targetMessageId(),
                    bundle.templateHash(), bundle.requestManifest(), epoch.managementGeneration(), clock.instant()));
            ids.add(attempts.saveAndFlush(attempt).getId());
        }
        return new Captured<>(bundle, List.copyOf(ids), List.copyOf(seasonIds));
    }

    private void recordFailure(List<UUID> ids, boolean known, String code, String possibleMessageId) {
        try {
            transactions.executeWithoutResult(status -> {
                for (UUID id : ids) {
                    var attempt = attempts.findByIdForUpdate(id).orElseThrow();
                    if (attempt.getState() != PickPublicationState.PREPARED) continue;
                    if (known) attempt.fail(code, clock.instant());
                    else attempt.uncertain(code, possibleMessageId, clock.instant());
                }
                attempts.flush();
            });
        } catch (RuntimeException unavailableDatabase) {
            // Committed PREPARED attempts retain admission and require reconciliation after recovery.
        }
    }

    private static BusinessRuleException unavailable() { return new BusinessRuleException("Publication target changed; reload its current state"); }
    private record Captured<T>(PreparedPickPublication<T> bundle, List<UUID> ids, List<UUID> seasonIds) { }
}
