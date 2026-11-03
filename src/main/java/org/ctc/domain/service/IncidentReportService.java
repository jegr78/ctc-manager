package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.AuthorizedIncidentActor;
import org.ctc.domain.incidents.IncidentReportAccess;
import org.ctc.domain.incidents.IncidentReportReceipt;
import org.ctc.domain.model.IncidentEventKind;
import org.ctc.domain.model.IncidentMatchContext;
import org.ctc.domain.model.IncidentParty;
import org.ctc.domain.model.IncidentPartyType;
import org.ctc.domain.model.IncidentPayload;
import org.ctc.domain.model.IncidentReport;
import org.ctc.domain.model.IncidentReportEvent;
import org.ctc.domain.model.IncidentReportRevision;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.IncidentReportEventRepository;
import org.ctc.domain.repository.IncidentReportRepository;
import org.ctc.domain.repository.IncidentReportRevisionRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IncidentReportService {
    private final IncidentReportRepository reports;
    private final IncidentReportRevisionRepository revisions;
    private final IncidentReportEventRepository events;
    private final IncidentWindowService windows;
    private final IncidentContextService contexts;
    private final Optional<IncidentReportAccess> access;
    private final PickWriteExecutor writes;
    private final Clock clock;
    private final EntityManager entityManager;
    private final org.springframework.context.ApplicationEventPublisher publisher;

    public IncidentReportReceipt submit(UUID windowId, UUID raceId, long expectedWindowVersion, UUID requestId,
                                        AuthorizedIncidentActor actor, IncidentPayload payload) {
        return writes.execute(() -> {
            preauthorize(actor);
            var window = windows.lock(windowId);
            authorize(actor, window.getContext(), null);
            var replay = replay(requestId, IncidentEventKind.SUBMITTED, windowId, raceId, null, actor);
            if (replay != null) return replay;
            window.requireVersion(expectedWindowVersion);
            var race = contexts.resolve(IncidentContextService.reference(window.getContext())).races().stream()
                    .filter(row -> row.getId().equals(raceId)).findFirst()
                    .orElseThrow(() -> new BusinessRuleException("Choose a race from this exact incident context"));
            validatePayload(payload); validateParty(actor.party(), window.getContext());
            var now = clock.instant(); window.requireOpenAt(now);
            var report = new IncidentReport(window, race, actor.userId(), actor.party());
            report.appendRevision(); report = reports.save(report);
            return append(report, payload, actor, requestId, IncidentEventKind.SUBMITTED, now);
        });
    }

    public IncidentReportReceipt edit(UUID reportId, long expectedWindowVersion, long expectedReportVersion, UUID requestId,
                                      AuthorizedIncidentActor actor, IncidentPayload payload) {
        return writes.execute(() -> {
            var report = lockReport(reportId, actor);
            var replay = replay(requestId, IncidentEventKind.EDITED, report.getWindow().getId(), report.getRace().getId(), reportId, actor);
            if (replay != null) return replay;
            report.getWindow().requireVersion(expectedWindowVersion); report.requireVersion(expectedReportVersion);
            validatePayload(payload);
            var now = clock.instant(); report.getWindow().requireOpenAt(now);
            report.appendRevision(); reports.save(report);
            return append(report, payload, actor, requestId, IncidentEventKind.EDITED, now);
        });
    }

    public IncidentReportReceipt withdraw(UUID reportId, long expectedWindowVersion, long expectedReportVersion,
                                          UUID requestId, AuthorizedIncidentActor actor) {
        return writes.execute(() -> {
            var report = lockReport(reportId, actor);
            var replay = replay(requestId, IncidentEventKind.WITHDRAWN, report.getWindow().getId(), report.getRace().getId(), reportId, actor);
            if (replay != null) return replay;
            report.getWindow().requireVersion(expectedWindowVersion); report.requireVersion(expectedReportVersion);
            var now = clock.instant(); report.getWindow().requireOpenAt(now);
            report.withdraw(); reports.save(report);
            var event = events.save(IncidentReportEvent.reportDecision(report, IncidentEventKind.WITHDRAWN,
                    actor.userId(), now.truncatedTo(ChronoUnit.MICROS), requestId));
            return event.receipt();
        });
    }

    private IncidentReportReceipt append(IncidentReport report, IncidentPayload payload, AuthorizedIncidentActor actor,
                                         UUID request, IncidentEventKind action, Instant now) {
        var accepted = now.truncatedTo(ChronoUnit.MICROS);
        var revision = revisions.save(new IncidentReportRevision(report, report.getLatestRevisionNumber(), report.getVersion(),
                payload, actor.userId(), accepted, request));
        var event = events.save(IncidentReportEvent.reportDecision(report, action, actor.userId(), accepted, request));
        if (action == IncidentEventKind.SUBMITTED) publisher.publishEvent(new org.ctc.domain.incidents.IncidentSubmissionAccepted(report, revision));
        return event.receipt();
    }

    private IncidentReport lockReport(UUID id, AuthorizedIncidentActor actor) {
        preauthorize(actor);
        var snapshot = reports.findById(id).orElseThrow(() -> new BusinessRuleException("Incident report is unavailable"));
        if (!snapshot.getWindow().getContext().getId().equals(actor.contextId())) throw new BusinessRuleException("Incident report is unavailable in this context");
        var window = windows.lock(snapshot.getWindow().getId());
        var report = reports.findByIdForUpdate(id).orElseThrow(() -> new BusinessRuleException("Incident report is unavailable"));
        entityManager.refresh(report, LockModeType.PESSIMISTIC_WRITE);
        authorize(actor, window.getContext(), report.getParty());
        if (!report.getAuthorId().equals(actor.userId())) throw new BusinessRuleException("Only the original creator can edit or withdraw this report");
        requirePartyIdentity(actor.party(), report.getParty());
        var race = contexts.resolve(IncidentContextService.reference(window.getContext())).races().stream()
                .filter(row -> row.getId().equals(report.getRace().getId())).findFirst()
                .orElseThrow(() -> new BusinessRuleException("The original incident race is no longer in this context"));
        requireRaceIdentity(report, race);
        return report;
    }

    private void preauthorize(AuthorizedIncidentActor actor) {
        if (actor == null || actor.contextId() == null || !IncidentContextService.snowflake(actor.userId())) {
            throw new BusinessRuleException("A current authorized incident reporter is required");
        }
        authorize(actor, contexts.get(actor.contextId()), null);
    }

    private void authorize(AuthorizedIncidentActor actor, IncidentMatchContext context, IncidentParty original) {
        var verifier = access.orElseThrow(() -> new BusinessRuleException("Incident reporting is unavailable until reporter authorization is configured"));
        if (context == null || !context.getId().equals(actor.contextId()) || !context.getGuildId().equals(actor.guildId())
                || !context.getChannelId().equals(actor.channelId())) throw new BusinessRuleException("Incident context is unavailable from this channel");
        verifier.requireCurrent(actor, context, original);
    }

    private IncidentReportReceipt replay(UUID request, IncidentEventKind kind, UUID window, UUID race, UUID report,
                                         AuthorizedIncidentActor actor) {
        if (request == null) throw new BusinessRuleException("A durable incident request identity is required");
        var event = events.findByRequestId(request).orElse(null);
        if (event == null) return null;
        var original = event.getReport();
        if (original == null || event.getKind() != kind || !original.getWindow().getId().equals(window)
                || !original.getRace().getId().equals(race) || report != null && !original.getId().equals(report)
                || !original.getAuthorId().equals(actor.userId())) {
            throw new BusinessRuleException("This request belongs to another incident operation");
        }
        authorize(actor, original.getWindow().getContext(), original.getParty());
        requirePartyIdentity(actor.party(), original.getParty());
        return event.receipt();
    }

    private static void validatePayload(IncidentPayload payload) {
        if (payload == null) throw new BusinessRuleException("A complete incident report is required");
        requireText(payload.getInvolvedDrivers(), 2000, "involved drivers");
        requireText(payload.getReplayTimestamp(), 200, "replay timestamp");
        requireText(payload.getDescription(), 20000, "description");
        if (payload.getEvidenceLinks() == null || payload.getEvidenceLinks().length() > 8000) {
            throw new BusinessRuleException("Incident evidence references must be at most 8000 characters");
        }
    }

    private static void requireText(String value, int maximum, String field) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new BusinessRuleException("Incident " + field + " is required and must be at most " + maximum + " characters");
        }
    }

    private static void validateParty(IncidentParty party, IncidentMatchContext context) {
        if (party == null || party.getType() == null || party.getId() == null || party.getLabel() == null
                || party.getLabel().isBlank() || party.getLabel().length() > 255 || !IncidentContextService.snowflake(party.getRoleId())) {
            throw new BusinessRuleException("A verified incident reporting party is required");
        }
        boolean valid;
        if (party.getType() == IncidentPartyType.TEAM) {
            valid = party.getId().equals(context.getHomeTeam().getId()) || party.getId().equals(context.getAwayTeam().getId());
        } else {
            UUID home = organization(context.getHomeTeam()), away = organization(context.getAwayTeam());
            valid = home.equals(away) && party.getId().equals(home);
        }
        if (!valid) throw new BusinessRuleException("The reporting party does not belong to this incident pairing");
    }

    private static UUID organization(Team team) { return team.getParentTeam() == null ? team.getId() : team.getParentTeam().getId(); }
    private static void requirePartyIdentity(IncidentParty current, IncidentParty original) {
        if (current == null || current.getType() != original.getType() || !original.getId().equals(current.getId())) {
            throw new BusinessRuleException("The reporter no longer represents the original reporting party");
        }
    }
    static void requireRaceIdentity(IncidentReport report, Race race) {
        if (race.getMatchday() == null || !report.getOriginalMatchday().getId().equals(race.getMatchday().getId())
                || race.getHomeTeam() == null || race.getAwayTeam() == null
                || !report.getOriginalHomeTeam().getId().equals(race.getHomeTeam().getId())
                || !report.getOriginalAwayTeam().getId().equals(race.getAwayTeam().getId())) {
            throw new BusinessRuleException("The original incident race identity changed; preserve its leg and participants");
        }
    }
}
