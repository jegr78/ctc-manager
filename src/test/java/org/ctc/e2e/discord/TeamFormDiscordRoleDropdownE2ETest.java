package org.ctc.e2e.discord;

import static org.junit.jupiter.api.Assertions.fail;

import org.ctc.e2e.PlaywrightConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("e2e")
class TeamFormDiscordRoleDropdownE2ETest extends PlaywrightConfig {

	@Test
	void givenColdCache_whenLoadTeamEditPage_thenRendersPlainTextWithBadgeWarning() {
		fail("not yet implemented");
	}

	@Test
	void givenWarmCache_whenLoadTeamEditPage_thenRendersSearchableDropdown() {
		fail("not yet implemented");
	}

	@Test
	void givenMobileViewport_whenLoadTeamEditPage_thenLayoutIsMobileCorrect() {
		fail("not yet implemented");
	}
}
