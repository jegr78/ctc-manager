---
phase: 79
slug: code-cleanup-test-performance-optimization-v1-10-milestone-c
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-15
---

# Phase 79 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Pulled from `79-RESEARCH.md` §"Validation Architecture".

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Mockito 5.20.0 |
| **Config file** | `pom.xml` (Surefire lines 260-302, Failsafe lines 273-420) |
| **Quick run command** | `./mvnw test -Dtest=<TargetClass>` |
| **Full suite command** | `./mvnw verify -Pe2e` |
| **Estimated runtime** | Baseline TBD (Plan Step 1 measures it) — Phase target ≤ baseline × 0.7 |

---

## Sampling Rate

- **Per cleanup-package commit (D-03):** `./mvnw test` — fast feedback after each per-package commit (~1200 unit tests, no IT).
- **Per wave merge:** `./mvnw verify` — runs unit + integration (no Playwright E2E). Gate before next wave starts.
- **Phase final gate (D-19):** `./mvnw verify -Pe2e` BUILD SUCCESS.
- **Independence audit (D-05 Wave 1):** `./mvnw test -Dsurefire.runOrder=reversealphabetical` AND `-Dsurefire.runOrder=random` × 3 seeds.
- **Max feedback latency:** ≤ 5 minutes for per-commit `./mvnw test`; ≤ 15 minutes for `./mvnw verify`.

---

## Per-Task Verification Map

> Populated by the planner once PLAN.md tasks are finalized. The map below seeds the required-coverage rows from `79-RESEARCH.md §"Phase Requirements → Test Map"`.

| Decision | Behavior | Test Type | Automated Command | Status |
|----------|----------|-----------|-------------------|--------|
| D-05 Wave 1 | All tests pass in reverse order | Independence audit | `./mvnw test -Dsurefire.runOrder=reversealphabetical` | ⬜ pending |
| D-05 Wave 1 | All tests pass with 3 random seeds | Independence audit | `./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed={1234,5678,9012}` | ⬜ pending |
| D-05 Wave 2 | Surefire with `forkCount=2C` + `reuseForks=true` passes | Parallelism | `./mvnw verify` | ⬜ pending |
| D-05 Wave 2 | Failsafe with `forkCount=1C` passes | Parallelism (IT) | `./mvnw verify` | ⬜ pending |
| D-06 | Baseline wallclock recorded in `79-AUTO-UAT.md` | Manual / scripted | `time ./mvnw clean verify -Pe2e --offline` | ⬜ pending |
| D-06 | Final wallclock ≤ baseline × 0.7 | Manual / scripted | `time ./mvnw clean verify -Pe2e --offline` | ⬜ pending |
| D-07 | `ci.yml` adds `concurrency` block; existing matrix unchanged | YAML lint + dry-run PR | `actionlint .github/workflows/ci.yml` | ⬜ pending |
| D-07 | Flaky-test quarantine works: `@Tag("flaky")` excluded from default build | Tag mechanism | `./mvnw test` (excludes flaky); `./mvnw test -DexcludedGroups=` (runs flaky) | ⬜ pending |
| D-08 | `TESTING.md` has "Test Invocation Discipline" section | Doc | Existence + content review | ⬜ pending |
| D-09..D-13 + D-20 | All Schutzwortliste-flagged comments survive | Cleanup grep | `grep -rE "MariaDB\|H2\|JEP\|CVE\|race\|thread-safe\|TODO\|HACK\|WORKAROUND\|FIXME\|deadlock\|OSIV\|Lombok\|Unsafe\|transitiv\|transitive\|pitfall\|auto-commit\|auditing\|AuditingEntityListener" src/` | ⬜ pending |
| D-14 | `/gsd-audit-milestone v1.10` runs clean OR all findings addressed | Workflow step | `/gsd-audit-milestone v1.10` | ⬜ pending |
| D-16 | Plan-SUMMARY frontmatter sweep applied to phases 56, 57, 62, 64 | Frontmatter audit | Manual diff | ⬜ pending |
| D-18 | JaCoCo line coverage ≥ 0.82 after cleanup | Coverage | `./mvnw verify` (built-in `jacoco-maven-plugin` check) | ⬜ pending |
| D-19 | Final phase gate green | Full E2E | `./mvnw verify -Pe2e` | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

The planner is expected to add per-task rows (`{N}-{plan}-{task}`) once PLAN.md exists; this seed table covers the decision-level acceptance criteria.

---

## Wave 0 Requirements

Per `79-RESEARCH.md §"Wave 0 Gaps"`: **None.** Phase 79 adds no new production code requiring new test coverage. Existing test infrastructure (1200+ unit tests, ~50 integration tests, Playwright E2E suite) covers all phase requirements.

The only new "test mechanic" is the `@Tag("flaky")` opt-out tag (D-07). Adding the tag annotation to a test does not require Wave 0 setup — the Surefire/Failsafe `<excludedGroups>flaky</excludedGroups>` configuration is a one-line pom.xml change applied in Plan Wave 2.

---

## Manual-Only Verifications

| Behavior | Decision | Why Manual | Test Instructions |
|----------|----------|------------|-------------------|
| Wallclock baseline measurement | D-06 | Wallclock is not assertable in a unit test; it is a stopwatch measurement on a clean tree | 1. `./mvnw clean` 2. `time ./mvnw verify -Pe2e --offline` 3. Repeat ×3; record min/median/max in `79-AUTO-UAT.md` |
| Wallclock final measurement | D-06 | Same as baseline | Same as baseline, after all D-05 changes land |
| `/gsd-audit-milestone v1.10` clean verdict | D-14, D-15 | Workflow command outputs human-readable findings; verdict requires human judgment | Run `/gsd-audit-milestone v1.10`; if any findings, plan them as Phase-79 follow-up steps OR Hotfix-Sub-Phase 79.X |
| `/gsd-complete-milestone v1.10` | D-14 | Workflow command; archives milestone after audit-clean | Run `/gsd-complete-milestone v1.10` only after audit returns clean |
| Squash-PR creation | D-17 | `gh pr create` requires human-readable PR title/body | `gh pr create --assignee jegr78 --title "chore(79): v1.10 milestone closer — cleanup, test perf, audit"` |
| CI cancellation behavior with `concurrency` block | D-07 / Research §"Open Questions" A1 | GHA `cancel-in-progress: true` interaction with required-status-checks needs real PR | Land D-07 in an early plan-step; verify with a throwaway force-push that the previous run is cancelled AND the cancellation does not block the PR |

---

## Validation Sign-Off

- [ ] All Phase-79 decisions (D-01..D-20) have an automated verify or a documented manual-only entry
- [ ] Sampling continuity: per-package commit cadence + per-wave verify + phase final E2E
- [ ] Wave 0 covers all MISSING references — N/A (no missing references; existing infra covers all)
- [ ] No watch-mode flags
- [ ] Feedback latency: per-package commit `./mvnw test` ≤ 5 min; per-wave `./mvnw verify` ≤ 15 min
- [ ] `nyquist_compliant: true` set in frontmatter once PLAN.md task IDs are mapped in

**Approval:** pending — sign off after PLAN.md is written and per-task IDs are mapped into the table above.
