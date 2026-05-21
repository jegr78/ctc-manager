package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChannelModifyRequest(
		String name,
		@JsonProperty("parent_id") String parentId) {
}
