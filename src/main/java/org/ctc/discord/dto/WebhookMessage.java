package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookMessage(String id, @JsonProperty("channel_id") String channelId, Integer flags) {
    public WebhookMessage { if (flags == null) flags = 0; }
    public WebhookMessage(String id, String channelId) { this(id, channelId, 0); }
}
