package org.ctc.gt7sync;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class Gt7SyncPreviewStoreTest {
    @Test
    void givenPreview_whenSourceChangesOrPreviewExpires_thenReviewIsRequired() {
        // given
        var clock = mock(Clock.class);
        var now = Instant.parse("2026-11-01T12:00:00Z");
        when(clock.instant()).thenReturn(now);
        var store = new Gt7SyncPreviewStore(clock, Duration.ofMinutes(20), 2);
        var id = store.capture(Map.of("car1", "approved"));
        // when / then
        assertThat(store.matches(id, "car1", "approved")).isTrue();
        assertThat(store.matches(id, "car1", "unseen")).isFalse();
        assertThat(store.matches(id, "car2", "approved")).isFalse();
        when(clock.instant()).thenReturn(now.plus(Duration.ofMinutes(20)));
        assertThat(store.matches(id, "car1", "approved")).isFalse();
    }

    @Test
    void givenBoundedStore_whenNewPreviewsArrive_thenOnlyOldestPreviewIsEvicted() {
        // given
        var store = new Gt7SyncPreviewStore(Clock.systemUTC(), Duration.ofMinutes(20), 2);
        var first = store.capture(Map.of("car1", "same"));
        var second = store.capture(Map.of("car1", "same"));
        // when
        var third = store.capture(Map.of("car1", "same"));
        // then
        assertThat(store.matches(first, "car1", "same")).isFalse();
        assertThat(store.matches(second, "car1", "same")).isTrue();
        assertThat(store.matches(third, "car1", "same")).isTrue();
    }
}
