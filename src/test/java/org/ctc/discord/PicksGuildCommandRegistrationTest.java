package org.ctc.discord;

import org.ctc.discord.exception.DiscordTransientException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PicksGuildCommandRegistrationTest {
    @Test
    void givenExplicitGuildRegistration_whenPosting_thenUseSingleCommandUpsertAndExistingBotIdentity() throws Exception {
        // given
        var builder = RestClient.builder().baseUrl("https://discord.com/api/v10").defaultHeader("Authorization", "Bot test-existing-token");
        var server = MockRestServiceServer.bindTo(builder).build();
        var discord = new DiscordRestClient(builder.build());
        server.expect(requestTo("https://discord.com/api/v10/applications/900/guilds/100/commands"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bot test-existing-token"))
                .andExpect(content().json("{\"type\":1,\"name\":\"picks\",\"description\":\"Manage private team picks\"}"))
                .andRespond(withSuccess("{\"id\":\"800\",\"guild_id\":\"100\",\"name\":\"picks\"}", MediaType.APPLICATION_JSON));
        // when
        discord.registerPicksGuildCommand("900", "100");
        // then
        server.verify();
    }

    @Test
    void givenDiscordFailure_whenRegistering_thenReuseExistingTypedErrorMapping() {
        // given
        var builder = RestClient.builder().baseUrl("https://discord.com/api/v10");
        var server = MockRestServiceServer.bindTo(builder).build();
        var discord = new DiscordRestClient(builder.build());
        server.expect(requestTo("https://discord.com/api/v10/applications/900/guilds/100/commands"))
                .andExpect(method(HttpMethod.POST)).andRespond(withServerError());
        // when / then
        assertThatThrownBy(() -> discord.registerPicksGuildCommand("900", "100")).isInstanceOf(DiscordTransientException.class);
        server.verify();
    }
}
