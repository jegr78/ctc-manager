package org.ctc.dataimport.exception;

/**
 * Resource not found at the requested URI: 404 from the Sheets or Calendar
 * endpoints — the spreadsheet ID or calendar ID is invalid.
 */
public final class NotFoundGoogleApiException extends GoogleApiException {

	public NotFoundGoogleApiException(String message, Throwable cause) {
		super(message, cause);
	}

	@Override
	public Category category() {
		return Category.NOT_FOUND;
	}
}
