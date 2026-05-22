package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChannelModifyRequest(
		String name,
		@JsonProperty("parent_id") String parentId) {
}
