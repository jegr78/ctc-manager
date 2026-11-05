package org.ctc.domain.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonCarPoolEntryRepository;
import org.ctc.domain.repository.TrackRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TyrePermissionServiceTest {
    @ParameterizedTest
    @CsvSource({"native,category", "native,singleton", "native,missing", "native,foreign", "native,wrong-car",
            "native,legacy", "private,category", "private,singleton", "private,missing", "private,foreign", "private,wrong-car"})
    void givenSportsWithoutAnOwnedExplicitOneMake_whenApplyingOrChecking_thenRejectBeforeMutation(String route, String kind) {
        // given
        var context = context(kind);
        // when / then
        var rejected = assertThatThrownBy(() -> apply(route, context, "SS, SM, SH", "SM"))
                .isInstanceOf(BusinessRuleException.class);
        rejected.hasMessageContaining(kind.equals("legacy") ? "season" : "One-Make");
        assertThat(context.race().getSettings()).isNull();
        assertThat(context.race().getCar()).isNull();
        assertThat(context.race().getTrack()).isNull();
        assertThat(context.race().getCarPoolEntry()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"native", "private"})
    void given2027AndItsSavedExplicitOneMake_whenChoosingSports_thenAccept(String route) {
        // given
        var context = context("explicit");
        // when / then
        assertThatCode(() -> apply(route, context, "SS, SM, SH", "SM")).doesNotThrowAnyException();
        if (route.equals("native")) {
            assertThat(context.race().getSettings().getAvailableTyres()).isEqualTo("SS, SM, SH");
            assertThat(context.race().getSettings().getMandatoryTyres()).isEqualTo("SM");
            assertThat(context.race().getCarPoolEntry()).isSameAs(context.entry());
        }
    }

    @ParameterizedTest
    @CsvSource({"'RS, SS',None", "'RS, RM, RH, I, W',I", "'RS, RM, RH, I, W',W", "RS,RM"})
    void givenInvalidNativeTyreCombinations_whenApplying_thenUseTheSameCompleteSelectionRules(String available, String mandatory) {
        // given
        var context = context("explicit");
        // when / then
        assertThatThrownBy(() -> apply("native", context, available, mandatory)).isInstanceOf(BusinessRuleException.class);
        assertThat(context.race().getSettings()).isNull();
        assertThat(context.race().getCar()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"legacy,All,Medium", "category,any,none", "explicit,'RS, RM, RH, I, W',None"})
    void givenExistingRacingText_whenApplying_thenRetainItsExactRepresentation(String kind, String available, String mandatory) {
        // given
        var context = context(kind);
        // when
        apply("native", context, available, mandatory);
        // then
        assertThat(context.race().getSettings().getAvailableTyres()).isEqualTo(available);
        assertThat(context.race().getSettings().getMandatoryTyres()).isEqualTo(mandatory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"matchday", "season"})
    void givenMissingSeasonContext_whenApplyingNativeSettings_thenKeepTheExistingBusinessRejection(String missing) {
        // given
        var context = context("explicit");
        if (missing.equals("matchday")) context.race().setMatchday(null);
        else context.race().getMatchday().getPhase().setSeason(null);
        // when / then
        assertThatThrownBy(() -> apply("native", context, "SS, SM, SH", "SM"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("season rules");
        assertThat(context.race().getSettings()).isNull();
        assertThat(context.race().getCar()).isNull();
        assertThat(context.race().getTrack()).isNull();
        assertThat(context.race().getCarPoolEntry()).isNull();
    }

    private static void apply(String route, Context context, String available, String mandatory) {
        if (route.equals("native")) {
            var service = new RaceSettingsApplicationService(mock(CarRepository.class), mock(TrackRepository.class),
                    mock(SeasonCarPoolEntryRepository.class));
            service.apply(context.race(), context.track(), context.car(), context.entry(),
                    new RaceSettingsValues(12, 2, 3, 4, "Default", 1, TimeProgressionMode.MULTIPLIER,
                            0, "Test weather", "Sunset", available, mandatory));
            return;
        }
        var cars = mock(RaceCarSelectionService.class);
        var races = mock(RaceRepository.class);
        var tracks = mock(TrackRepository.class);
        var reservations = mock(PickReservationService.class);
        var season = context.race().getMatchday().getSeason();
        when(cars.validate(any(), any(), any(), any(), any()))
                .thenReturn(new RaceCarSelectionService.Validation(null, context.entry(), context.car()));
        when(races.findBySeasonForSelectionUpdate(season.getId())).thenReturn(List.of());
        when(tracks.findById(context.track().getId())).thenReturn(Optional.of(context.track()));
        when(reservations.pending(season.getId(), true)).thenReturn(List.of());
        var payload = new PickPayload(context.track().getId(), context.car().getId(),
                context.entry() == null ? null : context.entry().getId(), 12, 2, 3, 4, PickInitialFuel.DEFAULT, 1,
                TimeProgressionMode.MULTIPLIER, 0, "Test weather", "Sunset", codes(available), codes(mandatory),
                season.getLobbyRulesProfile(), RaceStartType.GRID_FALSE_START, RaceSlipstream.REAL);
        new PickEligibilityService(cars, races, tracks, reservations).validate(context.race(), payload);
    }

    private static Set<PickTyre> codes(String text) {
        return text.equalsIgnoreCase("None") ? Set.of() : Arrays.stream(text.split(","))
                .map(String::trim).map(PickTyre::valueOf).collect(Collectors.toSet());
    }

    private static Context context(String kind) {
        var season = new Season("Test tyre permissions"); season.setId(UUID.randomUUID());
        season.setLobbyRulesProfile(kind.equals("legacy") ? LobbyRulesProfile.LEGACY : LobbyRulesProfile.CTC_2027);
        season.setCarPoolMode(CarPoolMode.CATEGORY_POOL); season.setCarPoolState(CarPoolState.FROZEN);
        var phase = new SeasonPhase(); phase.setSeason(season);
        var race = new Race(); race.setId(UUID.randomUUID()); race.setMatchday(new Matchday(phase, "Test tyres", 1));
        var home = new Team(); home.setId(UUID.randomUUID()); race.setHomeTeamOverride(home);
        var car = new Car(); car.setId(UUID.randomUUID()); car.setCategory(CarCategory.values()[0]);
        var track = new Track(); track.setId(UUID.randomUUID()); season.getTracks().add(track);
        var entry = new SeasonCarPoolEntry(); entry.setId(UUID.randomUUID()); entry.setSeason(season); entry.setLabel("Test One-Make label");
        var member = new SeasonCarPoolMember(); member.setEntry(entry); member.setCar(car); entry.getMembers().add(member);
        if (kind.equals("category") || kind.equals("singleton")) {
            var clause = new SeasonCarPoolClause(); clause.setEntry(entry); clause.setCategory(car.getCategory()); entry.getClauses().add(clause);
            if (kind.equals("category")) {
                var other = new Car(); other.setId(UUID.randomUUID()); other.setCategory(car.getCategory());
                var extra = new SeasonCarPoolMember(); extra.setEntry(entry); extra.setCar(other); entry.getMembers().add(extra);
            }
        } else entry.setOneMakeCar(car);
        if (kind.equals("foreign")) {
            var foreign = new Season("Test foreign season"); foreign.setId(UUID.randomUUID()); entry.setSeason(foreign);
        }
        if (kind.equals("wrong-car")) {
            var other = new Car(); other.setId(UUID.randomUUID()); entry.setOneMakeCar(other); member.setCar(other);
        }
        if (kind.equals("missing")) entry = null;
        return new Context(race, entry, car, track);
    }

    private record Context(Race race, SeasonCarPoolEntry entry, Car car, Track track) { }
}
