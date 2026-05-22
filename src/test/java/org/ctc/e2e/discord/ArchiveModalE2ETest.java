package org.ctc.e2e.discord;

import static org.junit.jupiter.api.Assertions.fail;

import org.ctc.e2e.PlaywrightConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("e2e")
class ArchiveModalE2ETest extends PlaywrightConfig {

	@BeforeEach
	void setUp() {
		setupPage();
	}

	@AfterEach
	void tearDown() {
		teardownPage();
	}

	@Test
	void givenChannelExistsAndCategoriesAvailable_whenOpenModal_thenCategoriesAndCountsAndDefaultRadioVisible() {
		fail("not yet implemented");
	}

	@Test
	void givenAllCategoriesFull_whenOpenModal_thenWarningBannerAndConfirmDisabled() {
		fail("not yet implemented");
	}

	@Test
	void givenSelectAndConfirm_whenSubmitForm_thenMoveToArchiveEndpointInvoked() {
		fail("not yet implemented");
	}

	@Test
	void givenMobileViewport_whenOpenModal_thenNoHorizontalOverflow() {
		fail("not yet implemented");
	}
}
