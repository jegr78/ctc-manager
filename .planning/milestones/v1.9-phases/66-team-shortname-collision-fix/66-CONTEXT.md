# Phase 66: Team ShortName Collision Fix (Driver Import) - Context

**Gathered:** 2026-05-07
**Status:** Revised by gap-closure plan 02 — see Revision History at the bottom of `<decisions>`. (Original: Ready for planning.)

<domain>
## Phase Boundary

Fix the runtime crash in `DriverSheetImportService` (preview + execute) when two teams share the same `shortName` — most commonly a parent team and one of its sub-teams (e.g., parent `ZFS` + sub `ZFS`).

**Root cause (locked):**
- `TeamRepository.findByShortName(String)` returns `Optional<Team>` (single-result semantics).
- `teams.short_name` has **no UNIQUE constraint** in `V1__initial_schema.sql`.
- The Team model deliberately allows parent + sub-team to share a shortName (`parentTeam` is a nullable self-FK).
- When 2 rows match → Hibernate throws `NonUniqueResultException` → wrapped as `IncorrectResultSizeDataAccessException` → 500 error in `GlobalExceptionHandler`. Five call sites in `DriverSheetImportService` (lines 135, 146, 166, 195, 296) are all vulnerable to the same crash.

**Resolution policy (locked by the user):**
- **Multi-match → prefer the parent team** (`parentTeam IS NULL`).
- **Single match** → use it (whether parent, standalone, or sub-team).
- **No match** → existing `UNKNOWN_TEAM_CODE` error flow stays intact.

**In scope:**
- Add a list-returning repository method.
- Replace the 5 import-service call sites with a parent-preferring resolver.
- Add a unit test reproducing the parent + sub collision.
- Migrate the existing 17 mock stubs (`findByShortName` → `findAllByShortName`).

**Out of scope (deferred — see <deferred>):**
- `TeamRepository.findByShortNameIgnoreCase` (production-dead; separate cleanup).
- Adding a UNIQUE constraint on `(parent_team_id, short_name)` (would lock down the data model — bigger decision, not a hotfix).
- Other UAT bugs from the v1.9 testing pass (will get their own phases).
- Migrating `TeamControllerTest` / `GroupsSeasonE2ETest` callers (they use unique test-prefix shortNames; no collision risk).

</domain>

<decisions>
## Implementation Decisions

### Resolution-policy location

- **D-01:** Resolver lives **at service level** as a private helper `resolveTeamByShortName(String) -> Optional<Team>` inside `DriverSheetImportService`. Reasons:
  - Only `DriverSheetImportService` uses shortName lookups in production (the 5 call sites). All other Team lookups go via `findById`.
  - CLAUDE.md (Architectural Principles) — *"Don't add features, refactor, or introduce abstractions beyond what the task requires"*. No shared `TeamLookupService` is justified for one consumer.
  - Domain knowledge (parent precedence) is import-policy, not a generic Team query.
- **D-02:** Repository stays declarative — no `@Query` with `ORDER BY CASE WHEN parent_team_id IS NULL ... LIMIT 1` magic. Add a plain list finder; the service applies the policy.

### Repository surface

- **D-03:** Add `List<Team> findAllByShortName(String shortName)` to `TeamRepository`. Spring Data derives the JPQL automatically; works in H2 (test) and MariaDB (prod).
- **D-04:** **Keep** the existing `Optional<Team> findByShortName(String)` — still used by `TeamControllerTest` and `GroupsSeasonE2ETest`, both with unique test-prefix shortNames. Removing it would force unrelated test rewrites that exceed this phase's scope. Document as "safe for unique short names" via a Javadoc note (one line, no heavy doc).
- **D-05:** **Do not touch** `findByShortNameIgnoreCase` — production-dead, but its removal is a separate cleanup phase. Out of scope.

### Resolver semantics

- **D-06:** Algorithm (revised in gap-closure plan 66-02 after UAT diagnosis — see `66-UAT.md` Gap 1 and `.planning/debug/shortname-resolver-picks-parent-without-phaseteam.md`):
  ```
  1. matches = teamRepository.findAllByShortName(shortName)
  2. if matches.isEmpty()      → return Optional.empty()
  3. if matches.size() == 1    → return Optional.of(matches.get(0))
  4. if regularPhase != null:
       for c in matches:
         if phaseTeamRepository.findByPhaseIdAndTeamId(regularPhase.id, c.id).isPresent():
           return Optional.of(c)               // sub-team-with-PhaseTeam wins
  5. parent = matches.stream().filter(t -> t.parentTeam == null).findFirst()
  6. if parent.isPresent()     → return parent  // legacy fallback (no regularPhase OR no candidate in REGULAR phase)
  7. else                      → log.warn(...); return Optional.of(matches.get(0))
  ```
  Original Phase 66 algorithm (parent-unconditionally) rested on a false assumption: it assumed parents are canonical race entities, but the user's data has parents as organisational buckets without `PhaseTeam(REGULAR)` rows. The season-aware step (4) preserves Phase 66's parent-precedence as a fallback for legacy seasons. [superseded by Phase 70 D-05/D-09 — see 70-CONTEXT.md]
- **D-07:** **Multi-parent edge case** (data inconsistency: 2+ teams with `parentTeam == null` and the same shortName) → log a warning at WARN level and pick first deterministically. Do NOT fail the import — that's the original bug. The warning is enough for ops visibility; this case is a data-integrity issue out of scope here. [partially preserved by Phase 70 D-05 — multi-parent edge case behavior unchanged; season-aware step removed]
- **D-08:** **No new TabWarning surfaced** for the multi-match case in this phase. The user wants the import to succeed silently when parent precedence resolves the collision (it's the intended data model). Adding a UI warning is a usability decision for a future phase. [superseded by Phase 70 D-09 — TabWarning category entirely removed]

### Migration of the 5 call sites

- **D-09:** All 5 call sites in `DriverSheetImportService.java` migrate to the helper:
  - Line 135 (execute → NEW_DRIVER rows)
  - Line 146 (execute → NEW_ASSIGNMENT rows)
  - Line 166 (execute → CONFLICT rows)
  - Line 195 (execute → FUZZY_SUGGESTION rows)
  - Line 296 (preview → buildTabPreview, the crash site reported)
  [updated by Phase 70 D-06 — second SeasonPhase argument dropped from all 5 call sites]
- **D-10:** Single bundled fix — one commit covers all 5 sites. Splitting per-site would create transient half-migrated states and dilutes review.

### Test strategy

- **D-11:** **One new failing test first (TDD)** in `DriverSheetImportServiceTest`:
  `givenTeamsWithSameShortNameParentAndSub_whenPreview_thenResolvesParentTeam` — stubs `teamRepository.findAllByShortName("ZFS")` to return `List.of(parentZfs, subZfs)`, asserts the row resolves to `parentZfs.getId()`. This is the regression fence.
- **D-12:** **Parametric coverage in same test class** for the resolver branches:
  - Single-match path (existing tests already cover this — only the stub method changes).
  - Empty-match path (existing UNKNOWN_TEAM_CODE test covers this — only the stub method changes).
  - Multi-parent edge case → one defensive test asserting "first parent wins, no exception thrown" (D-07).
- **D-13:** **Mechanical stub migration** — all 17 existing stubs `when(teamRepository.findByShortName("X")).thenReturn(Optional.of(team))` become `when(teamRepository.findAllByShortName("X")).thenReturn(List.of(team))`. No test logic changes.
- **D-14:** **No new integration test** — the JPQL is Spring Data derived (zero handwritten SQL); H2 + MariaDB both honor the derivation. Existing E2E and IT coverage transitively exercises the import path. Adding a `@SpringBootTest` for one query method exceeds the cost/benefit threshold.
- **D-15:** Test naming follows CLAUDE.md BDD convention — `givenContext_whenAction_thenExpectedResult()` with `// given` / `// when` / `// then` block comments.

### Plan structure

- **D-16:** **Single plan** (`66-01-PLAN.md`). The fix is small and atomic. Contents:
  1. Add failing test → confirm RED.
  2. Add `findAllByShortName` to `TeamRepository`.
  3. Add `resolveTeamByShortName` helper + replace 5 call sites.
  4. Migrate 17 existing stubs.
  5. `./mvnw verify` → confirm GREEN + JaCoCo ≥ 82%.
  6. Commit message: `fix(66): resolve team shortName collision in driver import`.

### Claude's Discretion

- **D-17:** **Whether to add a one-line Javadoc on the helper** is the planner's call (the helper's behavior is non-obvious — parent-precedence — so a short note is justified per CLAUDE.md "comment when WHY is non-obvious").
- **D-18:** **Where in `DriverSheetImportService` to place the helper** (top of class vs. near the call sites, near `cellToString`) is the planner's call. Suggested: alongside `cellToString` for symmetry (both private utility helpers).

### Revision History

- **D-06 revised (gap-closure plan 66-02, 2026-05-08)** — added season-aware step 4 (prefer candidate with PhaseTeam in target REGULAR phase) before falling back to parent precedence. Resolver signature changed from `resolveTeamByShortName(String)` to `resolveTeamByShortName(String, SeasonPhase)`. All 5 call sites migrated; 4 execute sites resolve `regularPhase` once per tab via `seasonPhaseService.findRegularPhase(...)` with `EntityNotFoundException → null` graceful fallback.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase artifacts

- `.planning/ROADMAP.md` § "Phase 66: Team ShortName Collision Fix (Driver Import)" — phase goal, depends on Phase 65.
- `.planning/STATE.md` § "Roadmap Evolution" — entry dated 2026-05-07 with bug origin context.

### Code surface (production)

- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` lines 135, 146, 166, 195, 296 — the 5 vulnerable call sites.
- `src/main/java/org/ctc/domain/repository/TeamRepository.java` — current Optional-returning finders; add `findAllByShortName` here.
- `src/main/java/org/ctc/domain/model/Team.java` lines 41–44, 48 (parentTeam / subTeams), 78–80 (`getParentOrSelf()`) — the parent/sub data model.
- `src/main/resources/db/migration/V1__initial_schema.sql` — confirms NO UNIQUE on `teams.short_name` (V1 cannot be modified per CLAUDE.md).

### Code surface (tests)

- `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` — 17 mock stubs to migrate, plus the new TDD test goes here.
- `src/test/java/org/ctc/admin/controller/TeamControllerTest.java` line 69 — keeps using `findByShortName` (unique shortName "MVR").
- `src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java` lines 106, 193–196, 285–288 — keeps using `findByShortName` (test-prefix shortNames).

### Repo conventions

- `CLAUDE.md` § Architectural Principles — *"Don't add features, refactor, or introduce abstractions beyond what the task requires"* (justifies private helper, no new service).
- `CLAUDE.md` § Constraints — minimum 82 % JaCoCo line coverage; no V1 migration changes; no breaking URL/endpoint changes.
- `CLAUDE.md` § Development Approach — TDD (Red → Green → Refactor); Given-When-Then test naming.
- `CLAUDE.md` § Git Workflow — branch from current state, conventional commits (`fix:` prefix).
- `.planning/codebase/CONVENTIONS.md` — DTO patterns, naming, layering.
- `.planning/codebase/TESTING.md` — Mockito + AssertJ unit-test idioms.

### Auto-mode chain anchors

- This phase was generated under `/gsd-discuss-phase 66 --auto --chain` — auto-advances to plan-phase, then execute-phase. No interactive gating until verify gate at the end.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable assets

- `Team.getParentOrSelf()` (Team.java:78) already exists. Not directly usable for resolution (it operates on a single team, not a candidate set), but confirms the parent/self semantic is canonical in the model.
- `Team.isSubTeam()` / `Team.hasSubTeams()` (Team.java:70, 74) — boolean predicates available if needed for future tests.
- `DriverSheetImportService.cellToString` (line 390) — reference pattern for a private utility helper inside this service. The new resolver mirrors its visibility/style.

### Established patterns

- Spring Data derived queries: existing `findByShortName` / `findByShortNameIgnoreCase` confirm the project relies on derivation, not handwritten JPQL. `findAllByShortName` slots in cleanly.
- Mockito stub style across `DriverSheetImportServiceTest`: `when(teamRepository.findByShortName("AHR")).thenReturn(Optional.of(teamAhr))` — uniform pattern across all 17 stubs, mechanical migration.
- `@Slf4j log.warn(...)` usage throughout the import service — matches the warning style for the multi-parent edge case (D-07).

### Integration points

- The `preview()` and `execute()` flows are bound by `@Transactional(readOnly = true)` and `@Transactional` respectively. The new helper is plain-Java synchronous and inherits the caller's transaction — no transaction-config changes needed.
- `DriverSheetImportController` (caller) is unchanged — the bug is below the controller layer.
- `GlobalExceptionHandler` — was the visible 500 endpoint; once the helper returns gracefully, this handler will no longer see this exception class for the import flow.

### Caller-context summary

| Call site (DriverSheetImportService.java) | Method | Today's behavior on collision |
|---|---|---|
| Line 135 | `execute → NEW_DRIVER rows` | Crashes after preview already read the same shortName |
| Line 146 | `execute → NEW_ASSIGNMENT rows` | Crashes for same reason |
| Line 166 | `execute → CONFLICT rows` | Crashes for same reason |
| Line 195 | `execute → FUZZY_SUGGESTION rows` | Crashes for same reason |
| Line 296 | `preview → buildTabPreview` | **Reported crash site** — fails first, blocks the rest |

</code_context>

<specifics>
## Specific Ideas

- The user's example data: parent `ZFS` + sub `ZFS` + sub `BFS` (where `BFS` exists only as a sub-team, no parent named `BFS`). The resolver MUST:
  - Resolve `ZFS` → parent `ZFS` (multi-match, parent precedence).
  - Resolve `BFS` → sub `BFS` (single-match, sub-team is the only candidate).
  Both cases are exercised by the new test family (D-11, D-12).
- The user invoked `--auto --chain` — they trust the recommended-default policy. Plan and execute will follow without further gating until the verify-gate.
- Auto-mode log: all gray areas selected; recommended option chosen for each (no AskUserQuestion calls).

</specifics>

<deferred>
## Deferred Ideas

- **`TeamRepository.findByShortNameIgnoreCase` cleanup** — production-dead, only mentioned in this code review. Belongs in a tooling/quality phase, not a hotfix.
- **UNIQUE constraint on `(parent_team_id, short_name)`** — would prevent the multi-parent edge case at the schema level, but it's a DB-level decision that affects existing data. Defer to a dedicated data-integrity phase.
- **TabWarning UI for multi-match resolution** — surface a soft "resolved to parent X" notice in the import preview UX. Future polish, not a hotfix.
- **Cross-tab driver collision detection** (separate UAT bug) — keep parking and triage in a separate Phase 67+ once the user reports it explicitly.
- **All other UAT bugs from v1.9 testing pass** — the user explicitly scoped this phase to the team-shortName crash. Other findings will get their own phases.

</deferred>

---

*Phase: 66-team-shortname-collision-fix*
*Context gathered: 2026-05-07*
