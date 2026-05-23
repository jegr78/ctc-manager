package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreadMetadata(Boolean archived) {

	public boolean isArchived() {
		return archived != null && archived;
	}
}
