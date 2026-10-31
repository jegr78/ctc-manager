package org.ctc.dataimport.exception;

public final class DisabledGoogleApiException extends GoogleApiException {

	public DisabledGoogleApiException(Throwable cause) {
		super(GoogleApiExceptionMapper.DISABLED_MESSAGE, cause);
	}

	@Override
	public Category category() {
		return Category.DISABLED;
	}
}
