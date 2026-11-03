package org.ctc.domain.service;

import java.time.Clock;
import java.util.Comparator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class IncidentPrivateReadService {
    private final IncidentAccessService access;
    private final IncidentContextService contexts;
    private final IncidentWindowService windows;
    private final IncidentReportRepository reports;
    private final IncidentReportRevisionRepository revisions;
    private final Clock clock;

    public IncidentReporterView home(AuthorizedIncidentActor actor) {
        if (actor == null) throw unavailable();
        var context = contexts.get(actor.contextId()); access.requireCurrent(actor, context, null);
        var target = contexts.resolve(IncidentContextService.reference(context)); var window = windows.getForContext(context.getId());
        boolean editable = window.getState() == IncidentWindowState.OPEN && window.getDeadline() != null && clock.instant().isBefore(window.getDeadline());
        var ordered = target.races().stream().sorted(Comparator.comparingInt((Race race) -> race.getMatchday().getSortIndex())
                .thenComparing(Race::getDateTime, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Race::getId)).toList();
        var choices = ordered.stream().map(race -> new IncidentReporterView.RaceChoice(race.getId(), "Leg " + (ordered.indexOf(race) + 1)
                + " | " + target.season().getName() + " | " + race.getMatchday().getLabel() + " | "
                + race.getHomeTeam().getShortName() + " vs " + race.getAwayTeam().getShortName())).toList();
        var cases = reports.findByWindowIdAndAuthorIdOrderByCaseReferenceAsc(window.getId(), actor.userId()).stream()
                .filter(report -> actor.party().getType() == report.getParty().getType() && actor.party().getId().equals(report.getParty().getId()))
                .map(report -> summary(report, editable)).toList();
        return new IncidentReporterView(context.getId(), window.getId(), window.getVersion(), editable,
                target.season().getName() + " | " + target.home().getShortName() + " vs " + target.away().getShortName(), choices, cases);
    }

    public IncidentReporterView.CaseDetail load(AuthorizedIncidentActor actor, UUID id) {
        var view = home(actor);
        if (id == null) throw unavailable();
        var report = reports.findByIdAndWindowContextIdAndAuthorId(id, actor.contextId(), actor.userId()).orElseThrow(IncidentPrivateReadService::unavailable);
        access.requireCurrent(actor, contexts.get(actor.contextId()), report.getParty());
        if (!report.getWindow().getId().equals(view.windowId()) || view.races().stream().noneMatch(race -> race.id().equals(report.getRace().getId()))) throw unavailable();
        IncidentReportService.requireRaceIdentity(report, report.getRace());
        var revision = revisions.findByReportIdAndRevisionNumber(id, report.getLatestRevisionNumber()).orElseThrow(IncidentPrivateReadService::unavailable);
        return new IncidentReporterView.CaseDetail(summary(report, view.editable()), revision.getPayload());
    }

    private static IncidentReporterView.CaseChoice summary(IncidentReport report, boolean editable) {
        return new IncidentReporterView.CaseChoice(report.getId(), report.getRace().getId(), report.getCaseReference(), report.getState().name(),
                report.getParty().getLabel(), report.getVersion(), report.getLatestRevisionNumber(), editable && report.getState() == IncidentReportState.ACTIVE);
    }
    private static BusinessRuleException unavailable() { return new BusinessRuleException("This incident case is unavailable for this reporter"); }
}
