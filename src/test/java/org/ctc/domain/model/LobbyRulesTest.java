package org.ctc.domain.model;

import java.util.Set;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class LobbyRulesTest {
    @ParameterizedTest
    @CsvSource({"TRACK,1", "CAR,2", "LAPS,4", "TYRE_WEAR,8", "FUEL_CONSUMPTION,16", "REFUELING_SPEED,32",
            "INITIAL_FUEL,64", "PIT_STOPS,128", "TIME_PROGRESSION,256", "WEATHER,512", "TIME_OF_DAY,1024",
            "AVAILABLE_TYRES,2048", "MANDATORY_TYRES,4096", "START_TYPE,8192", "SLIPSTREAM,16384"})
    void givenAnExistingOrAppendedDisclosureField_whenEncoding_thenPreserveItsExactWireBit(PickDisclosureField field, int bit) {
        // given / when / then
        assertThat(PickDisclosureField.mask(Set.of(field))).isEqualTo(bit);
        assertThat(PickDisclosureField.fields(bit)).containsExactly(field);
    }
    @ParameterizedTest
    @CsvSource({"LEGACY,GRID_FALSE_START,REAL,Off", "CTC_2027,GRID_FALSE_START,REAL,On",
            "CTC_2027,GRID_FALSE_START,WEAK,On", "CTC_2027,ROLLING,REAL,On", "CTC_2027,ROLLING,WEAK,On"})
    void givenApprovedPresetAndOptions_whenResolving_thenUseOnlyTheConfirmedLeagueChoices(
            LobbyRulesProfile profile, RaceStartType start, RaceSlipstream slipstream, String flags) {
        // given / when / then
        assertThatCode(() -> profile.requireChoices(start, slipstream)).doesNotThrowAnyException();
        assertThat(profile.getFlagRules()).isEqualTo(flags);
        assertThat(profile.getQualifyingMinutes()).isEqualTo(10);
        assertThat(profile.getQualifierSlipstream()).isEqualTo("Off");
    }

    @Test
    void givenLegacyPresetOrMissingChoices_whenSelectingNewOptions_thenRejectWithDomainGuidance() {
        // given / when / then
        assertThatThrownBy(() -> LobbyRulesProfile.LEGACY.requireChoices(RaceStartType.ROLLING, RaceSlipstream.REAL))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("season rules");
        assertThatThrownBy(() -> LobbyRulesProfile.LEGACY.requireChoices(RaceStartType.GRID_FALSE_START, RaceSlipstream.WEAK))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> LobbyRulesProfile.CTC_2027.requireChoices(null, RaceSlipstream.REAL))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> LobbyRulesProfile.CTC_2027.requireChoices(RaceStartType.ROLLING, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenNewSeasonAndEmpty2027Race_whenCapturing_thenKeepExplicitLegacyDefaultAndUnconfiguredDraftSemantics() {
        // given
        var season = new Season("Test rules");
        assertThat(season.getLobbyRulesProfile()).isEqualTo(LobbyRulesProfile.LEGACY);
        season.setLobbyRulesProfile(LobbyRulesProfile.CTC_2027);
        var phase = new SeasonPhase(); phase.setSeason(season);
        var day = new Matchday(); day.setPhase(phase);
        var race = new Race(); race.setMatchday(day);
        // when / then
        var empty = PickSetupSnapshot.from(race);
        assertThat(empty.lobbyRulesProfile()).isEqualTo(LobbyRulesProfile.CTC_2027);
        assertThat(empty.configured()).isFalse();
        race.setSettings(new RaceSettings(race));
        assertThat(PickSetupSnapshot.from(race)).isEqualTo(empty);
        race.getSettings().setStartType(RaceStartType.ROLLING);
        assertThat(PickSetupSnapshot.from(race).configured()).isTrue();
    }

    @Test
    void givenNewOptionsOrProfile_whenFingerprinting_thenBindEveryChoiceWithoutChangingLegacyCanonicalBytes() {
        // given
        var race = new Race(); var settings = new RaceSettings(race); race.setSettings(settings);
        var legacy = PickSetupSnapshot.from(race).fingerprint();
        var baseline = RaceSettingsValues.from(settings);
        var current = new PickSetupSnapshot(null, null, null, baseline, LobbyRulesProfile.CTC_2027);
        // when / then
        assertThat(current.fingerprint()).isNotEqualTo(legacy);
        settings.setStartType(RaceStartType.ROLLING);
        assertThat(PickSetupSnapshot.from(race).fingerprint()).isNotEqualTo(legacy);
        assertThat(new PickSetupSnapshot(null, null, null, RaceSettingsValues.from(settings), LobbyRulesProfile.CTC_2027).fingerprint())
                .isNotEqualTo(current.fingerprint());
        assertThat(new PickSetupSnapshot(null, null, null, RaceSettingsValues.from(settings), LobbyRulesProfile.CTC_2027).fingerprint())
                .isNotEqualTo(PickSetupSnapshot.from(race).fingerprint());
        settings.setStartType(RaceStartType.GRID_FALSE_START); settings.setSlipstream(RaceSlipstream.WEAK);
        assertThat(new PickSetupSnapshot(null, null, null, RaceSettingsValues.from(settings), LobbyRulesProfile.CTC_2027).fingerprint())
                .isNotEqualTo(current.fingerprint());
        assertThat(PickSetupSnapshot.from(race).fingerprint()).isNotEqualTo(legacy);
    }

    @Test
    void givenCaptured2027Options_whenProjectingOneField_thenKeepUnreleasedChoicesNullAndPreserveOldMaskBits() {
        // given
        var content = new PickPublicationContent(null, null, null, null, null, null, 12, 2, 3, 4, "Default", 1,
                TimeProgressionMode.MULTIPLIER, 0, "Test private weather", "Night", "RS, RM", "RM",
                LobbyRulesProfile.CTC_2027, RaceStartType.ROLLING, RaceSlipstream.WEAK);
        // when / then
        var weather = content.project(UUID.randomUUID(), Set.of(PickDisclosureField.WEATHER)).settings();
        assertThat(weather.weather()).isEqualTo("Test private weather");
        assertThat(weather.startType()).isNull(); assertThat(weather.slipstream()).isNull();
        var start = content.project(UUID.randomUUID(), Set.of(PickDisclosureField.START_TYPE)).settings();
        assertThat(start.startType()).isEqualTo(RaceStartType.ROLLING); assertThat(start.slipstream()).isNull();
        assertThat(start.weather()).isNull();
        assertThat(PickDisclosureField.fields(8191)).doesNotContain(PickDisclosureField.START_TYPE, PickDisclosureField.SLIPSTREAM);
        assertThat(content.settings().slipstream()).isEqualTo(RaceSlipstream.WEAK);
    }

    @Test
    void givenMissingCapturedRulesOrForbiddenLegacyChoices_whenConstructingPayload_thenRejectRatherThanDefault() {
        // given / when / then
        assertThatThrownBy(() -> payload(null, RaceStartType.GRID_FALSE_START, RaceSlipstream.REAL)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> payload(LobbyRulesProfile.CTC_2027, null, RaceSlipstream.REAL)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> payload(LobbyRulesProfile.CTC_2027, RaceStartType.GRID_FALSE_START, null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> payload(LobbyRulesProfile.LEGACY, RaceStartType.ROLLING, RaceSlipstream.REAL)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenIdenticalValuesFromDifferentProfiles_whenComparingSnapshots_thenRequireTheCapturedProfileToo() {
        // given
        var payload = payload(LobbyRulesProfile.CTC_2027, RaceStartType.GRID_FALSE_START, RaceSlipstream.REAL);
        var values = RaceSettingsValues.from(payload);
        // when / then
        assertThat(new PickSetupSnapshot(payload.trackId(), payload.carId(), null, values, LobbyRulesProfile.LEGACY).matches(payload)).isFalse();
        assertThat(new PickSetupSnapshot(payload.trackId(), payload.carId(), null, values, LobbyRulesProfile.CTC_2027).matches(payload)).isTrue();
    }

    private static PickPayload payload(LobbyRulesProfile profile, RaceStartType start, RaceSlipstream slipstream) {
        return new PickPayload(UUID.randomUUID(), UUID.randomUUID(), null, 12, 2, 3, 4, PickInitialFuel.DEFAULT, 1,
                TimeProgressionMode.MULTIPLIER, 0, "Test weather", "Sunset", Set.of(PickTyre.RS), Set.of(), profile, start, slipstream);
    }
}
