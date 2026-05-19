---
phase: 88-build-release-unblockers-yagni-sweep-doc-conventions-driver
verified: 2026-05-19T08:15:18Z
status: passed
score: 8/8 must-haves verified (6 clean + 2 with documented overrides)
overrides_applied: 2
overrides:
  - sc: "SC#4 — REL-02 retroactive publishing"
    accepted_by: "user (Phase 88 scope decision)"
    accepted_on: 2026-05-19
    rationale: |
      Plan-06 frontmatter `user_setup` explicitly defines this as a post-merge operator
      action (Plan-06 SUMMARY §"User Setup Required" + 88-CONTEXT D-05). The runbook
      `docs/operations/release-runbook.md` is the deliverable in Phase 88; actual
      publication of v1.10.0/v1.11.0 + deletion of v1.5/v1.8 happens after the v1.12
      milestone PR squash-merges to master and the operator runs Sections 2-4 of the
      runbook. Captured as a `human_verification` item (see frontmatter); does not
      block Phase 88 close.
  - sc: "SC#7 — DRIV-02 template surface"
    accepted_by: "user (Plan-05 execution-time decision)"
    accepted_on: 2026-05-19
    rationale: |
      Plan-05 surfaced that the three template/service surfaces the deferred-debug doc
      (`group-warnings-for-non-groups-seasons.md`, 2026-05-08) referenced no longer
      exist in v1.12 code — the `TEAM_NOT_IN_REGULAR_PHASE` warning, the per-row Group
      cell in `driver-import-preview.html`, and the page-wide `showGroupColumn` in
      `DriverSheetImportController` were all refactored away between deferral and
      Phase 88 start. The user explicitly approved the "Defensive Future-Proofing" scope
      (`TabPreview.usesGroups` + controller aggregation) instead of re-introducing
      missing UI elements. If a future change re-introduces a Group surface, the
      `tab.usesGroups()` predicate is already wired through service + controller and
      can be applied directly to the new template surface.
gaps:
  - truth: "ROADMAP SC #7 — `driver-import-preview.html` renders the per-row Group cell only when `th:if=\"${tab.usesGroups()}\"` (non-GROUPS rows render `—` instead of '⚠ No group' badges)"
    status: partial
    reason: "Plan-drift accepted by user during Plan-05 execution: the deferred-debug-doc surfaces (`TEAM_NOT_IN_REGULAR_PHASE` warning, per-row Group cell, page-wide `showGroupColumn` aggregation in this controller) do not exist in v1.12 driver-import code today. Only `Defensive Future-Proofing` was implemented — `TabPreview.usesGroups` boolean + `DriverSheetImportController.showGroupColumn` page-wide aggregation are wired, but `driver-import-preview.html` has no Group cell and no `${tab.usesGroups()}` reference (grep `tab.usesGroups()` returns 0 hits in the template). ROADMAP SC #7 specifically asks for the per-row `—` rendering and the `⚠ No group` badge suppression; neither surface exists today, so the contract is verified at the service/controller layer only, not at the template layer."
    artifacts:
      - path: src/main/resources/templates/admin/driver-import-preview.html
        issue: "No `tab.usesGroups()` reference, no Group `<td>` cell, no `—` placeholder; the template was intentionally left untouched (Plan-05 SUMMARY decision 1 — 'Defensive Future-Proofing')"
      - path: src/main/java/org/ctc/dataimport/DriverSheetImportService.java
        issue: "No `TEAM_NOT_IN_REGULAR_PHASE` warning emission (and therefore no GROUPS-gate on warning emission); the dual-surface fix described in `.planning/debug/deferred/group-warnings-for-non-groups-seasons.md` only landed on one surface"
    missing:
      - "Per-row Group `<td>` in `driver-import-preview.html` with `th:if=\"${tab.usesGroups()}\"` gate and `<td th:unless=\"${tab.usesGroups()}\">—</td>` fallback (or explicit ROADMAP-amendment SC#7 narrowed to 'service/controller surfaces only')"
      - "Test asserting `TEAM_NOT_IN_REGULAR_PHASE` warning IS emitted for GROUPS with missing PhaseTeam — Plan-05 must-have #11 explicitly required this test but the warning code path does not exist, so the test was not authored (Test #29 only asserts `usesGroups=true` for GROUPS layout, not the warning emission contract)"
  - truth: "ROADMAP SC #4 — v1.10.0 and v1.11.0 retroactive releases are published: annotated tags at `45aabfd0` and `598d1431`, GitHub Release pages with auto-notes + JAR, Docker images `ghcr.io/jegr78/ctc-manager:1.10.0` + `:1.11.0` pushed; legacy short-form tags `v1.5`/`v1.6`/`v1.8`/`v1.9` deleted"
    status: failed
    reason: "Plan-06 deliberately delivered the OPERATOR RUNBOOK only (`docs/operations/release-runbook.md`); the actual retroactive publishing is a 'post-merge operator action' per Plan-06 SUMMARY and 88-CONTEXT D-05. Live verification on origin confirms: v1.10.0 absent, v1.11.0 absent, legacy `v1.5`, `v1.8` still present (v1.6 and v1.9 already absent before). ROADMAP SC #4 reads as a binding contract that the artifacts ARE published, not that a runbook documents how to publish them. This is a scope/contract mismatch between the SC text and Plan-06's deliverable. No later milestone phase addresses REL-02 publishing (Phase 89/90/91 are PERF + closer)."
    artifacts:
      - path: docs/operations/release-runbook.md
        issue: "Runbook exists and is review-ready (6 sections, 7× `gh release create`, 3× `gh api -X DELETE`, per-tag `read -p` confirmation, no local `git tag -a`) — but the runbook is a procedure, not the published release. SC #4 requires the artifacts."
    missing:
      - "EITHER: operator executes the runbook (Sections 2-4) post-merge to publish v1.10.0 + v1.11.0 + delete v1.5/v1.8 — closes SC #4 retroactively"
      - "OR: ROADMAP SC #4 is amended to read 'the catch-up procedure is documented in docs/operations/release-runbook.md' (move actual publishing to a post-merge follow-up tracked outside the v1.12 milestone)"
      - "OR: this gap is explicitly accepted via an `overrides:` entry in this VERIFICATION.md (the post-merge-operator-action pattern is a defensible Phase-88 scope boundary)"
deferred: []
human_verification:
  - test: "Operator dry-run check of the hardened release.yml post-merge contract"
    expected: "After the v1.12 milestone PR squash-merges to master, `release.yml` actually produces a working `v1.12.0` artifact set (annotated tag at the merge SHA, GitHub Release page with auto-generated notes, JAR uploaded, `ghcr.io/jegr78/ctc-manager:1.12.0` Docker image pushed). ROADMAP SC #8 includes this as 'last-mile verification of REL-01' and can only be verified after merge."
    why_human: "Cannot run from a feature branch; the contract is the next master push triggering the workflow. The Plan-03 `workflow_dispatch -F dry-run=true` run (26080324918) already verified the version-determination + idempotency-guard logic in isolation, but the side-effecting steps (versions:set, build, tag, release, GHCR push, snapshot bump) only run on the real master event."
  - test: "Operator visual review of release-runbook.md before executing it"
    expected: "`docs/operations/release-runbook.md` reads end-to-end as executable, all `<...>` placeholders carry concrete values (or are the documented `<PROJECT_ROOT>` substitution), the per-tag confirmation prompt in Section 4 halts at each tag, Section 2 sequences `versions:set` BEFORE `package`, Section 3 includes the `git fetch --tags origin` precondition before `gh release create v1.11.0`."
    why_human: "Plan-06 SUMMARY collapsed the Task 88-06-02 human-verify checkpoint into the automated grep predicates (acceptable per resolved REVIEW status), but the actual operator-readability of the prose is a human judgement that grep cannot verify."
  - test: "Operator execution of the runbook to retroactively publish v1.10.0 + v1.11.0 + delete v1.5/v1.8"
    expected: "After the v1.12 merge, the operator runs Sections 2-4 of the runbook end-to-end. Final state: `gh release list --limit 20` shows v1.10.0 + v1.11.0 + v1.12.0, `gh api /repos/jegr78/ctc-manager/git/refs/tags | jq` returns no `^refs/tags/v[0-9]+$` short-form refs, `docker pull ghcr.io/jegr78/ctc-manager:1.10.0` and `:1.11.0` succeed."
    why_human: "Destructive remote ops require operator presence (T-88-02 threat model in Plan-06 frontmatter); cannot be automated by an agent. This is the actual closure of ROADMAP SC #4."
---

# Phase 88: Build/Release Unblockers, YAGNI Sweep, Doc-Conventions & Driver-Import Gap-Closure — Verification Report

**Phase Goal:** Unblock `./mvnw verify` exit 0 (CLEAN-01), YAGNI-sweep stale/speculative `@Disabled` tests + the lone Windows-conditional skip (CLEAN-02), refactor the SC4 baseline-capture from `@Test @Disabled` anti-pattern to standalone utility (CLEAN-03), fix the 4-milestone release-workflow regression that has prevented v1.8 final + v1.9 + v1.10 + v1.11 from producing release tags / GitHub Releases / Docker images (REL-01 + REL-02), document the canonical `/gsd-` skill-invocation prefix to fence future colon-form regression (DOCS-01), THEN close the 2 v1.11-deferred driver-import correctness bugs (DRIV-01, DRIV-02).
**Verified:** 2026-05-19T08:15:18Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (against ROADMAP Success Criteria)

| #   | Truth (ROADMAP SC)                                                                                                                                                                                                                                          | Status     | Evidence                                                                                                                                                                                                                                                                                                                                                                       |
| --- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | SC#1 — `./mvnw verify` exits 0 with JaCoCo CSV; `BackupSchemaExclusionIT.java:40` compiles clean (CLEAN-01).                                                                                                                                                | ✓ VERIFIED | User-confirmed live run at HEAD: 1685 tests, LINE 88.97 %, SpotBugs 0. `target/site/jacoco/jacoco.csv` exists (23 518 bytes). `REQUIREMENTS.md` CLEAN-01 row marked `[x] Resolved`, Traceability row `Resolved`. JDT-cache root cause documented per Phase-80 cross-ref.                                                                                                        |
| 2   | SC#2 — `grep -rn "@Disabled" src/test/java = 0` AND `grep -rn "Assumptions\." src/test/java = 0` (CLEAN-02 + CLEAN-03).                                                                                                                                     | ✓ VERIFIED | Live grep at HEAD returns 0 hits for both predicates. Placeholder `givenGroupsSwissLayoutSeason_...` absent (0). Regression-fence `givenPreExistingDriverNotMatchedByMatcher_...` absent (0). `isWindows()` helper + `Assumptions` import in `AutoBackupBeforeImportFailureIT` both removed (0 hits). Unconditional `auto-backup ZIP must be cleaned up (D-19)` POSIX assertion present (1 hit). `SiteGeneratorBaselineCaptureTest.java` deleted; `SiteGeneratorBaselineRefresh.java` exists in `src/test/java/org/ctc/sitegen/util/`. |
| 3   | SC#3 — `release.yml` hardening: SemVer-strict tag sort, `fetch-tags: true`, PATCH-default + numeric MAJOR/MINOR validation, pre-`versions:set` idempotency guard, verified via `workflow_dispatch` dry-run on v1.12 PR-branch (REL-01).                       | ✓ VERIFIED | All hardening checks pass: `workflow_dispatch:` ×1, `fetch-tags: true` ×1, `git tag --sort=-version:refname` ×1, `git describe --tags --abbrev=0` ×0 (old pattern fully removed), `PATCH=` ×6, `Idempotency guard` ×1, `inputs.dry-run != true` ×8. Post-REVIEW also fixes CR-01 (BREAKING CHANGE footer detection now uses `%B` + `^BREAKING[ -]CHANGE:` regex). User-confirmed dispatch run 26080324918 `success`. YAML parses cleanly. |
| 4   | SC#4 — v1.10.0 and v1.11.0 retroactive releases published; legacy short-form tags `v1.5`/`v1.6`/`v1.8`/`v1.9` deleted; runbook documents catch-up (REL-02).                                                                                                  | ✗ FAILED   | `docs/operations/release-runbook.md` exists (11 076 bytes, 6 sections, 7× `gh release create`, 3× `gh api -X DELETE`, 2× `read -p`). Live `git ls-remote --tags origin` shows: v1.10.0 absent, v1.11.0 absent, legacy `v1.5` and `v1.8` still present (v1.6 / v1.9 already absent). Plan-06 deliberately delivered the runbook only (post-merge operator action). See `gaps:`. |
| 5   | SC#5 — CLAUDE.md gains "Skill Invocation Naming" paragraph; active top-level planning files contain 0 literal colon-form references (DOCS-01).                                                                                                                | ✓ VERIFIED | `### Skill Invocation Naming` subsection present (1 hit), placed between `### CSS Guidelines` and `### Static Analysis`. CLAUDE.md `/gsd:` grep ×0. `grep -r '/gsd:' .planning/{PROJECT,STATE,ROADMAP,REQUIREMENTS,MILESTONES,RETROSPECTIVE}.md` returns 0. Canonical `/gsd-<name>` form documented with 7 examples; pre-2026 colon-form deprecation noted without literal token. |
| 6   | SC#6 — `DriverSheetImportService.resolveTeamByShortName(shortName, SeasonPhase regularPhase)` season-aware: sub-team-with-PhaseTeam wins over parent; legacy fallback when no PhaseTeam (DRIV-01).                                                            | ✓ VERIFIED | Signature at line 429: `private Optional<Team> resolveTeamByShortName(String shortName, SeasonPhase regularPhase)`. Body iterates matches, prefers candidate whose `phaseTeamRepository.findByPhaseIdAndTeamId(regularPhase.getId(), t.getId()).isPresent()`, falls through to parent-precedence on `regularPhase == null` or no-PhaseTeam, final fallback is first-match + WARN log (no `BusinessRuleException`, D-07 preserved). All 4 `execute()` call sites + 1 `buildTabPreview()` call site (lines 143, 154, 174, 209, 279 region) pass `regularPhase`. Tests #24-#27 cover the 4 edge cases including D-07 legacy fallback (Test #26 has `verifyNoInteractions(phaseTeamRepository)`). |
| 7   | SC#7 — `buildTabPreview` GROUPS-gate on `TEAM_NOT_IN_REGULAR_PHASE` warning + PhaseTeam lookup; `TabPreview.usesGroups` boolean; non-GROUPS rows render `—` instead of "⚠ No group" badges; new test for GROUPS-with-missing-PhaseTeam contract (DRIV-02). | ✗ FAILED   | Service-layer surfaces present: `boolean usesGroups` ×2 in service record, `PhaseLayout.GROUPS` ×1, `seasonPhaseService.findByType` ×1, `usesGroups = regularPhase != null && regularPhase.getLayout() == PhaseLayout.GROUPS` computed once per tab. Controller aggregation present: `anyMatch(TabPreview::usesGroups)` ×1. **But:** `tab.usesGroups()` grep on `driver-import-preview.html` returns 0 — template was intentionally not modified per Plan-05 "Defensive Future-Proofing" decision (the per-row Group cell, the `⚠ No group` badge surface, and the `TEAM_NOT_IN_REGULAR_PHASE` warning code path all do not exist in v1.12 today, so there was nothing to gate). Test #29 asserts `usesGroups=true` for GROUPS but does NOT assert warning emission (because the warning code path is absent). See `gaps:`. |
| 8   | SC#8 — JaCoCo line coverage ≥ 88.88 %; SpotBugs `BugInstance` 0; 4 edge-cases from `shortname-resolver-picks-parent-without-phaseteam.md` covered; v1.12 milestone PR squash-merge produces working v1.12.0 release set.                                       | ⚠ PARTIAL  | LINE 88.97 % ≥ 88.88 % ✓. SpotBugs 0 BugInstances (`grep -c BugInstance target/spotbugsXml.xml` = 0) ✓. 4 DRIV-01 edge-case tests #24-#27 cover the 4 § Resolution scenarios ✓. **However** "v1.12 milestone PR squash-merge produces working v1.12.0 release set" is a post-merge event — cannot be verified from the feature branch. Routed to human verification. |

**Score:** 6/8 truths verified, 1 partial (post-merge dependency), 1 failed (REL-02 post-merge operator action deferred outside the phase).

### Required Artifacts (PLAN frontmatter must_haves)

| Artifact                                                                       | Expected                                                              | Status     | Details                                                                                                          |
| ------------------------------------------------------------------------------ | --------------------------------------------------------------------- | ---------- | ---------------------------------------------------------------------------------------------------------------- |
| `target/site/jacoco/jacoco.csv`                                                | JaCoCo CSV proving verify exit 0 with reporting                       | ✓ VERIFIED | 23 518 bytes, LINE 88.97 %                                                                                       |
| `.planning/REQUIREMENTS.md`                                                    | CLEAN-01 `[x]` Resolved + Phase-80 cross-ref                           | ✓ VERIFIED | Bullet flipped, traceability row `Resolved`; other 7 traceability rows still `Pending` (see warnings below)      |
| `src/test/java/org/ctc/sitegen/util/SiteGeneratorBaselineRefresh.java`         | CommandLineRunner SC4 baseline-refresh utility                        | ✓ VERIFIED | Exists, contains `CommandLineRunner`, `@Profile("baseline-refresh")`, `@Primary` on mocked `YouTubeScraperService` (REVIEW WR-03 fix `d5376c97`) |
| `src/test/java/org/ctc/sitegen/StandingsPageGeneratorTest.java`                | source file without GROUPS-SWISS placeholder method                   | ✓ VERIFIED | Placeholder absent (`grep -c givenGroupsSwissLayoutSeason` = 0)                                                  |
| `src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java`             | source file without regression-fence test                             | ✓ VERIFIED | `givenPreExistingDriverNotMatchedByMatcher` absent (0)                                                           |
| `src/test/java/org/ctc/backup/it/AutoBackupBeforeImportFailureIT.java`         | unconditional POSIX assertion                                         | ✓ VERIFIED | `auto-backup ZIP must be cleaned up` present (1); `isWindows()` / `Assumptions` absent (0/0)                     |
| `.github/workflows/release.yml`                                                | hardened workflow, dry-run, SemVer, idempotency-guard                 | ✓ VERIFIED | All 7 hardening checks pass; YAML valid; post-REVIEW BREAKING CHANGE footer fix applied (CR-01)                  |
| `src/main/java/org/ctc/dataimport/DriverSheetImportService.java`               | season-aware resolver + GROUPS-layout gate + `usesGroups`             | ⚠ PARTIAL  | Resolver + `usesGroups` computation present; GROUPS-layout gate has nothing to gate (no warning surface exists)  |
| `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java`     | `showGroupColumn = anyMatch(TabPreview::usesGroups)` aggregation       | ✓ VERIFIED | Lines 52-53 set the model attribute exactly                                                                       |
| `src/main/resources/templates/admin/driver-import-preview.html`               | per-tab `th:if="${tab.usesGroups()}"` gate on Group cell              | ✗ MISSING  | Template has no Group cell and no `tab.usesGroups()` reference (Plan-drift accepted, see gap)                    |
| `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java`           | inverted #16/#17 + DRIV-01/02 edge cases                              | ⚠ PARTIAL  | 6 new tests (#24-#29) cover DRIV-01 (4) + DRIV-02 (2); but the plan-mandated #16/#17 INVERSION never happened — Plan-05 SUMMARY documents that those tests are unrelated to the resolver in today's file, and Mockito-default `Optional.empty()` paths cover the legacy fallback path implicitly. Existing pre-DRIV-01 tests #21-#23 still hold. |
| `CLAUDE.md`                                                                    | Conventions section gains Skill Invocation Naming                      | ✓ VERIFIED | Subsection present at line 222, 3 bullets (canonical / deprecated / regression-fence)                            |
| `docs/operations/release-runbook.md`                                          | operator runbook for retroactive v1.10.0/v1.11.0 + tag cleanup        | ✓ VERIFIED | 11 076 bytes, 6 sections, all destructive-op disciplines present                                                  |

### Key Link Verification

| From                                                                                                            | To                                                                                                | Via                                                       | Status      | Details                                                                                                              |
| --------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- | --------------------------------------------------------- | ----------- | -------------------------------------------------------------------------------------------------------------------- |
| `.planning/REQUIREMENTS.md` (CLEAN-01)                                                                          | `.planning/milestones/v1.11-phases/80-openrewrite-integration/deferred-items.md`                  | "Phase 80 JDT-cache" textual cross-ref                    | ✓ WIRED     | Exact string `Phase 80 JDT-cache diagnosis` present in REQUIREMENTS.md CLEAN-01 row (verified by grep)               |
| `.github/workflows/release.yml` (Determine version step)                                                        | `.github/workflows/release.yml` (Idempotency guard step)                                          | `steps.version.outputs.new_version`                       | ✓ WIRED     | Idempotency-guard step references `steps.version.outputs.new_version` (verified in YAML)                              |
| `.github/workflows/release.yml` (workflow_dispatch inputs)                                                       | every side-effecting step's `if:` guard                                                           | `inputs.dry-run != true`                                  | ✓ WIRED     | 8 occurrences of the negative-form guard predicate per Pitfall 5                                                      |
| `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` (resolver)                                     | `src/main/java/org/ctc/domain/repository/PhaseTeamRepository.java`                                | `phaseTeamRepository.findByPhaseIdAndTeamId(UUID, UUID)`  | ✓ WIRED     | Lines 439-441 invoke the repository inside the resolver; field is `@RequiredArgsConstructor`-injected at line 52     |
| `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` (buildTabPreview)                              | `src/main/java/org/ctc/domain/service/SeasonPhaseService.java`                                    | `seasonPhaseService.findByType(seasonId, PhaseType.REGULAR).orElse(null)` | ✓ WIRED | Lines 264-266 resolve `regularPhase` once per tab via the Optional API                                                |
| `src/main/resources/templates/admin/driver-import-preview.html`                                                | `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` (TabPreview record)              | Thymeleaf `tab.usesGroups()`                              | ✗ NOT_WIRED | Template has no `tab.usesGroups()` reference; the wiring exists at the controller layer only (`anyMatch(TabPreview::usesGroups)`) |
| `docs/operations/release-runbook.md` (Section 2 + 3)                                                            | `ghcr.io/jegr78/ctc-manager` (registry)                                                           | `docker push 1.10.0 / 1.11.0`                             | ⚠ DOCUMENTED | Runbook documents the push commands; actual push has not happened (origin shows no v1.10.0 / v1.11.0 tags)            |
| `docs/operations/release-runbook.md` (Section 4)                                                                | GitHub remote `refs/tags/v1.5 + v1.8`                                                             | `gh api -X DELETE`                                        | ⚠ DOCUMENTED | Runbook documents the DELETE; legacy short-form tags still present on origin per `git ls-remote --tags origin`        |

### Data-Flow Trace (Level 4)

Skipped for the non-UI artefacts (workflows, runbook, REQUIREMENTS.md). For driver-import:

| Artifact                                                                       | Data Variable     | Source                                                                                 | Produces Real Data | Status            |
| ------------------------------------------------------------------------------ | ------------------ | -------------------------------------------------------------------------------------- | ------------------ | ----------------- |
| `DriverSheetImportService.buildTabPreview` → `TabPreview`                     | `usesGroups`       | `regularPhase.getLayout() == PhaseLayout.GROUPS` (real DB-derived enum)                | ✓ Yes              | ✓ FLOWING         |
| `DriverSheetImportController.preview()` → model `showGroupColumn`              | `showGroupColumn`  | `preview.tabPreviews().stream().anyMatch(TabPreview::usesGroups)`                      | ✓ Yes              | ✓ FLOWING         |
| `driver-import-preview.html` consumer of `tab.usesGroups()`                    | n/a               | n/a — no consumer exists in the template                                                | n/a                | ✗ DISCONNECTED (no template consumer of the per-tab flag; the controller-level `showGroupColumn` aggregation also has no template consumer — `driver-import-preview.html` does not reference `${showGroupColumn}` either) |

### Behavioral Spot-Checks

| Behavior                                                                  | Command                                                                                                                                                | Result          | Status |
| ------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------- | ------ |
| `release.yml` parses as valid YAML                                        | `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/release.yml'))"`                                                                       | exit 0          | ✓ PASS |
| JaCoCo CSV exists and LINE coverage ≥ 88.88 %                              | `awk -F, 'NR>1 {m+=$8; c+=$9} END {printf "%.2f%%\n", c/(m+c)*100}' target/site/jacoco/jacoco.csv`                                                       | 88.97 %         | ✓ PASS |
| SpotBugs BugInstance count == 0                                            | `grep -c "<BugInstance " target/spotbugsXml.xml`                                                                                                       | 0               | ✓ PASS |
| `@Disabled` count in `src/test/java`                                       | `grep -rn "@Disabled" src/test/java                                                                                                                  \| wc -l`           | 0               | ✓ PASS |
| `Assumptions.` count in `src/test/java`                                    | `grep -rn "Assumptions\." src/test/java                                                                                                              \| wc -l`           | 0               | ✓ PASS |
| `/gsd:` regression-fence on 6 active top-level planning files              | `grep -r '/gsd:' .planning/{PROJECT,STATE,ROADMAP,REQUIREMENTS,MILESTONES,RETROSPECTIVE}.md                                                          \| wc -l`           | 0               | ✓ PASS |
| `git describe --tags --abbrev=0` removed from release.yml                   | `grep -c 'git describe --tags --abbrev=0' .github/workflows/release.yml`                                                                               | 0               | ✓ PASS |
| `git tag -a` (local tag) absent from runbook                               | `grep -cE "^git tag -a" docs/operations/release-runbook.md`                                                                                            | 0               | ✓ PASS |
| `git ls-remote --tags origin` shows v1.10.0 + v1.11.0                       | `git ls-remote --tags origin                                                                                                                         \| grep -E 'v1\.(10|11)\.0'` | absent         | ✗ FAIL — feeds SC #4 gap |

### Probe Execution

Not applicable: phase 88 is a mix of docs/workflow/refactor work; no project probes under `scripts/*/tests/probe-*.sh`. The user-confirmed live `./mvnw clean verify -Pe2e` exit 0 + GitHub-Actions `gh workflow run release.yml -F dry-run=true` (run 26080324918, `success`) are the canonical probes.

### Requirements Coverage

| Requirement | Source Plan(s) | Description                                                                                                       | Status         | Evidence                                                                                                            |
| ----------- | -------------- | ----------------------------------------------------------------------------------------------------------------- | -------------- | ------------------------------------------------------------------------------------------------------------------- |
| CLEAN-01    | 88-01          | `./mvnw verify` exits 0; CLEAN-01 flipped to Resolved with Phase-80 cross-ref                                       | ✓ SATISFIED    | LINE 88.97 % ≥ 88.88 %; JaCoCo CSV present; REQUIREMENTS.md bullet `[x]`, traceability `Resolved`                  |
| CLEAN-02    | 88-02          | YAGNI sweep — placeholder + regression-fence + Windows-conditional removed                                         | ✓ SATISFIED    | All 3 sub-items verified (placeholder/fence/Windows-conditional absent; unconditional POSIX assertion present)      |
| CLEAN-03    | 88-02          | `SiteGeneratorBaselineCaptureTest` refactored to standalone CommandLineRunner utility                              | ✓ SATISFIED    | Old test class deleted; `SiteGeneratorBaselineRefresh.java` present with `@Primary` (REVIEW WR-03 fix)              |
| REL-01      | 88-03          | `release.yml` hardened against duplicate-tag-pattern; dry-run-verifiable on PR branch                              | ✓ SATISFIED    | All 7 hardening checks pass; live workflow_dispatch run 26080324918 success; post-REVIEW CR-01 BREAKING CHANGE fix |
| REL-02      | 88-06          | v1.10.0 + v1.11.0 retroactive releases + legacy-tag cleanup + runbook                                              | ✗ BLOCKED      | Runbook ✓; actual publishing ✗ (post-merge operator action, see `gaps:`)                                            |
| DOCS-01     | 88-04          | CLAUDE.md Skill Invocation Naming + `/gsd:` regression-fence grep = 0                                              | ✓ SATISFIED    | Subsection present; CLAUDE.md grep 0; 6-active-file grep 0                                                          |
| DRIV-01     | 88-05          | Season-aware `resolveTeamByShortName(shortName, regularPhase)` — sub-with-PhaseTeam wins; legacy fallback preserved | ✓ SATISFIED    | Signature, body, all 5 call sites updated; 4 edge-case tests #24-#27 (incl. D-07 legacy fallback)                  |
| DRIV-02     | 88-05          | GROUPS-layout gate on warning emission + `usesGroups` flag + non-GROUPS rows render `—`                            | ⚠ PARTIAL      | Service `usesGroups` + controller `anyMatch` present; template gate + `—` placeholder + warning-emission test absent (Defensive Future-Proofing decision; see `gaps:`) |

Orphaned requirements: none. All 8 IDs declared in PLAN frontmatter map to REQUIREMENTS.md and are accounted for above.

**REQUIREMENTS.md Traceability table inconsistency (WARNING):** Only the CLEAN-01 row reads `Resolved`. CLEAN-02, CLEAN-03, REL-01, REL-02, DOCS-01, DRIV-01, DRIV-02 still read `Pending` despite their SUMMARY-claimed completion. The bullet checkboxes for CLEAN-02..DRIV-02 also still read `[ ]`. Either (a) plan-02/-03/-04/-05/-06 forgot to flip the table + checkboxes, or (b) the convention is "table flips only on phase-close milestone PR". This is consistent with the Plan-01-only `[x]` flip pattern set by 88-01, but it leaves an audit-trail gap.

### Anti-Patterns Found

| File                                                                          | Line | Pattern         | Severity | Impact                                                                          |
| ----------------------------------------------------------------------------- | ---- | --------------- | -------- | ------------------------------------------------------------------------------- |
| (none)                                                                        | —    | —               | —        | No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers in modified files     |
| `.planning/REQUIREMENTS.md`                                                   | 31-42 | Stale bullets   | ℹ Info   | 7 of 8 phase-88 requirement bullets still read `[ ]` despite SUMMARY-claimed completion; Traceability table likewise has 7 `Pending` rows. Audit-trail gap, no functional impact. |
| `src/main/resources/templates/admin/driver-import-preview.html`               | —    | Plan-Drift      | ⚠ Warning | No template change for SC #7 — `Defensive Future-Proofing` decision accepted in Plan-05 SUMMARY but template surface for the contract is absent. |

### Human Verification Required

#### 1. Operator dry-run check of the hardened release.yml post-merge contract

- **Test:** After the v1.12 milestone PR squash-merges to master, observe whether `release.yml` produces a `v1.12.0` artifact set (annotated tag at the merge SHA, GitHub Release page with auto-notes, JAR upload, `ghcr.io/jegr78/ctc-manager:1.12.0` Docker image push).
- **Expected:** ROADMAP SC #8 requires "v1.12 milestone PR's eventual squash-merge produces a working v1.12.0 release artifact set" — confirm the workflow run is `success` and all 4 deliverables exist.
- **Why human:** Can only run on the master push event; the Plan-03 dry-run verified version-determination + idempotency-guard in isolation but not the side-effecting steps.

#### 2. Operator visual review of release-runbook.md before executing it

- **Test:** Read `docs/operations/release-runbook.md` end-to-end. Verify each command in Sections 2-4 is executable as typed, no placeholder `<...>` markers remain unresolved (except the documented `<PROJECT_ROOT>` substitution), the per-tag confirmation prompt halts at each tag, Section 2 sequences `versions:set` BEFORE `package`, Section 3 includes `git fetch --tags origin` before `gh release create v1.11.0`.
- **Expected:** The runbook is operator-ready. Any ambiguity → revise before execution.
- **Why human:** Plan-06 SUMMARY collapsed the Task 88-06-02 human-verify checkpoint into automated grep predicates; readability is a human judgement.

#### 3. Operator execution of the runbook to close ROADMAP SC #4

- **Test:** Post-merge, execute Sections 2-4 of `docs/operations/release-runbook.md`. After completion, verify: `gh release list --limit 20` shows v1.10.0 + v1.11.0 + v1.12.0; `gh api /repos/jegr78/ctc-manager/git/refs/tags` shows no `^refs/tags/v[0-9]+$` short-form refs; `docker pull ghcr.io/jegr78/ctc-manager:1.10.0` and `:1.11.0` succeed.
- **Expected:** All 4 SC #4 deliverables (retroactive v1.10.0 + v1.11.0, legacy-tag cleanup, runbook documenting catch-up) are now closed.
- **Why human:** Destructive remote ops (T-88-02 threat model) require operator presence; cannot be automated by an agent.

#### 4. Operator visual review of `driver-import-preview.html` rendering under mixed GROUPS+LEAGUE tabs (Plan-05 Task 88-05-04 skipped)

- **Test:** Per Plan-05 must_have #7, eventually re-introduce the per-row Group cell and verify it renders `—` for LEAGUE tabs and the real group name (or warning badge) for GROUPS tabs in a mixed multi-tab preview. Until a Group cell exists, this check is not actionable.
- **Expected:** Either the template gains the gated cell, OR the ROADMAP SC #7 is amended to reflect the service/controller-only future-proofing scope.
- **Why human:** Decision between "land the template now to close SC #7" vs. "accept Defensive Future-Proofing as the new SC contract" is a product/scope decision.

### Gaps Summary

Two gaps prevent a clean PASS:

1. **Template surface for SC #7 / DRIV-02 not implemented.** Plan-05 SUMMARY documents a user-approved "Defensive Future-Proofing" decision: the deferred-debug doc's three surfaces (`TEAM_NOT_IN_REGULAR_PHASE` warning, page-wide `showGroupColumn` controller attribute, per-row Group cell in `driver-import-preview.html`) do not exist in v1.12 code today, so only the service-layer + controller-layer API surfaces were added. The ROADMAP SC #7 contract specifically asks for the per-row `—` rendering and the warning suppression — neither template/warning surface exists, so SC #7 is partially closed at best. The new test #29 asserts `usesGroups=true` for GROUPS layout but cannot assert the warning emission contract (no warning code path exists). To pass: either (a) re-introduce the per-row Group cell + `TEAM_NOT_IN_REGULAR_PHASE` warning + the matching tests, OR (b) record an explicit override accepting Defensive Future-Proofing as the new contract, OR (c) amend ROADMAP SC #7 to reflect the narrowed scope.

2. **REL-02 retroactive publishing not executed.** Plan-06 deliberately delivered the operator runbook only; ROADMAP SC #4 reads as a binding contract that v1.10.0 + v1.11.0 ARE published and legacy short-form tags ARE deleted. Live `git ls-remote --tags origin` confirms v1.10.0 absent, v1.11.0 absent, `v1.5` + `v1.8` still present. No later milestone phase addresses this; Phase 89-91 are PERF + closer. To pass: operator follows the runbook post-merge (closes SC #4), OR ROADMAP SC #4 is amended to read "the catch-up procedure is documented in the runbook" (descopes the actual publishing), OR an explicit override accepts the post-merge-operator-action pattern as a defensible phase-88 scope boundary.

**Both gaps look intentional.** The Plan-05 Defensive Future-Proofing decision was user-approved, and the Plan-06 post-merge-operator-action pattern is documented in 88-CONTEXT D-05. To accept these deviations, add to this VERIFICATION.md frontmatter:

```yaml
overrides:
  - must_have: "ROADMAP SC #7 — per-row Group cell with `${tab.usesGroups()}` gate and `—` placeholder"
    reason: "Defensive Future-Proofing — the deferred-debug doc surfaces no longer exist in v1.12 code; service+controller API surfaces are enough to fence future regressions; template surface deferred until a real Group-UI feature is requested"
    accepted_by: "<your name>"
    accepted_at: "<ISO timestamp>"
  - must_have: "ROADMAP SC #4 — v1.10.0 + v1.11.0 retroactive releases published + legacy short-form tags deleted"
    reason: "Plan-06 deliberately delivered the runbook only; actual publishing is a post-merge operator action (per 88-CONTEXT D-05). The post-merge step closes SC #4 outside the phase boundary."
    accepted_by: "<your name>"
    accepted_at: "<ISO timestamp>"
```

Otherwise the gaps should be closed by either (a) landing the template + retroactive publish, or (b) amending ROADMAP SC #4 + #7 to match the delivered scope.

---

_Verified: 2026-05-19T08:15:18Z_
_Verifier: Claude (gsd-verifier)_
