package org.ctc.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SeasonTest {

    @Test
    void getDisplayLabel_withoutDescription() {
        var season = new Season("CTC Season 4", 2026, 4);
        assertThat(season.getDisplayLabel()).isEqualTo("2026 | #4 | CTC Season 4");
    }

    @Test
    void getDisplayLabel_withDescription() {
        var season = new Season("Season 3", 2025, 3);
        season.setDescription("Group A");
        assertThat(season.getDisplayLabel()).isEqualTo("2025 | #3 | Season 3");
    }

    @Test
    void getDisplayLabel_descriptionNotInLabel() {
        var season = new Season("Regular Season", 2026, 4);
        season.setDescription("Group A");
        assertThat(season.getDisplayLabel()).isEqualTo("2026 | #4 | Regular Season");
    }

    @Test
    void constructor_setsAllFields() {
        var season = new Season("Test", 2025, 3);
        assertThat(season.getName()).isEqualTo("Test");
        assertThat(season.getYear()).isEqualTo(2025);
        assertThat(season.getNumber()).isEqualTo(3);
    }
}
