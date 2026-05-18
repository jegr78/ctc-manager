# Phase 88: Build/Release Unblockers, YAGNI Sweep, Doc-Conventions & Driver-Import Gap-Closure - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Land all v1.12 "unblockers" before any PERF work begins. Eight requirements across four sub-domains:

- **CLEAN** (3): unblock `./mvnw verify` exit 0 + JaCoCo CSV, YAGNI-sweep speculative `@Disabled`/Windows-conditional cruft, refactor `SiteGeneratorBaselineCaptureTest` from `@Test @Disabled` anti-pattern to standalone utility.
- **REL** (2): harden `.github/workflows/release.yml` against the 4-milestone duplicate-tag regression, retroactively publish v1.10.0 + v1.11.0 + delete legacy short-form tags (`v1.5`, `v1.6`, `v1.8`, `v1.9`).
- **DOCS** (1): document `/gsd-<name>` as canonical skill-invocation prefix in `CLAUDE.md` Conventions, deprecate `/gsd:<name>`.
- **DRIV** (2): season-aware shortName resolver (`PhaseTeam`-in-target-phase wins over parent), GROUPS-layout gate for group-assignment warnings + per-tab `usesGroups` flag.

Phase 88 is the v1.12 milestone's first phase and lands all baseline-correctness work before Phases 89-91 implement the PERF levers. Out-of-scope: any PERF work (Phases 89-91), UX-01 stretch (Phase 91).

</domain>

<decisions>
## Implementation Decisions

### Plan Structure

- **D-01: Six themen-gebundelte Plans, ALL sequential** (no parallel waves). Bundling related REQs reduces test-rewrite churn and produces atomic commits; sequential execution honors [[wave-pause]] discipline after recent multi-wave instability.

  Plan order:
  1. **Plan-01 CLEAN-01** (verify-only baseline gate — FIRST commit)
  2. **Plan-02 CLEAN-02 + CLEAN-03** (`@Disabled` sweep + Baseline-Refactor; shared `grep -rn "@Disabled" src/test/java = 0` acceptance)
  3. **Plan-03 REL-01** (`release.yml` hardening + synthetic `dry-run: true` verification)
  4. **Plan-04 DOCS-01** (CLAUDE.md "Skill Invocation Naming" paragraph)
  5. **Plan-05 DRIV-01 + DRIV-02** (resolver first, then layout-gate; shared test rewrite)
  6. **Plan-06 REL-02** (retroactive v1.10.0/v1.11.0 + legacy-tag cleanup; operator-action runbook)

### CLEAN-01 Reality Check

- **D-02: CLEAN-01 downgrades to verify-only.** `./mvnw clean test-compile` exits 0 in current workspace; v1.11 Phase 80 `deferred-items.md` (2026-05-16) already documented the apparent `BackupSchemaExclusionIT.java:40` compile error as RESOLVED — Eclipse JDT cache wrote stale `.class` files with `"Unresolved compilation problem"` markers, never javac. Memory rule [[clean-maven-build-authority]] was formulated *because of* this exact incident.

  **Plan-01 scope:** run `./mvnw clean verify` on the v1.12 PR-branch head, confirm JaCoCo CSV is generated, update `REQUIREMENTS.md` CLEAN-01 entry to "resolved via Phase 80 JDT-cache diagnosis (2026-05-16); verified clean on v1.12 head"; no source-level changes to `BackupSchemaExclusionIT.java` or any other test class. If the clean build fails for an unrelated reason, the diagnosis is real and Plan-01 expands to fix the underlying issue (highly unlikely given current state).

  **Explicit non-goal:** structural guards (typed-witness, `@SuppressWarnings("unchecked")`) are NOT applied — that would address an IDE-cache problem in source code and contradict [[clean-maven-build-authority]].

### CLEAN-02 + CLEAN-03 (Plan-02)

- **D-03: Three deletions/simplifications + one refactor, single plan, single grep-acceptance line.** Per REQUIREMENTS.md CLEAN-02:
  - DELETE `StandingsPageGeneratorTest.givenGroupsSwissLayoutSeason_whenGeneratePerGroup_thenShowBuchholzColumn` (empty placeholder, Phase 62 Plan 5/6 fixture deferral never materialized).
  - DELETE `DriverSheetImportServiceIT.givenPreExistingDriverNotMatchedByMatcher_whenExecuteNewDriverRow_thenReusesExistingDriver` (regression-fence for structurally unreachable path; already covered by Test #7 cross-tab same-PSN scenario).
  - SIMPLIFY `AutoBackupBeforeImportFailureIT.java:198-208` — drop `isWindows()` conditional + `Assumptions.assumeFalse(true, "Windows file-locking ...")` skip, keep POSIX assertion unconditional.
  - REFACTOR `SiteGeneratorBaselineCaptureTest` from `@Test @Disabled` to standalone utility (CLEAN-03).

  Plan-02 acceptance: `grep -rn "@Disabled" src/test/java` returns 0 hits AND `grep -rn "Assumptions\." src/test/java` returns 0 hits.

### REL-01 Verification (Plan-03)

- **D-04: Synthetic `dry-run: true` workflow_dispatch input.** `release.yml` gets a `workflow_dispatch` trigger with a `dry-run: boolean` input. The side-effecting steps (`versions:set`, build, tag-push, `gh release create`, docker push) get `if: inputs.dry_run != true` guards. Determine-Version + Idempotency-Guard + Parser run unconditionally so the new logic is exercised end-to-end. Verified on the v1.12 PR-branch with `gh workflow run release.yml -F dry-run=true` BEFORE Plan-03 is merged.

  Trade-off accepted: minor `if:`-conditional bloat in release.yml; gains full exercise of the hardened parser/guard logic on the actual workflow without side effects.

### REL-02 Catch-up Mechanism (Plan-06)

- **D-05: `gh release create --target <SHA>` runbook — strict remote-only operations.** Per [[no-local-git-tags]], no `git tag -a v… && git push origin v…` from the operator's machine. Per-version procedure documented in `docs/operations/release-runbook.md`:
  1. Build JAR + Docker image from the historical SHA via `git worktree add /tmp/v1.10 45aabfd0` + `./mvnw versions:set -DnewVersion=1.10.0 -DgenerateBackupPoms=false` + `./mvnw -DskipTests package`
  2. `gh release create v1.10.0 --target 45aabfd0 --title "v1.10.0" --generate-notes /tmp/v1.10/target/ctc-manager-1.10.0.jar` — creates remote tag + GitHub Release in one operation; no local tag push.
  3. `docker push ghcr.io/jegr78/ctc-manager:1.10.0` from the same worktree.
  4. Cleanup: `git worktree remove /tmp/v1.10`.
  5. Repeat for v1.11.0 (SHA `598d1431`).

  **Legacy short-form tag deletion** (`v1.5`, `v1.6`, `v1.8`, `v1.9`) — pure remote operation:
  ```
  gh api -X DELETE /repos/jegr78/ctc-manager/git/refs/tags/v1.5
  ```
  per tag, with explicit per-tag operator confirmation in the runbook (destructive op, cf. CLAUDE.md "Executing actions with care").

### DRIV-01 + DRIV-02 (Plan-05)

- **D-06: Resolver-first, then layout-gate** within Plan-05's commit sequence.
  - **Step 1 (DRIV-01):** `resolveTeamByShortName(shortName)` → `resolveTeamByShortName(shortName, SeasonPhase regularPhase)`. All 5 call sites (lines 136, 147, 167, 202, 291 in `DriverSheetImportService.java`) updated to pass `regularPhase`. Tests `DriverSheetImportServiceTest #16` and `#17` inverted to assert sub-team-with-`PhaseTeam` wins; new test added for "no candidate has PhaseTeam → parent-precedence fallback".
  - **Step 2 (DRIV-02):** `buildTabPreview` (lines 311-325) gates the `TEAM_NOT_IN_REGULAR_PHASE` warning emission + `PhaseTeam` lookup on `regularPhase.getLayout() == PhaseLayout.GROUPS`. `TabPreview` record gains a per-tab `usesGroups` boolean. `driver-import-preview.html` adds `th:if="${tab.usesGroups()}"` gate on the per-row Group cell. Test #16/#17 receive a second pass to assert no-warning on LEAGUE; new test asserts warning IS emitted for GROUPS with missing `PhaseTeam`.

  Two commits within Plan-05 — atomic per concern, deferred-debug-recommended sequence "test rewrite handles both contracts without churn" preserved.

- **D-07: Legacy fallback — preserve parent-precedence when `regularPhase == null`.** Per Phase 66 D-06 legacy semantics. When the resolver receives `regularPhase == null` (pre-V4 legacy season without a REGULAR phase, or graceful `EntityNotFoundException` recovery), fall through to the existing parent-precedence rule + WARN-log. No `BusinessRuleException` thrown — preserves legacy import behaviour.

### DOCS-01 (Plan-04)

- **D-08: CLAUDE.md "Conventions" section gains a "Skill Invocation Naming" paragraph.** Wording captures: (a) canonical form is `/gsd-<name>` (dash); (b) `/gsd:<name>` (colon, pre-2026) is deprecated; (c) regression-fence is `grep -r "/gsd:" .planning/*.md` returns 0 hits in top-level active files. Archived `milestones/v*.x-*.md` files explicitly out of scope. Exact paragraph wording is Claude's discretion within these constraints.

### Claude's Discretion

- **CLEAN-03 utility shape:** `CommandLineRunner` Spring bean (invoked via `./mvnw exec:java -Dexec.mainClass=...`) vs. `main()`-style helper class — planner picks whichever is closer to existing test-utility conventions in `src/test/java/org/ctc/sitegen/`.
- **DRIV-02 template fallback shape:** non-GROUPS rows render `—` placeholder vs. hide the Group cell entirely vs. show "n/a" — planner picks; user-facing UX detail.
- **REL-02 runbook step format:** numbered list with per-step `gh`/`docker` commands and confirmation prompts vs. shell script template — planner picks.
- **CLAUDE.md DOCS-01 paragraph wording:** exact prose within the constraints in D-08.
- **CLEAN-02 (b) Test #7 enhancement:** deferred-debug states Test #7 already covers the cross-tab same-PSN scenario; no additional test needed unless coverage actually regresses (verified by JaCoCo delta after deletion).
- **`docs/operations/release-runbook.md` placement:** new file vs. extending an existing operations doc — planner picks.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope & Requirements

- `.planning/ROADMAP.md` § "Phase 88: Build/Release Unblockers, YAGNI Sweep, Doc-Conventions & Driver-Import Gap-Closure" — goal, success criteria (8 items), dependencies
- `.planning/REQUIREMENTS.md` — CLEAN-01, CLEAN-02, CLEAN-03, REL-01, REL-02, DOCS-01, DRIV-01, DRIV-02 full text
- `.planning/PROJECT.md` § "Current Milestone: v1.12 Driver-Import Gap-Closure & Test Performance Round 2" — target features; § "Key Decisions" — Phase-66 D-06 (parent-precedence default that DRIV-01 refines)
- `.planning/STATE.md` § "Active Milestone — v1.12" + § "Baselines to Preserve" (JaCoCo ≥ 88.88 %, 1675 tests, `EXPORT_ORDER` = 24, SCHEMA_VERSION = 1, SpotBugs = 0, CodeQL exit 0)

### Driver-Import Gap-Closure (DRIV-01, DRIV-02)

- `.planning/debug/deferred/shortname-resolver-picks-parent-without-phaseteam.md` — DRIV-01 full diagnosis, root cause, suggested algorithm, edge cases (4 listed in § Resolution), files_to_change
- `.planning/debug/deferred/group-warnings-for-non-groups-seasons.md` — DRIV-02 full diagnosis, dual-surface fix (service-side gate + template-side per-tab flag), files_to_change

### CLEAN-01 Resolution Reference

- `.planning/milestones/v1.11-phases/80-openrewrite-integration/deferred-items.md` § "2026-05-16 — RESOLVED (FALSE POSITIVE): apparent IT compile error in BackupSchemaExclusionIT" — IDE-cache diagnosis + telltale-string rule

### Testing & Build Conventions

- `.planning/codebase/TESTING.md` § "Test Categorization (`@Tag`)" — every new test class needs `@Tag(...)`; § "Test Invocation Discipline" — one final `./mvnw verify -Pe2e` per phase
- `CLAUDE.md` — Subagent Rules, "Tag Tests by Category" rule, Git Workflow, "Static Analysis" gate, "OSIV" constraint, "Conventions" (target for DOCS-01)

### Operational Conventions

- `docs/operations/` (existing directory layout) — convention shape for `release-runbook.md` (REL-02 deliverable)

### Backup Wire Contract (must not be touched)

- `.planning/PROJECT.md` § "Backup Wire Contract (v1.10)" — `BackupSchema.SCHEMA_VERSION = 1`, 24-entity `EXPORT_ORDER` invariant (CLEAN-01 must verify the existing IT still passes; Plan-01 does not change this surface)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`PhaseTeamRepository.findByPhaseIdAndTeamId(UUID, UUID)`** — already exposed, exactly what DRIV-01's season-aware step needs. No new repository surface required.
- **`TeamRepository.findAllByShortName(String)`** — returns `List<Team>`, the pre-filtered candidate set DRIV-01 iterates.
- **`SeasonPhase.layout`** — `@Enumerated PhaseLayout {LEAGUE, GROUPS, BRACKET}`, `nullable=false`. Cheapest in-memory check, no DB roundtrip for DRIV-02's gate.
- **`TabPreview` record** (`DriverSheetImportService.java:414-426`) — adding a `boolean usesGroups` field is a straightforward record extension; downstream consumer is the template only.
- **`PhaseTestFixtures.regularPhase(...)` / `.groupsRegularPhase(...)`** — fixtures used by current tests #16/#17; DRIV-02 test inversions swap one for the other.
- **`@Tag("integration")` routing** (`.planning/codebase/TESTING.md`) — existing convention; CLEAN-01 verify-only does not add new test classes.

### Established Patterns

- **Parent-precedence on shortName multi-match** (Phase 66 D-06 default) — DRIV-01 *refines*, not inverts: sub-team-with-`PhaseTeam`-in-target-REGULAR-phase wins; parent-precedence remains the legacy fallback (D-07).
- **`@SpringBootTest + @ActiveProfiles("dev") + @Tag("integration")`** — pattern used by `BackupSchemaExclusionIT` (the CLEAN-01 verify target); standard for Spring-context ITs.
- **`@Disabled` anti-pattern** — Phase 88 closes 3 instances (CLEAN-02 (a) (b) + CLEAN-03); post-CLEAN-03 grep returns 0.
- **`gh release create --target <SHA>` for retroactive tagging** — respects [[no-local-git-tags]]; creates remote tag + GitHub Release in one call. Memory rule applies even for retroactive operations.
- **`./mvnw clean test-compile`** as compile-source-of-truth ([[clean-maven-build-authority]]) — `target/` cleared first to evict any stale JDT-cache `.class` files.

### Integration Points

- **`.github/workflows/release.yml`** (REL-01 hardening target) — current state: `git describe --tags --abbrev=0` at lines 36-37 (broken pattern); `actions/checkout@v6` at line 19 needs `fetch-tags: true` added; bash parser at lines 71-83 needs PATCH-default + numeric MAJOR/MINOR validation; pre-`versions:set` idempotency guard added BEFORE line 93's "Set release version" step.
- **`src/main/java/org/ctc/dataimport/DriverSheetImportService.java`** — resolver at lines 392-408 (DRIV-01 surface); `buildTabPreview` at lines 226-..., warning emission at lines 311-325 (DRIV-02 surface); 5 call sites at lines 136, 147, 167, 202, 291.
- **`src/main/java/org/ctc/admin/controller/DriverSheetImportController.java`** lines 55-64 — page-wide `showGroupColumn = anyMatch(...)` (DRIV-02 changes from page-wide to per-tab via `TabPreview.usesGroups()`).
- **`src/main/resources/templates/admin/driver-import-preview.html`** — per-row Group-cell rendering currently gated only by page-scoped `${showGroupColumn}`; DRIV-02 adds per-tab `${tab.usesGroups()}` gate, `—` (or planner-picked placeholder) for non-GROUPS rows.
- **`CLAUDE.md`** "Conventions" section — DOCS-01 insertion point; insertion paragraph documents `/gsd-<name>` vs deprecated `/gsd:<name>`.
- **v1.12 PR-branch `gsd/v1.12-driver-import-and-test-perf`** — Phase 88's working branch; per-plan atomic commits land here, milestone PR squash-merges to master.
- **`gh release create --generate-notes`** — auto-generates release notes from Conventional Commits between the previous tag and the target SHA; standard `gh` workflow.
- **`ghcr.io/jegr78/ctc-manager`** — Docker registry; REL-02 pushes `1.10.0` and `1.11.0` tags directly via `docker push`.

</code_context>

<specifics>
## Specific Ideas

- **CLEAN-01 acceptance phrasing** in REQUIREMENTS.md update — explicit reference back to `.planning/milestones/v1.11-phases/80-openrewrite-integration/deferred-items.md` so future readers immediately see the JDT-cache root cause without re-discovery; matches the [[clean-maven-build-authority]] precedent of documenting JDT signatures for fence-against-recurrence.
- **REL-02 retroactive notes generation** — `--generate-notes` between `45aabfd0..v1.9.0` for v1.10.0 and `45aabfd0..598d1431` for v1.11.0 (i.e., from the *previous* version's SHA to the target SHA). Notes should match the convention set by `gh release create` on past releases.
- **REL-02 worktree pattern** — `git worktree add /tmp/v1.10 45aabfd0` keeps the v1.12 branch clean of the historical `versions:set` change; the worktree is throwaway, `git worktree remove` after JAR is published.
- **DRIV-02 per-tab `usesGroups` propagation** — `usesGroups = regularPhase != null && regularPhase.getLayout() == PhaseLayout.GROUPS`; computed once per tab in `buildTabPreview`, stored in `TabPreview`, consumed exclusively by the template + controller's page-scoped `showGroupColumn` aggregation (page-scoped flag stays — it now reflects "any tab uses groups" for backwards compat).
- **`gh api -X DELETE /repos/jegr78/ctc-manager/git/refs/tags/<tag>`** — single-call legacy-tag delete; alternative `gh release delete <tag>` only works if a GitHub Release was associated (legacy short-form tags have no Releases). The `git/refs/tags` API endpoint is the unambiguous primitive.

</specifics>

<deferred>
## Deferred Ideas

- **`@SuppressWarnings("unchecked")` / typed-witness preemptive guard on `BackupSchemaExclusionIT`** — rejected (D-02). Addressing an IDE-cache problem in source code contradicts [[clean-maven-build-authority]].
- **Pre-emptive Test #7 enhancement for the deleted CLEAN-02 (b) regression-fence** — only if JaCoCo delta after Plan-02 shows actual coverage regression. Deferred-debug confirms current Test #7 covers the path; deferring to a "post-Plan-02 coverage spot-check" action.
- **Strict `BusinessRuleException` for `regularPhase == null` in DRIV-01** — rejected (D-07). Legacy pre-V4 data would break; parent-precedence preserves Phase 66 semantics gracefully.
- **Aggressive parallel Wave execution** — rejected (D-01). Sequential plans per [[wave-pause]] + [[subagent-stability]] memory.
- **Throwaway one-shot `retroactive-release.yml` workflow for REL-02** — rejected (D-05). Adds workflow file that must be cleaned up afterward; runbook-driven `gh` operations are simpler and reusable for any future retroactive-tag scenario.
- **PERF-related work** — out of Phase 88 scope; Phases 89-91 own PERF-01..06.
- **UX-01 (Google API typed-exception hierarchy + categorized error UX)** — stretch, Phase 91 (or descope to v1.13).

### Reviewed Todos (not folded)
None — `gsd-sdk query todo.match-phase 88` returned 0 matches.

</deferred>

---

*Phase: 88-Build/Release Unblockers, YAGNI Sweep, Doc-Conventions & Driver-Import Gap-Closure*
*Context gathered: 2026-05-18*
