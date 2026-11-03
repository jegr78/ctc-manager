package org.ctc.domain.service;

import java.util.*;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentAccessServiceTest {
    static final String USER = "400000000000000004", ROLE = "300000000000000003";
    final IncidentUnitFixture f = new IncidentUnitFixture();
    final IncidentContextService contexts = mock(IncidentContextService.class);
    final IncidentMatchContextRepository bindings = mock(IncidentMatchContextRepository.class);
    final IncidentReportWindowRepository windows = mock(IncidentReportWindowRepository.class);
    final MatchRepository matches = mock(MatchRepository.class);
    final DiscordGlobalConfigRepository configs = mock(DiscordGlobalConfigRepository.class);
    final IncidentChannelAccessSource source = mock(IncidentChannelAccessSource.class);
    final IncidentAccessService access = new IncidentAccessService(contexts, bindings, windows, matches, configs, source);

    @BeforeEach
    void setUp() {
        var config = new DiscordGlobalConfig(); config.setGuildId(IncidentUnitFixture.GUILD);
        when(configs.findFirstByOrderByIdAsc()).thenReturn(config);
        when(bindings.findByGuildIdAndChannelId(IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL)).thenReturn(Optional.of(f.context));
        when(contexts.resolve(f.reference())).thenAnswer(call -> f.target());
        when(matches.findAllByDiscordChannelIdOrderByIdAsc(IncidentUnitFixture.CHANNEL)).thenReturn(List.of(f.match));
        when(source.currentRoles(IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL, USER)).thenReturn(Set.of(ROLE, "900000000000000009"));
        when(windows.findById(f.window.getId())).thenReturn(Optional.of(f.window));
    }

    @Test
    void givenOneRelevantRoleAmongUnrelatedRoles_whenAuthorizing_thenAttributeItsExactTeam() {
        var actor = assertDoesNotThrowAccess();
        assertThat(actor.contextId()).isEqualTo(f.context.getId());
        assertThat(actor.guildId()).isEqualTo(IncidentUnitFixture.GUILD); assertThat(actor.channelId()).isEqualTo(IncidentUnitFixture.CHANNEL);
        assertThat(actor.userId()).isEqualTo(USER); assertThat(actor.party()).isEqualTo(f.party());
        assertThat(actor.toString()).doesNotContain(USER, f.home.getName());
    }

    @Test
    void givenInheritedParticipantRole_whenAuthorizing_thenKeepTheSubteamIdentity() {
        var parent = parent("Test organization", ROLE); f.home.setParentTeam(parent); f.home.setDiscordRoleId(null);
        var actor = assertDoesNotThrowAccess();
        assertThat(actor.party().getType()).isEqualTo(IncidentPartyType.TEAM); assertThat(actor.party().getId()).isEqualTo(f.home.getId());
        assertThat(actor.party().getRoleId()).isEqualTo(ROLE);
    }

    @Test
    void givenBothParticipantsSharingOneOrganizationRole_whenAuthorizing_thenAttributeOrganizationWithoutSubteamChoice() {
        var parent = parent("Test shared organization", ROLE);
        f.home.setParentTeam(parent); f.away.setParentTeam(parent); f.home.setDiscordRoleId(null); f.away.setDiscordRoleId(null);
        var actor = assertDoesNotThrowAccess();
        assertThat(actor.party()).isEqualTo(new IncidentParty(IncidentPartyType.ORGANIZATION, parent.getId(), parent.getName(), ROLE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "opposing", "unrelated-shared", "lookup-failed", "access-revoked"})
    void givenUnverifiedOrAmbiguousCurrentRoles_whenAuthorizing_thenDenyWithoutChoosingASide(String scenario) {
        switch (scenario) {
            case "none" -> when(source.currentRoles(IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL, USER)).thenReturn(Set.of("900000000000000009"));
            case "opposing" -> when(source.currentRoles(IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL, USER)).thenReturn(Set.of(ROLE, f.away.getDiscordRoleId()));
            case "unrelated-shared" -> f.away.setDiscordRoleId(ROLE);
            case "lookup-failed" -> when(source.currentRoles(IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL, USER)).thenThrow(new IllegalStateException("private upstream details"));
            case "access-revoked" -> when(source.currentRoles(IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL, USER)).thenReturn(null);
            default -> throw new AssertionError(scenario);
        }
        assertThatThrownBy(this::authorize).isInstanceOf(BusinessRuleException.class).hasMessageContaining("unavailable").hasMessageNotContaining("private upstream details");
        if (Set.of("none", "opposing", "unrelated-shared").contains(scenario)) assertThatThrownBy(this::authorize).hasMessageContaining("Ask management");
    }

    @ParameterizedTest
    @ValueSource(strings = {"guild", "configured-guild", "channel", "member", "unbound", "legacy", "participants", "season"})
    void givenInvalidChannelOrCanonicalOwnership_whenAuthorizing_thenDenyBeforePrivateData(String scenario) {
        switch (scenario) {
            case "configured-guild" -> { var changed = new DiscordGlobalConfig(); changed.setGuildId("999"); when(configs.findFirstByOrderByIdAsc()).thenReturn(changed); }
            case "unbound" -> when(bindings.findByGuildIdAndChannelId(IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL)).thenReturn(Optional.empty());
            case "legacy" -> { var other = new Match(); other.setId(UUID.randomUUID()); when(matches.findAllByDiscordChannelIdOrderByIdAsc(IncidentUnitFixture.CHANNEL)).thenReturn(List.of(f.match, other)); when(contexts.resolve(IncidentMatchRef.match(other.getId()))).thenThrow(new BusinessRuleException("ambiguous")); }
            case "participants" -> { var other = new Team("Test other", "T-O"); other.setId(UUID.randomUUID()); when(contexts.resolve(f.reference())).thenReturn(new IncidentMatchTarget(f.reference(), f.match, null, f.season, other, f.away, List.of(f.race))); }
            case "season" -> { var other = new Season("Test other season"); other.setId(UUID.randomUUID()); when(contexts.resolve(f.reference())).thenReturn(new IncidentMatchTarget(f.reference(), f.match, null, other, f.home, f.away, List.of(f.race))); }
            default -> { }
        }
        String guild = scenario.equals("guild") ? "999" : IncidentUnitFixture.GUILD;
        String channel = scenario.equals("channel") ? "999" : IncidentUnitFixture.CHANNEL;
        String user = scenario.equals("member") ? "not-numeric" : USER;
        assertThatThrownBy(() -> access.authorizeChannel(guild, channel, user)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("unavailable");
    }

    @Test
    void givenStandalonePlayoffContext_whenAuthorizing_thenUseItsOwnCanonicalParticipants() {
        var playoff = new PlayoffMatchup(); playoff.setId(UUID.randomUUID());
        var context = IncidentUnitFixture.id(new IncidentMatchContext(null, playoff, f.season, f.home, f.away,
                IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL, "test-manager", IncidentUnitFixture.NOW));
        var ref = IncidentMatchRef.playoff(playoff.getId());
        when(bindings.findByGuildIdAndChannelId(IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL)).thenReturn(Optional.of(context));
        when(contexts.resolve(ref)).thenReturn(new IncidentMatchTarget(ref, null, playoff, f.season, f.home, f.away, List.of(f.race)));
        when(matches.findAllByDiscordChannelIdOrderByIdAsc(IncidentUnitFixture.CHANNEL)).thenReturn(List.of());
        assertThat(assertDoesNotThrowAccess().contextId()).isEqualTo(context.getId());
    }

    @Test
    void givenWindowFromAnotherChannel_whenAuthorizing_thenRejectItsForgedIdentifier() {
        var other = new IncidentUnitFixture();
        when(windows.findById(other.window.getId())).thenReturn(Optional.of(other.window));
        assertThatThrownBy(() -> access.authorizeWindow(IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL, USER, other.window.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("unavailable");
        assertThat(org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> access.authorizeWindow(IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL, USER, f.window.getId())).contextId()).isEqualTo(f.context.getId());
    }

    @Test
    void givenOriginalPartyWithRenamedLabelAndChangedRole_whenRechecking_thenPreserveItsSnapshotAndRequireNewEligibility() {
        var original = f.party(); f.home.setName("Test renamed home"); f.home.setDiscordRoleId("600000000000000006");
        when(source.currentRoles(IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL, USER)).thenReturn(Set.of("600000000000000006"));
        var current = assertDoesNotThrowAccess();
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> access.requireCurrent(current, f.context, original));
        assertThat(original.getLabel()).isEqualTo("Test Incident home"); assertThat(original.getRoleId()).isEqualTo(ROLE);
        when(source.currentRoles(IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL, USER)).thenReturn(Set.of());
        assertThatThrownBy(() -> access.requireCurrent(current, f.context, original)).isInstanceOf(BusinessRuleException.class);
        verify(source, times(3)).currentRoles(IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL, USER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"context", "type", "party", "label", "role", "original"})
    void givenForgedOrChangedActorParty_whenRechecking_thenRejectInsteadOfTrustingActorRecord(String changed) {
        var candidate = new IncidentParty(changed.equals("type") ? IncidentPartyType.ORGANIZATION : IncidentPartyType.TEAM,
                changed.equals("party") ? f.away.getId() : f.home.getId(), changed.equals("label") ? "Forged" : f.home.getName(), changed.equals("role") ? "999" : ROLE);
        var actor = new AuthorizedIncidentActor(changed.equals("context") ? UUID.randomUUID() : f.context.getId(), IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL, USER, candidate);
        var original = changed.equals("original") ? new IncidentParty(IncidentPartyType.TEAM, f.away.getId(), f.away.getName(), f.away.getDiscordRoleId()) : null;
        assertThatThrownBy(() -> access.requireCurrent(actor, f.context, original)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("unavailable");
    }

    @ParameterizedTest
    @ValueSource(strings = {"home", "away"})
    void givenOneUnconfiguredOpponentRole_whenExactlyOneKnownParticipantRoleMatches_thenKeepThatTeam(String missing) {
        var expected = missing.equals("home") ? f.away : f.home;
        if (missing.equals("home")) f.home.setDiscordRoleId(null); else f.away.setDiscordRoleId(null);
        when(source.currentRoles(IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL, USER)).thenReturn(Set.of(expected.getDiscordRoleId()));
        var current = assertDoesNotThrowAccess();
        assertThat(current.party().getType()).isEqualTo(IncidentPartyType.TEAM); assertThat(current.party().getId()).isEqualTo(expected.getId());
    }

    private AuthorizedIncidentActor assertDoesNotThrowAccess() { return org.junit.jupiter.api.Assertions.assertDoesNotThrow(this::authorize); }
    private AuthorizedIncidentActor authorize() { return access.authorizeChannel(IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL, USER); }
    private Team parent(String name, String role) { var team = new Team(name, "T-ORG"); team.setId(UUID.randomUUID()); team.setDiscordRoleId(role); return team; }
}
