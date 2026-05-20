package org.ctc.dataimport.exception;

import java.io.IOException;

/**
 * Sealed base for the four typed Google API failure modes surfaced to the operator
 * UI as categorized flash badges. Extends {@link IOException} so existing
 * {@code catch (IOException ...)} sites remain backward-compatible while typed
 * catches at the controller boundary can dispatch on the four exhaustive subtypes.
 */
public abstract sealed class GoogleApiException extends IOException
		permits TransientGoogleApiException,
		        AuthGoogleApiException,
		        NotFoundGoogleApiException,
		        PermissionGoogleApiException {

	public enum Category { TRANSIENT, AUTH, NOT_FOUND, PERMISSION }

	protected GoogleApiException(String message, Throwable cause) {
		super(message, cause);
	}

	public abstract Category category();
}
