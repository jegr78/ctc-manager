package org.ctc.discord.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.ctc.backup.schema.BackupSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 93 invariant: DiscordGlobalConfig lives under org.ctc.discord.model.* (NOT
 * org.ctc.domain.model.*), so the Phase 72 D-15 BackupSchema package filter excludes it
 * from BackupSchema.EXPORT_ORDER. The export-order size therefore MUST stay at 24 — any
 * regression (moving the entity into domain.model or relaxing the package filter) bumps
 * the count and the wire-contract drifts.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class DiscordGlobalConfigGuardTest {

	@Autowired
	private BackupSchema backupSchema;

	@Test
	void givenPhase93Migration_whenInspectExportOrder_thenCountStaysAt24() {
		assertThat(backupSchema.getExportOrder())
				.as("Phase 93 must not bump BackupSchema.EXPORT_ORDER; DiscordGlobalConfig is "
						+ "structurally excluded by the org.ctc.domain.model.* package filter.")
				.hasSize(24);
	}

	@Test
	void givenPhase93Migration_whenInspectExportOrder_thenDoesNotContainDiscordGlobalConfig() {
		assertThat(backupSchema.getExportOrder())
				.as("DiscordGlobalConfig must not appear in the backup export order.")
				.noneMatch(ref -> "discord_global_config".equalsIgnoreCase(ref.tableName()));
	}
}
