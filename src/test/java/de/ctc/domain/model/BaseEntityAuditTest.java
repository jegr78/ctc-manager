package de.ctc.domain.model;

import de.ctc.domain.repository.DriverRepository;
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
    void shouldSetCreatedAtAndUpdatedAtOnSave() {
        var driver = new Driver("audit_test_psn", "Audit Test");
        assertNull(driver.getCreatedAt());
        assertNull(driver.getUpdatedAt());

        var saved = driverRepository.save(driver);

        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void shouldUpdateUpdatedAtButNotCreatedAtOnUpdate() throws InterruptedException {
        var driver = new Driver("audit_update_psn", "Audit Update");
        var saved = driverRepository.save(driver);

        var createdAt = saved.getCreatedAt();
        var updatedAt = saved.getUpdatedAt();
        assertNotNull(createdAt);
        assertNotNull(updatedAt);

        Thread.sleep(50);

        saved.setNickname("Updated Nickname");
        var updated = driverRepository.save(saved);

        assertEquals(createdAt, updated.getCreatedAt());
        assertTrue(updated.getUpdatedAt().isAfter(updatedAt) || updated.getUpdatedAt().equals(updatedAt));
    }
}
