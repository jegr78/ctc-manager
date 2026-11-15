package org.ctc.backup.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class LineupArchiveTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void givenOlderSchemaWithoutSize_whenNormalizing_thenRestoreSixDrivers() {
        // given
        var row = mapper.createObjectNode().put("id", "x");
        // when
        var normalized = LineupArchive.normalize(22, "seasons", row);
        // then
        assertThat(normalized.path("lineupSize").intValue()).isEqualTo(6);
        assertThat(LineupArchive.lineupSize(normalized)).isEqualTo(6);
    }

    @Test
    void givenOlderSchemaWithOtherSize_whenNormalizing_thenReject() {
        // when / then
        assertThatThrownBy(() -> LineupArchive.normalize(22, "seasons", mapper.createObjectNode().put("lineupSize", 8)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Season lineup sizes other than 6 require backup schema 23");
    }

    @Test
    void givenCurrentSchema_whenNormalizing_thenRequireSizeInRange() {
        // when / then
        assertThat(LineupArchive.normalize(23, "seasons", mapper.createObjectNode().put("lineupSize", 8)).path("lineupSize").intValue()).isEqualTo(8);
        for (var invalid : java.util.List.of(mapper.createObjectNode(), mapper.createObjectNode().put("lineupSize", 0), mapper.createObjectNode().put("lineupSize", 13))) {
            assertThatThrownBy(() -> LineupArchive.normalize(23, "seasons", invalid)).hasMessage("Season lineup size must be between 1 and 12");
        }
    }
}
