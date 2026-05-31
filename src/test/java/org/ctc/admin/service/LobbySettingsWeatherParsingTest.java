package org.ctc.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.ctc.admin.service.LobbySettingsGraphicService.WeatherDisplay;
import org.ctc.domain.model.Team;
import org.junit.jupiter.api.Test;

class LobbySettingsWeatherParsingTest {

	@Test
	void givenPresetPrefixedWeather_whenParsed_thenPresetCodeAndMethodPreset() {
		// given
		String raw = "Preset S02";

		// when
		WeatherDisplay result = LobbySettingsGraphicService.parseWeather(raw);

		// then
		assertThat(result.weatherMethod()).isEqualTo("Preset Weather");
		assertThat(result.presetWeather()).isEqualTo("S02");
		assertThat(result.customWeather()).isEqualTo("—");
	}

	@Test
	void givenCustomPrefixedWeather_whenParsed_thenSlotSequenceAndMethodCustom() {
		// given
		String raw = "Custom R01, ?, R04";

		// when
		WeatherDisplay result = LobbySettingsGraphicService.parseWeather(raw);

		// then
		assertThat(result.weatherMethod()).isEqualTo("Custom Weather");
		assertThat(result.customWeather()).isEqualTo("R01, ?, R04");
		assertThat(result.presetWeather()).isEqualTo("—");
	}

	@Test
	void givenUnprefixedWeather_whenParsed_thenTreatedAsPreset() {
		// given
		String raw = "S02";

		// when
		WeatherDisplay result = LobbySettingsGraphicService.parseWeather(raw);

		// then
		assertThat(result.weatherMethod()).isEqualTo("Preset Weather");
		assertThat(result.presetWeather()).isEqualTo("S02");
		assertThat(result.customWeather()).isEqualTo("—");
	}

	@Test
	void givenBlankWeather_whenParsed_thenDashFallbacks() {
		// given / when
		WeatherDisplay fromNull = LobbySettingsGraphicService.parseWeather(null);
		WeatherDisplay fromEmpty = LobbySettingsGraphicService.parseWeather("   ");

		// then
		assertThat(fromNull.weatherMethod()).isEqualTo("Preset Weather");
		assertThat(fromNull.presetWeather()).isEqualTo("—");
		assertThat(fromNull.customWeather()).isEqualTo("—");
		assertThat(fromEmpty.presetWeather()).isEqualTo("—");
		assertThat(fromEmpty.customWeather()).isEqualTo("—");
	}

	@Test
	void givenBothTeams_whenRoomNameBuilt_thenFullFormat() {
		// given
		Team home = new Team("Power One Racing", "P1R");
		Team away = new Team("Vez Racing", "VEZ");

		// when
		String roomName = LobbySettingsGraphicService.buildRoomName(2026, "MD4", home, away);

		// then
		assertThat(roomName).isEqualTo("CTC – 2026 – MD4 – P1R vs. VEZ");
	}

	@Test
	void givenMissingTeam_whenRoomNameBuilt_thenShortFormat() {
		// given
		Team home = new Team("Power One Racing", "P1R");

		// when
		String roomName = LobbySettingsGraphicService.buildRoomName(2026, "MD4", home, null);

		// then
		assertThat(roomName).isEqualTo("CTC – 2026 – MD4");
	}
}
