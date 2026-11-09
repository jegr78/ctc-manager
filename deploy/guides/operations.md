# Operating the private CTC installation

The Box is authoritative. The retired Mac installation stays stopped and has no supported way back.

Run operational commands as the dedicated ctc-manager user with its XDG_RUNTIME_DIR. Do not use the system Docker daemon or the courtside account. The selected release lives at /srv/ctc-manager/current. Configuration, secrets and state stay private under /srv/ctc-manager. Recovery units live on the expected local backup device under /mnt/backup/ctc-manager.

## Routine operation

Log in as `ctc-manager`, for example `ssh cachyos-ctc`, and use the home launcher:

```sh
~/.local/bin/ctc-manager doctor
~/.local/bin/ctc-manager status
```

It runs `/srv/ctc-manager/current/ctc-manager --directory /srv/ctc-manager`. Use the same login for `systemctl --user`.

Use `status` and `doctor`. Check private HTTPS health and authenticated management access. The app binds only 127.0.0.1:9092 and Tailscale Serve exposes its authenticated HTTPS endpoint on 9443. Actuator health and unauthenticated Prometheus metrics live on 127.0.0.1:9190, the MariaDB exporter on 127.0.0.1:9191. The Box monitoring scrapes both; never add them to Serve or Funnel. The exporter reaches MariaDB only through the socket volume, never through the database network, so the writer guard stays unchanged and it keeps running during backups. Do not enable Funnel for CTC, reset Serve, or alter courtside 443 and Mailpit 8443.

The Box also supplies Pi-hole DNS for the home LAN. Application installation, updates and recovery do not require a host reboot. Do not reboot the shared host for a routine CTC test. Coordinate any necessary host restart separately with the owner.

The dedicated user's Docker service is enabled with lingering. Its unit waits for daemon readiness and signals the daemon before its helpers. It has no forced stop timeout that could interrupt database shutdown. Containers use restart unless-stopped. A healthcheck reports failure; it does not repair every unhealthy JVM. Inspect the CTC user journal and container state, then restart only the selected CTC service after reconciling any deployment operation.

## Local backups

One daily service/timer captures a complete recovery unit and verifies it in isolation. Retention keeps two daily stands, one monthly stand and the three newest update units. Failed capture or proof never permits pruning. Errors stay in the local journal. There is no notification service or off-host prerequisite.

Use `systemctl --user status ctc-manager-backup.timer` and the service journal. Run `backup` for an extra complete verified stand. Check `doctor` afterward. See [backups.md](backups.md). Application ZIP export is separate and does not replace a complete deployment recovery unit.

## Deploying a release

Read the release notes, then run `deploy VERSION`. It verifies the bundle and the pinned images, writes an update unit, starts the release and rolls back by itself on failure. A merge or release never deploys on its own. See [updates.md](updates.md). Never bypass checksums or publish a locally built JAR or image.

## Recovery

Nightly units carry an isolated restore proof. `restore --unit UNIT` replaces the current database and files in place, starts the release that wrote the unit and leaves outgoing integrations paused. Verify authenticated records and artwork, take a fresh verified backup, reconcile any remote publication, then resume integrations with `resume-integrations --confirm INSTALLATION-ID`. See [recovery.md](recovery.md).

A recovered pending Pages request needs fresh owner approval. Its private host PAT must be provisioned separately after host recovery. Do not copy the old checkout or assume that a previously approved request remains safe to send.

## Failed or interrupted operations

Inspect the lock owner, running children, selected release and `state/deploy.log`. Do not remove an ambiguous stale lock because its recorded PID no longer exists. A deploy whose rollback failed names the `restore --unit` command to run.

A missing or wrong backup device fails before capture. For disk-full errors, restore space without deleting verified/protected units, inspect any incomplete owned capture/probe, and rerun the complete verification. Successful deploys reclaim old app images automatically using verified release digests, while retaining the current release, immediate rollback image and all container references. Failed removals only warn. Never run Docker system prune or remove resources by a broad name prefix.

## Publisher and keys

The host publisher requires Git and the GitHub CLI. Their technical credential remains limited to the configured repository.

Generate Site creates a private immutable snapshot. The owner reviews it and explicitly confirms Publish. The host-only PAT remains outside the app container. The single publisher timer advances short stages through the existing installation lock. CI, merge and Pages are distinct states. A successful merge alone is not a completed publication.

For failed CI or Pages, inspect the linked remote run and preserve the request identity. Rerun a known failed remote workflow before explicitly retrying the same snapshot. Reconcile an ambiguous dispatch or merge response instead of creating another request. Rotate an expired token through the owner's GitHub settings and replace the private host file with mode 0600. Manage the Tailscale node key and grants in the Tailnet admin console. No scheduled key-warning or Discord service is required.

## CTC-only shutdown or removal

Stop the CTC publisher/backup timers, stop the app through its selected launcher and remove only Serve port 9443. Disable only the dedicated CTC Docker user service after its owned containers stop. Preserve database/data volumes, secrets and recovery units until the owner separately approves their removal. Never stop system Docker, reset shared Serve/Funnel configuration or remove courtside resources.

## Evidence and limits

The accepted real migration contains 31 tables, 562 cars, 121 tracks, 33 teams, 287 drivers, four seasons and 715 original files. All 26 original source-column projections matched. A synthetic subsequent write was restored back onto the Mac in isolation without changing its native source database.

On the Box, an idle genuine 1.26 app used 120.8 MiB under its 2 GiB limit. The database used 5.133 MiB under its 1 GiB limit. A complete isolated restore plus authenticated graphic rendering took 65.18 seconds, with a sampled application peak of 1,030,645,350 bytes and database peak of 132,435,148 bytes. These are observed samples, not a recovery SLA or an exhaustive peak guarantee. The active source app stayed running and the probe published no host port.

Gateway reconnect and durable bot queues must be verified when #279 and #287 deliver them. This deployment does not claim those absent features work, and acceptance sends no test messages to real Discord channels.

The operational reference was reviewed at courtside commit 287374a7eb2d37c808658c5f0acd029d9320958f. CTC adapts its release, locking and recovery approach. Its PostgreSQL, MFA and mail components are outside this installation.
