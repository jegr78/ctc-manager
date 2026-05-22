package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class DiscordChannelServiceWireMockIT {

	@RegisterExtension
	static WireMockExtension wm = WireMockExtension.newInstance()
			.options(options().dynamicPort())
			.build();

	@DynamicPropertySource
	static void overrideDiscordConfig(DynamicPropertyRegistry registry) {
		registry.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
		registry.add("app.discord.bot-token", () -> "test-bot-token");
		registry.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
		registry.add("app.discord.rate-limit.jitter-ms", () -> "0");
		registry.add("app.discord.rate-limit.fivexx-backoff-ms", () -> "10,10,10");
	}

	@BeforeEach
	void resetWireMock() {
		wm.resetAll();
	}

	@Test
	void givenValidMatchAndConfig_whenCreateMatchChannel_thenDbWriteAnd3OutboundCalls() {
		fail("not yet implemented");
	}

	@Test
	void givenMissingHomeRole_whenCreateMatchChannel_thenBusinessRuleExceptionNoOutboundCalls() {
		fail("not yet implemented");
	}

	@Test
	void givenWebhookCreationFails_whenCreateMatchChannel_thenCleanupDeleteCalledDbUnchanged() {
		fail("not yet implemented");
	}
}
