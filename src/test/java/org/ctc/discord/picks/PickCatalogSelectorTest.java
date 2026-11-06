package org.ctc.discord.picks;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.ctc.domain.picks.PickTeamView.Choice;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PickCatalogSelectorTest {
    @Test
    void givenOverOneHundredTracks_whenPagingAndSearching_thenKeepEveryChoiceWithinDiscordLimits() {
        // given
        var choices = new java.util.ArrayList<Choice>(IntStream.range(0, 130)
                .mapToObj(i -> new Choice(UUID.randomUUID(), null, "Test circuit %03d".formatted(i))).toList());
        choices.add(new Choice(UUID.randomUUID(), null, "Test Spa layout one"));
        choices.add(new Choice(UUID.randomUUID(), null, "Test Spa layout two"));
        // when
        var pages = IntStream.range(0, 6).mapToObj(page -> PickCatalogSelector.page(choices, "", page)).toList();
        // then
        assertThat(pages).allSatisfy(page -> assertThat(page.choices()).hasSizeBetween(1, 25));
        assertThat(pages.stream().flatMap(page -> page.choices().stream()).toList()).containsExactlyElementsOf(choices);
        assertThat(PickCatalogSelector.page(choices, "SPA", 0).choices()).containsExactlyElementsOf(choices.subList(130, 132));
    }

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
