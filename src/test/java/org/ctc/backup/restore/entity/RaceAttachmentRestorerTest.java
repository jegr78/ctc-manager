package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Phase 75 / Plan 04 — Unit test for {@link RaceAttachmentRestorer}.
 *
 * <p>Schema (V1): {@code id UUID PK, race_id UUID NOT NULL, type VARCHAR(10) NOT NULL,
 * name VARCHAR NOT NULL, url VARCHAR(1000) NOT NULL, created_at TIMESTAMP,
 * updated_at TIMESTAMP}.
 *
 * <p>Critical: {@code AttachmentType} is {@code @Enumerated(EnumType.STRING)} per
 * {@code RaceAttachment.java} — MUST bind via {@code setString}, never via {@code setInt}.
 */
class RaceAttachmentRestorerTest {

    private final RaceAttachmentRestorer restorer = new RaceAttachmentRestorer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void whenTableNameCalled_thenReturnsRaceAttachments() {
        assertThat(restorer.tableName()).isEqualTo("race_attachments");
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenSampleAttachment_whenRestoreCalled_thenTypeBoundAsString() throws Exception {
        // given — FILE attachment per AttachmentType enum-as-VARCHAR
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JsonNode row = mapper.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "race": "22222222-2222-2222-2222-222222222222",
                  "type": "FILE",
                  "name": "qualifying-screenshot.png",
                  "url": "/uploads/2025/race-1/qualifying-screenshot.png",
                  "createdAt": "2025-04-15T22:00:00",
                  "updatedAt": "2025-04-15T22:00:00"
                }
                """);

        // when
        restorer.restore(List.of(row), jdbc);

        // then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbc, times(1)).batchUpdate(sqlCaptor.capture(), any(List.class),
                eq(500), setterCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .matches("^INSERT INTO race_attachments \\(.+\\) VALUES \\(\\?(?:, \\?)+\\)$")
                .contains("race_id")
                .contains("type")
                .contains("name")
                .contains("url");

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);
        verify(ps).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        verify(ps).setObject(2, UUID.fromString("22222222-2222-2222-2222-222222222222"));
        verify(ps).setString(3, "FILE");
        verify(ps).setString(4, "qualifying-screenshot.png");
        verify(ps).setString(5, "/uploads/2025/race-1/qualifying-screenshot.png");
        verify(ps).setTimestamp(6, Timestamp.valueOf("2025-04-15 22:00:00"));
        verify(ps).setTimestamp(7, Timestamp.valueOf("2025-04-15 22:00:00"));

        // critical negative assertion: type MUST NOT bind as int (@Enumerated(STRING) contract)
        verify(ps, never()).setInt(eq(3), any(Integer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenLinkAttachment_whenRestoreCalled_thenLinkTypeBoundAsString() throws Exception {
        // given — LINK variant of the AttachmentType enum
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JsonNode row = mapper.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "race": "22222222-2222-2222-2222-222222222222",
                  "type": "LINK",
                  "name": "YouTube highlight",
                  "url": "https://youtube.com/watch?v=abc",
                  "createdAt": "2025-04-15T22:00:00",
                  "updatedAt": "2025-04-15T22:00:00"
                }
                """);

        // when
        restorer.restore(List.of(row), jdbc);

        // then
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbc).batchUpdate(any(String.class), any(List.class), eq(500), setterCaptor.capture());

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, row);
        verify(ps).setString(3, "LINK");
    }
}
