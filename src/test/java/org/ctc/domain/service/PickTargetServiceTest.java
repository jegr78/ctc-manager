package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Set;
import org.ctc.domain.picks.ChannelPickActor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.AuthorizedPickActor;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.MatchdayPickRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PickTargetServiceTest {
    final SeasonRepository seasons = mock(SeasonRepository.class);
    final RaceRepository races = mock(RaceRepository.class);
    final MatchdayPickRepository picks = mock(MatchdayPickRepository.class);
    final EntityManager entityManager = mock(EntityManager.class);
    final PickCollectionStatus collection = mock(PickCollectionStatus.class);
    final PickAccessService access = mock(PickAccessService.class);
    final PickSessionEpoch epoch = new PickSessionEpoch();
    final PickTargetService service = new PickTargetService(seasons, races, picks, entityManager,
            new PickDeadlinePolicy(Clock.fixed(PickSubmissionServiceTest.NOW, ZoneOffset.UTC)), collection, access, epoch);
    final Season season = new Season("Test_PickTarget");
    final Team home = new Team();
    final Team away = new Team();
    final Race race = new Race();
    AuthorizedPickActor actor;

    @BeforeEach
    void setUp() {
        season.setId(UUID.randomUUID());
        home.setId(UUID.randomUUID());
        home.setShortName("T-HOME");
        away.setId(UUID.randomUUID());
        away.setShortName("T-AWAY");
        var phase = new SeasonPhase();
        phase.setId(UUID.randomUUID());
        phase.setSeason(season);
        phase.setPhaseType(PhaseType.REGULAR);
        phase.setLabel("Test phase");
        var day = new Matchday(phase, "Test Target", 1);
        day.setId(UUID.randomUUID());
        day.setGroup(new SeasonPhaseGroup(phase, "Test group", 0));
        day.setPickDeadline(LocalDateTime.of(2035, 1, 1, 12, 0));
        var match = new Match(day, home, away);
        match.setId(UUID.randomUUID());
        race.setId(UUID.randomUUID());
        race.setMatchday(day);
        race.setMatch(match);
        race.setDateTime(LocalDateTime.of(2026, 11, 10, 20, 0));
        race.setPicksManaged(true);
        season.setPicksCollectionEnabled(true);
        when(collection.isAvailable()).thenReturn(true);
        actor = new AuthorizedPickActor(race.getId(), season.getId(), home.getId(), "12345");
        when(seasons.findByIdForUpdate(season.getId())).thenReturn(Optional.of(season));
        when(races.findById(race.getId())).thenReturn(Optional.of(race));
        when(races.findBySeasonForSelectionUpdate(season.getId())).thenReturn(List.of(race));
        when(picks.findByRaceId(race.getId())).thenReturn(Optional.empty());
        when(access.authorizeChannel("123", "456", "12345")).thenAnswer(invocation -> channel());
    }

    @Test
    void givenScheduledExactHomeTargetInInactiveSeason_whenLocking_thenAllowOwnershipWithoutAnActiveSeasonRestriction() {
        // given / when / then
        assertThat(season.isActive()).isFalse();
        var target = service.lockForTeam(race.getId(), actor);
        assertThat(target).isNotNull();
        assertThat(target.race()).isSameAs(race);
        assertThat(service.isEligible(race)).isTrue();
        verify(seasons).findByIdForUpdate(season.getId());
        verify(races).findBySeasonForSelectionUpdate(season.getId());
    }

    @Test
    void givenReversedLeg_whenCheckingHomeOwnership_thenUseEffectiveHome() {
        // given / when / then
        race.setHomeTeamOverride(away);
        race.setAwayTeamOverride(home);
        var reversed = new AuthorizedPickActor(race.getId(), season.getId(), away.getId(), "12345");
        assertThat(service.lockForTeam(race.getId(), reversed)).isNotNull();
        assertThatThrownBy(() -> service.lockForTeam(race.getId(), actor)).isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"team", "season", "race", "user"})
    void givenForeignOrInvalidActor_whenLocking_thenDenyWithoutReturningPrivatePick(String invalid) {
        // given / when / then
        var foreign = new AuthorizedPickActor(invalid.equals("race") ? UUID.randomUUID() : race.getId(),
                invalid.equals("season") ? UUID.randomUUID() : season.getId(),
                invalid.equals("team") ? away.getId() : home.getId(), invalid.equals("user") ? "" : "12345");
        assertThatThrownBy(() -> service.lockForTeam(race.getId(), foreign))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("target");
    }

    @ParameterizedTest
    @ValueSource(strings = {"unscheduled", "unmanaged", "bye", "no-away", "results", "score", "walkover", "detached"})
    void givenIneligibleTarget_whenAssessing_thenDenyTeamEditing(String invalid) {
        // given / when / then
        switch (invalid) {
            case "unscheduled" -> race.setDateTime(null);
            case "unmanaged" -> race.setPicksManaged(false);
            case "bye" -> race.getMatch().setBye(true);
            case "no-away" -> race.getMatch().setAwayTeam(null);
            case "results" -> race.getResults().add(new RaceResult());
            case "score" -> race.getMatch().setHomeScore(0);
            case "walkover" -> race.getMatch().setWalkoverTeam(home);
            case "detached" -> race.setMatch(null);
            default -> throw new AssertionError(invalid);
        }
        assertThat(service.isEligible(race)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"round", "opponent"})
    void givenPersistedPickTargetIdentity_whenScheduleWasReassigned_thenDenyCurrentTargetAccess(String change) {
        // given
        var pick = new MatchdayPick(race, season, home);
        when(picks.findByRaceId(race.getId())).thenReturn(Optional.of(pick));
        if (change.equals("round")) {
            var other = new Matchday(race.getMatchday().getPhase(), "Another target round", 2);
            other.setId(UUID.randomUUID());
            race.setMatchday(other);
        } else {
            var opponent = new Team();
            opponent.setId(UUID.randomUUID());
            race.getMatch().setAwayTeam(opponent);
        }
        // when / then
        assertThatThrownBy(() -> service.lockForTeam(race.getId(), actor))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("target");
    }

    @Test
    void givenManagementActor_whenLocking_thenUseSameSeasonProtocolAndRejectMissingIdentity() {
        // given / when / then
        assertThat(service.lockForManagement(race.getId(), new ManagementPickActor("test-admin"))).isNotNull();
        assertThatThrownBy(() -> service.lockForManagement(race.getId(), new ManagementPickActor(" ")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("actor");
    }
    @Test
    void givenBoundChannel_whenListingTargets_thenShowOnlyEligibleExactHomeWithFullContext() {
        // given
        var foreign = new Race();
        foreign.setId(UUID.randomUUID());
        foreign.setMatchday(race.getMatchday());
        foreign.setMatch(race.getMatch());
        foreign.setHomeTeamOverride(away);
        foreign.setAwayTeamOverride(home);
        foreign.setDateTime(race.getDateTime().plusHours(1));
        foreign.setPicksManaged(true);
        when(seasons.findById(season.getId())).thenReturn(Optional.of(season));
        when(races.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(foreign, race));
        // when
        var targets = service.listHomeRaces(channel());
        // then
        assertThat(targets).hasSize(1);
        assertThat(targets.getFirst().raceId()).isEqualTo(race.getId());
        assertThat(targets.getFirst().label()).contains(season.getDisplayLabel(), "Test phase", "Test group", "Test Target", "T-AWAY", "Leg 1");
        assertThat(targets.getFirst().state()).isEqualTo(PickState.MISSING);
        assertThat(targets.getFirst().version()).isZero();
        assertThat(targets.getFirst().editable()).isTrue();
    }

    @Test
    void givenApprovedOrMissingDeadlineTarget_whenListing_thenKeepServerEditingReason() {
        // given
        when(seasons.findById(season.getId())).thenReturn(Optional.of(season));
        when(races.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(race));
        race.getMatchday().setPickDeadline(null);
        // when / then
        var missing = service.listHomeRaces(channel());
        assertThat(missing).hasSize(1);
        assertThat(missing.getFirst().editable()).isFalse();
        assertThat(missing.getFirst().reason()).contains("deadline", "management");
        var pick = new MatchdayPick(race, season, home);
        pick.submit();
        pick.approve(1);
        when(picks.findSnapshotByRaceId(race.getId())).thenReturn(Optional.of(pick));
        var approved = service.listHomeRaces(channel());
        assertThat(approved).hasSize(1);
        assertThat(approved.getFirst().editable()).isFalse();
        assertThat(approved.getFirst().reason()).contains("Approved");
        assertThat(approved.getFirst().version()).isEqualTo(2);
    }

    @Test
    void givenUnauthenticatedChannelActor_whenListing_thenDenyBeforeAnyPrivateRead() {
        // given / when / then
        assertThatThrownBy(() -> service.listHomeRaces(null)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("channel");
    }

    @Test
    void givenRevokedChannelActor_whenListingTargets_thenRecheckBeforeAnyPrivateRaceRead() {
        // given
        when(access.authorizeChannel("123", "456", "12345")).thenThrow(new BusinessRuleException("Channel access unavailable"));
        // when / then
        assertThatThrownBy(() -> service.listHomeRaces(channel())).isInstanceOf(BusinessRuleException.class);
        verify(races, never()).findByMatchdaySeasonId(any());
    }

    @Test
    void givenChannelBoundActor_whenLockingTarget_thenRecheckOnlyAfterAcquiringSeasonAndRaceLocks() {
        // given
        var channelActor = new AuthorizedPickActor(race.getId(), season.getId(), home.getId(), "12345",
                new AuthorizedPickActor.ChannelContext("123", "456", 0));
        // when
        service.lockForTeam(race.getId(), channelActor);
        // then
        var order = inOrder(seasons, races, access);
        order.verify(seasons).findByIdForUpdate(season.getId());
        order.verify(races).findBySeasonForSelectionUpdate(season.getId());
        order.verify(access).recheck(channelActor);
    }

    private ChannelPickActor channel() {
        return new ChannelPickActor("123", "456", "12345", Set.of(new ChannelPickActor.Binding(season.getId(), home.getId())));
    }

    @Test
    void givenTwoSeasonBindingsAndReturnLeg_whenListing_thenRetainBothSeasonContextsAndExactLegOwnership() {
        // given
        var second = new Season("Test second season"); second.setId(UUID.randomUUID()); second.setPicksCollectionEnabled(true);
        var phase = new SeasonPhase(); phase.setId(UUID.randomUUID()); phase.setSeason(second); phase.setPhaseType(PhaseType.REGULAR); phase.setLabel("Second league");
        var day = new Matchday(phase, "Second matchday", 2); day.setId(UUID.randomUUID()); day.setPickDeadline(race.getMatchday().getPickDeadline());
        var match = new Match(day, away, home); match.setId(UUID.randomUUID());
        var outbound = new Race(); outbound.setId(UUID.randomUUID()); outbound.setMatchday(day); outbound.setMatch(match);
        outbound.setDateTime(race.getDateTime()); outbound.setPicksManaged(true);
        var returning = new Race(); returning.setId(UUID.randomUUID()); returning.setMatchday(day); returning.setMatch(match);
        returning.setDateTime(race.getDateTime().plusDays(1)); returning.setHomeTeamOverride(home); returning.setAwayTeamOverride(away); returning.setPicksManaged(true);
        var both = new ChannelPickActor("123", "456", "12345", Set.of(new ChannelPickActor.Binding(season.getId(), home.getId()),
                new ChannelPickActor.Binding(second.getId(), home.getId())));
        when(access.authorizeChannel("123", "456", "12345")).thenReturn(both);
        when(seasons.findById(season.getId())).thenReturn(Optional.of(season)); when(seasons.findById(second.getId())).thenReturn(Optional.of(second));
        when(races.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(race));
        when(races.findByMatchdaySeasonId(second.getId())).thenReturn(List.of(outbound, returning));
        // when
        var listed = service.listHomeRaces(both);
        // then
        assertThat(listed).extracting(org.ctc.domain.picks.HomeRaceTarget::raceId).containsExactlyInAnyOrder(race.getId(), returning.getId());
        assertThat(listed.stream().filter(row -> row.raceId().equals(returning.getId())).findFirst().orElseThrow().label())
                .contains(second.getDisplayLabel(), "Second matchday", "T-AWAY", "Leg 2");
    }

    @Test
    void givenOwnConfirmedPickAfterRaceDecision_whenListing_thenKeepReadOnlyRecoveryTarget() {
        // given
        var pick = new MatchdayPick(race, season, home); pick.submit();
        race.getMatch().setHomeScore(0);
        when(picks.findSnapshotByRaceId(race.getId())).thenReturn(Optional.of(pick));
        when(seasons.findById(season.getId())).thenReturn(Optional.of(season));
        when(races.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(race));
        // when
        var listed = service.listHomeRaces(channel());
        // then
        assertThat(listed).hasSize(1);
        assertThat(listed.getFirst().editable()).isFalse();
        assertThat(listed.getFirst().state()).isEqualTo(PickState.SUBMITTED);
    }

    @Test
    void givenManagementActorFromOldRestoreGeneration_whenLocking_thenRejectInsideAdmittedTransactionAfterTargetLocks() {
        // given
        var manager = new ManagementPickActor("test-manager", epoch.getAsLong()); epoch.afterRestore(null);
        // when / then
        assertThatThrownBy(() -> service.lockForManagement(race.getId(), manager)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("reset");
        var order = inOrder(seasons, races);
        order.verify(races).findById(race.getId()); order.verify(seasons).findByIdForUpdate(season.getId());
        order.verify(races).findBySeasonForSelectionUpdate(season.getId());
    }

}
