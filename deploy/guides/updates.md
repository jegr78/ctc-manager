# exact release updates

Choose an explicit released version after reading its release notes and this guide. Ordinary
updates preserve the selected persistent volumes and secret paths. They reject downgrades,
application UID changes and MariaDB major changes. Those require a separate migration plan.

The update coordinator downloads the deployment bundle and checksum from the exact GitHub release
in `jegr78/ctc-manager`, validates its complete inventory, checks the upgrade contract and pulls
immutable images before stopping the application. Staging never selects a new running version.

The coordinator holds the installation lock, stops the app, captures a complete recovery unit and
proves that unit in isolation. The app stays stopped throughout capture, proof and activation.
An error before activation resumes only the unchanged source instance and its prior running state.
Once the candidate may have run migrations, an error requires complete recovery into separate
storage. The coordinator never starts the previous application on the possibly changed database.

## operator commands

Run as `ctc-manager` with its private rootless Docker socket and `XDG_RUNTIME_DIR`.
Choose a newer explicit version supported by its `upgrade.json` minimum source version.

```sh
/srv/ctc-manager/current/ctc-manager --directory /srv/ctc-manager update-check --release <version>
/srv/ctc-manager/current/ctc-manager --directory /srv/ctc-manager update --release <version>
```

`update-check` stages and verifies the release, notes, recovery capability, database major and
application UID. It pulls exact images and reports `ready`, the selected digests and private notes
path. It does not switch `current`, edit the installation configuration or stop the source app.
Read the candidate notes and its GitHub release page before starting the planned outage.

The first installation predating these commands needs the verified released management bundle
installed under `releases/<version>` first. Run that trusted bundle's launcher with the same
`--directory` and `update` command. It reads the current source release independently. Do not execute
management tools from a recovery unit. A future unsupported inventory likewise requires reviewed
matching management tools instead of bypassing checksum or compatibility checks.

`update` rechecks the candidate and source under the shared installation lock. It requires the
expected host backup device and a private `/mnt/backup/ctc-manager/units` destination. The command
records the old/new app and database digests, pre-update unit and operation phases. It starts the
candidate with integrations paused, waits for health and checks the selected runtime before
restoring the prior integration setting. An originally paused instance stays paused. An originally
stopped instance returns to stopped after its health proof. Volumes and secret paths remain selected.

## failures and interruption

`state/update.json` describes `prepared`, `freezing`, `capturing-held`, `proving-held`, `activating`,
`healthy-integrations-paused` and `completed`. A normal error before activation produces
`failed-before-activation` only after the unchanged source resumes its prior state. A failed resume
or changed source produces `failed-review-required`. Diagnose it before another update.

`failed-recovery-required` means the candidate may have changed the schema. The coordinator pauses
and stops its app, preserves the candidate selection and names the exact complete recovery unit.
Inspect private diagnostics, then use the current trusted management launcher's `restore` command from
[recovery.md](recovery.md) with a fresh independent storage generation. Keep the old volumes.
The launcher verifies and selects the installed source release named by the unit. Recovery switches
`current` to that matching release too. It never executes the release evidence inside the unit.
Reconcile the update receipt only after the restored authoritative instance has passed its checks.

A terminated command retains its installation lock and interrupted operation journal. Inspect the
recorded owner, child processes, selected configuration, `current`, app/database state and the
update phase. Do not delete a lock or repeat an ambiguous activation based only on a dead PID.
The coordinator refuses unresolved updates rather than guessing whether migrations ran.

`state/protected-recovery.json` binds the latest pre-update unit to its manifest checksum.
`previousUnits` retains earlier protected units until the new proof passes. Maintenance must keep
these paths and every unresolved update's recovery path. A failed backup or proof never licenses
pruning. These receipts stay outside the sealed units. No timer upgrades the app automatically.
