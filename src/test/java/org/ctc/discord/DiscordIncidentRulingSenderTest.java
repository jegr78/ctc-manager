package org.ctc.discord;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.ctc.discord.DiscordRestClient.*;
import org.ctc.discord.incidents.DiscordIncidentRulingSender;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.IncidentRulingPublication;
import org.ctc.domain.model.IncidentThreadStage;
import org.ctc.domain.service.IncidentRulingProjection;
import org.ctc.integration.OutboundPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DiscordIncidentRulingSenderTest {
    final Instant now = Instant.parse("2035-01-02T20:00:00Z"), until = now.plusSeconds(60);
    final UUID reportId = UUID.randomUUID();
    final DiscordRestClient discord = mock(DiscordRestClient.class);
    final IncidentThreadSender threads = mock(IncidentThreadSender.class);
    final OutboundPolicy outbound = mock(OutboundPolicy.class);
    final Clock clock = mock(Clock.class);
    final DiscordIncidentRulingSender sender = new DiscordIncidentRulingSender(discord, threads, outbound, clock);
    final String content = IncidentRulingProjection.render(reportId, "INC-CASE-TEST", "Test final ruling");
    IncidentThreadClaim proof;
    @BeforeEach void fixture() throws Exception {
        when(outbound.isEnabled()).thenReturn(true); when(clock.instant()).thenReturn(now);
        var work = new IncidentThreadClaim.Work(IncidentThreadStage.PUBLIC_THREAD, 0, "INC-CASE-TEST", "[CTC INC-CASE-TEST PUBLIC:0]",
                "Incident INC-CASE-TEST", "Test public starter", "Test public starter", "test-proof-nonce", "800", "800");
        proof = new IncidentThreadClaim(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, "runtime:0", until,
                new IncidentThreadClaim.Destination("100", "200", "600", List.of("500"), List.of(), Set.of("300", "400")), work);
        when(threads.verify(proof, "800")).thenReturn(new IncidentThreadResult(IncidentSendResult.Disposition.SENT, "800", "800", null));
        when(discord.fetchBotUser()).thenReturn(new BotUser("700", "test-bot", "0"));
        when(discord.fetchIncidentChannel("800")).thenReturn(channel(false));
        when(discord.listIncidentMessages("800", null, 100)).thenReturn(List.of());
        when(discord.createRulingMessage(eq("800"), anyString(), eq(content), eq(until))).thenReturn(message("900", content));
        when(discord.editBotMessage("800", "900", content, until)).thenReturn(message("900", content));
    }
    IncidentChannel channel(boolean archived) { return new IncidentChannel("800", "100", 11, "200", "Incident INC-CASE-TEST", null, 0, null, new IncidentThreadMetadata(archived, false, null), null); }
    BotMessage message(String id, String text) { return new BotMessage(id, "800", text, new BotUser("700", "test-bot", "0"), null); }
    IncidentRulingClaim claim(String previous) {
        return new IncidentRulingClaim(UUID.randomUUID(), reportId, proof.windowId(), UUID.randomUUID(), 1, "runtime:0", until,
                proof, content, previous, previous == null ? null : "900", IncidentRulingPublication.nonce(reportId, content));
    }
    void noRulingWrite() throws Exception { verify(discord, never()).createRulingMessage(any(), any(), any(), any()); verify(discord, never()).editBotMessage(any(), any(), any(), any()); }

    @Test void initialPublicationVerifiesTheStoredThreadAndCreatesOnlyTheApprovedSnapshot() throws Exception {
        var result = sender.send(claim(null));
        assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.SENT); assertThat(result.messageId()).isEqualTo("900");
        verify(threads).verify(proof, "800"); verify(discord).createRulingMessage("800", IncidentRulingPublication.nonce(reportId, content), content, until);
    }
    @Test void anExactExistingRulingIsAdoptedWithoutCreatingAnotherMessage() throws Exception {
        when(discord.listIncidentMessages("800", null, 100)).thenReturn(List.of(message("900", content)));
        var result = sender.send(claim(null));
        assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.SENT); assertThat(result.messageId()).isEqualTo("900"); noRulingWrite();
    }
    @Test void aCorrectionVerifiesAndEditsOnlyItsKnownMessage() throws Exception {
        String previous = IncidentRulingProjection.render(reportId, "INC-CASE-TEST", "Test prior ruling");
        when(discord.listIncidentMessages("800", null, 100)).thenReturn(List.of(message("900", previous)));
        when(discord.fetchBotMessage("800", "900")).thenReturn(message("900", previous));
        var result = sender.send(claim(previous));
        assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.SENT); assertThat(result.messageId()).isEqualTo("900");
        verify(discord).editBotMessage("800", "900", content, until); verify(discord, never()).createRulingMessage(any(), any(), any(), any());
    }
    @Test void anArchivedVerifiedTargetIsUnarchivedAndVerifiedAgainBeforePosting() throws Exception {
        when(discord.fetchIncidentChannel("800")).thenReturn(channel(true), channel(false));
        var result = sender.send(claim(null));
        assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.SENT);
        verify(discord).modifyChannel(eq("800"), any(org.ctc.discord.dto.ChannelModifyRequest.class), eq(until)); verify(threads, times(2)).verify(proof, "800");
    }
    @Test void aDifferentExistingBotRulingBlocksBlindPublicationFromAnOlderBackup() throws Exception {
        when(discord.listIncidentMessages("800", null, 100)).thenReturn(List.of(message("900", "Test different ruling\n[CTC " + reportId + " RULING]")));
        var result = sender.send(claim(null));
        assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.REJECTED); assertThat(result.code()).isEqualTo("CONFLICTING_RULING"); noRulingWrite();
    }
    @Test void multipleMatchingRulingsRemainUnresolved() throws Exception {
        when(discord.listIncidentMessages("800", null, 100)).thenReturn(List.of(message("901", content), message("900", content)));
        var result = sender.send(claim(null));
        assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.REJECTED); assertThat(result.code()).isEqualTo("AMBIGUOUS_RULING"); noRulingWrite();
    }
    @Test void expiredAuthorityAfterPreflightStopsBeforeAnyMutation() throws Exception {
        doAnswer(call -> { when(clock.instant()).thenReturn(until); return List.of(); }).when(discord).listIncidentMessages("800", null, 100);
        var result = sender.send(claim(null));
        assertThat(result.disposition()).isEqualTo(IncidentSendResult.Disposition.REJECTED); noRulingWrite();
    }
    @Test void disabledOutboundPolicyPreventsBothSendAndRecoveryReads() throws Exception {
        when(outbound.isEnabled()).thenReturn(false);
        assertThat(sender.send(claim(null)).disposition()).isEqualTo(IncidentSendResult.Disposition.REJECTED);
        assertThat(sender.verify(claim(null), "").disposition()).isEqualTo(IncidentSendResult.Disposition.REJECTED);
        verifyNoInteractions(discord, threads);
    }
    @Test void anUnverifiedStoredThreadCannotAuthorizeARulingPost() throws Exception {
        when(threads.verify(proof, "800")).thenReturn(new IncidentThreadResult(IncidentSendResult.Disposition.REJECTED, null, null, "VERIFICATION_FAILED"));
        assertThat(sender.send(claim(null)).disposition()).isEqualTo(IncidentSendResult.Disposition.REJECTED); noRulingWrite();
    }
    @Test void forgedSnapshotScopeIsRejectedBeforeAnyRemoteRead() throws Exception {
        var original = claim(null);
        var forged = new IncidentRulingClaim(original.id(), original.reportId(), original.windowId(), original.token(), original.version(), original.generation(),
                original.validUntil(), original.thread(), original.content(), null, null, "forged-nonce");
        assertThat(sender.send(forged).disposition()).isEqualTo(IncidentSendResult.Disposition.REJECTED);
        verifyNoInteractions(discord, threads);
    }
    @Test void aPostResponseFromAnotherAuthorLeavesTheOutcomeUncertain() throws Exception {
        doReturn(new BotMessage("900", "800", content, new BotUser("999", "test-other-user", "0"), null))
                .when(discord).createRulingMessage(eq("800"), anyString(), eq(content), eq(until));
        assertThat(sender.send(claim(null)).disposition()).isEqualTo(IncidentSendResult.Disposition.UNCERTAIN);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"duplicate", "cursor", "truncated"})
    void incompleteOrContradictoryInventoriesCannotProveRulingAbsence(String cause) throws Exception {
        var pages = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> {
            int page = pages.getAndIncrement();
            if (cause.equals("duplicate") && page > 0 || cause.equals("cursor") && page > 1 || page >= 10) return List.of();
            int start = cause.equals("cursor") && page == 1 ? 2100 : 2000 - page * 100;
            return java.util.stream.IntStream.range(0, 100).mapToObj(index -> message(cause.equals("duplicate") ? "2000" : Integer.toString(start - index), "Test discussion")).toList();
        }).when(discord).listIncidentMessages(eq("800"), nullable(String.class), eq(100));
        assertThat(sender.send(claim(null)).disposition()).isEqualTo(IncidentSendResult.Disposition.REJECTED); noRulingWrite();
    }

    @Test void recoveryProvesTheExactRequestedEditOrItsAbsenceWithoutPerformingTheEdit() throws Exception {
        String previous = IncidentRulingProjection.render(reportId, "INC-CASE-TEST", "Test prior ruling");
        when(discord.listIncidentMessages("800", null, 100)).thenReturn(List.of(message("900", previous)));
        when(discord.fetchBotMessage("800", "900")).thenReturn(message("900", previous));
        var missing = sender.verify(claim(previous), ""); assertThat(missing.code()).isEqualTo("PROVEN_MISSING");
        doReturn(List.of(message("900", content))).when(discord).listIncidentMessages("800", null, 100);
        doReturn(message("900", content)).when(discord).fetchBotMessage("800", "900");
        var sent = sender.verify(claim(previous), "900"); assertThat(sent.disposition()).isEqualTo(IncidentSendResult.Disposition.SENT); noRulingWrite();
    }
}
