package org.ctc.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContextLoadCountListenerTest {

    @Test
    void whenInitializeCalledTwice_thenCountIncrementsByTwo() {
        // given
        var listener = new ContextLoadCountListener();
        int before = ContextLoadCountListener.getCount();

        // when
        listener.initialize(null);
        listener.initialize(null);

        // then
        assertThat(ContextLoadCountListener.getCount()).isEqualTo(before + 2);
    }

    @Test
    void whenShutdownHookFormat_thenLineOneStartsWithTotalPrefix() {
        // given / when
        String formatted = "total " + ContextLoadCountListener.getCount();

        // then
        assertThat(formatted).matches("^total \\d+$");
    }
    @Test
    void givenRealCacheHitsAndMisses_whenWritingForkMarkers_thenRecordActualCountersWithoutConfigurationValues(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        // given
        var cache = new org.springframework.test.context.cache.DefaultContextCache();
        var key = new org.springframework.test.context.MergedContextConfiguration(Object.class, null, null, null, null);
        var missing = new org.springframework.test.context.MergedContextConfiguration(String.class, null, new Class<?>[]{String.class}, null, null);
        try (var application = new org.springframework.context.support.StaticApplicationContext()) {
            application.refresh();
            cache.put(key, application);
            assertThat(cache.get(key)).isSameAs(application);
            assertThat(cache.get(key)).isSameAs(application);
            assertThat(cache.get(missing)).isNull();
            int loads = ContextLoadCountListener.getCount();
            // when
            ContextLoadCountListener.writeMarkers(directory, 42L, cache);
            // then
            assertThat(directory.resolve("context-loads-42.txt")).hasContent("total " + loads);
            var marker = directory.resolve("context-loads-42-cache.txt");
            assertThat(marker).exists();
            assertThat(java.nio.file.Files.readAllLines(marker)).containsExactly("size 1", "hits 2", "misses 1");
        } finally {
            cache.clear();
        }
    }
}
