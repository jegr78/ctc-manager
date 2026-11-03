# Private incident intake and reporting-party access

Issue #289 implements the second deliverable of #287. Selection verified #288 and #282 are
closed, #289 is open and unassigned, and its parent has no milestone restriction. No open PR,
current incident branch, additional worktree or sibling Claude workspace collides with this work.
The latest local roadmap is shipped; GitHub defines the current delivery order. User-owned
deployment work #301 and its parent #294 are excluded. The assigned dependency dashboard is not
an implementation candidate.

Selection base: `4a20db629d0e5415b12735b506fa28f70446cebf`.

## Delivery checklist

- [x] Verify current issues, prerequisites, release order and parallel work.
- [x] Establish the requirements matrix and inspect the existing Gateway/access contracts.
- [x] Observe intended failing role/access and private interaction tests.
- [x] Resolve exact canonical contexts and current participant/organization roles.
- [x] Recheck scope, creator and original-party eligibility for private reads and writes.
- [x] Implement private transient drafts, explicit confirmation and My reports.
- [x] Preserve versions, strict cutoff, durable replay and one first-submit receipt request.
- [x] Verify real transaction/restore admission and production-shaped Discord payloads offline.
- [x] Falsify the new guards individually from a correct committed checkpoint.
- [x] Update README, CLAUDE and the user-facing Wiki draft.
- [x] Complete code review and an independent read-only security pass.
- [ ] Integrate current upstream and run one final committed-head clean verify with E2E and Docker.
- [ ] Pass all applicable exact-head CI checks, verify merge and reconcile the tracker/Wiki.
- [ ] Remove the proven landed topic branch and continue the queue.

## Requirements matrix

| Requirement | Production path | Positive witness | Rejection witness | Runtime evidence |
| --- | --- | --- | --- | --- |
| Exact channel context | IncidentAccessService and canonical context resolver | Regular and standalone playoff channels identify their one saved context | Wrong guild, duplicate legacy links, forged context/window and auxiliary ownership ambiguity reject | Unit access cases plus persisted regular/playoff interaction fixtures |
| Current reporting party | IncidentAccessService and current Discord member/permission source | One participant role among unrelated roles; inherited role; shared organization role produces one organization | No relevant role, distinct opposing roles and a role shared by unrelated organizations reject | Synthetic role matrix and production-shaped permission responses |
| Fresh authorization | IncidentReportAccess adapter and private read service | Original creator with current access reads/edits/withdraws their original party | Lost channel access or original-party eligibility denies future access; another member cannot read or mutate | Access-revocation and cross-user transaction cases |
| Frozen original attribution | Existing IncidentReportService and immutable party snapshot | Role/label changes do not rewrite a stored party | A changed party ID/type cannot replace original attribution | Existing foundation regressions plus private-handler persisted history |
| Shared Gateway | Existing transport, dispatcher and ephemeral response boundary | One connection routes picks and incident interactions | Unknown prefixes, wrong guilds and stale restore generations grant no access | Existing Gateway regressions and incident routing cases |
| Transient drafts | IncidentDraftSessionStore and IncidentInteractionHandler | Choose concrete race, collect the four source fields, review and explicitly confirm | Expiry, component spoofing, cross-user/channel/race actions and abandoned drafts create no case | Focused handler cases and persisted zero-write assertions |
| Private transport | Shared private acknowledgement/modal/continuation mechanics | Entry, validation, preview and committed receipt are ephemeral from the first response | No ordinary response fallback, payload logging, personal metadata in component IDs or slash arguments | Production-shaped transport fixtures and captured outbound payload assertions |
| My reports | Authorized private read projection and handler | Only the invoking creator sees their saved active/withdrawn cases and current versions | Foreign IDs and shared team roles never disclose another creator's undeclared case | Unit boundaries and real database interaction transactions |
| Versioned edit/withdraw | Existing IncidentReportService with base report/window versions | Valid current edits append history and withdrawal retains revisions | Stale versions and exact-deadline mutations reject without changing the last accepted case | Access-aware transaction/cutoff/concurrent interaction fixtures |
| Durable confirmation | Existing immutable SUBMITTED event/request identity and handler receipt | First confirmed submit yields one durable neutral-delivery request; replay returns its original receipt | Double clicks, lost replies, edits and withdrawal never create another report or early notice | Persisted event/request counts, restart/replay and transaction rollback assertions |
| Restore admission | Existing ImportLockService, write executor and session epoch | Complete Gateway work joins writer admission and releases it on every outcome | Restore waits for accepted work; in-flight/restored stale sessions cannot mutate or disclose data | Real barrier/transaction fixtures on H2 and MariaDB where isolation matters |
| Discord limits | Private views and opaque session choices | Long cases, multiple reports/races and all fields remain reachable within response limits | Invalid selector values and over-limit transport payloads reject safely without silent case truncation | Unit pagination/length cases plus production-shaped interaction harness |
| Historical integrity | Existing backup/restore and read-only incident audit | New submissions retain the same permanent schema/history contracts | No current-role lookup repairs or rewrites historical party snapshots during audit/restore | Existing incident backup/audit regressions alongside the new access fixtures |

## Scope and verification

Reuse the delivered transport and its one Gateway connection. The picks' private team-channel
binding policy is not incident authorization. Incident context comes from the actual match
channel; participant effective role IDs and the invoking member's current roles determine the
party. Buttons and names are never authority. A supplied window must belong to that context.

Drafts remain transient. Only explicit confirmation calls permanent submit. Saved-case reads
and every submitted edit/withdrawal recheck current authorization; no synchronous modal-opening
path may disclose saved private text without authorization. Keep component identities opaque,
user/channel/context/race bound and invalidated after restore. Honor the existing private
acknowledgement deadline and bounded worker mechanism.

The immutable first SUBMITTED decision and its unique request identity are already durable.
Inspect the #290 delivery contract before wiring its neutral-delivery request representation.
This issue does not send a neutral channel notice or create incident/stewards threads. Those
belong to #290 and #291. Ruling management remains #292, and operator activation/recovery
acceptance remains #293. No live guild registration, messages, real reports or production
activation is authorized for tests.

Keep new persistent shape, if required, aligned with migration, backup serialization/restoration,
preflight and the read-only audit in the same delivery. Current-role authorization must not
invent retrospective attribution or introduce Discord calls into historical audits.

Use focused lifecycle tests while implementing, then review before the expensive final run.
The final committed head must pass `./mvnw clean verify -Pe2e -Ddocker.available=true`, minimum
82% line coverage and all static gates. Test data and external responses are synthetic/offline.
No direct Maven plugin goals, skips, concurrent Maven processes or source edits during a build.

## Focused implementation evidence

The access matrix first produced 25 intended assertion failures with no JUnit errors or skips.
The current-channel permission source then produced 23 intended failures; role/access cases
were already green. The shared permission evaluator preserves the private-channel rule for
picks while incidents can use an ordinary visible match channel. The combined run passed 108
unit cases, including the production-shaped Discord REST contract and existing foundation tests.

The private read contract produced ten intended assertion failures before implementation. Its
combined run passed 118 unit and 24 integration cases, with no failures, errors or skips. It
queries cases by window and author, checks original-party eligibility and loads payloads only
after authorization. Existing native management and permanent history integration cases passed.

The transient session/private dialog contracts produced 26 intended assertion failures. Three
new routing assertions demonstrated that the shared dispatcher did not yet route incidents.
After implementation, 88 unit cases passed with no failures, errors or skips, including the
existing picks sessions, replies, dispatcher and interaction handler. The new cases cover opaque
scoping/expiry, explicit confirmation, durable request reuse, private previews, creator-specific
views, retained base versions, restore refusal and paged long text.

These are intermediate checkpoints. Real transaction/interaction witnesses, independent review,
individual guard falsifications and the final full verification remain required before delivery.
The immutable first SUBMITTED event is the durable neutral-receipt request supplied to #290;
only that later delivery unit adds the outbox and sends the notice. This handler confirms storage
and reports pending receipt delivery. Edits and withdrawals append their own history without
another SUBMITTED request. No new persistent shape is introduced here.

## Real interaction and current-role checkpoint

The first real shared-dispatcher run passed 208 unit cases and twenty of twenty-one H2
interaction cases. Its failure showed truncated race choices losing the leg number. Labels
now put the leg first and retain surrogate boundaries when shortened.

A configuration-change barrier then reproduced an accepted report after its configured role
was revoked while the writer waited. Context locking now refreshes participant and parent role
metadata through current read locks after season/guild admission. MariaDB also exposed reuse
of a FOR UPDATE legacy lookup from a read-only authorization transaction, SQLState 25006.
Read-only access now uses its own non-locking query; current write-side channel checks remain
locked. Both failures are fixed in the current source.

The combined run passed 208 unit and 44 integration cases, twenty-two each on H2 and MariaDB,
with zero failures, errors or skips. It exercises the actual configured shared dispatcher,
private acknowledgement port, Spring authorization adapter and committed report transactions.
It covers exact cutoff for all three mutations, original receipt replay at cutoff, competing
edits, revoked permissions, changed configured roles during waits, inherited/shared organization
attribution, standalone playoff scope, foreign controls/windows, ambiguous legacy links, frozen
original attribution and writer draining across the complete private operation.

The role contract was also corrected against #287: one known matching participant role is
sufficient even when the other participant's role is unconfigured. Five assertions demonstrated
the earlier restriction and missing private explanations. Fixed typed messages now explain
role ambiguity without echoing private inputs or upstream exceptions. A production-shaped
Discord JSON contract proves visible ordinary match channels and later member overwrite denial.

The harness uses the production interaction DTO and real transaction/dispatcher boundaries with
synthetic permission facts and a fake private response port. The shared SDK response tests cover
its actual ephemeral/mention-suppression translation. The restore-generation witness exercises
the shared epoch hook; full archive and deployment acceptance remain #293's deliverable.

An additional private-read boundary test denies loss of original-party eligibility before any
payload query; all eleven private-read cases pass. Thirty-three shared Gateway/SDK cases also
pass, including actual JDA modal serialization of the four incident fields, ephemeral immediate
and deferred replies, mention suppression, outgoing reconnect/shutdown and restore invalidation.
README, CLAUDE, domain vocabulary and operator instructions are updated. Four current Wiki pages
are prepared for publication after the verified merge. Command registration remains an explicit
operator API action after #293 acceptance; no live registration or activation was performed.

## Review and authorization boundary checkpoint

The self-review and independent read-only security review of production head
`1d34f7c3a376cf1917d277382b5f3d290897bb1e` found no outstanding findings. The review
traced current authorization, creator-scoped private payload reads, transient actions,
restore admission, final write revalidation and the shared picks permission policy.
CI configuration was inspected; exact-head results remain required before merge.

Test-only follow-up leaves that production source unchanged. Thirty-eight unit and four
integration cases passed for current configured-guild denial and team/parent role changes
during the season-lock wait. Another thirty-eight unit and four integration cases passed
for direct same-role foreign-creator payload rejection and a changed reporting party after
review. Both database runs used H2 and MariaDB, with zero failures, errors or skips.

## Individual guard falsification

All 27 probes started from correct committed checkpoint `f43977eb`, changed one semantic
protection at a time and restored the exact committed source after the intended red assertion.
Every probe had at least one intended assertion failure and zero JUnit errors or skips. The
working tree was clean after all restorations. Mockito's explicit no-write assertion is counted
as an assertion failure, not a technical test error.

| Protection | Independent probes |
| --- | --- |
| Current participant role, opposing/shared roles, configured guild, canonical ownership, window scope, actor candidate/context and original party | 9 |
| Current private-read access, original-party index, creator-scoped payload query and eligibility before loading text | 4 |
| Draft user/channel/guild, restore generation, exact expiry, action kind, invalidated stage and capacity | 8 |
| Reviewed party, complete writer admission/release, explicit confirmation and complete private pagination | 5 |
| Current configured role metadata after the season/guild wait on MariaDB | 1 |

The creator and reviewed-party probes used real persisted interaction transactions. The writer
admission probe removed registration and observed the restore drain proceeding too early. The
metadata probe removed the post-wait refresh and observed unauthorized storage. Correct code
and the complete positive/negative interaction matrix are checked again by the final clean build.

The remaining unchecked delivery gates above describe the state of this committed preflight
record. Final local test totals, coverage, exact-head CI and merge/housekeeping evidence are
recorded in the pull request and issue #289 without changing the verified source head.

## CI publication-order correction

The initial full local run passed on `7d9293a3`, but CI run `41706806719` exposed six
interaction failures across H2 and MariaDB. Immediate follow-up controls could arrive while
the preceding operation still held its transient session lock. The test client made that
publication race visible; it was not a reason to ignore the failures or increase a timeout.

A deterministic two-variant test resolves the newly published button/modal action on another
thread before the publication callback returns. Both variants failed at the intended assertion
with zero JUnit errors/skips. The handler now prepares its private message or modal under the
session lock and publishes it after releasing that lock. Complete writer admission and guarded
private transport still surround publication. Authorization, versions, confirmation and history
rules remain unchanged.

The focused recheck passed 56 unit and 48 integration cases, twenty-four each on H2 and MariaDB,
with zero failures, errors or skips. Independent review, two publication-order falsifications
and the final local/CI verification of the corrected committed head follow this checkpoint.

The self-review and independent security delta review of committed correction `bbc08f49`
found no outstanding findings. Prepared replies remain private, writer admission surrounds
forwarding, and current authorization, original-creator isolation, versions, cutoff and restore
generation checks remain intact. Both individual publication-order mutations produced the
intended failure, with zero errors/skips, and restored a clean committed checkpoint. Together
with the preceding 27 probes, 29 independent protection probes are recorded. The corrected
head receives a fresh canonical local build and exact-head CI in parallel before merge.
