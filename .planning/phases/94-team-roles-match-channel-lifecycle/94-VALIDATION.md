---
phase: 94
slug: team-roles-match-channel-lifecycle
status: shipped
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-21
audited: 2026-05-22
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
| `TeamFormSnowflakeValidationTest` | 94-01 | Unit | CHAN-01 | T-93-05 (input val) | Snowflake regex rejects non-snowflake, accepts empty + 17–20-digit | untagged | new | ✅ green |
| `DiscordSnowflakeTest` | 94-01 | Unit | CHAN-01 | T-93-05 | Shared `DiscordSnowflake.PATTERN` constant correctness | untagged | new | ✅ green |
| `TeamRepositoryDiscordRoleIdIT` | 94-01 | IT | CHAN-01 | — | `teams.discord_role_id` round-trip (H2 + MariaDB) | `@Tag("integration")` | new | ✅ green |
| `DiscordRoleCacheTest` | 94-01 | Unit | CHAN-01 | — | 60-min TTL + clock-injected expiry; refresh() bulk-replaces | untagged | new | ✅ green |
| `DiscordConfigControllerIT` (extend) | 94-01 | IT | CHAN-01 | — | `refreshRolesCache()` ALSO populates `roleCache` | `@Tag("integration")` | extend | ✅ green |
| `DiscordGlobalConfigRepositoryIT` (extend) | 94-01 | IT | CHAN-01 | — | `current_match_category_id` round-trip + Form render | `@Tag("integration")` | extend | ✅ green |
| `TeamFormDiscordRoleDropdownE2ETest` | 94-01 | E2E | CHAN-01 | — | Dropdown when cache warm, plain-text + warning when empty | `@Tag("e2e")` | new (`org.ctc.e2e.discord`) | ✅ green |
| `MatchRepositoryDiscordFieldsIT` | 94-02 | IT | CHAN-02 | — | `Match` round-trips 7 new fields | `@Tag("integration")` | new | ✅ green |
| `MatchFormValidationTest` | 94-02 | Unit | CHAN-02 | T-93-05 | discordTeaser ≤2000, streamLink ≤500, host ≤100 | untagged | new | ✅ green |
| `MatchToStringTest` | 94-02 | Unit | CHAN-02 | T-93-02 | `@ToString.Exclude` on `discordChannelWebhookUrl` | untagged | new | ✅ green |
| `DiscordPermissionsTest` | 94-02 / 94-04 | Unit | CHAN-02 / CHAN-02-FOLLOWUP | T-93-03 | Bitmask composite correctness (TEAM_MEMBER_ALLOW_MASK / DENY_MASK / EVERYONE_DENY_VIEW_MASK) + 94-04 `BOT_ALLOW_MASK` composition (7 bits + 2 deliberately-excluded) | untagged | new | ✅ green |
| `DiscordRestClientIT` (extend) | 94-02 | IT | CHAN-02 | — | `createWebhook` / `fetchChannel` / `deleteChannel` typed methods | `@Tag("integration")` | extend | ✅ green |
| `DiscordChannelServiceWireMockIT` | 94-02 / 94-04 / inline | IT | CHAN-02 / CHAN-02-FOLLOWUP | T-93-03 | 9-step happy-path; 94-04 4th-overwrite shape via stacked `matchingJsonPath`; inline intra-team match (LinkedHashSet dedup + dynamic audit size) | `@Tag("integration")` | new | ✅ green |
| `DiscordChannelServicePermissionAuditFailIT` | 94-02 / 94-04 | IT | CHAN-02 / CHAN-02-FOLLOWUP | T-93-03 | Audit-fail → DELETE-channel + DB rollback + `DiscordAuthException`; 94-04 size-mismatch + wrong-role-set + 4-overwrite-without-bot-member (noise `allow=0`) | `@Tag("integration")` | new | ✅ green |
| `DiscordChannelServiceCleanupFailIT` | 94-02 / 94-04 | IT | CHAN-02 / CHAN-02-FOLLOWUP | T-93-03 | Audit-fail + cleanup-DELETE-fails → both exceptions surfaced + WARN log (94-04: 5-overwrite trigger under size-4 audit) | `@Tag("integration")` | new | ✅ green |
| `MatchControllerCreateChannelErrorCategoryTest` | 94-02 | Unit | CHAN-02 | T-91-02-IL | Typed-catch → flash badge for AUTH / TRANSIENT / NOT_FOUND categories | untagged | new | ✅ green |
| `MatchEditFormIT` | 94-02 | IT | CHAN-02 | — | `POST /save-edit` round-trips 5 Discord scheduling fields | `@Tag("integration")` | new | ✅ green |
| `MatchDetailControllerE2ETest` | 94-02 | E2E | CHAN-02 | — | Detail page render + Create-Channel button visibility gating (3 conditions) | `@Tag("e2e")` | new (`org.ctc.e2e.discord`) | ✅ green |
| `DiscordCategoryResolverTest` | 94-03 | Unit | CHAN-03 | — | Regex matches year + (n) variants; defaultSelection logic | untagged | new | ✅ green |
| `DiscordCategoryResolverWireMockIT` | 94-03 | IT | CHAN-03 | — | `resolveArchiveCategoriesFor(year)` filter+sort+count derivation | `@Tag("integration")` | new | ✅ green |
| `DiscordChannelArchiveServiceWireMockIT` | 94-03 / inline | IT | CHAN-03 | — | `POST /move-to-archive` happy-path PATCH; inline JsonInclude(NON_NULL) assertion (`notMatching .*"name".*`); inline button-hidden after archive (re-render + `discordChannelArchivedAt` persisted) | `@Tag("integration")` | new | ✅ green |
| `MatchControllerMoveToArchiveErrorCategoryTest` | 94-03 | Unit | CHAN-03 | T-91-02-IL | `DiscordCategoryFullException` → `category-full` flash + runbook link | untagged | new | ✅ green |
| `ArchiveModalE2ETest` | 94-03 | E2E | CHAN-03 | — | Modal renders categories+counts, default-radio, all-full banner | `@Tag("e2e")` | new (`org.ctc.e2e.discord`) | ✅ green |
| `DiscordBotIdentityCacheTest` | 94-04 | Unit | CHAN-02-FOLLOWUP | T-93-03 / T-94-04-02 | Cold-cache lazy-fetch; cache-hit no-refetch; force-refresh re-fetch + cache update | untagged | new | ✅ green |
| `DiscordConfigControllerTest` | 94-04 | Unit | CHAN-02-FOLLOWUP | T-91-02-IL | `refreshRolesCache` happy-path asserts `verify(botIdentityCache).refresh()`; `botIdentityCache.refresh()` throwing `DiscordAuthException` → flash `errorCategory=auth` | untagged | new | ✅ green |
| `TeamEffectiveDiscordRoleIdTest` | inline (UAT-04) | Unit | CHAN-01 / CHAN-02 follow-up | — | `Team.getEffectiveDiscordRoleId()` parent-fallback semantics (5 BDD cases: own-ID present, parent fallback, both null, null parent, empty propagation) | untagged | new | ✅ green |
| `DiscordDevSeederIT` | inline (UAT-04) | IT | dev-only convenience | — | Boot-time seed of `DiscordGlobalConfig` from `app.discord.dev-seed.*` props; idempotent skip when `guildId` already set; auto-assign matching team roles by name; graceful DiscordApiException handling | `@Tag("integration")` | new | ✅ green |

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
- [x] `src/test/java/org/ctc/discord/service/DiscordCategoryResolverTest.java`
- [x] `src/test/java/org/ctc/discord/service/DiscordCategoryResolverWireMockIT.java`
- [x] `src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java`
- [x] `src/test/java/org/ctc/admin/controller/MatchControllerMoveToArchiveErrorCategoryTest.java`
- [x] `src/test/java/org/ctc/e2e/discord/ArchiveModalE2ETest.java`

**Plan 94-04 (CHAN-02-FOLLOWUP, UAT-04 gap-closure):**
- [x] `src/test/java/org/ctc/discord/DiscordBotIdentityCacheTest.java`
- [x] `src/test/java/org/ctc/discord/web/DiscordConfigControllerTest.java`

**Inline UAT-04 fixes (2026-05-22):**
- [x] `src/test/java/org/ctc/domain/model/TeamEffectiveDiscordRoleIdTest.java` (sub-team Discord-role inheritance)
- [x] `src/test/java/org/ctc/discord/DiscordDevSeederIT.java` (dev profile auto-seeder)
- [x] `DiscordChannelArchiveServiceWireMockIT` extended with `JsonInclude(NON_NULL)` payload-shape assertion + `givenChannelAlreadyArchived_whenRenderMatchDetail_thenMoveToArchiveButtonIsHidden`
- [x] `DiscordChannelServiceWireMockIT` extended with `givenIntraTeamMatchWithSameEffectiveRole_whenCreateMatchChannel_thenThreeOverwritePayloadAndAuditPasses` (LinkedHashSet dedup proof)

Plan-checked Wave-0 list updated 2026-05-22 — all 4 plans done.

Mark `[x]` once the file exists. All 23 base + 2 Plan 94-04 + 2 inline UAT-04 = 27 test artifacts shipped 2026-05-22. No new test-framework install needed — Surefire/Failsafe routing by `@Tag` already in place.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Mobile screenshot sweep (375×667) | CHAN-01/02/03 D-06 | Visual; no automated assertion on layout | `playwright-cli` opens each new page Desktop 1280×800 + Mobile 375×667 → screenshots land under `.screenshots/94-{plan}/` per [[feedback-screenshots-folder]] |
| Live-Discord UAT-04 (smoke against operator's test guild) | CHAN-01/02/03 | Requires real Discord bot + real test guild; cannot run in CI | UAT-04 plays back the 7 steps from CONTEXT.md § Live-UAT Strategy (D-12). Operator runs BEFORE Phase 95 plans 95-02/03/04 start. Tracked in STATE.md `Pending UATs` at Phase 94 close. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING test-file references
- [x] No watch-mode flags
- [x] Feedback latency < 90 s for unit / < 5 min for IT
- [x] `nyquist_compliant: true` set in frontmatter after all 23 base + 2 Plan-94-04 + 2 inline UAT-04 test classes shipped + green

**Approval:** 2026-05-22 (`/gsd-validate-phase 94`)

---

## Validation Audit 2026-05-22

| Metric | Count |
|--------|-------|
| Gaps found (tests) | 0 |
| Gaps found (Per-Task Map doc-only) | 4 rows (Plan 94-04 + inline UAT-04 fixes) |
| Resolved (auditor) | 0 — no test gaps |
| Resolved (doc update) | 4 — rows added inline |
| Escalated | 0 |

**Final tally:** 27 test artifacts (23 base + 2 Plan 94-04 + 2 inline UAT-04). Full verify `bjiyvgfy1` 2026-05-22 12:53:23 — BUILD SUCCESS, JaCoCo line coverage above 88.88 % baseline, SpotBugs `BugInstance size is 0`. UAT-04 closed by operator 2026-05-22 (sub-team inheritance, dev seeder, JsonInclude move-to-archive, archive-button gate all green against test guild).

**Playwright flake note (2026-05-22):** First verify-run of Bug-4 commit had 2 errors in `MatchdaysPageGeneratorTest` + `StandingsPageGeneratorTest` with `Page.captureScreenshot` protocol crash inside `TeamCardService.generateCard` during `DevDataSeeder` test-data seeding. Both pass cleanly when run in isolation (18/18). Re-running the full verify was green. Tracked as a known Chromium-resource-pressure pattern under heavy full-suite parallelism — not a regression from Phase 94 changes. Will memo to milestone retro if it recurs on CI.
