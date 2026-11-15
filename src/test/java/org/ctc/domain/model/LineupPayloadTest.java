package org.ctc.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LineupPayloadTest {
    private static final UUID DRIVER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void givenKnownAndProposedDrivers_whenRoundTripping_thenKeepOrderAndSnapshot() {
        // given
        var payload = new LineupPayload(6, List.of(new LineupPayload.Entry(DRIVER, "Known_1", "Known"),
                LineupPayload.Entry.proposal("New-Driver", "  New  ")));
        // when
        var copy = LineupPayload.fromJson(payload.toJson());
        // then
        assertThat(copy).isEqualTo(payload);
        assertThat(copy.driverIds()).containsExactly(DRIVER);
        assertThat(copy.proposed()).extracting(LineupPayload.Entry::psnId).containsExactly("New-Driver");
        assertThat(copy.proposed().getFirst().displayName()).as("display names are trimmed").isEqualTo("New");
        assertThat(copy.complete()).isFalse();
    }

    @Test
    void whenExceedingRequiredSize_thenReject() {
        // when / then
        assertThatThrownBy(() -> new LineupPayload(1, List.of(LineupPayload.Entry.proposal("Alpha", null), LineupPayload.Entry.proposal("Bravo", null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A lineup has at most 1 drivers");
    }

    @Test
    void whenRepeatingDriverOrPsn_thenReject() {
        // when / then
        assertThatThrownBy(() -> new LineupPayload(6, List.of(new LineupPayload.Entry(DRIVER, "A1", null), new LineupPayload.Entry(DRIVER, "A2", null))))
                .hasMessage("A driver appears twice");
        assertThatThrownBy(() -> new LineupPayload(6, List.of(LineupPayload.Entry.proposal("Alpha", null), LineupPayload.Entry.proposal("ALPHA", null))))
                .hasMessage("A PSN ID appears twice");
    }

    @Test
    void whenProposingInvalidPsnId_thenReject() {
        // when / then
        for (String invalid : List.of("ab", "1abc", "has space", "abcdefghijklmnopq")) {
            assertThatThrownBy(() -> LineupPayload.Entry.proposal(invalid, null)).as(invalid).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("3 to 16 letters");
        }
    }

    @Test
    void whenParsingUnexpectedShape_thenReject() {
        // when / then
        assertThatThrownBy(() -> LineupPayload.fromJson("{\"requiredSize\":6,\"entries\":[],\"extra\":1}")).hasMessage("Lineup payload has an invalid shape");
        assertThatThrownBy(() -> LineupPayload.fromJson("not json")).hasMessage("Lineup payload is not valid JSON");
        assertThatThrownBy(() -> LineupPayload.fromJson("{\"requiredSize\":6,\"entries\":[]}")).hasMessage("A lineup needs at least one driver");
    }
}
