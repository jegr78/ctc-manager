package org.ctc.admin.service;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.IncidentBindingForm;
import org.ctc.admin.dto.IncidentDeadlineForm;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.IncidentManagementActor;
import org.ctc.domain.incidents.IncidentMatchRef;
import org.ctc.domain.incidents.IncidentReportAccess;
import org.ctc.domain.model.IncidentMatchContext;
import org.ctc.domain.model.IncidentReportEvent;
import org.ctc.domain.model.IncidentReportState;
import org.ctc.domain.model.IncidentWindowState;
import org.ctc.domain.repository.IncidentReportEventRepository;
import org.ctc.domain.repository.IncidentReportRepository;
import org.ctc.domain.repository.IncidentReportRevisionRepository;
import org.ctc.domain.service.IncidentContextService;
import org.ctc.domain.service.IncidentMatchTarget;
import org.ctc.domain.service.IncidentWindowService;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentAdminService {
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter ZONED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm XXX 'Europe/Berlin'").withZone(IncidentWindowService.DEADLINE_ZONE);
    private final IncidentContextService contexts;
    private final IncidentWindowService windows;
    private final IncidentReportRepository reports;
    private final IncidentReportRevisionRepository revisions;
    private final IncidentReportEventRepository events;
    private final DiscordGlobalConfigRepository configs;
    private final Optional<IncidentReportAccess> access;
    private final Environment environment;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page viewTarget(IncidentMatchRef reference) {
        var target = contexts.resolve(reference);
        return page(target, contexts.find(target.reference()));
    }

    @Transactional(readOnly = true)
    public Page view(UUID contextId) {
        var context = contexts.get(contextId);
        return page(contexts.resolve(IncidentContextService.reference(context)), context);
    }

    @Transactional(readOnly = true)
    public UUID contextForMatch(UUID matchId) {
        var target = contexts.resolve(IncidentMatchRef.match(matchId));
        var context = contexts.find(target.reference());
        if (context == null) throw new BusinessRuleException("Bind the incident context before confirming its deadline");
        return context.getId();
    }

    public UUID bind(IncidentMatchRef reference, IncidentBindingForm form, Principal principal) {
        return contexts.bind(reference, form.getGuildId(), form.getChannelId(), form.getExpectedVersion(), actor(principal));
    }
    public void open(UUID contextId, IncidentDeadlineForm form, Principal principal) {
        var actor = actor(principal);
        windows.open(contextId, windows.parseDeadline(form.getDeadline(), form.getOffset()), form.getExpectedVersion(), actor);
    }
    public void change(UUID contextId, IncidentDeadlineForm form, Principal principal) {
        var actor = actor(principal);
        windows.changeDeadline(contextId, windows.parseDeadline(form.getDeadline(), form.getOffset()), form.getExpectedVersion(), form.getReason(), actor);
    }
    public IncidentBindingForm bindingForm(Page page) {
        var form = new IncidentBindingForm(); form.setGuildId(page.guildId()); form.setChannelId(page.channelId()); form.setExpectedVersion(0L); return form;
    }
    public IncidentDeadlineForm deadlineForm(Page page) {
        var form = new IncidentDeadlineForm(); form.setDeadline(page.deadlineLocal()); form.setOffset(page.offset());
        form.setExpectedVersion(page.windowVersion()); form.setReason(""); return form;
    }

    private Page page(IncidentMatchTarget target, IncidentMatchContext context) {
        boolean regular = target.match() != null;
        UUID targetId = regular ? target.match().getId() : target.playoffMatchup().getId();
        String back = regular ? "/admin/matches/" + targetId : "/admin/playoffs/matchup/" + targetId;
        var config = configs.findFirstByOrderByIdAsc();
        String guild = context != null ? context.getGuildId() : config == null ? "" : config.getGuildId();
        String channel = context != null ? context.getChannelId() : regular ? target.match().getDiscordChannelId() : "";
        var preview = context == null ? windows.previewUnbound(target.reference()) : windows.previewDeadline(context.getId());
        var window = context == null ? null : windows.getForContext(context.getId());
        boolean draft = window != null && window.getState() == IncidentWindowState.DRAFT;
        boolean expired = window != null && window.getDeadline() != null && !clock.instant().isBefore(window.getDeadline());
        boolean changeable = window != null && window.getState() == IncidentWindowState.OPEN && !expired;
        Instant proposed = window != null && !draft ? window.getDeadline() : preview.defaultDeadline();
        var local = proposed == null ? null : proposed.atZone(IncidentWindowService.DEADLINE_ZONE);
        String state = window == null ? "Not bound" : draft ? "Draft" : window.getState() == IncidentWindowState.CLOSED ? "Closed" : expired ? "Expired" : "Open";
        List<CaseView> cases = window == null ? List.of() : reports.findByWindowIdOrderByCaseReferenceAsc(window.getId()).stream().map(report -> {
            var revision = revisions.findByReportIdAndRevisionNumber(report.getId(), report.getLatestRevisionNumber())
                    .orElseThrow(() -> new BusinessRuleException("Incident history is inconsistent; review the data audit before changing reporting"));
            var payload = revision.getPayload();
            return new CaseView(report.getCaseReference(), report.getState() == IncidentReportState.ACTIVE ? "Active" : "Withdrawn",
                    report.getRace().getId(), report.getAuthorId(), report.getOriginalMatchday().getLabel(), report.getParty().getLabel(), report.getLatestRevisionNumber(),
                    payload.getInvolvedDrivers(), payload.getReplayTimestamp(), payload.getDescription(), payload.getEvidenceLinks());
        }).toList();
        List<DecisionView> decisions = window == null ? List.of() : events.findByWindowIdOrderByAcceptedAtAscIdAsc(window.getId()).stream()
                .filter(event -> event.getReport() == null).sorted(Comparator.comparingLong(IncidentReportEvent::getWindowVersion))
                .map(event -> new DecisionView(event.getKind().name().replace('_', ' '), ZONED.format(event.getAcceptedAt()), event.getActorId(),
                        event.getReason(), ZONED.format(event.getDeadline()))).toList();
        return new Page(target.season().getId(), context == null ? null : context.getId(), target.home().getShortName() + " vs " + target.away().getShortName(),
                back, back + "/incidents/bind", regular, guild, channel, state, draft, changeable,
                window == null ? 0 : window.getVersion(), preview.plannedStartLocal() == null ? "Not scheduled" : LOCAL.format(preview.plannedStartLocal()) + " Europe/Berlin",
                preview.defaultDeadline() == null ? "No automatic suggestion" : ZONED.format(preview.defaultDeadline()), preview.expired(), preview.problem(),
                window == null || window.getDeadline() == null ? "Not confirmed" : ZONED.format(window.getDeadline()),
                window == null || window.getDeadline() == null ? "" : "<t:" + window.getDeadline().getEpochSecond() + ":F>",
                local == null ? null : local.toLocalDateTime(), local == null ? "" : local.getOffset().toString(), access.isEmpty(), cases, decisions);
    }

    IncidentManagementActor actor(Principal principal) {
        String name = principal == null ? null : principal.getName();
        if (principal == null && environment.acceptsProfiles(Profiles.of("dev", "local"))) name = "development-manager";
        if (name == null || name.isBlank() || name.length() > 128) throw new BusinessRuleException("An authenticated management actor is required");
        return new IncidentManagementActor(name);
    }

    public record CaseView(String reference, String state, UUID raceId, String authorId, String matchday, String party, int revision,
                           String drivers, String replayTimestamp, String description, String evidence) {}
    public record DecisionView(String action, String at, String actor, String reason, String deadline) {}
    public record Page(UUID seasonId, UUID contextId, String pairing, String backPath, String bindingPath, boolean regular,
                       String guildId, String channelId, String state, boolean draft, boolean changeable, long windowVersion,
                       String plannedStart, String suggestedDeadline, boolean suggestionExpired, String previewProblem,
                       String confirmedDeadline, String discordDeadline, LocalDateTime deadlineLocal, String offset,
                       boolean intakeUnavailable, List<CaseView> cases, List<DecisionView> decisions) {
        public Page { cases = List.copyOf(cases); decisions = List.copyOf(decisions); }
        public boolean bound() { return contextId != null; }
    }
}
