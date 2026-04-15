# Phase 22: Dev Teams & Drivers - Context

**Gathered:** 2026-04-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Seed the dev profile with 14+ fictive teams (including parent teams with sub-teams), 10 drivers per team with fictive names, and generate team card images for all teams. This replaces the existing real CTC team data in TestDataService with entirely fictive data.

</domain>

<decisions>
## Implementation Decisions

### Team Naming
- **D-01:** Use racing-themed fictive names (e.g., "Velocity Racing", "Shadow Grid Motorsport") — not real CTC team names and not generic test names like "Team A"
- **D-02:** Each team needs shortName (3-4 chars), primaryColor, secondaryColor, accentColor — matching existing Team entity structure

### Sub-Team Structure
- **D-03:** 2-3 parent teams get sub-teams, each with 2-3 sub-teams (minimum 2 parents with 2+ sub-teams per ROADMAP SC-2)
- **D-04:** Sub-teams inherit parent's color scheme with variations (existing pattern in TestDataService)

### Driver Naming
- **D-05:** Use realistic-sounding fictive names (first + last name) — no real CTC driver names
- **D-06:** Exactly 10 drivers per team as per ROADMAP SC-3

### Team Card Generation
- **D-07:** Generate team cards at seed time during DevDataSeeder startup — not on-demand
- **D-08:** Use existing TeamCardService and Playwright infrastructure for generation

### Data Replacement Strategy
- **D-09:** Replace existing real team/driver data in TestDataService.seed() with fictive data
- **D-10:** Keep the same seeding structure (seedTeams → seedSubTeams → seedDrivers → etc.) but with fictive content
- **D-11:** Existing E2E test data in TestDataService that uses test-prefixed entities (T-ALF, Test_Alpha_1) must remain isolated — only replace the non-test seed data

### Claude's Discretion
- Specific team names, colors, and driver names — as long as they are fictive, racing-themed, and cover required structural variations
- Whether to generate logos programmatically or use placeholder images
- Order of seeding operations (must respect FK constraints)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing Code
- `src/main/java/org/ctc/admin/TestDataService.java` — Current seed data service, must be refactored with fictive data
- `src/main/java/org/ctc/admin/DevDataSeeder.java` — Dev profile CommandLineRunner that calls TestDataService.seed()
- `src/main/java/org/ctc/domain/model/Team.java` — Team entity with parentTeam/subTeams, colors, logoUrl
- `src/main/java/org/ctc/domain/model/Driver.java` — Driver entity
- `src/main/java/org/ctc/admin/service/TeamCardService.java` — Playwright-based team card generation

### Project Specs
- `CLAUDE.md` — Project conventions, test data isolation rules, TDD approach

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `TestDataService` — Full seeding infrastructure (teams, sub-teams, drivers, seasons, scorings, lineups)
- `TeamCardService` — Playwright-based card generation, already tested
- `DevDataSeeder` + `DemoDataSeeder` — Profile-based startup runners
- Team constructor: `new Team(name, shortName)` and `new Team(name, shortName, parentTeam)`
- Team helper methods: `team()` and `subTeam()` already exist in TestDataService

### Established Patterns
- DevDataSeeder runs on `dev` profile, calls TestDataService.seed()
- DemoDataSeeder runs on `demo` profile (GT7 car/track import), runs after DevDataSeeder
- TestDataService checks `seasonRepository.count() > 0` before seeding (idempotent)
- Sub-teams created via `subTeam()` helper with color inheritance
- Team card generation available via TeamCardService using Playwright

### Integration Points
- `TestDataService.seed()` — main entry point for dev profile data
- `TeamCardService.generateCard(team)` — team card generation
- Team list page shows teams with card images
- E2E tests use separate test-prefixed entities — must not be affected

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches for fictive data generation.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 22-dev-teams-drivers*
*Context gathered: 2026-04-09*
