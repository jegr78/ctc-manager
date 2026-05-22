package org.ctc.discord.service;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
class MatchEditFormIT {

	@Test
	void givenValidForm_whenPostSaveEdit_thenFlashSuccessAndFieldsPersisted() {
		fail("not yet implemented");
	}

	@Test
	void givenOversizedTeaser_whenPostSaveEdit_thenRendersFormWithErrorBadge() {
		fail("not yet implemented");
	}

	@Test
	void givenBlankForm_whenPostSaveEdit_thenAllFieldsClearedAndPersisted() {
		fail("not yet implemented");
	}
}
