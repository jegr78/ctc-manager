---
phase: 100
slug: match-day-channel-naming-scheme-phase-prefix-rs-po-pm-option
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-26
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
| **Estimated runtime** | unit ~10s · IT subset ~90s · full E2E ~18m |

---

## Sampling Rate

- **After every task commit:** `./mvnw test -Dtest=DiscordChannelServiceNamingTest` (unit, <10s)
- **After every IT fixture refresh task:** `./mvnw test -Dit.test=<TouchedIT> -DfailIfNoTests=false`
- **After every plan wave:** `./mvnw test -Dtest=DiscordChannel*IT,DiscordRest*IT` (all touched ITs)
- **Before `/gsd:verify-work`:** `./mvnw clean verify -Pe2e` must be green (Playwright + full suite)
- **Max feedback latency:** 10s (unit) / 90s (touched ITs)

---

## Per-Task Verification Map

> Plan IDs / task IDs will be added by the planner. Below is the decision-to-test-layer baseline derived from RESEARCH § 5 — every D-NN decision has at least one automated assertion.

| Decision | Behavior | Test Type | File | Automated Command | File Exists | Status |
|----------|----------|-----------|------|-------------------|-------------|--------|
| D-01 | `md{N}-{phase}-{home}-vs-{away}` baseline | unit | `DiscordChannelServiceNamingTest` | `./mvnw test -Dtest=DiscordChannelServiceNamingTest` | ❌ W0 | ⬜ pending |
| D-02 | Group token omitted when `Matchday.group == null` | unit | `DiscordChannelServiceNamingTest` | same | ❌ W0 | ⬜ pending |
| D-03 | `toLowerCase(Locale.ROOT)` on fully composed string | unit | `DiscordChannelServiceNamingTest` | same | ❌ W0 | ⬜ pending |
| D-04 | `phaseAbbrev` switch: `REGULAR→rs`, `PLAYOFF→po`, `PLACEMENT→pm` | unit | `DiscordChannelServiceNamingTest` | same | ❌ W0 | ⬜ pending |
| D-05 | Exhaustive switch, no `default` branch | compile | (compiler gate) | `./mvnw compile` | ✅ | ⬜ pending |
| D-06 | NFD-decompose + slugify of `SeasonPhaseGroup.name` (incl. umlauts) | unit | `DiscordChannelServiceNamingTest` | same | ❌ W0 | ⬜ pending |
| D-07 | Empty slug after derivation → omit token (no throw) | unit | `DiscordChannelServiceNamingTest` | same | ❌ W0 | ⬜ pending |
| D-08 | No migration of existing channels (no code path) | scope | — | — | N/A | n/a |
| D-09 | Two-scheme coexistence (no code path) | scope | — | — | N/A | n/a |
| D-10 | `> 100` chars → `BusinessRuleException` (no silent truncate) | unit + IT | `DiscordChannelServiceNamingTest` + `DiscordChannelServiceWireMockIT` overflow scenario | `./mvnw test -Dtest=DiscordChannelServiceNamingTest` + `-Dit.test=DiscordChannelServiceWireMockIT` | ❌ W0 | ⬜ pending |
| D-11 | `shortName` illegal-char handling deferred to Discord API | IT (existing) | `DiscordChannelServiceWireMockIT` | `./mvnw test -Dit.test=DiscordChannelServiceWireMockIT -DfailIfNoTests=false` | ✅ | ⬜ pending |
| D-12 | Naming logic stays inside `DiscordChannelService` (private static helpers) | code review | — | static analysis / review | ✅ | ⬜ pending |
| D-13 | Optional pure-unit naming test (covers D-01..D-07, D-10) | unit | `DiscordChannelServiceNamingTest` | same | ❌ W0 | ⬜ pending |
| D-14 | All 5 old-literal IT files refreshed to new format | IT | 5 IT files (see Wave 0) | `./mvnw test -Dit.test=DiscordChannel*IT,DiscordRestClientIT -DfailIfNoTests=false` | ❌ W0 (literals stale) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/ctc/discord/service/DiscordChannelServiceNamingTest.java` — new pure-unit class covering D-01, D-02, D-03, D-04, D-06, D-07, D-10. Uses `@ParameterizedTest` + `@CsvSource` for the 6 concrete examples in D-01 + the 6 slug examples in D-06.
- [ ] `DiscordRestClientIT.java:206` — refresh `md1-h-vs-a` literal to new format (per the IT's match setup: identify phase-type + group of the test match).
- [ ] `DiscordChannelArchiveServiceWireMockIT.java:91,113` — refresh 2× `md1-h-vs-a` literal.
- [ ] `DiscordChannelServiceCleanupFailIT.java:117,125` — refresh 2× `md1-hc-vs-ac` literal.
- [ ] `DiscordChannelServicePermissionAuditFailIT.java:111,128,154,182` — refresh 4× `md1-h-vs-a` literal.
- [ ] `DiscordChannelServiceWireMockIT.java:123,130,182,189,219` — refresh 5× `md1-home-vs-away` literal.

*(`DiscordAutoPostListenerIT` is intentionally not in Wave 0 — RESEARCH § 3 confirmed its stubs use placeholder `"name":"x"` and do not assert channel-name strings.)*

---

## Manual-Only Verifications

| Behavior | Decision Ref | Why Manual | Test Instructions |
|----------|--------------|------------|-------------------|
| Live Discord rejects illegal `shortName` chars at API level | D-11 | Cannot mock Discord's name-regex enforcement in unit/IT; only the real Discord API rejects | Optional — covered by existing UAT-03 Live-Discord smoke procedure (`docs/operations/discord-integration.md` § 4) and only triggers if operator manually edits a `Team.shortName` to contain illegal chars. Not a required Phase 100 acceptance gate. |
| Operator-facing flash message when 100-char overflow fires | D-10 | Operator UX — the flash text is observable only in browser | Trigger overflow via a long `SeasonPhaseGroup.name` on `/admin/seasons/{id}/phases/{phaseId}` form, then click create-channel button on `/admin/matches/{id}/edit`. Verify the flash message contains the produced name and length. Optional — automated unit test for the throw itself is the primary gate. |

---

## Validation Sign-Off

- [ ] All decisions D-01..D-14 mapped to at least one automated assertion OR scope-tagged as N/A
- [ ] Sampling continuity: no plan modifying production code lacks a follow-on test command
- [ ] Wave 0 closes the `DiscordChannelServiceNamingTest` gap before any IT-refresh plan wave
- [ ] No watch-mode flags (Maven only)
- [ ] Feedback latency < 90s on every per-task and per-wave gate
- [ ] `nyquist_compliant: true` set in frontmatter after planner derives task-level mapping

**Approval:** pending
