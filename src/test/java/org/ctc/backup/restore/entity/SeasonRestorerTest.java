package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

/**
 * Phase 75 / Plan 03 — Surefire unit test for {@link SeasonRestorer}.
 *
 * <p>Asserts:
 * <ul>
 *   <li>{@code tableName()} returns the exact snake_case table name {@code "seasons"}.</li>
 *   <li>{@code restore(...)} issues a single {@link JdbcTemplate#batchUpdate(String, List, int,
 *       ParameterizedPreparedStatementSetter)} call with an {@code INSERT INTO seasons (...) VALUES (?, ?, ...)}
 *       statement that matches the well-formedness regex (no string concatenation, one {@code ?}
 *       per column).</li>
 *   <li>The captured setter binds JSON values to the {@link PreparedStatement} with the correct
 *       JDBC types: native UUID via {@code setObject}, VARCHAR via {@code setString}, INT via
 *       {@code setInt}, BOOLEAN via {@code setBoolean}, and TIMESTAMP via {@code Timestamp.valueOf(
 *       LocalDateTime.parse(...))} — preserving the verbatim {@code createdAt}/{@code updatedAt}
 *       values for the auditing bypass contract.</li>
 * </ul>
 *
 * <p>The {@code @Component} (production discovery) and {@code !@Primary} (no shadow override)
 * annotations are also smoke-checked so the orchestrator's {@code Map<String, EntityRestorer>}
 * wiring (Plan 06) resolves predictably.
 */
@ExtendWith(MockitoExtension.class)
class SeasonRestorerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PreparedStatement preparedStatement;

    private SeasonRestorer restorer;

    @BeforeEach
    void setUp() {
        restorer = new SeasonRestorer();
    }

    @Test
    void whenTableNameQueried_thenReturnsSeasons() {
        // given / when / then
        assertThat(restorer.tableName()).isEqualTo("seasons");
    }

    @Test
    void givenComponentAnnotationOnClass_whenInspected_thenPresentAndNotPrimary() {
        // given / when / then — discoverable via @Component, never @Primary
        assertThat(SeasonRestorer.class.isAnnotationPresent(Component.class)).isTrue();
        assertThat(SeasonRestorer.class.isAnnotationPresent(Primary.class)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenSingleSeasonRow_whenRestoreCalled_thenBatchUpdateInvokedWithWellFormedInsertSql() throws Exception {
        // given
        String json = """
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "name": "Season 2023",
                  "year": 2023,
                  "number": 1,
                  "description": "Round Robin — two groups",
                  "active": true,
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-06-01T12:30:45"
                }
                """;
        JsonNode row = MAPPER.readTree(json);

        // when
        restorer.restore(List.of(row), jdbcTemplate);

        // then — capture the SQL + setter
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), eq(List.of(row)), eq(500),
                setterCaptor.capture());

        // then — SQL is well-formed: INSERT INTO seasons (col, ...) VALUES (?, ?, ...)
        String sql = sqlCaptor.getValue();
        assertThat(sql).matches("^INSERT INTO seasons \\([^)]+\\) VALUES \\(\\?(, \\?)+\\)$");
        assertThat(sql).contains("id", "name", "season_year", "season_number",
                "description", "active", "created_at", "updated_at");

        // then — drive the setter against the mock and verify type-correct bindings
        setterCaptor.getValue().setValues(preparedStatement, row);

        verify(preparedStatement).setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        verify(preparedStatement).setString(2, "Season 2023");
        verify(preparedStatement).setInt(3, 2023);
        verify(preparedStatement).setInt(4, 1);
        verify(preparedStatement).setString(5, "Round Robin — two groups");
        verify(preparedStatement).setBoolean(6, true);
        verify(preparedStatement).setTimestamp(7,
                Timestamp.valueOf(LocalDateTime.parse("2024-01-01T00:00:00")));
        verify(preparedStatement).setTimestamp(8,
                Timestamp.valueOf(LocalDateTime.parse("2024-06-01T12:30:45")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenSeasonRowWithNullDescription_whenRestoreCalled_thenDescriptionSetToNull() throws Exception {
        // given — description is the only nullable column on seasons
        String json = """
                {
                  "id": "22222222-2222-2222-2222-222222222222",
                  "name": "Test Season",
                  "year": 2024,
                  "number": 2,
                  "description": null,
                  "active": false,
                  "createdAt": "2024-02-01T08:00:00",
                  "updatedAt": "2024-02-01T08:00:00"
                }
                """;
        JsonNode row = MAPPER.readTree(json);

        // when
        restorer.restore(List.of(row), jdbcTemplate);

        // then
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(any(String.class), eq(List.of(row)), anyInt(),
                setterCaptor.capture());
        setterCaptor.getValue().setValues(preparedStatement, row);

        verify(preparedStatement).setString(5, null);
        verify(preparedStatement).setBoolean(6, false);
    }
}
