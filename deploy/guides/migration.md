# Native Mac migration

Use the verified release tools. This initial migration supports the existing native Mac layout,
MariaDB 13.0 and successful Flyway versions 1 through 6. The target must still be the empty,
paused qualification installation. It refuses a second cutover over real target data.

## Source inventory and freeze

The exporter needs the existing Mac `python3` with PyYAML, MariaDB client/dump and `lsof`.
It reads `application-local.yml`, `.env`, `.env.local`, `google-credentials.json`,
`data/local/uploads`, optional local import/backup staging and `docs/site`.
It never sources configuration as shell code or calls the port-killing app script.

Stop the identified CTC Maven process gracefully and keep it stopped throughout cutover.
Do not stop another listener or the native database service. The exporter refuses port 9091,
other database connections and enabled database events. Its observer must have existing global
PROCESS visibility on the same database instance. Use the existing local admin socket account
for that read-only observation; do not add grants merely to pass the freeze check.

```sh
./deploy/migrate-from-mac.sh inventory --source-root "$PWD" --observer-user jegr
mkdir -m 700 "$HOME/ctc-migration-export"
./deploy/migrate-from-mac.sh export --source-root "$PWD" --observer-user jegr \
  --destination "$HOME/ctc-migration-export/final"
```

The destination must not exist and its parent must be private. Export verifies source metadata
and file hashes again after capture. An error leaves a private partial export for inspection,
never a sealed successful export. The original database and files remain untouched.

`source.json` records native provenance, database/schema inventory, original-column hashes,
capture time and checksums for SQL, data and original private configuration. An unrecorded source
application version is JSON `null`; the current checkout version is not a substitute.

Transfer the entire private directory to a CTC-owned private directory on the box and preserve
the independently recorded SHA-256 of `source.json`. On Mac tar transfers, set
`COPYFILE_DISABLE=1` and use `tar --no-xattrs`. AppleDouble metadata files are unknown payloads
and are deliberately refused. Never print SQL, environment or credential contents into evidence.

## Isolated rehearsal

Run as `ctc-manager` with `XDG_RUNTIME_DIR=/run/user/1002`. Select an actual installed release
with the migration tools; it can differ from the currently active qualification release.
Use a private directory on the verified backup device, outside the normal retained `units` bucket.

```sh
/srv/ctc-manager/releases/VERSION/migrate-from-mac.sh rehearse \
  --directory /srv/ctc-manager --source-export /PRIVATE/FINAL_EXPORT \
  --source-sha256 SOURCE_MANIFEST_SHA256 --destination /mnt/backup/ctc-manager/migration-units
```

The shared installation lock coordinates rehearsal with maintenance. The existing Probe creates
fresh owned storage on an internal network, without host ports. Recovery policy disables external
integration reads and writes while retaining the real configuration and credential bytes.
It imports the native SQL, checks the original columns, runs the genuine target Flyway migrations,
and compares those columns again. It also compares the exact file inventory and authenticated
reads. The supported result is a new complete recovery unit with the actual target release and
schema, followed by the existing independent full restore-check and local team-card rendering.
The original native export remains separate and unchanged.

Private `state/migration-UNIT_ID.json` links the source manifest hash, exact target unit and proof.
A rehearsal is not production cutover. For final cutover, repeat the frozen final export and
rehearsal if the source has changed since the earlier rehearsal.

## Production cutover

Keep the daily backup timer stopped during the coordinated cutover. Preserve the original source
export, rehearsed unit, trusted releases and previous box volumes. Before the first real backup,
move only verified qualification-only normal units and their obsolete backup/update receipts into
a private qualification archive. Do not mix the empty qualification monthly stand into production
retention, remove unknown entries or delete real backups to make validation pass.

```sh
/srv/ctc-manager/releases/VERSION/migrate-from-mac.sh cutover \
  --directory /srv/ctc-manager --source-export /PRIVATE/FINAL_EXPORT \
  --source-sha256 SOURCE_MANIFEST_SHA256 --recovery /PRIVATE/REHEARSED_UNIT \
  --confirm ctc-manager:INSTALLATION_ID:recovery-production-SUFFIX
```

The target confirmation requires this installation and unused storage. Cutover accepts only the
exact source/unit proof. It uses the existing complete restore, preserves previous storage and
keeps integrations paused. A durable `state/production-authority.json` marks `writes-possible`
before activation can expose the target HTTP port. It then checks the activated source columns.
A post-boundary failure stops the candidate and records whether that stop succeeded.

Before marking the box authoritative, verify Tailnet HTTPS and Basic authentication, real record
counts and representative seasons/teams/drivers, uploads, the Google credential mount and calendar
identity. Generate a local graphic without external writes. Take and prove a new local backup of
the real target, reconcile pending external work, then enable only the intended integrations.
Record acceptance time, target generation and exact backup proof in the private authority receipt.
Re-enable the single daily backup timer and leave the Mac Maven process disabled.

## Failure boundaries

A recorded failure before `writes-possible` permits returning to the unchanged Mac source after
checking the target never opened for writes. Reconcile the authority receipt explicitly before a
retry. The command never clears an ambiguous receipt or guesses that an earlier attempt was safe.

After `writes-possible`, including `active-awaiting-acceptance`, preserve the latest box state.
Freeze its writer, capture a complete current unit and restore that unit into independent Mac
storage with the matching trusted release and credentials. Verify the new writes, files and
application before transferring authority and resuming integrations. Never start an old image
against a migrated database or restart the stale original Mac database as rollback.

The rehearsal qualified this boundary with a synthetic team created through the authenticated UI
in an isolated real-data clone, then a complete restore of the later state on the Mac. It did not
change an original team, the native Mac database or the live qualification installation.
