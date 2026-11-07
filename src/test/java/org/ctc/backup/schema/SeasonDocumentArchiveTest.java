package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SeasonDocumentArchiveTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ObjectNode row() {
        return mapper.createObjectNode().put("rulebookUrl", "https://example.org/rules")
                .put("racingEtiquetteUrl", "https://example.org/etiquette");
    }

    @Test
    void currentDocumentsRoundTripWithoutChangingInput() {
        var row = row();
        row.put("rulebookUrl", "  https://example.org/rules  ");
        var normalized = SeasonDocumentArchive.normalize(18, "seasons", row);
        assertThat(normalized.path("rulebookUrl").textValue()).isEqualTo("https://example.org/rules");
        assertThat(normalized.path("racingEtiquetteUrl").textValue()).isEqualTo("https://example.org/etiquette");
        assertThat(row.path("rulebookUrl").textValue()).startsWith("  ");
    }

    @ParameterizedTest
    @ValueSource(ints = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17})
    void olderArchivesRestoreNullsAndRejectNewDocumentData(int version) {
        var row = mapper.createObjectNode().put("name", "Test Legacy Rules");
        var normalized = SeasonDocumentArchive.normalize(version, "seasons", row);
        assertThat(normalized.path("rulebookUrl").isNull()).isTrue();
        assertThat(normalized.path("racingEtiquetteUrl").isNull()).isTrue();
        assertThat(row.has("rulebookUrl")).isFalse();
        for (String field : java.util.List.of("rulebookUrl", "racingEtiquetteUrl")) {
            var invalid = row.deepCopy().put(field, "https://example.org/document");
            assertThatThrownBy(() -> SeasonDocumentArchive.normalize(version, "seasons", invalid))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("schema 18");
        }
    }

    @Test
    void currentArchiveRequiresTypedSafeFields() {
        for (String field : java.util.List.of("rulebookUrl", "racingEtiquetteUrl")) {
            var missing = row(); missing.remove(field);
            var number = row().put(field, 123);
            var object = row(); object.set(field, mapper.createObjectNode());
            var unsafe = row().put(field, "javascript:alert(1)");
            var longUrl = row().put(field, "https://example.org/" + "a".repeat(2048));
            for (var invalid : java.util.List.of(missing, number, object, unsafe, longUrl)) {
                assertThatThrownBy(() -> SeasonDocumentArchive.normalize(18, "seasons", invalid))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }
        var empty = row().putNull("rulebookUrl").put("racingEtiquetteUrl", " ");
        var normalized = SeasonDocumentArchive.normalize(18, "seasons", empty);
        assertThat(normalized.path("rulebookUrl").isNull()).isTrue();
        assertThat(normalized.path("racingEtiquetteUrl").isNull()).isTrue();
        assertThat(SeasonDocumentArchive.normalize(18, "cars", empty)).isSameAs(empty);
        assertThatThrownBy(() -> SeasonDocumentArchive.normalize(18, "seasons", mapper.createArrayNode()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
