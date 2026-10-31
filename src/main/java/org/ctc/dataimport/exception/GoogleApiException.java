package org.ctc.dataimport.exception;

import java.io.IOException;

public abstract sealed class GoogleApiException extends IOException
		permits TransientGoogleApiException,
		        AuthGoogleApiException,
		        NotFoundGoogleApiException,
		        PermissionGoogleApiException,
		        DisabledGoogleApiException {

	public enum Category { TRANSIENT, AUTH, NOT_FOUND, PERMISSION, DISABLED }

	protected GoogleApiException(String message, Throwable cause) {
		super(message, cause);
	}

	public abstract Category category();
}
