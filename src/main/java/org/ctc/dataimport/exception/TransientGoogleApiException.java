package org.ctc.dataimport.exception;

/**
 * Retry-friendly Google API failure: network/socket errors, 408/429/5xx
 * responses, and unrecognised status codes default here.
 */
public final class TransientGoogleApiException extends GoogleApiException {

	public TransientGoogleApiException(String message, Throwable cause) {
		super(message, cause);
	}

	@Override
	public Category category() {
		return Category.TRANSIENT;
	}
}
