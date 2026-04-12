package org.ctc.domain.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityNotFoundExceptionTest {

	@Test
	void whenCreatedWithTypeAndId_thenMessageContainsBoth() {
		// when
		var exception = new EntityNotFoundException("Season", 42L);

		// then
		assertThat(exception.getMessage()).isEqualTo("Season not found with id: 42");
	}

	@Test
	void whenCreatedWithTypeAndId_thenGettersReturnValues() {
		// when
		var exception = new EntityNotFoundException("Season", 42L);

		// then
		assertThat(exception.getEntityType()).isEqualTo("Season");
		assertThat(exception.getEntityId()).isEqualTo(42L);
	}

	@Test
	void whenCreatedWithStringId_thenMessageContainsString() {
		// when
		var exception = new EntityNotFoundException("Team", "abc-123");

		// then
		assertThat(exception.getMessage()).isEqualTo("Team not found with id: abc-123");
	}
}
