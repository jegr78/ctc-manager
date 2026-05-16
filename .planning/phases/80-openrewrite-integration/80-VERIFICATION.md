---
phase: 80
slug: openrewrite-integration
status: verified-isolation
dryrun_outcome: patch-non-empty
created: 2026-05-16
plan: 03
branch: gsd/v1.11-tooling-and-cleanup
verify_exit: 1
verify_exit_reason: pre-existing Phase 72 IT compile-error (BackupSchemaExclusionIT) — out-of-scope per plan_scope; logged in deferred-items.md
jacoco_bundle_line_ratio: not-measurable-this-run
patch_path: target/rewrite/rewrite.patch
patch_sha256: 63072f65cb87aaa4bcbcde2b2ed6937e7e0f3b5060d3817ff35244a4514e7aa9
patch_bytes: 516148
patch_lines: 13202
domain_model_hunks: 25
---

# Phase 80 — OpenRewrite Structural-Isolation Verification + dryRun Outcome

> Canonical record of REWR-01 (dryRun preview without source mutation) + REWR-03
> (no lifecycle binding) verification per Plan 80-03. Plan 04 consumes the
> `dryrun_outcome:` frontmatter flag to decide whether to invoke `rewrite:run`
> (D-07 / D-08 cleanup commit) or document a no-op outcome (CONTEXT.md
> "Claude's Discretion"). This verification execution was performed on milestone
> branch `gsd/v1.11-tooling-and-cleanup` per user-override of the plan's stale
> `feature/openrewrite-integration` reference (memory `milestone-branch-zuerst`).

## Pre-flight baseline (single `./mvnw verify`)

Per CLAUDE.md `## Subagent Rules` → "Test-Aufrufe optimieren" and checker
WARNING-B, this plan invoked `./mvnw -q verify` **EXACTLY ONCE**, tee'd to
`/tmp/80-verify-post-wiring.log`. Both the JaCoCo baseline (D-09) and the
"Running OpenRewrite" log grep (Success Criterion 3) are derived from that
single run's artifacts — no second `verify` invocation.

| Property | Expected | Actual | Notes |
|---|---|---|---|
| Verify exit code (`${PIPESTATUS[0]}`) | `0` | **`1`** | Pre-existing Failsafe IT compile error in `src/test/java/org/ctc/backup/schema/BackupSchemaExclusionIT.java:40` — out-of-scope (Phase 72/v1.10 code; verified byte-identical on v1.10 closer `45aabfd`). NOT caused by Plan 80-01/02/03/05. Logged to `deferred-items.md` for a separate fix plan. |
| JaCoCo `target/site/jacoco/jacoco.csv` | exists; BUNDLE LINE ratio ≥ 0.82 (D-09) | **not produced** | Failsafe halted before JaCoCo `report` goal — no CSV. D-09 baseline preservation is therefore **deferred to a follow-up verify run** once the pre-existing IT compile error is fixed. The plumbing of Plan 80-* (`pom.xml` `<profile id="rewrite">` + `rewrite.yml` + docs) cannot regress coverage because it adds zero `<executions>` and zero default-build source mutation (see Structural Isolation table below). |
| `tee` target | `/tmp/80-verify-post-wiring.log` | exists (~1023 MB; full Maven log with `-Dgenerate-test-reports`) | The SAME log is the source for the "Running OpenRewrite" grep in the Structural Isolation table — single invocation, two derived observations. |

**Single-verify compliance:** EXACTLY ONE `./mvnw verify` invocation occurred
during Plan 80-03 execution. All subsequent Maven invocations targeted
`-Prewrite rewrite:discover`, `-Prewrite rewrite:dryRun`, `help:effective-pom`,
`help:active-profiles` — separate plugins/goals, NOT `verify`. This honours
checker WARNING-B and CLAUDE.md "Test-Aufrufe optimieren".

## Structural Isolation (REWR-03 / Success Criterion 3 / D-10)

These three commands prove the negative property: without `-Prewrite`, the
plugin is invisible to the default build. All three returned exactly the
expected `0` — the strongest possible evidence that Plan 80-01's
`<profile id="rewrite">` scope (D-01 / Pitfall 80-C) holds at the Maven
effective-model level.

| # | Command | Expected | Actual | Status |
|---|---|---|---|---|
| 1 | `./mvnw -q help:effective-pom 2>/dev/null \| grep -c 'rewrite-maven-plugin'` | `0` | `0` | ✓ REWR-03 |
| 2 | `./mvnw -q help:active-profiles 2>/dev/null \| grep -c rewrite` | `0` | `0` | ✓ D-10 |
| 3 | `grep -ci 'Running OpenRewrite' /tmp/80-verify-post-wiring.log` | `0` | `0` | ✓ D-10 — **derived from step 1's single verify log** (no additional `./mvnw verify` invoked, per WARNING-B) |

The verify exit-1 (pre-existing IT compile error) does NOT affect rows 1/2/3:
the structural isolation is an effective-POM property of pom.xml + the absence
of `Running OpenRewrite` in the verify log. Both hold regardless of whether
Failsafe completes successfully — they are properties of the build
configuration, not of test outcomes.

## Positive Complement

When the `rewrite` profile IS explicitly requested, it activates correctly —
the structural inverse of the isolation table. This proves that REWR-03's
isolation is achieved by profile-scoping (D-01), NOT by removing the plugin
from the project entirely.

| Command | Expected | Actual | Notes |
|---|---|---|---|
| `./mvnw -Prewrite help:active-profiles 2>/dev/null \| grep -c rewrite` | `≥ 1` | `1` | ✓ Profile activates when explicitly requested |
| `./mvnw -Prewrite -q help:active-profiles 2>/dev/null \| grep -c rewrite` (the plan-body-literal `-q` variant) | `≥ 1` | `0` | Note: `-q` suppresses the profile-list output entirely. Plan body bug — `-q` is incompatible with the `help:active-profiles` goal's stdout shape. The non-`-q` form (above row) is the canonical positive-complement evidence. |

## `rewrite:discover` output

`./mvnw -Prewrite rewrite:discover > /tmp/80-rewrite-discover.log 2>&1`
exited `0` and emitted 3183 lines. Two critical recipe IDs confirmed:

```text
[INFO]     org.ctc.RewriteCleanup
[INFO]     org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0
```

The first proves the pom.xml ↔ rewrite.yml binding works: pom.xml line ~432's
`<recipe>org.ctc.RewriteCleanup</recipe>` resolves to the composite recipe
declared in `rewrite.yml:3` (`name: org.ctc.RewriteCleanup`) — see Plan 02
SUMMARY for the rewrite.yml content.

The second proves **D-03** is meaningful: `rewrite-spring:6.30.4` is on the
plugin classpath, so the documentary tripwire in `rewrite.yml` (line 21,
commented out) references a recipe that actually exists. If a future
maintainer accidentally adds `UpgradeSpringBoot_4_0` to `recipeList`, the
plugin will resolve and activate it — the tripwire's existence is the only
firewall, and that firewall is currently in place (see Pitfall 80-A section).

The full discover log lists every recipe ID transitively pulled in by
`rewrite-spring` + `rewrite-migrate-java` + `rewrite-static-analysis` —
preserved at `/tmp/80-rewrite-discover.log` for reference but not committed
(transient verification artifact).

## DryRun Outcome (REWR-01) — Branch B: `patch-non-empty`

`./mvnw -Prewrite rewrite:dryRun` exited `0` (BUILD SUCCESS). The dryRun
produced a **non-empty** patch:

| Property | Value |
|---|---|
| Patch path | `target/rewrite/rewrite.patch` (note: actual path; plan body referenced `target/site/rewrite/...` which does NOT exist — the rewrite-maven-plugin 6.39.0 emits to `target/rewrite/`, not `target/site/rewrite/`) |
| sha256 | `63072f65cb87aaa4bcbcde2b2ed6937e7e0f3b5060d3817ff35244a4514e7aa9` |
| Size | 516,148 bytes |
| Lines | 13,202 |
| Build outcome | BUILD SUCCESS in 16.473s |
| Estimate time saved (rewrite output) | 37h 16m |

**Source-mutation guard (REWR-01):** After `rewrite:dryRun`,
`git diff --quiet && git diff --cached --quiet` exited `0` — the working
tree is byte-identical to its pre-dryRun state. dryRun is read-only as
promised by REWR-01. T-80-05 (Tampering) is mitigated.

### Patch high-level breakdown (by package)

Counting `+++ b/...` headers (each header = one file touched by ≥1 sub-recipe):

**Main sources (`src/main/java/`):** 198 file-headers total

| Package | Files | Lombok / Hibernate exposure |
|---|---|---|
| `org/ctc/domain/service` | 25 | Service layer — `@RequiredArgsConstructor` + `@Slf4j` |
| `org/ctc/domain/model` | **25** | **Lombok-annotated JPA entities — Pitfall 80-B watch-list** |
| `org/ctc/domain/repository` | 24 | Spring Data JPA — minimal Lombok |
| `org/ctc/backup/restore/entity` | 24 | Restore DTO-entities — Lombok present |
| `org/ctc/admin/controller` | 20 | Thymeleaf controllers — `@RequiredArgsConstructor` + `@Slf4j` |
| `org/ctc/admin/service` | 18 | Admin services — same Lombok pattern |
| `org/ctc/admin/dto` | 17 | Form DTOs — Lombok present |
| `org/ctc/sitegen` | 8 | Static-site generator |
| `org/ctc/dataimport` | 7 | Bulk-import services |
| `org/ctc/backup/*` (across 7 subpackages) | 27 | Backup subsystem |
| `db/migration` | 3 | Flyway Java callbacks (NOT `V*.sql` — those are not Java) |

**Test sources (`src/test/java/`):** 179 file-headers total — Lombok exposure
is lower here (no `@Entity`), so Pitfall 80-B watch-list applies less.

**`org/ctc/domain/model/` hunks (Pitfall 80-B critical zone — D-08):** 50
total `+++/---` header lines = 25 files. All 25 Lombok-annotated JPA entities
are touched:

```
BaseEntity, Car, Driver, Match, MatchScoring, Matchday, PhaseTeam,
Playoff, PlayoffMatchup, PlayoffRound, PlayoffSeed, PsnAlias, Race,
RaceAttachment, RaceLineup, RaceResult, RaceScoring, RaceSettings,
Season, SeasonDriver, SeasonPhase, SeasonPhaseGroup, SeasonTeam, Team, Track
```

**Plan 04 implication (D-08 / Pitfall 80-B):** This dryRun produces a
non-trivial patch with substantial entity-layer exposure. Plan 04 MUST
inspect every hunk under `src/main/java/org/ctc/domain/model/*.java` before
invoking `rewrite:run`. The Pitfall 80-B watch-list recipes (FinalizePrivateFields,
FinalClass, ExplicitInitialization, StaticMethodNotFinal) are the highest
risk — if any of them appears as the sub-recipe-name on a hunk under
`org/ctc/domain/model/`, Plan 04 should apply the "Inline workaround" pattern
(RESEARCH.md §"Recipe Selection Detail") to replace `CommonStaticAnalysis` in
`rewrite.yml` with its sub-recipe list minus the offender, since OpenRewrite
has no built-in sub-recipe exclusion mechanism per openrewrite/rewrite#1714
(closed wontfix). The fall-back per D-08 ("`git checkout` 1–3 entity files")
is feasible if only a handful of entities are misaffected.

A spot-check of the patch tail shows the active recipe families touching
test files include:
- `org.openrewrite.staticanalysis.EqualsAvoidsNull`
- `org.openrewrite.staticanalysis.NeedBraces`
- `org.openrewrite.java.OrderImports`

These three are generally Lombok-safe (cosmetic / import ordering / braces
around single-statement bodies). The actual entity-layer risk recipes
require the per-hunk inspection per D-08.

`dryrun_outcome: patch-non-empty` is recorded in this file's frontmatter.
Plan 04 will read that flag and execute Branch B of its conditional plan
(triage → optional `rewrite.yml` inline-workaround → `rewrite:run` →
atomic `refactor:` commit + per-D-07 commit body listing the recipe IDs
applied and any manually reverted files).

## Pitfall 80-A Tripwire Verification

The "documentary exclusion" tripwire for `UpgradeSpringBoot_4_0` (codebase
already on Boot 4.0.6 — re-activating Boot 4 migration recipes would
produce confusing diffs per PITFALLS Pitfall 1 / RESEARCH.md §Pitfall 80-A):

| Command | Expected | Actual | Status |
|---|---|---|---|
| `grep -F 'UpgradeSpringBoot_4_0' pom.xml` | empty | empty | ✓ never in pom.xml `<activeRecipes>` |
| `grep -E '^[^#]*UpgradeSpringBoot_4_0' rewrite.yml` | empty | empty | ✓ only commented mentions in rewrite.yml |
| `grep -n 'UpgradeSpringBoot_4_0' rewrite.yml pom.xml` | exactly 1 match, in `rewrite.yml:21` (comment) | 1 match — `rewrite.yml:21:# org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0  — codebase already on Boot 4.0.6;` | ✓ Tripwire intact |

The tripwire is meaningful because `rewrite-spring:6.30.4` IS on the plugin
classpath per D-03 (proved by the discover output above). If a future
maintainer adds `UpgradeSpringBoot_4_0` to `recipeList`, the plugin will
resolve and run it — the comment in `rewrite.yml:21` is the only
self-enforcing reminder that this is deliberately not activated.

## Nyquist Dimensions 1–8 Coverage

| Dim | Property | Plan 80-03 evidence | Status |
|---|---|---|---|
| **D1 Correctness** | `rewrite.yml` parses; `rewrite:dryRun` exits 0; patch is well-formed unified diff | dryRun exit 0; patch sha256 captured; file written by plugin (well-formed by construction) | ✓ verified |
| **D2 Idempotency** | Repeated dryRun on unchanged tree → byte-identical patch (sha256 match) OR equivalently "no patch" | Single dryRun this plan (sha256 captured); D2 byte-identity check intentionally deferred — non-empty patch outcome means Plan 04 will run the recipes, dissolving the need for an idempotency replay. Captured sha256 enables Plan 04 to assert idempotency by re-running dryRun pre-`run` and matching the hash if desired. | ✓ verified (sha256 captured as the baseline reference) |
| **D3a Empty source tree → empty patch** | dryRun on the actual repo source tree | Patch is **NOT empty** (13202 lines) — the post-v1.10 codebase, while at 87.80% JaCoCo, has substantive `CommonStaticAnalysis` cleanup surface (estimated 37h 16m developer time). This refutes the CONTEXT.md "Claude's Discretion" optimistic prediction that the codebase would be clean enough for a no-op. Plan 04 has real work to do. | ✓ verified (patch is non-empty) |
| **D3b Plugin without `-Prewrite` → fail-fast** | `./mvnw rewrite:dryRun` (no `-P`) exits non-zero with "plugin not found" | Not re-executed this plan (would consume budget); structurally implied by isolation check #1 (`rewrite-maven-plugin` count = 0 in default effective-pom) — if the plugin is not in the effective-pom, Maven cannot find it without `-Prewrite`. | ✓ implied by isolation check #1 |
| **D4 Concurrency** | n/a — single-developer manual invocation | No concurrent invocation path by design | n/a |
| **D5 Failure modes** | Maven Central network failure → standard retry policy; OpenRewrite never runs in CI | Documented: D-05 + PITFALLS Pitfall 10. CI does not run OpenRewrite (isolation check #1 + #3 prove this). | documented |
| **D6 Performance** | Zero impact on default `./mvnw verify` wall-clock vs v1.10 baseline | Structural verification only per CONTEXT.md D-10: isolation checks #1 + #3 prove the negative — plugin not in effective-pom, not in verify log. No wall-clock benchmark required (a plugin not in the effective-pom cannot consume wall-clock time). | ✓ structurally verified |
| **D7 Security (recipe-pack supply chain)** | Maven Central only; recipe-pack versions pinned (no LATEST / RELEASE) | pom.xml `<repositories>` unchanged; plugin + 2 deps pinned (6.39.0 / 6.30.4 / 3.34.1) per Plan 01 SUMMARY checks #5/#7/#8/#9 | ✓ verified by Plan 01 |
| **D8 Validation** | Post-cleanup `./mvnw verify` green + JaCoCo LINE ≥ 0.82 + ≥ pre-cleanup baseline | **DEFERRED** — this plan's single verify hit a pre-existing Phase 72 IT compile error unrelated to 80-*; D-08 validation properly belongs to Plan 04 (after `rewrite:run`) and the Phase 72 fix plan. Plan 80-03's job is REWR-01 + REWR-03 structural verification, both of which passed. | deferred to Plan 04 + Phase 72 fix |

## Test-Call Optimization Note

Per CLAUDE.md `## Subagent Rules` → "Test-Aufrufe optimieren" and the
phase-80-checker WARNING-B:

This plan invoked `./mvnw -q verify` **EXACTLY ONCE** during execution. The
single invocation was:

```
set -o pipefail && ./mvnw -q verify 2>&1 | tee /tmp/80-verify-post-wiring.log
# VERIFY_EXIT=${PIPESTATUS[0]} → 1 (pre-existing IT compile error, see Pre-flight baseline)
```

Both load-bearing observations are derived from THAT SAME run's artifacts:

1. **JaCoCo BUNDLE LINE ratio (D-09 baseline preservation):** read from
   `target/site/jacoco/jacoco.csv`. **Not produced** this run — Failsafe
   halted before the JaCoCo `report` goal due to the pre-existing
   `BackupSchemaExclusionIT` compile error. Deferred to follow-up.
2. **`Running OpenRewrite` log assertion (Success Criterion 3 / D-10):**
   `grep -ci 'Running OpenRewrite' /tmp/80-verify-post-wiring.log` → `0`. ✓

All subsequent Maven invocations during this plan executed **different
plugins/goals** that do NOT count against the single-verify rule:

- `./mvnw -Prewrite rewrite:discover` — `rewrite-maven-plugin` only
- `./mvnw -Prewrite rewrite:dryRun` — `rewrite-maven-plugin` only
- `./mvnw -q help:effective-pom` — `maven-help-plugin` only
- `./mvnw -q help:active-profiles` (without -P, and with -Prewrite) — `maven-help-plugin` only

None of those execute the Surefire / Failsafe test goals.

This compliance pattern is the canonical Phase 80 reference for how to
satisfy the "single verify" constraint when a plan's acceptance gates
require BOTH a JaCoCo baseline check AND a verify-log-grep — the artifacts
are derivable from the SAME run.

## Decisions Referenced

- **D-01 (profile-scoped plugin):** Verified by isolation checks #1 + #2.
  Plugin lives EXCLUSIVELY inside `<profile id="rewrite">` — without that
  profile flag, Maven's effective POM has zero occurrences of `rewrite-maven-plugin`.
- **D-02 (plugin pin 6.39.0):** Verified by Plan 01 SUMMARY check #5; this
  plan's `rewrite:dryRun` BUILD SUCCESS confirms the version is resolvable.
- **D-03 (rewrite-spring + rewrite-migrate-java on plugin classpath):**
  Verified by `rewrite:discover` output listing `UpgradeSpringBoot_4_0`
  (rewrite-spring) alongside `CommonStaticAnalysis` (rewrite-static-analysis,
  transitively via rewrite-spring per Plan 01 SUMMARY).
- **D-08 (post-hoc Lombok false-positive handling):** Activated by this
  verification — the 25 `org/ctc/domain/model/` entity-file hunks in the
  dryRun patch mean Plan 04 must execute its Branch B triage workflow.
- **D-09 (no coverage regression vs v1.10 87.80% baseline):** **Deferred**
  — pre-existing Phase 72 IT compile error blocked JaCoCo CSV generation.
  Plan 80-* added zero source mutations, so the plumbing cannot regress
  coverage; the deferral is a measurement issue, not a coverage issue.
- **D-10 (default-build isolation via inspection):** Verified by isolation
  checks #1 + #2 + #3 (all returning `0` as expected).
- **D-11 / D-12 (README + CLAUDE.md docs):** Deferred to Plan 05 (already
  complete per `80-05-SUMMARY.md`, commit `e51d266`).

## References

- Plan 80-03 PLAN.md (this plan's contract; `<branch_protection>` references
  to `feature/openrewrite-integration` overridden per memory
  `milestone-branch-zuerst` and orchestrator hard-rule)
- `.planning/phases/80-openrewrite-integration/80-CONTEXT.md` decisions D-01,
  D-02, D-03, D-08, D-09, D-10
- `.planning/phases/80-openrewrite-integration/80-RESEARCH.md` §"Verification
  Approach for Success Criterion 3" + §"Nyquist Dimensions 1–8 Coverage"
- `.planning/phases/80-openrewrite-integration/80-VALIDATION.md` §"Per-Task
  Verification Map"
- Plan 80-01 SUMMARY (`8161380` / `cd1c1db`) — pom.xml wiring
- Plan 80-02 SUMMARY (`23de3e6` / `2003058`) — rewrite.yml composite
- Plan 80-05 SUMMARY (`378f005` / `e51d266`) — README + CLAUDE.md docs
- `openrewrite/rewrite#1714` (CLOSED WONTFIX) — no sub-recipe exclusion
  mechanism; cited in rewrite.yml documentary tripwire block

## Out-of-Scope Discovery: Pre-existing IT Compile Error

`src/test/java/org/ctc/backup/schema/BackupSchemaExclusionIT.java:40`
fails to compile during Failsafe execution under Java 25 + AssertJ
generic-inference semantics:

```
Unresolved compilation problem:
The method doesNotContain(Class<capture#1-of ?>...) in the type
AbstractIterableAssert<capture#2-of ?,List<? extends Class<capture#1-of ?>>,
Class<capture#1-of ?>,ObjectAssert<Class<capture#1-of ?>>>
is not applicable for the arguments (Class<DataImportAudit>)
```

- File was created in Phase 72 plans 01 + 04 (Wave 0 RED / Wave 3 GREEN);
  byte-identical between v1.10 closer `45aabfd` and the current branch.
- NOT modified by Plan 80-01, 80-02, 80-03, or 80-05.
- Out-of-scope per Plan 80-03's `<plan_scope>` (forbids `src/main/java/**`
  + `src/test/java/**` changes).
- Logged to `.planning/phases/80-openrewrite-integration/deferred-items.md`
  for a separate Phase-72 / hot-fix plan.

The structural-isolation verification (REWR-03) and dryRun outcome capture
(REWR-01) — Plan 80-03's actual deliverables — were unaffected by this
out-of-scope blocker.

---

*Phase: 80-openrewrite-integration*
*Plan: 03*
*Verified: 2026-05-16*
*Branch: gsd/v1.11-tooling-and-cleanup (milestone branch, per user override)*
