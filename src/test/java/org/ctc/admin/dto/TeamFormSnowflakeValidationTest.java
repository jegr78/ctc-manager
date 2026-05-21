package org.ctc.admin.dto;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class TeamFormSnowflakeValidationTest {

	@Test
	void givenEmptyDiscordRoleId_whenValidate_thenNoViolation() {
		fail("not yet implemented");
	}

	@Test
	void givenValidSnowflake_whenValidate_thenNoViolation() {
		fail("not yet implemented");
	}

	@Test
	void givenInvalidDiscordRoleId_whenValidate_thenPatternViolation() {
		fail("not yet implemented");
	}

	@Test
	void givenName_shortName_required_whenValidate_thenViolation() {
		fail("not yet implemented");
	}
}
