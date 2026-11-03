package org.ctc.discord.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ctc.discord.DiscordWebhookClient;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PickPublicationAttempt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PickPublicationEvidenceService {
    private final DiscordWebhookClient client;
    private final ObjectMapper mapper;
    public void verify(String webhookUrl, String messageId, String webhookId, String expectedChannelId,
                       String manifest, String requestHash) throws DiscordApiException {
        if (!PickPublicationAttempt.hashMatches(PickPublicationAttempt.manifestHash(manifest), requestHash)) throw mismatch();
        var captured = PickPublicationManifest.read(manifest, mapper);
        var message = client.getMessageEvidence(webhookUrl, messageId);
        if (message == null || !messageId.equals(message.id()) || !webhookId.equals(message.webhookId())
                || expectedChannelId != null && !expectedChannelId.equals(message.channelId())
                || (message.flags() & (64 | 128)) != 0
                || !PickPublicationAttempt.hashMatches(captured.contentHash(), PickPublicationAttempt.manifestHash(message.content() == null ? "" : message.content()))
                || message.attachments().size() != captured.attachments().size()) throw mismatch();
        var names = new java.util.HashSet<String>();
        for (var attachment : message.attachments()) {
            if (!names.add(attachment.filename())) throw mismatch();
            var expected = captured.attachments().stream().filter(file -> file.filename().equals(attachment.filename())).findFirst().orElseThrow(PickPublicationEvidenceService::mismatch);
            if (attachment.size() != expected.bytes() || attachment.url() == null) throw mismatch();
            var bytes = client.downloadAttachment(attachment.url(), expected.bytes());
            if (bytes == null || bytes.length != expected.bytes() || !PickPublicationAttempt.hashMatches(PickPublicationManifest.hash(bytes), expected.sha256())) throw mismatch();
        }
    }

    private static BusinessRuleException mismatch() {
        return new BusinessRuleException("The Discord message does not prove this captured publication; its outcome remains pending");
    }
}
