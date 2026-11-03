# Permanent incident cases and reporting windows

Issue #288 delivers the first implementation unit of #287. The current upstream baseline is
`c659f16185d3370d6fb2b60ece5a1cbce02acc66`. GitHub is the current plan; the local roadmap's
latest milestone is already shipped. There are no open PRs, another checkout or recent
incident branches. Owner-managed deployment work and the assigned dependency dashboard are
excluded.

## Delivery checklist

- [x] Verify live issue state, parent dependencies and parallel work before selection.
- [x] Establish the context, window and report contracts with failing service tests.
- [x] Implement canonical regular/playoff contexts and explicit management bindings.
- [x] Implement confirmed deadlines, strict cutoff locking and immutable report history.
- [x] Protect incident identities through ordinary edits, deletion and results imports.
- [x] Add the current migration, backup schema, explicit restorers and archive preflight.
- [x] Verify H2/MariaDB concurrency, restore and source identity protection.
- [x] Verify native desktop/mobile management forms.
- [x] Update README, CLAUDE, domain vocabulary and the user-facing Wiki draft.
- [ ] Complete focused checks, individual new-guard witnesses and independent review.
- [ ] Verify the final committed head, pass GitHub CI, merge and reconcile the tracker.
- [ ] Publish the Wiki changes and remove the proven landed topic branch.

## Requirements matrix

| Requirement | Production path | Positive witness | Rejection witness | Runtime evidence |
| --- | --- | --- | --- | --- |
| One canonical pairing context | IncidentContextService and IncidentMatchTarget | Regular match and standalone playoff binding; auxiliary Match resolves to the same playoff context | Mixed ownership, unready/byes, wrong guild and unrelated shared legacy channels reject | IncidentContextServiceTest and H2/MariaDB unique/FK integration cases |
| Explicit opening and planned-start default | IncidentWindowService and native management forms | First planned start interpreted in Berlin plus 24 elapsed hours; explicit future override | Missing/ambiguous start has no invented default; past defaults cannot open silently | IncidentWindowServiceTest and browser preview/confirmation |
| Permanent confirmed deadline | IncidentReportWindow and IncidentReportEvent | Versioned open/change records actor, time, reason and the original planned-start snapshot | Schedule edits cannot change a confirmed cutoff; stale changes and expired/closed reopening reject | Service assertions and persisted window/event state |
| Complete durable case | IncidentReportService, IncidentReport and IncidentReportRevision | All four form-equivalent values, stable reference, exact race, creator and frozen party survive edits | Empty fields, invalid ownership and malformed attribution reject before mutation; 00:00 remains valid | IncidentReportServiceTest and database fixtures |
| Creator-only immutable history | IncidentReportService | Same author can edit or withdraw; distinct reports remain distinct; durable request replay returns its original receipt | Another team member, stale version or changed party cannot overwrite the case; withdrawn records stay queryable | Service and integration assertions on exact revisions/events |
| Strict cutoff and lock order | Window lock followed by report lock inside existing restore writer admission | A confirmed write before the deadline commits with one captured acceptance instant | At now >= deadline every mutation rejects; edit/withdraw versus closure has one serialized outcome | IncidentWindowConcurrencyIT and MariaDB subclass |
| Protected incident identities | Incident lifecycle checks beside existing race/match/pairing guards | Results-only edits preserve race/case IDs, history and attribution | Incident-bearing deletion, reparenting, participant changes and channel removal reject before mutation | Service/import integration tests and unchanged database state |
| Results import remains atomic | CsvImportService protected-leg preflight | Unambiguous result replacement reuses stable incident-bearing race IDs | Ambiguous full imports fail before driver, result or pairing mutations | Synthetic CSV/Sheets and H2/MariaDB import witnesses |
| Persistence ships with restore | New Flyway migration, BackupSchema, MixIns, explicit restorers and IncidentBackupValidator | Contexts, windows, active/withdrawn cases, sequential revisions and decisions round-trip with exact IDs | Missing/count-mismatched files, wrong owners and forged pointers/history reject before wipe | H2/MariaDB backup round-trips and malformed archive fixtures |
| Read-only historical integrity | IncidentIntegrityAuditService reusing IncidentBackupValidator | Valid history has no incident finding | A mismatched persisted version reports a safe row locator without private payload or repair | IncidentIntegrityAuditIT plus existing audit regression suites |
| Legacy compatibility | Explicit supported-schema map | Supported older archives contain no incident state or invented windows | New incident state cannot be disguised as an older archive; unsupported versions stay rejected | Manifest contract and preflight tests |
| Internal native management | IncidentContextController, IncidentWindowController, versioned DTOs and existing pairing pages | Regular/playoff links reach the canonical section; native desktop/mobile forms retain errors and timezone/offset context | Auth/CSRF, invalid dates, stale versions and private text escaping stay enforced | Controller/security and no-JavaScript browser cases |
| Delivered foundation stays private | Existing Gateway and public generators remain unchanged | Management can inspect permanent stored cases without public incident publication | No live reports, external URL downloads, announcements, automatic opening or real Discord posts | Offline fixtures, public-output regression and diff review |

## Boundaries and verification

This delivery stores the permanent incident foundation and its management deadline controls.
Role-resolution/private Gateway dialogs belong to #289, durable notices and scheduling to #290,
linked Discord threads to #291, ruling publication to #292 and final rollout acceptance to #293.
The foundation must not fabricate any of those later capabilities or silently enable intake.
Trusted actor contracts keep creator identity separate from the immutable reporting party.

Reuse the existing restore-admitted transaction boundary, pairing policy and native UI patterns.
Window/report mutation locks order window before report; identity coordination also follows the
existing season lock protocol. Incident reportability includes completed contested races and
does not reuse the picks-only unfinished-race or unlimited late-opening policy.

Determine migration and schema versions against current upstream. Never edit released migrations.
New shape requires matching restoration support in this PR. Before the expensive final run,
audit every constructor/caller and every archive/schema fixture affected by the new types and
version, then complete focused tests and review. Run one final committed-head
`./mvnw clean verify -Pe2e -Ddocker.available=true`; minimum line coverage is 82%, with Checkstyle,
SpotBugs/find-sec-bugs and CodeQL retained. Skipped MariaDB tests are not acceptance evidence.

All fixtures are synthetic and offline. No production activation, live guild registration,
Discord messages, real reports, scoring decisions or host-service changes are authorized here.

## Initial contract and persistence evidence

Observed 33 intended assertion failures and zero JUnit errors before implementing the service
contracts. Their focused rerun is green. The migration's first valid-row assertion also failed
before the table existed. The new V29 migration follows the fixed SQL-resource protocol already
used upstream, retaining UTC values beyond MariaDB's TIMESTAMP range.

The combined focused run passed 34 unit and eight integration cases, four each on H2 and MariaDB,
with zero failures, errors or skips. It verifies committed history/replay, active/withdrawn cases,
clock-authoritative closure, exact deadline snapshots after schedule changes, ambiguous legacy
channel rejection and dates in 2060. This is not final acceptance of backup, identity guards,
concurrency, native forms or the overall feature.

## Backup checkpoint

The expanded focused run passed 63 unit and 53 integration cases, with zero failures, errors
or skips. Schema 8 exports all 41 entity tables. H2 and MariaDB both preserve exact report IDs,
active/withdrawn state, revisions and original request receipts through restore. Malformed
archives reject during staging and again immediately before execution without wiping live data.
Supported schema-7 archives restore without incident records or invented reporting windows.

This checkpoint is incomplete. Protected source identities and results imports, concurrency
witnesses, management forms, documentation, independent review and final verification remain.

## Protected identity checkpoint

The lifecycle guard first produced six intended unit assertion failures. After implementation,
eight guard tests passed. Before connecting the guard to production callers, the source-path
fixture produced seven intended assertion failures and zero JUnit errors. The subsequent run
passed 138 unit and 41 integration cases, without failures, errors or skips.

The eight new source-path cases passed on both H2 and MariaDB. Report-bearing race deletion,
bound pairing/matchday deletion, participant swaps and matchday moves now reject before mutation.
Results-only imports preserve the exact incident race ID and withdrawn history. Ambiguous leg
mappings reject before driver creation. Ordinary setup/schedule edits remain possible and keep
the confirmed reporting deadline. Existing pick import, CSV transaction and playoff-decision
fixtures also passed. Standalone playoff imports, channel dependencies and the team-succession
witness still need their remaining coverage before the identity audit is complete.

## Channel and standalone playoff checkpoint

An archived-channel unit test failed at its intended assertion. Four source-path assertions then
failed without JUnit errors: open-window archival, closed-history archival, unrelated channel
linking and standalone playoff result identity. After implementation, 77 unit and 45 integration
cases passed with no failures, errors or skips. The twelve incident source cases passed on both
H2 and MariaDB; existing channel idempotency, WireMock and playoff selection cases also passed.

Channel changes now share the season/guild lock protocol with incident bindings and check
dependencies before external calls. Open reporting blocks archival. Closed reporting permits
explicit archival while retaining frozen revisions and exact attribution. Standalone playoff
results reuse their existing race without manufacturing an auxiliary regular Match. Concurrent
binding versus source edits/channel linking still needs its explicit runtime witness.

## Concurrency and audit checkpoint

Eight real-barrier concurrency cases passed on H2. MariaDB first exposed three intended source
assertion failures: a waiting race edit missed a new context, a waiting channel link missed a new
binding, and a waiting deletion missed a committed report. Current locking reads now protect
these paths. MariaDB snapshot conflicts return a domain retry without changing history.

The expanded run passed 64 unit and 89 integration cases with zero failures, errors or skips.
Each database ran thirteen identity cases and nine concurrency cases. These cover two edits from
one version, all three mutation types waiting past cutoff, closure after a committed edit, new
bindings/reports appearing during lock waits, team succession and restore admission through commit.
The closure witness checks rollback and a fresh explicit retry when MariaDB reports a snapshot
conflict. It then requires the exact last committed frozen revision.

The read-only incident audit first failed its malformed-version assertion, then passed both valid
and malformed persisted fixtures without repairing rows or exposing payload text. Existing data
and private-pick audit suites also passed.

CI on checkpoint `65048e73` passed 2,874 unit, 1,240 integration and 228 browser cases, with only
the three existing unit skips. Its verify gate failed on eleven static findings: one fixed-resource
SQL execution and ten managed-entity references in the internal pairing adapter. Their scoped
annotations and acceptance rationale are tracked in `docs/security/sast-acceptance.md`; a later
complete verification must confirm that static analysis is green. This earlier run is not final
acceptance of the changed head or the unfinished native forms.

## Native management checkpoint

Nine HTTP assertions failed before the native routes existed. The native implementation then
passed those cases. Three additional assertions demonstrated that missing version tokens were
being treated as the initial zero; required nullable version fields now reject that omission
before any mutation. Specific form messages preserve rejected raw dates without exposing Java
type names.

The final focused native run passed 19 unit, 34 integration and four browser cases, with zero
failures, errors or skips. Fourteen integration cases use the production security profile and
prove management authentication, CSRF rejection, tokens inside the actual new forms, recorded
authenticated actors and retained validation errors. Twenty management cases cover regular and
standalone playoff bindings, canonical aliases, future confirmation, missing/ambiguous/past
suggestions, invalid offsets/dates, required reasons/versions, stale edits, expiry, closed-window
reopening rejection, escaped active/withdrawn records and both existing pairing-page links.

The browser uses JavaScript-disabled contexts at 1440×1000 and 390×844. Native binding, confirmation
and deadline change work, long private text wraps within the viewport, stale input survives until
explicit reload, and expired pages hide mutation controls. Desktop/mobile screenshots were
visually inspected; `.screenshots/incident-management-{desktop,mobile}.png` and the stale-mobile
variant are local evidence. Their reports and parties are synthetic.

A nonexistent planned wall time written through the legacy Race timestamp mapping normalizes
during persistence. The integration fixture does not pretend that normalized value is still a
gap. Direct domain tests cover planned-gap handling; the native integration submits an actual
nonexistent Berlin deadline and requires rejection before persistence. Existing calendar/storage
timezone behavior remains outside this change.

Implementation is ready for self-review and the independent security pass. Individual semantic
guard witnesses, committed-head full verification, green CI and the actual merge are still
required. Wiki publication and tracker/branch cleanup follow the verified merge.

## Review corrections

The independent read-only security pass reviewed base `097d4939` through committed head
`80da0365`. It identified two introduced blockers. P1: under MariaDB repeatable read, closure
could miss a first report committed while it waited and close without freezing that report.
P2: deleting a bound context's last race before its first report stranded the permanent window.
Four integration assertions reproduced these failures with zero JUnit errors.

Closure now queries reports with a current write lock and refreshes their entities before
freezing. Bound context dependencies protect race deletion before any report exists, including
standalone playoff matchday deletion. The shared restore/audit validator also checks reportable
race ownership for contexts without reports. Its four new malformed-context cases failed before
implementation and now reject empty, bye, foreign-season and mismatched-participant contexts.

The correction run passed 69 unit and 72 integration cases, with no failures, errors or skips.
Each database ran sixteen identity and ten concurrency cases plus nine backup cases; the
read-only audit also passed. The new first-submission witness requires the committed latest
revision to be frozen, allowing only a domain retry with unchanged state before a fresh attempt.

CI on `ad33866d` cleared the original eleven static findings but reported overlapping default
prefix suppression for the two representation warnings. Their annotation now uses EXACT matching;
the detector and all other rules remain enabled. Final full verification still must confirm it.
