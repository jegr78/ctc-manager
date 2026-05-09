# Phase 59: Import & Test Data - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-28
**Phase:** 59-import-test-data
**Areas discussed:** Tab-Resolution & Season-Disambiguation, Group-Resolution & Warning-Surface, TestDataService Rebuild, Dev/Demo-Daten Showcase, Schreibreihenfolge SeasonDriver/PhaseTeam beim Execute, Migrations-Strategie für 2023-Konsolidierung, Test-Coverage-Strategie, Implementations-Reihenfolge

---

## Tab-Resolution & Season-Disambiguation

### Q1: Welche Tab-Patterns soll der Importer ab Phase 59 akzeptieren?

| Option | Description | Selected |
|--------|-------------|----------|
| Nur `2025` und `2025_S2` (Recommended) | Genau die zwei Patterns aus IMPORT-02: `^\d{4}$` (Legacy, 1 Saison/Jahr) + `^\d{4}_S\d+$` (z.B. `2025_S2`). Klare Konvention im Sheet, eine Regex-Erweiterung. | ✓ |
| Auch `2025-S2`, `2025_2`, `2025-2` | Liberalere Patterns mit mehreren Trennzeichen-Varianten. Mehr User-Komfort beim Sheet-Anlegen, aber breitere Regex und mehr Edge-Cases. | |
| Nur `2025_S2` (kein blankes `2025`) | Strict: jedes Sheet muss explizit die Saisonnummer im Tab-Namen tragen. Verhindert Mehrdeutigkeit von Anfang an, aber Bestands-Sheets mit `2025`-Tabs müssen umbenannt werden. | |

**User's choice:** Nur `2025` und `2025_S2`
**Notes:** Konvention bleibt schmal; backwards-compat via Auto-Resolution für Single-Saison-Jahre.

### Q2: Welche Signatur soll `SeasonRepository.findByYearAndNumber` haben?

| Option | Description | Selected |
|--------|-------------|----------|
| `Optional<Season>` (Recommended) | Setzt das Datenbank-UNIQUE-Constraint auf `(year, number)` voraus (fügen wir in Phase 59 mit V5-Migration hinzu, falls nicht da). Eindeutiger Vertrag im Code → kein 'Multiple seasons'-Fehler-Pfad mehr nötig. | ✓ |
| `List<Season>` (heutige Signatur) | Behält aktuelles API. Service muss bei `size != 1` selbst werfen / Fallback machen. Vermeidet DB-Migration, aber verschiebt das Eindeutigkeits-Problem in den Service. | |
| Beide: Optional als neuer Default + List @Deprecated | Wie Phase 58 D-01 Bridge-Pattern. Caller migrieren stückweise. Mehr Code, mehr Tests. | |

**User's choice:** `Optional<Season>` (recommended)
**Notes:** ABER später in Bereich 6 Migrations-Strategie nuanciert: kein DB-UNIQUE-Constraint einführen (Bestand-Konflikte), stattdessen Service-Layer-Wrapper mit BusinessRuleException. Repo-Methode bleibt deshalb `List<Season>`; das Service-Wrapper liefert das Optional-Verhalten.

### Q3: Tab `2025` (ohne `_S2`) bei MEHREREN Saisons im Jahr — wie verhalten?

| Option | Description | Selected |
|--------|-------------|----------|
| Manuelle UI-Auswahl im Preview (Recommended) | Wie heute, aber sauber: Tab-Preview zeigt Dropdown mit allen 2025-Saisons; Import nur nach Auswahl. `ambiguousReason='Multiple seasons for year 2025'` bleibt, Dropdown wird im Template gefüllt. | ✓ |
| Tab übersprungen + Fehler im Preview | Importer markiert Tab als unimportierbar; User muss Sheet umbenennen zu `2025_S1`/`2025_S2`. Strenger, fordert sauberes Sheet. | |
| Auto-Default zu number=1 | Wenn `2025` Tab und mehrere Saisons existieren, nimm immer `(2025, 1)`. Riskant — könnte versehentlich falsche Saison treffen. Nicht empfohlen. | |

**User's choice:** Manuelle UI-Auswahl (recommended)
**Notes:** UI-Dropdown selbst liegt aber in Phase 60 (UI-06); Phase 59 setzt nur den `ambiguousReason`-Datenpfad.

### Q4: Tab `2025` (ohne `_S2`) bei GENAU EINER Saison im Jahr — wie verhalten?

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-Resolution (Recommended) | Wenn nur eine 2025-Saison existiert, weise sie automatisch zu (`suggestedSeasonId=found.id`). Komfort für Single-Saison-Jahre, abwärtskompatibel mit Bestands-Sheets. | ✓ |
| Auch hier `2025_S1` erzwingen | Strict-Mode: `2025` Tab wird NIE auto-resolved, immer manuelle Auswahl nötig. Konsistent, aber lästig für Bestands-Workflow. | |

**User's choice:** Auto-Resolution
**Notes:** Backward-Compat-Komfort.

---

## Group-Resolution & Warning-Surface

### Q1: Wie soll die Group-Mitgliedschaft im Importer aufgelöst werden?

| Option | Description | Selected |
|--------|-------------|----------|
| Team → PhaseTeam(REGULAR) → group_id (Recommended) | 1-Schritt: aus dem Sheet-Team-Code wird das Team aufgelöst, dann via `phaseTeamRepository.findByPhaseIdAndTeamId(regularPhaseId, teamId)` die Gruppe ermittelt. RaceLineup nicht überhaupt konsultiert. | ✓ |
| Team → PhaseTeam, mit RaceLineup-Fallback | Wie oben, aber falls PhaseTeam fehlt: schau in RaceLineup der REGULAR-Phase. RaceLineup hat keine `group_id` — nur über Matchday.group_id ableitbar. Eher Overkill. | |
| Pure PhaseTeam, kein Fallback | Klare 1:1-Auflösung: PhaseTeam fehlt → Warning → import läuft mit `group_id=NULL` durch. Konsistent mit "No Fallback Calculations". | |

**User's choice:** Team → PhaseTeam(REGULAR) → group_id
**Notes:** Pure 1-Schritt-Auflösung. RaceLineup-Fallback aus Phase 58 D-10 ist Standings-Konzept, nicht Roster-Konzept.

### Q2: Wo werden Warnings im Preview-DTO untergebracht?

| Option | Description | Selected |
|--------|-------------|----------|
| `List<TabWarning>` pro TabPreview (Recommended) | Neues Record `TabWarning(WarningType type, String teamShortName, String message)` als Liste auf `TabPreview`. WarningType-Enum: `TEAM_NOT_IN_REGULAR_PHASE`, `GROUP_RESOLUTION_FAILED`. Template iteriert direkt, ist erweiterbar. | ✓ |
| Pro Row als optionales Warning-Feld | Jede Row-Variante (NewDriverRow, etc.) bekommt ein optional `String warning`-Feld. Granular, aber dupliziert die Info wenn ganzes Team fehlt. | |
| Beides: Tab-level für Team, Row-level für Driver-Edge-Cases | Tab-level für 'Team T-XYZ ist nicht in REGULAR-Phase' (1x pro Team), Row-level wenn ein Driver-spezifisches Problem auftritt. Mehr Code, aber detaillierter. | |

**User's choice:** `List<TabWarning>` pro TabPreview
**Notes:** Dedupliziert per teamShortName.

### Q3: Was passiert beim Execute, wenn ein Team kein PhaseTeam in der REGULAR-Phase hat?

| Option | Description | Selected |
|--------|-------------|----------|
| Durchlassen mit `group_id=NULL` (Recommended) | Driver wird via `SeasonDriver` zugewiesen, hat aber im Standings-Preview keine Gruppe. UI/Standings-View zeigt 'No group' für diese Drivers. Konsistent mit LEAGUE-Layout-Saisons. | ✓ |
| Tab abbrechen mit Fehler | Strict: Wenn auch nur ein Team kein PhaseTeam hat, schlägt der ganze Tab-Import fehl. | |
| Pro Driver entscheiden über `skip`-Param | Wie heute für Conflicts/Fuzzy: User klickt im Preview an. UI-Komplexität. | |

**User's choice:** Durchlassen mit `group_id=NULL`
**Notes:** Warning bleibt im Preview sichtbar; Standings rendert Driver ohne Group-Badge.

### Q4: Soll die Preview-Tabelle die aufgelöste Gruppe pro Row anzeigen?

| Option | Description | Selected |
|--------|-------------|----------|
| Ja, neue Spalte 'Group' im Preview (Recommended) | Pro Driver-Row wird die aufgelöste Group angezeigt. Backend ergänzt jeden Row-Record um optional `String resolvedGroupName`. UI-Detail liegt aber in Phase 60. | ✓ |
| Nein, nur Tab-level Group-Info | Driver-Rows bleiben unverändert; Tab-Preview-Header zeigt 'Group-aware' / 'LEAGUE'-Status. | |

**User's choice:** Ja, neue Spalte 'Group'
**Notes:** Backend-Datenfeld in Phase 59; Template-Rendering in Phase 60.

---

## TestDataService Rebuild

### Q1: Was soll mit den 2023-Saisons (Group A + Group B als 2 separate Seasons) passieren?

| Option | Description | Selected |
|--------|-------------|----------|
| Konsolidieren zu EINER GROUPS-Saison (Recommended) | 2023 wird zu (year=2023, number=1) mit EINER REGULAR-Phase, layout=GROUPS, 2 Gruppen 'Group A' + 'Group B' à 6 Teams. Saubere Demo des neuen Modells; Importer-E2E-Test deckt automatisch Group-Resolution ab. | ✓ |
| Beibehalten als 2 separate Saisons (Migrations-Realismus) | Das aktuelle 2023-Setup bleibt. Erfordert dass `findByYearAndNumber` doch List zurückgibt oder dass 1 von beiden auf number=2 geändert wird. | |
| Beibehalten, aber number unterscheiden: (2023, 1) + (2023, 2) | 2 separate Seasons, aber unique (year, number). Kein Group-Layout-Test in den Daten. | |

**User's choice:** Konsolidieren zu EINER GROUPS-Saison
**Notes:** Spiegelt das neue Modell sauber.

### Q2: Welche Test-Saison soll als GROUPS-Showcase dienen?

| Option | Description | Selected |
|--------|-------------|----------|
| Nur das konsolidierte 2023 reicht (Recommended) | 1x GROUPS-Saison im Test-Datensatz ist genug für DATA-01-Coverage. | ✓ |
| Zusätzlich aktive S4 (2026) auf GROUPS umstellen | Aktive Saison wird ebenfalls GROUPS-Layout. Massive Änderungen am bestehenden Match-/Race-Seed-Code. | |
| Zusätzlich neue 2025 GROUPS-Saison | Eine vierte Test-Saison als reine GROUPS-Showcase ohne Race-Daten. | |

**User's choice:** Nur das konsolidierte 2023 reicht
**Notes:** Vermeidet S4 Race-Data-Cascading.

### Q3: Was passiert mit `PhaseTestFixtures` (eingeführt in Phase 58 D-11)?

| Option | Description | Selected |
|--------|-------------|----------|
| Behalten für Mockito-Service-Tests (Recommended) | PhaseTestFixtures bleibt für reine Unit-Tests. TestDataService deckt den DB-/Integration-Test-Fall ab. Beide Werkzeuge bleiben nebeneinander. | ✓ |
| In TestDataService-Helper überführen | Konsolidiert Test-Setup, aber Mockito-Tests müssen umgestellt werden. | |
| Löschen | Risiko: Mockito-Tests müssen Fixtures inline bauen oder via DB-Setup laufen. | |

**User's choice:** Behalten für Mockito-Service-Tests
**Notes:** Klare Aufgabentrennung.

### Q4: Schreibt der TestDataService-Seed weiterhin `SeasonDriver`-Einträge oder pivotieren wir auf reines PhaseTeam?

| Option | Description | Selected |
|--------|-------------|----------|
| Beides parallel (Recommended) | SeasonDriver wird geschrieben + neu auch PhaseTeam. Konsistent mit Phase 58 D-10 (RaceLineup-Fallback für Stand-Ins via SeasonDriver). | ✓ |
| Nur PhaseTeam, SeasonDriver entfällt im Seed | RaceLineups setzen pro Race auf SeasonDriver auf. Ohne SeasonDriver würden viele bestehende Tests/UI-Pfade brechen. | |

**User's choice:** Beides parallel
**Notes:** SeasonDriver bleibt Source-of-Truth für Driver-Lineups vor Race-Erstellung.

---

## Dev/Demo-Daten Showcase

### Q1: Wie soll die konsolidierte 2023-GROUPS-Saison strukturiert sein?

| Option | Description | Selected |
|--------|-------------|----------|
| 2 Gruppen à 6 Teams (Recommended) | Spiegelt heutige Group A (6 Teams) + Group B (6 Teams) 1:1. Group A: ADR/ICL/SVT/NFR/HMS/VRX-A. Group B: EGP/PWR/VRX-B/SGM-B/SGM-S/TBR-R. | ✓ |
| 3 Gruppen à 4 Teams | Mehr Coverage für GROUPS-Variabilität, aber Restrukturierung der bestehenden 2023-Race-Daten. | |
| 2 Gruppen, asymmetrisch (5+7) | Edge-Case: ungleichgroße Gruppen, testet Bye-Logic im Swiss. Komplexer Seed-Code. | |

**User's choice:** 2 Gruppen à 6 Teams
**Notes:** Minimaler Diff zu bestehenden Race-Daten.

### Q2: Test-Season 2026 (T-ALF/T-BRV/T-BRV1/T-BRV2 aus `seedRaceLineups`) — welches Layout?

| Option | Description | Selected |
|--------|-------------|----------|
| Bleibt LEAGUE-Layout (Recommended) | Die isolierten Test-Saisons decken Sub-Team-Edge-Cases ab — nicht Groups. | ✓ |
| Auf GROUPS umstellen | 1-Gruppe-GROUPS ist semantisch nahe an LEAGUE. | |
| Neue zusätzliche Test-GROUPS-Saison anlegen | Test-Season 2025 als reine GROUPS-Test-Saison ohne Race-Daten. | |

**User's choice:** Bleibt LEAGUE-Layout
**Notes:** Test-Saison-Zweck = Sub-Team-Edge-Cases, nicht Groups.

### Q3: Welche PLAYOFF-Phasen sollen im Seed angelegt werden?

| Option | Description | Selected |
|--------|-------------|----------|
| Wie heute: nur 2023 + 2024 PLAYOFF (Recommended) | 2023 Playoff (Semifinal mit 4 Teams aus Group A/B Top-2) + 2024 Playoff (Final 2 Teams). | ✓ |
| Zusätzlich PLAYOFF auf S4 (2026, aktive Saison) | Aktive Saison bekommt erstmals PLAYOFF-Phase. Race-Daten in S4 sind noch nicht 'durch'. | |
| Nur 2023 PLAYOFF (2024 entfernen) | Reduzierter Demo-Datensatz. | |

**User's choice:** Wie heute (2023 + 2024)
**Notes:** Konsistenz mit Bestand.

### Q4: Soll für den Driver-Sheet-Import eine dedizierte E2E-Test-Saison existieren?

| Option | Description | Selected |
|--------|-------------|----------|
| Nein, reicht über konsolidiertes 2023 (Recommended) | E2E-Tests für den Importer nutzen die GROUPS-2023-Saison. | ✓ |
| Ja, separate 2025-Test-Saison | Isoliert die Importer-Tests vom restlichen Seed. | |

**User's choice:** Nein, reicht über konsolidiertes 2023
**Notes:** Vermeidet zusätzliche Saison.

---

## Schreibreihenfolge SeasonDriver/PhaseTeam beim Execute

### Q1: Wenn der Importer beim Execute ein Team trifft, das KEIN PhaseTeam in der REGULAR-Phase hat — soll automatisch ein PhaseTeam-Roster-Eintrag angelegt werden?

| Option | Description | Selected |
|--------|-------------|----------|
| Nein — nur SeasonDriver schreiben (Recommended) | Importer schreibt ausschließlich SeasonDriver wie heute. PhaseTeam-Roster wird manuell via Admin-UI (Phase 60) gepflegt. | ✓ |
| Ja — PhaseTeam mit group_id=NULL anlegen | Importer pflegt PhaseTeam mit. PhaseTeam ist Roster-Verantwortung der UI, Importer mischt sich ein. | |
| Nur wenn Importer beim NEW_DRIVER-Pfad: ja, sonst nein | Komplexere Logik. | |

**User's choice:** Nein — nur SeasonDriver schreiben
**Notes:** Saubere Verantwortungstrennung.

### Q2: Beim CONFLICT (Team-Wechsel im Sheet) — Driver wechselt zu Team B, Team B ist aber nicht in PhaseTeam: was tun?

| Option | Description | Selected |
|--------|-------------|----------|
| Wechsel durchlassen (SeasonDriver.team aktualisiert), Warning bleibt (Recommended) | Konsistent mit D-07. | ✓ |
| Wechsel blockieren / als Fehler markieren | Strict: Conflict-Resolution ist nur möglich, wenn das neue Team in PhaseTeam ist. | |

**User's choice:** Wechsel durchlassen
**Notes:** Warnings sind nicht-blockierend.

### Q3: Sollen die Importer-Tests verifizieren, dass die Group-Resolution KORREKT durchschlägt bis ins Standings?

| Option | Description | Selected |
|--------|-------------|----------|
| Ja, ein E2E-Test (Recommended) | Ein Integration-Test: Sheet-Tab `2023_S1` mit Driver `VRX_Driver01` → nach Import zeigt das Standings-Preview den Driver mit `group=Group A`. | ✓ |
| Nein, Service-Test reicht | Service-Test prüft nur die `resolvedGroupName`-Auflösung im PreviewDto. | |

**User's choice:** Ja, ein E2E-Test
**Notes:** Deckt IMPORT-03 SC3 vollständig ab.

### Q4: Welche Reihenfolge im `execute()`-Code: SeasonDriver vor PhaseTeam-Lookup, oder umgekehrt?

| Option | Description | Selected |
|--------|-------------|----------|
| PhaseTeam-Lookup ist nur für Preview/`resolvedGroupName`, Execute schreibt nur SeasonDriver (Recommended) | Saubere Trennung: Group-Resolution ist Preview-Layer, Execute-Layer schreibt SeasonDriver. | ✓ |
| Execute schreibt SeasonDriver UND aktualisiert PhaseTeam-Roster | Tighter coupling, mehr Code-Pfade. Würde D-07 aufweichen. | |

**User's choice:** PhaseTeam-Lookup nur Preview-only
**Notes:** Minimaler Diff, klare Verantwortung.

---

## Migrations-Strategie für 2023-Konsolidierung

### Q1: Soll eine V5-Migration `UNIQUE (year, number)` auf `seasons` einführen?

| Option | Description | Selected |
|--------|-------------|----------|
| Nein, kein DB-Constraint (Recommended) | Bestand-Duplicates bleiben möglich (Out-of-Scope). Service kapselt 'Optional<Season>'-Vertrag via Custom-Query. UI-User muss bei Mehrdeutigkeit das Sheet umbenennen oder via CONSOL-FUT-01 konsolidieren. | ✓ |
| Ja, V5 mit Auto-Renumber | Migration weist neue `number`-Werte auto zu. Heuristische Konsolidierung ist explizit Out-of-Scope laut REQUIREMENTS.md. | |
| Ja, V5 ohne Auto-Renumber (failing migration) | Migration setzt UNIQUE-Constraint, fällt fehl wenn Duplicates da sind. | |

**User's choice:** Nein, kein DB-Constraint
**Notes:** Respektiert die explizit Out-of-Scope-Definition.

### Q2: Was passiert, wenn der Importer einen Tab `2023_S1` sieht und (2023, 1) zweimal in DB hat?

| Option | Description | Selected |
|--------|-------------|----------|
| Service wirft `BusinessRuleException`, Importer markiert Tab als ambiguous (Recommended) | `SeasonRepository.findByYearAndNumber` returns `List<Season>`, Service wrappt: 0 → Empty, 1 → Of, >1 → wirft `BusinessRuleException`. Importer fängt das ab und setzt `ambiguousReason='Multiple seasons exist for (2023, 1) — consolidate them first'`. | ✓ |
| Service gibt einfach `Optional.empty()` zurück | Bei mehr als einem Treffer wird so getan, als gäbe es keine Saison. Schlecht. | |

**User's choice:** BusinessRuleException + ambiguousReason
**Notes:** Klare Audit-Spur.

### Q3: Wie soll die Repo-Methode tatsächlich implementiert werden?

| Option | Description | Selected |
|--------|-------------|----------|
| Repo `List<Season> findAllByYearAndNumber`, Service-Wrapper liefert Optional (Recommended) | Repo bleibt 'roh' (List), Service `SeasonManagementService.findUnique(year, number)` macht das Eindeutigkeits-Check und wirft bei >1. | ✓ |
| Repo direkt Optional via @Query mit COUNT-Check | JPQL-Subquery: returns Optional or throws. Spring Data Optional-Returns mit Multi-Treffer brechen normalerweise (NonUniqueResultException). | |

**User's choice:** Service-Wrapper über raw Repo
**Notes:** Repo bleibt neutral; Service-Layer hat die Domain-Regel. Korrigiert effektiv die "Optional<Season>"-Antwort aus Bereich 1 Q2.

### Q4: Ist die manuelle Saison-Konsolidierung (CONSOL-FUT-01) explizit OUT-OF-SCOPE für Phase 59?

| Option | Description | Selected |
|--------|-------------|----------|
| Ja, in `<deferred>`-Section dokumentieren (Recommended) | Phase 59 baut die Importer-Logik für das neue Modell. CONSOL-FUT-01 (UI-Feature 'Saisons konsolidieren') bleibt für eine spätere Milestone. | ✓ |
| Doch in Phase 59 mit-bauen (kleines UI-Feature) | Würde Scope deutlich aufblähen. | |

**User's choice:** Ja, in deferred dokumentieren
**Notes:** Scope-Disziplin.

---

## Test-Coverage-Strategie

### Q1: Welche Test-Schichten sollen IMPORT-01..04 abdecken?

| Option | Description | Selected |
|--------|-------------|----------|
| Unit + Service-IT, E2E deferred (Recommended) | Wie v1.8: Mockito-Unit-Tests + Service-IT mit gemocktem GoogleSheetsService. Playwright-E2E aufs Driver-Sheet-Importer ist fragil. | ✓ |
| Unit + Service-IT + Playwright-E2E | Vollständige Pyramide. Höherer Aufwand. | |
| Nur Service-IT (kein Mockito-Unit) | Verlangsamt Test-Suite. Phase 58 hat den umgekehrten Weg gewählt. | |

**User's choice:** Unit + Service-IT, E2E deferred
**Notes:** Konsistent mit v1.8-Entscheidung.

### Q2: Wie wird der Google Sheets API Call in Tests behandelt?

| Option | Description | Selected |
|--------|-------------|----------|
| GoogleSheetsService gemockt (Recommended) | `@MockBean`-mocked; Tests definieren Tab-Listen direkt. | ✓ |
| Echte Sheets-API in CI mit Service-Account-Credentials | Auth-Setup, Quote-Limits, fragile Tests. | |

**User's choice:** GoogleSheetsService gemockt
**Notes:** Mirrors v1.8 IT-Pattern.

### Q3: Welche Tests für den TestDataService-Rebuild (DATA-01) und DevDataSeeder (DATA-02)?

| Option | Description | Selected |
|--------|-------------|----------|
| @SpringBootTest Smoke-Tests + bestehende Test-Suite muss grün bleiben (Recommended) | Ein neuer @SpringBootTest verifiziert Seed-Resultat; alle bestehenden Service-/Controller-Tests müssen weiterlaufen. | ✓ |
| Plus expliziter PhaseTeam-Migration-Test | Zusätzlicher Test: SeasonDriver mit PhaseTeam-konsistent. | |
| Reduzieren auf Smoke-Test 'Spring-Context lädt' | Minimal. Weniger Coverage. | |

**User's choice:** Smoke-Tests + Regression-Coverage durch bestehende Suite
**Notes:** Pragmatisch, regression-getrieben.

### Q4: Wie strikt soll das 82%-Coverage-Gate für Phase 59 enforced werden?

| Option | Description | Selected |
|--------|-------------|----------|
| 82% Minimum für Phase 59-Code (Recommended) | Wie CLAUDE.md vorschreibt. Bewährt aus v1.x. | ✓ |
| Höhere Bar für Importer (90%+) | Importer ist user-visible und kritisch. Würde 82%-Standard projektweit nicht ändern. | |

**User's choice:** 82% Minimum projektweit
**Notes:** Konsistenz mit Bestand.

---

## Implementations-Reihenfolge

### Q1: Welche Reihenfolge für Phase 59-Pläne?

| Option | Description | Selected |
|--------|-------------|----------|
| Service-Wrapper + Importer + TestData+Demo (Recommended) | Wave 1: Service-Wrapper. Wave 2: Importer + TestDataService (parallel). Wave 3: E2E-Tests. | ✓ |
| TestData zuerst (Foundation), dann Importer | Vorteil: nachfolgende Tests laufen gegen das neue Modell. Nachteil: Importer-Code-Änderungen kommen später. | |
| Alles parallel ohne Waves | Maximale Parallelität, mehr Risiko bei Konflikten. | |

**User's choice:** Service-Wrapper → Importer + TestData parallel → E2E
**Notes:** Klare Abhängigkeiten.

### Q2: Sollen die Pläne logisch separat sein, oder zusammengefasst?

| Option | Description | Selected |
|--------|-------------|----------|
| 3-4 Pläne, klare Verantwortungen (Recommended) | Plan 59-01: SeasonRepository-Wrapper. Plan 59-02: DriverSheetImportService. Plan 59-03: TestDataService-Rebuild. Optional Plan 59-04: E2E-Tests. | ✓ |
| 2 große Pläne (Importer + Test-Daten) | Weniger Datei-Setup, größere Plan-Bodies. | |
| Alles in EINEM großen Plan | Macht Wave-Parallelität unmöglich. | |

**User's choice:** 3-4 Pläne mit klaren Verantwortungen
**Notes:** Atomare Plan-Größen.

### Q3: Soll PhaseTeam für die KONSOLIDIERTE 2023-Saison einmalig per Plan-Code im TestDataService gebaut werden, oder über bestehende Methoden?

| Option | Description | Selected |
|--------|-------------|----------|
| Im TestDataService-Code direkt (Recommended) | Self-contained Setup, keine Service-Abhängigkeit. Lombok-Builder-Cascade. | ✓ |
| Via SeasonPhaseService-API | Realistischere End-to-End-Validation, aber Test-Setup wird abhängig vom Service-Layer (zirkulär). | |
| PhaseTestFixtures ergänzen + reusen | Bridge zwischen Mockito-Welt und DB-Seed. | |

**User's choice:** Im TestDataService-Code direkt
**Notes:** Vermeidet Zirkularität.

### Q4: Wo wird die Group-Resolution-Logik (`Team → PhaseTeam(REGULAR) → group_id`) lokalisiert?

| Option | Description | Selected |
|--------|-------------|----------|
| Direkt im DriverSheetImportService (Recommended) | Lokal, einfach. Kein zusätzliches Service-Interface. | ✓ |
| Neuer `GroupResolutionService` | Wiederverwendbar — aber Phase 58 SVC-02 hat das schon über `StandingsService` abgedeckt. | |
| Methode auf `SeasonPhaseService` | Konsistent mit Phase 58 D-02. | |

**User's choice:** Direkt im DriverSheetImportService
**Notes:** Lokalität siegt; `SeasonPhaseService.findRegularPhase` (Phase 58 D-02) bleibt der zentrale Baustein für die Phase-Auflösung.

---

## Claude's Discretion

- Exact name of the service-wrapper method (`findUnique`, `findOneByYearAndNumber`, `findUniqueByYearAndNumber`).
- Whether `TabWarning.message` is rendered server-side (English String) or kept as a structured token for UI-side i18n.
- Whether `seedPhaseTeams()` is a new method on `TestDataService` or inlined into the existing `seedSeasons()`.
- Whether the PhaseTeam-and-SeasonDriver write order during execute matters (race conditions on save).
- Tests for the "ambiguous bestand-DB duplicate" case.
- Visibility / wiring of the `findRegularPhase` call inside `buildTabPreview` (per-row vs once-per-tab).

## Deferred Ideas

- `UNIQUE (year, number)` DB constraint — explicit Out-of-Scope per REQUIREMENTS.md.
- `CONSOL-FUT-01` UI for manual season-consolidation — future milestone.
- `IMPORT-FUT-01` Phase/Group override column in driver-import sheet — future milestone.
- Driver-import preview-template redesign — Phase 60 (UI-06).
- `PLAYOFF-FUT-01` sub-group-aware playoff brackets — future milestone.
- Drop of M:N `playoff_seasons` join table — Phase 61 (MIGR-06).
- Drop of legacy `Season` columns — Phase 61 (MIGR-06).
- Playwright E2E for the importer — deferred (mirrors v1.8 deferral).
- Real Google Sheets API call in CI — quote + auth concerns.
- Phase-level resolution caching beyond per-tab.
- PhaseTeam writes from the importer execute path — explicitly rejected.
