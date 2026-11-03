package org.ctc.discord;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import org.ctc.discord.dto.WebhookMessage;
import org.ctc.discord.dto.WebhookPayload;
import org.ctc.discord.exception.DiscordAuthException;
import org.ctc.discord.exception.DiscordNotFoundException;
import org.ctc.discord.exception.DiscordTransientException;
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
class DiscordWebhookClientIT {

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
        registry.add("app.discord.read-timeout", () -> "300ms");
        registry.add("app.discord.attachment-hosts", () -> "cdn.discordapp.com,localhost,127.0.0.1");
		registry.add("app.discord.rate-limit.fivexx-backoff-ms", () -> "10,10,10");
	}

	@Autowired
	private DiscordWebhookClient client;

	@BeforeEach
	void resetWireMock() {
		wm.resetAll();
	}

    @Test
    void givenExistingWebhookMessage_whenFetchingEvidence_thenReadTheExactMessageAndSignedAttachmentBytes() throws Exception {
        String path = "/api/v10/webhooks/123456789012345678/Test_evidence-token/messages/234567890123456789";
        String imagePath = "/attachments/345678901234567890/456789012345678901/settings-race-1.png";
        String imageUrl = wm.baseUrl() + imagePath + "?ex=abc&is=def&hm=Test_signature";
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo(path)).willReturn(okJson("""
                {"id":"234567890123456789","channel_id":"345678901234567890","webhook_id":"123456789012345678",
                 "content":"Test captured content","flags":0,"attachments":[{"id":"456789012345678901",
                 "filename":"settings-race-1.png","size":5,"url":"%s"}]}
                """.formatted(imageUrl))));
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo(imagePath))
                .withQueryParam("hm", equalTo("Test_signature")).willReturn(aResponse().withBody("image")));
        var evidence = client.getMessageEvidence(wm.baseUrl() + "/api/v10/webhooks/123456789012345678/Test_evidence-token", "234567890123456789");
        assertThat(evidence).isNotNull(); assertThat(evidence.webhookId()).isEqualTo("123456789012345678");
        assertThat(evidence.attachments()).singleElement().satisfies(file -> assertThat(file.url()).isEqualTo(imageUrl));
        assertThat(client.downloadAttachment(imageUrl, 5)).isEqualTo("image".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        wm.verify(0, postRequestedFor(com.github.tomakehurst.wiremock.client.WireMock.anyUrl()));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"image-extra", "tiny", "", "status", "redirect"})
    void givenWrongSizeOrUnsuccessfulAttachment_whenDownloading_thenRejectInsteadOfTrustingMetadata(String body) {
        String path = "/attachments/345678901234567890/456789012345678901/settings-race-1.png";
        var response = aResponse().withBody(body);
        if (body.equals("status")) response.withStatus(404);
        if (body.equals("redirect")) response.withStatus(302).withHeader("Location", "https://untrusted.invalid/private");
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo(path)).willReturn(response));
        assertThatThrownBy(() -> client.downloadAttachment(wm.baseUrl() + path, 5))
                .isInstanceOf(org.ctc.discord.exception.DiscordApiException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"http://cdn.discordapp.com/attachments/a/b/image.png", "https://evil.invalid/attachments/a/b/image.png",
            "https://cdn.discordapp.com.evil.invalid/attachments/a/b/image.png", "https://secret@cdn.discordapp.com/attachments/a/b/image.png",
            "https://cdn.discordapp.com:9443/attachments/a/b/image.png", "https://cdn.discordapp.com/attachments/a/b/image.png#private", "file:///private/image.png"})
    void givenUntrustedAttachmentUrl_whenDownloading_thenRejectBeforeAnyNetworkCall(String url) {
        assertThatThrownBy(() -> client.downloadAttachment(url, 5)).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class)
                .hasMessageNotContaining(url);
        wm.verify(0, com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(com.github.tomakehurst.wiremock.client.WireMock.anyUrl()));
    }

    @Test
    void givenMalformedRemoteMessage_whenFetchingEvidence_thenReturnASafeVerificationFailure() {
        // given
        String path = "/api/v10/webhooks/123456789012345678/Test_evidence-token/messages/234567890123456789";
        wm.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo(path)).willReturn(okJson(
                "{\"id\":\"234567890123456789\",\"content\":{\"private\":\"Test_Private_Malformed_Message\"},\"flags\":0}")));
        // when / then
        assertThatThrownBy(() -> client.getMessageEvidence(wm.baseUrl() + "/api/v10/webhooks/123456789012345678/Test_evidence-token", "234567890123456789"))
                .isInstanceOf(org.ctc.discord.exception.DiscordApiException.class).hasMessageNotContaining("Test_Private_Malformed_Message");
    }

    @Test
    void givenUnansweredWebhook_whenExecuting_thenBoundTheWaitAndReportAnUncertainTransportFailure() {
        // given
        String webhookPath = "/api/v10/webhooks/123456789012345678/Test_production-format-token";
        wm.stubFor(post(urlPathEqualTo(webhookPath)).withQueryParam("wait", equalTo("true"))
                .willReturn(okJson("{\"id\":\"234567890123456789\",\"channel_id\":\"345678901234567890\"}").withFixedDelay(1200)));
        long started = System.nanoTime();
        // when / then
        assertThatThrownBy(() -> client.execute(wm.baseUrl() + webhookPath, new WebhookPayload("Test frozen output", List.of())))
                .isInstanceOf(DiscordTransientException.class);
        assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(3));
        wm.verify(postRequestedFor(urlPathEqualTo(webhookPath)).withQueryParam("wait", equalTo("true")));
    }

    @Test
    void givenPossiblyAcceptedFirstWriteAndRejectedRetry_whenExecuting_thenReportDeliveryUncertainty() {
        // given
        String path = "/api/v10/webhooks/123456789012345679/Test_ambiguous-token";
        String scenario = "Test ambiguous write then rejection";
        wm.stubFor(post(urlPathEqualTo(path)).withQueryParam("wait", equalTo("true"))
                .inScenario(scenario).whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willSetStateTo("retried").willReturn(aResponse().withStatus(503)));
        wm.stubFor(post(urlPathEqualTo(path)).withQueryParam("wait", equalTo("true"))
                .inScenario(scenario).whenScenarioStateIs("retried")
                .willReturn(aResponse().withStatus(401).withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Unauthorized\",\"code\":0}")));
        // when / then
        assertThatThrownBy(() -> client.execute(wm.baseUrl() + path, new WebhookPayload("Test captured output", List.of())))
                .isInstanceOf(DiscordTransientException.class);
        wm.verify(2, postRequestedFor(urlPathEqualTo(path)).withQueryParam("wait", equalTo("true")));
    }

    @Test
    void givenRedirectWithAnApparentlyValidMessageBody_whenExecuting_thenNeverClaimSuccessfulDelivery() {
        // given
        String path = "/api/v10/webhooks/123456789012345680/Test_redirect-token";
        wm.stubFor(post(urlPathEqualTo(path)).withQueryParam("wait", equalTo("true"))
                .willReturn(aResponse().withStatus(302).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"234567890123456789\",\"channel_id\":\"345678901234567890\"}")));
        // when / then
        assertThatThrownBy(() -> client.execute(wm.baseUrl() + path, new WebhookPayload("Test captured output", List.of())))
                .isInstanceOf(DiscordTransientException.class);
        wm.verify(1, postRequestedFor(urlPathEqualTo(path)).withQueryParam("wait", equalTo("true")));
    }

	@Test
	void given200_whenExecute_thenReturnsWebhookMessage() throws Exception {
		String webhookPath = "/api/v10/webhooks/100/abc";
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-1\",\"channel_id\":\"chan-1\"}")));

		var payload = new WebhookPayload("Game On!", List.of());
		WebhookMessage out = client.execute(wm.baseUrl() + webhookPath, payload);

		assertThat(out.id()).isEqualTo("msg-1");
		assertThat(out.channelId()).isEqualTo("chan-1");
	}

	@Test
	void given401_whenExecute_thenThrowsDiscordAuthException() {
		String webhookPath = "/api/v10/webhooks/100/abc";
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(401)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"message\":\"Unauthorized\",\"code\":0}")));

		assertThatThrownBy(() -> client.execute(wm.baseUrl() + webhookPath, new WebhookPayload("hi", List.of())))
				.isInstanceOf(DiscordAuthException.class);
	}

	@Test
	void given404_whenExecute_thenThrowsDiscordNotFoundException() {
		String webhookPath = "/api/v10/webhooks/100/abc";
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(404)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"message\":\"Unknown Webhook\",\"code\":10015}")));

		assertThatThrownBy(() -> client.execute(wm.baseUrl() + webhookPath, new WebhookPayload("hi", List.of())))
				.isInstanceOf(DiscordNotFoundException.class);
	}

	@Test
	void given5xxExhausted_whenExecute_thenThrowsDiscordTransientException() {
		String webhookPath = "/api/v10/webhooks/100/abc";
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(503)));

		assertThatThrownBy(() -> client.execute(wm.baseUrl() + webhookPath, new WebhookPayload("hi", List.of())))
				.isInstanceOf(DiscordTransientException.class);
	}

	@Test
	void whenExecute_thenSendsWaitTrueQueryParam() throws Exception {
		String webhookPath = "/api/v10/webhooks/100/abc";
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withQueryParam("wait", equalTo("true"))
				.willReturn(okJson("{\"id\":\"msg-1\",\"channel_id\":\"chan-1\"}")));

		WebhookMessage out = client.execute(
				wm.baseUrl() + webhookPath, new WebhookPayload("Game On!", List.of()));

		assertThat(out).as("Discord returns 204 without ?wait=true — body must be non-null").isNotNull();
		assertThat(out.id()).isEqualTo("msg-1");
		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath))
				.withQueryParam("wait", equalTo("true")));
	}

	@Test
	void given200_whenEditMessage_thenReturnsUpdatedMessage() throws Exception {
		String webhookPath = "/api/v10/webhooks/100/abc";
		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/msg-1"))
				.willReturn(okJson("{\"id\":\"msg-1\",\"channel_id\":\"chan-1\"}")));

		WebhookMessage out = client.editMessage(
				wm.baseUrl() + webhookPath, "msg-1", new WebhookPayload("edited", List.of()));

		assertThat(out.id()).isEqualTo("msg-1");
	}
}
