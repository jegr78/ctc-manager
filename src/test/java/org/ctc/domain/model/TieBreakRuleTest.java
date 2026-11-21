package org.ctc.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TieBreakRuleTest {

    private final Driver homeDriver = driver();
    private final Driver awayDriver = driver();
    private final Predicate<RaceResult> home = result -> result.getDriver() == homeDriver;

    @Test
    void givenFirstLegWithResults_whenDecide_thenWinnerOfFirstLegDecides() {
        // given
        var first = leg(LocalDateTime.of(2026, 10, 24, 20, 0), awayDriver, homeDriver);
        var second = leg(LocalDateTime.of(2026, 10, 24, 21, 0), homeDriver, awayDriver);
        // when
        var decision = TieBreakRule.decide(List.of(second, first), home);
        // then
        assertThat(decision).contains(new TieBreakRule.Decision(TieBreakRule.Side.AWAY, first));
    }

    @Test
    void givenFirstLegWithoutResults_whenDecide_thenNextLegWithResultsDecides() {
        // given
        var first = leg(LocalDateTime.of(2026, 10, 24, 20, 0));
        var second = leg(LocalDateTime.of(2026, 10, 24, 21, 0), homeDriver, awayDriver);
        // when
        var decision = TieBreakRule.decide(List.of(first, second), home);
        // then
        assertThat(decision).contains(new TieBreakRule.Decision(TieBreakRule.Side.HOME, second));
        assertThat(TieBreakRule.legNumber(List.of(first, second), second)).isEqualTo(2);
    }

    @Test
    void givenDisqualifiedP1_whenDecide_thenNextClassifiedDriverDecides() {
        // given
        var race = leg(LocalDateTime.of(2026, 10, 24, 20, 0), homeDriver, awayDriver);
        race.getResults().getFirst().setDisqualified(true);
        // when
        var decision = TieBreakRule.decide(List.of(race), home);
        // then
        assertThat(decision).map(TieBreakRule.Decision::side).contains(TieBreakRule.Side.AWAY);
    }

    @Test
    void givenLegWithoutDateTime_whenDecide_thenSortedLast() {
        // given
        var undated = leg(null, homeDriver, awayDriver);
        var dated = leg(LocalDateTime.of(2026, 10, 24, 21, 0), awayDriver, homeDriver);
        // when
        var decision = TieBreakRule.decide(List.of(undated, dated), home);
        // then
        assertThat(decision).map(TieBreakRule.Decision::race).contains(dated);
    }

    @Test
    void givenEqualDateTimes_whenDecide_thenRaceIdBreaksOrder() {
        // given
        var time = LocalDateTime.of(2026, 10, 24, 20, 0);
        var low = leg(time, awayDriver, homeDriver);
        var high = leg(time, homeDriver, awayDriver);
        low.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        high.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        // when
        var decision = TieBreakRule.decide(List.of(high, low), home);
        // then
        assertThat(decision).map(TieBreakRule.Decision::race).contains(low);
    }

    @Test
    void givenNoResults_whenDecide_thenEmpty() {
        // when
        var decision = TieBreakRule.decide(List.of(leg(LocalDateTime.of(2026, 10, 24, 20, 0))), home);
        // then
        assertThat(decision).isEmpty();
    }

    @Test
    void givenGuestOnHomeSide_whenDecide_thenPredicateAttributionWins() {
        // given
        var guest = driver();
        var race = leg(LocalDateTime.of(2026, 10, 24, 20, 0), guest, awayDriver);
        Predicate<RaceResult> attribution = result -> Set.of(homeDriver, guest).contains(result.getDriver());
        // when
        var decision = TieBreakRule.decide(List.of(race), attribution);
        // then
        assertThat(decision).map(TieBreakRule.Decision::side).contains(TieBreakRule.Side.HOME);
    }

    private static Driver driver() {
        var driver = new Driver();
        driver.setId(UUID.randomUUID());
        return driver;
    }

    private static Race leg(LocalDateTime start, Driver... finishingOrder) {
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setDateTime(start);
        var results = new ArrayList<RaceResult>();
        for (int i = 0; i < finishingOrder.length; i++) {
            results.add(new RaceResult(race, finishingOrder[i], i + 1, i + 1, false));
        }
        race.setResults(results);
        return race;
    }
}
