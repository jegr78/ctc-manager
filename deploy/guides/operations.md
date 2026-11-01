# Operating the private CTC installation

The Box is authoritative. The native Mac application stays stopped. Do not restart it against its old database after the Box can accept writes. A return to the Mac requires a complete reverse migration of the latest Box database, files and private configuration.

Run operational commands as the dedicated ctc-manager user with its XDG_RUNTIME_DIR. Do not use the system Docker daemon or the courtside account. The selected release lives at /srv/ctc-manager/current. Configuration, secrets and state stay private under /srv/ctc-manager. Recovery units live on the expected local backup device under /mnt/backup/ctc-manager.

## Routine operation

For example, from the owner account on the Box:

```sh
CTC_RUNTIME_UID=$(id -u ctc-manager)
sudo -u ctc-manager env XDG_RUNTIME_DIR="/run/user/$CTC_RUNTIME_UID" \
  /srv/ctc-manager/current/ctc-manager --directory /srv/ctc-manager doctor
```

Use the same user and runtime directory for `systemctl --user` and the other launcher operations.

Use the selected launcher's `status` and `doctor` operations. Check private HTTPS health and authenticated management access. The app binds only 127.0.0.1:9092 and Tailscale Serve exposes its authenticated HTTPS endpoint on 9443. Do not enable Funnel for CTC, reset Serve, or alter courtside 443 and Mailpit 8443.

The Box also supplies Pi-hole DNS for the home LAN. Application installation, updates and recovery do not require a host reboot. Do not reboot the shared host for a routine CTC test. Coordinate any necessary host restart separately with the owner.

The dedicated user's Docker service is enabled with lingering. Its unit waits for daemon readiness and signals the daemon before its helpers. It has no forced stop timeout that could interrupt database shutdown. Containers use restart unless-stopped. A healthcheck reports failure; it does not repair every unhealthy JVM. Inspect the CTC user journal and container state, then restart only the selected CTC service after reconciling any deployment operation.

## Local backups

One daily service/timer captures a complete recovery unit and verifies it in isolation. Retention keeps two daily stands and one monthly stand, plus any unit protected by an unresolved update. Failed capture or proof never permits pruning. Errors stay in the local journal. There is no notification service or off-host prerequisite.

Use `systemctl --user status ctc-manager-backup.timer` and the service journal. Run the selected launcher's `maintenance` operation for an extra complete verified stand. Check `doctor` afterward. Application ZIP export is separate and does not replace a complete deployment recovery unit.

## Exact release updates

Read the explicit release notes, run `update-check --release VERSION`, then `update --release VERSION`. Both commands verify the release bundle and immutable image identities. If an old management bundle cannot recognize a newer inventory, install reviewed matching management tools first. Never bypass checksums, run a release downloaded by the app, or publish a locally built Mac JAR/image.

The coordinator freezes the source, captures and proves its complete pre-update stand, and only then permits target migrations. It preserves the previous integration state. After migrations may have run, never roll back only the image. Use a complete recovery unit and fresh storage instead.

## Recovery

Run `restore-check --recovery UNIT` before live recovery. It starts isolated MariaDB/application containers with no published ports and outbound integrations disabled. The proof checks database history and counts, original files, credentials, authentication and an actual graphic render.

For live recovery, use `restore --recovery UNIT --confirm ctc-manager:INSTALLATION-ID:recovery-UNUSED-NAME`. Keep old volumes. The command selects the unit's trusted installed source release, restores into a fresh generation and leaves outgoing integrations paused. Verify authenticated records and artwork, take a fresh verified backup, reconcile any remote publication, then explicitly resume integrations with the installation confirmation required by the launcher.

A recovered pending Pages request needs fresh owner approval. Its private host PAT must be provisioned separately after host recovery. Do not copy the old checkout or assume that a previously approved request remains safe to send.

## Failed or interrupted operations

Inspect the operation journal, lock owner, running children, selected release, storage generation and private phase receipt. Do not remove an ambiguous stale lock because its recorded PID no longer exists. Establish whether a candidate could have migrated the database before deciding between unchanged-source resumption and full recovery.

A missing or wrong backup device fails before capture. For disk-full errors, restore space without deleting verified/protected units, inspect any incomplete owned capture/probe, and rerun the complete verification. Never run Docker system prune or remove resources by a broad name prefix.

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
