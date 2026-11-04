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
    private final java.time.Clock clock;

    @Override public IncidentThreadResult send(IncidentThreadClaim claim) {
        if (!outbound.isEnabled()) return rejected("OUTBOUND_DISABLED");
        boolean posting = false;
        try {
            requireLive(claim);
            var prepared = prepare(claim);var work = claim.work();var target = claim.destination();
            String parent = parent(claim);
            switch (work.stage()) {
                case PRIVATE_STARTER, PUBLIC_STARTER -> {
                    requireLive(claim);posting = true;
                    if (privateSide(claim) && prepared.parent().type() == 15) {
                        var thread = discord.startIncidentForumThread(parent, work.title(), work.content(), target.forumTags(),claim.validUntil());
                        requireThread(claim, thread, null);
                        requireMessage(thread.message(), thread.id(), null, work.content(), prepared.botId(), null);
                        return sent(thread.message().id(), thread.id());
                    }
                    var message = discord.createBotMessage(parent, work.nonce(), work.content(),claim.validUntil());
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
                        requireLive(claim);posting = true;
                        var created = discord.startIncidentMessageThread(parent, work.starterId(), work.title(),claim.validUntil());
                        requireThread(claim, created, work.starterId());
                        return sent(work.starterId(), created.id());
                    }
                }
                case PRIVATE_BODY, PUBLIC_BODY, PRIVATE_REFERENCE -> {
                    var thread = discord.fetchIncidentChannel(work.threadId());requireThread(claim, thread, work.threadId());
                    verifyStarter(claim, prepared.botId(), prepared.parent().type() == 15 ? thread.id() : null);
                    require(!thread.threadMetadata().locked());
                    if (thread.threadMetadata().archived()) {
                        requireLive(claim);discord.modifyChannel(thread.id(), ChannelModifyRequest.unarchive(),claim.validUntil());
                        thread = discord.fetchIncidentChannel(work.threadId());requireThread(claim, thread, work.threadId());
                        require(!thread.threadMetadata().archived() && !thread.threadMetadata().locked());
                        prepared = prepare(claim);
                    }
                    requireLive(claim);posting = true;
                    var message = discord.createBotMessage(thread.id(), work.nonce(), work.content(),claim.validUntil());
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
                        var matches = matchingMessages(thread.id(), work.content(), prepared.botId(),work.marker());
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

    private void requireLive(IncidentThreadClaim claim) {
        require(claim!=null && claim.validUntil()!=null && clock.instant().isBefore(claim.validUntil()));
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
                var matches = matchingMessages(thread.id(), work.content(), prepared.botId(),work.marker());
                require(matches.size() <= 1);
                return matches.isEmpty() ? rejected("PROVEN_MISSING") : sent(matches.getFirst().id(), null);
            }
            case PRIVATE_STARTER, PUBLIC_STARTER -> {
                return reconcileStarter(claim,prepared);
            }
            default -> {return rejected("VERIFICATION_FAILED");}
        }
    }

    private IncidentThreadResult reconcileStarter(IncidentThreadClaim claim,Prepared prepared) throws DiscordApiException {
        var work=claim.work();String parent=parent(claim);boolean forum=prepared.parent().type()==15;
        var matches=new java.util.LinkedHashMap<String,IncidentThreadResult>();
        if(!forum)for(var message:matchingMessages(parent,work.content(),prepared.botId(),work.marker()))matches.put(message.id(),sent(message.id(),null));
        for(String id:inventory(claim.destination().guildId(),parent)) {
            var thread=discord.fetchIncidentChannel(id);requireInventoryThread(claim,thread,parent,id);
            if(forum) {
                var messages=matchingMessages(id,work.content(),prepared.botId(),work.marker());
                require(messages.size()<=1);
                if(hasCaseTitle(claim,thread) && messages.isEmpty())throw unavailable();
                for(var message:messages) {requireThread(claim,thread,id);matches.put(id,sent(message.id(),id));}
            } else {
                var source=discord.fetchBotMessage(parent,id);
                require(source!=null && id.equals(source.id()) && parent.equals(source.channelId()));
                boolean bot=source.author()!=null && prepared.botId().equals(source.author().id());
                if(bot && source.content()!=null && source.content().endsWith("\n"+work.marker()) && !work.content().equals(source.content()))throw unavailable();
                if(bot && work.content().equals(source.content())) {
                    requireThread(claim,thread,id);
                    matches.put(id,sent(id,privateSide(claim)?id:null));
                } else if(hasCaseTitle(claim,thread))throw unavailable();
            }
        }
        require(matches.size()<=1);
        if(!matches.isEmpty())return matches.values().iterator().next();
        if(work.stage()==org.ctc.domain.model.IncidentThreadStage.PRIVATE_STARTER)requireNoLaterPublicCase(claim,prepared.botId());
        return rejected("PROVEN_MISSING");
    }

    private void requireNoLaterPublicCase(IncidentThreadClaim claim,String botId) throws DiscordApiException {
        var target=claim.destination();var guild=discord.fetchAccessGuild(target.guildId());var member=discord.fetchAccessMember(target.guildId(),botId);
        var roles=discord.fetchIncidentRoles(target.guildId());var channel=discord.fetchIncidentChannel(target.matchChannelId());
        IncidentThreadPermissions.requirePublic(guild,channel,member,roles,botId,target.guildId(),target.matchChannelId());
        scanMessages(target.matchChannelId(),message -> {
            if(message.author()!=null && botId.equals(message.author().id()) && hasCaseMarker(message.content(),claim.work().caseReference()))throw unavailable();
        });
        for(String id:inventory(target.guildId(),target.matchChannelId())) {
            var thread=discord.fetchIncidentChannel(id);requireInventoryThread(claim,thread,target.matchChannelId(),id);
            var source=discord.fetchBotMessage(target.matchChannelId(),id);
            require(source!=null && id.equals(source.id()) && target.matchChannelId().equals(source.channelId()));
            boolean marker=hasCaseMarker(source.content(),claim.work().caseReference());
            if(hasCaseTitle(claim,thread) || source.author()!=null && botId.equals(source.author().id()) && marker)throw unavailable();
        }
    }

    private java.util.Set<String> inventory(String guildId,String parentId) throws DiscordApiException {
        var ids=new java.util.LinkedHashSet<String>();collectActive(ids,guildId,parentId);java.time.Instant previous=null;String before=null;
        for(int page=0;page<10;page++) {
            var archived=discord.listIncidentArchivedThreads(parentId,before,100);
            require(archived!=null && archived.threads()!=null && archived.hasMore()!=null && archived.threads().size()<=100);
            var seen=new HashSet<String>();
            for(var thread:archived.threads()) {
                require(thread!=null && snowflake(thread.id()) && parentId.equals(thread.parentId()) && seen.add(thread.id()));
                ids.add(thread.id());require(ids.size()<=50);
            }
            if(!archived.hasMore()) {collectActive(ids,guildId,parentId);return java.util.Set.copyOf(ids);}
            require(!archived.threads().isEmpty());var last=archived.threads().getLast();
            require(last.threadMetadata()!=null && last.threadMetadata().archiveTimestamp()!=null);
            var cursor=java.time.Instant.parse(last.threadMetadata().archiveTimestamp());
            require(previous==null || cursor.isBefore(previous));
            require(archived.threads().stream().filter(thread -> thread.threadMetadata()!=null && last.threadMetadata().archiveTimestamp().equals(thread.threadMetadata().archiveTimestamp())).count()==1);
            previous=cursor;before=cursor.toString();
        }
        throw unavailable();
    }
    private void collectActive(java.util.Set<String> ids,String guildId,String parentId) throws DiscordApiException {
        var active=discord.listIncidentActiveThreads(guildId);require(active!=null && active.threads()!=null && active.threads().size()<=500);
        for(var thread:active.threads()) {
            require(thread!=null && snowflake(thread.id()) && thread.parentId()!=null);
            if(parentId.equals(thread.parentId()))ids.add(thread.id());require(ids.size()<=50);
        }
    }
    private static void requireInventoryThread(IncidentThreadClaim claim,IncidentChannel thread,String parent,String id) {
        require(thread!=null && id.equals(thread.id()) && claim.destination().guildId().equals(thread.guildId()) && parent.equals(thread.parentId()) && Integer.valueOf(11).equals(thread.type()));
    }
    private static boolean hasCaseTitle(IncidentThreadClaim claim,IncidentChannel thread) {return thread.name()!=null && thread.name().contains("Incident "+claim.work().caseReference());}

    private static boolean hasCaseMarker(String content,String reference) {
        int line=content==null?-1:content.lastIndexOf('\n');
        return line>=0 && content.substring(line+1).startsWith("[CTC "+reference+" ");
    }
    private List<BotMessage> matchingMessages(String channelId,String content,String botId,String marker) throws DiscordApiException {
        var matches=new ArrayList<BotMessage>();
        scanMessages(channelId,message -> {
            if(message.author()!=null && botId.equals(message.author().id())) {
                require(message.content()==null || !message.content().endsWith("\n"+marker) || content.equals(message.content()));
                if(content.equals(message.content()))matches.add(message);
            }
        });
        return List.copyOf(matches);
    }
    private void scanMessages(String channelId,java.util.function.Consumer<BotMessage> consumer) throws DiscordApiException {
        var seen=new HashSet<String>();String before=null;
        for(int page=0;page<10;page++) {
            var messages=discord.listIncidentMessages(channelId,before,100);require(messages!=null && messages.size()<=100);
            for(var message:messages) {
                require(message!=null && snowflake(message.id()) && channelId.equals(message.channelId()) && seen.add(message.id()));
                consumer.accept(message);
            }
            if(messages.size()<100)return;
            String next=messages.getLast().id();require(!Objects.equals(next,before));before=next;
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
