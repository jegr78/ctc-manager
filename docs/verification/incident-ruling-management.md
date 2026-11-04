# Permanent incident cases and steward rulings

Issue #292 follows delivered #288 and #291 / PR #334. Selection base:
`bc68b3fb1ac276116c3571eb60716b7f9cd8f1dc`. Parent #287 has four of six completed
children and no milestone. The latest local roadmap, v1.17, is shipped. No open PR,
corresponding remote branch, sibling Claude checkout or additional worktree collides.
The assigned dependency dashboard is excluded; #293 awaits this implementation.

## Workflow checklist

- [x] Recheck tracker, parent chain, dependencies, release order and parallel work.
- [x] Map the full case/ruling contract and parent identity refinement to meaningful witnesses.
- [x] Observe intended unit and runtime assertion failures before implementing new behavior.
- [x] Implement permanent case history and explicit versioned ruling publication/recovery.
- [x] Preserve frozen public case identity, archive compatibility and read-only integrity checks.
- [x] Verify affected native E2Es and static gates early.
- [ ] Falsify new protections individually from the correct committed checkpoint.
- [x] Complete self-review and independent security review, fixing every validated finding.
- [ ] Run the canonical clean E2E verification on the committed final head, then PR/CI/merge.
- [ ] Reconcile the complete issue hierarchy, Wiki and own local branches before the next issue.

## Requirements matrix

| Contract | Production path | Positive witness | Failure and runtime witness |
| --- | --- | --- | --- |
| Permanent match-linked case | Existing canonical incident overview + new IncidentCaseController/read-model service | Active and withdrawn reports retain the same case, concrete race, deadline, frozen revision and both known thread URLs | Regular, standalone playoff and auxiliary-match views resolve the same case; missing/foreign identity fails without exposing another case; real H2/MariaDB |
| Complete internal history | IncidentReport/Revision/Event repositories + typed management DTOs | Original party/author, immutable revisions, withdrawals and ruling history remain readable after publication/archive/restore | Escaped multiline facts and attribution stay internal; participant projections and Discord output contain no appended personal reporter metadata |
| Frozen public pairing/race identity | Versioned disclosure projection and captured delivery evidence | Detailed public payload explicitly identifies its pairing and concrete race/leg alongside all four frozen source fields | Later renames/reordering cannot change queued or reconciled content; known schema-10 success/uncertainty retains compatible evidence; production-shaped HTTP and archive witnesses |
| Local draft and public preview | IncidentRulingService.saveDraft + explicit native DTO forms | Saving retains exact versioned text/author/time/reason and an escaped preview | Save, window close and thread replies send nothing; no automatic ruling inference; real transaction proxies and zero-HTTP assertions |
| Explicit exact-version publication | IncidentRulingService.publish + permanent delivery state | Management consent captures the requested ruling version and known match incident thread | Changed draft, stale version/generation, unpublished/foreign/deleted target or absent consent rejects before HTTP; prior confirmed ruling stays intact |
| Controlled correction | Immutable ruling versions and recorded known bot message | Explicit correction with reason edits the verified known ruling post and retains old text/version/reason internally | Wrong author/channel/case, missing reason, failed or uncertain edit cannot overwrite the previous confirmed local ruling; real-client PATCH contract |
| Durable bounded delivery | Existing writer admission/epoch/transaction pattern + focused ruling sender | Captured payload, lease and per-attempt authority survive restart; HTTP occurs outside SQL | Competing claims, late requests, remote success followed by SQL failure and unknown POST/edits preserve uncertainty and stable IDs; H2/MariaDB and WireMock |
| Verified archived-target recovery | Existing current thread metadata, bounded unarchive and message reads | Exact existing result is adopted without posting; an authorized archived target can reopen before explicit publication | Deleted/mismatched/ambiguous evidence remains unresolved; no replacement thread or private/public fallback; stale restore evidence never silently reposts |
| Internal management boundary | Existing /admin authentication/CSRF, trusted principal and typed versioned forms | Native save/preview/publish/reconcile actions derive the management actor server-side | Forged actor/state fields, missing versions, unauthenticated requests and team Discord interactions cannot authorize publication; production-profile route IT |
| Restore, backup and read-only audit | New migration + typed export/MixIns/restorers/preflight + IncidentIntegrityAuditService | All ruling versions, exact publication evidence and case links round-trip; restored contexts remain paused | Invalid ownership/pointers/destinations/operational grants reject before wipe; old archives invent no ruling or new-format proof; audit reports without repair |
| No scoring side effects | Ruling service has no scoring/result mutation path | Draft, publish, correction and reconciliation leave race results, penalties and standings unchanged | Real transaction-proxy IT compares stored scoring state before/after accepted and rejected actions |
| Native usable UI | Existing fieldsets, error panels, contextual links and case/ruling templates | Desktop/mobile, keyboard and no-JavaScript forms retain values and show separate report/thread/ruling states | Stale submissions retain entered values with reload/back links; distinctive script text is escaped; existing incident-management E2Es run before the expensive final build |

## Existing contracts to preserve

The Manager retains report facts and explicit rulings, not a mirror of Discord discussion or
footage. Rulings publish only through an explicit management action into the stored match thread.
There is no automated adjudication, new web account system or scoring integration.

`IncidentAdminService` already canonicalizes regular/playoff overview routes, derives trusted
management principals and prepares native deadline forms. Its current CaseView needs a stable
report ID for detail links. `IncidentReportRevisionRepository` provides ordered immutable history.
`IncidentThreadPublication` owns known private/public references and public completion evidence.

The bot RestClient already provides bounded create/get/thread operations and per-attempt send
deadlines. Existing message edits use webhook transport; verify the actual bot-message PATCH
contract before adding the missing capability. Preserve allowed-mentions suppression and the
existing receipt-specific labels. Reuse current window/report locks and writer admission, with
current reads after waits and no SQL transaction spanning external HTTP.

Released V31 and schema 10 are immutable compatibility boundaries. New persistent shapes need
the next upstream migration/schema version, explicit restorers and matching preflight/audit.
Do not rewrite old known remote content by reconstructing identity from later mutable metadata.
Restored ruling work must join the existing paused-context and fresh remote-review protocol.

## Verification order

Begin with the requested IncidentCaseControllerTest and IncidentRulingServiceTest contracts,
then real transaction and managed HTTP witnesses. Run affected existing/new native E2Es before
the expensive final build, including the six existing IncidentManagementE2ETest cases. Observe
each new protection fail at its intended assertion from a committed correct checkpoint, then
restore exact bytes. Complete code/security review and focused static verification first.

The final authoritative command is `./mvnw clean verify -Pe2e -Ddocker.available=true`, on a
committed head with current upstream. No source/resource/HEAD edits or concurrent Maven while
that run is active. Verification uses synthetic data and fake Discord only; live reports,
rulings, command registration, production activation and scoring changes remain operator work.

## Local implementation evidence

Local draft/case foundation: 85 focused unit contracts and 68 integration contracts pass.
The latter include permanent active/withdrawn history, native draft/preview and stale forms,
production authentication/CSRF with trusted principal attribution, schema topology and typed
backup round-trip with unchanged revision IDs and paused restored case contexts. Twelve new
malformed ruling archives first failed at their intended rejection assertions and now pass.
The first real database run exposed an empty aggregate insert before its first revision;
creating that first revision before saving the root fixes the ordering without weakening DDL.
Publication, correction and reconciliation were completed in the later checkpoint below.
These foundation counts are not final branch verification or a claim of issue completion.

## Review and verification checkpoint

The implementation includes native case/history/draft/preview/publish/review flows, durable
claims outside SQL, known-message corrections, opt-in worker integration and fresh ruling
review before restored contexts resume. V32/schema 11 exports 46 entities. New disclosure
captions are frozen before first persistence; null-caption legacy envelopes retain their
original bytes, including real schema-10 archive recovery.

The independent security pass found two archive contradictions: unresolved work could retain
an outdated prior confirmation, and confirmed rulings could claim existing receipt/thread
IDs. Five intended failing unit assertions preceded the fixes. Current nonterminal work now
matches the aggregate's approved/current confirmed version, ordered SENT work preserves the
preceding confirmation, and recorded receipt/thread IDs remain reserved. Stage and execute
reject both corruptions before wipe on H2 and MariaDB. The independent fix recheck found no
remaining finding. The first confirmation may skip drafts and superseded work.

Focused checks include 118 unit and 140 integration cases, with 13 ruling and 36 archive
cases on MariaDB; all passed. Real bot HTTP checks include frozen public captions and explicit
mention suppression. Ten native desktop/mobile cases pass without JavaScript, including the
six existing cases, explicit publication and unknown-outcome review. Further guard-contract
witnesses test attribution before private reads, malformed adapter results and inventory
bounds. These overlapping targeted counts are checkpoints, not a final-suite total.

Standalone static preflight uses SpotBugs 4.10.5, FindSecBugs 1.14.0, Max effort, Medium+High
and the unchanged repository exclusion file. It reports zero findings, errors and missing
classes with FindSecBugs enabled. A constructor warning was fixed by validating the permanent
report in a factory before object creation. The final authoritative Maven gates remain
required; this preflight does not replace them.

Guard falsification uses one body mutation at a time from the committed correct checkpoint,
then restores exact bytes. Timestamp compilation is restricted to these ABI-preserving
mutation loops (`-Dmaven.compiler.useIncrementalCompilation=false`); tests and lifecycle gates
remain enabled. The final clean command uses the default compiler mode and all required gates.
The checklist is a pre-merge snapshot; the issue/PR record provides the final committed-head,
canonical, CI, merge, Wiki and hierarchy evidence. Upstream advanced only the release version
to `c8671167` / 1.44.0-SNAPSHOT; adopt it before final verification.

A final write/audit consistency witness confirms that local draft history keeps the server's
actual Clock observations when time moves backwards. Revision numbers establish its order;
all timestamps remain format/range checked. Publication cutoff, snapshot time and claim lease
checks remain enforced. The earlier monotonic draft-time preflight constraint rejected valid
producer output and was removed after an intended failing read-only audit assertion.

The first complete pre-commit run exposed two legacy schema-error assertions that still
listed supported versions only through 10. Their expected wire error now includes 11; invalid
archives still reject before wipe. The duplicate-publication archive fixture also gives its
aggregate the matching decision count so the uniqueness witness cannot be masked by the
independent version-count check. These changes affect tests/documentation only.

Corrective targeted `verify -Pe2e` passed 82 archive unit cases, both schema-error integration
cases and all ten incident-management E2Es. Maven coverage, SpotBugs and the FindSecBugs-loaded
guard passed, with zero static findings. The preceding broad run completed all 238 E2Es green;
its two stale error-message assertions are now corrected. This is pre-commit evidence, not
final clean verification of the eventual committed head.

## CI migration-session regression

The first PR CI run found a valid publication rejected by H2 after the migration session
closed. Its cause was `ConditionInConstantSet` retaining that closed session, not an invalid
ruling state. V32 had missed the repository's existing CASE-membership convention. A new
direct-JDBC V32 witness closes the migration connection before inserting a valid publication;
it first failed at the intended valid-write assertion, with one failure and zero errors.
The unreleased V32 constraint now uses the same CASE form as V30. The witness also checks all
six supported states and rejects unknown/lowercase/empty values from the new connection.
No released migration, schema version, supported state or application behavior is relaxed.

The corrective targeted verify passed the new JDBC case, 23 ruling-model cases, six case
management cases, 27 H2 ruling delivery cases, ten native E2Es and Maven static gates.
An explicit `-Ddocker.available=true` run also passed all 27 MariaDB ruling delivery cases
with zero skips. The independent read-only delta review found no weakening or remaining
finding. The final clean verification and CI must still pass on the fixed committed head.
