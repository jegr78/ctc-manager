---
status: deferred
trigger: "Group assignment warnings fire on import preview for seasons whose REGULAR phase has no GROUPS layout — pure noise."
created: 2026-05-08T05:30:00Z
updated: 2026-05-18T16:00:00Z
deferred_at: 2026-05-18
deferred_to: v1.12
deferred_reason: "Diagnosed with root cause + files_to_change + fix algorithm fully documented; out of scope for v1.11 tooling/tech-debt sweep. Hand-off to v1.12 driver-import gap-closure phase. Bug surface is Driver-Import preview UI noise — not a data-correctness issue."
---

## Current Focus

hypothesis: confirmed — see Resolution
test: completed
expecting: -
next_action: hand off to gap-closure planner

## Symptoms

expected: When a season's REGULAR phase has no GROUPS layout, driver-import preview must NOT emit per-team `TEAM_NOT_IN_REGULAR_PHASE` / "No group" warnings — there is nothing to be missing.
actual: Season 2024 (no groups) — every row in the import preview gets a "⚠ No group" badge in the Group column AND the tab-level "Group assignment warnings" panel lists every team. Pure UI noise.
errors: none (warning bubbles, not exceptions)
reproduction: |
  1. Start app: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (port 9091)
  2. Open `http://localhost:9091/admin/drivers/import`
  3. Select a sheet whose tab matches season `2024 | #2 | S2-2024`
  4. Inspect bucket "New Drivers (148)": every row → GROUP = "⚠ No group"
  5. Tab-level "Group assignment warnings" panel also lists each team
started: Pre-existed; surfaced during /gsd-verify-work for Phase 66 on 2026-05-08.

## Evidence

- src/main/java/org/ctc/dataimport/DriverSheetImportService.java lines 248-256, 311-325: `regularPhase` resolved unconditionally; branch at 313-325 fires whenever `regularPhase != null` AND team has no PhaseTeam row. **No layout check, no SeasonPhaseGroup-presence check.**
- src/main/java/org/ctc/admin/controller/DriverSheetImportController.java lines 55-64: `showGroupColumn` is page-wide (anyMatch). When at least ONE tab is GROUPS, every tab inherits `showGroupColumn=true`, so non-GROUPS tab rows still render the "⚠ No group" badge.
- src/main/java/org/ctc/domain/model/SeasonPhase.java: `layout` is `@Enumerated PhaseLayout {LEAGUE, GROUPS, BRACKET}`, `nullable=false`. Single in-memory field read — no extra DB roundtrip.
- src/main/resources/templates/admin/driver-import-preview.html: per-row "No group" badge is gated only by page-scoped `${showGroupColumn}`. No per-tab "uses groups" flag carried in `TabPreview`.
- src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java tests #16 and #17 use `PhaseTestFixtures.regularPhase(...)` which constructs `PhaseLayout.LEAGUE` (PhaseTestFixtures.java:49) and assert the warning IS emitted. **These tests codify the bug, not the contract.** Test #18 (no REGULAR phase) is correct.

## Resolution

root_cause: |
  `DriverSheetImportService.buildTabPreview` (lines 311-325) fires `TEAM_NOT_IN_REGULAR_PHASE` and leaves `resolvedGroupName == null` whenever a team has no `PhaseTeam` row in the REGULAR phase — without checking the phase's layout. For LEAGUE-layout phases every team trivially has `PhaseTeam.group == null`, so the warning fires for every team. The cheapest authoritative check (`regularPhase.getLayout() == PhaseLayout.GROUPS`) is already in scope but unused.

  Two surfaces must suppress simultaneously:
  (1) Service-side: warning emission + skip the PhaseTeam lookup when layout != GROUPS.
  (2) Template-side: per-row "⚠ No group" badge persists for non-GROUPS tabs in mixed multi-tab previews because `showGroupColumn` is page-wide. A per-tab `usesGroups` flag in `TabPreview` is needed.

  Tests #16 and #17 in `DriverSheetImportServiceTest` currently assert the bug — they must be inverted to use a GROUPS phase, and a new test must assert no warning for LEAGUE.

fix: ""  # owned by gap-closure planner

files_to_change:
  - src/main/java/org/ctc/dataimport/DriverSheetImportService.java (gate at line 313 + add `usesGroups` to TabPreview record)
  - src/main/resources/templates/admin/driver-import-preview.html (add per-tab gate `tab.usesGroups()` on row Group cells; "—" fallback)
  - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java (invert tests #16, #17 to use `PhaseTestFixtures.groupsRegularPhase(...)`; add new LEAGUE-no-warning test)

dependency_notes: |
  Orthogonal to the major Phase 66 gap (parent-precedence revision). Once the resolver picks the sub-team that actually has a `PhaseTeam(REGULAR)`, the `TEAM_NOT_IN_REGULAR_PHASE` warning becomes legitimate again for genuinely missing teams in GROUPS seasons. Coherent gap-closure plan should sequence them so the test rewrite handles both contracts without churn — but neither blocks the other.
