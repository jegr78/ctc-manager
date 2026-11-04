package org.ctc.discord;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.ctc.discord.exception.DiscordTransientException;
import org.ctc.integration.OutboundPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest(classes = {DiscordConfig.class, DiscordRateLimitInterceptor.class, DiscordHostValidator.class, DiscordRestClient.class, OutboundPolicy.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"ctc.outbound.enabled=true", "app.discord.bot-token=test-incident-thread-token",
        "app.discord.allowed-hosts=discord.com,localhost,127.0.0.1", "app.discord.rate-limit.jitter-ms=0", "app.discord.rate-limit.fivexx-backoff-ms=0,0"})
@ActiveProfiles("dev")
@Tag("integration")
class DiscordIncidentRulingRestIT {
    @RegisterExtension static WireMockExtension wm = WireMockExtension.newInstance().options(options().dynamicPort()).build();
    @DynamicPropertySource static void endpoint(DynamicPropertyRegistry properties) { properties.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10"); }
    @Autowired DiscordRestClient client;
    @BeforeEach void reset() { wm.resetAll(); }

    @Test void givenAnApprovedRuling_whenCreating_thenSuppressMentionsAndKeepTheStableNonce() {
        wm.stubFor(post(urlEqualTo("/api/v10/channels/800/messages")).willReturn(okJson("{\"id\":\"900\",\"channel_id\":\"800\",\"content\":\"Test approved ruling <@123>\",\"author\":{\"id\":\"700\"}}")));
        var sent = assertDoesNotThrow(() -> client.createRulingMessage("800", "test-ruling-nonce", "Test approved ruling <@123>", java.time.Instant.now().plusSeconds(60)));
        assertThat(sent.id()).isEqualTo("900");
        wm.verify(1, postRequestedFor(urlEqualTo("/api/v10/channels/800/messages"))
                .withHeader("Authorization", equalTo("Bot test-incident-thread-token"))
                .withRequestBody(equalToJson("{\"content\":\"Test approved ruling <@123>\",\"allowed_mentions\":{\"parse\":[]},\"nonce\":\"test-ruling-nonce\",\"enforce_nonce\":true}")));
    }
    @Test void givenAnUncertainRulingCreation_whenDiscordReturnsServerFailure_thenStopAfterOnePost() {
        wm.stubFor(post(urlEqualTo("/api/v10/channels/800/messages")).willReturn(serverError()));
        assertThatThrownBy(() -> client.createRulingMessage("800", "test-ruling-nonce", "Test approved ruling", java.time.Instant.now().plusSeconds(60)))
                .isInstanceOf(DiscordTransientException.class);
        wm.verify(1, postRequestedFor(urlEqualTo("/api/v10/channels/800/messages")));
    }

    @Test void givenAnApprovedCorrection_whenEditingTheKnownBotMessage_thenUseBotPatchAndSuppressMentionsAgain() {
        wm.stubFor(patch(urlEqualTo("/api/v10/channels/800/messages/900")).willReturn(okJson("{\"id\":\"900\",\"channel_id\":\"800\",\"content\":\"Test corrected ruling <@123>\",\"author\":{\"id\":\"700\"}}")));
        var edited = assertDoesNotThrow(() -> client.editBotMessage("800", "900", "Test corrected ruling <@123>", java.time.Instant.now().plusSeconds(60)));
        assertThat(edited.id()).isEqualTo("900"); assertThat(edited.content()).isEqualTo("Test corrected ruling <@123>");
        wm.verify(1, patchRequestedFor(urlEqualTo("/api/v10/channels/800/messages/900"))
                .withHeader("Authorization", equalTo("Bot test-incident-thread-token"))
                .withRequestBody(equalToJson("{\"content\":\"Test corrected ruling <@123>\",\"allowed_mentions\":{\"parse\":[]}}")));
        wm.verify(0, postRequestedFor(anyUrl()));
    }
    @Test void givenAnUncertainBotEdit_whenDiscordReturnsServerFailure_thenPreserveTheUnknownOutcomeAfterOnePatch() {
        wm.stubFor(patch(urlEqualTo("/api/v10/channels/800/messages/900")).willReturn(serverError()));
        assertThatThrownBy(() -> client.editBotMessage("800", "900", "Test corrected ruling", java.time.Instant.now().plusSeconds(60)))
                .isInstanceOf(DiscordTransientException.class);
        wm.verify(1, patchRequestedFor(urlEqualTo("/api/v10/channels/800/messages/900")));
    }

}
