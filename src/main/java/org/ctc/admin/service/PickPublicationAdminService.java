package org.ctc.admin.service;

import java.io.IOException;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.PickPublicationForm;
import org.ctc.discord.service.DiscordPickReconciliationService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.PickPublicationAttemptRepository;
import org.ctc.domain.repository.PickPublicationSnapshotRepository;
import org.ctc.domain.service.PickPublicationService;
import org.ctc.domain.service.PickSessionEpoch;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class PickPublicationAdminService {
    private final PickPublicationAttemptRepository attempts;
    private final PickPublicationSnapshotRepository snapshots;
    private final PickPublicationService publications;
    private final DiscordPickReconciliationService discord;
    private final PickSessionEpoch epoch;
    private final Environment environment;

    @Transactional(readOnly = true)
    public List<Summary> pending() {
        var batches = new LinkedHashMap<UUID, Summary>();
        for (var row : attempts.findByStateInOrderByCapturedAtDesc(List.of(PickPublicationState.PREPARED, PickPublicationState.UNCERTAIN))) {
            batches.putIfAbsent(row.getBatchId(), new Summary(row.getBatchId(), label(row), row.getOutput().name(), row.getState().name()));
        }
        return List.copyOf(batches.values());
    }
    @Transactional(readOnly = true)
    public Detail detail(UUID batchId) {
        String generation = epoch.managementGeneration();
        var records = attempts.findByBatchId(batchId);
        if (records.isEmpty()) throw new BusinessRuleException("The publication record is unavailable; reload the list");
        var first = records.getFirst();
        var rows = records.stream().map(row -> new Row(label(row), row.getContentVersion(), row.getState().name(),
                row.fields().stream().sorted().map(Enum::name).toList(), !row.getContentHash().equals(PickPublicationContent.from(row.getRace()).fingerprint()))).toList();
        boolean open = records.stream().allMatch(row -> row.getState() == PickPublicationState.PREPARED || row.getState() == PickPublicationState.UNCERTAIN);
        return new Detail(batchId, first.getOutput().name(), first.getChannelId(), first.getMessageId(), first.getTargetMessageId(),
                publications.reconciliationFingerprint(batchId), generation, rows, open, publications.isInFlight(batchId));
    }
    public PickPublicationForm form(Detail detail) {
        var form = new PickPublicationForm(); form.setExpectedFingerprint(detail.fingerprint()); form.setExpectedGeneration(detail.generation());
        form.setMessageId(detail.messageId() == null ? detail.targetMessageId() : detail.messageId()); return form;
    }

    public org.ctc.admin.dto.MatchdayPickView.Publication status(Race race) {
        var pending = attempts.findByActiveRaceId(race.getId()).orElse(null);
        var confirmed = snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(race.getId()).stream()
                .map(PickPublicationSnapshot::getAttempt).filter(row -> row.getState() == PickPublicationState.SUCCEEDED && row.matchesTarget(race)).toList();
        if (confirmed.isEmpty()) return new org.ctc.admin.dto.MatchdayPickView.Publication("Unpublished", null, List.of(), pending == null ? null : pending.getBatchId(), pending != null);
        var latest = confirmed.stream().max(java.util.Comparator.comparingLong(PickPublicationAttempt::getContentVersion)).orElseThrow();
        var fields = confirmed.stream().filter(row -> row.getContentVersion() == latest.getContentVersion() && row.getContentHash().equals(latest.getContentHash()))
                .flatMap(row -> row.fields().stream()).distinct().sorted().map(Enum::name).toList();
        boolean changed = !latest.getContentHash().equals(PickPublicationContent.from(race).fingerprint());
        return new org.ctc.admin.dto.MatchdayPickView.Publication(changed ? "Internal changes unpublished" : "Captured fields published", latest.getContentVersion(), fields,
                pending == null ? latest.getBatchId() : pending.getBatchId(), pending != null);
    }
    public void confirm(UUID batchId, PickPublicationForm form, Principal principal) throws IOException {
        publications.reconcile(batchId, form.getExpectedFingerprint(), form.getReason(), actor(principal, form.getExpectedGeneration()),
                rows -> discord.prepare(rows, form.getMessageId()));
    }
    public void abandon(UUID batchId, PickPublicationForm form, Principal principal) {
        publications.abandon(batchId, form.getExpectedFingerprint(), form.getReason(), form.isRemoteHandled(), actor(principal, form.getExpectedGeneration()), discord::quarantine);
    }
    private ManagementPickActor actor(Principal principal, String generation) {
        String name = principal == null ? null : principal.getName();
        if (principal == null && environment.acceptsProfiles(Profiles.of("dev", "local"))) name = "development-manager";
        if (name == null || name.isBlank() || name.length() > 128) throw new BusinessRuleException("An authenticated management actor is required");
        return new ManagementPickActor(name, generation);
    }
    private static String label(PickPublicationAttempt attempt) {
        var race = attempt.getRace();
        return race.getMatchday().getLabel() + " / " + race.getHomeTeam().getShortName() + " vs " + race.getAwayTeam().getShortName();
    }
    public record Summary(UUID batchId, String label, String output, String state) { }
    public record Row(String label, long version, String state, List<String> fields, boolean changed) { }
    public record Detail(UUID batchId, String output, String channelId, String messageId, String targetMessageId, String fingerprint,
                         String generation, List<Row> rows, boolean open, boolean inFlight) { }
}
