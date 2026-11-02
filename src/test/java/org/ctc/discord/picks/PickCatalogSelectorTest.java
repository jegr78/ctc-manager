package org.ctc.discord.picks;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.ctc.domain.picks.PickTeamView.Choice;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PickCatalogSelectorTest {
    @Test
    void givenLargeLocalCatalog_whenPagingAndSearching_thenKeepDiscordLimitAndStableCompleteCoverage() {
        // given
        List<Choice> choices = IntStream.range(0, 61).mapToObj(i -> new Choice(UUID.randomUUID(), null, "Test car %02d".formatted(i))).toList();
        // when / then
        assertThat(PickCatalogSelector.page(choices, "", 0).choices()).hasSize(25).containsExactlyElementsOf(choices.subList(0, 25));
        assertThat(PickCatalogSelector.page(choices, "", 2).choices()).hasSize(11).containsExactlyElementsOf(choices.subList(50, 61));
        assertThat(PickCatalogSelector.page(choices, "CAR 5", 0).choices()).hasSize(10);
        assertThat(PickCatalogSelector.page(choices, "absent", 0).choices()).isEmpty();
        assertThat(PickCatalogSelector.page(choices, "", 999).page()).isEqualTo(2);
        assertThatThrownBy(() -> PickCatalogSelector.page(choices, "", -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
