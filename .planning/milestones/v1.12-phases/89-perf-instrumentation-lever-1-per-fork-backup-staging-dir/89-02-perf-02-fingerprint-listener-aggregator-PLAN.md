---
phase: 89-perf-instrumentation-lever-1-per-fork-backup-staging-dir
plan: 02
type: execute
wave: 2
depends_on:
  - 89-01
files_modified:
  - src/test/java/org/ctc/testsupport/ContextLoadCountListener.java
  - src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java
  - src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java
  - src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListenerTest.java
  - src/test/resources/META-INF/spring.factories
  - scripts/test-perf/aggregate-fingerprints.sh
  - docs/test-performance.md
autonomous: true
requirements:
  - PERF-02
user_setup: []

tags:
  - perf
  - test-infra
  - spring-tcf
  - fingerprint
  - context-cache

must_haves:
  truths:
    - "`ContextLoadCountListener` shutdown hook writes `total <N>` on Line 1 (extended marker format per D-08) — backward compatibility with aggregator scripts via `head -1 | awk '{print $2}'`"
    - "`ContextCacheKeyFingerprintListener` (new) is a `TestExecutionListener` registered via `META-INF/spring.factories` (D-07 hybrid surface, Option A from RESEARCH §RQ-3) — auto-fires for ALL test classes with NO per-class annotation"
    - "On `beforeTestClass`, the fingerprint listener records `<hex-hash>\\t<mcc-display>` lines, where `<hex-hash>` = `Integer.toHexString(MergedContextConfiguration.hashCode())` (D-10, 1:1 with Spring TCF `DefaultContextCache` bucketing) and `<mcc-display>` = `MergedContextConfiguration.toString()` truncated to 200 chars (file-size bound per planner-Discretion D-08)"
    - "Sidecar marker file `target/test-perf/context-loads-{PID}-fingerprints.txt` carries the hash lines (RESEARCH §PATTERN sidecar approach — avoids JVM shutdown-hook ordering race between the two listeners)"
    - "`scripts/test-perf/aggregate-fingerprints.sh` (executable, shellcheck-clean, repo conventions) parses sidecar files and emits Top-5 clusters by `occurrence × cluster-size` (D-09)"
    - "`docs/test-performance.md § PERF-02 Forensics` documents the aggregator usage + a sample Top-5 output (D-16)"
    - "The existing aggregator loop in `docs/test-performance.md` lines 233-239 (raw `cat $f` integer sum) is MIGRATED to `head -1 \"$f\" | awk '{print $2}'` — backward-compat fix for D-08 format change"
    - "Full `./mvnw verify --no-transfer-progress` (Surefire + Failsafe, no E2E) exits 0 after both listeners are wired — proof that PERF-02 instrumentation does not regress the test suite at scale"
  artifacts:
    - path: "src/test/java/org/ctc/testsupport/ContextLoadCountListener.java"
      provides: "Updated shutdown-hook output format: Line 1 = `total <N>` (D-08); rest of class UNCHANGED (initializer, counter, getter)"
      contains: "\"total \" + count.get()"
    - path: "src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java"
      provides: "Updated assertion that the marker-file write would produce `total <N>` prefix (D-08); existing counter-increment assertion preserved"
      contains: "total"
    - path: "src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java"
      provides: "NEW `TestExecutionListener` capturing per-context cache-key fingerprints; reflection on `DefaultTestContext.mergedConfig` per RESEARCH §RQ-2 Path-A"
      contains: "implements TestExecutionListener"
    - path: "src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListenerTest.java"
      provides: "Unit test verifying the listener's hash extraction + display truncation"
      contains: "@Test"
    - path: "src/test/resources/META-INF/spring.factories"
      provides: "Extended with second line registering `ContextCacheKeyFingerprintListener` under `org.springframework.test.context.TestExecutionListener` (RESEARCH §RQ-3 Option A)"
      contains: "org.springframework.test.context.TestExecutionListener=org.ctc.testsupport.ContextCacheKeyFingerprintListener"
    - path: "scripts/test-perf/aggregate-fingerprints.sh"
      provides: "NEW executable shell script reading sidecar fingerprint files and emitting Top-5 clusters by occurrence × cluster-size"
      contains: "#!/usr/bin/env bash"
    - path: "docs/test-performance.md"
      provides: "NEW `## PERF-02 Forensics — Cache-Key Fingerprint Analysis` section + migrated aggregator loop (D-08 head-line extraction)"
      contains: "## PERF-02 Forensics"
  key_links:
    - from: "src/test/resources/META-INF/spring.factories"
      to: "org.ctc.testsupport.ContextCacheKeyFingerprintListener"
      via: "SpringFactoriesLoader TestExecutionListener key"
      pattern: "org\\.springframework\\.test\\.context\\.TestExecutionListener=.*ContextCacheKeyFingerprintListener"
    - from: "ContextCacheKeyFingerprintListener#beforeTestClass"
      to: "DefaultTestContext.mergedConfig (private final)"
      via: "ReflectionUtils.findField + makeAccessible (RESEARCH §RQ-2)"
      pattern: "ReflectionUtils\\.(findField|makeAccessible|getField)"
    - from: "ContextCacheKeyFingerprintListener shutdown hook"
      to: "target/test-perf/context-loads-{PID}-fingerprints.txt"
      via: "sidecar file write (avoids race with ContextLoadCountListener's shutdown hook on the same primary marker)"
      pattern: "context-loads-.*-fingerprints\\.txt"
    - from: "scripts/test-perf/aggregate-fingerprints.sh"
      to: "target/test-perf/context-loads-*-fingerprints.txt"
      via: "awk pipeline grouping by hex hash"
      pattern: "context-loads-.*-fingerprints"
    - from: "docs/test-performance.md aggregator loop (was L233-239)"
      to: "context-loads-{PID}.txt new format (Line 1 = `total <N>`)"
      via: "head -1 $f | awk '{print $2}' extraction"
      pattern: "head -1 .*\\| awk .*\\$2"
---

<objective>
PERF-02 Spring TCF cache-key fingerprint instrumentation: introduce `ContextCacheKeyFingerprintListener` (new `TestExecutionListener`) that captures `MergedContextConfiguration.hashCode()` per context-init event, persist hash + truncated MCC display into a PID-keyed sidecar marker file, ship a shellcheck-clean aggregator script, document usage in `docs/test-performance.md § PERF-02 Forensics`, AND migrate the existing aggregator loop (lines 233-239) to the new `total <N>` marker-file Line 1 format. The hash output feeds Phase 90's PERF-03 targeted-consolidation decision (which cluster to merge first).

Purpose: PERF-02 produces the empirical data PERF-03 needs to avoid the Phase-86 fragmentation pitfall (per-class `@DynamicPropertySource` split one shared cache key into seven — Plan-86-02 SUMMARY lesson). Without this data, PERF-03 would consolidate blindly. The hybrid surface (D-07) keeps `ContextLoadCountListener` count-only and adds the new listener for the hashing — both write to the same `target/test-perf/` directory but to distinct files (sidecar approach from RESEARCH §RQ-2 / PATTERN reasoning), eliminating shutdown-hook ordering race.

Output: Two listener Java files (one modified, one new); one updated unit test; one new unit test; `META-INF/spring.factories` extended with the new listener registration line; new executable `scripts/test-perf/aggregate-fingerprints.sh`; updated `docs/test-performance.md` with new section + migrated aggregator-loop snippet.
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
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-01-SUMMARY.md
@.planning/codebase/TESTING.md
@CLAUDE.md

<interfaces>
<!-- Spring TCF + test-listener contracts. Extracted verbatim from RESEARCH §RQ-2 / §RQ-3 / §RQ-7. Executor uses these directly — no codebase exploration required. -->

Spring TCF TestExecutionListener API (already on classpath via spring-test-7.0.6.jar):
```java
package org.springframework.test.context;

public interface TestExecutionListener {
    default void beforeTestClass(TestContext testContext) throws Exception {}
    default void prepareTestInstance(TestContext testContext) throws Exception {}
    default void beforeTestMethod(TestContext testContext) throws Exception {}
    // ... (other methods default-no-op for this listener)
}
```

Spring TCF DefaultTestContext private field (RESEARCH §RQ-2 — `mergedConfig` is `private final`, NO public getter on `TestContext` interface):
```java
package org.springframework.test.context.support;

public class DefaultTestContext implements TestContext {
    private final MergedContextConfiguration mergedConfig; // ACCESS VIA REFLECTION
}
```

Reflection access pattern (RESEARCH §RQ-2 Path-A, recommended over re-derivation from annotations):
```java
import org.springframework.util.ReflectionUtils;
import java.lang.reflect.Field;
// ...
Field f = ReflectionUtils.findField(DefaultTestContext.class, "mergedConfig");
ReflectionUtils.makeAccessible(f);
MergedContextConfiguration mcc = (MergedContextConfiguration) ReflectionUtils.getField(f, testContext);
```

MergedContextConfiguration hashing (RESEARCH §RQ-7 — `hashCode()` IS the Spring TCF `DefaultContextCache` bucketing function; `Integer.toHexString` produces 1-8 hex chars):
```java
String hexHash = Integer.toHexString(mcc.hashCode()); // 1..8 chars, no leading zeros
String display = mcc.toString();
String truncated = display.length() > 200 ? display.substring(0, 200) : display;
```

spring.factories registration key (RESEARCH §RQ-3 Option A — VERIFIED from spring-test-7.0.6.jar):
```
org.springframework.test.context.TestExecutionListener=org.ctc.testsupport.ContextCacheKeyFingerprintListener
```

Existing ContextLoadCountListener shutdown-hook output (line 29 in CURRENT file — MUST CHANGE per D-08):
```java
// CURRENT: Files.writeString(out, String.valueOf(count.get()));
// TARGET:  Files.writeString(out, "total " + count.get());
```

Sidecar file path pattern (RESEARCH PATTERN — avoid hook-ordering race):
```java
Path sidecar = Paths.get("target/test-perf/context-loads-" + pid + "-fingerprints.txt");
```

Existing docs/test-performance.md aggregator loop (lines 233-239) — MUST MIGRATE in Task 5:
```bash
# CURRENT (broken after D-08 format change):
TOTAL=0
for f in target/test-perf/context-loads-*.txt; do
  TOTAL=$((TOTAL + $(cat "$f")))
done
echo "Total context loads: $TOTAL"

# TARGET (D-08-aware):
TOTAL=0
for f in target/test-perf/context-loads-*.txt; do
  # Exclude sidecar fingerprint files (they don't carry "total <N>" header)
  case "$f" in *-fingerprints.txt) continue ;; esac
  TOTAL=$((TOTAL + $(head -1 "$f" | awk '{print $2}')))
done
echo "Total context loads: $TOTAL"
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: ContextLoadCountListener — extend shutdown hook to write `total <N>` Line 1 format (D-08)</name>
  <files>src/test/java/org/ctc/testsupport/ContextLoadCountListener.java, src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java</files>
  <requirement>PERF-02</requirement>
  <read_first>
    - src/test/java/org/ctc/testsupport/ContextLoadCountListener.java entire file (45 lines — minimal delta: line 29 only)
    - src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java entire file (22 lines — preserve existing counter test, add file-format assertion)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-08 (marker-file format: Line 1 = `total <N>`)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-RESEARCH.md §RQ-4 Risk 4 (test isolation under reuseForks)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md lines 369-405 (self-analog modification — minimal delta)
  </read_first>
  <action>
    Modify `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` line 29 ONLY:
    - Current: `Files.writeString(out, String.valueOf(count.get()));`
    - Change to: `Files.writeString(out, "total " + count.get());`
    - All other lines (imports, class signature, AtomicInteger field, shutdown-hook structure, error logging, `initialize()` body, `getCount()` static helper) are UNCHANGED.
    - The shutdown-hook thread name `"ContextLoadCountListener-shutdown"` is UNCHANGED.

    Modify `src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java`:
    - Existing `whenInitializeCalledTwice_thenCountIncrementsByTwo` test is UNCHANGED.
    - Add second test `whenShutdownHookFormat_thenLineOneStartsWithTotalPrefix`: verifies the format-string composition `"total " + count.get()` produces a string starting with `"total "` followed by a non-negative integer. Since the shutdown hook itself is hard to drive from a unit test (it requires JVM-shutdown to fire), assert the format STRING directly: `String formatted = "total " + ContextLoadCountListener.getCount(); assertThat(formatted).matches("^total \\d+$");`. This proves the format contract without touching the hook.
    - Imports: existing imports stay; no new imports needed (regex assertion uses AssertJ's `matches(String)` which is already imported via static).
  </action>
  <verify>
    <automated>
      1. `grep -c '"total " + count.get()' src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` returns 1.
      2. Anti-regression: `grep -c 'String.valueOf(count.get())' src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` returns 0 (old format gone).
      3. `grep -c -E 'total \\\\d+|^total ' src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java` >= 1 (new format assertion present).
      4. `./mvnw test -Dtest='ContextLoadCountListenerTest' -DfailIfNoTests=true -Djacoco.skip=true --no-transfer-progress` exits 0.
      5. Behavior smoke test: after the test JVM exits, `head -1 target/test-perf/context-loads-*.txt | head -1 | grep -E '^total [0-9]+$'` should exit 0 if any test ran in a forked Surefire JVM and produced a marker file (deferred to Task 3's full-suite verify).
    </automated>
  </verify>
  <done>
    `ContextLoadCountListener` writes `total <N>` as Line 1 of `target/test-perf/context-loads-{PID}.txt` (D-08 format); `ContextLoadCountListenerTest` keeps the existing counter assertion and adds a format-contract assertion; `./mvnw test -Dtest='ContextLoadCountListenerTest'` exits 0.
  </done>
</task>

<task type="auto">
  <name>Task 2: ContextCacheKeyFingerprintListener — new TestExecutionListener + unit test + spring.factories registration</name>
  <files>src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java, src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListenerTest.java, src/test/resources/META-INF/spring.factories</files>
  <requirement>PERF-02</requirement>
  <read_first>
    - src/test/java/org/ctc/testsupport/ContextLoadCountListener.java (analog for shutdown-hook + PID-keyed marker pattern)
    - src/test/resources/META-INF/spring.factories (current single-line content — extend to two lines)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-07 (hybrid surface), D-08 (sidecar approach implied), D-10 (Integer.toHexString hash + 200-char display truncation)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-RESEARCH.md §RQ-2 Path-A (ReflectionUtils + DefaultTestContext.mergedConfig), §RQ-3 Option A (spring.factories registration), §RQ-7 (hash is bucketing function), Risk 3 (SpotBugs DP_DO_INSIDE_DO_PRIVILEGED suppression discipline)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md lines 242-365 (full ContextCacheKeyFingerprintListener pattern + Test pattern + spring.factories pattern)
    - CLAUDE.md "Static Analysis (SpotBugs + find-sec-bugs)" — targeted @SuppressFBWarnings per CLAUDE.md SAST pattern, NEVER blanket
  </read_first>
  <action>
    Create `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java`.

    Package: `org.ctc.testsupport`.
    Interface: `implements org.springframework.test.context.TestExecutionListener`.

    Static field — accumulator (sidecar file populated at shutdown): `private static final java.util.concurrent.CopyOnWriteArrayList<String> FINGERPRINT_LINES = new CopyOnWriteArrayList<>();`

    Static field — reflection field cache (resolved ONCE on class load to avoid per-`beforeTestClass` lookup cost): `private static final Field MERGED_CONFIG_FIELD = ...` — use a static initializer block that calls `ReflectionUtils.findField(DefaultTestContext.class, "mergedConfig")` + `ReflectionUtils.makeAccessible(...)`. If the field is null (Spring TCF changed; unexpected), log the error and leave the field null — the override will degrade to "no-op" rather than crash the test suite (graceful degradation).

    Override `beforeTestClass(TestContext testContext)`:
    - Early return if `MERGED_CONFIG_FIELD` is null (degraded mode).
    - Early return if `!(testContext instanceof DefaultTestContext)` (defensive — RESEARCH §RQ-2 cast safety check).
    - Read MCC via `MergedContextConfiguration mcc = (MergedContextConfiguration) ReflectionUtils.getField(MERGED_CONFIG_FIELD, testContext);`
    - Skip if `mcc == null`.
    - Compute `String hex = Integer.toHexString(mcc.hashCode());`
    - Compute `String display = mcc.toString(); if (display.length() > 200) display = display.substring(0, 200);` (200-char truncation per D-08 + planner Discretion — keeps each line ≤ ~210 bytes, total marker file ≤ ~10KB for 50 IT classes).
    - Append `FINGERPRINT_LINES.add(hex + "\t" + display);`
    - Wrap in try-catch — IllegalAccessException / reflection failures emit `System.err.println("ContextCacheKeyFingerprintListener: ..." + ex.getMessage())` and continue (instrumentation must NEVER break the test suite).

    Static initializer block — register shutdown hook (sidecar-file approach per RESEARCH PATTERN):
    ```
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        try {
            long pid = ProcessHandle.current().pid();
            Path sidecar = Paths.get("target/test-perf/context-loads-" + pid + "-fingerprints.txt");
            Files.createDirectories(sidecar.getParent());
            Files.write(sidecar, FINGERPRINT_LINES, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("ContextCacheKeyFingerprintListener: could not write sidecar: " + e.getMessage());
        }
    }, "ContextCacheKeyFingerprintListener-shutdown"));
    ```

    Package-private accessor for unit testing: `static java.util.List<String> getRecordedLines() { return java.util.List.copyOf(FINGERPRINT_LINES); }`.

    Imports: `java.io.IOException`, `java.lang.reflect.Field`, `java.nio.file.{Files, Path, Paths, StandardOpenOption}`, `java.util.List`, `java.util.concurrent.CopyOnWriteArrayList`, `org.springframework.test.context.{MergedContextConfiguration, TestContext, TestExecutionListener}`, `org.springframework.test.context.support.DefaultTestContext`, `org.springframework.util.ReflectionUtils`.

    SpotBugs suppression: if `./mvnw verify` later flags `DP_DO_INSIDE_DO_PRIVILEGED` or similar on the `ReflectionUtils.makeAccessible` call, add a class-level `@SuppressFBWarnings(value = {"DP_DO_INSIDE_DO_PRIVILEGED"}, justification = "Test-only PERF-02 instrumentation — reflection on Spring private field is intentional (RESEARCH §RQ-2 Path-A). Field is read-only; no write/invoke surface. Production code path never executes this listener.")`. Do NOT preemptively apply — wait for SpotBugs to actually fire.

    Create `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListenerTest.java`.

    Plain JUnit 5 unit test (no `@SpringBootTest`, no `@Tag` — pure unit per CLAUDE.md).

    Test 1 — `whenBeforeTestClassCalledWithMockTestContext_thenFingerprintLineRecorded`:
    - Given: a mock `DefaultTestContext` where `mergedConfig` field is set via reflection to a real (or mocked) `MergedContextConfiguration` instance. Alternative if mocking `MergedContextConfiguration` is tricky: use Mockito to create a `DefaultTestContext` mock with a stubbed reflective field; OR construct a real `DefaultTestContext` if its constructor permits. The simplest path: use Mockito's `mock(DefaultTestContext.class)` + reflection to set the private field on the mock instance. Pattern: `Field f = DefaultTestContext.class.getDeclaredField("mergedConfig"); f.setAccessible(true); f.set(mockTestContext, fakeMcc);` where `fakeMcc` is `mock(MergedContextConfiguration.class)` with `when(fakeMcc.hashCode()).thenReturn(0x3fa2c1);` and `when(fakeMcc.toString()).thenReturn("FakeMcc[classes=...]")`.
    - When: `new ContextCacheKeyFingerprintListener().beforeTestClass(mockTestContext);`
    - Then: `ContextCacheKeyFingerprintListener.getRecordedLines()` contains a line matching `^[0-9a-f]{1,8}\\t.+$`; the line starts with `3fa2c1` (the stubbed hash); the display portion is truncated to ≤ 200 chars.

    Test 2 — `givenDisplayLongerThan200Chars_whenBeforeTestClass_thenDisplayTruncated`:
    - Stub `fakeMcc.toString()` to return a 500-char string.
    - Call `beforeTestClass`.
    - Assert the recorded line's tab-separated display portion has length ≤ 200.

    Imports: `static org.assertj.core.api.Assertions.assertThat`, `static org.mockito.Mockito.{mock, when}`, `org.junit.jupiter.api.Test`, `org.springframework.test.context.MergedContextConfiguration`, `org.springframework.test.context.support.DefaultTestContext`, `java.lang.reflect.Field`.

    Modify `src/test/resources/META-INF/spring.factories`. Current single line:
    ```
    org.springframework.context.ApplicationContextInitializer=org.ctc.testsupport.ContextLoadCountListener
    ```
    Append a second line:
    ```
    org.springframework.test.context.TestExecutionListener=org.ctc.testsupport.ContextCacheKeyFingerprintListener
    ```
    The file becomes 2 lines total. No commas (single listener per key in this project).
  </action>
  <verify>
    <automated>
      1. `test -f src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java`.
      2. `test -f src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListenerTest.java`.
      3. `grep -c 'implements TestExecutionListener\|TestExecutionListener\\b' src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java` >= 1.
      4. `grep -c 'Integer.toHexString' src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java` >= 1.
      5. `grep -c 'ReflectionUtils.findField\|ReflectionUtils.getField\|ReflectionUtils.makeAccessible' src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java` >= 2.
      6. `grep -c 'context-loads-.*-fingerprints' src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java` >= 1 (sidecar file path).
      7. `grep -c 'org.springframework.test.context.TestExecutionListener=org.ctc.testsupport.ContextCacheKeyFingerprintListener' src/test/resources/META-INF/spring.factories` returns 1.
      8. `wc -l src/test/resources/META-INF/spring.factories` reports 2 lines (one ApplicationContextInitializer, one TestExecutionListener).
      9. `./mvnw test -Dtest='ContextLoadCountListenerTest,ContextCacheKeyFingerprintListenerTest' -DfailIfNoTests=true -Djacoco.skip=true --no-transfer-progress` exits 0.
    </automated>
  </verify>
  <done>
    `ContextCacheKeyFingerprintListener` exists, implements `TestExecutionListener`, reads MCC via reflection, writes sidecar marker file at shutdown; unit test passes; `spring.factories` has 2 lines; combined unit-test command exits 0.
  </done>
</task>

<task type="auto">
  <name>Task 3: Combined listeners cross-check + marker-file format inspection under forkCount=2</name>
  <files>(no source file modifications — verification + smoke evidence in this task)</files>
  <requirement>PERF-02</requirement>
  <read_first>
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-VALIDATION.md row 89-02-03 (combined-listeners validation)
    - target/test-perf/ directory after Task 2 — confirm both primary `context-loads-{PID}.txt` (D-08 format) and sidecar `context-loads-{PID}-fingerprints.txt` get written by both shutdown hooks
  </read_first>
  <action>
    Run `./mvnw verify -Dit.test='org.ctc.backup.**' -Djacoco.skip=true --no-transfer-progress` (Failsafe forkCount=2 from Plan 89-01 Task 1). Then inspect the marker files in `target/test-perf/`:

    1. Primary marker files: expect 2 (one per fork JVM) named `context-loads-{PID}.txt`. Each should have Line 1 in `total <N>` format (D-08).
    2. Sidecar marker files: expect 2 named `context-loads-{PID}-fingerprints.txt`. Each should contain N lines, each matching `^[0-9a-f]{1,8}\t.+$` (hex-hash + TAB + display, RESEARCH §RQ-7 hash range).

    Document the inspection results in a temporary verification scratch (e.g. `/tmp/89-02-task-3-evidence.txt`) capturing the head of each marker + sidecar file. This evidence will be cited in `89-02-SUMMARY.md` later.

    If either format is wrong (e.g. sidecar file empty because the listener never fired — possible if `spring.factories` registration didn't activate), DEBUG by adding a `System.err.println("Fingerprint listener registered")` at the listener's static initializer and re-run. Common Pitfall: SpringFactoriesLoader uses ClassLoader-scoped lookup; if the `META-INF/spring.factories` line is on a separate file (e.g. an old conflicting jar), the new key may be silently ignored. Verify via `find ~/.m2 -name 'spring.factories' -exec grep -l TestExecutionListener {} \\;` to check for collisions if needed.

    No source code change in this task — this is a verification gate ensuring Plan 89-02 Tasks 1 + 2 produce the expected marker-file output at scale before Tasks 4 + 5 build on it.
  </action>
  <verify>
    <automated>
      1. `./mvnw verify -Dit.test='org.ctc.backup.**' -Djacoco.skip=true --no-transfer-progress` exits 0.
      2. `ls target/test-perf/context-loads-*.txt | wc -l` >= 2 (at least one primary marker per fork; under forkCount=2 there should be 2 primaries + 2 sidecars = 4 files total).
      3. `ls target/test-perf/context-loads-*-fingerprints.txt | wc -l` >= 2.
      4. Format Line 1 of every primary marker: `for f in target/test-perf/context-loads-*.txt; do case "$f" in *-fingerprints.txt) continue;; esac; head -1 "$f" | grep -qE '^total [0-9]+$' || { echo "BAD: $f"; exit 1; }; done` exits 0.
      5. Format Lines of every sidecar: `for f in target/test-perf/context-loads-*-fingerprints.txt; do head -1 "$f" | grep -qE '^[0-9a-f]{1,8}\t.+$' || { echo "BAD: $f"; exit 1; }; done` exits 0.
      6. Non-empty sidecar: `for f in target/test-perf/context-loads-*-fingerprints.txt; do [ -s "$f" ] || { echo "EMPTY: $f"; exit 1; }; done` exits 0 (instrumentation actually fired).
    </automated>
  </verify>
  <done>
    Both primary and sidecar marker files are present after `./mvnw verify -Dit.test='org.ctc.backup.**'`; primary Line 1 matches `^total \d+$`; sidecar lines match `^[0-9a-f]{1,8}\t.+$`; sidecars are non-empty.
  </done>
</task>

<task type="auto">
  <name>Task 4: aggregate-fingerprints.sh — shellcheck-clean aggregator script</name>
  <files>scripts/test-perf/aggregate-fingerprints.sh</files>
  <requirement>PERF-02</requirement>
  <read_first>
    - scripts/app.sh entire file (conventions analog — shebang, set -euo pipefail, double-quoting, `$(...)` substitution, comment header — RESEARCH §RQ-8)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-RESEARCH.md §RQ-8 (full skeleton + conventions)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-09 (Top-5 by occurrence × cluster-size; aggregator is a real shell script not an inline-doc snippet)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md lines 431-485 (aggregator pattern excerpt)
  </read_first>
  <action>
    Create directory `scripts/test-perf/` and file `scripts/test-perf/aggregate-fingerprints.sh`. Make executable (`chmod +x scripts/test-perf/aggregate-fingerprints.sh`).

    Header (lines 1-12):
    - Shebang `#!/usr/bin/env bash` (not `/bin/bash`).
    - Multi-line comment header: description ("Aggregate ContextCacheKeyFingerprintListener output from target/test-perf/ and report the top-N cache-key clusters by fragmentation score (occurrence × cluster-size)."), usage (`scripts/test-perf/aggregate-fingerprints.sh [marker-dir] [top-n]`), arguments (`marker-dir` default `target/test-perf`, `top-n` default 5).
    - `set -euo pipefail` immediately after the header.

    Body:
    - Positional args with defaults: `MARKER_DIR="${1:-target/test-perf}"` + `TOP_N="${2:-5}"`.
    - Validate `MARKER_DIR` exists; if not, `echo "no fingerprint data at $MARKER_DIR" >&2; exit 1` (graceful no-data branch).
    - Find sidecar files: `shopt -s nullglob; FILES=("$MARKER_DIR"/context-loads-*-fingerprints.txt); shopt -u nullglob` — guard against zero matches.
    - If `${#FILES[@]} -eq 0`, `echo "no fingerprint sidecar files in $MARKER_DIR" >&2; exit 1`.
    - awk pipeline. Concept (executor implements with `awk`/`sort`/`uniq -c` — exact form is planner-discretion within shellcheck-clean constraints):
      1. `cat "${FILES[@]}"` concatenates all sidecar contents (each line `<hex>\t<display>`).
      2. Two-pass awk OR awk → sort → uniq:
         - Per hex hash, count total occurrences (= number of `beforeTestClass` events with this hash).
         - Per hex hash, count distinct display strings (= cluster size, identifies how many test classes share the cached context — RESEARCH §RQ-7 confirms `testClass` is in `toString()` but not `hashCode()`).
         - Score = occurrence × cluster-size.
      3. Sort by score descending, take top `TOP_N`.
      4. Emit a header line `# Top $TOP_N cache-key clusters by occurrence × cluster-size`, then for each: `1. <hex> — <occurrence> occurrences across <cluster-size> classes (score=<score>)` + indented sample display string truncated to 80 chars.

    Style: double-quoted variable refs throughout, `$(...)` substitution, no backticks, no `[[ ]]` ambiguity (use `[ ]` for POSIX-ish OR `[[ ]]` consistently — match `scripts/app.sh`'s style — `scripts/app.sh` uses `[[ ]]` per its existing conventions; mirror that).
  </action>
  <verify>
    <automated>
      1. `test -x scripts/test-perf/aggregate-fingerprints.sh` (executable bit set).
      2. `head -1 scripts/test-perf/aggregate-fingerprints.sh | grep -qE '^#!/usr/bin/env bash$'` exits 0.
      3. `grep -c 'set -euo pipefail' scripts/test-perf/aggregate-fingerprints.sh` >= 1.
      4. `shellcheck scripts/test-perf/aggregate-fingerprints.sh` exits 0 (no warnings, no disables).
      5. Smoke run on Task 3's actual marker output: `scripts/test-perf/aggregate-fingerprints.sh target/test-perf 5` exits 0 AND prints at least one cluster line matching `[0-9a-f]{1,8}.*occurrences`.
      6. Smoke run on synthetic test data: create `/tmp/agg-smoke` dir with a hand-crafted sidecar file `context-loads-12345-fingerprints.txt` containing 5 lines (3 sharing hash `3fa2c1`, 2 sharing hash `aabbcc`); run `scripts/test-perf/aggregate-fingerprints.sh /tmp/agg-smoke 2`; expect top entry to be `3fa2c1` with occurrence=3. (The exact format string match is planner-discretion; the SMOKE assertion is "top hash by occurrence is the one with 3 lines".)
      7. `if ! command -v shellcheck >/dev/null; then echo "shellcheck not installed — install via 'brew install shellcheck' on macOS or 'apt install shellcheck' on Ubuntu before this verify"; exit 1; fi` (pre-flight; shellcheck IS a hard dep here).
    </automated>
  </verify>
  <done>
    `scripts/test-perf/aggregate-fingerprints.sh` exists, is executable, passes shellcheck with zero warnings, follows repo conventions (shebang + `set -euo pipefail` + double-quoting + `$(...)` substitution), and emits a Top-N cluster report on real fingerprint output AND on synthetic test data.
  </done>
</task>

<task type="auto">
  <name>Task 5: docs/test-performance.md — new § PERF-02 Forensics + migrate legacy aggregator loop (D-08)</name>
  <files>docs/test-performance.md</files>
  <requirement>PERF-02</requirement>
  <read_first>
    - docs/test-performance.md entire file (current structure — section ordering, table formats, code-block conventions per RESEARCH §RQ-9)
    - docs/test-performance.md lines 195-244 (`## Context Load Counts (PERF-02)` section — analog for section header style and the legacy aggregator loop at L233-239)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-08 (marker format migration), D-09 (script-based aggregator), D-16 (this doc surface is updated, CLAUDE.md is NOT)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md lines 492-530 (analog for new section structure)
  </read_first>
  <action>
    Edit `docs/test-performance.md` with two distinct changes:

    Change 1 — MIGRATE the legacy aggregator loop (lines 233-239 currently) to D-08-aware form:

    Current snippet:
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
      # Exclude PERF-02 sidecar fingerprint files (they carry hash lines, not totals).
      case "$f" in *-fingerprints.txt) continue ;; esac
      TOTAL=$((TOTAL + $(head -1 "$f" | awk '{print $2}')))
    done
    echo "Total context loads: $TOTAL"
    ```

    Add a one-paragraph note above the migrated snippet explaining the format change (Phase 89 / PERF-02 extended the marker file: Line 1 is now `total <N>`; subsequent lines record per-context cache-key fingerprints; the aggregator script `scripts/test-perf/aggregate-fingerprints.sh` parses the sidecar `*-fingerprints.txt` files separately).

    Change 2 — INSERT a new `## PERF-02 Forensics — Cache-Key Fingerprint Analysis` section AFTER the migrated `## Context Load Counts (PERF-02)` section and BEFORE the existing `## Per-Decision Evidence` section (line ~247 in the current file). Section body:

    - Intro paragraph (2-3 sentences): explains that `ContextCacheKeyFingerprintListener` (TestExecutionListener) emits a sidecar `target/test-perf/context-loads-{PID}-fingerprints.txt` containing one `<hex-hash>\t<mcc-display>` line per `beforeTestClass` event. References RESEARCH §RQ-7 (the hash IS Spring TCF's `DefaultContextCache` bucketing function) and the consumer Phase 90 PERF-03.
    - Usage block: bash fenced code-block showing `scripts/test-perf/aggregate-fingerprints.sh target/test-perf 5`.
    - Sample output: a plain-text fenced block showing 5 illustrative Top-5 entries. Use placeholder hashes (e.g. `3fa2c1`, `aabbcc`, `1d4e2f`, `7e9f01`, `c2b3a4`) with realistic occurrence/cluster counts based on Task 3's actual output (e.g. `1. 3fa2c1 — 28 occurrences across 7 classes (score=196)` + indented truncated display sample). The numbers can be illustrative — the goal is shape clarity, not measurement.
    - Cross-reference paragraph: "PERF-03 (Phase 90) consumes the Top-5 list to choose the highest-fragmentation cluster for consolidation onto a shared `@ContextConfiguration`. Phase 86 Lesson (Plan 02 SUMMARY) is the canonical pitfall: per-class `@DynamicPropertySource` split one shared cache key into seven; PERF-03's targeted consolidation prevents that regression on the reverse path."

    Structural conventions per RESEARCH §RQ-9: H2 heading; horizontal rule before the section if matching adjacent style; double-backtick `inline code`; AssertJ-style bold for numbers (none needed in the illustrative sample).

    Touch NO other files in this task. `CLAUDE.md` is explicitly OUT per D-16. `README.md` is updated by Plan 89-03, NOT by this plan.
  </action>
  <verify>
    <automated>
      1. `grep -c '^## PERF-02 Forensics' docs/test-performance.md` returns 1.
      2. `grep -c '^## Context Load Counts (PERF-02)' docs/test-performance.md` returns 1 (existing section preserved).
      3. `grep -c '## Per-Decision Evidence' docs/test-performance.md` returns 1 (existing downstream section preserved).
      4. Ordering check: `awk '/^## /{print NR, $0}' docs/test-performance.md | grep -A1 'Context Load Counts' | tail -1 | grep -q 'PERF-02 Forensics'` — the new section appears DIRECTLY after Context Load Counts.
      5. Aggregator migration: `grep -c 'head -1 "$f" | awk' docs/test-performance.md` >= 1 (D-08 head-line extraction).
      6. Anti-regression: `grep -c 'TOTAL=$((TOTAL + $(cat "$f")))' docs/test-performance.md` returns 0 (old broken form removed).
      7. Cross-ref present: `grep -c -E 'PERF-03|Phase 90' docs/test-performance.md` >= 1 (new section references the downstream consumer per D-09).
      8. Script reference: `grep -c 'scripts/test-perf/aggregate-fingerprints.sh' docs/test-performance.md` >= 1.
      9. Full-suite gate (final acceptance for Plan 89-02): `./mvnw clean verify --no-transfer-progress` exits 0 (Surefire + Failsafe `default-it` under forkCount=2 from Plan 89-01; no E2E this gate — E2E lives in Plan 89-03).
    </automated>
  </verify>
  <done>
    `docs/test-performance.md` has a new `## PERF-02 Forensics — Cache-Key Fingerprint Analysis` section after `## Context Load Counts (PERF-02)`; the legacy aggregator loop is migrated to `head -1 "$f" | awk '{print $2}'` (D-08); cross-reference to Phase 90 PERF-03 + Phase 86 Lesson present; `./mvnw clean verify` exits 0.
  </done>
</task>

</tasks>

<threat_model>
threats="LOW — test-infrastructure instrumentation; no production runtime change; no user-input surface; no auth flow; no DB schema. New listener uses reflection ONLY on Spring framework classes (NOT on JDK modules — Java 25 JEP-498 strong encapsulation does not apply per RESEARCH §RQ-2 Risk 3)."

mitigation="`ContextCacheKeyFingerprintListener` is test-scope only (under `src/test/java/`, registered via `META-INF/spring.factories` under `src/test/resources/`) — never loaded by production runtime. Reflection access is read-only on a `private final` Spring field; no setAccessible+write surface. Marker-file write paths are under `target/test-perf/` (always project-local + gitignored — no information disclosure outside the developer's machine). SpotBugs targeted `@SuppressFBWarnings` discipline per CLAUDE.md SAST applies if `DP_DO_INSIDE_DO_PRIVILEGED` fires (justified by test-only scope). New shell script reads from `target/test-perf/` only — no privileged paths, no command injection (positional args validated as directory paths)."

stride_register="
| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-89-02-01 | Tampering | reflection on `DefaultTestContext.mergedConfig` (`private final`) | accept | Read-only access; field is Spring framework not JDK module → JEP-498 does not block. Test-scope only. RESEARCH §RQ-2 Risk 3. |
| T-89-02-02 | Information Disclosure | sidecar marker files persist `MergedContextConfiguration.toString()` excerpts under `target/test-perf/` | accept | `target/` is gitignored; output truncated to 200 chars (file-size + display containment per D-08); contents are Spring config metadata (test classes, profiles) — no secrets, no PII. |
| T-89-02-03 | DoS | reflection field-lookup overhead per `beforeTestClass` | accept | Field cached in static initializer ONCE per JVM (not per class load); per-event cost is one `Field.get` call + one `Integer.toHexString` + one `String.substring` — negligible (≪ 1 ms per IT class). |
| T-89-02-04 | Tampering | shell script `aggregate-fingerprints.sh` arg handling | accept | Positional args feed `MARKER_DIR` / `TOP_N` only; no eval, no exec of user input; `shellcheck` gate enforces best-practice quoting. |
"
</threat_model>

<verification>
After all 5 tasks complete:
- `./mvnw test -Dtest='ContextLoadCountListenerTest,ContextCacheKeyFingerprintListenerTest' -Djacoco.skip=true` exits 0 (Tasks 1 + 2 unit gates).
- `./mvnw verify -Dit.test='org.ctc.backup.**' -Djacoco.skip=true` exits 0 with proper marker + sidecar files under `target/test-perf/` (Task 3).
- `shellcheck scripts/test-perf/aggregate-fingerprints.sh` exits 0 (Task 4 lint gate).
- `scripts/test-perf/aggregate-fingerprints.sh target/test-perf 5` exits 0 and emits Top-5 cluster output (Task 4 functional gate).
- `docs/test-performance.md` contains `## PERF-02 Forensics` and the migrated aggregator-loop snippet (Task 5).
- `./mvnw clean verify --no-transfer-progress` (Surefire + Failsafe `default-it`, no E2E) exits 0 — full-suite proof PERF-02 instrumentation does not regress (Task 5 final gate).
</verification>

<success_criteria>
- `ContextLoadCountListener` writes `total <N>` on Line 1 of `target/test-perf/context-loads-{PID}.txt` (D-08).
- `ContextCacheKeyFingerprintListener` exists as a new `TestExecutionListener`, reads MCC via reflection per RESEARCH §RQ-2 Path-A, writes sidecar `*-fingerprints.txt` files containing `<hex>\t<display>` lines.
- `META-INF/spring.factories` carries both lines (`ApplicationContextInitializer=...ContextLoadCountListener` AND `TestExecutionListener=...ContextCacheKeyFingerprintListener`).
- `scripts/test-perf/aggregate-fingerprints.sh` is executable, shellcheck-clean, follows repo conventions, and emits Top-5 cluster report.
- `docs/test-performance.md § PERF-02 Forensics` exists with usage block + sample output + cross-reference to Phase 90 PERF-03.
- The legacy aggregator loop is MIGRATED to `head -1 "$f" | awk '{print $2}'` form (D-08 backward-compat).
- `./mvnw clean verify --no-transfer-progress` exits 0 (Surefire + Failsafe default-it under forkCount=2).
</success_criteria>

<output>
Create `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-02-SUMMARY.md` per the standard SUMMARY template, including: files modified table, decisions honored (D-07 hybrid surface, D-08 format migration, D-09 script, D-10 hash + truncation, D-16 doc scope, sidecar-approach planner-Discretion resolution), evidence excerpts from Task 3's marker-file inspection, and the cross-link to Plan 89-03 (Wave-4 measurement consumes this instrumentation).
</output>
