# Linked incident thread publication

Issue #291 follows delivered #290 / PR #333. Selection base:
`c1d121d9879afdc2cd20f46ff4df65d15f4add1e`. Parent #287 and this child have no milestone.
The latest local roadmap, v1.17, is shipped; the current tracker supplies the dependency order.
No open PR, corresponding remote branch, sibling Claude checkout or additional worktree collides.
The assigned dependency dashboard is excluded. #292 owns rulings/case-history expansion and #293
owns complete rollout acceptance. Runtime activation, real reports and live Discord calls remain
operator work; verification uses synthetic fixtures and a local fake Discord server.

## Workflow checklist

- [x] Recheck tracker, dependencies, release order and parallel work.
- [x] Map acceptance criteria to production paths and meaningful failure witnesses.
- [x] Observe failing projection, privacy and production-shaped HTTP contracts before implementation.
- [x] Implement only the missing thread APIs and current destination/permission preflight.
- [x] Persist and recover each successful publication step, with safe unknown-outcome handling.
- [ ] Expose typed management recovery/status and preserve exact archive/config references.
- [ ] Falsify new protections individually; complete self-review and independent security review.
- [ ] Run focused `verify` including static gates before the final expensive clean full run.
- [ ] Verify the final committed head locally and in CI; merge and reconcile hierarchy/Wiki/branches.

## Requirements matrix

| Acceptance | Production path | Positive witness | Failure and runtime witness |
| --- | --- | --- | --- |
| Frozen active disclosure only | IncidentThreadPublicationService + existing DISCLOSURE operation/revision | Closed active report renders its captured final revision | Open window, withdrawn report, receipt operation or foreign revision cannot publish; real H2/MariaDB |
| Public reporter privacy | Controlled immutable public projection | Reporting party and complete four source fields, permanent case marker | Distinct author ID/name/avatar/private destination markers absent from actual public HTTP payload |
| Text and forum API shapes | Existing DiscordRestClient / focused incident adapter | New detailed starter, thread from its known message; forum starter with required tags | Never use early receipt as starter; wrong type/tag, malformed response and Unicode bounds; real-client WireMock IT |
| Private parent confidentiality | Current guild/channel/role/overwrite preflight | Configured staff-only text/forum parent and bot rights | Everyone/team access, wrong guild/type and permission changes reject before every detailed post; no fallback |
| Durable linked steps | Publication service + permanent case/delivery mapping | Persist private thread, public thread and private-to-public reference separately | Partial success survives restart, later SQL failure cannot erase prior IDs; unknown sends never blindly recreate |
| Exact recovery/adoption | Bounded current-target thread/message reads | Verify known starter/thread identity or unique stable incident marker, including archived records | Foreign markers/targets, deleted/ambiguous results and stale versions remain unresolved; actual HTTP and DB tests |
| Restore/concurrency | Existing ImportLockService, epoch and paused window protocol | One admitted claim/send/ack, mutually exclusive worker claims | Restore waits, stale generations fail, every error path releases admission; H2/MariaDB witnesses |
| Native management | Typed forms and trusted management principal | Separate public-delivery status and verified adoption/proven-failure retry | Auth/CSRF, missing/stale version/generation, unchecked review and forged entity/actor fields cannot mutate |
| Permanent backup/audit | New migration, serializers/restorers/preflight + read-only audit | Exact destination/config, step outcomes and both thread IDs round-trip | Foreign ownership/targets rejected before wipe; old archives invent no publication; restored sends stay paused |

The existing receipt remains a distinct historical operation. Detailed match messages never link
back to the private stewards thread. The application retains complete original facts internally;
public splitting preserves readable complete facts rather than silently dropping text. Evidence
URLs remain data and are never fetched by the server.

## Existing contracts and API references

Reuse the current RestClient/error/rate-limit infrastructure and the existing active/archived
thread listing and unarchive behavior. Current AccessChannel/member helpers serve type-0 intake;
keep their callers stable while adding only the publication capabilities required here. Current
DiscordGlobalConfig has no stewards destination or staff-role fields, so configuration needs an
explicit typed extension with matching archive support. WireMock is already a test dependency.

Primary references: [Discord channels](https://docs.discord.com/developers/resources/channel),
[permissions](https://docs.discord.com/developers/topics/permissions) and
[messages](https://docs.discord.com/developers/resources/message). Verify the exact endpoint,
permission, tag and pagination contracts before wiring the adapter. Do not infer a destination
from a channel name or assume that thread type alone proves confidentiality.


## Frozen projection checkpoint

The first complete projection contract produced three intended assertion failures across eight
cases, with zero errors/skips. Its implementation then passed all thirteen selected unit cases,
including the existing notice/disclosure contracts. The projection reads the immutable delivery
revision and captured party label, keeps all four facts across ordered bounded chunks, preserves
Unicode pairs, normalizes malformed Unicode and escapes the display label. Open, withdrawn,
unfrozen, mismatched-frozen and receipt operations cannot produce a detailed payload. Personal
author metadata is never added. Transport-specific stable step markers will reserve the remaining
message space; this checkpoint does not create messages or threads.


## Real-client HTTP checkpoint

Seven intended WireMock assertion failures, zero errors/skips, preceded the missing thread APIs.
The corrected managed RestClient check passed 35 selected unit and 17 integration cases, including
the existing rate-limit suite. Text publication creates a new detailed starter and starts its
thread from that known ID. Forum creation sends a nested message and selected tags, retains the
returned starter/thread identities independently and does not invent unsupported nonce fields.
Current channel/tag metadata and bounded archived-page cursor information are available without
changing the legacy listing methods.

The official forum message parameter list has no nonce/enforce_nonce contract. Both new creation
calls therefore use an internal request attribute to stop automatic replay after an ambiguous
5xx. A confirmed 429 remains safely retryable. WireMock proves one POST on server failure or
request timeout, safe retry after pure rate-limit rejection and the unchanged retry behavior of
existing nonce-backed messages. This checkpoint does not yet authorize or dispatch a real case;
current private-parent permissions, durable step mapping and management recovery remain in scope.


## Current destination policy checkpoint

Four intended positive-path assertion failures preceded the pure destination policy. Its 42 cases
then passed, alongside eight projection cases. A separate intended HTTP assertion failure
preceded the bot-managed role metadata read; the corrected real-client suite passed all nine
cases, with all nine existing rate-limit cases also green. Privacy uses actual current role and
member overwrites. Only this bot's own integration-managed role is exempt from the configured
staff allowlist; a shared administrator role stays broad even if the bot also possesses it.
Wrong guild/type/identity, team/everyone/member visibility, missing bot rights, malformed or
duplicate permission records and unverified/required/moderated forum tags fail closed.
The policy remains pure; the publication sender must invoke it again before every detailed post.


## Durable dispatch and native recovery checkpoint

Twelve intended assertion failures preceded the mapping/configuration behavior; the corrected
selection passed 93 unit cases. Five intended database assertion failures preceded real dispatch,
and a separate intended worker assertion failure preceded its disclosure queue. The focused
selection then passed 98 unit cases. The H2 runtime check passed eight dispatch, fourteen native
recovery and five failure/concurrency cases, all with zero failures/errors/skips. The subsequent
transport correction passed 53 selected unit and 26 managed HTTP cases. These are focused
checkpoints, not the final canonical verification.

The existing DISCLOSURE row owns each claim/lease/version and one permanent publication mapping.
Each successful starter, bound thread, continuation and private reference commits separately.
HTTP runs outside SQL while one restore writer admission spans claim through outcome commit.
Remote success followed by SQL failure preserves prior IDs and leaves the current step claimed,
then uncertain after expiry. Two actual workers cannot send the same claimed step. Restore waits
for admitted HTTP and its outcome transaction; sender exceptions, lease expiry and generation
changes retain uncertainty and release admission. A successful public projection supersedes
an unsent neutral receipt even while the private reference is still pending.

Native recovery verifies every already recorded remote step before adopting the current result
or allowing its proven absence. Trusted principals, explicit confirmation, reason and current
version/generation remain mandatory. Generic receipt recovery cannot reset disclosure state.
The restored context's existing resume timestamp and per-delivery operational review grant
gate each later disclosure step separately; a successful step consumes that grant. This also
covers remote work that may have happened after the restored backup.

The real-client suite covers current privacy changes between posts and while unarchiving.
A separate failing assertion exposed the latter gap; repeating preflight after reopening fixed
it. Known archived threads can be adopted without posting, deleted threads are not replaced,
and ambiguous forum creation remains uncertain without a blind public fallback.

New V31 DDL and schema-10 serializer/restorer wiring are present. Strict publication/configuration
archive preflight, backup round-trip/audit witnesses and bounded forum absence reconciliation
remain to be completed before final review and release.
