package org.ctc.backup.restore.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.util.List;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

class DiscordGlobalConfigRestorerGuardTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String COMPLETE_ROW = """
			{
			  "id": 1,
			  "guildId": "g-1",
			  "announcementWebhookUrl": "https://discord.com/api/webhooks/w/tok",
			  "raceResultsForumChannelId": "ch-1",
			  "standingsForumChannelId": "ch-2",
			  "raceResultsForumWebhookUrl": null,
			  "standingsForumWebhookUrl": null,
			  "vsEmojiName": "vs",
			  "botApplicationId": null,
			  "currentMatchCategoryId": "cat-1",
			  "matchdayPairingsTemplate": null,
			  "createdAt": "2026-05-28T10:00:00",
			  "updatedAt": "2026-05-28T10:00:00"
			}
			""";

	private static JsonNode rowWithoutField(String fieldToOmit) throws Exception {
		var node = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(COMPLETE_ROW);
		node.remove(fieldToOmit);
		return node;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void invokeBatch(DiscordGlobalConfigRestorer restorer, JsonNode row) {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		doAnswer(inv -> {
			ParameterizedPreparedStatementSetter<JsonNode> pss = inv.getArgument(3);
			PreparedStatement ps = mock(PreparedStatement.class);
			JsonNode singleRow = (JsonNode) ((java.util.Collection) inv.getArgument(1)).iterator().next();
			pss.setValues(ps, singleRow);
			return new int[][]{{1}};
		}).when(jdbc).batchUpdate(anyString(), any(java.util.Collection.class), anyInt(),
				any(ParameterizedPreparedStatementSetter.class));
		restorer.restore(List.of(row), jdbc);
	}

	private static void assertGuardThrowsForMissingField(String missingField) throws Exception {
		DiscordGlobalConfigRestorer restorer = new DiscordGlobalConfigRestorer();
		JsonNode row = rowWithoutField(missingField);

		assertThatThrownBy(() -> invokeBatch(restorer, row))
				.isInstanceOf(BackupArchiveException.class)
				.satisfies(t -> {
					BackupArchiveException e = (BackupArchiveException) t;
					assertThat(e.reason()).isEqualTo(Reason.MANIFEST_INVALID);
					assertThat(e.getMessage()).contains(missingField);
				});
	}

	@Test
	void givenMissingGuildId_whenRestore_thenManifestInvalid() throws Exception {
		assertGuardThrowsForMissingField("guildId");
	}

	@Test
	void givenMissingAnnouncementWebhookUrl_whenRestore_thenManifestInvalid() throws Exception {
		assertGuardThrowsForMissingField("announcementWebhookUrl");
	}

	@Test
	void givenMissingRaceResultsForumChannelId_whenRestore_thenManifestInvalid() throws Exception {
		assertGuardThrowsForMissingField("raceResultsForumChannelId");
	}

	@Test
	void givenMissingStandingsForumChannelId_whenRestore_thenManifestInvalid() throws Exception {
		assertGuardThrowsForMissingField("standingsForumChannelId");
	}

	@Test
	void givenMissingVsEmojiName_whenRestore_thenManifestInvalid() throws Exception {
		assertGuardThrowsForMissingField("vsEmojiName");
	}

	@Test
	void givenMissingCurrentMatchCategoryId_whenRestore_thenManifestInvalid() throws Exception {
		assertGuardThrowsForMissingField("currentMatchCategoryId");
	}

	@Test
	void givenCompleteRow_whenRestore_thenNoException() throws Exception {
		DiscordGlobalConfigRestorer restorer = new DiscordGlobalConfigRestorer();
		JsonNode row = MAPPER.readTree(COMPLETE_ROW);

		invokeBatch(restorer, row);
	}
}
