# Release Management Design

## Context

Das CTC-Manager-Projekt naehert sich dem ersten Release (1.0.0). Bisher gibt es keine Versionierung, keine Tags, kein CHANGELOG und keinen Release-Prozess. Die aktuelle Version in `pom.xml` ist `0.0.1-SNAPSHOT`.

**Ziel:** Automatisiertes Release-Management mit Semantic Versioning, Conventional Commits und Docker-Image-Publishing bei jedem Merge nach `master`.

## Entscheidungen

| Aspekt | Entscheidung |
|--------|-------------|
| Versionierung | Semantic Versioning (SemVer) |
| Commit-Konvention | Conventional Commits (englisch) |
| Release-Trigger | Automatisch, sobald alle Pflicht-Checks der Master-Revision grün sind |
| Version-Bestimmung | Aus Commit-Messages abgeleitet (feat→Minor, fix→Patch, BREAKING CHANGE→Major) |
| Erste Version | 1.0.0 |
| Docker Registry | GitHub Container Registry (ghcr.io) |
| Entwicklungsversionen | Immer SNAPSHOT (z.B. `1.1.0-SNAPSHOT`) |
| Flyway Migrationen | Bestehende Dateien eingefroren ab 1.0.0, nur neue Versionen |

## Workflow

### Entwicklungsphase

```
feature/xyz Branch → Squash-Merge PR → master (SNAPSHOT)
```

- `pom.xml` enthaelt immer `X.Y.Z-SNAPSHOT` auf `master`
- Feature-Branches werden per Squash-Merge nach `master` gemergt
- PR-Title folgt Conventional Commits: `feat: add playoff bracket seeding`
- Der Squash-Merge-Commit auf `master` uebernimmt den PR-Title

### Release-Phase (automatisch)

```
master Push → CI + CodeQL grün → Gate → Version bestimmen → Build → Tag + master atomar → Release → Docker → SNAPSHOT Bump
```

1. `release.yml` startet per `workflow_run`, wenn ein `CI`- oder `CodeQL SAST`-Lauf eines Pushs auf `master` endet. Ein Push selbst löst keinen Release aus.
2. `scripts/ci/release-gate.sh` prüft die Revision `head_sha` des auslösenden Laufs. Released wird nur, wenn der jeweils letzte Push-Lauf von `ci.yml` und `codeql.yml` auf dieser Revision erfolgreich ist und darin die Jobs `build-and-test`, `dockerfile-noble-pin-guard`, `docker-build` und `Analyze (java-kotlin)` erfolgreich sind. Das Gate liest Workflow-Runs statt Check-Runs, weil jeder Workflow mit `checks: write` einen Check-Run gleichen Namens anlegen könnte. Läuft ein Check noch, ist er fehlgeschlagen, abgebrochen oder fehlt er, endet der Lauf als Skip mit Hinweis. Der zweite der beiden `workflow_run`-Läufe released dann.
3. Hat `master` die Revision schon überholt (außer durch Release- und Bump-Commits), ist sie obsolet und wird übersprungen; die neuere Revision released mit ihren eigenen Checks.
4. `concurrency: release` serialisiert alle Läufe; ein laufender Release wird nie abgebrochen.
5. `scripts/ci/release-version.sh` liest die Commits seit dem letzten Tag und bestimmt den SemVer-Bump via Conventional Commits.
6. Setzt die Release-Version in `pom.xml`, baut mit `./mvnw verify`, committet `release: vX.Y.Z` (mit `[skip ci]` im Body) und pusht Commit, Tag und `master` mit `git push --atomic`.
7. Erstellt das GitHub Release, baut und pusht das Docker-Image zu `ghcr.io/jegr78/ctc-manager:X.Y.Z` + `:latest`.
8. Bumpt `pom.xml` auf den nächsten SNAPSHOT und committet `chore: bump version to X.Y.Z-SNAPSHOT [skip ci]`.

### Wiederaufnahme nach einem Abbruch

Liegt auf `master` direkt über der Revision ein `release: vX.Y.Z`-Commit mit Tag `vX.Y.Z`, der nur die Version in `pom.xml` auf `X.Y.Z` ändert, setzt ein erneuter Lauf derselben Revision diesen Release fort statt eine neue Version zu bestimmen. Er erstellt nur, was fehlt: das GitHub Release (`gh release view`), das Image (`docker manifest inspect`) und den SNAPSHOT-Bump (nur wenn `master` noch auf `X.Y.Z` steht). Ändert der getaggte Release-Commit mehr, oder existiert ein Tag `vX.Y.Z` ohne passenden Release-Commit, bricht der Lauf ab. Landet vor dem erneuten Lauf schon ein neuer Commit, released dieser eine neue Version; das fehlende Release der alten Version wird dann von Hand nachgezogen (`docs/operations/release-runbook.md`).

### Endlosschleifen-Vermeidung

Release- und SNAPSHOT-Bump-Commit enthalten `[skip ci]`. Dadurch laufen weder CI noch CodeQL, und es entsteht kein weiterer `workflow_run`.

### Nur Doku-Pushs

`codeql.yml` analysiert jeden Push auf `master` ohne `paths-ignore`, damit auch eine reine Doku-Revision alle Pflicht-Checks hat und released werden kann.

## Conventional Commits Konvention

### Prefixe und Auswirkung

| Prefix | SemVer-Bump | Beispiel |
|--------|-------------|---------|
| `feat:` | Minor (1.0.0 → 1.1.0) | `feat: add Google Calendar integration` |
| `fix:` | Patch (1.0.0 → 1.0.1) | `fix: correct scoring calculation for DNF` |
| `docs:` | Patch | `docs: update API documentation` |
| `chore:` | Patch | `chore: update dependencies` |
| `refactor:` | Patch | `refactor: extract scoring logic to service` |
| `test:` | Patch | `test: add E2E tests for playoff bracket` |
| `style:` | Patch | `style: fix CSS alignment in standings table` |
| `perf:` | Patch | `perf: optimize image loading for team cards` |
| `ci:` | Kein Release | `ci: update GitHub Actions workflow` |
| `BREAKING CHANGE:` | Major (1.0.0 → 2.0.0) | Footer: `BREAKING CHANGE: remove legacy API` |

### Format

```
<type>(<optional scope>): <description>

[optional body]

[optional footer(s)]
```

Beispiele:
```
feat: add race results graphic generation

fix(scoring): handle DNF positions correctly in multi-leg matches

feat!: redesign team management API
BREAKING CHANGE: TeamDto fields renamed for consistency
```

### Kein Release bei

- Commits die nur `ci:` oder `build:` Prefixe haben
- SNAPSHOT-Bump-Commits (`chore: bump version to X.Y.Z-SNAPSHOT [skip ci]`); sie zählen nicht als releasebare Änderung

## Dateien und Aenderungen

### Neue Dateien

#### 1. `.github/workflows/release.yml`

Automatisierter Release-Workflow, siehe "Release-Phase". Gate und Versionslogik liegen in `scripts/ci/release-gate.sh` und `scripts/ci/release-version.sh`; `ReleaseScriptsTest` prüft beide gegen ein temporäres Repository und ein gefälschtes `gh`.

#### 2. `versions-maven-plugin` in `pom.xml`

Plugin-Ergaenzung im `<build><plugins>` Bereich:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>versions-maven-plugin</artifactId>
</plugin>
```

### Bestehende Dateien

#### 3. `pom.xml` — Version auf `1.0.0-SNAPSHOT` setzen

```xml
<version>1.0.0-SNAPSHOT</version>
```

Aktuell: `0.0.1-SNAPSHOT` → Aendern zu `1.0.0-SNAPSHOT`

#### 4. `.github/workflows/ci.yml` — Release-Workflow nicht blockieren

Der Release-Workflow wartet per `workflow_run` auf CI und CodeQL und released nur eine Revision, deren Pflicht-Checks grün sind.

#### 5. `Dockerfile` — JAR-Name anpassen

Das Dockerfile verwendet `ctc-manager-*.jar` als Glob-Pattern, das funktioniert mit jeder Version.

### Optionale Dateien (spaeter)

- `CHANGELOG.md` — Kann spaeter ergaenzt werden, GitHub Release Notes decken das vorerst ab
- Commitlint-Config — Kann spaeter ein Pre-Commit-Hook oder GitHub Action werden um Conventional Commits zu erzwingen

## Erstmaliges Release: 1.0.0

Beim ersten Merge nach `master` mit der neuen Pipeline:

1. Kein vorheriger Tag vorhanden → Fallback auf `v0.0.0`
2. Erster `feat:` Commit → Minor-Bump → `0.1.0`

**Problem:** Das ergibt nicht `1.0.0`.

**Loesung:** Vor dem ersten automatischen Release manuell den Tag `v0.255.255` setzen — unpraktisch. Stattdessen:

- `pom.xml` auf `1.0.0-SNAPSHOT` setzen
- Ersten Release-Tag `v1.0.0` manuell erstellen (einmalig)
- Danach greift die Automatik ab `v1.0.0` weiter

**Konkreter Ablauf fuer 1.0.0:**
1. PR mit allen Aenderungen (Workflow, pom.xml, Plugin) nach `master` mergen
2. Der Release-Workflow laeuft, findet keinen vorherigen Tag
3. Spezialfall im Workflow: Wenn kein Tag existiert UND pom.xml `1.0.0-SNAPSHOT` enthaelt → Release als `1.0.0`
4. Ab dann automatisch: naechster `feat:` Merge → `1.1.0`, naechster `fix:` → `1.1.1`

**Vereinfachung:** Der Workflow liest die SNAPSHOT-Version aus `pom.xml` wenn kein vorheriger Tag existiert. Dadurch bestimmt die SNAPSHOT-Version das initiale Release.

## Version-Bump-Logik nach Release

| Release-Version | Bump-Typ | Naechster SNAPSHOT |
|----------------|----------|-------------------|
| 1.0.0 | (initial) | 1.1.0-SNAPSHOT |
| 1.1.0 | feat → minor | 1.2.0-SNAPSHOT |
| 1.1.1 | fix → patch | 1.2.0-SNAPSHOT |
| 2.0.0 | breaking → major | 2.1.0-SNAPSHOT |

Der SNAPSHOT zeigt immer die naechste erwartete Minor-Version. Bei Patch-Releases bleibt der SNAPSHOT auf der naechsten Minor.

## CI-Workflow Interaktion

```
PR erstellt → ci.yml (Build + Tests + Coverage)
PR gemergt  → ci.yml + codeql.yml → release.yml (Gate, Release + Docker)
```

`ci.yml` und `codeql.yml` validieren, `release.yml` released erst danach.

## Commit-Message Umstellung

Ab sofort englische Commit-Messages mit Conventional Commits Prefixen:

**Alt (deutsch):**
```
Playoff: Bracket-Seeds & Round-Grafiken (#88)
```

**Neu (englisch, Conventional Commits):**
```
feat: add playoff bracket seeds and round graphics (#88)
```

Die Umstellung gilt fuer neue Commits. Bestehende History wird nicht umgeschrieben.

## Flyway Migrations-Strategie

### Regel ab Release 1.0.0

**Bestehende Migrationsdateien duerfen nie mehr geaendert werden.** Alle Schema-Aenderungen muessen als neue Migrationsdateien mit aufsteigender Versionsnummer angelegt werden.

### Aktueller Stand

- `V1__initial_schema.sql` — Konsolidiertes Gesamtschema (entstand waehrend der Entwicklung vor 1.0.0)
- Diese Datei wird mit Release 1.0.0 **eingefroren**

### Namenskonvention fuer neue Migrationen

```
V{N}__{kurzbeschreibung}.sql
```

Beispiele:
```
V2__add_calendar_event_table.sql
V3__add_driver_nickname_column.sql
V4__create_playoff_bracket_index.sql
```

- `N` ist fortlaufend (V2, V3, V4, ...)
- Doppelter Underscore `__` zwischen Version und Beschreibung (Flyway-Konvention)
- Beschreibung in snake_case, englisch
- Jede Migration muss idempotent-sicher sein (kein `IF NOT EXISTS` noetig — Flyway trackt angewandte Migrationen)

### Regeln

1. **Niemals** bestehende `V*__.sql` Dateien aendern — Flyway prueft Checksummen und bricht bei Abweichung ab
2. **Jede Schema-Aenderung** (neue Tabelle, neue Spalte, Index, Constraint) bekommt eine eigene Migrationsdatei
3. **Daten-Migrationen** (z.B. Default-Werte setzen, Daten transformieren) als separate Migration nach der Schema-Migration
4. **Rueckwaerts-kompatibel** entwickeln: Spalten erst als nullable hinzufuegen, in einem spaeteren Release als NOT NULL setzen (falls noetig)
5. **H2 + MariaDB Kompatibilitaet** beachten — SQL-Syntax muss fuer beide Datenbanken funktionieren (wie in V1)

### Entwicklungsablauf

```
Feature-Branch: V2__add_foo_table.sql erstellen
    → PR Review: Migration pruefen
    → Merge nach master: Flyway wendet V2 beim naechsten Start an
    → Release: Migration ist Teil des Release-Artefakts
```

Bei Konflikten (zwei PRs erstellen beide V2): Der zweite PR muss seine Migration auf V3 umnummerieren vor dem Merge.

## CLAUDE.md Anpassungen

Folgende Abschnitte in `CLAUDE.md` aktualisieren:

- **Git-Workflow:** Commit-Messages auf Englisch mit Conventional Commits Prefixen
- **Flyway-Regel:** Bestehende Migrationen nie aendern, nur neue Versionen anlegen
- **Befehle:** Release-relevante Kommandos ergaenzen (falls noetig)

## Verifikation

1. **Unit:** `./mvnw verify` muss mit neuer pom.xml Version (`1.0.0-SNAPSHOT`) grueen sein
2. **Workflow-Syntax:** `act` oder GitHub Actions Linter fuer `release.yml`
3. **Erster Release:** PR mergen, pruefen ob:
   - Tag `v1.0.0` erstellt wird
   - GitHub Release mit Notes existiert
   - Docker-Image auf `ghcr.io` gepusht wurde
   - pom.xml auf `1.1.0-SNAPSHOT` gebumpt wurde
   - Kein Endlos-Loop durch SNAPSHOT-Commit
4. **Zweiter Release:** Feature-PR mergen, pruefen ob `v1.1.0` korrekt erstellt wird
