package org.ctc.domain.model;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class PickPublicationContentTest {
    @Test
    void givenCapturedRace_whenCurrentSettingsAndCatalogLabelsChange_thenKeepTheOriginalContentAndFingerprint() {
        // given
        var race = race(); var captured = PickPublicationContent.from(race); var hash = captured.fingerprint();
        // when
        race.getSettings().setWeather("Test later private weather"); race.getCar().setName("Test renamed car");
        race.getTrack().setName("Test renamed track"); race.getCarPoolEntry().setLabel("Test renamed entry");
        // then
        var projection = captured.project(race.getId(), PickDisclosureField.all());
        assertThat(projection.carName()).contains("Test original car"); assertThat(projection.trackName()).isEqualTo("Test original track");
        assertThat(projection.poolEntryLabel()).isEqualTo("Test original entry"); assertThat(projection.settings().weather()).isEqualTo("Test original weather");
        assertThat(captured.fingerprint()).isEqualTo(hash).hasSize(64);
        assertThat(PickPublicationContent.from(race).fingerprint()).isNotEqualTo(hash);
        assertThat(captured.toString()).doesNotContain("Test original");
    }

    @Test
    void givenLobbyMask_whenProjecting_thenKeepCarAndPoolEntryPrivateWhileShowingOtherFields() {
        // given / when
        var projection = PickPublicationContent.from(race()).project(UUID.randomUUID(), PickDisclosureField.lobby());
        // then
        assertThat(projection.carName()).isNull(); assertThat(projection.poolEntryLabel()).isNull();
        assertThat(projection.trackName()).isEqualTo("Test original track");
        assertThat(projection.settings().weather()).isEqualTo("Test original weather");
        assertThat(projection.settings().laps()).isEqualTo(12);
    }

    @Test
    void givenEmptyMask_whenProjecting_thenReturnWhollyWithheldChoices() {
        // given / when
        var projection = PickPublicationContent.from(race()).project(UUID.randomUUID(), Set.of());
        // then
        assertThat(projection.carName()).isNull(); assertThat(projection.poolEntryLabel()).isNull();
        assertThat(projection.trackName()).isNull(); assertThat(projection.settings()).isNull();
    }

    @Test
    void givenWeatherOnlyMask_whenProjecting_thenNeverCarryOtherPrivateFieldsInsideTheSettingsValue() {
        // given / when
        var projection = PickPublicationContent.from(race()).project(UUID.randomUUID(), Set.of(PickDisclosureField.WEATHER));
        // then
        assertThat(projection.carName()).isNull(); assertThat(projection.trackName()).isNull();
        var settings = projection.settings(); assertThat(settings.weather()).isEqualTo("Test original weather");
        assertThat(settings).extracting(RaceSettingsValues::laps, RaceSettingsValues::tyreWear, RaceSettingsValues::fuelConsumption,
                RaceSettingsValues::refuelingSpeed, RaceSettingsValues::initialFuel, RaceSettingsValues::requiredPitStops,
                RaceSettingsValues::timeProgressionMode, RaceSettingsValues::timeProgressionMultiplier, RaceSettingsValues::timeOfDay,
                RaceSettingsValues::availableTyres, RaceSettingsValues::mandatoryTyres).containsOnlyNulls();
    }

    @ParameterizedTest
    @EnumSource(TimeProgressionMode.class)
    void givenEitherTimeMode_whenCapturingAndProjecting_thenKeepEqualConditionsDistinctFromZero(TimeProgressionMode mode) {
        // given
        var race = race(); race.getSettings().setTimeProgressionMode(mode);
        race.getSettings().setTimeProgressionMultiplier(mode == TimeProgressionMode.EQUAL_CONDITIONS ? null : 0);
        // when / then
        var projected = PickPublicationContent.from(race).project(race.getId(), PickDisclosureField.all()).settings();
        assertThat(projected.timeProgressionMode()).isEqualTo(mode);
        assertThat(projected.timeProgressionMultiplier()).isEqualTo(mode == TimeProgressionMode.EQUAL_CONDITIONS ? null : 0);
    }

    @Test
    void givenKnownMask_whenEncodingAndDecoding_thenPreserveEveryFieldAndRejectUnknownBits() {
        // given / when / then
        assertThat(PickDisclosureField.fields(PickDisclosureField.mask(PickDisclosureField.all()))).isEqualTo(PickDisclosureField.all());
        assertThat(PickDisclosureField.fields(0)).isEmpty();
        assertThatThrownBy(() -> PickDisclosureField.fields(1 << 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"car", "track", "entry"})
    void givenOnlyAPresentationLabelChanged_whenFingerprinting_thenStartDifferentContent(String label) {
        // given
        var race = race(); var first = PickPublicationContent.from(race).fingerprint();
        // when
        switch (label) {
            case "car" -> race.getCar().setName("Test changed car label");
            case "track" -> race.getTrack().setName("Test changed track label");
            case "entry" -> race.getCarPoolEntry().setLabel("Test changed entry label");
            default -> throw new IllegalArgumentException(label);
        }
        // then
        assertThat(PickPublicationContent.from(race).fingerprint()).isNotEqualTo(first);
    }

    @Test
    void givenPresentationLabelsWithSeparators_whenFingerprinting_thenKeepTheirExactBoundaries() {
        // given
        var source = PickPublicationContent.from(race());
        // when / then
        assertThat(relabeled(source, "X:Y", "Z").fingerprint()).isNotEqualTo(relabeled(source, "X", ":YZ").fingerprint());
    }

    PickPublicationContent relabeled(PickPublicationContent source, String track, String car) {
        return new PickPublicationContent(source.trackId(), source.carId(), source.carPoolEntryId(), track, car, source.poolEntryLabel(),
                source.laps(), source.tyreWear(), source.fuelConsumption(), source.refuelingSpeed(), source.initialFuel(), source.requiredPitStops(),
                source.timeProgressionMode(), source.timeProgressionMultiplier(), source.weather(), source.timeOfDay(), source.availableTyres(), source.mandatoryTyres());
    }

    Race race() {
        var race = new Race(); race.setId(UUID.randomUUID());
        var car = new Car("Test manufacturer", "Test original car"); car.setId(UUID.randomUUID()); race.setCar(car);
        var track = new Track("Test original track"); track.setId(UUID.randomUUID()); race.setTrack(track);
        var entry = new SeasonCarPoolEntry(); entry.setId(UUID.randomUUID()); entry.setLabel("Test original entry"); race.setCarPoolEntry(entry);
        var settings = new RaceSettings(race); race.setSettings(settings);
        new RaceSettingsValues(12, 2, 3, 4, "Default", 1, TimeProgressionMode.MULTIPLIER, 0,
                "Test original weather", "Sunset", "RS, RM", "RM").applyTo(settings);
        return race;
    }
}
