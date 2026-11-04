package org.ctc.discord.incidents;

import java.math.BigInteger;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.DiscordRestClient.BotMessage;
import org.ctc.discord.DiscordRestClient.IncidentChannel;
import org.ctc.discord.dto.ChannelModifyRequest;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordTransientException;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.IncidentRulingPublication;
import org.ctc.domain.model.IncidentThreadStage;
import org.ctc.integration.OutboundPolicy;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiscordIncidentRulingSender implements IncidentRulingSender {
    private final DiscordRestClient discord;
    private final IncidentThreadSender threads;
    private final OutboundPolicy outbound;
    private final Clock clock;

    @Override public IncidentSendResult send(IncidentRulingClaim claim) {
        if (!outbound.isEnabled()) return rejected("OUTBOUND_DISABLED");
        boolean posting = false;
        try {
            requireLive(claim); var prepared = prepare(claim);
            var existing = inspect(claim, prepared.botId(), "");
            if (!"PROVEN_MISSING".equals(existing.code())) return existing;
            if (prepared.channel().threadMetadata().locked()) return rejected("TARGET_LOCKED");
            if (prepared.channel().threadMetadata().archived()) {
                requireLive(claim); discord.modifyChannel(channel(claim), ChannelModifyRequest.unarchive(), claim.validUntil());
                prepared = prepare(claim);
                if (prepared.channel().threadMetadata().archived() || prepared.channel().threadMetadata().locked()) return rejected("TARGET_LOCKED");
                existing = inspect(claim, prepared.botId(), "");
                if (!"PROVEN_MISSING".equals(existing.code())) return existing;
            }
            requireLive(claim); posting = true;
            var message = claim.previousMessageId() == null
                    ? discord.createRulingMessage(channel(claim), claim.nonce(), claim.content(), claim.validUntil())
                    : discord.editBotMessage(channel(claim), claim.previousMessageId(), claim.content(), claim.validUntil());
            requireMessage(message, claim, prepared.botId(), claim.content()); requireLive(claim);
            return sent(message.id());
        } catch (DiscordApiException failure) {
            return posting && !confirmedRejection(failure) ? uncertain() : rejected("DESTINATION");
        } catch (RuntimeException failure) { return posting ? uncertain() : rejected("DESTINATION"); }
    }

    @Override public IncidentSendResult verify(IncidentRulingClaim claim, String remoteId) {
        if (!outbound.isEnabled()) return rejected("OUTBOUND_DISABLED");
        try {
            requireLive(claim); var prepared = prepare(claim);
            if (remoteId == null || !remoteId.isEmpty() && !snowflake(remoteId)) return rejected("VERIFICATION_FAILED");
            var result = inspect(claim, prepared.botId(), remoteId); requireLive(claim); return result;
        } catch (DiscordApiException | RuntimeException failure) { return rejected("VERIFICATION_FAILED"); }
    }

    private Prepared prepare(IncidentRulingClaim claim) throws DiscordApiException {
        if (claim == null || claim.reportId() == null || claim.thread() == null || claim.thread().work() == null
                || claim.thread().destination() == null || claim.content() == null) throw unavailable();
        var proof = claim.thread(); var work = proof.work();
        if (work.stage() != IncidentThreadStage.PUBLIC_THREAD || !snowflake(work.threadId())
                || !work.threadId().equals(work.starterId()) || !claim.windowId().equals(proof.windowId())
                || !claim.validUntil().equals(proof.validUntil()) || !claim.generation().equals(proof.generation())
                || claim.content().length() > 2000 || !claim.content().startsWith("Ruling " + work.caseReference() + "\n\n")
                || !claim.content().endsWith("\n" + claim.marker())
                || !IncidentRulingPublication.nonce(claim.reportId(), claim.content()).equals(claim.nonce())) throw unavailable();
        if (claim.previousMessageId() == null ? claim.previousContent() != null
                : !snowflake(claim.previousMessageId()) || claim.previousContent() == null || claim.previousContent().length() > 2000
                        || !claim.previousContent().endsWith("\n" + claim.marker())) throw unavailable();
        var verified = threads.verify(proof, work.threadId());
        if (verified == null || verified.disposition() != IncidentSendResult.Disposition.SENT
                || !work.threadId().equals(verified.messageId()) || !work.threadId().equals(verified.threadId())) throw unavailable();
        var bot = discord.fetchBotUser();
        if (bot == null || !snowflake(bot.id())) throw unavailable();
        var thread = discord.fetchIncidentChannel(work.threadId());
        if (thread == null || !work.threadId().equals(thread.id()) || !proof.destination().guildId().equals(thread.guildId())
                || !proof.destination().matchChannelId().equals(thread.parentId()) || !Integer.valueOf(11).equals(thread.type())
                || !work.title().equals(thread.name()) || thread.threadMetadata() == null
                || thread.threadMetadata().archived() == null || thread.threadMetadata().locked() == null) throw unavailable();
        return new Prepared(bot.id(), thread);
    }

    private IncidentSendResult inspect(IncidentRulingClaim claim, String botId, String remoteId) throws DiscordApiException {
        var matches = matching(claim, botId);
        if (matches.size() > 1) return rejected("AMBIGUOUS_RULING");
        if (claim.previousMessageId() == null) {
            if (matches.isEmpty()) return rejected(remoteId.isEmpty() ? "PROVEN_MISSING" : "VERIFICATION_FAILED");
            var message = matches.getFirst();
            if (!claim.content().equals(message.content())) return rejected("CONFLICTING_RULING");
            if (!remoteId.isEmpty() && !remoteId.equals(message.id())) return rejected("VERIFICATION_FAILED");
            requireMessage(message, claim, botId, claim.content()); return sent(message.id());
        }
        if (matches.size() != 1 || !claim.previousMessageId().equals(matches.getFirst().id())
                || !remoteId.isEmpty() && !claim.previousMessageId().equals(remoteId)) return rejected("CONFLICTING_RULING");
        var message = discord.fetchBotMessage(channel(claim), claim.previousMessageId());
        if (message == null) throw unavailable();
        if (claim.content().equals(message.content())) {
            requireMessage(message, claim, botId, claim.content()); return sent(message.id());
        }
        requireMessage(message, claim, botId, claim.previousContent());
        return rejected(remoteId.isEmpty() ? "PROVEN_MISSING" : "VERIFICATION_FAILED");
    }

    private List<BotMessage> matching(IncidentRulingClaim claim, String botId) throws DiscordApiException {
        var matches = new ArrayList<BotMessage>(); var seen = new HashSet<String>(); String before = null;
        for (int page = 0; page < 10; page++) {
            var messages = discord.listIncidentMessages(channel(claim), before, 100);
            if (messages == null || messages.size() > 100) throw unavailable();
            for (var message : messages) {
                if (message == null || !snowflake(message.id()) || !channel(claim).equals(message.channelId()) || !seen.add(message.id())) throw unavailable();
                if (message.author() != null && botId.equals(message.author().id()) && message.content() != null
                        && message.content().endsWith("\n" + claim.marker())) matches.add(message);
            }
            if (messages.size() < 100) return List.copyOf(matches);
            String last = messages.getLast().id();
            if (before != null && new BigInteger(last).compareTo(new BigInteger(before)) >= 0) throw unavailable();
            before = last;
        }
        throw unavailable();
    }
    private static void requireMessage(BotMessage message, IncidentRulingClaim claim, String botId, String expected) {
        if (message == null || !snowflake(message.id()) || !channel(claim).equals(message.channelId())
                || !expected.equals(message.content()) || message.author() == null || !botId.equals(message.author().id())
                || claim.previousMessageId() != null && !claim.previousMessageId().equals(message.id())
                || message.nonce() != null && !claim.nonce().equals(message.nonce()) && claim.previousMessageId() == null) throw unavailable();
    }
    private void requireLive(IncidentRulingClaim claim) {
        if (claim == null || claim.validUntil() == null || !clock.instant().isBefore(claim.validUntil())) throw unavailable();
    }
    private static String channel(IncidentRulingClaim claim) { return claim.thread().work().threadId(); }
    private static boolean snowflake(String value) { return value != null && value.matches("[0-9]{1,20}"); }
    private static boolean confirmedRejection(DiscordApiException failure) {
        return failure.category() != DiscordApiException.Category.TRANSIENT
                || failure instanceof DiscordTransientException transientFailure && transientFailure.isConfirmedRejection();
    }
    private static IncidentSendResult sent(String id) { return new IncidentSendResult(IncidentSendResult.Disposition.SENT, id, null); }
    private static IncidentSendResult rejected(String code) { return new IncidentSendResult(IncidentSendResult.Disposition.REJECTED, null, code); }
    private static IncidentSendResult uncertain() { return new IncidentSendResult(IncidentSendResult.Disposition.UNCERTAIN, null, "SEND_UNCERTAIN"); }
    private static BusinessRuleException unavailable() { return new BusinessRuleException("The known incident ruling target could not be verified"); }
    private record Prepared(String botId, IncidentChannel channel) { }
}
