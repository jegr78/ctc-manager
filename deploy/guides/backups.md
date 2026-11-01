# scheduled backups

Run maintenance as the dedicated `ctc-manager` host user. It shares the same installation lock
as update, recovery and manual backup. A complete capture briefly stops only CTC, restarts it in
its prior state and leaves MariaDB running. The subsequent isolated proof has no public port or
outbound access. Remote replication never receives an incomplete recovery unit.

## provision the destination and key

Install restic on the host from the operating system package manager. Choose a repository outside
this box and initialize it separately. Record its exact repository ID from `restic cat config`.
Keep the repository password and replacement-host access instructions outside the box too. The
working password file on this host permits automated backups; it is not the only recovery key.
Do not put credentials in shell history, process arguments or this repository.

Create private files below `/srv/ctc-manager/secrets`, owned by the installation user:

- `restic-repository` contains the selected remote URL, for example an SFTP destination.
- `restic-password` contains the encryption password.
- `restic-environment.json` contains only backend access environment variables, or `{}` for SSH.
- `backup-alert` is an executable private hook that accepts one private JSON event-file path.

The hook delivers through the operator-selected route and must exit nonzero if delivery fails.
It must not post to team or match channels by default. Event files contain safe source, stage and
time metadata. The service journal contains no raw backup payload or backend credentials.
`state/restic-last-error.log` is private and can contain backend diagnostics. Treat it as sensitive.

Write private `config/backup.json` after destination, external key custody and alerting are ready:

```json
{
  "format": 1,
  "repositoryFile": "/srv/ctc-manager/secrets/restic-repository",
  "passwordFile": "/srv/ctc-manager/secrets/restic-password",
  "environmentFile": "/srv/ctc-manager/secrets/restic-environment.json",
  "repositoryId": "<64 lowercase hex characters from the initialized repository>",
  "alertHook": "/srv/ctc-manager/secrets/backup-alert",
  "keyCustodyConfirmed": true,
  "offHostConfirmed": true
}
```

The operator confirms the actual destination is on a different host. Local directory backends,
plain HTTP REST backends and obvious SFTP loopback paths are refused. Remote configuration alone
is not operational acceptance; prove retrieval from the selected destination. A temporary local
restic test only qualifies the interface.

Supported access environment keys are AWS access/session/region variables, B2 account variables,
Azure account/key/SAS variables and `GOOGLE_APPLICATION_CREDENTIALS`. Repository/password command
overrides, loader variables and PATH overrides are rejected. SFTP uses the dedicated user's own
SSH configuration. Keep SSH private keys and host-key verification in place.

## run and inspect

The units directory must exist privately under the recorded backup mount:

```sh
mkdir -m 700 /mnt/backup/ctc-manager/units
/srv/ctc-manager/current/ctc-manager --directory /srv/ctc-manager maintenance
/srv/ctc-manager/current/ctc-manager --directory /srv/ctc-manager backup-check
/srv/ctc-manager/current/ctc-manager --directory /srv/ctc-manager prune --dry-run
/srv/ctc-manager/current/ctc-manager --directory /srv/ctc-manager retrieve-check --unit /mnt/backup/ctc-manager/units/<completed-unit>
```

Maintenance records local completion, isolated proof and remote completion separately in private
`state/backups.json`. A failed external copy preserves local success but returns failure, sends
an alert and skips retention. Successful copies bind the exact repository ID, full snapshot ID,
source path, installation/unit tags and sealed manifest checksum. The remote retrieval command
uses that exact snapshot, validates every artifact and runs the isolated full application proof.
Its `retrieval` receipt is separate from upload success.

`doctor` includes backup freshness and missing configuration, and exits nonzero while protection
is incomplete. `backup-check` exits nonzero when
any required evidence is absent, older than 30 hours or failed. The hourly timer catches a missed
daily run even when the last successful backup service status is still green. Check the journal,
`systemctl --user list-timers` and the independent alert route after an outage.

A capture or process interruption leaves private operation/evidence state. Inspect the shared
lock owner and child processes before reconciling it. Persistent timers never remove stale locks.
An alert service runs independently of the Docker daemon, so a daemon or lock failure can still
reach the selected notification hook. Missing alert configuration remains a failed service.

## retention

Retention keeps the union of 14 daily, eight ISO-weekly and twelve monthly calendar buckets,
plus the newest complete unit, units without confirmed remote copies, protected pre-update
units and units named by unresolved update recovery. It never prunes release directories or
images. Dates are evaluated in UTC. Retention validates all units before planning deletion and
confirms exact remote snapshots before removing any candidate.

Unknown, corrupt or foreign units, a failed newest operation, changed replica receipts and changed
protected references stop pruning. Inspect these entries rather than deleting them to make a run
pass. An interrupted `.partial-*` entry requires operator reconciliation. A full disk remains a
failure; required recovery units are never removed as an emergency space workaround.

Remote snapshots currently accumulate without automatic deletion. The calendar policy above bounds
local units only. Provision and monitor remote capacity separately; record the destination retention
policy before production acceptance. CTC does not issue `restic forget` or prune unrelated remote data.

## enable during cutover

The examples are user units for `ctc-manager`, not root system units. Install the five files
`ctc-manager-backup.service`, `ctc-manager-backup.timer`, `ctc-manager-backup-check.service`,
`ctc-manager-backup-check.timer` and `ctc-manager-backup-alert@.service` from the selected release
into the dedicated user's `~/.config/systemd/user`, then run `systemctl --user daemon-reload`.
Do not replace courtside's units or repair its dangling timer as part of this setup.

The daily timer runs at 03:45 Europe/Berlin with up to 15 minutes of random delay and persistent
catch-up. Recheck competing maintenance and measure capture duration first. Enable only in the
scheduled cutover window after destination, key custody, successful retrieval and alert drills:

```sh
systemctl --user enable --now ctc-manager-backup.timer ctc-manager-backup-check.timer
systemctl --user list-timers ctc-manager-backup.timer ctc-manager-backup-check.timer
journalctl --user -u ctc-manager-backup.service -u ctc-manager-backup-check.service
```

Observe an actual scheduled completion. Inject a safe failure in an isolated qualification setup
to prove the selected alert route. Record free space, measured unit size and retention capacity.
Do not claim #299 or the parent ready before off-host retrieval and these host checks pass.

On a replacement host, provision the dedicated user/private stack and trusted release materials,
recover the restic password and backend access from external custody, select the exact snapshot,
retrieve into private staging and validate it before full independent-storage recovery. Keep
integrations paused until the authoritative instance and pending work have been reviewed.

References: [restic repository setup](https://restic.readthedocs.io/en/stable/030_preparing_a_new_repo.html),
[backup](https://restic.readthedocs.io/en/stable/040_backup.html),
[restore](https://restic.readthedocs.io/en/stable/050_restore.html).
