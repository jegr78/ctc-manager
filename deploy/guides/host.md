# CachyOS host setup

Run setup as the host operator from a verified, operator-owned release or source directory. The operator needs noninteractive sudo. The script does not install packages, change the system Docker service or grant the CTC account sudo/Docker-group access.

## Prerequisites

Use Linux x86_64 with Docker Compose, dockerd-rootless.sh, rootlesskit, slirp4netns, newuidmap/newgidmap, Python3, systemd, Tailscale, getent, findmnt and ss installed. Cgroup v2 must delegate memory control to the rootless daemon. The host needs at least 4GiB RAM. The inventory reports current available RAM; reserve capacity for courtside and other host services. Keep at least 3GiB free under /srv before setup. Plan additional space for images, application data and recovery units.

Mount the dedicated backup device at /mnt/backup and obtain its UUID from findmnt. Setup rejects a missing or substituted device. On the current box the configured UUID is 31d7af2e-b6de-4680-9dfb-692ffb692fc4.

```bash
bash deploy/setup-host.sh --check --backup-uuid 31d7af2e-b6de-4680-9dfb-692ffb692fc4
bash deploy/setup-host.sh --apply --backup-uuid 31d7af2e-b6de-4680-9dfb-692ffb692fc4
```

Check is read-only. Apply creates or validates ctc-manager, its private home, /srv/ctc-manager and /mnt/backup/ctc-manager. Existing foreign owners, supplementary groups and overlapping subordinate IDs cause failure. Subordinate mappings cannot include existing host user/group IDs. Setup enables lingering and only the CTC user's Docker service. Its endpoint is unix:///run/user/1002/docker.sock on this box; use the discovered UID on another host.

The shared installation lock excludes deployment operations. Setup records private state/host.json and state/host-setup.json. An existing lock requires inspection of its owner and journal before manual reconciliation. Do not delete a lock merely because a previous command failed.

## Private ingress

Setup adds exactly HTTPS port9443 to Tailscale Serve, proxying http://127.0.0.1:9092. It retains every unrelated Serve/Funnel setting, including courtside on443 and Mailpit on8443. It rejects an existing unrelated mapping or public listener. Repeating setup validates the existing CTC route.

Use https://jegr-linux-box.taile09c97.ts.net:9443 from an authorized Tailnet device. The application still requires the existing single-admin Basic authentication for management. Docker publishes only loopback9092. MariaDB has no host port. Never enable Funnel for9443 or attach CTC to courtside's public443 route.

The Tailnet owner manages ACL/grants and node authorization in the Tailscale admin console. Review management-device access and node-key expiry there. Setup records no auth key and never changes the shared Tailnet policy. A missing key-expiry field in local status does not prove expiry is disabled.

Verify the Tailnet HTTPS health endpoint and authenticated admin page after starting the stack. A request to the LAN IP on9092/9443 must fail. An external device without Tailnet access must not reach9443. Preserve before/after listener and Serve inventories. Reboot and unattended-start verification belong to final operational acceptance.

## CTC-only removal

Stop the CTC app through its installed launcher first. Disable only the CTC ingress with `sudo tailscale serve --https=9443 off`. Disable the Docker user service as ctc-manager with its XDG_RUNTIME_DIR and user DBus environment, then disable lingering only for ctc-manager after checking for remaining CTC timers. Preserve application/database volumes and recovery units until the owner explicitly approves their removal. Never reset Tailscale Serve, stop system Docker or remove courtside resources.
