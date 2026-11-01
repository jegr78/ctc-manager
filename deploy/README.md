# Private production deployment

Use an exact released deployment archive, its inventory and immutable application/MariaDB
image digests. Keep runtime configuration outside the release directory. Existing root-level
Compose recipes remain available for local or external-database use.

The dedicated `ctc-manager` Linux user owns the installation with mode 0700. Its rootless
Docker socket must be below its private `XDG_RUNTIME_DIR`, normally `/run/user/<uid>/`.
The launcher rejects links, non-socket endpoints and a foreign socket owner. Do not use the system Docker daemon or join
the host docker group. CTC publishes only loopback 9092; MariaDB has no published port.
Follow [guides/host.md](guides/host.md) for read-only host checks and explicit setup of
Tailnet-only HTTPS9443 without changing courtside. Run host setup from an operator-owned verified source copy.

Copy `config.example` to `/srv/ctc-manager/config/installation.json`, replace all placeholders,
and set its mode to 0600. All referenced secrets must be regular mode-0600 files under the
installation in mode-0700 directories. The protected `secrets/app.env` supplies
`SPRING_SECURITY_USER_NAME`, `SPRING_SECURITY_USER_PASSWORD`, `DATABASE_USERNAME=ctc`,
`DATABASE_PASSWORD`, optional `DISCORD_BOT_TOKEN` and `GOOGLE_CALENDAR_ID`. Generate passwords,
never use the example development credentials. The app database password must match
`secrets/db-password`. Keep `secrets/db-root-password` separate. Mount the actual Google service
account JSON as `secrets/google.json` without exposing it in logs or a public directory.

Run commands as the installation owner:

```sh
/srv/ctc-manager/current/ctc-manager --directory /srv/ctc-manager doctor
/srv/ctc-manager/current/ctc-manager --directory /srv/ctc-manager up
/srv/ctc-manager/current/ctc-manager --directory /srv/ctc-manager status
/srv/ctc-manager/current/ctc-manager --directory /srv/ctc-manager stop
```

`doctor` validates private files, digest pins, the configured daemon's rootless status and
memory-limit support. `up` starts the stack with bounded readiness waits. `stop` stops only the
app, retaining the database and volumes. Mutations use an installation lock and record their
stage under `state/operation.json`. An abandoned lock refuses further changes until the operator
checks its owner, journal and runtime. Never delete a lock based only on a PID that could have
been reused. A failure after starting services is visible and does not reset data.

Initial limits are 2 GiB for the app and 1 GiB for MariaDB. The Java heap uses at most 50 percent
of the app allowance. Measure Chromium's peak before changing these values. Console logs rotate
at 10 MB with three files per container; application file logging retains its existing policy.
The JVM explicitly uses Europe/Berlin so graphics retain the source installation time zone.
Healthchecks indicate readiness and do not restart every unhealthy container automatically.

The installation and backup paths must not contain symlinks. The credentials-init helper copies the private Google file into a dedicated named volume
as its mapped container application UID. It runs as root only inside the rootless namespace,
without networking or a writable root filesystem. The app receives that volume read-only.
The source file stays private to the host user. Test service-account reads before cutover.

Validate release archives without extracting or executing their contents:

```sh
python3 scripts/ci/deployment-bundle.py verify ctc-manager-deployment-<version>.tar.gz
```

The inventory proves integrity, not publisher authenticity. Obtain archives only from the
expected GitHub release and compare its exact version/image identity. Do not execute a launcher
copied from an untrusted backup. Full backup/update/recovery and Mac migration are separate
parent-issue deliverables. Publishing scripts is not evidence of a completed migration.

`database-image` records the tested MariaDB version tag and digest for Renovate. Release
archives retain the immutable canonical image identity. Change it deliberately
with compatibility evidence. Release retries validate existing assets and upload only missing
ones; they refuse to overwrite completed assets with different bytes.

## complete recovery

[guides/recovery.md](guides/recovery.md) documents complete recovery units, isolated restore
checks, independent live-recovery storage and explicit integration resumption. Capture preserves
the app's prior state and seals database/files/configuration/secret artifacts together. Probe runs
have no host port or external network. The `recovery-check` profile forces outbound integration
suspension and retains production Basic authentication. Optional `storageGeneration` selects
separate named volumes; `outboundPaused` must be a JSON boolean.

Recovery receipts and diagnostics live under private installation state, outside sealed units.
Automatic full checks reject historical images without the supported recovery policy. These
commands do not establish that the separate Mac cutover has completed.

## checked updates

[guides/updates.md](guides/updates.md) describes `update-check --release VERSION` and
`update --release VERSION`. Both commands select exact published deployment material under the
installation lock. An update preserves the selected volumes and keeps the app stopped from final
capture through restore proof and activation. A candidate that may have changed the schema never
triggers an old-image-only rollback. Private `state/update.json` and `state/protected-recovery.json`
record the recovery path and retain the pre-update unit.
