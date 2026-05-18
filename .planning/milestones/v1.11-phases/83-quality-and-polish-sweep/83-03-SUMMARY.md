---
plan: 83-03
requirements: [QUAL-03]
status: complete
date: 2026-05-17
---

# Plan 83-03 — QUAL-03 Per-Group Matchday-Generation UI

## Outcome

The Season-detail "Generate Matchdays" form now exposes a per-group `<select>` for GROUPS-layout phases. `SeasonController:251` no longer hardcodes `groupId=null` — it forwards `form.getGroupId()` to `MatchdayGeneratorService.generate(...)`. For SINGLE/LEAGUE-layout phases the conditional template fragment is hidden and the form posts `groupId=null` (existing behaviour preserved).

## Files Modified / Created

| File | Change |
|------|--------|
| `src/main/java/org/ctc/admin/dto/MatchdayGeneratorForm.java` | + `private UUID groupId` field (with Lombok `@Getter/@Setter`); no `@NotNull` validation (form must accept `null` for SINGLE/LEAGUE-layout submissions; service-layer enforces non-null for GROUPS per `MatchdayGeneratorService.java:49-51`) |
| `src/main/java/org/ctc/admin/controller/SeasonController.java` | `generateForm` GET exposes `phase` model attribute so template can read `${phase.layout}` and iterate `${phase.groups}`. `generate` POST line ~251 changed from `null` to `form.getGroupId()` |
| `src/main/resources/templates/admin/matchday-generator.html` | + conditional `<div class="form-group" th:if="${phase.layout.name() == 'GROUPS'}">` rendering a `<select th:field="*{groupId}" required>` with `<option>` per `${phase.groups}` |
| `src/test/java/org/ctc/e2e/MatchdayGeneratorGroupsE2ETest.java` | NEW — `@Tag("e2e")` test seeding Test-prefix Season 2098 with 2 groups + 4 teams, asserting the per-group select renders, selecting Group A, submitting, and verifying matchdays are bound to Group A (none to Group B) |

## Tests

| Test | Result | Time |
|------|--------|------|
| `MatchdayGeneratorGroupsE2ETest.givenGroupsLayoutSeason_whenGenerateMatchdaysForGroupA_thenMatchdaysAreBoundToGroupA` | PASS | 0.796 s (test logic; ~30 s incl. Spring/Playwright setup) |

Full `./mvnw verify -Pe2e` lifecycle ran cleanly through Surefire and Failsafe. Screenshot `.screenshots/qual-03-matchday-generator-groups.png` captured (126 KB, renders season-detail page with MD 1 matchday link in the Matchdays section + Group A roster).

## Discovery 1: Flash-Attribute Loss on `SeasonController#detail`

`SeasonController#detail` at `SeasonController.java:35-60` performs an auto-redirect from `/admin/seasons/{id}` to `/admin/seasons/{id}/phases/{phaseId}` when a REGULAR phase exists. Spring flash attributes survive ONE redirect, but the matchday-generator success path is a double redirect:

1. POST `/admin/seasons/{id}/generate` → flash `successMessage` + 302 to `/admin/seasons/{id}`
2. GET `/admin/seasons/{id}` → reads season → finds REGULAR phase → 302 to `/admin/seasons/{id}/phases/{phaseId}`
3. GET `/admin/seasons/{id}/phases/{phaseId}` → flash already consumed in step 2; success-flash is GONE

The MD 1 matchday IS created correctly; the success-flash is silently lost. The E2E test asserts on the observable outcome (matchday-link visible on the rendered phase page), not on the transient flash. Backlog item for a future "flash-attribute hardening" phase — fix pattern: `SeasonController#detail` should propagate inbound flash attributes to the target URL via `RedirectAttributes#getFlashAttributes()` merge, or eliminate the double redirect entirely.

## Discovery 2: Test-Pollution Regression in `BackupImportE2ETest` (FIXED in same phase)

The initial commit of `MatchdayGeneratorGroupsE2ETest` (commit `3f91ba6b`) had only `teardownPage()` in `@AfterEach` — no DB cleanup. When the test ran in the same Spring context as `BackupImportE2ETest` (shared H2 in-memory DB; Failsafe `forkCount=2, reuseForks=true`), the Test-Season 2098 + 4 teams + 2 groups + 1 phase + 1 matchday remained in the DB and broke `BackupImportE2ETest`'s wipe+restore round-trip — the success flash never rendered.

**Initial misclassification corrected (per `feedback_no_flaky_dismissal`):** the failure was first mis-labeled as "pre-existing flaky" + "out of scope" — wrong on both counts. Phase 82 verification (`82-VERIFICATION.md`) recorded all 1655 tests green, and the failure was reliably reproducible under the new commit, so it was a regression I introduced.

**Fix (commit `20233926`):** explicit `@AfterEach` cleanup deleting matchdays + phase-teams + season-teams + groups + phases + season + Test-prefix teams per CLAUDE.md "Isolate Test Data Completely". Full `./mvnw verify -Pe2e` then reports 38/38 E2E green, JaCoCo + SpotBugs unchanged.

## Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| `MatchdayGeneratorForm` gains `groupId` field without `@NotNull` (D-17) | ✅ |
| Form renders group `<select>` conditionally for GROUPS-layout (D-16) | ✅ |
| Form is hidden for SINGLE-layout, posts `groupId=null` | ✅ (existing call paths verified by SeasonController*Test green) |
| `SeasonController.java:251` no longer hardcodes `null` (D-18) | ✅ |
| `MatchdayGeneratorService.generate(...)` accepts the wired `groupId` (D-18) | ✅ (no service-level change needed) |
| `generateForm` GET handler exposes `phase` model attribute (D-19) | ✅ |
| Playwright E2E confirms group select renders + form submit works + matchdays bound to chosen group only (D-20) | ✅ |
| No new Flyway migration | ✅ |
| No `pom.xml` change | ✅ |
| Commits on milestone branch `gsd/v1.11-tooling-and-cleanup` | ✅ |

## Backlog Notes

- **Flash-attribute hardening** — `SeasonController#detail` auto-redirect silently drops flash attributes. Affects QUAL-03 + likely `BackupImportE2ETest`. Candidate for a v1.12 polish phase. Pattern fix: detail handler should forward flash to the target phase URL (manual model attribute pass-through) or `Model#asMap`-merge before the second redirect.
