# Phase 88: Build/Release Unblockers, YAGNI Sweep, Doc-Conventions & Driver-Import Gap-Closure — Research

**Researched:** 2026-05-18
**Domain:** Java 25 / Spring Boot 4.x / Maven `./mvnw verify` build pipeline + GitHub Actions release workflow + Driver-import semantic correctness + Project doc conventions
**Confidence:** HIGH (all factual claims verified against repo HEAD `e117b97196f36f27b41fc8faaf9cb388a86b00f1` on branch `gsd/v1.12-driver-import-and-test-perf` + remote `gh api`/`gh release` queries)

## Summary

Phase 88 is the v1.12 milestone's first phase. It clears 8 baseline-correctness items (CLEAN-01..03, REL-01/02, DOCS-01, DRIV-01/02) before the PERF phases (89–91) measure anything. Six themen-gebundelte Plans run **sequentially** per [[wave-pause]]; no parallel waves. All work lands on the existing milestone branch `gsd/v1.12-driver-import-and-test-perf` as atomic per-plan commits; the milestone PR squash-merges to `master`.

The research surfaces three non-obvious realities the planner MUST honor:
1. **CLEAN-01 is verify-only**, not a source fix: `./mvnw clean test-compile` already exits 0 at HEAD; the Phase-80 `deferred-items.md` (2026-05-16) proved the apparent `BackupSchemaExclusionIT.java:40` AssertJ error is an Eclipse JDT IDE-cache artifact (`"Unresolved compilation problem"` is never a javac signature). A source-level `@SuppressWarnings("unchecked")` or typed-witness is explicitly forbidden by [[clean-maven-build-authority]].
2. **DRIV-01/DRIV-02 must re-introduce surfaces that Phase 70 D-05/D-09 deliberately removed.** The current `DriverSheetImportService` has NO `regularPhase` parameter, NO `TEAM_NOT_IN_REGULAR_PHASE` warning, NO `usesGroups` flag on `TabPreview`, and the controller/template have NO `showGroupColumn` model attribute. Phase 70 decommissioned those branches as "dead" under the parent-always-wins policy. DRIV-01 inverts the precedence (sub-team-with-PhaseTeam in target REGULAR phase wins, parent-precedence becomes legacy fallback), so the warning + per-tab GROUPS gate must be **re-introduced**, not modified.
3. **REL-02 cleanup target is smaller than CONTEXT-Md suggested.** Of the 4 named legacy short-form tags (`v1.5`, `v1.6`, `v1.8`, `v1.9`), only **`v1.3`, `v1.5`, `v1.8`** still exist on the remote (verified via `gh api /repos/jegr78/ctc-manager/git/refs/tags`). `v1.6` and `v1.9` short-form are already absent. v1.10.0 + v1.11.0 are confirmed missing (no remote tag, no GitHub Release, no GHCR image).

**Primary recommendation:** Plan-01 = pure verification + REQUIREMENTS.md text edit. Plans 02–05 = code/template/test/workflow edits with explicit grep/dry-run acceptance gates. Plan-06 = pure operator-runbook (`docs/operations/release-runbook.md`) with destructive-op confirmation prompts per CLAUDE.md "Subagent Rules"; the runbook is executed by the operator after the milestone PR squash-merges, NOT by an agent.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Build/verify gate (CLEAN-01) | Build tool (Maven) | Researcher (REQUIREMENTS.md text edit) | `./mvnw verify` exit code is the authoritative gate; documentation reflects the gate, never substitutes for it |
| YAGNI test deletions (CLEAN-02 a/b) | Test code (`src/test/java/**`) | — | Pure test-code surgery; no production-code touch; coverage delta verified via JaCoCo CSV |
| Windows-conditional removal (CLEAN-02 c) | Test code (Failsafe IT) | — | Behaviour change at the test boundary only; production `BackupImportService` D-19 "best-effort cleanup" contract unchanged |
| Baseline-utility refactor (CLEAN-03) | Test-scope helper class (`src/test/java/org/ctc/sitegen/`) | Documentation (`docs/testing/` or sitegen README) | Replaces `@Test @Disabled` anti-pattern with `main()`/CommandLineRunner; not a Spring component in `src/main/` |
| Release workflow hardening (REL-01) | CI workflow (`.github/workflows/release.yml`) | bash parser | All side-effecting `run:` steps gated by `if: inputs.dry_run != true`; SemVer-strict tag-sort replaces `git describe` |
| Retroactive release publish (REL-02) | Operator runbook (`docs/operations/release-runbook.md`) | Remote git API (`gh release create --target SHA`), Docker registry (`ghcr.io`) | NEVER local `git tag` per [[no-local-git-tags]]; throwaway worktree builds the historical JAR/image |
| Doc convention (DOCS-01) | `CLAUDE.md` Conventions section | grep-regression fence over `.planning/*.md` | Single-file content edit + a grep gate the planner verifies in VERIFICATION.md |
| Season-aware resolver (DRIV-01) | Service (`org.ctc.dataimport.DriverSheetImportService`) | Repository (`PhaseTeamRepository`, already exposed) | Pure service-layer semantic refinement; repository surface unchanged |
| GROUPS-layout warning gate (DRIV-02) | Service (warning emission + `TabPreview` record) + Controller (`DriverSheetImportController`) + Template (`driver-import-preview.html`) | — | Dual-surface fix: service computes per-tab `usesGroups`; template gates per-row Group cell on `${tab.usesGroups()}`; controller aggregates page-wide `showGroupColumn` for backwards compat |

## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Six themen-gebundelte Plans, ALL sequential (no parallel waves). Order: 01 CLEAN-01 → 02 CLEAN-02+03 → 03 REL-01 → 04 DOCS-01 → 05 DRIV-01+02 → 06 REL-02. Honors [[wave-pause]] discipline.
- **D-02:** CLEAN-01 downgrades to **verify-only**. No source-level `@SuppressWarnings("unchecked")` or typed-witness hack. Plan-01 runs `./mvnw clean verify` on the v1.12 PR-branch head, confirms JaCoCo CSV is generated, updates REQUIREMENTS.md CLEAN-01 entry to `Status: Resolved (Phase 80 JDT-cache diagnosis 2026-05-16; verified clean on v1.12 head 2026-05-18)`. The Phase-80 `deferred-items.md` is the cross-reference fence.
- **D-03:** Plan-02 covers CLEAN-02 (a/b/c) + CLEAN-03 in a single plan. Acceptance: `grep -rn "@Disabled" src/test/java` returns 0 AND `grep -rn "Assumptions\." src/test/java` returns 0.
- **D-04:** REL-01 verification via synthetic `workflow_dispatch` input `dry-run: boolean`. Side-effecting steps (`versions:set`, `mvnw verify`, build/tag commit, `git push origin <tag>`, `gh release create`, docker login + build + push, snapshot bump) gated by `if: inputs.dry_run != true`. Determine-Version + Idempotency-Guard + Parser run unconditionally (the new logic must be exercised). Verified on the v1.12 PR-branch with `gh workflow run release.yml -F dry-run=true` BEFORE Plan-03 is merged.
- **D-05:** REL-02 is operator-runbook only — `gh release create --target <SHA>` strictly remote-only per [[no-local-git-tags]]. Throwaway worktree pattern: `git worktree add /tmp/v1.10 45aabfd0` + `./mvnw versions:set -DnewVersion=1.10.0 -DgenerateBackupPoms=false` + `./mvnw -DskipTests package` + `gh release create v1.10.0 --target 45aabfd0 …` + `docker push ghcr.io/jegr78/ctc-manager:1.10.0` + `git worktree remove /tmp/v1.10`. Legacy short-form tag deletion: `gh api -X DELETE /repos/jegr78/ctc-manager/git/refs/tags/<tag>` with per-tag operator confirmation.
- **D-06:** DRIV-01 + DRIV-02 in Plan-05 with two commits: (1) season-aware resolver + test inversion (#16/#17); (2) GROUPS-layout gate + per-tab `usesGroups` + template + new test.
- **D-07:** Legacy fallback when `regularPhase == null` — preserve parent-precedence + WARN log; no `BusinessRuleException` thrown. Phase 66 D-06 legacy semantics preserved.
- **D-08:** CLAUDE.md "Skill Invocation Naming" paragraph documents `/gsd-<name>` (canonical, dash), deprecates `/gsd:<name>` (pre-2026, colon), regression-fence is `grep -r "/gsd:" .planning/*.md` returning 0 hits in top-level active files. Archived `.planning/milestones/v*.x-*.md` is explicitly out of scope. Exact wording is Claude's discretion within these constraints.

### Claude's Discretion

- **CLEAN-03 utility shape:** `CommandLineRunner` Spring bean vs. `main()`-style helper class — planner picks whichever is closer to existing `src/test/java/org/ctc/sitegen/` conventions.
- **DRIV-02 template fallback shape:** non-GROUPS rows render `—`, hide Group cell entirely, or show `n/a` — planner picks (user-facing UX detail).
- **REL-02 runbook step format:** numbered list with `gh`/`docker` commands and confirmation prompts vs. shell-script template — planner picks.
- **CLAUDE.md DOCS-01 paragraph wording:** exact prose within D-08 constraints.
- **CLEAN-02 (b) Test #7 enhancement:** only if post-Plan-02 JaCoCo delta shows actual coverage regression. Default = no additional test.
- **`docs/operations/release-runbook.md` placement:** new file vs. extending `import-runbook.md` (the only file currently in `docs/operations/`).

### Deferred Ideas (OUT OF SCOPE)

- `@SuppressWarnings("unchecked")` / typed-witness preemptive guard on `BackupSchemaExclusionIT` — rejected (D-02). Source-level fix to an IDE-cache problem contradicts [[clean-maven-build-authority]].
- Pre-emptive Test #7 enhancement for the deleted CLEAN-02 (b) regression-fence — only if JaCoCo delta shows regression.
- Strict `BusinessRuleException` for `regularPhase == null` in DRIV-01 — rejected (D-07).
- Aggressive parallel Wave execution — rejected (D-01).
- Throwaway one-shot `retroactive-release.yml` workflow for REL-02 — rejected (D-05).
- PERF-related work — Phases 89–91.
- UX-01 (Google API typed-exception hierarchy) — stretch, Phase 91 or v1.13.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CLEAN-01 | `./mvnw verify` exits 0; JaCoCo CSV generated | Verified `./mvnw clean test-compile` exits 0 at HEAD `e117b971` (this session, line "[PLAT-07 build-guard] OK"). Phase-80 `deferred-items.md` documents JDT IDE-cache root cause. |
| CLEAN-02 | YAGNI sweep: delete 2 disabled tests + simplify 1 Windows-conditional | 4 `@Disabled`/`Assumptions.*` sites located with file:line below. Production paths unaffected. |
| CLEAN-03 | `SiteGeneratorBaselineCaptureTest` → standalone utility | 2 `@Test` methods, both write byte-baselines via `siteGeneratorService.generate()` + `Files.copy(...)`. Bean shape: `CommandLineRunner` or `main()`. |
| REL-01 | Hardened `release.yml` — fetch-tags + SemVer-strict sort + parser + idempotency guard + dry-run gates | Full workflow file read; 9 side-effecting steps identified; current parser at lines 71–83 verified broken on duplicate-tag pattern. |
| REL-02 | Retroactive v1.10.0 + v1.11.0 + legacy-tag cleanup → runbook | SHAs verified: `45aabfd0` v1.10 close (msg: `v1.10: Spring Boot 4.0.6 + Data Export/Import + Docker Noble Pin (#121)`), `598d1431` v1.11 close (msg: `feat(v1.11): tooling infrastructure & tech-debt sweep`). Remote tag inventory verified via `gh api`. |
| DOCS-01 | CLAUDE.md "Conventions" Skill Invocation Naming paragraph + grep fence | Conventions section starts at CLAUDE.md:190 ("## Conventions"). Current `/gsd:` count in 6 active top-level files = 6 (all are documentary references to the deprecated form itself — see DOCS-01 detail below). |
| DRIV-01 | Season-aware resolver `resolveTeamByShortName(shortName, SeasonPhase regularPhase)` | `DriverSheetImportService.java:392-408` resolver body verified. 5 call sites identified at lines **136, 147, 167, 202, 291** (matches CONTEXT.md verbatim). `PhaseTeamRepository.findByPhaseIdAndTeamId(UUID, UUID)` already exposed (`PhaseTeamRepository.java:17`). |
| DRIV-02 | GROUPS-layout gate + per-tab `usesGroups` + template gate | **NB:** the current code (HEAD `e117b971`) has neither a `regularPhase` parameter on `buildTabPreview`, nor `TEAM_NOT_IN_REGULAR_PHASE` warning, nor `usesGroups` on `TabPreview`, nor `showGroupColumn` on the controller/template. Phase 70 D-09 (PROJECT.md Key Decisions) decommissioned these surfaces as "dead branches" under parent-always-wins. DRIV-02 **re-introduces** them now that DRIV-01 makes them legitimate again. |

## Standard Stack

### Core (already in repo — verified versions)

| Library / Tool | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.6 (`<spring-boot.version>` in pom.xml) | App framework | Locked in v1.10 (Phase 71); CLAUDE.md "Technology Stack" |
| Java | 25 (Eclipse Temurin) | Runtime | Locked in v1.10; pom.xml `<java.version>25` |
| JUnit Jupiter | 5.x (Spring Boot–managed) | Test framework | Sole test runner in repo; `@Tag` routing live |
| AssertJ | bundled via `spring-boot-starter-test` | Test assertions | `assertThat(...)` ubiquitous; ~1675 tests |
| Lombok | 1.18.46 | Boilerplate | CLAUDE.md "Lombok Usage" |
| Maven Wrapper | `./mvnw` | Build orchestration | CLAUDE.md "Commands" |
| `gh` CLI | bundled with operator's local install | GitHub API / Releases | CLAUDE.md "Git Workflow" → `gh` for ALL GitHub ops |
| Docker / GHCR | `ghcr.io/jegr78/ctc-manager` | Release image registry | release.yml lines 125–141 |

### No new dependencies introduced

Phase 88 introduces **zero** new third-party libraries. All changes are:
- Pure existing-code edits (DRIV-01/02, CLEAN-02/03 deletions)
- Workflow YAML edits (REL-01)
- Markdown edits (DOCS-01, REL-02 runbook, REQUIREMENTS.md CLEAN-01 entry)
- Operator-action via existing tools (REL-02)

Because no packages are installed, the Package Legitimacy Audit reduces to "no audit required."

## Package Legitimacy Audit

Not applicable — Phase 88 installs zero new packages. No `pom.xml` `<dependency>` additions, no `npm install`, no shell-script downloads. The release.yml hardening (REL-01) only re-orders existing `actions/*` steps and adds bash logic; no new actions are pulled in.

If a future plan adds a dependency (e.g., a Gradle wrapper or a Maven plugin for the baseline-utility refactor in CLEAN-03), Plan-02 must run the standard `mvnw dependency:tree` + slopcheck cycle.

## Architecture Patterns

### System Architecture Diagram (Phase 88 deliverables)

```
                              ┌───────────────────────────────────────────────┐
                              │  Operator (local terminal — post-merge action)│
                              └─────────────────────────┬─────────────────────┘
                                                        │
                                ┌───────────────────────┼───────────────────────┐
                                │                       │                       │
                                v                       v                       v
                    ┌──────────────────┐    ┌────────────────────┐    ┌──────────────────┐
                    │  ./mvnw verify   │    │ release.yml on push│    │ release-runbook  │
                    │  (CLEAN-01..03)  │    │ to master (REL-01) │    │ gh CLI (REL-02)  │
                    └──────────────────┘    └────────────────────┘    └──────────────────┘
                            │                       │                          │
                            │                       │ workflow_dispatch        │
                            v                       │   -F dry-run=true ──┐    │
                    Surefire + Failsafe             v                     │    v
                    + JaCoCo CSV          Determine-Version step          │  gh release create
                                          (unconditional)                 │   --target SHA
                                                  │                       │
                                                  v                       │
                                          Idempotency Guard               │
                                          (git rev-parse v$N^{})          │
                                                  │                       │
                                                  v                       │
                                          versions:set + verify ──────────┘ (skipped on dry-run)
                                                  │
                                                  v
                                          tag + gh release + docker push
                                                  │
                                                  v
                                          GHCR :version :latest

                    ┌─────────────────────────────────────────────────────────┐
                    │ HTTP-time driver-import preview (DRIV-01, DRIV-02)      │
                    └─────────────────────────────────────────────────────────┘
                            │
                            v
                    DriverSheetImportController.preview
                    (admin/drivers/import/preview)
                            │
                            v
                    DriverSheetImportService.preview
                            │
                            v
                    for each tab → buildTabPreview(spreadsheetId, tabName)
                            │
                            ├── resolveSeason → suggestedSeasonId
                            │
                            ├── if suggestedSeasonId != null →
                            │     SeasonPhaseService.findByType(seasonId, REGULAR) → Optional<SeasonPhase>
                            │     regularPhase = optional.orElse(null)
                            │     usesGroups = regularPhase != null && layout == GROUPS
                            │
                            ├── for each data row →
                            │     resolveTeamByShortName(shortName, regularPhase)  ← DRIV-01
                            │       size==1 → return it
                            │       size>1  → prefer candidate with PhaseTeam(regularPhase.id, c.id)
                            │                  fallback → parent-precedence (DRIV-01 D-07)
                            │
                            └── if usesGroups → emit TEAM_NOT_IN_REGULAR_PHASE warning      ← DRIV-02
                                                + lookup PhaseTeam.group → resolvedGroupName
                                else        → skip warning + skip lookup (suppression)

                    TabPreview record gains `boolean usesGroups`
                            │
                            v
                    driver-import-preview.html: per-row Group cell gated by ${tab.usesGroups()}
                    Controller aggregates page-wide showGroupColumn = anyMatch(tab.usesGroups())
                            │
                            v
                    Admin sees Group column ONLY for GROUPS-layout tabs
```

### Component Responsibilities

| File | Responsibility | Phase 88 Plan |
|------|---------------|---------------|
| `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` | Resolver + buildTabPreview + usesGroups computation | Plan-05 |
| `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` | Page-wide `showGroupColumn` aggregation (NEW model attribute) | Plan-05 |
| `src/main/resources/templates/admin/driver-import-preview.html` | Per-row Group cell + per-tab gate | Plan-05 |
| `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` | Tests #16 + #17 inversion + new GROUPS-with-missing-PhaseTeam test + new no-PhaseTeam-fallback test | Plan-05 |
| `src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java:345-375` | DELETE `givenPreExistingDriverNotMatchedByMatcher_whenExecuteNewDriverRow_thenReusesExistingDriver` + STRUCTURAL `@Disabled` comment block at lines 333-346 | Plan-02 |
| `src/test/java/org/ctc/sitegen/StandingsPageGeneratorTest.java:247-251` | DELETE `givenGroupsSwissLayoutSeason_whenGeneratePerGroup_thenShowBuchholzColumn` | Plan-02 |
| `src/test/java/org/ctc/sitegen/SiteGeneratorBaselineCaptureTest.java` (entire file) | DELETE `@Test @Disabled` shell; create `src/test/java/org/ctc/sitegen/util/SiteGeneratorBaselineRefresh.java` (Claude's-discretion shape) | Plan-02 |
| `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportFailureIT.java:198-209` + import on `:20` + helper on `:285` | SIMPLIFY: drop `if (isWindows())` branch, drop `Assumptions` import, keep POSIX assertion unconditional, drop the `private static boolean isWindows()` helper if no other call site | Plan-02 |
| `.github/workflows/release.yml` (entire file) | All hardening per D-04 | Plan-03 |
| `CLAUDE.md` (after current line 220 "CSS Guidelines" section or new ## sub-heading) | DOCS-01 "Skill Invocation Naming" paragraph | Plan-04 |
| `.planning/REQUIREMENTS.md` line 30 (CLEAN-01 paragraph) | Status flip Open → Resolved with cross-ref | Plan-01 |
| `docs/operations/release-runbook.md` (NEW or extension of `import-runbook.md`) | Operator runbook for REL-02 | Plan-06 |

### Pattern 1: Sequential Plan Execution with Plan-Local Verification Gate

**What:** Each plan ends with its own gate before the next plan starts. Per [[wave-pause]], the executor halts at each plan boundary for user OK.
**When to use:** Multi-plan phase where later plans depend on earlier plans being green (CLEAN-01 verify-only must pass before any further edits; REL-01 dry-run must succeed before REL-02 runbook is meaningful).
**Plan-local gate examples:**
```bash
# Plan-01 gate:
./mvnw clean verify && ls -lh target/site/jacoco/jacoco.csv

# Plan-02 gate (CLEAN-02+03):
grep -rn "@Disabled" src/test/java     # must return 0 hits
grep -rn "Assumptions\." src/test/java # must return 0 hits
./mvnw clean verify

# Plan-03 gate (REL-01):
gh workflow run release.yml --ref gsd/v1.12-driver-import-and-test-perf -F dry-run=true
gh run watch --exit-status $(gh run list --workflow=release.yml --limit=1 --json databaseId -q '.[0].databaseId')

# Plan-04 gate (DOCS-01):
grep -r "/gsd:" .planning/PROJECT.md .planning/STATE.md .planning/ROADMAP.md \
                .planning/REQUIREMENTS.md .planning/MILESTONES.md .planning/RETROSPECTIVE.md \
                | grep -v "documenting that" | grep -v "deprecated" | grep -v "colon" \
                | grep -v 'inline-sweep that landed' | grep -v 'cleared the 16' | wc -l   # must return 0

# Plan-05 gate (DRIV-01+02):
./mvnw -Dtest='DriverSheetImportServiceTest' test
./mvnw -Dit.test='DriverSheetImportServiceIT' failsafe:integration-test failsafe:verify
./mvnw clean verify    # final full-suite gate after Plan-05 per [[clean-build-test-only]]

# Plan-06 gate (REL-02 runbook): documentation only, no automated gate.
#   Runbook execution happens POST-merge by operator; runbook itself must be reviewable.
```

### Pattern 2: Strict Remote-Only Tagging via `gh release create --target <SHA>`

**What:** REL-02 retroactively publishes v1.10.0 + v1.11.0 without ever invoking `git tag -a` locally. The `gh release create --target <SHA>` call creates the remote tag AND the GitHub Release in a single API operation.
**When to use:** Retroactive release that must respect [[no-local-git-tags]].
**Example for v1.10.0 (SHA `45aabfd0`):**
```bash
# Step 1 — Build historical JAR in throwaway worktree (clean tree)
git worktree add /tmp/v1.10-build 45aabfd0
cd /tmp/v1.10-build
./mvnw versions:set -DnewVersion=1.10.0 -DgenerateBackupPoms=false
./mvnw -DskipTests package
ls -lh target/ctc-manager-1.10.0.jar   # confirm artifact

# Step 2 — Publish remote tag + GitHub Release + attach JAR
gh release create v1.10.0 \
  --target 45aabfd0 \
  --title "v1.10.0" \
  --notes-start-tag v1.9.0 \
  --generate-notes \
  target/ctc-manager-1.10.0.jar

# Step 3 — Build + push Docker image
docker build -t ghcr.io/jegr78/ctc-manager:1.10.0 .
echo $GITHUB_TOKEN | docker login ghcr.io -u jegr78 --password-stdin
docker push ghcr.io/jegr78/ctc-manager:1.10.0

# Step 4 — Cleanup
cd /Users/jegr/Documents/github/ctc-manager
git worktree remove /tmp/v1.10-build

# Repeat for v1.11.0 (SHA 598d1431, --notes-start-tag v1.10.0)
```

**Critical ordering:** `versions:set` MUST run BEFORE `package`; otherwise the JAR carries `1.11.0-SNAPSHOT` (the current pom.xml version on master) instead of `1.10.0`. Verified via `./mvnw -DforceStdout help:evaluate -Dexpression=project.version` returning `1.11.0-SNAPSHOT` at current HEAD.

### Pattern 3: `workflow_dispatch` Dry-Run Gate

**What:** Add `workflow_dispatch:` trigger with a `dry-run: boolean` input to `release.yml`. Gate every side-effecting step with `if: inputs.dry_run != true && steps.version.outputs.should_skip != 'true'`.
**Example workflow_dispatch declaration (Plan-03):**
```yaml
on:
  push:
    branches: [master]
  workflow_dispatch:
    inputs:
      dry-run:
        description: 'Dry-run: exercise version determination + idempotency guard, skip all side effects.'
        required: false
        type: boolean
        default: false
```

**Critical:** `${{ inputs.dry-run }}` resolves to `false` (string) on the `push:` trigger because the input is not provided. The condition `if: inputs.dry-run != true` works in both contexts (push: input absent → not true → step runs; workflow_dispatch with dry-run=true → step skipped).

### Pattern 4: Season-Aware Resolver Algorithm (DRIV-01)

**Reference:** `.planning/debug/deferred/shortname-resolver-picks-parent-without-phaseteam.md` § Resolution `suggested_algorithm`.

```java
private Optional<Team> resolveTeamByShortName(String shortName, SeasonPhase regularPhase) {
    List<Team> matches = teamRepository.findAllByShortName(shortName);
    if (matches.isEmpty()) {
        return Optional.empty();
    }
    if (matches.size() == 1) {
        return Optional.of(matches.get(0));
    }
    // DRIV-01: prefer candidate with PhaseTeam in target REGULAR phase
    if (regularPhase != null) {
        for (Team c : matches) {
            if (phaseTeamRepository.findByPhaseIdAndTeamId(regularPhase.getId(), c.getId()).isPresent()) {
                return Optional.of(c);
            }
        }
    }
    // D-07 legacy fallback: parent-precedence
    Optional<Team> parent = matches.stream()
            .filter(t -> t.getParentTeam() == null)
            .findFirst();
    if (parent.isPresent()) {
        return parent;
    }
    log.warn("Multiple teams share shortName '{}' with no parent in target phase — picking first deterministically (data-integrity issue)", shortName);
    return Optional.of(matches.get(0));
}
```

**5 call-site update map** (each line currently passes only `row.teamShortName()`/`rawTeamCode`):

| File line | Calling context | How to obtain `regularPhase` |
|-----------|----------------|------------------------------|
| `DriverSheetImportService.java:136` | `execute()` NEW_DRIVER loop, inside `for (NewDriverRow row : tab.newDrivers())`, `Season season` in scope at line 116 | Resolve once per tab via `seasonPhaseService.findByType(season.getId(), PhaseType.REGULAR).orElse(null)` BEFORE the row loops (line ~120, after `season` is bound). Pass `regularPhase` into the 5 inner-loop call sites |
| `DriverSheetImportService.java:147` | `execute()` NEW_ASSIGNMENT loop, `season` in scope | Same per-tab `regularPhase` variable |
| `DriverSheetImportService.java:167` | `execute()` CONFLICT loop, `season` in scope | Same per-tab `regularPhase` variable |
| `DriverSheetImportService.java:202` | `execute()` FUZZY_SUGGESTION loop, `season` in scope | Same per-tab `regularPhase` variable |
| `DriverSheetImportService.java:291` | `buildTabPreview()` UNKNOWN_TEAM_CODE step (Step 3, line 291: `Optional<Team> teamOpt = resolveTeamByShortName(rawTeamCode);`) | Already has `suggestedSeasonId` at line 244–252. Add `SeasonPhase regularPhase = suggestedSeasonId != null ? seasonPhaseService.findByType(suggestedSeasonId, PhaseType.REGULAR).orElse(null) : null;` once at the top of `buildTabPreview` (after season resolution) |

**Dependency injection:** Add `private final SeasonPhaseService seasonPhaseService;` to the constructor-injected fields and `private final PhaseTeamRepository phaseTeamRepository;` (resolver dependency). Both already exist as Spring beans; `@RequiredArgsConstructor` does the wiring.

### Pattern 5: GROUPS-Layout Gate + Per-Tab `usesGroups` Flag (DRIV-02)

**TabPreview record extension** (current at `DriverSheetImportService.java:414-426`):
```java
public record TabPreview(
    String tabName,
    int year,
    Integer number,
    UUID suggestedSeasonId,
    String ambiguousReason,
    boolean usesGroups,                  // NEW — DRIV-02
    List<NewDriverRow> newDrivers,
    List<NewAssignmentRow> newAssignments,
    List<ConflictRow> conflicts,
    List<FuzzySuggestionRow> fuzzySuggestions,
    List<UnchangedRow> unchanged,
    List<ErrorRow> errors
) {}
```

`buildTabPreview` computes `usesGroups = regularPhase != null && regularPhase.getLayout() == PhaseLayout.GROUPS` once per tab, passes it into the `new TabPreview(...)` constructor at the current line 359. Skip warning emission + PhaseTeam lookup entirely when `!usesGroups`.

**Controller change** (`DriverSheetImportController.java` after line 47):
```java
model.addAttribute("showGroupColumn",
        preview.tabPreviews().stream().anyMatch(TabPreview::usesGroups));
```

**Template change** (`driver-import-preview.html`): Per-row Group cell gated by `th:if="${tab.usesGroups()}"`. Page-wide `${showGroupColumn}` stays as a header-level gate (so the column header only appears if at least one tab uses groups; per-row content is suppressed for non-GROUPS rows even within a mixed multi-tab preview). User-discretion fallback: `—` text.

### Anti-Patterns to Avoid

- **`@Test @Disabled` for manual utilities** (CLEAN-03 closes this): Disabled tests force the "remove @Disabled, run, re-add @Disabled" cycle and pollute the `@Test` semantic surface. Use `CommandLineRunner` (Spring-context-aware, invoked via `./mvnw exec:java -Dexec.mainClass=…`) or a standalone `main()` class.
- **Source-level `@SuppressWarnings("unchecked")` to fix an IDE-cache hallucination** (CLEAN-01 D-02 forbids this): When Maven output contains `"Unresolved compilation problem"`, that string is an Eclipse JDT signature, never produced by javac. Diagnose with `./mvnw clean test-compile` first; only edit source if javac itself complains.
- **Local `git tag -a vX.Y.0 && git push origin vX.Y.0` for retroactive releases** ([[no-local-git-tags]] forbids this): use `gh release create --target <SHA>` instead.
- **Page-wide model attribute as the sole gate for per-tab UI state** (DRIV-02 fixes this): a page-wide `showGroupColumn` cannot distinguish between mixed-tab previews. Always carry per-tab state on the record and aggregate at the page level only for header-row visibility.
- **`Assumptions.assumeFalse(true, …)`** (CLEAN-02 c removes this): asserting `true` is always-skip — equivalent to no test. If a platform genuinely is unsupported, gate the entire test class with `@EnabledOnOs(OS.LINUX | OS.MAC)`; if the platform was speculative (no other Windows-aware code in the codebase, CI runs ubuntu-latest, dev runs darwin), DELETE the conditional.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Resolve REGULAR phase for a season | Custom JPA query / direct repository call | `seasonPhaseService.findByType(seasonId, PhaseType.REGULAR)` (`SeasonPhaseService.java:74`) | Returns `Optional` — perfect for `regularPhase == null` graceful fallback per D-07. The strict `findRegularPhase(UUID)` throws `EntityNotFoundException` (line 65–68) — wrong shape for our use case. |
| Lookup PhaseTeam by phase + team | Inline JPA query / loop over `findByPhaseId(...)` | `phaseTeamRepository.findByPhaseIdAndTeamId(UUID, UUID)` (`PhaseTeamRepository.java:17`) | Already exposed and used elsewhere; returns `Optional<PhaseTeam>`. No new repository surface. |
| Pre-filter teams by shortName | New query | `teamRepository.findAllByShortName(String) → List<Team>` (existing, used at `DriverSheetImportService.java:393`) | Already the canonical entry point; only the multi-match resolution policy changes. |
| Sort tags by SemVer | Custom bash sorting / `sort -V` | `git tag --sort=-version:refname --list 'v[0-9]*.[0-9]*.[0-9]*' \| head -1` | Strict 3-part SemVer regex ignores legacy short-form `v1.5`, `v1.8` etc.; deterministic; native to git. |
| Generate release notes between two SHAs | Custom commit log parser | `gh release create … --notes-start-tag <prev> --generate-notes` | Auto-generates release notes from Conventional Commits between the start tag and target SHA. Standard `gh` workflow. |
| Build historical version of the app | Reset master to old SHA + build + re-tag | `git worktree add /tmp/<vX> <SHA>` + isolated `versions:set` + `package` | Keeps the v1.12 branch clean; worktree is throwaway. Verified `gsd/v1.12-driver-import-and-test-perf` not affected by historical worktree. |
| Suppress dependent CI steps in a workflow | Comment out + uncomment manually | `if: inputs.dry_run != true` step-level guards | Idempotent, reversible, exercised in unit-test-equivalent fashion via `workflow_dispatch -F dry-run=true`. |
| Delete a tag from the remote when no Release exists | `git push origin :refs/tags/v1.5` | `gh api -X DELETE /repos/jegr78/ctc-manager/git/refs/tags/v1.5` | `gh release delete <tag>` only works if a Release exists. The git/refs/tags API endpoint is the unambiguous primitive for orphan tags (legacy short-form tags have no associated GitHub Release — verified via `gh release list`). |

**Key insight:** Phase 88 has 0 NIH risk because every problem already has a project-standard solution. The work is glue/orchestration, not invention.

## Runtime State Inventory

> Required for rename/refactor/migration phases. Phase 88 is a mixed phase: REL-01/REL-02 affect release-pipeline runtime state; DRIV-01/02 are code-only.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — Phase 88 does not touch entity persistence. DRIV-01/02 changes affect the preview/execute *output* but never alter the persistence path of `SeasonDriver`, `Driver`, `PhaseTeam`, or `Team`. The PhaseTeam table is only READ, never written, by the resolver. | None |
| Live service config | **GHCR registry `ghcr.io/jegr78/ctc-manager`** is missing tags `1.10.0` and `1.11.0` (verified via `gh release list` — last release is `v1.9.0` 2026-05-09). REL-02 docker-push from historical worktree closes this. **GitHub Releases** are missing entries for v1.10.0 + v1.11.0; REL-02 `gh release create --target` closes this. | REL-02 runbook (operator action post-merge) |
| OS-registered state | None — no Windows Task Scheduler, no launchd plists, no systemd units. The Phase 78 noble-pin docker image is registered with GHCR by tag but does not require OS-level cleanup. | None |
| Secrets / env vars | `RELEASE_TOKEN` (PAT) and `GITHUB_TOKEN` (workflow auto-injected) referenced in release.yml lines 23, 117. REL-01 hardening does NOT change either secret. REL-02 operator-runbook requires `GITHUB_TOKEN` for `docker login ghcr.io` (operator's local PAT). | None |
| Build artifacts | **pom.xml `<version>` = `1.11.0-SNAPSHOT`** (verified via `./mvnw help:evaluate -Dexpression=project.version` returning `1.11.0-SNAPSHOT`). The manual bump (commit `87daec68`) jumped past 1.8/1.9/1.10 because all 3 release workflows failed. After REL-02 v1.11.0 is published retroactively, the post-merge auto-bump in release.yml (Plan-03 hardened version) will produce `v1.12.0` for this milestone. **No action in Phase 88 itself** — this is normal release-pipeline behaviour after REL-01 lands. | None — pom.xml stays `1.11.0-SNAPSHOT` through end of Phase 88; release.yml bumps on first post-merge run after milestone close |

**Legacy tag inventory** (verified `gh api /repos/jegr78/ctc-manager/git/refs/tags` 2026-05-18):
- Present short-form (REL-02 deletes): **`v1.3`**, **`v1.5`**, **`v1.8`** (3 tags, NOT 4 as CONTEXT-D-05 suggested)
- Already absent: `v1.6`, `v1.9` (CONTEXT-D-05 listed these but they are not on the remote — both already cleaned up at some point, or never created in short-form)
- Missing semver releases: `v1.10.0`, `v1.11.0` (REL-02 creates)
- All `v1.X.Y` 3-part semver tags v1.0.0..v1.9.0 are present and SHOULD NOT be touched

**Re-validation step for the planner:** Plan-06 MUST start with `gh api /repos/jegr78/ctc-manager/git/refs/tags | jq -r '.[].ref'` to re-confirm the legacy-tag set at execution time (the inventory could shift between research and execution).

## Common Pitfalls

### Pitfall 1: VS Code JDT Stale `.class` Files Surface as Phantom Compile Errors

**What goes wrong:** A `BackupSchemaExclusionIT.java:40` "compile error" message appears in `./mvnw verify` output — but the message says `"Unresolved compilation problem"` which is an Eclipse JDT compiler signature, never produced by Maven's javac.
**Why it happens:** VS Code's Java Language Server writes `.class` files into `target/test-classes/` with embedded `"Unresolved compilation problem"` markers. If `mvnw verify` ran without a preceding `clean`, Failsafe loads the stale `.class` and surfaces the marker at runtime.
**How to avoid:** ALWAYS run `./mvnw clean test-compile` as the first diagnostic step when encountering this error. If javac actually fails, fix the source. If `clean test-compile` succeeds, the error was IDE-cache only — proceed with `./mvnw clean verify` and the issue is gone. **[[clean-maven-build-authority]] memory rule.**
**Warning signs:** Exact string `"Unresolved compilation problem"` in Maven output. javac would say `error: cannot find symbol`, `error: incompatible types`, etc.

### Pitfall 2: `git describe --tags --abbrev=0` Returns Non-SemVer Short-Form Tags

**What goes wrong:** On the current master, `git describe --tags --abbrev=0` may return `v1.9` (legacy short-form) instead of `v1.9.0` (semver). The bash parser at release.yml:71-83 does `IFS='.' read -r MAJOR MINOR PATCH <<< "${LAST_TAG#v}"`, which on `1.9` leaves `PATCH` empty/null. The next minor-bump produces `1.10.` (trailing dot) or `1.10` — neither matches the expected pattern, AND if it accidentally matches an existing tag (`v1.10` short-form, if present) the subsequent `git tag` fails with `fatal: tag 'v1.10' already exists` (exit 128) after the 19-minute build.
**Why it happens:** `git describe` walks tag history in reachability order, not version order. Legacy short-form tags (`v1.5`, `v1.8`) coexist with `vX.Y.Z` semver tags.
**How to avoid (REL-01 D-04):**
- Replace `git describe --tags --abbrev=0` with `git tag --sort=-version:refname --list 'v[0-9]*.[0-9]*.[0-9]*' | head -1` (strict 3-part SemVer pattern, deterministic)
- Add `fetch-tags: true` to `actions/checkout@v6` step (without it, `git tag` returns empty in CI even when remote has tags — `fetch-depth: 0` does NOT imply `fetch-tags: true` on GHA `actions/checkout` v4+)
- Default PATCH=0 in the parser: `IFS='.' read -r MAJOR MINOR PATCH <<< "$VERSION"; PATCH="${PATCH:-0}"`
- Validate `MAJOR` and `MINOR` are numeric before arithmetic: `[[ "$MAJOR" =~ ^[0-9]+$ && "$MINOR" =~ ^[0-9]+$ ]] || { echo "::error::Invalid version $LAST_TAG"; exit 1; }`
- Pre-`versions:set` idempotency guard BEFORE the 19-minute build:
  ```bash
  if git rev-parse "v${NEW_VERSION}^{}" >/dev/null 2>&1; then
    echo "::error::Tag v${NEW_VERSION} already exists — aborting before build to save CI minutes."
    exit 1
  fi
  ```

**Warning signs:** Release workflow exit 128 with `fatal: tag 'vX.Y.0' already exists`. Verified failed runs (from CONTEXT-line 129): 26044380205 (v1.11), 25955094759 (v1.10), 25609204039 (v1.9), 24925033178 (v1.8 final).

### Pitfall 3: `versions:set` Runs After `package` in Retroactive Build

**What goes wrong:** Operator runs `./mvnw -DskipTests package` in the worktree without preceding `versions:set`, so the JAR is named `ctc-manager-1.11.0-SNAPSHOT.jar` (the current master pom.xml version) instead of `ctc-manager-1.10.0.jar`. The subsequent `gh release create … target/ctc-manager-1.10.0.jar` fails with "file not found"; if the operator manually corrects the filename, the JAR's `META-INF/MANIFEST.MF` advertises the wrong version.
**Why it happens:** The historical SHA's pom.xml may not match the desired release version, and the `versions:set` plugin must rewrite pom.xml in the worktree BEFORE `package` reads it.
**How to avoid:** REL-02 runbook MUST sequence `versions:set` BEFORE `package`, EVERY time, with explicit operator confirmation that pom.xml version is correct before package runs:
```bash
./mvnw versions:set -DnewVersion=1.10.0 -DgenerateBackupPoms=false
grep "<version>1.10.0</version>" pom.xml || { echo "POM version not set"; exit 1; }
./mvnw -DskipTests package
ls target/ctc-manager-1.10.0.jar || { echo "JAR not produced at expected path"; exit 1; }
```

### Pitfall 4: `gh api -X DELETE` Is Irreversible — No Soft-Delete on Git Refs

**What goes wrong:** Operator runs `gh api -X DELETE /repos/jegr78/ctc-manager/git/refs/tags/v1.5` without per-tag confirmation, accidentally deletes a tag that some external system (CI artifact ref, downstream pin) was tracking.
**Why it happens:** The `git/refs/tags` API endpoint is a hard delete. There is no soft-delete or undo.
**How to avoid:** REL-02 runbook MUST require per-tag confirmation:
```bash
# Per-tag prompt — operator types "yes" to confirm each
for tag in v1.3 v1.5 v1.8; do
  read -p "Delete legacy short-form tag '$tag' (irreversible)? [type 'yes'] " yn
  [[ "$yn" == "yes" ]] || { echo "Skipped $tag"; continue; }
  gh api -X DELETE "/repos/jegr78/ctc-manager/git/refs/tags/$tag"
  echo "Deleted $tag"
done
```
Per CLAUDE.md "Subagent Rules" "Branch Protection" + general destructive-op caution: any `-X DELETE`/`--force` op MUST be explicitly user-confirmed inside the runbook, not silently scripted.
**Warning signs:** Operator copy-pastes a bash loop without re-reading the tag list.

### Pitfall 5: `if: inputs.dry_run != true` Misfires on `push:` Trigger

**What goes wrong:** Operator writes `if: ${{ inputs.dry_run == false }}` (positive form) — but on the `push:` trigger, `inputs.dry_run` is `null`/unset, so `null == false` is falsy and the step is skipped on every push.
**Why it happens:** GitHub Actions `inputs.<name>` is only populated on `workflow_dispatch:`. On `push:`, accessing `inputs.dry_run` returns the empty string, which compares as false against literal `false` via type coercion.
**How to avoid:** Use the NEGATIVE form `if: inputs.dry-run != true` (skip only when explicitly true). This works on both push (input unset → not true → step runs) and workflow_dispatch (dry-run=false → not true → step runs; dry-run=true → step skipped).
**Warning signs:** Push to master produces no release artifacts.

### Pitfall 6: `@Tag` Annotation Missing on a New IT Class

**What goes wrong:** A new `*IT.java` Spring-context integration test (e.g., a future DRIV-02 GROUPS-warning IT) is added without `@Tag("integration")`. Surefire's `excludedGroups=integration,e2e,flaky` does NOT exclude it (it's untagged). Surefire loads `@SpringBootTest` under `forkCount=2C` which is wrong for ITs.
**Why it happens:** TESTING.md "Test Categorization (`@Tag`)" rule is filename-AGNOSTIC; the `@Tag` annotation drives routing.
**How to avoid:** Per CLAUDE.md "Tag Tests by Category": EVERY new `*IT.java` class needs `@Tag("integration")`. Phase 88 Plan-05 may add IT-shape tests — verify on each.
**Warning signs:** Test runs in Surefire output instead of Failsafe; race conditions appear under elevated fork count.

### Pitfall 7: REL-02 Operator Runs From Inside the Worktree, Not From Project Root

**What goes wrong:** Operator does `cd /tmp/v1.10-build && gh release create v1.10.0 …`. `gh` reads `.git/config` to find the repo; the worktree has a `.git` file (NOT a directory) pointing back to `/Users/jegr/.../ctc-manager/.git/worktrees/v1.10-build`. `gh` may or may not resolve the right repo depending on installation. The safer path: stay in the project root for `gh release create` (which only needs the SHA), enter the worktree for `versions:set + package + docker build`.
**How to avoid:** REL-02 runbook explicitly directs the operator about CWD per step.

## Code Examples

### CLEAN-02 (a) Deletion — `StandingsPageGeneratorTest`

**Source:** `src/test/java/org/ctc/sitegen/StandingsPageGeneratorTest.java:247-251`
```java
    /**
     * D-33: Buchholz column appears only on per-group + Swiss-format pages. The current
     * TestDataService has no Swiss + GROUPS fixture (Season 2024 is SWISS but LEAGUE-layout;
     * Season 2023 is GROUPS but ROUND_ROBIN). Disabled with a clear deferral note for Plan 6.
     */
    @Test
    @Disabled("requires Swiss + GROUPS fixture; deferred to Plan 5/6 fixture extension")
    void givenGroupsSwissLayoutSeason_whenGeneratePerGroup_thenShowBuchholzColumn() {
        // Placeholder for the Swiss + GROUPS Buchholz column check.
    }
}
```
**Action:** DELETE lines 242-251 (Javadoc + annotations + method body) AND verify no `import org.junit.jupiter.api.Disabled;` orphan remains. Run `./mvnw clean test-compile` to verify.

### CLEAN-02 (b) Deletion — `DriverSheetImportServiceIT`

**Source:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java:333-375`
```java
    // 8. GAP-70-01 hypothesis 2 — pre-existing Driver classified as NEW_DRIVER.
    // STRUCTURAL @Disabled: DriverMatchingService.findDriver short-circuits on
    // ... [13-line comment block]
    @Disabled("Hypothesis 2 unreachable — see DriverMatchingService.findDriver exact-match short-circuit. "
            + "Task 1 fix is exercised by Test #7 (cross-tab same-PSN). Plan 70-04 Task 3 decision rule.")
    @Test
    void givenPreExistingDriverNotMatchedByMatcher_whenExecuteNewDriverRow_thenReusesExistingDriver() throws IOException {
        // ... method body (lines 348-375)
    }
}
```
**Action:** DELETE lines 333-375 (comment block + annotations + method body). Verify `import org.junit.jupiter.api.Disabled;` (line 19) is now orphaned — also DELETE that import. **Coverage assertion:** the deleted test exercises the `findByPsnId(psnId).orElseGet(...)` branch in `DriverSheetImportService.java:128-135` (NEW_DRIVER computeIfAbsent recovery). The remaining coverage of that branch comes from Test #7 (`givenSameNewDriverPsnInTwoTabs_whenExecute_thenExactlyOneDriverRowInserted`, lines 287-331), which exercises the same `computeIfAbsent` + `findByPsnId` path with a cross-tab cache hit on tab B. JaCoCo CSV delta after Plan-02 should show the branch coverage unchanged. If it regresses, fold Test #7 enhancement (line `194` of CONTEXT) into Plan-02 as a 2-line additional assertion.

### CLEAN-02 (c) Simplification — `AutoBackupBeforeImportFailureIT`

**Source:** `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportFailureIT.java:198-209` (with helper at line 285 and import at line 20)
```java
        if (!newDirs.isEmpty()) {
            Path autoBackupZip = newDirs.get(0).resolve("auto-backup-before-import.zip");
            if (isWindows()) {
                // RESEARCH Pitfall #7 — Windows file-locking may prevent Files.deleteIfExists;
                // the D-19 contract is "best-effort, never throws", NOT "always deletes".
                Assumptions.assumeFalse(true, "Windows file-locking — skipping cleanup assertion");
            } else {
                assertThat(Files.notExists(autoBackupZip))
                        .as("partial auto-backup ZIP must be cleaned up on POSIX (D-19)")
                        .isTrue();
            }
        }
```
**Action (simplification):**
```java
        if (!newDirs.isEmpty()) {
            Path autoBackupZip = newDirs.get(0).resolve("auto-backup-before-import.zip");
            assertThat(Files.notExists(autoBackupZip))
                    .as("partial auto-backup ZIP must be cleaned up (D-19)")
                    .isTrue();
        }
```
Also DELETE:
- Line 20: `import org.junit.jupiter.api.Assumptions;` (no other usages in the file)
- Line 285: `private static boolean isWindows() { ... }` (verify by `grep -n "isWindows()" src/test/java/org/ctc/backup/it/AutoBackupBeforeImportFailureIT.java` — if only the one call site at line 200 was using it, the helper can be removed)

**Coverage impact:** None — the unconditional POSIX branch was already executing on CI (ubuntu-latest) and dev (darwin). Only the Windows-skip branch is removed; it was always-skipped.

### CLEAN-03 Refactor — `SiteGeneratorBaselineCaptureTest` → Standalone Utility

**Source (current):** `src/test/java/org/ctc/sitegen/SiteGeneratorBaselineCaptureTest.java` (92 lines, full read above)

The class has 2 `@Test` methods (`captureLeagueOnlyBaseline`, `captureLeagueOnlyTeamAndDriverProfileBaselines`) that both:
1. Mock `youTubeScraperService.scrapeVideoId(...)` to return a fixed video ID
2. `testDataService.seed()` to load the Season 2026 single-LEAGUE fixture
3. `siteGeneratorService.generate()` to produce the static site under a `@TempDir`
4. `Files.copy(...)` the generated files to `src/test/resources/sitegen/baseline/*.html` (these are the SC4 byte-identity baselines)

**Existing `src/test/java/org/ctc/sitegen/` conventions** (verified `ls`):
- All other files are `@Test`-driven (`SiteGeneratorServiceTest.java`, `SiteGeneratorE2ETest.java`, `SiteGeneratorServiceIT.java`, `SiteGeneratorPhaseAwarenessIT.java`)
- No existing `main()`-class helper or `CommandLineRunner` bean
- Spring-context bootstrap convenience is non-trivial: the class uses `@SpringBootTest`, `@MockitoBean`, `@Autowired TestDataService` (test-scope `@Component` in `org.ctc.admin.TestDataService`). A plain `public static void main(String[] args)` cannot get those beans easily.

**Recommended utility shape:** `CommandLineRunner` Spring bean under `src/test/java/org/ctc/sitegen/util/SiteGeneratorBaselineRefresh.java`, invoked via `./mvnw exec:java -Dexec.mainClass=org.springframework.boot.SpringApplication -Dexec.args="--spring.profiles.active=dev,baseline-refresh"`. The runner runs ONCE on context-bootstrap when the `baseline-refresh` Spring profile is active, then exits via `SpringApplication.exit(...)`. This shape:
- Keeps `@SpringBootTest`'s context-bootstrap convenience (the helper can `@Autowired` `SiteGeneratorService` + `TestDataService` + `SiteProperties` exactly like the disabled test did)
- Replaces the misleading `@Test @Disabled` annotation pair with explicit operator action (`./mvnw exec:java -Dspring.profiles.active=dev,baseline-refresh`)
- Documented in `docs/testing/baseline-refresh.md` (new) or `src/test/java/org/ctc/sitegen/README.md`

**Action skeleton (CommandLineRunner):**
```java
@Slf4j
@Component
@Profile("baseline-refresh")
@RequiredArgsConstructor
public class SiteGeneratorBaselineRefresh implements CommandLineRunner {
    private final SiteGeneratorService siteGeneratorService;
    private final SiteProperties siteProperties;
    private final TestDataService testDataService;
    private final ApplicationContext applicationContext;

    @Override
    public void run(String... args) throws Exception {
        Path tempDir = Files.createTempDirectory("baseline-refresh-");
        siteProperties.setOutputDir(tempDir.toString());
        testDataService.seed();
        siteGeneratorService.generate();

        Path seasonDir = tempDir.resolve("season").resolve("2026-4-regular-season");
        copyBaseline(seasonDir.resolve("standings.html"),
                "src/test/resources/sitegen/baseline/single-league-standings.html");
        copyBaseline(seasonDir.resolve("team").resolve("adr.html"),
                "src/test/resources/sitegen/baseline/single-league-team-profile.html");
        copyBaseline(seasonDir.resolve("driver").resolve("adr-driver01.html"),
                "src/test/resources/sitegen/baseline/single-league-driver-profile.html");

        SpringApplication.exit(applicationContext, () -> 0);
    }
    // ... copyBaseline(...) helper
}
```

**Alternative — plain `main()` shape** (Claude's discretion, only if the planner prefers): use `SpringApplication.run(CtcManagerApplication.class, args)` in `main` with `args` providing `--spring.profiles.active=dev`, then directly fetch beans from the returned context. Adds 5 lines of boilerplate but avoids a new Spring profile.

**Source-marker requirement:** The new utility class needs a top-of-file Javadoc reference to Phase 62 Plan 0 / Plan 4 (so future readers find the SC4 byte-identity invariant context). Existing `youTubeScraperService` mock has to be wired (the live YouTube scraper is non-deterministic — keep `MockitoBean` shape via a `@TestConfiguration` or a `@Profile("baseline-refresh")`-scoped fake bean).

### REL-01 Workflow Hardening — Complete Rewrite of `release.yml` Steps

**Current (`.github/workflows/release.yml:19-23`):**
```yaml
      - name: Checkout
        uses: actions/checkout@v6
        with:
          fetch-depth: 0
          # PAT with repo scope — required to push past branch protection
          token: ${{ secrets.RELEASE_TOKEN }}
```
**Hardened:**
```yaml
      - name: Checkout
        uses: actions/checkout@v6
        with:
          fetch-depth: 0
          fetch-tags: true          # REL-01: required so `git tag` returns remote tags
          # PAT with repo scope — required to push past branch protection
          token: ${{ secrets.RELEASE_TOKEN }}
```

**Current (`.github/workflows/release.yml:32-91` — "Determine version" step):**
```yaml
      - name: Determine version
        id: version
        run: |
          # Check if any tags exist
          if git describe --tags --abbrev=0 2>/dev/null; then
            LAST_TAG=$(git describe --tags --abbrev=0)
            HAS_TAGS=true
          else
            HAS_TAGS=false
          fi
          # ... [lines 42-91]
          # IFS='.' read -r MAJOR MINOR PATCH <<< "$VERSION"   # line 73
          # ... arithmetic ...
```
**Hardened (Plan-03 D-04 — full bash):**
```yaml
      - name: Determine version
        id: version
        run: |
          # REL-01 (a): strict 3-part SemVer pattern, sorted by version not reachability.
          # Ignores legacy short-form tags (v1.5, v1.8, etc.) deliberately.
          LAST_TAG=$(git tag --sort=-version:refname --list 'v[0-9]*.[0-9]*.[0-9]*' | head -1)

          if [ -z "$LAST_TAG" ]; then
            HAS_TAGS=false
          else
            HAS_TAGS=true
          fi
          echo "has_tags=$HAS_TAGS" >> $GITHUB_OUTPUT

          if [ "$HAS_TAGS" = "false" ]; then
            POM_VERSION=$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)
            NEW_VERSION=${POM_VERSION%-SNAPSHOT}
            echo "new_version=$NEW_VERSION" >> $GITHUB_OUTPUT
            echo "Initial release: $NEW_VERSION (from pom.xml $POM_VERSION)"
          else
            echo "last_tag=$LAST_TAG" >> $GITHUB_OUTPUT
            COMMITS=$(git log ${LAST_TAG}..HEAD --pretty=format:"%s")

            if ! echo "$COMMITS" | grep -qE "^(feat|fix|docs|refactor|perf|test|style|chore)(\(.+\))?[!]?:"; then
              echo "should_skip=true" >> $GITHUB_OUTPUT
              echo "No releasable commits found, skipping release"
              exit 0
            fi

            BUMP="patch"
            if echo "$COMMITS" | grep -qE "^feat(\(.+\))?!:|BREAKING CHANGE"; then
              BUMP="major"
            elif echo "$COMMITS" | grep -qE "^feat(\(.+\))?:"; then
              BUMP="minor"
            fi

            # REL-01 (c): parser hardening — default PATCH=0; validate numeric MAJOR/MINOR.
            VERSION=${LAST_TAG#v}
            IFS='.' read -r MAJOR MINOR PATCH <<< "$VERSION"
            PATCH="${PATCH:-0}"
            if ! [[ "$MAJOR" =~ ^[0-9]+$ && "$MINOR" =~ ^[0-9]+$ && "$PATCH" =~ ^[0-9]+$ ]]; then
              echo "::error::Invalid SemVer in last tag '$LAST_TAG' (MAJOR=$MAJOR MINOR=$MINOR PATCH=$PATCH)"
              exit 1
            fi

            case $BUMP in
              major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
              minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
              patch) PATCH=$((PATCH + 1)) ;;
            esac

            NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}"
            echo "new_version=$NEW_VERSION" >> $GITHUB_OUTPUT
            echo "bump=$BUMP" >> $GITHUB_OUTPUT
            echo "Release: $NEW_VERSION ($BUMP bump from $LAST_TAG)"
          fi

          IFS='.' read -r M N P <<< "$NEW_VERSION"
          NEXT_SNAPSHOT="${M}.$((N + 1)).0-SNAPSHOT"
          echo "next_snapshot=$NEXT_SNAPSHOT" >> $GITHUB_OUTPUT

      - name: Idempotency guard — refuse if tag already exists
        if: steps.version.outputs.should_skip != 'true'
        run: |
          # REL-01 (d): refuse BEFORE the 19-minute build
          if git rev-parse "v${{ steps.version.outputs.new_version }}^{}" >/dev/null 2>&1; then
            echo "::error::Tag v${{ steps.version.outputs.new_version }} already exists. Aborting before build."
            exit 1
          fi
          echo "Tag v${{ steps.version.outputs.new_version }} is available."
```

**Dry-run gates on side-effecting steps (lines 93-149 of current file):**

| Current line / step name | Current `if:` | Hardened `if:` |
|--------------------------|---------------|----------------|
| 93 "Set release version in pom.xml" | `if: steps.version.outputs.should_skip != 'true'` | `if: steps.version.outputs.should_skip != 'true' && inputs.dry-run != true` |
| 97 "Build and verify" | `if: steps.version.outputs.should_skip != 'true'` | `if: steps.version.outputs.should_skip != 'true' && inputs.dry-run != true` |
| 101 "Configure git" | `if: steps.version.outputs.should_skip != 'true'` | `if: steps.version.outputs.should_skip != 'true' && inputs.dry-run != true` |
| 107 "Create release commit and tag" | `if: steps.version.outputs.should_skip != 'true'` | `if: steps.version.outputs.should_skip != 'true' && inputs.dry-run != true` |
| 114 "Push tag and create GitHub Release" | `if: steps.version.outputs.should_skip != 'true'` | `if: steps.version.outputs.should_skip != 'true' && inputs.dry-run != true` |
| 125 "Login to GHCR" | `if: steps.version.outputs.should_skip != 'true'` | `if: steps.version.outputs.should_skip != 'true' && inputs.dry-run != true` |
| 133 "Build and push Docker image" | `if: steps.version.outputs.should_skip != 'true'` | `if: steps.version.outputs.should_skip != 'true' && inputs.dry-run != true` |
| 143 "Bump to next SNAPSHOT" | `if: steps.version.outputs.should_skip != 'true'` | `if: steps.version.outputs.should_skip != 'true' && inputs.dry-run != true` |

**`workflow_dispatch` trigger declaration** at top of file (replaces the `on: push:` block currently lines 3-5):
```yaml
on:
  push:
    branches: [master]
  workflow_dispatch:
    inputs:
      dry-run:
        description: 'Dry-run: exercise version determination + idempotency guard, skip versions:set/build/tag/release/docker/snapshot.'
        required: false
        type: boolean
        default: false
```

**Plan-03 verification command** (run from the v1.12 PR-branch BEFORE merge):
```bash
gh workflow run release.yml --ref gsd/v1.12-driver-import-and-test-perf -F dry-run=true
sleep 5
RUN_ID=$(gh run list --workflow=release.yml --limit=1 --json databaseId -q '.[0].databaseId')
gh run watch "$RUN_ID" --exit-status
# Expected: workflow run succeeds; logs show "Release: 1.12.0 (minor bump from v1.11.0)" — wait, v1.11.0 won't exist until REL-02 runs.
#   On the v1.12 PR-branch BEFORE REL-02 publishes v1.10.0/v1.11.0, the last tag is v1.9.0,
#   so dry-run says "Release: 1.10.0 (minor bump from v1.9.0)" — that's expected during the dry-run.
#   After REL-02 publishes v1.11.0 retroactively, a re-run dry-run would say "Release: 1.12.0 (minor bump from v1.11.0)".
```

### DOCS-01 — CLAUDE.md "Skill Invocation Naming" Paragraph

**Insertion point:** After current `CLAUDE.md:220` (the `### CSS Guidelines` section, currently last item before "### Static Analysis"). Add a new `### Skill Invocation Naming` subsection between "CSS Guidelines" and "Static Analysis".

**Suggested paragraph wording** (Claude's discretion per D-08):
```markdown
### Skill Invocation Naming

* **Canonical prefix:** GSD skills are invoked via `/gsd-<name>` (dash, current syntax). Examples: `/gsd-plan-phase`, `/gsd-execute-phase`, `/gsd-validate-phase`, `/gsd-verify-work`, `/gsd-new-milestone`, `/gsd-discuss-phase`, `/gsd-research-phase`.
* **Deprecated prefix:** `/gsd:<name>` (colon, pre-2026 syntax) is no longer recognised. Replace any historical reference in active planning files with the dash form on sight; archived `.planning/milestones/v*.x-*.md` is left untouched (historical immutability).
* **Regression fence:** Active top-level planning files (`.planning/{PROJECT,STATE,ROADMAP,REQUIREMENTS,MILESTONES,RETROSPECTIVE}.md`) must contain zero colon-form references. Documentary mentions of the deprecated form itself (inside back-ticks, e.g. ``"`/gsd:` (deprecated)"``) are acceptable.
```

**Note on the 6 remaining `/gsd:` refs** (verified via `grep -n /gsd: .planning/{PROJECT,STATE,ROADMAP,REQUIREMENTS,MILESTONES,RETROSPECTIVE}.md`):
- `REQUIREMENTS.md:43` — text inside DOCS-01 requirement itself (`NOT /gsd:<name>`), documentary
- `STATE.md:69` + `:122` — documentary references to the deprecated form within a deferred-items table and a roadmap-evolution note
- `PROJECT.md:35` + `ROADMAP.md:177` + `:185` — all 3 are documentary references in the DOCS-01 description

All 6 hits are **legitimate documentary mentions of the deprecated form** (referencing it AS the thing being deprecated). They appear inside backticks (` `/gsd:` `), labelled "deprecated" or "colon-form". The DOCS-01 grep-regression fence must therefore use a slightly looser predicate than the literal `grep -r "/gsd:"`:

```bash
# Strict grep — finds ALL hits, including documentary
grep -r "/gsd:" .planning/PROJECT.md .planning/STATE.md .planning/ROADMAP.md \
                .planning/REQUIREMENTS.md .planning/MILESTONES.md .planning/RETROSPECTIVE.md
# Expected: 6 hits at HEAD (all documentary)

# Fence grep — excludes lines that themselves describe the deprecated form
grep -r "/gsd:" .planning/PROJECT.md .planning/STATE.md .planning/ROADMAP.md \
                .planning/REQUIREMENTS.md .planning/MILESTONES.md .planning/RETROSPECTIVE.md \
  | grep -vE "(deprecated|colon|/gsd:.*\(.*colon|documenting that|inline-sweep|prevents regression|deprecates|skill-invocation refs)" \
  | wc -l
# Expected: 0 — no LIVE invocations
```

The planner should adopt the fence-grep predicate in VERIFICATION.md. The simpler grep (`wc -l = 0`) is achievable only by either (a) refactoring all 6 documentary mentions to a less-machine-detectable form (e.g., write "colon-form prefix" instead of `` `/gsd:` `` in backticks), or (b) accepting that documentary mentions of the deprecated form will keep the strict grep nonzero forever. **Recommend (a)** — rewrite the 6 documentary uses to drop the literal `/gsd:` token, replace with phrases like "the deprecated colon-form prefix" or "the pre-2026 colon syntax". Then `grep -r "/gsd:" .planning/*.md` returns 0 cleanly. Plan-04 needs to do BOTH the CLAUDE.md insert AND the 6-line documentary cleanup in active files.

### REL-02 Operator Runbook — `docs/operations/release-runbook.md`

**Placement decision:** existing `docs/operations/import-runbook.md` is a backup-import operator runbook with a clear domain boundary. **NEW file** `docs/operations/release-runbook.md` is the right shape — it sits alongside the existing import-runbook and reads as a sibling concern (operator-facing release lifecycle).

**File shape (Claude's discretion within D-05 constraints):** numbered list with per-step `gh`/`docker` commands and explicit per-tag confirmation prompts. Sections:

1. **Section 1 — Prerequisites:** PAT scopes (`repo`, `write:packages`), `gh auth status` green, `docker login ghcr.io`, free disk for worktrees.
2. **Section 2 — Retroactive v1.10.0:** worktree build → `gh release create --target 45aabfd0` → `docker push ghcr.io/jegr78/ctc-manager:1.10.0` → worktree cleanup.
3. **Section 3 — Retroactive v1.11.0:** identical pattern, SHA `598d1431`, `--notes-start-tag v1.10.0`.
4. **Section 4 — Legacy short-form tag cleanup:** per-tag confirmation loop for `v1.3`, `v1.5`, `v1.8`. **NB:** v1.6 + v1.9 already absent on remote per current `gh api` inventory — runbook should re-verify the inventory at the start of this section ("Run `gh api /repos/jegr78/ctc-manager/git/refs/tags | jq -r '.[].ref' | grep '^refs/tags/v[0-9]\\+\\(\\.[0-9]\\+\\)\\?$'` and confirm which short-form tags actually need deletion") so the operator doesn't try to delete tags that aren't there.
5. **Section 5 — Post-runbook verification:**
   - `gh release list --limit 20` — confirms v1.10.0 + v1.11.0 are listed
   - `gh api /repos/jegr78/ctc-manager/git/refs/tags | jq -r '.[].ref'` — confirms short-form tags are gone
   - `docker pull ghcr.io/jegr78/ctc-manager:1.10.0` (from any machine with GHCR read access) — confirms image is pullable
6. **Section 6 — Future-proof releases:** brief paragraph stating that REL-01 hardening means every subsequent milestone PR squash-merge to master auto-produces a vX.Y.0 release — operator does NOT run this runbook unless catching up missed releases.

**Source-marker requirement:** runbook must reference [[no-local-git-tags]] memory rule explicitly (so operators reading the runbook understand WHY worktree+gh-only, never local `git tag`).

## State of the Art

| Old Approach | Current Approach (Phase 88) | When Changed | Impact |
|--------------|------------------------------|--------------|--------|
| `git describe --tags --abbrev=0` for last-tag detection | `git tag --sort=-version:refname --list 'v[0-9]*.[0-9]*.[0-9]*' \| head -1` | REL-01 D-04 (Phase 88) | Skips legacy short-form tags; deterministic; ignores reachability order |
| `actions/checkout@v6` with only `fetch-depth: 0` | Same + `fetch-tags: true` | REL-01 D-04 (Phase 88) | `git tag` returns remote tags inside the runner (otherwise empty even with `fetch-depth: 0`) |
| Local `git tag -a vX.Y.0 && git push origin vX.Y.0` for retroactive releases | `gh release create vX.Y.0 --target <SHA> --generate-notes …` | [[no-local-git-tags]] memory rule (post-v1.11) | Single remote API call creates tag + Release atomically; no risk of local-tag drift |
| `@Test @Disabled` for manual maintenance utilities | `CommandLineRunner` Spring bean / `main()`-class | CLEAN-03 D-03 (Phase 88) | Eliminates "remove @Disabled, run, re-add" cycle; clean `@Test` semantic surface |
| Parent-team always wins on shortName multi-match (Phase 70 D-05) | Sub-team with PhaseTeam in target REGULAR phase wins; parent-precedence as legacy fallback (DRIV-01 D-06) | DRIV-01 (Phase 88, refines Phase 70 D-05) | Data-correctness fix for production import path; preserves Phase 66 legacy semantics for pre-V4 seasons |
| Page-wide `showGroupColumn = anyMatch(tab.usesGroups())` as sole row-cell gate (HYPOTHETICAL — current code has none) | Per-tab `usesGroups()` on TabPreview + page-wide aggregation for header only (DRIV-02) | DRIV-02 (Phase 88) | Mixed multi-tab previews: non-GROUPS rows correctly render `—` placeholder; GROUPS rows correctly render group name or warning badge |
| 16 `/gsd:` references in active planning files | 0 active invocations + DOCS-01 convention paragraph | DOCS-01 D-08 (Phase 88) | Operator copy-paste friction eliminated; regression-fence in place |

**Deprecated / outdated:**
- `@Test @Disabled` for manual workflows (CLEAN-03 closes this)
- `git describe --tags --abbrev=0` in release workflows (REL-01 closes this)
- `Assumptions.assumeFalse(true, ...)` as a platform-skip mechanism (CLEAN-02 c closes this; if a platform truly is unsupported, gate with `@EnabledOnOs` at the class level)
- Phase 70 D-09 "Group-resolution UX decommissioned" — DRIV-02 reactivates the surface now that DRIV-01 makes it semantically legitimate

## Assumptions Log

> All claims in this research are either VERIFIED via tool execution against the local repo + remote gh API, or CITED against existing planning docs / memory rules. Below is the explicit list of items that are ASSUMED.

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `./mvnw clean verify` exits 0 at HEAD `e117b97196f36f27b41fc8faaf9cb388a86b00f1` | CLEAN-01 verify-only | If a tests fails in `verify` (only `test-compile` was verified in this session due to compile-time scope), Plan-01 expands to fix root cause. CONTEXT.md D-02 already covers this expansion path. **[ASSUMED]** the full `verify` pass — only `test-compile` was run to confirm no source-level compile error |
| A2 | `v1.6` and `v1.9` short-form tags are absent on the remote (CONTEXT-D-05 lists them as targets for deletion) | Runtime State Inventory | REL-02 runbook tries to delete a non-existent tag → `gh api -X DELETE` returns 422 Unprocessable. Runbook section 4 must include an inventory re-verification step. **[ASSUMED]** the inventory does not shift between research and execution — re-verify in Plan-06 |
| A3 | `docker login ghcr.io` works with the operator's personal access token at runbook execution time | REL-02 Section 1 | Operator needs to refresh PAT or run `docker logout && docker login`. Runbook explicitly lists the auth-status check. **[ASSUMED]** operator has a working GHCR token |
| A4 | The Phase 80 `deferred-items.md` JDT-cache diagnosis still applies — i.e., no new code has been added since 2026-05-16 that would re-introduce a real compile error on `BackupSchemaExclusionIT.java:40` | CLEAN-01 | If false, Plan-01 expands to a real fix per D-02 fallback. **[ASSUMED]** `git diff e117b971 5cc76ab9 -- src/test/java/org/ctc/backup/it/BackupSchemaExclusionIT.java` is empty — not verified in this session because the file is part of the v1.11 baseline that is preserved |
| A5 | `PhaseTeamRepository` does NOT need a new method; `findByPhaseIdAndTeamId(UUID, UUID)` is sufficient for DRIV-01 | DRIV-01 implementation | New repo method would mean a `@Query` annotation + an IT — adds scope. **[VERIFIED: PhaseTeamRepository.java:17]** — already exposed. No assumption risk |
| A6 | The CLEAN-02 (b) deleted test's coverage is already provided by Test #7 cross-tab same-PSN | CLEAN-02 (b) | If JaCoCo delta after Plan-02 shows regression on the `findByPsnId(psnId).orElseGet(...)` branch, Test #7 enhancement is needed. CONTEXT-line 194 covers this. **[CITED: shortname-resolver-picks-parent-without-phaseteam.md and DriverSheetImportServiceIT.java:333-346 comment block]** |

## Open Questions

1. **Should the DOCS-01 grep-regression fence use the strict or the lenient predicate?**
   - **What we know:** The 6 remaining `/gsd:` refs are documentary (text that describes the deprecated form). Strict grep cannot return 0 without rewriting these 6 mentions to use phrases like "colon-form prefix" instead of the literal token.
   - **What's unclear:** Whether D-08 expects a strict-0 outcome or accepts the lenient "documentary-mentions-allowed" predicate.
   - **Recommendation:** Plan-04 ALSO rewrites the 6 documentary mentions to non-literal phrasing (1-3 word edits per file, ~10 minutes total), and the regression-fence becomes the strict `grep -r "/gsd:" .planning/*.md | wc -l == 0` form. Cleanest, most enforceable, fits user discretion in D-08 explicitly.

2. **Does the v1.11.0 retroactive release's `--notes-start-tag` need to be `v1.10.0` (which itself doesn't exist yet at the time the v1.11 release is created) or `v1.9.0` (which exists)?**
   - **What we know:** REL-02 publishes v1.10.0 THEN v1.11.0 in sequence within the same operator session. After step 2 (Section 2 of the runbook) completes, `v1.10.0` exists on the remote.
   - **What's unclear:** Whether `gh release create --notes-start-tag v1.10.0` resolves the tag against the remote inventory at the moment of the call (which would work) or against the operator's local repo state (which depends on whether `git fetch` has been run since v1.10.0 was created).
   - **Recommendation:** Section 3 of the runbook starts with `git fetch --tags origin` to ensure v1.10.0 is locally known before step 3 runs `gh release create v1.11.0 --notes-start-tag v1.10.0`. `gh` always re-queries the remote for ref existence, so this should work; the fetch is defensive belt-and-suspenders.

3. **What is the right behaviour for `regularPhase = null` in DRIV-02's per-tab `usesGroups` computation?**
   - **What we know:** D-07 mandates legacy fallback (preserve parent-precedence) when `regularPhase == null`. CONTEXT-line 174 says `usesGroups = regularPhase != null && regularPhase.getLayout() == PhaseLayout.GROUPS`.
   - **What's unclear:** When `regularPhase == null`, should the template render `—` (suppressed) or render the resolved team's `parentTeam.shortName` (resolved info still available)?
   - **Recommendation:** Per CONTEXT-line 174, `usesGroups = false` when `regularPhase == null`, so the template renders `—` — no group info available. This matches Phase 70 D-09 legacy behaviour. Plan-05 should add a unit test asserting this for the `regularPhase == null` legacy-fallback path (test #21 `givenSeasonHasNoRegularPhase_whenPreviewWithCollision_thenFallsBackToParentPrecedence` at `DriverSheetImportServiceTest.java:586-607` is the closest existing fixture).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Java 25 (Eclipse Temurin) | All Plans | ✓ | Per pom.xml `<java.version>25`; verified `./mvnw clean test-compile` exits 0 | — |
| `./mvnw` Maven wrapper | All Plans | ✓ | Repo-local | — |
| Spring Boot 4.0.6 dependencies | All Plans | ✓ | pom.xml | — |
| `gh` CLI | REL-01 dry-run + REL-02 runbook | ✓ | Verified `gh release list` succeeded | — |
| `docker` CLI | REL-02 Docker push | ✓ on operator machine (CLAUDE.md `docker compose` commands) | — | REL-02 Section 1 prerequisite check |
| `jq` | REL-02 inventory re-verification (`gh api … \| jq …`) | ✓ macOS/Linux standard | — | — |
| `git worktree add` | REL-02 worktree build | ✓ git 2.5+ (modern) | — | — |
| GHCR PAT with `write:packages` scope | REL-02 docker push | ✓ assumed operator has one | — | Runbook Section 1 explicit prereq |
| Playwright browsers (chromium) | E2E if any test in Plan-02 cleanup touches sitegen E2E | ✓ Already cached per CLAUDE.md "Install Playwright Chromium" | — | — |

**Missing dependencies with no fallback:** None — all required tools are present.
**Missing dependencies with fallback:** None — all required tools are present.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5.x (Spring Boot–managed) + Mockito + AssertJ |
| Config file | `pom.xml` (Surefire + Failsafe sections; no `pytest.ini`/`jest.config.*` equivalent) |
| Quick run command | `./mvnw test` (Surefire, ~3 minutes) |
| Full suite command | `./mvnw verify -Pe2e` (Surefire + Failsafe + Playwright E2E + JaCoCo, CI median 23:00) |
| @Tag routing | Surefire: untagged unit tests; Failsafe: `@Tag("integration")`; `-Pe2e`: `@Tag("e2e")` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CLEAN-01 | `./mvnw clean verify` exits 0 with JaCoCo CSV | build gate | `./mvnw clean verify && ls target/site/jacoco/jacoco.csv` | n/a (build gate) |
| CLEAN-02 (a) | `@Disabled` GROUPS-SWISS placeholder removed | grep + compile | `grep -n "givenGroupsSwissLayoutSeason" src/test/java; ./mvnw clean test-compile` | ✅ (delete-target) |
| CLEAN-02 (b) | `@Disabled` regression-fence test removed; coverage retained | grep + JaCoCo CSV delta | `grep -n "givenPreExistingDriverNotMatched" src/test/java; ./mvnw clean verify` | ✅ (delete-target) |
| CLEAN-02 (c) | Windows-conditional skip simplified | grep + IT runs unconditionally | `grep -n "isWindows()" src/test/java; ./mvnw -Dit.test='AutoBackupBeforeImportFailureIT' failsafe:integration-test failsafe:verify` | ✅ (simplify-target) |
| CLEAN-03 | Standalone utility replaces `@Test @Disabled` | grep + bean-exists check | `grep -rn "@Disabled" src/test/java \| wc -l == 0; grep -rn "SiteGeneratorBaselineRefresh" src/test/java` | ❌ (NEW file in Plan-02) |
| REL-01 | Workflow exercises hardened parser + idempotency guard | manual dispatch | `gh workflow run release.yml -F dry-run=true --ref gsd/v1.12-driver-import-and-test-perf; gh run watch <id> --exit-status` | ✅ existing (mutate) |
| REL-02 | Retroactive releases published; legacy tags cleaned | manual operator post-merge | `gh release list \| grep -E 'v1\.(10\|11)\.0'; gh api /repos/jegr78/ctc-manager/git/refs/tags \| jq -r '.[].ref' \| grep -E '^refs/tags/v[0-9]+$' \| wc -l == 0` | ❌ runbook NEW |
| DOCS-01 | Strict grep returns 0 across 6 active top-level files; CLAUDE.md has the new paragraph | grep + read | `grep -rn '/gsd:' .planning/PROJECT.md .planning/STATE.md .planning/ROADMAP.md .planning/REQUIREMENTS.md .planning/MILESTONES.md .planning/RETROSPECTIVE.md \| wc -l == 0; grep -c 'Skill Invocation Naming' CLAUDE.md == 1` | ✅ (mutate) |
| DRIV-01 | Resolver season-aware; 4 edge cases covered by tests | unit | `./mvnw -Dtest='DriverSheetImportServiceTest' test` (asserts new + inverted #16/#17 + edge cases) | ✅ existing (mutate + add) |
| DRIV-02 | Service skips warning + lookup on non-GROUPS; usesGroups field flows to template | unit + IT | `./mvnw -Dtest='DriverSheetImportServiceTest' test; ./mvnw -Dit.test='DriverSheetImportServiceIT' failsafe:integration-test` (new GROUPS-with-missing-PhaseTeam test) | ✅ existing (mutate + add) |

### New Test-Tag Requirements

| New test file | Tag | Justification |
|---------------|-----|---------------|
| `DriverSheetImportServiceTest` additions (4 new tests for DRIV-01 edge cases + DRIV-02 GROUPS) | (none, untagged) | Pure Mockito unit tests (no `@SpringBootTest`); per TESTING.md Surefire-route |
| `DriverSheetImportServiceIT` additions (if any IT-shape needed for full coverage) | `@Tag("integration")` | Spring-context IT per CLAUDE.md "Tag Tests by Category" — class already has `@Tag("integration")` at line 46, new methods inherit |
| `SiteGeneratorBaselineRefresh` (new) | None — it's a `CommandLineRunner`/main(), NOT a test | Not invoked by Surefire/Failsafe; no `@Tag` needed |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest='<focused-test-class>'` or `./mvnw -Dit.test='<focused-IT-class>' failsafe:integration-test failsafe:verify` (per [[test-call-optimization]] memory rule — no blanket `./mvnw verify` per task)
- **Per plan merge:** `./mvnw clean verify` (per [[clean-build-test-only]] memory rule — one full-suite per plan)
- **Phase gate:** `./mvnw clean verify -Pe2e` (full E2E suite, per [[e2e-verification]] memory rule — once at end of Plan-05 or Plan-06)

### Wave 0 Gaps

- [ ] `src/test/java/org/ctc/sitegen/util/SiteGeneratorBaselineRefresh.java` — NEW utility class replacing the `@Test @Disabled` shell (Plan-02 / CLEAN-03)
- [ ] `docs/operations/release-runbook.md` — NEW operator runbook (Plan-06 / REL-02)
- [ ] `CLAUDE.md` "Skill Invocation Naming" paragraph — content insertion at line 220+ (Plan-04 / DOCS-01)
- [ ] 6 documentary `/gsd:` rewrites in `.planning/{PROJECT,STATE,ROADMAP,REQUIREMENTS,MILESTONES,RETROSPECTIVE}.md` — text edits (Plan-04 / DOCS-01)
- [ ] `.planning/REQUIREMENTS.md` CLEAN-01 entry — status flip Open → Resolved with cross-ref to Phase-80 deferred-items.md (Plan-01)

*(All other infrastructure — JUnit, Spring Boot test starter, JaCoCo, Surefire, Failsafe, `@Tag` routing — is already in place from v1.11. No framework install needed.)*

## Security Domain

`security_enforcement` is implicit per `.planning/config.json` (not explicitly false). Phase 88 has 4 security-sensitive surfaces:

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | no | No auth code touched; CLAUDE.md "Profiles: Auth only for prod/docker" already in place |
| V3 Session Management | no | No session code touched |
| V4 Access Control | yes (REL-02 destructive tag deletion) | Operator-confirmation prompt per tag in runbook; per-tag `read -p "type 'yes'"` gate |
| V5 Input Validation | yes (REL-01 dry-run flag parser; release version parser) | Numeric validation `[[ "$MAJOR" =~ ^[0-9]+$ ]]`; `inputs.dry-run` is `boolean` type per GHA validation |
| V6 Cryptography | no | No crypto code; existing BCrypt-N/A per CodeQL gate (Phase 85) unchanged |
| V11 Business Logic | yes (DRIV-01 resolver) | Resolver returns deterministic Team for given (shortName, regularPhase); ordering not exposed to user; idempotent |

### Known Threat Patterns for Phase 88's surfaces

| Pattern | STRIDE | Standard Mitigation | Where in Phase 88 |
|---------|--------|---------------------|-------------------|
| Workflow input tampering (`dry-run` flag) | Tampering | `if: inputs.dry-run != true` guards every side-effecting step; positive form does not skip on `push:` (Pitfall 5) | REL-01 D-04 |
| Irreversible destructive op via stale runbook | Repudiation | Per-tag confirmation prompt (`read -p "yes"`) in runbook; pre-deletion inventory re-verification | REL-02 D-05 Section 4 |
| Image content tampering during retroactive build | Tampering | `versions:set` BEFORE `package` (Pitfall 3); JAR manifest carries correct version; SHA-256 of the produced JAR could be logged in the runbook output (defensive option) | REL-02 Section 2/3 |
| `gh release create --generate-notes` injects arbitrary git-log content | Information Disclosure | `gh` already vets the rendered notes; commit log is internal — no user-supplied content in `git log %s` | REL-02 (accepted, no extra mitigation) |
| Stored XSS through `/admin/drivers/import` preview | XSS | Thymeleaf escapes `${tab.usesGroups()}` boolean rendering, `${row.teamShortName()}` text rendering by default; no `th:utext` on DRIV-02 added cells | DRIV-02 template edit |
| SSRF in retroactive build via Maven repo proxy | Information Disclosure | Maven wrapper pins repo URLs from `~/.m2/settings.xml`; no operator-supplied URL in REL-02 | REL-02 (accepted, no extra mitigation) |

**Plan-level threat-model blocks** (planner copies into each PLAN's frontmatter `<threat_model>`):

```yaml
# Plan-03 REL-01 threat model
threat_model:
  - input_tampering:
      surface: workflow_dispatch dry-run input
      mitigation: 'if: inputs.dry-run != true on every side-effecting step (positive form does not skip on push trigger — Pitfall 5)'
  - parser_injection:
      surface: bash $(git tag --sort=…) output
      mitigation: 'IFS=. read parser + numeric regex validation; quoted variable expansion throughout'

# Plan-06 REL-02 threat model
threat_model:
  - irreversible_destructive_op:
      surface: gh api -X DELETE /repos/.../git/refs/tags/<tag>
      mitigation: per-tag operator confirmation prompt (read -p "yes"); pre-deletion inventory re-verification via gh api
  - image_content_tamper:
      surface: docker push ghcr.io/jegr78/ctc-manager:<vX.Y.0>
      mitigation: 'versions:set -DnewVersion=<X.Y.0> runs BEFORE package (Pitfall 3); JAR + image embed correct version; throwaway worktree isolates from v1.12 branch'
```

## Project Constraints (from CLAUDE.md)

- **Test Coverage:** Minimum 82% line coverage. Current v1.11 baseline is 88.88% (6.88 pp buffer). Phase 88 deletions (CLEAN-02 a/b) may reduce coverage marginally — must stay ≥ 82% AND should stay ≥ 88.88% per STATE.md "Baselines to Preserve" (no regression).
- **Flyway:** V1-V7 immutable. Phase 88 does NOT touch migrations.
- **Profiles:** Auth only `prod`/`docker`. CLEAN-03 utility's potential new profile `baseline-refresh` is operator-local (`dev` or `local` profile), no auth implications.
- **OSIV:** Remains enabled. DRIV-01/02 changes go through services + controller, no template lazy-init triggered.
- **Backward Compatibility:** No breaking changes to URLs/endpoints. DRIV-02 adds a model attribute `showGroupColumn` — additive, no existing template that doesn't read it breaks. `TabPreview` record gets a new field `usesGroups` — Java record evolution is **non-breaking at the source level** when adding fields; **but Jackson serialization** of `TabPreview` over backup-export changes the wire format. **Backup wire contract:** `BackupSchema.SCHEMA_VERSION = 1` MUST not bump → confirm DriverImportPreview is NOT part of backup `EXPORT_ORDER` (verified: `EXPORT_ORDER` lists 24 entities under `org.ctc.domain.model.*`; `TabPreview` is in `org.ctc.dataimport.*`, NOT exported, confirmed via PROJECT.md "Backup Wire Contract" sec 4).
- **Playwright:** Compile-scope dependency. CLEAN-03 utility may or may not use Playwright (it uses `SiteGeneratorService` which itself uses Playwright at runtime for graphics). Pre-installation step `./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"` (CLAUDE.md Commands) is operator's prereq for running the baseline-refresh utility.
- **Tag Tests by Category (`@Tag`):** Every new `*IT.java` needs `@Tag("integration")`. Plan-05's new DRIV-02 IT (if any added) inherits from class-level `@Tag("integration")` already present at `DriverSheetImportServiceIT.java:46`.
- **No Inline Styles on Buttons:** DRIV-02 template touches a `<td>` cell with text content — no buttons, no inline-style risk. Template gate uses `th:if`, not `style=…`.
- **Isolate Test Data Completely:** Any new DRIV-01/02 test data must use a `T-`/`Test_`/`Phase88-Test-` prefix per CLAUDE.md and `DriverSheetImportServiceTest.java:643` precedent ("Test-MRL Parent", "T-MRL"). Plan-05's new tests must follow this convention.
- **Subagent Rules:** Plan execution uses Opus or Sonnet model, not Haiku (per CLAUDE.md "Subagent Rules"). Branch `gsd/v1.12-driver-import-and-test-perf` named explicitly in every subagent prompt; no branch switching. Post-dispatch validation: `git branch --show-current && git log --oneline -3 && git diff --stat`.
- **Static Analysis (SpotBugs):** Phase 88 deletions and additions must keep `BugInstance count == 0` (verified by `./mvnw verify` running `spotbugs:check`). New `SiteGeneratorBaselineRefresh` utility class needs `@Slf4j @Component @RequiredArgsConstructor` annotation order (per CLAUDE.md Lombok Usage, alphabetical).

## Sources

### Primary (HIGH confidence)

- **Local repo at HEAD `e117b97196f36f27b41fc8faaf9cb388a86b00f1`** on branch `gsd/v1.12-driver-import-and-test-perf` — every file:line citation in this research was read directly via the Read tool in this session.
- **`.planning/milestones/v1.11-phases/80-openrewrite-integration/deferred-items.md`** — CLEAN-01 JDT-cache diagnosis (lines 7-67).
- **`.planning/debug/deferred/shortname-resolver-picks-parent-without-phaseteam.md`** — DRIV-01 root cause, suggested algorithm, 4 edge cases (lines 52-80).
- **`.planning/debug/deferred/group-warnings-for-non-groups-seasons.md`** — DRIV-02 root cause, dual-surface fix (lines 41-58).
- **`.planning/PROJECT.md` "Key Decisions" + "Current Milestone v1.12" + "Backup Wire Contract (v1.10)"** — all 4 sections read in full this session.
- **`.planning/STATE.md` "Active Milestone v1.12" + "Baselines to Preserve" + "Roadmap-level decisions"** — full file read.
- **`.planning/REQUIREMENTS.md`** — CLEAN-01..03, REL-01/02, DOCS-01, DRIV-01/02 full text (lines 30-43).
- **`.planning/ROADMAP.md` § "Phase 88"** — 8 success criteria, dependencies.
- **`.planning/codebase/TESTING.md` § "Test Categorization (`@Tag`)" lines 89-132** — tag routing rules.
- **`CLAUDE.md`** — Subagent Rules (lines 145-157), Conventions (lines 190-232), Git Workflow, Static Analysis.
- **`gh api /repos/jegr78/ctc-manager/git/refs/tags`** — verified remote tag inventory this session.
- **`gh release list --limit 50`** — verified GitHub Releases this session.
- **`git rev-parse 45aabfd0` + `git rev-parse 598d1431`** — verified SHAs exist on the v1.12 branch HEAD.
- **`./mvnw clean test-compile`** — verified exit 0 this session.
- **`./mvnw help:evaluate -Dexpression=project.version`** — confirmed pom.xml version `1.11.0-SNAPSHOT`.

### Secondary (MEDIUM confidence)

- **GitHub Actions docs for `actions/checkout@v6` `fetch-tags` behaviour** — cross-referenced with verified-failed CI runs from CONTEXT-line 129 (run IDs 26044380205, 25955094759, 25609204039, 24925033178).

### Tertiary (LOW confidence)

None — all factual claims in this research were verified against live tool calls in this session.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in repo, versions verified
- Architecture: HIGH — file:line citations directly verified; resolver algorithm matches deferred-debug `suggested_algorithm` verbatim
- Pitfalls: HIGH — JDT-cache pitfall has documented evidence in Phase-80 deferred-items.md; release-workflow regression has 4 documented failed runs in CONTEXT; ordering pitfalls (Pitfall 3, 5) derive from authoritative bash/GHA semantics

**Research date:** 2026-05-18
**Valid until:** 2026-06-17 (30 days — stable codebase, no upstream Spring Boot / Maven / gh CLI version change expected within window)

## RESEARCH COMPLETE
