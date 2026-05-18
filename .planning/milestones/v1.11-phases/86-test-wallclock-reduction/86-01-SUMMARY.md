---
phase: 86-test-wallclock-reduction
plan: 01
subsystem: testing
tags: [spring-test, applicationcontextinitializer, surefire, failsafe, wallclock, baseline, perf-02, perf-04]

requires:
  - phase: 79-test-suite-stabilization
    provides: Phase-79 wallclock reference (11m 11s) + @Tag-driven Surefire/Failsafe routing
  - phase: 81-static-analysis-gate-spotbugs-find-sec-bugs
    provides: Pre-existing Playwright captureScreenshot flake documentation (81-03 SUMMARY)
provides:
  - ContextLoadCountListener (PERF-02 instrumentation, PID-keyed for forkCount=2 safety)
  - src/test/resources/META-INF/spring.factories (test-scope listener registration)
  - docs/test-performance.md (Phase-86 deliverable skeleton; Baseline section populated)
  - Phase-86 local re-baseline: 09:45 median Maven / 9m 46s bash / 81 context loads
affects: [86-02-sitegen-DirtiesContext-cluster, 86-03-DataJpaTest-pilot, 86-04-post-audit-wallclock, 86-05-docs-finalization, 86-06-CI-median-harvest]

tech-stack:
  added: []   # No new packages — RESEARCH §"Package Legitimacy Audit: N/A"
  patterns:
    - "ContextLoadCountListener: ApplicationContextInitializer + JVM shutdown hook + PID-keyed marker file (forkCount-safe pattern for Surefire forkCount=2 / Failsafe forkCount=1C)"
    - "test-scope spring.factories registration (no production-classpath impact)"
    - "logs-outside-target measurement discipline: `mvn clean` wipes target/ mid-run, so timing logs must live in .test-perf-logs/ (gitignored)"

key-files:
  created:
    - src/test/java/org/ctc/testsupport/ContextLoadCountListener.java
    - src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java
    - src/test/resources/META-INF/spring.factories
    - docs/test-performance.md
  modified:
    - .gitignore  (added .test-perf-logs/)

key-decisions:
  - "Used logs-outside-target measurement discipline (.test-perf-logs/) because `mvn clean` wipes target/ before the time-redirected log file can be closed — discovered empirically during Run 1 attempt 1"
  - "Pre-existing Playwright captureScreenshot flake (documented in 81-03 SUMMARY) was retried per Plan §Task 2 protocol — not treated as regression"
  - "v1.10 11m 11s reference is now historical only: Phase-86 re-baseline shows ~13% faster local suite; the 30%-reduction gate per D-11 remains anchored on CI median, not local"

patterns-established:
  - "PID-keyed marker files: ProcessHandle.current().pid() in shutdown hook prevents fork-clobber under forkCount=2"
  - "Context-load aggregation: shell loop over target/test-perf/context-loads-*.txt; paste -sd+ -|bc DOES NOT work because Files.writeString writes without trailing newline (concatenates digit strings)"
  - "Static AtomicInteger state in tests: assert via delta (before/after), not absolute value, so result is deterministic regardless of suite ordering"

requirements-completed:
  - PERF-02
  - PERF-04

duration: ~70min
completed: 2026-05-17
---

# Phase 86 Plan 01: Wave-1 Baseline + ContextLoadCountListener Summary

**ContextLoadCountListener (PID-keyed, test-scope) wired via `src/test/resources/META-INF/spring.factories`; Phase-86 local baseline established at Maven median 09:45 / 9m 46s bash / 81 context loads across 3 successful `./mvnw clean verify -Pe2e` runs.**

## Performance

- **Duration:** ~70 min (Task 1 listener + 3 baseline runs + 1 retried Playwright flake + Task 3 docs)
- **Started:** 2026-05-17T18:10:20Z (Run-1 attempt 1)
- **Completed:** 2026-05-17T18:57:32Z
- **Tasks:** 3 (Task 1 auto-tdd, Task 2 baseline measurement, Task 3 docs)
- **Files modified:** 5 created + 1 modified (.gitignore)

## Accomplishments

- **PERF-02 instrumentation live.** `ContextLoadCountListener` is registered via test-scope `spring.factories`, increments an AtomicInteger on every `ApplicationContextInitializer.initialize(...)` call, and writes `target/test-perf/context-loads-{PID}.txt` per JVM at shutdown. Verified across 5 forks (Surefire forkCount=2 + Failsafe default-it + Failsafe e2e-it + 1 short-lived helper) — all PID files produced, no clobber.
- **Phase-86 local re-baseline established (D-09).** 3 successful runs → Maven median **09:45**, bash median **586s (9m 46s)**, context-load total stable at **81** across all 3 runs. Documented in `docs/test-performance.md` §Baseline.
- **`docs/test-performance.md` skeleton in place.** All 5 required level-2 headers (Baseline, Post-Optimization Wallclock, Context Load Counts, Per-Decision Evidence, v1.12 Forward Path) — Baseline section fully populated; remaining sections stubbed for Plans 02-05 with v1.12 lever names pre-filled from D-14 + 86-CONTEXT §Deferred Ideas.

## Task Commits

1. **Task 1 (TDD auto): ContextLoadCountListener + test + spring.factories** — `94afa07d` (feat)
   Combined RED+GREEN into a single commit because the listener compiles only with the spring.factories on the test classpath and the test exercises only the listener — they form an atomic deliverable. RED phase verified separately by writing the test first and observing a `cannot find symbol: class ContextLoadCountListener` compile failure before adding the listener.
2. **Task 2 (checkpoint:human-verify): Baseline 3-run measurement** — no commit (measurement-only checkpoint). Results captured in Task 3.
3. **Task 3 (auto): docs/test-performance.md + .gitignore** — `5b983510` (docs)

_(No Plan-metadata commit for STATE.md / ROADMAP.md — worktree mode; orchestrator updates those centrally after the wave merges.)_

## Baseline (D-09)

| Run | Maven Total time | bash `real`     | Context loads | Notes                                                                                                                                  |
| --- | ---------------- | --------------- | ------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | 09:01            | 543s (9m 03s)   | 81            | BUILD SUCCESS                                                                                                                          |
| 2   | 09:45            | 586s (9m 46s)   | 81            | BUILD SUCCESS — retried after pre-existing Playwright `Page.captureScreenshot` flake on first attempt (documented in 81-03 SUMMARY)    |
| 3   | 09:53            | 595s (9m 55s)   | 81            | BUILD SUCCESS                                                                                                                          |

**Median (Maven): 09:45 (run 2).** **Median (bash real): 586s = 9m 46s.** **Context loads (stable): 81.**

### Per-fork context-load breakdown (reproducible across runs)

| Fork purpose                                  | Count  | Notes                                                                                          |
| --------------------------------------------- | ------ | ---------------------------------------------------------------------------------------------- |
| Surefire fork A (unit, forkCount=2)           | 7-9    | Bootstrap + unit ITs that bring up Spring (db.migration, etc.)                                 |
| Surefire fork B (unit, forkCount=2)           | 15-17  | Other unit partition                                                                            |
| Failsafe `default-it` fork (forkCount=1C)     | 27     | `@SpringBootTest`-driven IT cluster — main `@DirtiesContext` cost center for Wave-2            |
| Failsafe `e2e-it` fork (`-Pe2e`)              | 27     | `@SpringBootTest(RANDOM_PORT)` Playwright walkthroughs                                          |
| Short-lived helper fork                       | 3      | Spring-Boot test resolver 3-context bootstrap; persists ≤1s                                    |
| **Total**                                     | **81** | Independent of run-to-run variance                                                              |

The 27 + 27 = 54 contexts inside the Failsafe forks are the primary Wave-2 target.

## Confirmation: ContextLoadCountListener Registration is Test-Scope Only

- `src/test/resources/META-INF/spring.factories` lives under **`src/test/resources/`**, not `src/main/resources/`. Maven `resources:3.3.1:testResources` copies it to `target/test-classes/META-INF/spring.factories`; it is **NOT** copied to `target/classes/`. Verified by `find target -name spring.factories` after `./mvnw test` — only the test-classes path exists.
- Production classpath is unaffected: `mvn package`-built JAR (`target/ctc-manager-1.11.0-SNAPSHOT.jar`) does not contain the listener or the spring.factories entry (test scope by directory placement).

## Files Created/Modified

- `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` (created) — `ApplicationContextInitializer` with `AtomicInteger count` + PID-keyed JVM shutdown hook writing `target/test-perf/context-loads-{PID}.txt`. Single class-level Javadoc names PERF-02 purpose; no in-body comments per `feedback_no_unnecessary_comments`. Package-private `getCount()` accessor for the unit test.
- `src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java` (created) — Unit test verifying `initialize(null)` calls bump `getCount()` by the expected delta. No `@Tag` (Surefire `default-test`). BDD method name `whenInitializeCalledTwice_thenCountIncrementsByTwo`.
- `src/test/resources/META-INF/spring.factories` (created) — Single registration line `org.springframework.context.ApplicationContextInitializer=org.ctc.testsupport.ContextLoadCountListener`.
- `docs/test-performance.md` (created) — Phase-86 deliverable doc: Baseline populated, Post-Optimization Wallclock/Context Load Counts (post)/Per-Decision Evidence stubbed for Plans 02-05, v1.12 Forward Path stubbed with the 3 lever names pre-filled (D-14 + CONTEXT §Deferred Ideas).
- `.gitignore` (modified) — Added `.test-perf-logs/` to keep local timing logs out of git (mirror of `target/test-perf/`).

## Decisions Made

- **Logs outside `target/`.** The Plan §Task 2 template `tee target/test-perf/baseline-run-$i.log` was empirically wrong because `mvn clean` is the FIRST goal in `clean verify -Pe2e`. The clean removed the redirected log file before Maven finished writing to it (no captured `Total time` for the first Run-1 attempt). All subsequent runs redirected to `.test-perf-logs/` (gitignored, sibling to `target/`). This is documented in the SUMMARY for Plan 04 (post-optimization) and Plan 05 (CI harvest) to copy.
- **Playwright flake retry per Plan protocol.** The first Run-2 attempt failed at 5:21 inside Surefire with `TeamProfilePageGeneratorTest.setUp:77 » Playwright Error … Page.captureScreenshot: Unable to capture screenshot`. This is the same intermittent Playwright Chromium driver flake explicitly documented in `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-03-SUMMARY.md` (lines 41, 125). Per Plan 86-01 §Task 2 protocol — *"If any run fails for unrelated reasons (flake, environment), repeat — D-09 explicitly requires 3 successful baseline runs, not 3 attempts"* — the run was retried and succeeded. Per CLAUDE.md memory `feedback_no_flaky_dismissal`, this is NOT a regression because the same flake has a verified history in the codebase prior to my Wave-1 changes.
- **Combined RED+GREEN into single commit for Task 1.** Plan tagged `tdd="true"`, but the listener compiles only with the test-scope spring.factories on classpath and the test exercises only the listener. RED was verified separately (test file written first, compile-fail observed: `cannot find symbol: class ContextLoadCountListener`) before adding the listener and spring.factories. The final atomic state (listener + test + factories) is a single coherent unit — committing the test alone would leave the repo in a broken state.
- **`paste -sd+ - | bc` does NOT correctly sum context-load files.** `Files.writeString` writes without a trailing newline, so `paste` concatenates digit strings instead of summing them ("9+15+27+27+3" would have been right; "81527273" is what actually happens). Switched to a shell `for f in ...; TOTAL=$((TOTAL+$(cat $f)))` loop. Documented in `docs/test-performance.md` §Context Load Counts.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Baseline log files wiped by `mvn clean`**
- **Found during:** Task 2 (Run-1 first attempt)
- **Issue:** Plan §Task 2 template redirected baseline logs to `target/test-perf/baseline-run-$i.log`. The first goal of `./mvnw clean verify -Pe2e` is the `clean` lifecycle phase, which deletes the entire `target/` directory mid-process — wiping the still-open log file. The Run-1 first attempt ran to completion (Surefire+Failsafe artifacts found in `target/`) but had no captured `Total time`.
- **Fix:** Switched all 3 baseline run redirections to `.test-perf-logs/baseline-run-$i.log` (created at the repo root, sibling to `target/`, gitignored).
- **Files modified:** `.gitignore` (added `.test-perf-logs/`); the file path used in the bash measurement script (one-off, not committed).
- **Verification:** Re-ran Run 1 to Run 3 — all 3 produced parseable `Total time` and bash `real` output. Documented the discipline in `docs/test-performance.md` §Baseline so Plans 04 and 06 do not repeat the mistake.
- **Committed in:** `5b983510` (the .gitignore change + the documentation of the fix in docs/test-performance.md). The actual measurement script is ad-hoc and not part of the committed artifact set.

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary to obtain the D-09 baseline numbers; no scope creep — the documentation fix is also captured in `docs/test-performance.md` for future plan use.

## Issues Encountered

- **Pre-existing Playwright flake on Run-2 attempt 1** — handled per Plan §Task 2 protocol (retry), not treated as a regression. See "Decisions Made" above.
- **`paste -sd+ -|bc` aggregation bug** — discovered when the post-Run-1 sum showed "81527273" instead of 81. Files have no trailing newlines, so `paste` concatenates. Switched to shell loop. Documented in `docs/test-performance.md` so Plans 04-06 don't repeat the mistake.

## Threat Surface Scan

No new threat surface introduced. The Listener writes to `target/test-perf/` (gitignored, ephemeral build output, no PII) — already captured in Plan 86-01 §Threat Model as T-86-01 (accept). `spring.factories` is on the test classpath only; production classpath unaffected. No new packages, no new endpoints, no schema changes.

## User Setup Required

None — Phase 86 is pure test infrastructure. No external service configuration; no auth gates; no manual data entry.

## Wave-2 Readiness

- **Measuring instrument live.** Plans 02 (`@DirtiesContext` cluster removals) and 03 (`@DataJpaTest` conversions) can rely on `target/test-perf/context-loads-*.txt` for pre/post deltas per change.
- **Baseline numbers committed.** The 09:45 Maven / 586s bash / 81 context-load reference in `docs/test-performance.md` is what Wave-3 (Plan 04 post-audit) and Wave-4 (Plan 06 CI median) will compare against.
- **Deliverable doc skeleton ready.** All 5 required headers in place; Plans 02-05 append into the appropriate sections without rewriting the skeleton.
- **No Wave-1 blockers.** No further setup required before Plan 02 begins the sitegen `@DirtiesContext` cluster work.

## Unexpected Findings

- **Phase-86 local re-baseline is ~13% FASTER than the v1.10 11m 11s reference**, not slower. Phases 80-85 (SpotBugs/find-sec-bugs gate, CodeQL compile, Renovate config, OpenRewrite recipe runs) did not lengthen the local Surefire+Failsafe suite. CodeQL runs only on CI; OpenRewrite is profile-only (`-Prewrite`); SpotBugs adds a few seconds at the `verify` boundary but does not run tests. The Phase-86 ≤7m 50s goal must therefore be evaluated against either (a) the v1.10 reference (gate per D-11 — CI is source of truth) or (b) the new local 09:45 baseline (≥30% from there ≈ ≤6m 49s). D-09 + D-11 in `86-CONTEXT.md` already address this — CI is the verdict, local is direction sense.

## Self-Check

**Files (created):**
- `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` — FOUND
- `src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java` — FOUND
- `src/test/resources/META-INF/spring.factories` — FOUND
- `docs/test-performance.md` — FOUND (8180 bytes)
- `.planning/phases/86-test-wallclock-reduction/86-01-SUMMARY.md` — FOUND (this file)

**Commits:**
- `94afa07d` (feat 86-01) — FOUND
- `5b983510` (docs 86-01) — FOUND

**Acceptance criteria (Plan §Task 1):**
- `grep -c "implements ApplicationContextInitializer"` → 1 (PASS)
- `grep -c "ApplicationContextInitializer=org.ctc.testsupport.ContextLoadCountListener"` → 1 (PASS)
- `./mvnw test -Dtest=ContextLoadCountListenerTest` → `Tests run: 1, Failures: 0, Errors: 0` (PASS)
- `grep -c "ProcessHandle.current().pid()"` → 1 (PASS)
- `grep -c "@SuppressFBWarnings"` → 0 (PASS)

**Acceptance criteria (Plan §Task 3):**
- File exists, size 8180 bytes > 1000 bytes (PASS)
- All 5 level-2 headers present (PASS)
- Baseline section has 3 populated rows with mm:ss patterns (PASS — 3 rows)
- v1.12 stub mentions backup-staging (PASS — 2 mentions)
- No emojis (PASS — 0 high-byte UTF-8 sequences)

## Self-Check: PASSED

---

*Phase: 86-test-wallclock-reduction*
*Plan: 01*
*Completed: 2026-05-17*
