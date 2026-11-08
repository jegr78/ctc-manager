# 0001 In-place deploy replaces storage generations

Date: 2026-11-07. Status: accepted.

## Context

The CTC Manager is a single-admin app on one host. An update took `update-check` plus `update`, an isolated restore proof of the pre-update unit and a journal with seven phases. A failure after a possible migration left the app stopped and required a restore into a new storage generation. Every recovery created another set of volumes and secret directories. The Mac migration tooling stayed in the bundle after the cutover. A release committed twice to `master` and ran the full test suite again.

The CLR driver index runs a smaller flow on the same host. This decision ports it.

## Decision

- `ctc-manager deploy [VERSION|latest]` downloads, verifies and starts a release in place. It writes an update unit without restore proof first and rolls back by itself on any failure after that point. A changed `flyway_schema_history` or database image restores the unit in place, otherwise the deploy switches back to the previous release.
- `restore --unit UNIT` restores in place: it drops the database and data volumes and fills them from the unit.
- `storageGeneration` stays as the permanent volume prefix. No new generation is created.
- The update journal, the operation journal, `update-check`, `update`, `restore-check`, `backup-check`, `prune` and the Mac migration go. One lock remains.
- The release job builds with the release version and skips the tests that CI already ran on the same commit. Nothing is committed to `master`.
- Outbound integrations stay paused while a new release starts and after every rollback or restore.
- A deploy stays a manual operator action. No merge, timer or hook deploys.

## Consequences

- A failed deploy needs no operator action unless the rollback fails too. That case leaves the app stopped and names the `restore --unit` command.
- The isolated restore proof runs only for nightly units. An update unit is a dump taken seconds before the switch and is trusted without proof.
- In-place restore discards the current database. The previous state survives only in the units on the backup device.
- The first successful deploy deletes the volumes and secret directories of other generations and the legacy state files.
