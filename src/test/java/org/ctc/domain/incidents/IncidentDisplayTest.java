package org.ctc.domain.incidents;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IncidentDisplayTest {
    @Test void givenSharedUuidPrefixes_whenDisplaying_thenKeepAllIdentityBitsAndDistinctReferences() {
        String first = "INC-6E37B1BE-20ED-48EC-9C95-9D07812AF45E";
        String second = "INC-6E37B1BE-20ED-48EC-9C95-9D07812AF45F";
        String display = IncidentDisplay.reference(first);
        assertThat(display).isEqualTo("INC:bjexviDtSOyclZ0HgSr0Xg").hasSize(26).isNotEqualTo(IncidentDisplay.reference(second));
        var bytes = ByteBuffer.wrap(Base64.getUrlDecoder().decode(display.substring(4)));
        assertThat(new UUID(bytes.getLong(), bytes.getLong()).toString()).isEqualTo(first.substring(4).toLowerCase(java.util.Locale.ROOT));
        assertThat(IncidentDisplay.reference("INC-legacy-test")).isEqualTo("INC-legacy-test");
        assertThat(IncidentDisplay.reference("INC-bjexviDtSOyclZ0HgSr0Xg")).isEqualTo("INC-bjexviDtSOyclZ0HgSr0Xg").isNotEqualTo(display);
        String lowerCaseLegacy = "INC-" + first.substring(4).toLowerCase(java.util.Locale.ROOT);
        assertThat(IncidentDisplay.reference(lowerCaseLegacy)).isEqualTo(lowerCaseLegacy).isNotEqualTo(display);
    }

    @Test void givenLegacyAndReadableFooters_whenCheckingConflicts_thenIdentifyExactCanonicalMarkersOnly() {
        String marker = "[CTC INC-TEST PUBLIC:0]";
        for (Integer version : new Integer[]{null, 1}) {
            String content = "Report\n" + IncidentDisplay.footer(marker, version);
            assertThat(IncidentDisplay.hasMarker(content, marker)).isTrue();
            assertThat(IncidentDisplay.hasCaseMarker(content, "INC-TEST")).isTrue();
            assertThat(IncidentDisplay.hasCaseMarker(content, "INC-TES")).isFalse();
            assertThat(IncidentDisplay.hasMarker(content, "[CTC INC-OTHER PUBLIC:0]")).isFalse();
        }
        assertThat(IncidentDisplay.hasMarker(null, marker)).isFalse();
        assertThat(IncidentDisplay.hasCaseMarker("No footer", "INC-TEST")).isFalse();
        assertThat(IncidentDisplay.footer(marker, 1)).isEqualTo("-# ||[CTC INC-TEST PUBLIC:0]||");
    }
}
