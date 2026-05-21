---
phase: 94
slug: team-roles-match-channel-lifecycle
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-21
---

# Phase 94 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from 94-RESEARCH.md § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ + Mockito + WireMock + Playwright |
| **Config file** | `pom.xml` (Surefire/Failsafe routed by `@Tag` per `.planning/codebase/TESTING.md`) |
| **Quick run command** | `./mvnw -Dtest=<ClassName> test` (unit) ; `./mvnw -Dit.test=<ClassName>IT verify -DfailIfNoTests=false` (IT) |
| **Full suite command** | `./mvnw verify -Pe2e` |
| **Estimated runtime** | ~18 min (CI E2E 17:39 baseline + ~60–90 s for new tests, within ±20 % budget per STATE.md) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw -Dtest=<NewClass> test` (per `[[feedback-test-call-optimization]]`).
- **After every plan wave (= each plan close):** Run `./mvnw clean test-compile` + `./mvnw verify` (Surefire + Failsafe excluding e2e).
- **Before `/gsd-verify-work`:** Run `./mvnw verify -Pe2e` (full Playwright sweep).
- **Max feedback latency:** 90 s for unit, ~5 min for IT, ~18 min for full.

---

## Per-Task Verification Map

> Plans 94-01, 94-02, 94-03 each map 1:1 to CHAN-01/02/03 (CONTEXT.md D-07). Test classes below come from RESEARCH.md § Validation Architecture (23 entries).

| Test Class | Plan | Type | Requirement | Threat Ref | Behavior Covered | @Tag | Wave 0 | Status |
|------------|------|------|-------------|------------|------------------|------|--------|--------|
| `TeamFormSnowflakeValidationTest` | 94-01 | Unit | CHAN-01 | T-93-05 (input val) | Snowflake regex rejects non-snowflake, accepts empty + 17–20-digit | untagged | new | ⬜ pending |
| `DiscordSnowflakeTest` | 94-01 | Unit | CHAN-01 | T-93-05 | Shared `DiscordSnowflake.PATTERN` constant correctness | untagged | new | ⬜ pending |
| `TeamRepositoryDiscordRoleIdIT` | 94-01 | IT | CHAN-01 | — | `teams.discord_role_id` round-trip (H2 + MariaDB) | `@Tag("integration")` | new | ⬜ pending |
| `DiscordRoleCacheTest` | 94-01 | Unit | CHAN-01 | — | 60-min TTL + clock-injected expiry; refresh() bulk-replaces | untagged | new | ⬜ pending |
| `DiscordConfigControllerIT` (extend) | 94-01 | IT | CHAN-01 | — | `refreshRolesCache()` ALSO populates `roleCache` | `@Tag("integration")` | extend | ⬜ pending |
| `DiscordGlobalConfigRepositoryIT` (extend) | 94-01 | IT | CHAN-01 | — | `current_match_category_id` round-trip + Form render | `@Tag("integration")` | extend | ⬜ pending |
| `TeamFormDiscordRoleDropdownE2ETest` | 94-01 | E2E | CHAN-01 | — | Dropdown when cache warm, plain-text + warning when empty | `@Tag("e2e")` | new (`org.ctc.e2e.discord`) | ⬜ pending |
| `MatchRepositoryDiscordFieldsIT` | 94-02 | IT | CHAN-02 | — | `Match` round-trips 7 new fields | `@Tag("integration")` | new | ⬜ pending |
| `MatchFormValidationTest` | 94-02 | Unit | CHAN-02 | T-93-05 | discordTeaser ≤2000, streamLink ≤500, host ≤100 | untagged | new | ⬜ pending |
| `MatchToStringTest` | 94-02 | Unit | CHAN-02 | T-93-02 | `@ToString.Exclude` on `discordChannelWebhookUrl` | untagged | new | ⬜ pending |
| `DiscordPermissionsTest` | 94-02 | Unit | CHAN-02 | T-93-03 | Bitmask composite correctness (TEAM_MEMBER_ALLOW_MASK / DENY_MASK / EVERYONE_DENY_VIEW_MASK) | untagged | new | ⬜ pending |
| `DiscordRestClientIT` (extend) | 94-02 | IT | CHAN-02 | — | `createWebhook` / `fetchChannel` / `deleteChannel` typed methods | `@Tag("integration")` | extend | ⬜ pending |
| `DiscordChannelServiceWireMockIT` | 94-02 | IT | CHAN-02 | T-93-03 | 9-step happy-path: createChannel → createWebhook → fetchChannel → audit-pass → DB write | `@Tag("integration")` | new | ⬜ pending |
| `DiscordChannelServicePermissionAuditFailIT` | 94-02 | IT | CHAN-02 | T-93-03 | Audit-fail → DELETE-channel + DB rollback + `DiscordAuthException` | `@Tag("integration")` | new | ⬜ pending |
| `DiscordChannelServiceCleanupFailIT` | 94-02 | IT | CHAN-02 | T-93-03 | Audit-fail + cleanup-DELETE-fails → both exceptions surfaced + WARN log | `@Tag("integration")` | new | ⬜ pending |
| `MatchControllerCreateChannelErrorCategoryTest` | 94-02 | Unit | CHAN-02 | T-91-02-IL | Typed-catch → flash badge for AUTH / TRANSIENT / NOT_FOUND categories | untagged | new | ⬜ pending |
| `MatchEditFormIT` | 94-02 | IT | CHAN-02 | — | `POST /save-edit` round-trips 5 Discord scheduling fields | `@Tag("integration")` | new | ⬜ pending |
| `MatchDetailControllerE2ETest` | 94-02 | E2E | CHAN-02 | — | Detail page render + Create-Channel button visibility gating (3 conditions) | `@Tag("e2e")` | new (`org.ctc.e2e.discord`) | ⬜ pending |
| `DiscordCategoryResolverTest` | 94-03 | Unit | CHAN-03 | — | Regex matches year + (n) variants; defaultSelection logic | untagged | new | ⬜ pending |
| `DiscordCategoryResolverWireMockIT` | 94-03 | IT | CHAN-03 | — | `resolveArchiveCategoriesFor(year)` filter+sort+count derivation | `@Tag("integration")` | new | ⬜ pending |
| `DiscordChannelArchiveServiceWireMockIT` | 94-03 | IT | CHAN-03 | — | `POST /move-to-archive` happy-path PATCH | `@Tag("integration")` | new | ⬜ pending |
| `MatchControllerMoveToArchiveErrorCategoryTest` | 94-03 | Unit | CHAN-03 | T-91-02-IL | `DiscordCategoryFullException` → `category-full` flash + runbook link | untagged | new | ⬜ pending |
| `ArchiveModalE2ETest` | 94-03 | E2E | CHAN-03 | — | Modal renders categories+counts, default-radio, all-full banner | `@Tag("e2e")` | new (`org.ctc.e2e.discord`) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

> Test stubs that the planner MUST schedule as Task 1 of each plan (per Nyquist Dimension 8 — every implementation task must have an existing test file to update).

**Plan 94-01:**
- [ ] `src/test/java/org/ctc/admin/dto/TeamFormSnowflakeValidationTest.java`
- [ ] `src/test/java/org/ctc/discord/dto/DiscordSnowflakeTest.java`
- [ ] `src/test/java/org/ctc/domain/repository/TeamRepositoryDiscordRoleIdIT.java`
- [ ] `src/test/java/org/ctc/discord/DiscordRoleCacheTest.java`
- [ ] `src/test/java/org/ctc/e2e/discord/TeamFormDiscordRoleDropdownE2ETest.java`

**Plan 94-02:**
- [ ] `src/test/java/org/ctc/domain/repository/MatchRepositoryDiscordFieldsIT.java`
- [ ] `src/test/java/org/ctc/admin/dto/MatchFormValidationTest.java`
- [ ] `src/test/java/org/ctc/domain/model/MatchToStringTest.java`
- [ ] `src/test/java/org/ctc/discord/DiscordPermissionsTest.java`
- [ ] `src/test/java/org/ctc/discord/service/DiscordChannelServiceWireMockIT.java`
- [ ] `src/test/java/org/ctc/discord/service/DiscordChannelServicePermissionAuditFailIT.java`
- [ ] `src/test/java/org/ctc/discord/service/DiscordChannelServiceCleanupFailIT.java`
- [ ] `src/test/java/org/ctc/admin/controller/MatchControllerCreateChannelErrorCategoryTest.java`
- [ ] `src/test/java/org/ctc/e2e/discord/MatchDetailControllerE2ETest.java`
- [ ] `src/test/java/org/ctc/discord/service/MatchEditFormIT.java`

**Plan 94-03:**
- [ ] `src/test/java/org/ctc/discord/service/DiscordCategoryResolverTest.java`
- [ ] `src/test/java/org/ctc/discord/service/DiscordCategoryResolverWireMockIT.java`
- [ ] `src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java`
- [ ] `src/test/java/org/ctc/admin/controller/MatchControllerMoveToArchiveErrorCategoryTest.java`
- [ ] `src/test/java/org/ctc/e2e/discord/ArchiveModalE2ETest.java`

No new test-framework install needed — Surefire/Failsafe routing by `@Tag` already in place.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Mobile screenshot sweep (375×667) | CHAN-01/02/03 D-06 | Visual; no automated assertion on layout | `playwright-cli` opens each new page Desktop 1280×800 + Mobile 375×667 → screenshots land under `.screenshots/94-{plan}/` per [[feedback-screenshots-folder]] |
| Live-Discord UAT-04 (smoke against operator's test guild) | CHAN-01/02/03 | Requires real Discord bot + real test guild; cannot run in CI | UAT-04 plays back the 7 steps from CONTEXT.md § Live-UAT Strategy (D-12). Operator runs BEFORE Phase 95 plans 95-02/03/04 start. Tracked in STATE.md `Pending UATs` at Phase 94 close. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING test-file references
- [ ] No watch-mode flags
- [ ] Feedback latency < 90 s for unit / < 5 min for IT
- [ ] `nyquist_compliant: true` set in frontmatter after all 23 test classes shipped + green

**Approval:** pending
