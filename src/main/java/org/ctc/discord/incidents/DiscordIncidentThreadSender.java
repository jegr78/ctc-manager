package org.ctc.discord.incidents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.DiscordRestClient.*;
import org.ctc.discord.dto.ChannelModifyRequest;
import org.ctc.discord.exception.*;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.*;
import org.ctc.integration.OutboundPolicy;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiscordIncidentThreadSender implements IncidentThreadSender {
    private final DiscordRestClient discord;
    private final DiscordGlobalConfigRepository configs;
    private final OutboundPolicy outbound;

    @Override public IncidentThreadResult send(IncidentThreadClaim claim) {
        if (!outbound.isEnabled()) return rejected("OUTBOUND_DISABLED");
        boolean posting = false;
        try {
            var prepared = prepare(claim);var work = claim.work();var target = claim.destination();
            String parent = parent(claim);
            switch (work.stage()) {
                case PRIVATE_STARTER, PUBLIC_STARTER -> {
                    posting = true;
                    if (privateSide(claim) && prepared.parent().type() == 15) {
                        var thread = discord.startIncidentForumThread(parent, work.title(), work.content(), target.forumTags());
                        requireThread(claim, thread, null);
                        requireMessage(thread.message(), thread.id(), null, work.content(), prepared.botId(), null);
                        return sent(thread.message().id(), thread.id());
                    }
                    var message = discord.createBotMessage(parent, work.nonce(), work.content());
                    requireMessage(message, parent, null, work.content(), prepared.botId(), work.nonce());
                    return sent(message.id(), null);
                }
                case PRIVATE_THREAD, PUBLIC_THREAD -> {
                    require(prepared.parent().type() == 0);
                    verifyStarter(claim, prepared.botId(), null);
                    try {
                        var existing = discord.fetchIncidentChannel(work.starterId());requireThread(claim, existing, work.starterId());
                        return sent(work.starterId(), existing.id());
                    } catch (DiscordNotFoundException missing) {
                        posting = true;
                        var created = discord.startIncidentMessageThread(parent, work.starterId(), work.title());
                        requireThread(claim, created, work.starterId());
                        return sent(work.starterId(), created.id());
                    }
                }
                case PRIVATE_BODY, PUBLIC_BODY, PRIVATE_REFERENCE -> {
                    var thread = discord.fetchIncidentChannel(work.threadId());requireThread(claim, thread, work.threadId());
                    verifyStarter(claim, prepared.botId(), prepared.parent().type() == 15 ? thread.id() : null);
                    require(!thread.threadMetadata().locked());
                    if (thread.threadMetadata().archived()) {
                        discord.modifyChannel(thread.id(), ChannelModifyRequest.unarchive());
                        thread = discord.fetchIncidentChannel(work.threadId());requireThread(claim, thread, work.threadId());
                        require(!thread.threadMetadata().archived() && !thread.threadMetadata().locked());
                        prepared = prepare(claim);
                    }
                    posting = true;
                    var message = discord.createBotMessage(thread.id(), work.nonce(), work.content());
                    requireMessage(message, thread.id(), null, work.content(), prepared.botId(), work.nonce());
                    return sent(message.id(), null);
                }
                default -> {return rejected("INVALID_CLAIM");}
            }
        } catch (DiscordApiException failure) {
            return posting && !confirmedRejection(failure) ? uncertain("SEND_UNCERTAIN") : rejected("DESTINATION");
        } catch (RuntimeException failure) {
            return posting ? uncertain("INVALID_RESPONSE") : rejected("DESTINATION");
        }
    }

    @Override public IncidentThreadResult verify(IncidentThreadClaim claim, String remoteId) {
        if (!outbound.isEnabled()) return rejected("OUTBOUND_DISABLED");
        try {
            var prepared = prepare(claim);var work = claim.work();
            if (remoteId == null || remoteId.isEmpty()) return verifyMissing(claim, prepared);
            require(snowflake(remoteId));
            switch (work.stage()) {
                case PRIVATE_STARTER -> {
                    if (prepared.parent().type() == 15) {
                        var thread = discord.fetchIncidentChannel(remoteId);requireThread(claim, thread, remoteId);
                        var matches = matchingMessages(thread.id(), work.content(), prepared.botId());
                        require(matches.size() == 1);
                        return sent(matches.getFirst().id(), thread.id());
                    }
                    var message = discord.fetchBotMessage(parent(claim), remoteId);
                    requireMessage(message, parent(claim), remoteId, work.content(), prepared.botId(), work.nonce());
                    return sent(message.id(), null);
                }
                case PUBLIC_STARTER -> {
                    var message = discord.fetchBotMessage(parent(claim), remoteId);
                    requireMessage(message, parent(claim), remoteId, work.content(), prepared.botId(), work.nonce());
                    return sent(message.id(), null);
                }
                case PRIVATE_THREAD, PUBLIC_THREAD -> {
                    boolean forum=privateSide(claim) && prepared.parent().type()==15;
                    require(remoteId.equals(forum?work.threadId():work.starterId()));
                    var thread = discord.fetchIncidentChannel(remoteId);requireThread(claim, thread, remoteId);
                    verifyStarter(claim, prepared.botId(), forum?thread.id():null);
                    return sent(work.starterId(), remoteId);
                }
                case PRIVATE_BODY, PUBLIC_BODY, PRIVATE_REFERENCE -> {
                    var thread = discord.fetchIncidentChannel(work.threadId());requireThread(claim, thread, work.threadId());
                    verifyStarter(claim, prepared.botId(), prepared.parent().type() == 15 ? thread.id() : null);
                    var message = discord.fetchBotMessage(thread.id(), remoteId);
                    requireMessage(message, thread.id(), remoteId, work.content(), prepared.botId(), work.nonce());
                    return sent(message.id(), null);
                }
                default -> {return rejected("VERIFICATION_FAILED");}
            }
        } catch (DiscordApiException | RuntimeException failure) {return rejected("VERIFICATION_FAILED");}
    }

    private Prepared prepare(IncidentThreadClaim claim) throws DiscordApiException {
        require(claim != null && claim.destination() != null && claim.work() != null);
        var target = claim.destination();var work = claim.work();var config = configs.findFirstByOrderByIdAsc();
        require(config != null && target.guildId().equals(config.getGuildId()) && target.stewardsChannelId().equals(config.getStewardsChannelId())
                && String.join(",", target.staffRoles()).equals(config.getIncidentStaffRoleIds())
                && String.join(",", target.forumTags()).equals(config.getStewardsForumTagIds()));
        require(!target.matchChannelId().equals(target.stewardsChannelId()) && work.stage() != null
                && work.title() != null && !work.title().isBlank() && work.title().length() <= 100
                && work.content() != null && !work.content().isBlank() && work.content().length() <= 2000
                && work.starterContent() != null && work.nonce() != null && work.nonce().matches("[A-Za-z0-9_-]{1,25}"));
        var bot = discord.fetchBotUser();require(bot != null && snowflake(bot.id()));
        var guild = discord.fetchAccessGuild(target.guildId());var member = discord.fetchAccessMember(target.guildId(), bot.id());
        var roles = discord.fetchIncidentRoles(target.guildId());var stewards = discord.fetchIncidentChannel(target.stewardsChannelId());
        IncidentThreadPermissions.requirePrivate(guild, stewards, member, roles, bot.id(), target.guildId(), target.stewardsChannelId(),
                new HashSet<>(target.staffRoles()), target.participantRoles(), target.forumTags());
        if (privateSide(claim)) return new Prepared(bot.id(), stewards);
        var match = discord.fetchIncidentChannel(target.matchChannelId());
        IncidentThreadPermissions.requirePublic(guild, match, member, roles, bot.id(), target.guildId(), target.matchChannelId());
        return new Prepared(bot.id(), match);
    }

    private void verifyStarter(IncidentThreadClaim claim, String botId, String forumThread) throws DiscordApiException {
        var work = claim.work();require(snowflake(work.starterId()));
        String channel = forumThread == null ? parent(claim) : forumThread;
        var message = discord.fetchBotMessage(channel, work.starterId());
        requireMessage(message, channel, work.starterId(), work.starterContent(), botId, null);
    }

    private IncidentThreadResult verifyMissing(IncidentThreadClaim claim, Prepared prepared) throws DiscordApiException {
        var work = claim.work();
        switch (work.stage()) {
            case PRIVATE_THREAD, PUBLIC_THREAD -> {
                verifyStarter(claim, prepared.botId(), null);
                try {
                    var existing = discord.fetchIncidentChannel(work.starterId());requireThread(claim, existing, work.starterId());
                    return sent(work.starterId(), existing.id());
                } catch (DiscordNotFoundException missing) {return rejected("PROVEN_MISSING");}
            }
            case PRIVATE_BODY, PUBLIC_BODY, PRIVATE_REFERENCE -> {
                var thread = discord.fetchIncidentChannel(work.threadId());requireThread(claim, thread, work.threadId());
                verifyStarter(claim, prepared.botId(), prepared.parent().type() == 15 ? thread.id() : null);
                var matches = matchingMessages(thread.id(), work.content(), prepared.botId());
                require(matches.size() <= 1);
                return matches.isEmpty() ? rejected("PROVEN_MISSING") : sent(matches.getFirst().id(), null);
            }
            case PRIVATE_STARTER, PUBLIC_STARTER -> {
                if (prepared.parent().type() == 15) return uncertain("FORUM_REVIEW_REQUIRED");
                var matches = matchingMessages(parent(claim), work.content(), prepared.botId());
                require(matches.size() <= 1);
                return matches.isEmpty() ? rejected("PROVEN_MISSING") : sent(matches.getFirst().id(), null);
            }
            default -> {return rejected("VERIFICATION_FAILED");}
        }
    }

    private List<BotMessage> matchingMessages(String channelId, String content, String botId) throws DiscordApiException {
        var matches = new ArrayList<BotMessage>();var seen = new HashSet<String>();String before = null;
        for (int page = 0; page < 10; page++) {
            var messages = discord.listIncidentMessages(channelId, before, 100);require(messages != null && messages.size() <= 100);
            for (var message : messages) {
                require(message != null && snowflake(message.id()) && channelId.equals(message.channelId()) && seen.add(message.id()));
                if (content.equals(message.content()) && message.author() != null && botId.equals(message.author().id())) matches.add(message);
            }
            if (messages.size() < 100) return List.copyOf(matches);
            String next = messages.getLast().id();require(!Objects.equals(next, before));before = next;
        }
        throw unavailable();
    }

    private static void requireThread(IncidentThreadClaim claim, IncidentChannel thread, String expectedId) {
        require(thread != null && snowflake(thread.id()) && (expectedId == null || expectedId.equals(thread.id()))
                && claim.destination().guildId().equals(thread.guildId()) && parent(claim).equals(thread.parentId())
                && Integer.valueOf(11).equals(thread.type()) && claim.work().title().equals(thread.name())
                && thread.threadMetadata() != null && thread.threadMetadata().archived() != null && thread.threadMetadata().locked() != null);
    }
    private static void requireMessage(BotMessage message, String channel, String id, String content, String botId, String nonce) {
        require(message != null && snowflake(message.id()) && (id == null || id.equals(message.id())) && channel.equals(message.channelId())
                && content.equals(message.content()) && message.author() != null && botId.equals(message.author().id())
                && (nonce == null || message.nonce() == null || nonce.equals(message.nonce())));
    }
    private static boolean privateSide(IncidentThreadClaim claim) {
        return switch (claim.work().stage()) {case PUBLIC_STARTER, PUBLIC_THREAD, PUBLIC_BODY -> false;default -> true;};
    }
    private static String parent(IncidentThreadClaim claim) {return privateSide(claim) ? claim.destination().stewardsChannelId() : claim.destination().matchChannelId();}
    private static boolean confirmedRejection(DiscordApiException failure) {
        return failure.category() != DiscordApiException.Category.TRANSIENT
                || failure instanceof DiscordTransientException transientFailure && transientFailure.isConfirmedRejection();
    }
    private static boolean snowflake(String value) {return value != null && value.matches("[0-9]{1,20}");}
    private static void require(boolean condition) {if (!condition) throw unavailable();}
    private static BusinessRuleException unavailable() {return new BusinessRuleException("Current incident destination or remote evidence could not be verified");}
    private static IncidentThreadResult sent(String message, String thread) {return new IncidentThreadResult(IncidentSendResult.Disposition.SENT, message, thread, null);}
    private static IncidentThreadResult rejected(String code) {return new IncidentThreadResult(IncidentSendResult.Disposition.REJECTED, null, null, code);}
    private static IncidentThreadResult uncertain(String code) {return new IncidentThreadResult(IncidentSendResult.Disposition.UNCERTAIN, null, null, code);}
    private record Prepared(String botId, IncidentChannel parent) { }
}
