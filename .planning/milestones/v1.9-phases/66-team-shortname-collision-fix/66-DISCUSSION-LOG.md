# Phase 66: Team ShortName Collision Fix (Driver Import) - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-07
**Phase:** 66-team-shortname-collision-fix
**Mode:** `--auto --chain` (no interactive AskUserQuestion calls; recommended option auto-selected per area)
**Areas discussed:** Resolution-policy location, Repository surface, Resolver semantics, Migration of call sites, Test strategy, Plan structure

---

## Resolution-policy location

| Option | Description | Selected |
|--------|-------------|----------|
| Service-level private helper | Private `resolveTeamByShortName` in `DriverSheetImportService`, uses a new list-returning repo method | ✓ |
| Repository-level `@Query` with ORDER BY ... LIMIT 1 | Custom JPQL with `parentTeam IS NULL` ordering | |
| New shared `TeamLookupService` | Extract a domain-wide lookup service | |

**Auto-selected:** Service-level private helper.
**Rationale (recommended default):** Only `DriverSheetImportService` consumes shortName lookups in production. CLAUDE.md explicitly forbids speculative abstractions ("Don't add features, refactor, or introduce abstractions beyond what the task requires"). Resolution policy is import-domain knowledge, not a generic Team query.

---

## Repository surface

| Option | Description | Selected |
|--------|-------------|----------|
| Add `List<Team> findAllByShortName` (Spring Data derived) | Plain derived finder, works in H2 + MariaDB | ✓ |
| Add `Stream<Team> findAllByShortName` | Streaming variant — overkill for ≤2 rows | |
| Replace existing `findByShortName` outright | Forces unrelated test rewrites | |

**Auto-selected:** `List<Team> findAllByShortName(String)`.
**Rationale:** Spring Data derivation is the project's established pattern. Existing `findByShortName` stays for unique-shortName test fixtures (`TeamControllerTest`, `GroupsSeasonE2ETest`).

---

## Resolver semantics

| Option | Description | Selected |
|--------|-------------|----------|
| Single match → use it; multi → prefer parent (`parentTeam IS NULL`); empty → empty | Direct mapping of user's policy | ✓ |
| Throw on multi-parent (data-integrity violation) | Stricter — but reintroduces a crash | |
| Always pick first row regardless of parent status | Simpler — but doesn't honor parent precedence | |

**Auto-selected:** Three-branch policy with `log.warn` on the multi-parent edge case (D-06, D-07).
**Rationale:** Matches user's stated policy verbatim; doesn't fail the import on data-integrity oddities (the original bug was an unintended crash).

---

## Migration of the 5 call sites

| Option | Description | Selected |
|--------|-------------|----------|
| Single bundled fix — all 5 sites in one commit | Atomic, easy to review | ✓ |
| Per-site commits (5 commits) | Granular — but produces transient half-migrated states | |
| Migrate preview only (the reported crash site) | Leaves 4 sites still vulnerable — not a fix | |

**Auto-selected:** Single bundled fix.
**Rationale:** All 5 sites share the identical bug shape; splitting fragments review and creates broken intermediate commits.

---

## Test strategy

| Option | Description | Selected |
|--------|-------------|----------|
| TDD: failing unit test first → fix → migrate existing 17 stubs | Mockito-based, consistent with existing test class | ✓ |
| Add `@SpringBootTest` integration test against H2 | Heavier, redundant with existing E2E coverage | |
| Skip new test, only migrate stubs | Loses regression fence | |

**Auto-selected:** TDD unit test path.
**Rationale:** CLAUDE.md mandates TDD; existing 17 stubs already provide single/empty match coverage; one new test pins the parent-precedence regression.

---

## Plan structure

| Option | Description | Selected |
|--------|-------------|----------|
| Single plan `66-01-PLAN.md` covering all steps | Atomic — fix is small | ✓ |
| Two plans (repo+service split from test migration) | Adds plan-orchestration overhead for trivial work | |

**Auto-selected:** Single plan.
**Rationale:** Hot-fix scope; Phase 65 used 3 plans because of distinct caller groups, Phase 66 has only one.

---

## Claude's Discretion

- **D-17:** Whether to add a one-line Javadoc on the helper — left to planner.
- **D-18:** Where in `DriverSheetImportService` to place the helper (top vs. near `cellToString`) — left to planner.

## Deferred Ideas

- `TeamRepository.findByShortNameIgnoreCase` cleanup (production-dead) — separate quality phase.
- UNIQUE constraint on `(parent_team_id, short_name)` — separate data-integrity phase.
- TabWarning UI for multi-match resolution — future polish.
- Cross-tab driver collision detection (separate UAT finding) — separate phase.
- All other UAT bugs from v1.9 testing pass — separate phases per finding.
