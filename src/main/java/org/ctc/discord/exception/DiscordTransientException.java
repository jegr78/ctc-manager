package org.ctc.discord.exception;

public final class DiscordTransientException extends DiscordApiException {
    private final boolean confirmedRejection;

	public DiscordTransientException(String message, Throwable cause) {
        this(message, cause, false);
	}

    public DiscordTransientException(String message, Throwable cause, boolean confirmedRejection) {
        super(message, cause); this.confirmedRejection = confirmedRejection;
    }
    public boolean isConfirmedRejection() { return confirmedRejection; }

	@Override
	public Category category() {
		return Category.TRANSIENT;
	}
}
