package org.ctc.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.ctc.discord.DiscordRateLimitInterceptor;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.DiscordWebhookClient;
import org.ctc.discord.dto.WebhookPayload;
import org.ctc.discord.exception.DiscordApiException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"DATABASE_URL=jdbc:h2:mem:recovery-policy;DB_CLOSE_DELAY=-1",
		"DATABASE_USERNAME=sa",
		"DATABASE_PASSWORD=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.security.user.name=recovery-admin",
		"spring.security.user.password=recovery-test-password",
		"app.discord.bot-token=restored-bot-token",
		"ctc.outbound.enabled=true",
		"app.upload-dir=target/recovery-policy/uploads",
		"app.backup.staging-dir=target/recovery-policy/staging",
		"app.backup.import-backups-dir=target/recovery-policy/import-backups",
		"ctc.site.output-dir=target/recovery-policy/site",
		"logging.config=classpath:logback-test.xml"
})
@ActiveProfiles("recovery-check")
@AutoConfigureMockMvc
@Import(RecoveryPolicyIT.RecordingConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("integration")
class RecoveryPolicyIT {

	@Autowired
	private DataSource dataSource;
	@Autowired
	private StartupDelivery startupDelivery;
	@Autowired
	private RecordingInterceptor recordingInterceptor;
	@Autowired
	private DiscordWebhookClient webhookClient;
	@Autowired
	private MockMvc mockMvc;

	@Test
	void givenRecoveryProfile_whenConnecting_thenConfiguredDatabaseIsUsed() throws Exception {
		// when
		try (var connection = dataSource.getConnection()) {
			// then
			assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:recovery-policy");
		}
	}

	@Test
	void givenRestoredBotToken_whenStartupRequestsDiscord_thenOutboundIsDisabled() {
		// when
		DiscordApiException failure = startupDelivery.failure;
		// then
		assertThat(failure).isNotNull();
		assertThat(failure.category().name()).isEqualTo("DISABLED");
		assertThat(recordingInterceptor.attempts.get()).isZero();
	}

	@Test
	void givenRestoredWebhook_whenSending_thenOutboundIsDisabled() {
		// when / then
		assertThatThrownBy(() -> webhookClient.execute(
				"https://discord.com/api/webhooks/100/restore-check-token",
				new WebhookPayload("T-Recovery fixture", List.of())))
				.isInstanceOfSatisfying(DiscordApiException.class,
						failure -> assertThat(failure.category().name()).isEqualTo("DISABLED"));
		assertThat(recordingInterceptor.attempts.get()).isZero();
	}

	@Test
	void givenRecoveryProfile_whenAnonymous_thenAdminIsUnauthorized() throws Exception {
		// when / then
		mockMvc.perform(get("/admin/seasons").with(anonymous())).andExpect(status().isUnauthorized());
	}

	@Test
	void givenRecoveryProfile_whenAuthenticated_thenAdminRemainsReadable() throws Exception {
		// when / then
		mockMvc.perform(get("/admin/seasons").with(httpBasic("recovery-admin", "recovery-test-password")))
				.andExpect(status().isOk());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class RecordingConfiguration {
		@Bean
		@Primary
		RecordingInterceptor recoveryRecordingInterceptor(Clock clock, OutboundPolicy policy) {
			return new RecordingInterceptor(clock, policy);
		}

		@Bean
		StartupDelivery recoveryStartupDelivery(DiscordRestClient client) {
			return new StartupDelivery(client);
		}
	}

	static class RecordingInterceptor extends DiscordRateLimitInterceptor {
		final AtomicInteger attempts = new AtomicInteger();

		RecordingInterceptor(Clock clock, OutboundPolicy policy) {
			super(clock, "0", "0", 0, policy);
		}

		@Override
		public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
				throws IOException {
			return super.intercept(request, body, (outboundRequest, outboundBody) -> {
				attempts.incrementAndGet();
				throw new IOException("Network transport blocked by recovery test");
			});
		}
	}

	static class StartupDelivery {
		private final DiscordRestClient client;
		DiscordApiException failure;

		StartupDelivery(DiscordRestClient client) {
			this.client = client;
		}

		@EventListener(ApplicationReadyEvent.class)
		public void deliverPendingWork() {
			try {
				client.fetchBotUser();
			} catch (DiscordApiException exception) {
				failure = exception;
			}
		}
	}
}
