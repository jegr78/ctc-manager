package org.ctc.discord.exception;

public final class DiscordAuthException extends DiscordApiException {

	public DiscordAuthException(String message, Throwable cause) {
		super(message, cause);
	}

	@Override
	public Category category() {
		return Category.AUTH;
	}
}
