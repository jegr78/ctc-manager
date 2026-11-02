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
- [x] Write and observe behavioral assertion-red Gateway and access tests.
- [x] Implement bounded transport and exact current permission checks.
- [x] Persist bindings with matching migration, backup and audit support.
- [x] Prove real transaction restore exclusion and permission revocation.
- [x] Add native binding controls and inspect desktop/mobile behavior.
- [x] Update README, CLAUDE, glossary and Wiki draft.
- [x] Complete code/security review and individual guard falsification.
The remaining delivery checklist lives on [PR #324](https://github.com/jegr78/ctc-manager/pull/324):
committed-head canonical verification, matching CI, confirmed merge, full tracker/Wiki reconciliation
and own-branch cleanup. Their measured results and readbacks are recorded there.

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

- Gateway lifecycle/private-response/dispatcher contracts: intended assertion-red runs with zero
  errors, then green together. The dispatcher uses bounded Spring workers and only routes picks
  interactions from the configured guild. Restore-held requests and queue saturation stay private.
- SDK response adapter: five intended assertion failures, zero errors, then green. JDA callbacks
  explicitly start ephemeral, suppress mentions and never fall back to ordinary channel messages.
  Modal text and string-select components use immediate supported replies.
- SDK transport: nine intended assertion failures, zero errors, then green. Uses JDA 6.7.0 light
  configuration without message-content intent, automatic reconnect, owned-session shutdown and
  the existing outbound policy. Recorded command/button/select/modal fixtures never read raw tokens.
- Combined focused checkpoint: 86 tests, zero failures/errors, on Java 25. These are intermediate
  unit witnesses, not the final clean-verify or merge evidence. Spring binding/configuration wiring,
  durable binding recovery, real-proxy admission/revocation and the native setup UI remain pending.


## Persistence and transaction integration

- Binding preflight: 14 intended assertion failures, zero errors, then green. Current schema is 6
  with 34 exported entities. Schema versions 1 through 5 may omit bindings and gain no channel grant.
- H2 migration constraints: seven intended assertion failures, zero errors, then seven green.
  The same seven cases pass against isolated MariaDB. Exact scopes persist; duplicate team/channel
  grants, missing references and malformed Discord IDs fail at the database boundary.
- Focused storage run: 52 unit and 30 integration tests pass. Expanded recovery/audit run: 14 unit
  and 51 integration tests pass, including complete H2/MariaDB history/binding round trips, schema-5
  private history without bindings, legacy schemas 1–5 and read-only shared-channel conflict audit.
- Confirmation/read recheck: seven intended assertion failures, zero errors, then 46 unit tests
  green. Gateway actor provenance retains exact channel identity and the current restore generation.
  The real write path takes season/target locks before current binding and permission rechecks.
- GatewayRestoreAdmissionIT passes ten real-proxy integration cases. Permission/binding/guild changes
  and stale generations reject previously selected actors. An admitted write blocks restore drain
  through commit; later interactions refuse privately. SQL flushed before a forced commit failure
  rolls back without a saved receipt. Shutdown interrupts active work and releases admission.
- Actual backup imports increment the generation after commit; rejected or rolled-back imports
  preserve it. Tests use the existing transaction-owning writer helper and never add an outer
  transaction around Gateway submission.

## Native management interface

- Administration and single-command registration: 18 intended assertion failures, zero errors,
  then 18 green. Controller behavior: eight intended assertion failures, zero errors, then green.
  A test generic-type compilation error was diagnosed through clean test-compile and corrected
  before the behavioral red run; it is not counted as intended red evidence.
- Full focused composition run: 152 unit, 20 integration and three Playwright tests pass.
  Productive authentication and CSRF protect all mutation routes. The real management service
  refuses registration before confidentiality acceptance. View/health reads do not heal configuration
  or perform external calls. Saving checks current bot access without channel creation or posting.
- Desktop 1440×1000 and mobile 390×844 save/remove flows work with JavaScript disabled. Failure
  retains season, team and channel inputs. Registration remains disabled. Screenshots under ignored
  `.screenshots/private-picks-{desktop,mobile}.png` were inspected; only the local test server and
  mocked Discord facts were used.
- Upstream advanced only through automatic release/version commits d11f0f31 and 9bf34039. Final
  verification will use the rebased committed head against that current master.

These focused runs preceded final review and guard falsification. The delivery gate and actual
merge evidence are recorded on PR #324.

- Final focused additions: 26 unit, ten MariaDB real-proxy admission tests and three browser
  tests pass. Desktop/mobile screenshots were refreshed after keeping channel/guild IDs on one
  line inside the existing horizontal table region. Four worker/queue-bound cases are covered.
- Self-review found an SDK-owned shutdown retry bug: the session was marked closed before an
  SDK shutdown exception could be retried. One intended assertion failure, zero errors, proved
  the case; the closed flag now advances after a successful owned shutdown attempt. No new issue
  was needed for this correction in the current adapter.


## Final review and guard evidence

The complete independent security pass reviewed `9bf34039..589053ad` and reported no findings.
Subsequent changes strengthen test fixtures and record evidence. The only later production delta
is the finalization hardening described below; callback and authorization guard bodies are unchanged. The SDK shutdown-retry correction was already included
in the reviewed code. Later fixture checks passed 87 unit tests, and isolated H2 constraints passed
seven integration cases.

All 72 guards below were broken individually from a correct committed checkpoint. Each qualified
run reached the intended assertion failure with zero JUnit errors, then restored the exact committed
source bytes. One initial SDK-intent probe hit Mockito's static-verification wrapper and was excluded;
a captured-argument assertion fixed that test harness and the repeated probe qualified normally.

Body-only probes used the Maven `test`/`integration-test` lifecycle with changed-source compilation
via `maven.compiler.useIncrementalCompilation=false`. No signatures or dependency contracts changed
inside those probes. Logs confirmed the changed file was compiled and the selected assertion ran.
The final `clean verify -Pe2e -Ddocker.available=true` uses default compiler behavior and the complete
repository gates. No test, coverage, lint or security gate was skipped or weakened.

| Guard | Assertion witness | Failures |
| --- | --- | ---: |
| startup-disabled | [DiscordPicksGatewayTest](../../src/test/java/org/ctc/discord/picks/DiscordPicksGatewayTest.java), `givenDefaultSettings*` | 1 |
| startup-privacy | [DiscordPicksGatewayTest](../../src/test/java/org/ctc/discord/picks/DiscordPicksGatewayTest.java), `givenExplicitOptIn*` | 1 |
| startup-identity | [DiscordPicksGatewayTest](../../src/test/java/org/ctc/discord/picks/DiscordPicksGatewayTest.java), `givenIncompleteIdentity*` | 1 |
| single-consumer | [DiscordPicksGatewayTest](../../src/test/java/org/ctc/discord/picks/DiscordPicksGatewayTest.java), `givenReadyOptIn*` | 1 |
| guild-scope | [PickGatewayDispatcherTest](../../src/test/java/org/ctc/discord/picks/PickGatewayDispatcherTest.java), `givenOtherGuild*` | 2 |
| feature-routing | [PickGatewayDispatcherTest](../../src/test/java/org/ctc/discord/picks/PickGatewayDispatcherTest.java), `givenUnrelatedCommand*` | 1 |
| restore-refusal | [PickGatewayDispatcherTest](../../src/test/java/org/ctc/discord/picks/PickGatewayDispatcherTest.java), `givenRestoreHolding*` | 1 |
| stopped-refusal | [PickGatewayDispatcherTest](../../src/test/java/org/ctc/discord/picks/PickGatewayDispatcherTest.java), `givenStoppedDispatcher*` | 1 |
| dispatch-expiry | [PickGatewayDispatcherTest](../../src/test/java/org/ctc/discord/picks/PickGatewayDispatcherTest.java), `givenExpiredInitial*` | 1 |
| resource-bounds | [PickGatewayDispatcherTest](../../src/test/java/org/ctc/discord/picks/PickGatewayDispatcherTest.java), `givenInvalidResource*` | 3 |
| initial-window | [PickGatewayReplyTest](../../src/test/java/org/ctc/discord/picks/PickGatewayReplyTest.java), `givenExactlyExpired*` | 1 |
| single-ack | [PickGatewayReplyTest](../../src/test/java/org/ctc/discord/picks/PickGatewayReplyTest.java), `givenAlreadyDeferred*` | 1 |
| continuation-expiry | [PickGatewayReplyTest](../../src/test/java/org/ctc/discord/picks/PickGatewayReplyTest.java), `givenAcknowledgedInteraction*` | 1 |
| generation-invalidation | [PickGatewayReplyTest](../../src/test/java/org/ctc/discord/picks/PickGatewayReplyTest.java), `givenRestoreBeforeAcknowledgement*` | 1 |
| old-payload-masking | [PickGatewayReplyTest](../../src/test/java/org/ctc/discord/picks/PickGatewayReplyTest.java), `givenRestoreAfterWork*` | 1 |
| ack-before-work | [PickGatewayReplyTest](../../src/test/java/org/ctc/discord/picks/PickGatewayReplyTest.java), `givenDeferredReply*` | 1 |
| queue-busy-private | [PickGatewayReplyTest](../../src/test/java/org/ctc/discord/picks/PickGatewayReplyTest.java), `givenFullWorkQueue*` | 1 |
| generic-failure | [PickGatewayReplyTest](../../src/test/java/org/ctc/discord/picks/PickGatewayReplyTest.java), `givenBusinessFailure*` | 1 |
| ephemeral-defer | [JdaPickGatewayResponseTest](../../src/test/java/org/ctc/discord/picks/JdaPickGatewayResponseTest.java), `givenDeferredReply*` | 1 |
| ephemeral-initial | [JdaPickGatewayResponseTest](../../src/test/java/org/ctc/discord/picks/JdaPickGatewayResponseTest.java), `givenImmediatePrivateError*` | 1 |
| ephemeral-followup | [JdaPickGatewayResponseTest](../../src/test/java/org/ctc/discord/picks/JdaPickGatewayResponseTest.java), `givenDeferredReply*` | 1 |
| mention-free-followup | [JdaPickGatewayResponseTest](../../src/test/java/org/ctc/discord/picks/JdaPickGatewayResponseTest.java), `givenDeferredReply*` | 1 |
| mention-free-initial | [JdaPickGatewayResponseTest](../../src/test/java/org/ctc/discord/picks/JdaPickGatewayResponseTest.java), `givenImmediatePrivateError*` | 1 |
| no-message-content | [JdaPickGatewayTransportTest](../../src/test/java/org/ctc/discord/picks/JdaPickGatewayTransportTest.java), `givenOutgoingSession*` | 1 |
| sdk-reconnect | [JdaPickGatewayTransportTest](../../src/test/java/org/ctc/discord/picks/JdaPickGatewayTransportTest.java), `givenOutgoingSession*` | 1 |
| owned-shutdown | [JdaPickGatewayTransportTest](../../src/test/java/org/ctc/discord/picks/JdaPickGatewayTransportTest.java), `givenOutgoingSession*` | 1 |
| shutdown-retry | [JdaPickGatewayTransportTest](../../src/test/java/org/ctc/discord/picks/JdaPickGatewayTransportTest.java), `givenSdkShutdownFailure*` | 1 |
| channel-id-syntax | [PickAccessServiceTest](../../src/test/java/org/ctc/domain/service/PickAccessServiceTest.java), `givenUnauthorizedChannel*` | 2 |
| configured-guild | [PickAccessServiceTest](../../src/test/java/org/ctc/domain/service/PickAccessServiceTest.java), `givenUnauthorizedChannel*` | 1 |
| shared-match-channel | [PickAccessServiceTest](../../src/test/java/org/ctc/domain/service/PickAccessServiceTest.java), `givenUnauthorizedChannel*` | 1 |
| unambiguous-scope | [PickAccessServiceTest](../../src/test/java/org/ctc/domain/service/PickAccessServiceTest.java), `givenUnauthorizedChannel*` | 1 |
| current-private-access | [PickAccessServiceTest](../../src/test/java/org/ctc/domain/service/PickAccessServiceTest.java), `givenUnauthorizedChannel*` | 1 |
| managed-target | [PickAccessServiceTest](../../src/test/java/org/ctc/domain/service/PickAccessServiceTest.java), `givenStaleOrForeignTarget*` | 1 |
| target-exact-scope | [PickAccessServiceTest](../../src/test/java/org/ctc/domain/service/PickAccessServiceTest.java), `givenStaleOrForeignTarget*` | 2 |
| original-target-identity | [PickAccessServiceTest](../../src/test/java/org/ctc/domain/service/PickAccessServiceTest.java), `givenStaleOrForeignTarget*` | 1 |
| rebuild-forged-scope | [PickAccessServiceTest](../../src/test/java/org/ctc/domain/service/PickAccessServiceTest.java), `givenForgedTeamScope*` | 1 |
| write-generation | [PickAccessServiceTest](../../src/test/java/org/ctc/domain/service/PickAccessServiceTest.java), `givenRevocationAfterTarget*` | 1 |
| current-binding-at-write | [PickAccessServiceTest](../../src/test/java/org/ctc/domain/service/PickAccessServiceTest.java), `givenRevocationAfterTarget*` | 1 |
| chooser-current-authority | [PickTargetServiceTest](../../src/test/java/org/ctc/domain/service/PickTargetServiceTest.java), `givenRevokedChannelActor*` | 1 |
| under-lock-recheck | [PickTargetServiceTest](../../src/test/java/org/ctc/domain/service/PickTargetServiceTest.java), `givenChannelBoundActor*` | 1 |
| fresh-facts-guild | [DiscordPickChannelPermissionSourceTest](../../src/test/java/org/ctc/discord/picks/DiscordPickChannelPermissionSourceTest.java), `givenUnavailableOrUnauthorized*` | 1 |
| fresh-facts-channel-guild | [DiscordPickChannelPermissionSourceTest](../../src/test/java/org/ctc/discord/picks/DiscordPickChannelPermissionSourceTest.java), `givenUnavailableOrUnauthorized*` | 1 |
| fresh-facts-channel-id | [DiscordPickChannelPermissionSourceTest](../../src/test/java/org/ctc/discord/picks/DiscordPickChannelPermissionSourceTest.java), `givenUnavailableOrUnauthorized*` | 1 |
| fresh-facts-member-id | [DiscordPickChannelPermissionSourceTest](../../src/test/java/org/ctc/discord/picks/DiscordPickChannelPermissionSourceTest.java), `givenUnavailableOrUnauthorized*` | 1 |
| guild-text-channel | [DiscordPickChannelPermissionSourceTest](../../src/test/java/org/ctc/discord/picks/DiscordPickChannelPermissionSourceTest.java), `givenUnavailableOrUnauthorized*` | 1 |
| private-everyone | [DiscordPickChannelPermissionSourceTest](../../src/test/java/org/ctc/discord/picks/DiscordPickChannelPermissionSourceTest.java), `givenUnavailableOrUnauthorized*` | 1 |
| role-overwrite-deny | [DiscordPickChannelPermissionSourceTest](../../src/test/java/org/ctc/discord/picks/DiscordPickChannelPermissionSourceTest.java), `givenUnavailableOrUnauthorized*` | 1 |
| member-overwrite-deny | [DiscordPickChannelPermissionSourceTest](../../src/test/java/org/ctc/discord/picks/DiscordPickChannelPermissionSourceTest.java), `givenUnavailableOrUnauthorized*` | 1 |
| permission-lookup-fail-closed | [DiscordPickChannelPermissionSourceTest](../../src/test/java/org/ctc/discord/picks/DiscordPickChannelPermissionSourceTest.java), `givenUnavailableOrUnauthorized*` | 2 |
| binding-legacy-schema | [TeamPickBindingBackupValidatorTest](../../src/test/java/org/ctc/backup/schema/TeamPickBindingBackupValidatorTest.java), `givenLegacySchema*` | 5 |
| binding-season-reference | [TeamPickBindingBackupValidatorTest](../../src/test/java/org/ctc/backup/schema/TeamPickBindingBackupValidatorTest.java), `givenBrokenBinding*` | 1 |
| binding-team-reference | [TeamPickBindingBackupValidatorTest](../../src/test/java/org/ctc/backup/schema/TeamPickBindingBackupValidatorTest.java), `givenBrokenBinding*` | 1 |
| binding-numeric-guild | [TeamPickBindingBackupValidatorTest](../../src/test/java/org/ctc/backup/schema/TeamPickBindingBackupValidatorTest.java), `givenBrokenBinding*` | 1 |
| binding-numeric-channel | [TeamPickBindingBackupValidatorTest](../../src/test/java/org/ctc/backup/schema/TeamPickBindingBackupValidatorTest.java), `givenBrokenBinding*` | 1 |
| binding-team-unique | [TeamPickBindingBackupValidatorTest](../../src/test/java/org/ctc/backup/schema/TeamPickBindingBackupValidatorTest.java), `givenBrokenBinding*` | 1 |
| binding-channel-unique | [TeamPickBindingBackupValidatorTest](../../src/test/java/org/ctc/backup/schema/TeamPickBindingBackupValidatorTest.java), `givenBrokenBinding*` | 1 |
| binding-shared-channel | [TeamPickBindingBackupValidatorTest](../../src/test/java/org/ctc/backup/schema/TeamPickBindingBackupValidatorTest.java), `givenBrokenBinding*` | 1 |
| admin-binding-input | [PrivatePicksAdminServiceTest](../../src/test/java/org/ctc/admin/service/PrivatePicksAdminServiceTest.java), `givenInvalidOrUnsafeBinding*` | 1 |
| admin-shared-channel | [PrivatePicksAdminServiceTest](../../src/test/java/org/ctc/admin/service/PrivatePicksAdminServiceTest.java), `givenInvalidOrUnsafeBinding*` | 1 |
| admin-channel-conflict | [PrivatePicksAdminServiceTest](../../src/test/java/org/ctc/admin/service/PrivatePicksAdminServiceTest.java), `givenInvalidOrUnsafeBinding*` | 1 |
| admin-current-private-access | [PrivatePicksAdminServiceTest](../../src/test/java/org/ctc/admin/service/PrivatePicksAdminServiceTest.java), `givenInvalidOrUnsafeBinding*` | 1 |
| admin-season-write-lock | [PrivatePicksAdminServiceTest](../../src/test/java/org/ctc/admin/service/PrivatePicksAdminServiceTest.java), `givenValidPrivateChannel*` | 1 |
| registration-privacy | [PrivatePicksAdminServiceTest](../../src/test/java/org/ctc/admin/service/PrivatePicksAdminServiceTest.java), `givenUnavailableRegistration*` | 1 |
| registration-identity | [PrivatePicksAdminServiceTest](../../src/test/java/org/ctc/admin/service/PrivatePicksAdminServiceTest.java), `givenUnavailableRegistration*` | 2 |
| committed-restore-epoch | [PickBackupRoundTripIT](../../src/test/java/org/ctc/backup/service/PickBackupRoundTripIT.java), `givenApprovedCorrection*` | 1 |
| db-team-unique | [TeamPickChannelBindingIT](../../src/test/java/org/ctc/domain/service/TeamPickChannelBindingIT.java), `givenConflictingOrMalformedScope*` | 1 |
| db-channel-unique | [TeamPickChannelBindingIT](../../src/test/java/org/ctc/domain/service/TeamPickChannelBindingIT.java), `givenConflictingOrMalformedScope*` | 1 |
| db-season-fk | [TeamPickChannelBindingIT](../../src/test/java/org/ctc/domain/service/TeamPickChannelBindingIT.java), `givenConflictingOrMalformedScope*` | 1 |
| db-team-fk | [TeamPickChannelBindingIT](../../src/test/java/org/ctc/domain/service/TeamPickChannelBindingIT.java), `givenConflictingOrMalformedScope*` | 1 |
| db-guild-syntax | [TeamPickChannelBindingIT](../../src/test/java/org/ctc/domain/service/TeamPickChannelBindingIT.java), `givenConflictingOrMalformedScope*` | 1 |
| db-channel-syntax | [TeamPickChannelBindingIT](../../src/test/java/org/ctc/domain/service/TeamPickChannelBindingIT.java), `givenConflictingOrMalformedScope*` | 1 |

| production-json-owner-id | [DiscordPickAccessRestContractTest](../../src/test/java/org/ctc/discord/DiscordPickAccessRestContractTest.java), `givenProductionDiscordJson*` | 1 |

The production HTTP contract also checks all four exact Discord GET paths, bot authorization and
literal guild/channel/member/role JSON, then repeats the complete fetch after permission revocation.
It passes with the real REST client and permission decoder against a local fake HTTP transport.
An incorrect `owner_id` mapping produces the intended assertion failure with zero errors. The first
canonical run was stopped to add this required wire-format witness; it is not counted as completed
verification. That HTTP contract addition changes tests only; the later finalization hardening is recorded below.

Canonical command for the final committed head:

```bash
./mvnw clean verify -Pe2e -Ddocker.available=true
```

The matching-head unit/integration/E2E totals, JaCoCo ratio, Checkstyle, SpotBugs/find-sec-bugs,
CodeQL, CI and merge readbacks are maintained in the PR delivery checklist.


## Static-analysis correction

The first complete canonical run on `9df40003` passed 2,606 unit, 1,030 integration and 219 browser
tests with zero failures/errors and the three existing unit skips. Its final SpotBugs check then
reported `CT_CONSTRUCTOR_THROW`: the reply wrapper's validating constructor belonged to an
inheritable class. That run is recorded as failed, not as final verification.

`PickGatewayReply` is now final. It has no subclasses and is constructed directly rather than
proxied by Spring. This prevents finalizer-based subclass access to a partly initialized wrapper.
No detector, threshold or suppression was changed. A focused lifecycle verification passed the
reply/admission tests, reported zero SpotBugs findings and confirmed the find-sec-bugs pack active.
The existing 72 guard bodies remain unchanged. The final hardening review and repeated clean
canonical result are recorded on PR #324 for the corrected committed head.
