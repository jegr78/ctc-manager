package org.ctc.discord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.model.Team;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DiscordChannelServiceNamingTest {

	@ParameterizedTest
	@CsvSource({
			"REGULAR,rs,2,md3-rs-alf-vs-bra",
			"PLAYOFF,po,0,md1-po-alf-vs-bra",
			"PLACEMENT,pm,0,md1-pm-alf-vs-bra"
	})
	void givenMatchWithPhaseTypeAndNoGroup_whenChannelName_thenBaselineFormat(
			PhaseType phaseType, String expectedAbbrev, int sortIndex, String expected) {
		Match match = buildMatch(phaseType, null, sortIndex, "alf", "bra");

		String name = DiscordChannelService.channelName(match);

		assertThat(name).isEqualTo(expected);
		assertThat(name).contains("-" + expectedAbbrev + "-");
	}

	@ParameterizedTest
	@CsvSource({
			"Group A,group-a",
			"Group B,group-b",
			"Bronze,bronze",
			"Pro Division,pro-division",
			"Über-Liga,uber-liga",
			"Group A!!!,group-a"
	})
	void givenMatchWithGroupName_whenChannelName_thenGroupSlugInsertedAfterPhase(
			String groupName, String expectedSlug) {
		Match match = buildMatch(PhaseType.REGULAR, groupName, 2, "alf", "bra");

		String name = DiscordChannelService.channelName(match);

		assertThat(name).isEqualTo("md3-rs-" + expectedSlug + "-alf-vs-bra");
	}

	@Test
	void givenMatchWithGroupNameThatSlugifiesToEmpty_whenChannelName_thenGroupTokenOmitted() {
		Match match = buildMatch(PhaseType.REGULAR, "!!!", 0, "alf", "bra");

		String name = DiscordChannelService.channelName(match);

		assertThat(name).isEqualTo("md1-rs-alf-vs-bra");
	}

	@Test
	void givenMixedCaseTeamShortNames_whenChannelName_thenLowercased() {
		Match match = buildMatch(PhaseType.REGULAR, null, 0, "ALF", "Bra");

		String name = DiscordChannelService.channelName(match);

		assertThat(name).isEqualTo("md1-rs-alf-vs-bra");
	}

	@Test
	void givenGroupNameThatPushesOverHundredChars_whenChannelName_thenBusinessRuleExceptionWithLengthInMessage() {
		String hugeGroupName = "a".repeat(95);
		Match match = buildMatch(PhaseType.REGULAR, hugeGroupName, 0, "ho", "aw");

		assertThatThrownBy(() -> DiscordChannelService.channelName(match))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("exceeds 100 characters")
				.hasMessageContaining("111");
	}

	@Test
	void givenNameLandsAtExactly100Chars_whenChannelName_thenAccepted() {
		String group = "a".repeat(84);
		Match match = buildMatch(PhaseType.REGULAR, group, 0, "ho", "aw");

		String name = DiscordChannelService.channelName(match);

		assertThat(name).hasSize(100);
		assertThat(name).endsWith("ho-vs-aw");
	}

	@Test
	void givenMatchWithoutMatchday_whenChannelName_thenBusinessRuleException() {
		Match match = new Match();
		match.setHomeTeam(team("alf"));
		match.setAwayTeam(team("bra"));

		assertThatThrownBy(() -> DiscordChannelService.channelName(match))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("matchday with phase");
	}

	private static Match buildMatch(PhaseType phaseType, String groupName, int sortIndex,
			String homeShortName, String awayShortName) {
		Season season = new Season();
		SeasonPhase phase = new SeasonPhase(season, phaseType, PhaseLayout.LEAGUE, 0);
		SeasonPhaseGroup group = groupName == null ? null : new SeasonPhaseGroup(phase, groupName, 0);
		Matchday matchday = new Matchday();
		matchday.setPhase(phase);
		matchday.setGroup(group);
		matchday.setSortIndex(sortIndex);
		matchday.setLabel("md");
		Match match = new Match();
		match.setMatchday(matchday);
		match.setHomeTeam(team(homeShortName));
		match.setAwayTeam(team(awayShortName));
		return match;
	}

	private static Team team(String shortName) {
		Team t = new Team();
		t.setShortName(shortName);
		return t;
	}
}
