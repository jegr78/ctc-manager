# Phase 100: Match Day Channel Naming Scheme — Pattern Map

**Mapped:** 2026-05-26
**Files analyzed:** 7 (1 new + 1 modified production + 5 modified ITs)
**Analogs found:** 7 / 7

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/test/java/org/ctc/discord/service/DiscordChannelServiceNamingTest.java` | test (pure-unit) | transform | `src/test/java/org/ctc/discord/service/DiscordPostServiceWebhookUrlPatternTest.java` | exact |
| `src/main/java/org/ctc/discord/service/DiscordChannelService.java` (channelName + helpers) | service (private static helpers) | transform | `src/main/java/org/ctc/discord/service/DiscordPostService.java` (slug + switch) | role-match |
| `src/test/java/org/ctc/discord/service/DiscordChannelServiceWireMockIT.java` | test (IT, WireMock) | request-response | self — fixture literal refresh only | self |
| `src/test/java/org/ctc/discord/service/DiscordChannelServicePermissionAuditFailIT.java` | test (IT, WireMock) | request-response | self — fixture literal refresh only | self |
| `src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java` | test (IT, WireMock) | request-response | self — fixture literal refresh only | self |
| `src/test/java/org/ctc/discord/service/DiscordChannelServiceCleanupFailIT.java` | test (IT, WireMock) | request-response | self — fixture literal refresh only | self |
| `src/test/java/org/ctc/discord/DiscordRestClientIT.java` | test (IT, WireMock) | request-response | self — fixture literal refresh only | self |

---

## Pattern Assignments

### `src/test/java/org/ctc/discord/service/DiscordChannelServiceNamingTest.java` (test, transform)

**Analog:** `src/test/java/org/ctc/discord/service/DiscordPostServiceWebhookUrlPatternTest.java`

This file tests a package-private static method directly (no Spring context, no WireMock). The analog is in the same package `org.ctc.discord.service` under `src/test/java` and calls `DiscordPostService.parseWebhookUrl(...)` directly — same access pattern as calling `DiscordChannelService.channelName(...)` after widening to package-private.

**Secondary analog for @CsvSource structure:** `src/test/java/org/ctc/discord/web/DiscordConfigControllerErrorCategoryTest.java` — the only `@ParameterizedTest` + `@CsvSource` in the discord package (no Spring context, bare JUnit 5).

**Imports pattern** (from `DiscordPostServiceWebhookUrlPatternTest.java` lines 1-8 + `DiscordConfigControllerErrorCategoryTest.java` lines 1-8):
```java
package org.ctc.discord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
```

**No class-level annotations** — pure unit tests in this codebase carry no `@SpringBootTest`, no `@ActiveProfiles`, no `@Tag` (see CLAUDE.md "Tag Tests by Category": plain unit tests stay untagged). Compare `DiscordPostServiceWebhookUrlPatternTest.java` line 8: `class DiscordPostServiceWebhookUrlPatternTest {` — bare class declaration.

**Core @CsvSource pattern** (from `DiscordConfigControllerErrorCategoryTest.java` lines 11-21):
```java
@ParameterizedTest
@CsvSource({
    "TRANSIENT,transient",
    "AUTH,auth",
    "NOT_FOUND,not-found",
    "CATEGORY_FULL,category-full"
})
void givenCategoryEnum_whenLowercaseAndHyphenated_thenMatchesBemClassSuffix(Category category, String expected) {
    String actual = category.name().toLowerCase().replace('_', '-');
    assertThat(actual).isEqualTo(expected);
}
```

**Exception test pattern** (from `DiscordPostServiceWebhookUrlPatternTest.java` lines 56-61):
```java
@Test
void givenGarbageUrl_whenParse_thenThrows() {
    assertThatThrownBy(() -> DiscordPostService.parseWebhookUrl(
            "https://example.com/not-a-webhook"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not match expected shape");
}
```
For Phase 100, the exception test calls `DiscordChannelService.channelName(match)` and asserts `BusinessRuleException` with `hasMessageContaining("exceeds 100 characters")`.

**Domain object construction pattern** (from `DiscordPostServiceMatchdayPairingsPreFlightTest.java` lines 68-92):
```java
private static Matchday validMatchday() {
    Matchday md = new Matchday();
    md.setId(UUID.randomUUID());
    ...
    return md;
}

private static Team team(String shortName) {
    Team t = new Team();
    t.setId(UUID.randomUUID());
    t.setShortName(shortName);
    ...
    return t;
}
```
The naming test must construct `Match`, `Matchday`, `SeasonPhase`, `SeasonPhaseGroup`, and `Team` instances in-memory without Spring. This is the established pattern: use no-arg constructors + setters, no Spring context. Note `SeasonPhaseGroup` constructor signature: `SeasonPhaseGroup(SeasonPhase phase, String name, int sortIndex)` (line 34 of `SeasonPhaseGroup.java`). `Matchday` constructor: `Matchday(SeasonPhase phase, String label, int sortIndex)` (line 53 of `Matchday.java`).

**Test naming convention** (Given-When-Then, from `DiscordPostServiceWebhookUrlPatternTest.java`):
- Method names: `givenContext_whenAction_thenExpectedResult()`
- Simple cases without preconditions: `whenAction_thenResult()` is allowed
- Body structure: `// given` / `// when` / `// then` comments

**Access modifier decision:** `channelName(Match)` must be widened from `private` to package-private (no modifier) so the test in `org.ctc.discord.service` can call it directly. `DiscordPostServiceWebhookUrlPatternTest.java` shows this exact pattern — it accesses `DiscordPostService.parseWebhookUrl(...)` which is declared `static` with package/wider visibility.

---

### `src/main/java/org/ctc/discord/service/DiscordChannelService.java` (service, transform)

**Analog:** `src/main/java/org/ctc/discord/service/DiscordPostService.java` (lines 605-607 for slug shape; lines 33-39 in `DiscordApiExceptionMapper.java` for exhaustive switch-expression; lines 107-118 of `DiscordChannelService.java` itself for `assertPreconditions` guard pattern)

**Current channelName implementation** (lines 120-127 — the method to replace):
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

**Existing assertPreconditions guard pattern** (lines 107-118 — D-10 100-char guard follows this shape):
```java
private static void assertPreconditions(Match match, DiscordGlobalConfig cfg) {
    boolean missing = match.getHomeTeam() == null
            || ...
            || cfg.getCurrentMatchCategoryId().isBlank();
    if (missing) {
        throw new BusinessRuleException(
                "Channel creation requires both team Discord roles and a current match category.");
    }
}
```
The D-10 guard is inline at the bottom of `channelName` rather than extracted, because it operates on the already-computed string length.

**Exhaustive switch-expression pattern** (from `DiscordApiExceptionMapper.java` lines 33-39):
```java
return switch (status) {
    case 401 -> new DiscordAuthException(AUTH_MESSAGE, e);
    case 403 -> from403(e);
    case 404 -> new DiscordNotFoundException(NOT_FOUND_MESSAGE, e);
    case 400 -> from400(e);
    default -> new DiscordTransientException(TRANSIENT_MESSAGE, e);
};
```
For `phaseAbbrev(PhaseType)`, use the same arrow-switch form but **no `default` branch** (D-05 — exhaustive sealed enum, compiler-enforced):
```java
private static String phaseAbbrev(PhaseType type) {
    return switch (type) {
        case REGULAR   -> "rs";
        case PLAYOFF   -> "po";
        case PLACEMENT -> "pm";
    };
}
```

**Slug method shape** (from `DiscordPostService.java` line 605-607 — structural analog, NOT reusable per D-06):
```java
private static String slug(String label) {
    return label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
}
```
The new `groupSlug(SeasonPhaseGroup)` uses the same placement (private static at bottom of class) and same return-via-chain style, but adds NFD-decompose step before lowercasing:
```java
private static String groupSlug(SeasonPhaseGroup group) {
    String normalized = java.text.Normalizer.normalize(group.getName(), java.text.Normalizer.Form.NFD);
    String stripped = normalized.replaceAll("\\p{M}", "");
    String lowered = stripped.toLowerCase(Locale.ROOT);
    String dashed = lowered.replaceAll("[^a-z0-9]", "-");
    String collapsed = dashed.replaceAll("-{2,}", "-");
    return collapsed.replaceAll("^-|-$", "");
}
```

**Import additions needed for `DiscordChannelService.java`:** No new imports for `BusinessRuleException` (already imported at line 30), `Locale` (line 14), `Match` (line 31). New import needed: `SeasonPhaseGroup` (`org.ctc.domain.model.SeasonPhaseGroup`) and `PhaseType` (`org.ctc.domain.model.PhaseType`). Follow existing import block style (lines 1-36): static imports first, then `java.*`, then `lombok.*`, then `org.ctc.*` alphabetically.

---

## Shared Patterns

### WireMock IT Structure (applies to all 5 modified IT files)

**Source:** `src/test/java/org/ctc/discord/service/DiscordChannelServiceWireMockIT.java`

All 5 IT files follow this structure and NONE need structural changes — only JSON string literal values change:

```java
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class DiscordChannelServiceXxxIT {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideDiscordConfig(DynamicPropertyRegistry registry) {
        registry.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
        ...
    }
}
```

**Match seeding pattern** (consistent across all 5 ITs — uses `TestHelper.createMatchdayInRegularPhase`):
```java
Matchday md = helper.createMatchdayInRegularPhase(season, "MD-1-" + suffix, 0);
```
`createMatchdayInRegularPhase` (TestHelper.java lines 61-66) always creates `PhaseType.REGULAR` + `PhaseLayout.LEAGUE` + `group=null`. This means all refreshed IT fixtures use the **no-group format**: `md1-rs-{home}-vs-{away}`.

### BusinessRuleException pattern (applies to D-10 guard in channelName)

**Source:** `src/main/java/org/ctc/discord/service/DiscordChannelService.java` lines 114-117

```java
throw new BusinessRuleException(
        "Channel creation requires both team Discord roles and a current match category.");
```

The D-10 message format (planner's call per CONTEXT D-10): `"Discord channel name exceeds 100 characters: " + name + " (" + name.length() + ")"`.

---

## IT Fixture Literal Substitution Map

Each IT file requires only JSON string value changes — no structural changes, no new stubs, no new assertions (except one recommended addition — see Q4 in RESEARCH.md §8).

### `src/test/java/org/ctc/discord/DiscordRestClientIT.java`

| Line | Current value | New value | What changes |
|---|---|---|---|
| 206 | `"md1-h-vs-a"` | `"md1-rs-h-vs-a"` | Response JSON `name` field only |

Context: `givenChannelId_whenFetchChannel_thenReturnsChannelWithPermissionOverwrites()`. The test asserts `ch.id()` and `ch.permissionOverwrites()` size/ids — NOT `ch.name()`. This is a consistency-only change (D-14); the test passes regardless.

### `src/test/java/org/ctc/discord/service/DiscordChannelServicePermissionAuditFailIT.java`

Match fixture: `seedMatch(suffix)` → shortNames `"h" + suffix` and `"a" + suffix`, REGULAR/LEAGUE, no group.

| Line | Method | Current value | New value |
|---|---|---|---|
| 111 | `stubHappyPathCreate()` — `POST /guilds/g1/channels` response | `"md1-h-vs-a"` | `"md1-rs-h-vs-a"` (suffix="" for this stub — channel name in response JSON) |
| 128 | `givenFetchChannelReturnsFiveOverwrites...` — `GET /channels/c1` response | `"md1-h-vs-a"` | `"md1-rs-h-vs-a"` |
| 154 | `givenFetchChannelReturnsFourOverwritesWithWrongRoleSet...` — `GET /channels/c1` response | `"md1-h-vs-a"` | `"md1-rs-h-vs-a"` |
| 182 | `givenFetchChannelReturnsFourRoleOverwritesNoBotMember...` — `GET /channels/c1` response | `"md1-h-vs-a"` | `"md1-rs-h-vs-a"` |

Note: `stubHappyPathCreate()` at line 111 uses a single literal without suffix (the channel name echoed back by Discord). The test method suffixes (`"E"`, `"S"`, `"M"`) flow into `seedMatch(suffix)` → team shortNames (`"hE"/"aE"`, `"hS"/"aS"`, `"hM"/"aM"`). The stub at line 111 is a shared helper used by all 3 test methods — its channel name is `"md1-h-vs-a"` (no suffix), which should become `"md1-rs-h-vs-a"`. The `fetchChannel` stubs at lines 128, 154, 182 are method-local; their name field is also suffix-free in the current code.

Wait — re-reading the actual stub at line 111: `"{\"id\":\"c1\",\"name\":\"md1-h-vs-a\",..."`. The suffix is appended to team shortNames but NOT to the stub channel name (the stub is re-used across test methods). The new stub literal is `"md1-rs-h-vs-a"`. The per-test `fetchChannel` stubs (128, 154, 182) also use `"md1-h-vs-a"` → `"md1-rs-h-vs-a"`.

### `src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java`

Match fixture: `seedMatchWithChannel(suffix)` → shortNames `"h" + suffix` and `"a" + suffix`, REGULAR/LEAGUE, no group.

| Line | Method | Current value | New value |
|---|---|---|---|
| 91 | `givenChannelExistsAndCategoryHasRoom...` — `PATCH /channels/c1` response | `"md1-h-vs-a"` | `"md1-rs-h-vs-a"` |
| 113 | `givenChannelAlreadyArchived...` — `PATCH /channels/c1` response | `"md1-h-vs-a"` | `"md1-rs-h-vs-a"` |

Context: Neither test asserts the `name` field of the PATCH response — they assert `parent_id` and `discordChannelArchivedAt`. These are consistency-only changes (D-14).

### `src/test/java/org/ctc/discord/service/DiscordChannelServiceCleanupFailIT.java`

Match fixture: `seedMatch()` → shortNames `"hc"` and `"ac"`, REGULAR/LEAGUE, no group.

| Line | Method | Current value | New value |
|---|---|---|---|
| 117 | `givenAuditFailAndDeleteReturns500...` — `POST /guilds/g1/channels` response | `"md1-hc-vs-ac"` | `"md1-rs-hc-vs-ac"` |
| 125 | `givenAuditFailAndDeleteReturns500...` — `GET /channels/c1` response | `"md1-hc-vs-ac"` | `"md1-rs-hc-vs-ac"` |

### `src/test/java/org/ctc/discord/service/DiscordChannelServiceWireMockIT.java`

Match fixture: `seedMatch(suffix)` → shortNames `"home" + suffix` and `"away" + suffix`, REGULAR/LEAGUE, no group.

| Line | Method | Current value | New value |
|---|---|---|---|
| 123 | `givenValidMatchAndConfig...` — `POST /guilds/g1/channels` response | `"md1-home-vs-away"` | `"md1-rs-homeH-vs-awayH"` |
| 130 | `givenValidMatchAndConfig...` — `GET /channels/c1` response | `"md1-home-vs-away"` | `"md1-rs-homeH-vs-awayH"` |
| 182 | `givenIntraTeamMatchWithSameEffectiveRole...` — `POST /guilds/g1/channels` response | `"md1-home-vs-away"` | `"md1-rs-homeIT-vs-awayIT"` |
| 189 | `givenIntraTeamMatchWithSameEffectiveRole...` — `GET /channels/c1` response | `"md1-home-vs-away"` | `"md1-rs-homeIT-vs-awayIT"` |
| 219 | `givenWebhookCreationFails...` — `POST /guilds/g1/channels` response | `"md1-home-vs-away"` | `"md1-rs-homeWF-vs-awayWF"` |

Note: The `seedMatch(suffix)` method creates teams with shortNames `"home" + suffix` and `"away" + suffix` (lines 105-106). The three test methods use suffixes `"H"`, `"IT"`, `"WF"` respectively (lines 118, 178, 215). `channelName()` lowercases the full string (D-03), so `"homeH"` stays lowercase as `"homeh"`. Wait — `.toLowerCase(Locale.ROOT)` is applied at the end. `"home" + "H"` = `"homeH"` → lowercased = `"homeh"`. The correct new literals are therefore:
- Method suffix `"H"` → `"md1-rs-homeh-vs-awayh"` (lowercased)
- Method suffix `"IT"` → `"md1-rs-homeit-vs-awayit"` (lowercased)
- Method suffix `"WF"` → `"md1-rs-homewf-vs-awaywf"` (lowercased)

The old literals `"md1-home-vs-away"` were also lowercase due to `toLowerCase(Locale.ROOT)` — the suffix letters get lowercased in the new format.

**RESEARCH.md Q4 recommendation:** Add `withRequestBody(matchingJsonPath("$.name", equalTo("md1-rs-homeh-vs-awayh")))` to the `wm.verify(postRequestedFor(...))` block in `givenValidMatchAndConfig_whenCreateMatchChannel_thenDbWriteAnd4OverwritesIncludingBotMember` (currently lines 148-152). This pins the outbound channel name per CLAUDE.md "WireMock is not Real-API Coverage".

---

## No Analog Found

None — all files have close analogs.

---

## Metadata

**Analog search scope:** `src/test/java/org/ctc/discord/**`, `src/main/java/org/ctc/discord/service/**`, `src/main/java/org/ctc/domain/model/**`
**Files scanned:** 12 (production) + 8 (test)
**Pattern extraction date:** 2026-05-26
