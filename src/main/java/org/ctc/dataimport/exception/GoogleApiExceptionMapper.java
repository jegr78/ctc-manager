package org.ctc.dataimport.exception;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Set;
import org.ctc.integration.ExternalIntegrationDisabledException;

public final class GoogleApiExceptionMapper {

	public static final String DISABLED_MESSAGE = ExternalIntegrationDisabledException.MESSAGE;
	public static final String TRANSIENT_MESSAGE = "Connection problem — retry";
	public static final String AUTH_MESSAGE = "Authentication problem — re-link Google account";
	public static final String NOT_FOUND_MESSAGE = "Sheet not found — check ID";
	public static final String PERMISSION_MESSAGE =
			"Access denied — share the sheet with the service account";

	private static final Set<String> AUTH_REASONS = Set.of(
			"authError", "invalidCredentials", "unauthorized");

	private GoogleApiExceptionMapper() {
	}

	public static GoogleApiException from(IOException e) {
		if (e instanceof ExternalIntegrationDisabledException) {
			return new DisabledGoogleApiException(e);
		}
		if (e instanceof GoogleJsonResponseException gjre) {
			return fromGoogleJson(gjre);
		}
		return new TransientGoogleApiException(TRANSIENT_MESSAGE, e);
	}

	public static AuthGoogleApiException from(GeneralSecurityException e) {
		return new AuthGoogleApiException(AUTH_MESSAGE, e);
	}

	private static GoogleApiException fromGoogleJson(GoogleJsonResponseException gjre) {
		return switch (gjre.getStatusCode()) {
			case 401 -> new AuthGoogleApiException(AUTH_MESSAGE, gjre);
			case 403 -> from403(gjre);
			case 404 -> new NotFoundGoogleApiException(NOT_FOUND_MESSAGE, gjre);
			default -> new TransientGoogleApiException(TRANSIENT_MESSAGE, gjre);
		};
	}

	private static GoogleApiException from403(GoogleJsonResponseException gjre) {
		GoogleJsonError details = gjre.getDetails();
		if (details == null) {
			return new PermissionGoogleApiException(PERMISSION_MESSAGE, gjre);
		}
		List<GoogleJsonError.ErrorInfo> errors = details.getErrors();
		if (errors == null || errors.isEmpty()) {
			return new PermissionGoogleApiException(PERMISSION_MESSAGE, gjre);
		}
		String reason = errors.get(0).getReason();
		if (reason != null && AUTH_REASONS.contains(reason)) {
			return new AuthGoogleApiException(AUTH_MESSAGE, gjre);
		}
		return new PermissionGoogleApiException(PERMISSION_MESSAGE, gjre);
	}
}
