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
- **Error Pages** — Consistent CTC styling, status-specific guidance and a native return to seasons. A history button appears only after navigation from another local admin page; development exception details stay collapsed.
- **Standings & Rankings** — Season and all-time standings for teams and drivers. The admin has native season, phase and group navigation, labeled searches and keyboard sorting that preserves ranking positions across filters and pages. Driver rankings cover the entire selected phase.
- **Playoffs** — Single/double elimination brackets (4/8 teams) with best-of-legs support
- **Swiss Pairing** — Automatic round generation with Buchholz tiebreaker
- **Round and Matchup Workspaces** — Swiss round navigation, labeled quick scores and match links; playoff legs show result counts once, with clear preparation and decision actions. New legs are configured in the race editor.
- **Driver Sheet Import** — Preview season choices and explain conflict and fuzzy-match decisions beside labeled controls. Tabs without a selected season are skipped; invalid rows stay visible for correction.
- **Driver Merge** — Review the source and retained target profiles, transferred records and removed duplicates before confirming. The preview explains permanent source deletion and PSN alias retention.
- **Power Rankings Editor** — Choose a season group, reorder teams with buttons or drag-and-drop, restore the rating order and download a PNG. The native download form also works without JavaScript.
- **Team Cards Workspace** — Select a season, review available and missing artwork, open full-size previews and download PNGs or a ZIP. Generation shows an inline status and prevents concurrent submissions; native forms and downloads work without JavaScript.
- **Graphic Template Editor** — Labeled HTML editor beside a responsive preview, inline loading and error messages, normal keyboard navigation and native save/reset forms. Preview samples support guest markers used by lineup and results graphics.
- **Data Import** — Guided source and destination selection with native required fields, inline matchday creation and cancellation of stale season requests. CSV upload and Google Sheets scorecard import with a driver matching preview, labeled assignment decisions and a persistent execution bar. Suggested matches and new-driver choices are submitted with the import
- **GT7 Sync** — Scrape cars and tracks (with images) from gran-turismo.com, preview new entries with independent catalog filters and import selected items. Selection persists across filters and pages, with a live count and a persistent import bar. Supports the current GT7 data bundles.
- **Static Site Generation** — Generate a complete static website for GitHub Pages
- **Website Navigation** — Native menus with neutral current-page and season markers, Escape and outside-click dismissal, keyboard skip links and scrollable menus on small screens. Navigation remains usable without JavaScript.
- **Admin Navigation** — Current-page labels, a keyboard skip link and a mobile menu with focus containment, a close button, Escape support and focus restoration. Closed menus stay out of the focus order; navigation remains visible without JavaScript.
- **CTC Interface** — Black, white and grey styling with yellow branding, a neutral public landing page, keyboard-accessible navigation and mobile season and alltime standings with expandable statistics, matchday cards and expandable driver rankings and race results with full tables. Team and driver profiles present season statistics before detailed history. Playoffs provide expandable rounds and race results; the archive supports season search and chronological sorting. Team and driver directories offer labeled search and season filters with live counts and a reset action. Community links use fully clickable cards with explicit new-tab labels. The desktop admin includes a season workspace with contextual navigation, a phase-first overview with collapsible setup, accessible team dialogs, searchable season and team lists, driver status filters with keyboard sorting and pagination, searchable car/track catalogs with keyboard sorting and pagination, sectioned team/driver/car/track editors with labeled controls and visible validation errors, direct links from driver assignments, searchable scoring schemes with labeled position editors and preserved validation inputs, sectioned race setup with native car/track selections and contextual matchday editors, sectioned season/phase/group configuration with preserved validation context and labeled catalog assignments, sectioned playoff creation and responsive matchup seeding with labeled team/seed fields and frozen controls, grouped match metadata and roles with explicit forfeiting-team selection and reversible bye controls, pairing deadlines with field validation and matchday generation with group roster counts and preserved failed submissions, race/match details organized around preparation and results with grouped publishing tools, labeled lineups with guest validation and keyboard focus, matchday search/status filters and a persistent save bar for race results.
- **Team Cards** — Generate 1080x1920 team card PNGs with colors, logo, rating and standings
- **Race Attachments** — Upload files or link external resources to races
- **Docker** — Local development and production deployment with MariaDB
- **Backup & Restore** — Export a full ZIP backup of all 27 entity tables (24 league entities, the 2 Discord-state entities `discord_global_config` and `discord_post`, and the public URL slugs in `site_slugs`); restore via a preview-and-confirm import flow that accepts schema versions 1, 2 and 3, so older backups remain restorable
- **Channel Archive Dialog** — Labeled destination categories with channel counts, disabled full categories, keyboard focus and Escape/cancel support. A native inline form remains available without JavaScript.
- **Backup Workspace** — Separate export and restore panels, labeled current and backup row counts, a neutral archive preview and a clear replacement confirmation.
- **Data Audit** — Read-only check at `/admin/data-audit` that lists inconsistent historical records (stale aggregates, duplicate pairings, phase and group mismatches, succession problems, playoff decisions, ambiguous public URLs) with evidence and a proposed correction. See the [Data Audit wiki page](../../wiki/Data-Audit).
- **Audit and Generation Workspaces** — Neutral audit summaries, category navigation and keyboard-accessible evidence tables; CTC-styled website generation with a review and publishing guide.
- **Discord Integration** — Per-match Discord channels with 11 structured posts (team cards, settings, lineups, schedule, results, match preview, matchday overview, power rankings, standings), forum-thread linking for race-results + standings, auto-edit on schedule/preview changes, pre-flight gates and stale-detection signals. See [Discord Integration wiki page](../../wiki/Discord-Integration) and [`docs/operations/discord-integration.md`](docs/operations/discord-integration.md).
- **Discord Role Selection** — A labeled native selection works without JavaScript. Optional search filters roles by name or ID, preserves the selected role and retains saved IDs missing from the cache.
- **Discord Workspaces** — Sectioned configuration with saved-setting test guidance, neutral missing-field hints and explained connection/cache actions. Recorded posts have native season, match and type filters, readable timestamps and keyboard-accessible tables.

> **Note (v1.13):** Per-match Discord channels with structured posting workflows are now available
> under `/admin/matches/{id}`. Operator setup (bot registration, OAuth permissions, token wiring,
> forum-channel + thread setup, daily operations, troubleshooting) is documented in
> [`docs/operations/discord-integration.md`](docs/operations/discord-integration.md).

## Backup & Restore

v1.10 introduces a full database backup/restore feature accessible via `/admin/backup`.

### Export

1. Navigate to `/admin/backup` in the admin sidebar.
2. Click **Export Backup** — a ZIP file (`ctc-backup-<ISO-instant>.zip`) downloads immediately.
3. Store the ZIP in a safe location. Each export captures all 27 entity tables (including Discord global config, idempotency tracking and public URL slugs).

### Import

1. Navigate to `/admin/backup` and upload a ZIP via **Import Backup**.
2. Review the preview: per-table row counts (current vs. backup) and schema-version match indicator.
3. Check the **I understand** confirmation and click **Execute Import**. The database is replaced atomically.

> **Schema-Version lock:** The import rejects a backup whose schema version is not 1, 2 or 3 before it
> writes anything.

### Recovery

If an import fails or you need to revert, see [`docs/operations/import-runbook.md`](docs/operations/import-runbook.md)
for step-by-step recovery from `data/<profile>/import-backups/<ts>/` (containers: `/app/data/import-backups/<ts>/` in the `ctc-data` volume).

> **Note (v1.11):** Recovery storage is now profile-isolated to `data/<profile>/import-backups/` (e.g., `data/dev/import-backups/` or `data/prod/import-backups/`). Pre-v1.11 artifacts under `data/.import-backups/` remain in place and are not migrated automatically.
>
> **Note (v1.12):** Google Sheets / Calendar API errors during driver-import or calendar-event creation now surface a categorized error badge (Transient / Auth / NotFound / Permission) with actionable hardcoded messages. Operator setup, error-category reference, and troubleshooting steps are documented in [`docs/operations/google-integration.md`](docs/operations/google-integration.md). Milestone PR #129.

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

Both Compose files keep all application state in two named volumes, so recreating the `app`
container loses nothing:

| Volume | Mount | Content |
|---|---|---|
| `ctc-data` | `/app/data` | `uploads/` (images, attachments, custom graphic templates), `site/` (generated website), `backup-staging/`, `import-backups/` (pre-import recovery archives) |
| `ctc-logs` | `/app/logs` | Log files |

The `docker` and `prod` profiles point `app.upload-dir`, `ctc.site.output-dir` and both
`app.backup.*` directories below `/app/data`. The uploads directory must not be a mount root:
a backup import replaces it by renaming it next to the recovery archive.

Upgrading from the earlier layout needs one copy of the uploads; the website is rebuilt by the
next generation run.

```bash
# prod: older images kept uploads in the container layer, save them before replacing the container
docker compose -f docker-compose.prod.yml cp app:/app/data/dev/uploads ./uploads-rescue
docker compose -f docker-compose.prod.yml up -d   # with the new image
docker compose -f docker-compose.prod.yml cp ./uploads-rescue/. app:/app/data/uploads/
docker compose -f docker-compose.prod.yml exec -u root app chown -R ctc:ctc /app/data/uploads

# docker: copy the former ctc-uploads volume into the new ctc-data volume
docker compose up -d
docker run --rm -v ctc-manager_ctc-uploads:/from:ro -v ctc-manager_ctc-data:/to alpine \
  sh -c 'cp -a /from/. /to/uploads/'
```

The old `ctc-uploads` and `ctc-site-output` volumes stay on disk until you remove them.

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

## Test Performance

Test wallclock metrics, per-phase optimisation history, and the per-fork backup-staging-dir + cache-key fingerprint instrumentation are documented in [`docs/test-performance.md`](docs/test-performance.md). The current authoritative CI E2E-step baseline is the **v1.12 Phase 91 PERF-06 5-run median of 17:39** (Δ−23.3 % vs v1.11 23:00 baseline; harvested via `workflow_dispatch` on milestone PR #129 head SHA `b63a2be1`; methodology in [`docs/test-performance.md § PERF-06 Re-Harvest`](docs/test-performance.md#perf-06-re-harvest-phase-91)). The local Wave-4 median (Phase 89, per-fork `app.backup.staging-dir` + Failsafe `default-it` `forkCount=2 reuseForks=true` + PERF-02 cache-key fingerprint listener) is 09:19 (−10.4 % vs Phase-86 10:24 local baseline).

Developers can enable Testcontainers MariaDB container reuse by adding `testcontainers.reuse.enable=true` to `~/.testcontainers.properties` — see the [`## PERF-04 Testcontainers Reuse`](docs/test-performance.md#perf-04-testcontainers-reuse) section in `docs/test-performance.md` for the opt-in protocol, the `docker ps` verification command, and the cleanup hint. CI behavior is unchanged; both MariaDB-backed ITs are gated by `@EnabledIfSystemProperty(named = "docker.available", matches = "true")` and CI never sets the flag.
