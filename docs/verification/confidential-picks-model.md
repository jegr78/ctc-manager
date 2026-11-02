# Confidential picks model verification

Issue #281, parent #279. Depends on delivered #280; category integration reuses delivered #276.
Selected from the live tracker after #278 and parent #273 closed. No open PR, competing checkout,
related active remote branch, assignee or inherited milestone occupies this work. The local GSD
milestone is shipped. Owner-planned deployment boot evidence in #301 remains excluded.

[CONTEXT.md](../../CONTEXT.md) records business terminology only. The live parent specification
is the state-machine and initial payload contract; source-form responses are never imported.

## Requirements matrix

| Requirement | Production path | Positive witness | Rejection or absence witness | Runtime proof |
| --- | --- | --- | --- | --- |
| Ordinary cutoff and explicit exceptions | Clock/Berlin deadline policy, target/submission/review services | Before cutoff, explicit opening and returned resubmission | At/after cutoff, no deadline, invalid DST, manual close, approved lock | Deterministic unit clock and persistent target state |
| Immutable complete revisions and receipts | Pick/revision repositories and submission transaction | Complete confirm adds one version; retry returns original receipt | Abandoned drafts add nothing; distinct stale versions conflict; foreign replay denies | Unit policy plus H2/MariaDB real transactions |
| Typed initial profile | PickPayload and shared validation | Every accepted form option, zero multiplier and Equal Conditions | Positive laps, bounded factors/text, fuel/None/tyre-subset/type mismatches | Boundary unit cases and revision persistence |
| Exact ownership and targets | Target service, immutable original matchday/opponent references and authorized actor identity | Scheduled non-bye effective-home race in bound season | Cross-team/season, result-bearing, decided or detached target | Refreshed locked state, native domain calls |
| Reservations and actual usage | Shared eligibility and season locking | Latest pending selection reserves; another home independent; current target excluded once | Duplicate car/category/track; stale reservation replacement; closed/returned reservations retained | H2/MariaDB concurrent claims and ordinary admin saves |
| Atomic latest approval | Review service and settings-only write operation | Exact latest version/revision copied and reservation transferred | Stale revision, invalid target/pool and DB failure leave all prior rows unchanged | Approval rollback and submit-vs-approve transactions |
| Management history | Immutable decisions and correction records | Actor/reason/time for open/return/close/approve/correction | Missing reason/actor, approved team edits, silent history mutation | Durable decision rows and receipt checks |
| Enrollment and confidentiality marker | Season opt-in and race creation/lifecycle services | Reviewed eligible targets enrolled; new targets inherit | Disable/archive/bot stop closes writes without clearing marker/history | Service lifecycle and constructor/caller audit |
| Stable target identity | Race/match/playoff/Swiss/replacement/import writers | Metadata/results updates preserving identity remain valid | Picks-bearing reassignment/deletion and ambiguous whole imports reject before changes | Native save/delete/import and indirect lifecycle ITs |
| Complete backup contract | New migration, schema topology, MixIns/restorers/preflight | Exact history, pointers, flags and reasons round trip | Unsupported/forged/missing references reject before replacement; old backups default off | H2 and MariaDB full restore and FK checks |
| Restore writer admission | Submission/review transaction entry | Writer lease covers commit/rollback | Import-held admission and timeout refuse writes before a transaction | Real concurrent restore/writer boundaries |
| Delivery | Documentation, review, guard falsification, canonical build | All required local/CI/security gates and verified merge | No half-complete published collector or new live Discord side effect | Committed clean verify, matching CI and tracker readback |

## Checklist

- [x] Recheck live tracker, dependencies, parent/milestone chain and parallel occupancy.
- [x] Create the behavior branch and record requirements before production changes.
- [x] Record resolved business terms without implementation details.
- [x] Audit all persistent fields, constructors, writers, target changes, eligibility and backup callers.
- [x] Write and observe assertion-red policy/payload/submission/review tests before implementation.
- [x] Implement deadline/override, immutable revision/receipt and ownership contracts.
- [x] Integrate reservations and an atomic settings-only approval/correction operation.
- [x] Preserve enrollment/history through lifecycle, import and deletion paths.
- [x] Prove rollback, replay and concurrent operations on H2 and MariaDB.
- [x] Add migration, matching backup support and full round-trip/preflight coverage together.
- [x] Update README, CLAUDE and glossary; prepare the Wiki update for post-merge publication.
- [x] Complete code self-review and independent security review; resolve all findings.
- [x] Falsify domain, ownership, reservation, restore and SQL guards individually from a correct committed checkpoint and restore exact source.
The authoritative committed-head clean verify, matching-head CI/CodeQL, squash merge and
post-merge hierarchy/Wiki/branch reconciliation are tracked in the live delivery checklist on
[PR #323](https://github.com/jegr78/ctc-manager/pull/323). This document records the pre-verification
checkpoint so recording the final build result does not change its verified head.

## Initial integration audit

RaceService currently combines pairing/schedule changes with selection/settings writes. Approval
requires a narrow shared settings-only operation so it cannot reorient, reschedule, change results
or publish. Season selection locks and refreshed current race/pairing state from #276 remain the
single serialization protocol. Existing category eligibility must be reused.

The restore admission API is ImportLockService.tryEnterWriter/exitWriter, currently wrapped around
mutating HTTP requests. A non-HTTP pick writer must acquire admission before opening its transaction
and retain it through commit or rollback. Drafts are not submissions or reservations.

Determine migration and backup schema versions from current upstream before adding persistent
shapes. Keep revision pointers numeric to avoid an aggregate/latest-revision restore cycle.

## Focused evidence and review corrections

- Deadline, receipt and eligibility regression probes: 25 tests, 4 intended assertion failures,
  zero errors before correction; 43 focused tests pass afterward. A single captured Instant now
  supplies deadline comparison and the durable receipt. A known request can recover its original
  receipt without resending the wizard payload, after current actor ownership is checked.
- Restore and integrity audit: 34 integration tests pass, including three synthetic private-history
  fixtures and existing audit/controller behavior. The audit reports the first inconsistency and
  never repairs history or prints settings payload text.
- Backup round-trip and malformed-archive preflight: 16 H2 and 16 MariaDB tests pass with the
  original matchday/opponent snapshot and new composite target foreign key. Both staging and a
  subsequently tampered execution reject malformed history before the wipe. Supported legacy
  archives default collection off. A disabled bot does not erase restored private state.
- Backup reservation parity: two additional intended assertion failures, zero errors, prove that
  regular and playoff byes must not consume historical car/track choices. Validator and ordinary
  eligibility now follow the same bye rule.
- Enrollment is explicit in native scheduling/creation paths. ORM callbacks are intentionally
  absent: metadata edits of an already scheduled legacy race must not silently enroll it, and
  detached legacy import proxies must remain usable.
- Public submission/review entry points reject an already active transaction so writer admission
  always precedes transaction start and successful receipts are returned only after commit.
- Collection and confidentiality readiness remain unavailable. The schema/model can ship before
  the Gateway and disclosure work, while supported restore cannot activate private content early.

Integration tests use committed synthetic fixtures and isolated H2/MariaDB containers. They do not
contact Discord, Google services or the permanent deployment. Canonical verification, guard
falsification, review completion and CI evidence are recorded below when finished.

- Real transaction concurrency/rollback/admission: ten tests each on H2 and MariaDB pass,
  including inherited receipt/history persistence tests. Conflicting claims commit one winner;
  disjoint claims both succeed. Submit/approve and replace/return preserve one versioned transition.
  The rollback probe first flushes and observes new settings SQL, then rejects the decision insert
  with a temporary test-only database constraint and verifies the entire original state afterward.
  The restore-drain probe pauses an admitted approval after SQL flush, blocks new submissions and
  proves writer draining waits until the committed approval is visible.

## Review and guard evidence

The independent security pass reviewed production commit `2fd9e285` and its seven-file delta,
then rechecked the two-assertion test-only delta in `f943a108`. No security findings remain.
Code review corrected the cutoff/receipt timestamp split, durable replay without retained wizard
payload, bye-consumption parity and the missing aggregate assertion for manual close.

The correct production baseline is `2fd9e285`; the strengthened aggregate test is committed in
`f943a108`. Each of the following 57 mutations changed one protection at a time, produced intended
assertion failures with **zero test errors**, and restored the exact committed source before the
next mutation. Incidental harness errors were diagnosed separately and are excluded from this
list. The final clean build discards all compiled mutation artifacts.

<details>
<summary>Individual guard probes</summary>

| Protection deliberately broken | Witness | Intended assertion failures |
| --- | --- | ---: |
| collection admission | `PickDeadlinePolicyTest` | 3 |
| deadline approved lock | `PickDeadlinePolicyTest` | 1 |
| manual close priority | `PickDeadlinePolicyTest` | 1 |
| management opening | `PickDeadlinePolicyTest` | 3 |
| dst offset | `PickDeadlinePolicyTest` | 2 |
| strict cutoff | `PickDeadlinePolicyTest` | 5 |
| aggregate close | `MatchdayPickTest` | 1 |
| aggregate version | `MatchdayPickTest` | 1 |
| missing only open | `MatchdayPickTest` | 1 |
| submitted only return | `MatchdayPickTest` | 1 |
| exact approved revision | `MatchdayPickTest` | 1 |
| approved only correction | `MatchdayPickTest` | 1 |
| aggregate approved lock | `MatchdayPickTest` | 2 |
| typed complete payload | `PickPayloadTest` | 33 |
| replay ownership | `PickSubmissionServiceTest` | 1 |
| deadline before write | `PickSubmissionServiceTest` | 6 |
| replay before version | `PickSubmissionServiceTest#givenDurableRequestWithoutRetainedWizardPayload_whenReplaying_thenRecoverOriginalReceipt` | 1 |
| single acceptance instant | `PickSubmissionServiceTest` | 1 |
| management reason | `PickReviewServiceTest` | 3 |
| review approved lock | `PickReviewServiceTest` | 1 |
| review latest revision | `PickReviewServiceTest` | 1 |
| correction state | `PickReviewServiceTest` | 1 |
| team target ownership | `PickTargetServiceTest` | 2 |
| management identity | `PickTargetServiceTest` | 1 |
| original target frame | `PickTargetServiceTest` | 2 |
| shared car eligibility | `PickEligibilityServiceTest` | 1 |
| actual track consumption | `PickEligibilityServiceTest` | 1 |
| pending track consumption | `PickEligibilityServiceTest` | 1 |
| bye eligibility | `PickEligibilityServiceTest` | 2 |
| restore admission before transaction | `PickWriteExecutorTest` | 1 |
| no outer transaction | `PickWriteExecutorTest` | 1 |
| lease through transaction | `PickWriteExecutorTest` | 2 |
| lifecycle deletion | `PickLifecycleGuardTest` | 1 |
| lifecycle identity | `PickLifecycleGuardTest` | 1 |
| managed setup review | `PickLifecycleGuardTest` | 1 |
| backup bye parity | `PickBackupValidatorTest` | 2 |
| missing deadline | `PickDeadlinePolicyTest` | 1 |
| channel actor | `PickTargetServiceTest` | 1 |
| team actor | `PickTargetServiceTest` | 2 |
| season track membership | `PickEligibilityServiceTest` | 1 |
| shared pending car reservation | `RaceCarSelectionServiceTest` | 2 |
| payload integer shape | `PickPayloadReaderTest` | 2 |
| payload text shape | `PickPayloadReaderTest` | 2 |
| backup receipt uniqueness | `PickBackupValidatorTest` | 1 |
| backup original owner | `PickBackupValidatorTest` | 3 |
| backup replayed state | `PickBackupValidatorTest` | 4 |
| migration resource checksum | `V26PickModelMigrationTest` | 1 |
| sql pick uniqueness | `V26PickModelMigrationTest` | 1 |
| sql nonnegative version | `V26PickModelMigrationTest` | 1 |
| sql state membership | `V26PickModelMigrationTest` | 1 |
| sql manual overrides | `V26PickModelMigrationTest` | 1 |
| sql distinct opponent | `V26PickModelMigrationTest` | 1 |
| sql approved pointers | `V26PickModelMigrationTest` | 1 |
| sql original matchday | `V26PickModelMigrationTest` | 1 |
| collector release gate | `PickCollectionDisabledIT` | 1 |
| confidentiality release gate | `PickCollectionDisabledIT` | 2 |
| private archive preflight | `PickCollectionDisabledIT` | 2 |

</details>
