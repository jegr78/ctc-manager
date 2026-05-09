---
phase: 68-lombok-unsafe-deprecation-warning-fix
plan: 01
subsystem: infra

tags: [lombok, jdk-25, jep-498, sun-misc-unsafe, maven, surefire, failsafe, maven-compiler-plugin]

# Dependency graph
requires:
  - phase: 67
    provides: "Phase-67 baseline of Tests run = 1231 with JaCoCo BUNDLE LINE >= 0.82 — preserved by this hygiene-only change."
provides:
  - "Lombok 1.18.46 pinned via project `<lombok.version>` property override (overrides Spring Boot 4.0.5 parent BOM transitive 1.18.44)."
  - "JEP 498 escape flag `--sun-misc-unsafe-memory-access=allow` injected into Surefire and Failsafe argLine and into the maven-compiler-plugin compile fork — silences the four `WARNING: ... sun.misc.Unsafe ... lombok.permit.Permit` lines emitted under JDK 25 across compile + test JVMs."
  - "Discoverable removal anchor: each modified block carries `<!-- JEP 498 escape: silence Lombok #3959 Permit/Unsafe warnings until upstream fix lands -->` so the workaround can be removed once upstream Lombok issue #3959 ships."
affects: [phase-69+ (any future Lombok bump), JDK-26-readiness, build-warning-cleanliness]

# Tech tracking
tech-stack:
  added: []  # No new libraries — version pin only.
  patterns:
    - "Project `<properties>` override of parent-BOM-resolved versions (existing pattern for `<playwright.version>`; now applied to `<lombok.version>` too)."
    - "JEP 498 `--sun-misc-unsafe-memory-access=allow` flag applied uniformly to all JVM forks the build creates (compile fork via maven-compiler-plugin `<compilerArgs>` `-J` prefix; Surefire fork via `<argLine>`; Failsafe fork via `<argLine>`)."

key-files:
  created: []
  modified:
    - "pom.xml"

key-decisions:
  - "Adopted Path 1+2 (hygiene bump + JEP 498 flag) per CONTEXT.md D-02-revised — pure version pin alone cannot achieve the goal because Lombok master/edge/1.18.46 all still call `sun.misc.Unsafe::objectFieldOffset` (RESEARCH.md HIGH confidence + upstream issue #3959 open)."
  - "JEP 498 flag must be applied to THREE fork sites — Surefire + Failsafe + maven-compiler-plugin — not just the two Surefire/Failsafe sites named in the original plan. The compile-phase warnings are emitted by the Maven compiler plugin's annotation-processor JVM, which is distinct from test-fork JVMs (see Deviations below)."
  - "Forked compile JVM (`<fork>true</fork>`) accepted as the cost of in-pom.xml-only configuration. The alternative (`.mvn/jvm.config` file) was rejected to honor the `ONE file: pom.xml` critical constraint from the orchestrator."

patterns-established:
  - "When adding JEP 498 escape flags to silence transitively-deprecated `sun.misc.Unsafe` calls, audit ALL Maven-fork sites (compile, test, integration-test) — Surefire/Failsafe coverage alone leaves the compile-phase warnings unsuppressed."
  - "Use `<compilerArgs>` `-J<jvm-flag>` form (not `<argLine>`) to forward JVM flags into a forked maven-compiler-plugin javac process."

requirements-completed: [D-01, D-02-revised, D-05-resolved, D-07, D-08, D-11-revised, D-12, D-13, D-14-revised, D-15]

# Metrics
duration: ~10 min (dominated by two `./mvnw verify` runs at ~3-4 min each)
completed: 2026-05-07
---

# Phase 68 Plan 01: Lombok Unsafe Deprecation Warning Fix Summary

**Lombok 1.18.46 pinned + JEP 498 `--sun-misc-unsafe-memory-access=allow` flag applied to compile-fork + Surefire + Failsafe argLine — all four `sun.misc.Unsafe`/`lombok.permit.Permit` JDK-25 warning lines silenced across `./mvnw verify` and `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`.**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-05-07T20:55:00Z (approximate — recorded at task start)
- **Completed:** 2026-05-07T21:06:14Z
- **Tasks:** 4 / 4
- **Files modified:** 1 (`pom.xml`)
- **Verify runs:** 2 (one extra needed due to deviation Rule 3 — see below)

## Accomplishments

- Pinned `<lombok.version>1.18.46</lombok.version>` in `pom.xml` `<properties>`, overriding the Spring Boot 4.0.5 parent BOM's transitive 1.18.44 resolution. `dependency:tree` confirms `org.projectlombok:lombok:jar:1.18.46:compile (optional)`.
- Added JEP 498 escape flag `--sun-misc-unsafe-memory-access=allow` to BOTH Surefire and Failsafe `<argLine>` blocks, with `@{argLine}` (JaCoCo forwarding) and Mockito javaagent path preserved untouched.
- **Deviation auto-fix:** Added the SAME JEP 498 flag to the maven-compiler-plugin's `<compilerArgs>` (with `-J` prefix and `<fork>true</fork>`) — the compile-phase warnings, emitted by the annotation-processor JVM, are NOT covered by Surefire/Failsafe argLine. Without this third site, Gate 4 (`./mvnw verify` warning count = 0) fails. See Deviations.
- All three modified blocks carry a discoverable HTML TODO comment: `<!-- JEP 498 escape: silence Lombok #3959 Permit/Unsafe warnings until upstream fix lands -->`.
- `./mvnw verify` exits 0 with **Tests run: 1231, Failures: 0, Errors: 0, Skipped: 4** (Phase-67 baseline preserved). JaCoCo: "All coverage checks have been met" (≥ 0.82).
- `timeout 60`-equivalent `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` startup window emits zero Permit/Unsafe warning lines (Spring Boot reached `Started CtcManagerApplication in 2.976 seconds`).

## Task Commits

Per CONTEXT.md D-12 (atomic commit) and the orchestrator's "single atomic commit per CONTEXT.md D-12" critical constraint, all four tasks land in **one** commit:

1. **Tasks 1 + 2 + 3 + 4 (atomic)** — `97c0489` (`fix(68): silence Lombok Permit/Unsafe warnings (JEP 498 + 1.18.46 hygiene bump)`)

_Note: per orchestrator instruction "Do NOT update STATE.md or ROADMAP.md", no follow-up plan-metadata commit is created. SUMMARY.md is committed in a separate `docs(68): plan 01 summary` commit per the plan's `<output>` section._

## Files Created/Modified

- `pom.xml` — Three configuration edits, all in the `<build>` section:
  1. New `<lombok.version>1.18.46</lombok.version>` line in `<properties>` (immediately after `<playwright.version>`).
  2. New `<!-- JEP 498 escape: ... -->` comment + JEP 498 flag in `maven-compiler-plugin` `<compilerArgs>` with `<fork>true</fork>` (deviation Rule 3 — see below).
  3. New `<!-- JEP 498 escape: ... -->` comment + JEP 498 flag in `maven-surefire-plugin` `<argLine>`.
  4. New `<!-- JEP 498 escape: ... -->` comment + JEP 498 flag in `maven-failsafe-plugin` `<argLine>` (inside the `e2e` profile).

Diff stat: 1 file changed, 10 insertions, 2 deletions (deeper than the plan's predicted +5 / -2 because of the deviation fix).

## Decisions Made

- **D-01, D-05-resolved (followed plan):** Pinned Lombok to 1.18.46 via `<properties>` override.
- **D-02-revised (followed plan):** Adopted JEP 498 `--sun-misc-unsafe-memory-access=allow` in `<argLine>` for Surefire and Failsafe; preserved `@{argLine}` and Mockito javaagent.
- **D-12 (followed plan):** Single atomic commit `fix(68): silence Lombok Permit/Unsafe warnings (JEP 498 + 1.18.46 hygiene bump)`.
- **NEW (this run):** Extended D-02-revised's flag application to include the `maven-compiler-plugin` compile fork — this was a plan gap, see Deviations Rule 3 below.
- **NEW (this run):** Chose `<fork>true</fork>` + `<compilerArgs><arg>-J...</arg></compilerArgs>` over `.mvn/jvm.config` to honor the `ONE file: pom.xml` orchestrator constraint.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Compile-phase JEP 498 flag missing — Gate 4 fails as planned**

- **Found during:** Task 3 (`./mvnw verify` final-gate run)
- **Issue:** After Tasks 1 + 2 (Lombok pin + Surefire/Failsafe argLine flag), `./mvnw verify 2>&1 | grep -cE "sun\.misc\.Unsafe|lombok\.permit\.Permit"` returned **4**, not the expected 0. All four warning lines were emitted at log lines 22-25, immediately after `[INFO] --- compiler:3.14.1:compile (default-compile) @ ctc-manager ---` and `[INFO] Compiling 182 source files with javac [debug parameters release 25] to target/classes`. Surefire and Failsafe forks contributed **zero** warnings — the JEP 498 flag in their `<argLine>` blocks worked perfectly. The remaining warnings come from the Maven compiler plugin loading the Lombok annotation processor; that processor's `lombok.permit.Permit` calls `sun.misc.Unsafe::objectFieldOffset` from a JVM (the compile JVM) that does NOT inherit the Surefire/Failsafe `<argLine>`. RESEARCH.md anticipated compile-phase as a warning surface but the plan only patched Surefire/Failsafe.
- **Fix:** Added `<fork>true</fork>` and `<compilerArgs><arg>-J--sun-misc-unsafe-memory-access=allow</arg></compilerArgs>` to the existing `maven-compiler-plugin` `<configuration>` block (where the `<annotationProcessorPaths>` already lives). The `-J` prefix forwards the flag from the compiler-plugin invocation into the forked javac JVM, which is where `lombok.permit.Permit` is loaded during annotation processing. Same `<!-- JEP 498 escape: ... -->` HTML comment placed above the new entries for grep-discoverability.
- **Files modified:** `pom.xml` (extended the existing `maven-compiler-plugin` block).
- **Verification:** Re-ran `./mvnw verify` once more (second and final verify run for the phase). Result: BUILD SUCCESS, exit 0, `grep -cE "sun\.misc\.Unsafe|lombok\.permit\.Permit"` returns **0**, Tests run: 1231 baseline preserved, JaCoCo coverage gate met. No further deviations needed.
- **Committed in:** `97c0489` (atomic commit — bundled with Tasks 1 + 2).

**2. [Plan-text gate ambiguity, NOT a deviation] Comment uses `Lombok` (capital L), validation gate uses `lombok` (lowercase)**

- **Found during:** Task 2 acceptance-criteria check
- **Issue:** PLAN.md `<acceptance_criteria>` says `[ $(grep -c "lombok #3959" pom.xml) -ge 2 ]` (lowercase, case-sensitive); the verbatim TODO comment text given in PLAN.md `<action>` and CONTEXT.md D-02-revised uses capital `Lombok #3959` (as in "GitHub project Lombok"). VALIDATION.md gate 3 uses `grep -c "lombok #3959\|lombok#3959"` (also lowercase, case-sensitive).
- **Fix:** None — written the comment exactly as the plan's verbatim text dictates (`Lombok #3959`). Validated via `grep -ic` (case-insensitive) which returns 2, satisfying the gate's intent (≥ 1).
- **Files modified:** None additional.
- **Verification:** `grep -ic "lombok #3959\|lombok#3959" pom.xml` → 2 (≥ 1 ✓).
- **Committed in:** Same atomic commit `97c0489`.

---

**Total deviations:** 1 auto-fixed (Rule 3 - blocking).
**Impact on plan:** The Rule 3 fix is essential — without it, Gate 4 (the headline phase outcome) cannot be met. Same JEP 498 flag, same TODO comment style, same `pom.xml` file, same atomic commit. No scope creep, no new files, no production-source changes. Plan body remains structurally correct; this discovery refines the plan's `<interfaces>` section for any future re-application: the maven-compiler-plugin block must also be patched, not just Surefire and Failsafe.

## Issues Encountered

- **`timeout` not available on macOS for spring-boot:run sanity:** macOS `darwin25` ships neither `timeout` nor `gtimeout` in the default toolchain. Worked around by using a background-process pattern (`./mvnw spring-boot:run > log 2>&1 &` + `sleep 75` + `pkill -P $PID; kill $PID`). Confirmed Spring Boot reached `Started CtcManagerApplication in 2.976 seconds` and the warning grep returned 0.
- **`pgrep -af` self-matching after kill:** transient `pgrep -af "spring-boot|CtcManager|9090"` invocations matched their own argv, surfacing as phantom PIDs in the post-kill check. Verified definitively via `lsof -nP -iTCP:9090 -sTCP:LISTEN` (empty) and `ps aux | grep ... | grep -v grep` (empty) — no orphan Spring Boot processes remain.
- **Branch invariant note:** The plan's `<acceptance_criteria>` repeatedly check `[ "$(git branch --show-current)" = "gsd/v1.9-season-phases-groups" ]`. This worktree runs on its own per-worktree branch `worktree-agent-a8b33f7022d113c8b` (per orchestrator parallel-execution convention — the orchestrator merges this branch back into `gsd/v1.9-season-phases-groups` after the agent returns). The orchestrator's prompt explicitly disables branch-switching here. Branch did NOT switch during execution; `git reflog` shows only commits on the worktree branch.

## Verification Gates — Final State

| Gate | Command | Expected | Actual | Status |
|------|---------|----------|--------|--------|
| 1 | `grep -c "<lombok.version>1.18.46</lombok.version>" pom.xml` | 1 | 1 | PASS |
| 2 | `grep -c "sun-misc-unsafe-memory-access=allow" pom.xml` | ≥ 2 | 3 (compile + Surefire + Failsafe) | PASS (exceeds expectation due to Rule 3 fix) |
| 3 | `grep -ic "lombok #3959\|lombok#3959" pom.xml` | ≥ 1 | 3 | PASS |
| 4 | `./mvnw verify 2>&1 \| grep -cE "sun\.misc\.Unsafe\|lombok\.permit\.Permit"` | 0 | 0 | PASS |
| 5 | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (60s window) warning count | 0 | 0 | PASS |
| 6 | `./mvnw test -q -Dtest=DriverSheetImportServiceTest` warning count | 0 | (covered by Gate 4 — Surefire is the only fork involved here) | PASS (transitive) |
| Behavior 1 | `./mvnw verify` exit code | 0 | 0 | PASS |
| Behavior 2 | Tests run total | 1231, 0 failures, 0 errors | 1231, 0, 0, 4 skipped | PASS |
| Behavior 3 | JaCoCo BUNDLE LINE | ≥ 0.82 | "All coverage checks have been met" | PASS |
| Behavior 4 | `dependency:tree` Lombok version | 1.18.46 | `org.projectlombok:lombok:jar:1.18.46:compile (optional)` | PASS |

## Self-Check: PASSED

- File `/Users/jegr/Documents/github/ctc-manager/.claude/worktrees/agent-a8b33f7022d113c8b/.planning/phases/68-lombok-unsafe-deprecation-warning-fix/68-01-SUMMARY.md` will be created by this Write call (this very file).
- Commit `97c0489` confirmed via `git log --oneline -1`: `fix(68): silence Lombok Permit/Unsafe warnings (JEP 498 + 1.18.46 hygiene bump)`.
- `./mvnw verify` exit 0, warning count 0, Tests run 1231, JaCoCo gate met (all captured at completion time of Task 3 second-run).

## Upstream Removal Trigger

When upstream Lombok issue [projectlombok/lombok#3959](https://github.com/projectlombok/lombok/issues/3959) ships a release with `lombok.permit.Permit` migrated off `sun.misc.Unsafe::objectFieldOffset` (likely toward `MethodHandles.privateLookupIn` per the deprecation-transition norm):

1. Bump `<lombok.version>` in `pom.xml` `<properties>` to that release.
2. Re-run `./mvnw verify` to confirm warning count remains 0 even WITHOUT the JEP 498 flag.
3. Remove the three `<!-- JEP 498 escape: ... -->` comment + flag pairs from pom.xml (compile-fork `<compilerArgs>`, Surefire `<argLine>`, Failsafe `<argLine>`).
4. Consider whether the `<lombok.version>` pin itself is still load-bearing or can revert to BOM-resolved.
5. Consider whether `<fork>true</fork>` on the maven-compiler-plugin is still needed (it adds compile-fork overhead; remove if no longer required).

The triple-comment grep anchor (`grep "JEP 498 escape" pom.xml`) makes the entire workaround discoverable for the future maintainer.

## User Setup Required

None — pure build-config change, no external services, no env vars, no migrations.

## Next Phase Readiness

- Phase 68 ships clean. `gsd/v1.9-season-phases-groups` (post worktree merge-back) gains a single `fix(68): silence Lombok Permit/Unsafe warnings (JEP 498 + 1.18.46 hygiene bump)` commit. CI (`./mvnw verify` + JaCoCo PR comment) should match local results.
- Recommended follow-up: a `chore` phase periodically polls upstream Lombok #3959 — when it closes with a fix-version, schedule a "remove JEP 498 workaround" plan (see Upstream Removal Trigger).
- No blockers for any in-flight work.

---
*Phase: 68-lombok-unsafe-deprecation-warning-fix*
*Completed: 2026-05-07*
