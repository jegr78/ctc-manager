package org.ctc.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.support.DefaultTestContext;

class ContextCacheKeyFingerprintListenerTest {

    @Test
    void whenBeforeTestClassCalledWithMockTestContext_thenFingerprintLineRecorded() throws Exception {
        // given
        DefaultTestContext ctx = mock(DefaultTestContext.class);
        MergedContextConfiguration mcc = new MergedContextConfiguration(
                Object.class, null, null, null, null);
        setMergedConfig(ctx, mcc);
        int before = ContextCacheKeyFingerprintListener.getRecordedLines().size();

        // when
        new ContextCacheKeyFingerprintListener().beforeTestClass(ctx);

        // then
        var lines = ContextCacheKeyFingerprintListener.getRecordedLines();
        assertThat(lines).hasSize(before + 1);
        String last = lines.get(lines.size() - 1);
        assertThat(last).matches("^[0-9a-f]{1,8}\t.+$");
        String expectedHex = Integer.toHexString(mcc.hashCode());
        assertThat(last).startsWith(expectedHex + "\t");
        assertThat(last.split("\t", 2)[1].length()).isLessThanOrEqualTo(200);
    }

    @Test
    void givenDisplayLongerThan200Chars_whenBeforeTestClass_thenDisplayTruncated() throws Exception {
        // given
        DefaultTestContext ctx = mock(DefaultTestContext.class);
        MergedContextConfiguration mcc = new MergedContextConfiguration(
                Object.class, null, null, null, null) {
            @Override
            public String toString() {
                return "X".repeat(500);
            }
        };
        setMergedConfig(ctx, mcc);

        // when
        new ContextCacheKeyFingerprintListener().beforeTestClass(ctx);

        // then
        var lines = ContextCacheKeyFingerprintListener.getRecordedLines();
        String last = lines.get(lines.size() - 1);
        String display = last.split("\t", 2)[1];
        assertThat(display).hasSize(200);
    }

    private static void setMergedConfig(DefaultTestContext target, MergedContextConfiguration value)
            throws Exception {
        Field f = DefaultTestContext.class.getDeclaredField("mergedConfig");
        f.setAccessible(true);
        f.set(target, value);
    }
}
