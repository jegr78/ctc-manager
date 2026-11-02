package org.ctc.discord.picks;

import java.math.BigInteger;
import java.util.List;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.DiscordRestClient.*;
import org.ctc.discord.dto.PermissionOverwrite;
import org.ctc.discord.exception.DiscordNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscordPickChannelPermissionSourceTest {
    static final String GUILD = "100000000000000001", CHANNEL = "200000000000000002", USER = "300000000000000003";
    static final String TEAM_ROLE = "400000000000000004", PARENT_ROLE = "500000000000000005", OWNER = "900000000000000009";
    static final String VIEW = "1024";
    final DiscordRestClient discord = mock(DiscordRestClient.class);
    final DiscordPickChannelPermissionSource source = new DiscordPickChannelPermissionSource(discord);

    @BeforeEach
    void setUp() throws Exception {
        // given
        when(discord.fetchAccessGuild(GUILD)).thenReturn(new AccessGuild(GUILD, OWNER));
        when(discord.fetchAccessMember(GUILD, USER)).thenReturn(member(List.of(TEAM_ROLE)));
        when(discord.fetchPermissionRoles(GUILD)).thenReturn(List.of(new PermissionRole(GUILD, VIEW),
                new PermissionRole(TEAM_ROLE, "0"), new PermissionRole(PARENT_ROLE, "0")));
        when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(List.of(overwrite(GUILD, 0, "0", VIEW),
                overwrite(TEAM_ROLE, 0, VIEW, "0"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"role-allow", "member-allow", "role-base", "administrator", "owner", "high-permission-bits"})
    void givenCurrentPrivateChannelViewWithoutManagerRole_whenChecking_thenAllowExactMember(String scenario) throws Exception {
        // given
        switch (scenario) {
            case "member-allow" -> {
                when(discord.fetchAccessMember(GUILD, USER)).thenReturn(member(List.of()));
                when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(List.of(overwrite(GUILD, 0, "0", VIEW),
                        overwrite(USER, 1, VIEW, "0"))));
            }
            case "role-base", "high-permission-bits" -> {
                String permission = scenario.equals("role-base") ? VIEW : BigInteger.ONE.shiftLeft(80).setBit(10).toString();
                when(discord.fetchPermissionRoles(GUILD)).thenReturn(List.of(new PermissionRole(GUILD, "0"), new PermissionRole(TEAM_ROLE, permission)));
                when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(List.of()));
            }
            case "administrator" -> {
                when(discord.fetchPermissionRoles(GUILD)).thenReturn(List.of(new PermissionRole(GUILD, VIEW), new PermissionRole(TEAM_ROLE, "8")));
                when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(List.of(overwrite(GUILD, 0, "0", VIEW), overwrite(USER, 1, "0", VIEW))));
            }
            case "owner" -> {
                when(discord.fetchAccessGuild(GUILD)).thenReturn(new AccessGuild(GUILD, USER));
                when(discord.fetchAccessMember(GUILD, USER)).thenReturn(member(List.of()));
            }
            case "role-allow" -> { }
            default -> throw new AssertionError(scenario);
        }
        // when / then
        assertThat(source.hasCurrentPrivateAccess(GUILD, CHANNEL, USER)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"public", "parent-role-only", "role-deny", "member-deny", "unknown-role", "unknown-permission", "missing-overwrites",
            "wrong-guild", "wrong-channel", "wrong-member", "not-a-text-channel", "missing-member", "missing-guild", "lookup-failed", "ambiguous-role"})
    void givenUnavailableOrUnauthorizedChannelFacts_whenChecking_thenDenyWithoutRoleInference(String scenario) throws Exception {
        // given
        switch (scenario) {
            case "public" -> when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(List.of()));
            case "parent-role-only" -> when(discord.fetchAccessMember(GUILD, USER)).thenReturn(member(List.of(PARENT_ROLE)));
            case "role-deny" -> when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(List.of(overwrite(GUILD, 0, "0", VIEW), overwrite(TEAM_ROLE, 0, "0", VIEW))));
            case "member-deny" -> when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(List.of(overwrite(GUILD, 0, "0", VIEW), overwrite(TEAM_ROLE, 0, VIEW, "0"), overwrite(USER, 1, "0", VIEW))));
            case "unknown-role" -> when(discord.fetchAccessMember(GUILD, USER)).thenReturn(member(List.of("600000000000000006")));
            case "unknown-permission" -> when(discord.fetchPermissionRoles(GUILD)).thenReturn(List.of(new PermissionRole(GUILD, VIEW), new PermissionRole(TEAM_ROLE, "unknown")));
            case "missing-overwrites" -> when(discord.fetchAccessChannel(CHANNEL)).thenReturn(channel(null));
            case "wrong-guild" -> when(discord.fetchAccessChannel(CHANNEL)).thenReturn(new AccessChannel(CHANNEL, "999", 0, channel(List.of()).permissionOverwrites()));
            case "wrong-channel" -> when(discord.fetchAccessChannel(CHANNEL)).thenReturn(new AccessChannel("999", GUILD, 0, List.of()));
            case "wrong-member" -> when(discord.fetchAccessMember(GUILD, USER)).thenReturn(new AccessMember(new BotUser("999", "Test other", "0"), List.of(TEAM_ROLE)));
            case "not-a-text-channel" -> when(discord.fetchAccessChannel(CHANNEL)).thenReturn(new AccessChannel(CHANNEL, GUILD, 1, List.of()));
            case "missing-member" -> when(discord.fetchAccessMember(GUILD, USER)).thenReturn(null);
            case "missing-guild" -> when(discord.fetchAccessGuild(GUILD)).thenReturn(null);
            case "lookup-failed" -> when(discord.fetchAccessMember(GUILD, USER)).thenThrow(mock(DiscordNotFoundException.class));
            case "ambiguous-role" -> when(discord.fetchPermissionRoles(GUILD)).thenReturn(List.of(new PermissionRole(GUILD, VIEW), new PermissionRole(TEAM_ROLE, "0"), new PermissionRole(TEAM_ROLE, "8")));
            default -> throw new AssertionError(scenario);
        }
        // when / then
        assertThat(source.hasCurrentPrivateAccess(GUILD, CHANNEL, USER)).isFalse();
    }

    @Test
    void givenAccessRevokedAfterEarlierInteraction_whenCheckingAgain_thenFetchCurrentFactsAndDeny() throws Exception {
        // given
        when(discord.fetchAccessMember(GUILD, USER)).thenReturn(member(List.of(TEAM_ROLE)), member(List.of(PARENT_ROLE)));
        // when / then
        assertThat(source.hasCurrentPrivateAccess(GUILD, CHANNEL, USER)).isTrue();
        assertThat(source.hasCurrentPrivateAccess(GUILD, CHANNEL, USER)).isFalse();
        verify(discord, times(2)).fetchAccessGuild(GUILD);
        verify(discord, times(2)).fetchAccessChannel(CHANNEL);
        verify(discord, times(2)).fetchAccessMember(GUILD, USER);
        verify(discord, times(2)).fetchPermissionRoles(GUILD);
    }

    static AccessMember member(List<String> roles) { return new AccessMember(new BotUser(USER, "Test private member", "0"), roles); }
    static AccessChannel channel(List<PermissionOverwrite> overwrites) { return new AccessChannel(CHANNEL, GUILD, 0, overwrites); }
    static PermissionOverwrite overwrite(String id, int type, String allow, String deny) { return new PermissionOverwrite(id, type, allow, deny); }
}
