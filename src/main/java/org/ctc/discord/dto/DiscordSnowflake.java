package org.ctc.discord.dto;

public final class DiscordSnowflake {

	public static final String PATTERN = "^$|^\\d{17,20}$";
	public static final String MESSAGE = "Must be a Discord snowflake (17-20 digits) or empty";

	private DiscordSnowflake() {
	}
}
