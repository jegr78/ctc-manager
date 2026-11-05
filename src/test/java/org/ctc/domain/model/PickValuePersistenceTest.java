package org.ctc.domain.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PickValuePersistenceTest {
    @Test
    void givenInstantAcrossBerlinSummerTime_whenConverting_thenStoreAndReadUtcWithoutHostTimezoneDependence() {
        // given / when / then
        var converter = new UtcInstantConverter();
        var instant = Instant.parse("2026-10-25T01:30:00.123456Z");
        var stored = converter.convertToDatabaseColumn(instant);
        assertThat(stored).isEqualTo(LocalDateTime.parse("2026-10-25T01:30:00.123456"));
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(instant);
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void givenTyreSetsIncludingNone_whenConverting_thenUseStableTypedRepresentation() {
        // given / when / then
        var converter = new PickTyreSetConverter();
        assertThat(converter.convertToDatabaseColumn(Set.of(PickTyre.RH, PickTyre.RM, PickTyre.RS))).isEqualTo("RS,RM,RH");
        assertThat(converter.convertToEntityAttribute("RS,RM,RH")).containsExactlyInAnyOrder(PickTyre.RS, PickTyre.RM, PickTyre.RH);
        assertThat(converter.convertToDatabaseColumn(Set.of())).isEmpty();
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RS,RS", "None,RS", "IM", "RS,", ",RS", "RS, RM"})
    void givenInvalidPersistedTyres_whenReading_thenRejectWithoutDiscardingInvalidTokens(String value) {
        // given / when / then
        assertThatThrownBy(() -> new PickTyreSetConverter().convertToEntityAttribute(value))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("tyre");
    }
}
