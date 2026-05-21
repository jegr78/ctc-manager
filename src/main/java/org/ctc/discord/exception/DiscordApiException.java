package org.ctc.discord.exception;

import java.io.IOException;

public abstract sealed class DiscordApiException extends IOException
		permits DiscordTransientException,
				DiscordAuthException,
				DiscordNotFoundException,
				DiscordCategoryFullException {

	public enum Category {
		TRANSIENT,
		AUTH,
		NOT_FOUND,
		CATEGORY_FULL
	}

	protected DiscordApiException(String message, Throwable cause) {
		super(message, cause);
	}

	public abstract Category category();
}
