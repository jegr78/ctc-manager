---
phase: 95-match-channel-posts
plan: 04
status: validated
nyquist_compliant: true
test_count_added: 7
total_test_classes_phase95: 19
build_command: ./mvnw verify -Pe2e
build_result: success
duration: ~10min
line_coverage: 0.8861
spotbugs_bug_count: 0
codeql_high_severity_alerts: 0
verified: 2026-05-22
---

# Plan 95-04 Validation

## Test Inventory (Plan 95-04 only)

| Class | Tag | Tests | Purpose |
|-------|-----|-------|---------|
| `DiscordPostServiceMatchResultsIT` | integration | 4 | Multipart-POST + PATCH-on-existing + BusinessRuleException + filename pin |
| `DiscordPostServiceScheduleIT` | integration | 5 | 4-field embed + _TBD_ placeholder + `<t:UNIX:F>` Date format + BusinessRuleException |
| `MatchServiceScheduleEditHookIT` | integration | 4 | Pre/post-diff hook: changed-with-post / unchanged / changed-without-post / 5xx-swallow |
| `MatchDetailMatchResultsStaleIT` | integration | 3 | 3 label states (Post / Re-Post / Update) — race-result-based stale signal |
| `MatchUpdatedAtNoopSaveIT` | integration | 1 | Pitfall-4 empirical pin (Assumption A1 FALSIFIED — pinned with isAfter assertion) |
| `MatchDetailMatchResultsButtonE2ETest` | e2e | 4 | Match Results button visibility across 4 states + desktop/mobile |
| `MatchDetailScheduleButtonE2ETest` | e2e | 4 | Schedule button visibility across 4 states + desktop/mobile |

**Plan 95-04 total:** 7 classes, 25 tests.

## Nyquist Compliance

Per phase-level VALIDATION strategy (D-95-08): each requirement is sampled at ≥ 2 independent test points (Nyquist criterion).

| Requirement | Sample 1 (WireMock IT) | Sample 2 (Controller IT or E2E) |
|-------------|------------------------|---------------------------------|
| POST-04 happy-path | `DiscordPostServiceMatchResultsIT#givenAllRacesHaveResults_…thenSinglePngMultipartPost` | `MatchDetailMatchResultsButtonE2ETest#givenCompleteResultsNoPost_…thenPostButtonVisible` |
| POST-04 stale-detection | `MatchDetailMatchResultsStaleIT#givenStaleMatchResultsRow_…thenUpdateLabelAndStale` | `MatchDetailMatchResultsButtonE2ETest#givenStalePost_…thenUpdateLabelVisible` |
| POST-04 pre-flight | `DiscordPostServiceMatchResultsIT#givenOneRaceMissingResults_…thenBusinessRuleException` | `MatchDetailMatchResultsButtonE2ETest#givenIncompleteResults_…thenButtonDisabledWithTooltip` |
| POST-05 happy-path | `DiscordPostServiceScheduleIT#givenRaceWithDateTime_…thenJsonPostWithEmbedContaining4Fields` | `MatchDetailScheduleButtonE2ETest#givenRaceDateTimeSetNoPost_…thenPostScheduleButtonVisible` |
| POST-05 auto-edit hook | `MatchServiceScheduleEditHookIT#givenScheduleFieldsChangedAndSchedulePostExists_…thenWebhookPatchFiresOnce` | `MatchServiceScheduleEditHookIT#givenWebhook5xx_…thenMatchSaveStillCommitsAndHookSwallows` |
| POST-05 _TBD_ rendering | `DiscordPostServiceScheduleIT#givenNullLobbyHost_…thenRendersTbdPlaceholder` | `DiscordPostServiceScheduleIT#givenBlankStreamer_…thenRendersTbdPlaceholder` |

**Verdict:** `nyquist_compliant: true` — all POST-04/POST-05 requirements covered at ≥ 2 independent points.

## Phase-95 Regression Check

`./mvnw verify -Pe2e` on the final commit:
- BUILD SUCCESS (Total time: 9:49 min)
- Plans 95-01 + 95-02 + 95-03 tests all PASS (no regressions)
- `BackupSchemaGuardTest` + `DiscordGlobalConfigGuardTest` + `DiscordPostGuardTest` all PASS (Landmine 1 invariant holds: EXPORT_ORDER.size() = 24, SCHEMA_VERSION = 1)
- Phase 94 ITs (DiscordChannelServiceWireMockIT, …PermissionAuditFailIT, …CleanupFailIT, …ArchiveServiceWireMockIT) all PASS — `createMatchChannel` signature invariant honored
- JaCoCo line coverage: 88.61 % (above configured jacoco:check threshold)
- SpotBugs: 0 BugInstance
- Total test count: 1566 Surefire + 448 Failsafe = 2014 tests

## Landmines + Pitfalls Verdicts

| ID | Source | Status |
|----|--------|--------|
| RESEARCH Landmine 1 | DiscordPost excluded from BackupSchema.EXPORT_ORDER | Verified — `DiscordPostGuardTest` PASS |
| RESEARCH Landmine 2 | No `raceNumber` field; use list-index | Verified — `grep -c "getRaceNumber" DiscordPostService.java` = 0 |
| RESEARCH Landmine 3 | Hook target is `updateDiscordFields`, NOT `save(MatchForm)` | Verified — `grep -c "save(MatchForm" MatchService.java` = 0 |
| RESEARCH Landmine 4 | No `Embed.color` field (deferred to Phase 98) | Verified — `grep -ci "color" DiscordPostService.java` = 0 |
| RESEARCH Landmine 5 | `matchCanRenderResults` predicate (no `Match.final` field) | Verified — predicate uses `match.getRaces().stream().allMatch(r -> !r.getResults().isEmpty())` |
| RESEARCH Landmine 6 | Auto-post hook swallows exceptions; channel persists | Verified — Plan 95-02 `DiscordChannelServiceAutoPostHookIT` PASS |
| RESEARCH Landmine 7 | DiscordHostValidator.requireAllowed defense-in-depth | Verified — first line of every postOrEdit + editMessageWithAttachments call |
| RESEARCH Landmine 8 | Playwright synchronous up to ~30s/card | Documented — operator informed in 95-02 SUMMARY |
| RESEARCH Pitfall 4 | Assumption A1: no-op save doesn't advance updatedAt | **FALSIFIED** — `MatchUpdatedAtNoopSaveIT` PASS with FLIPPED assertion; stale-signal pivoted to `max(race.results[*].updatedAt)` |
| RESEARCH Pitfall 5 | No `?wait=true` on PATCH | Verified — `grep -c "wait=true" DiscordWebhookClient.java` = 0 |
| RESEARCH Pitfall 6 | N+1 lookup for RaceLineup acceptable | Documented — 3-4 races/match makes it negligible |
| RESEARCH Pitfall 7 | Path-traversal guard on readPng | Verified — `DiscordPostServiceTeamCardsIT#givenAttackerControlledCardPath_…thenSecurityException` PASS |
| RESEARCH Pitfall 9 | requireAllowed first line in client methods | Verified — `grep -c "requireAllowed" DiscordWebhookClient.java` ≥ 4 |

## Phase-Close Checklist

- [x] All 4 plan summaries written (95-01-SUMMARY.md / 95-02-SUMMARY.md / 95-03-SUMMARY.md / 95-04-SUMMARY.md)
- [x] All 4 plans flipped to complete in ROADMAP.md via `gsd-sdk query roadmap.update-plan-progress` after each plan
- [x] `./mvnw verify -Pe2e` BUILD SUCCESS on the head commit of `gsd/v1.13-discord-integration`
- [x] JaCoCo + SpotBugs gates green
- [x] UAT-05 (11-step Live-Discord Post Lifecycle Smoke) staged in `.planning/STATE.md` § Pending UATs
- [x] `MatchUpdatedAtNoopSaveIT` empirical verdict documented (Pitfall 4 falsified, pivot applied)
- [x] No `Embed.color` field (deferred to Phase 98 polish per Landmine 4) — backlog item recorded

## Deferred Items

- **Embed.color field on all 5 post-types** — Phase 98 polish (CSS-style cohesion across all Discord embeds). Tracked under PROJECT.md / v1.13-ROADMAP.md as a deferral, not a regression.
- **Race.dateTime auto-edit Schedule trigger** — RaceService stays Discord-free in v1.13 per D-95-04a. Operators must manually Re-Post Schedule when a race-time changes (the button is visible because a SCHEDULE row exists; clicking it re-renders the embed with the new time). Deferred to v1.14.
- **`.screenshots/95-04/` capture** — playwright-cli mobile-viewport sweep is staged but not executed inline. Operator can run the captures during the UAT-05 procedure as supporting evidence.

---
*Phase: 95-match-channel-posts*
*Plan: 04*
*Validation completed: 2026-05-22*
