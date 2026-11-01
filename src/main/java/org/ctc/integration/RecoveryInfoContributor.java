package org.ctc.integration;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("recovery-check")
@RequiredArgsConstructor
public class RecoveryInfoContributor implements InfoContributor {

	private final OutboundPolicy outboundPolicy;

	@Override
	public void contribute(Info.Builder builder) {
		builder.withDetail("ctcRecovery", Map.of("version", 1, "outboundEnabled", outboundPolicy.isEnabled()));
	}
}
