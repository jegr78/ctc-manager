package org.ctc.domain.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SeasonDocumentLinksTest {
    @org.junit.jupiter.api.Test
    void longEncodedControlSequenceIsRejectedAndOrdinaryEscapesArePreserved() {
        assertThatThrownBy(() -> SeasonDocumentLinks.normalize("https://example.org/" + "%00".repeat(600)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SeasonDocumentLinks.normalize("https://example.org/a%20b%2Fc?x=%2A"))
                .isEqualTo("https://example.org/a%20b%2Fc?x=%2A");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void optionalValuesNormalizeToNull(String value) {
        assertThat(SeasonDocumentLinks.normalize(value)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.org/rules.pdf", "HTTPS://example.org:443/rules?q=1&version=2#rules", " https://example.org/rules "})
    void publishedDocumentsKeepTheirFullAddress(String value) {
        assertThat(SeasonDocumentLinks.normalize(value)).isEqualTo(value.strip());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.org", "javascript:alert(1)", "data:text/html,test", "//example.org", "https:///rules", "https://user@example.org", "https://example.org:0/rules", "https://example.org:65536/rules", "https://example.org/%0Dtest", "https://example.org/\\rules", "https://example.org/a b", "https://example.org/\nscript", "https://[invalid]", "https://example.org/%zz"})
    void unsafeDestinationsAreRejectedWithoutFetching(String value) {
        assertThatThrownBy(() -> SeasonDocumentLinks.normalize(value)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(SeasonDocumentLinks.MESSAGE);
    }
}
