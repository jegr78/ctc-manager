package org.ctc.domain.model;

import java.util.Set;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class PickTyreTextTest {
    @ParameterizedTest
    @CsvSource({"All,false,5", "any,false,5", "Medium,true,1", "None,true,0",
            "'RS, RM, RH, I, W',false,5", "'SS, SM, SH',false,3"})
    void givenKnownSavedSpellings_whenReading_thenRetainTheirExactSemanticCompounds(String text, boolean mandatory, int count) {
        // given / when / then
        assertThat(PickTyreText.read(text, mandatory)).hasValueSatisfying(tyres -> assertThat(tyres).hasSize(count));
    }

    @Test
    void givenUnclassifiedHistoricalText_whenReading_thenDoNotGuessAFamily() {
        // given / when / then
        assertThat(PickTyreText.read("Test saved custom tyre wording", false)).isEmpty();
        assertThat(PickTyreText.read("All", true)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RS,RS", "Soft,RS"})
    void givenRepeatedCompoundOrEquivalentAlias_whenReading_thenRejectBeforeSetConversion(String text) {
        // given / when / then
        assertThatThrownBy(() -> PickTyreText.read(text, false)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("only once");
    }

    @ParameterizedTest
    @ValueSource(strings = {"X", " "})
    void givenOversizedTextIncludingWhitespace_whenReading_thenRejectBeforeTreatingItAsAnEmptyDraft(String unit) {
        // given / when / then
        assertThatThrownBy(() -> PickTyreText.read(unit.repeat(256), false)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("255");
    }

    @Test
    void givenMissingOrBlankNativeValues_whenReading_thenKeepIncompleteDraftSemantics() {
        // given / when / then
        assertThat(PickTyreText.read(null, false)).hasValue(Set.of());
        assertThat(PickTyreText.read(" ", false)).hasValue(Set.of());
    }

    @Test
    void givenBothKnownFamilies_whenReading_thenRetainBothForTheSharedSelectionValidatorToReject() {
        // given / when / then
        assertThat(PickTyreText.read("RS, SS", false)).hasValue(Set.of(PickTyre.RS, PickTyre.SS));
    }

    @Test
    void givenAnEmptyToken_whenReading_thenRejectMalformedNativeText() {
        // given / when / then
        assertThatThrownBy(() -> PickTyreText.read("RS,", false)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empty entries");
    }
}
