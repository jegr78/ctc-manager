package org.ctc.gt7sync;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GtPlusCarYearSourceTest {
    @Test
    void givenOversizedSource_whenParsing_thenRejectsAtTheSizeBoundary() {
        // given
        String payload = "x".repeat(4 * 1024 * 1024);
        // when / then
        assertThatThrownBy(() -> GtPlusCarYearSource.parseCandidates(payload))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("size limit");
        assertThatThrownBy(() -> GtPlusCarYearSource.parseFlight(payload))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("size limit");
    }

    @Test
    void givenTooManyFrames_whenParsing_thenRejectsBeforeCollectingUnboundedFrames() {
        // given
        String html = "<script>" + "self.__next_f.push([1,\"\"]);".repeat(2049) + "</script>";
        // when / then
        assertThatThrownBy(() -> GtPlusCarYearSource.parseCandidates(html))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("too many frames");
    }

    @Test
    void givenCapturedChunkedFlight_whenParsing_thenYearsJoinByCanonicalIdWithoutSwaps() throws Exception {
        // given
        String html = Files.readString(Path.of("src/test/resources/gt7/gtplus-flight.html"));
        // when
        var candidates = GtPlusCarYearSource.parseCandidates(html);
        // then
        assertThat(candidates).containsOnlyKeys("car3531", "car3599", "car3606");
        assertThat(candidates.get("car3531").year()).isEqualTo(2020);
        assertThat(candidates.get("car3531").evidence()).contains("3531", "2020");
        assertThat(candidates.get("car3599").year()).isNull();
    }

    @Test
    void givenMalformedAndConflictingYears_whenParsing_thenRecordsRequireReview() throws Exception {
        // given
        String payload = "1:{\"cars\":[{\"entity\":\"car\",\"code\":9,\"year\":1999},{\"entity\":\"car\",\"code\":9,\"year\":2099},{\"entity\":\"car\",\"code\":10,\"year\":\"2000\"}]}";
        // when
        var candidates = GtPlusCarYearSource.parseFlight(payload);
        // then
        assertThat(candidates.get("car9").year()).isNull();
        assertThat(candidates.get("car9").problem()).contains("Conflicting");
        assertThat(candidates.get("car9").evidence()).contains("1999", "2099");
        assertThat(candidates.get("car10").year()).isNull();
        assertThat(candidates.get("car10").problem()).contains("integer");
        assertThatThrownBy(() -> GtPlusCarYearSource.parseCandidates("<html>Changed layout</html>"))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("layout");
    }
    @Test
    void givenRepeatedIdenticalRecordsAndFractionalYear_whenParsing_thenDuplicatesCollapseAndFractionRequiresReview() throws Exception {
        // given
        String payload = "1:{\"cars\":[{\"entity\":\"car\",\"code\":9,\"year\":1999},{\"entity\":\"car\",\"code\":9,\"year\":1999},{\"entity\":\"car\",\"code\":10,\"year\":2000.5}]}";
        // when
        var candidates = GtPlusCarYearSource.parseFlight(payload);
        // then
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get("car9").year()).isEqualTo(1999);
        assertThat(candidates.get("car9").problem()).isNull();
        assertThat(candidates.get("car10").year()).isNull();
        assertThat(candidates.get("car10").problem()).contains("integer");
    }

}
