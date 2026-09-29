package org.ctc.backup.service;

import java.nio.file.Path;
import java.util.UUID;
import org.ctc.backup.dto.BackupImportResult;
import org.ctc.backup.exception.UploadsRestoreException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackupImportCoordinatorTest {

	private static final UUID STAGING_ID = UUID.randomUUID();
	private static final UUID AUDIT_ID = UUID.randomUUID();
	private static final Path RECOVERY_DIR = Path.of("/app/data/import-backups/ts");

	private final BackupImportService importService = mock(BackupImportService.class);
	private final BackupImportOutcomeRegistry registry = new BackupImportOutcomeRegistry();
	private final BackupImportCoordinator coordinator = new BackupImportCoordinator(importService, registry);

	@Test
	void givenUploadsRestored_whenExecuted_thenResultReturnedAndOutcomeConsumed() {
		// given
		BackupImportResult result = new BackupImportResult(AUDIT_ID, 5, 2);
		when(importService.execute(STAGING_ID)).thenReturn(result);
		registry.record(AUDIT_ID, UploadsRestoreOutcome.restored(RECOVERY_DIR));

		// when
		BackupImportResult returned = coordinator.execute(STAGING_ID);

		// then
		assertThat(returned).isSameAs(result);
		assertThat(registry.take(AUDIT_ID)).as("the outcome is removed once read").isEmpty();
	}

	@Test
	void givenUploadsSwapFailed_whenExecuted_thenUploadsRestoreExceptionCarriesAuditAndRecoveryDir() {
		// given
		when(importService.execute(STAGING_ID)).thenReturn(new BackupImportResult(AUDIT_ID, 5, 2));
		registry.record(AUDIT_ID, UploadsRestoreOutcome.failed(RECOVERY_DIR, "rename failed"));

		// when / then
		assertThatThrownBy(() -> coordinator.execute(STAGING_ID))
				.isInstanceOfSatisfying(UploadsRestoreException.class, ex -> {
					assertThat(ex.getAuditUuid()).isEqualTo(AUDIT_ID);
					assertThat(ex.getRecoveryDir()).isEqualTo(RECOVERY_DIR);
					assertThat(ex.getMessage()).isEqualTo("rename failed");
				});
	}

	@Test
	void givenNoOutcomeRecorded_whenExecuted_thenTreatedAsFailedInsteadOfSuccess() {
		// given
		when(importService.execute(STAGING_ID)).thenReturn(new BackupImportResult(AUDIT_ID, 5, 2));

		// when / then
		assertThatThrownBy(() -> coordinator.execute(STAGING_ID))
				.isInstanceOf(UploadsRestoreException.class)
				.hasMessage("the uploads swap did not report a result");
	}
}
