package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ThreadCreateRequest(
		String name,
		@JsonProperty("auto_archive_duration") int autoArchiveDuration) {
}
