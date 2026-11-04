package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Function;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.IncidentManagementActor;
import org.ctc.domain.incidents.IncidentRulingClaim;
import org.ctc.domain.incidents.IncidentSendResult;
import org.ctc.domain.model.IncidentRuling;
import org.ctc.domain.model.IncidentRulingRevision;
import org.ctc.domain.model.IncidentDeliveryState;
import org.ctc.domain.model.IncidentRulingPublication;
import org.ctc.domain.repository.IncidentReportRepository;
import org.ctc.domain.repository.IncidentRulingRepository;
import org.ctc.domain.repository.IncidentRulingRevisionRepository;
import org.ctc.domain.repository.IncidentRulingPublicationRepository;
import org.ctc.domain.repository.IncidentDeliveryRepository;
import org.ctc.domain.repository.IncidentThreadPublicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IncidentRulingService {
    private final IncidentReportRepository reports;
    private final IncidentRulingRepository rulings;
    private final IncidentRulingRevisionRepository revisions;
    private final IncidentRulingPublicationRepository publications;
    private final IncidentDeliveryRepository deliveries;
    private final IncidentThreadPublicationRepository threads;
    private final org.ctc.domain.incidents.IncidentRulingSender sender;
    private final IncidentThreadPublicationService threadProofs;
    private final IncidentWindowService windows;
    private final ImportLockService imports;
    private final PickSessionEpoch epoch;
    private final Clock clock;
    private final EntityManager em;
    private final TransactionTemplate transactions;

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP2", matchType = edu.umd.cs.findbugs.annotations.SuppressMatchType.EXACT,
            justification = "Spring injects its transaction-scoped EntityManager proxy; copying it would break locking context identity. See docs/security/sast-acceptance.md.")
    public IncidentRulingService(IncidentReportRepository reports, IncidentRulingRepository rulings,
            IncidentRulingRevisionRepository revisions, IncidentWindowService windows, ImportLockService imports,
            PickSessionEpoch epoch, Clock clock, EntityManager em, PlatformTransactionManager manager,
            IncidentRulingPublicationRepository publications, IncidentDeliveryRepository deliveries, IncidentThreadPublicationRepository threads,
            org.ctc.domain.incidents.IncidentRulingSender sender, IncidentThreadPublicationService threadProofs) {
        this.reports = reports; this.rulings = rulings; this.revisions = revisions; this.windows = windows;
        this.imports = imports; this.epoch = epoch; this.clock = clock; this.em = em;
        this.transactions = new TransactionTemplate(manager);
        this.publications = publications; this.deliveries = deliveries; this.threads = threads; this.sender = sender; this.threadProofs = threadProofs;
    }

    public IncidentRulingRevision saveDraft(UUID reportId, long expectedVersion, String text, String reason,
            IncidentManagementActor actor, String generation) {
        IncidentRulingRevision.acceptedText(text);
        return write(reportId, true, actor, generation,
                ruling -> {
                    var revision = ruling.draft(expectedVersion, text, reason, actor, clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
                    rulings.save(ruling);
                    return revisions.save(revision);
                });
    }
    public UUID publish(UUID reportId, int rulingVersion, long expectedVersion,
            IncidentManagementActor actor, String generation) {
        return write(reportId, false, actor, generation, ruling -> {
            ruling.requireVersion(expectedVersion);
            var report = ruling.getReport();
            var revision = revisions.findByRulingIdAndRevisionNumber(ruling.getId(), rulingVersion).orElseThrow(IncidentRulingService::unavailable);
            var disclosure = deliveries.findByOperationKeyForUpdate(reportId + ":DISCLOSURE").orElseThrow(IncidentRulingService::unavailable);
            em.refresh(disclosure, LockModeType.PESSIMISTIC_WRITE);
            if (!reportId.equals(disclosure.getReport().getId())) throw unavailable();
            var mapping = threads.findByDeliveryIdForUpdate(disclosure.getId()).orElseThrow(IncidentRulingService::unavailable);
            em.refresh(mapping, LockModeType.PESSIMISTIC_WRITE);
            if (!mapping.publicComplete() || mapping.getPublicThreadId() == null) throw unavailable();
            IncidentThreadProjection.capture(disclosure);
            String previousContent = null;
            if (ruling.getPublishedRevisionNumber() != null) {
                var previousRevision = revisions.findByRulingIdAndRevisionNumber(ruling.getId(), ruling.getPublishedRevisionNumber())
                        .orElseThrow(IncidentRulingService::unavailable);
                var previous = publications.findByRevisionId(previousRevision.getId()).orElseThrow(IncidentRulingService::unavailable);
                em.refresh(previous, LockModeType.PESSIMISTIC_WRITE);
                if (previous.getState() != IncidentDeliveryState.SENT || !ruling.getPublishedMessageId().equals(previous.getMessageId())
                        || !mapping.getPublicThreadId().equals(previous.getChannelId())) throw unavailable();
                previousContent = previous.getContent();
            }
            var at = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            for (var pending : publications.findByRevisionRulingIdOrderByRequestedAtAscIdAsc(ruling.getId())) {
                em.refresh(pending, LockModeType.PESSIMISTIC_WRITE);
                if (pending.getState() != IncidentDeliveryState.SENT && pending.getState() != IncidentDeliveryState.SUPERSEDED) pending.supersede(at);
            }
            ruling.requestPublication(expectedVersion, rulingVersion);
            return publications.save(IncidentRulingPublication.capture(revision, mapping,
                    IncidentRulingProjection.render(report.getId(), report.getCaseReference(), revision.getText()), previousContent, actor, at)).getId();
        });
    }
    public boolean dispatch(UUID id) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Ruling dispatch admission must precede its transaction");
        String generation = epoch.managementGeneration();
        if (!imports.tryEnterWriter()) return false;
        try {
            var claim = transactions.execute(status -> claim(id, generation));
            if (claim == null) return false;
            requireGeneration(generation); IncidentSendResult result;
            try { result = sender.send(claim); }
            catch (RuntimeException failure) { result = uncertain(); }
            var outcome = normalize(result);
            transactions.executeWithoutResult(status -> {
                requireGeneration(generation); var row = lockedPublication(id);
                if (row == null) throw unavailable();
                requireCurrent(row); var at = now();
                if (outcome.disposition() == IncidentSendResult.Disposition.SENT) {
                    row.acknowledge(claim.token(), claim.version(), generation, at, outcome.messageId());
                    row.getRevision().getRuling().confirmPublication(row.getRevision().getRevisionNumber(), row.getPreviousRevisionNumber(), outcome.messageId());
                } else row.fail(claim.token(), claim.version(), generation, at, outcome.code(),
                        outcome.disposition() == IncidentSendResult.Disposition.UNCERTAIN, at.plusSeconds(60));
            });
            return true;
        } catch (RuntimeException failure) { throw RaceSelectionConcurrency.translate(failure) == failure ? failure : unavailable(); }
        finally { imports.exitWriter(); }
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.List<UUID> dueRulings(int limit) {
        if (limit < 1 || limit > 25) throw new IllegalArgumentException("Ruling due-work batches must contain between 1 and 25 rows");
        return publications.findDueIds(now(), org.springframework.data.domain.PageRequest.of(0, limit));
    }
    public void reconcile(UUID reportId, UUID publicationId, long expectedVersion, long publicationVersion,
            String decision, String remoteId, IncidentManagementActor actor, String reason, boolean reviewed, String generation) {
        IncidentContextService.requireManager(actor);
        String remote = remoteId == null ? "" : remoteId;
        if (!reviewed || reason == null || reason.isBlank() || reason.length() > 1000
                || !("CONFIRM_SENT".equals(decision) || "RETRY_MISSING".equals(decision))
                || !remote.isEmpty() && !remote.matches("[0-9]{1,20}") || "RETRY_MISSING".equals(decision) && !remote.isEmpty()) throw unavailable();
        requireGeneration(generation);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Ruling review admission must precede its transaction");
        if (!imports.tryEnterWriter()) throw unavailable();
        try {
            var review = transactions.execute(status -> {
                requireGeneration(generation); var row = lockedPublication(publicationId);
                if (row == null || !reportId.equals(row.getRevision().getRuling().getReport().getId())) throw unavailable();
                row.getRevision().getRuling().requireVersion(expectedVersion); requireCurrent(row);
                row.requireReview(publicationVersion, now()); return snapshot(row, generation, now().plusSeconds(600));
            });
            IncidentSendResult verification;
            try { verification = normalize(sender.verify(review, remote)); }
            catch (RuntimeException failure) { throw unavailable(); }
            boolean adopt = "CONFIRM_SENT".equals(decision);
            if (adopt ? verification.disposition() != IncidentSendResult.Disposition.SENT
                    || !remote.isEmpty() && !remote.equals(verification.messageId())
                    : verification.disposition() != IncidentSendResult.Disposition.REJECTED || !"PROVEN_MISSING".equals(verification.code())) {
                throw new BusinessRuleException("The requested ruling outcome could not be verified; keep its delivery unresolved");
            }
            transactions.executeWithoutResult(status -> {
                requireGeneration(generation); var row = lockedPublication(publicationId);
                if (row == null || !reportId.equals(row.getRevision().getRuling().getReport().getId()) || !now().isBefore(review.validUntil())) throw unavailable();
                var root = row.getRevision().getRuling(); root.requireVersion(expectedVersion); requireCurrent(row);
                if (adopt) {
                    row.reconcileDelivered(publicationVersion, actor.id(), reason, now(), verification.messageId());
                    root.confirmPublication(row.getRevision().getRevisionNumber(), row.getPreviousRevisionNumber(), verification.messageId());
                } else row.reconcileMissing(publicationVersion, actor.id(), reason, now());
            });
        } catch (RuntimeException failure) { throw RaceSelectionConcurrency.translate(failure) == failure ? failure : unavailable(); }
        finally { imports.exitWriter(); }
    }
    private IncidentRulingClaim claim(UUID id, String generation) {
        requireGeneration(generation); var row = lockedPublication(id);
        if (row == null || row.getRevision().getRuling().getReport().getWindow().isDeliveryPaused()) return null;
        var at = now();
        if (row.getState() == IncidentDeliveryState.CLAIMED) { row.expireClaim(at); return null; }
        if (row.getState() != IncidentDeliveryState.PENDING && row.getState() != IncidentDeliveryState.FAILED || at.isBefore(row.getNextAttemptAt())) return null;
        var window = row.getRevision().getRuling().getReport().getWindow();
        if (window.getDeliveryResumedAt() != null && !row.isRestoreReviewed()) return null;
        requireCurrent(row); row.claim(UUID.randomUUID(), generation, at, at.plusSeconds(600));
        return snapshot(row, generation, row.getLeaseUntil());
    }
    private IncidentRulingClaim snapshot(IncidentRulingPublication row, String generation, java.time.Instant until) {
        var report = row.getRevision().getRuling().getReport(); var mapping = row.getThreadPublication();
        return new IncidentRulingClaim(row.getId(), report.getId(), report.getWindow().getId(), row.getClaimToken(), row.getVersion(), generation, until,
                threadProofs.rulingProof(mapping.getDelivery(), mapping, generation, until), row.getContent(), row.getPreviousContent(), row.getPreviousMessageId(), row.getNonce());
    }
    private IncidentRulingPublication lockedPublication(UUID id) {
        if (id == null) return null;
        var observed = publications.findById(id).orElse(null); if (observed == null) return null;
        UUID reportId = observed.getRevision().getRuling().getReport().getId();
        UUID windowId = observed.getRevision().getRuling().getReport().getWindow().getId();
        windows.lock(windowId);
        var report = reports.findByIdForUpdate(reportId).orElseThrow(IncidentRulingService::unavailable);
        em.refresh(report, LockModeType.PESSIMISTIC_WRITE);
        var root = rulings.findByReportIdForUpdate(reportId).orElseThrow(IncidentRulingService::unavailable);
        em.refresh(root, LockModeType.PESSIMISTIC_WRITE);
        var row = publications.findByIdForUpdate(id).orElse(null); if (row == null) return null;
        em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
        if (!root.getId().equals(row.getRevision().getRuling().getId()) || !windowId.equals(report.getWindow().getId())) throw unavailable();
        em.refresh(row.getThreadPublication(), LockModeType.PESSIMISTIC_WRITE);
        em.refresh(row.getThreadPublication().getDelivery(), LockModeType.PESSIMISTIC_WRITE);
        return row;
    }
    private void requireCurrent(IncidentRulingPublication row) {
        var root = row.getRevision().getRuling(); var report = root.getReport(); var mapping = row.getThreadPublication();
        var context = report.getWindow().getContext();
        if (!java.util.Objects.equals(root.getRequestedRevisionNumber(), row.getRevision().getRevisionNumber())
                || !java.util.Objects.equals(root.getPublishedRevisionNumber(), row.getPreviousRevisionNumber())
                || !java.util.Objects.equals(root.getPublishedMessageId(), row.getPreviousMessageId())
                || !report.getId().equals(mapping.getDelivery().getReport().getId()) || !mapping.publicComplete()
                || !row.getChannelId().equals(mapping.getPublicThreadId()) || !row.getGuildId().equals(context.getGuildId())
                || !context.getChannelId().equals(mapping.getDelivery().getChannelId())) throw unavailable();
        IncidentThreadProjection.capture(mapping.getDelivery());
    }
    private static IncidentSendResult normalize(IncidentSendResult result) {
        if (result == null || result.disposition() == null) return uncertain();
        if (result.disposition() == IncidentSendResult.Disposition.SENT && (result.messageId() == null || !result.messageId().matches("[0-9]{1,20}"))) return uncertain();
        if (result.disposition() != IncidentSendResult.Disposition.SENT && (result.code() == null || !result.code().matches("[A-Z0-9_]{1,64}"))) return uncertain();
        return result;
    }
    private static IncidentSendResult uncertain() { return new IncidentSendResult(IncidentSendResult.Disposition.UNCERTAIN, null, "SEND_UNCERTAIN"); }
    private java.time.Instant now() { return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS); }

    public String preview(IncidentRulingRevision revision) {
        var report = revision.getRuling().getReport();
        return IncidentRulingProjection.render(report.getId(), report.getCaseReference(), revision.getText());
    }
    private <T> T write(UUID reportId, boolean create, IncidentManagementActor actor, String generation, Function<IncidentRuling, T> action) {
        if (actor == null || actor.id() == null || actor.id().isBlank() || actor.id().length() > 128) {
            throw new BusinessRuleException("An authenticated management actor is required");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Ruling admission must precede its transaction");
        requireGeneration(generation);
        if (!imports.tryEnterWriter()) throw unavailable();
        try {
            return transactions.execute(status -> {
                requireGeneration(generation);
                var observed = reports.findById(reportId).orElseThrow(IncidentRulingService::unavailable);
                UUID windowId = observed.getWindow().getId();
                windows.lock(windowId);
                var report = reports.findByIdForUpdate(reportId).orElseThrow(IncidentRulingService::unavailable);
                em.refresh(report, LockModeType.PESSIMISTIC_WRITE);
                if (!windowId.equals(report.getWindow().getId())) throw unavailable();
                var existing = rulings.findByReportIdForUpdate(reportId);
                IncidentRuling ruling;
                if (existing.isPresent()) {
                    ruling = existing.get(); em.refresh(ruling, LockModeType.PESSIMISTIC_WRITE);
                } else {
                    if (!create) throw unavailable();
                    ruling = IncidentRuling.forReport(report);
                }
                return action.apply(ruling);
            });
        } catch (RuntimeException failure) {
            throw RaceSelectionConcurrency.translate(failure);
        } finally { imports.exitWriter(); }
    }
    private void requireGeneration(String generation) {
        if (!epoch.managementGeneration().equals(generation)) throw unavailable();
    }
    private static BusinessRuleException unavailable() { return new BusinessRuleException("Incident ruling state changed; reload the current case"); }
}
