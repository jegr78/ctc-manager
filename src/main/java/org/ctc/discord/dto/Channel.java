package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Channel(
		String id,
		String name,
		int type,
		@JsonProperty("parent_id") String parentId) {
}
