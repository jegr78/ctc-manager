---
phase: 100
slug: match-day-channel-naming-scheme-phase-prefix-rs-po-pm-option
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-26
audited: 2026-05-26
---

# Phase 100 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source: `100-RESEARCH.md` § 5 (Validation Architecture).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + WireMock 3.x |
| **Config file** | `pom.xml` (Surefire / Failsafe / JaCoCo) |
| **Quick run command** | `./mvnw test -Dtest=DiscordChannelServiceNamingTest` |
| **Full suite command** | `./mvnw clean verify -Pe2e` |
| **Estimated runtime** | unit ~0.3s · IT subset ~60s · full E2E ~10m |

---

## Sampling Rate

- **After every task commit:** `./mvnw test -Dtest=DiscordChannelServiceNamingTest` (unit, <1s)
- **After every IT fixture refresh task:** `./mvnw test -Dit.test=<TouchedIT> -DfailIfNoTests=false`
- **After every plan wave:** `./mvnw test -Dtest=DiscordChannel*IT,DiscordRest*IT` (all touched ITs)
- **Before `/gsd:verify-work`:** `./mvnw clean verify -Pe2e` must be green (Playwright + full suite) — **PASSED 2026-05-26 in 9:55 min**
- **Max feedback latency:** 1s (unit) / 60s (touched ITs)

---

## Per-Task Verification Map

> All 14 decisions have at least one automated assertion OR are scope-tagged N/A.

| Decision | Behavior | Test Type | File | Automated Command | File Exists | Status |
|----------|----------|-----------|------|-------------------|-------------|--------|
| D-01 | `md{N}-{phase}-{home}-vs-{away}` baseline | unit | `DiscordChannelServiceNamingTest.givenMatchWithPhaseTypeAndNoGroup_…` | `./mvnw test -Dtest=DiscordChannelServiceNamingTest` | ✅ | ✅ green |
| D-02 | Group token omitted when `Matchday.group == null` | unit | `DiscordChannelServiceNamingTest.givenMatchWithPhaseTypeAndNoGroup_…` | same | ✅ | ✅ green |
| D-03 | `toLowerCase(Locale.ROOT)` on fully composed string | unit | `DiscordChannelServiceNamingTest.givenMixedCaseTeamShortNames_…` + `givenTurkishCapitalIInShortName_…` | same | ✅ | ✅ green |
| D-04 | `phaseAbbrev` switch: `REGULAR→rs`, `PLAYOFF→po`, `PLACEMENT→pm` | unit | `DiscordChannelServiceNamingTest.givenMatchWithPhaseTypeAndNoGroup_…` (parameterized) + `givenPlayoffPhaseWithGroup_…` + `givenPlacementPhaseWithGroup_…` | same | ✅ | ✅ green |
| D-05 | Exhaustive switch, no `default` branch | compile | (compiler gate) — `grep -c "default ->" DiscordChannelService.java` returns 0 | `./mvnw compile` | ✅ | ✅ green |
| D-06 | NFD-decompose + slugify of `SeasonPhaseGroup.name` (incl. umlauts, ß, digits, whitespace edges) | unit | `DiscordChannelServiceNamingTest.givenMatchWithGroupName_…` (9 @CsvSource rows incl. `Über-Liga`, `Straße`, `Group 42`, whitespace) | same | ✅ | ✅ green |
| D-07 | Empty slug after derivation → omit token (no throw) | unit | `DiscordChannelServiceNamingTest.givenMatchWithGroupNameThatSlugifiesToEmpty_…` | same | ✅ | ✅ green |
| D-08 | No migration of existing channels (no code path) | scope | — STATE.md Deferred Items records the leave-as-is verdict | — | N/A | ✅ documented |
| D-09 | Two-scheme coexistence (no code path) | scope | — STATE.md Deferred Items records the acceptance | — | N/A | ✅ documented |
| D-10 | `> 100` chars → `BusinessRuleException` (no silent truncate) — plus 100-char success-boundary asserted | unit | `DiscordChannelServiceNamingTest.givenGroupNameThatPushesOverHundredChars_…` + `givenNameLandsAtExactly100Chars_…` | `./mvnw test -Dtest=DiscordChannelServiceNamingTest` | ✅ | ✅ green |
| D-11 | `shortName` illegal-char handling deferred to Discord API | IT (existing) | `DiscordChannelServiceWireMockIT` | `./mvnw test -Dit.test=DiscordChannelServiceWireMockIT -DfailIfNoTests=false` | ✅ | ✅ green |
| D-12 | Naming logic stays inside `DiscordChannelService` (private static helpers) | code review | confirmed in `100-REVIEW.md` — `phaseAbbrev` + `groupSlug` are `private static` | static analysis / review | ✅ | ✅ green |
| D-13 | Pure-unit naming test (covers D-01..D-07, D-10 + boundary + null-guard + Turkish-i + ZWJ-edge) | unit | `DiscordChannelServiceNamingTest` (20 invocations) | same | ✅ | ✅ green |
| D-14 | All 5 old-literal IT files refreshed to new format + outbound `matchingJsonPath` pinning | IT | `DiscordRestClientIT` (any-name stub), `DiscordChannelServicePermissionAuditFailIT`, `DiscordChannelArchiveServiceWireMockIT`, `DiscordChannelServiceCleanupFailIT`, `DiscordChannelServiceWireMockIT` (+ `matchingJsonPath("$.name", equalTo("md1-rs-homeh-vs-awayh"))` pin) | `./mvnw test -Dit.test=DiscordChannel*IT,DiscordRestClientIT -DfailIfNoTests=false` — 28 ITs green | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/java/org/ctc/discord/service/DiscordChannelServiceNamingTest.java` — pure-unit class with 20 invocations covering D-01..D-07, D-10, plus boundary (100-char success), null-guard, PLAYOFF+group, PLACEMENT+group, Turkish-i, `Straße`, `Group 42`, whitespace edges
- [x] `DiscordRestClientIT.java:206` — refreshed (later neutralized to `any-name` per IN-05 to avoid phantom grep hits)
- [x] `DiscordChannelArchiveServiceWireMockIT.java:91,113` — 2× refresh to `md1-rs-h-vs-a`
- [x] `DiscordChannelServiceCleanupFailIT.java:117,125` — 2× refresh to `md1-rs-hc-vs-ac`
- [x] `DiscordChannelServicePermissionAuditFailIT.java:111,128,154,182` — 4× refresh to `md1-rs-h-vs-a`
- [x] `DiscordChannelServiceWireMockIT.java:123,130,182,189,219` — 5× refresh (suffix-aware) + outbound `matchingJsonPath("$.name", equalTo("md1-rs-homeh-vs-awayh"))` pinning assertion (closes "WireMock is not Real-API Coverage" gap)

*(`DiscordAutoPostListenerIT` is intentionally not in Wave 0 — RESEARCH § 3 confirmed its stubs use placeholder `"name":"x"` and do not assert channel-name strings.)*

---

## Manual-Only Verifications

| Behavior | Decision Ref | Why Manual | Test Instructions |
|----------|--------------|------------|-------------------|
| Live Discord rejects illegal `shortName` chars at API level | D-11 | Cannot mock Discord's name-regex enforcement in unit/IT; only the real Discord API rejects | Optional — covered by existing UAT-03 Live-Discord smoke procedure (`docs/operations/discord-integration.md` § 4) and only triggers if operator manually edits a `Team.shortName` to contain illegal chars. Not a required Phase 100 acceptance gate. |
| Operator-facing flash message when 100-char overflow fires | D-10 | Operator UX — the flash text is observable only in browser | Trigger overflow via a long `SeasonPhaseGroup.name` on `/admin/seasons/{id}/phases/{phaseId}` form, then click create-channel button on `/admin/matches/{id}/edit`. Verify the flash message contains the produced name and length. Automated unit test for the throw itself is the primary gate. **Tracked in `100-UAT.md` Test 5.** |
| Live Discord create-channel produces new format on operator's test guild | D-01..D-14 | WireMock IT pins the outbound JSON body — but only a real Discord call confirms the channel-name is honored end-to-end | Tracked in `100-UAT.md` Tests 2-4. Reuses UAT-04 procedure (`docs/operations/discord-integration.md`). |

---

## Validation Sign-Off

- [x] All decisions D-01..D-14 mapped to at least one automated assertion OR scope-tagged as N/A
- [x] Sampling continuity: no plan modifying production code lacks a follow-on test command
- [x] Wave 0 closes the `DiscordChannelServiceNamingTest` gap before any IT-refresh plan wave
- [x] No watch-mode flags (Maven only)
- [x] Feedback latency < 60s on every per-task and per-wave gate (unit < 1s, ITs ≤ 60s)
- [x] `nyquist_compliant: true` set in frontmatter — audit complete 2026-05-26

**Approval:** ✅ green — Phase 100 is Nyquist-compliant. All 14 decisions automated or scope-N/A.

---

## Validation Audit 2026-05-26

| Metric | Count |
|--------|-------|
| Decisions in scope | 14 (D-01..D-14) |
| Automated coverage (unit + IT) | 12 (D-01..D-07, D-10..D-14) |
| Scope-only (N/A by design) | 2 (D-08, D-09) — documented in STATE.md Deferred Items |
| Compile-time gate | 1 (D-05) — exhaustiveness enforced by javac |
| Code-review gate | 1 (D-12) — confirmed in `100-REVIEW.md` |
| Gaps found | 0 |
| Gaps resolved this audit | 0 |
| Escalated to manual-only | 0 |
| Test count delta vs. pre-Phase-100 | `DiscordChannelServiceNamingTest` +20 (new) · 5 ITs unchanged (refresh only) |
| Phase-end `./mvnw clean verify -Pe2e` | ✅ BUILD SUCCESS 2026-05-26 — 2255 tests, JaCoCo 88.98%, SpotBugs 0 |
