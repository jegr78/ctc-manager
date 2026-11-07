package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.ctc.domain.model.SeasonDocumentLinks;

public final class SeasonDocumentArchive {
    private SeasonDocumentArchive() { }

    public static JsonNode normalize(int version, String table, JsonNode row) {
        if (!"seasons".equals(table)) return row;
        if (!(row instanceof ObjectNode object)) throw new IllegalArgumentException("Season row must be an object");
        var copy = object.deepCopy();
        for (String field : List.of("rulebookUrl", "racingEtiquetteUrl")) {
            var value = copy.get(field);
            if (version < 18) {
                if (value != null && !value.isNull()) throw new IllegalArgumentException("Season document links require backup schema 18");
                copy.putNull(field);
            } else {
                if (value == null || !value.isNull() && !value.isTextual()) throw new IllegalArgumentException("Invalid season document URL");
                String url = SeasonDocumentLinks.normalize(value.isNull() ? null : value.textValue());
                if (url == null) copy.putNull(field);
                else copy.put(field, url);
            }
        }
        return copy;
    }
}
