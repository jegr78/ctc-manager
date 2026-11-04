package org.ctc.discord;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
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

@SpringBootTest(classes={DiscordConfig.class,DiscordRateLimitInterceptor.class,DiscordHostValidator.class,DiscordRestClient.class,OutboundPolicy.class},
        webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"ctc.outbound.enabled=true","app.discord.bot-token=test-incident-thread-token","app.discord.allowed-hosts=discord.com,localhost,127.0.0.1","app.discord.read-timeout=500ms","app.discord.rate-limit.jitter-ms=0","app.discord.rate-limit.fivexx-backoff-ms=0,0"})
@ActiveProfiles("dev")
@Tag("integration")
class DiscordIncidentThreadRestIT {
    @RegisterExtension static WireMockExtension wm=WireMockExtension.newInstance().options(options().dynamicPort()).build();
    @DynamicPropertySource static void endpoint(DynamicPropertyRegistry properties){properties.add("app.discord.base-url",() -> wm.baseUrl()+"/api/v10");}
    @Autowired DiscordRestClient client;
    @BeforeEach void reset(){wm.resetAll();}
    String thread(String id,String parent,boolean archived) {return "{\"id\":\""+id+"\",\"guild_id\":\"100\",\"parent_id\":\""+parent+"\",\"type\":11,\"name\":\"Test INC-case marker\",\"thread_metadata\":{\"archived\":"+archived+",\"locked\":false,\"archive_timestamp\":\"2026-11-03T12:00:00Z\"}}";}
    String forum() {return "{\"id\":\"800\",\"guild_id\":\"100\",\"parent_id\":\"300\",\"type\":11,\"thread_metadata\":{\"archived\":false,\"locked\":false},\"message\":{\"id\":\"801\",\"channel_id\":\"800\",\"author\":{\"id\":\"700\"},\"content\":\"Test controlled snapshot\"}}";}

    @Test void givenANewDetailedTextStarter_whenCreatingItsThread_thenUseTheKnownStarterAndPreserveTheDedicatedApiShape() {
        wm.stubFor(post(urlEqualTo("/api/v10/channels/200/messages")).willReturn(okJson("{\"id\":\"600\",\"channel_id\":\"200\"}")));
        wm.stubFor(post(urlEqualTo("/api/v10/channels/200/messages/600/threads")).willReturn(okJson(thread("600","200",false))));
        var starter=assertDoesNotThrow(() -> client.createBotMessage("200","test-new-disclosure-nonce","Test controlled snapshot"));
        var created=assertDoesNotThrow(() -> client.startIncidentMessageThread("200",starter.id(),"Test INC-case marker"));
        assertThat(created.id()).isEqualTo(starter.id());assertThat(created.parentId()).isEqualTo("200");assertThat(created.type()).isEqualTo(11);
        wm.verify(postRequestedFor(urlEqualTo("/api/v10/channels/200/messages")).withHeader("Authorization",equalTo("Bot test-incident-thread-token")).withRequestBody(matchingJsonPath("$.allowed_mentions.parse",equalToJson("[]"))).withRequestBody(matchingJsonPath("$.nonce",equalTo("test-new-disclosure-nonce"))));
        wm.verify(postRequestedFor(urlEqualTo("/api/v10/channels/200/messages/600/threads")).withRequestBody(equalToJson("{\"name\":\"Test INC-case marker\",\"auto_archive_duration\":1440}")));
    }
    @Test void givenAForumDestinationAndSelectedTags_whenCreating_thenNestItsStarterAndKeepReturnedMessageAndThreadIdentitiesSeparate() {
        wm.stubFor(post(urlEqualTo("/api/v10/channels/300/threads")).willReturn(okJson(forum())));
        var created=assertDoesNotThrow(() -> client.startIncidentForumThread("300","Test INC-case marker","Test controlled snapshot",List.of("900")));
        assertThat(created.id()).isEqualTo("800");assertThat(created.message().id()).isEqualTo("801");assertThat(created.message().channelId()).isEqualTo("800");
        wm.verify(1,postRequestedFor(urlEqualTo("/api/v10/channels/300/threads")).withHeader("Authorization",equalTo("Bot test-incident-thread-token"))
                .withRequestBody(equalToJson("{\"name\":\"Test INC-case marker\",\"auto_archive_duration\":1440,\"applied_tags\":[\"900\"],\"message\":{\"content\":\"Test controlled snapshot\",\"allowed_mentions\":{\"parse\":[]}}}")));
    }
    @Test void givenCurrentForumMetadata_whenReading_thenPreservePermissionOverwritesRequiredTagFlagAndModeratedTags() {
        wm.stubFor(get(urlEqualTo("/api/v10/channels/300")).willReturn(okJson("{\"id\":\"300\",\"guild_id\":\"100\",\"type\":15,\"flags\":16,\"permission_overwrites\":[{\"id\":\"100\",\"type\":0,\"allow\":\"0\",\"deny\":\"1024\"}],\"available_tags\":[{\"id\":\"900\",\"name\":\"Test Review\",\"moderated\":true}]}")));
        var channel=assertDoesNotThrow(() -> client.fetchIncidentChannel("300"));assertThat(channel.type()).isEqualTo(15);assertThat(channel.flags()).isEqualTo(16);assertThat(channel.permissionOverwrites()).hasSize(1);assertThat(channel.availableTags().getFirst().moderated()).isTrue();
    }
    @Test void givenArchivedPublicThreads_whenReadingABoundedPage_thenRetainCursorMetadataWithoutChangingTheLegacyListing() {
        wm.stubFor(get(urlPathEqualTo("/api/v10/channels/300/threads/archived/public")).withQueryParam("before",equalTo("2026-11-03T12:00:00Z")).withQueryParam("limit",equalTo("25")).willReturn(okJson("{\"threads\":["+thread("800","300",true)+"],\"has_more\":true}")));
        var page=assertDoesNotThrow(() -> client.listIncidentArchivedThreads("300","2026-11-03T12:00:00Z",25));assertThat(page.hasMore()).isTrue();assertThat(page.threads().getFirst().threadMetadata().archived()).isTrue();
    }
    @Test void givenAnAmbiguousForumServerFailure_whenCreating_thenStopAfterOnePostWithoutAnAutomaticDuplicateCreation() {
        wm.stubFor(post(urlEqualTo("/api/v10/channels/300/threads")).willReturn(serverError()));
        assertThatThrownBy(() -> client.startIncidentForumThread("300","Test INC-case marker","Test controlled snapshot",List.of())).isInstanceOf(DiscordTransientException.class);
        wm.verify(1,postRequestedFor(urlEqualTo("/api/v10/channels/300/threads")));
    }
    @Test void givenAForumTimeoutAfterRequestAcceptance_whenCreating_thenKeepItsOutcomeUnknownAndNeverRepeatThePost() {
        wm.stubFor(post(urlEqualTo("/api/v10/channels/300/threads")).willReturn(okJson(forum()).withFixedDelay(1200)));
        assertThatThrownBy(() -> client.startIncidentForumThread("300","Test INC-case marker","Test controlled snapshot",List.of())).isInstanceOf(DiscordTransientException.class);
        wm.verify(1,postRequestedFor(urlEqualTo("/api/v10/channels/300/threads")));
    }
    @Test void givenAProvenForumRateLimitRejection_whenCreating_thenRetryThatSafeRejectionUsingTheSameRequest() {
        wm.stubFor(post(urlEqualTo("/api/v10/channels/300/threads")).inScenario("Test quota").whenScenarioStateIs("Started").willReturn(aResponse().withStatus(429).withHeader("Retry-After","0")).willSetStateTo("available"));
        wm.stubFor(post(urlEqualTo("/api/v10/channels/300/threads")).inScenario("Test quota").whenScenarioStateIs("available").willReturn(okJson(forum())));
        var created=assertDoesNotThrow(() -> client.startIncidentForumThread("300","Test INC-case marker","Test controlled snapshot",List.of()));assertThat(created.id()).isEqualTo("800");wm.verify(2,postRequestedFor(urlEqualTo("/api/v10/channels/300/threads")));
    }
    @Test void givenExistingNonceBackedMessages_whenTransientFailureOccurs_thenKeepTheExistingClientRetryContract() {
        wm.stubFor(post(urlEqualTo("/api/v10/channels/200/messages")).inScenario("Test legacy retry").whenScenarioStateIs("Started").willReturn(serverError()).willSetStateTo("available"));
        wm.stubFor(post(urlEqualTo("/api/v10/channels/200/messages")).inScenario("Test legacy retry").whenScenarioStateIs("available").willReturn(okJson("{\"id\":\"600\",\"channel_id\":\"200\"}")));
        var sent=assertDoesNotThrow(() -> client.createBotMessage("200","test-existing-nonce","Test existing neutral message"));assertThat(sent.id()).isEqualTo("600");wm.verify(2,postRequestedFor(urlEqualTo("/api/v10/channels/200/messages")));
    }
}
