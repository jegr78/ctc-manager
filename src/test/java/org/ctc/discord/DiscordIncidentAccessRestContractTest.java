package org.ctc.discord;

import org.ctc.discord.incidents.DiscordIncidentChannelAccessSource;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DiscordIncidentAccessRestContractTest {
    final RestClient.Builder builder = RestClient.builder().baseUrl("https://discord.com/api/v10").defaultHeader("Authorization", "Bot test-incident-contract");
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    final DiscordIncidentChannelAccessSource source = new DiscordIncidentChannelAccessSource(new DiscordRestClient(builder.build()));

    @Test
    void givenProductionJsonForVisibleMatchChannelAndLaterMemberDeny_whenResolving_thenUseCurrentRolesAndOverwriteOrder() {
        expectFacts("[]"); expectFacts("[{\"id\":\"300\",\"type\":1,\"allow\":\"0\",\"deny\":\"1024\"}]");
        assertThat(source.currentRoles("100", "200", "300")).containsExactly("400");
        assertThatThrownBy(() -> source.currentRoles("100", "200", "300")).isInstanceOf(BusinessRuleException.class).hasMessageContaining("unavailable");
        server.verify();
    }
    private void expectFacts(String overwrites) {
        expect("/guilds/100", "{\"id\":\"100\",\"owner_id\":\"999\",\"name\":\"Test incident server\"}");
        expect("/channels/200", "{\"id\":\"200\",\"guild_id\":\"100\",\"type\":0,\"name\":\"test-match\",\"permission_overwrites\":" + overwrites + "}");
        expect("/guilds/100/members/300", "{\"user\":{\"id\":\"300\",\"username\":\"Test private reporter\",\"discriminator\":\"0\"},\"roles\":[\"400\"],\"joined_at\":\"2026-01-01T00:00:00Z\"}");
        expect("/guilds/100/roles", "[{\"id\":\"100\",\"permissions\":\"1024\",\"name\":\"@everyone\"},{\"id\":\"400\",\"permissions\":\"0\",\"name\":\"Test participant\"}]");
    }
    private void expect(String path, String body) {
        server.expect(requestTo("https://discord.com/api/v10" + path)).andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bot test-incident-contract"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
