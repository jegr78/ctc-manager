# Local backups

The dedicated `ctc-manager` user stores complete database, files, configuration and credential
recovery units under `/mnt/backup/ctc-manager/units` on the recorded backup device. No restic
repository, remote credentials, webhook or backup configuration file is needed.

One daily user service runs at 03:45 Europe/Berlin with up to 15 minutes of random delay.
`Persistent=true` catches up a missed run. The shared installation lock prevents overlap with
updates and recovery. Capture briefly stops only CTC, then restores its prior running state.
The existing isolated restore check verifies the result without external writes or host ports.

## Commands

Run as `ctc-manager` with its rootless Docker environment:

```sh
/srv/ctc-manager/current/ctc-manager --directory /srv/ctc-manager maintenance
/srv/ctc-manager/current/ctc-manager --directory /srv/ctc-manager doctor
/srv/ctc-manager/current/ctc-manager --directory /srv/ctc-manager prune --dry-run
```

Keep the latest backup on each of two distinct days and the first successful backup of the
newest Berlin calendar month. A monthly stand can also be one of the daily stands. There are
no weekly stands. Pending update recovery stays protected until a verified backup of the active
image exists. Unresolved update units remain protected. Never delete release material here.

Failed capture/proof, unknown/corrupt/foreign units or changed recovery references stop pruning.
Interrupted operations require checking the private journal and lock owner before retrying.
Do not remove an ambiguous lock or delete unknown backup directories to make a run pass.

`state/backups.json` records local completion, its exact isolated proof and the last attempt.
`doctor` reports missing or stale evidence after 30 hours and checks the expected backup device.
Failures return nonzero and stay in the local systemd journal. There is no separate alarm service
or notification delivery.

## Enable during cutover

Install only `ctc-manager-backup.service` and `ctc-manager-backup.timer` from the selected
verified release into `~/.config/systemd/user` for `ctc-manager`:

```sh
systemctl --user daemon-reload
systemctl --user enable --now ctc-manager-backup.timer
systemctl --user list-timers ctc-manager-backup.timer
journalctl --user -u ctc-manager-backup.service
```

Verify one scheduled invocation, its complete unit and isolated proof. Measure capture time and
capacity using the real migrated data. Do not alter courtside's services or timers. For recovery,
use `restore-check` and the independent-storage `restore` procedure in [recovery.md](recovery.md).
