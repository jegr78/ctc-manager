package org.ctc.domain.service;

import java.time.LocalDateTime;
import java.util.Optional;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.repository.SeasonCarPoolEntryRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RaceSettingsApplicationServiceTest {
    @Test
    void givenLegacyRaceAndForbiddenNativeOptions_whenApplying_thenRejectBeforeCreatingSettings() {
        // given
        var service = new RaceSettingsApplicationService(mock(CarRepository.class), mock(TrackRepository.class), mock(SeasonCarPoolEntryRepository.class));
        var race = new Race(); var phase = new SeasonPhase(); phase.setSeason(new Season("Test invalid native rules"));
        var day = new Matchday(); day.setPhase(phase); race.setMatchday(day);
        var invalid = new RaceSettings(race); invalid.setStartType(RaceStartType.ROLLING);
        // when / then
        assertThatThrownBy(() -> service.apply(race, null, null, null, RaceSettingsValues.from(invalid))).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);
        assertThat(race.getSettings()).isNull();
    }

    @Test
    void given2027PayloadForLegacyRace_whenApplyingDirectly_thenRejectBeforeResolvingPrivateSelections() {
        // given
        var cars = mock(CarRepository.class); var tracks = mock(TrackRepository.class); var entries = mock(SeasonCarPoolEntryRepository.class);
        var service = new RaceSettingsApplicationService(cars, tracks, entries);
        var source = PickSubmissionServiceTest.payload();
        var payload = new PickPayload(source.trackId(), source.carId(), null, source.laps(), source.tyreWear(), source.fuelConsumption(),
                source.refuelingSpeed(), source.initialFuel(), source.requiredPitStops(), source.timeProgressionMode(), source.timeProgressionMultiplier(),
                source.weather(), source.timeOfDay(), source.availableTyres(), source.mandatoryTyres(), LobbyRulesProfile.CTC_2027, RaceStartType.ROLLING, RaceSlipstream.WEAK);
        var race = new Race(); var phase = new SeasonPhase(); phase.setSeason(new Season("Test invalid private rules"));
        var day = new Matchday(); day.setPhase(phase); race.setMatchday(day);
        // when / then
        assertThatThrownBy(() -> service.apply(race, payload)).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);
        assertThat(race.getSettings()).isNull();
        verifyNoInteractions(cars, tracks, entries);
    }
    @Test
    void givenCompletePick_whenApplyingSettings_thenCopyEveryFieldWithoutChangingSchedulePairingOrResults() {
        // given / when / then
        var cars = mock(CarRepository.class);
        var tracks = mock(TrackRepository.class);
        var entries = mock(SeasonCarPoolEntryRepository.class);
        var service = new RaceSettingsApplicationService(cars, tracks, entries);
        var payload = PickSubmissionServiceTest.payload();
        var car = new Car();
        car.setId(payload.carId());
        var track = new Track();
        track.setId(payload.trackId());
        when(cars.findById(payload.carId())).thenReturn(Optional.of(car));
        when(tracks.findById(payload.trackId())).thenReturn(Optional.of(track));
        var race = new Race();
        var match = new Match();
        var day = new Matchday();
        var phase = new SeasonPhase(); phase.setSeason(new Season("Test settings application")); day.setPhase(phase);
        var result = new RaceResult();
        var when = LocalDateTime.of(2026, 12, 1, 20, 0);
        race.setDateTime(when);
        race.setMatchday(day);
        race.setMatch(match);
        race.getResults().add(result);
        service.apply(race, payload);
        assertThat(race.getSettings()).isNotNull();
        var settings = race.getSettings();
        assertThat(settings.getRace()).isSameAs(race);
        assertThat(settings.isComplete()).isTrue();
        assertThat(settings.getNumberOfLaps()).isEqualTo(payload.laps());
        assertThat(settings.getTyreWearMultiplier()).isEqualTo(payload.tyreWear());
        assertThat(settings.getFuelConsumptionMultiplier()).isEqualTo(payload.fuelConsumption());
        assertThat(settings.getRefuelingSpeed()).isEqualTo(payload.refuelingSpeed());
        assertThat(settings.getInitialFuel()).isEqualTo("Default");
        assertThat(settings.getNumberOfRequiredPitStops()).isEqualTo(payload.requiredPitStops());
        assertThat(settings.getTimeProgressionMode()).isEqualTo(payload.timeProgressionMode());
        assertThat(settings.getTimeProgressionMultiplier()).isZero();
        assertThat(settings.getWeather()).isEqualTo(payload.weather());
        assertThat(settings.getTimeOfDay()).isEqualTo(payload.timeOfDay());
        assertThat(settings.getAvailableTyres()).isEqualTo("RS, RM");
        assertThat(settings.getMandatoryTyres()).isEqualTo("None");
        assertThat(race.getCar()).isSameAs(car);
        assertThat(race.getTrack()).isSameAs(track);
        assertThat(race.getDateTime()).isEqualTo(when);
        assertThat(race.getMatch()).isSameAs(match);
        assertThat(race.getMatchday()).isSameAs(day);
        assertThat(race.getResults()).containsExactly(result);
        verifyNoInteractions(entries);
    }
}
