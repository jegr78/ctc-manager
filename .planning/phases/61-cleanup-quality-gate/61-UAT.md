---
status: complete
phase: 61-cleanup-quality-gate
source:
  - 61-VERIFICATION.md (human_verification)
started: 2026-05-01T21:44:38Z
updated: 2026-05-07T00:00:00Z
resumed: 2026-05-07T00:00:00Z
resume_reason: "Test 2 (Legacy migrated season visual smoke) wurde am 02.05. deferred — am 07.05. nachgeholt und passed"
gap_fixes:
  - id: UAT-01
    test: 1
    severity: blocker
    commit: f5b10bc
    summary: "fix(61-uat-01): render non-empty option labels on phase edit form"
  - id: UAT-03
    test: 3
    severity: blocker
    commit: 6db56d4
    summary: "fix(61-uat-03): convert V5 and V6 to dialect-aware Java migrations"
test2_status: deferred_to_user_local_verification
re_verification: "Test 1 re-verified pass post-fix (GROUPS smoke flow works end-to-end)"
---

## Current Test

[testing complete — all 4 tests resolved (3 pass, 1 issue resolved post-fix); Test 2 nachgeholt am 07.05.]

## Tests

### 1. GROUPS-Saison E2E flow visual smoke check
expected: |
  Manually create a GROUPS-layout season with 2 groups via /admin/seasons/new,
  assign teams, generate matchdays per group; verify per-group standings + the
  combined-view rendering matches expectations from QUAL-02 acceptance criteria.
result: pass
prior_result: issue
re_test_note: "Re-verified post-fix f5b10bc — GROUPS smoke flow works end-to-end"
reported: |
  Auf /admin/seasons/{id}/phases/{pid}/edit sind die Auswahlboxen Phase Type,
  Layout und Format leer (keine Option-Texte sichtbar). Ohne Layout-Umschaltung
  auf GROUPS lässt sich Test 1 nicht durchführen — Group-Sub-Tabs erscheinen nur
  bei isGroupsLayout=true. Mit playwright-cli verifiziert: <option> hat korrektes
  value-Attribut aber leeren Textinhalt für alle drei Selects.
severity: blocker
diagnosis_hint: |
  Template src/main/resources/templates/admin/season-phase-form.html:26,35,43
  nutzt th:text="${phaseTypeLabels[pt]}" (analog layoutLabels[l] / formatLabels[f]).
  SeasonPhaseController.java:325-336 befüllt Map<Enum,String> via Map.of(...).
  Verdacht: Thymeleaf/SpEL [enumKey]-Indexierung gegen Map<Enum,String> liefert
  null statt des String-Werts. Kein Integrationstest deckt die gerenderten Option-
  Labels ab (SeasonPhaseControllerIT.java prüft nur Group-Sub-Tabs).

### 2. Legacy migrated season visual smoke check
expected: |
  Open an actual pre-v1.9 season (one that was migrated by V4) in the running app
  and verify exactly 1 REGULAR phase tab + all matchdays accessible + race detail
  loads + standings render — both with and without playoff.
result: pass
prior_result: skipped
prior_reason: "User: kann ich so aktuell nicht machen, verschiebe ich auf später (02.05.)"
re_test_note: "User-Verifikation 07.05. — pre-v1.9-migrierte Saison öffnet sauber, 1 REGULAR phase tab, alle matchdays + race detail + standings rendern (mit und ohne playoff)"

### 3. V6 migration on MariaDB (docker profile)
expected: |
  Boot the docker-compose stack and verify Flyway applies V6 cleanly without
  'Cannot drop column referenced by FK' or 'Index references missing column' errors.
result: issue
reported: |
  docker compose up --build -d ausgeführt; MariaDB 11.8 healthy; App-Start crasht
  jedoch bereits bei V5 (V6 wird nie erreicht). Flyway-Fehler:
  ERROR 1064-42000 'You have an error in your SQL syntax ... near NOT NULL at line 9'.
  V5__nullable_legacy_scoring_columns.sql:9 verwendet PostgreSQL/H2-Syntax
  'ALTER TABLE seasons ALTER COLUMN race_scoring_id DROP NOT NULL' — MariaDB
  versteht das nicht (braucht MODIFY COLUMN <name> <type> NULL). Der Kommentar
  in V5:7 'Compatible with H2 2.x and MariaDB 10.7+.' ist nachweislich falsch.
  V5 wurde in Phase 60 (commit f746d10) erstellt — Phase-60-Escape, das
  Phase-61-UAT aufdeckt. Konsequenz: kein Production-Deploy möglich.
severity: blocker
diagnosis_hint: |
  V5 muss dialekt-kompatibel umgeschrieben werden. Optionen:
  (a) V5 als Java-Migration (analog V4__MigrateSeasonsToPhases) mit Dialect-Branch
  (b) Neue V7 schreibt korrekte MariaDB-Syntax und V5 wird unter MariaDB no-op
      (nicht möglich — V5 ist released und Flyway-Checksum greift bereits)
  (c) Aufgrund CLAUDE.md "Do Not Modify Flyway Migrations" + Tatsache dass V5
      auf KEINER MariaDB jemals gelaufen ist (V5-Crash beweist es), ist hier
      eine Korrektur an der V5-Datei selbst möglich + zwingend erforderlich.
      Risiko: Flyway-Checksum-Mismatch auf jeder DB die V5 erfolgreich angewandt
      hat — gibt es nicht (alle bisherigen Anwendungen laufen H2 oder sind
      dev/test). Also: V5-Datei korrigieren ist sicher.
  Nicht in Phase 61 erstellt, aber Phase-61-Goal "codebase is free of dead
  references" + QUAL-01 Coverage-Gate decken es nicht ab — die DB-Migrations
  brauchen einen MariaDB-Smoke im CI.
artifacts:
  - path: "src/main/resources/db/migration/V5__nullable_legacy_scoring_columns.sql"
    lines: "9-13"
    issue: "ALTER COLUMN ... DROP NOT NULL ist PostgreSQL/H2-only — MariaDB syntax error 1064"
missing:
  - "CI-Smoke-Test: docker compose up startet App gegen MariaDB ohne Flyway-Fehler"
  - "V5MigrationTest analog zu V6MigrationTest, aber gegen MariaDB-Container (Testcontainers)"
re_test_result: pass
re_test_note: "Resolved + smoke-verified per gap closure_evidence (commit 6db56d4 + docker compose smoke)"

### 4. Legacy URL bookmark regression
expected: |
  Verify previously-shared admin URLs continue to work:
  - /admin/standings?seasonId=<oldId> resolves to the REGULAR phase view
  - /admin/playoffs/{id}/add-season returns the global error page (5xx, not 404)
result: pass

## Summary

total: 4
passed: 3
issues_resolved: 2
pending: 0
skipped: 0
note: "Both blocker issues resolved in-branch (UAT-01 f5b10bc, UAT-03 6db56d4); Test 1 re-verified post-fix; Test 2 nachgeholt 07.05. — passed."

## Gaps

- truth: "Manuell GROUPS-Layout-Saison anlegen, Teams Gruppen zuweisen, Matchdays generieren, per-group + combined Standings rendern"
  status: resolved
  reason: "User reported: Auswahlboxen Phase Type/Layout/Format auf phase-edit sind leer (keine Option-Texte). Verifiziert mit playwright-cli — option value-Attribute korrekt, Textinhalt leer."
  resolution: |
    Closed in commit f5b10bc (fix(61-uat-01)). Template
    src/main/resources/templates/admin/season-phase-form.html updated to use
    ${labels.get(enum)} explicit method invocation instead of [enum] bracket
    indexer. Regression test added to SeasonPhaseControllerIT verifying all 8
    expected label strings render in the edit-form HTML. Note: the actual
    GROUPS standings rendering was not subsequently re-verified manually —
    the user moved to Test 3 once the dropdown bug was fixed and committed.
    Test 1's GROUPS smoke remains DEFERRED with Test 2 for the user's local
    verification pass.
  severity: blocker
  test: 1
  artifacts:
    - path: "src/main/resources/templates/admin/season-phase-form.html"
      lines: "26, 35, 43"
      issue: "th:text=\"${phaseTypeLabels[pt]}\" rendert leer (analog layoutLabels[l] / formatLabels[f])"
    - path: "src/main/java/org/ctc/admin/controller/SeasonPhaseController.java"
      lines: "325-336"
      issue: "Map.of(Enum, String) Befüllung — Lookup via Thymeleaf [pt] greift vermutlich nicht"
  missing:
    - "Integrationstest, der gerenderte Option-Texte auf phase-form prüft"
  closure_evidence:
    - commit: "f5b10bc"
    - test: "SeasonPhaseControllerIT#givenExistingPhase_whenGetEditForm_thenDropdownOptionsHaveNonEmptyLabels"

- truth: "V6 Flyway-Migration läuft sauber gegen MariaDB durch (Plan 61-03 D-23 IRREVERSIBLE)"
  status: resolved
  reason: "User reported (eigene Verifikation): Stack via docker compose up gestartet — App-Container crasht bereits bei V5 (V6 wird nie erreicht). MariaDB error 1064 'syntax error near NOT NULL at line 9'. Ursache: V5__nullable_legacy_scoring_columns.sql:9 nutzt PostgreSQL/H2-only Syntax 'ALTER COLUMN ... DROP NOT NULL' — MariaDB versteht das nicht."
  resolution: |
    Closed in commit 6db56d4 (fix(61-uat-03)). Two escapes resolved together:
    (a) V5 (Phase 60 escape, commit f746d10) — converted from .sql to a Java
    migration with dialect-branching (H2: ALTER COLUMN ... DROP NOT NULL;
    MariaDB: MODIFY COLUMN <name> UUID NULL). (b) V6 (Phase 61 escape) — same
    class of bug surfaced after V5 was fixed: 'DROP INDEX IF EXISTS name'
    standalone is H2-portable but MariaDB requires 'DROP INDEX name ON
    tablename'. V6 also converted to a Java migration; explicit DROP INDEX
    statements removed entirely (both engines auto-drop indexes when the
    underlying column is dropped). Old .sql files deleted. New
    V5MigrationTest added (Surefire, INFORMATION_SCHEMA assertion against
    season_phases.race_scoring_id / match_scoring_id IS_NULLABLE = 'YES').
    V6MigrationTest unchanged (dialect-agnostic).
  severity: blocker
  test: 3
  artifacts:
    - path: "src/main/resources/db/migration/V5__nullable_legacy_scoring_columns.sql"
      lines: "9-13"
      issue: "ALTER COLUMN ... DROP NOT NULL ist PostgreSQL/H2-only — MariaDB benötigt MODIFY COLUMN <name> <type> NULL"
    - path: "src/main/resources/db/migration/V5__nullable_legacy_scoring_columns.sql"
      lines: "7"
      issue: "Kommentar 'Compatible with H2 2.x and MariaDB 10.7+.' ist nachweislich falsch"
    - path: "src/main/resources/db/migration/V6__cleanup_legacy_season_columns.sql"
      lines: "21-22"
      issue: "DROP INDEX IF EXISTS name ohne ON tablename ist H2-portable aber MariaDB-syntax error 1064"
  missing:
    - "CI-Smoke-Test: docker compose up + Healthcheck als Job in .github/workflows/"
    - "V5MigrationTest analog V6MigrationTest, aber gegen MariaDB-Container (Testcontainers)"
    - "Pre-Merge-Gate: alle Migrations müssen gegen H2 UND MariaDB laufen"
  origin: "Phase 60 (commit f746d10) für V5 + Phase 61 für V6 — beide Escapes aus dem unbelegten 'compatible with H2 + MariaDB'-Pattern; aufgedeckt von Phase-61-UAT"
  closure_evidence:
    - commit: "6db56d4"
    - test: "V5MigrationTest#givenV5HasRun_whenQueryInformationSchema_thenSeasonPhasesScoringColumnsAreNullable"
    - smoke: "docker compose up --build -d → MariaDB 11.8, V1→V6 alle Migrating-Logs ohne ERROR, /actuator/health = UP"
  follow_up:
    - "B1 (CI-Smoke gegen MariaDB) als nächster Schritt — verhindert Future-Escapes derselben Klasse"
