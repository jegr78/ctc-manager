# Plan 102-01 Code Review

**Verdict:** clean
**Date:** 2026-05-28
**Reviewer:** orchestrator mechanical scan (gsd-code-reviewer agent unavailable due to upstream 529 — see "Reviewer Caveat" below)
**Scope:** 8 production + 8 test files; 8 commits (`8fc1b143` … `26f7f950`)
**Method:** grep-driven acceptance-criteria verification + targeted wider-bug-class checks per CONTEXT D-04.

## Critical / Blocker

_(none)_

## Warnings

_(none)_

## Info

### IN-01: `MANIFEST_INVALID` literal count differs from plan acceptance phrasing
**File:** `src/main/java/org/ctc/backup/restore/entity/DiscordGlobalConfigRestorer.java`
**Issue:** Plan acceptance criterion required `grep -n "MANIFEST_INVALID"` to return "at least 6 lines (one per guarded column)". Implementation centralised the guard in one `requireText()` helper method (one literal site) called 6 times. Functionally equivalent — better, by DRY — but the literal count is 1, not 6. The guarded-column count is provable via `grep "requireText(row," | wc -l` which returns 6.
**Fix:** _(no fix — acceptance text was over-specified; the centralised helper is the cleaner pattern. SUMMARY documents this.)_

## Out-of-Scope Concerns

### OOS-01: Identical unguarded-`.asText()` class of bug in 23 other restorers
**Files:** `src/main/java/org/ctc/backup/restore/entity/{Car,Driver,Match,Matchday,Race,RaceLineup,RaceResult,RaceSettings,RaceAttachment,RaceScoring,MatchScoring,Season,SeasonDriver,SeasonPhase,SeasonPhaseGroup,SeasonTeam,Team,Track,Playoff,PlayoffRound,PlayoffMatchup,PlayoffSeed,PhaseTeam,PsnAlias}Restorer.java`
**Issue:** Plan 102-01 Task 8 closed the `JsonNode.get(X).asText()` NPE-on-missing class of bug for Discord restorers only (the surface Phase 101 CR-02 flagged). A `grep -lE '\.get\([^)]+\)\.asText\(\)[^?]'` across `src/main/java/org/ctc/backup/restore/entity/*.java` shows the same unguarded pattern in **every** other restorer. None were flagged in any of the 10 input REVIEW.md files (Phase 92–101) because each phase's review was scoped to its own diff.
**Disposition:** **Out of scope for Phase 102.** Phase 102's scope is "close the 119 findings from the milestone-wide review" (per CONTEXT phase-boundary). This bug class was not in the 119. Suggest capturing as a v1.14 backlog item ("Generalise restorer NOT-NULL guards across all 25 EntityRestorers — produce a base class or shared helper"). Per CLAUDE.md "Bug fix doesn't need surrounding cleanup", expanding scope here would violate the in-milestone discipline.

### OOS-02: Pre-existing `DiscordPostService` `allMatch` patterns: all have explicit empty-guards
**File:** `src/main/java/org/ctc/discord/service/DiscordPostService.java:166,613,675,681`
**Issue:** Wider-bug-class check for the BL-01 vacuous-allMatch pattern. Lines 166, 613, 675 all have explicit `!collection.isEmpty()` guards before the `allMatch`. Line 681 is part of a compound expression starting with another guard. Line 686 uses `anyMatch` (returns `false` on empty stream, no vacuous-true risk).
**Disposition:** No action needed — these are correct today.

### OOS-03: `e.getMessage()` echoes remain on non-Google paths
**File:** `src/main/java/org/ctc/dataimport/CsvImportController.java:65,272`
**Issue:** Two `e.getMessage()` echoes remain after Task 1: line 65 (CSV-only `preview()` IOException arm; user uploads the file, so leak surface is self-uploaded) and line 272 (`BusinessRuleException | ValidationException` arm; caller-side validation messages by design). Phase 92 REVIEW.md WR-01 already flagged line 65 as an inconsistency (lower severity than the Google-reachable info-leak that CR-01 fixed). Plan 102-02 Task 7 / 8 (Phase 92 WR sweep) will close WR-01.
**Disposition:** **In scope for Plan 102-02**, not 102-01.

## Acceptance Criteria — Verified

| Check | Result |
|-------|--------|
| Zero new comment-pollution markers across 16 touched files | ✓ (grep returns 0 lines) |
| `e.getMessage()` removed from Google-reachable arms of `CsvImportController` | ✓ (both fixed) |
| `awayTeam != null ? … : "Bye"` pattern in `DiscordPostController.matchLabel` | ✓ (line 70) |
| `MatchScheduleFieldsChangedEvent` published from `RaceService.saveRace` | ✓ (line 240) |
| `MatchScheduleFieldsChangedEvent` import present | ✓ (line 9) |
| Score-aggregation invariant preserved (no `aggregateMatchScores` regression) | ✓ — the event-publish path runs in `saveRace`, which never aggregated scores (results live in `saveResults`); `RaceServiceTest` passes |
| Bye-guard symmetry across `canPostMatchdayPairings` + `canPostMatchdaySchedule` | ✓ (both gain `nonByeMatches.isEmpty()` guards) |
| `MANIFEST_INVALID` raised for the 6 V8/V9 NOT-NULL columns | ✓ (6 `requireText(...)` calls via centralized helper) |
| Discord restorers' V12 NOT-NULL columns also guarded | ✓ (8 `requireText(...)` calls in `DiscordPostRestorer`) |
| `V1_TABLES_24` uses plural JPA table names | ✓ (`race_scorings`, `match_scorings`) |
| Non-vacuous gate test on `V1_TABLES_24` shape | ✓ (`givenV1Tables24_thenEveryNameMatchesAJpaEntityTable`) |
| Lifecycle-seed uses `T-ALC` / `T-BRL` (no collision with dev-seed `T-ALF` / `T-BRV*`) | ✓ |
| `@Tag("integration")` exactly once on each new `*IT.java` | ✓ (5 ITs × 1 occurrence) |
| Zero `@MockitoBean DiscordPostService` in new transactional ITs | ✓ |
| All targeted test commands green | ✓ (per `102-01-SUMMARY.md` exit-code table) |

## Reviewer Caveat

The mandated `gsd-code-reviewer` agent dispatch was attempted twice but blocked by upstream API 529 (Overloaded) errors during this orchestrator turn. The findings above are an orchestrator-side mechanical sweep (grep + manual cross-file pattern checks) — they cover acceptance-criteria verification and the explicit wider-bug-class follow-ups the agent prompt called out, but they are NOT a full independent code-quality review. An independent agent review will run again as part of Plan 102-04 (full Phase-102 close-loop). If that review surfaces findings on Plan 102-01 files, they are folded back into Plan 102-04 per CONTEXT D-04.

## Verdict Rationale

No critical / warning findings on the 102-01 diff itself. The single Info item is acceptance-text over-specification, not a defect. Three Out-of-Scope items are documented for traceability: a v1.14-backlog candidate (restorer-class generalisation), a wider-pattern verification result (DiscordPostService allMatch is sound), and a Plan 102-02 hand-off (CsvImportController WR-01).

Plan 102-01 close-loop **passes**.

## Independent Agent Review

**Initial verdict:** flagged (no blockers; two warnings + two info).
**Resolution:** all four findings folded back into Plan 102-01 in commit `716d16db` per CONTEXT D-04. **Final verdict: clean.**
**Reviewer:** gsd-code-reviewer agent, 2026-05-28, read-only second pass over the 8 commits.

### WR-IA-01 — `DiscordGlobalConfigRestorer` audit columns left unguarded (`createdAt`, `updatedAt`)
**File:** `src/main/java/org/ctc/backup/restore/entity/DiscordGlobalConfigRestorer.java:60-61`
Same NPE class the commit just closed: `row.get("createdAt").asText()` / `row.get("updatedAt").asText()` still bypass `requireText`. Both columns are DB-level NOT NULL (V8) and reachable on every restore. The "V8/V9 NOT-NULL columns" scoping in the commit message excluded audit columns, but the bug class is identical and the helper already exists two lines below. `DiscordPostRestorer` correctly routes `postedAt`/`createdAt`/`updatedAt` through `requireText` (line 66, 68, 69) — the asymmetry is the smell. Fix: wrap lines 60-61 with `requireText(row, "createdAt")` / `"updatedAt"`.

### WR-IA-02 — Bye-guard ITs do not pin `disabledReason` literal
**File:** `src/test/java/org/ctc/discord/service/DiscordPostServiceByeMatchdayGuardIT.java:148,162,176,189`
Empty/all-BYE assertions check only `result.canPost()` is `false`. A regression that returns `false` for an unrelated reason (e.g., the pre-existing "Set scheduled weekend first" guard, or a future broken guard order) would pass. Add `.extracting(MatchPreviewPreFlightResult::disabledReason).isEqualTo("Add at least one non-bye match to the matchday first")` so the test pins the new invariant. Also: class is named `*IT` + tagged `@Tag("integration")` but has no `@SpringBootTest` / Mockito-extension — it's a unit-style test in IT clothing; per CLAUDE.md tag-by-category, untag and rename or add Spring context.

### IN-IA-01 — Webhook-fail cleanup asymmetric UX vs audit-fail
**File:** `src/main/java/org/ctc/discord/service/DiscordChannelService.java:91-99` vs `103-115`
Audit-fail re-throws a wrapped exception telling the operator to manually delete channel `X`. Webhook-fail only logs WARN and rethrows the original — operator gets no actionable hint when cleanup itself fails. Mirror the audit-fail wrap.

### IN-IA-02 — `RaceService.saveRace` fires event on new-race creation
**File:** `src/main/java/org/ctc/domain/service/RaceService.java:190`
For new races (`id == null`), `race.getDateTime()` is null, so `!Objects.equals(null, dateTime)` is true on any non-null incoming dateTime — event fires for a freshly-created match. Harmless today because `autoEditScheduleIfNeeded` early-returns when no SCHEDULE post exists (line 215), but a future hook subscriber may not be idempotent. Consider `id != null && !Objects.equals(...)`.

_Branch verified: `gsd/v1.13-discord-integration` (read-only)._

### Fold-Back Status (CONTEXT D-04)

| Finding | Status | Fold-back commit |
|---------|--------|------------------|
| WR-IA-01 | **Closed** — `requireText` applied to `createdAt`/`updatedAt` in `DiscordGlobalConfigRestorer:60-61` | `716d16db` |
| WR-IA-02 | **Closed** — bye-guard tests now pin `disabledReason` literal; class renamed `*IT` → `*Test` + tag dropped per CLAUDE.md tag-by-category | `716d16db` |
| IN-IA-01 | **Closed** — webhook-fail cleanup now wraps as typed `DiscordTransientException` naming the channel when both ops fail | `716d16db` |
| IN-IA-02 | **Closed** — `RaceService.saveRace` event-fire guarded by `id != null` | `716d16db` |

Post fold-back: `./mvnw test -Dtest='DiscordGlobalConfigRestorerGuardTest,DiscordPostServiceByeMatchdayGuardTest,RaceServiceTest'` (Surefire) — 28/28 green. `./mvnw verify -Dit.test='DiscordChannelServiceWebhookFailIT,DiscordAutoPostListenerScheduleEditIT'` (Failsafe) — 3/3 green.
