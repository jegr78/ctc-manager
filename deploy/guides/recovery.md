# complete recovery units

Run the launcher as `ctc-manager` from a verified installed release. A recovery unit contains the
application database, all of `/app/data`, the original installation configuration, environment,
Google credentials and both database passwords. Treat the entire directory as secret material.
The existing application ZIP import/export remains separate.

## capture

Create a private destination below `/mnt/backup/ctc-manager`. Host setup's private
`state/host.json` records the expected backup UUID. Capture refuses a missing or different mount
and requires at least 3 GiB free before starting. A later disk-full error fails capture too.

```sh
mkdir -m 700 /mnt/backup/ctc-manager/units
/srv/ctc-manager/current/ctc-manager --directory /srv/ctc-manager \
  backup --destination /mnt/backup/ctc-manager/units
```

The command returns JSON with the completed unit path and manifest checksum. It stops the app,
waits for a clean exit, checks the selected database/storage/image identities and rejects other
containers attached to the source database network or volumes. MariaDB remains running. The
logical dump includes application tables, routines, events and triggers, excluding system users.
All artifacts are private and checksummed. Publication renames a fully validated, flushed staging
directory on the same filesystem. `.partial-*` directories never count as completed backups.

Standalone capture restores the previous app running/stopped state after handled failures.
The internal `capture(..., hold_stopped=True)` API supports updates without an intermediate
restart. Only the update coordinator may use it and must reconcile its abort/activation state.

## isolated restore check

Use the trusted installed release whose manifest matches `release.json` inside the unit. Do not
execute any file from the unit. Make the exact recorded image digests available in this user's
rootless Docker daemon before checking.

```sh
/srv/ctc-manager/releases/<version>/ctc-manager --directory /srv/ctc-manager \
  restore-check --recovery /mnt/backup/ctc-manager/units/<unit>
```

Historical images without recovery capability version 1 fail before a probe starts. An unknown
Spring profile alone does not prove outbound isolation. Supported images must declare the
capability and confirm the active disabled policy through authenticated `/actuator/info`.

Every run owns a unique internal network and volumes. It publishes no host port and adds no
Tailscale listener. The app runs with a read-only root, no capabilities and a temporary filesystem.
That temporary filesystem permits execution because Playwright extracts its bundled driver there.
The database client uses MariaDB sandbox mode and the application account. SQL import errors abort
the check. A client shell escape must not execute. File restoration ignores archived owners and
sets the target image's application owner inside the rootless namespace.
The final filesystem flush runs as that application owner, so a private mode-0700 data root stays
usable without granting the extraction helper permission to bypass filesystem access checks.
Any flush failure aborts restoration.

The check compares table counts and Flyway history, compares complete file contents and empty
directories, verifies Google credential bytes as the app user and the restored database root login,
verifies Basic authentication and admin reads, and generates a local team card from a
restored season team. An empty installation uses a Chromium screenshot and records that fallback.
Graphical output and probes stay local. Discord/Google tokens and Google JSON are retained, while
the application policy disables external reads and writes. The probe also has no external network.

`state/recovery-checks/<run>/` stores private diagnostics and structured results bound to the unit
ID and manifest hash. Receipts stay outside completed units. Cleanup checks the exact run label
before removing any resource. Review `resources.json` after an interrupted run; never delete
resources using a broad project-name filter.

## live recovery

Read the installation ID from `config/installation.json`. Choose an unused generation beginning
with `recovery-`. The confirmation binds that installation and the independent target storage.

```sh
/srv/ctc-manager/releases/<version>/ctc-manager --directory /srv/ctc-manager \
  restore --recovery /mnt/backup/ctc-manager/units/<unit> \
  --confirm ctc-manager:<installation-id>:recovery-<chosen-name>
```

The command requires a successful isolated check, restores into fresh volumes and verifies them
before stopping the active app. It preserves the previous volumes and private configuration under
`state/before-<generation>.json`. It activates the selected source images with fresh private secret
paths and integrations paused. It does not put an old application onto a migrated active database.
Inspect `state/live-recovery.json` if activation fails. Preserve both storage generations until the
operator has selected and verified the authoritative one.

Original configuration/environment bytes remain in the unit. Runtime restoration accepts the
supported Basic/Discord/Google environment keys and fixes database, profile, JVM and storage
settings. It does not execute arbitrary backed-up JVM agents or external configuration files.
Review unsupported custom configuration manually before resuming integrations.

Review pending external work, old Discord references and any other instance before resuming.
The recovery process never replays deliveries automatically.

```sh
/srv/ctc-manager/releases/<version>/ctc-manager --directory /srv/ctc-manager \
  resume-integrations --confirm <installation-id>
```

This explicit command validates the current instance and recreates its app with integrations
allowed. Normal deployments keep their existing behavior unless `outboundPaused` is set.

## failure handling

`state/operation.json` records started, completed, failed or interrupted operations. A terminated
launcher leaves its lock for inspection. Check the journal, owner metadata, child processes and
container state before reconciling it. Do not remove a lock based only on a potentially reused PID.
A handled capture failure leaves no completed unit and restores the prior app state. Private
partial directories and diagnostics remain available for inspection. Do not prune any unit because
a capture or remote copy failed. Scheduled retention and off-host copies are separate maintenance
work, and the Mac cutover is a separate verified migration.

Checksums establish integrity and matching release provenance. They do not authenticate a backup
received from an untrusted party. Accept units only from owner-controlled backup storage.
