package org.ctc.domain.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.incidents.PublicIncidentCase;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.IncidentRulingPublicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentPublicReadServiceTest {
    final Instant now = Instant.parse("2026-11-08T16:00:00Z");
    final UUID season = UUID.randomUUID(), reportId = UUID.randomUUID(), raceId = UUID.randomUUID(), dayId = UUID.randomUUID();
    final IncidentRulingPublicationRepository rows = mock(IncidentRulingPublicationRepository.class);
    final IncidentPublicReadService service = new IncidentPublicReadService(rows, Clock.fixed(now, ZoneOffset.UTC));
    final IncidentRulingPublication row = mock(IncidentRulingPublication.class, RETURNS_DEEP_STUBS);
    final IncidentRulingRevision revision = row.getRevision();
    final IncidentRuling ruling = revision.getRuling();
    final IncidentReport report = ruling.getReport();
    final IncidentReportWindow window = report.getWindow();
    final IncidentMatchContext context = window.getContext();
    final IncidentThreadPublication thread = row.getThreadPublication();
    final IncidentDelivery disclosure = thread.getDelivery();
    final IncidentReportRevision frozen = disclosure.getRevision();
    final Race race = report.getRace();

    IncidentPublicReadServiceTest() {
        when(rows.findConfirmedForSeason(season, now)).thenReturn(List.of(row));
        when(row.getState()).thenReturn(IncidentDeliveryState.SENT);
        when(row.getCompletedAt()).thenReturn(now.minusSeconds(30));
        when(row.getMessageId()).thenReturn("950");
        when(row.getGuildId()).thenReturn("100");
        when(row.getChannelId()).thenReturn("801");
        when(revision.getRevisionNumber()).thenReturn(1);
        when(revision.getText()).thenReturn("Final ruling: no further action.");
        when(revision.getAcceptedAt()).thenReturn(now.minusSeconds(60));
        when(ruling.getPublishedRevisionNumber()).thenReturn(1);
        when(ruling.getPublishedMessageId()).thenReturn("950");
        when(report.getId()).thenReturn(reportId);
        when(report.getCaseReference()).thenReturn("INC-TEST-PUBLIC");
        when(report.getState()).thenReturn(IncidentReportState.ACTIVE);
        when(report.getFrozenRevisionNumber()).thenReturn(2);
        when(window.getState()).thenReturn(IncidentWindowState.CLOSED);
        when(window.getDeadline()).thenReturn(now.minusSeconds(180));
        when(window.getClosedAt()).thenReturn(now.minusSeconds(120));
        when(context.getSeason().getId()).thenReturn(season);
        when(context.getGuildId()).thenReturn("100");
        when(context.getPlayoffMatchup()).thenReturn(null);
        UUID match = UUID.randomUUID(), home = UUID.randomUUID(), away = UUID.randomUUID();
        when(context.getMatch().getId()).thenReturn(match);
        when(race.getMatch().getId()).thenReturn(match);
        when(race.getId()).thenReturn(raceId);
        when(race.getMatchday().getId()).thenReturn(dayId);
        when(race.getMatchday().getSeason().getId()).thenReturn(season);
        when(race.getHomeTeam().getId()).thenReturn(home);
        when(race.getAwayTeam().getId()).thenReturn(away);
        when(report.getOriginalHomeTeam().getId()).thenReturn(home);
        when(report.getOriginalAwayTeam().getId()).thenReturn(away);
        when(report.getOriginalMatchday().getId()).thenReturn(dayId);
        when(report.getOriginalMatchday().getSeason().getId()).thenReturn(season);
        when(report.getOriginalMatchday().getLabel()).thenReturn("Matchday 1");
        when(disclosure.getOperation()).thenReturn(IncidentDeliveryOperation.DISCLOSURE);
        when(disclosure.getState()).thenReturn(IncidentDeliveryState.SENT);
        when(disclosure.getReport().getId()).thenReturn(reportId);
        when(disclosure.getCaseReference()).thenReturn("INC-TEST-PUBLIC");
        when(disclosure.getGuildId()).thenReturn("100");
        when(disclosure.getPartyLabel()).thenReturn("Test Alpha");
        when(disclosure.getPairingLabel()).thenReturn("T-ALF vs T-BRV");
        when(disclosure.getRaceLabel()).thenReturn("Leg 1 · Matchday 1 · Race " + raceId);
        when(frozen.getReport().getId()).thenReturn(reportId);
        when(frozen.getRevisionNumber()).thenReturn(2);
        when(frozen.getPayload()).thenReturn(new IncidentPayload("Alpha 1 / Bravo 3", "00:13.500", "Reported facts", "Shared replay"));
        when(thread.getPublicThreadId()).thenReturn("801");
        when(thread.publicComplete()).thenReturn(true);
        when(row.getContent()).thenReturn(IncidentRulingProjection.render(reportId, "INC-TEST-PUBLIC", "Final ruling: no further action."));
    }

    @Test
    void givenConfirmedCase_whenProjecting_thenUseFrozenReportAndPublishedRulingWithoutTechnicalSuffix() {
        when(ruling.getLatestRevisionNumber()).thenReturn(3);
        var actual = service.forSeason(season).getFirst();
        assertThat(actual.description()).isEqualTo("Reported facts");
        assertThat(actual.involvedDrivers()).isEqualTo("Alpha 1 / Bravo 3");
        assertThat(actual.ruling()).isEqualTo("Final ruling: no further action.");
        assertThat(actual.rulingVersion()).isEqualTo(1);
        assertThat(actual.raceLabel()).isEqualTo("Leg 1 · Matchday 1");
        assertThat(actual.getPublishedAtLabel()).isEqualTo("08 Nov 2026 15:59 UTC");
        assertThat(actual.toString()).doesNotContain("Reported facts", "Alpha 1");
        assertThat(Arrays.stream(PublicIncidentCase.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getType))
                .allSatisfy(type -> assertThat(type.getPackageName()).doesNotStartWith("org.ctc.domain.model"));
    }

    @Test
    void givenArchivedConfirmedCaseWithPausedOutbound_whenProjecting_thenRetainItsConfirmedEvidence() {
        when(window.isDeliveryPaused()).thenReturn(true);
        assertThat(service.forSeason(season)).hasSize(1);
    }

    @Test
    void givenPlayoffCase_whenProjecting_thenRequireItsConcretePlayoffRace() {
        var matchup = mock(PlayoffMatchup.class);when(matchup.getId()).thenReturn(UUID.randomUUID());
        when(context.getPlayoffMatchup()).thenReturn(matchup);when(race.getPlayoffMatchup()).thenReturn(matchup);
        assertThat(service.forSeason(season)).hasSize(1);
        when(race.getPlayoffMatchup()).thenReturn(null);
        assertThat(service.forSeason(season)).isEmpty();
    }

    @Test
    void givenConfirmedPublicCaseWithPendingPrivateReference_whenProjecting_thenDoNotWaitForInternalThreadBookkeeping() {
        when(disclosure.getState()).thenReturn(IncidentDeliveryState.PENDING);
        when(thread.getReferenceMessageId()).thenReturn(null);
        assertThat(service.forSeason(season)).hasSize(1);
    }

    @Test
    void givenLongFrozenText_whenProjecting_thenRetainEveryCharacterAndTheExactRuling() {
        String description = "Reported facts\n" + "d".repeat(18000);
        String evidence = "https://example.invalid/" + "e".repeat(7000);
        String verdict = "Final ruling " + "v".repeat(1600);
        when(frozen.getPayload()).thenReturn(new IncidentPayload("Alpha / Bravo", "00:13.500", description, evidence));
        when(revision.getText()).thenReturn(verdict);
        when(row.getContent()).thenReturn(IncidentRulingProjection.render(reportId, "INC-TEST-PUBLIC", verdict));

        var actual = service.forSeason(season).getFirst();

        assertThat(actual.description()).isEqualTo(description);
        assertThat(actual.evidenceReferences()).isEqualTo(evidence);
        assertThat(actual.ruling()).isEqualTo(verdict);
    }

    @ParameterizedTest
    @ValueSource(strings = {"withdrawn", "open", "future-close", "bad-cutoff", "future-delivery", "pending", "uncertain",
            "unpublished-version", "wrong-message", "wrong-season", "race-season", "original-season", "wrong-matchday", "wrong-match",
            "wrong-team", "wrong-guild", "wrong-channel", "wrong-report", "wrong-revision", "receipt", "incomplete-thread", "changed-content", "missing-identity", "early-delivery", "wrong-reference"})
    void givenIneligibleOrMismatchedEvidence_whenProjecting_thenPublishNothing(String invalid) {
        switch (invalid) {
            case "withdrawn" -> when(report.getState()).thenReturn(IncidentReportState.WITHDRAWN);
            case "open" -> when(window.getState()).thenReturn(IncidentWindowState.OPEN);
            case "future-close" -> when(window.getClosedAt()).thenReturn(now.plusSeconds(1));
            case "bad-cutoff" -> when(window.getDeadline()).thenReturn(now);
            case "future-delivery" -> when(row.getCompletedAt()).thenReturn(now.plusSeconds(1));
            case "pending" -> when(row.getState()).thenReturn(IncidentDeliveryState.PENDING);
            case "uncertain" -> when(row.getState()).thenReturn(IncidentDeliveryState.UNCERTAIN);
            case "unpublished-version" -> when(ruling.getPublishedRevisionNumber()).thenReturn(2);
            case "wrong-message" -> when(ruling.getPublishedMessageId()).thenReturn("951");
            case "wrong-season" -> when(context.getSeason().getId()).thenReturn(UUID.randomUUID());
            case "race-season" -> when(race.getMatchday().getSeason().getId()).thenReturn(UUID.randomUUID());
            case "original-season" -> when(report.getOriginalMatchday().getSeason().getId()).thenReturn(UUID.randomUUID());
            case "wrong-matchday" -> when(report.getOriginalMatchday().getId()).thenReturn(UUID.randomUUID());
            case "wrong-match" -> when(race.getMatch().getId()).thenReturn(UUID.randomUUID());
            case "wrong-team" -> when(race.getHomeTeam().getId()).thenReturn(UUID.randomUUID());
            case "wrong-guild" -> when(row.getGuildId()).thenReturn("101");
            case "wrong-channel" -> when(row.getChannelId()).thenReturn("802");
            case "wrong-report" -> when(frozen.getReport().getId()).thenReturn(UUID.randomUUID());
            case "wrong-revision" -> when(frozen.getRevisionNumber()).thenReturn(1);
            case "receipt" -> when(disclosure.getOperation()).thenReturn(IncidentDeliveryOperation.RECEIPT);
            case "incomplete-thread" -> when(thread.publicComplete()).thenReturn(false);
            case "missing-identity" -> when(disclosure.getPairingLabel()).thenReturn(null);
            case "early-delivery" -> when(row.getCompletedAt()).thenReturn(now.minusSeconds(150));
            case "wrong-reference" -> when(disclosure.getCaseReference()).thenReturn("INC-OTHER");
            case "changed-content" -> when(row.getContent()).thenReturn("Unconfirmed changed ruling");
            default -> throw new AssertionError(invalid);
        }
        assertThat(service.forSeason(season)).isEmpty();
    }
}
