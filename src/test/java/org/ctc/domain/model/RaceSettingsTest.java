package org.ctc.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RaceSettingsTest {

    @Test
    void givenAllFieldsFilled_whenIsComplete_thenReturnsTrue() {
        // given
        var settings = createCompleteSettings();

        // when / then
        assertThat(settings.isComplete()).isTrue();
    }

    @Test
    void givenNullIntegerField_whenIsComplete_thenReturnsFalse() {
        // given
        var settings = createCompleteSettings();
        settings.setNumberOfLaps(null);

        // when / then
        assertThat(settings.isComplete()).isFalse();
    }

    @Test
    void givenBlankStringField_whenIsComplete_thenReturnsFalse() {
        // given
        var settings = createCompleteSettings();
        settings.setWeather("   ");

        // when / then
        assertThat(settings.isComplete()).isFalse();
    }

    @Test
    void givenNullStringField_whenIsComplete_thenReturnsFalse() {
        // given
        var settings = createCompleteSettings();
        settings.setInitialFuel(null);

        // when / then
        assertThat(settings.isComplete()).isFalse();
    }

    @Test
    void givenEmptySettings_whenIsComplete_thenReturnsFalse() {
        // given
        var settings = new RaceSettings(new Race());

        // when / then
        assertThat(settings.isComplete()).isFalse();
    }

    @Test
    void givenEachIntegerFieldSetToNull_whenIsComplete_thenReturnsFalse() {
        // given / when / then
        // tyreWearMultiplier
        var s1 = createCompleteSettings();
        s1.setTyreWearMultiplier(null);
        assertThat(s1.isComplete()).isFalse();

        // fuelConsumptionMultiplier
        var s2 = createCompleteSettings();
        s2.setFuelConsumptionMultiplier(null);
        assertThat(s2.isComplete()).isFalse();

        // refuelingSpeed
        var s3 = createCompleteSettings();
        s3.setRefuelingSpeed(null);
        assertThat(s3.isComplete()).isFalse();

        // numberOfRequiredPitStops
        var s4 = createCompleteSettings();
        s4.setNumberOfRequiredPitStops(null);
        assertThat(s4.isComplete()).isFalse();

        // timeProgressionMultiplier
        var s5 = createCompleteSettings();
        s5.setTimeProgressionMultiplier(null);
        assertThat(s5.isComplete()).isFalse();
    }

    @Test
    void givenEachStringFieldBlankOrNull_whenIsComplete_thenReturnsFalse() {
        // given / when / then
        var s1 = createCompleteSettings();
        s1.setTimeOfDay("");
        assertThat(s1.isComplete()).isFalse();

        var s2 = createCompleteSettings();
        s2.setAvailableTyres("  ");
        assertThat(s2.isComplete()).isFalse();

        var s3 = createCompleteSettings();
        s3.setMandatoryTyres(null);
        assertThat(s3.isComplete()).isFalse();
    }

    private RaceSettings createCompleteSettings() {
        var settings = new RaceSettings(new Race());
        settings.setNumberOfLaps(20);
        settings.setTyreWearMultiplier(3);
        settings.setFuelConsumptionMultiplier(3);
        settings.setRefuelingSpeed(10);
        settings.setInitialFuel("90");
        settings.setNumberOfRequiredPitStops(0);
        settings.setTimeProgressionMultiplier(5);
        settings.setWeather("Preset S02");
        settings.setTimeOfDay("Afternoon");
        settings.setAvailableTyres("RS, RM");
        settings.setMandatoryTyres("RS");
        return settings;
    }
}
