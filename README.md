# CTC Manager

**CTC Manager** is a Gran Turismo Team vs. Team League Manager — an admin application for managing racing leagues across multiple seasons.

## Tech Stack

- Java 25, Spring Boot 4.x, Maven
- Thymeleaf (Server-side rendering)
- MariaDB (Production) / H2 (Development)
- Flyway (Database migrations)
- Google Sheets API v4 (Scorecard import)
- Playwright (E2E testing)
- Docker (Local + Production deployment)

## Features

- **Seasons & Matchdays** — League and Swiss-system formats with configurable rounds
- **Teams & Sub-Teams** — Parent/child team hierarchy with sub-team lineups per matchday
- **Drivers** — PSN ID tracking, season-team assignments, fuzzy name matching
- **Race Results & Scoring** — Position points (20-2), qualifying points (3-2-1), fastest lap bonus (2)
- **Standings & Rankings** — Season and all-time standings for teams and drivers
- **Playoffs** — Single/double elimination brackets (4/8 teams) with best-of-legs support
- **Swiss Pairing** — Automatic round generation with Buchholz tiebreaker
- **Data Import** — CSV upload and Google Sheets scorecard import with driver matching preview
- **GT7 Sync** — Scrape cars and tracks (with images) from gran-turismo.com
- **Static Site Generation** — Generate a complete static website for GitHub Pages
- **Team Cards** — Generate 1080x1920 team card PNGs with colors, logo, rating and standings
- **Race Attachments** — Upload files or link external resources to races
- **Docker** — Local development and production deployment with MariaDB
- **Backup & Restore** — Export a full ZIP backup of all 24 entity tables; restore via a preview-and-confirm import flow with schema-version locking

## Backup & Restore

v1.10 introduces a full database backup/restore feature accessible via `/admin/backup`.

### Export

1. Navigate to `/admin/backup` in the admin sidebar.
2. Click **Export Backup** — a ZIP file (`ctc-backup-<ISO-instant>.zip`) downloads immediately.
3. Store the ZIP in a safe location. Each export captures all 24 entity tables.

### Import

1. Navigate to `/admin/backup` and upload a ZIP via **Import Backup**.
2. Review the preview: per-table row counts (current vs. backup) and schema-version match indicator.
3. Check the **I understand** confirmation and click **Execute Import**. The database is replaced atomically.

> **Schema-Version lock:** The import is rejected if the backup's schema version does not match the
> current application version. Do not import backups from a different major schema version.

### Recovery

If an import fails or you need to revert, see [`docs/operations/import-runbook.md`](docs/operations/import-runbook.md)
for step-by-step recovery from `data/<profile>/import-backups/<ts>/`.

> **Note (v1.11):** Recovery storage is now profile-isolated to `data/<profile>/import-backups/` (e.g., `data/dev/import-backups/` or `data/prod/import-backups/`). Pre-v1.11 artifacts under `data/.import-backups/` remain in place and are not migrated automatically.

### Full Guide

See the [Backup & Restore wiki page](../../wiki/Backup-and-Restore) for the step-by-step export
workflow, import workflow, schema-version explanation, and recovery procedures.

## Quick Start

```bash
# Development (H2 in-memory, port 9090)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Development with GT7 demo data (cars, tracks, images)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo

# Run tests
./mvnw verify

# Docker (App + MariaDB, port 8080)
docker compose up --build -d
```

## Playwright Setup (Team Cards + E2E Tests)

Playwright needs a Chromium browser installed locally for team card generation and E2E tests.

### Install Chromium

```bash
# All platforms (macOS, Linux, Windows) — via Maven:
./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

This downloads the Chromium binary to the platform-specific cache directory:

| Platform | Cache Location |
|----------|---------------|
| macOS | `~/Library/Caches/ms-playwright/` |
| Linux | `~/.cache/ms-playwright/` |
| Windows | `%LOCALAPPDATA%\ms-playwright\` |

### Linux: Additional OS Dependencies

On Linux (Debian/Ubuntu), Chromium requires native libraries:

```bash
# Install dependencies (via Playwright)
./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install-deps chromium"

# Or manually (Debian/Ubuntu)
sudo apt-get install -y libnss3 libatk-bridge2.0-0 libdrm2 libxkbcommon0 \
    libgbm1 libpango-1.0-0 libcairo2 libasound2t64 libxshmfence1
```

macOS and Windows include these dependencies natively — no extra setup needed.

### Docker

The Dockerfile handles Chromium installation automatically during the build.

## Development

### OpenRewrite (developer-invoked refactoring)

CTC Manager wires the [OpenRewrite](https://docs.openrewrite.org/) Maven plugin
into a dedicated `rewrite` profile, NOT the default `verify` lifecycle. Recipes
are run on demand by the developer and never as part of CI — this avoids any
risk of silent in-place source mutation during a build.

The active recipe set is defined in [`rewrite.yml`](./rewrite.yml). Today it
activates only `org.openrewrite.staticanalysis.CommonStaticAnalysis`.

Workflow:

```bash
# 1. Preview changes (writes target/rewrite/rewrite.patch if non-empty)
./mvnw -Prewrite rewrite:dryRun

# 2. Inspect the patch file — confirm no Lombok-entity false positives
cat target/rewrite/rewrite.patch

# 3. Apply the recipes to source files in place
./mvnw -Prewrite rewrite:run

# 4. Review with git diff, then commit normally
git diff
```

Without `-Prewrite` the plugin is not on the build, so a plain `./mvnw verify`
adds zero overhead.

## Documentation

See the [Wiki](../../wiki) for detailed documentation on architecture, features, setup, and configuration.
