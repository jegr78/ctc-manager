package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Thread(
		String id,
		String name,
		@JsonProperty("parent_id") String parentId,
		int flags,
		@JsonProperty("thread_metadata") ThreadMetadata threadMetadata,
		@JsonProperty("last_message_id") String lastMessageId) {

	public static final int FLAG_PINNED = 1 << 1;

	public boolean pinned() {
		return (flags & FLAG_PINNED) != 0;
	}

	public boolean archived() {
		return threadMetadata != null && threadMetadata.archived();
	}
}
