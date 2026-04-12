package org.ctc.domain.model;

import org.ctc.domain.repository.DriverRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class BaseEntityAuditTest {

	@Autowired
	private DriverRepository driverRepository;

	@Test
	void givenNewEntity_whenSaved_thenCreatedAtAndUpdatedAtAreSet() {
		// given
		var driver = new Driver("audit_test_psn", "Audit Test");
		assertNull(driver.getCreatedAt());
		assertNull(driver.getUpdatedAt());

		// when
		var saved = driverRepository.save(driver);

		// then
		assertNotNull(saved.getCreatedAt());
		assertNotNull(saved.getUpdatedAt());
	}

	@Test
	void givenSavedEntity_whenUpdated_thenUpdatedAtChangesButCreatedAtDoesNot() throws InterruptedException {
		// given
		var driver = new Driver("audit_update_psn", "Audit Update");
		var saved = driverRepository.save(driver);

		var createdAt = saved.getCreatedAt();
		var updatedAt = saved.getUpdatedAt();
		assertNotNull(createdAt);
		assertNotNull(updatedAt);

		Thread.sleep(50);

		// when
		saved.setNickname("Updated Nickname");
		var updated = driverRepository.save(saved);

		// then
		assertEquals(createdAt, updated.getCreatedAt());
		assertTrue(updated.getUpdatedAt().isAfter(updatedAt) || updated.getUpdatedAt().equals(updatedAt));
	}
}
