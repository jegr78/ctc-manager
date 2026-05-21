package org.ctc.domain.repository;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class TeamRepositoryDiscordRoleIdIT {

	@Autowired
	private TeamRepository teamRepository;

	@Test
	void givenTeamWithDiscordRoleId_whenSaveAndReload_thenSnowflakePersists() {
		fail("not yet implemented");
	}

	@Test
	void givenTeamWithNullDiscordRoleId_whenSave_thenColumnNullable() {
		fail("not yet implemented");
	}

	@Test
	void givenTeamSavedWithRoleId_whenClearRoleId_thenColumnUpdatesToNull() {
		fail("not yet implemented");
	}
}
