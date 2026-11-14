# Discord picks acceptance and rollout

> Superseded in part by #425: V44/schema 22 removes the season and race activation flags, the activation
> review, the confidentiality readiness gate and new management corrections. Approval no longer locks the team,
> and publication sends the stored setup. The evidence below describes the state before that change.

Issue #286 completes the acceptance contract of #279. The baseline is the merged publication
feature in #329. Automated checks use synthetic data and offline Discord transports. Live guild
registration, messages and production activation remain separate operator actions.

## Delivery checklist

- [x] Verify current issues, prerequisites, workspaces and parallel branches.
- [x] Audit the current schema, restore statements and existing acceptance witnesses.
- [x] Exercise the complete private workflow through approval and explicit publication.
- [x] Verify restore, zero/Equal Conditions, rejected archives and read-only audit findings.
- [x] Complete capability readiness while retaining default-off Gateway and season opt-in.
- [x] Document the operator rollout checklist and prepare the Wiki update.
- [x] Complete focused tests, guard witnesses and independent security review.
- [ ] Run committed-head canonical verification and all GitHub checks, then merge.
- [ ] Reconcile #286 and #279, publish the Wiki update and remove the landed branch.

## Requirements matrix

Existing witnesses are reused and extended where the final contract needs a combined case.
The existing backup and Gateway restore fixtures become the requested
`DiscordPicksBackupRoundTripIT` and `DiscordPicksRestoreAdmissionIT` suites, with their MariaDB
subclasses retaining the same behavioral cases.

| Criterion | Production path | Positive witness | Failure witness | Runtime evidence |
| --- | --- | --- | --- | --- |
| Current schema and exact restore topology | BackupSchema, EntityTopoSorter, MixIns and entity restorers | All 36 schema-7 tables, exact IDs and owned season catalogs round-trip | Required files/counts, dangling references and unsupported versions reject before wipe | H2/MariaDB backup suites and repository graph probe |
| Complete immutable history | PickSubmissionService, PickReviewService and backup restorers | Multiple revisions, return/open/close, approval and private correction survive restore | Duplicate requests, invalid pointers, ownership and forged publication evidence reject | DiscordPicksBackupRoundTripIT and MariaDB subclass |
| Zero remains distinct from Equal Conditions | PickPayload, RaceSettings and publication content | Restore numeric 0x and nullable Equal Conditions with their exact modes | Invalid mode/multiplier combinations remain rejected | Archive assertions plus existing native UI and real PNG witnesses |
| Public/private boundary survives restore | PickDisclosureService, publication attempts/snapshots | Older confirmed lobby fields remain public while a newer correction stays private | Uncertain delivery never releases a car or fabricates a confirmation | Backup, publication and generated-site fixtures |
| Gateway-originating restore admission | PickGatewayDispatcher, PickWriteExecutor and ImportLockService | Restore waits through an admitted transaction's commit | New writes, failed commits and stale restored sessions cannot create a receipt/revision | DiscordPicksRestoreAdmissionIT and MariaDB subclass |
| Full fixed-pool workflow | PickInteractionHandler and existing domain services | Team-wide private view, submit/replace, approval transfer/lock and explicit offline publication | Stale confirmations, revoked permissions, deadline closure and return-leg authority deny writes | Real handler, persisted records, public HTML and recorded webhook requests |
| Category integration uses the existing policy | SeasonCarPoolService and PickReservationService | Frozen entry and concrete car pass the wizard and approval | A consumed entry cannot be reused by the same effective home team | Extended category workflow plus existing reservation/concurrency suites |
| Read-only integrity audit | DataAuditService and PickIntegrityAuditService | Valid closed/returned history remains intact | Misbound channels, corrupt approval/workflow pointers, duplicate usage, unreviewed ambiguous deadlines and unproven publication state are reported | Persisted malformed fixtures; counts, values and private text remain unchanged |
| Native administration and private response safety | Existing authenticated forms and Gateway response ports | No-JavaScript desktop/mobile forms, escaped private text and catalogs above 25 options | CSRF, stale forms, private callback expiry and permission changes remain enforced | Existing browser and transport suites rerun on the final head |
| Delivered capability with explicit activation | PickCollectionStatus, PickGatewaySettings and DiscordPicksGateway | Completed confidentiality/collection capability permits valid private restore and workflow | Default-off Gateway, disabled seasons and outbound recovery policy still prevent unintended collection/transport | Real component/configuration and Gateway startup tests |
| Operator rollout and recovery | Application/Docker configuration and operations guide | One consumer, exact bindings, private receipt recovery and deliberate registration/season activation | Paused restore, uncertain delivery and unsupported live actions have explicit operator handling | Reviewed configuration and operator checklist; offline fixtures only |

## Scope and verification

The current schema is 7 with 36 export tables; supported archive schemas remain 1 through 7.
Released migrations stay unchanged. Final acceptance adds no schema version merely to repeat
persistence work already delivered by the owning issues.

The baseline #329 passed 2,809 unit, 1,186 integration and 228 browser cases, with three existing
unit skips, 92.94% line coverage, zero SpotBugs findings and the loaded find-sec-bugs guard.
Those counts establish the starting state, not completion of this issue. The final feature PR
will record its own exact head/base, canonical counts, static analysis and GitHub/CodeQL results.

Before merge, run `./mvnw clean verify -Pe2e -Ddocker.available=true` on the committed final head
and current upstream base. Do not claim skipped MariaDB cases as evidence. Reuse existing
unchanged guard proofs and falsify any new protection at its intended assertion.

After the actual merge, audit the full parent scope before closing #279. Publish the reviewed
Wiki changes and remove only the proven landed topic branch.

## Focused acceptance evidence

The combined focused lifecycle passed 134 unit, 116 integration and 12 browser cases, with
zero failures, errors or skips. Integration cases included the H2 and MariaDB backup and
Gateway restore-admission subclasses. All ten complete workflow cases passed, including real
PNG rendering and recorded offline webhook delivery. Desktop and mobile native binding forms
were visually inspected. Their obsolete acceptance-pending copy was corrected before final
verification.

The capability test first failed at its expected readiness assertion. Both unreviewed Berlin
DST cases first failed at the expected missing-finding assertion before the read-only audit
was added. Existing history validation remains authoritative before deadline diagnostics.

Live guild registration, operator UAT and production activation were not performed. The
operations checklist records those separately authorized actions and retains Google Forms
until the operator explicitly retires it.

## Guard and review evidence

Six independent mutations of the new diagnostic scope each caused exactly one intended
assertion failure and zero JUnit errors: enrollment, unfinished target, explicit opening,
explicit closure, approved revision and missing deadline. Each probe restored the exact
committed source before the next probe. Existing write, authorization, restore and disclosure
guards are unchanged and retain their owning issues' evidence.

Self-review and an independent read-only security pass reviewed checkpoint
`44993687df8e351541ea7146f0b216ca7efc5a1c` against
`321ceff91203f43b687c199efd9aa3f78f98ac69` with no actionable findings. Default-off startup,
explicit season activation, current channel authorization and pre-wipe restore validation
remain enforced. Final verification below belongs to the subsequent committed head after
integrating upstream release/version-only commits.

The initial full run exposed one obsolete security assertion that still expected the temporary
confidentiality gate. It was corrected to require actual missing-guild configuration at the real
service. The focused controller/service/security/browser regression then passed 25 unit,
18 integration and three browser cases. Authentication and CSRF cases remain unchanged.
The interrupted red run is not final evidence; final canonical verification restarts on the
corrected committed head.

The complete regression run also identified three old disabled-capability assertions that
relied on the pre-release real component always returning false. Their fixture now explicitly
sets unavailable collection/confidentiality capability, preserving the no-history and pre-wipe
rejection checks. Its focused rerun passed one real-default unit case and all three integration
cases. The completed red run had no other failures across 2,810 unit, 1,198 integration and
228 browser cases. It remains diagnostic evidence, not final acceptance.
