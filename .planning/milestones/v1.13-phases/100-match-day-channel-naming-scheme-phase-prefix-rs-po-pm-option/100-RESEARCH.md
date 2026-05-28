# Phase 100: Match Day Channel Naming Scheme — Research

**Researched:** 2026-05-26
**Domain:** Discord channel naming logic — `DiscordChannelService` + WireMock IT fixture refresh
**Confidence:** HIGH (all findings from live codebase, no assumed facts)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- D-01: Format `md{N}-{phase}-[{group}-]{home}-vs-{away}`, lowercase, dash-separated.
- D-02: Group token omitted entirely when `matchday.getGroup() == null`.
- D-03: Final `.toLowerCase(Locale.ROOT)` on fully composed string.
- D-04: Hardcoded `phaseAbbrev(PhaseType)` switch-expression in `DiscordChannelService`.
- D-05: No default branch — exhaustive sealed switch only.
- D-06: Full slugified `SeasonPhaseGroup.name` via NFD-decompose + strip combining marks.
- D-07: Empty slug after derivation → treat as `group == null` (omit token, don't throw).
- D-08: Existing channels left-as-is (no migration).
- D-09: Two-scheme coexistence is acceptable.
- D-10: `> 100` chars → throw `BusinessRuleException`, no silent truncate.
- D-11: No regex pre-validation of `shortName` — Discord API rejects directly if illegal.
- D-12: Keep logic inside `DiscordChannelService` (private static helpers).
- D-13: Indirect WireMock IT coverage preferred; optional pure-unit `DiscordChannelServiceNamingTest` at planner's discretion.
- D-14: All 8 old-format IT occurrences must be refreshed.

### Claude's Discretion
- Exact wording of `BusinessRuleException` message in D-10 (must include produced name and length).
- Whether to add `DiscordChannelServiceNamingTest` (pure-unit parameterized).
- Method extraction within `DiscordChannelService` vs package-private class (public API must not change).

### Deferred Ideas (OUT OF SCOPE)
- Operator-configurable phase abbreviations.
- `SeasonPhaseGroup.slug` field.
- Bulk-rename action for old-scheme channels.
- Diagnostic list of old-scheme channels.
- Archive-category naming.
- Forum-thread / announcement-channel naming.
- Discord channel-renaming on `Team.shortName` change.
</user_constraints>

---

## Summary

Phase 100 is a narrowly scoped refactor of one private static method in
`DiscordChannelService`. The production change is mechanically straightforward:
add two new tokens (phase abbreviation, optional group slug) to the existing
7-line `channelName(Match)` implementation. The bulk of the work is updating
WireMock IT fixture strings in 5 confirmed IT files (the CONTEXT.md named 8
files but 3 were overcounted — see §3 below for the exact count).

OSIV is enabled and `createMatchChannel` is `@Transactional`, so all lazy
associations (`matchday → phase → phaseType`, `matchday → group → name`) are
accessible inside the service method without any extra fetch strategy change.
The `MatchRepository.findById` used by the controller does NOT include an
`@EntityGraph` for `matchday.phase` or `matchday.group`, but OSIV keeps the
session open through the controller call, making lazy access safe.

A pre-existing `slug()` method in `DiscordPostService` (line 605) uses a
simpler regex-only approach (no NFD-decompose) and must NOT be reused — D-06
requires NFD-decompose for umlaut handling. The new `groupSlug()` helper
will be a distinct private static method.

**Primary recommendation:** Implement `phaseAbbrev(PhaseType)` and
`groupSlug(SeasonPhaseGroup)` as private static helpers in
`DiscordChannelService`, update `channelName(Match)` to compose all tokens,
add the 100-char guard, then refresh 5 IT files. Add the optional pure-unit
`DiscordChannelServiceNamingTest` — the WireMock-level coverage is indirect
and the naming logic has enough edge cases (empty slug, umlaut, 100-char cap)
to justify a fast, zero-Spring parameterized test.

---

## 1. Codebase Confirmation

### DiscordChannelService.channelName(Match) — Lines 120-127

**VERIFIED.** The method exists at lines 120-127 exactly as CONTEXT.md describes:

```java
private static String channelName(Match match) {
    int matchdayNumber = match.getMatchday().getSortIndex() + 1;
    return ("md" + matchdayNumber + "-"
            + match.getHomeTeam().getShortName()
            + "-vs-"
            + match.getAwayTeam().getShortName())
            .toLowerCase(Locale.ROOT);
}
```

`createMatchChannel` at line 52 is the sole production caller. `channelName` is called at line 59 — AFTER `assertPreconditions` (line 54). This ordering is preserved in the new implementation (no change needed to call order).

### assertPreconditions — Lines 107-118

**VERIFIED.** Checks `homeTeam`, `awayTeam`, their `effectiveDiscordRoleId`, and `currentMatchCategoryId`. Does NOT check `matchday.phase` or `matchday.group` — those are guaranteed NOT NULL / nullable per the domain model. No change needed.

### PhaseType enum

**VERIFIED.** Has exactly three values: `REGULAR`, `PLAYOFF`, `PLACEMENT`. No `BRACKET` value. `PhaseLayout` (separate enum) has `LEAGUE`, `GROUPS`, `BRACKET` — this is the layout, not the phase type. The D-04 exhaustive switch on `PhaseType` is correct and covers all cases.

### SeasonPhase.phaseType

**VERIFIED.** `@Column(nullable = false)`, `@Enumerated(EnumType.STRING)`. Accessing `match.getMatchday().getPhase().getPhaseType()` will never return null.

### Matchday.phase

**VERIFIED.** `@ManyToOne(fetch = FetchType.LAZY)`, `@JoinColumn(name = "phase_id", nullable = false)`. Lazy but NOT NULL in DB. Safe to traverse inside `@Transactional` context.

### Matchday.group

**VERIFIED.** `@ManyToOne(fetch = FetchType.LAZY)`, `@JoinColumn(name = "group_id")` — **no `nullable = false`**, confirming it is nullable. Java-side `null` check in `channelName` for the group token is required and correct per D-02.

### SeasonPhaseGroup.name

**VERIFIED.** `@NotBlank @Column(nullable = false)`. Form-layer validation ensures non-empty, but D-07's empty-slug guard is correct defense-in-depth.

### TestDataService lines 188-196

**VERIFIED.** Lines 188-199 of `TestDataService.java` create:
- `s1Regular` — `new SeasonPhase(s1, PhaseType.REGULAR, PhaseLayout.GROUPS, 0)`
- `s1GroupA` — `new SeasonPhaseGroup(s1Regular, "Group A", 0)`
- `s1GroupB` — `new SeasonPhaseGroup(s1Regular, "Group B", 1)`

These fixtures exist for demo/E2E data (the `TestDataService` is the production dev-mode seeder, not `TestHelper`). The ITs for this phase use `TestHelper`, not `TestDataService`.

### CONTEXT.md Drift: "8 test files" vs actual 5

**⚠ MINOR DISCREPANCY — not a CONTEXT FLAW.** CONTEXT.md §D-14 says "8 existing test files that hard-code old names" and the `<canonical_refs>` section lists only **6** files (5 confirmed + `DiscordAutoPostListenerIT` with a caveat). The grep confirms exactly **5 files** with old-format channel-name literals. `DiscordAutoPostListenerIT` does NOT contain old-format literals (see §3 below). The CONTEXT.md count of 8 is an overcounting error in the discussion session; the true count is 5 fixture files.

---

## 2. Domain Model Notes

### Lazy-Loading Path: `channelName(Match)`

`createMatchChannel` is annotated `@Transactional` (line 51). The call chain inside:

```
createMatchChannel(match)            — @Transactional session open
  → assertPreconditions(match, cfg)  — accesses match.homeTeam, match.awayTeam (already loaded)
  → channelName(match)               — NEW: accesses match.matchday.phase.phaseType
                                                        match.matchday.group (nullable)
```

`Match.matchday` is `FetchType.LAZY`. When `createMatchChannel` is called from
`MatchController.createDiscordChannel`, the controller calls
`matchService.findById(id)` first (which uses `matchRepository.findById` — standard
`JpaRepository`, no `@EntityGraph` for `matchday`), then passes the loaded entity
to `discordChannelService.createMatchChannel(match)`.

**Risk analysis:** OSIV is enabled (`spring.jpa.open-in-view=true`). The HTTP
request keeps a Hibernate session open from controller entry through view rendering.
`discordChannelService.createMatchChannel` is called within that session scope.
Accessing `match.getMatchday()` (lazy) → `phase` (lazy) → `phaseType` (basic
column, no further join) will trigger two additional lazy-load SQL queries. Both
are single-row lookups by FK. This is safe under OSIV.

**From the ITs:** All IT files use `@Transactional` on the test class and seed the
match via `TestHelper`, which does `matchdayRepository.save(new Matchday(regular, ...))`.
The `Matchday` object is constructed with the phase already set in-memory. Inside a
`@Transactional` IT, lazy access works fine.

**No `@EntityGraph` change needed.** OSIV + `@Transactional` service method cover
the lazy-load path.

### NULL / NOT NULL Summary

| Field chain | DB nullable | Java type | Implication |
|---|---|---|---|
| `match.matchday` | NOT NULL | `FetchType.LAZY` | Safe, never null |
| `matchday.phase` | NOT NULL | `FetchType.LAZY` | Safe, never null |
| `phase.phaseType` | NOT NULL | `PhaseType` enum | Safe, never null |
| `matchday.group` | **nullable** | `FetchType.LAZY` | Must null-check in Java |
| `group.name` | NOT NULL, `@NotBlank` | `String` | Safe when group non-null |

---

## 3. Existing Test-Fixture Audit

### Confirmed: 5 files with old-format literals (grep verified)

**File 1: `DiscordRestClientIT.java:206`**
- Old literal: `"md1-h-vs-a"` in WireMock response body for `fetchChannel("c1")`
- Context: Tests `DiscordRestClient.fetchChannel` directly (not `DiscordChannelService`). The literal is a mock `name` field in the response JSON — only the `id` and `permissionOverwrites` are asserted; the `name` field is not asserted by the test.
- **Impact assessment:** This literal is in a **response body** that the test does NOT assert the `name` field of. Technically this could stay as-is without breaking. However, D-14 mandates a refresh for consistency. Update to `md1-rs-h-vs-a` (LEAGUE layout, no group).

**File 2: `DiscordChannelServicePermissionAuditFailIT.java` — lines 111, 128, 154, 182**
- Old literal: `"md1-h-vs-a"` in WireMock stubs for channel create response (line 111) and `fetchChannel` response (lines 128, 154, 182).
- Context: Tests the audit-fail + cleanup-delete path. The test seeds a match via `helper.createMatchdayInRegularPhase(season, ...)` which creates a REGULAR-phase matchday with LEAGUE layout (TestHelper hardcodes `PhaseLayout.LEAGUE`, `sortIndex=0`). The channel name produced by the new `channelName()` will be `md1-rs-h{suffix}-vs-a{suffix}` (no group, LEAGUE layout → group token omitted).
- **Required change:** All 4 occurrences → `md1-rs-h<suffix>-vs-a<suffix>` (matching the test's shortNames).

**File 3: `DiscordChannelArchiveServiceWireMockIT.java` — lines 91, 113**
- Old literal: `"md1-h-vs-a"` in PATCH response body for archive move.
- Context: Tests archive move, not channel create. The `name` field in the PATCH response is NOT asserted by the test (it asserts `parent_id` and `discordChannelArchivedAt`). The match is seeded with `createMatchdayInRegularPhase` (LEAGUE, no group). Update: `md1-rs-h{suffix}-vs-a{suffix}`.
- **Note:** These literals appear in the stub response body only, not in the outbound request assertion. Still refresh per D-14.

**File 4: `DiscordChannelServiceCleanupFailIT.java` — lines 117, 125**
- Old literal: `"md1-hc-vs-ac"` in channel-create response (line 117) and `fetchChannel` response (line 125).
- Context: Tests audit-fail + DELETE 500 path. Match is seeded via `createMatchdayInRegularPhase` with teams shortNames `"hc"` and `"ac"`.
- **Required change:** `"md1-hc-vs-ac"` → `"md1-rs-hc-vs-ac"` (REGULAR, no group).

**File 5: `DiscordChannelServiceWireMockIT.java` — lines 123, 130, 182, 189, 219**
- Old literal: `"md1-home-vs-away"` in channel-create and `fetchChannel` response bodies.
- Context: Main happy-path WireMock IT. Matches seeded via `createMatchdayInRegularPhase` with shortNames `"home{suffix}"` / `"away{suffix}"`.
- **Required change:** `"md1-home-vs-away"` → `"md1-rs-home-vs-away"` etc. (3 distinct test method stubs, each with its own shortName pattern).

### DiscordAutoPostListenerIT — **NO old-format literals**

**VERIFIED. No change needed for old literals.** The `DiscordAutoPostListenerIT` does NOT contain `md1-h-vs-a`, `md1-home-vs-away`, or `md1-hc-vs-ac`. Its `stubChannelCreate` helper at line 161-179 uses `"name\":\"x\""` as a placeholder (not a real channel name). The test asserts `channelId` persistence and `DiscordPost` creation — the channel `name` field is irrelevant to what it verifies.

**However:** The IT calls `channelService.createMatchChannel(match)` (lines 200, 222, 243) where the match is seeded via `createMatchdayInRegularPhase` (LEAGUE layout, no group). After Phase 100, `channelName()` will produce `md1-rs-lst-h{suffix}-vs-lst-a{suffix}`. The WireMock stub for `POST /guilds/g-lst/channels` returns `"name\":\"x\""` — this is what Discord echoes back, not what the service sends. The service sends the computed name in the request body. The test does NOT assert the outbound request body for the channel name (unlike `DiscordChannelServiceWireMockIT`). Therefore `DiscordAutoPostListenerIT` will continue to pass without changes.

### Summary: Files requiring changes

| File | Lines | Old literal | New literal | Change type |
|---|---|---|---|---|
| `DiscordRestClientIT.java` | 206 | `md1-h-vs-a` | `md1-rs-h-vs-a` | Response JSON name field |
| `DiscordChannelServicePermissionAuditFailIT.java` | 111, 128, 154, 182 | `md1-h-vs-a` | `md1-rs-h<suffix>-vs-a<suffix>` | Response JSON name field |
| `DiscordChannelArchiveServiceWireMockIT.java` | 91, 113 | `md1-h-vs-a` | `md1-rs-h<suffix>-vs-a<suffix>` | Response JSON name field |
| `DiscordChannelServiceCleanupFailIT.java` | 117, 125 | `md1-hc-vs-ac` | `md1-rs-hc-vs-ac` | Response JSON name field |
| `DiscordChannelServiceWireMockIT.java` | 123, 130, 182, 189, 219 | `md1-home-vs-away` | `md1-rs-home-vs-away` (per method) | Response JSON name field |

**Total occurrences to update: 14** (not 8 as CONTEXT.md suggested — the exact per-line counts are listed above).

---

## 4. New Test Coverage Recommendation

### Recommendation: Add `DiscordChannelServiceNamingTest` (pure-unit)

**Rationale:** The WireMock IT coverage validates that `createMatchChannel` succeeds end-to-end with the new name format, but it tests one concrete scenario per test method. The naming logic has 6+ orthogonal cases (3 phase types × group present/absent), plus edge cases (empty slug, 100-char overflow, umlaut in group name). A parameterized pure-unit test covers these quickly (zero Spring context overhead, sub-100ms) and pins the exact output format as a regression fence.

### Proposed `DiscordChannelServiceNamingTest` structure

```java
@Tag // no tag — pure unit, runs in Surefire default fork
class DiscordChannelServiceNamingTest {

    // Parameters: sortIndex, phaseType, groupName(nullable), homeShortName, awayShortName, expectedName
    @ParameterizedTest
    @CsvSource({
        "2, REGULAR,  ,          alf, bra, md3-rs-alf-vs-bra",
        "2, REGULAR,  Group A,   alf, bra, md3-rs-group-a-alf-vs-bra",
        "2, REGULAR,  Group B,   alf, bra, md3-rs-group-b-alf-vs-bra",
        "0, PLAYOFF,  ,          alf, bra, md1-po-alf-vs-bra",
        "0, PLACEMENT,,          alf, bra, md1-pm-alf-vs-bra",
        "2, REGULAR,  Über-Liga, alf, bra, md3-rs-uber-liga-alf-vs-bra",
        "2, REGULAR,  !!!, alf, bra, md3-rs-alf-vs-bra",   // empty slug → omit token (D-07)
        "2, REGULAR,  Pro Division, alf, bra, md3-rs-pro-division-alf-vs-bra",
    })
    void whenChannelName_thenFormatsCorrectly(int sortIndex, PhaseType phaseType,
            String groupName, String homeShortName, String awayShortName, String expected) {
        // Construct mock domain objects (no Spring, no DB)
        ...
        assertThat(channelName(match)).isEqualTo(expected);
    }

    @Test
    void givenOverlongName_whenChannelName_thenThrowsBusinessRuleException() {
        // group name of 80 chars → produces > 100 char result
        ...
        assertThatThrownBy(() -> channelName(match))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("exceeds 100 characters");
    }
}
```

Because `channelName(Match)` is `private static`, this test will need either:
- `ReflectionTestUtils` (Spring test helper, but works without Spring context), or
- The method temporarily widened to package-private for test access (acceptable since class is not part of a public API).
- A thin package-private static method extracted specifically for testing.

**Recommended approach:** Make `channelName(Match)` package-private (remove `private` → default access). This is within `org.ctc.discord.service`, same package as the test. The method is `static` so no Spring context is needed to call it. This is the least invasive change.

### WireMock IT fixture refresh (5 files)

Each of the 5 files needs the old-format literal replaced with the new format. Since all test matches are seeded via `TestHelper.createMatchdayInRegularPhase` (which creates REGULAR-phase, LEAGUE-layout matchdays with no group), all refreshed literals follow the no-group format: `md1-rs-{home}-vs-{away}`.

The `DiscordChannelServiceWireMockIT` test `givenValidMatchAndConfig_whenCreateMatchChannel_thenDbWriteAnd4OverwritesIncludingBotMember` also asserts the outbound request body via `wm.verify(postRequestedFor(...).withRequestBody(matchingJsonPath(...)))` — but only for the permission overwrite structure, NOT for the channel `name` field. The channel name in the request body is not verified in any existing IT. Phase 100 does NOT need to add `name` assertion to the existing test methods unless D-13 is revisited (the CONTEXT says WireMock IT coverage is indirect — the name is exercised through successful channel creation).

---

## 5. Validation Architecture (Nyquist Dimension 8)

### Test Framework
| Property | Value |
|---|---|
| Framework | JUnit 5 + Mockito + WireMock 3.x |
| Unit config | Surefire (default, no `@Tag`) |
| IT config | Surefire (`@Tag("integration")`) |
| E2E config | Failsafe + `-Pe2e` (`@Tag("e2e")`) |
| Quick run (unit only) | `./mvnw test -Dtest=DiscordChannelServiceNamingTest` |
| Full run | `./mvnw clean verify -Pe2e` |

### Decision → Test Layer Map

| Decision | Behavior | Test Layer | Automated Command | Coverage Status |
|---|---|---|---|---|
| D-01 | `md{N}-{phase}-{home}-vs-{away}` basic format | Unit | `DiscordChannelServiceNamingTest` | ❌ Wave 0 gap |
| D-02 | Group token omitted when group == null | Unit | `DiscordChannelServiceNamingTest` | ❌ Wave 0 gap |
| D-03 | `toLowerCase(Locale.ROOT)` applied | Unit | `DiscordChannelServiceNamingTest` | ❌ Wave 0 gap |
| D-04 | `phaseAbbrev` switch: `REGULAR→rs`, `PLAYOFF→po`, `PLACEMENT→pm` | Unit | `DiscordChannelServiceNamingTest` | ❌ Wave 0 gap |
| D-05 | No default branch (compiler-enforced) | Compile | `./mvnw compile` | Compiler gate |
| D-06 | NFD-decompose + slugify group name | Unit | `DiscordChannelServiceNamingTest` | ❌ Wave 0 gap |
| D-07 | Empty slug after derivation → omit token | Unit | `DiscordChannelServiceNamingTest` | ❌ Wave 0 gap |
| D-08 | No migration of existing channels | Scope | — (no production code path) | N/A |
| D-09 | Two-scheme coexistence | Scope | — (no production code path) | N/A |
| D-10 | >100 chars → `BusinessRuleException` | Unit + IT | `DiscordChannelServiceNamingTest` + one IT scenario | ❌ Wave 0 gap |
| D-11 | `shortName` illegal chars deferred to Discord API | IT (existing) | `DiscordChannelServiceWireMockIT` | ✅ Existing |
| D-12 | Private static helpers in `DiscordChannelService` | Code review | — | By implementation |
| D-13 | WireMock IT stubs updated | IT | `./mvnw test -Dtest=DiscordChannel*IT` | ❌ Fails until fixture refresh |
| D-14 | All 5 old-literal IT files updated | IT | `./mvnw test -Dtest=DiscordChannel*IT,DiscordRest*IT,DiscordChannelArchive*IT` | ❌ Fails until fixture refresh |

### Sampling Rate
- **Per task commit (TDD-red/green):** `./mvnw test -Dtest=DiscordChannelServiceNamingTest`  (unit), then `-Dit.test=DiscordChannelServiceWireMockIT -DfailIfNoTests=false` (IT)
- **Per wave merge:** `./mvnw test -Dtest=DiscordChannel*IT,DiscordRest*IT`
- **Phase gate:** `./mvnw clean verify -Pe2e` (full suite + Playwright)

### Wave 0 Gaps
- [ ] `src/test/java/org/ctc/discord/service/DiscordChannelServiceNamingTest.java` — covers D-01 through D-07, D-10

No new test framework installation needed — JUnit 5 + AssertJ already present.

---

## 6. Implementation Sketch

### `channelName(Match)` — new body (pseudocode)

```java
// package-private for testability (was private)
static String channelName(Match match) {
    int n = match.getMatchday().getSortIndex() + 1;
    String phase = phaseAbbrev(match.getMatchday().getPhase().getPhaseType());

    SeasonPhaseGroup group = match.getMatchday().getGroup();
    String groupToken = "";
    if (group != null) {
        String slug = groupSlug(group);
        if (!slug.isEmpty()) {
            groupToken = slug + "-";
        }
        // else: D-07 defense-in-depth — treat as no group
    }

    String name = ("md" + n + "-" + phase + "-" + groupToken
            + match.getHomeTeam().getShortName()
            + "-vs-"
            + match.getAwayTeam().getShortName())
            .toLowerCase(Locale.ROOT);

    if (name.length() > 100) {
        throw new BusinessRuleException(
                "Discord channel name exceeds 100 characters: " + name + " (" + name.length() + ")");
    }
    return name;
}

private static String phaseAbbrev(PhaseType type) {
    return switch (type) {
        case REGULAR   -> "rs";
        case PLAYOFF   -> "po";
        case PLACEMENT -> "pm";
    };
}

private static String groupSlug(SeasonPhaseGroup group) {
    String normalized = java.text.Normalizer.normalize(group.getName(), java.text.Normalizer.Form.NFD);
    String stripped = normalized.replaceAll("\\p{M}", "");
    String lowered = stripped.toLowerCase(Locale.ROOT);
    String dashed = lowered.replaceAll("[^a-z0-9]", "-");
    String collapsed = dashed.replaceAll("-{2,}", "-");
    return collapsed.replaceAll("^-|-$", "");
}
```

**Notes on ordering:**
1. `phaseAbbrev` and `groupSlug` are called BEFORE the 100-char guard — the guard applies to the fully composed string.
2. `toLowerCase(Locale.ROOT)` is applied to the fully composed string at the end (D-03). This means `phaseAbbrev` can return uppercase `"RS"` and it will still be lowercased, but the switch returns lowercase directly for clarity.
3. The 100-char guard is at the END, after `.toLowerCase(Locale.ROOT)`, because lowercase cannot make a string longer.
4. `groupToken` already includes the trailing `-` separator, making the composition `"md{n}-{phase}-{groupToken}{home}-vs-{away}"` — when `groupToken=""` this correctly collapses to the no-group format.

**Existing analogous `slug()` in `DiscordPostService` (line 605):**
```java
private static String slug(String label) {
    return label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
}
```
This method does NOT use NFD-decompose and handles umlauts differently (`ü` → `-`). It cannot be reused for `groupSlug` per D-06. The two methods can coexist independently.

**`SiteSlugger.slugify` (Spring `@Component`):**
Uses explicit character substitutions for German umlauts (`ü→ue` etc.) — different approach from D-06's NFD path. Cannot be injected into a `private static` method. Do not use.

---

## 7. Landmines & Risks

### Landmine 1: `assertPreconditions` ordering vs `channelName`

**Status: SAFE.** `assertPreconditions` is called at line 54, before `channelName` at line 59. The new `channelName` adds lazy-loads of `matchday.phase` and `matchday.group`, which happen inside `@Transactional`. No ordering change required. The 100-char guard throws `BusinessRuleException` which is caught by the controller's `catch (BusinessRuleException e)` block (line 204 of `MatchController`) — same handler that catches `assertPreconditions` violations.

### Landmine 2: `Locale.ROOT` vs `Locale.GERMAN` umlaut behavior in `toLowerCase`

**Status: IMPORTANT.** D-03 specifies `toLowerCase(Locale.ROOT)`, which is the existing behavior. The new `groupSlug` helper must also use `Locale.ROOT` when lowercasing. `Locale.ROOT` is correct for Discord channel names because it avoids locale-specific case mappings (e.g., Turkish `i/İ`). The NFD-decompose step strips the combining marks before lowercase, so `"Über"` → NFD → `"Über"` → strip `̈` → `"Uber"` → lowercase → `"uber"`. This is correct.

**Trap:** If `groupSlug` applies `.toLowerCase(Locale.ROOT)` BEFORE stripping combining marks, the `ü` → NFD → `ü` → lowercase → `ü` still works (combining mark on `u` not affected by lowercase). Lowercase before or after stripping combining marks produces the same result — but applying NFD-normalize before lowercase is clearer.

### Landmine 3: TestHelper creates REGULAR / LEAGUE phases — no group

**Status: KNOWN IMPLICATION.** Every existing IT that calls `helper.createMatchdayInRegularPhase` produces a matchday with a REGULAR phase (LEAGUE layout, no group). This means:
- All refreshed IT fixture strings follow the **no-group format** `md1-rs-{home}-vs-{away}`.
- No existing IT tests the group-present code path at the WireMock level.
- `DiscordChannelServiceNamingTest` must cover the group-present cases explicitly (or a new IT scenario needs to seed a GROUPS-layout matchday with a group).

**Recommendation:** Cover group-present with the pure-unit `DiscordChannelServiceNamingTest`. No WireMock IT for group-present is required by D-13 unless the planner judges the unit test insufficient.

### Landmine 4: `DiscordAutoPostListenerIT` — transaction isolation (NOT @Transactional)

**Status: NOTE.** Unlike the other ITs, `DiscordAutoPostListenerIT` is NOT annotated `@Transactional` at the class level — it uses `@AfterEach` to clean up. After Phase 100, `channelName` will be called inside the `createMatchChannel @Transactional` method. The `matchday.phase` lazy load will trigger a new DB fetch inside that transaction. Since the entity was persisted in `setUp`, this is safe.

### Landmine 5: `DiscordRestClientIT.java:206` — the `name` field is NOT asserted

**Status: INFO.** The literal at line 206 is in a `fetchChannel` response stub — the test asserts `ch.id()` and `ch.permissionOverwrites()` size, NOT `ch.name()`. Updating it is purely cosmetic (D-14 consistency), not a functional test change. The test will not break regardless of the value in this field.

### Landmine 6: `PhaseLayout.BRACKET` exists — does it affect `channelName`?

**Status: SAFE.** `BRACKET` is a `PhaseLayout` value (layout of matchdays within a phase), not a `PhaseType`. A PLAYOFF phase uses `PhaseLayout.BRACKET`. The `phaseAbbrev` switch operates on `PhaseType` (which has only 3 values: `REGULAR`, `PLAYOFF`, `PLACEMENT`). A `PhaseType.PLAYOFF` with `PhaseLayout.BRACKET` will correctly map to `"po"`. No issue.

### Landmine 7: `SeasonPhaseGroup` lazy-load inside `channelName` without separate `@EntityGraph`

**Status: SAFE under OSIV.** `Matchday.group` is `FetchType.LAZY`. When accessed inside `channelName(Match)` (called from within `createMatchChannel @Transactional`), Hibernate can load it lazily. OSIV is enabled. The controller flow is:
```
HTTP request → MatchController → matchService.findById(id) → discordChannelService.createMatchChannel(match)
```
The Hibernate session is open for the entire HTTP request. The lazy load of `group` triggers a SQL `SELECT FROM season_phase_groups WHERE id = ?`, which is fine.

**If someone ever calls `channelName(match)` outside a web request or transaction** (e.g., in a batch job, background thread, or `@AfterEach` cleanup), it would throw `LazyInitializationException`. This is a theoretical risk for future callers — acceptable per D-12 (only one caller exists now).

---

## 8. Open Questions for Planner

### Q1: `DiscordChannelServiceNamingTest` — add it or not?

CONTEXT.md D-13 says "planner's call." Research recommendation: **add it.** The naming method has multiple code paths (3 phase types, group present/absent, empty-slug guard, 100-char guard, umlaut handling) that are better validated by a fast, isolated unit test than inferred from WireMock stubs that only exercise the no-group REGULAR case. The implementation cost is one file, ~30 test cases, ~100ms runtime.

If the planner decides NOT to add it, the WireMock ITs must be updated to include at least one group-present test case to achieve D-01/D-02/D-06 coverage at the IT level.

### Q2: Commit strategy — one commit or atomic per file?

CONTEXT.md does not specify. The inline-sequential discipline from CLAUDE.md does not prescribe commit granularity for mechanical fixture refreshes. Options:
- **Option A (recommended):** Two commits: (1) production `DiscordChannelService` change + optional `DiscordChannelServiceNamingTest`, (2) all 5 IT fixture refreshes. This makes the production change reviewable in isolation.
- **Option B:** Single commit with all changes. Simpler, but harder to review.
- **Option C:** One commit per IT file (5 commits). Granular but noisy for a mechanical find-replace.

### Q3: `channelName` access modifier — `private` → package-private?

If `DiscordChannelServiceNamingTest` is added, the method needs to be testable. The planner must decide: package-private (no modifier) vs staying `private` and using `ReflectionTestUtils`. Package-private is cleaner for a static method. `DiscordChannelService` is in `org.ctc.discord.service`; the test would live in the same package under `src/test/java`.

### Q4: WireMock IT body assertions on channel name — add explicit `name` assertions?

Currently, no existing IT asserts the channel `name` in the outbound `POST /guilds/{guildId}/channels` request body. CLAUDE.md ("WireMock is not Real-API Coverage") requires tests that pin the production format. An explicit `withRequestBody(matchingJsonPath("$.name", equalTo("md1-rs-home-vs-away")))` assertion in `DiscordChannelServiceWireMockIT.givenValidMatchAndConfig_whenCreateMatchChannel_thenDbWriteAnd4OverwritesIncludingBotMember` would pin the actual outbound channel name.

**Recommendation:** Add this assertion in the happy-path IT test method during the fixture refresh — it is a one-liner and prevents silent name regressions. This is a "WireMock is not Real-API Coverage" enforcement.

---

## Architecture Patterns

No new architectural tier is introduced. `channelName(Match)` remains a private static pure function inside `DiscordChannelService`. The pattern follows the existing `assertPreconditions` (private static guard) style.

### Existing `slug()` analogue in `DiscordPostService`

```java
// DiscordPostService.java:605 — simpler slug, no NFD, not reusable for D-06
private static String slug(String label) {
    return label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
}
```

The new `groupSlug()` is more sophisticated (NFD-decompose). Both methods can coexist without collision.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---|---|---|
| Unicode NFD decompose | Custom char-by-char iteration | `java.text.Normalizer.normalize(name, Form.NFD)` (JDK built-in) |
| Combining mark regex | Manual Unicode table | `\p{M}` (POSIX Unicode category) |
| Locale-safe lowercase | String.toLowerCase() | `.toLowerCase(Locale.ROOT)` |

---

## Environment Availability

No external dependencies for this phase. The phase is pure Java source change + test fixture update. No CLI tools, databases beyond H2, or external services beyond the existing WireMock + Discord API test setup.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|---|---|---|
| — | — | — | — |

**All claims in this research are VERIFIED from live codebase at HEAD of `gsd/v1.13-discord-integration`.** No assumed facts. All file/line references were read directly.

---

## Sources

### Primary (HIGH confidence — live codebase reads)
- `src/main/java/org/ctc/discord/service/DiscordChannelService.java` — verified lines 51, 54, 59, 107-118, 120-127
- `src/main/java/org/ctc/domain/model/Matchday.java` — fetch strategy, nullability of `phase` and `group`
- `src/main/java/org/ctc/domain/model/SeasonPhase.java` — `phaseType` nullability
- `src/main/java/org/ctc/domain/model/SeasonPhaseGroup.java` — `name` nullability
- `src/main/java/org/ctc/domain/model/PhaseType.java` — enum values (exactly 3)
- `src/main/java/org/ctc/domain/model/Match.java` — `matchday` fetch strategy
- `src/main/java/org/ctc/admin/TestDataService.java:188-199` — `s1Regular`, `s1GroupA`, `s1GroupB` fixtures
- `src/test/java/org/ctc/TestHelper.java` — `createMatchdayInRegularPhase` creates REGULAR/LEAGUE/no-group
- `src/test/java/org/ctc/discord/DiscordRestClientIT.java:206` — literal verified
- `src/test/java/org/ctc/discord/service/DiscordChannelServicePermissionAuditFailIT.java:111,128,154,182` — literals verified
- `src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java:91,113` — literals verified
- `src/test/java/org/ctc/discord/service/DiscordChannelServiceCleanupFailIT.java:117,125` — literals verified
- `src/test/java/org/ctc/discord/service/DiscordChannelServiceWireMockIT.java:123,130,182,189,219` — literals verified
- `src/test/java/org/ctc/discord/service/DiscordAutoPostListenerIT.java` — no old literals, no outbound name assertion
- `src/main/java/org/ctc/discord/service/DiscordPostService.java:605` — existing `slug()` method (NOT reusable per D-06)
- `src/main/java/org/ctc/sitegen/SiteSlugger.java` — Spring component slugifier (NOT injectable into static method)
- `src/main/java/org/ctc/domain/repository/MatchRepository.java` — no `@EntityGraph` for `matchday.phase` or `group`
- `src/main/java/org/ctc/admin/controller/MatchController.java:191-211` — single production caller, `BusinessRuleException` handler confirmed

---

## RESEARCH COMPLETE

**Phase:** 100 — Match Day Channel Naming Scheme
**Confidence:** HIGH

### Key Findings
- The `channelName(Match)` method at lines 120-127 is exactly as CONTEXT.md describes — single change point confirmed.
- CONTEXT.md overcounts "8 files" — grep confirms **5 files** with old-format literals; `DiscordAutoPostListenerIT` has NO old literals and needs NO update.
- All lazy associations (`matchday.phase`, `matchday.group`) are safe under `@Transactional` + OSIV — no `@EntityGraph` change needed.
- A pre-existing `slug()` method in `DiscordPostService` exists but differs from D-06's NFD approach and must NOT be reused.
- `PhaseType` has exactly 3 values (REGULAR, PLAYOFF, PLACEMENT); the exhaustive switch-expression in D-04 is complete. `BRACKET` is a `PhaseLayout` value, not a `PhaseType`.

### File Created
`.planning/phases/100-match-day-channel-naming-scheme-phase-prefix-rs-po-pm-option/100-RESEARCH.md`

### Confidence Assessment
| Area | Level | Reason |
|---|---|---|
| Codebase verification | HIGH | All files read directly from HEAD |
| Domain model / OSIV | HIGH | Entity annotations read, `@Transactional` scope confirmed |
| Test fixture scope | HIGH | grep verified, all 5 files + DiscordAutoPostListenerIT read in full |

### Ready for Planning
Research complete. Planner has verified file/line references, fixture scope (5 files, 14 literal occurrences), OSIV safety, implementation structure, and test coverage gaps (Wave 0: `DiscordChannelServiceNamingTest`).
