package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Phase 75 / Plan 03 — Surefire unit test for {@link PsnAliasRestorer}.
 *
 * <p>Note: the V1 schema column is {@code alias} (UNIQUE); the plan's interfaces block listed
 * {@code psn_alias} / {@code alias_lower} / {@code valid_from} / {@code valid_to} which do not
 * exist in V1__initial_schema.sql:300-310. The schema (and the {@code PsnAlias} entity field
 * {@code alias}) is the source of truth (Rule 3 — blocking issue resolved by following
 * V1__initial_schema.sql).
 */
@ExtendWith(MockitoExtension.class)
class PsnAliasRestorerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PreparedStatement preparedStatement;

    private PsnAliasRestorer restorer;

    @BeforeEach
    void setUp() {
        restorer = new PsnAliasRestorer();
    }

    @Test
    void whenTableNameQueried_thenReturnsPsnAliases() {
        assertThat(restorer.tableName()).isEqualTo("psn_aliases");
    }

    @Test
    void givenComponentAnnotationOnClass_whenInspected_thenPresentAndNotPrimary() {
        assertThat(PsnAliasRestorer.class.isAnnotationPresent(Component.class)).isTrue();
        assertThat(PsnAliasRestorer.class.isAnnotationPresent(Primary.class)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenSinglePsnAliasRow_whenRestoreCalled_thenBatchUpdateInvokedWithWellFormedInsertSql() throws Exception {
        // given
        String json = """
                {
                  "id": "11111111-6666-6666-6666-111111111111",
                  "alias": "AliasOne",
                  "driver": "bbbbbbbb-6666-6666-6666-bbbbbbbbbbbb",
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-01-01T00:00:00"
                }
                """;
        JsonNode row = MAPPER.readTree(json);

        // when
        restorer.restore(List.of(row), jdbcTemplate);

        // then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ParameterizedPreparedStatementSetter<JsonNode>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), eq(List.of(row)), eq(500),
                setterCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertThat(sql).matches("^INSERT INTO psn_aliases \\([^)]+\\) VALUES \\(\\?(, \\?)+\\)$");
        assertThat(sql).contains("id", "driver_id", "alias", "created_at", "updated_at");

        setterCaptor.getValue().setValues(preparedStatement, row);

        verify(preparedStatement).setObject(1, UUID.fromString("11111111-6666-6666-6666-111111111111"));
        verify(preparedStatement).setObject(2, UUID.fromString("bbbbbbbb-6666-6666-6666-bbbbbbbbbbbb"));
        verify(preparedStatement).setString(3, "AliasOne");
        verify(preparedStatement).setTimestamp(4,
                Timestamp.valueOf(LocalDateTime.parse("2024-01-01T00:00:00")));
        verify(preparedStatement).setTimestamp(5,
                Timestamp.valueOf(LocalDateTime.parse("2024-01-01T00:00:00")));
    }
}
