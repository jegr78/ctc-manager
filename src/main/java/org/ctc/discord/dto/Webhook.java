package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Webhook(
		String id,
		String name,
		String token,
		String url,
		@JsonProperty("channel_id") String channelId) {
}
