package org.ctc.integration;

import java.io.IOException;

public final class ExternalIntegrationDisabledException extends IOException {

	public static final String MESSAGE = "External integrations are disabled for this instance.";

	public ExternalIntegrationDisabledException() {
		super(MESSAGE);
	}
}
