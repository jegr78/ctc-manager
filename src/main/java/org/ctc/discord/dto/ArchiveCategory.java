package org.ctc.discord.dto;

public record ArchiveCategory(
		String id,
		String name,
		int num,
		int currentChannelCount) {
}
