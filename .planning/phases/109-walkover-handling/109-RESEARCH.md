# Phase 109: Walkover Handling — Research

**Researched:** 2026-05-30
**Domain:** Spring Boot 4.x / JPA entity extension, Flyway SQL migration, StandingsService scoring branch, MatchController edit flow, Thymeleaf template label rendering, Playwright-rendered graphic templates
**Confidence:** HIGH — all findings grounded in direct file reads with line numbers

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Persist walkover via new nullable FK column `walkover_team_id` (→ `teams`) on `matches`. Added by Flyway **V17** (single ALTER, H2 + MariaDB compatible; V1–V16 immutable).
- **D-02:** `walkover_team_id` references the **non-competing team (the forfeiter)**. The opponent receives the auto-win.
- **D-03:** Walkover stays **separate** from `bye`. No enum/`byeType` unification.
- **D-04:** JPA mapping should follow the existing `homeTeam`/`awayTeam` convention (`@ManyToOne(fetch = LAZY)`). DB column is `walkover_team_id` either way.
- **D-05:** Auto-win awarded **at standings read-time** in `StandingsService.processMatch()`. `homeScore`/`awayScore` stay `null`. No change to `ScoringService.aggregateMatchScores()`.
- **D-06:** In `processMatch()`, when `walkover_team_id` is set: **opponent** gets `addWin()` + `addMatchPoints(pointsWin)`; **forfeiter** gets `addLoss()` + 0 match points.
- **D-07:** Walkover affects **only Win/Loss + match points** — no synthetic point difference, no Buchholz contribution.
- **D-08:** Walkover branch **takes precedence** in `processMatch()` — ignores `homeScore`/`awayScore` and race results for standings. Results are **not deleted**.
- **D-09:** Walkover marked on **existing match edit form** (`match-form-edit.html`, `MatchForm` DTO, `MatchController` edit endpoint).
- **D-10:** Single dropdown: `Kein Walkover` (= `null`), Home team, Away team.
- **D-11:** Service-level validation: selected team ∈ {home, away}; a `bye` match cannot also be walkover → `BindingResult` error + `errorMessage` flash.
- **D-12:** "w/o" label appears **next to the forfeiter** (the team in `walkover_team_id`).
- **D-13:** Standings views: **both** `admin/matchday-detail.html` **and** `site/standings.html`.
- **D-14:** Graphics: **all three** — `match-results-render`, `lineup-render`, `provisional-scores-render`. Build additively on Phase 108 baseline.
- **D-15:** Races remain unchanged when walkover is set. `aggregateMatchScores`/`recomputeMatchScoresFromAllLegs` already skip races without results.
- **D-16:** **No walkover-specific Discord behaviour in this phase.**

### Claude's Discretion
- Exact JPA mapping of `walkover_team_id` (raw UUID vs. `@ManyToOne`) — follow `Match` home/away convention (D-04).
- CSS class naming for the "w/o" badge — mirror `.match-bye` / Phase-108 empty-state styling.
- Whether a `.sql` migration suffices for V17 — confirmed below: plain `.sql` is correct.

### Deferred Ideas (OUT OF SCOPE)
- Richer walkover model (dedicated points config, forfeit reasons).
- Walkover-specific Discord handling (D-16).
- Destructive walkover variants (deleting race results/races on walkover).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| WO-01 | Team that does not compete is handled analogously to `Match.bye` — opponent receives auto-win with full match points | `StandingsService.processMatch()` bye branch (line 332) is the exact pattern to mirror; `addWin()` + `addMatchPoints(matchScoring.getPointsWin())` |
| WO-02 | Walkover state persisted via new Flyway migration (H2 + MariaDB compatible; existing migrations untouched) | V17 is next (V16 is current); single `ALTER TABLE matches ADD COLUMN walkover_team_id UUID NULL` + FK constraint is H2/MariaDB compatible — confirmed by V10/V11 pattern |
| WO-03 | Visible "w/o" label in standings and relevant graphics | `matchday-detail.html` line 57 `.match-bye` is the admin pattern; `site/standings.html` has no existing bye label — walkover label requires a new `TeamStanding` flag or context variable; graphics require a `walkoverTeamId` context variable in each of the three render services |
| WO-04 | Admin can mark a match as walkover through UI/form | `MatchController.edit` GET (lines 101–113) + `saveEdit` POST (lines 116–129) + `MatchForm` DTO + `MatchService.updateDiscordFields` (lines 153–181) — the edit flow and pattern are fully established |
</phase_requirements>

---

## Summary

Phase 109 adds walkover handling as a close analogue to the existing `bye` feature. All seven touch-points are clear from direct code inspection:

**V17 migration** is a single `ALTER TABLE` statement — the same pattern as V11 (one nullable timestamp column). No Java dialect-aware migration is needed; the FK syntax `REFERENCES teams(id)` is identical between H2 2.x and MariaDB 10.7+. Column name: `walkover_team_id UUID NULL`.

**Match entity** gains one `@ManyToOne(fetch = LAZY)` field `walkoverTeam` with `@JoinColumn(name = "walkover_team_id")`, and must be added to the `@ToString(exclude=...)` list (currently `{"matchday", "homeTeam", "awayTeam", "races", "discordChannelWebhookUrl"}`).

**StandingsService.processMatch()** currently has a two-branch structure: bye branch first (lines 332–339), then score-null guard (line 342), then score arithmetic. The walkover branch inserts **between** the bye branch and the score-null guard, taking precedence over partial scores per D-08. Forfeiter identification: compare `match.getWalkoverTeam().getId()` with `match.getHomeTeam().getId()` to determine which standing gets `addWin()` vs `addLoss()`. Both teams resolve via `resolveTeamId()` and must be present in `standingsMap`.

**ScoringService** requires **no change**. Both `aggregateMatchScores()` (line 127) and `recomputeMatchScoresFromAllLegs()` (line 62) already guard `race.isBye()` and skip empty-result races — walkover races have no results and thus are already skipped.

**Admin UI** — `MatchController.edit` GET builds a `MatchForm` and passes the `match` entity to the model (OSIV is active). `MatchForm` needs one new field `walkoverTeamId` (UUID). `match-form-edit.html` gets a new `<select>` dropdown. `MatchService` needs a new `updateWalkover(UUID matchId, UUID walkoverTeamId, BindingResult)` method (or extend `updateDiscordFields` — but keeping walkover in its own method is cleaner because the validation logic is distinct). `MatchController.saveEdit` POST calls that method before the existing redirect.

**Graphics services** (`MatchResultsGraphicService`, `LineupGraphicService`, `ProvisionalScoresGraphicService`) are JaCoCo-excluded (Playwright runtime). They each have a `Context` variable pattern; a `walkoverTeamId` variable is set, and the corresponding `.html` render templates add a "w/o" badge next to the forfeiter's team card slot.

**Standings templates** — `matchday-detail.html` already renders a match-score-area with the `match.bye` → `.match-bye` pattern; the walkover label mirrors this inline using `match.walkoverTeam != null`. For `site/standings.html`, the standings table renders `TeamStanding` rows — there is no per-match rendering, only aggregated W/D/L. The "w/o" label must be surfaced differently: either via a `hasWalkover` flag on `TeamStanding` (populated during `processMatch()`) or via a new `walkoverCount` counter. A `hasWalkover` boolean flag on `TeamStanding` is the minimal, correct approach (mirroring the existing `getPlayed()` pattern).

**Primary recommendation:** Implement the walkover branch in `processMatch()` first, then the V17 migration + entity field, then the admin UI form changes, then the template "w/o" labels, in that TDD order.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Walkover persistence (V17 FK column) | Database / Flyway | — | Schema change; pure SQL ALTER |
| Walkover JPA mapping | Backend entity (`Match`) | — | `@ManyToOne` field following existing convention |
| Auto-win scoring at read-time | Backend service (`StandingsService`) | — | Standings are derived at request time, not stored — same as bye |
| Admin marking UI | Frontend Server (SSR) + Backend (MatchController + MatchService) | — | Form DTO → service → repository pattern |
| "w/o" label in admin matchday view | Frontend Server (SSR, Thymeleaf) | — | Template accesses `match.walkoverTeam` directly via OSIV |
| "w/o" flag in public standings | Backend service (`StandingsService.TeamStanding`) + SSR | — | Flag must be computed during score traversal, not in template |
| "w/o" label in graphics | Playwright-rendered templates (graphic services) | — | Context variable injection; no new service logic |

---

## Standard Stack

This phase uses only the existing project stack — no new library dependencies.

### Core (existing)
| Component | Version | Purpose |
|-----------|---------|---------|
| Spring Boot 4.x | current | MVC, JPA, Thymeleaf |
| JPA / Hibernate | Spring-managed | Entity + repository |
| Flyway | Spring-managed | V17 SQL migration |
| Thymeleaf | Spring-managed | Server-side template rendering |
| H2 / MariaDB | both | H2 for dev/test; MariaDB for local/prod |

### No new packages
No external packages are added in this phase.

## Package Legitimacy Audit

No external packages are added. Section not applicable.

---

## Architecture Patterns

### System Architecture Diagram

```
Admin POST /admin/matches/{id}/save-edit
        │
        ▼
MatchController.saveEdit()                  ← thin HTTP handler
        │ calls
        ▼
MatchService.updateWalkover(id, walkoverTeamId)
        │ validates: team ∈ {home, away}, not bye
        │ sets match.walkoverTeam = team (or null)
        ▼
matchRepository.save(match)                 ← persists walkover_team_id FK

--- READ-TIME SCORING ---

GET standings / graphic generation
        │
        ▼
StandingsService.calculateStandings(phaseId, groupId)
        │ for each match:
        ▼
StandingsService.processMatch(match, standingsMap, matchScoring, successionMap)
        │
        ├─ isBye?         → homeTeam addWin/addMatchPoints (existing)
        │
        ├─ isWalkover?    → forfeiter addLoss(); opponent addWin()+addMatchPoints(pointsWin)
        │                   ALSO: forfeiter.hasWalkover = true (for template label)
        │
        └─ has scores?    → normal arithmetic (existing)

--- TEMPLATE RENDERING ---

admin/matchday-detail.html
        └─ match.walkoverTeam != null → show "w/o" badge next to forfeiter

site/standings.html
        └─ s.hasWalkover → show "(w/o)" note next to forfeiter's row

match-results-render.html / lineup-render.html / provisional-scores-render.html
        └─ walkoverTeamId variable → forfeiter team card slot shows "w/o" overlay
```

### Recommended Project Structure

No new top-level directories. Changes are to existing files only.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| DB UUID FK | Custom string column | `@ManyToOne @JoinColumn(nullable=true)` | JPA FK handling is proven; UUID is the entity ID type |
| Validation "team ∈ {home, away}" | Template-level check | Service-level + BindingResult | Templates must stay lean (CLAUDE.md); service validation survives non-HTML callers |
| Score calculation | Custom formula at save-time | `StandingsService.processMatch()` extension | Consistency with bye — derived at read-time, no DB writes |

---

## Key Research Findings

### 1. Flyway V17 Migration

**VERIFIED** by reading V1–V16 migration files.

**File name:** `V17__add_matches_walkover_team_id.sql`

**Pattern reference:** V11 (line 1) adds a single nullable column with one `ALTER TABLE` statement. V10 (lines 8–14) adds multiple nullable columns. Neither uses a Java migration — H2 2.x and MariaDB 10.7+ both support `ALTER TABLE … ADD COLUMN … UUID NULL REFERENCES … (id)` with inline FK constraint syntax.

**H2/MariaDB FK syntax check:** The V1 initial schema (lines 148–161) uses `CONSTRAINT fk_match_home_team FOREIGN KEY (home_team_id) REFERENCES teams(id)` on `CREATE TABLE`. The `ALTER TABLE` FK syntax works in H2 2.x and MariaDB. Best practice for H2 compatibility: use a named `CONSTRAINT` in the `ALTER TABLE` statement, not inline. However, V10/V11 use bare column adds without FK constraints in the ALTER — the FK constraint was added in V1 CREATE. For `walkover_team_id`, since it is a new column, the constraint must be added explicitly. Safe approach:

```sql
ALTER TABLE matches ADD COLUMN walkover_team_id UUID NULL;
ALTER TABLE matches ADD CONSTRAINT fk_match_walkover_team
    FOREIGN KEY (walkover_team_id) REFERENCES teams(id);
```

Both statements work identically on H2 2.x and MariaDB 10.7+. A single `ADD COLUMN ... REFERENCES ...` inline syntax also works in both engines for simple FKs, but using two statements (column first, constraint second) is safer and matches the V1 pattern.

**Important:** V10 has Phase/Plan/Task comment pollution (lines 1–7) that violates CLAUDE.md "No Comment Pollution". V17 must NOT include such comments — the header must be omitted entirely.

**Recommended V17 content:**
```sql
ALTER TABLE matches ADD COLUMN walkover_team_id UUID NULL;
ALTER TABLE matches ADD CONSTRAINT fk_match_walkover_team
    FOREIGN KEY (walkover_team_id) REFERENCES teams(id);
```

No Java dialect-aware migration (like V5) is needed. A plain `.sql` file suffices.

---

### 2. Match Entity Change

**VERIFIED** by reading `Match.java` (full file, lines 1–80).

**Current entity state (relevant fields):**
- Line 19: `@ToString(exclude = {"matchday", "homeTeam", "awayTeam", "races", "discordChannelWebhookUrl"})`
- Lines 33–38: `homeTeam` — `@ManyToOne(fetch = LAZY)` + `@JoinColumn(name = "home_team_id", nullable = false)`
- Lines 36–38: `awayTeam` — `@ManyToOne(fetch = LAZY)` + `@JoinColumn(name = "away_team_id")` (nullable — no `nullable = false`)
- Line 44: `bye` — `@Column(nullable = false)` primitive boolean

**New field to add** (following `awayTeam` convention exactly):
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "walkover_team_id")
private Team walkoverTeam;
```

**`@ToString` update:** Add `"walkoverTeam"` to the exclude list to prevent lazy-load issues in logging:
```java
@ToString(exclude = {"matchday", "homeTeam", "awayTeam", "walkoverTeam", "races", "discordChannelWebhookUrl"})
```

**OSIV implications:** OSIV is active (`spring.jpa.open-in-view=true`). Templates can safely access `match.walkoverTeam.shortName` without N+1 workarounds — the session is open for the full HTTP request. No `@EntityGraph` change is needed for the edit form (the match is loaded by ID in `MatchController.edit` at line 103 — lazy loading works within the OSIV session).

---

### 3. StandingsService.processMatch() Extension

**VERIFIED** by reading `StandingsService.java` lines 330–388.

**Current `processMatch()` flow:**
1. Line 332: `if (match.isBye())` → awards homeTeam a win; returns
2. Line 342: `if (match.getHomeScore() == null || match.getAwayScore() == null)` → returns (skip unscored)
3. Lines 346–387: score arithmetic (win/loss/draw)

**Walkover branch insertion:** Between step 1 and step 2, add:

```java
if (match.getWalkoverTeam() != null) {
    UUID forfeiterTeamId = resolveTeamId(match.getWalkoverTeam().getId(), successionMap);
    UUID homeId = resolveTeamId(match.getHomeTeam().getId(), successionMap);
    UUID opponentId = forfeiterTeamId.equals(homeId)
            ? resolveTeamId(match.getAwayTeam().getId(), successionMap)
            : homeId;
    var forfeiterStanding = standingsMap.get(forfeiterTeamId);
    var opponentStanding = standingsMap.get(opponentId);
    if (forfeiterStanding != null) {
        forfeiterStanding.addLoss();
        forfeiterStanding.setHasWalkover(true);
    }
    if (opponentStanding != null) {
        opponentStanding.addWin();
        opponentStanding.addMatchPoints(matchScoring.getPointsWin());
    }
    return;
}
```

**Precedence:** This branch executes BEFORE the score-null guard (D-08 satisfied) — even if `homeScore`/`awayScore` were already set from pre-walkover race results, the walkover branch takes over.

**TeamStanding changes needed:**
- Add `private boolean hasWalkover;` field
- Add `public void setHasWalkover(boolean hasWalkover) { this.hasWalkover = hasWalkover; }`
- Add `public boolean isHasWalkover() { return hasWalkover; }`
- Update `merge()` method to OR the flag: `this.hasWalkover = this.hasWalkover || other.hasWalkover;`

**`getPlayed()` check:** `getPlayed() = wins + draws + losses` (line 458). Both forfeiter (`addLoss()` → losses=1) and opponent (`addWin()` → wins=1) get played=1, so neither is filtered by `standings.removeIf(s -> s.getPlayed() == 0)` (line 175). Correct.

**`calculateStandingsWithBuchholz()` path:** Calls `calculateStandings()` (line 204) which calls `processMatch()` — the walkover branch is automatically picked up. No separate change needed in the Buchholz path.

**`isMatchdayScheduleStale()` method:** Line 57 already skips bye matches (`if (match.isBye()) continue`). Walkover matches are NOT byes — they have races and Discord channels and can have schedule datetime on their races. No skip of walkover matches here is appropriate (the schedule staleness check should still consider walkover match updates).

**Buchholz opponent map** (`calculateBuchholzScoresForPhase`, lines 305–316): Builds from races, not matches. Race-level `race.isBye()` check at line 309 skips bye races. Walkover match races are real (not bye races), but they have no results — they show up in the race list but contribute no Buchholz because Buchholz is built from race presence, not results. Since both teams have a race (just unscored), they would appear as opponents of each other. This is correct per D-07: no Buchholz contribution — but note the Buchholz map is built from races, not from walkover state. However, because `calculateBuchholzScoresForPhase` uses `raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull` (line 306) which returns actual Race entities, and walkover match races exist (they were created at match creation), they will appear. The sum-of-opponent-points Buchholz calculation will still use those teams' real points from `processMatch()` — the walkover opponent and forfeiter will have 3 pts and 0 pts respectively, which is correct input to Buchholz. No special handling is needed.

---

### 4. ScoringService — No Change Needed

**VERIFIED** by reading `ScoringService.java` lines 62–186.

- `recomputeMatchScoresFromAllLegs()` (line 62): Guards `if (race.isBye()) return` (line 63). Then iterates legs and skips `if (leg.getResults().isEmpty()) continue` (line 77). Walkover match races have no results → skipped. No `homeScore`/`awayScore` change is written for walkover. This is correct per D-05 (scores stay null) — but note that `recomputeMatchScoresFromAllLegs` writes `match.setHomeScore(matchHome)` where `matchHome = 0` if all legs empty. **This is a concern:** if a walkover match's legs are all empty, `recomputeMatchScoresFromAllLegs` would set `homeScore=0, awayScore=0` (not null). Then `processMatch()` would see non-null scores and skip the walkover branch!

  **Critical fix:** `recomputeMatchScoresFromAllLegs()` and `aggregateMatchScores()` need a walkover guard:
  ```java
  if (race.getMatch() != null && race.getMatch().getWalkoverTeam() != null) {
      return; // walkover match — scores must stay null for processMatch() to award correctly
  }
  ```
  This guard is analogous to the existing `race.isBye()` guard and is necessary for D-08 correctness.

- `aggregateMatchScores()` (line 127): Guards `if (race.getResults().isEmpty()) return` (line 128) and `if (race.isBye()) return` (line 131). For walkover matches with no results, `getResults().isEmpty()` is true → method returns immediately at line 128. **No write occurs.** So `aggregateMatchScores()` is actually safe for walkover matches. But `recomputeMatchScoresFromAllLegs()` is NOT — it writes `homeScore=0, awayScore=0` even when all legs are empty (the loop computes 0+0=0 for each leg and writes it). The walkover guard in `recomputeMatchScoresFromAllLegs()` is required.

**CLAUDE.md rule "Score Aggregation on Result Save":** After every `raceRepository.save(race)` that writes results, call `scoringService.aggregateMatchScores(race)`. For walkover match races, results are never written — so this rule never fires. No impact.

---

### 5. Admin UI — MatchController Edit Flow

**VERIFIED** by reading `MatchController.java` (full), `MatchService.java` (full), `MatchForm.java` (full), `match-form-edit.html` (full).

**Current `edit` GET flow** (MatchController lines 101–113):
1. Load match by ID (`matchService.findById(id)`)
2. Build `MatchForm`, populate `id`, `discordTeaser`, `streamLink`, `lobbyHost`, `raceDirector`, `streamer`
3. Add `matchForm` + `match` to model
4. Return view `admin/match-form-edit`

**Current `saveEdit` POST flow** (lines 116–129):
1. Validate via `@Valid @ModelAttribute("matchForm") MatchForm form` + `BindingResult result`
2. If errors → re-show form (loads match entity via `matchService.findById(id)`)
3. Call `matchService.updateDiscordFields(id, form)`
4. Flash `successMessage`
5. Redirect to `/admin/matches/{id}`

**`MatchForm` current fields** (all lines): `id`, `discordTeaser` (@Size 2000), `streamLink` (@Size 500), `lobbyHost` (@Size 100), `raceDirector` (@Size 100), `streamer` (@Size 100)

**`MatchService.updateDiscordFields()`** (lines 153–181): Loads match, updates 5 discord fields, saves, publishes events if schedule/preview fields changed.

**Required changes:**

**`MatchForm`:** Add `walkoverTeamId` (UUID, no `@Size` — not a string):
```java
private UUID walkoverTeamId;  // null = no walkover; non-null = forfeiter team UUID
```

**`MatchController.edit` GET:** Also populate `form.setWalkoverTeamId(match.getWalkoverTeam() != null ? match.getWalkoverTeam().getId() : null)`. The `match` entity is already in the model for OSIV access — pass both `homeTeam` and `awayTeam` ids/names to the model for dropdown rendering, or just rely on `match` being available in the template.

**`MatchController.saveEdit` POST:** Add walkover update call. Two approaches:
- Option A: Extend `updateDiscordFields` to also handle `walkoverTeamId` (simple, one method call)
- Option B: Add a separate `matchService.updateWalkover(id, walkoverTeamId)` step

Option B is cleaner because the validation logic (`team ∈ {home, away}`, not-bye check) belongs in the service and may return validation errors that need to be surfaced via `BindingResult`. Recommended:

```java
// After the existing !result.hasErrors() check:
try {
    matchService.updateWalkover(id, form.getWalkoverTeamId(), result);
} catch (BusinessRuleException e) {
    redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    return "redirect:/admin/matches/" + id;
}
if (result.hasErrors()) {
    model.addAttribute("match", matchService.findById(id));
    return "admin/match-form-edit";
}
matchService.updateDiscordFields(id, form);
```

Actually, BindingResult must be passed before redirect. Simpler: validate in `MatchService.updateWalkover()` and throw `BusinessRuleException` on violation; controller catches it as errorMessage flash. No BindingResult propagation needed since no Jakarta Validation annotation on the UUID field.

**`MatchService.updateWalkover(UUID matchId, UUID walkoverTeamId)` method:**
```java
@Transactional
public void updateWalkover(UUID matchId, UUID walkoverTeamId) {
    Match match = findById(matchId);
    if (walkoverTeamId == null) {
        match.setWalkoverTeam(null);
        matchRepository.save(match);
        log.info("Cleared walkover for match {}", matchId);
        return;
    }
    if (match.isBye()) {
        throw new BusinessRuleException("A bye match cannot be marked as a walkover.");
    }
    Team homeTeam = match.getHomeTeam();
    Team awayTeam = match.getAwayTeam();
    if (awayTeam == null) {
        throw new BusinessRuleException("Match has no away team.");
    }
    if (!walkoverTeamId.equals(homeTeam.getId()) && !walkoverTeamId.equals(awayTeam.getId())) {
        throw new BusinessRuleException("Walkover team must be one of the match's two teams.");
    }
    Team walkoverTeam = teamRepository.findById(walkoverTeamId)
            .orElseThrow(() -> new EntityNotFoundException("Team", walkoverTeamId));
    match.setWalkoverTeam(walkoverTeam);
    matchRepository.save(match);
    log.info("Set walkover for match {}: forfeiter={}", matchId, walkoverTeam.getShortName());
}
```

**`MatchController.saveEdit` updated flow:**

```java
@PostMapping("/{id}/save-edit")
public String saveEdit(@PathVariable UUID id,
                       @Valid @ModelAttribute("matchForm") MatchForm form,
                       BindingResult result,
                       Model model,
                       RedirectAttributes redirectAttributes) {
    if (result.hasErrors()) {
        model.addAttribute("match", matchService.findById(id));
        return "admin/match-form-edit";
    }
    try {
        matchService.updateWalkover(id, form.getWalkoverTeamId());
    } catch (BusinessRuleException e) {
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        redirectAttributes.addFlashAttribute("errorCategory", "data-incomplete");
        return "redirect:/admin/matches/" + id;
    }
    matchService.updateDiscordFields(id, form);
    redirectAttributes.addFlashAttribute("successMessage", "Match details updated.");
    return "redirect:/admin/matches/" + id;
}
```

**`match-form-edit.html` dropdown:**

The form currently uses `th:object="${matchForm}"`. The dropdown needs to present three options: None, Home team, Away team. Because `match` is already in the model (populated in the `edit` GET handler), the dropdown can use `${match.homeTeam}` and `${match.awayTeam}`.

```html
<div class="form-group">
    <label for="walkoverTeamId">Walkover</label>
    <select id="walkoverTeamId" th:field="*{walkoverTeamId}">
        <option value="">Kein Walkover</option>
        <option th:value="${match.homeTeam.id}" th:text="${match.homeTeam.shortName + ' (Home)'}"></option>
        <option th:unless="${match.awayTeam == null}"
                th:value="${match.awayTeam.id}"
                th:text="${match.awayTeam.shortName + ' (Away)'}"></option>
    </select>
</div>
```

**Note:** Thymeleaf `th:field="*{walkoverTeamId}"` on a `<select>` automatically sets `selected` on the matching option when the UUID value equals. This uses Thymeleaf's standard `SelectTagProcessor` — works with UUID type when `ConversionService` is registered (which Spring Boot auto-registers).

---

### 6. Standings Templates — "w/o" Label

**VERIFIED** by reading `matchday-detail.html` (lines 39–104) and `standings.html` (full file).

**`admin/matchday-detail.html`:**

The match row (lines 39–69) currently handles bye via `th:if="${match.bye}"` to show a `<span class="match-bye">Bye</span>`. The walkover label goes in the **away-team name cell** (when forfeiter = away) or the **home-team name area** (when forfeiter = home). Since both teams are real and shown, the "w/o" badge should appear as an inline label next to the forfeiter's name, not replacing the score area.

Recommended placement: next to the team name in the match header, using a new `.match-wo` CSS class in `admin.css`:

```css
.match-wo {
    font-size: 13px;
    color: var(--text-dim);
    font-style: italic;
}
```

Template modification on `matchday-detail.html` (in the match-header div):
```html
<div class="match-team-name match-team-name--right">
    <span th:text="${match.homeTeam.shortName}"></span>
    <span th:if="${match.walkoverTeam != null and match.walkoverTeam.id == match.homeTeam.id}"
          class="match-wo">w/o</span>
</div>
<!-- ... score area (unchanged) ... -->
<div th:unless="${match.bye}" class="match-team-name">
    <span th:text="${match.awayTeam?.shortName}"></span>
    <span th:if="${match.walkoverTeam != null and match.walkoverTeam.id == match.awayTeam?.id}"
          class="match-wo">w/o</span>
</div>
```

**`site/standings.html`:**

The standings table (lines 39–51) shows aggregated `TeamStanding` rows. There is no per-match detail. The "w/o" label must appear in the team's row — logically next to the team name or as a small badge. Since `TeamStanding` gains a `hasWalkover` flag (from `processMatch()`), the template can use it:

```html
<td>
    <a class="entity-link font-bold" th:href="${teamSlugMap.get(s.team.id)}"
       th:text="${s.team.shortName}"></a>
    <span class="text-dim" th:text="' ' + ${s.team.name}"></span>
    <span th:if="${s.hasWalkover}" class="text-dim text-xs">(w/o)</span>
</td>
```

**Note:** The site CSS (`style.css`) has `text-dim` (line 338) but no `.text-xs`. Either add `.text-xs` to `style.css` or use inline Thymeleaf styling. Looking at the existing pattern — `admin.css` has `.match-bye` with `font-size: 13px`. The site template uses `class="text-dim"` throughout. The simplest approach is to use `class="text-dim"` alone, which renders in the site's dim gray color.

---

### 7. Graphics — "w/o" Label

**VERIFIED** by reading all three graphic services and their render templates.

**`MatchResultsGraphicService`** (lines 36–89):
- Line 37: throws if `homeTeam == null`
- Line 40: throws if `awayTeam == null` — walkover has BOTH teams, so this guard is not triggered
- Context variables set: `homeCardBase64`, `awayCardBase64`, `homeTotal`, `awayTotal`, `homeIsWinner`, `awayIsWinner`, `raceRows`
- `buildRaceRows()` (lines 91–113) skips races with empty results (`if (race.getResults().isEmpty()) continue`) — walkover races produce no rows

For a walkover match with no results, `raceRows` will be empty and `homeTotal`/`awayTotal` will both be 0. The footer already shows these totals. The "w/o" label needs to appear on the forfeiter's team card side.

**New context variable:** `ctx.setVariable("walkoverTeamId", match.getWalkoverTeam() != null ? match.getWalkoverTeam().getId().toString() : null)`

**`match-results-render.html` change:** In the footer section:
```html
<div class="team-total" th:classappend="${homeIsWinner ? 'winner' : ''}" th:text="${homeTotal}"></div>
```
Add a "w/o" indicator. Or, on the team card container:
```html
<div class="team-card-container">
    <img th:src="${homeCardBase64}" alt="Home Team Card">
    <div th:if="${walkoverTeamId != null and walkoverTeamId == homeTeam.id.toString()}" class="wo-badge">w/o</div>
</div>
```

Since the template renders in a closed Playwright browser (no Spring context), `${homeTeam.id}` would not be available unless set as a context variable. **Simpler approach:** add boolean context variables `homeIsWalkover` and `awayIsWalkover`:

In the service:
```java
boolean homeIsWalkover = match.getWalkoverTeam() != null
        && match.getWalkoverTeam().getId().equals(match.getHomeTeam().getId());
ctx.setVariable("homeIsWalkover", homeIsWalkover);
ctx.setVariable("awayIsWalkover", match.getWalkoverTeam() != null && !homeIsWalkover);
```

In the template, for the forfeiter's card container:
```html
<div th:if="${homeIsWalkover}" class="wo-badge">w/o</div>
```

CSS for graphic templates (inline `<style>` in the HTML):
```css
.wo-badge {
    font-size: 32px; font-weight: 700; color: var(--text-dim, #9a9aa6);
    text-align: center; margin-top: 8px; font-style: italic;
}
```

**`LineupGraphicService`** (lines 44–119):
- Line 46: throws if `homeTeam == null` or `awayTeam == null`
- Line 53: `lineups = raceLineupRepository.findByRaceId(race.getId())`
- Line 55: throws if `lineups.isEmpty()` — **this is a gating issue**: for a walkover match, lineups may not have been entered. The service should not throw for walkover races. However, `LineupGraphicService.generateLineup()` takes a `Race` not a `Match` — the walkover is on the `Match`. The service would need to check `race.getMatch().getWalkoverTeam() != null` and handle that case.

Actually, the lineup graphic is for a `Race` entity, not directly a `Match`. If an admin posts the lineup graphic for a walkover match's race (no lineup entries), the current service throws `IllegalStateException("No lineup entries for this race")`. The walkover guard should be added: if the race's match has a walkover, treat the forfeiter's side as empty (all "n/a" pairings, or inject "w/o" as the forfeiter name).

**New approach:** `generateLineup()` should check `race.getMatch().getWalkoverTeam()` and if set, still render both team cards but inject a "w/o" overlay on the forfeiter's side. Add boolean `homeIsWalkover`/`awayIsWalkover` context variables.

**`ProvisionalScoresGraphicService`** (lines 40–58):
- Line 41: throws if `race.getResults().isEmpty()` — **for a walkover race, results are empty.** The service would throw before checking walkover state.

The `DiscordPostService.matchHasProvisionalData()` (line 690) gates whether the provisional button is enabled. For walkover matches with no results, `matchHasProvisionalData()` returns false → the button is disabled → the service is never called through the normal UI. However, walkover-aware handling is still advisable for robustness. The planner can choose between: (a) add a walkover guard at the top of `generateProvisional()` to throw a more meaningful error, or (b) allow provisional rendering with empty rows and w/o badge. Option (a) is simpler and correct (walkover matches have no provisional data to show by definition).

All three graphic services are JaCoCo-excluded — no coverage obligation for the service class code. The templates ARE exercised by E2E tests if posted via Playwright, but no JaCoCo requirement.

---

### 8. ScoringService Walkover Guard (Critical)

**VERIFIED** by careful re-reading of `ScoringService.recomputeMatchScoresFromAllLegs()` lines 62–93.

The method:
1. Returns early if `race.isBye()` (line 63)
2. If match exists and `homeTeam != null`: iterates legs, sums points from empty legs (0+0=0), then calls `match.setHomeScore(0)` and `match.setAwayScore(0)` (line 89) → **saves 0,0 scores**

If `recomputeMatchScoresFromAllLegs()` is called for a walkover match leg (e.g., during a results-clear operation), it would write `homeScore=0, awayScore=0` to the match. Then `processMatch()` would see non-null scores (0 is not null) and fall through to the score-arithmetic branch, completely bypassing the walkover branch. This would award no points to either team.

**Required guard in `recomputeMatchScoresFromAllLegs()`:**
```java
if (race.isBye()) {
    return;
}
// NEW: walkover matches keep homeScore/awayScore null — scoring is read-time only
if (race.getMatch() != null && race.getMatch().getWalkoverTeam() != null) {
    return;
}
```

`aggregateMatchScores()` (line 127) already guards `if (race.getResults().isEmpty()) return` at line 128 — walkover races with no results return immediately. Safe. **No change needed in `aggregateMatchScores()`.**

---

### 9. Testing Strategy

**VERIFIED** by reading TESTING.md, StandingsServiceTest.java patterns, MatchControllerTest.java, V13MigrationIT.java, CtcDevSpringBootContext.java.

#### Unit Tests (no Spring context, Mockito)

**File:** `src/test/java/org/ctc/domain/service/StandingsServiceTest.java` — add `@Nested` inner class inside the existing test class (inherits `@ExtendWith(MockitoExtension.class) @MockitoSettings(LENIENT)`).

Required test cases (follow `givenByeMatch_whenCalculateStandings_thenTeamGetsWin` pattern at line 213):

1. `givenWalkoverMatchHomeForfeit_whenCalculateStandings_thenOpponentWinsAndForfeiterLoses()` — home team is walkover → away gets win+3pts, home gets loss
2. `givenWalkoverMatchAwayForfeit_whenCalculateStandings_thenOpponentWinsAndForfeiterLoses()` — away team is walkover → home gets win+3pts, away gets loss
3. `givenWalkoverMatchWithPartialScores_whenCalculateStandings_thenWalkoverTakesPrecedence()` — match has homeScore=70, awayScore=50 AND walkover set → walkover wins, not score
4. `givenWalkoverMatch_whenCalculateStandings_thenNoPointDifferenceRecorded()` — confirm pointsFor/pointsAgainst both 0
5. `givenWalkoverMatch_whenCalculateStandings_thenHasWalkoverFlagSet()` — forfeiter's `TeamStanding.isHasWalkover()` is true; opponent's is false

**File:** `src/test/java/org/ctc/domain/service/ScoringServiceTest.java` — add test for the walkover guard in `recomputeMatchScoresFromAllLegs()`:

6. `givenWalkoverMatchRace_whenRecomputeMatchScoresFromAllLegs_thenScoresRemainUnchanged()` — confirms guard fires, no score write

#### Integration Tests (@Tag("integration"), @SpringBootTest, H2)

**File:** `src/test/java/db/migration/V17MigrationIT.java` — follows V13MigrationIT.java pattern exactly:
```java
@CtcDevSpringBootContext
@Tag("integration")
class V17MigrationIT {
    @Autowired DataSource dataSource;

    @Test
    void givenH2WithV17Applied_whenInspectingMatchesWalkoverTeamIdColumn_thenExists()
    // Use DatabaseMetaData.getColumns("MATCHES", "walkover_team_id")

    @Test
    void givenH2WithV17Applied_whenInspectingWalkoverTeamIdNullability_thenNullable()
    // Verify NULLABLE = DatabaseMetaData.columnNullable
}
```

**File:** `src/test/java/org/ctc/admin/controller/MatchControllerTest.java` (existing file, add `@Nested` inner class or new test methods — file is `@SpringBootTest @ActiveProfiles("dev") @Transactional`):

7. `givenMatchEditForm_whenSaveEditWithWalkoverTeam_thenWalkoverPersisted()` — POST to `/admin/matches/{id}/save-edit` with `walkoverTeamId=<awayTeamId>`, verify redirect, verify `match.getWalkoverTeam() != null`
8. `givenByeMatch_whenSaveEditWithWalkoverTeam_thenErrorFlash()` — bye match → walkover POST → errorMessage flash
9. `givenWalkoverMatch_whenSaveEditWithNullWalkoverTeam_thenWalkoverCleared()` — clear walkover

#### E2E Tests (@Tag("e2e"), Playwright)

**File:** `src/test/java/org/ctc/e2e/WalkoverE2ETest.java` (new):

10. `givenMatchEditForm_whenMarkWalkover_thenWoLabelAppearsInMatchdayDetail()` — navigate to match edit, select away team as walkover, save, navigate to matchday-detail, verify "w/o" text appears next to the away team name in the match row.

**TestDataService / isolation:** E2E tests use `TestDataService` with `Test-`/`T-` prefix entities. The `TestDataService` will need no change for the walkover field (it defaults to null). The `TestHelper.createFullSeasonFixture()` provides a match with homeTeam+awayTeam for controller ITs.

#### Coverage

- `StandingsService.processMatch()` walkover branch: covered by unit tests
- `MatchService.updateWalkover()`: covered by controller IT
- `ScoringService.recomputeMatchScoresFromAllLegs()` guard: covered by unit test
- Graphic services: JaCoCo-excluded — no coverage obligation
- Templates: not JaCoCo-instrumented

Current JaCoCo baseline: 89.42% (≥82% gate). The new `updateWalkover()` method and `processMatch()` branch additions should be well-covered. The `hasWalkover` flag on `TeamStanding` is a plain field — covered implicitly by unit tests.

---

## Common Pitfalls

### Pitfall 1: Score-Null Guard Bypasses Walkover (CRITICAL)
**What goes wrong:** If `recomputeMatchScoresFromAllLegs()` is called on a walkover match race (e.g., result clear), it writes `homeScore=0, awayScore=0`. Then `processMatch()` sees non-null scores and falls through to the score-arithmetic branch, giving no points to either team.
**Why it happens:** `recomputeMatchScoresFromAllLegs()` iterates all legs, sums up 0+0=0, and unconditionally sets scores.
**How to avoid:** Add `if (race.getMatch() != null && race.getMatch().getWalkoverTeam() != null) return;` early in `recomputeMatchScoresFromAllLegs()`.
**Warning signs:** Unit test `givenWalkoverMatchWithPartialScores_whenCalculateStandings_thenWalkoverTakesPrecedence` fails, or standings show 0 pts for both teams on a walkover.

### Pitfall 2: Forfeiter Absent From Standings (removeIf filter)
**What goes wrong:** If the forfeiter only gets `addLoss()` but `getPlayed() = wins + draws + losses`, they have played=1 and are NOT filtered. Correct — but if someone accidentally doesn't call `addLoss()`, `getPlayed()=0` and the forfeiter disappears from standings.
**How to avoid:** Unit test case 1/2 above explicitly verifies `forfeiterStanding.getLosses() == 1`.

### Pitfall 3: Thymeleaf UUID Comparison in Template
**What goes wrong:** `th:if="${match.walkoverTeam.id == match.homeTeam.id}"` uses Thymeleaf SpEL `==` which does object-reference equality, not UUID value equality, for some Thymeleaf versions. UUIDs may be different objects with equal values.
**How to avoid:** Use `th:if="${match.walkoverTeam != null and match.walkoverTeam.id.equals(match.homeTeam.id)}"` (explicit `.equals()` call). Or compare string representations: `match.walkoverTeam.id.toString() == match.homeTeam.id.toString()` — but `.equals()` is clearest.

### Pitfall 4: @ToString Circular Reference
**What goes wrong:** If `walkoverTeam` is not added to the `@ToString(exclude=...)` list, Lombok generates a `toString()` that accesses the lazy-loaded `walkoverTeam` outside a session, causing `LazyInitializationException` in logs.
**How to avoid:** Add `"walkoverTeam"` to the `@ToString(exclude=...)` list in `Match.java`.

### Pitfall 5: Lineup Graphic Throws for Walkover Races
**What goes wrong:** `LineupGraphicService.generateLineup()` throws `IllegalStateException("No lineup entries for this race")` if the walkover match's race has no lineup entries (which is the expected state — the team didn't compete).
**How to avoid:** Add a guard: if `race.getMatch().getWalkoverTeam() != null`, skip the lineup-empty check and render the forfeiter's side with "w/o" placeholder. OR: don't expose the lineup button for walkover matches (UI-level guard in match-detail.html).

### Pitfall 6: Stale Succession Map for Walkover Team
**What goes wrong:** `walkoverTeam` may be a sub-team. `resolveTeamId(match.getWalkoverTeam().getId(), successionMap)` resolves it to its parent. But the opponent-resolution (`forfeiterTeamId.equals(homeId)`) compares the RESOLVED ID, not the raw ID. Ensure consistency.
**How to avoid:** Always resolve all four IDs (forfeiterRaw → forfeiterResolved, homeRaw → homeResolved) before comparing.

---

## Code Examples

### V17 Migration (verified pattern from V10/V11)
```sql
ALTER TABLE matches ADD COLUMN walkover_team_id UUID NULL;
ALTER TABLE matches ADD CONSTRAINT fk_match_walkover_team
    FOREIGN KEY (walkover_team_id) REFERENCES teams(id);
```

### Match Entity Field (follow awayTeam convention, Match.java lines 36–38)
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "walkover_team_id")
private Team walkoverTeam;
```

### StandingsService.processMatch() Walkover Branch (insert after line 339, before line 342)
```java
if (match.getWalkoverTeam() != null) {
    UUID forfeiterRaw = match.getWalkoverTeam().getId();
    UUID homeRaw = match.getHomeTeam().getId();
    UUID awayRaw = match.getAwayTeam().getId();
    UUID forfeiterResolved = resolveTeamId(forfeiterRaw, successionMap);
    UUID opponentResolved = forfeiterRaw.equals(homeRaw)
            ? resolveTeamId(awayRaw, successionMap)
            : resolveTeamId(homeRaw, successionMap);
    var forfeiterStanding = standingsMap.get(forfeiterResolved);
    var opponentStanding = standingsMap.get(opponentResolved);
    if (forfeiterStanding != null) {
        forfeiterStanding.addLoss();
        forfeiterStanding.setHasWalkover(true);
    }
    if (opponentStanding != null) {
        opponentStanding.addWin();
        opponentStanding.addMatchPoints(matchScoring.getPointsWin());
    }
    return;
}
```

### ScoringService Guard (insert after line 64, recomputeMatchScoresFromAllLegs)
```java
if (race.getMatch() != null && race.getMatch().getWalkoverTeam() != null) {
    return;
}
```

### MatchService.updateWalkover() (new method, follows updateDiscordFields pattern)
```java
@Transactional
public void updateWalkover(UUID matchId, UUID walkoverTeamId) {
    Match match = findById(matchId);
    if (walkoverTeamId == null) {
        match.setWalkoverTeam(null);
        matchRepository.save(match);
        log.info("Cleared walkover for match {}", matchId);
        return;
    }
    if (match.isBye()) {
        throw new BusinessRuleException("A bye match cannot be marked as a walkover.");
    }
    if (match.getAwayTeam() == null) {
        throw new BusinessRuleException("Match has no away team — cannot be a walkover.");
    }
    boolean isHome = walkoverTeamId.equals(match.getHomeTeam().getId());
    boolean isAway = walkoverTeamId.equals(match.getAwayTeam().getId());
    if (!isHome && !isAway) {
        throw new BusinessRuleException("Walkover team must be one of the match's two teams.");
    }
    Team walkoverTeam = teamRepository.findById(walkoverTeamId)
            .orElseThrow(() -> new EntityNotFoundException("Team", walkoverTeamId));
    match.setWalkoverTeam(walkoverTeam);
    matchRepository.save(match);
    log.info("Set walkover for match {}: forfeiter={}", matchId, walkoverTeam.getShortName());
}
```

Note: `teamRepository` must be injected into `MatchService` — it is currently already there in `MatchService` (line 46).

---

## Validation Architecture

> `workflow.nyquist_validation: true` in `.planning/config.json` — section is required.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Spring Boot Test + Mockito + Playwright |
| Config file | `pom.xml` (Surefire lines 266–309; Failsafe lines 291–308; E2E `-Pe2e` lines 440–460) |
| Quick run command | `./mvnw -Dtest=StandingsServiceTest test` |
| Full suite command | `./mvnw clean verify -Pe2e` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| WO-01 | Home-forfeit walkover: opponent wins, forfeiter loses, correct points | unit | `./mvnw -Dtest=StandingsServiceTest test` | ✅ (add nested test) |
| WO-01 | Away-forfeit walkover: opponent wins, forfeiter loses, correct points | unit | `./mvnw -Dtest=StandingsServiceTest test` | ✅ (add nested test) |
| WO-01 | Walkover precedes partial results (D-08) | unit | `./mvnw -Dtest=StandingsServiceTest test` | ✅ (add nested test) |
| WO-01 | No point difference / Buchholz from walkover (D-07) | unit | `./mvnw -Dtest=StandingsServiceTest test` | ✅ (add nested test) |
| WO-01 | recomputeMatchScoresFromAllLegs skips walkover races | unit | `./mvnw -Dtest=ScoringServiceTest test` | ✅ (add test) |
| WO-02 | V17 migration adds `walkover_team_id` column (nullable) | integration | `./mvnw -Dit.test=V17MigrationIT -DfailIfNoTests=false verify` | new `V17MigrationIT` — authored green with V17 (plan 109-01) |
| WO-02 | MatchController save-edit persists walkover_team_id FK | integration | `./mvnw -Dit.test=MatchControllerTest -DfailIfNoTests=false verify` | ✅ (add test method) |
| WO-03 | "w/o" label appears in matchday-detail after marking | e2e | `./mvnw verify -Pe2e` (WalkoverE2ETest) | new `WalkoverE2ETest` — authored green after the label exists (plan 109-04) |
| WO-04 | Admin can mark/clear walkover through edit form | integration | `./mvnw -Dit.test=MatchControllerTest -DfailIfNoTests=false verify` | ✅ (add test method) |
| WO-04 | Bye match cannot be walkover → errorMessage flash | integration | `./mvnw -Dit.test=MatchControllerTest -DfailIfNoTests=false verify` | ✅ (add test method) |

### Sampling Rate
- **Per task commit:** `./mvnw -Dtest=StandingsServiceTest,ScoringServiceTest test` (unit only, fast)
- **Per wave merge:** `./mvnw clean verify` (excludes E2E)
- **Phase gate:** `./mvnw clean verify -Pe2e` must exit 0 before `/gsd-verify-work`

### New Test Files (no red/disabled stubs)
Both new test files are authored in the same plan as the code that makes them pass — committed green, never `@Disabled` or red (CLAUDE.md "Clean Maven Build is the Source of Truth"):
- `src/test/java/db/migration/V17MigrationIT.java` — WO-02 (Flyway H2 schema); written **with V17 in plan 109-01**.
- `src/test/java/org/ctc/e2e/WalkoverE2ETest.java` — WO-03/WO-04 E2E (Playwright); written **enabled in plan 109-04** once the dropdown (109-03) and matchday-detail label (109-04) exist.

---

## Security Domain

Phase 109 does not introduce authentication, session management, or cryptography changes. The walkover marking is admin-only (all `/admin/**` routes are secured in prod via profile; dev/local remain open). Input validation is at the service layer (team ∈ {home, away}), not just DB-constraint-level.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Admin routes already secured by profile (`prod`/`docker`) |
| V3 Session Management | no | No session change |
| V4 Access Control | no | Existing `/admin/**` gating unchanged |
| V5 Input Validation | yes | Service-layer validation: team UUID must be null or ∈ {homeTeam.id, awayTeam.id}; not-bye check; EntityNotFoundException for invalid UUID |
| V6 Cryptography | no | No crypto involved |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Mass assignment via MatchForm | Tampering | MatchForm DTO (not entity direct binding) — per existing convention |
| Invalid team UUID submitted | Tampering | Service validation: `!isHome && !isAway` → BusinessRuleException before any DB write |
| Walkover on a non-scheduled match | Tampering | `match.isBye()` guard rejects bye matches; `awayTeam == null` guard rejects structural-bye matches |

---

## Environment Availability

This phase makes no new environment dependencies. The existing MariaDB (local/prod) and H2 (dev/test) environments already satisfy all requirements. The Flyway V17 migration runs on first startup with the new schema.

Step 2.6: SKIPPED (no new external dependencies — only existing H2/MariaDB, Maven, JUnit, Playwright already verified in Phase 108).

---

## Runtime State Inventory

> Phase 109 adds a new column (`walkover_team_id`) and a service branch — not a rename/refactor. However, one runtime state consideration exists.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | Existing `matches` rows will have `walkover_team_id = NULL` after V17 migration | None — `NULL` = no walkover, correct default |
| Live service config | None | None |
| OS-registered state | None | None |
| Secrets/env vars | None | None |
| Build artifacts | None — the column is new, no old name to invalidate | None |

**Nothing found requiring migration of existing data** — the `NULL` default for existing rows is semantically correct (no match was previously a walkover).

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Bye: structural absence (awayTeam=null) | Walkover: both teams real, one forfeits (walkoverTeam!=null) | Phase 109 (new) | Standings now handle two auto-win patterns independently |
| StandingsService.processMatch(): 2 branches | StandingsService.processMatch(): 3 branches | Phase 109 (new) | Walkover branch between bye and score branches |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Thymeleaf `th:field="*{walkoverTeamId}"` on `<select>` with UUID type auto-selects the matching option via Spring's ConversionService | Section 5 (Admin UI) | Dropdown doesn't pre-select the current forfeiter on edit re-load; easy fix: compare IDs manually with `th:selected` |
| A2 | Both H2 2.x and MariaDB 10.7+ support `ADD CONSTRAINT ... FOREIGN KEY` in a separate `ALTER TABLE` statement after `ADD COLUMN` | Section 1 (Flyway V17) | Low risk — standard SQL; but if H2 requires inline syntax, combine into one `ADD COLUMN ... REFERENCES` |
| A3 | `LineupGraphicService.generateLineup()` is accessible only via Discord post flow which is already gated by `matchHasCompleteLineups()`; walkover matches with no lineup won't trigger it via normal UI | Section 7 (Graphics) | If there's a direct admin button that bypasses the gate, the service throws; UI guard or service guard needed |

**All other claims are VERIFIED from direct code reads with line numbers.**

---

## Open Questions (RESOLVED)

1. **Lineup graphic for walkover match**
   - What we know: `LineupGraphicService` throws if no lineup entries exist, and walkover matches have no lineup
   - What's unclear: Is there a lineup post button exposed on walkover match detail that would trigger this?
   - Recommendation: Add a service-level walkover guard in `generateLineup()` (render the forfeiter side with a "w/o" placeholder), AND check whether `matchHasCompleteLineups()` would gate the button
   - RESOLVED: Add a walkover guard in `generateLineup()` before the `lineups.isEmpty()` throw; covered by plan 109-05 Task 1.

2. **Provisional graphic for walkover match**
   - What we know: `ProvisionalScoresGraphicService.generateProvisional()` throws if results empty; `matchHasProvisionalData()` gates the UI button
   - What's unclear: Whether the "Re-Post Provisional" button would still appear if a previous provisional was posted before walkover was set
   - Recommendation: Planner should verify whether `provisionalPost != null` could be true for a walkover match and whether re-posting would throw; if so, add a walkover guard to `generateProvisional()`
   - RESOLVED: Add a walkover guard in `generateProvisional()` with a clear message; covered by plan 109-05 Task 2.

---

## Sources

### Primary (HIGH confidence)
All findings verified by direct file reads of the actual source code, with line numbers cited:

- `src/main/java/org/ctc/domain/model/Match.java` — entity field convention (lines 33–45)
- `src/main/java/org/ctc/domain/service/StandingsService.java` — `processMatch()` (lines 330–388), `TeamStanding` (lines 394–502), `calculateBuchholzScoresForPhase()` (lines 305–316)
- `src/main/java/org/ctc/domain/service/ScoringService.java` — `recomputeMatchScoresFromAllLegs()` (lines 62–93), `aggregateMatchScores()` (lines 127–186)
- `src/main/java/org/ctc/admin/controller/MatchController.java` — edit GET/POST (lines 101–129)
- `src/main/java/org/ctc/admin/dto/MatchForm.java` — all fields (lines 1–30)
- `src/main/java/org/ctc/domain/service/MatchService.java` — `updateDiscordFields()` (lines 153–181), `findById()` (line 62)
- `src/main/resources/db/migration/V1__initial_schema.sql` — matches table FK pattern (lines 148–161)
- `src/main/resources/db/migration/V10__add_matches_discord_and_scheduling_fields.sql` — ALTER TABLE pattern
- `src/main/resources/db/migration/V11__add_matches_discord_channel_archived_at.sql` — minimal ALTER TABLE
- `src/main/resources/templates/admin/matchday-detail.html` — `.match-bye` pattern (lines 56–57)
- `src/main/resources/templates/site/standings.html` — standings table structure (lines 39–51)
- `src/main/resources/templates/admin/match-form-edit.html` — full edit form
- `src/main/resources/templates/admin/match-results-render.html` — graphic template
- `src/main/resources/templates/admin/lineup-render.html` — graphic template
- `src/main/resources/templates/admin/provisional-scores-render.html` — graphic template
- `src/main/java/org/ctc/admin/service/MatchResultsGraphicService.java` — graphic service (lines 36–89)
- `src/main/java/org/ctc/admin/service/LineupGraphicService.java` — graphic service (lines 44–119)
- `src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java` — graphic service (lines 40–58)
- `src/main/resources/static/admin/css/admin.css` — `.match-bye` class (lines 1484–1488)
- `src/test/java/org/ctc/domain/service/StandingsServiceTest.java` — bye test pattern (lines 212–232)
- `src/test/java/db/migration/V13MigrationIT.java` — migration IT pattern (full)
- `src/test/java/org/ctc/testsupport/CtcDevSpringBootContext.java` — test annotation
- `src/test/java/org/ctc/TestHelper.java` — `createFullSeasonFixture()` (lines 151–162)
- `.planning/phases/109-walkover-handling/109-CONTEXT.md` — locked decisions
- `.planning/REQUIREMENTS.md` — WO-01..04
- `.planning/STATE.md` — baselines, V17 confirmation
- `.planning/config.json` — nyquist_validation: true

### Metadata

**Confidence breakdown:**
- V17 migration SQL: HIGH — confirmed by V1/V10/V11 patterns
- StandingsService extension: HIGH — processMatch() fully read; bye pattern confirmed
- ScoringService guard (critical pitfall): HIGH — confirmed by reading recomputeMatchScoresFromAllLegs() fully
- Admin UI flow: HIGH — MatchController + MatchForm + MatchService fully read
- Template changes: HIGH — all three graphic templates + matchday-detail + standings fully read
- TeamStanding hasWalkover flag: HIGH — getPlayed() confirmed as wins+draws+losses

**Research date:** 2026-05-30
**Valid until:** 2026-06-30 (stable framework, low churn)
