# Private website publication

The Manager remains a single-admin application behind Tailnet HTTPS and existing Basic authentication. Generate Site creates an immutable private snapshot before replacing local output. The owner reviews that exact snapshot and confirms Publish. Generation alone never requests publication.

The snapshot contains only generated public website files. Storage retains the three most recent snapshots plus the snapshot referenced by the current publication request. Private preview requires authentication and uses a response-level CSP sandbox and an iframe without same-origin permission. Scripts cannot read Manager pages, submit forms or connect to backend endpoints. Direct preview links retain the sandbox. Preview files have no-store caching.

## Host setup

Use a fine-grained PAT from the owner's account for jegr78/ctc-manager only. Grant Contents and Pull requests read/write, Actions read/write. CI results come from the Actions jobs API; no Checks permission is required. Set an expiration. Do not grant Administration or Pages write to the runtime publisher. Initial Pages configuration uses the operator's existing owner login and selects GitHub Actions as source. It does not publish content.

Place the PAT at `/srv/ctc-manager/secrets/pages-token`, owned by ctc-manager with mode 0600. Never put its value into chat, command arguments, a Git remote URL or the app environment. The publisher passes it only to Git/GitHub child processes. The app has neither the PAT nor a Docker socket. A PAT cannot restrict writes to one repository directory. The host publisher checks the commit and every file against the approved snapshot and permits changes only under docs/site.

Set `CTC_SITE_PUBLICATION_ENABLED=true` in the private app environment and recreate the app using the selected release. Install the supplied publisher service and timer in the dedicated user's systemd directory, run daemon-reload and enable the timer. The timer processes one stage per run. There is no separate daemon or notification service. Runtime state and the disposable checkout live under `/srv/ctc-manager/state/pages-publisher`.

## Approval and status

Generate, privately preview, check the snapshot identity and confirm Publish. Repeated active clicks return the same request. The worker creates one deterministic branch and pull request, checks its exact head, waits for repository CI, and merges through the existing repository API. The squash commit records the complete approved website Git tree. The Pages workflow checks that tree before upload, and the worker independently checks the merged tree. It then waits for the Pages workflow matching the merge commit. If Git already matches but no deployment exists, the explicit approval starts the fixed workflow for the approved commit/tree and records the returned run ID. Published means that workflow succeeded and the public index matches the approved snapshot, not just that the PR merged.

A changed master, failed check, unexpected remote result or mismatch stops publication. The existing public site remains available. Review the linked PR and workflow before explicitly retrying the same snapshot. If a known Pages run failed, rerun that linked workflow on GitHub before retrying. An ambiguous dispatch response requires checking Actions and recording its matching run ID, never blindly dispatching again. A different snapshot cannot replace an unresolved request. Do not delete worker state or force-push branches to clear a failure. Reconcile the recorded request, branch, commit and PR first. If the base changed, the operator must review/rebase the publication branch with exact approved website bytes and update its private recorded base/head after verification. Do not bypass repository protection.

## Maintenance and recovery

The worker shares the installation lock with backup, update and recovery and refuses paused outbound state or an unhealthy app. A boot marker must belong to the current container start. App startup revokes pending approvals, including after restoration, before writing the new marker. Corrupt publisher metadata disables publication without preventing the Manager from starting. Reapprove the exact snapshot only after reconciling any already created remote PR/run.

Snapshots, request and status are part of the regular application data backup. The disposable checkout need not be restored. The host PAT must be provisioned or rotated separately after host recovery. It is never included in public exports. Revoke an expired or suspect token on GitHub and replace the private file atomically, then retry the same reviewed request. No token value appears in the UI or service errors.

Keep local backups at two daily and one monthly stand. Publisher errors stay in the local journal and Generate Site status. There are no Discord alerts.

The reverse proxy uses `SERVER_FORWARD_HEADERS_STRATEGY=framework` to keep authenticated redirects on HTTPS. The selected Compose recipe and recovery environment set this constant. Historical restore tools may require it in the restored private app environment before exposing the restored instance.
