package org.ctc.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SeasonTest {

    @Test
    void givenSeasonWithoutDescription_whenGetDisplayLabel_thenFormatsCorrectly() {
        // given
        var season = new Season("CTC Season 4", 2026, 4);

        // when / then
        assertThat(season.getDisplayLabel()).isEqualTo("2026 | #4 | CTC Season 4");
    }

    @Test
    void givenSeasonWithDescription_whenGetDisplayLabel_thenDescriptionNotIncluded() {
        // given
        var season = new Season("Season 3", 2025, 3);
        season.setDescription("Group A");

        // when / then
        assertThat(season.getDisplayLabel()).isEqualTo("2025 | #3 | Season 3");
    }

    @Test
    void givenSeasonWithDescription_whenGetDisplayLabel_thenReturnsNameNotDescription() {
        // given
        var season = new Season("Regular Season", 2026, 4);
        season.setDescription("Group A");

        // when / then
        assertThat(season.getDisplayLabel()).isEqualTo("2026 | #4 | Regular Season");
    }

    @Test
    void whenConstructed_thenAllFieldsAreSet() {
        // when
        var season = new Season("Test", 2025, 3);

        // then
        assertThat(season.getName()).isEqualTo("Test");
        assertThat(season.getYear()).isEqualTo(2025);
        assertThat(season.getNumber()).isEqualTo(3);
    }
}
