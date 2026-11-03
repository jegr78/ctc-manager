package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookMessageEvidence(String id, @JsonProperty("channel_id") String channelId,
        @JsonProperty("webhook_id") String webhookId, String content, int flags, List<Attachment> attachments) {
    public WebhookMessageEvidence { attachments = attachments == null ? List.of() : List.copyOf(attachments); }
    @Override public String toString() { return "WebhookMessageEvidence[internal]"; }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attachment(String id, String filename, long size, String url) {
        @Override public String toString() { return "WebhookAttachmentEvidence[internal]"; }
    }
}
