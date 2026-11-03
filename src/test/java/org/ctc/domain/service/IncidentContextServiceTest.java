package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.IncidentManagementActor;
import org.ctc.domain.incidents.IncidentMatchRef;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IncidentContextServiceTest {
    final IncidentUnitFixture f = new IncidentUnitFixture();
    final MatchRepository matches = mock(MatchRepository.class);
    final PlayoffMatchupRepository playoffs = mock(PlayoffMatchupRepository.class);
    final SeasonRepository seasons = mock(SeasonRepository.class);
    final IncidentMatchContextRepository contexts = mock(IncidentMatchContextRepository.class);
    final IncidentReportWindowRepository windows = mock(IncidentReportWindowRepository.class);
    final DiscordGlobalConfigRepository configs = mock(DiscordGlobalConfigRepository.class);
    final PickWriteExecutor writes = mock(PickWriteExecutor.class);
    final EntityManager em = mock(EntityManager.class);
    IncidentContextService service;

    @BeforeEach
    void setUp() {
        when(matches.findById(f.match.getId())).thenReturn(Optional.of(f.match));
        when(seasons.findByIdForUpdate(f.season.getId())).thenReturn(Optional.of(f.season));
        var config = new DiscordGlobalConfig(); config.setGuildId(IncidentUnitFixture.GUILD);
        when(configs.findFirstByOrderByIdAsc()).thenReturn(config);
        when(writes.execute(any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        when(contexts.saveAndFlush(any())).thenAnswer(inv -> IncidentUnitFixture.id(inv.getArgument(0)));
        when(windows.save(any())).thenAnswer(inv -> IncidentUnitFixture.id(inv.getArgument(0)));
        service = new IncidentContextService(matches, playoffs, seasons, contexts, windows, configs, em, writes,
                Clock.fixed(IncidentUnitFixture.NOW, ZoneOffset.UTC));
    }

    @Test
    void givenRegularCompletedContestedRace_whenResolving_thenRetainItsCanonicalMatch() {
        f.match.setHomeScore(2); f.match.setAwayScore(1);
        var target = assertDoesNotThrow(() -> service.resolve(f.reference()));
        assertThat(target.reference()).isEqualTo(f.reference());
        assertThat(target.races()).containsExactly(f.race);
        assertThat(target.season()).isSameAs(f.season);
    }

    @Test
    void givenStandalonePlayoff_whenResolving_thenUseItsOwnParticipantsAndRaces() {
        var playoff = playoff(); f.race.setMatch(null);
        var target = assertDoesNotThrow(() -> service.resolve(IncidentMatchRef.playoff(playoff.getId())));
        assertThat(target.reference()).isEqualTo(IncidentMatchRef.playoff(playoff.getId()));
        assertThat(target.home()).isSameAs(f.home); assertThat(target.away()).isSameAs(f.away);
        assertThat(target.races()).containsExactly(f.race);
    }

    @Test
    void givenAuxiliaryMatchForPlayoffRace_whenResolvingBothViews_thenReturnOnePlayoffIdentity() {
        var playoff = playoff();
        var throughMatch = assertDoesNotThrow(() -> service.resolve(f.reference()));
        var direct = assertDoesNotThrow(() -> service.resolve(IncidentMatchRef.playoff(playoff.getId())));
        assertThat(throughMatch.reference()).isEqualTo(direct.reference()).isEqualTo(IncidentMatchRef.playoff(playoff.getId()));
    }

    @Test
    void givenMixedRegularAndPlayoffOwnership_whenResolving_thenRejectAmbiguousScope() {
        playoff();
        var other = new Race(); other.setId(UUID.randomUUID()); other.setMatch(f.match); other.setMatchday(f.day);
        f.match.getRaces().add(other);
        assertThatThrownBy(() -> service.resolve(f.reference())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("ambiguous");
    }

    @Test
    void givenByeOrMissingOpponent_whenResolving_thenRejectUnreportablePairing() {
        f.match.setBye(true);
        assertThatThrownBy(() -> service.resolve(f.reference())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("contested");
        f.match.setBye(false); f.match.setAwayTeam(null);
        assertThatThrownBy(() -> service.resolve(f.reference())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("contested");
    }

    @Test
    void givenArchivedRegularChannel_whenBinding_thenRejectWithoutCreatingAnIncidentWindow() {
        f.match.setDiscordChannelArchivedAt(java.time.LocalDateTime.of(2026, 11, 3, 12, 0));
        assertThatThrownBy(() -> service.bind(f.reference(), IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL,
                new IncidentManagementActor("test-manager"))).isInstanceOf(BusinessRuleException.class).hasMessageContaining("archived");
        verify(contexts, never()).saveAndFlush(any());
    }

    @Test
    void givenWrongGuildOrUnrelatedChannel_whenBinding_thenRejectBeforeCreatingContext() {
        assertThatThrownBy(() -> service.bind(f.reference(), "999", IncidentUnitFixture.CHANNEL, new IncidentManagementActor("test-manager")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("guild");
        assertThatThrownBy(() -> service.bind(f.reference(), IncidentUnitFixture.GUILD, "999", new IncidentManagementActor("test-manager")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("channel");
        verify(contexts, never()).saveAndFlush(any());
    }

    @Test
    void givenExplicitUnambiguousBinding_whenBinding_thenCreateOneDraftWindowWithoutOpeningIt() {
        UUID context = assertDoesNotThrow(() -> service.bind(f.reference(), IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL,
                new IncidentManagementActor("test-manager")));
        var saved = ArgumentCaptor.forClass(IncidentReportWindow.class); verify(windows).save(saved.capture());
        assertThat(saved.getValue().getContext().getId()).isEqualTo(context);
        assertThat(saved.getValue().getState()).isEqualTo(IncidentWindowState.DRAFT);
        assertThat(saved.getValue().getDeadline()).isNull();
        assertThat(saved.getValue().getContext().getBoundBy()).isEqualTo("test-manager");
    }

    private PlayoffMatchup playoff() {
        var playoff = new Playoff(); playoff.setPhase(f.phase);
        var round = new PlayoffRound(); round.setPlayoff(playoff);
        var matchup = new PlayoffMatchup(round, 1); matchup.setId(UUID.randomUUID());
        matchup.setTeam1(f.home); matchup.setTeam2(f.away); matchup.getRaces().add(f.race);
        f.race.setPlayoffMatchup(matchup);
        when(playoffs.findById(matchup.getId())).thenReturn(Optional.of(matchup));
        return matchup;
    }
}
