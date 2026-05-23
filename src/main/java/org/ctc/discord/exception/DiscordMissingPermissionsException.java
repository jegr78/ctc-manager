package org.ctc.discord.exception;

public final class DiscordMissingPermissionsException extends DiscordApiException {

	public DiscordMissingPermissionsException(String message, Throwable cause) {
		super(message, cause);
	}

	@Override
	public Category category() {
		return Category.MISSING_PERMISSIONS;
	}
}
