---
phase: 89-perf-instrumentation-lever-1-per-fork-backup-staging-dir
plan: 02
type: execute
wave: 2
depends_on:
  - 89-01-perf-01-per-fork-staging-refactor
files_modified:
  - src/test/java/org/ctc/testsupport/ContextLoadCountListener.java
  - src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java
  - src/test/resources/META-INF/spring.factories
  - src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java
  - src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListenerTest.java
  - scripts/test-perf/aggregate-fingerprints.sh
  - docs/test-performance.md
autonomous: true
requirements: [PERF-02]
must_haves:
  truths:
    - "`ContextLoadCountListener` shutdown hook writes Line 1 of `target/test-perf/context-loads-{PID}.txt` as `total <count>` (replacing the previous bare integer) via `StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING`."
    - "`ContextLoadCountListenerTest` asserts the new `total <N>` format in addition to the in-memory counter increment."
    - "New `ContextCacheKeyFingerprintListener` (package `org.ctc.testsupport`) implements `org.springframework.test.context.TestExecutionListener`; its `beforeTestClass(TestContext)` reflectively reads the `mergedConfig` field on `DefaultTestContext` and accumulates one `<hex-hash>\\t<mcc-display>` line per invocation."
    - "At JVM shutdown, the fingerprint listener appends its accumulated hash lines to the SAME `target/test-perf/context-loads-{PID}.txt` marker file (in APPEND mode, AFTER the `total <count>` Line 1 written by `ContextLoadCountListener` — same-file, NOT a sidecar, per D-08)."
    - "Marker-file shutdown-hook ordering invariant is DOCUMENTED in `ContextCacheKeyFingerprintListener` Javadoc: `ContextLoadCountListener` writes Line 1 via TRUNCATE_EXISTING; this listener APPENDs via APPEND. JVM shutdown-hook ordering is REVERSE registration; this listener registers AFTER `ContextLoadCountListener` because its class is loaded later via Spring's `TestExecutionListener` SPI."
    - "`<mcc-display>` is `mergedContextConfiguration.toString()` truncated to at most 200 characters (D-08, D-10)."
    - "`<hex-hash>` is `Integer.toHexString(mergedContextConfiguration.hashCode())` (D-10) — matches the Spring TCF `DefaultContextCache` bucketing function 1:1 (per RESEARCH RQ-7)."
    - "`src/test/resources/META-INF/spring.factories` contains a second registration line `org.springframework.test.context.TestExecutionListener=org.ctc.testsupport.ContextCacheKeyFingerprintListener` (alongside the existing `ApplicationContextInitializer` line, which is left UNCHANGED)."
    - "`ContextCacheKeyFingerprintListenerTest` (unit, plain JUnit 5, no `@Tag`) seeds real records via `beforeTestClass(mockCtx)` and asserts the marker-file format: Line 1 matches `total \\d+`, Lines 2+ match `^[0-9a-f]{1,8}\\t.+$`, and APPEND semantics preserve Line 1."
    - "`scripts/test-perf/aggregate-fingerprints.sh` exists, is executable (`chmod +x`), is `shellcheck`-clean (no disables), accepts optional positional args `MARKER_DIR` (default `target/test-perf`) and `TOP_N` (default `5`), groups by hex hash, computes `occurrence × cluster_size` score, and emits the top-N clusters to stdout."
    - "`docs/test-performance.md` contains a new H2 section `## PERF-02 Forensics — Cache-Key Fingerprint Analysis` inserted between the existing `## Context Load Counts (PERF-02)` (lines 195-244) and `## Per-Decision Evidence` (line 247), with a usage block and Top-5 output example."
    - "`docs/test-performance.md` aggregator loop at lines 233-239 is migrated from `cat \"$f\"` to `head -1 \"$f\" | awk '{print $2}'` extraction (D-08 format-migration; RESEARCH RQ-9)."
    - "`./mvnw clean verify` exits 0 with all 1011+ tests green (no regression to the 1011 baseline)."
  artifacts:
    - path: "src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java"
      provides: "TestExecutionListener that fingerprints MergedContextConfiguration per test class"
      exports: ["beforeTestClass"]
    - path: "src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListenerTest.java"
      provides: "Unit test for the fingerprint listener — verifies marker-file format + APPEND semantics + truncation"
    - path: "scripts/test-perf/aggregate-fingerprints.sh"
      provides: "Shell aggregator: marker files → top-N clusters by fragmentation score"
      contains: "#!/usr/bin/env bash"
    - path: "src/test/resources/META-INF/spring.factories"
      provides: "Registration of the new TestExecutionListener via SpringFactoriesLoader"
      contains: "org.springframework.test.context.TestExecutionListener=org.ctc.testsupport.ContextCacheKeyFingerprintListener"
    - path: "docs/test-performance.md"
      provides: "New § PERF-02 Forensics + migrated aggregator loop"
      contains: "PERF-02 Forensics"
  key_links:
    - from: "ContextCacheKeyFingerprintListener#beforeTestClass"
      to: "DefaultTestContext.mergedConfig"
      via: "ReflectionUtils.findField + makeAccessible"
      pattern: "ReflectionUtils\\.findField.*mergedConfig"
    - from: "ContextLoadCountListener + ContextCacheKeyFingerprintListener shutdown hooks"
      to: "target/test-perf/context-loads-{PID}.txt"
      via: "shared marker file (PID-keyed, TRUNCATE for Line 1, APPEND for hash lines)"
      pattern: "context-loads-.*\\.txt"
    - from: "scripts/test-perf/aggregate-fingerprints.sh"
      to: "target/test-perf/context-loads-*.txt"
      via: "tail -n +2 (skip count line) | awk grouping"
      pattern: "target/test-perf/context-loads"
---

<objective>
PERF-02: Add per-context cache-key fingerprinting so PERF-03 (Phase 90 consolidation) has empirical data to identify the highest-fragmentation IT clusters.

Purpose: Phase 86 D-12 identified that the count signal alone (81 → 79 context loads) hides the more important fragmentation signal (one cache key splitting into seven distinct keys after `@DynamicPropertySource` per-class binding). Per-class fingerprinting via `MergedContextConfiguration.hashCode()` exposes which test classes actually share a Spring context vs. which split into singleton clusters. Without this data, PERF-03 would consolidate blind and risk re-introducing the Plan-02 fragmentation pattern from a different angle (per Phase-86 SUMMARY § Lesson).

Output (5 tasks, TDD-ordered):
- Task 1 (RED) — Migrated `ContextLoadCountListener` shutdown-hook output format (`total <count>`) + GREEN unit test for the listener; new `ContextCacheKeyFingerprintListener` skeleton (stub `beforeTestClass`, stub test-accessor helpers) + FAILING unit test class `ContextCacheKeyFingerprintListenerTest` (red — exercises the not-yet-implemented behavior).
- Task 2 (GREEN) — Implement `ContextCacheKeyFingerprintListener.beforeTestClass` + `writeMarkerFile` + shutdown-hook + register in `META-INF/spring.factories`. Task 1's failing test flips to green.
- Task 3 (REFACTOR / cross-plan validation) — Run full backup IT suite under the combined listeners; verify marker-file ordering invariant holds.
- Task 4 — New aggregator script `scripts/test-perf/aggregate-fingerprints.sh` (shellcheck-clean).
- Task 5 — New `docs/test-performance.md § PERF-02 Forensics` H2 section + aggregator-loop migration (lines 233-239 → `head -1 "$f" | awk '{print $2}'`).

Depends on Plan 89-01 (PERF-01) because the fingerprint listener must be validated under elevated Failsafe `forkCount=2`. The shared marker-file write coordination (Line 1 = `total <N>` from listener A, Lines 2+ = hash records from listener B) only matters when multiple forks write concurrently to distinct PID-keyed files; that scenario only exists after PERF-01 lifts Failsafe to `forkCount=2`.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/ROADMAP.md
@.planning/REQUIREMENTS.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-RESEARCH.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-VALIDATION.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-01-perf-01-per-fork-staging-refactor-PLAN.md
@CLAUDE.md
@docs/test-performance.md
@scripts/app.sh
@src/test/java/org/ctc/testsupport/ContextLoadCountListener.java
@src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java
@src/test/resources/META-INF/spring.factories

<interfaces>
<!-- Key contracts and conventions for executors. -->

`ContextLoadCountListener` current implementation (full file, 45 lines — modify the shutdown hook output format only):
```java
package org.ctc.testsupport;

public class ContextLoadCountListener
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final AtomicInteger count = new AtomicInteger(0);

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                long pid = ProcessHandle.current().pid();
                Path out = Paths.get("target/test-perf/context-loads-" + pid + ".txt");
                Files.createDirectories(out.getParent());
                Files.writeString(out, String.valueOf(count.get()));   // <-- this line changes
            } catch (IOException e) {
                System.err.println("ContextLoadCountListener: could not write marker file: " + e.getMessage());
            }
        }, "ContextLoadCountListener-shutdown"));
    }

    @Override public void initialize(ConfigurableApplicationContext applicationContext) { count.incrementAndGet(); }
    static int getCount() { return count.get(); }
}
```

Spring 7.0.6 `TestExecutionListener` interface (RESEARCH RQ-3): `void beforeTestClass(TestContext testContext) throws Exception` — fires once per test class on the JVM that runs it.

Spring 7.0.6 `DefaultTestContext` structure (RESEARCH RQ-2): `private final MergedContextConfiguration mergedConfig` — `private final`, no public getter on either `DefaultTestContext` or the `TestContext` interface. Access requires reflection. Recommended path: Spring's own `ReflectionUtils`:
```java
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.DefaultTestContext;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.util.ReflectionUtils;
import java.lang.reflect.Field;

Field f = ReflectionUtils.findField(DefaultTestContext.class, "mergedConfig");
ReflectionUtils.makeAccessible(f);
MergedContextConfiguration mcc = (MergedContextConfiguration) ReflectionUtils.getField(f, testContext);
```
Cast `(DefaultTestContext) testContext` is safe — `DefaultTestContext` has been the sole `TestContext` implementation since Spring 4.0 (RQ-2 cast-safety confirmation).

`MergedContextConfiguration.hashCode()` (Spring 7.0.6, RESEARCH RQ-7): the exact bucketing function used by `DefaultContextCache.contextMap` (a `LinkedHashMap<MergedContextConfiguration, ApplicationContext>`). `Integer.toHexString(mcc.hashCode())` produces 1–8 hex characters (no leading zeros for positive ints; negative ints return 8 chars via two's complement, e.g., `ffffffff` for `-1`).

`spring.factories` current state (1 line):
```
org.springframework.context.ApplicationContextInitializer=org.ctc.testsupport.ContextLoadCountListener
```
Extend with one additional line (RQ-3 Option A: one-line addition, zero changes to existing IT classes):
```
org.springframework.test.context.TestExecutionListener=org.ctc.testsupport.ContextCacheKeyFingerprintListener
```

`docs/test-performance.md` aggregator-loop migration target (current lines 233-239):
```bash
TOTAL=0
for f in target/test-perf/context-loads-*.txt; do
  TOTAL=$((TOTAL + $(cat "$f")))
done
echo "Total context loads: $TOTAL"
```
Migrate to (RESEARCH RQ-9):
```bash
TOTAL=0
for f in target/test-perf/context-loads-*.txt; do
  TOTAL=$((TOTAL + $(head -1 "$f" | awk '{print $2}')))
done
echo "Total context loads: $TOTAL"
```

`scripts/app.sh` shell conventions (RESEARCH RQ-8):
```bash
#!/usr/bin/env bash
# <description>
# Usage:
#   ./scripts/...
set -euo pipefail
```
- `set -euo pipefail` immediately after the comment header.
- Double-quote ALL variable references: `"$var"`, `"$(cmd)"`.
- `$(...)` command substitution (not backticks).
- No `shellcheck disable=...` directives — must be clean as-is.

Shutdown-hook coordination strategy (D-08 LOCKED — same-file, NOT sidecar; Option A from revision):
- `ContextLoadCountListener` writes Line 1 via `Files.writeString(out, "total " + count.get() + "\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)` — owns the file's first line.
- `ContextCacheKeyFingerprintListener` opens the file via `Files.write(out, records, StandardOpenOption.CREATE, StandardOpenOption.APPEND)` — appends lines 2+ AFTER.
- JVM shutdown-hook ordering is non-deterministic but Spring's class-loading sequence loads the `ApplicationContextInitializer` SPI (count listener) BEFORE the `TestExecutionListener` SPI (fingerprint listener); the fingerprint listener's static-block shutdown-hook registration thus happens AFTER. Since JVM shutdown hooks fire concurrently (not in registration order — they are threads), this ordering is NOT guaranteed by registration order alone. Mitigation: the TRUNCATE_EXISTING + APPEND open-option pairing makes the order tolerant — if the count hook fires LAST, it truncates and re-writes Line 1 (hash lines would be lost in that edge case, but `MergedContextConfiguration` access requires Spring TCF activation that only occurs in tests, and the count hook's TRUNCATE wins). The documented invariant in Task 2's Javadoc covers the engineer-facing contract. If the integration step in Task 3 reveals a real race (sibling lines appearing before Line 1 in marker files), the fallback is Option B from RESEARCH RQ-4: a single coordinator class.

JVM shutdown-hook concurrency note: per `Runtime.addShutdownHook` Javadoc, registered hooks run concurrently as separate threads. They do NOT run in registration order. The PRACTICAL ordering observed in this codebase (RESEARCH RQ-4 empirical note) is: short-lived hooks finish first; the count hook is shorter (one writeString) than the fingerprint hook (one Files.write of N lines). For the realistic N (< 100 contexts per fork), both complete in milliseconds and the integration test in Task 3 validates the resulting file.
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1 (TDD RED): Migrate `ContextLoadCountListener` format + introduce `ContextCacheKeyFingerprintListener` skeleton + write FAILING unit tests</name>
  <files>src/test/java/org/ctc/testsupport/ContextLoadCountListener.java, src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java, src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java, src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListenerTest.java</files>
  <behavior>
    - `ContextLoadCountListenerTest.whenWriteMarkerFile_thenFirstLineMatchesTotalFormat`: GREEN immediately (Task 1 includes the listener fix in the same task).
    - `ContextCacheKeyFingerprintListenerTest.givenMockedTestContext_whenBeforeTestClassCalled_thenLineAccumulated`: RED at end of Task 1 (the stub `beforeTestClass` returns immediately and adds nothing to records; the test expects exactly 1 record with hex+display format).
    - `ContextCacheKeyFingerprintListenerTest.givenSeededRecords_whenWriteMarkerFile_thenLine1PreservedAndHashLinesAppended`: RED at end of Task 1 (the stub `writeMarkerFile` is a no-op; the test seeds Line 1 `total 0`, accumulates 2 records via `beforeTestClass`, calls `writeMarkerFile`, asserts Line 1 unchanged AND Lines 2-3 match `^[0-9a-f]{1,8}\t.+$`).
    - `ContextCacheKeyFingerprintListenerTest.givenLongMccToString_whenBeforeTestClassCalled_thenDisplayTruncatedTo200Chars`: RED at end of Task 1 (stub returns immediately; the test asserts the display column has exactly 200 chars).
  </behavior>
  <read_first>
    - src/test/java/org/ctc/testsupport/ContextLoadCountListener.java (full file, 45 lines — modify line 29 + add `writeMarkerFile` static helper).
    - src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java (full file, 22 lines — add new format-assertion test method).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-07, D-08, D-10 (locks marker-file format, hash algorithm, listener type — all NON-NEGOTIABLE).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-RESEARCH.md § Risk 4 (migration rationale) and § RQ-2 (reflection path for `mergedConfig`).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md § `ContextLoadCountListener.java (modification)` and § `ContextCacheKeyFingerprintListener.java`.
    - CLAUDE.md § "Test Naming (Given-When-Then)" + § "No unnecessary comments" + § "Static Analysis (SpotBugs + find-sec-bugs)".
  </read_first>
  <action>
    Four atomic file edits. Use the Edit tool for existing files and the Write tool for new files. Order matters — the `ContextLoadCountListener` fix lands BEFORE the new fingerprint test class compiles against the new stubs.

    EDIT 1 — `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java`:
    - Change the shutdown-hook write call (current line 29) from `Files.writeString(out, String.valueOf(count.get()))` to the new format with `TRUNCATE_EXISTING`. The lambda body should now delegate to a new `writeMarkerFile(Path)` helper so the unit test can drive it without invoking the actual JVM shutdown hook.
    - Add a new package-private static method:
      ```
      static void writeMarkerFile(Path out) throws IOException {
          Files.createDirectories(out.getParent());
          Files.writeString(out, "total " + count.get() + "\n",
                  java.nio.file.StandardOpenOption.CREATE,
                  java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
      }
      ```
    - Update the existing shutdown-hook lambda to call `writeMarkerFile(out)` inside its try/catch.
    - Add the import for `java.nio.file.StandardOpenOption` at the top of the file.
    - Update the class Javadoc to reference the new format: "writes the per-fork tally as Line 1 of `target/test-perf/context-loads-{PID}.txt` in the form `total <count>` at JVM shutdown via `TRUNCATE_EXISTING`. `ContextCacheKeyFingerprintListener` appends per-context fingerprint records to the same file in `APPEND` mode after this Line 1 (see PERF-02)."

    EDIT 2 — `src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java`:
    - Keep the existing `whenInitializeCalledTwice_thenCountIncrementsByTwo()` test unchanged.
    - Add a new test method `whenWriteMarkerFile_thenFirstLineMatchesTotalFormat(@TempDir Path tempDir)` that calls `listener.initialize(null)`, then `ContextLoadCountListener.writeMarkerFile(out)`, then reads `Files.readAllLines(out).get(0)` and asserts it matches regex `total \d+`.
    - Add imports for `@TempDir`, `java.io.IOException`, `java.nio.file.Files`, `java.nio.file.Path`.

    WRITE 3 — Create `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java` as a STUB SKELETON (the GREEN implementation lands in Task 2):
    ```java
    package org.ctc.testsupport;

    import java.io.IOException;
    import java.lang.reflect.Field;
    import java.nio.file.Files;
    import java.nio.file.Path;
    import java.util.Collections;
    import java.util.List;
    import java.util.concurrent.CopyOnWriteArrayList;

    import org.springframework.test.context.TestContext;
    import org.springframework.test.context.TestExecutionListener;

    /**
     * PERF-02 instrumentation: per-test-class MergedContextConfiguration fingerprint listener.
     *
     * <p>STUB (Task 1 RED phase) — beforeTestClass / writeMarkerFile bodies arrive in Task 2.
     *
     * <p>Marker-file ordering invariant (documented for the GREEN implementation in Task 2):
     * <ul>
     *   <li>{@code ContextLoadCountListener} writes Line 1 via {@code TRUNCATE_EXISTING};</li>
     *   <li>this listener APPENDs hash lines via {@code StandardOpenOption.APPEND} after;</li>
     *   <li>JVM shutdown hooks run concurrently (not in registration order — see
     *       {@link Runtime#addShutdownHook(Thread)}); the TRUNCATE+APPEND open-option pairing
     *       makes the file content deterministic when the count hook runs to completion;</li>
     *   <li>Spring class-loading order loads the {@code ApplicationContextInitializer} SPI
     *       (count listener) BEFORE the {@code TestExecutionListener} SPI (this class), so
     *       this listener's static-block shutdown hook registers AFTER the count hook;</li>
     *   <li>Re-validate the ordering invariant whenever either listener is modified.</li>
     * </ul>
     */
    public class ContextCacheKeyFingerprintListener implements TestExecutionListener {

        static final int DISPLAY_TRUNCATION_LIMIT = 200;
        static final CopyOnWriteArrayList<String> records = new CopyOnWriteArrayList<>();

        @Override
        public void beforeTestClass(TestContext testContext) {
            // STUB — implementation lands in Task 2 (GREEN).
        }

        static List<String> getRecordedLines() {
            return Collections.emptyList();   // STUB — Task 2 returns List.copyOf(records).
        }

        static void clearRecordsForTest() {
            // STUB — Task 2 calls records.clear().
        }

        static void writeMarkerFile(Path out) throws IOException {
            // STUB — Task 2 writes records to `out` in APPEND mode.
        }
    }
    ```

    WRITE 4 — Create `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListenerTest.java` with the THREE failing tests. SEED ACTUAL RECORDS in Test 2 (per revision WARNING 2: the test must prove APPEND mode preserves Line 1 AND real hash lines land on Lines 2+).

    Full test class:
    ```java
    package org.ctc.testsupport;

    import static org.assertj.core.api.Assertions.assertThat;

    import java.io.IOException;
    import java.lang.reflect.Field;
    import java.nio.file.Files;
    import java.nio.file.Path;
    import java.util.List;

    import org.junit.jupiter.api.Test;
    import org.junit.jupiter.api.io.TempDir;
    import org.mockito.Mockito;
    import org.springframework.test.context.MergedContextConfiguration;
    import org.springframework.test.context.support.DefaultTestContext;
    import org.springframework.util.ReflectionUtils;

    class ContextCacheKeyFingerprintListenerTest {

        @Test
        void givenMockedTestContext_whenBeforeTestClassCalled_thenLineAccumulated() throws Exception {
            // given
            ContextCacheKeyFingerprintListener.clearRecordsForTest();
            var listener = new ContextCacheKeyFingerprintListener();
            DefaultTestContext ctx = Mockito.mock(DefaultTestContext.class);
            MergedContextConfiguration mcc = Mockito.mock(MergedContextConfiguration.class);
            Mockito.doReturn(0x3fa2c1).when(mcc).hashCode();
            Mockito.when(mcc.toString())
                    .thenReturn("[MergedContextConfiguration@1234 testClass=FooTest, locations=[]]");
            Field f = ReflectionUtils.findField(DefaultTestContext.class, "mergedConfig");
            ReflectionUtils.makeAccessible(f);
            f.set(ctx, mcc);

            // when
            listener.beforeTestClass(ctx);

            // then
            List<String> recorded = ContextCacheKeyFingerprintListener.getRecordedLines();
            assertThat(recorded).hasSize(1);
            assertThat(recorded.get(0)).matches("[0-9a-f]{1,8}\\t.+");
            assertThat(recorded.get(0)).startsWith("3fa2c1\t");
        }

        @Test
        void givenSeededRecords_whenWriteMarkerFile_thenLine1PreservedAndHashLinesAppended(@TempDir Path tempDir)
                throws Exception {
            // given — seed Line 1 with the count-listener format
            Path out = tempDir.resolve("context-loads-test.txt");
            Files.writeString(out, "total 0\n");
            ContextCacheKeyFingerprintListener.clearRecordsForTest();

            // and — seed two real records via beforeTestClass with two distinct mocks
            var listener = new ContextCacheKeyFingerprintListener();
            DefaultTestContext ctx1 = Mockito.mock(DefaultTestContext.class);
            MergedContextConfiguration mcc1 = Mockito.mock(MergedContextConfiguration.class);
            Mockito.doReturn(0x111111).when(mcc1).hashCode();
            Mockito.when(mcc1.toString()).thenReturn("[mcc-one]");
            Field f = ReflectionUtils.findField(DefaultTestContext.class, "mergedConfig");
            ReflectionUtils.makeAccessible(f);
            f.set(ctx1, mcc1);
            listener.beforeTestClass(ctx1);

            DefaultTestContext ctx2 = Mockito.mock(DefaultTestContext.class);
            MergedContextConfiguration mcc2 = Mockito.mock(MergedContextConfiguration.class);
            Mockito.doReturn(0x222222).when(mcc2).hashCode();
            Mockito.when(mcc2.toString()).thenReturn("[mcc-two]");
            f.set(ctx2, mcc2);
            listener.beforeTestClass(ctx2);

            // when
            ContextCacheKeyFingerprintListener.writeMarkerFile(out);

            // then
            List<String> lines = Files.readAllLines(out);
            assertThat(lines).hasSizeGreaterThanOrEqualTo(3);
            assertThat(lines.get(0)).matches("total \\d+");
            assertThat(lines.get(1)).matches("^[0-9a-f]{1,8}\\t.+$");
            assertThat(lines.get(2)).matches("^[0-9a-f]{1,8}\\t.+$");
        }

        @Test
        void givenLongMccToString_whenBeforeTestClassCalled_thenDisplayTruncatedTo200Chars() throws Exception {
            // given
            ContextCacheKeyFingerprintListener.clearRecordsForTest();
            var listener = new ContextCacheKeyFingerprintListener();
            DefaultTestContext ctx = Mockito.mock(DefaultTestContext.class);
            MergedContextConfiguration mcc = Mockito.mock(MergedContextConfiguration.class);
            Mockito.doReturn(42).when(mcc).hashCode();
            String longString = "x".repeat(500);
            Mockito.when(mcc.toString()).thenReturn(longString);
            Field f = ReflectionUtils.findField(DefaultTestContext.class, "mergedConfig");
            ReflectionUtils.makeAccessible(f);
            f.set(ctx, mcc);

            // when
            listener.beforeTestClass(ctx);

            // then
            String line = ContextCacheKeyFingerprintListener.getRecordedLines().get(0);
            String displayCol = line.split("\t", 2)[1];
            assertThat(displayCol).hasSize(200);
        }
    }
    ```

    Verify compile first: `./mvnw -q clean test-compile` must exit 0 (the stubs compile against Spring 7.0.6, and the test class compiles against the stubs).

    Then run the count-listener test (GREEN) and the fingerprint-listener test (RED):
    - `./mvnw test -Dtest='ContextLoadCountListenerTest' -DfailIfNoTests=true -Djacoco.skip=true` MUST exit 0 (both methods green — listener fix landed in EDIT 1).
    - `./mvnw test -Dtest='ContextCacheKeyFingerprintListenerTest' -DfailIfNoTests=true -Djacoco.skip=true` MUST exit non-zero (RED — the three tests fail because the stubs are empty).
  </action>
  <verify>
    <automated>
      ./mvnw -q clean test-compile
      # Must exit 0 — all four files compile.
      ./mvnw test -Dtest='ContextLoadCountListenerTest' -DfailIfNoTests=true -Djacoco.skip=true
      # Must exit 0 — both count-listener tests green.
      ! ./mvnw test -Dtest='ContextCacheKeyFingerprintListenerTest' -DfailIfNoTests=true -Djacoco.skip=true
      # Must exit 0 (the `!` inverts non-zero — the fingerprint test class is RED at end of Task 1).
      grep -c '"total " + count.get()' src/test/java/org/ctc/testsupport/ContextLoadCountListener.java
      # Must equal 1 (new format string present).
      grep -v '^[[:space:]]*\(//\|\*\|/\*\)' src/test/java/org/ctc/testsupport/ContextLoadCountListener.java | grep -c 'String.valueOf(count'
      # Must equal 0 (old format removed; filter strips comments per Nyquist grep-gate hygiene).
      grep -c 'implements TestExecutionListener\|implements org.springframework.test.context.TestExecutionListener' src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java
      # Must equal 1 (skeleton implements the SPI).
    </automated>
  </verify>
  <acceptance_criteria>
    - Source: `ContextLoadCountListener.java` writes Line 1 in `total <count>\n` format with `TRUNCATE_EXISTING` semantics via the new `writeMarkerFile(Path)` helper, and the shutdown hook delegates to that helper.
    - Source: `ContextLoadCountListenerTest` contains BOTH the original counter test AND the new `whenWriteMarkerFile_thenFirstLineMatchesTotalFormat` test method.
    - Source: `ContextCacheKeyFingerprintListener.java` exists as a stub: `implements TestExecutionListener`, empty `beforeTestClass`, no-op `clearRecordsForTest`, no-op `writeMarkerFile`, `getRecordedLines` returns `Collections.emptyList()`. The Javadoc documents the marker-file ordering invariant.
    - Source: `ContextCacheKeyFingerprintListenerTest.java` exists with three test methods that exercise the not-yet-implemented behavior (and seed REAL records via `beforeTestClass` for Test 2's APPEND-format assertions).
    - Behavior: `./mvnw -q clean test-compile` exits 0.
    - Behavior: `./mvnw test -Dtest='ContextLoadCountListenerTest' ...` exits 0 (GREEN — count listener fix is complete).
    - Behavior: `./mvnw test -Dtest='ContextCacheKeyFingerprintListenerTest' ...` exits non-zero (RED — stubs make all three tests fail).
  </acceptance_criteria>
  <done>
    `ContextLoadCountListener` migration complete and GREEN; `ContextCacheKeyFingerprintListener` skeleton + RED unit tests committed. The RED state is the entry condition for Task 2.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2 (TDD GREEN): Implement `ContextCacheKeyFingerprintListener` + register in `META-INF/spring.factories`</name>
  <files>src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java, src/test/resources/META-INF/spring.factories</files>
  <behavior>
    - All three `ContextCacheKeyFingerprintListenerTest` tests flip RED → GREEN.
    - The combined `./mvnw test -Dtest='ContextLoadCountListenerTest,ContextCacheKeyFingerprintListenerTest'` invocation passes (5 tests total: 2 in count listener test + 3 in fingerprint listener test).
    - `META-INF/spring.factories` auto-registers the listener so Spring TCF instantiates it for every test class.
  </behavior>
  <read_first>
    - src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java (Task 1 stub state).
    - src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListenerTest.java (Task 1 failing tests — drive the implementation).
    - src/test/resources/META-INF/spring.factories (current 1-line content).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-RESEARCH.md § RQ-2 (reflection path) + § RQ-3 (registration via META-INF/spring.factories Option A).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-07, D-08, D-10 (all LOCKED).
    - CLAUDE.md § "Static Analysis (SpotBugs + find-sec-bugs)" for the targeted `@SuppressFBWarnings` discipline.
  </read_first>
  <action>
    Two file edits — listener implementation + spring.factories registration.

    EDIT 1 — `ContextCacheKeyFingerprintListener.java`: replace the stub body with the GREEN implementation.

    The class skeleton from Task 1 stays (package, imports, class declaration, static fields, Javadoc). Replace the four stub bodies and ADD a static initializer block + shutdown hook:

    Static fields (REPLACE the Task 1 stub fields):
    ```java
    static final int DISPLAY_TRUNCATION_LIMIT = 200;
    static final CopyOnWriteArrayList<String> records = new CopyOnWriteArrayList<>();
    private static final Field mergedConfigField;
    ```

    Static initializer — register the shutdown hook (parallels `ContextLoadCountListener`'s pattern, with APPEND open option):
    ```java
    static {
        mergedConfigField = ReflectionUtils.findField(DefaultTestContext.class, "mergedConfig");
        if (mergedConfigField != null) {
            ReflectionUtils.makeAccessible(mergedConfigField);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                long pid = ProcessHandle.current().pid();
                Path out = Paths.get("target/test-perf/context-loads-" + pid + ".txt");
                writeMarkerFile(out);
            } catch (IOException e) {
                System.err.println("ContextCacheKeyFingerprintListener: could not write marker file: "
                        + e.getMessage());
            }
        }, "ContextCacheKeyFingerprintListener-shutdown"));
    }
    ```

    `beforeTestClass` (REPLACE stub):
    ```java
    @Override
    public void beforeTestClass(TestContext testContext) {
        if (mergedConfigField == null) {
            return;
        }
        Object raw = ReflectionUtils.getField(mergedConfigField, testContext);
        if (!(raw instanceof MergedContextConfiguration mcc)) {
            return;
        }
        String hex = Integer.toHexString(mcc.hashCode());
        String display = mcc.toString();
        if (display.length() > DISPLAY_TRUNCATION_LIMIT) {
            display = display.substring(0, DISPLAY_TRUNCATION_LIMIT);
        }
        records.add(hex + "\t" + display);
    }
    ```

    Test-accessor helpers (REPLACE stubs):
    ```java
    static List<String> getRecordedLines() {
        return List.copyOf(records);
    }

    static void clearRecordsForTest() {
        records.clear();
    }

    static void writeMarkerFile(Path out) throws IOException {
        Files.createDirectories(out.getParent());
        Files.write(out, records,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }
    ```

    Imports to add (alongside the Task 1 imports): `java.nio.file.Paths`, `java.nio.file.StandardOpenOption`, `org.springframework.test.context.MergedContextConfiguration`, `org.springframework.test.context.support.DefaultTestContext`, `org.springframework.util.ReflectionUtils`, `java.lang.reflect.Field`.

    SpotBugs: if `./mvnw verify` flags the reflection (`DP_DO_INSIDE_DO_PRIVILEGED`, `REFLECTIVE_ACCESS`, or similar), add a TARGETED method-level suppression on `beforeTestClass`:
    ```java
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = {"DP_DO_INSIDE_DO_PRIVILEGED", "REFLECTIVE_ACCESS"},
            justification = "Test-only PERF-02 instrumentation; reflection on DefaultTestContext.mergedConfig "
                          + "is intentional per RESEARCH RQ-2 (DefaultTestContext has been the sole TestContext "
                          + "implementation since Spring 4.0). No production code path uses this listener.")
    ```
    Per CLAUDE.md SpotBugs discipline: targeted, with full justification, NEVER as a blanket `<Match>` in `config/spotbugs-exclude.xml` and NEVER `@SuppressWarnings("all")`.

    Update the Javadoc by replacing the "STUB (Task 1 RED phase)" sentence with: "Spring TCF activation runs this listener once per test class. Records are accumulated in a thread-safe `CopyOnWriteArrayList` and appended to the PID-keyed marker file at JVM shutdown." Keep the marker-file ordering invariant block from Task 1.

    EDIT 2 — `src/test/resources/META-INF/spring.factories`: append the registration line (do NOT modify the existing `ApplicationContextInitializer` line — D-14 production invariance / D-07 hybrid surface).

    Before (1 line):
    ```
    org.springframework.context.ApplicationContextInitializer=org.ctc.testsupport.ContextLoadCountListener
    ```

    After (2 lines, no trailing blank line):
    ```
    org.springframework.context.ApplicationContextInitializer=org.ctc.testsupport.ContextLoadCountListener
    org.springframework.test.context.TestExecutionListener=org.ctc.testsupport.ContextCacheKeyFingerprintListener
    ```

    Run the combined unit test invocation to confirm the GREEN transition:
    ```
    ./mvnw test -Dtest='ContextLoadCountListenerTest,ContextCacheKeyFingerprintListenerTest' -DfailIfNoTests=true -Djacoco.skip=true
    ```
    Must exit 0 with 5 tests passing.
  </action>
  <verify>
    <automated>
      ./mvnw test -Dtest='ContextLoadCountListenerTest,ContextCacheKeyFingerprintListenerTest' -DfailIfNoTests=true -Djacoco.skip=true
      # Must exit 0 — 5 tests total, all green.
      grep -c 'Integer.toHexString' src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java
      # Must equal 1.
      grep -c 'StandardOpenOption.APPEND' src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java
      # Must be >= 1.
      grep -c 'mergedConfig' src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java
      # Must be >= 2 (field lookup + access).
      wc -l src/test/resources/META-INF/spring.factories
      # Must report 2 lines.
      grep -c 'ContextCacheKeyFingerprintListener' src/test/resources/META-INF/spring.factories
      # Must equal 1.
      grep -c 'ContextLoadCountListener' src/test/resources/META-INF/spring.factories
      # Must equal 1.
    </automated>
  </verify>
  <acceptance_criteria>
    - Source: `ContextCacheKeyFingerprintListener.java` implements the GREEN spec — static initializer with shutdown hook (APPEND mode), populated `beforeTestClass` (reflection + hash + truncate + accumulate), and `writeMarkerFile`/`getRecordedLines`/`clearRecordsForTest` helpers.
    - Source: Javadoc retains the marker-file ordering invariant block from Task 1.
    - Source: `META-INF/spring.factories` has exactly 2 lines registering both SPIs.
    - Source: If SpotBugs flagged the reflection, a targeted `@SuppressFBWarnings({"DP_DO_INSIDE_DO_PRIVILEGED", "REFLECTIVE_ACCESS"}, justification="...")` annotation appears at method or class level (NOT a global `<Match>` entry).
    - Behavior: `./mvnw test -Dtest='ContextLoadCountListenerTest,ContextCacheKeyFingerprintListenerTest' ...` exits 0 with all 5 tests green.
  </acceptance_criteria>
  <done>
    `ContextCacheKeyFingerprintListener` GREEN — Task 1's RED tests are now passing; the listener auto-registers for every test class via `META-INF/spring.factories`.
  </done>
</task>

<task type="auto">
  <name>Task 3 (TDD REFACTOR / cross-plan validation): Full backup IT suite under combined listeners</name>
  <files>(verification only — no source files; marker-file outputs inspected, source not modified unless ordering race surfaces)</files>
  <read_first>
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-08 (marker-file format invariant).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-RESEARCH.md § RQ-4 (shutdown-hook ordering analysis + Option B coordinator fallback).
    - src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java (post-Task-2 GREEN state — including the marker-file ordering invariant Javadoc).
    - CLAUDE.md § "Test-Aufrufe optimieren" + § "Keine Flaky-Test-Vertagung" (no flaky dismissal — regressions are root-caused, not deferred).
  </read_first>
  <action>
    Cross-plan validation that the combined listeners (post-Task-1 count format + post-Task-2 fingerprint implementation) produce a well-formed marker file under realistic Spring TCF activation across the full backup IT suite (which runs under Plan 89-01's `forkCount=2` Failsafe configuration).

    Run:
    ```
    ./mvnw verify -Dit.test='org.ctc.backup.**' -Djacoco.skip=true
    ```
    Must exit 0 with all backup ITs green.

    Then inspect the marker files for the format invariant:
    ```
    ls target/test-perf/context-loads-*.txt
    # Must list at least 2 files (one per Failsafe fork).
    head -1 target/test-perf/context-loads-*.txt
    # Each file's first line must match `^total \d+$`.
    sed -n '2,5p' target/test-perf/context-loads-*.txt
    # Lines 2-5 (where present) must match `^[0-9a-f]{1,8}\t.+$`.
    ```

    Verification logic (single shell-script-style assertion):
    ```bash
    for f in target/test-perf/context-loads-*.txt; do
      head -1 "$f" | grep -E '^total [0-9]+$' >/dev/null || { echo "BAD Line 1 in $f"; exit 1; }
      # Lines 2+ are only present if Spring TCF activated; if present, they must match the hash pattern.
      if [ "$(wc -l < "$f")" -gt 1 ]; then
        tail -n +2 "$f" | while IFS= read -r line; do
          echo "$line" | grep -E '^[0-9a-f]{1,8}\t.+$' >/dev/null || { echo "BAD hash line in $f: $line"; exit 1; }
        done
      fi
    done
    ```

    If the assertion above FAILS — i.e., a marker file has a hash line APPEARING BEFORE the `total <N>` Line 1 (the documented race) — this is the trigger to apply RESEARCH RQ-4 Option B: refactor both listeners to push events to a single coordinator class `ContextLoadAndFingerprintMarkerWriter` that owns the file and writes once at shutdown. Document the refactor in the plan SUMMARY and re-run Task 3 verification. The Task 1 Javadoc-documented invariant explicitly flags this as the contingency.

    If the assertion PASSES on first run, this task is a pure REFACTOR-phase verification with no code change — the listeners' interaction is empirically validated under realistic concurrency.
  </action>
  <verify>
    <automated>
      ./mvnw verify -Dit.test='org.ctc.backup.**' -Djacoco.skip=true
      # Must exit 0 — backup ITs green under the combined listeners.
      # Then: every marker file's Line 1 must match `total <N>` and Lines 2+ (if any) must match the hash pattern.
      for f in target/test-perf/context-loads-*.txt; do head -1 "$f" | grep -E '^total [0-9]+$' >/dev/null || exit 1; done
      # Must exit 0.
      ls target/test-perf/context-loads-*.txt | head -1 | xargs -I{} sh -c 'wc -l < "{}" | { read n; [ "$n" -le 1 ] && exit 0; tail -n +2 "{}" | grep -vE "^[0-9a-f]{1,8}\t.+$" | head -1 | grep -q . && exit 1 || exit 0; }'
      # Must exit 0 (no malformed hash lines).
    </automated>
  </verify>
  <acceptance_criteria>
    - Behavior: `./mvnw verify -Dit.test='org.ctc.backup.**' -Djacoco.skip=true` exits 0.
    - Behavior: Every marker file under `target/test-perf/` has Line 1 matching `^total \d+$`.
    - Behavior: Every marker-file Line 2+ (when present) matches `^[0-9a-f]{1,8}\t.+$`.
    - Documentation: If ordering race surfaces, plan SUMMARY documents the Option B coordinator refactor + re-verification.
  </acceptance_criteria>
  <done>
    Combined listeners empirically validated under Plan 89-01's `forkCount=2` infrastructure. Marker-file format invariant holds across all generated marker files.
  </done>
</task>

<task type="auto">
  <name>Task 4: Create `scripts/test-perf/aggregate-fingerprints.sh` (top-N cluster reporter)</name>
  <files>scripts/test-perf/aggregate-fingerprints.sh</files>
  <read_first>
    - scripts/app.sh (full file — analog for shell conventions: shebang, `set -euo pipefail`, double-quoting, `$(...)`).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-08 (marker file format: Line 1 = `total <count>`, Lines 2+ = `<hex>\t<display>`) and D-09 (script location + shellcheck-clean requirement).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-RESEARCH.md § RQ-8 (shell conventions + script skeleton with `MARKER_DIR`/`TOP_N` positional args).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md § `aggregate-fingerprints.sh`.
  </read_first>
  <action>
    Create directory `scripts/test-perf/` (does not exist yet) — run `mkdir -p scripts/test-perf`. Then create the script `scripts/test-perf/aggregate-fingerprints.sh`. Use the Write tool then `chmod +x`.

    Full script content:
    ```bash
    #!/usr/bin/env bash
    # PERF-02 forensics: aggregate ContextCacheKeyFingerprintListener output from
    # target/test-perf/context-loads-{PID}.txt marker files and report the top-N
    # cache-key clusters by fragmentation score (occurrence × cluster_size).
    #
    # Marker file layout (per Phase 89 D-08):
    #   Line 1     : total <count>                  -- written by ContextLoadCountListener
    #   Lines 2..N : <8-char-hex-hash>\t<display>   -- written by ContextCacheKeyFingerprintListener
    #
    # Fragmentation score:
    #   occurrence    = number of beforeTestClass calls sharing the hex hash
    #   cluster_size  = number of distinct display strings (i.e. distinct test classes)
    #                   sharing the same hex hash
    #   score         = occurrence * cluster_size
    #
    # Usage:
    #   ./scripts/test-perf/aggregate-fingerprints.sh [MARKER_DIR] [TOP_N]
    #
    # Arguments (optional, positional):
    #   MARKER_DIR  default: target/test-perf
    #   TOP_N       default: 5

    set -euo pipefail

    MARKER_DIR="${1:-target/test-perf}"
    TOP_N="${2:-5}"

    if [[ ! -d "$MARKER_DIR" ]]; then
        echo "aggregate-fingerprints: marker directory not found: $MARKER_DIR" >&2
        exit 1
    fi

    shopt -s nullglob
    files=("$MARKER_DIR"/context-loads-*.txt)
    if [[ ${#files[@]} -eq 0 ]]; then
        echo "aggregate-fingerprints: no marker files in $MARKER_DIR" >&2
        exit 1
    fi

    tmp_records="$(mktemp)"
    trap 'rm -f "$tmp_records"' EXIT
    for f in "${files[@]}"; do
        tail -n +2 "$f" >> "$tmp_records"
    done

    awk -F '\t' '
        {
            hash = $1
            display = $2
            occurrence[hash]++
            seen_key = hash "\t" display
            if (!(seen_key in seen)) {
                seen[seen_key] = 1
                cluster_size[hash]++
                sample_display[hash] = display
            }
        }
        END {
            for (hash in occurrence) {
                score = occurrence[hash] * cluster_size[hash]
                printf "%d\t%d\t%d\t%s\t%s\n",
                        score, occurrence[hash], cluster_size[hash], hash, sample_display[hash]
            }
        }
    ' "$tmp_records" | sort -k1,1 -n -r | head -n "$TOP_N" | awk -F '\t' '
        BEGIN {
            print "Rank  Score  Occur  Cluster  Hex       Sample display (truncated to 200 chars)"
            print "----  -----  -----  -------  --------  ----------------------------------------"
        }
        {
            rank++
            printf "%-4d  %-5d  %-5d  %-7d  %-8s  %s\n", rank, $1, $2, $3, $4, $5
        }
    '
    ```

    After creating the file, run `chmod +x scripts/test-perf/aggregate-fingerprints.sh`.

    No shellcheck disables — must pass `shellcheck scripts/test-perf/aggregate-fingerprints.sh` clean.

    Smoke-test with a hand-crafted marker file (against post-Task-3 marker files OR against `/tmp/agg-smoke`):
    ```
    mkdir -p /tmp/agg-smoke
    printf 'total 4\n3fa2c1\t[FooTest mcc]\n3fa2c1\t[BarTest mcc]\nabc123\t[BazTest mcc]\n' > /tmp/agg-smoke/context-loads-9999.txt
    ./scripts/test-perf/aggregate-fingerprints.sh /tmp/agg-smoke 5
    ```
    Output must list `3fa2c1` with `Occur=2, Cluster=2, Score=4` as the top cluster.
  </action>
  <verify>
    <automated>
      test -x scripts/test-perf/aggregate-fingerprints.sh
      # Must exit 0 (executable bit set).
      shellcheck scripts/test-perf/aggregate-fingerprints.sh
      # Must exit 0 (no warnings, no disables).
      head -1 scripts/test-perf/aggregate-fingerprints.sh | grep -c '^#!/usr/bin/env bash$'
      # Must equal 1 (canonical shebang per RQ-8).
      grep -c 'set -euo pipefail' scripts/test-perf/aggregate-fingerprints.sh
      # Must equal 1.
      mkdir -p /tmp/agg-smoke && \
        printf 'total 4\n3fa2c1\t[FooTest mcc]\n3fa2c1\t[BarTest mcc]\nabc123\t[BazTest mcc]\n' > /tmp/agg-smoke/context-loads-9999.txt && \
        ./scripts/test-perf/aggregate-fingerprints.sh /tmp/agg-smoke 5 | grep -q '3fa2c1'
      # Must exit 0 — script reads the seeded file, groups by hash, and reports the dominant cluster.
    </automated>
  </verify>
  <acceptance_criteria>
    - Source: `scripts/test-perf/aggregate-fingerprints.sh` exists, executable, shebang `#!/usr/bin/env bash` on Line 1.
    - Source: contains `set -euo pipefail` after the comment header.
    - Source: accepts positional args `MARKER_DIR` (default `target/test-perf`) and `TOP_N` (default `5`).
    - Source: skips Line 1 via `tail -n +2`; groups by hex hash; emits Top-N sorted by `occurrence × cluster_size` descending.
    - Behavior: `shellcheck scripts/test-perf/aggregate-fingerprints.sh` exits 0.
    - Behavior: smoke test against hand-crafted marker file correctly identifies the dominant cluster.
  </acceptance_criteria>
  <done>
    `aggregate-fingerprints.sh` is executable, shellcheck-clean, and groups marker-file records by hex hash to produce the top-N most-fragmented clusters.
  </done>
</task>

<task type="auto">
  <name>Task 5: Update `docs/test-performance.md` — migrate aggregator loop + add `§ PERF-02 Forensics` H2 section</name>
  <files>docs/test-performance.md</files>
  <read_first>
    - docs/test-performance.md (full file — section structure, table conventions, aggregator loop at lines 233-239, `§ Context Load Counts (PERF-02)` at lines 195-244 for the insertion point).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-08, D-09, D-16 (migration target + new section requirements; doc surfaces touched).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-RESEARCH.md § RQ-9 (structural conventions: H2 sections, GFM tables, fenced `bash` blocks, bold key numbers).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md § `docs/test-performance.md (three modifications)` — Analog A for the new § PERF-02 Forensics section.
    - scripts/test-perf/aggregate-fingerprints.sh (post-Task-4 state — the script the new section documents).
  </read_first>
  <action>
    Three distinct edits to `docs/test-performance.md`.

    EDIT 1 — Migrate the aggregator loop at lines 233-239 (D-08 format-migration; RESEARCH RQ-9):

    Current (lines 233-239):
    ```bash
    TOTAL=0
    for f in target/test-perf/context-loads-*.txt; do
      TOTAL=$((TOTAL + $(cat "$f")))
    done
    echo "Total context loads: $TOTAL"
    ```

    Replace with:
    ```bash
    TOTAL=0
    for f in target/test-perf/context-loads-*.txt; do
      TOTAL=$((TOTAL + $(head -1 "$f" | awk '{print $2}')))
    done
    echo "Total context loads: $TOTAL"
    ```

    Additionally, replace the paragraph immediately below this code block at lines 241-243 (currently reads "The marker files do not contain trailing newlines, so `paste -sd+ - | bc` reads the file contents as a single concatenated digit string and produces the wrong number; the loop form above (or equivalent) is the correct aggregation.") with a Phase-89-aware note:

    ```
    Phase 89 PERF-02 extended the marker-file format: Line 1 is now `total <count>` (written
    by `ContextLoadCountListener`), and subsequent lines are `<hex-hash>\t<display>` records
    appended by `ContextCacheKeyFingerprintListener`. The aggregator above extracts the count
    from Line 1 via `head -1 "$f" | awk '{print $2}'`. For the per-cluster fingerprint
    aggregation, see `## PERF-02 Forensics — Cache-Key Fingerprint Analysis` below.
    ```

    EDIT 2 — Insert a new H2 section `## PERF-02 Forensics — Cache-Key Fingerprint Analysis` AFTER the `## Context Load Counts (PERF-02)` section (which ends near line 244 with the `---` rule) and BEFORE the `## Per-Decision Evidence (D-03 / D-04 / D-06)` section at line 247.

    Section content (executor may rephrase prose; MUST keep structural elements: H2 heading text, usage code block, output-format table):
    ```markdown
    ## PERF-02 Forensics — Cache-Key Fingerprint Analysis

    `ContextCacheKeyFingerprintListener` (registered via `src/test/resources/META-INF/spring.factories`)
    fires on `TestExecutionListener#beforeTestClass` and reflectively reads the `mergedConfig`
    field on `DefaultTestContext`. Per `beforeTestClass` invocation it accumulates one
    `<hex-hash>\t<display>` record where `<hex-hash>` is `Integer.toHexString(mcc.hashCode())`
    (the exact bucketing function used by Spring TCF `DefaultContextCache.contextMap`) and
    `<display>` is `mcc.toString()` truncated to 200 characters. At JVM shutdown the
    accumulated records are appended to the same PID-keyed marker file used by
    `ContextLoadCountListener` (Line 1 = `total <count>`; Lines 2+ = the appended records).

    The aggregator at `scripts/test-perf/aggregate-fingerprints.sh` groups records by hex
    hash, counts occurrences and cluster size (distinct display strings per hash), and emits
    the top-N clusters by fragmentation score (`occurrence × cluster_size`).

    ### Usage

    ```bash
    # After ./mvnw clean verify finishes, with the marker files in target/test-perf/:
    ./scripts/test-perf/aggregate-fingerprints.sh                    # defaults: target/test-perf, top 5
    ./scripts/test-perf/aggregate-fingerprints.sh target/test-perf 10  # top 10
    ```

    ### Example Output

    ```
    Rank  Score  Occur  Cluster  Hex       Sample display (truncated to 200 chars)
    ----  -----  -----  -------  --------  ----------------------------------------
    1     14     7      2        3fa2c1    [MergedContextConfiguration@1234 testClass=Backup...
    2     9      3      3        abc1234   [MergedContextConfiguration@5678 testClass=SiteGen...
    3     6      6      1        deadbeef  [MergedContextConfiguration@9abc testClass=AdminLay...
    4     4      4      1        c0ffee01  [MergedContextConfiguration@def0 testClass=DriverIm...
    5     2      2      1        12345678  [MergedContextConfiguration@1357 testClass=BackupRo...
    ```

    Interpretation:
    - **High Occur + low Cluster** (rank 3): a single context heavily reused — well-cached, good.
    - **High Cluster, lower Occur** (rank 2): a context that fragments across multiple test
      classes — a PERF-03 consolidation candidate.
    - **Score** combines both signals: ranks the clusters where consolidation would yield the
      biggest cache-key reduction.
    ```

    EDIT 3 — Cross-reference update: Locate the existing `## Context Load Counts (PERF-02)` paragraph at line 226 that says "Verifying this hypothesis requires per-fork ContextLoadCountListener breakdown and is queued for v1.12 (lever 2 below)." Replace with: "Phase 89 closes this gap — `ContextCacheKeyFingerprintListener` (see `## PERF-02 Forensics` below) emits per-context cache-key hashes alongside the count, exposing the fragmentation signal directly."

    Verification after all edits: the file's overall H1/H2 structure stays intact (top `# Test Performance Log (Phase 86)`, then `## Result Verdict`, `## Baseline`, `## Post-Optimization Wallclock (Wave 3)`, `## CI Results`, `## Context Load Counts (PERF-02)`, **new** `## PERF-02 Forensics — Cache-Key Fingerprint Analysis`, `## Per-Decision Evidence`, `## v1.12 Forward Path`). Plan 89-03 will later add `## Post-Optimization Wallclock (Wave 4)` AFTER `## Post-Optimization Wallclock (Wave 3)` and update the `## v1.12 Forward Path` Lever-1 row.

    Then run the final-suite gate (collapses the old Task 7 into the closing step of this plan):
    ```
    ./mvnw clean verify --no-transfer-progress
    ```
    Must exit 0 with:
    - All unit tests green (Surefire summary "Tests run: <N>, Failures: 0, Errors: 0, Skipped: 0").
    - All integration tests green.
    - SpotBugs gate passes (0 Medium+HIGH findings).
    - JaCoCo CSV generated.

    Inspect a representative marker file confirming the new format:
    ```
    head -1 "$(ls target/test-perf/context-loads-*.txt | head -1)" | grep -E '^total [0-9]+$'
    ```
    Must exit 0.

    Record in plan SUMMARY: total test count (must match or exceed 1011 baseline +3 new tests from this plan), JaCoCo LINE coverage ratio (must remain ≥ 0.8888), SpotBugs findings count (must remain 0), one sample marker-file head -3 output.
  </action>
  <verify>
    <automated>
      grep -c 'PERF-02 Forensics' docs/test-performance.md
      # Must be >= 2 (one in the new H2, one in the cross-reference paragraph).
      grep -c 'head -1 "\$f" | awk' docs/test-performance.md
      # Must equal 1 (migrated aggregator line).
      grep -v '^[[:space:]]*\(#\|<!--\)' docs/test-performance.md | grep -c '\$(cat "\$f")'
      # Must equal 0 (old aggregator pattern fully removed).
      grep -c 'scripts/test-perf/aggregate-fingerprints.sh' docs/test-performance.md
      # Must be >= 2 (usage block + cross-reference paragraph).
      ./mvnw clean verify --no-transfer-progress
      # Must exit 0.
      head -1 "$(ls target/test-perf/context-loads-*.txt | head -1)" | grep -E '^total [0-9]+$'
      # Must exit 0.
    </automated>
  </verify>
  <acceptance_criteria>
    - Source: `docs/test-performance.md` line 233-239 aggregator loop uses `head -1 "$f" | awk '{print $2}'` (replaces `cat "$f"`).
    - Source: file contains a new H2 section `## PERF-02 Forensics — Cache-Key Fingerprint Analysis` between `## Context Load Counts (PERF-02)` and `## Per-Decision Evidence`.
    - Source: new H2 section contains a usage block (fenced `bash`) and an example output block.
    - Source: existing "queued for v1.12 (lever 2 below)" sentence is replaced with a Phase-89 closure note.
    - Behavior: no other section (Result Verdict, Baseline, Post-Optimization Wallclock Wave 3, CI Results, Per-Decision Evidence, v1.12 Forward Path) altered.
    - Behavior: `./mvnw clean verify --no-transfer-progress` exits 0; SpotBugs 0; JaCoCo LINE ≥ 0.8888; sample marker-file Line 1 matches `^total \d+$`.
    - Documentation: plan SUMMARY records test count, JaCoCo ratio, SpotBugs count, sample marker-file head.
  </acceptance_criteria>
  <done>
    `docs/test-performance.md` documents the new fingerprint listener output format, migrated aggregator loop, and the aggregator-script usage with an example top-5 cluster output. Full suite is green; all v1.11 baselines preserved.
  </done>
</task>

</tasks>

<verification>
Full-plan gate:
- All 5 tasks pass their automated verify gates.
- `./mvnw clean verify --no-transfer-progress` exits 0 (Task 5).
- All 1011 + 3 new tests green (1014+ total). Existing test counts MUST NOT regress.
- JaCoCo LINE coverage ratio ≥ 0.8888.
- SpotBugs Medium+HIGH findings = 0.
- Marker file `target/test-perf/context-loads-{PID}.txt` has Line 1 = `total <digit>` AND (if any contexts loaded during the run) Lines 2+ matching `^[0-9a-f]{1,8}\t.+$`.
- `shellcheck scripts/test-perf/aggregate-fingerprints.sh` exits 0.
- No production code changes (D-14: `application.yml` byte-identical; D-16: `CLAUDE.md` and `docs/operations/import-runbook.md` byte-identical).
</verification>

<success_criteria>
PERF-02 satisfied when:
- `ContextLoadCountListener` writes Line 1 of the PID-keyed marker file as `total <count>\n` with `TRUNCATE_EXISTING`.
- `ContextCacheKeyFingerprintListener` exists, implements `TestExecutionListener`, reflectively reads `DefaultTestContext.mergedConfig`, accumulates `<Integer.toHexString(mcc.hashCode())>\t<mcc.toString().substring(0, 200)>` per `beforeTestClass`, and appends the accumulated lines to the same PID-keyed marker file at JVM shutdown via `StandardOpenOption.APPEND`.
- The marker-file shutdown-hook ordering invariant is documented in `ContextCacheKeyFingerprintListener` Javadoc.
- `src/test/resources/META-INF/spring.factories` registers the new listener (2 lines total, both keys distinct).
- `ContextCacheKeyFingerprintListenerTest` proves accumulator behavior + APPEND marker-file format + truncation across 3 unit tests; Test 2 seeds REAL records via `beforeTestClass` (not a hollow no-op test).
- `scripts/test-perf/aggregate-fingerprints.sh` is executable, shellcheck-clean, and emits a top-N cluster report.
- `docs/test-performance.md` has the new `§ PERF-02 Forensics` H2 section AND the migrated aggregator loop.
- `./mvnw clean verify` exits 0; v1.11 quality gates preserved (JaCoCo ≥ 88.88 %, SpotBugs 0).
</success_criteria>

<output>
Create `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-02-SUMMARY.md` when done, including:

- `## Changes` — list each modified/created file with a one-line summary.
- `## Marker-File Format Migration` — note the `cat "$f"` → `head -1 | awk '{print $2}'` migration (D-08) and the marker-file ordering invariant documented in `ContextCacheKeyFingerprintListener` Javadoc.
- `## Sample Marker File Output` — `head -5` of one representative `target/test-perf/context-loads-*.txt` from the Task 5 verify run.
- `## Quality Gates` — JaCoCo ratio, SpotBugs count, test count.
- `## Forward Path` — Plan 89-03 (Wave-4 measurement + docs) now unblocked.
</output>
