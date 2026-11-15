package org.ctc.discord;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class StatsGuildCommandRegistrationTest {
    static final String OPTIONS = """
            [{"type":3,"name":"name","description":"%s","required":true,"autocomplete":true},
             {"type":3,"name":"scope","description":"Current season, alltime or a chosen season","required":false,
              "choices":[{"name":"Current season","value":"current"},{"name":"Alltime","value":"alltime"},{"name":"Season","value":"season"}]},
             {"type":3,"name":"season","description":"Season for the Season scope","required":false,"autocomplete":true}]""";

    @Test
    void givenExplicitRegistration_whenPosting_thenUpsertOnlyTheStatsCommandWithSubcommandsAndAutocomplete() throws Exception {
        // given
        var builder = RestClient.builder().baseUrl("https://discord.com/api/v10").defaultHeader("Authorization", "Bot test-existing-token");
        var server = MockRestServiceServer.bindTo(builder).build();
        var discord = new DiscordRestClient(builder.build());
        server.expect(requestTo("https://discord.com/api/v10/applications/900/guilds/100/commands"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"type":1,"name":"stats","description":"Driver and team statistics","options":[
                          {"type":1,"name":"driver","description":"Statistics of one driver","options":%s},
                          {"type":1,"name":"team","description":"Statistics of one team","options":%s}]}"""
                        .formatted(OPTIONS.formatted("PSN ID or nickname"), OPTIONS.formatted("Team name or short name")), org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"id\":\"801\",\"guild_id\":\"100\",\"name\":\"stats\"}", MediaType.APPLICATION_JSON));
        // when
        discord.registerStatsGuildCommand("900", "100");
        // then
        server.verify();
    }
}
