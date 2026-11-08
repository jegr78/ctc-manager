package org.ctc.domain.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;

@Converter
public class TrackTimeOptionsConverter implements AttributeConverter<List<String>, String> {
    @Override public String convertToDatabaseColumn(List<String> value) {
        return value == null || value.isEmpty() ? null : String.join("\n", value);
    }
    @Override public List<String> convertToEntityAttribute(String value) {
        // Keep malformed legacy values readable by the audit; writers validate them separately.
        return value == null || value.isEmpty() ? List.of() : Arrays.asList(value.split("\n", -1));
    }
}
