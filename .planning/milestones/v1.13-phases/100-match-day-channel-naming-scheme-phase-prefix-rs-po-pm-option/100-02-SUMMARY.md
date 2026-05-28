---
phase: 100
plan: 02
status: complete
self_check: PASSED
completed: 2026-05-26
commits:
  - c9400e00 -- test(100-02): refresh IT channel-name fixtures to new format + pin outbound name on happy-path
---

# Plan 100-02 — SUMMARY

## Self-Check: PASSED

- ✅ Task 1 — `DiscordRestClientIT.java`: 1× `md1-h-vs-a` → `md1-rs-h-vs-a`
- ✅ Task 2 — `DiscordChannelServicePermissionAuditFailIT.java`: 4× `md1-h-vs-a` → `md1-rs-h-vs-a`
- ✅ Task 3 — `DiscordChannelArchiveServiceWireMockIT.java`: 2× `md1-h-vs-a` → `md1-rs-h-vs-a`
- ✅ Task 4 — `DiscordChannelServiceCleanupFailIT.java`: 2× `md1-hc-vs-ac` → `md1-rs-hc-vs-ac`
- ✅ Task 5 — `DiscordChannelServiceWireMockIT.java`: 5× `md1-home-vs-away` → suffix-aware new format (2× `homeh-vs-awayh`, 2× `homeit-vs-awayit`, 1× `homewf-vs-awaywf`) + 1× outbound-name `matchingJsonPath("$.name", equalTo("md1-rs-homeh-vs-awayh"))` pinning assertion on happy-path test
- ✅ Task 6 — Cross-cutting grep gate: `grep -rE "md1-h-vs-a|md1-home-vs-away|md1-hc-vs-ac" src/test/java/org/ctc/discord/` returns 0
- ✅ All 5 ITs green: 16+3+4+1+4 = 28 IT test invocations
- ✅ 1843 surefire unit tests still green (no regressions)
- ✅ JaCoCo coverage gate met, SpotBugs 0 issues
- ✅ Zero src/main/ changes (test-only plan)

## Deliverables

| File | Change |
|------|--------|
| `src/test/java/org/ctc/discord/DiscordRestClientIT.java` | 1 literal swap |
| `src/test/java/org/ctc/discord/service/DiscordChannelServicePermissionAuditFailIT.java` | 4 literal swaps |
| `src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java` | 2 literal swaps |
| `src/test/java/org/ctc/discord/service/DiscordChannelServiceCleanupFailIT.java` | 2 literal swaps |
| `src/test/java/org/ctc/discord/service/DiscordChannelServiceWireMockIT.java` | 5 suffix-aware literal swaps + 1 new pinning assertion |

## Key Changes

- 14 stale stub-response literals refreshed to the new Phase-100 channel-name format.
- New outbound-request-body pinning on the happy-path test (`DiscordChannelServiceWireMockIT.givenValidMatchAndConfig…`): the production code must emit `md1-rs-homeh-vs-awayh` for the seedMatch("H") fixture — verified via `matchingJsonPath("$.name", equalTo(...))`. Closes the "WireMock is not Real-API Coverage" coverage gap.
- Zero changes outside `src/test/java/org/ctc/discord/**`.

## Decisions Honored

D-14 (5-file fixture refresh), D-11 (shortName verbatim — no test introduces illegal chars).

## Metrics

- Files modified: 5 test files
- LOC delta: +15 / −14 (mostly 1-for-1 swaps plus the new pinning line)
- Test runtime: WireMockIT 4.0 s, PermissionAuditFailIT 25.7 s, ArchiveIT 25.9 s, CleanupFailIT 3.6 s, RestClientIT 3.4 s
- Build gate: `./mvnw verify` (default-it profile only) — BUILD SUCCESS in ~5:30 min
