# Private picks Gateway and channel access verification

Issue #282, parent #279. Delivered #281 supplies the actor/target contracts, private model and
writer admission. Live selection found no competing PR, active related branch, other checkout,
assignee or inherited milestone. The shipped GSD milestone imposes no active release boundary.
Owner-managed deployment #301 and its host boot remain excluded.

## Scope

Deliver Gateway transport, exact private team-channel bindings, current channel/target access
checks and the internal binding/health controls. Dialog stages belong to #283, management review
to #284, disclosure to #285 and operational rollout to #286. Collection remains unavailable until
the existing confidentiality readiness gate is complete. Tests use fake transport and permission
responses; real bot registration, messages and deployment activation are operator actions.

## Requirements matrix

| Requirement | Production path | Positive witness | Failure witness | Runtime proof |
| --- | --- | --- | --- | --- |
| Disabled defaults and one consumer | Gateway properties/lifecycle and SDK adapter | Explicit ready opt-in starts one session; shutdown closes it | Disabled, missing token/guild or unfinished privacy starts no session | Fake-session unit tests and default Spring startup |
| Maintained outgoing transport | JDA adapter and existing Discord identity | SDK reconnect/resume, bounded queues and shutdown | No hand-written protocol, message-content intent or incoming endpoint | Adapter tests, Java 25 dependency build, source/route audit |
| Exact management-owned binding | Binding entity/repository/service and native form DTO | Exact season/team/guild/channel persists | Duplicate/ambiguous binding, foreign IDs and shared match channel reject | H2/MariaDB constraints and native form/controller tests |
| Channel access without a selected race | PickAccessService and fresh Discord facts | Any current private-channel member receives its configured scopes | DMs, other guilds, lookup failure, revoked view and role-only inference deny | Recorded member/role/overwrite fixtures |
| Exact selected-target access | Target authorization and domain actor recheck | Exact managed home target in a verified scope | Wrong race/home/season or stale local binding deny | Real transaction proxy and permission-revocation IT |
| Private interaction responses | Transport-neutral handler/response adapter | Ephemeral defer before queued work; private completion | Saturated queue, exceptions and expired continuation never post publicly | Deterministic clock, fake callbacks and deadline tests |
| Modal acknowledgement support | Immediate response adapter contract | A modal opens through an immediate supported response | No defer-then-modal chain or expired acknowledgement | Adapter unit fixtures |
| Explicit command registration | Operator-only registration operation | Upsert this feature's configured guild command | No startup registration or replacement of unrelated commands | Local fake registration API |
| Restore exclusion and session lifetime | Reused PickWriteExecutor admission and restore listener | Admitted writes cover commit/rollback; restored sessions invalidate | Restore-held writes and failed commits yield no saved receipt | GatewayRestoreAdmissionIT with actual service proxies |
| Complete binding recovery | New migration, backup schema/MixIn/restorer/preflight | IDs and exact bindings survive H2/MariaDB restore | Broken references, duplicate scopes or malformed IDs reject before wipe | Full round trip and legacy archive tests |
| Internal setup and health | Existing admin security, native forms, service view models | Management configures bindings and sees consumer health | Missing/invalid input retains values; UI causes no real Discord mutation | Controller/E2E checks and desktop/mobile Playwright |
| Delivery | Documentation, review, guard probes and repository gates | Review findings fixed; exact-head full verification and CI green | No activation before privacy acceptance | Verified merge, hierarchy readback and own-branch cleanup |

## Checklist

- [x] Recheck live issues, dependencies, hierarchy, milestone and parallel occupancy.
- [x] Create the behavior branch and record requirements before production changes.
- [ ] Write and observe behavioral assertion-red Gateway and access tests.
- [ ] Implement bounded transport and exact current permission checks.
- [ ] Persist bindings with matching migration, backup and audit support.
- [ ] Prove real transaction restore exclusion and permission revocation.
- [ ] Add native binding controls and inspect desktop/mobile behavior.
- [ ] Update README, CLAUDE, glossary and Wiki draft.
- [ ] Complete code/security review and individual guard falsification.
- [ ] Publish a reviewable PR, pass final committed-head clean verify and matching CI.
- [ ] Confirm merge, reconcile the full hierarchy/Wiki and clean the topic branch.

## Implementation references

JDA 6.7.0 is the current stable release, verified against the upstream release API on 2026-11-02.
It supports the upcoming channel-obfuscation changes. Fresh authorization must deny when current
channel/member/overwrite facts are unavailable; stale role caches never grant access. Keep the
existing bot identity and REST publishing clients, exclude unused voice/audio dependencies and
pin the Gateway library version.

- https://github.com/discord-jda/JDA/releases/tag/v6.7.0
- https://docs.jda.wiki/net/dv8tion/jda/api/JDABuilder.html
- https://docs.jda.wiki/net/dv8tion/jda/api/interactions/callbacks/IReplyCallback.html
- https://docs.discord.com/developers/interactions/receiving-and-responding
- https://docs.discord.com/developers/topics/permissions

Authorization remains separate from editability: an authorized member can recover an original
receipt or inspect own history after results, while the existing target/deadline policies still
reject new confirmations. Initial channel access reconstructs scopes from current bindings;
client-supplied or stale actor scopes never grant access.

## Focused evidence

- Access-service red: 20 tests, four intended assertion failures, zero errors. Covers initial
  channel scope without a race, fresh target ownership, stale/forged scope rejection, shared
  match channels, lookup failure and access to own historical receipt data.
- Fresh Discord permission red: 22 tests, seven intended assertion failures, zero errors.
  Covers role/member overwrites, administrator/owner access, public-channel refusal, failed or
  incomplete lookups, parent-role-only refusal and permission revocation between interactions.
- Both classes pass together: 42 tests, zero failures/errors. Permission arithmetic uses
  BigInteger as required by Discord's variable-length permission representation and performs
  fresh member/channel/guild/role reads instead of granting from metadata caches.

The first code checkpoint provides the independently testable access policies and raw REST facts.
The binding model/repository and access classes are deliberately not registered with JPA/Spring
until migration and backup support are added together. No Gateway session or collection path is
activated by this checkpoint.
