package org.ctc.dataimport.exception;

/**
 * Access denied to a resource that exists: 403 with a permission-related reason
 * (or null details — defensive default for 403), typically because the target
 * sheet/calendar has not been shared with the service-account principal.
 */
public final class PermissionGoogleApiException extends GoogleApiException {

	public PermissionGoogleApiException(String message, Throwable cause) {
		super(message, cause);
	}

	@Override
	public Category category() {
		return Category.PERMISSION;
	}
}
