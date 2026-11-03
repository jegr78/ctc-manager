package org.ctc.discord.incidents;

import java.util.*;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.DiscordRestClient.*;
import org.ctc.discord.dto.PermissionOverwrite;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscordIncidentChannelAccessSourceTest {
    static final String GUILD = "100", CHANNEL = "200", USER = "300", ROLE = "400";
    final DiscordRestClient discord = mock(DiscordRestClient.class);
    final DiscordIncidentChannelAccessSource source = new DiscordIncidentChannelAccessSource(discord);

    @BeforeEach
    void setUp() throws Exception {
        when(discord.fetchAccessGuild(GUILD)).thenReturn(new AccessGuild(GUILD, "900"));
        when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(List.of()));
        when(discord.fetchAccessMember(GUILD, USER)).thenReturn(new AccessMember(new BotUser(USER, "Test reporter", "0"), List.of(ROLE)));
        when(discord.fetchPermissionRoles(GUILD)).thenReturn(List.of(new PermissionRole(GUILD, "1024"), new PermissionRole(ROLE, "0")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"public", "role-allow", "member-allow", "administrator", "owner", "high-bits"})
    void givenCurrentVisibleMatchChannel_whenResolving_thenReturnCurrentMemberRolesWithoutPrivatePicksPolicy(String scenario) throws Exception {
        switch (scenario) {
            case "role-allow" -> when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(List.of(overwrite(GUILD, 0, "0", "1024"), overwrite(ROLE, 0, "1024", "0"))));
            case "member-allow" -> when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(List.of(overwrite(GUILD, 0, "0", "1024"), overwrite(USER, 1, "1024", "0"))));
            case "administrator" -> { when(discord.fetchPermissionRoles(GUILD)).thenReturn(List.of(new PermissionRole(GUILD, "0"), new PermissionRole(ROLE, "8"))); when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(List.of(overwrite(USER, 1, "0", "1024")))); }
            case "owner" -> { when(discord.fetchAccessGuild(GUILD)).thenReturn(new AccessGuild(GUILD, USER)); when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(List.of(overwrite(USER, 1, "0", "1024")))); }
            case "high-bits" -> when(discord.fetchPermissionRoles(GUILD)).thenReturn(List.of(new PermissionRole(GUILD, java.math.BigInteger.ONE.shiftLeft(80).setBit(10).toString()), new PermissionRole(ROLE, "0")));
            default -> { }
        }
        var roles = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> source.currentRoles(GUILD, CHANNEL, USER));
        assertThat(roles).containsExactly(ROLE); assertThatThrownBy(() -> roles.add("999")).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"role-deny", "member-deny", "unknown-role", "permission", "overwrites", "guild-facts", "channel-guild", "channel-id", "member-id", "channel-type", "member", "guild", "duplicate-role", "duplicate-overwrite", "lookup-failed", "invalid-member-role"})
    void givenMissingOrRevokedDiscordFacts_whenResolving_thenDenyWithoutLeakingUpstreamDetails(String scenario) throws Exception {
        switch (scenario) {
            case "role-deny" -> when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(List.of(overwrite(ROLE, 0, "0", "1024"))));
            case "member-deny" -> when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(List.of(overwrite(USER, 1, "0", "1024"))));
            case "unknown-role" -> when(discord.fetchAccessMember(GUILD, USER)).thenReturn(new AccessMember(new BotUser(USER, "Test", "0"), List.of("999")));
            case "permission" -> when(discord.fetchPermissionRoles(GUILD)).thenReturn(List.of(new PermissionRole(GUILD, "not-a-number"), new PermissionRole(ROLE, "0")));
            case "overwrites" -> when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(null));
            case "guild-facts" -> when(discord.fetchAccessGuild(GUILD)).thenReturn(new AccessGuild("999", "900"));
            case "channel-guild" -> when(discord.fetchAccessChannel(CHANNEL)).thenReturn(new AccessChannel(CHANNEL, "999", 0, List.of()));
            case "channel-id" -> when(discord.fetchAccessChannel(CHANNEL)).thenReturn(new AccessChannel("999", GUILD, 0, List.of()));
            case "member-id" -> when(discord.fetchAccessMember(GUILD, USER)).thenReturn(new AccessMember(new BotUser("999", "Test", "0"), List.of(ROLE)));
            case "channel-type" -> when(discord.fetchAccessChannel(CHANNEL)).thenReturn(new AccessChannel(CHANNEL, GUILD, 11, List.of()));
            case "member" -> when(discord.fetchAccessMember(GUILD, USER)).thenReturn(null);
            case "guild" -> when(discord.fetchAccessGuild(GUILD)).thenReturn(null);
            case "duplicate-role" -> when(discord.fetchPermissionRoles(GUILD)).thenReturn(List.of(new PermissionRole(GUILD, "1024"), new PermissionRole(ROLE, "0"), new PermissionRole(ROLE, "8")));
            case "duplicate-overwrite" -> when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(List.of(overwrite(ROLE, 0, "0", "0"), overwrite(ROLE, 0, "1024", "0"))));
            case "lookup-failed" -> when(discord.fetchAccessMember(GUILD, USER)).thenThrow(new IllegalStateException("private callback token"));
            case "invalid-member-role" -> when(discord.fetchAccessMember(GUILD, USER)).thenReturn(new AccessMember(new BotUser(USER, "Test", "0"), Arrays.asList((String) null)));
            default -> throw new AssertionError(scenario);
        }
        assertThatThrownBy(() -> source.currentRoles(GUILD, CHANNEL, USER)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("unavailable").hasMessageNotContaining("private callback token");
    }

    @Test
    void givenEarlierAccess_whenPermissionChanges_thenFetchCurrentFactsAgain() throws Exception {
        var first = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> source.currentRoles(GUILD, CHANNEL, USER));
        assertThat(first).containsExactly(ROLE);
        when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(List.of(overwrite(USER, 1, "0", "1024"))));
        assertThatThrownBy(() -> source.currentRoles(GUILD, CHANNEL, USER)).isInstanceOf(BusinessRuleException.class);
        verify(discord, times(2)).fetchAccessMember(GUILD, USER);
    }

    private AccessChannel channel(List<PermissionOverwrite> rows) { return new AccessChannel(CHANNEL, GUILD, 0, rows); }
    private PermissionOverwrite overwrite(String id, int type, String allow, String deny) { return new PermissionOverwrite(id, type, allow, deny); }
}
