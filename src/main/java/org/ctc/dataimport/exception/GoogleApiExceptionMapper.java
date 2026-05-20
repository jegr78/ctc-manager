package org.ctc.dataimport.exception;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Set;

/**
 * Maps the raw {@link IOException} / {@link GeneralSecurityException} surface that
 * Google's Java client lib throws onto the four typed {@link GoogleApiException}
 * subtypes consumed by the controller flash UX. The 4 user-visible message
 * strings live on this class as constants so controllers and the
 * {@code docs/operations/google-integration.md} runbook share a single source of
 * truth (Update-on-Triage discipline).
 *
 * <p>Mapping rules — see {@code 91-RESEARCH.md § Pattern 2} for the authoritative
 * table.
 */
public final class GoogleApiExceptionMapper {

	/** Hardcoded user-visible message strings — mirrored verbatim in
	 * controllers and {@code docs/operations/google-integration.md § Error
	 * Categories}. */
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
		if (e instanceof GoogleJsonResponseException gjre) {
			return fromGoogleJson(gjre);
		}
		return new TransientGoogleApiException(TRANSIENT_MESSAGE, e);
	}

	public static AuthGoogleApiException from(GeneralSecurityException e) {
		return new AuthGoogleApiException(AUTH_MESSAGE, e);
	}

	private static GoogleApiException fromGoogleJson(GoogleJsonResponseException gjre) {
		// 408, 429, 5xx, and unknown status codes all map to TRANSIENT (lenient default);
		// only 401 / 403 / 404 dispatch to specific subtypes per RESEARCH § Pattern 2.
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
