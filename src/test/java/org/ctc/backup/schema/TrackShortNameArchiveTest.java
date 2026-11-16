package org.ctc.backup.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class TrackShortNameArchiveTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void givenSchema25RowWithoutField_whenNormalize_thenNullShortName() {
        // given
        ObjectNode row = mapper.createObjectNode().put("name", "Fuji");

        // when
        var normalized = TrackShortNameArchive.normalize(25, "tracks", row);

        // then
        assertThat(normalized.has("displayShortName")).isTrue();
        assertThat(normalized.get("displayShortName").isNull()).as("older schemas carry no short name").isTrue();
    }

    @Test
    void givenSchema25RowWithShortName_whenNormalize_thenRejected() {
        // given
        ObjectNode row = mapper.createObjectNode().put("name", "Fuji").put("displayShortName", "FUJI");

        // when / then
        assertThatThrownBy(() -> TrackShortNameArchive.normalize(25, "tracks", row))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema 26");
    }

    @Test
    void givenSchema26RowWithShortName_whenNormalize_thenKept() {
        // given
        ObjectNode row = mapper.createObjectNode().put("name", "Spa").put("displayShortName", "SPA");

        // when
        var normalized = TrackShortNameArchive.normalize(26, "tracks", row);

        // then
        assertThat(normalized.get("displayShortName").asText()).isEqualTo("SPA");
    }

    @Test
    void givenSchema26ShortNameOver30Chars_whenNormalize_thenRejected() {
        // given
        ObjectNode row = mapper.createObjectNode().put("name", "Spa").put("displayShortName", "X".repeat(31));

        // when / then
        assertThatThrownBy(() -> TrackShortNameArchive.normalize(26, "tracks", row))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenOtherTable_whenNormalize_thenUnchanged() {
        // given
        ObjectNode row = mapper.createObjectNode().put("displayShortName", "SPA");

        // when
        var normalized = TrackShortNameArchive.normalize(25, "cars", row);

        // then
        assertThat(normalized).isSameAs(row);
    }
}
