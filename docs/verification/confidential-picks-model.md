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
- [ ] Update README, CLAUDE, glossary and user-facing Wiki.
- [ ] Complete code self-review and independent security review; resolve all findings.
- [ ] Falsify every new guard from a correct committed checkpoint and restore exact source.
- [ ] Run authoritative committed-head clean verify -Pe2e -Ddocker.available=true.
- [ ] Pass matching-head CI/CodeQL, exact-head squash merge, reconcile hierarchy/Wiki and clean topic branch.

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
