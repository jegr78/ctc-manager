# Phase 61: Cleanup & Quality Gate - Context

**Gathered:** 2026-05-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Final cleanup of v1.9 (Season Phases & Groups). Phase 61 schliesst die Migration ab, indem alle alten Bridge-Konstrukte aus Schema und Code entfernt werden, und sichert die Qualität via JaCoCo-Gate + zwei Playwright-E2E-Tests.

**Lieferumfang per ROADMAP-Anforderungen + bewusste Scope-Erweiterung:**

1. **MIGR-06 Schema-Cleanup (erweitert):** V6-SQL-Migration droppt:
   - 8 Spalten aus `seasons`: `format`, `total_rounds`, `legs`, `event_duration_minutes`, `start_date`, `end_date`, `race_scoring_id`, `match_scoring_id`
   - M:N-Tabelle `playoff_seasons`
   - **Erweitert über ROADMAP hinaus** (Scope-Decision in Phase 61 dokumentiert): Bridge-Spalten `matchdays.season_id` und `playoffs.season_id` (Phase 56 D-02 / Phase 57 SC5 hatten diese als "bleiben" definiert — werden hier endgültig entfernt). Begründung: das neue Modell ist `Season → SeasonPhase → Matchday/Playoff`, die Bridge-FKs sind denormalisiert und wartungsbelastend.

2. **Code-Cleanup (Compile-Kaskade aus Schema-Drop):**
   - Season-Entity: 8 Felder entfernt (format, totalRounds, legs, eventDurationMinutes, startDate, endDate, raceScoring, matchScoring)
   - Matchday.season-Field entfernt; `Matchday.getSeason()` als first-class Convenience-Getter behalten (`return phase.getSeason();`)
   - Playoff.season-Field entfernt; `Playoff.getSeason()` als first-class Convenience-Getter behalten
   - Playoff.seasons-M:N-Field + `@JoinTable("playoff_seasons")` entfernt
   - Season.matchdays als Derived-Getter (kein @OneToMany mehr) — `phases.stream().flatMap(p -> p.matchdays.stream()).sorted(...).toList()`
   - PlayoffService.addSeasonToPlayoff + removeSeasonFromPlayoff (beide @Deprecated) entfernt
   - PlayoffService + PlayoffSeedingService: `playoff.getSeasons()`-Iterations entfernt (nutzen jetzt nur `playoff.getSeason()` first-class)
   - MatchService.getLegs()-Aufrufe migriert: `matchday.getSeason().getLegs()` → `matchday.getPhase().getLegs()`
   - Vollständiger grep-Audit über `season.getFormat/getLegs/getStartDate/getEndDate/getTotalRounds/getEventDurationMinutes/getRaceScoring/getMatchScoring` in src/main/java + src/main/resources/templates + src/test (Thymeleaf-SpEL crasht erst zur Laufzeit, nicht beim Compile — explizit auditen)
   - Legacy-Endpoints `/admin/playoffs/{id}/add-season` und `/admin/playoffs/{id}/remove-season` komplett entfernt (Phase 60 D-43 hatte sie UI-versteckt aber funktional belassen). Alte Bookmarks geben 404.

3. **QUAL-01 JaCoCo-Gate:** Threshold bleibt bei 82% (CLAUDE.md-konform). Falls Cleanup unter 82% rutscht, wird in Plan 61-05 Coverage-Repair durchgeführt — keine Threshold-Senkung.

4. **QUAL-02 GROUPS-E2E-Test:** Voller End-to-End-Workflow in einer Test-Methode (`GroupsSeasonE2ETest`):
   Saison anlegen → REGULAR-Phase (LEAGUE) auto-bootstrapped → 2. Phase mit GROUPS-Layout anlegen → 2 Groups ("Group A", "Group B") anlegen → Teams (4-6) zur Saison hinzufügen + zu Groups zuweisen → Driver-Import via gemocktem Sheet (1 Tab "2099", 6-12 Driver-Rows; Group via PhaseTeam aufgelöst) → Matchdays pro Group generieren (2 Matchdays × Group, je 1 Race) → Race-Results UI-eintragen (volle Klick-Eintragung, kein Service-Shortcut) → Standings (per-group + combined-view) verifizieren. Hybrid-Assertions: UI (Page-Visibility, Tabellen-Cells) + DB-State (`phaseTeamRepository.findByPhaseId(p)` Counts, `seasonPhaseRepository.findByType(...).getLayout() == GROUPS`).

5. **QUAL-03 Regression-E2E-Test:** Zwei Test-Methoden (`LegacyMigratedSeasonE2ETest`):
   - `givenLegacyMigratedSeasonWithoutPlayoff_thenRegularTabOnly`
   - `givenLegacyMigratedSeasonWithPlayoff_thenRegularAndPlayoffTabs`
   
   Pre-Insert via @Sql in legacy-shape POST-V4 (Saison + 1 SeasonPhase REGULAR mit den Daten, Matchday mit phase_id, ggf. Playoff mit phase_id). Vollständiger Lese-Path: Saison-Detail-Page öffnet, exakt 1 REGULAR-Tab (+ optional PLAYOFF-Tab), Matchday-Liste rendert vollständig, Race-Detail rendert mit Results, Legacy-Standings-URL (`/admin/standings?season={id}`) auto-redirected zur REGULAR-Phase, Standings-Tabelle zeigt erwartete Werte. Pures Read-Only — kein Schreib-Pfad.

**Tracked Behavior Changes (must call out at PR/Release time):**

- **Schema-Drop nicht rückgängig in Prod:** V6 droppt 10 Spalten + 1 Tabelle. Backup-Empfehlung an Ops kommuniziert.
- **`/admin/playoffs/{id}/add-season` und `/remove-season` geben jetzt 404** (Phase 60 D-43 versteckte sie, Phase 61 entfernt sie). UI hat seit Phase 60 keine Links mehr darauf.
- **Bridge-Spalten `matchdays.season_id` und `playoffs.season_id` weg** — externe Konsumenten (falls existent) müssen ihre Queries auf `season_phases.season_id` umstellen (über JOIN `matchdays.phase_id → season_phases.id`).

**Übernommen aus früheren Phasen (nicht erneut diskutiert):**

- **Phase 60 D-44** — Conservative-Cleanup hat bereits PlayoffController/StandingsController/SeasonController/MatchdayController/RaceController auf phaseId-kanonische Service-Methoden umgestellt; Phase 61 räumt nur die letzten 2 @Deprecated PlayoffService-Bridges (addSeasonToPlayoff/removeSeasonFromPlayoff) auf.
- **Phase 58 D-23** — SiteGenerator ist bereits phase-aware.
- **Phase 60 V5** — `seasons.race_scoring_id` und `match_scoring_id` sind bereits NULLABLE (V6 droppt sie nun ganz).
- **Phase 57 V4** — Migration der Daten in Phase-Schema ist bereits passiert; V6 setzt darauf auf, keine Pre-Checks nötig.
- **Hibernate ddl-auto=validate** auf allen Profilen (dev/local/docker/prod) — nach V6 + Entity-Cleanup muss Schema vs. Entity exakt matchen, sonst Startup-Failure (Test-Suite fängt das).

**Explizit out of scope für Phase 61:**

- **Threshold-Bump über 82%** — bleibt bei 82%, organische Erhöhung durch Cleanup ist Bonus, keine Plan-Anforderung.
- **Doc-Update (README/Wiki)** — separater Schritt nach Phase 61 (oder im Milestone-Wrap-Up).
- **Sub-group-aware Playoff-Brackets** (`PLAYOFF-FUT-01`) — future milestone.
- **Saison-Konsolidierungs-UI** (`CONSOL-FUT-01`) — future milestone.
- **Phase-/Group-Override-Spalte im Sheet** (`IMPORT-FUT-01`) — future milestone.

</domain>

<decisions>
## Implementation Decisions

### Cleanup-Scope (D-01..D-06)

- **D-01: Maximaler Cleanup-Scope inkl. Bridge-Spalten** — `matchdays.season_id` und `playoffs.season_id` werden zusätzlich zu den 8 ROADMAP-Spalten + `playoff_seasons` gedropped. Erweitert ROADMAP-SC1; ROADMAP wird in Plan 61-01 entsprechend aktualisiert. Begründung: das neue Modell ist `Season → SeasonPhase → Matchday/Playoff`, die Bridge-FKs sind denormalisiert.

- **D-02: Convenience-Getter behalten als first-class API** — `Matchday.getSeason()` und `Playoff.getSeason()` bleiben, intern delegiert auf `phase.getSeason()`. Konsistent mit project memory `feedback_entity_refactoring`. Reduziert Code-Pattern-Drift in ~14 Aufrufstellen + Templates + Tests. JavaDoc dokumentiert: "Convenience getter — derives season via getPhase().getSeason()". Kein @Deprecated-Marker — sind sinnvolle API.

- **D-03: Legacy-Endpoints komplett entfernen** — `/admin/playoffs/{id}/add-season` + `/remove-season` werden gestrichen (404 für alte Bookmarks). UI seit Phase 60 D-43 versteckt; keine externen Konsumenten erwartet. CLAUDE.md "No breaking changes to existing URLs" wird hier bewusst gebrochen — als Tracked Behavior Change im PR + Release-Notes ausgewiesen.

- **D-04: ROADMAP-Update als erster Schritt von Phase 61** — Plan 61-01 startet mit `ROADMAP.md`-Update (Phase-61-Goal + SC1 erweitern auf zusätzliche Bridge-Spalten) und einem Eintrag in `PROJECT.md` Key-Decisions-Tabelle, der die Scope-Erweiterung mit Begründung dokumentiert. Audit-Trail bleibt sauber im Git-Log.

- **D-05: Season.matchdays als Derived-Getter** — wenn `Matchday.season` weg ist, bricht `Season.matchdays` `@OneToMany(mappedBy="season")`. Lösung: Field-Replacement durch Methode `getMatchdays()` die `phases.stream().flatMap(p -> p.getMatchdays().stream()).sorted(byPhaseSortIndexThenMatchdaySortIndex).toList()` zurückliefert. SeasonPhase.matchdays bleibt das einzige `@OneToMany`. Konsistent mit dem Convenience-Getter-Approach (D-02).

- **D-06: Test-Cleanup compile-getrieben + grep-Audit-Pass** — Compile-Failures markieren betroffene Test-Files. Zusätzlich grep-Audit auf `setSeason` / `getSeasons-add` / `season.getFormat/getLegs/...` in `src/test/`. Neue TestHelper-Factory-Methoden (z. B. `createMatchdayInRegularPhase(season)`, `createPlayoffInPhase(season)`) werden bei Bedarf eingeführt; bestehende `givenContext_whenAction_thenResult`-Pattern bleibt. ~30-50 Test-Files Impact erwartet.

### MIGR-06 Mechanik (D-07..D-10)

- **D-07: Pure SQL, single V6 file** — `V6__cleanup_legacy_season_columns.sql` in `src/main/resources/db/migration/`. DROP-Statements in korrekter FK-Reihenfolge: 1) `DROP TABLE playoff_seasons`, 2) `ALTER TABLE matchdays DROP COLUMN season_id`, 3) `ALTER TABLE playoffs DROP COLUMN season_id`, 4) 8× `ALTER TABLE seasons DROP COLUMN ...`. H2 + MariaDB beide unterstützen ALTER TABLE DROP COLUMN. Konsistent mit V5-Pattern.

- **D-08: Code-First, dann V6** — Plan 61-02 (Java-Entity-Refactoring + Aufrufstellen-Migration + Tests grün) läuft VOR Plan 61-03 (V6-Migration). Begründung: Hibernate ddl-auto=validate würde sonst beim Startup brechen, weil @JoinColumn auf phantom-column zeigt. Code-First sorgt für sauberen Zwischenzustand.

- **D-09: V6MigrationTest in src/test/java/db/migration/** — `V6MigrationTest.java` spiegelt die V4-Migration-Source-Struktur. `@SpringBootTest` mit `@ActiveProfiles("dev")` startet H2 + Flyway, asserted: (a) `seasons.format`-Spalte existiert nicht mehr (Information-Schema-Query), (b) `playoff_seasons`-Tabelle existiert nicht mehr, (c) `matchdays.season_id` und `playoffs.season_id` weg, (d) bestehende Saison/Phase-Daten weiterhin lesbar. Läuft im **normalen `./mvnw verify`** (Surefire), nicht im `-Pe2e`-Profile. CI fängt Migration-Regressionen sofort.

- **D-10: Keine Pre-Checks in V6** — V4 hatte fail-fast Data-Integrity-Checks (Phase 57 D-05). V6 baut darauf auf — wenn V4 + V5 erfolgreich gelaufen sind, ist die Datenform garantiert konsistent. Reine SQL-Migration, einfach + atomar + auditbar.

### QUAL-02 GROUPS-E2E (D-11..D-14)

- **D-11: Voller End-to-End-Workflow in einer Test-Methode** — `GroupsSeasonE2ETest.givenGroupsLayout_whenFullSeasonWorkflow_thenStandingsCorrect()` deckt den gesamten Workflow ab (~80-150 Zeilen, 30-60s Laufzeit). Begründung: ROADMAP-SC3 verlangt "creates a GROUPS-layout season... imports drivers... generates matchdays... records results... verifies per-group standings AND a combined-view standings table" — das ist ein zusammenhängender Pfad, kein logisch trennbares Test-Set.

- **D-12: GoogleSheetsService-Stub via @TestConfiguration** — konsistent mit `ImportE2eTest.TestGoogleSheetsConfig`-Pattern: `@Bean @Primary GoogleSheetsService` im Test-Modul liefert synthetische Sheet-Daten (1 Tab "2099", 6-12 Driver-Rows mit Team-Zuordnung). Stub liefert genau die Group-Auflösungs-Daten, die der Test braucht. Kein Netzwerk.

- **D-13: Hybrid Assertions UI + DB-State** — Playwright-Assertions auf Page-Visibility (Tab-Labels, Tabellen-Cells, Standings-Werte) PLUS direkte Repository-Lookups (`seasonPhaseRepository.findByType(s, REGULAR).getLayout() == GROUPS`, `phaseTeamRepository.findByPhaseId(p)` hat 4-6 Einträge mit korrekten Group-IDs, etc.). DB-State fängt strukturelle Bugs (Phase 58 D-20 Roster-Init), UI fängt Render-Bugs (Phase 60 D-29..D-36 Combined-View).

- **D-14: T-Prefix konsistent für Test-Daten** — Saison: `Test-GROUPS Season 2099`. Teams: `T-GA-1`, `T-GA-2`, `T-GB-1`, `T-GB-2`. Driver-PSNIDs: `T_groups_drv01..drv12`. Jahr 2099 vermeidet Kollision mit echten Saisons (2024-2026) und DevDataSeeder. Konsistent mit project memory `feedback_test_data_isolation` + CLAUDE.md "Isolate Test Data Completely".

### QUAL-02 Volumen (D-15..D-16)

- **D-15: Volle UI-Klick-Eintragung für Race-Results** — Test navigiert zur Result-Eintragungs-Page, klickt Drivers, gibt Punkte/Position ein, klickt Save. Validiert UI-Form-Bindings + JS-Interaktionen + Success-Flash. ROADMAP-SC3 "records results" impliziert UI-Pfad. Längere Test-Laufzeit, höhere Confidence.

- **D-16: 2 Matchdays pro Group, 1 Race pro Matchday** — Group A: 2 Matchdays × 1 Race. Group B: 2 Matchdays × 1 Race. Total 4 Races. Erlaubt Combined-View Aggregation über mehrere Einträge zu testen. Standings haben sinnvolle Punkt-Differenzen. Test-Laufzeit 30-60s.

### QUAL-03 Regression-E2E (D-17..D-19)

- **D-17: @Sql Pre-Insert in legacy-shape POST-V4** — Test setzt Saison + Matchday + ggf. Playoff direkt via `@Sql`-Script ein (post-V6-Schema, da Flyway zum Test-Start schon V1-V6 durchgelaufen ist). Effektiv simulieren wir "Saison existiert in Phase-Form" — semantisch eine "migrierte Bestandssaison". V6 hat bereits gelaufen, wir testen dass die migrate-erzeugte Form funktioniert.

- **D-18: Vollständiges Lese-Path-Assertion** — Test öffnet Saison-Detail-Page → asserted: (a) genau 1 REGULAR-Tab, (b) PLAYOFF-Tab vorhanden falls Fixture es enthält, (c) Matchday-Liste rendert vollständig, (d) Click auf Matchday → Race-Liste, (e) Race-Detail mit Results, (f) Legacy-Standings-URL `?season=` auto-redirected zur REGULAR-Phase, (g) Standings-Tabelle hat erwartete Werte. Pures Read-Only — kein Schreib-Pfad. ~50-80 Zeilen Test-Code.

- **D-19: Beide Varianten (mit + ohne Playoff)** — Zwei Test-Methoden in `LegacyMigratedSeasonE2ETest`: `givenLegacyMigratedSeasonWithoutPlayoff_thenRegularTabOnly` und `givenLegacyMigratedSeasonWithPlayoff_thenRegularAndPlayoffTabs`. Beide via @Sql-Fixtures. Deckt beide ROADMAP-SC4-Sub-Cases.

### Coverage / JaCoCo (D-20..D-21)

- **D-20: 82%-Threshold halten** — `<jacoco.minimum>0.82</jacoco.minimum>` in pom.xml bleibt unverändert. Falls Cleanup organisch erhöht — Bonus, kein Plan-Lock. Konsistent mit CLAUDE.md "Minimum 82% line coverage" + project memory `feedback_coverage_strategy`.

- **D-21: QUAL-01 = Phasen-Erfolgs-Kriterium, nicht Threshold-Senkung** — Falls nach Cleanup Coverage < 82%, schreibt Plan 61-05 zusätzliche Tests. Wahrscheinlichste Quelle: Service-Methoden auf neuem Phase-Modell, die heute under-tested sind (z. B. SeasonPhaseService.update, SeasonPhaseGroupService.delete). Plan-Schritt: "Coverage-Repair-bei-Bedarf".

### Plan-Strukturierung + Workflow (D-22..D-25)

- **D-22: 5 Plans, sequentiell** —
  - **Plan 61-01**: ROADMAP-Update + PROJECT.md-Scope-Decision-Eintrag (D-04).
  - **Plan 61-02**: Code-Cleanup (Entity-Refactoring + ~14 Aufrufstellen + Test-Fixes) (D-01..D-06).
  - **Plan 61-03**: V6 SQL Migration + V6MigrationTest (D-07..D-10).
  - **Plan 61-04**: QUAL-02 GROUPS-E2E (D-11..D-16).
  - **Plan 61-05**: QUAL-03 Regression-E2E + ggf. Coverage-Repair (D-17..D-21).
  
  Sequentiell, weil Plan 61-02 Plan 61-03 vorbereiten muss (Code-First D-08). Plans 61-04 und 61-05 könnten technisch parallel, aber siehe D-24.

- **D-23: Tracked Behavior Changes explizit** — CONTEXT.md (oben) + jeder Plan-SUMMARY + finaler PR-Description listen die Behavior Changes (Schema-Drop irreversibel, Endpoint-Removal 404, Bridge-Spalten-Drop). Konsistent mit Phase 58 SUMMARY-Pattern. Klarer Audit-Trail bei Release-Notes.

- **D-24: Sequentielle Plan-Ausführung auf gsd/v1.9-Branch** — keine Worktrees für Phase 61. Konsistent mit Phase 60. project memory `feedback_subagent_stability` ist kritischer als Parallel-Speedup für nur 2 unabhängige Plans am Phase-Ende. Subagent-Disziplin > Parallel-Performance.

- **D-25: Branch bleibt gsd/v1.9-season-phases-groups, kein Rebase** — Branch hat schon viele Commits aus Phase 56-60. Rebase auf origin/master könnte Konflikte erzeugen. Direkt weitermachen, am Milestone-Ende (nach Phase 61) per `gh pr merge --squash` mergen. Begründung des Users: Stabilität > strenger fetch-pattern.

### Milestone-Abschluss (D-26)

- **D-26: v1.9 wrap-up nach Phase 61** — Standard-GSD-Workflow:
  1. `/gsd-execute-phase 61` (alle 5 Plans sequentiell)
  2. `/gsd-verify-work 61` (UAT)
  3. `/gsd-audit-milestone v1.9` (Cross-Phase-Audit)
  4. `/gsd-complete-milestone v1.9` (Archive + Bump)
  5. `/gsd-ship` für PR + Release

### Claude's Discretion

- **Exakte SQL-Statement-Reihenfolge in V6** — Planner finalisiert (Tabelle vor FK-Spalten vor Saison-Spalten ist die logische Folge, aber genaue Syntax bleibt offen).
- **Test-Helper-Methoden-Naming** in TestHelper.java (z. B. `createMatchdayInRegularPhase` vs. `createMatchdayInPhase`) — Planner wählt nach existing TestHelper-Convention.
- **JavaDoc-Wording** der Convenience-Getter (D-02) — Planner formuliert nach existing JavaDoc-Stil in Season/Matchday/Playoff.
- **Wave-Plan-Splitting innerhalb von Plan 61-02** (z. B. Wave 1: Season-Entity-Felder weg + alle direkten Aufrufer, Wave 2: Matchday/Playoff-Bridge weg, Wave 3: Test-Fixes) — Planner finalisiert nach Compile-Cluster-Größe.
- **Konkretes @Sql-Script-Inhalt** für QUAL-03-Fixtures — Planner definiert Saisonen/Matchdays/Races mit testbaren Werten.
- **Reihenfolge der Standings-Asserts** (per-group zuerst oder combined zuerst in QUAL-02) — Planner entscheidet.
- **Test-Driver-Ranking-Werte** für QUAL-02 (welche Punkte / welche Plätze für Group-A vs Group-B-Drivers) — Planner wählt sinnvolle Differenzen.
- **JaCoCo-Exclusions** — falls neue Convenience-Getter unterhalb der Coverage-Erwartung sind, Planner prüft ob exclude oder Test schreiben.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Foundation & Phase-Specific Requirements
- `.planning/REQUIREMENTS.md` §MIGR-06, §QUAL-01, §QUAL-02, §QUAL-03 — die 4 locked requirements für Phase 61.
- `.planning/REQUIREMENTS.md` §"Out of Scope"-Tabelle — `UNIQUE (year, number)`-Constraint, heuristische Saison-Konsolidierung, sub-group-aware Playoffs sind explizit ausgeschlossen.
- `.planning/ROADMAP.md` §"Phase 61: Cleanup & Quality Gate" (Z. 242-257) — Goal, Success Criteria 1-4, Dependency-Boundary (Depends on Phase 60). **Achtung:** SC1 wird in Plan 61-01 erweitert um Bridge-Spalten-Drop (D-04).
- `.planning/STATE.md` §"Phase Numbering" + §"Key Technical Context" — Phase 61 ist Milestone-Wrap-Up.
- `.planning/PROJECT.md` §"Current Milestone: v1.9 Season Phases & Groups" — Milestone-Goal-Alignment + Key-Decisions-Tabelle (Plan 61-01 fügt hier ein).
- `/Users/jegr/.claude/plans/ich-bin-mit-dem-pure-gem.md` — Original-Architektur-Quelle für v1.9; relevante Sektion: "Schlüssel-Entscheidungen" (Punkt: cars/tracks bleiben Saison-weit, format/scoring/dates wandern zu Phase).

### Prior Phase Contexts (state inheritance — read before planning)
- `.planning/phases/56-model-schema-foundation/56-CONTEXT.md` §D-01 (parallel additive entity scope), §D-02 (matchdays.phase_id / playoffs.phase_id NULLABLE → NOT NULL in V4), §D-03 (UNIQUE-Constraints), §D-04/D-05 (Phase-Type/Layout enums) — was die Schema-Foundation festgelegt hat.
- `.planning/phases/57-data-migration/57-CONTEXT.md` §D-01..D-05 (V4-Java-Migration-Mechanik), §D-06 (REGULAR-phase 1:1 Field-Mapping), §D-07..D-09 (PLAYOFF-phase Field-Mapping), §D-08 (PLAYOFF format='LEAGUE' DB-default), §D-09 (`playoff.phase_id` populated, M:N stayed) — was V4 hinterlassen hat (V6 baut darauf auf).
- `.planning/phases/58-service-layer/58-CONTEXT.md` §D-01..D-03 (phaseId-canonical APIs + @Deprecated bridges, Phase 60 hat conservative cleanup gemacht, Phase 61 räumt Reste), §D-04..D-06 (Combined-View standings), §D-14 (UNIQUE phaseType pre-check), §D-18 (Saison-Delete-Guard), §D-19 (PlayoffService.createPlayoff auto-creates PLAYOFF), §D-20 (PhaseTeam roster init), §D-23 (SiteGenerator phase-aware), §D-25 (auto-sync block — wurde in Phase 60 entfernt), §D-26 (MatchdayService dual-API).
- `.planning/phases/59-import-test-data/59-CONTEXT.md` §D-02 (findByYearAndNumber), §D-05 (Group-Lookup via PhaseTeam-REGULAR), §D-06 (TabWarning-Records), §D-08 (resolvedGroupName auf Driver-Row-Records).
- `.planning/phases/60-admin-ui/60-CONTEXT.md` §D-43 (Legacy-Endpoints UI-versteckt aber funktional — Phase 61 entfernt sie, **D-03**), §D-44 (Conservative-Bridge-Cleanup — Phase 61 räumt Reste auf, **D-01**), §"Explizit out of scope für Phase 60" (MIGR-06, E2E GROUPS, aggressive @Deprecated-Cleanup → Phase 61).
- `.planning/phases/57-data-migration/57-PLAN.md` (oder Plan-Files) — Konkretes V4-Inhalt für V6-Konsistenz-Verifikation.

### Project Conventions (binding)
- `CLAUDE.md` §"Architectural Principles" — **No Fallback Calculations** (D-03/D-10 strikte SC-Drop, keine "Cascade-Hacks"), **Keep Controllers Thin**, **DTOs instead of Entities in Controllers**, **Keep Thymeleaf Templates Lean**.
- `CLAUDE.md` §"Constraints" — **82% Line-Coverage** (D-20), **Flyway: Do not change the existing V1 migration; only new V2+ migrations** (V6 ist eine NEUE Migration, V1-V5 unverändert), **Backward Compatibility: No breaking changes to existing URLs/endpoints** (D-03 ist bewusste Ausnahme — als Tracked Behavior Change dokumentiert), **OSIV bleibt aktiv**.
- `CLAUDE.md` §"Development Approach" — **TDD: Tests first**, given-when-then naming. **Visual Verification with `playwright-cli`** für UI-Änderungen ist Pflicht (Desktop + Mobile) — relevant für QUAL-02 manuelle Verifikation.
- `CLAUDE.md` §"Subagent Rules" — Model-Selection (opus/sonnet, kein haiku für Code), Branch-Protection (kein git stash/checkout/reset), Post-Dispatch-Validierung. Project memory `feedback_subagent_stability` ist die Volumen-Quelle.
- `CLAUDE.md` §"Git Workflow" — Branching, PRs, gh CLI, jegr78 als Assignee, CI-Check vor Merge. project memory `feedback_pr_workflow`.
- `CLAUDE.md` §"Conventions" — Layer-Naming, Controller/DTO-Patterns, Logging.
- `.planning/codebase/ARCHITECTURE.md` — three-tier MVC, Controller→Service→Repository.
- `.planning/codebase/CONVENTIONS.md` — Service-Naming, `@Service` + `@RequiredArgsConstructor` + `@Slf4j`.
- `.planning/codebase/STRUCTURE.md` — `org.ctc.admin.controller`, `org.ctc.admin.dto`, `org.ctc.admin.service`-Layer-Mapping.
- `.planning/codebase/TESTING.md` §"E2E Testing (Playwright)" (Z. 360+) — `PlaywrightConfig`-Base-Class, `@SpringBootTest(webEnvironment = RANDOM_PORT)`, Test-Pattern, Locators. **Quelle für QUAL-02/QUAL-03-Implementation.**
- `.planning/codebase/TESTING.md` §"Coverage" (Z. 487+) — JaCoCo-Threshold-Config, Excluded-from-Coverage-Liste.

### Existing Code (read for pattern alignment)

**Schema / Migrations:**
- `src/main/resources/db/migration/V1__initial_schema.sql` — original Schema, NICHT verändern.
- `src/main/resources/db/migration/V2__add_fk_indexes.sql` — NICHT verändern.
- `src/main/resources/db/migration/V3__add_season_phase_tables.sql` — Phase 56 (NICHT verändern).
- `src/main/java/db/migration/V4__MigrateSeasonsToPhases.java` — Phase 57 Java-Migration (Referenz für V6-Test-Verifikation, NICHT verändern).
- `src/main/resources/db/migration/V5__nullable_legacy_scoring_columns.sql` — Phase 60 (Pattern-Vorlage für V6).

**Entities (Cleanup-Targets):**
- `src/main/java/org/ctc/domain/model/Season.java` (Z. 47-63) — 8 Felder zu entfernen + getter/setter; Z. 65-67 `Season.matchdays` @OneToMany wird Derived-Getter (D-05).
- `src/main/java/org/ctc/domain/model/Matchday.java` (Z. 27-28) — `Matchday.season` Field weg; `getSeason()` Convenience-Getter (D-02).
- `src/main/java/org/ctc/domain/model/Playoff.java` (Z. 30-31, 49-52) — `Playoff.season` und `Playoff.seasons` (M:N) Felder weg; `getSeason()` Convenience-Getter (D-02).
- `src/main/java/org/ctc/domain/model/SeasonPhase.java` — bleibt unverändert; ist die kanonische Quelle für format/legs/dates/scoring nach Cleanup.

**Services (Cleanup-Targets):**
- `src/main/java/org/ctc/domain/service/PlayoffService.java` (Z. 125, 141) — 2 @Deprecated-Methoden weg (D-03 cascade); Z. 156-160 + Z. 348 — `playoff.getSeasons()`-Iterations weg.
- `src/main/java/org/ctc/domain/service/PlayoffSeedingService.java` (Z. 64-66, Z. 71, Z. 212) — `playoff.getSeasons()`-Iteration und `playoff.getSeason()` cleanup.
- `src/main/java/org/ctc/domain/service/MatchService.java` (Z. 83, 106) — `matchday.getSeason().getLegs()` → `matchday.getPhase().getLegs()`.
- `src/main/java/org/ctc/domain/service/MatchdayService.java`, `RaceService.java` — grep-Audit für `matchday.getSeason()` (Convenience-Getter bleibt, aber check ob noch direkte Field-Reads).
- `src/main/java/org/ctc/domain/service/StandingsService.java` (Z. 142-168) — Cleanup-Reste aus Phase 60-Kommentaren prüfen.

**Controllers (Cleanup-Targets):**
- `src/main/java/org/ctc/admin/controller/PlayoffController.java` — `/admin/playoffs/{id}/add-season` + `/remove-season` Endpoints + zugehörige Routes komplett entfernen (D-03).

**Templates (Audit-Targets — runtime SpEL-Crash-Risiko):**
- `src/main/resources/templates/admin/season-detail.html` — bereits Phase 60 umgebaut, aber grep-Audit auf `${season.format/...}`-SpEL-Reste.
- `src/main/resources/templates/admin/season-form.html` — UI-01 hatte Felder schon entfernt, aber grep-Audit.
- `src/main/resources/templates/admin/playoff-bracket.html` — grep-Audit.
- `src/main/resources/templates/admin/standings.html` — grep-Audit.
- `src/main/resources/templates/admin/matchday-form.html`, `matchday-list.html` — grep-Audit auf `season.legs/format`.

**Tests (Bestehender Pattern für E2E):**
- `src/test/java/org/ctc/e2e/PlaywrightConfig.java` — `@SpringBootTest(webEnvironment = RANDOM_PORT)` Base-Class für QUAL-02 + QUAL-03.
- `src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java` — Pattern-Vorlage für vollständigen Workflow-Test.
- `src/test/java/org/ctc/e2e/ImportE2eTest.java` — `@TestConfiguration GoogleSheetsConfig`-Pattern für QUAL-02 (D-12).
- `src/test/java/org/ctc/e2e/ScoringE2ETest.java` — Pattern für Result-Eintragung + Standings-Asserts.
- `src/test/java/org/ctc/TestHelper.java` — bestehende Factory-Methoden; ergänzen um phase-aware Variants (D-06).
- `src/main/java/org/ctc/admin/TestDataService.java` — Z. 932-Kommentar zu legacy-hack als historischer Referenz.

**Application-Config:**
- `src/main/resources/application-{dev,local,docker,prod}.yml` — `spring.jpa.hibernate.ddl-auto=validate` aktiv, weshalb Code-First (D-08) zwingend ist.
- `pom.xml` Z. 198-249 — JaCoCo-Config + Threshold (D-20: bleibt 82%).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`@TestConfiguration` + `@Bean @Primary GoogleSheetsService`-Pattern** (ImportE2eTest) — direkt für QUAL-02 verwendet (D-12).
- **`PlaywrightConfig`-Base-Class** mit `@SpringBootTest(webEnvironment = RANDOM_PORT) @ActiveProfiles("dev")` — beide neuen E2E-Tests extenden das (D-11, D-17).
- **`TestHelper.createX`-Factory-Methoden** — neue Phase-aware-Helper folgen demselben Pattern (`createMatchdayInRegularPhase(season)` etc., D-06).
- **V5-SQL-Pattern** (`V5__nullable_legacy_scoring_columns.sql`) — H2 + MariaDB-kompatibles `ALTER TABLE` direkt für V6 reusable (D-07).
- **Phase 58 D-19 PlayoffService.createPlayoff** auto-creates PLAYOFF-Phase atomar — QUAL-02-Fixture-Setup nutzt das.
- **Phase 58 D-04/D-05 Combined-View `List<TeamStanding>`** mit nullable group-Field — QUAL-02 Combined-Assert testet diese Struktur.
- **Phase 58 D-20 PhaseTeam-Roster auto-init** für REGULAR-Phase + GROUPS-Phase mit user-zugewiesenen Groups — QUAL-02-Setup-Pfad.
- **Phase 59 D-08 `TabPreview.warnings` + `Driver-Row.resolvedGroupName`** — QUAL-02 testet diese Group-Auflösung.
- **`@Sql`-Pattern in Spring-Tests** — Standard für Pre-Insert-Fixtures (D-17).
- **`SeasonRepository.findByYearAndNumber`** (Phase 59 D-02) — QUAL-02-Setup für klare Test-Saison-Identifikation.
- **`@Slf4j @Service @RequiredArgsConstructor`** — neue Convenience-Getter brauchen kein neues Stereotype.
- **`@Transactional(readOnly = true)`** auf GET-Methoden — Convenience-Getter sind read-only.

### Established Patterns

- **Convenience-Method-Pattern** (Phase 60 D-25/D-26 Service-Auto-Sync, project memory `feedback_entity_refactoring`) — Convenience-Getter analog (D-02).
- **First-Class-API-Pattern** — Methoden ohne @Deprecated-Marker werden langfristig genutzt (D-02).
- **Plan-Phase-Strukturierung** mit ROADMAP-Update als Plan 1 (Phase 56-57 hatten ähnliche Vor-Schritte) — D-04.
- **TDD-Pattern** mit `givenContext_whenAction_thenResult` BDD-Naming (CLAUDE.md) — alle neuen Tests folgen.
- **Failsafe-vs-Surefire-Split** (CLAUDE.md §Build) — V6MigrationTest läuft in Surefire (Standard verify), nicht Failsafe (D-09).
- **`@Sql` für Pre-Insert-Fixtures** — Standard-Spring-Test-Pattern (D-17).
- **Hybrid Page-Visibility + Repository-Asserts** in E2E-Tests (existing AdminWorkflowE2ETest macht UI; QUAL-02 erweitert um Repository-Asserts) — D-13.
- **Behavior-Change-Tracking** (Phase 58 SUMMARY hatte explizite "Behavior changes shipped"-Sektion, project memory `feedback_orchestrator_discipline`) — Plan-SUMMARYs für Phase 61 folgen demselben Stil (D-23).

### Integration Points

- **Hibernate ddl-auto=validate** auf allen Profilen — der Schema-vs-Entity-Zwang erzwingt Code-First (D-08). Ohne Code-First würde der Application-Startup zwischen Plan 61-02 und Plan 61-03 brechen.
- **Flyway-Locations** in Spring Boot scannen `db/migration` (resources) UND `db/migration` (java) — V4 ist Java, V6 ist SQL, koexistieren ohne Spezial-Config.
- **JaCoCo `<configuration>` in pom.xml Z. 198-249** — `<minimum>` an einer Stelle änderbar, Excluded-Liste in `<excludes>`. D-21 fügt nichts hinzu außer ggf. neue Tests (kein Exclude).
- **MatchdayController:86 + 88** — `matchday.getSeason()` bleibt durch Convenience-Getter funktional (D-02), kein Aufruf-Site-Change nötig.
- **AbstractMatchdayGraphicService:46 + AbstractPlayoffRoundGraphicService:46** — auf Convenience-Getter angewiesen; bleiben unangetastet.
- **MatchService.legs (Z. 83, 106)** — Aufruf wird auf `matchday.getPhase().getLegs()` umgestellt, weil Season.legs weg ist (D-01-Compile-Kaskade).
- **`/admin/standings?season={id}` Legacy-URL** (Phase 60 D-12, D-31) — Auto-Resolve zur REGULAR-Phase. QUAL-03 testet das (D-18 (f)).
- **PlayoffSeedingService.autoSeedBracket** (Phase 58 D-15) — `playoff.getSeasons()`-Iteration weg (D-01); nutzt nur `playoff.getSeason()` first-class.

</code_context>

<specifics>
## Specific Ideas

- **Project memory `feedback_entity_refactoring`** ("Convenience-Methoden für Übergang") war ausschlaggebend für D-02 — Convenience-Getter als first-class-API statt Force-Migration aller Aufrufstellen reduziert Diff-Größe um Faktor 5+, weniger Pattern-Drift in Templates.
- **Project memory `feedback_test_data_isolation`** ("Test-Entities mit Prefix, nie echte Daten für E2E") war ausschlaggebend für D-14 — `T-`-Prefix + Jahr 2099 verhindert Kollisionen mit DevDataSeeder-Saisons (2024-2026).
- **Project memory `feedback_subagent_stability`** war ausschlaggebend für D-22/D-24 — sequentielle Plans, kein Worktree, opus/sonnet-Modellpflicht für Code-Änderungen, Branch-Protection-Klauseln in Subagent-Prompts.
- **Project memory `feedback_coverage_strategy`** war ausschlaggebend für D-20 — 82% bleibt der Mindestwert, Bumpen wäre mit dem Phase-Cleanup-Charakter inkonsistent.
- **Project memory `feedback_orchestrator_discipline`** ("Behavior-Change-Tracking explizit") war ausschlaggebend für D-23 — Phase 58 hatte das Pattern, Phase 61 reaktiviert es wegen destruktiver Schema-Änderung.
- **Phase 60 D-43-Versprechen** ("`/admin/playoffs/{id}/add-season` + `/remove-season` bleiben funktional bis Phase 61") wird in D-03 explizit eingelöst — Endpoints werden 404. Tracked Behavior Change.
- **ROADMAP-SC1 Erweiterung** in Plan 61-01 (D-04) — Phase 56 D-02 / Phase 57 SC5 hatten die Bridge-Spalten als "bleiben" definiert. D-01 erweitert das Phase-61-Goal um diese Drops; PROJECT.md Key-Decisions-Eintrag dokumentiert die Scope-Erweiterung mit Begründung "denormalisiert + wartungsbelastend".
- **V6MigrationTest in src/test/java/db/migration/** (D-09) — spiegelt die V4-Java-Migration-Source-Struktur (`src/main/java/db/migration/V4__...java`). Andere Migrations-Tests (falls künftig nötig) landen daneben.
- **D-15 (Volle UI-Klick-Eintragung) ist bewusst gewählt gegen Service-Shortcut** — ROADMAP-SC3 schreibt "records results" — semantisch ein UI-Pfad. Trade-Off: längere Test-Laufzeit (60s vs. 20s), aber höhere Confidence für QUAL-Anforderung.
- **D-19 (beide Playoff-Varianten in QUAL-03)** — ROADMAP-SC4 nennt "plus the PLAYOFF tab if a playoff was migrated" — beide Sub-Cases müssen abgedeckt sein, sonst ist die Regression-Garantie unvollständig.

</specifics>

<deferred>
## Deferred Ideas

- **README + Wiki-Update für v1.9-Features** — separater Schritt nach Phase 61 (oder im Milestone-Wrap-Up via `/gsd-docs-update`). project memory `feedback_docs_update` ("Docs bei Features").
- **Threshold-Bump auf > 82% nach Cleanup** — bleibt bei 82% (D-20). Falls organische Erhöhung: dokumentieren, aber nicht in pom.xml-Lock.
- **JaCoCo-Aspirationsziel 85%** — nicht in Phase 61. Future Milestone, falls gewünscht.
- **Sub-group-aware Playoff-Brackets** (`PLAYOFF-FUT-01`) — future milestone (Phase 60 hatte das schon dahin verschoben).
- **Saison-Konsolidierungs-UI** (`CONSOL-FUT-01`) — future milestone.
- **Phase-/Group-Override-Spalte im Sheet** (`IMPORT-FUT-01`) — future milestone.
- **Manueller Saison-Selector-Dropdown für ambigue Tabs** (Phase 59 D-03 / Phase 60 D-38) — nur backend-side aktiv, UI bewusst nicht gebaut. Keine Phase-61-Aktion.
- **Optimistic Locking (`@Version`) auf Phase-Edit** (Phase 60 Claude's Discretion) — bleibt out of scope, falls in Praxis kein Issue.
- **Drag-and-Drop für Tab-/Group-Reorder + Roster-Editor** (Phase 60 deferred) — future, falls gewünscht.
- **Mobile-Dropdown-Navigation als Alternative zu Horizontal-Scroll** (Phase 60 D-11) — future, falls Bedarf bei vielen Phasen.
- **Java-V6-Migration mit Pre-SELECT-Validation** — D-10 entscheidet sich für reine SQL ohne Pre-Checks. Java-V6 wäre denkbar, falls in Zukunft strengere Pre-Conditions nötig.
- **Worktree-Parallelisierung für Plans 61-04 + 61-05** (D-24 entscheidet sequentiell) — denkbar, falls Phase 61 unter Zeitdruck steht. Aktueller Trade-Off: Subagent-Stabilität > Speedup.
- **Rebase auf origin/master vor Phase 61** (D-25 entscheidet sich dagegen) — falls Mainline weit auseinanderdriftet, wäre Rebase denkbar; aktuell nicht.
- **Testcontainers-MariaDB-Migration-Test** — H2-only-Test (D-09) reicht für CI-Gate. Testcontainers wäre Overkill.

</deferred>

---

*Phase: 61-cleanup-quality-gate*
*Context gathered: 2026-05-01*
