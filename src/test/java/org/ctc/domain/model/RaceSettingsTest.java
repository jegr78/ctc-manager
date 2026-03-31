package org.ctc.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RaceSettingsTest {

    @Test
    void isComplete_allFieldsFilled_returnsTrue() {
        var settings = createCompleteSettings();
        assertThat(settings.isComplete()).isTrue();
    }

    @Test
    void isComplete_nullIntegerField_returnsFalse() {
        var settings = createCompleteSettings();
        settings.setNumberOfLaps(null);
        assertThat(settings.isComplete()).isFalse();
    }

    @Test
    void isComplete_blankStringField_returnsFalse() {
        var settings = createCompleteSettings();
        settings.setWeather("   ");
        assertThat(settings.isComplete()).isFalse();
    }

    @Test
    void isComplete_nullStringField_returnsFalse() {
        var settings = createCompleteSettings();
        settings.setInitialFuel(null);
        assertThat(settings.isComplete()).isFalse();
    }

    @Test
    void isComplete_emptySettings_returnsFalse() {
        var settings = new RaceSettings(new Race());
        assertThat(settings.isComplete()).isFalse();
    }

    @Test
    void isComplete_eachIntegerFieldNull_returnsFalse() {
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
    void isComplete_eachStringFieldBlank_returnsFalse() {
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
