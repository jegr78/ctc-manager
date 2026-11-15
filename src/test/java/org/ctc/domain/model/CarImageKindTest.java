package org.ctc.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CarImageKindTest {

    @ParameterizedTest
    @CsvSource({"1_01,SCENE", "1_03,SCENE", "2_02,STUDIO", "3_01,COCKPIT", "4_01,OTHER", "10_01,OTHER"})
    void givenGt7Slot_whenMapping_thenUseItsPrefix(String slot, CarImageKind kind) {
        // when / then
        assertThat(CarImageKind.forGt7Slot(slot)).isEqualTo(kind);
    }
}
