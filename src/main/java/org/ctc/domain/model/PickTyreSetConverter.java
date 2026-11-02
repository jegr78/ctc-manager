package org.ctc.domain.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.ctc.domain.exception.BusinessRuleException;

@Converter
public class PickTyreSetConverter implements AttributeConverter<Set<PickTyre>, String> {
    @Override public String convertToDatabaseColumn(Set<PickTyre> value) {
        return value == null ? null : Arrays.stream(PickTyre.values()).filter(value::contains)
                .map(Enum::name).collect(Collectors.joining(","));
    }
    @Override public Set<PickTyre> convertToEntityAttribute(String value) {
        if (value == null) return null;
        if (value.isEmpty()) return Set.of();
        var tyres = EnumSet.noneOf(PickTyre.class);
        for (String token : value.split(",", -1)) {
            try {
                if (!tyres.add(PickTyre.valueOf(token))) throw new IllegalArgumentException("Duplicate tyre");
            } catch (IllegalArgumentException exception) {
                throw new BusinessRuleException("Invalid persisted pick tyre set");
            }
        }
        return Set.copyOf(tyres);
    }
}
