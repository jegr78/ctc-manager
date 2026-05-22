package org.ctc.domain.repository;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class MatchRepositoryDiscordFieldsIT {

	@Test
	void givenMatchWith7DiscordFields_whenSaveAndReload_thenAllFieldsPersist() {
		fail("not yet implemented");
	}

	@Test
	void givenMatchWithNullDiscordFields_whenSave_thenColumnsNullable() {
		fail("not yet implemented");
	}

	@Test
	void givenMatchWithDiscordFields_whenClearFields_thenColumnsUpdateToNull() {
		fail("not yet implemented");
	}
}
