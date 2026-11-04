# Incident workflow acceptance

Issue #293 completes the verification and operator documentation for parent #287. Selection
base: `e09060a068fb727e3a38791aec580dadff03eb80`, after #292 / PR #335 was merged,
reconciled, documented in the Wiki and cleaned up. All prerequisites, including #282, are
closed. No milestone or parallel workspace/PR blocks this work. The assigned dependency
dashboard remains user-owned. The local v1.17 roadmap is shipped.

## Workflow checklist

- [x] Recheck tracker, dependencies, parent scope, release order and parallel work.
- [x] Map every acceptance criterion to production, positive, rejection and runtime evidence.
- [x] Inspect existing assertions and archive topology before adding missing witnesses.
- [x] Exercise the complete synthetic private-report, cutoff, disclosure, ruling and restore flow.
- [x] Verify distinctive privacy probes across public payloads, previews, site artifacts and diagnostics.
- [x] Complete explicit H2/MariaDB and native desktop/mobile targeted coverage; retain restore-admission coverage in the canonical suite.
- [x] Document setup, recovery, retained history and a separately authorized future rollout.
- [x] Complete self-review and independent security review; fix validated findings directly.
- [x] Falsify the five new audit rules individually and restore the correct checkpoint exactly.
- [x] Integrate current upstream and prepare canonical clean verification on the committed final head.
- [ ] Observe the canonical result and matching external checks on that exact head.
- [ ] Require matching green CI, verify merge and reconcile #293 through the full parent chain.
- [ ] Publish Wiki updates and remove the delivered own topic branch before fresh selection.

## Acceptance matrix

Existing test names below identify evidence to inspect, not a claim that an entire criterion
is already satisfied. Reuse meaningful existing witnesses and add only missing behavior checks.

| Contract | Production path | Positive witness | Rejection and runtime evidence |
| --- | --- | --- | --- |
| Persistent topology and wire contract | V29–V32, incident DDL, BackupSchema, explicit MixIns/restorers | All 46 tables and incident columns/FKs/keys export and restore exactly | Schema topology/guard tests, explicit INSERT audit, valid/invalid new-connection checks on H2/MariaDB |
| Complete permanent case archive | Report/window/history, linked threads, ruling revisions/publication | Active/withdrawn and shared-organization cases retain original IDs, authors, deadlines, frozen versions and known remote references | Inspect IncidentBackupRoundTripIT and its MariaDB counterpart; compare exact restored evidence and preserve unresolved partial work |
| Preflight before wipe | BackupImportService and shared IncidentBackupValidator | Supported current/legacy archives restore their actual contracts | Missing files/counts, foreign ownership, invalid attribution/pointers/targets and forged grants fail stage and execute before deletion |
| Restore writer admission | ImportLockService, shared Gateway, due worker and delivery services | Admitted work drains before replace; restored contexts stay paused | Gateway/worker admission and stale-generation tests on H2/MariaDB; no HTTP or blind resend from restored evidence |
| Read-only operational audit | IncidentIntegrityAuditService through DataAuditService | Valid history and operational evidence remain unchanged | Missing ownership, ambiguous roles, invalid pointers, expired open windows and failed/uncertain/partial/wrong-destination work produce bounded findings without writes or private payload leakage |
| Full offline workflow | Real Gateway dispatcher and incident management/domain services | Planned-start default, explicit override, private submit, one receipt, edit, withdrawal, exact cutoff, linked active disclosure, explicit ruling and permanent case after restore | Add a synthetic cross-component witness where existing tests are separate; real SQL/transaction proxies, fake Discord, H2/MariaDB |
| Deterministic attribution | IncidentAccessService and current match-role adapter | Team and shared-organization reports retain their original accepted party | Opposing/unrelated/revoked roles, another member and foreign context deny private reads/writes without changing history |
| Before-cutoff confidentiality | Actual public transport payloads, generic previews and site generation | Distinctive private facts and submitting-account metadata remain absent from public output | Probe HTML/JSON/assets, logs and errors; prove public fixtures/output exist so absence assertions are not vacuous |
| Controlled disclosure and disable/archive | Frozen disclosure projection and opted-in delivery worker | Only final active versions disclose the reporting party and complete facts after cutoff | Withdrawn/pre-cutoff/unpublished material stays private; public attribution excludes appended personal reporter metadata; disabled/archive paths cannot release it |
| Failure and stale-backup recovery | Receipt/thread/ruling claims, known-message proof and management reconciliation | Preserve confirmed IDs, partial pairs and prior confirmed rulings; adopt only verified results | Unknown response, lost acknowledgement, competing workers and expired authority remain unresolved until explicit proof; no duplicate receipt/thread/ruling |
| Canonical schedule and identity edges | Context/window lifecycle, race import and pairing guards | Regular, standalone playoff and auxiliary Match views use the same concrete original identity | Existing DST/no-start, changed deadline/old job, legacy-channel collision, role, duplicate event, result-reimport and archived-target witnesses; finish any relevant skip |
| Native management usability | Incident context/delivery/case forms and existing error conventions | Desktop/mobile and no-JavaScript controls preserve history, fields, links and explicit decisions | Stale versions, escaped multiline text, required review and trusted principal/CSRF; retain the complete incident E2E set |
| Future operator rollout | Operations guide, README, CLAUDE and Wiki | Explicit setup/permissions/opening/cutoff/recovery/history checklist with traceable offline evidence | Live test-guild UAT, command registration, production activation, real reports/rulings/scoring and retiring Forms require separate operator authorization |

## Scope and verification boundaries

Start with an evidence audit of the delivered implementation. Existing archive, role, cutoff,
delivery, concurrency and native tests already cover substantial parts of this contract. A
filename or a successful status alone is insufficient: inspect state and payload assertions.
Record remaining gaps before writing tests or changing production behavior. Fix related real
findings in this issue; do not add features or duplicate already-covered components.

The baseline is V32 / schema 11 / 46 entities. Delivered migrations are immutable. Verification
and documentation alone retain that wire contract. Introduce V33 or schema 12 only if an actual
new persistent contract becomes necessary, with its owning round-trip/preflight evidence.

Use English BDD test names, synthetic teams/data and fake Discord. Exercise real transaction
proxies and SQL; transport stubs must not replace the producer/publication services. Keep all
new before-cutoff privacy probes distinct from the deliberately public involved-driver facts
after disclosure. A ruling remains separate from scoring and Discord discussion/footage is
not mirrored into the backup.

Run focused witnesses and affected native E2Es before the final expensive build. MariaDB runs
must include `-Ddocker.available=true`; report actual executed/skipped cases. Preserve the
repository's compile-error diagnostic, coverage and static-analysis gates. Canonical command:
`./mvnw clean verify -Pe2e -Ddocker.available=true`, on a committed head with current upstream.
Do not edit source/resources/HEAD or run concurrent Maven during that build.

Delivery records offline acceptance and an explicit future operator rollout. It does not enable
production collection, register commands, send live messages or remove historical Forms.
After merge, audit #293 and every parent against their full scope before closing them.


## Evidence inspected and additional witnesses

- `BackupSchemaTopologyIT` checks the 46-table dependency order and immutable export list.
  The ten incident restorers use explicit INSERT columns. They preserve context/report/revision,
  request/operation identity, current/frozen/ruling pointers, planned-start/deadline snapshots,
  destinations and delivery evidence. Window restore explicitly pauses delivery; delivery/ruling
  review grants default false and are excluded by their MixIns. V29–V32 and schema 11 are unchanged.
- `IncidentBackupRoundTripIT` and `IncidentBackupMariaDbIT` already exercise exact active/withdrawn
  cases, revisions, original receipts, delivery outcomes, partial thread mappings, confirmed and
  uncertain ruling evidence and supported old archives. Their malformed archives reject at both
  stage and execute before wipe. Ruling and thread JSON comparisons prove retained evidence, rather
  than merely counting rows. The new acceptance story adds the combined shared-organization,
  planned-start/deadline and permanent-case witness instead of rebuilding those tests.
- `IncidentWindowServiceTest`, `IncidentPersistenceIT`, `IncidentIdentityProtectionIT` and their
  MariaDB variants cover elapsed-hour DST defaults, absent/ambiguous starts, original deadlines,
  canonical standalone/auxiliary playoff identity, legacy channel collisions, results-only import
  and rejected identity/deletion changes. `IncidentWindowConcurrencyIT` and
  `IncidentCutoffConcurrencyIT`, plus their MariaDB subclasses, assert locked current-time and
  revision state while writers compete. `IncidentInteractionIT` covers duplicate confirmations,
  creator isolation and current role rejection; its unchanged default fixture remains exercised.
- `IncidentWorkerRestoreAdmissionIT`, `GatewayRestoreAdmissionMariaDbIT` and the receipt/thread/ruling
  dispatch/recovery suites retain writer admission through HTTP and outcome commit. They test stale
  generation/lease, SQL acknowledgement failure after remote success, unknown results, partial pairs,
  verified adoption and paused restore without blind reposting. `IncidentDueWorkWorkerTest` proves
  default-off, outbound/recovery admission and bounded discovery. Real-shaped REST/sender tests
  cover bot payloads, empty allowed mentions, current private audience and archived/inaccessible targets.
- `IncidentRulingDeliveryIT` retains the existing nonzero race points and match-score snapshot
  through drafting, publication and uncertain correction. The full acceptance story additionally
  proves score restoration after deliberately changing a post-backup score.
- New `IncidentWorkflowAcceptanceIT` and its explicit MariaDB subclass exercise the real private
  Gateway, transaction services, neutral receipt adapter, frozen disclosure projection, ruling and
  complete backup stage/execute. Only external transport ports are replaced. Both TEAM and
  ORGANIZATION stories preserve original party, author, report revision IDs, history and confirmed
  thread/ruling references, then prove all restored sends remain paused. Before cutoff, distinctive
  facts and reporter identity are absent from actual receipt payloads, generic preview output,
  generated site files containing the synthetic season, captured logs and actual authorization/
  expired-confirmation errors. Public disclosure contains final facts and accepted party without
  appended personal reporter metadata or internal ruling reasons.
- New audit witnesses first failed on absent operational findings, then passed after adding fixed
  status/locator diagnostics. They check overdue OPEN windows, unrelated shared roles, FAILED and
  UNCERTAIN receipts/rulings and a valid partial thread with retained attempt provenance. Repeated
  reads preserve states, versions, revision counts and known IDs. Validation runs before operational
  inspection, and no finding contains a report payload, author or ruling text.
- `IncidentManagementE2ETest` provides native desktop/mobile and no-JavaScript binding, opening,
  deadline changes, permanent report/ruling history, draft preview, stale forms, delivery review,
  restore resumption, explicit publication and unknown-result review. No UI layout was changed.

Targeted results before the final build: audit/ruling verification ran 68 integration cases with
zero failures, errors or skips; the combined story ran four cases, including both MariaDB variants,
with zero failures, errors or skips. The independent security pass reported no findings. Final
canonical results, new-rule counterchecks and the merged PR are recorded at delivery below.

Live test-guild UAT and production activation remain unchecked operator actions in the
[incident rollout checklist](../operations/discord-integration.md#incident-rollout-checklist).
No automated evidence is presented as a live-guild test. No new issues were created.


## Pre-commit verification

The targeted `verify -Pe2e -Ddocker.available=true` passed 3,234 unit cases, 120 selected
integration cases and all ten incident desktop/mobile/no-JavaScript E2Es. SpotBugs reported zero
findings and the coverage gate passed. All selected integration/E2E cases ran without skips.
Three unchanged unit skips remain: the deferred `SeasonPhaseControllerTest` empty-state fixture
and the two manual `StandingsGraphicPreviewTest` image regenerators. They are unrelated to this
incident acceptance contract. The canonical clean suite remains the authoritative final check.


## Individual audit-rule counterchecks

After committing the correct implementation as `41d23998`, each of the five new diagnostics was
individually disabled: overdue window, ambiguous role, receipt outcome, ruling outcome and partial
thread pair. The selected witnesses produced seven intended empty-finding assertion failures,
zero errors and zero skips. FAILED and UNCERTAIN each ran for both receipt/ruling checks. After
every probe the source was restored byte-for-byte, and the final worktree matched the checkpoint.
No mutation was committed. There are no new write-admission guards, schema changes or API routes.

The external-result boxes remain unchecked in this pre-merge source artifact. The closing record
in [issue #293](https://github.com/jegr78/ctc-manager/issues/293) and its merged PR carry the final
canonical/CI, Wiki, tracker and branch-cleanup evidence. The future operator checklist remains
separate from automated acceptance.
