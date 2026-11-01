package org.ctc.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class OutboundPolicy {

	private final boolean enabled;

	public OutboundPolicy(@Value("${ctc.outbound.enabled:true}") boolean configured, Environment environment) {
		this.enabled = configured && !environment.matchesProfiles("recovery-check");
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void requireEnabled() throws ExternalIntegrationDisabledException {
		if (!enabled) {
			throw new ExternalIntegrationDisabledException();
		}
	}
}
