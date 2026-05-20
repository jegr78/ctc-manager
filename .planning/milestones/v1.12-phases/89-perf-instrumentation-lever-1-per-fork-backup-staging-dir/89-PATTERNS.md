# Phase 89: PERF Instrumentation & Lever 1 (Per-Fork Backup-Staging-Dir) - Pattern Map

**Mapped:** 2026-05-19
**Files analyzed:** 10 (5 created, 5 modified)
**Analogs found:** 10 / 10

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java` | test | request-response (Spring context boot + value assertion) | `src/test/java/org/ctc/backup/service/BackupImportServiceIT.java` | exact |
| `src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java` | test | event-driven (ApplicationReadyEvent re-trigger + filesystem assertion) | `src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java` | exact |
| `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java` | infra | instrumentation (TestExecutionListener → marker file I/O) | `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` | role-match |
| `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListenerTest.java` | test | unit (counter/file output assertion) | `src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java` | exact |
| `scripts/test-perf/aggregate-fingerprints.sh` | script | shell pipeline (I/O: read marker files → stdout top-N cluster report) | `scripts/app.sh` | role-match |
| `pom.xml` (Surefire + Failsafe `<systemPropertyVariables>`, `forkCount`, `<properties>`) | build | config (Maven property injection at fork-dispatch time) | `pom.xml` lines 266-309 (existing Surefire/Failsafe block) | exact |
| `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` (shutdown hook format) | infra | instrumentation (marker file write format change) | self (minimal delta: `String.valueOf(count)` → `"total " + count`) | exact |
| `src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java` (marker format assertion) | test | unit | self (add assertion for `"total "` prefix) | exact |
| `src/test/resources/META-INF/spring.factories` (new `TestExecutionListener` line) | config | registration (SpringFactoriesLoader key extension) | self (existing `ApplicationContextInitializer` line) | exact |
| `docs/test-performance.md` + `README.md` | doc | I/O sink (new §§ inserted; existing § v1.12 Forward Path edited) | `docs/test-performance.md` §§ `Post-Optimization Wallclock (Wave 3)` + `Context Load Counts (PERF-02)` | exact |

---

## Pattern Assignments

### Plan 89-01

---

#### `src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java`

**Role:** test | **Data flow:** Spring context boot → `@Value` injection → path-shape assertion

**Analog:** `src/test/java/org/ctc/backup/service/BackupImportServiceIT.java`

**Imports + class annotations pattern** (lines 17-49):
```java
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class BackupImportServiceIT {

    @Value("${app.backup.staging-dir}")
    String stagingDirRaw;

    Path stagingDir;
```

**What to copy:**
- Package declaration `package org.ctc.backup.service;`
- `@SpringBootTest @ActiveProfiles("dev") @TestInstance(TestInstance.Lifecycle.PER_CLASS) @Tag("integration")` class-level annotation stack — exact order
- `import org.springframework.beans.factory.annotation.Value;` for the `@Value("${app.backup.staging-dir}")` injection
- AssertJ static import `import static org.assertj.core.api.Assertions.assertThat;`
- `Path stagingDir;` field (injected as `@Value("${app.backup.staging-dir}") Path stagingDir;` using `Path` type directly, not `String`)
- Given-when-then comment structure in test body

**What to adapt:**
- Field type: use `@Value("${app.backup.staging-dir}") Path stagingDir;` (inject as `Path`, not `String stagingDirRaw`)
- Assertion: `assertThat(stagingDir.getFileName().toString()).matches("backup-staging-fork-\\d+");`
- Fork-number parity assertion: `String forkNum = System.getProperty("surefire.forkNumber"); if (forkNum != null) { assertThat(stagingDir.getFileName().toString()).endsWith("-" + forkNum); }`
- No `@BeforeAll` / `@BeforeEach` needed — single assertion-only test methods
- No `@Autowired` services needed (pure property-value check)

**Plan assignment:** Plan 89-01

---

#### `src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java`

**Role:** test | **Data flow:** event-driven (ApplicationReadyEvent re-trigger + filesystem write/assert)

**Analog:** `src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java`

**Full analog** (all 138 lines — load-bearing structural pattern):
```java
package org.ctc.backup.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupStagingCleanupIT {

    @Autowired ApplicationContext context;
    @Autowired BackupStagingCleanup cleanup;

    @Test
    void givenThreeStaleStagingFiles_whenApplicationReady_thenAllDeletedAndCountLogged(...)
            throws IOException {
        // given
        Files.createDirectories(tempStagingDir);
        Files.write(tempStagingDir.resolve("upload-" + UUID.randomUUID() + ".zip"), ...);

        // when
        context.publishEvent(new ApplicationReadyEvent(
                new SpringApplication(), new String[]{}, (ConfigurableApplicationContext) context, Duration.ZERO));

        // then
        assertThat(...);
    }
}
```

**The key re-trigger pattern** (`BackupStagingCleanupIT.java` lines 79-80):
```java
context.publishEvent(new ApplicationReadyEvent(
        new SpringApplication(), new String[]{}, (ConfigurableApplicationContext) context, Duration.ZERO));
```

**What to copy:**
- Package `org.ctc.backup.service` (required — `BackupStagingCleanup` is package-private)
- `@SpringBootTest @ActiveProfiles("dev") @TestInstance(TestInstance.Lifecycle.PER_CLASS) @Tag("integration")` (add `PER_CLASS` for `@BeforeAll`/`@AfterAll` lifecycle)
- Imports for `ApplicationReadyEvent`, `SpringApplication`, `ConfigurableApplicationContext`, `Duration`
- `@Autowired ApplicationContext context;` + `@Autowired BackupStagingCleanup cleanup;`
- `context.publishEvent(new ApplicationReadyEvent(...))` re-trigger pattern verbatim
- `Files.createDirectories(...)` + `Files.write(...)` fixture seeding pattern
- Given-when-then comment structure

**What to adapt:**
- Remove `@DynamicPropertySource` / `@TempDir` — the new IT uses the per-fork system-property path (`@Value("${app.backup.staging-dir}") Path ownForkDir;`)
- Add `private Path siblingForkDir;` constructed from `ownForkDir.getParent().resolve("backup-staging-fork-99")`
- `@BeforeAll void setUp()`: create `siblingForkDir` via `Files.createDirectories`
- `@AfterAll void tearDown()`: recursively delete `siblingForkDir`
- Test assertion: after sweep, own-fork `upload-*.zip` files removed; sibling-fork dir (`-fork-99`) files completely untouched
- Remove `@ExtendWith(OutputCaptureExtension.class)` and `CapturedOutput` parameters — race IT does not assert log output, only filesystem state

**Plan assignment:** Plan 89-01

---

#### `pom.xml` — Surefire `<systemPropertyVariables>`, Failsafe `default-it` `<forkCount>` + `<systemPropertyVariables>`, `<properties>` default

**Role:** build | **Data flow:** Maven property injection → JVM system property at fork-dispatch time

**Analog:** `pom.xml` lines 264-309 (existing Surefire + Failsafe `default-it` block)

**Existing Surefire block** (lines 264-280):
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <!-- JEP 498 escape: silence Lombok #3959 Permit/Unsafe warnings until upstream fix lands -->
        <argLine>@{argLine} --sun-misc-unsafe-memory-access=allow -javaagent:...</argLine>
        <!-- Conservative process-level parallelism: 2 forked JVMs total (not 2C). -->
        <forkCount>2</forkCount>
        <reuseForks>true</reuseForks>
        <!-- Tag-based test routing ... -->
        <excludedGroups>integration,e2e,flaky</excludedGroups>
    </configuration>
</plugin>
```

**Existing Failsafe `default-it` block** (lines 292-308):
```xml
<execution>
    <id>default-it</id>
    <goals>
        <goal>integration-test</goal>
        <goal>verify</goal>
    </goals>
    <configuration>
        <!-- JEP 498 escape: ... -->
        <argLine>@{argLine} --sun-misc-unsafe-memory-access=allow -javaagent:...</argLine>
        <!-- Tag-based test routing ... -->
        <groups>integration</groups>
        <excludedGroups>e2e,flaky</excludedGroups>
    </configuration>
</execution>
```

**Existing `<properties>` block** (lines 16-24):
```xml
<properties>
    <java.version>25</java.version>
    <playwright.version>1.59.0</playwright.version>
    <lombok.version>1.18.46</lombok.version>
    <testcontainers.version>2.0.5</testcontainers.version>
</properties>
```

**What to copy:**
- `<configuration>` indentation and structure within Surefire plugin block — add `<systemPropertyVariables>` after `<reuseForks>true</reuseForks>` in Surefire block
- Same `<systemPropertyVariables>` block added inside Failsafe `default-it` `<configuration>` after the existing `<excludedGroups>` line
- `<forkCount>` + `<reuseForks>` placement inside Failsafe `<configuration>` mirrors Surefire's lines 271-272

**What to adapt (D-03, D-04, D-05, D-11):**
- Add to `<properties>` block (line 24, before `</properties>`): `<surefire.forkNumber>0</surefire.forkNumber>` — Maven-level fallback for IDE/non-fork invocations
- Add `<systemPropertyVariables>` in **Surefire** `<configuration>`:
  ```xml
  <systemPropertyVariables>
      <app.backup.staging-dir>data/${spring.profiles.active:dev}/backup-staging-fork-${surefire.forkNumber}</app.backup.staging-dir>
  </systemPropertyVariables>
  ```
  Use **literal duplication** — do NOT extract to a `<properties>` entry (RESEARCH RQ-1: Maven would resolve `${surefire.forkNumber}` as a Maven property at POM-parse time, not fork-dispatch time)
- Add identical `<systemPropertyVariables>` block in **Failsafe `default-it`** `<configuration>`
- Add to Failsafe `default-it` `<configuration>`:
  ```xml
  <forkCount>2</forkCount>
  <reuseForks>true</reuseForks>
  ```
  Place before `<groups>integration</groups>` (mirrors Surefire ordering)

**Plan assignment:** Plan 89-01

---

### Plan 89-02

---

#### `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java`

**Role:** infra | **Data flow:** `TestExecutionListener#beforeTestClass` → reflection-accessed `MergedContextConfiguration` → append to PID-keyed marker file at shutdown

**Analog:** `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` (full file, 45 lines)

**Full analog:**
```java
package org.ctc.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class ContextLoadCountListener
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final AtomicInteger count = new AtomicInteger(0);

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                long pid = ProcessHandle.current().pid();
                Path out = Paths.get("target/test-perf/context-loads-" + pid + ".txt");
                Files.createDirectories(out.getParent());
                Files.writeString(out, String.valueOf(count.get()));
            } catch (IOException e) {
                System.err.println("ContextLoadCountListener: could not write marker file: "
                        + e.getMessage());
            }
        }, "ContextLoadCountListener-shutdown"));
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        count.incrementAndGet();
    }

    static int getCount() {
        return count.get();
    }
}
```

**What to copy:**
- `package org.ctc.testsupport;` declaration
- PID-keyed marker file path pattern: `"target/test-perf/context-loads-" + pid + ".txt"`
- Shutdown hook registration: `Runtime.getRuntime().addShutdownHook(new Thread(() -> { ... }, "ContextCacheKeyFingerprintListener-shutdown"))`
- `Files.createDirectories(out.getParent())` before write
- `System.err.println(...)` error reporting in catch block
- `ProcessHandle.current().pid()` PID retrieval

**What to adapt (D-07, D-08, D-10, RESEARCH RQ-2, RQ-3):**
- Interface: `implements org.springframework.test.context.TestExecutionListener` (not `ApplicationContextInitializer`)
- Override method: `public void beforeTestClass(TestContext testContext)` instead of `initialize(...)`
- Accumulator: use `java.util.concurrent.CopyOnWriteArrayList<String>` to collect `"<hex-hash>\t<mcc-display>"` strings (one per `beforeTestClass` call) rather than an `AtomicInteger`
- MCC access via reflection using Spring's own utils (RESEARCH RQ-2 Path-A recommendation):
  ```java
  Field f = ReflectionUtils.findField(DefaultTestContext.class, "mergedConfig");
  ReflectionUtils.makeAccessible(f);
  MergedContextConfiguration mcc = (MergedContextConfiguration) ReflectionUtils.getField(f, testContext);
  ```
  Add `@SuppressFBWarnings({"DP_DO_INSIDE_DO_PRIVILEGED"}, justification="Test-only PERF-02 instrumentation — reflection on Spring private field is intentional")` if SpotBugs fires
- Hash: `Integer.toHexString(mcc.hashCode())`
- Display: `mcc.toString().substring(0, Math.min(200, mcc.toString().length()))` — 200-char truncation
- Shutdown hook writes: the marker file is written by `ContextLoadCountListener`'s hook (which is responsible for Line 1 `"total <N>"` after PERF-02 modification); the fingerprint listener appends its hash lines into the **same file** AFTER the count line. Coordination: fingerprint listener writes to a separate `List<String>` at `beforeTestClass` time; at shutdown, it opens the marker file in APPEND mode and writes one `<hex-hash>\t<mcc-display>` line per entry. The two shutdown hooks share the same filename but execute sequentially (JVM shutdown hook ordering within a single thread is undefined — use a single hook that produces both the `total` line and all hash lines, OR: write to a sidecar file `context-loads-{PID}-fingerprints.txt` and let the aggregator script combine both. **Planner decision: sidecar file approach is simpler — avoids hook ordering race.**) If the planner picks the sidecar approach, the aggregator script reads the sidecar file; `ContextLoadCountListener` stays unchanged except for the `total <N>` format migration.
- Required imports: `org.springframework.test.context.TestContext`, `org.springframework.test.context.support.DefaultTestContext`, `org.springframework.test.context.MergedContextConfiguration`, `org.springframework.util.ReflectionUtils`, `java.lang.reflect.Field`, `java.util.concurrent.CopyOnWriteArrayList`

**Plan assignment:** Plan 89-02

---

#### `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListenerTest.java`

**Role:** test | **Data flow:** unit (listener method call → assert side-effect output)

**Analog:** `src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java` (full file, 22 lines)

**Full analog:**
```java
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
}
```

**What to copy:**
- `package org.ctc.testsupport;` declaration
- No Spring annotations (`@SpringBootTest`, `@Tag`) — plain JUnit 5 unit test (no `@Tag` on plain unit tests per CLAUDE.md)
- `import static org.assertj.core.api.Assertions.assertThat;` only AssertJ import
- `import org.junit.jupiter.api.Test;` only JUnit import
- `var listener = new ContextCacheKeyFingerprintListener();` instantiation pattern
- Given-when-then structure
- Package-private helper accessor pattern (`static int getCount()` → `static List<String> getRecordedLines()` or similar)

**What to adapt:**
- Test method names follow the `whenAction_thenResult` pattern (no precondition needed for simple tests per CLAUDE.md)
- Test behavior: call `listener.beforeTestClass(mockTestContext)` with a mocked or minimal `TestContext`; assert the returned/accumulated hash line matches `"[0-9a-f]{1,8}\t.*"` regex
- If the listener writes to a temporary file in tests, use `@TempDir` + `@BeforeEach` to redirect output path for test isolation
- Mock `TestContext` using Mockito for the `beforeTestClass` parameter, or create a minimal stub

**Plan assignment:** Plan 89-02

---

#### `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` (modification — shutdown hook format)

**Role:** infra (modification) | **Data flow:** same PID-keyed file write, format change only

**Analog:** self (current line 29)

**Current line 29:**
```java
Files.writeString(out, String.valueOf(count.get()));
```

**What to adapt (D-08, RESEARCH Risk 4):**
- Change line 29 to: `Files.writeString(out, "total " + count.get());`
- No other change needed — `getCount()` static accessor stays, `initialize()` stays

**Plan assignment:** Plan 89-02

---

#### `src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java` (modification — marker format assertion)

**Role:** test (modification) | **Data flow:** unit assertion update

**Current assertion (lines 19-20):**
```java
assertThat(ContextLoadCountListener.getCount()).isEqualTo(before + 2);
```

**What to adapt:**
- The existing test asserts the in-memory counter — no change needed there
- Add a second test method that verifies the shutdown hook would write `"total <N>"` format:
  - Arrange: create a temp output path
  - Act: invoke the shutdown hook logic directly (extract to a package-private method `writeMarkerFile(Path out)`) OR simply assert that `"total " + count` produces the expected prefix string
  - Assert: file content starts with `"total "` followed by a digit

**Plan assignment:** Plan 89-02

---

#### `src/test/resources/META-INF/spring.factories` (new `TestExecutionListener` line)

**Role:** config | **Data flow:** SpringFactoriesLoader registration

**Analog:** self — current content (line 1):
```
org.springframework.context.ApplicationContextInitializer=org.ctc.testsupport.ContextLoadCountListener
```

**What to copy:**
- Exact key format for a new `TestExecutionListener` entry (RESEARCH RQ-3 confirmed key is `org.springframework.test.context.TestExecutionListener`)

**What to adapt (D-07, RESEARCH RQ-3):**
- Add a second line:
  ```
  org.springframework.test.context.TestExecutionListener=org.ctc.testsupport.ContextCacheKeyFingerprintListener
  ```
- File remains 2 lines total; no comma-separated multi-value needed since only one listener is registered per key in this project's file

**Plan assignment:** Plan 89-02

---

#### `scripts/test-perf/aggregate-fingerprints.sh`

**Role:** script | **Data flow:** shell pipeline (read `target/test-perf/context-loads-{PID}*.txt` files → group by hex hash → sort by score → stdout top-N)

**Analog:** `scripts/app.sh` (lines 1-15)

**Shebang + safety header pattern** (lines 1-10 of `scripts/app.sh`):
```bash
#!/usr/bin/env bash
# Start/stop the CTC Manager application.
# Usage:
#   ./scripts/app.sh start [profile]   — start with given profile (default: dev)
#   ./scripts/app.sh stop              — stop the running instance
#   ./scripts/app.sh status            — check if running
#
# Profiles: dev, dev,demo, local

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
```

**Variable and double-quoting conventions** (any block in `scripts/app.sh`):
```bash
local profile="${1:-dev}"
local pid=""
echo "Starting CTC Manager with profile '$profile'..."
```

**What to copy:**
- Shebang: `#!/usr/bin/env bash` (not `/bin/bash`)
- `set -euo pipefail` immediately after shebang/comment block
- Multi-line comment header block with `# Description` + `# Usage:` + `# Arguments:` lines
- `"${1:-default}"` pattern for optional positional argument with default
- Double-quoted variable references throughout: `"$var"`, `"$(cmd)"`
- `$(...)` command substitution (not backticks)

**What to adapt (D-09, RESEARCH RQ-8):**
- Script is in `scripts/test-perf/` subdirectory (does not yet exist — task includes `mkdir -p scripts/test-perf/`)
- No `SCRIPT_DIR` / `PROJECT_DIR` subshell (script reads from `target/test-perf/` relative to CWD, i.e., project root)
- Two optional positional arguments: `MARKER_DIR="${1:-target/test-perf}"` and `TOP_N="${2:-5}"`
- Core pipeline logic:
  ```bash
  # For each marker file: skip Line 1 (total count); parse remaining lines as "<hex>\t<display>"
  # Group by hex hash; count occurrences per hash; count distinct display strings per hash (cluster size)
  # Sort by occurrence * cluster_size descending; print top TOP_N
  ```
  Implementation: multi-pass `awk` over `tail -n +2 "$f"` output piped through `sort | uniq -c | awk` pipeline
- Must be `chmod +x` (executable bit) — document in Plan task
- Must pass `shellcheck scripts/test-perf/aggregate-fingerprints.sh` — no shellcheck disables
- RESEARCH RQ-8 note: the aggregator loop in `docs/test-performance.md` L233-239 is migrated to use `head -1 "$f" | awk '{print $2}'` extraction (Plan 89-02 task, doc update)

**Plan assignment:** Plan 89-02

---

### Plan 89-03

---

#### `docs/test-performance.md` (three modifications)

**Role:** doc | **Data flow:** human-readable measurement record (append-only sections)

**Analog A — new `§ PERF-02 Forensics` section:**
Closest analog: `docs/test-performance.md` `## Context Load Counts (PERF-02)` section (lines 195-244)

**Section structure to mirror** (lines 195-244):
```markdown
## Context Load Counts (PERF-02)

| Measurement Point        | Context Loads | Run Command                              |
| --- | --- | --- |

**Delta interpretation...** prose paragraph

PID-keyed marker files are emitted by ...

```bash
TOTAL=0
for f in target/test-perf/context-loads-*.txt; do
  TOTAL=$((TOTAL + $(cat "$f")))
done
echo "Total context loads: $TOTAL"
```
```

**What to copy:**
- H2 heading format: `## PERF-02 Forensics — Cache-Key Fingerprint Analysis`
- `\`\`\`bash` fenced code block for usage example (per RQ-9 conventions)
- `---` horizontal rule before and after major sections
- Bold key numbers pattern: `**Median: ...**`

**What to adapt (D-09, RESEARCH RQ-9):**
- Insert after `## Context Load Counts (PERF-02)` section (after line ~244) and before `## Per-Decision Evidence`
- Content: aggregator usage block (`scripts/test-perf/aggregate-fingerprints.sh [target/test-perf] [5]`) + example Top-5 output (plain list format: `1. <hex-hash> — N occurrences across M classes`)
- Migration note: update the existing aggregator loop (lines 233-239) to use `head -1 "$f" | awk '{print $2}'` instead of `cat "$f"` (D-08 format change)

**Analog B — new `§ Post-Optimization Wallclock (Wave 4)` section:**
Closest analog: `docs/test-performance.md` `## Post-Optimization Wallclock (Wave 3)` section (lines 115-140)

**Five-column table to replicate** (lines 122-129):
```markdown
## Post-Optimization Wallclock (Wave 3)

Local 3-run post-audit measured on the same hardware as the Plan-01 baseline,
identical command and same idle-system protocol. Branch
`gsd/v1.11-tooling-and-cleanup` after Wave-2 commits ...

| Run | Maven Total time | bash `real`     | Context loads | Notes                    |
| --- | ---------------- | --------------- | ------------- | ------------------------ |
| 1   | 09:24            | 565s (9m 26s)   | 79            | BUILD SUCCESS, no flakes |
| 2   | 10:24            | 625s (10m 25s)  | 79            | BUILD SUCCESS, no flakes |
| 3   | 10:30            | 631s (10m 31s)  | 78            | BUILD SUCCESS, no flakes |

**Median (Maven): 10:24 (run 2).** **Median (bash real): 625s = 10m 25s.**
```

**What to copy:**
- H2 heading, intro sentence pattern (branch name + commit SHAs), five-column table header/separator, bold Median line format
- Verdict pattern: `**Achieved X% reduction** (Wave-4 median vs 10:24 Phase-86-post-audit baseline)`

**What to adapt (D-02, D-16):**
- Insert after `## Post-Optimization Wallclock (Wave 3)` section (after line ~140), before `## CI Results (PERF-05)`
- Branch: `gsd/v1.12-driver-import-and-test-perf`; 3 local runs with actual measured values (filled in during Plan 89-03 execution)
- Delta vs. **10:24** Phase-86-post-audit baseline (not the 09:45 pre-audit; honest reporting per D-02)
- D-02: no hard local reduction gate — record actual delta, positive or negative
- JaCoCo ≥ 88.88 % statement in Notes column or below table

**Analog C — `§ v1.12 Forward Path` Lever-1 update:**
Closest analog: `docs/test-performance.md` `## v1.12 Forward Path` table (lines 329-350)

**Table row to modify** (line 340, Lever 1 row):
```markdown
| **1. Per-fork `data/dev/backup-staging/` refactor** ... | ~60-90s | M | ... | `src/main/java/...`, ... |
```

**What to adapt:**
- Add `DONE (Phase 89)` to the `Required Touchpoints` column or as a new "Status" column
- Add a note below the table: `§ Post-Optimization Wallclock (Wave 4)` measured delta reference; `§ PERF-02 Forensics` fingerprint data feeds Phase 90 PERF-03

**Plan assignment:** Plan 89-03

---

#### `README.md` (Test-Performance section pointer update)

**Role:** doc | **Data flow:** link update

**Analog:** `README.md` existing content (no current test-performance section exists — grep confirmed no match for "Performance" in README.md)

**What to create/adapt (D-16):**
- README.md currently has no "Test Performance" section — Plan 89-03 adds a minimal pointer paragraph after `## Documentation` (line 148)
- Pattern to match: existing `## Documentation` section format (line 148-150)
- Content: one or two sentences pointing to `docs/test-performance.md` and the Wave-4 median figure
- No table, no code block — plain text link consistent with the adjacent `## Documentation` paragraph style

**Plan assignment:** Plan 89-03

---

## Shared Patterns

### `@SpringBootTest` + `@Tag("integration")` IT annotation stack

**Source:** `src/test/java/org/ctc/backup/service/BackupImportServiceIT.java` lines 45-49
**Apply to:** `BackupStagingDirPerForkIT`, `BackupStagingCleanupRaceIT`
```java
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class ...
```

### `@Value("${app.backup.staging-dir}")` injection

**Source:** `src/test/java/org/ctc/backup/service/BackupImportServiceIT.java` lines 60-61
**Apply to:** `BackupStagingDirPerForkIT` (as `Path` type), `BackupStagingCleanupRaceIT` (as `Path` type)
```java
@Value("${app.backup.staging-dir}")
String stagingDirRaw;   // new ITs use Path directly instead of String
```

### `ApplicationReadyEvent` re-trigger

**Source:** `src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java` lines 79-80
**Apply to:** `BackupStagingCleanupRaceIT`
```java
context.publishEvent(new ApplicationReadyEvent(
        new SpringApplication(), new String[]{}, (ConfigurableApplicationContext) context, Duration.ZERO));
```

### PID-keyed marker file path

**Source:** `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` lines 26-29
**Apply to:** `ContextCacheKeyFingerprintListener` (same path `"target/test-perf/context-loads-" + pid + ".txt"` or sidecar `"...-fingerprints.txt"`)
```java
long pid = ProcessHandle.current().pid();
Path out = Paths.get("target/test-perf/context-loads-" + pid + ".txt");
Files.createDirectories(out.getParent());
```

### Shutdown hook registration

**Source:** `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` lines 23-35
**Apply to:** `ContextCacheKeyFingerprintListener`
```java
static {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        try {
            // ... write logic ...
        } catch (IOException e) {
            System.err.println("ContextCacheKeyFingerprintListener: could not write marker file: "
                    + e.getMessage());
        }
    }, "ContextCacheKeyFingerprintListener-shutdown"));
}
```

### Shell script shebang + error handling

**Source:** `scripts/app.sh` lines 1-10
**Apply to:** `scripts/test-perf/aggregate-fingerprints.sh`
```bash
#!/usr/bin/env bash
# <description>
set -euo pipefail
```

---

## No Analog Found

All 10 files have analogs. No entries in this section.

---

## Metadata

**Analog search scope:** `src/test/java/org/ctc/backup/service/`, `src/test/java/org/ctc/testsupport/`, `scripts/`, `docs/`, `pom.xml`, `src/test/resources/META-INF/`
**Files scanned:** 9 source files read directly + pom.xml targeted sections
**Pattern extraction date:** 2026-05-19
