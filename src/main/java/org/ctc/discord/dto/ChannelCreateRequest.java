package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChannelCreateRequest(
		String name,
		int type,
		@JsonProperty("parent_id") String parentId) {
}
