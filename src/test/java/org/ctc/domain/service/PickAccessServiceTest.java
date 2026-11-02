package org.ctc.domain.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PickAccessServiceTest {
    static final String GUILD = "100000000000000001", CHANNEL = "200000000000000002", USER = "300000000000000003";
    final TeamPickChannelBindingRepository bindings = mock(TeamPickChannelBindingRepository.class);
    final PickChannelPermissionSource permissions = mock(PickChannelPermissionSource.class);
    final DiscordGlobalConfigRepository configs = mock(DiscordGlobalConfigRepository.class);
    final MatchRepository matches = mock(MatchRepository.class);
    final RaceRepository races = mock(RaceRepository.class);
    final MatchdayPickRepository picks = mock(MatchdayPickRepository.class);
    final PickSessionEpoch epoch = new PickSessionEpoch();
    final PickAccessService service = new PickAccessService(bindings, permissions, configs, matches, races, picks, epoch);
    final Season season = new Season("Test private channel access");
    final Team home = new Team();
    final Team away = new Team();
    final Race race = new Race();
    TeamPickChannelBinding binding;

    @BeforeEach
    void setUp() {
        // given
        season.setId(UUID.randomUUID()); home.setId(UUID.randomUUID()); away.setId(UUID.randomUUID());
        var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
        var day = new Matchday(phase, "Test authorization target", 1);
        day.setId(UUID.randomUUID());
        race.setId(UUID.randomUUID()); race.setMatchday(day); race.setMatch(new Match(day, home, away));
        race.setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0)); race.setPicksManaged(true);
        binding = new TeamPickChannelBinding(season, home, GUILD, CHANNEL);
        var config = new DiscordGlobalConfig(); config.setGuildId(GUILD);
        when(configs.findFirstByOrderByIdAsc()).thenReturn(config);
        when(bindings.findByGuildIdAndChannelId(GUILD, CHANNEL)).thenReturn(List.of(binding));
        when(permissions.hasCurrentPrivateAccess(GUILD, CHANNEL, USER)).thenReturn(true);
        when(races.findById(race.getId())).thenReturn(Optional.of(race));
    }

    @Test
    void givenPrivateChannelMemberWithoutManagerRoleOrSelectedRace_whenAuthorizingChannel_thenReturnOnlyExplicitScope() {
        // when
        var result = new AtomicReference<ChannelPickActor>();
        assertThatCode(() -> result.set(service.authorizeChannel(GUILD, CHANNEL, USER))).doesNotThrowAnyException();
        // then
        assertThat(result.get()).isEqualTo(channel());
        verify(permissions).hasCurrentPrivateAccess(GUILD, CHANNEL, USER);
        verifyNoInteractions(races, picks);
        verify(configs, never()).save(any());
    }

    @Test
    void givenExactChannelBoundInTwoSeasons_whenAuthorizingBeforeChoosingTarget_thenRetainBothExplicitScopes() {
        // given
        var other = new Season("Test another explicit season"); other.setId(UUID.randomUUID());
        when(bindings.findByGuildIdAndChannelId(GUILD, CHANNEL)).thenReturn(List.of(binding,
                new TeamPickChannelBinding(other, home, GUILD, CHANNEL)));
        var result = new AtomicReference<ChannelPickActor>();
        // when
        assertThatCode(() -> result.set(service.authorizeChannel(GUILD, CHANNEL, USER))).doesNotThrowAnyException();
        // then
        assertThat(result.get().bindings()).containsExactlyInAnyOrder(new ChannelPickActor.Binding(season.getId(), home.getId()),
                new ChannelPickActor.Binding(other.getId(), home.getId()));
        verifyNoInteractions(races, picks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dm", "other-guild", "invalid-user", "unbound", "shared-match", "revoked", "lookup-failed", "ambiguous"})
    void givenUnauthorizedChannelContext_whenAuthorizing_thenDenyWithoutAnyPrivateTargetRead(String cause) {
        // given
        if (cause.equals("unbound")) when(bindings.findByGuildIdAndChannelId(GUILD, CHANNEL)).thenReturn(List.of());
        if (cause.equals("shared-match")) when(matches.existsByDiscordChannelId(CHANNEL)).thenReturn(true);
        if (cause.equals("revoked")) when(permissions.hasCurrentPrivateAccess(GUILD, CHANNEL, USER)).thenReturn(false);
        if (cause.equals("lookup-failed")) when(permissions.hasCurrentPrivateAccess(GUILD, CHANNEL, USER)).thenThrow(new IllegalStateException("offline fixture"));
        if (cause.equals("ambiguous")) when(bindings.findByGuildIdAndChannelId(GUILD, CHANNEL)).thenReturn(List.of(binding,
                new TeamPickChannelBinding(season, away, GUILD, CHANNEL)));
        // when / then
        assertThatThrownBy(() -> service.authorizeChannel(cause.equals("dm") ? null : cause.equals("other-guild") ? "999" : GUILD,
                CHANNEL, cause.equals("invalid-user") ? "spoofed" : USER)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("unavailable");
        verifyNoInteractions(races, picks);
    }

    @Test
    void givenCurrentChannelScope_whenSelectingEffectiveHomeRace_thenAuthorizeItsExactStoredIdentity() {
        // given
        var result = new AtomicReference<AuthorizedPickActor>();
        // when
        assertThatCode(() -> result.set(service.authorizeTarget(channel(), race.getId()))).doesNotThrowAnyException();
        // then
        assertThat(result.get().raceId()).isEqualTo(race.getId());
        assertThat(result.get().seasonId()).isEqualTo(season.getId());
        assertThat(result.get().teamId()).isEqualTo(home.getId());
        assertThat(result.get().userId()).isEqualTo(USER);
        assertThat(result.get().channel()).isEqualTo(new AuthorizedPickActor.ChannelContext(GUILD, CHANNEL, epoch.getAsLong()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-home", "wrong-season", "unmanaged", "missing-race", "binding-revoked", "permission-revoked", "changed-opponent"})
    void givenStaleOrForeignTarget_whenSelecting_thenDenyAccessToPrivatePick(String cause) {
        // given
        if (cause.equals("wrong-home")) { race.setHomeTeamOverride(away); race.setAwayTeamOverride(home); }
        if (cause.equals("wrong-season")) { var other = new Season("Test foreign season"); other.setId(UUID.randomUUID()); race.getMatchday().getPhase().setSeason(other); }
        if (cause.equals("unmanaged")) race.setPicksManaged(false);
        if (cause.equals("missing-race")) when(races.findById(race.getId())).thenReturn(Optional.empty());
        if (cause.equals("binding-revoked")) when(bindings.findByGuildIdAndChannelId(GUILD, CHANNEL)).thenReturn(List.of());
        if (cause.equals("permission-revoked")) when(permissions.hasCurrentPrivateAccess(GUILD, CHANNEL, USER)).thenReturn(false);
        if (cause.equals("changed-opponent")) {
            var saved = new MatchdayPick(race, season, home);
            when(picks.findSnapshotByRaceId(race.getId())).thenReturn(Optional.of(saved));
            var opponent = new Team(); opponent.setId(UUID.randomUUID()); race.getMatch().setAwayTeam(opponent);
        }
        // when / then
        assertThatThrownBy(() -> service.authorizeTarget(channel(), race.getId())).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void givenForgedTeamScopeInOldActor_whenSelectingForeignHome_thenRebuildAuthorityFromCurrentBinding() {
        // given
        race.setHomeTeamOverride(away); race.setAwayTeamOverride(home);
        var forged = new ChannelPickActor(GUILD, CHANNEL, USER, Set.of(new ChannelPickActor.Binding(season.getId(), away.getId())));
        // when / then
        assertThatThrownBy(() -> service.authorizeTarget(forged, race.getId())).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void givenHistoricalOwnRaceAfterResults_whenRecoveringReceipt_thenKeepAccessSeparateFromNewSubmissionEligibility() {
        // given
        race.getResults().add(new RaceResult());
        // when / then
        assertThatCode(() -> service.authorizeTarget(channel(), race.getId())).doesNotThrowAnyException();
    }

    @Test
    void givenAuthorizedContext_whenRecheckingUnderWriteLock_thenReadCurrentBindingAndPermissionsAgain() {
        // given
        var actor = new AuthorizedPickActor(race.getId(), season.getId(), home.getId(), USER,
                new AuthorizedPickActor.ChannelContext(GUILD, CHANNEL, epoch.getAsLong()));
        when(bindings.findCurrentForUpdate(season.getId(), home.getId(), GUILD, CHANNEL)).thenReturn(Optional.of(binding));
        // when
        service.recheck(actor);
        // then
        verify(bindings).findCurrentForUpdate(season.getId(), home.getId(), GUILD, CHANNEL);
        verify(permissions).hasCurrentPrivateAccess(GUILD, CHANNEL, USER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"removed-binding", "revoked-permission", "restore-generation"})
    void givenRevocationAfterTargetSelection_whenConfirming_thenRejectOldAuthority(String invalid) {
        // given
        var actor = new AuthorizedPickActor(race.getId(), season.getId(), home.getId(), USER,
                new AuthorizedPickActor.ChannelContext(GUILD, CHANNEL, epoch.getAsLong()));
        when(bindings.findCurrentForUpdate(season.getId(), home.getId(), GUILD, CHANNEL)).thenReturn(Optional.of(binding));
        if (invalid.equals("removed-binding")) when(bindings.findCurrentForUpdate(season.getId(), home.getId(), GUILD, CHANNEL)).thenReturn(Optional.empty());
        if (invalid.equals("revoked-permission")) when(permissions.hasCurrentPrivateAccess(GUILD, CHANNEL, USER)).thenReturn(false);
        if (invalid.equals("restore-generation")) epoch.afterRestore(null);
        // when / then
        assertThatThrownBy(() -> service.recheck(actor)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("unavailable");
    }

    ChannelPickActor channel() {
        return new ChannelPickActor(GUILD, CHANNEL, USER, Set.of(new ChannelPickActor.Binding(season.getId(), home.getId())));
    }
}
