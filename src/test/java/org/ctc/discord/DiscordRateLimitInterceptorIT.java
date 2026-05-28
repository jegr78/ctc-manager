package org.ctc.discord;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.ctc.discord.exception.DiscordAuthException;
import org.ctc.discord.exception.DiscordTransientException;
import org.ctc.discord.util.BucketState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class DiscordRateLimitInterceptorIT {

	@RegisterExtension
	static WireMockExtension wm = WireMockExtension.newInstance()
			.options(options().dynamicPort())
			.build();

	@DynamicPropertySource
	static void overrideDiscordConfig(DynamicPropertyRegistry registry) {
		registry.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
		registry.add("app.discord.bot-token", () -> "test-bot-token");
		registry.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
		// Disable jitter so tests do not sleep beyond Retry-After: 0
		registry.add("app.discord.rate-limit.jitter-ms", () -> "0");
		// Compress the 5xx backoff schedule so exhaustion tests run in well under a second
		registry.add("app.discord.rate-limit.fivexx-backoff-ms", () -> "10,10,10");
	}

	@Autowired
	private DiscordRestClient discordRestClient;

	@Autowired
	private DiscordRateLimitInterceptor interceptor;

	@BeforeEach
	void resetWireMock() {
		wm.resetAll();
	}

	@Test
	void given429ThenSuccess_whenFetchBotUser_thenInterceptorRetriesAndSucceeds() throws Exception {
		// given
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.inScenario("rl-429")
				.whenScenarioStateIs(Scenario.STARTED)
				.willReturn(aResponse().withStatus(429)
						.withHeader("Retry-After", "0"))
				.willSetStateTo("retried"));
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.inScenario("rl-429")
				.whenScenarioStateIs("retried")
				.willReturn(okJson("{\"id\":\"42\",\"username\":\"CTC-Bot\",\"discriminator\":\"0001\"}")));

		// when
		var user = discordRestClient.fetchBotUser();

		// then
		assertThat(user.username()).isEqualTo("CTC-Bot");
		wm.verify(2, getRequestedFor(urlPathEqualTo("/api/v10/users/@me")));
	}

	@Test
	void given4Consecutive429_whenFetchBotUser_thenThrowsDiscordTransientException() {
		// given — interceptor allows 3 retries; the 4th 429 must surface as transient
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.willReturn(aResponse().withStatus(429)
						.withHeader("Retry-After", "0")));

		// when / then
		assertThatThrownBy(() -> discordRestClient.fetchBotUser())
				.isInstanceOf(DiscordTransientException.class)
				.hasMessageContaining("Discord connection problem");
		wm.verify(4, getRequestedFor(urlPathEqualTo("/api/v10/users/@me")));
	}

	@Test
	void given500ThenSuccess_whenFetchBotUser_thenInterceptorRetriesAndSucceeds() throws Exception {
		// given
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.inScenario("rl-500")
				.whenScenarioStateIs(Scenario.STARTED)
				.willReturn(aResponse().withStatus(500))
				.willSetStateTo("retried"));
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.inScenario("rl-500")
				.whenScenarioStateIs("retried")
				.willReturn(okJson("{\"id\":\"42\",\"username\":\"CTC-Bot\",\"discriminator\":\"0001\"}")));

		// when
		var user = discordRestClient.fetchBotUser();

		// then
		assertThat(user.username()).isEqualTo("CTC-Bot");
		wm.verify(2, getRequestedFor(urlPathEqualTo("/api/v10/users/@me")));
	}

	@Test
	void given4Consecutive5xx_whenFetchBotUser_thenThrowsDiscordTransientException() {
		// given
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.willReturn(aResponse().withStatus(503)));

		// when / then
		assertThatThrownBy(() -> discordRestClient.fetchBotUser())
				.isInstanceOf(DiscordTransientException.class);
		wm.verify(4, getRequestedFor(urlPathEqualTo("/api/v10/users/@me")));
	}

	@Test
	void given200WithBucketHeaders_whenFetchBotUser_thenBucketStateUpdated() throws Exception {
		// given
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.willReturn(okJson("{\"id\":\"42\",\"username\":\"CTC-Bot\",\"discriminator\":\"0001\"}")
						.withHeader("X-RateLimit-Bucket", "bucket-alpha")
						.withHeader("X-RateLimit-Remaining", "4")
						.withHeader("X-RateLimit-Reset-After", "0.5")));

		// when
		discordRestClient.fetchBotUser();

		// then
		BucketState state = interceptor.bucketState("bucket-alpha");
		assertThat(state).isNotNull();
		assertThat(state.remaining()).isEqualTo(4);
		assertThat(state.resetAt()).isNotNull();
	}

	@Test
	void givenMalformedBucketHeaders_whenFetchBotUser_thenResponsePropagatesNormally() throws Exception {
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.willReturn(okJson("{\"id\":\"42\",\"username\":\"CTC-Bot\",\"discriminator\":\"0001\"}")
						.withHeader("X-RateLimit-Bucket", "bucket-garbage")
						.withHeader("X-RateLimit-Remaining", "not-a-number")
						.withHeader("X-RateLimit-Reset-After", "")));

		discordRestClient.fetchBotUser();

		BucketState state = interceptor.bucketState("bucket-garbage");
		assertThat(state).isNotNull();
		assertThat(state.remaining()).isZero();
	}

	@Test
	void given401_whenFetchBotUser_thenNoRetryAndAuthExceptionPropagates() {
		// given — 401 must NOT trigger interceptor retries
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.willReturn(aResponse().withStatus(401)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"message\":\"Unauthorized\",\"code\":0}")));

		// when / then
		assertThatThrownBy(() -> discordRestClient.fetchBotUser())
				.isInstanceOf(DiscordAuthException.class);
		wm.verify(1, getRequestedFor(urlPathEqualTo("/api/v10/users/@me")));
	}
}
