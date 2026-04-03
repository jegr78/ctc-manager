# Release Management Design

## Context

Das CTC-Manager-Projekt naehert sich dem ersten Release (1.0.0). Bisher gibt es keine Versionierung, keine Tags, kein CHANGELOG und keinen Release-Prozess. Die aktuelle Version in `pom.xml` ist `0.0.1-SNAPSHOT`.

**Ziel:** Automatisiertes Release-Management mit Semantic Versioning, Conventional Commits und Docker-Image-Publishing bei jedem Merge nach `master`.

## Entscheidungen

| Aspekt | Entscheidung |
|--------|-------------|
| Versionierung | Semantic Versioning (SemVer) |
| Commit-Konvention | Conventional Commits (englisch) |
| Release-Trigger | Automatisch bei Merge nach `master` |
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
master Push → Analyse Commits → Version bestimmen → Build → Tag → Release → Docker → SNAPSHOT Bump
```

1. GitHub Actions `release.yml` triggert auf Push nach `master`
2. Filtert Bot-Commits (SNAPSHOT-Bump) um Endlosschleifen zu vermeiden
3. Liest Commits seit letztem Tag → bestimmt SemVer-Bump via Conventional Commits
4. Entfernt `-SNAPSHOT` aus `pom.xml` → Release-Version (z.B. `1.1.0`)
5. Baut Projekt mit `./mvnw verify`
6. Erstellt Git-Tag `vX.Y.Z` und GitHub Release mit auto-generierten Notes
7. Baut Docker-Image und pusht zu `ghcr.io/jegr78/ctc-manager:X.Y.Z` + `:latest`
8. Bumpt `pom.xml` auf naechsten SNAPSHOT (z.B. `1.2.0-SNAPSHOT`)
9. Committet SNAPSHOT-Bump mit `chore: bump version to X.Y.Z-SNAPSHOT [skip ci]`

### Endlosschleifen-Vermeidung

Der SNAPSHOT-Bump-Commit enthaelt `[skip ci]` im Commit-Message. Zusaetzlich prueft der Workflow ob der Committer der GitHub Actions Bot ist und ueberspringt in dem Fall.

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
- Commits mit `[skip ci]` (SNAPSHOT-Bumps)

## Dateien und Aenderungen

### Neue Dateien

#### 1. `.github/workflows/release.yml`

Automatisierter Release-Workflow:

```yaml
name: Release

on:
  push:
    branches: [master]

permissions:
  contents: write
  packages: write

jobs:
  release:
    # Skip bot commits (SNAPSHOT bumps)
    if: "!contains(github.event.head_commit.message, '[skip ci]')"
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v6
        with:
          fetch-depth: 0  # Full history for tag analysis
          token: ${{ secrets.GITHUB_TOKEN }}

      - name: Setup JDK 25
        uses: actions/setup-java@v5
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: 'maven'

      - name: Determine version bump
        id: version
        run: |
          # Get last tag (or use v0.0.0 if none exists)
          LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "v0.0.0")
          echo "last_tag=$LAST_TAG" >> $GITHUB_OUTPUT

          # Get commits since last tag
          COMMITS=$(git log ${LAST_TAG}..HEAD --pretty=format:"%s" 2>/dev/null || git log --pretty=format:"%s")

          # Determine bump type
          BUMP="patch"
          if echo "$COMMITS" | grep -qE "^feat(\(.+\))?!:|BREAKING CHANGE"; then
            BUMP="major"
          elif echo "$COMMITS" | grep -qE "^feat(\(.+\))?:"; then
            BUMP="minor"
          fi

          # Parse current version from last tag
          VERSION=${LAST_TAG#v}
          IFS='.' read -r MAJOR MINOR PATCH <<< "$VERSION"

          # Apply bump
          case $BUMP in
            major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
            minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
            patch) PATCH=$((PATCH + 1)) ;;
          esac

          NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}"
          NEXT_SNAPSHOT="${MAJOR}.$((MINOR + 1)).0-SNAPSHOT"

          echo "new_version=$NEW_VERSION" >> $GITHUB_OUTPUT
          echo "next_snapshot=$NEXT_SNAPSHOT" >> $GITHUB_OUTPUT
          echo "bump=$BUMP" >> $GITHUB_OUTPUT
          echo "Release: $NEW_VERSION (${BUMP} bump from ${LAST_TAG})"

      - name: Skip if no releasable commits
        id: check
        run: |
          COMMITS=$(git log ${{ steps.version.outputs.last_tag }}..HEAD --pretty=format:"%s" 2>/dev/null || git log --pretty=format:"%s")
          if echo "$COMMITS" | grep -qE "^(feat|fix|docs|refactor|perf|test|style|chore)(\(.+\))?:"; then
            echo "should_release=true" >> $GITHUB_OUTPUT
          else
            echo "should_release=false" >> $GITHUB_OUTPUT
            echo "No releasable commits found, skipping release"
          fi

      - name: Set release version in pom.xml
        if: steps.check.outputs.should_release == 'true'
        run: |
          ./mvnw versions:set -DnewVersion=${{ steps.version.outputs.new_version }} -DgenerateBackupPoms=false

      - name: Build and verify
        if: steps.check.outputs.should_release == 'true'
        run: ./mvnw verify -Dspring.profiles.active=dev

      - name: Configure git
        if: steps.check.outputs.should_release == 'true'
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"

      - name: Create tag
        if: steps.check.outputs.should_release == 'true'
        run: |
          git add pom.xml
          git commit -m "release: v${{ steps.version.outputs.new_version }}"
          git tag -a "v${{ steps.version.outputs.new_version }}" -m "Release v${{ steps.version.outputs.new_version }}"

      - name: Create GitHub Release
        if: steps.check.outputs.should_release == 'true'
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          git push origin "v${{ steps.version.outputs.new_version }}"
          gh release create "v${{ steps.version.outputs.new_version }}" \
            --title "v${{ steps.version.outputs.new_version }}" \
            --generate-notes \
            target/ctc-manager-${{ steps.version.outputs.new_version }}.jar

      - name: Login to GHCR
        if: steps.check.outputs.should_release == 'true'
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push Docker image
        if: steps.check.outputs.should_release == 'true'
        run: |
          VERSION=${{ steps.version.outputs.new_version }}
          IMAGE=ghcr.io/${{ github.repository_owner }}/ctc-manager

          docker build -t ${IMAGE}:${VERSION} -t ${IMAGE}:latest .
          docker push ${IMAGE}:${VERSION}
          docker push ${IMAGE}:latest

      - name: Bump to next SNAPSHOT
        if: steps.check.outputs.should_release == 'true'
        run: |
          ./mvnw versions:set -DnewVersion=${{ steps.version.outputs.next_snapshot }} -DgenerateBackupPoms=false
          git add pom.xml
          git commit -m "chore: bump version to ${{ steps.version.outputs.next_snapshot }} [skip ci]"
          git push origin master
```

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

Keine Aenderung noetig. Der CI-Workflow laeuft auf PRs und Push nach master. Der Release-Workflow ist ein separater Workflow. Beide laufen parallel — CI validiert, Release publiziert.

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
PR gemergt  → ci.yml (Build + Tests) + release.yml (Release + Docker)
```

Beide Workflows laufen unabhaengig. `ci.yml` validiert Code-Qualitaet, `release.yml` kuemmert sich um Versionierung und Publishing.

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
