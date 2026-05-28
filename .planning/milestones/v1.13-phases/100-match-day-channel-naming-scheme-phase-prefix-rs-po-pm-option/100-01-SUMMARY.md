---
phase: 100
plan: 01
status: complete
self_check: PASSED
completed: 2026-05-26
commits:
  - 435a4a2a -- test(100-01): add DiscordChannelServiceNamingTest — pure-unit naming coverage
  - add3ef11 -- feat(100-01): extend channel naming scheme — phase prefix + optional group slug
---

# Plan 100-01 — SUMMARY

## Self-Check: PASSED

- ✅ `DiscordChannelServiceNamingTest` — 12/12 invocations green (3 phase-type cases + 6 group-slug cases + 3 single-tests)
- ✅ `channelName(Match)` widened from `private` to package-private (same-package test access)
- ✅ `phaseAbbrev(PhaseType)` exhaustive switch — REGULAR→rs / PLAYOFF→po / PLACEMENT→pm, no default branch (D-05)
- ✅ `groupSlug(SeasonPhaseGroup)` — NFD-decompose + strip combining marks + lowercase + `[^a-z0-9]→-` + collapse + trim (D-06)
- ✅ Empty-slug → no-group collapse (D-07) — verified by `givenMatchWithGroupNameThatSlugifiesToEmpty…`
- ✅ Final `.toLowerCase(Locale.ROOT)` applied to fully composed string (D-03)
- ✅ 100-char overflow throws `BusinessRuleException` with name + length in message (D-10)
- ✅ Zero comment pollution introduced (CLAUDE.md "No Comment Pollution")
- ✅ Zero `default ->` branches added (D-05)

## Deliverables

| Artifact | Path | Notes |
|----------|------|-------|
| Pure-unit test | `src/test/java/org/ctc/discord/service/DiscordChannelServiceNamingTest.java` | 5 methods, 12 invocations, bare-JUnit 5, no Spring context, untagged |
| Production change | `src/main/java/org/ctc/discord/service/DiscordChannelService.java` | New `channelName(Match)` body + 2 private static helpers + 3 new imports |

## Key Changes

- `channelName(Match)` access modifier: `private` → package-private (so the same-package test can call it directly per D-13).
- New private static helper `phaseAbbrev(PhaseType)` — exhaustive arrow-switch, compile-fail on future enum additions.
- New private static helper `groupSlug(SeasonPhaseGroup)` — 6-step NFD-decompose + slugify chain.
- Defensive 100-char `BusinessRuleException` guard fires after `.toLowerCase(Locale.ROOT)` so the message echoes the produced name as the operator sees it (D-10).
- 3 new imports: `java.text.Normalizer`, `org.ctc.domain.model.Matchday` (local var), `org.ctc.domain.model.PhaseType`, `org.ctc.domain.model.SeasonPhaseGroup`.

## Decisions Honored

D-01, D-02, D-03, D-04, D-05, D-06, D-07, D-10, D-11 (verbatim shortName), D-12 (helpers stay inside DiscordChannelService), D-13 (pure-unit class added).

## Out-of-Scope (Plan 100-02)

`DiscordChannelServiceWireMockIT`, `DiscordChannelServicePermissionAuditFailIT`, `DiscordChannelArchiveServiceWireMockIT`, `DiscordChannelServiceCleanupFailIT`, `DiscordRestClientIT` — stub-response literals still carry old format. Plan 100-02 refreshes all 14 occurrences + adds the outbound-name pinning assertion.

## Metrics

- Files modified: 2 (1 src, 1 test)
- LOC delta: +106 test, +38 src body (3-line removal + 41 new) = +144
- Targeted test runtime: 0.265 s
- Build gate: `./mvnw test -Dtest=DiscordChannelServiceNamingTest` BUILD SUCCESS
