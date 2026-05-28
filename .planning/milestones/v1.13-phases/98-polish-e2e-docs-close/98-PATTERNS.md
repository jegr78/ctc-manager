# Phase 98: Polish + E2E + Docs + Close — Pattern Map

**Mapped:** 2026-05-24
**Files analyzed:** 11 (3 plans × ~4 files/plan minus shared file overlap)
**Analogs found:** 11/11

## File Classification

| New/Modified File | Plan | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|------|-----------|----------------|---------------|
| `src/main/resources/static/admin/css/admin.css` | 98-01 | css | append-only | self — existing `.discord-actions` block (lines 215-228) | exact (same MQ, same file) |
| `docs/operations/discord-integration.md` | 98-01 | runbook | append-only | self — existing § 1–5 (417 lines) | exact (same file) |
| `docs/operations/images/discord/*.png` | 98-01 | screenshots | static-asset | `.screenshots/uat-03/` workflow + `playwright-cli screenshot` | role-match (first commit-tracked screenshot directory) |
| NEW `src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java` | 98-02 | e2e-test | request-response + multipart | `src/test/java/org/ctc/e2e/discord/posts/MatchDetailMatchResultsButtonE2ETest.java` | exact (E2E + Spring + Playwright + Discord posts) |
| NEW `src/test/java/org/ctc/discord/wiremock/WireMockDiscordStubs.java` | 98-02 | test-helper | static-utility | inline stub builders in `DiscordWebhookClientThreadIdIT.java` + `DiscordWebhookClientMultipartEditIT.java` | role-match (no existing static stub holder) |
| `src/main/java/org/ctc/admin/TestDataService.java` | 98-02 | service | data-seed (append method) | self — existing `seed()` + `seedTeams()` + `seedDrivers()` / `TestHelper.createFullSeasonFixture()` | exact (same file, same package) |
| `README.md` | 98-03 | docs | append-only | self — existing "Backup & Restore" section (lines 32-63) | exact (same file, same pattern) |
| `.planning/MILESTONES.md` | 98-03 | docs | append-only | self — existing v1.12 entry (lines 1-47) | exact (same file, locked format) |
| `.planning/REQUIREMENTS.md` | 98-03 | docs | status-flip | self — existing INFRA-01..03 Resolved rows (lines 123-125) | exact (same file) |
| `.planning/STATE.md` | 98-03 | docs | append-only | self — existing UAT-07 PASS block (lines 192-223) | exact (same file) |
| NEW `.wiki-clone/Discord-Integration.md` | 98-03 | wiki-page | append-only | no in-repo analog (wiki is separate git repo) — closest: `README.md` "Backup & Restore" + `docs/operations/discord-integration.md` § 1.1 | no-analog |
| `.gitignore` | 98-03 | config | append-only | self — `.worktrees/` entry under `### Superpowers ###` block | exact (same file) |

## Pattern Assignments

---

### Plan 98-01 — `src/main/resources/static/admin/css/admin.css` (css, append-only)

**Analog:** self (same file, existing `.discord-actions` block).

**Existing `.card` rule** (lines 166-172) — the planner appends `min-width: 0`, `box-sizing: border-box`, `max-width: 100%` here:

```css
.card {
    background: var(--bg-card);
    border: 1px solid var(--border);
    border-radius: var(--radius-md);
    padding: 24px;
    margin-bottom: 16px;
}
```

**Existing `.form-group input/select/textarea` rule** (lines 309-321) — planner appends `min-width: 0`:

```css
.form-group input,
.form-group select,
.form-group textarea {
    width: 100%;
    padding: 8px 12px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    font-size: 14px;
    background: var(--bg-input);
    color: var(--white);
    transition: border-color 0.2s;
}
```

**Existing `.searchable-dropdown .dropdown-list` rule** (lines 823-834) — planner appends `max-width: 100%`:

```css
.searchable-dropdown .dropdown-list {
    display: none;
    position: absolute;
    z-index: 100;
    width: 100%;
    max-height: 250px;
    overflow-y: auto;
    background: var(--bg-input);
    border: 1px solid var(--border);
    border-top: none;
    border-radius: 0 0 var(--radius-sm) var(--radius-sm);
}
```

**Existing `@media (max-width: 640px)` block** (lines 221-228) — planner extends this exact MQ with the new `.card { padding: 16px; }` rule. DO NOT create a second `@media (max-width: 640px)` block elsewhere; reuse this one:

```css
@media (max-width: 640px) {
    .discord-actions {
        flex-direction: column;
        align-items: stretch;
    }
    .discord-actions > * { width: 100%; }
    .discord-actions .btn { width: 100%; }
}
```

**Insertion hint:**
- `.card` rule (line 166): append the three new properties IN-PLACE inside the existing block (before the closing brace at line 172).
- `.form-group input/select/textarea` (line 311): append `min-width: 0;` after `transition: border-color 0.2s;` at line 319 (before closing brace at line 320).
- `.searchable-dropdown .dropdown-list` (line 823): append `max-width: 100%;` and `box-sizing: border-box;` before closing brace at line 834.
- `@media (max-width: 640px)` (line 221): append a NEW selector `.card { padding: 16px; }` inside this existing block (before the closing brace at line 228). DO NOT open a new media query.

**No-Inline-Styles invariant:** the only edits are to existing class rules; no inline `style="…"` attributes are introduced in templates. CLAUDE.md "CSS Guidelines".

---

### Plan 98-01 — `docs/operations/discord-integration.md` (runbook, append-only)

**Analog:** self (existing 417-line file, sections § 1 – § 5 + § "Minimum Bot Permissions").

**Existing section hierarchy** — planner pins numbering decisions against this list:

```
# CTC Manager — Discord Integration Runbook   (line 1)
## 1. Setup — Bot Registration                 (line 19)
### 1.1. Create the Discord Application + Bot  (line 25)
### 1.2. Reveal the Bot Token                  (line 35)
### 1.3. Bot Permissions — OAuth2 URL Generator (line 49)
### 1.4. Invite the Bot into a Guild           (line 89)
### 1.5. Wire the Token into the JVM Process   (line 104)
### 1.6. Enable Discord Developer Mode         (line 178)
### 1.7. Copy the Guild ID                     (line 200)
### 1.8. Webhook URL (for the test button)     (line 224)
## 2. Admin Configuration Page                 (line 243)
### 2.1. Six Form Fields                       (line 247)
### 2.2. Four Test/Refresh Buttons             (line 261)
## 3. Error Categories                         (line 278)
## 4. UAT-03 — Live-Discord Smoke              (line 297)
## 5. Troubleshooting                          (line 347)
## Minimum Bot Permissions                     (line 405)
```

**Existing § 1.8 "Webhook URL" closes at line 240 with a `---` separator** (line 241). Planner appends `### 1.9. Forum-Channel + Thread Setup` AFTER line 240 and BEFORE line 241's `---`. New § 1.9 mirrors the imperative tone of § 1.7 (Copy the Guild ID): `"You open the Discord client → Channels → Create Channel → Forum → …"`.

**Existing § 2.2 closes at line 275 with a `---` separator** (line 276). Planner appends `### 2.3. Daily Operations` AFTER line 275 and BEFORE line 276's `---`. New § 2.3 documents Phase 94-97 operator workflows (Channel-Create + 5 Match-Channel-Posts + PROVISIONAL + MATCHDAY_OVERVIEW + POWER_RANKINGS + STANDINGS + Archive-Modal).

**Existing § 5 "Troubleshooting" continues until line 403** (last subsection `### Log lines contain the literal string …`). Planner appends:
- **NEW `## 6. Token-Rotation Procedure`** AFTER line 403 and BEFORE line 405's `## Minimum Bot Permissions` heading.
- **NEW `## 7. UAT-08 Procedure + Extended Troubleshooting`** AFTER § 6 and BEFORE `## Minimum Bot Permissions`.

**Section-number rationale (D-98-DOCS-2):** § 1.9 fits cleanly under § 1 (Setup) numbered after 1.8. § 2.3 extends § 2 (Admin Configuration Page) past the existing 2.1/2.2. § 6 + § 7 follow the existing `## N. <Title>` flat top-level pattern (analog to `## 4. UAT-03 — …` and `## 5. Troubleshooting`). The `## Minimum Bot Permissions` heading stays at the bottom (it has no number on purpose — operator-reference, not setup-flow).

**Imperative tone reference excerpt** (existing § 4 UAT-03 lines 313-339 — planner copies sentence shape for § 7 UAT-08):

```markdown
**Procedure:**

1. Start the app via the wrapper script — `dev` profile is sufficient (H2 in-memory,
   no MariaDB setup required, the Discord HTTP calls still go to the real
   `https://discord.com/api/v10`):
   ```bash
   ./scripts/app.sh start dev
   ```
2. Browse to `http://localhost:9090/admin/discord-config` (or `:9091` for `local`).
3. Fill **Guild ID** with the test-guild snowflake; click **Save**.
   → expect green `.alert-success` "Configuration saved."
4. Click **Test Connection**.
   → expect `Connected as <bot-username>` matching the name you set in Developer Portal § 1.1.
```

**Existing § 5 Troubleshooting entry-shape** (line 386-396 — planner mirrors for new `category-full` / `not-found` / `missing webhook` entries):

```markdown
### "Test Announcement-Webhook" returns `auth` badge

- The configured `announcementWebhookUrl` was rotated or revoked in the Discord-server.
- Re-create the webhook in the Discord-server → Edit Channel → Integrations → Webhooks
  → New Webhook → copy URL → paste into `/admin/discord-config` → Save → retry.
```

**No-Comment-Pollution invariant:** no `Phase 98`, `UAT-08`, `Plan 98-01`, or `Closes ROADMAP Krit-6` markers in headers, body, or footnotes. Sections are titled by their stable operator meaning ("Token-Rotation Procedure", "UAT-08 Procedure"), not by their planning history. CLAUDE.md "No Comment Pollution".

---

### Plan 98-01 — `docs/operations/images/discord/*.png` (screenshots, static-asset)

**Analog:** existing `.screenshots/uat-03/` workflow (gitignored) + `playwright-cli screenshot` CLI convention from CLAUDE.md "Development Approach".

**Pattern from existing UAT-03 STATE.md entry** (STATE.md line 128):

```
**Screenshots:** `.screenshots/uat-03/` (gitignored locally — 8 PNGs: Desktop initial + 4 button-success states,
  Mobile initial + scrolled + Test-Connection success).
```

**Generation command pattern** (CLAUDE.md "Development Approach" + STATE.md UAT-06 screenshot workflow):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo &
playwright-cli open http://localhost:9090/admin/discord-config
playwright-cli screenshot --filename=docs/operations/images/discord/01-discord-config-cold.png http://localhost:9090/admin/discord-config
```

**Path convention (D-98-DOCS-3 Planner-Discretion 7):** prefix-number for sort-by-story (`01-discord-config-cold.png`, `02-match-detail-team-cards.png`, …). Mirrors the existing `.screenshots/98-mobile-polish/` directory convention but committed instead of gitignored.

**Insertion hint:** create `docs/operations/images/discord/` directory in the same commit as the `docs/operations/discord-integration.md` § 1.9 / § 2.3 / § 6 / § 7 edits; `.png` files are committed (NOT gitignored). The `.gitignore` rule `### Skills ###` block (line 38-46) only ignores `.screenshots/`, NOT `docs/operations/images/`.

---

### Plan 98-02 — `src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java` (NEW e2e-test)

**Analog:** `src/test/java/org/ctc/e2e/discord/posts/MatchDetailMatchResultsButtonE2ETest.java` (Phase 95) — closest E2E that combines Playwright + Spring + Discord post entities.

**Imports pattern** (analog lines 1-28 — pin verbatim into the new file's top):

```java
package org.ctc.e2e.discord.lifecycle;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ViewportSize;
import java.time.LocalDateTime;
import org.ctc.TestHelper;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.e2e.PlaywrightConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
```

**Class-shape pattern** (analog lines 30-55 — class header + `@Autowired` block + setup/teardown):

```java
@Tag("e2e")
class MatchDetailMatchResultsButtonE2ETest extends PlaywrightConfig {

    @Autowired
    DiscordPostRepository discordPostRepository;

    @Autowired
    MatchRepository matchRepository;

    @Autowired
    RaceResultRepository raceResultRepository;

    @Autowired
    TestHelper helper;

    @BeforeEach
    void setUp() {
        setupPage();
        discordPostRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        teardownPage();
        discordPostRepository.deleteAll();
    }
```

**Adapt for Phase 98:** keep `@Tag("e2e")`, extend `PlaywrightConfig`, autowire `TestDataService` instead of bespoke `TestHelper.create…()` calls (since D-98-E2E-3 commits `seedFullMatchdayLifecycle()`), keep the `discordPostRepository.deleteAll()` setup/teardown to enforce test isolation per CLAUDE.md "Isolate Test Data Completely".

**Mobile-viewport context pattern** (analog lines 121-136 — reuse for any "operator views on mobile" sub-step in the Mega-Walkthrough if the Hybrid-C variant uses one):

```java
try (BrowserContext mobileContext = browser.newContext(
        new Browser.NewContextOptions().setViewportSize(new ViewportSize(375, 667)))) {
    Page mobile = mobileContext.newPage();
    mobile.navigate(url("/admin/matches/" + match.getId()));
    assertThat(mobile.locator("[data-testid='update-match-results']")).isVisible();
}
```

**WireMock NOT injected via `@AutoConfigureWireMock` — CRITICAL CORRECTION:**

CONTEXT D-98-E2E-2 says `@AutoConfigureWireMock(port = 0)` + `discord.api.base-url=http://localhost:${wiremock.server.port}`, BUT all 8 existing Phase-93..97 ITs (`DiscordPostServiceIT`, `DiscordWebhookClientMultipartEditIT`, `DiscordWebhookClientThreadIdIT`, `DiscordRateLimitInterceptorIT`, `DiscordRestClientIT`, `DiscordDevSeederIT`, `DiscordLogMaskingIT`, `DiscordWebhookClientMultipartIT`, `DiscordWebhookClientIT`) use a different idiomatic pattern: `@RegisterExtension static WireMockExtension wm` + `@DynamicPropertySource`. The property name is `app.discord.base-url` (NOT `discord.api.base-url`).

**Canonical WireMock-setup excerpt** (from `DiscordWebhookClientThreadIdIT.java` lines 32-61 — pin verbatim, only changing the test-class name):

```java
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class DiscordWebhookClientThreadIdIT {

    private static final byte[] PNG_BYTES = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideDiscordConfig(DynamicPropertyRegistry registry) {
        registry.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
        registry.add("app.discord.bot-token", () -> "test-bot-token");
        registry.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
        registry.add("app.discord.rate-limit.jitter-ms", () -> "0");
        registry.add("app.discord.rate-limit.fivexx-backoff-ms", () -> "10,10,10");
    }

    @Autowired
    private DiscordWebhookClient client;

    @BeforeEach
    void resetWireMock() {
        wm.resetAll();
    }
```

**Planner action:** keep `extends PlaywrightConfig` + `@Tag("e2e")` from the analog AND combine with the `@RegisterExtension static WireMockExtension wm` + `@DynamicPropertySource` shape from `DiscordWebhookClientThreadIdIT`. The property name MUST be `app.discord.base-url` (NOT `discord.api.base-url` — CONTEXT D-98-E2E-2 is mistaken; ignore the CONTEXT name in favour of the actual `application.yml` key the existing 8 ITs use).

**Multipart-body assertion pattern** (from `DiscordWebhookClientMultipartEditIT.java` lines 79-100 — pin into the Mega-Walkthrough's per-stage `wm.verify(...)`):

```java
wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/" + messageId))
        .withHeader("Content-Type", containing("multipart/form-data"))
        .withMultipartRequestBody(aMultipart("payload_json")
                .withHeader("Content-Type", containing("application/json"))
                .withBody(matchingJsonPath("$.content", containing("hello"))))
        .withMultipartRequestBody(aMultipart("files[0]")
                .withHeader("Content-Type", equalTo("image/png")))
        .willReturn(okJson("{\"id\":\"msg-edit-2\",\"channel_id\":\"chan-1\"}")));

// ... call under test ...

wm.verify(patchRequestedFor(urlPathEqualTo(webhookPath + "/messages/" + messageId)));
```

**`?thread_id=` query-param verify pattern** (from `DiscordWebhookClientThreadIdIT.java` lines 63-78 — D-98-E2E-4 explicit-query-param-rule for Forum-Thread stages):

```java
wm.stubFor(post(urlPathEqualTo(webhookPath))
        .withQueryParam("wait", equalTo("true"))
        .withQueryParam("thread_id", equalTo("THREAD123"))
        .willReturn(okJson("{\"id\":\"msg-1\",\"channel_id\":\"chan-1\"}")));

// ... call under test ...

wm.verify(postRequestedFor(urlPathEqualTo(webhookPath))
        .withQueryParam("wait", equalTo("true"))
        .withQueryParam("thread_id", equalTo("THREAD123")));
```

**Test-method naming (BDD per CLAUDE.md "TDD/BDD"):** single `@Test fullMatchdayLifecycle()` per D-98-E2E-1, OR Hybrid-C with `@Test fullMatchdayLifecycle()` + 8 private `step1_createChannel_thenChannelIdStored()` / `step2_postTeamCards_thenMultipartPosted()` / … methods (Planner-Discretion). Either way method names follow `givenContext_whenAction_thenExpectedResult()` BDD shape. CLAUDE.md "Test Naming".

**No-Comment-Pollution invariant:** no `Phase 98`, `D-98-E2E-1`, `UAT-08`, `Wave-Pause`, or REQ-ID markers anywhere in the test file. CLAUDE.md "No Comment Pollution".

---

### Plan 98-02 — `src/test/java/org/ctc/discord/wiremock/WireMockDiscordStubs.java` (NEW test-helper)

**Analog:** no static stub-holder exists yet — closest reference: inline stub builders in `DiscordWebhookClientThreadIdIT.java` (lines 63-78, 80-90, 93-108, 111-124, 126-147) + `DiscordWebhookClientMultipartEditIT.java` (lines 62-99).

**Recommended static-method-holder shape** (no existing class to mirror — synthesize from `WireMock.stubFor(...)` builder patterns):

```java
package org.ctc.discord.wiremock;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

public final class WireMockDiscordStubs {

    private WireMockDiscordStubs() {}

    public static void stubCreateChannel(WireMockExtension wm, String guildId, long channelSnowflake) {
        wm.stubFor(post(urlPathEqualTo("/api/v10/guilds/" + guildId + "/channels"))
                .willReturn(okJson("{\"id\":\"" + channelSnowflake + "\",\"name\":\"md1-t-alf-vs-t-bra\"}")));
    }

    public static void stubCreateWebhook(WireMockExtension wm, long channelSnowflake, long webhookSnowflake, String token) {
        wm.stubFor(post(urlPathEqualTo("/api/v10/channels/" + channelSnowflake + "/webhooks"))
                .willReturn(okJson("{\"id\":\"" + webhookSnowflake + "\",\"token\":\"" + token + "\"}")));
    }

    public static void stubExecuteWebhook(WireMockExtension wm, long webhookId, String token, long messageSnowflake) {
        wm.stubFor(post(urlPathEqualTo("/api/v10/webhooks/" + webhookId + "/" + token))
                .willReturn(okJson("{\"id\":\"" + messageSnowflake + "\",\"channel_id\":\"chan\"}")));
    }

    public static void stubPatchMessage(WireMockExtension wm, long webhookId, String token, long messageSnowflake) {
        wm.stubFor(patch(urlPathEqualTo("/api/v10/webhooks/" + webhookId + "/" + token + "/messages/" + messageSnowflake))
                .willReturn(okJson("{\"id\":\"" + messageSnowflake + "\",\"channel_id\":\"chan\"}")));
    }

    public static void stubArchiveChannel(WireMockExtension wm, long channelSnowflake) {
        wm.stubFor(patch(urlPathEqualTo("/api/v10/channels/" + channelSnowflake))
                .willReturn(okJson("{\"id\":\"" + channelSnowflake + "\",\"parent_id\":\"archive-cat-1\"}")));
    }
}
```

**Package placement (D-98-E2E-11):** `src/test/java/org/ctc/discord/wiremock/` — sibling to production `org.ctc.discord.*` code, test-only visibility (lives under `src/test/`). NOT in `src/test/java/org/ctc/e2e/discord/lifecycle/` so future Phase-95-97 ITs can reuse it without an awkward up-and-over import.

**No Lombok needed:** the class is a stateless static-method holder with a private constructor. No `@Slf4j`, no `@RequiredArgsConstructor`.

**No-Comment-Pollution:** no Phase/Plan/Task markers in the class header. Method names alone (`stubCreateChannel`, `stubExecuteWebhook`, …) document intent. CLAUDE.md "No Comment Pollution".

---

### Plan 98-02 — `src/main/java/org/ctc/admin/TestDataService.java` (service, append method)

**Analog:** self — existing `seed()` orchestrator (lines 72-94) + `seedTeams()` / `seedDrivers()` / `seedSeasonDrivers()` / `seedMatchdaysAndResults()` private helpers.

**Existing `seed()` orchestrator** (lines 72-94 — `seedFullMatchdayLifecycle()` mirrors this shape for the lifecycle-test fixture):

```java
@Transactional
public void seed() {
    if (seasonRepository.count() > 0) {
        log.debug("Seed data already present, skipping");
        return;
    }
    var scorings = seedScorings();
    var teams = seedTeams();
    seedSubTeams(teams);
    copyDemoLogos(teams);
    seedSeasons(teams, scorings);
    seedPhaseTeams();
    seedDrivers();
    seedAliases();
    seedSeasonDrivers();
    seedMatchdaysAndResults();
    seedRaceLineups();
    seedPlayoffs();
    generateTeamCards();
    log.info("Seed data created: {} teams, {} seasons, {} drivers, {} race-lineups, {} results",
            teamRepository.count(), seasonRepository.count(), driverRepository.count(),
            raceLineupRepository.count(), raceResultRepository.count());
}
```

**Existing helper constructor pattern** (lines 132-138 + 1015-1017):

```java
private Team team(String name, String shortName, String primary, String secondary, String accent) {
    var t = new Team(name, shortName);
    t.setPrimaryColor(primary);
    t.setSecondaryColor(secondary);
    t.setAccentColor(accent);
    return t;
}

private Driver driver(String psnId, String nickname) {
    return driverRepository.save(new Driver(psnId, nickname));
}
```

**Existing `TestHelper.createFullSeasonFixture(...)` pattern** (file `src/test/java/org/ctc/TestHelper.java` lines 131-150 — record-style return value):

```java
/**
 * Creates a full setup: Season with two teams added, a matchday, a match, and a race.
 * Returns the race for further use.
 */
public SeasonFixture createFullSeasonFixture(String prefix) {
    var season = createSeason(prefix + "_Season");
    var homeTeam = createTeam(prefix + " Home", prefix + "_HOM");
    var awayTeam = createTeam(prefix + " Away", prefix + "_AWY");
    season.addTeam(homeTeam);
    season.addTeam(awayTeam);
    season = seasonRepository.save(season);
    var matchday = createMatchday(season, prefix + " MD1", 1);
    var match = createMatch(matchday, homeTeam, awayTeam);
    var race = createRace(matchday, match);
    return new SeasonFixture(season, matchday, match, race, homeTeam, awayTeam);
}

public record SeasonFixture(Season season, Matchday matchday, Match match, Race race,
                            Team homeTeam, Team awayTeam) {
}
```

**Test-prefix invariant (CLAUDE.md "Isolate Test Data Completely"):** every new entity in `seedFullMatchdayLifecycle()` MUST use the `T-` / `Test-` prefix. Reference STATE.md UAT-04 / UAT-05 / UAT-06 / UAT-07 entries which all use `T-ALF`, `Test-Season 2026`, `T-Bot`, etc.

**Recommended new method shape** (synthesize from the two analogs):

```java
@Transactional
public LifecycleFixture seedFullMatchdayLifecycle() {
    var scorings = seedScorings();
    var home = teamRepository.save(team("Test Alfa", "T-ALF", "#FF0000", "#000000", "#FFFFFF"));
    var away = teamRepository.save(team("Test Bravo", "T-BRA", "#0000FF", "#000000", "#FFFFFF"));
    home.setDiscordRoleId("100000000000000001");
    away.setDiscordRoleId("100000000000000002");
    var season = createSeason("Test-Season 2099", 2099, 1, scorings);
    season.addTeam(home);
    season.addTeam(away);
    season = seasonRepository.save(season);
    var matchday = createMatchday(season, "MD1", 0);
    var match = matchRepository.save(new Match(matchday, home, away));
    // ... RaceLineup + Settings + Race-Times wiring ...
    log.info("Seeded lifecycle fixture: season={} match={}", season.getName(), match.getId());
    return new LifecycleFixture(season, matchday, match, home, away);
}

public record LifecycleFixture(Season season, Matchday matchday, Match match, Team homeTeam, Team awayTeam) {
}
```

**Insertion hint (D-98-PROD-1 APPEND-only):** add the new method at the BOTTOM of the class, AFTER the existing `private record ScoringDefaults(...)` at line 1052 and BEFORE the closing `}` of the class at line 1054. The new `public record LifecycleFixture(...)` goes immediately below `seedFullMatchdayLifecycle()`. Do NOT rewrite any existing method, do NOT reorder existing helpers.

**Coverage-exclusion invariant:** `TestDataService` is already in `pom.xml` jacoco-excluded list (CLAUDE.md "Excluded from coverage"). The new method does NOT need its own unit test for coverage; it is exercised indirectly via `DiscordFullMatchdayLifecycleE2ETest`. JaCoCo gate remains at 82 %, baseline 88.88 % per D-98-QG-1.

**`@Profile({"dev","local"})` invariant:** the class is already annotated `@Profile({"dev", "local"})` (line 40). Do NOT change the profile annotation; the E2E test runs against the `dev` profile per `PlaywrightConfig` convention.

---

### Plan 98-03 — `README.md` (docs, append-only)

**Analog:** self (existing 158-line README, "Backup & Restore" section as the closest "feature blurb + wiki link" precedent).

**Existing feature-bullet list under "Features"** (lines 15-31 — planner appends a new bullet here):

```markdown
## Features

- **Seasons & Matchdays** — League and Swiss-system formats with configurable rounds
- **Teams & Sub-Teams** — Parent/child team hierarchy with sub-team lineups per matchday
...
- **Backup & Restore** — Export a full ZIP backup of all 24 entity tables; restore via a preview-and-confirm import flow with schema-version locking
```

**Existing detailed feature section "Backup & Restore"** (lines 32-63 — planner mirrors this exact shape for a "Discord Integration" section if D-98-WIKI-3 calls for an in-README detail block, NOT just a bullet):

```markdown
## Backup & Restore

v1.10 introduces a full database backup/restore feature accessible via `/admin/backup`.

### Export

1. Navigate to `/admin/backup` in the admin sidebar.
2. Click **Export Backup** — a ZIP file (`ctc-backup-<ISO-instant>.zip`) downloads immediately.
3. Store the ZIP in a safe location. Each export captures all 24 entity tables.

### Import

...

### Full Guide

See the [Backup & Restore wiki page](../../wiki/Backup-and-Restore) for the step-by-step export
workflow, import workflow, schema-version explanation, and recovery procedures.
```

**Existing "Note (v1.12)" inline-callout pattern** (lines 56-58 — planner mirrors for "Note (v1.13)" if needed):

```markdown
> **Note (v1.12):** Google Sheets / Calendar API errors during driver-import or calendar-event creation
> now surface a categorized error badge (Transient / Auth / NotFound / Permission) with actionable
> hardcoded messages. Operator setup, error-category reference, and troubleshooting steps are
> documented in [`docs/operations/google-integration.md`](docs/operations/google-integration.md).
> Milestone PR #129.
```

**Wiki-link convention** (line 152):

```markdown
## Documentation

See the [Wiki](../../wiki) for detailed documentation on architecture, features, setup, and configuration.
```

**Insertion hint (D-98-WIKI-3):**
- **Short bullet** — appended to the `## Features` list (line 31, AFTER the existing "Backup & Restore" bullet). Format: `- **Discord Integration** — Per-match Discord channels with 11 structured posts (team cards, settings, lineups, schedule, results), forum-thread linking for race-results + standings, auto-edit on schedule changes. See [Discord Integration wiki page](../../wiki/Discord-Integration) and [`docs/operations/discord-integration.md`](docs/operations/discord-integration.md).`
- **Optional detail section** — IF the canonical paragraph is too long for the bullet, append a `## Discord Integration` H2 section AFTER line 63 (end of "Backup & Restore" section, BEFORE "Quick Start" on line 65). Mirror the "Backup & Restore" shape (1 paragraph intro + sub-sections for Setup / Daily Operations / Full Guide).

**Sample-screenshot embed pattern** (D-98-WIKI-3 + REQ DOCS-03 "sample screenshot of `/admin/discord-config`"):

```markdown
![Discord Configuration Page](docs/operations/images/discord/01-discord-config-cold.png)
```

**No-Comment-Pollution:** no Phase/Plan/UAT markers in README headers or body. The "Note (v1.13)" callout is acceptable (mirrors existing "Note (v1.11)" + "Note (v1.12)" — version-tag is operator-meaningful, not planning-history).

---

### Plan 98-03 — `.planning/MILESTONES.md` (docs, append-only)

**Analog:** self — existing v1.12 entry (lines 13-47) is the locked format for v1.13.

**Existing v1.12 entry shape** (lines 13-47 — planner pins verbatim structure for v1.13):

```markdown
## v1.12 Driver-Import Gap-Closure & Test Performance Round 2 (Shipped: 2026-05-20)

**Phases completed:** 4 phases (88-91), 15 plans, 15/15 requirements satisfied (14 must-have + 1 stretch — UX-01 resolved IN per Phase 91 D-01)
**Diff:** +19 294 / −462 across 127 files (111 commits in milestone range)
**Tests:** 1696 tests passing (Surefire + Failsafe + Playwright E2E); JaCoCo line coverage 88.44 % (gate 82 %, v1.11 baseline 88.88 %, Δ−0.44 pp — flagged for v1.13 cleanup; root cause documented in Plan 91-02 SUMMARY § JaCoCo coverage delta)
**Timeline:** 2 days (2026-05-18 → 2026-05-20)
**Branch:** `gsd/v1.12-driver-import-and-test-perf` (PR #129)
**Final-gate CI:** PERF-06 5-run harvest median Run [26157245962](...) @ SHA `b63a2be1` SUCCESS — E2E step 17:39 (1059s, median of 5 sequential `workflow_dispatch` runs after dropping min+max; variance 18.2 % within D-10 20 % tolerance), Δ−23.3 % vs v1.11 23:00 baseline, SpotBugs 0 BugInstance
**Audit verdict:** passed (`v1.12-MILESTONE-AUDIT.md` will land post-`/gsd-complete-milestone v1.12`); Nyquist scoreboard compliant 4/0/0 (Phases 88+89+90+91 all `nyquist_compliant: true` per D-11 strict)

**Key accomplishments:**

- Phase 88 (...) — ...
- Phase 89 (...) — ...
- Phase 90 (...) — ...
- Phase 91 (...) — ...
- JaCoCo line coverage 88.44 % (above 82 % pom gate; ...)

**Deferred to next milestone (acknowledged at close):**

- ...

**Post-merge self-resolving (not tech debt):**

- v1.12 milestone PR #129 squash-merge to master (CI release workflow handles `v1.12.0` tag + GitHub Release + Docker images via the hardened workflow from Phase 88 REL-01 — no local `git tag` per `feedback_no_local_git_tags`)

Known deferred items at close: see `STATE.md` Deferred Items + `v1.12-MILESTONE-AUDIT.md` (lands post-`/gsd-complete-milestone v1.12`)

---
```

**Locked fields for v1.13 entry:**
- `## v1.13 Discord Integration & Carry-Forwards (Shipped: <YYYY-MM-DD>)` — date filled at squash-merge
- `**Phases completed:** 7 phases (92-98), <total-plans>/<>, 25/25 requirements satisfied`
- `**Diff:** +<adds> / −<dels> across <files> files (<commits> commits in milestone range)` — filled from `git diff --stat origin/master..HEAD`
- `**Tests:** <N> tests passing (Surefire + Failsafe + Playwright E2E); JaCoCo line coverage <X> % (gate 82 %, v1.12 baseline 88.44 %, Δ<sign><Y> pp)`
- `**Timeline:** <D> days (<start> → <end>)`
- ``**Branch:** `gsd/v1.13-discord-integration` (PR #<num>)``
- `**Final-gate CI:** Run [<run-id>](...) @ SHA `<sha>` SUCCESS — E2E step <mm:ss> (within 17:39 ± 20 % per Baselines to Preserve), SpotBugs 0 BugInstance`
- `**Audit verdict:** passed (`v1.13-MILESTONE-AUDIT.md` lands post-`/gsd-complete-milestone v1.13`); Nyquist scoreboard compliant 7/0/0`
- Per-phase `Key accomplishments` bullets — 7 phases × 2-4 lines each
- `**Deferred to next milestone:**` — items from STATE.md Deferred Items still open after milestone close
- `**Post-merge self-resolving:**` — v1.13 tag + GitHub Release per `feedback_no_local_git_tags`

**Insertion hint:** prepend a NEW `## v1.13 …` block BEFORE line 13 (the existing v1.12 entry). After the new block, insert the existing `---` separator. The file is descending-chronological; v1.13 goes ABOVE v1.12.

**Note on the duplicate v1.12 placeholder header** (lines 3-11 — a partial entry exists with `0 phases, 0 plans, 0 tasks`): leave it alone. It's a known bookkeeping artifact already present at branch start; out-of-scope for Phase 98.

---

### Plan 98-03 — `.planning/REQUIREMENTS.md` (docs, status-flip)

**Analog:** self — existing INFRA-01..03 `Resolved` rows (lines 123-125) are the locked format for the 25 v1.13-REQ flip.

**Existing Resolved-row format** (lines 123-125):

```markdown
| INFRA-01 | 93 | Resolved |
| INFRA-02 | 93 | Resolved |
| INFRA-03 | 93 | Resolved |
```

**Existing Pending-row format** (lines 126-143 — all 22 still-Pending REQ-IDs):

```markdown
| CHAN-01 | 94 | Pending |
| CHAN-02 | 94 | Pending |
...
| DOCS-03 | 98 | Pending |
```

**Existing checkbox format in the body** (e.g. line 16 for UX-01 — planner flips `[ ]` to `[x]` in the same edit):

```markdown
- [ ] **UX-01**: `CsvImportController` ...
```

**Insertion hint (D-98-PLAN-5 + D-98-CLOSE-2):** flip ALL of the following from `Pending` to `Resolved` in the Traceability table (lines 118-143):
- UX-01, COV-01, CLEAN-01, DOCS-01, BOOK-01 (Phase 92)
- CHAN-01, CHAN-02, CHAN-03 (Phase 94)
- POST-01..05 (Phase 95)
- GRAFX-01, FORUM-01, FORUM-02 (Phase 96)
- POST-06, POST-07a, POST-07b, POST-08 (Phase 97)
- E2E-01, DOCS-02, DOCS-03 (Phase 98)

Total: 22 rows flipped to Resolved. INFRA-01..03 are already Resolved at branch start.

AND flip the corresponding 22 `- [ ]` checkboxes in the body (lines 16-90) to `- [x]`.

**Verification command** (analog to BOOK-01's verification grep at line 24):

```bash
grep -c '^| .* | .* | Pending |$' .planning/REQUIREMENTS.md   # MUST be 0
grep -c '^- \[ \]' .planning/REQUIREMENTS.md                   # MUST be 0
```

**No-Comment-Pollution:** the existing REQ-ID body sentences ARE the requirement text — do NOT add a `Closed in Phase 98` or `Resolved 2026-05-XX` marker inline. Status moves to the Traceability table only.

---

### Plan 98-03 — `.planning/STATE.md` (docs, append-only)

**Analog:** self — existing UAT-07 PASS block (lines 192-223) is the format for staging UAT-08.

**Existing UAT-07 PASS block** (lines 192-223 — pin verbatim shape for new UAT-08):

```markdown
### UAT-07: Live Matchday-Level Posts Lifecycle (Phase 97 POST-06 + POST-07a + POST-07b + POST-08)

- **Pre-UAT-07** — UAT-06 (Phase 96 forum-thread lifecycle) must have succeeded; the operator has the same `Saison 4 - 2026` race-results forum-thread + `2026` standings forum-thread linked to a target season; bot has Manage-Webhooks on both forums and a `:CTC:` custom emoji uploaded.
- **Procedure** (9 steps per 97-VALIDATION.md Manual-Only):
  1. `/admin/seasons/{id}/edit` → Discord Integration card → POST-06 `Post Match Preview` button (from Match-Detail) → multipart-POST lands in announcement-webhook channel with Markdown body (H1/H2/H3/teaser/Date/Stream/Game On! emoji line) + Settings.png + Lineups.png attachments.
  2. ...
  9. ...
- **Status:** PASS 2026-05-24 — all 9 steps verified live on operator's test guild via playwright-cli + live Discord client.
- **Result:**
  - **Steps 1-3** ✅ POST-06 ...
  - **Step 4** ✅ POST-07a ...
- **Date:** 2026-05-24
- **Polish-Welle (7 in-milestone Fixes surfaced during UAT-07):**
  1. `093c29de` — ...
- **Plan-End reverify** on `a59d1c99`: 1807 Tests grün (Surefire 1218 + Failsafe 589), JaCoCo 88.60 %, SpotBugs 0, ~9:35 min.
```

**Pre-UAT-08 staged-block shape** (planner creates this AT Plan 98-01 close, BEFORE UAT-08 execution):

```markdown
### UAT-08: Live Full-Matchday-Lifecycle (Phase 98 E2E-01 mirror — operator-driven)

- **Pre-UAT-08** — Phase 98 Plan 98-01 + 98-02 + 98-03 committed; bot has operating cache on the operator's test guild; at least 1 spare match unposted.
- **Procedure** (8-stage walkthrough per `docs/operations/discord-integration.md` § 7):
  1. Create Discord Channel ...
  2. Post Team Cards ...
  ...
  8. Move-to-Archive ...
- **Status:** pending operator action — **required before `/gsd-complete-milestone v1.13`** (per CONTEXT D-98-E2E-9).
- **Result:** _(operator fills after execution)_
- **Date:** _(operator fills)_
- **Screenshots:** `.screenshots/uat-08/` (gitignored locally — N PNGs: per-stage operator-side captures).
```

**Position-of-UAT-08 in STATE.md (insertion hint):** append AFTER the existing UAT-07 block (line 223) and BEFORE the next H2 section `## Accumulated Context` (line 225). UAT-08 stays in `## Pending UATs` section.

**`milestone:` frontmatter:** at Plan 98-03 close, update line 6 `stopped_at: Phase 98 context gathered` → `stopped_at: Phase 98 closed — UAT-08 staged` (or similar), and `last_activity` to the close-date. `status: executing` remains until `/gsd-complete-milestone v1.13`.

**No new section headers:** UAT-08 fits inside the existing `## Pending UATs` H2 (line 95). Do NOT add a new H2 for Phase 98 close.

---

### Plan 98-03 — `.wiki-clone/Discord-Integration.md` (NEW wiki page)

**Analog:** no in-repo analog (wiki is a separate git repo cloned into a gitignored folder). Closest in-repo reference for the *canonical paragraph*: README.md "Backup & Restore" section (lines 32-63) + `docs/operations/discord-integration.md` § 1.1 intro.

**Wiki page-shape reference** (existing `Backup-and-Restore` wiki page is the precedent per STATE.md line 109 "README + GitHub Wiki page pushed to ctc-manager.wiki.git" — content not committed in this repo but the README link convention is the proof of pattern):

```markdown
# Discord Integration

<one-paragraph canonical description>

CTC Manager integrates with Discord to automate per-match channel creation
and structured posting workflows. Each match gets its own Discord channel
with 11 structured post types (team cards, settings, lineups, schedule,
provisional scores, match results, match previews, matchday overviews,
power rankings, standings — plus forum-thread posts for race-results +
season standings). Auto-edit hooks keep posts in sync when match schedules
or stream links change. Pre-flight gates and stale-detection signals
prevent accidental duplicate posts. Designed for a single Discord guild
operated by one league administrator.

## Highlight Features

- **Per-match channels** — Automatically created with the proper
  permission-overwrites so only the participating teams (+ the operator)
  can see the channel.
- **11 structured post types** — ...
- **Auto-edit on form save** — ...
- **Pre-flight gates** — ...
- **Stale-detection signals** — ...

## Setup

See [`docs/operations/discord-integration.md`](../docs/operations/discord-integration.md)
for the complete operator-runbook covering bot registration, OAuth permissions,
token wiring, forum-channel setup, and daily-operations workflows.

## Screenshot

![Discord Configuration Page](images/discord-config.png)
```

**Canonical-paragraph identity (D-98-WIKI-3):** the same paragraph also lives in README.md as the bullet body. Operator-facing copy, not marketing — describe what the integration DOES + for WHOM + WHERE to find detailed docs.

**Wiki-asset upload pattern** (D-98-WIKI-2): screenshots committed to the wiki repo via `git add .wiki-clone/images/discord-config.png`. Wiki repos accept image files as normal commits.

**`Home.md` Sidebar update** (D-98-WIKI-2): add a link to `Discord-Integration` analog to existing Wiki-page links (Backup-and-Restore precedent). Format depends on existing wiki sidebar shape (planner inspects the cloned `.wiki-clone/Home.md` and mirrors).

**No-Comment-Pollution:** wiki page has zero `Phase 98`, `D-98-WIKI-*`, `Resolved 2026-05-XX` markers. Only operator-meaningful headers.

---

### Plan 98-03 — `.gitignore` (config, append-only)

**Analog:** self — existing `### Superpowers ###` block (lines around `.superpowers/` + `.worktrees/`).

**Existing block** (the `.worktrees/` entry under `### Superpowers ###`):

```
### Superpowers ###
.superpowers/
.worktrees/
```

**Existing Skills block** (right after Superpowers):

```
### Skills ###
.agents/
.claude/skills/
.junie/
.playwright-cli/
.playwright-mcp/
.screenshots/
skills-lock.json
```

**Insertion hint (D-98-PROD-1):** check first with `grep -n "wiki-clone" .gitignore` — at branch-start this matched 0 lines. Append a new entry under a fresh `### Wiki Clone ###` heading OR (preferred) under the existing `### Superpowers ###` block (since `.wiki-clone/` is a transient local-clone tool, analog to `.worktrees/`):

```
### Superpowers ###
.superpowers/
.worktrees/
.wiki-clone/
```

The grep-verify command in Plan 98-03 task definition:

```bash
grep -q "^\.wiki-clone/$" .gitignore || echo "MISSING — add .wiki-clone/ to .gitignore"
```

---

## Shared Patterns

### Pattern S-1: Append-only on Shared Files (CLAUDE.md "Subagent Rules" `feedback_worktree_file_clobber`)

**Source:** CLAUDE.md "Subagent Rules" — "No File Clobber on Shared Files".

**Apply to:** `admin.css` (98-01), `discord-integration.md` (98-01), `TestDataService.java` (98-02), `README.md` (98-03), `MILESTONES.md` (98-03), `STATE.md` (98-03), `REQUIREMENTS.md` (98-03), `.gitignore` (98-03).

**Rule:** every edit APPENDS new content after existing content; existing classes / sections / methods / rows are NEVER rewritten. After each edit the planner spot-checks `wc -l` before+after to confirm the file grew (or stayed identical on no-op flips like REQUIREMENTS.md status-only changes).

**Verify command per file:**

```bash
git diff --stat HEAD~1 HEAD -- src/main/resources/static/admin/css/admin.css   # expect only "+N lines, 0 lines −"
```

### Pattern S-2: `@Tag` Convention (CLAUDE.md "Test Categorization (`@Tag`)")

**Source:** `.planning/codebase/TESTING.md` "Test Categorization (`@Tag`)" + CLAUDE.md "Architectural Principles" "Tag Tests by Category".

**Apply to:** `DiscordFullMatchdayLifecycleE2ETest.java` (98-02) — `@Tag("e2e")`. `WireMockDiscordStubs.java` is a helper (no `@Tag`).

**Excerpt** (TESTING.md "Example: Proper Test Tagging"):

```java
@Tag("e2e")
class ScoringE2ETest extends PlaywrightConfig {
    @BeforeEach void setUp() { setupPage(); }
    @AfterEach void tearDown() { teardownPage(); }
    ...
}
```

Failsafe routes E2E tests only when `-Pe2e` profile is active (pom.xml lines 440-460). Selective trigger: `./mvnw -Dit.test=DiscordFullMatchdayLifecycleE2ETest -DfailIfNoTests=false verify -Pe2e`.

### Pattern S-3: WireMock `withQueryParam(...)` (CLAUDE.md "Build & Test Discipline" `feedback_wiremock_vs_real_api`)

**Source:** CLAUDE.md "Build & Test Discipline" — "WireMock is not Real-API Coverage" + `DiscordWebhookClientThreadIdIT.java` lines 63-90.

**Apply to:** every stub + verify in `DiscordFullMatchdayLifecycleE2ETest` and `WireMockDiscordStubs`. NEVER stub or verify a Webhook URL by `urlPathEqualTo` alone — always add `.withQueryParam("wait", equalTo("true"))` AND (for forum stages) `.withQueryParam("thread_id", equalTo(...))`. Production code always sends `?wait=true` on webhook POSTs and `?thread_id=` on forum-thread posts.

**Verify command:**

```bash
grep -c "withQueryParam" src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java   # MUST be >0
grep -c "withQueryParam" src/test/java/org/ctc/discord/wiremock/WireMockDiscordStubs.java                     # MUST be >0
```

### Pattern S-4: No Comment Pollution (CLAUDE.md "Conventions / No Comment Pollution")

**Source:** CLAUDE.md "No Comment Pollution".

**Apply to:** every file in Plans 98-01 / 98-02 / 98-03 — `.css`, `.md`, `.java`, `.gitignore`.

**Banned in source files:**
- `// Phase 98 ...`, `// Plan 98-02 ...`, `// UAT-08 follow-up: ...`, `// Closes REQ E2E-01`, `// Wave-Pause closeout`
- File-header blocks restating purpose ("/* Compatible with H2 + MariaDB */")
- "Added for X" / "Used by Y" cross-references

**Sweep-when-touching invariant (CLAUDE.md "When refactoring, remove pollution from touched files"):** if Plan 98-01 edits a `.card` rule that has a stale `/* Closes Phase 91 ... */` comment above it, the comment goes in the same edit.

**Verify command:**

```bash
grep -rn 'Phase 9[0-9]\|UAT-0[0-9]\|Plan 9[0-9]\|Wave-' src/main/resources/static/admin/css/admin.css   # MUST be 0
grep -rn 'Phase 9[0-9]\|UAT-0[0-9]\|Plan 9[0-9]\|Wave-' src/test/java/org/ctc/e2e/discord/lifecycle/    # MUST be 0
grep -rn 'Phase 9[0-9]\|UAT-0[0-9]\|Plan 9[0-9]\|Wave-' src/test/java/org/ctc/discord/wiremock/         # MUST be 0
grep -rn 'Phase 9[0-9]\|UAT-0[0-9]\|Plan 9[0-9]\|Wave-' docs/operations/discord-integration.md          # MUST be 0
```

(Existing `STATE.md`, `MILESTONES.md`, and per-phase `*-CONTEXT.md` are PLANNING files — version-tags are allowed. The rule binds source + runbook + README + Wiki only.)

### Pattern S-5: BDD Test Naming (CLAUDE.md "Development Approach")

**Source:** CLAUDE.md "Development Approach" — "Test Naming (Given-When-Then)".

**Apply to:** `DiscordFullMatchdayLifecycleE2ETest.fullMatchdayLifecycle()` (or `step1_..._step8_` if Hybrid-C variant chosen).

**Excerpt** (analog `MatchDetailMatchResultsButtonE2ETest` line 91):

```java
@Test
void givenIncompleteResults_whenLoadDesktopMatchDetail_thenButtonDisabledWithTooltip() {
    Match match = seedMatch("E1", false);
    page.navigate(url("/admin/matches/" + match.getId()));
    // given ... when ... then
}
```

**For the top-level Mega-Walkthrough** (D-98-E2E-1 — eine `@Test`-Methode is allowed even if it doesn't have a single given/when/then — single comprehensive name fits): `fullMatchdayLifecycle()`. The `given_when_then` shape applies to each private helper if Hybrid-C is chosen.

### Pattern S-6: Test-Data Isolation (CLAUDE.md "Architectural Principles")

**Source:** CLAUDE.md "Architectural Principles" — "Isolate Test Data Completely".

**Apply to:** `TestDataService.seedFullMatchdayLifecycle()` and `DiscordFullMatchdayLifecycleE2ETest`.

**Rule:** entities use `T-` / `Test-` prefixes (`T-ALF`, `T-BRA`, `Test-Season 2099`). Mirror UAT-04 / UAT-05 / UAT-06 / UAT-07 wording in STATE.md.

**Deterministic Snowflake IDs (D-98-E2E-6):** every Discord-side identifier in stubs uses `900000000000000001L`-prefixed long literals (channel/webhook/message/thread IDs). Allows DB-assertions like:

```java
assertThat(match.getDiscordChannelId()).isEqualTo("900000000000000001");
```

### Pattern S-7: Sequential-Inline Branch Discipline (CLAUDE.md "Subagent Rules")

**Source:** CLAUDE.md "Subagent Rules" + CONTEXT D-98-PLAN-1 carry-forward from Phase 92-97 D-9*-05.

**Apply to:** entire Phase 98 — Plans 98-01 / 98-02 / 98-03 execute inline on `gsd/v1.13-discord-integration`. NO worktrees. NO subagent dispatch (other than read-only research). Wave-Pause after EACH plan close per `feedback_wave_pause`.

**Operator-prompt invariant:** every executor / planner / validator prompt in Phase 98 must include the active branch (`gsd/v1.13-discord-integration`) and explicitly forbid `git stash`, `git checkout`, `git reset`, branch-switch.

## No Analog Found

| File | Plan | Role | Reason |
|------|------|------|--------|
| `.wiki-clone/Discord-Integration.md` | 98-03 | wiki-page | The GitHub Wiki is a separate git repo cloned into a gitignored folder. No in-repo wiki content exists. Planner synthesizes the canonical paragraph from README.md + `docs/operations/discord-integration.md` § 1.1. |

All other files have direct in-repo analogs (either self-append patterns or sibling-class patterns from Phases 93-97).

## Metadata

**Analog search scope:**
- `src/test/java/org/ctc/discord/` (10 IT files — Phase 93-97 WireMock pattern source)
- `src/test/java/org/ctc/e2e/discord/` (15 E2E files — Phase 94-97 Playwright pattern source)
- `src/main/java/org/ctc/admin/TestDataService.java` (1054-line seed orchestrator)
- `src/test/java/org/ctc/TestHelper.java` (`createFullSeasonFixture` pattern)
- `src/main/resources/static/admin/css/admin.css` (2056-line CSS surface)
- `docs/operations/discord-integration.md` (417-line existing runbook)
- `README.md` + `.planning/MILESTONES.md` + `.planning/REQUIREMENTS.md` + `.planning/STATE.md` (self-append patterns)

**Files scanned:** ~30 (per-section targeted Reads, no full-file re-reads).

**Pattern extraction date:** 2026-05-24

**Critical correction from CONTEXT:** D-98-E2E-2 names property `discord.api.base-url` and annotation `@AutoConfigureWireMock(port = 0)`. The actual idiom in 8/8 existing Discord ITs is `@RegisterExtension static WireMockExtension wm` + `@DynamicPropertySource` with property `app.discord.base-url`. Planner MUST use the actual idiom — CONTEXT D-98-E2E-2 is documentation drift.

## PATTERN MAPPING COMPLETE
