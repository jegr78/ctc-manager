---
phase: 95
slug: match-channel-posts
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-22
---

# Phase 95 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Per D-95-08 each of the 4 plans ships its own `9X-XX-VALIDATION.md` derived from this phase-level strategy.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + AssertJ + WireMock 3.9.2 (test scope) + Playwright (compile scope, `@Tag("e2e")`) |
| **Config file** | `pom.xml` (Surefire / Failsafe / JaCoCo / `-Pe2e` profile) |
| **Quick run command** | `./mvnw -Dit.test={current-IT-class} verify` |
| **Full suite command** | `./mvnw verify -Pe2e` |
| **Estimated runtime** | Quick: ~15-45 s per IT; Full: ~ 18 min CI E2E |

---

## Sampling Rate

- **After every task commit:** `./mvnw -Dit.test={current-IT-class} verify` (15-45 s per IT)
- **After every plan wave:** `./mvnw -Dit.test=DiscordPost*IT,MatchController*Post*IT,MatchService*ScheduleEdit*IT,DiscordChannelService*Auto*IT verify`
- **Before `/gsd:verify-work` / plan close:** `./mvnw verify -Pe2e` green
- **Phase gate (`/gsd-validate-phase 95`):** `./mvnw verify -Pe2e` green + JaCoCo line coverage ≥ 88.88 % + SpotBugs `BugInstance` count = 0 + CodeQL gate-step exit 0 + `BackupSchemaGuardTest` still green (size 24, SCHEMA_VERSION 1)
- **Max feedback latency:** 60 s (single IT quick-run)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------------|-----------|-------------------|-------------|--------|
| 95-01-* | 01 | 1 | POST-01 | webhook URL revalidated via `DiscordHostValidator.requireAllowed()` before parsing `(webhook_id, webhook_token)` | integration | `./mvnw -Dit.test=DiscordPostServiceWireMockIT verify` | ❌ W0 | ⬜ pending |
| 95-01-* | 01 | 1 | POST-01 | multipart-PATCH wire format = existing `executeMultipart` POST format with `.patch().uri("/messages/{messageId}")` | integration | `./mvnw -Dit.test=DiscordWebhookClientMultipartEditIT verify` | ❌ W0 | ⬜ pending |
| 95-01-* | 01 | 1 | POST-01 | `/admin/discord/posts` filter form is `DiscordPostFilterForm` DTO (no entity binding) — CLAUDE.md mass-assignment rule | integration | `./mvnw -Dit.test=DiscordPostFilterControllerIT verify` | ❌ W0 | ⬜ pending |
| 95-01-* | 01 | 1 | POST-01 | guard test: `BackupSchema.EXPORT_ORDER` neither grows to 25 nor contains `discord_post` row (per RESEARCH Landmine 1) | unit | `./mvnw test -Dtest=DiscordPostGuardTest` | ❌ W0 | ⬜ pending |
| 95-01-* | 01 | 1 | POST-01 | Playwright listing page Desktop+Mobile sweep | e2e | `./mvnw verify -Pe2e -Dit.test=DiscordPostsListE2ETest` | ❌ W0 | ⬜ pending |
| 95-02-* | 02 | 2 | POST-02 | `postTeamCards` happy-path with 2 attachments → multipart-POST OR multipart-PATCH | integration | `./mvnw -Dit.test=DiscordPostServiceTeamCardsIT verify` | ❌ W0 | ⬜ pending |
| 95-02-* | 02 | 2 | POST-02 | auto-post hook fires AFTER channel + webhook persist; failure leaves channel persisted (D-95-01a + RESEARCH Landmine 6) | integration | `./mvnw -Dit.test=DiscordChannelServiceAutoPostHookIT verify` | ❌ W0 | ⬜ pending |
| 95-02-* | 02 | 2 | POST-02 | refresh endpoint regenerates + re-posts | integration | `./mvnw -Dit.test=MatchControllerTeamCardsRefreshIT verify` | ❌ W0 | ⬜ pending |
| 95-02-* | 02 | 2 | POST-02 | Match-Detail buttons appear per state (Post / Re-Post / Refresh) | e2e | `./mvnw verify -Pe2e -Dit.test=MatchDetailTeamCardsButtonsE2ETest` | ❌ W0 | ⬜ pending |
| 95-03-* | 03 | 3 | POST-03 | `postSettings` builds N-attachment multipart with `settings-race-{i+1}.png` (list-index per RESEARCH Landmine 2) | integration | `./mvnw -Dit.test=DiscordPostServiceSettingsBundleIT verify` | ❌ W0 | ⬜ pending |
| 95-03-* | 03 | 3 | POST-03 | `postLineups` analog | integration | `./mvnw -Dit.test=DiscordPostServiceLineupsBundleIT verify` | ❌ W0 | ⬜ pending |
| 95-03-* | 03 | 3 | POST-03 | pre-flight predicates: 3 branches for SETTINGS + 3 for LINEUPS (all-complete / one-missing / empty-races) | unit | `./mvnw test -Dtest=DiscordPostServicePreFlightTest` | ❌ W0 | ⬜ pending |
| 95-03-* | 03 | 3 | POST-03 | pre-flight failure → `BusinessRuleException` → flash with `data-incomplete` errorCategory | integration | `./mvnw -Dit.test=MatchControllerPostSettingsPreFlightIT verify` | ❌ W0 | ⬜ pending |
| 95-03-* | 03 | 3 | POST-03 | Match-Detail Settings/Lineups buttons appear/disable correctly | e2e | `./mvnw verify -Pe2e -Dit.test=MatchDetailSettingsLineupsButtonsE2ETest` | ❌ W0 | ⬜ pending |
| 95-04-* | 04 | 4 | POST-04 | `postMatchResults` multipart-POST with `byte[]` from `generateMatchResults` | integration | `./mvnw -Dit.test=DiscordPostServiceMatchResultsIT verify` | ❌ W0 | ⬜ pending |
| 95-04-* | 04 | 4 | POST-04 | stale-detection: `existing.updatedAt < match.updatedAt` → button label flips to "Update" | integration | `./mvnw -Dit.test=MatchDetailMatchResultsStaleIT verify` | ❌ W0 | ⬜ pending |
| 95-04-* | 04 | 4 | POST-04 | no-op `matchRepository.save()` does NOT advance `match.updatedAt` (RESEARCH Pitfall 4 empirical pin) | integration | `./mvnw -Dit.test=MatchUpdatedAtNoopSaveIT verify` | ❌ W0 | ⬜ pending |
| 95-04-* | 04 | 4 | POST-04 | Match-Detail Match-Results button (Post / Re-Post / Update labels) | e2e | `./mvnw verify -Pe2e -Dit.test=MatchDetailMatchResultsButtonE2ETest` | ❌ W0 | ⬜ pending |
| 95-04-* | 04 | 4 | POST-05 | `postSchedule` builds Embed with 4 fields + `_TBD_` for blanks (no color field — RESEARCH Landmine 4) | integration | `./mvnw -Dit.test=DiscordPostServiceScheduleIT verify` | ❌ W0 | ⬜ pending |
| 95-04-* | 04 | 4 | POST-05 | auto-edit hook on `MatchService.updateDiscordFields` (not `save` — RESEARCH Landmine 3) — 3 branches: changed→PATCH; unchanged→no PATCH; no SCHEDULE post→no PATCH | integration | `./mvnw -Dit.test=MatchServiceScheduleEditHookIT verify` | ❌ W0 | ⬜ pending |
| 95-04-* | 04 | 4 | POST-05 | Match-Detail Schedule button visibility (firstRaceTime != null) | e2e | `./mvnw verify -Pe2e -Dit.test=MatchDetailScheduleButtonE2ETest` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Per D-95-08 each plan ships its own VALIDATION.md. The aggregate Wave 0 test files for Phase 95:

**Plan 95-01 Wave 0:**
- [ ] `src/test/java/org/ctc/discord/service/DiscordPostServiceWireMockIT.java`
- [ ] `src/test/java/org/ctc/discord/DiscordWebhookClientMultipartEditIT.java`
- [ ] `src/test/java/org/ctc/discord/web/DiscordPostFilterControllerIT.java`
- [ ] `src/test/java/org/ctc/discord/model/DiscordPostGuardTest.java` (RESEARCH Landmine 1 — analog to `DiscordGlobalConfigGuardTest`)
- [ ] `src/test/java/org/ctc/e2e/discord/posts/DiscordPostsListE2ETest.java`

**Plan 95-02 Wave 0:**
- [ ] `src/test/java/org/ctc/discord/service/DiscordPostServiceTeamCardsIT.java`
- [ ] `src/test/java/org/ctc/discord/service/DiscordChannelServiceAutoPostHookIT.java`
- [ ] `src/test/java/org/ctc/admin/controller/MatchControllerTeamCardsRefreshIT.java`
- [ ] `src/test/java/org/ctc/e2e/discord/posts/MatchDetailTeamCardsButtonsE2ETest.java`

**Plan 95-03 Wave 0:**
- [ ] `src/test/java/org/ctc/discord/service/DiscordPostServiceSettingsBundleIT.java`
- [ ] `src/test/java/org/ctc/discord/service/DiscordPostServiceLineupsBundleIT.java`
- [ ] `src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java` (Mockito-only, untagged)
- [ ] `src/test/java/org/ctc/admin/controller/MatchControllerPostSettingsPreFlightIT.java`
- [ ] `src/test/java/org/ctc/e2e/discord/posts/MatchDetailSettingsLineupsButtonsE2ETest.java`

**Plan 95-04 Wave 0:**
- [ ] `src/test/java/org/ctc/discord/service/DiscordPostServiceMatchResultsIT.java`
- [ ] `src/test/java/org/ctc/discord/service/DiscordPostServiceScheduleIT.java`
- [ ] `src/test/java/org/ctc/domain/service/MatchServiceScheduleEditHookIT.java`
- [ ] `src/test/java/org/ctc/admin/controller/MatchDetailMatchResultsStaleIT.java`
- [ ] `src/test/java/org/ctc/domain/service/MatchUpdatedAtNoopSaveIT.java` (research-validation IT for Pitfall 4)
- [ ] `src/test/java/org/ctc/e2e/discord/posts/MatchDetailMatchResultsButtonE2ETest.java`
- [ ] `src/test/java/org/ctc/e2e/discord/posts/MatchDetailScheduleButtonE2ETest.java`

**Framework install:** N/A — JUnit 5 + Mockito + AssertJ + WireMock + Playwright all already in `pom.xml`.

**Test count budget:** ~ 50-70 new tests across the 4 plans (in line with CONTEXT D-95-07 estimate). Coverage MUST hold Phase-94 baseline of ≥ 88.88 %.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live Discord post lifecycle (UAT-05, D-95-10) | POST-01..05 | WireMock cannot validate Discord-side rendering (timestamp render in operator timezone, image carousel, edit indicator badges, embed-field UI). | Operator-Test-Guild run per D-95-10 steps 1-11 after Phase-95 PR merges on milestone branch. Recorded in STATE.md as UAT-05. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60 s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
