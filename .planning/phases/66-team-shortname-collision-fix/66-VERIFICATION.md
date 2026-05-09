---
phase: 66-team-shortname-collision-fix
verified: 2026-05-08T11:30:00Z
status: passed
score: 9/9 must-haves verified
overrides_applied: 0
re_verification:
  previous_status: passed
  previous_score: 9/9
  superseded_truths: [2, 6, 7, 8, 9]
  superseded_by: phase-70
  note: "Phase 70 inverts the season-aware sub-team resolver and removes group resolution from the import preview path. Original Phase-66 hotfix scope (no crash on shortName collision) remains satisfied — only the post-resolution-tail behavior changed."
previous_re_verification:
  previous_status: passed
  previous_score: 7/7
  gaps_closed:
    - "GAP-66-01 (shortname-resolver-picks-parent-without-phaseteam): season-aware resolver — sub-team-with-PhaseTeam wins over parent in target REGULAR phase"
    - "GAP-66-02 (group-warnings-for-non-groups-seasons): TEAM_NOT_IN_REGULAR_PHASE warnings layout-gated to GROUPS phases; per-row Group cell gated by tab.usesGroups()"
  gaps_remaining: []
  regressions: []
  note: "Post-gap-closure verification (2026-05-08) — supersedes the May 7 PASS report which only covered Plan 66-01 (parent-precedence resolver). Plans 66-02 + 66-03 introduced season-aware step + layout gate per UAT findings. Itself superseded by Phase 70 (see re_verification above)."
---

# Phase 66: Team ShortName Collision Fix — Post-Gap-Closure Verification Report

**Phase Goal:** Driver CSV imports must work without spurious `TEAM_NOT_IN_REGULAR_PHASE` warnings on shortName collisions and on non-GROUPS seasons, without crashing on duplicate shortNames. Source: ROADMAP.md Phase 66 + UAT-discovered gaps in `66-UAT.md`.

**Verified:** 2026-05-08T11:30:00Z
**Status:** passed
**Re-verification:** Yes — post-gap-closure (plans 66-02 + 66-03 closed GAP-66-01 + GAP-66-02 from `66-UAT.md`).

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Driver sheet import does not crash on shortName collision (parent + sub) — original `IncorrectResultSizeDataAccessException` regression fence holds | VERIFIED | `DriverSheetImportService.java:436` calls list-returning `findAllByShortName` exclusively; resolver returns `Optional<Team>` cleanly. Tests #19-#22 preview parent+sub collisions without exception. UAT Test 1 result: PASS. |
| 2 | Multi-match in a season WITH REGULAR phase prefers the candidate that has a PhaseTeam in that REGULAR phase (sub-team-with-PhaseTeam wins) | VERIFIED | `DriverSheetImportService.java:443-449` iterates candidates and returns first with PhaseTeam in `regularPhase`. Test #19 (line 734) asserts sub-team wins, no warning, no error. |
| 3 | Multi-match WITHOUT a REGULAR phase falls back to parent precedence (legacy preserved) | VERIFIED | `DriverSheetImportService.java:450-454` selects first `parentTeam == null`. Test #21 (line 817) asserts parent picked when `findRegularPhase` throws `EntityNotFoundException`; `verifyNoInteractions(phaseTeamRepository)` confirms layout-blind path. |
| 4 | Multi-parent edge case (no parent among candidates, no PhaseTeam either) logs WARN and picks first deterministically — no exception | VERIFIED | `DriverSheetImportService.java:456-457` `log.warn(...)` + `return Optional.of(matches.get(0))`. Test #22 (line 847) asserts no exception. |
| 5 | All 5 service call sites pass `regularPhase` — no orphan single-arg invocation remains | VERIFIED | `grep -nE "resolveTeamByShortName\(" DriverSheetImportService.java` → 1 declaration (line 435) + 5 call sites (lines 144, 155, 175, 204, 309), each with `regularPhase` argument. |
| 6 | TEAM_NOT_IN_REGULAR_PHASE warning is layout-gated: only emitted when `regularPhase.getLayout() == PhaseLayout.GROUPS` | VERIFIED | `DriverSheetImportService.java:328` gates the entire group-resolution branch; LEAGUE phases short-circuit before `phaseTeamRepository` lookup. Test #23 (line 875) asserts empty warnings + `verifyNoInteractions(phaseTeamRepository)` for LEAGUE; Test #16 (line 658) asserts warning STILL fires for GROUPS. |
| 7 | `TabPreview.usesGroups` flag computed once per tab from canonical signal | VERIFIED | `DriverSheetImportService.java:269` computes `regularPhase != null && regularPhase.getLayout() == PhaseLayout.GROUPS`; passed to canonical `TabPreview` constructor at line 398. Field declared at line 477. Test #24 asserts `tab.usesGroups()=true` for GROUPS; Test #23 asserts `false` for LEAGUE. |
| 8 | Per-row Group cell in template gated by `tab.usesGroups()` across all 5 buckets; "No group" badge never renders for non-GROUPS tabs | VERIFIED | `driver-import-preview.html` lines 60-64, 85-89, 115-119, 150-154, 180-184 — all 5 buckets render three branches: name when usesGroups+name, badge when usesGroups+null, em-dash otherwise. `grep` confirms 15 occurrences of `tab.usesGroups()` (3 × 5) and 5 of `&#x26A0; No group` (each guarded). |
| 9 | Page-wide `showGroupColumn` (column header) preserved unchanged; non-GROUPS tabs get column header in mixed multi-tab previews but render em-dash content | VERIFIED | `DriverSheetImportController.java:57-64` retains `anyMatch(GROUPS)` page-wide gate; `model.addAttribute("showGroupColumn", ...)`. Template column-header `<th th:if="${showGroupColumn}">` preserved across all 5 buckets; per-row `<td>` is also gated by `showGroupColumn` then narrowed by `tab.usesGroups()`. |

**Score:** 9/9 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/org/ctc/domain/repository/TeamRepository.java` | `List<Team> findAllByShortName(String)` derived finder; legacy `findByShortName` preserved | VERIFIED | Line 26 declares list finder; line 17 retains `Optional<Team> findByShortName(String)` with Javadoc clarifying uniqueness contract; line 19 retains `findByShortNameIgnoreCase`. |
| `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` | Season-aware resolver + layout-gated warnings + 5 migrated call sites + per-tab `regularPhase` resolution + `usesGroups` field on `TabPreview` | VERIFIED | Resolver at line 435 implements 7-step D-06 (revised) algorithm; per-tab `regularPhase` resolved once in execute (lines 128-133) and preview (lines 258-264); layout gate at lines 269 + 328; 5 call sites all pass `regularPhase`; `TabPreview` record extended with `boolean usesGroups` at line 477. |
| `src/main/resources/templates/admin/driver-import-preview.html` | All 5 buckets gate per-row Group cell on `tab.usesGroups()`; em-dash placeholder when false; column header preserves page-wide gate | VERIFIED | Lines 60-64, 85-89, 115-119, 150-154, 180-184 each render the three-branch pattern. Column header `<th th:if="${showGroupColumn}">` retained at lines 54, 79, 106, 141, 174. |
| `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` | 31 tests total; tests #16/#17/#20 fixtures use `groupsRegularPhase`; tests #19/#20 inverted; tests #21/#22/#23/#24 cover new contracts | VERIFIED | 31 `@Test` methods present; #16 (line 658), #17 (line 685), #20 (line 771) use `PhaseTestFixtures.groupsRegularPhase(...)`; #19 (line 734) asserts sub-team wins via PhaseTeam; #21 (line 817) covers no-REGULAR-phase fallback; #22 (line 847) covers two-parent edge; #23 (line 875) covers LEAGUE no-warning; #24 (line 899) covers GROUPS positive contract. |
| `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` | `showGroupColumn` page-wide gate preserved unchanged | VERIFIED | Lines 57-64 retain `anyMatch(p -> p.getLayout() == PhaseLayout.GROUPS)` page-wide check. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `DriverSheetImportService.resolveTeamByShortName` | `TeamRepository.findAllByShortName` | `teamRepository.findAllByShortName(shortName)` | WIRED | Line 436. |
| `DriverSheetImportService.resolveTeamByShortName` (multi-match path) | `PhaseTeamRepository.findByPhaseIdAndTeamId` | iterate candidates with `regularPhase != null` guard | WIRED | Lines 443-449. |
| `DriverSheetImportService.execute` (4 sites) | per-tab `regularPhase` resolution | `seasonPhaseService.findRegularPhase(season.getId())` with `EntityNotFoundException → null` | WIRED | Lines 128-133; consumed at lines 144, 155, 175, 204. |
| `DriverSheetImportService.buildTabPreview` (preview) | per-tab `regularPhase` + `usesGroups` | `seasonPhaseService.findRegularPhase(suggestedSeasonId)`; `usesGroups = regularPhase != null && layout == GROUPS` | WIRED | Lines 258-269; consumed at line 309 (resolver) and line 328 (layout gate). |
| `DriverSheetImportService.buildTabPreview` warning emission | gated on `PhaseLayout.GROUPS` | `if (regularPhase != null && regularPhase.getLayout() == PhaseLayout.GROUPS)` | WIRED | Line 328 — LEAGUE phases short-circuit; no `phaseTeamRepository` lookup, no warning. |
| `TabPreview` record | template `tab.usesGroups()` | passed in canonical constructor at line 398 | WIRED | All 5 buckets in template consume `tab.usesGroups()`. |
| `DriverSheetImportController` | `showGroupColumn` model attribute | `model.addAttribute("showGroupColumn", ...)` line 64 | WIRED | Template column-header `th:if="${showGroupColumn}"` at lines 54, 79, 106, 141, 174. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `DriverSheetImportService.preview` | `regularPhase` | `seasonPhaseService.findRegularPhase(suggestedSeasonId)` (real DB-backed Spring Data) with `EntityNotFoundException → null` fallback | Yes — bound to actual `SeasonPhase` entity with `getLayout()` enum from DB | FLOWING |
| `DriverSheetImportService.preview` | `usesGroups` | derived from real `regularPhase.getLayout()` enum | Yes — `PhaseLayout.GROUPS` vs `LEAGUE` distinguishable in execution | FLOWING |
| `DriverSheetImportService.resolveTeamByShortName` | `matches` | `teamRepository.findAllByShortName(shortName)` (Spring Data derived, real JPQL) | Yes — returns DB-backed `Team` rows | FLOWING |
| `DriverSheetImportService.resolveTeamByShortName` (multi-match step) | PhaseTeam presence per candidate | `phaseTeamRepository.findByPhaseIdAndTeamId(phase.id, team.id)` | Yes — real DB lookup | FLOWING |
| Template per-row Group cell | `tab.usesGroups()`, `row.resolvedGroupName()` | `TabPreview.usesGroups` field populated at line 398; `resolvedGroupName` written at lines 332, 354, 366, 375, 380, 384, 389 | Yes — both fields populated from real domain logic | FLOWING |

No HOLLOW or DISCONNECTED paths detected.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| `DriverSheetImportServiceTest` passes (31 tests including all gap-closure tests) | `./mvnw test -Dtest=DriverSheetImportServiceTest` | `Tests run: 31, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS | PASS |
| Resolver migration grep gate empty (no orphan single-arg call) | `grep -nE "resolveTeamByShortName\(" DriverSheetImportService.java` | 1 declaration + 5 call sites, all with `regularPhase` | PASS |
| No direct `findByShortName` calls in dataimport package | `grep -rn "teamRepository\.findByShortName(\|teamRepository\.findByShortNameIgnoreCase(" src/main/java/org/ctc/dataimport/` | empty | PASS |
| Layout-gate sites grep (must be 2: usesGroups computation + warning gate) | `grep -nE "regularPhase\.getLayout\(\) == PhaseLayout\.GROUPS" DriverSheetImportService.java` | 2 hits at lines 269 + 328 | PASS |
| Template "No group" badge never ungated | `grep -E '<span th:if="\$\{row\.resolvedGroupName\(\) == null\}" class="badge badge-warning">' driver-import-preview.html` | empty (every badge guarded by `tab.usesGroups()`) | PASS |
| Template `tab.usesGroups()` count (3 × 5 buckets) | `grep -c "tab.usesGroups()" driver-import-preview.html` | 15 | PASS |
| Template "No group" badge count (1 × 5 buckets) | `grep -c "&#x26A0; No group" driver-import-preview.html` | 5 | PASS |
| `findAllByShortName` declared exactly once with correct signature | `grep -c "List<Team> findAllByShortName(String shortName);" TeamRepository.java` | 1 | PASS |
| TDD commit ordering (test → fix → docs per plan) | `git log --oneline` for phase 66 | `dd123e0 → d204624 → 4d26b75 → 852ba9d` (Plan 01); `a7a7994 → 9665d42 → 58ff793` (Plan 02); `48dcfeb → 3b66e77 → dd2ab55` (Plan 03) — all in TDD-RED-then-GREEN order | PASS |
| Branch invariant | `git branch --show-current` | `gsd/v1.9-season-phases-groups` (matches phase branch) | PASS |

### Anti-Patterns Found

None. No TODO/FIXME, no empty implementations, no hardcoded stubs in modified files. Comments present where the WHY is non-obvious (e.g. `gap-66-03 — layout gate` block comment at lines 266-268, 325-326). Javadoc on resolver helper at lines 413-434 documents the revised D-06 contract and links back to `66-CONTEXT.md`.

### Convention Compliance

| Check | Status | Evidence |
|-------|--------|----------|
| TDD order (test commit BEFORE fix/feat) — three plans | PASS | Plan 01: `dd123e0 test → d204624 feat → 4d26b75 fix`. Plan 02: `a7a7994 test → 9665d42 fix`. Plan 03: `48dcfeb test → 3b66e77 fix`. |
| BDD test naming `givenContext_whenAction_thenExpectedResult` | PASS | All 6 new tests (#19-#24) follow exactly. Sample: `givenTeamsWithSameShortNameAndSubHasPhaseTeam_whenPreview_thenResolvesSubTeam` (line 734). |
| Conventional Commits | PASS | `test:`, `feat:`, `fix:`, `docs:`, `chore:` prefixes correct across all phase 66 commits. |
| Branch invariant `gsd/v1.9-season-phases-groups` | PASS | `git branch --show-current` returns `gsd/v1.9-season-phases-groups`. Working tree clean (only `.claude/worktrees/` untracked). |
| Out-of-scope files untouched | PASS | Only the four expected source files modified across plans 01-03 (TeamRepository, DriverSheetImportService, DriverSheetImportServiceTest, driver-import-preview.html) plus planning docs. `TeamControllerTest.java` and `GroupsSeasonE2ETest.java` not touched (verified via `git log --name-only` — both files absent from phase 66 commits). |
| `findByShortName` preserved in TeamRepository (D-04) | PASS | Line 17 retains `Optional<Team> findByShortName(String shortName);` with explanatory Javadoc. |
| `findByShortNameIgnoreCase` preserved (D-05) | PASS | Line 19 retains the method declaration. |
| UI texts in English (project rule) | PASS | Template renders "No group", "Group", em-dash. No German strings introduced. |
| No inline styles on buttons (project rule) | PASS | Template change adds CSS class `badge badge-warning` (existing); no `style=` attribute introduced. |

### Threat Model Outcomes

| Threat | Disposition | Evidence |
|--------|-------------|----------|
| T-66-01 Information Disclosure (JPA stack trace via 500) | MITIGATED | Resolver returns `Optional` cleanly; `IncorrectResultSizeDataAccessException` path unreachable for the documented data shape. Confirmed in UAT Test 1 (PASS). |
| T-66-02 Denial of Service via crash | MITIGATED | Tests #19-#24 cover all collision paths (parent+sub, no-PhaseTeam fallback, no-REGULAR-phase legacy, two-parent edge, LEAGUE layout, GROUPS layout) — none throw. |
| T-66-02-04 Repudiation (admin loses operational signal) | MITIGATED | Genuine missing-team warnings still fire when `regularPhase.layout == GROUPS` (Tests #16, #17, #20). |
| T-66-03-03 XSS via template change | ACCEPTED | New `&mdash;` is a static character entity; no user-input interpolation in the new branch. |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| GAP-66-01 | 66-02-PLAN.md | Season-aware resolver: prefer PhaseTeam(REGULAR) candidate, fall back to parent precedence | SATISFIED | Truth #2, #3, #5 verified. Resolver at lines 435-458 implements revised D-06. Tests #19, #21, #22 cover the contract. |
| GAP-66-02 | 66-03-PLAN.md | Layout-gated TEAM_NOT_IN_REGULAR_PHASE warnings + per-row template gate | SATISFIED | Truth #6, #7, #8, #9 verified. Layout gates at lines 269 + 328; template per-row gate via `tab.usesGroups()` across all 5 buckets; page-wide column header preserved. Tests #16, #17, #20, #23, #24 cover the contract. |

### UAT Resolution

The May 7 verification (initial PASS) preceded UAT testing. UAT (`66-UAT.md`) revealed 2 gaps:

- **UAT Test 2 (parent precedence with warning)** → diagnosed as GAP-66-01 → closed by Plan 66-02 (season-aware resolver). Sub-team-with-PhaseTeam now wins; the spurious `TEAM_NOT_IN_REGULAR_PHASE` warning no longer fires for the dominant data shape (MRL/P1R/ZFS).
- **UAT Test 1 follow-up (group warnings on LEAGUE seasons)** → diagnosed as GAP-66-02 → closed by Plan 66-03 (layout gate). Season 2024 (LEAGUE) preview will no longer surface "⚠ No group" badges.
- **UAT Test 3 (execute after preview)** was BLOCKED on Test 2 + the layout gate. Both blockers closed; user is unblocked to retry the execute path.

The phase outcome — "make CSV driver imports work without spurious TEAM_NOT_IN_REGULAR_PHASE warnings on shortName collisions and on non-GROUPS seasons, without crashing on duplicate shortNames" — is delivered:

1. **No crashes:** Phase 66-01 baseline preserved (truth #1).
2. **No spurious warnings on shortName collisions:** Resolver routes to the sub-team with PhaseTeam in REGULAR phase, so `phaseTeamRepository.findByPhaseIdAndTeamId(...)` succeeds and no warning is emitted (truth #2 + #6).
3. **No spurious warnings on non-GROUPS seasons:** Layout gate suppresses the warning emission entirely for LEAGUE phases (truth #6 + #8).

## Verdict: PASS

All 9 observable truths verified by codebase evidence (file:line citations above). All 5 required artifacts present and substantive. All 7 key links wired. All 5 data-flow paths flowing. All 10 behavioral spot-checks PASS. No anti-patterns. Convention compliance complete. Both UAT-discovered gaps closed; phase goal achieved as specified in ROADMAP.md and `66-UAT.md`.

The May 7 PASS report was correct for Plan 66-01 in isolation but was issued before UAT. This re-verification confirms the post-gap-closure state delivers the full phase outcome.

---

_Verified: 2026-05-08T11:30:00Z_
_Verifier: Claude (gsd-verifier)_

## PHASE COMPLETE

## Phase-70 Re-Open Addendum (2026-05-09)

Phase 70 (Driver Import — Parent-Only Team Resolution) inverts the season-aware step that
Phase 66's gap-closure plans 66-02 + 66-03 introduced into `DriverSheetImportService.resolveTeamByShortName`.
Live UAT against local MariaDB (Saison 2023, parent MRL + sub-teams MRL 1 / MRL 2 in different
Groups, 2026-05-09) revealed that Phase 66 D-04 (`sub-team-with-PhaseTeam wins over parent`)
violated the user's domain model. The user clarified 2026-05-09 that `SeasonDriver.team` is
always the parent; sub-team variation happens per-match via `RaceLineup.team`, not per-phase.

### Truths superseded

- **Truth #2** (Multi-match in a season WITH REGULAR phase prefers the candidate that has a
  PhaseTeam in that REGULAR phase) — **SUPERSEDED**. Phase 70 D-05: parent always wins on
  multi-match, regardless of REGULAR-phase membership.
- **Truth #6** (TEAM_NOT_IN_REGULAR_PHASE warning is layout-gated to GROUPS) — **SUPERSEDED**.
  Phase 70 D-09: the warning category is removed entirely.
- **Truth #7** (`TabPreview.usesGroups` flag computed once per tab from canonical signal) —
  **SUPERSEDED**. Phase 70 D-09: the field is removed from the `TabPreview` record.
- **Truth #8** (Per-row Group cell in template gated by `tab.usesGroups()` across all 5 buckets)
  — **SUPERSEDED**. Phase 70 D-09: the per-row Group cell is removed from all 5 buckets in
  `driver-import-preview.html`.
- **Truth #9** (Page-wide `showGroupColumn` preserved unchanged) — **SUPERSEDED**. Phase 70 D-09:
  the `showGroupColumn` model attribute is removed from `DriverSheetImportController` and the
  Group column header is removed from the template.

### Truths preserved

- **Truth #1** (no crash on shortName collision) — **STILL HOLDS** post-Phase-70. The list-returning
  `findAllByShortName` + `Optional<Team>` resolver-return shape are unchanged. Tests #21 and #22 in
  `DriverSheetImportServiceTest` continue to fence the no-exception contract.
- **Truth #3** (Multi-match WITHOUT REGULAR phase falls back to parent precedence) — **STILL HOLDS**
  and is now the universal rule (no longer a fallback). Test #21 unchanged.
- **Truth #4** (Multi-parent edge case logs WARN and picks first deterministically) — **STILL HOLDS**.
  Phase 70 D-05 keeps this behavior.
- **Truth #5** (5 service call sites pass second arg) — **UPDATED**. Post-Phase-70 the 5 call sites
  pass exactly 1 arg (Phase 70 D-06).

### References

- Phase 70 CONTEXT: `.planning/phases/70-driver-import-parent-only-team-resolution/70-CONTEXT.md`
- Phase 70 plans: `70-01-PLAN.md` (resolver inversion), `70-02-PLAN.md` (UX decommission),
  `70-03-PLAN.md` (test reconciliation + this addendum + final verify)
