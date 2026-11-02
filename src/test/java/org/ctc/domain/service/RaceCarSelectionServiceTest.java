package org.ctc.domain.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceCarSelectionServiceTest {
    @Mock SeasonRepository seasons;
    @Mock TeamRepository teams;
    @Mock RaceRepository races;
    @Mock PickReservationService reservations;
    @InjectMocks RaceCarSelectionService service;
    Season season;
    Team home;
    Team other;
    Car mr;
    Car rr;
    Car fr;
    SeasonCarPoolEntry combined;
    SeasonCarPoolEntry alternative;

    @BeforeEach
    void setUp() {
        season = new Season("Test entry consumption");
        season.setId(UUID.randomUUID());
        season.setCarPoolMode(CarPoolMode.CATEGORY_POOL);
        season.setCarPoolState(CarPoolState.FROZEN);
        home = team("T-HOME");
        other = team("T-OTHER");
        mr = car("MR", Drivetrain.MR);
        rr = car("RR", Drivetrain.RR);
        fr = car("FR", Drivetrain.FR);
        combined = entry(season, "Gr.3 MR + RR", mr, rr);
        alternative = entry(season, "Alternative", fr);
        lenient().when(seasons.findById(season.getId())).thenReturn(Optional.of(season));
        lenient().when(seasons.findByIdForUpdate(season.getId())).thenReturn(Optional.of(season));
        lenient().when(teams.findById(home.getId())).thenReturn(Optional.of(home));
        lenient().when(teams.findById(other.getId())).thenReturn(Optional.of(other));
        usage();
    }

    @Test
    void givenPendingCombinedEntry_whenChoosingAnotherMember_thenReserveWholeEntryForExactHome() {
        // given
        var pendingRace = UUID.randomUUID();
        when(reservations.pending(season.getId(), true)).thenReturn(List.of(
                new PickReservationService.Reservation(pendingRace, home.getId(), mr.getId(), combined.getId(), UUID.randomUUID())));
        // when / then
        assertThat(service.validate(season.getId(), home.getId(), null, combined.getId(), rr.getId()).valid()).isFalse();
        assertThat(service.validate(season.getId(), home.getId(), pendingRace, combined.getId(), rr.getId()).valid()).isTrue();
        assertThat(service.validate(season.getId(), other.getId(), null, combined.getId(), rr.getId()).valid()).isTrue();
    }

    @Test
    void givenPendingFixedCar_whenChoosingCar_thenReserveOnlyThatCarForExactHome() {
        // given
        season.setCarPoolMode(CarPoolMode.FIXED_CARS);
        season.getCars().addAll(List.of(mr, rr));
        var pendingRace = UUID.randomUUID();
        when(reservations.pending(season.getId(), true)).thenReturn(List.of(
                new PickReservationService.Reservation(pendingRace, home.getId(), mr.getId(), null, UUID.randomUUID())));
        // when / then
        assertThat(service.validate(season.getId(), home.getId(), null, null, mr.getId()).valid()).isFalse();
        assertThat(service.validate(season.getId(), home.getId(), null, null, rr.getId()).valid()).isTrue();
        assertThat(service.validate(season.getId(), other.getId(), null, null, mr.getId()).valid()).isTrue();
    }

    @Test
    void givenCombinedEntryConsumedWithMr_whenSelectingRr_thenWholeEntryIsUnavailableForThatHome() {
        // given
        usage(race(season, home, other, combined, mr));
        // when
        var availability = service.availability(season.getId(), home.getId(), null);
        var result = service.validate(season.getId(), home.getId(), null, combined.getId(), rr.getId());
        // then
        assertThat(availability.consumedEntryIds()).containsExactly(combined.getId());
        assertThat(availability.entries().stream().filter(e -> e.id().equals(combined.getId())).findFirst().orElseThrow().consumed()).isTrue();
        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("already");
        assertThat(service.validate(season.getId(), home.getId(), null, alternative.getId(), fr.getId()).valid()).isTrue();
    }

    @Test
    void givenEntryUsedByAnotherHome_whenSelecting_thenTheOtherTeamsUsageIsIndependent() {
        // given
        usage(race(season, other, home, combined, mr));
        // when / then
        assertThat(service.availability(season.getId(), home.getId(), null).consumedEntryIds()).isEmpty();
        assertThat(service.validate(season.getId(), home.getId(), null, combined.getId(), rr.getId()).valid()).isTrue();
    }

    @Test
    void givenAnotherSeasonWithItsOwnEntry_whenSelectingSameConcreteCar_thenConsumptionDoesNotLeak() {
        // given
        usage(race(season, home, other, combined, mr));
        var another = new Season("Test independent season");
        another.setId(UUID.randomUUID());
        another.setCarPoolMode(CarPoolMode.CATEGORY_POOL);
        another.setCarPoolState(CarPoolState.FROZEN);
        var anotherEntry = entry(another, "Independent MR/RR", mr, rr);
        when(seasons.findByIdForUpdate(another.getId())).thenReturn(Optional.of(another));
        // when / then
        assertThat(service.validate(another.getId(), home.getId(), null, anotherEntry.getId(), rr.getId()).valid()).isTrue();
    }

    @Test
    void givenOwnSavedSelection_whenEditingWithinEntryOrSwitchingEntry_thenExcludeOnlyThatRace() {
        // given
        var own = race(season, home, other, combined, mr);
        usage(own);
        // when / then
        assertThat(service.availability(season.getId(), home.getId(), own.getId()).consumedEntryIds()).isEmpty();
        assertThat(service.validate(season.getId(), home.getId(), own.getId(), combined.getId(), rr.getId()).valid()).isTrue();
        assertThat(service.validate(season.getId(), home.getId(), own.getId(), alternative.getId(), fr.getId()).valid()).isTrue();
        usage(own, race(season, home, other, alternative, fr));
        assertThat(service.validate(season.getId(), home.getId(), own.getId(), alternative.getId(), fr.getId()).valid()).isFalse();
        assertThat(own.getCar()).isSameAs(mr);
        assertThat(own.getCarPoolEntry()).isSameAs(combined);
    }

    @Test
    void givenDeletedSelection_whenReadingUsageAgain_thenEntryBecomesAvailable() {
        // given
        usage(race(season, home, other, combined, mr));
        assertThat(service.validate(season.getId(), home.getId(), null, combined.getId(), rr.getId()).valid()).isFalse();
        // when
        usage();
        // then
        assertThat(service.validate(season.getId(), home.getId(), null, combined.getId(), rr.getId()).valid()).isTrue();
    }

    @Test
    void givenEmptyDraftOrPartialPair_whenValidating_thenOnlyTheCompletelyEmptyDraftIsAllowed() {
        // given
        season.setCarPoolState(CarPoolState.DRAFT);
        // when / then
        assertThat(service.validate(season.getId(), home.getId(), null, null, null).valid()).isTrue();
        assertThat(service.validate(season.getId(), home.getId(), null, combined.getId(), null).valid()).isFalse();
        assertThat(service.validate(season.getId(), home.getId(), null, null, mr.getId()).valid()).isFalse();
        assertThat(service.validate(season.getId(), home.getId(), null, combined.getId(), mr.getId()).valid()).isFalse();
        assertThat(service.availability(season.getId(), home.getId(), null).entries()).allSatisfy(e -> {
            assertThat(e.cars()).isEmpty();
            assertThat(e.reason()).contains("Freeze");
        });
    }

    @Test
    void givenUnknownOrForeignEntryOrNonMember_whenValidating_thenRejectInsteadOfClearingTheSelection() {
        // given
        var foreign = new Season("Test foreign pool");
        foreign.setId(UUID.randomUUID());
        var foreignEntry = entry(foreign, "Foreign", mr);
        season.getCarPoolEntries().add(foreignEntry);
        // when / then
        assertThat(service.validate(season.getId(), home.getId(), null, UUID.randomUUID(), mr.getId()).valid()).isFalse();
        assertThat(service.validate(season.getId(), home.getId(), null, foreignEntry.getId(), mr.getId()).valid()).isFalse();
        assertThat(service.availability(season.getId(), home.getId(), null).entries())
                .noneMatch(choice -> choice.id().equals(foreignEntry.getId()));
        assertThat(service.validate(season.getId(), home.getId(), null, combined.getId(), fr.getId()).valid()).isFalse();
        assertThat(service.validate(season.getId(), home.getId(), null, combined.getId(), UUID.randomUUID()).valid()).isFalse();
    }

    @Test
    void givenFrozenMembershipAndChangedMetadata_whenReadingOrSelecting_thenUseSnapshotWithoutReevaluation() {
        // given
        mr.setCategory(CarCategory.GR_4);
        rr.setDrivetrain(Drivetrain.FR);
        // when
        var choices = service.availability(season.getId(), home.getId(), null).entries();
        // then
        assertThat(choices).filteredOn(e -> e.id().equals(combined.getId())).singleElement().satisfies(choice ->
                assertThat(choice.cars()).extracting(RaceCarSelectionService.CarChoice::id).containsExactlyInAnyOrder(mr.getId(), rr.getId()));
        assertThat(service.validate(season.getId(), home.getId(), null, combined.getId(), mr.getId()).car()).isSameAs(mr);
    }

    @Test
    void givenReturnLegOverride_whenCheckingUsage_thenEffectiveHomeOwnsTheEntry() {
        // given
        var reverse = race(season, home, other, combined, mr);
        reverse.setHomeTeamOverride(other);
        reverse.setAwayTeamOverride(home);
        usage(reverse);
        // when / then
        assertThat(service.availability(season.getId(), home.getId(), null).consumedEntryIds()).isEmpty();
        assertThat(service.validate(season.getId(), other.getId(), null, combined.getId(), rr.getId()).valid()).isFalse();
    }

    @Test
    void givenByeOrUnselectedDraft_whenCheckingUsage_thenNeitherConsumesAnEntry() {
        // given
        var bye = race(season, home, null, combined, mr);
        bye.getMatch().setBye(true);
        usage(bye, race(season, home, other, null, null));
        // when / then
        assertThat(service.availability(season.getId(), home.getId(), null).consumedEntryIds()).isEmpty();
        assertThat(service.validate(season.getId(), home.getId(), null, combined.getId(), rr.getId()).valid()).isTrue();
    }

    @Test
    void givenPlayoffSelection_whenCheckingUsage_thenItsEffectiveHomeConsumesInTheSeason() {
        // given
        var race = race(season, home, other, combined, mr);
        race.setMatch(null);
        var matchup = new PlayoffMatchup();
        matchup.setTeam1(home);
        matchup.setTeam2(other);
        race.setPlayoffMatchup(matchup);
        usage(race);
        // when / then
        assertThat(service.validate(season.getId(), home.getId(), null, combined.getId(), rr.getId()).valid()).isFalse();
        matchup.setBye(true);
        assertThat(service.validate(season.getId(), home.getId(), null, combined.getId(), rr.getId()).valid()).isTrue();
    }

    @Test
    void givenFixedPool_whenSelecting_thenKeepConcreteMembershipAndReuseRulesWithoutEntries() {
        // given
        season.setCarPoolMode(CarPoolMode.FIXED_CARS);
        season.setCarPoolState(CarPoolState.DRAFT);
        season.getCars().addAll(List.of(mr, rr));
        usage(race(season, home, other, null, mr));
        // when / then
        assertThat(service.validate(season.getId(), home.getId(), null, null, mr.getId()).valid()).isFalse();
        assertThat(service.validate(season.getId(), home.getId(), null, null, rr.getId()).valid()).isTrue();
        assertThat(service.validate(season.getId(), home.getId(), null, null, fr.getId()).valid()).isFalse();
        assertThat(service.validate(season.getId(), home.getId(), null, combined.getId(), rr.getId()).valid()).isFalse();
        assertThat(service.availability(season.getId(), home.getId(), null).entries()).isEmpty();
    }

    @Test
    void givenNoHomeOrUnknownHome_whenSelecting_thenDoNotInventConsumptionOwnership() {
        // given / when / then
        assertThat(service.availability(season.getId(), null, null).consumedEntryIds()).isEmpty();
        assertThat(service.validate(season.getId(), null, null, combined.getId(), mr.getId()).valid()).isFalse();
        assertThat(service.validate(season.getId(), UUID.randomUUID(), null, combined.getId(), mr.getId()).valid()).isFalse();
    }

    @Test
    void givenUnknownSeason_whenSelecting_thenReturnAValidationError() {
        // given / when / then
        assertThatCode(() -> assertThat(service.validate(UUID.randomUUID(), home.getId(), null,
                combined.getId(), mr.getId()).valid()).isFalse()).doesNotThrowAnyException();
    }

    @Test
    void givenTwoIndependentOwners_whenSwappingBothAssignments_thenValidateFinalOwnershipTogether() {
        // given
        var first = race(season, home, other, combined, mr);
        var second = race(season, other, home, combined, rr);
        usage(first, second);
        // when / then
        assertThat(service.validateAssignments(season.getId(), List.of(
                assignment(first, other, combined, mr), assignment(second, home, combined, rr))).valid()).isTrue();
        assertThat(first.getHomeTeam()).isSameAs(home);
        assertThat(second.getHomeTeam()).isSameAs(other);
    }

    @Test
    void givenAnotherLegRetainsEntry_whenReorientingOneLegIntoItsHome_thenRejectBeforeMutation() {
        // given
        var first = race(season, home, other, combined, mr);
        var second = race(season, other, home, combined, rr);
        usage(first, second);
        // when
        var result = service.validateAssignments(season.getId(), List.of(assignment(second, home, combined, rr)));
        // then
        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("already");
        assertThat(second.getHomeTeam()).isSameAs(other);
        assertThat(second.getCar()).isSameAs(rr);
    }

    @Test
    void givenTwoNewLegsWithDifferentCars_whenSharingHomeAndEntry_thenRejectWholeBatch() {
        // given / when
        var result = service.validateAssignments(season.getId(), List.of(
                new RaceCarSelectionService.Assignment(null, home.getId(), false, combined.getId(), mr.getId()),
                new RaceCarSelectionService.Assignment(null, home.getId(), false, combined.getId(), rr.getId())));
        // then
        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("already");
    }

    @Test
    void givenByeAssignment_whenChoosingCar_thenRejectButAllowUnconfiguredBye() {
        // given / when / then
        assertThat(service.validateAssignments(season.getId(), List.of(
                new RaceCarSelectionService.Assignment(null, home.getId(), true, combined.getId(), mr.getId()))).valid()).isFalse();
        assertThat(service.validateAssignments(season.getId(), List.of(
                new RaceCarSelectionService.Assignment(null, home.getId(), true, null, null))).valid()).isTrue();
    }

    @Test
    void givenBatchSelection_whenEntryIsForgedOrIncomplete_thenReject() {
        // given / when / then
        assertThat(service.validateAssignments(season.getId(), List.of(
                new RaceCarSelectionService.Assignment(null, home.getId(), false, UUID.randomUUID(), mr.getId()))).valid()).isFalse();
        assertThat(service.validateAssignments(season.getId(), List.of(
                new RaceCarSelectionService.Assignment(null, home.getId(), false, combined.getId(), null))).valid()).isFalse();
    }

    private static RaceCarSelectionService.Assignment assignment(Race race, Team home,
                                                                SeasonCarPoolEntry entry, Car car) {
        return new RaceCarSelectionService.Assignment(race.getId(), home.getId(), false,
                entry == null ? null : entry.getId(), car == null ? null : car.getId());
    }

    @Test
    void givenOneRaceTwiceInAPlannedChange_whenValidating_thenRejectBeforeOmittingItsUsageTwice() {
        // given
        var own = race(season, home, other, combined, mr);
        usage(own);
        // when / then
        assertThat(service.validateAssignments(season.getId(), List.of(
                assignment(own, home, combined, mr), assignment(own, other, combined, rr))).valid()).isFalse();
    }

    @Test
    void givenFixedPoolBatch_whenTwoNewLegsReuseConcreteCarForOneHome_thenKeepExistingFixedReuseRestriction() {
        // given
        season.setCarPoolMode(CarPoolMode.FIXED_CARS);
        season.getCars().add(mr);
        // when / then
        assertThat(service.validateAssignments(season.getId(), List.of(
                new RaceCarSelectionService.Assignment(null, home.getId(), false, null, mr.getId()),
                new RaceCarSelectionService.Assignment(null, home.getId(), false, null, mr.getId()))).valid()).isFalse();
    }

    @Test
    void givenExplicitOrRegularPairingOwnership_whenProjectingPlayoffHome_thenKeepExactResolutionPrecedence() {
        // given
        var own = race(season, home, other, combined, mr);
        // when / then
        assertThat(own.projectPlayoffHome(other)).isSameAs(home);
        own.setMatch(null);
        assertThat(own.projectPlayoffHome(other)).isSameAs(other);
        own.setAwayTeamOverride(home);
        assertThat(own.projectPlayoffHome(other)).isSameAs(other);
        own.setHomeTeamOverride(home);
        assertThat(own.projectPlayoffHome(other)).isSameAs(home);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void givenExistingBye_whenValidatingAnEditedSelection_thenRejectCarsButAllowExplicitEmptyDraft(boolean playoff) {
        // given
        var bye = race(season, home, other, combined, mr);
        if (playoff) {
            bye.setMatch(null);
            var matchup = new PlayoffMatchup();
            matchup.setTeam1(home);
            matchup.setBye(true);
            bye.setPlayoffMatchup(matchup);
        } else bye.getMatch().setBye(true);
        usage(bye);
        // when / then
        var rejected = service.validate(season.getId(), home.getId(), bye.getId(), combined.getId(), rr.getId());
        assertThat(rejected.valid()).isFalse();
        assertThat(rejected.message()).contains("bye");
        assertThat(service.validate(season.getId(), home.getId(), bye.getId(), null, null).valid()).isTrue();
        assertThat(bye.getCar()).isSameAs(mr);
        assertThat(bye.getCarPoolEntry()).isSameAs(combined);
    }

    private void usage(Race... values) {
        lenient().when(races.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(values));
        lenient().when(races.findBySeasonForSelectionUpdate(season.getId())).thenReturn(List.of(values));
    }

    private static Team team(String name) {
        var team = new Team("Test selection " + name, name);
        team.setId(UUID.randomUUID());
        return team;
    }

    private static Car car(String name, Drivetrain drivetrain) {
        var car = new Car("Test selection", name);
        car.setId(UUID.randomUUID());
        car.setCategory(CarCategory.GR_3);
        car.setDrivetrain(drivetrain);
        return car;
    }

    private static SeasonCarPoolEntry entry(Season owner, String label, Car... cars) {
        var entry = new SeasonCarPoolEntry();
        entry.setId(UUID.randomUUID());
        entry.setSeason(owner);
        entry.setLabel(label);
        owner.getCarPoolEntries().add(entry);
        for (var car : cars) {
            var member = new SeasonCarPoolMember();
            member.setId(UUID.randomUUID());
            member.setEntry(entry);
            member.setCar(car);
            entry.getMembers().add(member);
        }
        return entry;
    }

    private static Race race(Season owner, Team home, Team away, SeasonCarPoolEntry entry, Car car) {
        var phase = new SeasonPhase(owner, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
        var matchday = new Matchday(phase, "Test selection matchday", 0);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(new Match(matchday, home, away));
        race.setCarPoolEntry(entry);
        race.setCar(car);
        return race;
    }
}
