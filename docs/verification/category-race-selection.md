# Race category selection verification

Issue #276, parent #273. Selected after #275 merged through PR #319. The initial base is
`0bd77237949e46179a131470ff658579cd93390a`. GitHub remains the canonical tracker.

| Requirement | Production path | Positive test | Rejection test | Runtime evidence |
| --- | --- | --- | --- | --- |
| Consume whole entry by season and effective home | RaceCarSelectionService | MR choice consumes its combined MR/RR entry | A second RR member cannot bypass consumption | Unit tests and native browser |
| Share read/write availability policy | availability/validate and used-selections | Own race excluded; another team and season independent | Unknown season/team and forged selection IDs rejected | Service and controller tests |
| Validate complete frozen selections | RaceService save | Both empty draft or valid entry/member pair | Partial pair, draft/foreign entry, nonmember, fixed mode entry | Unit and persistence tests |
| Reject before managed state changes | Save and re-edit transaction | Successful selection change updates usage | Rejected edit retains settings, pairing, car, entry and old usage | Real transaction integration test |
| Serialize all usage-changing operations | Season DB locking protocol | Exactly one concurrent same-team/entry write wins | Concurrent edit, delete and reorientation cannot bypass consumption | H2 and MariaDB integration tests |
| Effective home ownership | Match/playoff/override paths | Return leg override owns its selection | Bye or foreign team cannot consume | Service and persistence tests |
| Preserve unrelated stale writes | Race metadata/calendar updates | Calendar field changes preserve concurrent selection | Cached race cannot overwrite car/entry | Two-transaction integration test |
| Preserve fixed pool and track rules | Compatibility saves and form data | Existing car/track selection and drafts | Existing reuse guards remain enforced | Existing regression tests |
| Native entry/member editor | RaceForm/Controller/template | No-JS entry then concrete car and own edit | All values/options retained after invalid submission | HTTP and browser tests |
| Ignore stale availability responses | Race setup JavaScript | Latest home/entry response wins | Earlier delayed response cannot replace choices | Browser controlled response test |
| Preserve configured races on results overwrite | CSV/Sheets import preflight | Single leg and reversed return rows retain identity/configuration | Ambiguous mapping leaves all old races/results/usage unchanged | Unit and transactional import tests |
| Release usage safely | Deletion and aggregate deletion | Deleted race releases entry | Concurrent delete/reassign remains serialized | Real transaction tests |
| Preserve backup integrity | Schema-4 Race fields and restore | Exact entry/car UUIDs and supported legacy rows | Bad selections rejected before replacement | Archive integration tests |
| Read-only audit | DataAuditService | Consistent category selections | Synthetic duplicate/foreign/bye violations | Audit integration tests |

Before implementation, audit every Race save/delete, team/parent reassignment, form-data constructor and used-selection reader across src. Read the existing write-audit and caller snapshots, then repeat the searches against the actual selected base.

## Implementation checklist

- [x] Recheck tracker, dependencies, parent milestones and parallel work.
- [x] Create the behavior branch from current origin/master and record requirements.
- [x] Complete the write/call-site audit against this base.
- [x] Write focused policy/save/form tests and observe the intended red failures.
- [x] Implement shared entry consumption policy and transactional writer protocol.
- [x] Preserve selections through deterministic results overwrite; reject ambiguous imports unchanged.
- [x] Implement native entry/member selection and latest-response handling.
- [x] Verify H2/MariaDB concurrency, rollback, backup and read-only audit.
- [x] Inspect desktop/mobile/native browser behavior.
- [ ] Complete code review and independent security review; fix every finding.
- [x] Falsify newly introduced guards and restore committed bytes.
- [ ] Run authoritative clean verify -Pe2e on the committed reviewed head.
- [ ] Verify matching-head CI, squash merge, Wiki/tracker reconciliation and branch cleanup.

## Scope inherited from the foundation

V24 already provides Race.carPoolEntry and the season-owned snapshot. Schema 4 already persists
entry/member references and rejects invalid complete selections before restore. Keep that shape
and extend behavior tests through the new write path. Add no duplicate nullable column or silent
conversion. Presentation outside race setup remains #277.

The audit includes indirect ownership changes through manual/automatic playoff seeding, winner
advancement and revocation. Validate planned batch ownership before changing managed entities,
lineups or decision history. Distinguish blank scheduling from writes that consume or release an
entry. Unrelated cached Race/Match/PlayoffMatchup writers must preserve concurrent selection or
participant changes.

MariaDB tests must establish a context read before competing for the season lock and inspect
actual transaction defaults. Measure current locking reads and snapshot-conflict behavior; a
passing H2 test alone does not establish the MariaDB contract. Keep existing server isolation and
restore write-admission rules.

## Write audit

| Path | Classification | Required handling |
| --- | --- | --- |
| RaceService.saveRace and applyPairing | Selection and shared/reversed-leg ownership changes | Validate the complete planned assignment before mutation, with source/target season locks |
| RaceService.deleteRace | Explicit release | Fresh owner lock before deleting race/lineups and recomputing scores |
| RaceService results/quick score and RaceCalendarService | Unrelated scalar/results updates | Preserve concurrent selection through cached entities |
| MatchService.deleteMatch and MatchdayService.deleteMatchday | Cascaded release | Join the owning season lock protocol |
| MatchService.createMatchWithLegs/addLeg, SwissPairingService and generator callers | Blank scheduling | Keep unconfigured races valid and preserve return orientation |
| PlayoffService decision/advancement/reopen | Indirect effective-home changes | Validate proposed successor ownership before history/participant/lineup mutation |
| PlayoffSeedingService manual/automatic seeding | Individual/batch participant changes | Validate the planned ownership before changing configured legs |
| CsvImportService, including Sheets delegation | Results overwrite with deletion/recreation | Whole-import preflight and deterministic race reuse; ambiguous mapping rejects unchanged |
| TestDataService | Explicit fixed-mode fixtures and blank schedules | Retain safe defaults; use isolated category fixtures for new tests |
| BackupImportService and entity restorers | Admitted replace-all restoration | Retain existing writer admission and schema-4 preflight; no per-row competing lock protocol |
| Season/phase deletion | Refuses nonempty child aggregates | Preserve existing guards; it cannot release a configured race through this path |
| Team metadata/succession labels | Does not change stored effective-home identity | No category consumption change from renaming or display-only successor metadata |

The audit searched all production and test saveRace calls, RaceData/RaceFormData constructors,
RaceRepository uses, direct car/container/team setter writes and aggregate removals. Native
sub-issue #276 owns these paths. Local grep snapshots and the detailed read-only assessment are
kept in the task cache for implementation; the current source remains authoritative.

## Behavior evidence

TDD runs recorded assertion failures before implementation for shared policy, batch writes,
native saves, cached transaction state, playoff reseeding/advancement, import preservation,
form rebuilding, audit violations and asynchronous browser responses. Compile errors were
not counted as behavior failures.

- Policy tests cover whole-entry consumption, frozen membership, complete pairs, fixed mode,
  effective home, own-race exclusion, independent teams/seasons and joint assignment swaps.
- H2 and MariaDB transaction tests cover current race/parent state, real database locks,
  concurrent same-home writes, failed edits, deletion through race/match/matchday, and stale
  scalar writes. MariaDB retains REPEATABLE READ and enabled innodb_snapshot_isolation.
  Its measured error 1020 is a domain retry; calendar IDs persist in a fresh transaction.
- Cross-season move tests cover ordered locks, a whole unscored pairing move, retained sibling
  rejection, duplicate target pairing, an owner changed while waiting, and independent usage.
- Playoff transaction tests cover individual and batch seeds, standings-derived seed/roster
  rollback, advancement/revocation rejection, and cached metadata/score updates.
- Import tests cover stable single-leg identity despite reversed scorecard blocks, return legs
  mapped by distinct effective homes, and ambiguous whole-import rejection before any race
  deletion or driver creation. Negative cases include incomplete pairs, fixed-mode entry
  references, count mismatch, repeated homes and foreign matchday context.
- The native HTTP editor preserves selected entry/car/track IDs, submitted home context,
  disabled field defaults, explicit clearing, raw numeric/date values and friendly errors.
  Availability retains existing response keys and returns a domain 404 for unknown seasons.
- Browser scenarios cover JavaScript and native refresh, MR consuming the whole MR/RR entry,
  late JSON bodies, rapid entry changes, failed availability, disabled-choice resubmission,
  entry replacement releasing old usage, and Berlin/London conversion after date correction.
  Existing fixed car/track editor regressions remain included.
- Schema-4 round trip uses the native save path and preserves exact entry/car/member IDs and
  settings. Bad selections reject before replacement. Audit reports historical duplicate,
  bye and missing-home selections without changing data.

A later stale-aggregate test exposed a destructive matchday deletion after a committed move:
its cached children were still removed from the target. Deletion now refreshes cached child
ownership, rejects a moved child with a domain reload message, and refreshes current aggregate
membership before cascading. Matchday persist/merge/remove/detach remain; refresh does not
cascade back through loaded child graphs. Nine move/aggregate cases and eleven H2 selection
transaction cases pass, including separately cached match/race lists and newly committed children.

The guard ledger contains 68 individually falsified logical protections. Retained evidence has
assertion failures at the intended property and zero test errors. Earlier lint-only and incidental
exception attempts were excluded and replaced with appropriate witnesses. Sources were restored
byte-for-byte from the correct commits after each mutation. The covered paths include policy,
writer hooks, database/current-state reads, indirect playoffs, imports, audit, native defaults,
late browser responses, calendar transaction boundaries and stale aggregate deletion.

Existing controller contracts exposed two fixed-mode message regressions. Their original text
and home-team short name are restored; all 90 focused policy/service/controller tests and six
persistent rejection cases pass without weakening assertions.

## Visual inspection

An isolated dev server with synthetic data was inspected using playwright-cli at desktop
1440px and mobile 375px. Long team names and 120-character entry labels remain contained.
The catalog anchor clears the mobile navigation at 72px. Normal and validation-error views
were inspected. Invalid laps/date text survives native refresh; error messages do not expose
Java conversion details. The mobile document and viewport are both 375px wide.
Screenshots are local ignored artifacts under .screenshots/category-race-*.png.
The preview server and named browser session were stopped after inspection.

## Delivery gates

The remaining checklist records work still required before delivery. Code and independent
security reviews precede the authoritative committed-head clean verify -Pe2e run. Each new
logical guard is independently falsified against its behavior assertion and restored from the
correct checkpoint. The PR records the final verification counts, guard evidence and matching
CI head. Merge is complete only after GitHub reports MERGED and Wiki, issue hierarchy and
local branch cleanup have been reconciled.
