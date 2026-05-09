# Phase 70: Driver Import — Parent-Only Team Resolution — Context

**Gathered:** 2026-05-09
**Status:** Ready for planning
**Source:** Direct conversation with user 2026-05-09 after live UAT against local MariaDB (Saison 2023, parent MRL + Sub-Teams MRL 1/MRL 2 in unterschiedlichen Groups)

<domain>
## Phase Boundary

Korrektur an v1.9-Phase-66. Phase 66 hat einen Sub-Team-Resolver eingebaut (CONTEXT D-04: „sub-team-with-PhaseTeam wins over parent"), der gegen das echte Domänenmodell verstößt. Phase 70 baut diesen Default zurück und entfernt die mit ihm verkoppelte Group-Resolution-UX im Driver-Import-Preview komplett.

**In Scope:**
- `DriverSheetImportService.resolveTeamByShortName` Logik (Single + Multi-Match)
- Group-Resolution-Block im Preview-Pfad
- 5 Preview-Row-Records + `TabPreview.usesGroups`
- `DriverSheetImportController` Model-Attribute
- Template `driver-import-preview.html`
- Phase-66-Tests #16, #19, #20, #23, #24 (anpassen oder löschen)
- Phase-66-Doku-Addendum (Truths invertiert markieren)

**Explicitly Out of Scope:**
- RaceLineup / Match-Ebene Code (das ist die saubere Sub-Team-Schicht und bleibt unangetastet)
- `Season.getEligibleTeams()` (filtert parent mit Subs raus — bereits korrekt)
- `SeasonDriver`-Daten-Migration (User bestätigt: keine bestehenden falschen Einträge)
- `findByShortName` (wird weiter von TeamControllerTest, GroupsSeasonE2ETest mit unique Test-Prefix-shortNames genutzt — bleibt)
- Schema-Changes (keine Flyway-Migration; `phase_teams.group_id` bleibt nullable wie in V3)
- UAT-02 Legacy-season-real-data-Smoke (bleibt deferred wie in v1.9-MILESTONE-AUDIT)
- Quality Gate Lock / CI comment-noise guard (bleibt für v1.10)

</domain>

<decisions>
## Implementation Decisions

### Domänenmodell (vom User explizit bestätigt 2026-05-09)

- **D-01:** Teams treten für eine **komplette Saison** an. Alle Fahrer eines Teams sind am **Haupt-/Parent-Team** hinterlegt — `SeasonDriver.team_id` zeigt **immer** auf das Parent (oder ein Solo-Team ohne Subs). Sub-Team-Aufteilung erfolgt **pro Match** über `RaceLineup.team_id`, nicht pro Phase oder Saison.
- **D-02:** Fahrer können nur bei **Saisonwechsel** zu einem anderen Parent-Team wechseln. Innerhalb einer Saison ist `SeasonDriver.team_id` stabil; Sub-Team-Variationen geschehen ausschließlich auf RaceLineup-Ebene.
- **D-03:** Sub-Teams können **eigenständige shortNames** haben (z.B. `MRL` parent + `MRL 1`/`MRL 2` Subs). Sie können denselben shortName wie der Parent haben (z.B. `ZFS`/`ZFS` aus Phase-66-CONTEXT) — beide Fälle sind valide. Der shortName-Lookup darf von beiden Konventionen nicht abhängen.
- **D-04:** Im Driver-Sheet steht **immer der Parent-shortName** (User bestätigt 2026-05-09). Sheet-Werte wie `MRL 1` / `MRL 2` (Sub-shortName) sind **nicht** zu erwarten und werden nicht speziell behandelt — falls sie auftauchen und als unique shortName matchen, gibt der Single-Match-Pfad das Sub direkt zurück (kein Parent-Lookup), was per `resolveTeamByShortName(matches.size()==1)` bereits heute korrekt funktioniert.

### Driver-Import-Resolver (invertiert Phase 66 D-04)

- **D-05:** `resolveTeamByShortName(shortName, regularPhase)` → **immer Parent-Precedence**, unabhängig vom Phase-Layout:
  - 0 Matches → `Optional.empty()` (Aufrufer emittiert `UNKNOWN_TEAM_CODE`)
  - 1 Match → den nehmen (parent oder solo-sub mit eigenem shortName — beides legitim)
  - N Matches → erstes Team mit `parentTeam == null`; bei 0 Parents (Multi-Sub-data-integrity-Edge-Case) → WARN log + `matches.get(0)` deterministisch
- **D-06:** Der `regularPhase`-Parameter wird im neuen Resolver **nicht mehr benötigt**. Er kann entweder (a) ersatzlos aus Signatur + 5 Call-Sites entfernt werden, oder (b) als unbenutzter Parameter bleiben für Signatur-Stabilität. **Plan-Empfehlung: ersatzlos entfernen** — der Parameter ist Phase-66-Artefakt und sein Vorhandensein verleitet zu erneuter Layout-abhängiger Logik.
- **D-07:** Die `findRegularPhase`-Calls bei `DriverSheetImportService.java:130` und `:261` werden nicht mehr für die Resolver-Logik gebraucht. Falls sie an anderer Stelle im Pfad verwendet werden (z.B. für Season-Lookup beim CSV-Import), bleiben sie dort — Plan-Phase muss das einmal traceieren und entscheiden. Erwartung: beide Aufrufe werden überflüssig und können ersatzlos weg.

### Group-Resolution-UX (komplett raus)

- **D-08:** Group-Anzeige im Driver-Import-Preview macht generell keinen Sinn (User-Bestätigung 2026-05-09: „Group Anzeige macht beim Fahrer Import generell keinen Sinn — bezieht sich immer auf die komplette Saison"). Die Group-Zuordnung eines Fahrers ist Match-Level (RaceLineup) und beim Saison-Import unbekannt.
- **D-09:** Komplettes Entfernen folgender Code-Pfade:
  - `DriverSheetImportService.java:324-340` (Group-Resolution + TEAM_NOT_IN_REGULAR_PHASE-Warning-Emission)
  - `DriverSheetImportService.java:269` (`usesGroups` Berechnung)
  - `WarningType.TEAM_NOT_IN_REGULAR_PHASE` Enum-Konstante (line 529)
  - `resolvedGroupName` Field aus 5 Preview-Row-Records (`NewDriverRow`, `NewAssignmentRow`, `ConflictRow`, `FuzzySuggestionRow`, `UnchangedRow`)
  - `TabPreview.usesGroups` Field
  - `DriverSheetImportController` `showGroupColumn` Model-Attribute + zugehörige Page-wide GROUPS-Detection
  - Template `driver-import-preview.html`: Group-Spalten-Header + 5 per-row Group-Cells + Warning-Box für TEAM_NOT_IN_REGULAR_PHASE
- **D-10:** Verbleibende Fehlerkategorien im Driver-Import-Preview:
  - `BLANK_PSN_ID`, `BLANK_TEAM_CODE`, `UNKNOWN_TEAM_CODE`, `DUPLICATE_IN_TAB` — alle bleiben unverändert
  - Keine neue Warning-Kategorie ersetzt `TEAM_NOT_IN_REGULAR_PHASE`. Die Frage „in welcher Group fährt der Fahrer?" gehört Match-Phase, nicht Import-Phase.

### Test-Anpassung

- **D-11:** Phase-66-Tests, die das Sub-Team-Win-Verhalten oder die Group-Warning festschreiben, werden invertiert oder gelöscht:
  - Test #16 (warning fires for GROUPS) → **löschen** (Warning weg)
  - Test #19 (sub-team-with-PhaseTeam wins) → **invertieren**: parent gewinnt; sub-team mit PhaseTeam wird ignoriert
  - Test #20 (Phase 66 D-04 Path A) → **invertieren oder löschen**, je nach genauem Inhalt
  - Test #23 (LEAGUE phase: no warning) → **löschen** (trivial wahr nach Refactor)
  - Test #24 (`tab.usesGroups()=true` für GROUPS) → **löschen** (Field entfernt)
- **D-12:** Phase-66-Tests, die die robuste Multi-Match-Behandlung absichern (kein Crash bei collision), bleiben:
  - Test #21 (multi-parent edge case) → bleibt semantisch unverändert
  - Test #22 (no-REGULAR-phase fallback) → bleibt semantisch unverändert
- **D-13:** Mindestens **ein neuer Test** wird ergänzt, der das Parent-Always-Verhalten explizit absichert: `givenSheetReferencesParentShortNameWithSubsInGroupsPhase_whenPreview_thenAssignsParentNoWarning` — verifiziert, dass bei Setup „parent MRL + 2 Subs in 2 Groups + sheet sagt 'MRL'" das Resolved-Team das parent ist, keine Warning emittiert wird, und kein `phaseTeamRepository`-Aufruf erfolgt (`verifyNoInteractions(phaseTeamRepository)` für den Group-Pfad).
- **D-14:** Test-Daten-Konvention bleibt verbindlich (CLAUDE.md): Test-Entities mit Test-Prefix (`T-MRL`, `Test_Alpha_1`, etc.). Keine echten Team-Daten in Tests. Ggf. neue Test-Fixtures benötigen Prefix.

### Phase-66-Doku-Addendum

- **D-15:** `66-VERIFICATION.md` erhält am Ende ein neues Section: `## Phase-70 Re-Open Addendum (2026-05-09)`, das die invertierten Truths benennt:
  - Truth #2 (sub-team-with-PhaseTeam wins) — **superseded by Phase 70**: parent always wins
  - Truth #6 (TEAM_NOT_IN_REGULAR_PHASE layout-gated to GROUPS) — **superseded**: warning entfernt
  - Truth #7 (`TabPreview.usesGroups` from canonical signal) — **superseded**: field entfernt
  - Truth #8 (per-row Group cells gated by `tab.usesGroups()`) — **superseded**: cells entfernt
  - Truth #9 (page-wide `showGroupColumn` preserved) — **superseded**: column entfernt
- **D-16:** `66-CONTEXT.md` D-04..D-09 (Sub-Team-Resolver-Logik) erhalten am Ende des jeweiligen Bullet einen Inline-Vermerk `[superseded by Phase 70 D-05/D-09 — see 70-CONTEXT.md]`. Die Original-Wording bleibt erhalten (Audit-Trail).
- **D-17:** Frontmatter-Updates: `66-VERIFICATION.md` `re_verification` Block bekommt einen weiteren Eintrag mit `previous_status: passed`, `previous_score: 9/9`, `superseded_truths: [2, 6, 7, 8, 9]`, `superseded_by: phase-70`, `note: "Phase 70 inverts the season-aware sub-team resolver and removes group resolution from the import preview path. Original Phase-66 hotfix scope (no crash on shortName collision) remains satisfied — only the post-resolution-tail behavior changed."`

### Branch + Commit-Hygiene

- **D-18:** Aktive Branch `gsd/v1.9-season-phases-groups` an jedem Checkpoint und Commit (D-18 von Phase 67 + 69 wiederverwendet). Subagent-Prompts MÜSSEN explizit `git stash`, `git checkout`, `git reset`, Branch-Switching verbieten (CLAUDE.md Subagent Rules + `feedback_subagent_stability`).
- **D-19:** Kein Worktree für Phase 70 — Scope ist klein (~1 Service-Klasse + 5 Records + 1 Controller + 1 Template + Test-Suite). Inline auf `gsd/v1.9-season-phases-groups`.
- **D-20:** Conventional-Commit-Prefix-Strategie:
  - `refactor(70-NN): ...` für die Resolver-Logik-Inversion (kein Bug-Fix-Tag, weil Phase 66 D-04 ein bewusster Architektur-Default war, kein Bug)
  - `refactor(70-NN): ...` für Group-Resolution-Removal (UX-Decommission)
  - `test(70-NN): ...` für neue Tests / invertierte Tests
  - `docs(70-NN): ...` für Phase-66-Addendum
  - **Optional**: `fix(70-NN): ...` falls der Plan-Reviewer es für einen User-sichtbaren Bug-Fix hält. Plan-Phase entscheidet endgültig.

### Final Verify Gate

- **D-21:** Phase 70 schließt mit **`./mvnw verify -Pe2e`** (eine final-Verify, analog zu Phase 69 D-16). Surefire (1235 Tests) + Failsafe Playwright E2E (31 Tests) + JaCoCo line ≥ 0.82. Erwartete Test-Anzahl-Änderung: -3 bis -5 (gelöschte Phase-66-Tests) +1 (neuer Parent-Always-Test) = ~1230 Surefire-Tests danach. JaCoCo-Coverage darf nicht unter 0.82 fallen.
- **D-22:** Manuelles UAT durch den User auf der lokalen MariaDB nach dem `./mvnw verify -Pe2e`: Driver-Import auf Saison 2023 (parent MRL + MRL 1 in Group 2 + MRL 2 in Group 1) muss ohne Warnung durchlaufen, alle MRL-Fahrer müssen `SeasonDriver.team = MRL parent` bekommen. Auto-UAT über `playwright-cli` ist machbar (analog Phase 69 D-01), aber NICHT zwingend in Phase 70 erforderlich — der Plan-Phase entscheidet, ob er Auto-UAT als Plan-Schritt aufnimmt oder dem User die manuelle Verifikation überlässt.

### Plan-Organisation (Claude's Discretion)

- **D-23:** Planner darf 1-3 Plans wählen. Empfohlene Gruppierung:
  - **Plan 70-01**: Resolver-Inversion (`DriverSheetImportService.resolveTeamByShortName` parent-precedence; entfernt Group-Resolution-Block; entfernt `regularPhase`-Parameter wo möglich; entfernt `WarningType.TEAM_NOT_IN_REGULAR_PHASE`)
  - **Plan 70-02**: UX-Decommission (5 Records `resolvedGroupName` weg; `TabPreview.usesGroups` weg; Controller `showGroupColumn` weg; Template Group-Spalte + Warning-Box weg)
  - **Plan 70-03**: Test-Anpassung + Phase-66-Doku-Addendum + finaler `./mvnw verify -Pe2e`
- **D-24:** Wave-Strategie: Plan 70-01 + 70-02 können in Wave 1 parallel laufen (disjunkte Files: Service vs. Records+Controller+Template). Plan 70-03 in Wave 2 (depends_on 70-01 + 70-02, weil Tests nur grün sein können nachdem die Refactors landen). Final-Verify gehört in Plan 70-03.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Source of truth (Phase 66 baseline)
- `.planning/phases/66-team-shortname-collision-fix/66-CONTEXT.md` — D-01..D-09 dokumentieren die ursprüngliche Sub-Team-Resolver-Logik. D-04..D-09 werden durch Phase 70 superseded.
- `.planning/phases/66-team-shortname-collision-fix/66-VERIFICATION.md` — Truths #1..#9. Phase 70 inverts #2, #6, #7, #8, #9.
- `.planning/phases/66-team-shortname-collision-fix/66-01-SUMMARY.md`, `66-02-SUMMARY.md`, `66-03-SUMMARY.md` — Implementations-Notizen für die Phase-66-Defaults.
- `.planning/phases/66-team-shortname-collision-fix/66-UAT.md` — UAT-derived gaps GAP-66-01 + GAP-66-02 (closed by Phase 66 plans 02 + 03; Phase 70 supersedes both).

### Code (zu ändern)
- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` — primärer Refactor-Target:
  - Lines 130, 261: `findRegularPhase`-Aufrufe (vermutlich überflüssig nach Refactor)
  - Lines 269: `usesGroups` Berechnung (zu entfernen)
  - Lines 324-340: Group-Resolution-Block + Warning-Emission (zu entfernen)
  - Lines 435-458: `resolveTeamByShortName` (zu invertieren — parent-precedence, kein PhaseTeam-Lookup)
  - Lines 460-526: 5 Preview-Row-Records + `TabPreview` (zu reduzieren)
  - Line 528-529: `WarningType.TEAM_NOT_IN_REGULAR_PHASE` Enum (zu entfernen)
  - Call-Sites des Resolvers: lines 144, 155, 175, 204, 309 (`resolveTeamByShortName(rawTeamCode, regularPhase)`)
- `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` — `showGroupColumn` Model-Attribute + Detection-Logik (zu entfernen)
- `src/main/resources/templates/admin/driver-import-preview.html` — Group-Spalten-Header + 5 per-row Group-Cells + Warning-Box (zu entfernen)

### Code (zu erhalten — explizit unangetastet)
- `src/main/java/org/ctc/domain/model/Team.java` — Parent/Sub-Beziehung bleibt (kein Schema-Change)
- `src/main/java/org/ctc/domain/model/Season.java::getEligibleTeams()` (Lines 154-167) — bereits korrekt, filtert parent-mit-Subs raus
- `src/main/java/org/ctc/domain/model/RaceLineup.java` — Match-Ebene Sub-Team-Auswahl, das ist die saubere Schicht
- `src/main/java/org/ctc/domain/model/SeasonDriver.java` — `team` zeigt auf Parent (korrekt)
- `src/main/java/org/ctc/domain/repository/TeamRepository.java::findByShortName` (Phase-66-D-04 single) — wird weiter von TeamControllerTest, GroupsSeasonE2ETest genutzt; bleibt
- `src/main/java/org/ctc/domain/repository/TeamRepository.java::findAllByShortName` (Phase-66-D-03) — bleibt; wird vom neuen Resolver weiter genutzt
- `src/main/java/db/migration/V*.java` + `src/main/resources/db/migration/V*.sql` — keine Migrations-Changes (CLAUDE.md: V1 immutable; V2+ neue Files nur bei echtem Schema-Change; Phase 70 hat keinen Schema-Bedarf)

### Tests (zu ändern)
- `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` — Tests #16, #19, #20, #23, #24 anpassen oder löschen; Test #21, #22 bleiben; mindestens 1 neuer Test (D-13)
- `src/test/java/org/ctc/admin/controller/DriverSheetImportControllerTest.java` (falls vorhanden) — `showGroupColumn`-bezogene Tests anpassen
- E2E-Tests im `src/test/java/org/ctc/e2e/`-Pfad: ggf. `DriverImportE2ETest`/`GroupsSeasonE2ETest` prüfen, ob sie Group-Spalten-Assertions enthalten

### Project conventions (constraints — non-negotiable)
- `CLAUDE.md` — Subagent-Rules (model `opus`/`sonnet` für code, Branch-Schutz, Post-Dispatch-Validation, Atomic Tasks); Conventional-Commits in Englisch; TDD/BDD Given-When-Then Test-Naming; Test-Daten-Isolation (Test-Prefix); Coverage 82% Minimum; Flyway V1 immutable; OSIV active.
- `.planning/PROJECT.md` — v1.9-Milestone-Status (1235 unit + 31 E2E Tests, JaCoCo 87.25% nach Phase 69)
- `.planning/STATE.md` — aktuelle Position (Phase 69 COMPLETE, branch `gsd/v1.9-season-phases-groups`)
- `pom.xml` — JaCoCo `<minimum>0.82</minimum>` line gate (bleibt unverändert)
- `feedback_test_call_optimization` (memory) — keine mehrfachen `mvnw verify`; gezielte `./mvnw test -Dtest=...` für individuelle Tests; ein finaler `./mvnw verify -Pe2e` am Phase-Ende
- `feedback_e2e_verification` (memory) — Endverifikation immer mit `-Pe2e`
- `feedback_racelineup_source_of_truth` (memory) — RaceLineup vor SeasonDriver für Fahrer-Team-Zuordnungen (D-01 ist davon abgeleitet)
- `feedback_grep_all_usages` (memory) — vor jeder Refactor-Änderung codebase-weit nach Pattern suchen (besonders `resolveTeamByShortName`, `resolvedGroupName`, `usesGroups`, `showGroupColumn`, `TEAM_NOT_IN_REGULAR_PHASE`)

### Methodology mirrors
- `.planning/phases/69-milestone-closure-hygiene/69-CONTEXT.md` — Branch-Invariant + Test-Discipline + Final-Verify-Pattern (Phase 70 D-18..D-21 spiegeln Phase 69 D-17..D-19)
- `.planning/phases/65-graphics-bridge-migration/65-PLAN.md` — Multi-Plan-Refactor-Pattern (Phase 70 D-23/D-24 könnten dieselbe Wave-Struktur nutzen)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`Season.getEligibleTeams()`** (`Season.java:154-167`): bereits korrekt — filtert parent mit Subs aus den Match-Pairings raus. Phase 70 ergänzt nur den Driver-Import-Pfad mit konsistentem Verhalten (parent als SeasonDriver.team, Subs als RaceLineup.team).
- **`Team.parentTeam`/`subTeams`-Beziehung** (`Team.java:41-48`): vorhandenes Datenmodell, das die ganze Phase-70-Logik erlaubt (`parentTeam == null` → Filterkriterium für Resolver D-05).
- **`TeamRepository.findAllByShortName(String)`** (Phase 66 D-03): bleibt das Lookup-Tool des neuen Resolvers.
- **`SeasonDriver.team`** (`SeasonDriver.java:team`): zeigt heute schon auf das Team aus dem Resolver-Result. Nach Refactor zeigt es konsistent auf Parent (für Teams mit Subs) oder auf solo-Team (für Teams ohne Subs) — keine Schema-Änderung nötig.

### Established Patterns
- **Conventional-Commit-Scope** (`refactor(NN-MM):`, `test(NN-MM):`, `docs(NN-MM):`): Phase 67/69 Convention.
- **Atomic Per-Task Commits** (CLAUDE.md "Atomic Tasks"): jeder Task = 1 commit; kein Mass-Refactor in einem einzigen Commit.
- **Phase-Übergreifende Doku-Addenda** (Phase 67 ACCEPT-Override, Phase 61 UAT-Closure): das Pattern für D-15/D-16/D-17 Phase-66-Re-Open-Addendum.
- **Final `./mvnw verify -Pe2e` als einziger Full-Verify** (Phase 69 D-16/D-17): Phase 70 wendet dasselbe an (D-21).

### Integration Points
- Phase 70 berührt **kein** anderes v1.9-Phasen-Artefakt außer Phase 66 (Doku-Addendum). Plan SUMMARYs der Phasen 56-65, 67-69 bleiben unangetastet.
- Phase-69-Verification-Result (38/38 REQ-IDs satisfied, integration PASS) bleibt nach Phase 70 valid — IMPORT-04 wird invertiert, aber die Wiring-Chain-Validierung ist unverändert (DriverSheetImportService bleibt mit SeasonPhaseService verkoppelt; Resolver-Internals ändern sich nur).
- v1.9-MILESTONE-AUDIT.md erhält **kein** Re-Audit-Update durch Phase 70 selbst — der nächste `/gsd-audit-milestone v1.9` Lauf nach Phase-70-Completion wird das automatisch picken.

### Risks
- **Test-Coverage-Drift**: Wenn 4-5 Phase-66-Tests gelöscht werden, kann JaCoCo line coverage knapp unters 0.82-Gate fallen, weil ungetesteter Code rausfliegt. Plan-Phase muss Coverage-Impact pre-execution kalkulieren; ggf. neue Test-Fälle hinzufügen, um Gate zu halten.
- **`regularPhase`-Parameter-Removal**: Wenn der Parameter aus `resolveTeamByShortName` entfernt wird, müssen 5 Call-Sites angepasst werden. Falls eine Call-Site den Parameter aus anderen Gründen erwartet (z.B. Method-Reference-Compatibility), kann das brechen. Plan-Phase muss alle 5 Call-Sites tracen.
- **Multi-Parent-Edge-Case bei Single-Match**: Aktueller Code in line 440-441 (single match → return directly) ignoriert das parent-Filter. Das ist OK für Single-Match (parent oder solo-sub, beides legitim per D-04). Aber: stell sicher, dass es keine versteckte Test-Erwartung gibt, die das anders sieht.

</code_context>

<deferred>
## Deferred Ideas

- **Auto-UAT für Driver-Import als Plan-Step** (D-22): Auto-UAT via `playwright-cli` analog zu Phase 69 D-01 wäre möglich, aber Phase 70 lässt den Plan-Phase entscheiden. Wenn nicht in Plan 70-03 aufgenommen, dann manueller User-Smoke-Test nach `./mvnw verify -Pe2e`.
- **Sheet-shortName-Validation gegen `findByShortName(parent)`**: Falls in Zukunft ein Sheet aus Versehen einen Sub-shortName referenziert (z.B. „MRL 1"), könnte das Single-Match-Verhalten überraschen — Driver wird dem Sub zugeordnet (was per D-04 Konvention nicht erwartet ist, aber technisch ok). Eine UI-Warnung „Sheet referenziert Sub-Team — soll auf Parent normalisiert werden?" wäre denkbar, ist aber out-of-scope für Phase 70.
- **REQUIREMENTS.md IMPORT-04 Wording**: IMPORT-04 lautet aktuell vermutlich „warning for unmapped teams" o.ä. Die Beschreibung müsste auf „UNKNOWN_TEAM_CODE für unbekannte Sheet-shortNames" reduziert werden. Das ist eine Doku-Änderung in REQUIREMENTS.md, die der Plan-Phase als optionalen Task aufnehmen kann.
- **Group-Anzeige in einer anderen UX-Stelle**: Falls Stakeholder später doch eine Vorschau „in welcher Group fährt dieser Fahrer typischerweise?" wollen, gehört das in einen separaten Match-Lineup-Editor, nicht in den Saison-Import. Out-of-scope.
- **Quality Gate Lock / CI comment-noise guard** (carried from Phase 67 D-06 + Phase 69 deferred): bleibt für v1.10.
- **UAT-02 Legacy-season-real-data-Smoke** (carried from Phase 61 D-02 + Phase 69 deferred): bleibt opportunistisch nach nächstem prod-deploy.

</deferred>

---

*Phase: 70-driver-import-parent-only-team-resolution*
*Context gathered: 2026-05-09 via direct conversation after live UAT against local MariaDB*
*Branch at context-gathering: `gsd/v1.9-season-phases-groups`*
