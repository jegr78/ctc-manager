---
phase: 79
slug: code-cleanup-test-performance-optimization-v1-10-milestone-c
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-15
approved_on: 2026-05-18
audit_method: retroactive
---

# Phase 79 — Validation Strategy

> Per-phase validation contract — retroactively approved by Phase 87 / Plan 87-08
> on 2026-05-18 via `/gsd:validate-phase 79` against the restored phase directory.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Mockito 5.20.0 |
| **Config file** | `pom.xml` (Surefire lines 260-302, Failsafe lines 273-420) |
| **Quick run command** | `./mvnw test -Dtest=<TargetClass>` |
| **Full suite command** | `./mvnw verify -Pe2e` |
| **Estimated runtime** | Baseline ~33:00 → final ~23:00 CI median (Phase 86 PERF-05 confirmed; 30% reduction achieved vs. ≤ baseline × 0.7 target) |

---

## Sampling Rate

- **Per cleanup-package commit (D-03):** `./mvnw test` — fast feedback after each per-package commit (~1200 unit tests, no IT).
- **Per wave merge:** `./mvnw verify` — runs unit + integration (no Playwright E2E). Gate before next wave starts.
- **Phase final gate (D-19):** `./mvnw verify -Pe2e` BUILD SUCCESS (confirmed at Phase 79 close 2026-05-15 + Phase 86 baseline 23:00 CI median).
- **Independence audit (D-05 Wave 1):** `./mvnw test -Dsurefire.runOrder=reversealphabetical` AND `-Dsurefire.runOrder=random` × 3 seeds — executed during 79-01, results captured in 79-01-SUMMARY.md.
- **Max feedback latency:** ≤ 5 minutes for per-commit `./mvnw test`; ≤ 15 minutes for `./mvnw verify`.

---

## Per-Task Verification Map

> Decision-row map: Phase 79 has no formal REQ-IDs; the 20 decisions D-01..D-20 act as acceptance criteria.
> Retroactive audit 2026-05-18: every decision is COVERED via CI or documented as Manual-Only with scripted command.

| Decision | Behavior | Test Type | Automated Command | Evidence | Status |
|----------|----------|-----------|-------------------|----------|--------|
| D-05 Wave 1 | All tests pass in reverse order | Independence audit | `./mvnw test -Dsurefire.runOrder=reversealphabetical` | 79-01-SUMMARY.md "Independence audit" section — reverse order green | ✅ green |
| D-05 Wave 1 | All tests pass with 3 random seeds | Independence audit | `./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed={1234,5678,9999}` | 79-01-SUMMARY.md — 3 random seeds green | ✅ green |
| D-05 Wave 2 | Surefire with `forkCount=2C` (or 2 conservative) + `reuseForks=true` passes | Parallelism | `./mvnw verify` | pom.xml Surefire `<forkCount>2</forkCount>` + `<reuseForks>true</reuseForks>` (line 273 area); CI run green | ✅ green |
| D-05 Wave 2 | Failsafe with `forkCount=1C` passes | Parallelism (IT) | `./mvnw verify` | pom.xml Failsafe block (line 273-420); CI run green | ✅ green |
| D-06 | Baseline wallclock recorded | Manual / scripted | `time ./mvnw clean verify -Pe2e --offline` | 79-01-SUMMARY.md baseline + docs/test-performance.md baseline tables | ✅ green (Manual-Only: doc/log evidence) |
| D-06 | Final wallclock ≤ baseline × 0.7 | Manual / scripted | `time ./mvnw clean verify -Pe2e --offline` | Phase 86 PERF-05: 23:00 CI median ≤ 33:00 × 0.7 = 23:06 — target met within tolerance | ✅ green (Manual-Only) |
| D-07 | `ci.yml` adds `concurrency` block | YAML lint + dry-run PR | `actionlint .github/workflows/ci.yml` | `.github/workflows/ci.yml` contains `concurrency: group: ${{ github.workflow }}-${{ github.ref }} / cancel-in-progress: true` | ✅ green (Manual-Only: grep) |
| D-07 | `@Tag("flaky")` excluded from default build | Tag mechanism | `./mvnw test` (excludes flaky); `./mvnw test -DexcludedGroups=` (runs flaky) | pom.xml Surefire `<excludedGroups>integration,e2e,flaky</excludedGroups>`; Failsafe `<excludedGroups>e2e,flaky</excludedGroups>` | ✅ green |
| D-08 | `TESTING.md` has "Test Invocation Discipline" section | Doc | `grep "## Test Invocation Discipline" .planning/codebase/TESTING.md` | TESTING.md line 629 — section present (last updated 2026-05-15 per file-footer marker) | ✅ green (Manual-Only: doc grep) |
| D-09..D-13 + D-20 | All Schutzwortliste-flagged comments survive | Cleanup grep | `grep -rE "MariaDB\|H2\|JEP\|CVE\|race\|thread-safe\|deadlock\|OSIV\|Lombok\|Unsafe\|auditing" src/` | 79-02a..79-02h SUMMARYs document per-package cleanup commits; Schutzwortliste preserved per per-commit grep evidence | ✅ green (Manual-Only: cleanup grep) |
| D-14 | `/gsd-audit-milestone v1.10` runs clean | Workflow step | `/gsd-audit-milestone v1.10` | v1.10-MILESTONE-AUDIT.md `status: passed` (closed 2026-05-15); 39/39 REQ-IDs traced | ✅ green |
| D-16 | Plan-SUMMARY frontmatter sweep applied to phases 56, 57, 62, 64 | Frontmatter audit | Manual diff | 79-06-SUMMARY.md documents the sweep; spot-checked phases 56/57/62/64 have `phase:` in SUMMARY frontmatter | ✅ green (Manual-Only) |
| D-18 | JaCoCo line coverage ≥ 0.82 after cleanup | Coverage | `./mvnw verify` (built-in `jacoco-maven-plugin` check) | pom.xml jacoco `<minimum>0.82</minimum>` (COVEREDRATIO limit); CI gate green | ✅ green |
| D-19 | Final phase gate green | Full E2E | `./mvnw verify -Pe2e` | 79-07-SUMMARY.md `BUILD SUCCESS`; Phase 86 PR-branch CI median 23:00 also confirms post-Phase-79 wallclock with E2E | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Phase 87 retroactive audit verdict:** 0 gap-fill tests required. Phase 79 is overwhelmingly doc + CI-config (D-05 fork config, D-06 wallclock, D-07 concurrency + flaky tag, D-08 doc, D-09..D-13 Schutzwortliste, D-14 audit workflow, D-16 frontmatter sweep, D-18 JaCoCo gate, D-19 E2E). All decisions either COVERED via CI gate (D-05 Wave 2, D-14, D-18, D-19) or Manual-Only with scripted grep / workflow command (everything else).

---

## Wave 0 Requirements

Per `79-RESEARCH.md §"Wave 0 Gaps"`: **None.** Phase 79 adds no new production code requiring new test coverage. Existing test infrastructure (1200+ unit tests, ~50 integration tests, Playwright E2E suite) covers all phase requirements.

The only new "test mechanic" is the `@Tag("flaky")` opt-out tag (D-07). Adding the tag annotation to a test does not require Wave 0 setup — the Surefire/Failsafe `<excludedGroups>flaky</excludedGroups>` configuration is a one-line pom.xml change applied in Plan Wave 2.

**Retroactive audit confirmation (2026-05-18):** Wave 0 satisfied retroactively — all referenced test infrastructure exists on disk and CI gates remain green per Phase 86 23:00 baseline.

---

## Manual-Only Verifications

| Behavior | Decision | Why Manual | Test Instructions |
|----------|----------|------------|-------------------|
| Wallclock baseline measurement | D-06 | Wallclock is not assertable in a unit test; it is a stopwatch measurement on a clean tree | 1. `./mvnw clean` 2. `time ./mvnw verify -Pe2e --offline` 3. Repeat ×3; record min/median/max in `79-AUTO-UAT.md` (archived in git history) |
| Wallclock final measurement | D-06 | Same as baseline | Same as baseline, after all D-05 changes land — Phase 86 PERF-05 re-baselined to 23:00 CI median |
| `/gsd-audit-milestone v1.10` clean verdict | D-14, D-15 | Workflow command outputs human-readable findings; verdict requires human judgment | Run `/gsd-audit-milestone v1.10`; verdict: PASSED (recorded in v1.10-MILESTONE-AUDIT.md status: passed) |
| `/gsd-complete-milestone v1.10` | D-14 | Workflow command; archives milestone after audit-clean | Run `/gsd-complete-milestone v1.10` only after audit returns clean — executed 2026-05-15 |
| Squash-PR creation | D-17 | `gh pr create` requires human-readable PR title/body | `gh pr create --assignee jegr78 --title "chore(79): v1.10 milestone closer — cleanup, test perf, audit"` — PR #108 merged |
| CI cancellation behavior with `concurrency` block | D-07 | GHA `cancel-in-progress: true` interaction with required-status-checks needs real PR | Confirmed in production CI on the v1.10 milestone PR; `.github/workflows/ci.yml` concurrency block landed |
| Schutzwortliste preservation | D-09..D-13, D-20 | Per-comment grep is the verification; not unit-testable behavior | Spot-checked during retroactive audit: `grep -rE "MariaDB\|H2\|JEP\|CVE\|race\|thread-safe\|deadlock\|OSIV\|Lombok\|Unsafe\|auditing" src/` returns the protected comment set; per-package 79-02a..79-02h SUMMARY entries cite specific preserved comments |
| Plan-SUMMARY frontmatter sweep | D-16 | Workflow side effect (`gsd-state` frontmatter validator); not runtime test | 79-06-SUMMARY.md documents the sweep applied to phases 56, 57, 62, 64; spot-check 2026-05-18 confirmed `phase:` in SUMMARY frontmatter for all 4 |

---

## Validation Sign-Off

- [x] All Phase-79 decisions (D-01..D-20) have an automated verify or a documented manual-only entry
- [x] Sampling continuity: per-package commit cadence + per-wave verify + phase final E2E
- [x] Wave 0 covers all MISSING references — N/A (no missing references; existing infra covers all)
- [x] No watch-mode flags
- [x] Feedback latency: per-package commit `./mvnw test` ≤ 5 min; per-wave `./mvnw verify` ≤ 15 min
- [x] `nyquist_compliant: true` set in frontmatter (Phase 87 retroactive approval 2026-05-18)

---

## Validation Audit 2026-05-18

**Auditor:** Phase 87 / Plan 87-08 retroactive audit (gsd-nyquist-auditor methodology inlined; doc + CI-config heavy phase, no subagent needed).
**Restore source:** git ref `60f5f915^` (parent of "chore(v1.11): clear v1.10 phase directories before new milestone").
**Restored files:** 34 artefacts at `.planning/milestones/v1.10-phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/` (16 PLAN, 14 SUMMARY, CONTEXT, RESEARCH, VERIFICATION, draft VALIDATION).
**Gap analysis:**

- COVERED via CI gate: D-05 Wave 2 (fork config), D-07 flaky-tag exclusion, D-14 (v1.10 audit closed), D-18 (JaCoCo 82% in pom.xml), D-19 (E2E green).
- Manual-Only (grep / workflow / doc / config-file evidence): D-05 Wave 1 (independence audit captured in 79-01-SUMMARY.md), D-06 (wallclock measurement), D-07 (ci.yml concurrency block grep), D-08 (TESTING.md section grep), D-09..D-13 + D-20 (Schutzwortliste grep), D-16 (frontmatter sweep).
- **Gap-fill tests added:** 0 (Phase 79 is doc + CI-config; nothing maps to a runtime assertion not already covered).
- **Impl bugs found:** 0.
**Phase-87 wallclock guard (D-06):** Captured separately in Plan 87-08 Task 5d (CI run-id + Total time vs. 24:09 ceiling).
**JaCoCo coverage:** ≥ 82% gate green on master per Phase-86 PR-branch CI median (PERF-05).
**Transition:** `status: draft` → `status: approved`; `nyquist_compliant: false` → `true`; `wave_0_complete: false` → `true`.

**Approval:** approved 2026-05-18 — retroactive audit via Phase 87 / Plan 87-08
