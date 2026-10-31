package org.ctc.discord.exception;

public final class DiscordDisabledException extends DiscordApiException {

	public DiscordDisabledException(Throwable cause) {
		super(DiscordApiExceptionMapper.DISABLED_MESSAGE, cause);
	}

	@Override
	public Category category() {
		return Category.DISABLED;
	}
}
