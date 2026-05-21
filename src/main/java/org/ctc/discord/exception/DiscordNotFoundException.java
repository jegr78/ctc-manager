package org.ctc.discord.exception;

public final class DiscordNotFoundException extends DiscordApiException {

	public DiscordNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	@Override
	public Category category() {
		return Category.NOT_FOUND;
	}
}
