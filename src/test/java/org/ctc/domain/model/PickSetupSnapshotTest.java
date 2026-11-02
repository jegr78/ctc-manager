package org.ctc.domain.model;

import java.util.UUID;
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
        settings.setWeather("S01:timeOfDay=Sunset"); settings.setTimeOfDay("Night");
        var first = PickSetupSnapshot.from(race).fingerprint();
        // when / then
        settings.setWeather("S01"); settings.setTimeOfDay("Sunset:timeOfDay=Night");
        assertThat(PickSetupSnapshot.from(race).fingerprint()).isNotEqualTo(first);
        var car = new Car("Test", "Choice"); car.setId(UUID.randomUUID()); race.setCar(car);
        assertThat(PickSetupSnapshot.from(race).configured()).isTrue();
        var withCar = PickSetupSnapshot.from(race).fingerprint();
        car.setId(UUID.randomUUID());
        assertThat(PickSetupSnapshot.from(race).fingerprint()).isNotEqualTo(withCar);
        assertThat(PickSetupSnapshot.from(race).toString()).doesNotContain("S01", "Night");
    }
}
