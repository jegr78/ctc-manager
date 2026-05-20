package org.ctc.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.ctc.CtcManagerApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Shared Spring TCF context configuration for {@code dev}-profile integration tests.
 *
 * <p>Composes {@code @SpringBootTest(classes = CtcManagerApplication.class)} and
 * {@code @ActiveProfiles("dev")} into a single annotation so every consumer collapses
 * onto the same {@link org.springframework.test.context.MergedContextConfiguration}
 * cache bucket — Spring TCF's {@code DefaultContextCache} keys on the merged config
 * <em>excluding the test class</em>, so two classes wearing this annotation share
 * one cached context.
 *
 * <p>Do NOT add {@code @DirtiesContext}, {@code @Tag}, {@code @Transactional}, or
 * {@code @DynamicPropertySource} to this annotation. {@code @Tag} and
 * {@code @Transactional} belong on the subclass (they vary per consumer);
 * {@code @DirtiesContext} or {@code @DynamicPropertySource} would defeat the
 * consolidation goal by fragmenting or evicting the shared cache key.
 *
 * <p>Phase 90 PERF-03 consolidates the {@code 9cefac4c} and {@code f524774b}
 * cache-key buckets identified by Phase 89 PERF-02 instrumentation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest(classes = CtcManagerApplication.class)
@ActiveProfiles("dev")
public @interface CtcDevSpringBootContext {
}
