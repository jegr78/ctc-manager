package org.ctc.domain.service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.incidents.PublicIncidentCase;
import org.ctc.domain.model.IncidentDeliveryOperation;
import org.ctc.domain.model.IncidentDeliveryState;
import org.ctc.domain.model.IncidentReportState;
import org.ctc.domain.model.IncidentRulingPublication;
import org.ctc.domain.model.IncidentWindowState;
import org.ctc.domain.repository.IncidentRulingPublicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentPublicReadService {
    private final IncidentRulingPublicationRepository publications;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PublicIncidentCase> forSeason(UUID seasonId) {
        Objects.requireNonNull(seasonId);
        var now = clock.instant();
        return publications.findConfirmedForSeason(seasonId, now).stream()
                .filter(row -> eligible(row, seasonId, now)).map(this::project).toList();
    }

    private boolean eligible(IncidentRulingPublication row, UUID seasonId, java.time.Instant now) {
        var revision = row.getRevision();
        var ruling = revision.getRuling();
        var report = ruling.getReport();
        var window = report.getWindow();
        var thread = row.getThreadPublication();
        var disclosure = thread.getDelivery();
        var frozen = disclosure.getRevision();
        var race = report.getRace();
        var original = report.getOriginalMatchday();
        var context = window.getContext();
        boolean owningRace = context.getPlayoffMatchup() != null
                ? race.getPlayoffMatchup() != null && context.getPlayoffMatchup().getId().equals(race.getPlayoffMatchup().getId())
                : context.getMatch() != null && race.getMatch() != null && context.getMatch().getId().equals(race.getMatch().getId());
        if (row.getState() != IncidentDeliveryState.SENT || row.getCompletedAt() == null
                || row.getCompletedAt().isAfter(now)
                || !Objects.equals(ruling.getPublishedRevisionNumber(), revision.getRevisionNumber())
                || row.getMessageId() == null || !row.getMessageId().equals(ruling.getPublishedMessageId())
                || report.getState() != IncidentReportState.ACTIVE || window.getState() != IncidentWindowState.CLOSED
                || window.getDeadline() == null || window.getClosedAt() == null
                || window.getClosedAt().isBefore(window.getDeadline()) || window.getClosedAt().isAfter(now)
                || !seasonId.equals(window.getContext().getSeason().getId())
                || !seasonId.equals(race.getMatchday().getSeason().getId()) || !seasonId.equals(original.getSeason().getId())
                || !original.getId().equals(race.getMatchday().getId()) || !owningRace
                || !report.getOriginalHomeTeam().getId().equals(race.getHomeTeam().getId())
                || !report.getOriginalAwayTeam().getId().equals(race.getAwayTeam().getId())
                || !row.getGuildId().equals(context.getGuildId()) || !row.getGuildId().equals(disclosure.getGuildId())
                || !row.getChannelId().equals(thread.getPublicThreadId())
                || row.getCompletedAt().isBefore(window.getClosedAt())
                || row.getCompletedAt().isBefore(revision.getAcceptedAt())
                || disclosure.getOperation() != IncidentDeliveryOperation.DISCLOSURE
                || !report.getId().equals(disclosure.getReport().getId()) || !report.getId().equals(frozen.getReport().getId())
                || !Objects.equals(report.getFrozenRevisionNumber(), frozen.getRevisionNumber())
                || !report.getCaseReference().equals(disclosure.getCaseReference()) || !thread.publicComplete()
                || disclosure.getPairingLabel() == null || disclosure.getPairingLabel().isBlank()
                || disclosure.getRaceLabel() == null || disclosure.getRaceLabel().isBlank()) return false;
        return IncidentRulingProjection.render(report.getId(), report.getCaseReference(), revision.getText()).equals(row.getContent());
    }

    private PublicIncidentCase project(IncidentRulingPublication row) {
        var revision = row.getRevision();
        var report = revision.getRuling().getReport();
        var disclosure = row.getThreadPublication().getDelivery();
        var facts = disclosure.getRevision().getPayload();
        var day = report.getOriginalMatchday();
        String raceLabel = disclosure.getRaceLabel();
        String suffix = " · Race " + report.getRace().getId();
        if (raceLabel != null && raceLabel.endsWith(suffix)) raceLabel = raceLabel.substring(0, raceLabel.length() - suffix.length());
        return new PublicIncidentCase(report.getId(), report.getCaseReference(), day.getId(), day.getLabel(), day.getSortIndex(),
                disclosure.getPairingLabel(), raceLabel, disclosure.getPartyLabel(), facts.getInvolvedDrivers(), facts.getReplayTimestamp(),
                facts.getDescription(), facts.getEvidenceLinks(), revision.getText(), revision.getRevisionNumber(), row.getCompletedAt());
    }
}
