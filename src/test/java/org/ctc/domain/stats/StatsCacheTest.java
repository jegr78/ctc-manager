package org.ctc.domain.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class StatsCacheTest {
    final AtomicLong nanos = new AtomicLong();
    final AtomicInteger loads = new AtomicInteger();
    final StatsCache<Integer> cache = new StatsCache<>(nanos::get);

    @Test
    void givenCachedValue_whenReadWithinAMinute_thenReuseItAndReloadAfterwards() {
        // given
        cache.get("key", loads::incrementAndGet);
        // when
        nanos.addAndGet(59_000_000_000L);
        int reused = cache.get("key", loads::incrementAndGet);
        nanos.addAndGet(1_000_000_000L);
        int reloaded = cache.get("key", loads::incrementAndGet);
        // then
        assertThat(reused).as("a value younger than a minute is reused").isEqualTo(1);
        assertThat(reloaded).as("a value of one minute is reloaded").isEqualTo(2);
    }

    @Test
    void givenManyKeys_whenFillingTheCache_thenBoundItsSize() {
        // when
        for (int key = 0; key < 65; key++) cache.get("key" + key, loads::incrementAndGet);
        int first = cache.get("key0", loads::incrementAndGet);
        // then
        assertThat(first).as("the cache drops old entries once 64 keys are stored").isEqualTo(66);
    }

    @Test
    void givenLongLabel_whenCreatingAChoice_thenTruncateWithoutSplittingACharacter() {
        // given
        String label = "a".repeat(96) + "🏁" + "b".repeat(10);
        // when
        var choice = new StatsChoice(label, "v");
        // then
        assertThat(choice.label()).isEqualTo("a".repeat(96) + "...");
        assertThat(new StatsChoice("x".repeat(120), "v").label()).hasSize(100);
    }
}
