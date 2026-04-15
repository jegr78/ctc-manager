# Phase 22: Dev Teams & Drivers - Research

**Researched:** 2026-04-09
**Domain:** TestDataService refactoring — fictive seed data + team card generation
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Use racing-themed fictive names (e.g., "Velocity Racing", "Shadow Grid Motorsport") — not real CTC team names and not generic test names like "Team A"
- **D-02:** Each team needs shortName (3-4 chars), primaryColor, secondaryColor, accentColor — matching existing Team entity structure
- **D-03:** 2-3 parent teams get sub-teams, each with 2-3 sub-teams (minimum 2 parents with 2+ sub-teams per ROADMAP SC-2)
- **D-04:** Sub-teams inherit parent's color scheme with variations (existing pattern in TestDataService)
- **D-05:** Use realistic-sounding fictive names (first + last name) — no real CTC driver names
- **D-06:** Exactly 10 drivers per team as per ROADMAP SC-3
- **D-07:** Generate team cards at seed time during DevDataSeeder startup — not on-demand
- **D-08:** Use existing TeamCardService and Playwright infrastructure for generation
- **D-09:** Replace existing real team/driver data in TestDataService.seed() with fictive data
- **D-10:** Keep the same seeding structure (seedTeams → seedSubTeams → seedDrivers → etc.) but with fictive content
- **D-11:** Existing E2E test data in TestDataService that uses test-prefixed entities (T-ALF, Test_Alpha_1, Test-Season 2026) must remain isolated — only replace the non-test seed data

### Claude's Discretion
- Specific team names, colors, and driver names — as long as they are fictive, racing-themed, and cover required structural variations
- Whether to generate logos programmatically or use placeholder images
- Order of seeding operations (must respect FK constraints)

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DATA-01 | Dev profile creates 14+ teams with fictive names, including 2-3 parent teams with sub-teams | Covered by refactoring seedTeams() + seedSubTeams() with fictive data |
| DATA-02 | Dev profile creates 10 drivers per team with fictive names | Covered by refactoring seedDrivers() — exactly 10 per team |
| DATA-03 | Team cards generated for all dev teams | Covered by calling TeamCardService.generateAllCards() at seed time |
</phase_requirements>

---

## Summary

Phase 22 is a pure data-replacement phase. The entire seeding infrastructure already exists and works: `TestDataService.seed()` is called by `DevDataSeeder` on dev profile startup, the `Team` entity accepts name/shortName/colors/parentTeam, and `TeamCardService.generateAllCards(Season)` handles Playwright-based card generation.

The work is: (1) replace the 10 real team names and all real driver names in `TestDataService` with fictive alternatives, (2) maintain the parent/sub-team structure with D-03 requirements (2-3 parents, each with 2-3 subs), and (3) add a `seedTeamCards()` call after `seedSeasons()` + `seedDrivers()` completes so that cards are generated at startup.

The critical constraint is **E2E test isolation** (D-11): `seedRaceLineups()` creates test-prefixed entities (T-ALF, T-BRV, Test_Alpha_1, etc.) that E2E tests reference directly by name. Those must remain untouched. Only the non-test data in `seedTeams()`, `seedSubTeams()`, `seedDrivers()`, `seedAliases()`, and `seedSeasonDrivers()` gets replaced.

**Primary recommendation:** Refactor the data content in `TestDataService.seedTeams()`, `seedSubTeams()`, `seedDrivers()`, `seedAliases()`, `seedSeasonDrivers()` with fictive racing data, and add `seedTeamCards()` at the end of `seed()` before the final log statement.

---

## Standard Stack

No new libraries required. All tooling is already in the project.

### Existing Infrastructure Used
| Component | Location | Purpose |
|-----------|----------|---------|
| `TestDataService` | `org.ctc.admin.TestDataService` | Seed data entry point, all helper methods exist |
| `DevDataSeeder` | `org.ctc.admin.DevDataSeeder` | `@Profile("dev")` CommandLineRunner — calls `testDataService.seed()` |
| `TeamCardService` | `org.ctc.admin.service.TeamCardService` | Playwright-based card generation via `generateAllCards(Season)` |
| `Team` entity | `org.ctc.domain.model.Team` | `new Team(name, shortName)` / `new Team(name, shortName, parentTeam)` |
| `Driver` entity | `org.ctc.domain.model.Driver` | `new Driver(psnId, nickname)`, unique constraint on `psnId` |

---

## Architecture Patterns

### Current seed() call chain (VERIFIED: TestDataService.java)
```
seed()
  ├── seedScorings()        ← keep unchanged (E2E tests reference "CTC Standard", "Standard 3-1-0")
  ├── seedTeams()           ← replace all 10 real teams with fictive teams
  ├── seedSubTeams(teams)   ← replace all sub-teams with fictive sub-teams
  ├── copyDemoLogos(teams)  ← keep as-is (copies classpath logos if they exist, silently skips)
  ├── seedSeasons(teams, scorings) ← update shortName lookups to match new fictive short names
  ├── seedDrivers()         ← replace all real PSN IDs/nicknames with fictive ones, exactly 10 per team
  ├── seedAliases()         ← replace alias examples with fictive equivalents
  ├── seedSeasonDrivers()   ← update driver PSN ID lookups to match new fictive IDs
  ├── seedRaceLineups()     ← DO NOT CHANGE (creates T-ALF, T-BRV, Test_Alpha_x, Test-Season 2026/2025)
  └── [NEW] seedTeamCards() ← add after seedRaceLineups() — generate cards for active season
```

### Team Card Generation Pattern
`TeamCardService.generateAllCards(Season)` takes the active season, iterates its `SeasonTeam` entries, and generates a card for each team that does not have sub-teams in that same season. It calls Playwright headlessly.

The method signature: `public List<String> generateAllCards(Season season) throws IOException` [VERIFIED: TeamCardService.java:111].

To call it at seed time, `TestDataService` needs `TeamCardService` injected and a reference to the active season returned from `seedSeasons()`.

### Sub-Team Counting for DATA-01

Current (real) team count: 10 parent teams + 9 sub-teams = 19 total.
Required minimum: 14+ total (DATA-01), 2+ parents each with 2+ subs (D-03).

New target: 10 parent teams + sub-teams for 2-3 parents (2-3 subs each) = 14-19 total teams. Matches requirement.

### E2E Test Data Boundaries (VERIFIED: AdminWorkflowE2ETest.java, ScoringE2ETest.java)

E2E tests reference the following seed data BY EXACT NAME:
- `"CTC Standard"` (race scoring preset) — referenced in ScoringE2ETest and AdminWorkflowE2ETest
- `"Standard 3-1-0"` (match scoring preset) — referenced in ScoringE2ETest and AdminWorkflowE2ETest
- `"T-ALF"` / `"Test Alpha Racing"` — referenced in AdminWorkflowE2ETest team detail tests
- `"T-BRV"` / `"Test Bravo Racing"` — referenced in AdminWorkflowE2ETest sub-team tests
- `"Test_Alpha_1"`, `"Test_Alpha_2"` — referenced in AdminWorkflowE2ETest driver lineup tests
- `"Test-Season 2026"`, `"Test-Season 2025"` — referenced in AdminWorkflowE2ETest season accordion tests
- `"Test MD 1"` — referenced indirectly through season/race lineup tests

**None of the real team names** (P1R, CLR, TCR, ART, AHR, MRL, GXR, DTR, VEZ, TNR) are referenced in any E2E test. These are safe to replace.

### Driver Entity Constraint
`Driver.psnId` has `@Column(unique = true)` [VERIFIED: Driver.java]. Fictive PSN IDs must be unique within the dataset. Pattern: use `<TeamShortName>_Driver01` through `<TeamShortName>_Driver10` as PSN IDs — guaranteed unique.

### Team Card Generation Requires Playwright
The `generateAllCards` method uses `Playwright.create()` / `pw.chromium().launch()` [VERIFIED: TeamCardService.java:95-106]. Playwright Chromium must be installed for this to succeed. The seed method should handle `IOException` gracefully (log warning, do not fail startup) so dev profile starts even if Playwright is not installed.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Team card image generation | Custom image generation code | `TeamCardService.generateAllCards(Season)` — already exists |
| Sub-team color inheritance | Custom color logic | `subTeam(name, shortName, parent)` helper in TestDataService (inherits all 3 colors from parent) |
| FK ordering for seed | Custom dependency resolution | Follow existing call order: teams before seasons before drivers |

---

## Common Pitfalls

### Pitfall 1: Breaking E2E Tests by Changing Test-Prefix Entities
**What goes wrong:** Modifying `seedRaceLineups()` or the test-prefixed data it creates.
**Why it happens:** The method is in the same class and uses the same `driver()` helper.
**How to avoid:** Do NOT touch `seedRaceLineups()` — only change `seedTeams()`, `seedSubTeams()`, `seedDrivers()`, `seedAliases()`, `seedSeasonDrivers()`.
**Warning signs:** Any edit that touches `testAlpha`, `testBravo`, `Test_Alpha_`, `Test_Bravo`, `Test-Season`.

### Pitfall 2: Breaking Scoring Preset Names
**What goes wrong:** Renaming or removing the "CTC Standard" / "Standard 3-1-0" scoring presets.
**Why it happens:** `seedScorings()` creates them; E2E tests reference them by exact label.
**How to avoid:** Leave `seedScorings()` unchanged.
**Warning signs:** Any grep for "CTC Standard" or "Standard 3-1-0" in E2E tests.

### Pitfall 3: TeamCardService Not Injected in TestDataService
**What goes wrong:** `TeamCardService` is not currently a dependency of `TestDataService`.
**Why it happens:** Phase adds a new `seedTeamCards()` step that requires `TeamCardService`.
**How to avoid:** Add `private final TeamCardService teamCardService;` to TestDataService constructor injection (Lombok `@RequiredArgsConstructor` handles it) and add to constructor list.
**Warning signs:** Compile error on `teamCardService.generateAllCards(...)`.

### Pitfall 4: Card Generation Fails Without Active Season
**What goes wrong:** `generateAllCards(Season)` requires a persisted `Season` with `SeasonTeam` entries whose team IDs are non-null.
**Why it happens:** Seasons are saved at the end of `seedSeasons()`, but `generateAllCards` is called after, so the active season is available.
**How to avoid:** Return the active season from `seedSeasons()` and pass it to `seedTeamCards()`. Call `seasonRepository.findAll()` in `seedTeamCards()` to fetch it with its `SeasonTeam` collection loaded, or pass the season directly from `seedSeasons()`.

### Pitfall 5: IOException from Playwright Not Crashing Dev Startup
**What goes wrong:** If Playwright Chromium is not installed, `generateAllCards` throws `IOException`, which propagates up through `seed()` and could crash `DevDataSeeder.run()`.
**Why it happens:** `TeamCardService.generateCard` uses `Playwright.create()` which throws if browser not available.
**How to avoid:** Wrap `seedTeamCards()` in try-catch, log a warning, and continue — team cards are nice-to-have, not required for dev startup.

### Pitfall 6: Short Names Must Remain Unique
**What goes wrong:** Duplicate `shortName` values cause confusion in season lookups.
**Why it happens:** `seedSeasons()` and `seedSeasonDrivers()` look up teams by shortName.
**How to avoid:** Each team (parent and sub-team) must have a unique shortName. Current pattern: sub-teams append a suffix (e.g., "VRX 1", "VRX 2").

### Pitfall 7: Short Names 3-4 Characters (D-02)
**What goes wrong:** Using 5+ character short names violates D-02.
**Why it happens:** Easy to overlook when naming sub-teams (e.g., "AHR 1" = 5 chars).
**How to avoid:** Check: all parent short names ≤ 4 chars. Sub-team short names may be 4-6 chars with suffix (current code has "CLR 1", "TNR A", "P1Rx" — these are 4-5 chars). The D-02 constraint says "3-4 chars" for the base short name. Sub-teams can append a space + suffix.

---

## Code Examples

### Existing team() and subTeam() helpers (VERIFIED: TestDataService.java)
```java
// Parent team
private Team team(String name, String shortName, String primary, String secondary, String accent) {
    var t = new Team(name, shortName);
    t.setPrimaryColor(primary);
    t.setSecondaryColor(secondary);
    t.setAccentColor(accent);
    return t;
}

// Sub-team inheriting parent colors
private Team subTeam(String name, String shortName, Team parent) {
    return subTeam(name, shortName, parent,
        parent.getPrimaryColor(), parent.getSecondaryColor(), parent.getAccentColor());
}

// Sub-team with color variation
private Team subTeam(String name, String shortName, Team parent,
                     String primary, String secondary, String accent) {
    var t = new Team(name, shortName, parent);
    t.setPrimaryColor(primary);
    t.setSecondaryColor(secondary);
    t.setAccentColor(accent);
    return t;
}
```

### New seedTeamCards() pattern to add
```java
private void seedTeamCards(Season activeSeason) {
    try {
        var paths = teamCardService.generateAllCards(activeSeason);
        log.info("Generated {} team cards for active season", paths.size());
    } catch (IOException e) {
        log.warn("Team card generation failed (Playwright not installed?): {}", e.getMessage());
    }
}
```

### Injecting TeamCardService into TestDataService
```java
// Add to field declarations in TestDataService:
private final TeamCardService teamCardService;
// Lombok @RequiredArgsConstructor picks it up automatically
```

### Driver pattern for 10 drivers per team (fictive)
```java
// PSN IDs: <ShortName>_Driver01..10, Nicknames: first + last names
driver("VRX_Driver01", "Marco Ferretti");
driver("VRX_Driver02", "Sophie Laurent");
// ... 8 more
```

### Returning active season from seedSeasons()
```java
// Change return type of seedSeasons() from void to Season
private Season seedSeasons(List<Team> parentTeams, ScoringDefaults scorings) {
    // ... existing season creation logic ...
    var activeSeason = createSeason(...);
    activeSeason.setActive(true);
    seasonRepository.save(activeSeason);
    // ...
    return activeSeason;
}

// In seed():
var activeSeason = seedSeasons(teams, scorings);
// ...
seedTeamCards(activeSeason);
```

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito |
| Config file | pom.xml (Surefire/Failsafe) |
| Quick run command | `./mvnw test -pl . -Dtest=TestDataService*` |
| Full suite command | `./mvnw verify` |
| E2E suite command | `./mvnw verify -Pe2e` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DATA-01 | Dev profile creates 14+ teams with fictive names and parent/sub-team structure | Integration test | `./mvnw test -Dtest=TestDataServiceIntegrationTest` | ❌ Wave 0 |
| DATA-02 | Each team has exactly 10 drivers with fictive names | Integration test | `./mvnw test -Dtest=TestDataServiceIntegrationTest` | ❌ Wave 0 |
| DATA-03 | Team cards generated for all dev teams | E2E (Playwright required) | `./mvnw verify -Pe2e -Dtest=AdminWorkflowE2ETest` | ✅ (existing test covers card generation flow) |

### Sampling Rate
- **Per task commit:** `./mvnw test`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** `./mvnw verify -Pe2e` before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java` — covers DATA-01, DATA-02 (counts teams, verifies parent/sub structure, counts drivers per team)

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Maven wrapper (`./mvnw`) | Build & test | ✓ | (project) | — |
| Java 25 | Runtime | ✓ | (project) | — |
| Playwright Chromium | DATA-03 team card generation at seed time | Unknown — runtime availability depends on install | — | Log warning and skip card generation; cards can be regenerated manually via UI |

**Missing dependencies with fallback:**
- Playwright Chromium: If not installed, `seedTeamCards()` must catch `IOException` and log a warning rather than failing startup. Cards can be generated afterward via the Team Cards admin UI.

---

## Security Domain

This phase makes no security-relevant changes. It operates purely on dev-profile seed data with no auth, no external input, and no HTTP endpoints added or modified.

| ASVS Category | Applies | Note |
|---------------|---------|------|
| V5 Input Validation | No | Data is hardcoded constants, not user input |
| All others | No | Dev-only seed data, no prod impact |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `seedSeasons()` can be refactored to return the active `Season` without breaking anything | Architecture Patterns | Low — method is private and only called once from `seed()` |
| A2 | Playwright Chromium is not guaranteed to be installed in all dev environments | Environment Availability | Low — handled by try-catch with graceful fallback |

---

## Open Questions

1. **Should `seedTeamCards()` use `generateAllCards(Season)` or iterate and call `generateCard(SeasonTeam)` directly?**
   - What we know: `generateAllCards` skips parent teams that have sub-teams in the season (by design — cards are per playing team, not per parent)
   - What's unclear: For the dev season, whether parent teams with sub-teams should get cards
   - Recommendation: Use `generateAllCards(activeSeason)` — it implements the correct skip logic already.

2. **Logo handling: copy classpath logos or leave null?**
   - What we know: `copyDemoLogos()` copies from `classpath:demo/team-logos/<shortName>.png` — only if the file exists. New fictive teams will not have classpath logos.
   - What's unclear: Whether to provide placeholder logos for fictive teams
   - Recommendation: Leave `copyDemoLogos()` in place but it will silently skip teams without classpath logos. Team card generation works without a logo (logo becomes null in the template). This is Claude's discretion per CONTEXT.md.

---

## Sources

### Primary (HIGH confidence)
- `src/main/java/org/ctc/admin/TestDataService.java` — full seeding infrastructure, helper methods, call order [VERIFIED: file read]
- `src/main/java/org/ctc/admin/DevDataSeeder.java` — @Profile("dev") runner [VERIFIED: file read]
- `src/main/java/org/ctc/admin/service/TeamCardService.java` — generateAllCards(Season) signature and Playwright usage [VERIFIED: file read]
- `src/main/java/org/ctc/domain/model/Team.java` — constructors, fields [VERIFIED: file read]
- `src/main/java/org/ctc/domain/model/Driver.java` — psnId unique constraint [VERIFIED: file read]
- `src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java` — E2E test dependencies on seed data names [VERIFIED: file read]
- `src/test/java/org/ctc/e2e/ScoringE2ETest.java` — scoring preset name dependencies [VERIFIED: grep]

### Secondary (MEDIUM confidence)
None required — all findings verified from codebase directly.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new libraries, all infrastructure verified from codebase
- Architecture: HIGH — call chain, entity structure, and E2E test dependencies all verified from source files
- Pitfalls: HIGH — each pitfall is grounded in actual code (unique constraint, E2E test exact-name lookups, IOException from Playwright)

**Research date:** 2026-04-09
**Valid until:** Stable — no external dependencies; valid until TestDataService or TeamCardService is refactored
