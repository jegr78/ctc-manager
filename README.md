# CTC Manager

**CTC Manager** is a Gran Turismo Team vs. Team League Manager — an admin application for managing racing leagues across multiple seasons.

The active season is unique. Deactivate the current season before activating another. Upgrading from a database with several active seasons keeps the newest by year and season number, with UUID as the final tie-breaker. Restore preview rejects archives with several active seasons before replacing data. Swiss leagues and individual Swiss groups use Buchholz; admin and public alltime driver rankings both include playoff results.

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
- **Team penalty points** — Record both complete penalty totals per race leg, offset their difference and floor official team scores at zero. Immutable corrections retain reasons and managers internally; driver points stay unchanged. See [Team penalty points](../../wiki/Team-Penalty-Points).
- **Driver disqualifications** — Record or clear a DSQ for one saved driver result. Race points shift to the following drivers, qualifying and fastest lap bonuses remain, and permanent internal history preserves the original position. Enter the separate 20 team penalty points manually. See [Driver disqualifications](../../wiki/Driver-Disqualifications).
- **Race Results & Scoring** — Position points (20-2), qualifying points (3-2-1), fastest lap bonus (2)
- **Error Pages** — Consistent CTC styling, status-specific guidance and a native return to seasons. A history button appears only after navigation from another local admin page; development exception details stay collapsed.
- **Standings & Rankings** — Season and all-time standings for teams and drivers. The admin has native season, phase and group navigation, labeled searches and keyboard sorting that preserves ranking positions across filters and pages. Driver rankings cover the entire selected phase.
- **Playoffs** — Single/double elimination brackets (4/8 teams) with best-of-legs support
- **Swiss Pairing** — Automatic round generation with Buchholz tiebreaker
- **Scoring integrity** — Match metadata edits preserve quick scores. Result and lineup changes share season locks; a concurrent MariaDB snapshot conflict asks the operator to reload. Race-scoring points and a phase's scoring assignment are protected once results exist. Create a new scheme for different points; historical mismatches appear in the read-only data audit.
- **Round and Matchup Workspaces** — Swiss round navigation, labeled quick scores and match links; playoff legs show result counts once, with clear preparation and decision actions. New legs are configured in the race editor.
- **Driver Sheet Import** — Preview season choices and explain conflict and fuzzy-match decisions beside labeled controls. Tabs without a selected season are skipped; invalid rows stay visible for correction.
- **Driver Merge** — Review the source and retained target profiles, transferred records and removed duplicates before confirming. The preview explains permanent source deletion and PSN alias retention.
- **Power Rankings Editor** — Choose a season group, reorder teams with buttons or drag-and-drop, restore the rating order and download a PNG. The native download form also works without JavaScript.
- **Team Cards Workspace** — Select a season, review available and missing artwork, open full-size previews and download PNGs or a ZIP. Generation shows an inline status and prevents concurrent submissions; native forms and downloads work without JavaScript.
- **Graphic Template Editor** — Labeled HTML editor beside a responsive preview, inline loading and error messages, normal keyboard navigation and native save/reset forms. Preview samples support guest markers used by lineup and results graphics.
- **Data Import** — Guided source and destination selection with native required fields, inline matchday creation and cancellation of stale season requests. CSV upload and Google Sheets scorecard import with a driver matching preview, labeled assignment decisions and a persistent execution bar. Suggested matches and new-driver choices are submitted with the import
- **GT7 Sync** — Scrape cars and tracks (with images) from gran-turismo.com, preview new entries with independent catalog filters and import selected items. Selection persists across filters and pages, with a live count and a persistent import bar. Missing images on existing tracks can be selected separately and loaded without recreating tracks or replacing supplied images. Import results report image updates and individual failures. Official category and drivetrain metadata can be updated separately for existing cars. Candidate model years use GTPlus IDs, suffix corroboration and reviewed corrections; manual values and explicitly unknown years remain protected. Previews show source evidence and require a fresh review when source or local data changes.
- **Static Site Generation** — Generate a complete static website for GitHub Pages
- **Website Navigation** — Native menus with neutral current-page and season markers, Escape and outside-click dismissal, keyboard skip links and scrollable menus on small screens. Navigation remains usable without JavaScript.
- **Admin Navigation** — Current-page labels, a keyboard skip link and a mobile menu with focus containment, a close button, Escape support and focus restoration. Closed menus stay out of the focus order; navigation remains visible without JavaScript.
- **CTC Interface** — Black, white and grey styling with yellow branding, a neutral public landing page, keyboard-accessible navigation and mobile season and alltime standings with expandable statistics, matchday cards and expandable driver rankings and race results with full tables. Team and driver profiles present season statistics before detailed history. Playoffs provide expandable rounds and race results; the archive supports season search and chronological sorting. Team and driver directories offer labeled search and season filters with live counts and a reset action. Community links use fully clickable cards with explicit new-tab labels. The desktop admin includes a season workspace with contextual navigation, a phase-first overview with collapsible setup, accessible team dialogs, searchable season and team lists, driver status filters with keyboard sorting and pagination, searchable car/track catalogs with keyboard sorting and pagination, sectioned team/driver/car/track editors with labeled controls and visible validation errors, direct links from driver assignments, searchable scoring schemes with labeled position editors and preserved validation inputs, sectioned race setup with native car/track selections and contextual matchday editors, sectioned season/phase/group configuration with preserved validation context and labeled catalog assignments, sectioned playoff creation and responsive matchup seeding with labeled team/seed fields and frozen controls, grouped match metadata and roles with explicit forfeiting-team selection and reversible bye controls, pairing deadlines with field validation and matchday generation with group roster counts and preserved failed submissions, race/match details organized around preparation and results with grouped publishing tools, labeled lineups with guest validation and keyboard focus, matchday search/status filters and a persistent save bar for race results.
- **Team Cards** — Generate 1080x1920 team card PNGs with colors, logo, rating and standings
- **Race Attachments** — Upload files or link external resources to races
- **Docker** — Local development and production deployment with MariaDB
- **Season car pools** — Keep fixed car choices or build named category entries from local metadata. Combine alternative category/drivetrain/year clauses, preview eligible cars and overlaps, then freeze exact membership. Catalog changes preserve frozen choices; used pools cannot be reopened. Race setup selects an entry and one concrete frozen member. Saving consumes the whole entry for the effective home team that season. Native forms retain invalid inputs, concurrent selections serialize through the season, and unambiguous CSV/Sheets overwrites preserve configured race identities. Admin race summaries, public matchdays and settings graphics show the concrete car with its saved pool-entry label. Custom settings templates keep `carName` and can use optional `poolEntryLabel`.
- **Backup & Restore** — Export a full ZIP backup of all 48 entity tables, including exact seasonal pool snapshots, Discord state and public URL slugs; restore via a preview-and-confirm import flow that accepts schema versions 1 through 16, so older backups remain restorable
- **Channel Archive Dialog** — Labeled destination categories with channel counts, disabled full categories, keyboard focus and Escape/cancel support. A native inline form remains available without JavaScript.
- **Backup Workspace** — Separate export and restore panels, labeled current and backup row counts, a neutral archive preview and a clear replacement confirmation.
- **Data Audit** — Read-only check at `/admin/data-audit` for historical aggregates, pairings, phases, succession, playoffs, public URLs, race settings and seasonal pool references. Separate eligibility information explains unknown car metadata and exact-year exclusions. Unknown years remain valid; opening the audit changes no record. See the [Data Audit wiki page](../../wiki/Data-Audit).
- **Audit and Generation Workspaces** — Neutral audit summaries, category navigation and keyboard-accessible evidence tables; CTC-styled website generation with a review and publishing guide.
- **Race settings** — Time progression supports an explicit Equal Conditions mode, distinct from numeric 0x. The mode survives editing and backup/restore and appears in settings and lobby graphics. Custom settings templates can use `timeProgressionDisplay`.
- **Incident reporting.** Management confirms a regular or playoff reporting deadline. The private `/incidents` workflow selects the concrete race and saves reports after review and confirmation. Reporters can retrieve their own cases, edit prefilled saved values or withdraw before cutoff; original attribution and immutable history remain stored. Withdrawn cases are labeled in private history and excluded from post-deadline disclosure. First submissions queue a detail-free receipt. After cutoff, active frozen reports receive linked match and private stewards threads, with the reporting party and complete incident facts. The stewards destination and allowed staff roles are configured explicitly. For actual post-cutoff disclosure, the bot grants missing match-channel visibility, history and thread participation to those staff roles before posting public discussion. Other channel permissions stay unchanged. Successful delivery steps remain stored through partial failures; uncertain results and restored work require verified management recovery. Management opens permanent cases with report and ruling history, saves local drafts and explicitly publishes a reviewed version into the known match thread. Corrections require a reason and preserve the known ruling message; results and scores remain unchanged. New disclosure envelopes retain frozen pairing and concrete race labels. The read-only data audit also locates overdue open windows, ambiguous shared roles and failed, uncertain or partial delivery without revealing report content. Command registration and activation remain explicit operator actions; follow the [incident rollout checklist](docs/operations/discord-integration.md#incident-rollout-checklist). See [Incident Reporting](../../wiki/Incident-Reporting).
- **Confidential team picks.** Private Discord dialogs, immutable history and management review keep approval separate from explicit publication. The Gateway defaults off and seasons opt in individually; use the [operator rollout checklist](docs/operations/discord-integration.md#operator-rollout-checklist). Current backups preserve bindings, private history and confirmed field masks, with the documented defaults for supported earlier schemas.
- **Discord Integration** — Per-match Discord channels with 11 structured posts (team cards, settings, lineups, schedule, results, match preview, matchday overview, power rankings, standings), forum-thread linking for race-results + standings, auto-edit on schedule/preview changes, pre-flight gates and stale-detection signals. See [Discord Integration wiki page](../../wiki/Discord-Integration) and [`docs/operations/discord-integration.md`](docs/operations/discord-integration.md).
- **Private team channels** — Configure exact season/team/channel bindings and inspect outgoing Gateway health under Discord Config → Private team picks. Current private-channel access controls team authority. Command registration is an explicit management action, and the consumer defaults off until the operator enables it.
- **Private picks dialogs** — The `/picks` workflow offers home-race selection, searchable track catalogs and category-first vehicle selection. Each catalog page has at most 25 choices. Explicit One-Makes resolve their fixed car; categories show their eligible members. Replace picks prefills the latest saved values. Race setup and dependent mandatory-tyre choices use separate steps. Review the full summary before confirming. Authorized channel members can retrieve the latest saved revision; unfinished drafts expire after 30 minutes and never replace it. Collection uses the explicit Gateway and season opt-ins.
- **GT7 lobby category.** Internal race details, pick review and lobby graphics show the selected car’s structured Gr.1, Gr.2, Gr.3, Gr.4 or Gr.B classification. Other and unknown classes show a dash, regardless of custom season pool names. Regenerate graphics explicitly after changes.
- **Pick deadline navigation.** Configure the matchday deadline directly from its workspace or pick-review status, independently of Discord announcements. The filtered review overview links back to its selected matchday.
- **Picks management review** — Review exact revisions and their history from race or matchday workspaces. Open missing submissions or return corrections without a new deadline, close team editing, and approve complete picks atomically. Differing internal settings require explicit replacement consent. Later corrections retain the approved revision and team lock; publication remains separate.
- **Pick publication** — Existing settings, lobby and explicit preview controls capture exact approved content. Public pages show only confirmed disclosed fields; lobby-only output keeps the car withheld. Later edits remain private. Season opt-in reviews existing settings explicitly, and unconfirmed Discord deliveries have a native verification/retirement screen.
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
3. Store the ZIP in a safe location. Each export captures all 48 entity tables (including Discord global config, idempotency tracking and public URL slugs).

### Import

1. Navigate to `/admin/backup` and upload a ZIP via **Import Backup**.
2. Review the preview: per-table row counts (current vs. backup) and schema-version match indicator.
3. Check the **I understand** confirmation and click **Execute Import**. The database is replaced atomically.

> **Schema-Version lock:** The import rejects a backup whose schema version is outside versions 1 through 16 before it
> writes anything. Schema 4 also validates pool references and limits preflight to 100,000 retained reference rows.

### Recovery

If an import fails or you need to revert, see [`docs/operations/import-runbook.md`](docs/operations/import-runbook.md)
for step-by-step recovery from `data/<profile>/import-backups/<ts>/` (containers: `/app/data/import-backups/<ts>/` in the `ctc-data` volume).

> **Note (v1.11):** Recovery storage is now profile-isolated to `data/<profile>/import-backups/` (e.g., `data/dev/import-backups/` or `data/prod/import-backups/`). Pre-v1.11 artifacts under `data/.import-backups/` remain in place and are not migrated automatically.
>
> **Note (v1.12):** Google Sheets / Calendar API errors during driver-import or calendar-event creation now surface a categorized error badge (Transient / Auth / NotFound / Permission) with actionable hardcoded messages. Operator setup, error-category reference, and troubleshooting steps are documented in [`docs/operations/google-integration.md`](docs/operations/google-integration.md). Milestone PR #129.

Regular-season walkover backups retain the forfeiting team and saved scores. Imports also accept the historical embedded-team reference, using only its ID. Invalid walkover references are rejected before live data is deleted; the read-only Data Audit reports inconsistent pairing markers.

### Full Guide

See the [Backup & Restore wiki page](../../wiki/Backup-and-Restore) for the step-by-step export
workflow, import workflow, schema-version explanation, and recovery procedures.

## Seasonal categories and one-makes

In the season editor, select **Categories and One-Makes**. Add a category with the existing
category, drivetrain and exact-year filters, or choose **One-Make**, apply the entry type,
and select a specific catalog car. Both types share the same season pool. The editor lists
**Categories** and **One-Makes** separately.

For the 2027 example, create eleven category entries: Gr.1 Hypercars with five alternative
year clauses for 2021–2025; Gr.1 LMPs for 2016; Gr.2 Super GTs separately for 2016 and 2008;
Gr.3 FR and MR; Gr.4 AWD, FF, FR and MR; and Gr.B. Each alternative still requires its GT
category. The labels “Hypercars”, “LMPs” and “Super GTs” describe the intended pool; review
the matching models in the preview because those labels are not additional catalog filters.
Add the ten individual models from the season rules as One-Make entries using their car IDs.

Review the preview and freeze the complete pool before accepting picks. Each entry must have
at least one car and entries cannot overlap. A One-Make snapshot contains exactly its selected
car, including when that car has no category metadata. Later catalog changes do not replace the
snapshot. The home team selects one concrete member car, and every driver uses that same car.
Each category or One-Make entry is available once per home team across the entire season,
including playoffs. Pending picks retain the existing reservation rules.

Backup schema 13 preserves team penalty history, One-Make definitions and their exact frozen members. Schemas 1–11
remain importable; older entries keep their category definitions. Import rejects missing cars,
mixed definitions and inconsistent One-Make snapshots before any database wipe.

## Tyre selections

Each race uses either Racing or Sports tyres. Both offer Hard, Medium and Soft. Racing also offers
Intermediate and Wet automatically whenever Racing is selected; neither can be deselected or mandatory. Mandatory compounds may be
empty and otherwise must be dry members of the available selection. Sports requires the CTC 2027
rules profile and a frozen, explicitly defined One-Make entry with its concrete car. A category
with one member remains a category. Racing stays available for One-Makes.

Native race forms, the private pick wizard and management corrections enforce the same policy.
Select dry compounds from one family. Mandatory choices follow the selected available dry compounds.
The private wizard asks for mandatory tyres afterwards; native forms update them immediately with
JavaScript or through **Update mandatory choices** without it. Refresh after changing a pool entry. Saving does not
publish settings. Lobby graphics show the actual family; custom lobby templates can use
`v.tyreFamily`, and settings templates can use `tyreFamily`. Existing compound-text variables retain
their saved strings. Withheld public fields remain withheld.

V37 widens typed available-tyre storage to 12 characters. Backup schema 15 restores both families
and all five Racing compounds, with entry permissions checked before wiping live data. Schemas 1
through 14 retain their original typed RS/RM/RH contract; historical native text such as `All` or
Intermediate/Wet remains compatible. Equivalent native selections preserve exact saved text and
fingerprints. The read-only audit reports known invalid settings without rewriting history.

## Season lobby rules

Existing and newly created seasons use **Previous CTC rules** until management explicitly selects
**CTC 2027** under **Edit season → Lobby rules**. Select the profile before configuring races,
collecting private picks or preparing a publication. Any saved setup, private selection history
or publication attempt freezes the profile, including failed or retired attempts. Season metadata
can still be edited.

The previous profile keeps the checked grid start, Real race slipstream and Flag Rules Off.
CTC 2027 lets the home team choose the checked grid start or Rolling Start and Real or Weak
race slipstream. Flag Rules are On. Both profiles retain ten-minute qualifying without a one-shot
restriction, qualifier slipstream Off and the other existing fixed CTC settings. The lobby screenshots
identify available fields; their selected sample values do not change those rules.

Choose start type and race slipstream in the native race editor or private pick wizard. Management
reviews and corrections carry the same values. Forms opened before a permitted profile change
require a reload; **Refresh choices** explicitly loads the current race context without saving.
Saving or approving settings does not publish them. Existing explicit publication actions capture
immutable content, and public output reveals only confirmed fields.

Backup schema 15 preserves the profile and race choices in settings, private revisions, corrections
and publication captures. Schemas 1 through 13 restore the previous profile and its exact original
fingerprints, publication hashes and masks. Current archives require explicit valid choices and
are validated before any wipe. The read-only audit reports inconsistent lobby rules separately.
Custom settings/lobby templates can use `lobbyRulesProfile`, `startType`, `slipstream`, `flagRules`,
`qualifyingMinutes` and `qualifierSlipstream`. Unknown custom-template coverage discloses no new fields.

## Team penalty points

Open a race and select **Team penalty points** after entering the final driver classification.
One retrospective penalty second equals one additional team penalty point. Enter both complete
team totals and an internal correction reason. For gross points `80:70` and penalties `20:10`,
the net deduction is `10:0` and the official score is `70:70`. Offset the complete totals first,
then apply a zero floor to each official score. Driver points remain unchanged.

Each leg is adjusted separately before match/playoff aggregation. Corrections and zero resets
retain their immutable history. Later result edits or an unambiguous CSV/Sheets replacement
retain the penalties and use current driver points. A decided playoff must be explicitly reopened
before correction. Permanent history prevents ordinary deletion, participant reassignment and
manual quick-score replacement or conversion to a walkover, including after a zero reset.

Official totals feed standings, winners, alltime team statistics and results views. Regenerate
existing result/provisional graphics and the public site, then explicitly publish the correction.
Saving penalties sends no Discord message. Public output contains numerical adjustments; reasons
and manager identities remain internal. Schema 13 validates and restores all correction revisions;
legacy schemas 1 through 12 import without penalty history. The read-only audit reports invalid
history and skips unsafe aggregate comparisons.

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

Manual standings PNG previews are generated explicitly with
`./mvnw -Dtest=StandingsGraphicPreview test`. The generator in `src/test/java/org/ctc/tools/`
uses isolated H2 fixtures with outbound integrations disabled and writes three images into
`.screenshots/`. Its name keeps this visual inspection tool outside normal test discovery.

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

Private GitHub Pages publication uses the existing Generate Site workspace. Review an immutable snapshot and explicitly approve publication. The host publisher keeps its GitHub token outside the app container. See [Pages setup and recovery](deploy/guides/pages.md).

### Permanent private deployment

Use the [operator runbook](deploy/guides/operations.md) for daily operation, updates and recovery.
The [acceptance record](docs/deployment/acceptance.md) lists measured evidence and remaining checks.
Run all deployment fixtures with `bash deploy/tests/acceptance.sh`.

Use [the host setup guide](deploy/guides/host.md) to provision the dedicated CachyOS account,
rootless startup and private Tailnet HTTPS on 9443. Setup checks existing ownership, subordinate
IDs, memory support, backup-device identity and port conflicts before changing CTC resources.
It preserves courtside 443 and Mailpit 8443.

The versioned production recipe in [deploy/README.md](deploy/README.md) runs an authenticated
CTC app and MariaDB under a dedicated rootless Docker user. It pins images by digest, binds
only loopback port 9092 and validates private configuration before operating the stack.
Release assets include a deployment archive and checksum inventory. This recipe is separate
from the existing local and external-database Compose files. Host provisioning, backups and
updates use verified release bundles. The real Mac data has migrated to the Box, which is now
authoritative. Keep the native Mac app stopped.
Complete recovery units and isolated restore checks are documented in
[the recovery guide](deploy/guides/recovery.md). Restore probes retain production authentication,
force integrations off and use independent storage without a host port. Historical images need
explicit supported recovery capability before automatic full checks.
Use [the update guide](deploy/guides/updates.md) for exact release selection, pre-update recovery
proof and failure handling after possible schema migrations.

The [native Mac migration guide](deploy/guides/migration.md) preserves the original export,
rehearses target schema changes in isolated storage and records the single-writer cutover.
The private Manager remains a single-admin application.

### Docker

The root `docker-compose.yml` exposes the app on `127.0.0.1:8080` and MariaDB on `127.0.0.1:3307`. Its example credentials are for local development. Remote access requires an explicit binding override and individual credentials. Image replacements retain the old file until the database transaction commits and remove new files when it rolls back. An unknown commit outcome retains both files for review.


The Dockerfile installs Chromium with its matching system dependencies and runs a non-root
screenshot check during the image build.

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

## Dependency and security checks

Renovate proposes current stable dependency updates, including majors, without a dashboard
approval gate or PR throttling. Patch automerge retains required CI checks. CodeQL runs daily
and reports unresolved HIGH/CRITICAL findings as failures on scheduled/manual scans. See
[dependency update policy](docs/security/dependency-updates.md) for alert coverage and operations.
The MariaDB JDBC connector has an explicit version so Renovate can update it independently
of the Spring Boot BOM. A runtime dependency check rejects its known vulnerable versions.

Scheduled local backups and the two-daily/one-monthly retention are described in the
[backup runbook](deploy/guides/backups.md). One user timer runs backups; errors stay in the local journal.

## Driver disqualifications

Save the driver results, then select **Review DSQ** beside a driver in the race result editor.
Choose **Disqualified (DSQ)** or **Classified** and record an internal reason. DSQ removes only
race-position points and advances the following drivers for race scoring. Qualifying and fastest
lap bonuses remain. The editor retains the original finishing position; public results, default
graphics and driver history show DSQ or the effective position. A DSQ does not count as a driver's
best finishing position.

Each correction reaggregates match and playoff totals immediately. The separate 20 team penalty
points are entered manually as part of the team's complete total. No DSQ action changes those
saved totals automatically. Reopen a decided playoff before correcting its results, regenerate
existing graphics and the public site, then explicitly publish the correction.

Native result edits preserve result IDs, DSQ status and history. Results-only CSV and Sheets
imports reject history-bearing targets before mutation. Permanent evidence also prevents ordinary
result/guest deletion, participant reassignment, source-driver merging and quick-score replacement,
even after clearing a DSQ. Native forms reject stale revision, result-fingerprint and restore tokens.
Correct duplicate finishing positions before recording a DSQ; unrelated historical gaps remain.

V38 and backup schema 16 add the result flag and the 48th entity table. Typed backup/restore retains
exact ownership, revision IDs, reasons, actors and microsecond timestamps. Schemas 1 through 15
default missing flags to false without inventing history. Both staging and execute validate DSQ
state and history before deleting live data. The read-only Data Audit reports inconsistent evidence
with record IDs; it never repairs history or discloses internal reasons in public output.
