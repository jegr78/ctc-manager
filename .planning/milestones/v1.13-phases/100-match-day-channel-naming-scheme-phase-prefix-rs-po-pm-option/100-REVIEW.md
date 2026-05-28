---
phase: 100-match-day-channel-naming-scheme-phase-prefix-rs-po-pm-option
reviewed: 2026-05-26T00:00:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - src/main/java/org/ctc/discord/service/DiscordChannelService.java
  - src/test/java/org/ctc/discord/DiscordRestClientIT.java
  - src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java
  - src/test/java/org/ctc/discord/service/DiscordChannelServiceCleanupFailIT.java
  - src/test/java/org/ctc/discord/service/DiscordChannelServiceNamingTest.java
  - src/test/java/org/ctc/discord/service/DiscordChannelServicePermissionAuditFailIT.java
  - src/test/java/org/ctc/discord/service/DiscordChannelServiceWireMockIT.java
findings:
  critical: 0
  warning: 2
  info: 6
  total: 8
status: issues_found
---

# Phase 100: Code Review Report

**Reviewed:** 2026-05-26
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

The Phase 100 change extends `DiscordChannelService.channelName(Match)` from the legacy `md{N}-{home}-vs-{away}` format to the new `md{N}-{phase}-[{group}-]{home}-vs-{away}` format and refreshes 5 IT fixtures. The production code is small, focused, and correct for the explicitly-tested cases. The 5 IT refreshes correctly mirror the new format for each `seedMatch` call, and the new `matchingJsonPath("$.name", equalTo(...))` pin in `DiscordChannelServiceWireMockIT` does add real value over the previous `urlPathEqualTo`-only assertion (it would catch a regression of the production format the way the WireMock-vs-real-API memory recommends).

No critical bugs or security issues. Two correctness warnings (one untested boundary, one redundant assertion duplication) and six info-level observations (test coverage gaps, minor robustness gaps, one defensive-validation gap on `getMatchday()`/`getPhase()` chains).

The "no comment pollution" rule is observed — no `// Phase 100`, `// Plan 100-XX`, or `// D-NN` markers appear in any of the 7 files. BDD `// given / when / then` markers and one multi-line WHY comment in `PermissionAuditFailIT` (explaining the noise-entry mechanism) are legitimate and stay.

## Warnings

### WR-01: 100-character boundary edge is asserted only on the failing side

**File:** `src/test/java/org/ctc/discord/service/DiscordChannelServiceNamingTest.java:73-82`
**Issue:** `givenGroupNameThatPushesOverHundredChars_…` verifies the throw-path at length 111, but there is no test asserting that a name of *exactly* 100 characters succeeds. The check is `name.length() > 100` (strictly greater than), so a 100-char name must pass — but if the operator slips a `>=` in later, no test catches it. Discord's documented limit is 100 (inclusive), so the boundary is load-bearing.
**Fix:** Add one more parameterized case (or a sibling `@Test`) constructed to land at exactly 100 characters and assert it returns successfully. Example:
```java
@Test
void givenNameLandsAtExactly100Chars_whenChannelName_thenAccepted() {
    // base "md1-rs-" (7) + group + "-" (1) + "ho-vs-aw" (8) = 100 → group = 84 chars
    String group = "a".repeat(84);
    Match match = buildMatch(PhaseType.REGULAR, group, 0, "ho", "aw");

    String name = DiscordChannelService.channelName(match);

    assertThat(name).hasSize(100);
}
```

### WR-02: `channelName(match)` NPEs on missing `getMatchday()`/`getPhase()`/`getHomeTeam()` when called outside `createMatchChannel`

**File:** `src/main/java/org/ctc/discord/service/DiscordChannelService.java:124-148`
**Issue:** `channelName` is package-private static and is now called directly from `DiscordChannelServiceNamingTest`. Inside the production call-graph, `assertPreconditions` runs first and protects against null teams — but `matchday`, `matchday.getPhase()`, and `matchday.getPhase().getPhaseType()` are **not** validated by `assertPreconditions`. If a refactor ever invokes `channelName(match)` from another caller (e.g. a future diagnostic/preview endpoint), a null chain link produces a `NullPointerException` rather than the `BusinessRuleException` this defensive layer is otherwise designed to throw. The project's "fail-fast `BusinessRuleException` for defensive validation" convention is partially satisfied at the entry point but bypassed here.
**Fix:** Either (a) keep the method strictly internal (mark it `private` and add a wrapper if a test must call it — currently visible only to the same package), or (b) add a brief guard at the top of `channelName`:
```java
static String channelName(Match match) {
    Matchday matchday = match.getMatchday();
    if (matchday == null || matchday.getPhase() == null
            || match.getHomeTeam() == null || match.getAwayTeam() == null) {
        throw new BusinessRuleException(
                "Channel name requires matchday with phase and both teams.");
    }
    // ...
}
```
Option (a) is preferred — it keeps the validation centralized in `assertPreconditions` and avoids dual-source-of-truth.

## Info

### IN-01: Switch on `PhaseType` has no `default` branch — exhaustiveness relies on enum stability

**File:** `src/main/java/org/ctc/discord/service/DiscordChannelService.java:150-156`
**Issue:** The switch expression is correctly exhaustive today (per the project's D-05 decision — javac enforces exhaustiveness for enum switch expressions in Java 25), but if a fourth `PhaseType` value is added in a future release and this file is not touched, the build will fail at compile time — that is the intended behavior. The risk is purely social: a future developer might add a default branch out of "defensive habit", silencing the compile-time signal. Worth a one-line WHY comment to lock in the intentional omission.
**Fix:** Optional — add a single-line WHY comment above the switch (allowed by the No-Comment-Pollution rule as a hidden-invariant marker):
```java
// No default branch: PhaseType is exhaustive — adding a new value must force a re-mapping here.
return switch (type) {
    case REGULAR -> "rs";
    case PLAYOFF -> "po";
    case PLACEMENT -> "pm";
};
```

### IN-02: `phaseAbbrev` × group coverage gap (PLAYOFF + group, PLACEMENT + group)

**File:** `src/test/java/org/ctc/discord/service/DiscordChannelServiceNamingTest.java:21-35,37-53`
**Issue:** `givenMatchWithPhaseTypeAndNoGroup_…` covers all 3 phase types without a group; `givenMatchWithGroupName_…` covers groups only on `REGULAR`. The combinatorial cases `PLAYOFF + group` and `PLACEMENT + group` are not asserted. Production-wise these are plausible — a PLAYOFF phase could legitimately have groups (e.g. "Winners' Bracket"). The slugify is shared so it's unlikely to regress per-phase, but the placement-after-phase-abbrev ordering is the load-bearing thing.
**Fix:** Add two `@CsvSource` rows to the second `@ParameterizedTest`, parametrize the `PhaseType` (currently hard-coded `REGULAR`), or add one targeted test like:
```java
@Test
void givenPlayoffPhaseWithGroup_whenChannelName_thenPhaseThenGroupOrder() {
    Match match = buildMatch(PhaseType.PLAYOFF, "Winners Bracket", 0, "alf", "bra");
    assertThat(DiscordChannelService.channelName(match))
            .isEqualTo("md1-po-winners-bracket-alf-vs-bra");
}
```

### IN-03: Multi-codepoint / ZWJ edge cases for `groupSlug` not exercised

**File:** `src/main/java/org/ctc/discord/service/DiscordChannelService.java:158-165` and `src/test/java/org/ctc/discord/service/DiscordChannelServiceNamingTest.java:37-53`
**Issue:** The slugify chain handles ASCII, Latin-1 supplement (`Ü→u`), and pure-punctuation cases correctly. Untested but plausible: ZWJ-joined emoji sequences (e.g. `U+1F468 + ZWJ + U+1F469 + ZWJ + U+1F466 (family emoji)`), ZWNJ (`U+200C`), German `ß` (does not decompose under NFD — stays as `ß`, becomes `-` via the `[^a-z0-9]` step → e.g. `Straße` → `stra-e`). Tracing the chain shows behavior is *deterministic*, but a regression test would document the contract.
**Fix:** Add one or two parameterized cases — at minimum:
```java
"Straße,stra-e",        // ß does not decompose → becomes -
"Group 42,group-42",     // digits preserved
"   spaced   ,spaced",   // leading/trailing whitespace stripped
```

### IN-04: `seedMatch(suffix)` in WireMock ITs uses lowercase-friendly suffixes — `Locale.ROOT` lowercase path not implicitly exercised

**File:** `src/test/java/org/ctc/discord/service/DiscordChannelServiceWireMockIT.java:102-112` and siblings
**Issue:** All IT fixtures pass already-lowercase suffixes (`"H"`, `"WF"`, `"IT"` → the suffix is *appended* to lowercase `"home"`/`"away"` literals so the resulting shortName is `"homeH"` etc.). The `toLowerCase(Locale.ROOT)` step then folds these to `"homeh"`. This works but the IT does not *purposefully* exercise a non-ASCII or Turkish-i shortname (where `Locale.getDefault()` vs `Locale.ROOT` would diverge — e.g. `"İ"` under `tr_TR` default). The unit test `givenMixedCaseTeamShortNames_…` covers ASCII mixed-case which is sufficient, but a single ROOT-vs-default-locale assertion would pin the convention. Low priority.
**Fix:** Optional unit case in `DiscordChannelServiceNamingTest`:
```java
@Test
void givenTurkishCapitalI_whenChannelName_thenUsesRootLowercaseNotTurkish() {
    Match match = buildMatch(PhaseType.REGULAR, null, 0, "İST", "bra");
    String name = DiscordChannelService.channelName(match);
    // Under Locale.ROOT, "İ".toLowerCase() → "i" + U+0307 (combining dot above) (i + combining dot above)
    // — still distinct from Locale.TURKISH, which would yield "i" alone.
    assertThat(name).startsWith("md1-rs-i");
}
```

### IN-05: `DiscordRestClientIT:206` stub uses `md1-rs-h-vs-a` but the test is **about** `fetchChannel`, not channel naming — coincidental coupling

**File:** `src/test/java/org/ctc/discord/DiscordRestClientIT.java:204-220`
**Issue:** This test (`givenChannelId_whenFetchChannel_thenReturnsChannelWithPermissionOverwrites`) only asserts on `id`, `permissionOverwrites.size()`, and ID extraction. The stub's `name` field value (`md1-rs-h-vs-a`) is never asserted. Refreshing this literal here is harmless but creates a phantom signal — a future grep for `md1-rs-` will pull this test up as a "naming test" when it is not. Consider using a sentinel name like `"any-name"` so the literal makes its irrelevance explicit.
**Fix:** Change to `"any-name"` or `"unused"`:
```java
.willReturn(okJson("{\"id\":\"c1\",\"name\":\"any-name\",\"type\":0,"
```

### IN-06: `groupSlug` repeats `replaceAll` chain — micro-optimization & readability

**File:** `src/main/java/org/ctc/discord/service/DiscordChannelService.java:158-165`
**Issue:** Five chained `replaceAll`/`toLowerCase` calls each compile their regex pattern at every invocation. For a feature called once per match-channel creation (low frequency), the cost is negligible — but the chain is also slightly hard to read. Per the project's "Spring-Native over JDK-Built-In" rule, JDK `Normalizer` + chained regex is acceptable here (no Spring equivalent), so this is purely a style/readability note. Pre-compiled `Pattern` constants would also signal the regexes are stable.
**Fix:** Optional — extract three pre-compiled patterns and use `Matcher.replaceAll`:
```java
private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}");
private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9]");
private static final Pattern MULTI_DASH = Pattern.compile("-{2,}");
private static final Pattern EDGE_DASH = Pattern.compile("^-|-$");
```
Skip if perceived as YAGNI.

---

## Verification Notes (positive observations)

- `@Tag("integration")` correctly applied to all 5 IT files; the new unit test `DiscordChannelServiceNamingTest` is correctly **untagged** per the project convention.
- All test method names follow Given-When-Then.
- `Locale.ROOT` is correctly placed on `toLowerCase` in both `channelName` (line 142) and `groupSlug` (line 161).
- The `matchingJsonPath("$.name", equalTo("md1-rs-homeh-vs-awayh"))` pin in `DiscordChannelServiceWireMockIT:149` is the exact pattern recommended by the project's "WireMock is not Real-API Coverage" memory — it pins the *production-emitted* request body, not just the URL.
- The `BusinessRuleException` message in `channelName` includes both the name and the length, which makes operator diagnostics direct: `"Discord channel name exceeds 100 characters: md1-rs-… (111)"`.
- The `switch (type)` is intentionally exhaustive with no default — Java 25 enforces this at compile time, and the project's D-05 decision documents the choice.
- No comment pollution (no `// Phase 100`, `// Plan-100-XX`, `// D-NN` markers).
- All `channelName` call sites verified via `grep` — only `createMatchChannel` (production) and the new unit test call it; no stragglers.

---

_Reviewed: 2026-05-26_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
