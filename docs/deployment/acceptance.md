# Private deployment acceptance

Parent #294. Final operational issue #301. This records measured acceptance, not a recovery SLA.

## Installation and authority

The CachyOS installation runs under the dedicated ctc-manager account and rootless Docker. Its private HTTPS endpoint is https://jegr-linux-box.taile09c97.ts.net:9443. Tailscale Serve forwards to loopback 9092. MariaDB publishes no host port. CTC has no Funnel route.

The Box is authoritative. The native Mac application remains stopped. A return requires a complete reverse migration of the latest Box state, including subsequent writes.

The accepted migration preserved all 26 original source-column projections and 715 original files. Production has 31 tables, 562 cars, 121 tracks, 33 teams, 287 drivers and four seasons. Existing Google credential bytes and integration identities matched. #300 contains the complete cutover and independent reverse-migration evidence.

## Requirement and evidence map

| Requirement | Implementation | Verification | Operational evidence |
| --- | --- | --- | --- |
| Private rootless stack and owned secrets | #295, #296 | recipe.sh, host-setup.sh, release-bundle.sh | real prod Basic Auth, rootless cgroup memory limits and credential readability |
| Complete recovery and safe activation | #297 | recovery.sh | genuine image restore, exact schema/counts/files, credentials and Chromium graphic |
| Exact release updates | #298 | update.sh | genuine 1.26 to 1.27 update, protected pre-update unit and healthy target |
| Local scheduled backup and retention | #299 | maintenance.sh | actual timer invocation, lock refusal in local journal, verified local stand and doctor ready |
| Native migration and authority boundary | #300 | migration.sh | real cutover and isolated reverse migration after a synthetic later write |
| Owner-approved immutable Pages | #302 | site-publication.sh, production-auth browser test | private real snapshot, synthetic authorized GitHub PR/CI/Pages drills and cleanup |
| Reboot and coexistence | #301 | final live checks | CTC-only restart passed in 16.31 seconds; whole-host boot test omitted because the Box supplies home LAN DNS |

Run `bash deploy/tests/acceptance.sh` for the existing fixture suites together. It does not operate the installed service or contact production destinations.

## Measurements

The genuine 1.26 isolated restore and graphic proof took 65.18 seconds. The sampled application peak was 1,030,645,350 bytes under a 2 GiB limit. The database peak was 132,435,148 bytes under a 1 GiB limit. The active source stayed running and the probe exposed no host port. Idle use was 120.8 MiB for the application and 5.133 MiB for MariaDB. Sampling cannot establish an exhaustive peak guarantee.

A complete genuine 1.27 maintenance capture and isolated restore proof took 64.93 seconds. A separate 1.27 isolated restore with a pending publication fixture took 43.10 seconds. The fixture was inserted only into the restored clone after the exact file inventory check. Startup revoked its approval, wrote a disabled publication runtime marker and the host tick stopped before reading the request or contacting GitHub. The isolated image has no Docker healthcheck, so this tick qualification used its observed successful HTTP health as the worker's healthy-container prerequisite. No production request was created.

The actual whole-host boot test remains unperformed. The Box supplies Pi-hole DNS for the home LAN, so this deployment does not restart it for acceptance. Linger and the dedicated Docker service are enabled. The corrected CTC-only daemon restart passed in 16.31 seconds. Both original container identities restarted automatically and became healthy. The host boot ID, shared Serve configuration and all five courtside containers remained unchanged. courtside and Mailpit returned HTTP 200. Pi-hole continued answering on LAN DNS port 53. This is a daemon restart, not a full host boot.

A separately measured genuine 1.27 maintenance took 60.697 seconds in total. The SQL dump took 0.112 seconds. Stopping CTC through healthy restart took 17.863 seconds, and its isolated restore proof took 39.836 seconds. The operator timing wrapper called the existing capture/proof/retention functions under the normal installation lock. It changed no installed runtime code. The data archive was 36,085,760 bytes and the SQL dump 395,279 bytes. These sizes include generated public output and its private snapshot, beyond the original 25,336,942 bytes of migrated files.

## Rootless shutdown correction

The actual daemon restart first failed because systemd signalled Docker and its helpers together, then applied the host's ten-second stop timeout. CTC application and database processes survived outside the service cgroup, leaving stale OCI runtime entries and preventing container restart. The operator verified each exact CTC process identity, start time and container cgroup, stopped those two processes gracefully and removed only their stopped OCI runtime records. The existing containers and data volumes were preserved.

The dedicated unit now uses Type=notify, NotifyAccess=all, KillMode=mixed and TimeoutStopSec=0, following the [upstream Docker Rootless setup unit](https://github.com/moby/moby/blob/master/contrib/dockerd-rootless-setuptool.sh). A failing unit contract preceded the change. The repeated live daemon restart then passed. No automatic broad process or runtime cleanup was added.

## Failure boundaries

The existing fixture suites inject missing/full storage, corrupt units, interrupted capture/update, stale owners, image/health failures, failed isolated proof and resource ownership changes. They assert refusal before unsafe mutation, no false successful backup, no pruning after failed proof and no old-image restart after possible migrations. Actual genuine update/recovery and scheduled backup qualifications are linked from #297, #298 and #299. Shared-host acceptance does not fill a live disk or force memory exhaustion.

## Publication and deferred integrations

The configured public destination is https://jegr78.github.io/ctc-manager/. No real site content has been published. The owner must choose Publish for one reviewed snapshot. The authorized synthetic repository exercised real CI failure/retry, failed Pages preserving the previous site, known-run retry and project-subpath assets. Its Pages site was removed and the repository made private afterward.

After the first owner-approved real publication, verify that its last successful public version remains reachable while CTC is stopped. This conditional check does not authorize publication now.

Gateway reconnect and durable team/incident queues belong to #279 and #287 and remain future integration tests. This acceptance sends no test messages to real Discord channels and makes no Google writes. Local backups retain two daily stands and one monthly stand. There is no off-host or notification-service requirement.

The operational reference is courtside commit 287374a7eb2d37c808658c5f0acd029d9320958f.
