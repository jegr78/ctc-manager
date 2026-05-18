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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Phase 75 / Plan 03 — Surefire unit test for {@link DriverRestorer}.
 */
@ExtendWith(MockitoExtension.class)
class DriverRestorerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PreparedStatement preparedStatement;

    private DriverRestorer restorer;

    @BeforeEach
    void setUp() {
        restorer = new DriverRestorer();
    }

    @Test
    void whenTableNameQueried_thenReturnsDrivers() {
        assertThat(restorer.tableName()).isEqualTo("drivers");
    }

    @Test
    void givenComponentAnnotationOnClass_whenInspected_thenPresentAndNotPrimary() {
        assertThat(DriverRestorer.class.isAnnotationPresent(Component.class)).isTrue();
        assertThat(DriverRestorer.class.isAnnotationPresent(Primary.class)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenSingleDriverRow_whenRestoreCalled_thenBatchUpdateInvokedWithWellFormedInsertSql() throws Exception {
        // given
        String json = """
                {
                  "id": "12345678-1234-1234-1234-123456789012",
                  "psnId": "PSN_TEST_01",
                  "nickname": "TestDriver",
                  "active": true,
                  "createdAt": "2024-01-01T00:00:00",
                  "updatedAt": "2024-01-15T10:00:00"
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
        assertThat(sql).matches("^INSERT INTO drivers \\([^)]+\\) VALUES \\(\\?(, \\?)+\\)$");
        assertThat(sql).contains("id", "psn_id", "nickname", "active", "created_at", "updated_at");

        setterCaptor.getValue().setValues(preparedStatement, row);

        verify(preparedStatement).setObject(1, UUID.fromString("12345678-1234-1234-1234-123456789012"));
        verify(preparedStatement).setString(2, "PSN_TEST_01");
        verify(preparedStatement).setString(3, "TestDriver");
        verify(preparedStatement).setBoolean(4, true);
        verify(preparedStatement).setTimestamp(5,
                Timestamp.valueOf(LocalDateTime.parse("2024-01-01T00:00:00")));
        verify(preparedStatement).setTimestamp(6,
                Timestamp.valueOf(LocalDateTime.parse("2024-01-15T10:00:00")));
    }
}
