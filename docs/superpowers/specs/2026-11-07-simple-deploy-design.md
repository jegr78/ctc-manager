# Simple release and deploy for the CTC Manager

The CTC Manager is a single-admin app on one host. Today a merged change passes the full test suite three times (pull request, master push, release job), every release commits twice to `master`, and an update on the box takes `update-check` plus `update` with a fully proven backup and no automatic way back. The CLR driver index already runs the leaner shape (`gitlab.com/community-league-racing/driver-index`, `docs/superpowers/specs/2026-11-07-simple-deploy-design.md` there). This design ports it and lists every intended difference.

A release still happens on its own after a green `master`. A deploy to the box never does: it stays a manual operator action.

Delivery is one pull request.

## Release

`release.yml` keeps its trigger after `CI` and `CodeQL SAST` and keeps `release-gate.sh` unchanged. The release job reuses the image and JAR that `ci.yml` built and tested for the same commit instead of running `./mvnw verify` again, so each `master` commit is tested once.

The version follows the conventional commits since the last `vX.Y.Z` tag, the same rule as CLR:

| Commits since last tag | Result |
|---|---|
| `!` or `BREAKING CHANGE` | major |
| `feat` | minor |
| `fix`, `perf`, `chore(deps)` | patch |
| only `docs`, `test`, `ci`, `chore`, `refactor`, `style` | no release |

Nothing is committed to `master`: `pom.xml` stays on the snapshot version, the build receives the release version, and the job creates the tag and the GitHub release through the API. `RELEASE_TOKEN` is no longer needed for pushes to `master`. An existing release with identical assets counts as done; a release whose commit is no longer the `master` tip is skipped.

## Bundle

The bundle keeps its exact inventory check in `deploy/lib/release.py`, with trees verified against their own manifest so an older verifier accepts newer bundles and the new code accepts the installed tree. It carries the launcher, the systemd units and a home launcher `deploy/ctc-manager-launcher`; the deploy installs them.

## Deploy

One command on the box:

```sh
~/.local/bin/ctc-manager deploy [VERSION|latest]
```

Steps, under the existing installation lock:

1. Download bundle and checksum from the GitHub release, verify both, extract to `/srv/ctc-manager/releases/VERSION` through a staging directory. Refuse a version that is not newer than the running one, a source older than the minimum in `upgrade.json`, and an app image with a different UID. Check the backup device before anything stops.
2. Pull the pinned image digests. A changed MariaDB major is refused unless `--allow-database-upgrade` is given.
3. Record the rows of `flyway_schema_history`.
4. Stop the app and write an update unit without restore proof.
5. Pause outbound integrations, point `installation.json` and `current` at the new release and start it. Flyway migrates on startup.
6. Success means the compose healthcheck reports healthy within 180 seconds. Then install the bundle's units and launcher, reload the user systemd manager, restore the previous `outboundPaused` value and remove legacy state.

On failure before step 4 nothing changes. From step 4 on the deploy rolls back by itself: if `flyway_schema_history` changed or the database image changed, restore the update unit in place (then into a fresh database volume), otherwise only switch back; start the old release and wait for health. Outbound integrations stay paused after a rollback until `resume-integrations`. A rollback that also fails leaves the app stopped and says so. Interrupts and `SIGTERM` reach the rollback, `SIGHUP` is ignored, and Docker output goes to `state/deploy.log`.

`deploy --drill-rollback` runs every step on the running version and then treats health as failed.

## Intended differences to CLR

- Outbound integrations (Discord, Google, GT7, YouTube, remote downloads) are paused while the new release starts, because CTC has real external effects at startup. CLR's deploy never touches `outboundPaused`.
- Flyway migrates at app start. The deploy detects a migration by comparing `flyway_schema_history` before and after.
- No phpMyAdmin, so there is no stop and start around deploy or backup.
- Units carry no cached images; the images stay available by digest.
- Retention keeps today's rule (the newest unit, the newest unit on two distinct Berlin days, the first unit of the newest month) plus the three newest update units.
- The app image UID check and the `upgrade.json` minimum source version stay.
- The host is reached as `cachyos-ctc`; GitHub Actions and GitHub releases replace GitLab CI.

## Backups and restore

Nightly units keep their full isolated restore proof, including credentials and a graphic render. Update units use the same format and location without the proof. `restore --unit UNIT` works in place: stop the app, drop the database and data volumes, restore dump, data and secrets from the unit, start the release that wrote the unit, wait for health. The `--confirm` string goes away. Outbound integrations stay paused after a restore until `resume-integrations`. The uploads directory keeps sharing a volume with the recovery archives.

## What goes

- storage generations: `storageGeneration` stays as the permanent volume prefix, no new generation is ever created; `state/before-<gen>.json`
- `update-check`, `update`, `restore-check`, `backup-check`, `prune` and the `state/update.json` journal, `state/update-before-*.json` and `state/protected-recovery.json`
- the operation journal; one lock remains
- Mac migration: `migrate-from-mac.sh`, `deploy/lib/migration.py`, `state/production-authority.json` and the cutover commands
- the coupling of `publication.py` to the generation

Remaining commands: `deploy`, `backup`, `restore --unit UNIT`, `resume-integrations`, `status`, `doctor`, `up`, `stop`, `publish-site`. The nightly timer runs `backup`, which writes, proves and prunes.

## Conversion of the running box

The first switch runs the new release's launcher once by hand, after downloading and extracting with the installed tools. After a successful first deploy it removes the legacy state files and the containers and volumes of other generations. Every later update is the single command.

## Operator access

A Claude Code permission rule allows `ssh cachyos-ctc ctc-manager deploy *`, `status` and `doctor`. It only removes the prompt; Claude still deploys only when the operator asks.

## Tests and trial

- Deploy tests with fakes cover: success, refusal of an older or equal version, refusal of a database major change, failure before the backup, failure after a migration with restore, failure without migration, failed rollback, interrupt, drill, latest resolution, outbound restored after success and kept paused after a rollback.
- CI tests cover the version rule and that `release.yml` no longer pushes to `master`.
- On the box: the first real deploy right after a nightly backup, then a rollback drill.

## Documentation

An ADR records the end of storage generations, the update journal and the Mac migration. `deploy/guides/updates.md`, `recovery.md`, `backups.md`, `operations.md` and `migration.md` are rewritten or removed, `CLAUDE.md` sections "recovery integration contract", "Permanent production deployment", "Host provisioning", "Native Mac migration" and the CI/CD release bullet are updated, and README and wiki follow.
