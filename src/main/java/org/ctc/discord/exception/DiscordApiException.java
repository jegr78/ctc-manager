package org.ctc.discord.exception;

import java.io.IOException;

public abstract sealed class DiscordApiException extends IOException
		permits DiscordTransientException,
				DiscordAuthException,
				DiscordMissingPermissionsException,
				DiscordNotFoundException,
				DiscordCategoryFullException,
				DiscordDisabledException {

	public enum Category {
		TRANSIENT,
		AUTH,
		MISSING_PERMISSIONS,
		NOT_FOUND,
		CATEGORY_FULL,
		DISABLED
	}

	protected DiscordApiException(String message, Throwable cause) {
		super(message, cause);
	}

	public abstract Category category();
}
