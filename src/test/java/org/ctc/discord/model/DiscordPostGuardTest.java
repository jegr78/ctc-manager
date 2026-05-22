package org.ctc.discord.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.ctc.backup.schema.BackupSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class DiscordPostGuardTest {

	@Autowired
	private BackupSchema backupSchema;

	@Test
	void givenPhase95Migration_whenInspectExportOrder_thenCountStaysAt24() {
		assertThat(backupSchema.getExportOrder())
				.as("Phase 95 must not bump BackupSchema.EXPORT_ORDER; DiscordPost is "
						+ "structurally excluded by the org.ctc.domain.model.* package filter.")
				.hasSize(24);
	}

	@Test
	void givenPhase95Migration_whenInspectSchemaVersion_thenStaysAtOne() {
		assertThat(BackupSchema.SCHEMA_VERSION)
				.as("Phase 95 must not bump SCHEMA_VERSION — DiscordPost is not part of the wire contract.")
				.isEqualTo(1);
	}

	@Test
	void givenPhase95Migration_whenInspectExportOrder_thenDoesNotContainDiscordPost() {
		assertThat(backupSchema.getExportOrder())
				.as("DiscordPost must not appear in the backup export order.")
				.noneMatch(ref -> "discord_post".equalsIgnoreCase(ref.tableName()));
	}
}
