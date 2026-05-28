package org.ctc.discord.exception;

public final class DiscordCategoryFullException extends DiscordApiException {

	public DiscordCategoryFullException(String message, Throwable cause) {
		super(message, cause);
	}

	@Override
	public Category category() {
		return Category.CATEGORY_FULL;
	}
}
