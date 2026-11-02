package org.ctc.discord;

import org.ctc.discord.picks.DiscordPickChannelPermissionSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DiscordPickAccessRestContractTest {
    final RestClient.Builder builder = RestClient.builder().baseUrl("https://discord.com/api/v10")
            .defaultHeader("Authorization", "Bot test-contract-token");
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    final DiscordPickChannelPermissionSource permissions = new DiscordPickChannelPermissionSource(new DiscordRestClient(builder.build()));

    @Test
    void givenProductionDiscordJsonAndLaterRevocation_whenCheckingAccess_thenDeserializeExactFactsAndFetchThemAgain() {
        // given
        expectFacts("[\"400\"]");
        expectFacts("[]");
        // when / then
        assertThat(permissions.hasCurrentPrivateAccess("100", "200", "300")).isTrue();
        assertThat(permissions.hasCurrentPrivateAccess("100", "200", "300")).isFalse();
        server.verify();
    }

    void expectFacts(String roles) {
        expect("/guilds/100", """
                {"id":"100","name":"Test server","owner_id":"999"}
                """);
        expect("/channels/200", """
                {"id":"200","guild_id":"100","type":0,"name":"test-private-team",
                 "permission_overwrites":[
                    {"id":"100","type":0,"allow":"0","deny":"1024"},
                    {"id":"400","type":0,"allow":"1024","deny":"0"}]}
                """);
        expect("/guilds/100/members/300", """
                {"user":{"id":"300","username":"Test member","discriminator":"0","bot":false},
                 "roles":%s,"joined_at":"2026-01-01T00:00:00Z"}
                """.formatted(roles));
        expect("/guilds/100/roles", """
                [{"id":"100","name":"@everyone","permissions":"1024","position":0},
                 {"id":"400","name":"Test channel access","permissions":"0","position":1}]
                """);
    }

    void expect(String path, String body) {
        server.expect(requestTo("https://discord.com/api/v10" + path)).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bot test-contract-token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
