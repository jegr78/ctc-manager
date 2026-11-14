package org.ctc.admin.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.admin.dto.PrivatePickBindingForm;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.picks.DiscordPicksGateway;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.PickChannelPermissionSource;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrivatePicksAdminServiceTest {
    final TeamPickChannelBindingRepository bindings = mock(TeamPickChannelBindingRepository.class);
    final SeasonRepository seasons = mock(SeasonRepository.class);
    final TeamRepository teams = mock(TeamRepository.class);
    final MatchRepository matches = mock(MatchRepository.class);
    final DiscordGlobalConfigRepository configs = mock(DiscordGlobalConfigRepository.class);
    final DiscordRestClient discord = mock(DiscordRestClient.class);
    final PickChannelPermissionSource permissions = mock(PickChannelPermissionSource.class);
    final DiscordPicksGateway gateway = mock(DiscordPicksGateway.class);
    final PrivatePicksAdminService service = new PrivatePicksAdminService(bindings, seasons, teams, matches, configs,
            discord, permissions, gateway);
    final Season season = new Season("Test binding season");
    final Team team = new Team();
    final DiscordGlobalConfig config = new DiscordGlobalConfig();

    @BeforeEach
    void setUp() throws Exception {
        // given
        season.setId(UUID.randomUUID()); team.setId(UUID.randomUUID()); team.setName("Test team"); team.setShortName("TGB");
        config.setGuildId("100"); config.setBotApplicationId("900");
        when(configs.findFirstByOrderByIdAsc()).thenReturn(config);
        when(seasons.findByIdForUpdate(season.getId())).thenReturn(Optional.of(season));
        when(seasons.findById(season.getId())).thenReturn(Optional.of(season));
        when(teams.findById(team.getId())).thenReturn(Optional.of(team));
        when(discord.fetchBotUser()).thenReturn(new DiscordRestClient.BotUser("900", "Test bot", "0"));
        when(permissions.hasCurrentPrivateAccess("100", "200", "900")).thenReturn(true);
        when(bindings.saveAndFlush(any())).thenAnswer(invocation -> {
            var row = (TeamPickChannelBinding) invocation.getArgument(0);
            if (row.getId() == null) row.setId(UUID.randomUUID());
            return row;
        });
    }

    @Test
    void givenValidPrivateChannel_whenSaving_thenBindExactScopeUnderSeasonLockWithoutCreatingOrPosting() throws Exception {
        // when
        var id = service.save(form());
        // then
        assertThat(id).isNotNull();
        var saved = org.mockito.ArgumentCaptor.forClass(TeamPickChannelBinding.class);
        verify(bindings).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSeason()).isSameAs(season);
        assertThat(saved.getValue().getTeam()).isSameAs(team);
        assertThat(saved.getValue().getGuildId()).isEqualTo("100");
        assertThat(saved.getValue().getChannelId()).isEqualTo("200");
        var order = inOrder(seasons, bindings);
        order.verify(seasons).findByIdForUpdate(season.getId());
        order.verify(bindings).saveAndFlush(any());
        verify(discord).fetchBotUser(); verifyNoMoreInteractions(discord);
    }

    @Test
    void givenExistingTeamBinding_whenSavingNewChannel_thenReplaceOnlyThatExactBinding() {
        // given
        var existing = new TeamPickChannelBinding(season, team, "100", "199"); existing.setId(UUID.randomUUID());
        when(bindings.findBySeasonIdAndTeamIdAndGuildId(season.getId(), team.getId(), "100")).thenReturn(Optional.of(existing));
        // when / then
        assertThat(service.save(form())).isEqualTo(existing.getId());
        assertThat(existing.getChannelId()).isEqualTo("200");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-season", "missing-team", "malformed-channel", "missing-guild", "shared-channel", "public-or-no-access", "lookup-failure", "channel-owned-by-other-team"})
    void givenInvalidOrUnsafeBinding_whenSaving_thenRefuseWithoutPersisting(String invalid) throws Exception {
        // given
        var form = form();
        switch (invalid) {
            case "missing-season" -> when(seasons.findByIdForUpdate(season.getId())).thenReturn(Optional.empty());
            case "missing-team" -> when(teams.findById(team.getId())).thenReturn(Optional.empty());
            case "malformed-channel" -> {
                form.setChannelId("invalid");
                when(permissions.hasCurrentPrivateAccess("100", "invalid", "900")).thenReturn(true);
            }
            case "missing-guild" -> config.setGuildId("");
            case "shared-channel" -> when(matches.existsByDiscordChannelId("200")).thenReturn(true);
            case "public-or-no-access" -> when(permissions.hasCurrentPrivateAccess("100", "200", "900")).thenReturn(false);
            case "lookup-failure" -> when(discord.fetchBotUser()).thenThrow(new org.ctc.discord.exception.DiscordTransientException("Test unavailable", null));
            case "channel-owned-by-other-team" -> {
                var other = new Team(); other.setId(UUID.randomUUID());
                var row = new TeamPickChannelBinding(season, other, "100", "200"); row.setId(UUID.randomUUID());
                when(bindings.findBySeasonIdAndGuildIdAndChannelId(season.getId(), "100", "200")).thenReturn(Optional.of(row));
            }
        }
        // when / then
        assertThatThrownBy(() -> service.save(form)).isInstanceOf(BusinessRuleException.class);
        verify(bindings, never()).saveAndFlush(any());
    }

    @Test
    void givenBindingToRemove_whenDeleting_thenTakeSeasonLockAndUseCurrentBinding() {
        // given
        var row = new TeamPickChannelBinding(season, team, "100", "200"); row.setId(UUID.randomUUID());
        when(bindings.findById(row.getId())).thenReturn(Optional.of(row));
        when(bindings.findCurrentForUpdate(season.getId(), team.getId(), "100", "200")).thenReturn(Optional.of(row));
        // when
        service.delete(row.getId());
        // then
        var order = inOrder(seasons, bindings);
        order.verify(seasons).findByIdForUpdate(season.getId());
        order.verify(bindings).findCurrentForUpdate(season.getId(), team.getId(), "100", "200");
        order.verify(bindings).delete(row);
        verifyNoInteractions(discord, permissions);
    }

    @Test
    void givenDefaultDisabledGateway_whenReadingSetup_thenShowHealthAndChoicesWithoutExternalActivity() {
        // given
        when(gateway.health()).thenReturn(DiscordPicksGateway.Health.DISABLED);
        when(seasons.findAll()).thenReturn(List.of(season)); when(teams.findAll()).thenReturn(List.of(team));
        when(bindings.findAllByOrderByGuildIdAscChannelIdAsc()).thenReturn(List.of());
        // when
        var view = service.overview();
        // then
        assertThat(view).isNotNull();
        assertThat(view.health()).isEqualTo("Disabled");
        assertThat(view.registrationReady()).as("registration needs only the configured bot identity").isTrue();
        assertThat(view.seasons()).extracting(PrivatePicksAdminService.Choice::id).containsExactly(season.getId());
        assertThat(view.teams()).extracting(PrivatePicksAdminService.Choice::id).containsExactly(team.getId());
        verifyNoInteractions(discord, permissions);
        verify(configs, never()).save(any());
    }

    @Test
    void givenExplicitRegistrationAfterPrivacyAcceptance_whenRegistering_thenUpsertOnlyPicksInConfiguredGuild() throws Exception {
        // given
        // when
        service.registerCommand();
        // then
        verify(discord).fetchBotUser();
        verify(discord).registerPicksGuildCommand("900", "100");
        verifyNoMoreInteractions(discord);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-application", "identity-mismatch"})
    void givenUnavailableRegistration_whenRequesting_thenMakeNoCommandMutation(String invalid) throws Exception {
        // given
        if (invalid.equals("missing-application")) config.setBotApplicationId("");
        if (invalid.equals("identity-mismatch")) config.setBotApplicationId("901");
        // when / then
        assertThatThrownBy(service::registerCommand).isInstanceOf(BusinessRuleException.class);
        verify(discord, never()).registerPicksGuildCommand(anyString(), anyString());
    }

    PrivatePickBindingForm form() {
        var form = new PrivatePickBindingForm(); form.setSeasonId(season.getId()); form.setTeamId(team.getId()); form.setChannelId("200");
        return form;
    }
}
