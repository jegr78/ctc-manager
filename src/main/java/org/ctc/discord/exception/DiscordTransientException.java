package org.ctc.discord.exception;

public final class DiscordTransientException extends DiscordApiException {

	public DiscordTransientException(String message, Throwable cause) {
		super(message, cause);
	}

	@Override
	public Category category() {
		return Category.TRANSIENT;
	}
}
