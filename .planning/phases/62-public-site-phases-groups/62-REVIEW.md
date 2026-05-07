---
phase: 62-public-site-phases-groups
reviewed: 2026-05-06T00:00:00Z
depth: standard
files_reviewed: 36
files_reviewed_list:
  - src/main/java/org/ctc/admin/TestDataService.java
  - src/main/java/org/ctc/domain/repository/RaceResultRepository.java
  - src/main/java/org/ctc/domain/service/DriverRankingService.java
  - src/main/java/org/ctc/domain/service/StandingsService.java
  - src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java
  - src/main/java/org/ctc/sitegen/DriverRankingPageGenerator.java
  - src/main/java/org/ctc/sitegen/MatchdaysPageGenerator.java
  - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
  - src/main/java/org/ctc/sitegen/SiteSlugger.java
  - src/main/java/org/ctc/sitegen/StandingsPageGenerator.java
  - src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java
  - src/main/java/org/ctc/sitegen/TemplateWriter.java
  - src/main/java/org/ctc/sitegen/model/GenerationContext.java
  - src/main/java/org/ctc/sitegen/model/GroupSubTabView.java
  - src/main/java/org/ctc/sitegen/model/PhaseBreakdownEntry.java
  - src/main/java/org/ctc/sitegen/model/PhaseTabView.java
  - src/main/resources/static/site/css/style.css
  - src/main/resources/templates/site/driver-profile.html
  - src/main/resources/templates/site/driver-ranking.html
  - src/main/resources/templates/site/matchdays.html
  - src/main/resources/templates/site/standings.html
  - src/main/resources/templates/site/team-profile.html
  - src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java
  - src/test/java/org/ctc/domain/service/StandingsServiceTest.java
  - src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java
  - src/test/java/org/ctc/sitegen/DriverRankingPageGeneratorTest.java
  - src/test/java/org/ctc/sitegen/MatchdaysPageGeneratorTest.java
  - src/test/java/org/ctc/sitegen/SiteGeneratorBaselineCaptureTest.java
  - src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java
  - src/test/java/org/ctc/sitegen/SiteGeneratorPhaseAwarenessIT.java
  - src/test/java/org/ctc/sitegen/SiteGeneratorServiceIT.java
  - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
  - src/test/java/org/ctc/sitegen/StandingsPageGeneratorTest.java
  - src/test/java/org/ctc/sitegen/TeamProfilePageGeneratorTest.java
  - src/test/resources/sitegen/baseline/single-league-driver-profile.html
  - src/test/resources/sitegen/baseline/single-league-standings.html
  - src/test/resources/sitegen/baseline/single-league-team-profile.html
findings:
  critical: 0
  warning: 5
  info: 8
  total: 13
severity_counts:
  critical: 0
  warning: 5
  info: 8
  total: 13
status: issues_found
---

# Phase 62: Code Review Report

**Reviewed:** 2026-05-06
**Depth:** standard
**Files Reviewed:** 36
**Status:** issues_found

## Summary

Phase 62 macht den statischen Public-Site phase- und group-aware. Die Architektur ist sauber: 5 neue page-generator-Beans entkoppeln die Orchestrierung in `SiteGeneratorService`, ein neues `GenerationContext`-Record reduziert Parameter-Boilerplate, und `RaceLineup` bleibt Source of Truth für Driver-Team-Attribution. CLAUDE.md-Regeln werden weitgehend respektiert: Templates bleiben dünn, keine Inline-Styles, englische UI-Texte, dedizierter Test-Prefix in Test-Fixtures.

Es gibt **keine BLOCKER**. Aber:
- **Tatsächlich vorhandene Bugs/Defekte:** ein verbleibender Dead-Code-Helper in `DriverRankingService.resolveTeamFromLineup` (BUG-Risiko, B-1), eine inkonsistente alltime-Aggregation für Buchholz/PLAYOFF (W-2), eine schwache Fallback-Logik bei `attributeTeamFromRegularOrLineup` (W-3), und eine fragile lexikographische Season-Name-Sortierung in `calculateAlltimeRanking` (W-4).
- **Tests:** mehrere Generator-Tests laufen über `SiteGeneratorServiceIT`/`...IT` als `@SpringBootTest` mit `@DirtiesContext` und vollem `Flyway clean+migrate` pro Klasse — eine bekannte Ressourcenschlacht (W-5), aber funktional korrekt. Eine kritische Annahme im `DriverRankingPageGeneratorTest` ist, dass PLAYOFF-driver-data nichtleer ist — wenn sie es ist, fällt dieser Test in einen Branch, der "doesNotExist" prüft (zu schwach, I-7).
- **Security:** `SiteGeneratorService.cleanOutputDirectory` und `copyLogoToAssets` sind defensiv (`outPath.getNameCount() < 2`-Check, Path-Traversal-Check, normalize). Der scraped `videoId` wird sanitized. Keine SQL/SpEL/Path-Issues gefunden.

Die Hauptkritik ist Code-Qualität: eine ungenutzte (immer null zurückgebende) Helper-Methode wirkt im Quelltext wie ein TDD-Stub, der nicht aufgeräumt wurde, und das Doppel-Code-Muster `copyLogoToAssets` (zwei identische Kopien in `SiteGeneratorService` und `TeamProfilePageGenerator`) verstößt gegen DRY trotz der RESEARCH.md-Begründung.

## Warnings

### W-1: `DriverRankingService.resolveTeamFromLineup` ist toter Stub — gibt immer `null` zurück und macht per-phase Team-Attribution stillschweigend zu `null`

**File:** `src/main/java/org/ctc/domain/service/DriverRankingService.java:201-203`
**Issue:** Die private Methode `resolveTeamFromLineup(driverId, race)` ist auskommentiert/leer:

```java
private Team resolveTeamFromLineup(UUID driverId, Race race) {
    return null;
}
```

Sie wird in `calculateRankingForPhase` (Zeile 59) tatsächlich aufgerufen:

```java
Team team = resolveTeamFromLineup(driverId, result.getRace());
return new DriverRanking(result.getDriver(), team);
```

Konsequenz: für **per-phase rankings** (`driver-ranking-{phaseSlug}.html`) bekommt jede `DriverRanking` ein `team=null`. Im Template (`driver-ranking.html:32`) führt das zu `r.team != null ? r.team.shortName : '-'`, also Strich statt Teamname. Der JavaDoc behauptet zwar "team attribution intentionally left null", aber die UI-SPEC zeigt Team-Spalten als Standard auf der per-phase-Seite. Die Tests fassen diese Verhaltensentscheidung nicht ab — kein einziger Test prüft, dass die Team-Spalte auf `driver-ranking-regular.html` Werte enthält. Der Reviewer kann nicht beweisen, dass das gewünscht ist; basierend auf User-Erwartung ("Driver Ranking" zeigt zugehöriges Team) ist das ein Regression.

**Fix:** Entweder Methode korrekt implementieren (Lookup via `raceLineupRepository.findByRaceIdAndDriverId(...)` oder ähnlichem), oder sie ersatzlos entfernen und im Template den Branch dokumentieren. Wenn `null` wirklich beabsichtigt ist, JavaDoc-Begründung in den UI-SPEC verlinken und einen Test ergänzen, der die `-`-Anzeige festklopft.

```java
// Empfehlung: Lookup analog zu attributeTeamFromRegularOrLineup, aber per-race scope
private Team resolveTeamFromLineup(UUID driverId, Race race) {
    return raceLineupRepository.findByRaceIdAndDriverId(race.getId(), driverId)
            .map(RaceLineup::getTeam)
            .orElse(null);
}
```

### W-2: Inkonsistente Buchholz/Standings-Aggregation für `calculateAlltimeStandings` über mehrere Phasen

**File:** `src/main/java/org/ctc/domain/service/StandingsService.java:169-195`
**Issue:** D-19 wechselt `calculateAlltimeStandings` so, dass über alle SeasonPhasen aggregiert wird (REGULAR + PLAYOFF + PLACEMENT). Aber:

1. `calculateBuchholzScoresForPhase` (Zeile 245-247) leitet auf `calculateBuchholzScores(seasonId)` um, das wiederum nur **REGULAR-Matches** über `findByMatchdaySeasonIdAndPlayoffMatchupIsNull` (Zeile 214) zieht. Wenn alltime-standings künftig Buchholz aufrufen, mischt das REGULAR-only Buchholz-Werte mit cross-phase-Punkten — semantisch inkonsistent. Der Code ist defensiv, weil heute kein Caller `calculateAlltimeStandingsWithBuchholz` ruft, aber die Erweiterung ist eine Falltür: die JavaDoc in Zeile 240-244 schreibt "phase's matchdays are the only matchdays", was für PLAYOFF-Phasen falsch ist (PLAYOFF-Matchdays liegen ebenso in der Phase, nur Race finden geht nur über Season).

2. `calculateAlltimeStandings` ruft `calculateStandings(phase.getId(), null)` für **jede** Phase ab und merged via `parentTeam.getId()`. Bei einer PLAYOFF-Phase ohne `Match`-Rows liefert das eine leere Liste (`continue` in Zeile 175), was korrekt ist — aber bei einer LEAGUE/SWISS-Phase mit Matches werden Match-Punkte gemerged (`alltime.merge(standing)` Zeile 181). Das führt für eine 2023-style-Season mit GROUPS-REGULAR (12 Teams) + PLAYOFF (4 Teams als Match-Rows existieren nur theoretisch) korrekt: nur REGULAR-Matches zählen.

3. Aber `merge(other)` (Zeile 348) merged Wins/Draws/Losses/Points/PointsFor/PointsAgainst. Wenn ein Team in derselben Season zwei Phasen hat und in beiden REGULAR-style Matches gespielt wurden (z.B. PLACEMENT-Phase mit echten Match-Rows), zählen Wins doppelt. Test `givenSeasonWithPlayoffPhase_whenCalculateAlltimeStandings_thenIncludesPlayoffPoints` (StandingsServiceTest:894) mockt genau dieses Szenario und erwartet 6 Punkte — aber der Test mockt PLAYOFF mit echten Match-Rows. In der Realität (Phase 62) werden PLAYOFF-Punkte aus `Race.playoffMatchup` aggregiert, NICHT aus `Match`-Rows. Das heißt: der Test bestätigt nur das Mocking, nicht die Produktionsrealität.

**Fix:**
- Klarstellen via JavaDoc auf `calculateAlltimeStandings(List<Season>)`, dass nur Phasen mit `Match`-Rows beitragen, NICHT PLAYOFF-Bracket-Rounds.
- Für eine korrektere alltime-Aggregation müsste man PLAYOFF-Punkte aus PlayoffMatchup-Outcomes herleiten — Open Question für nachfolgende Phasen.
- `calculateBuchholzScoresForPhase` zumindest mit einem `if (phase.getPhaseType() != PhaseType.REGULAR) return Map.of()` absichern, damit zukünftige Caller nicht in die Falle laufen.

### W-3: `attributeTeamFromRegularOrLineup` Fallback verwendet `lineups.get(0)` ohne deterministische Ordnung

**File:** `src/main/java/org/ctc/domain/service/DriverRankingService.java:185-193`
**Issue:** Wenn der Driver keinen REGULAR-phase-Team-Match hat, wird auf `lineups.get(0).getTeam()` zurückgefallen. `findByDriverIdAndRaceMatchdaySeasonId` (im Repo) gibt eine `List` zurück, und ohne `ORDER BY` ist die Reihenfolge nicht deterministisch — H2 kann zwischen Test-Runs unterschiedlich sortieren. Damit wird das Team-Attribution-Ergebnis für stand-ins (cross-team-driver) **flaky**. Konkret: ein Driver, der in zwei Sub-Teams desselben Parent-Teams als Stand-in fährt (siehe TestDataService Zeile 644: `VRX_Driver05` taucht in `VRX A` UND `VRX B` auf), bekommt je nach DB-Iteration ein anderes Team auf alltime/season-aggregated-driver-ranking.

**Fix:** Repository-Query um deterministisches `ORDER BY` ergänzen (z.B. nach `race.matchday.sortIndex` ASC), oder im Service explizit sortieren:

```java
return lineups.stream()
        .filter(rl -> regularPhaseTeamIds.contains(rl.getTeam().getId()))
        .findFirst()
        .map(RaceLineup::getTeam)
        .orElseGet(() -> lineups.stream()
                .min(Comparator.comparing(rl -> rl.getRace().getMatchday().getSortIndex()))
                .map(RaceLineup::getTeam)
                .orElse(null));
```

Begründung: feedback_racelineup_source_of_truth + feedback_score_aggregation verlangen deterministische Aggregation; dieser Branch verstößt subtle dagegen.

### W-4: `calculateAlltimeRanking` verwendet lexikographische Season-Name-Sortierung statt year/number

**File:** `src/main/java/org/ctc/domain/service/DriverRankingService.java:151-153`
**Issue:**

```java
e -> e.getValue().stream()
        .max(Comparator.comparing(sd -> sd.getSeason().getName()))
        .map(sd -> sd.getTeam().getParentOrSelf())
```

Die "most recent" Season wird per `Season.name`-String alphabetisch ermittelt. `Season.name` ist vom User editierbar (z.B. "Regular Season", "Season 2023", "Season 2024 — Empty Phase"). Das führt zu falschem "most-recent"-Team:
- "Regular Season" (2024) > "Regular Season" (2026) — beide gleich → undefined Team-Pick
- "Season 2024 — Empty Phase" > "Season 2023" — funktioniert zufällig
- aber "Regular Season" < "Season 2023" → bei Driver der in beiden mitfuhr, würde S2023 als "most recent" gewinnen, obwohl 2024 später ist.

Der unit-Test `givenDriverInMultipleTeams_whenCalculateAlltimeRanking_thenShowsMostRecentTeam` (DriverRankingServiceTest:233) testet genau das mit `season.name = "2026"` und `season2.name = "2025"` und kommentiert: "Season '2026' > '2025' alphabetisch, so TNR is most recent". Der Test verschleiert das eigentliche Bug.

**Fix:** Nach `(year DESC, number DESC)` oder `Season.startDate` sortieren:

```java
.max(Comparator
        .comparingInt((SeasonDriver sd) -> sd.getSeason().getYear())
        .thenComparingInt(sd -> sd.getSeason().getNumber()))
.map(sd -> sd.getTeam().getParentOrSelf())
```

Test entsprechend mit zwei Seasons unterschiedlichen `year` updaten.

### W-5: 6 phase-aware ITs nutzen `@DirtiesContext` + Flyway `clean()+migrate()` pro Test-Klasse → CI-Laufzeit-Multiplikator

**File:** `src/test/java/org/ctc/sitegen/SiteGeneratorPhaseAwarenessIT.java:73-83` (und 5 weitere Klassen mit identischem Pattern)
**Issue:** `StandingsPageGeneratorTest`, `MatchdaysPageGeneratorTest`, `DriverRankingPageGeneratorTest`, `TeamProfilePageGeneratorTest`, `DriverProfilePageGeneratorTest`, `SiteGeneratorPhaseAwarenessIT` führen alle in `@BeforeAll`:

1. `Flyway.configure().clean()` (kompletter DB-Reset)
2. `Flyway.configure().migrate()` (alle V1+ Migrations)
3. `testDataService.seed()` (~5 Sekunden für Team-Cards via Playwright)
4. `siteGeneratorService.generate()` (>500 HTML-Files)

Pro Klasse → 6× pro `./mvnw verify`-Run. Bei `@TestInstance(PER_CLASS)` reduziert sich das auf je 1× pro Klasse, aber `@DirtiesContext` zwingt Spring zum Context-Reload zwischen Klassen. Das ist 30+ Sekunden CI-Overhead. Außerdem verteilt sich die SC4 byte-identity-Logik über mehrere Tests (Plan 0/Plan 1/Plan 4 Baselines), die alle dieselbe Fixture brauchen.

Der Comment-Block "Flyway clean+migrate guarantees a fresh DB regardless of preceding test classes" ist eine Workaround-Begründung, kein Architektur-Argument. Das wahre Problem ist, dass H2 mit `DB_CLOSE_DELAY=-1` zwischen Spring-Contexts persistiert.

**Fix:** Konsolidiere in ein einziges `@SpringBootTest`-IT (z.B. ausbauen von `SiteGeneratorPhaseAwarenessIT`), das einmal seedet+generiert, und teile die Output-Pfade per `static Path` zwischen `@Nested`-Klassen pro Page-Type. Alternativ `@TestExecutionListeners` mit DB-Cleanup nur einmal pro Maven-Modul.

Begründung: feedback_test_call_optimization (keine mehrfachen mvnw verify, gezielte tests, EIN finaler verify) — die Tests verstoßen sinngemäß gegen die Spirit dieser Regel auf intra-`verify`-Ebene.

## Info

### IN-1: `DriverRankingService.attributeTeamFromRegularOrLineup` hat ungenutzten Parameter `Driver driver`

**File:** `src/main/java/org/ctc/domain/service/DriverRankingService.java:184`
**Issue:** Parameter `driver` ist explizit als "unused here, for caller readability" dokumentiert, aber der Parameter wird im Methodenkörper nicht referenziert. Caller-Readability als Begründung ist schwach — die Methodensignatur kann der `driverId` ablesen lassen.
**Fix:** Parameter entfernen und alle Aufrufer (Zeile 91-92) anpassen.

### IN-2: `copyLogoToAssets` ist 27-LOC-Codeduplikat zwischen `SiteGeneratorService` und `TeamProfilePageGenerator`

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:469-495` und `src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java:226-252`
**Issue:** Beide Klassen tragen eine bit-identische Kopie (gleiche Path-Traversal-Defense, gleiche Logging-Strings, gleicher Catch-Block). Der RESEARCH.md-Kommentar "choice b: helper has its own copy" akzeptiert das explizit, aber zukünftige Wartung muss zwei Stellen anpassen — feedback_grep_all_usages verlangt vor jedem Refactoring eine codebase-weite Suche, dieses Duplikat erhöht die Wahrscheinlichkeit, dass eine der beiden Kopien stehengelassen wird.
**Fix:** Eine `LogoAssetCopier`-Component extrahieren, in beide Klassen via `@RequiredArgsConstructor` injizieren. Erfordert ein neues Bean, aber spart 27 LOC + eine `@Value("${app.upload-dir}")`-Duplikation.

### IN-3: `DriverProfilePageGenerator` mischt FQN-Imports und kurze Imports

**File:** `src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java:61, 89, 103`
**Issue:** Inline-FQN-Schreibweise wie `new java.util.HashSet<java.util.UUID>()`, `Collectors.toCollection(java.util.HashSet::new)`, `new LinkedHashMap<>()`. `LinkedHashMap` und `Set` sind oben importiert, `HashSet` und `UUID` nicht — vermutlich wurde die Import-Liste handgepflegt und blieb unvollständig.
**Fix:** Imports konsolidieren (`import java.util.HashSet; import java.util.UUID;`), FQN-Stellen durch kurze Namen ersetzen.

### IN-4: TestDataService verwendet inline-FQN-Imports `java.util.function.Function<...>` an mehreren Stellen

**File:** `src/main/java/org/ctc/admin/TestDataService.java:194-204, 351-358, 458-461, 569-582, 458` und weitere
**Issue:** Identisches Muster wie IN-3. Die Klasse hat 1076 Zeilen und ist dadurch unleserlicher als nötig. Mindestens 6 Stellen verwenden `java.util.function.Function<String, Team> findParent = ...` statt eines top-level Imports.
**Fix:** `import java.util.function.Function;` ergänzen, FQN-Stellen kürzen.

### IN-5: `DriverRankingService.calculateAlltimeRanking()` hat eine Kommentar-Diskrepanz zur Tracked-Behavior-Change

**File:** `src/main/java/org/ctc/domain/service/DriverRankingService.java:108-121`
**Issue:** Der JavaDoc auf der no-arg-Variante schreibt: *"Alltime only covers REGULAR-phase results (via `findByRacePlayoffMatchupIsNull`), so SeasonDriver is sufficient for team attribution."* Die seasonId-überladene Variante (D-19) inkludiert PLAYOFF-Results. Beide rufen denselben privaten Helper. Die Doku der no-arg-Variante widerspricht dem Behavior-Change-Kommentar der seasonId-Variante (Zeile 127-130: *"Tracked Behavior Change ... Aggregation now includes PLAYOFF-matchup-linked race results"*). Konsumenten müssen aktiv lesen, um zu wissen, ob die no-arg-Variante (genutzt von admin-Pages) auch geupdated wurde — aktuell nicht.
**Fix:** JavaDoc auf der no-arg-Variante ergänzen: *"Production callers SHOULD use `calculateAlltimeRanking(seasonIds)` to get cross-phase D-19 behavior; this no-arg variant is REGULAR-only for legacy admin views."*

### IN-6: Templates `standings.html` und `matchdays.html` haben Tag-Layout-Artefakte direkt nach `<section>` und nach `</nav>`

**File:** `src/main/resources/templates/site/standings.html:5,14,25` und `src/main/resources/templates/site/matchdays.html:5,14`
**Issue:** Erste Zeilen sehen so aus:

```html
<section><nav th:if="${showPhaseTabs}" ...>
   ...
</nav><nav th:if="${showGroupTabs}" ...>
```

Kein Whitespace zwischen `<section>` und `<nav>`, kein Whitespace zwischen den beiden `<nav>`s. Das kommt vermutlich aus dem byte-identity-Zwang (SC4) — leerer-Whitespace-Output musste gegen die Plan-0-Baseline matchen. Funktional korrekt, aber die HTML-Source ist hässlich und schwer zu lesen.
**Fix:** Wenn das byte-identity-invariant erlaubt: Whitespace einfügen für lesbarkeit. Sonst Comment ergänzen, der das beabsichtigte Markup-Layout dokumentiert (`<!-- whitespace omitted to satisfy SC4 byte-identity -->`).

### IN-7: `DriverRankingPageGeneratorTest.givenMultiPhaseSeason_whenGenerate_thenPerPhaseVariantsExist` hat schwachen "doesNotExist"-Branch

**File:** `src/test/java/org/ctc/sitegen/DriverRankingPageGeneratorTest.java:154-167`
**Issue:**

```java
if (playoffHasDrivers) {
    assertThat(seasonDir.resolve("driver-ranking-playoff.html")).exists();
} else {
    assertThat(seasonDir.resolve("driver-ranking-playoff.html")).doesNotExist();
}
```

Der Test passt sich an den DB-State an (`if (playoffHasDrivers)`). Wenn ein zukünftiger Refactor die Playoff-Lineup-Erzeugung bricht (z.B. `createPlayoffRaces` legt keine Lineups mehr an), nimmt der Test den `else`-Branch und passt klaglos. Der Test sollte das DB-Setup *garantieren* (Assert vor dem if), nicht spiegeln.
**Fix:**

```java
boolean playoffHasDrivers = !driverRankingService.calculateRankingForPhase(playoff.getId()).isEmpty();
assertThat(playoffHasDrivers)
        .as("TestDataService MUST seed PLAYOFF lineups for the SC2 fixture")
        .isTrue();
assertThat(seasonDir.resolve("driver-ranking-playoff.html")).exists();
```

### IN-8: `SiteGeneratorService.copyLogoToAssets` und `TeamProfilePageGenerator.copyLogoToAssets` werfen `Files.copy(..., REPLACE_EXISTING)` ohne Pre-Check, dass `target.getParent()` ein Directory ist

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:486-488` und `src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java:243-245`
**Issue:** Wenn `target.getParent()` zufällig auf ein File zeigt (z.B. ein anderer Generator hat dieselbe Pfadkomponente bereits als File angelegt), wirft `createDirectories` `FileAlreadyExistsException`. Der Catch fängt das, loggt nur eine Warning und gibt `null` zurück, sodass das fehlende Logo silent fehlt. Realistisch wahrscheinlich nicht, aber feedback_worktree_file_clobber zeigt, dass parallele Agent-Setups solche Edge-Cases auslösen.
**Fix:** Im Catch-Block mehr Detail loggen (`log.warn("Failed to copy logo: src={} target={} cause={}", logoFile, target, e.getMessage(), e)`), und ggf. Test ergänzen, der das Verhalten bei kollidierender Pfad-Komponente festklopft.

---

_Reviewed: 2026-05-06_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
