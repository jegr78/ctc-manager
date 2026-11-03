package org.ctc.domain.model;

import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PickSetupSnapshotTest {
    @Test
    void givenEmptySetup_whenCapturing_thenTreatDefaultTimeModeAsUnconfigured() {
        // given
        var race = new Race();
        // when / then
        var empty = PickSetupSnapshot.from(race);
        assertThat(empty.configured()).isFalse();
        assertThat(empty.fingerprint()).hasSize(64);
        race.setSettings(new RaceSettings(race));
        assertThat(PickSetupSnapshot.from(race)).isEqualTo(empty);
        race.getSettings().setTimeProgressionMode(TimeProgressionMode.EQUAL_CONDITIONS);
        assertThat(PickSetupSnapshot.from(race).configured()).isTrue();
        assertThat(PickSetupSnapshot.from(race).fingerprint()).isNotEqualTo(empty.fingerprint());
    }

    @Test
    void givenDifferentValuesWithSeparators_whenFingerprinting_thenKeepExactFieldBoundariesAndIdentity() {
        // given
        var race = new Race(); var settings = new RaceSettings(race); race.setSettings(settings);
        settings.setWeather("S01:Sunset"); settings.setTimeOfDay("Night");
        var first = PickSetupSnapshot.from(race).fingerprint();
        // when / then
        settings.setWeather("S01"); settings.setTimeOfDay(":SunsetNight");
        assertThat(PickSetupSnapshot.from(race).fingerprint()).isNotEqualTo(first);
        var car = new Car("Test", "Choice"); car.setId(UUID.randomUUID()); race.setCar(car);
        assertThat(PickSetupSnapshot.from(race).configured()).isTrue();
        var withCar = PickSetupSnapshot.from(race).fingerprint();
        car.setId(UUID.randomUUID());
        assertThat(PickSetupSnapshot.from(race).fingerprint()).isNotEqualTo(withCar);
        assertThat(PickSetupSnapshot.from(race).toString()).doesNotContain("S01", "Night");
    }

    @Test
    void givenCapturedCompleteSetup_whenAnyIndividualFieldChanges_thenInvalidateReviewFingerprint() {
        // given
        List<Consumer<Race>> changes = List.of(
                race -> { var track = new Track("Test changed"); track.setId(UUID.randomUUID()); race.setTrack(track); },
                race -> { var car = new Car("Test", "Changed"); car.setId(UUID.randomUUID()); race.setCar(car); },
                race -> { var entry = new SeasonCarPoolEntry(); entry.setId(UUID.randomUUID()); race.setCarPoolEntry(entry); },
                race -> race.getSettings().setNumberOfLaps(13), race -> race.getSettings().setTyreWearMultiplier(3),
                race -> race.getSettings().setFuelConsumptionMultiplier(4), race -> race.getSettings().setRefuelingSpeed(5),
                race -> race.getSettings().setInitialFuel("30"), race -> race.getSettings().setNumberOfRequiredPitStops(2),
                race -> race.getSettings().setTimeProgressionMode(TimeProgressionMode.EQUAL_CONDITIONS),
                race -> race.getSettings().setTimeProgressionMultiplier(1), race -> race.getSettings().setWeather("R03"),
                race -> race.getSettings().setTimeOfDay("Night"), race -> race.getSettings().setAvailableTyres("RM"),
                race -> race.getSettings().setMandatoryTyres("None"));
        // when / then
        for (int i = 0; i < changes.size(); i++) {
            var race = new Race(); var setup = new RaceSettings(race); race.setSettings(setup);
            new RaceSettingsValues(12, 2, 3, 4, "Default", 1, TimeProgressionMode.MULTIPLIER, 0, "S01", "Sunset", "RS, RM", "RM").applyTo(setup);
            var fingerprint = PickSetupSnapshot.from(race).fingerprint(); changes.get(i).accept(race);
            assertThat(PickSetupSnapshot.from(race).fingerprint()).as("setup field %s", i).isNotEqualTo(fingerprint);
        }
    }
}
