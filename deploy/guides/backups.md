# Local backups

The dedicated `ctc-manager` user stores complete recovery units with database, files, configuration and credentials under `/mnt/backup/ctc-manager/units` on the recorded backup device. No restic repository, remote credentials, webhook or backup configuration file is needed.

One daily user service runs at 03:45 Europe/Berlin with up to 15 minutes of random delay. `Persistent=true` catches up a missed run. The unit orders itself after the site publisher. The shared installation lock prevents overlap with deploy and restore; `backup` waits up to 16 minutes for it.

## commands

```sh
~/.local/bin/ctc-manager backup
~/.local/bin/ctc-manager doctor
```

`backup` stops phpMyAdmin if it runs, briefly stops the app, writes a unit, restores the prior app state, proves the unit in an isolated restore and then prunes. It starts phpMyAdmin again afterwards, also after a failure. The proof checks table counts, Flyway history, files, credentials, authentication and a graphic render without host ports or external network.

## retention

Pruning keeps the newest unit, the newest unit on each of two distinct Berlin days, the first unit of the newest Berlin month and the three newest update units `recovery-update-*`. A monthly stand can also be one of the daily stands. Failed capture or proof, unknown or corrupt units stop pruning. Never delete release material here.

`state/backups.json` records the last completion, its proof and the last attempt. `doctor` reports missing or stale evidence after 30 hours and checks the expected backup device. Failures return nonzero and stay in the local systemd journal. There is no alarm service.

## timer

`deploy` installs `ctc-manager-backup.service` and `ctc-manager-backup.timer` from the running release. Check them with:

```sh
systemctl --user list-timers ctc-manager-backup.timer
journalctl --user -u ctc-manager-backup.service
```

For a restore, see [recovery.md](recovery.md).
