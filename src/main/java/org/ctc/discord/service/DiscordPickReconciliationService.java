package org.ctc.discord.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.ctc.discord.DiscordHostValidator;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PickPublicationAttempt;
import org.ctc.domain.model.PickPublicationOutput;
import org.ctc.domain.picks.PreparedPickReconciliation;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DiscordPickReconciliationService {
    private final PickPublicationEvidenceService evidence;
    private final DiscordPostRepository posts;
    private final DiscordGlobalConfigService config;
    private final DiscordHostValidator hosts;
    private final Clock clock;

    public PreparedPickReconciliation<DiscordPost> prepare(List<PickPublicationAttempt> rows, String candidate) {
        var first = rows.getFirst();
        var match = first.getRace().getMatch();
        if (match == null || candidate == null || !candidate.matches("[0-9]{1,20}")) throw changed();
        for (var row : rows) {
            if (!row.matchesTarget(row.getRace()) || row.getRace().getMatch() == null || !match.getId().equals(row.getRace().getMatch().getId())
                    || !first.getRequestHash().equals(row.getRequestHash()) || !first.getWebhookId().equals(row.getWebhookId())
                    || !first.getChannelId().equals(row.getChannelId()) || first.getOutput() != row.getOutput()
                    || !Objects.equals(first.getTargetMessageId(), row.getTargetMessageId())
                    || row.getTargetMessageId() != null && !candidate.equals(row.getTargetMessageId())
                    || row.getMessageId() != null && !candidate.equals(row.getMessageId())) throw changed();
        }
        boolean preview = first.getOutput() == PickPublicationOutput.MATCH_PREVIEW;
        String url = preview ? config.getOrInitialize().getAnnouncementWebhookUrl() : match.getDiscordChannelWebhookUrl();
        if (url == null || url.isBlank()) throw new BusinessRuleException("Restore the matching webhook configuration before verifying this publication, or retire the uncertain remote output");
        hosts.requireAllowed(url);
        var credentials = DiscordPostService.parseWebhookUrl(url);
        if (!first.getWebhookId().equals(credentials.id()) || !preview && !first.getChannelId().equals(match.getDiscordChannelId())) throw changed();
        var type = type(first.getOutput()); var matchId = match.getId(); var channel = first.getChannelId();
        var existing = posts.findByChannelIdAndPostTypeAndMatchId(channel, type, matchId).orElse(null);
        if (existing != null && (!Objects.equals(first.getTargetMessageId(), existing.getMessageId()) || !first.getWebhookId().equals(existing.getWebhookId()))) throw changed();
        Long previousId = existing == null ? null : existing.getId();
        String previousMessage = existing == null ? null : existing.getMessageId();
        String manifest = first.getRequestManifest(), requestHash = first.getRequestHash(), webhook = first.getWebhookId();
        return new PreparedPickReconciliation<>(() -> {
            evidence.verify(url, candidate, webhook, preview ? null : channel, manifest, requestHash);
            return candidate;
        }, message -> {
            var current = posts.findByChannelIdAndPostTypeAndMatchId(channel, type, matchId).orElse(null);
            if (previousId == null ? current != null : current == null || !previousId.equals(current.getId()) || !Objects.equals(previousMessage, current.getMessageId())) throw changed();
            var row = current == null ? new DiscordPost() : current;
            if (current == null) row.setPostedAt(LocalDateTime.now(clock));
            row.setChannelId(channel); row.setWebhookId(webhook); row.setWebhookToken(credentials.token());
            row.setMessageId(message); row.setMatchId(matchId); row.setPostType(type); row.setAttachmentsReplacedAt(LocalDateTime.now(clock));
            return posts.saveAndFlush(row);
        });
    }

    public void quarantine(List<PickPublicationAttempt> rows) {
        for (var attempt : rows) {
            if (attempt.getTargetMessageId() == null && attempt.getMessageId() == null) continue;
            var matching = posts.findAll().stream().filter(post -> attempt.getWebhookId().equals(post.getWebhookId())
                    && (post.getMessageId().equals(attempt.getTargetMessageId()) || post.getMessageId().equals(attempt.getMessageId()))).toList();
            posts.deleteAll(matching);
        }
        posts.flush();
    }

    private static DiscordPostType type(PickPublicationOutput output) {
        return switch (output) { case SETTINGS -> DiscordPostType.SETTINGS; case LOBBY_SETTINGS -> DiscordPostType.LOBBY_SETTINGS; case MATCH_PREVIEW -> DiscordPostType.MATCH_PREVIEW; };
    }
    private static BusinessRuleException changed() {
        return new BusinessRuleException("Publication target or mapping changed; reload the review and verify the original physical output");
    }
}
