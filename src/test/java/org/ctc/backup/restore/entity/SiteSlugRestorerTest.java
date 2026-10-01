package org.ctc.backup.restore.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.ctc.backup.exception.BackupArchiveException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;

class SiteSlugRestorerTest {

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

	@ParameterizedTest
	@CsvSource({
			"TEAM, ../../uploads/x, team-a",
			"TEAM, team-a, javascript:alert(1)",
			"DRIVER, Team-A, team-a",
			"VENUE, team-a, team-a"
	})
	void givenRowOutsideTheSlugFormat_whenRestored_thenTheImportIsRejectedBeforeAnyInsert(
			String kind, String slug, String baseSlug) throws Exception {
		// given
		JsonNode row = new ObjectMapper().readTree("""
				{"id":"6a1f6f8e-3c55-4b55-9d64-8f0d5c1b2a10","kind":"%s","slug":"%s","baseSlug":"%s",
				 "entityId":null,"createdAt":"2026-09-30T10:00:00","updatedAt":"2026-09-30T10:00:00"}
				""".formatted(kind, slug, baseSlug));

		// when / then
		assertThatThrownBy(() -> new SiteSlugRestorer().restore(List.of(row), jdbcTemplate))
				.isInstanceOf(BackupArchiveException.class)
				.hasMessageContaining("site slug");
		verifyNoInteractions(jdbcTemplate);
	}
}
