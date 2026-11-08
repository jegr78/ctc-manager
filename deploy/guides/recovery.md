# Recovery units and restore

A recovery unit contains the application database, all of `/app/data`, the installation configuration, environment, Google credentials and both database passwords. Treat the whole directory as secret material. The application ZIP import/export stays separate.

## units

Nightly `backup` writes `recovery-*` units with an isolated restore proof. `deploy` writes `recovery-update-*` units right before it switches releases; those carry no proof. Both live under `/mnt/backup/ctc-manager/units` and use the same format.

Capture refuses a missing or different backup device and needs 3 GiB free. It stops the app, waits for a clean exit, checks the selected database, storage and image identities and rejects other containers on the database network or volumes. MariaDB keeps running. The dump includes application tables, routines, events and triggers, without system users. Every artifact is private and checksummed. A unit appears only after a validated, flushed staging directory is renamed; `.partial-*` directories never count.

## restore

```sh
~/.local/bin/ctc-manager restore --unit /mnt/backup/ctc-manager/units/<unit>
```

The command validates the unit against `installationId` and needs the release that wrote the unit under `/srv/ctc-manager/releases`. It stops phpMyAdmin and the app, removes the database and data volumes, copies the unit's secrets to the configured paths, points `current` at that release with integrations paused, imports the dump, restores the files and starts the stack. It waits for health and starts phpMyAdmin again if it ran.

Restore works in place. The current database is gone afterwards, so take a fresh `backup` first if the current state might still matter. A unit written by a release without `lib/rollout.py`, such as 1.51.0, needs `--legacy-release` in addition.

Runtime restoration accepts the supported Basic, Discord and Google environment keys and fixes database, profile, JVM and storage settings. It does not execute backed-up JVM agents or external configuration files.

## resume integrations

Integrations stay paused after a restore or a deploy rollback. Review pending external work, old Discord references and any other running instance first. Recovery never replays deliveries.

```sh
~/.local/bin/ctc-manager resume-integrations --confirm <installation-id>
```

The installation ID is in `/srv/ctc-manager/config/installation.json`.

## failures

A terminated command leaves its lock for inspection. Check the lock owner, child processes and container state before removing it. Do not remove a lock based only on a PID that could have been reused. A failed capture leaves no completed unit and restores the prior app state. Diagnostics stay under `state/`.

Checksums prove integrity and release provenance, not who sent a unit. Accept units only from owner-controlled backup storage.
