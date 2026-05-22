package org.ctc.e2e.discord;

import static org.junit.jupiter.api.Assertions.fail;

import org.ctc.e2e.PlaywrightConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("e2e")
class MatchDetailControllerE2ETest extends PlaywrightConfig {

	@BeforeEach
	void setUp() {
		setupPage();
	}

	@AfterEach
	void tearDown() {
		teardownPage();
	}

	@Test
	void givenMatchWithoutDiscord_whenLoadDetail_thenHeaderAndDiscordActionsAndScheduleAndRacesVisible() {
		fail("not yet implemented");
	}

	@Test
	void givenMatchWithBothTeamRolesAndCategorySet_whenLoadDetail_thenCreateChannelEnabled() {
		fail("not yet implemented");
	}

	@Test
	void givenMatchWithChannel_whenLoadDetail_thenChannelBadgeAndArchiveModalTriggerVisible() {
		fail("not yet implemented");
	}

	@Test
	void givenMobileViewport_whenLoadDetail_thenNoHorizontalOverflow() {
		fail("not yet implemented");
	}
}
