package org.ctc.admin.service;

import java.security.Principal;
import java.time.Clock;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.IncidentRulingDraftForm;
import org.ctc.admin.dto.IncidentRulingPublishForm;
import org.ctc.admin.dto.IncidentRulingReviewForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.IncidentReportRevision;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.IncidentRulingService;
import org.ctc.domain.service.IncidentWindowService;
import org.ctc.domain.service.PickSessionEpoch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentCaseAdminService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm XXX 'Europe/Berlin'")
            .withZone(IncidentWindowService.DEADLINE_ZONE);
    private final IncidentReportRepository reports;
    private final IncidentReportRevisionRepository reportRevisions;
    private final IncidentReportEventRepository events;
    private final IncidentRulingRepository rulingRows;
    private final IncidentRulingRevisionRepository rulingRevisions;
    private final IncidentDeliveryRepository deliveries;
    private final IncidentThreadPublicationRepository threads;
    private final IncidentRulingService rulings;
    private final IncidentAdminService management;
    private final PickSessionEpoch epoch;
    private final IncidentRulingPublicationRepository publications;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page view(UUID reportId) {
        var report = reports.findById(reportId).orElseThrow(() -> new BusinessRuleException("Incident case is unavailable"));
        var window = report.getWindow(); var context = window.getContext();
        var overview = management.view(context.getId());
        var delivery = deliveries.findByOperationKey(reportId + ":DISCLOSURE").orElse(null);
        var mapping = delivery == null ? null : threads.findByDeliveryId(delivery.getId()).orElse(null);
        var root = rulingRows.findByReportId(reportId).orElse(null);
        var history = root == null ? List.<org.ctc.domain.model.IncidentRulingRevision>of()
                : rulingRevisions.findByRulingIdOrderByRevisionNumberAsc(root.getId());
        if (root != null && (history.isEmpty() || history.size() != root.getLatestRevisionNumber()
                || history.getLast().getRevisionNumber() != root.getLatestRevisionNumber())) throw inconsistent();
        var latest = history.isEmpty() ? null : history.getLast();
        Map<Integer, org.ctc.domain.model.IncidentRulingPublication> work = root == null ? Map.of()
                : publications.findByRevisionRulingIdOrderByRequestedAtAscIdAsc(root.getId()).stream()
                .collect(Collectors.toMap(row -> row.getRevision().getRevisionNumber(), Function.identity()));
        var current = root == null || root.getRequestedRevisionNumber() == null ? null : work.get(root.getRequestedRevisionNumber());
        if (root != null && root.getRequestedRevisionNumber() != null && current == null) throw inconsistent();
        var publication = current == null ? null : new PublicationView(current.getId(), current.getVersion(), current.getRevision().getRevisionNumber(),
                publicationState(current.getState()), current.getRequestedBy(), TIME.format(current.getRequestedAt()), current.getReconciliationReason(),
                current.getState() != org.ctc.domain.model.IncidentDeliveryState.SENT && current.getState() != org.ctc.domain.model.IncidentDeliveryState.SUPERSEDED,
                current.getState() == org.ctc.domain.model.IncidentDeliveryState.PENDING || current.getState() == org.ctc.domain.model.IncidentDeliveryState.FAILED
                        || current.getState() == org.ctc.domain.model.IncidentDeliveryState.UNCERTAIN
                        || current.getState() == org.ctc.domain.model.IncidentDeliveryState.CLAIMED && (window.isDeliveryPaused()
                                || current.getLeaseUntil() != null && !clock.instant().isBefore(current.getLeaseUntil())));
        boolean replaceable = current == null || current.getState() == org.ctc.domain.model.IncidentDeliveryState.SENT
                || current.getState() == org.ctc.domain.model.IncidentDeliveryState.SUPERSEDED
                || (current.getState() == org.ctc.domain.model.IncidentDeliveryState.PENDING || current.getState() == org.ctc.domain.model.IncidentDeliveryState.FAILED)
                        && (window.getDeliveryResumedAt() == null || current.isRestoreReviewed());
        boolean publishable = root != null && !Objects.equals(root.getRequestedRevisionNumber(), root.getLatestRevisionNumber())
                && !window.isDeliveryPaused() && mapping != null && mapping.publicComplete()
                && report.getState() == org.ctc.domain.model.IncidentReportState.ACTIVE && replaceable;
        var decisions = events.findByWindowIdOrderByAcceptedAtAscIdAsc(window.getId()).stream()
                .filter(event -> event.getReport() != null && reportId.equals(event.getReport().getId()))
                .map(event -> new DecisionView(event.getKind().name(), TIME.format(event.getAcceptedAt()), event.getActorId())).toList();
        String publicThread = mapping == null ? null : mapping.getPublicThreadId();
        return new Page(reportId, context.getSeason().getId(), context.getId(), report.getRace().getId(),
                report.getCaseReference(), report.getState().name().equals("ACTIVE") ? "Active" : "Withdrawn", overview.pairing(),
                report.getOriginalMatchday().getLabel(), report.getParty().getLabel(), report.getAuthorId(), overview.confirmedDeadline(),
                report.getFrozenRevisionNumber() == null ? "Not frozen" : report.getFrozenRevisionNumber().toString(),
                link(context.getGuildId(), mapping == null ? null : mapping.getPrivateThreadId(), null),
                link(context.getGuildId(), publicThread, null),
                delivery == null ? "Not disclosed" : delivery.getState().name().replace('_', ' '),
                root == null || root.getPublishedRevisionNumber() == null ? "No ruling published" : "Published version " + root.getPublishedRevisionNumber(),
                root == null || root.getPublishedMessageId() == null ? null : link(context.getGuildId(), publicThread, root.getPublishedMessageId()),
                root == null ? 0 : root.getVersion(), root == null ? 0 : root.getLatestRevisionNumber(),
                latest == null ? "" : latest.getText(), latest == null ? "" : latest.getReason(),
                latest == null ? "" : rulings.preview(latest), epoch.managementGeneration(),
                reportRevisions.findByReportIdOrderByRevisionNumberAsc(reportId).stream().map(IncidentCaseAdminService::reportRevision).toList(),
                decisions, history.stream().map(revision -> new RulingRevisionView(revision.getRevisionNumber(), revision.getText(), revision.getReason(),
                        revision.getActorId(), TIME.format(revision.getAcceptedAt()), Objects.equals(root.getPublishedRevisionNumber(), revision.getRevisionNumber()),
                        work.containsKey(revision.getRevisionNumber()) ? publicationState(work.get(revision.getRevisionNumber()).getState()) : "Draft")).toList(),
                publication, publishable, window.isDeliveryPaused());
    }
    public void saveDraft(UUID reportId, IncidentRulingDraftForm form, Principal principal) {
        rulings.saveDraft(reportId, form.getExpectedVersion(), form.getText(), form.getReason(), management.actor(principal), form.getGeneration());
    }
    public void publish(UUID reportId, IncidentRulingPublishForm form, Principal principal) {
        rulings.publish(reportId, form.getRulingVersion(), form.getExpectedVersion(), management.actor(principal), form.getGeneration());
    }
    public void reconcile(UUID reportId, IncidentRulingReviewForm form, Principal principal) {
        var actor = management.actor(principal);
        rulings.reconcile(reportId, form.getPublicationId(), form.getExpectedVersion(), form.getPublicationVersion(),
                form.getDecision(), form.getMessageId(), actor, form.getReason(), form.isReviewed(), form.getGeneration());
    }
    private static ReportRevisionView reportRevision(IncidentReportRevision revision) {
        var payload = revision.getPayload();
        return new ReportRevisionView(revision.getRevisionNumber(), revision.getActorId(), TIME.format(revision.getAcceptedAt()),
                payload.getInvolvedDrivers(), payload.getReplayTimestamp(), payload.getDescription(), payload.getEvidenceLinks());
    }
    private static BusinessRuleException inconsistent() { return new BusinessRuleException("Incident ruling history is inconsistent; review the data audit before changing this case"); }
    private static String publicationState(org.ctc.domain.model.IncidentDeliveryState state) {
        return switch (state) {
            case PENDING -> "Waiting"; case CLAIMED -> "Sending or awaiting outcome"; case SENT -> "Confirmed";
            case FAILED -> "Rejected, retry pending"; case UNCERTAIN -> "Outcome unknown; review required";
            case SUPERSEDED -> "Replaced by another approved version";
        };
    }
    private static String link(String guild, String thread, String message) {
        if (thread == null) return null;
        return "https://discord.com/channels/" + guild + "/" + thread + (message == null ? "" : "/" + message);
    }
    public record ReportRevisionView(int number, String actor, String at, String drivers, String timestamp, String description, String evidence) { }
    public record DecisionView(String action, String at, String actor) { }
    public record RulingRevisionView(int number, String text, String reason, String actor, String at, boolean published, String publicationState) { }
    public record PublicationView(UUID id, long version, int rulingVersion, String state, String author, String at, String reviewReason, boolean unresolved, boolean reviewable) { }
    public record Page(UUID reportId, UUID seasonId, UUID contextId, UUID raceId, String reference, String state, String pairing,
            String matchday, String party, String author, String deadline, String frozenRevision,
            String privateThreadUrl, String publicThreadUrl, String threadState, String rulingState, String rulingUrl,
            long version, int latestRulingVersion, String draftText, String draftReason, String preview, String generation,
            List<ReportRevisionView> reportHistory, List<DecisionView> decisions, List<RulingRevisionView> rulingHistory,
            PublicationView publication, boolean publishable, boolean paused) {
        public Page { reportHistory = List.copyOf(reportHistory); decisions = List.copyOf(decisions); rulingHistory = List.copyOf(rulingHistory); }
    }
}
