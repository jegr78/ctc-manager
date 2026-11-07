# Deploying a release

A merge to `master` creates a release when the commits since the last tag contain a `feat`, `fix`, `perf`, `chore(deps)` or breaking change. A release never reaches the box by itself. The operator deploys it:

```sh
~/.local/bin/ctc-manager deploy [VERSION|latest]
```

Run it as `ctc-manager` on the box, for example `ssh cachyos-ctc ~/.local/bin/ctc-manager deploy 1.53.0`. Read the release notes on GitHub first.

## steps

The command holds the installation lock and waits up to 16 minutes for a running backup.

1. It checks the backup device, downloads bundle and checksum from the GitHub release in `jegr78/ctc-manager`, verifies both and extracts the bundle to `/srv/ctc-manager/releases/VERSION`.
2. It refuses a version that is not newer than the running one, a source older than `minimumSourceVersion` in the new `upgrade.json`, an app image with a different UID and, without `--allow-database-upgrade`, a different MariaDB major version. It pulls the pinned images.
3. It records `flyway_schema_history`, stops phpMyAdmin if it runs, stops the app and writes an update unit `recovery-update-*` to `/mnt/backup/ctc-manager/units`.
4. It pauses outbound integrations, points `installation.json` and `current` at the new release and starts it. Flyway migrates during startup.
5. Once Compose reports the stack healthy within 180 seconds, it installs the release's systemd units, the phpMyAdmin unit and `~/.local/bin/ctc-manager`, reloads the user manager and restores the previous `outboundPaused` value.
6. It starts phpMyAdmin again if it ran before and deletes the state of the generation era.

A failure in steps 1 or 2 changes nothing. From step 3 on the command rolls back by itself. If the Flyway history or the database image changed, it restores the update unit in place, otherwise it starts the previous release on the unchanged database. Host files installed in step 5 return to their previous content. Integrations stay paused after a rollback until `resume-integrations`.

If the rollback fails too, the app stays stopped and the error names the recovery command:

```sh
~/.local/bin/ctc-manager restore --unit /mnt/backup/ctc-manager/units/recovery-update-...
```

Ctrl-C and `SIGTERM` reach the rollback. `SIGHUP` is ignored, so a dropped SSH session does not interrupt it. Docker output goes to `/srv/ctc-manager/state/deploy.log`.

## rollback drill

```sh
~/.local/bin/ctc-manager deploy VERSION --drill-rollback
```

The drill runs every step with the running version and then treats health as failed. It proves that rollback works on the box.

## first switch from 1.51.0

Release 1.51.0 has no `deploy`. Its backup drop-ins restart phpMyAdmin after the nightly run, so do not run the first switch between 03:30 and 04:30. Run the new launcher once by hand as `ctc-manager`:

```sh
V=1.53.0
work=$(mktemp -d) && cd "$work"
base=https://github.com/jegr78/ctc-manager/releases/download/v$V
curl -fsSLO "$base/ctc-manager-deployment-$V.tar.gz"
curl -fsSLO "$base/ctc-manager-deployment-$V.tar.gz.sha256"
sha256sum -c "ctc-manager-deployment-$V.tar.gz.sha256"
tar -xzf "ctc-manager-deployment-$V.tar.gz"
"./ctc-manager-deployment-$V/ctc-manager" --directory /srv/ctc-manager deploy "$V"
```

The deploy downloads and verifies the release again into `releases/$V`, so the scratch copy only supplies the code. Remove `$work` afterwards. A rollback during this first deploy returns to 1.51.0 with its units and keeps the legacy state. If that rollback fails too, the error names `releases/$V/ctc-manager --directory /srv/ctc-manager restore --unit UNIT --legacy-release`, because 1.51.0 has no in-place restore and the home launcher does not exist yet. After success, every later update is the single command.
