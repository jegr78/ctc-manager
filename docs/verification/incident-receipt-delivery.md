# Durable incident receipts and reporting cutoff

Issue #290 is selected after delivered #288 and #289. Parent #287 is open and has no milestone;
#290 is open and unassigned. No open PR, incident receipt branch, other worktree or sibling
Claude checkout collides. The dependency dashboard is assigned and excluded. The last local
release plan is shipped; GitHub defines this dependency order. Selection base: `ddaed7e93d0527569ca9935ae3292ca79980da80`.

## Delivery checklist

- [x] Verify current tracker, dependencies, release order and parallel work.
- [x] Map production paths, positive/negative cases and real runtime witnesses.
- [ ] Reproduce intended receipt, cutoff, immutable snapshot and recovery failures.
- [x] Implement atomic first-submit receipt work and locked deadline/disclosure capture.
- [ ] Add bounded durable delivery with explicit retry/uncertainty and restore admission.
- [ ] Align migration, archive round-trip/preflight, audit and operator documentation.
- [ ] Review and independently assess security; falsify new protections individually.
- [ ] Verify one final committed head locally and in CI, then merge and reconcile the tracker/Wiki.
- [ ] Clean up the proven landed branch and continue the queue.

## Requirements and existing contracts

The existing first SUBMITTED event is a durable request source. Receipt delivery and detailed
post-cutoff disclosure are distinct operations. Edits/withdrawals must never enqueue another
neutral receipt. The current report service already validates current permissions, strict server
cutoff, original party and durable request ownership under window locks. Keep those rules.

| Requirement | Existing/new path | Positive witness | Failure witness and runtime proof |
| --- | --- | --- | --- |
| Atomic first-submit receipt | IncidentReportService.append + new delivery planner/repository | One durable generic request commits with the first report | Rollback leaves neither, replay/edit/withdraw leave one; real H2/MariaDB |
| Neutral outbound payload | New IncidentNoticeService + DiscordRestClient bot message adapter | Fixed detail-free text, mention suppression and captured destination | Distinctive private marker absent from actual HTTP payload; malformed/nonmatching response not success |
| Deadline and frozen disclosure | IncidentWindowService.closeDue + new IncidentDisclosureService | Under current locks, freeze final active revision and enqueue immutable snapshot | No early closure, stale observed deadline ignored, withdrawn excluded, concurrent edit/close serialized |
| Bounded durable work | New IncidentDeliveryService/worker | Bounded claim/lease, unique operation key, retry proven safe outcomes | Unknown sends become uncertain, no blind duplicate; stale claim cannot acknowledge restored work |
| Receipt supersession | Delivery service and disclosure state | Generic receipt stays historical; pending receipt superseded after actual disclosure | Closure alone must not pretend detailed disclosure succeeded |
| Restart/restore | Persisted due scans, ImportLockService, epoch/post-commit hook | Restart finds due windows; complete work joins writer admission | Restore blocks new writes, drains accepted work, pauses restored external sends; error paths release |
| Permanent archive/audit | New migration + BackupSchema/MixIn/restorer/preflight/audit | Round-trip exact operation/snapshot/state references | Invalid ownership/revisions/claims rejected before wipe; retain unknown delivery states paused |

Production call sites inspected: IncidentReportService.submit/edit/withdraw/append,
IncidentWindowService.closeDue/lock, IncidentReport.freeze, PickWriteExecutor,
PickSessionEpoch.afterRestore and DiscordRestClient. DiscordRestClient currently has no generic
bot-message creation method; extend its existing bounded HTTP/error/rate-limit infrastructure.
Do not build a second transport or use webhooks. The runtime defaults and OutboundPolicy must
prevent premature external activation. #291 owns creating linked detailed threads; #292 owns
rulings; #293 owns full rollout acceptance. No live registrations, notices or production changes.

Keep implementation focused, perform review before the one final clean build, and report evidence
on the issue/PR. Avoid per-field architecture expansion and repeated full-suite verification.

## Atomic storage checkpoint

Five intended unit assertion failures, with zero errors/skips, preceded the first implementation.
The capture and existing incident mutation/window contracts then passed all 33 unit cases.
Synchronous Spring event listeners with MANDATORY propagation enqueue the receipt or final
snapshot inside the producer's transaction; failures before commit roll back the report/revision/
event/operation together. Real rollback and closure checks passed four cases on each of H2 and
MariaDB. Final disclosures refer to their immutable revision, with a captured case/party label.

Schema 9 adds one delivery entity, bringing the archive to 42 tables. All prior schemas remain
supported; schema 8 may omit the new operation file. Serializer/restorer, export registration,
FK topology, preflight and the read-only incident audit include delivery ownership/destination,
operation identity, frozen revision, claim and outcome metadata. Thirty-eight unit and 48
integration cases passed, including thirteen archive cases on each database. Four new malformed
operation variants are rejected before wipe, including foreign private revisions and destinations.

The restore-only deliveryPaused flag belongs to each reporting window, is excluded from the
wire snapshot and is set on every restored window. This preserves exact operation IDs/states
while keeping their future external work paused across restart. V30 seeds already committed
SUBMITTED requests and closed active snapshots during upgrade; importing old archives never
invents external work. A direct migration witness first failed for a report lacking SUBMITTED
provenance, then passed after restricting the upgrade source to confirmed first submissions.
It also proves future timestamps survive after the creator connection closes.

This checkpoint does not complete #290. Remaining work is the actual neutral HTTP sender,
bounded claims/leases and due worker, safe retry/uncertainty/reconciliation, receipt supersession,
worker restore/concurrency witnesses, status presentation, final documentation, individual guard
falsification, independent review, canonical local/CI verification and merge/housekeeping.

## Claim and outcome checkpoint

The delivery state contract first produced 21 intended assertion failures, zero errors/skips.
Its corrected run passed 53 unit cases including the upgrade, capture and archive validators.
Pending/known-rejected work can acquire one claim; future work, restored contexts and already
claimed/sent/uncertain work cannot. Acknowledgement requires the exact token, aggregate version,
runtime generation, live lease and a valid remote message identity. Known rejection can become
retryable after its delay; unknown results and expired claims are quarantined. Supersession keeps
an unsent generic operation and its original revision but never erases an already sent receipt.

These state transitions are not yet wired to HTTP or the worker. The complete issue scope and
all remaining delivery/reconciliation/restore/concurrency/UI/review/verification gates remain open.

## HTTP dispatch and worker checkpoint

The production-shaped HTTP contract first produced nineteen intended assertion failures and
zero errors/skips, including the existing retry interceptor. Its corrected run passed thirty
unit and nine rate-limit integration cases. The bot sends fixed neutral text with empty parsed
mentions and a stable nonce no longer than 25 characters, with enforce_nonce enabled. It verifies
the captured guild/channel and the returned message identity. Pure rate-limit rejection stays
retryable; an earlier unknown server result remains uncertain even after a later rate-limit or
permission rejection. No raw upstream body or exception becomes a persisted failure code.
[Discord's message API](https://docs.discord.com/developers/resources/message#create-message)
limits nonce deduplication to recent messages, so persisted unknown outcomes never retry blindly.

The initial dispatch integration contract produced seventeen intended assertion failures without
errors/skips. The complete corrected check passed forty-four unit and twenty-six H2/MariaDB
integration cases. SQL locks are released before HTTP. One writer admission spans claim commit,
external send and outcome commit, including all failures; restore waits for that complete operation.
The current window and operation are refreshed under their existing locking order. Restart scans
use persisted pending/retryable work; abandoned claims become uncertain. Detailed disclosure jobs
are excluded from neutral dispatch, and a pending generic receipt is superseded only after its
corresponding disclosure is confirmed sent. Deadline/closure races freeze the accepted revision.

The scheduled worker is off unless app.discord.incidents.delivery-enabled is explicitly true.
It skips recovery-check/outbound-disabled policy and active restore, requests batches of ten due
windows and receipts, and prevents overlapping ticks. Its bounded claim lease is ten minutes;
the existing HTTP timeout and retry settings are unchanged. Final operator recovery controls,
management status presentation, full archive state/restore witnesses, review and delivery gates
remain open for this same issue.
