---
phase: 86-test-wallclock-reduction
plan: 05
status: complete
date: 2026-05-17
---

# Plan 86-05 — Phase-86 Close: Post-Audit Verdict + v1.12 Forward Path

## Outcome

Phase 86 closes via the **OR-branch** of PERF-04: ≥30% wallclock reduction is
not achieved on the local 3-run measurement (in fact a -6.7% regression);
PERF-04 is satisfied by documenting the architectural blocker and a concrete
v1.12 forward path. `docs/test-performance.md` is now the complete Phase-86
deliverable — 6 top-level sections fully populated, 3 Per-Decision subsections
citing Plans 02/03/04, and a 3-row v1.12 lever table with delta/effort/risk/
touchpoints columns.

The phase-close `./mvnw verify -Pe2e` gate is BUILD SUCCESS with JaCoCo line
coverage **88.97%** (≥82% gate, no regression). Wave-3 closes here per D-16
(soft cap, no further plans even if the gate misses 30%). Plan 06 will harvest
5 master-branch CI runs after merge — that median, not this local 10:24, is
the authoritative v1.11 baseline per D-11.

## Post-audit triple + reduction

| Run | Maven Total time | bash `real`     | Context loads |
|-----|------------------|-----------------|---------------|
| 1   | 09:24            | 565s (9m 26s)   | 79            |
| 2   | 10:24            | 625s (10m 25s)  | 79            |
| 3   | 10:30            | 631s (10m 31s)  | 78            |

**Median Maven: 10:24.** **Median bash real: 10m 25s.** **Median context-loads: 79.**

**Reduction: -6.7%** (10:24 post vs 09:45 baseline). All 3 post-audit runs
BUILD SUCCESS, no flakes.

A possible run-noise contribution: baseline spread 52s, post spread 66s. Run-1
of each pair tracked closely (09:01 vs 09:24, +23s). The median delta on n=3
is therefore not statistically robust — Plan 06's CI 5-run median is the
authoritative re-measurement.

## Context-load delta interpretation

81 → 79 (-2) is the *count* signal. The *fingerprint* changed materially:

- Plan 02 split a shared `@SpringBootTest + dev` cache key into 7 unique
  `@DynamicPropertySource`-bound keys for the sitegen cluster.
- Plan 04 collapsed 3 `@SpringBootTest` ITs into 1 shared `@DataJpaTest` key.
- Plan 03's latch-free methods no longer evict; Cluster C
  TestDataServiceIntegrationTest no longer evicts at class end.

The 2-context net reduction is the algebraic sum of these and smaller effects.
The wallclock regression is most likely driven by the cache-fingerprint change,
not the count — when Spring's TCF cache evicts under LRU, *which* context gets
rebuilt now matters as much as the total count. Verifying this requires
per-fork cache-key fingerprinting (extension of `ContextLoadCountListener`)
queued for v1.12 lever 2.

## Result Verdict (one sentence)

PERF-04 satisfied via OR-branch: Phase-86 Wave-2 audit did not deliver ≥30%
wallclock reduction (in fact -6.7% regression to median 10:24), but the
structural fixes (sitegen outputDir isolation, backup-IT latch-aware
`@DirtiesContext` audit, `@DataJpaTest` slice pilot) are correct on their own
terms and the v1.12 forward path documents the top-3 architectural levers with
delta/effort/risk for the next milestone.

## v1.12 Levers (1-line each)

1. **Per-fork `data/dev/backup-staging/` refactor** — ~60-90s estimated delta,
   M effort, unlocks Failsafe `forkCount>1C` for backup ITs without staging-dir
   races (D-14 Top-1).
2. **Shared `@SpringBootTest` `@ContextConfiguration` strategy** — ~30-60s
   delta, M-L effort, requires per-fork ContextLoadCountListener extension to
   profile cache fingerprint before the refactor.
3. **Testcontainers MariaDB `withReuse(true)`** — ~0s in v1.12 (pre-emptive),
   S effort, pays off only once MariaDB ITs exist (none planned in v1.11).

## Phase-close gate confirmation

Final `./mvnw verify -Pe2e` BUILD SUCCESS on 2026-05-17 across all three
post-audit runs (run 3 preserved at `target/test-perf/phase-86-final-verify.log`
and `.test-perf-logs/post-audit-run-3.log`).

- JaCoCo line coverage: **88.97%** (target ≥82%)
- JaCoCo instruction coverage: 88.03%
- JaCoCo branch coverage: 76.68%
- ContextLoadCountListener fired in all 5 forks (PID files in
  `target/test-perf/context-loads-*.txt`)

## PR-readiness for Plan 06

Plan 06 (CI 5-run median harvest, PERF-05) can begin once the v1.11 PR is
merged to master. The CI workflow `.github/workflows/ci.yml` runs `mvnw verify
-Pe2e` on every master commit; 5 consecutive successful runs after merge
provide the median that becomes the new v1.11 baseline per D-10/D-11.

## Issues encountered

The local wallclock regression is the dominant finding. Plan 06's CI median
will either:
- Confirm the regression (local n=3 representative) — v1.12 Lever 1 becomes
  P0 work
- Reveal the regression as local-only noise (CI is the reference per D-11) —
  in which case the structural fixes hold neutral or positive

Either outcome is acceptable per PERF-04 OR-branch; documentation is the
deliverable for this phase.

## Follow-ups

- **Plan 06 (Wave 4):** harvest 5 master-branch CI runs after merge, compute
  median (drop min + max), record as v1.11 CI baseline in
  `docs/test-performance.md` (PERF-05).
- **v1.12 Lever 1 (D-14):** per-fork `data/dev/backup-staging/` refactor.
- **v1.12 Lever 2 instrumentation:** extend `ContextLoadCountListener` to
  dump per-fork cache-key fingerprints before the shared
  `@ContextConfiguration` refactor.
