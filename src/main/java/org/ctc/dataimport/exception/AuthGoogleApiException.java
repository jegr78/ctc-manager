package org.ctc.dataimport.exception;

/**
 * Credentials are unreadable, expired, or rejected by Google: 401, 403 with an
 * auth-related reason, and {@link java.security.GeneralSecurityException} all
 * map here.
 */
public final class AuthGoogleApiException extends GoogleApiException {

	public AuthGoogleApiException(String message, Throwable cause) {
		super(message, cause);
	}

	@Override
	public Category category() {
		return Category.AUTH;
	}
}
