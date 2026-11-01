# Reviewed car metadata, issue #274

## Requirements and verification matrix

| Criterion | Production path | Positive test | Error-path test | Runtime evidence |
| --- | --- | --- | --- | --- |
| Official category and drivetrain by GT7 ID | Gt7ScraperService, CarMetadataService | offline Gr.3/FR, RR, AWD fixtures | unknown markers and misleading names stay unknown | captured official bundle parsing |
| Four-digit candidate years with evidence | GtPlusCarYearSource, CarMetadataService | 1929, 1999, 2000, 2016, car3531 and reviewed corrections | malformed/conflicting records, suffix mismatch, source failure | offline chunked/escaped flight fixtures |
| Field-level merges preserve manual decisions | CarMetadataService, CarService | independent attributes and explicit cleared year | untrusted changes never replace accepted years | unit and controller checks |
| Existing catalog enrichment preserves identity | Gt7SyncService and preview | selected metadata updates, retained UUID/image/links | ambiguous name/ID matches and changed previews require review | focused preview/execute integration checks |
| Native labeled edit/review forms | CarForm, CarController, sync templates | manual review and update selection across pages | binding/domain errors retain submitted form | desktop/mobile browser screenshots |
| Full persistent representation restores safely | V23 migration, CarMixIn, CarRestorer | nullable years, provenance and explicit unknown round trip | invalid values fail and legacy absent fields default safely | H2 and MariaDB checks, complete backup round trip |

## Source policy

Official GT7 carClass and driveTrain fields supply classification. GTPlus supplies
candidate years joined by integer GT7 ID, without evaluating downloaded JavaScript.
A matching two-digit suffix corroborates a four-digit source value; it never
constructs its century. IDs car3599 and car3606 have reviewed 2025 corrections.
A no-suffix or conflicting candidate remains available for manual review.

The parent issue records the 2026-10-31 reconciliation: 580 ID matches, 454 integer
years, 126 nulls, 426 suffix matches, two missing years and 28 review candidates.
Those are historical observations, not fixed expectations for a changing catalog.

Sources: https://www.gran-turismo.com/gb/gt7/carlist/ and https://gtplus.app/gt7/cars.

## Pre-merge checklist checkpoint

Delivery and tracker completion are recorded on [issue #274](https://github.com/jegr78/ctc-manager/issues/274) after merge.

- [x] Audit scraper records, preview constructors, sync/save calls and backup fields.
- [x] Observe failing parser, merge, preview and manual-form tests before implementation.
- [x] Implement bounded source parsing, validation and field-level provenance.
- [x] Verify identities, manual overrides, selection state and preview consistency end to end.
- [x] Verify migration/restore and desktop/mobile/native form behavior.
- [x] Review code and security, fix findings and falsify new guards individually.
- [ ] Run committed-head `clean verify -Pe2e`, wait for CI and merge.
- [ ] Reconcile #274 and parent #273, update Wiki and remove the landed branch.

## Captured source qualification

The parser was run against the saved real HTML and official bundle before UI
integration: 583 stock candidates, all 580 official IDs matched, 454 candidate
years. Engine-swap variants were excluded. Automated tests use reduced offline
fixtures rather than this changing live response or hard-coded catalog totals.

## Review and runtime evidence

The independent security pass found an entity load before the car row lock in the
manual save path. A two-transaction H2 regression reproduced a lost source year
without an override. Loading and locking the car before the duplicate-name query
fixes that interleaving; the regression and subsequent security review pass.

Focused checks include a complete backup round trip, unchanged persisted
season/race references, source and local-state preview invalidation, explicit
unknown years, and selection retention over multiple pages. The native browser
flow saved a manual 2020 year, cleared it with its override retained, then applied
two existing-car metadata updates with zero new imports. Desktop 1440×1050 and
mobile 390×844 views were inspected through playwright-cli. Screenshots remain
in the ignored `.screenshots/` directory.

## Individual guard falsification

37 independent mutations were applied to committed checkpoints, observed red at
behavioral assertions, and restored byte for byte. They cover field overrides,
explicit unknown years, accepted-year preservation, century handling, source ID
joins/types/ranges, candidate integer/range validation, payload/frame bounds,
preview revalidation and store hash/expiry/capacity, restore value/provenance/flag
validation, all four database constraints, local/name identity ambiguity, and the
car lock order, fresh locked reads and timestamp precision. Compilation errors and unprepared mock exceptions did not count
as evidence. The lock-order mutation reproduced the lost-update assertion with
two real transactions after the correction had been committed.

The final authoritative run uses `./mvnw clean verify -Pe2e` on the restored,
committed head. Surefire runs untagged tests; Failsafe runs integration tests and
the profile's separate Playwright execution. Both Failsafe summaries, coverage,
SpotBugs and FindSecBugs must pass before the PR is published.

The first authoritative full run ran 2,139 unit tests, 766 integration tests
and 200 Playwright tests without failures or errors, with five existing skips
and 91.94% line coverage. SpotBugs then reported
`CT_CONSTRUCTOR_THROW` on the preview store's two constructors. Making that
component final prevents subclass finalizers from observing partial construction;
no analysis rule was suppressed. The authoritative build is repeated on this
corrected committed head before publication.

## Fresh locked state

A further two-transaction regression showed that a pessimistic query can return
an already managed, stale Car. This is reachable when an earlier lookup has
populated the persistence context, including an ambiguous-name lookup earlier in
a sync batch. `CarLockingRepository` now locks and refreshes matching rows before
any write policy reads them. The tests verify stale-preview rejection and an
explicit unknown override after a concurrent accepted year, with persisted manual
fields and images unchanged.

Snapshot hashes cover identity, image, metadata and override values. They exclude
technical auditing timestamps, whose database precision can change on refresh
without a business change. A deterministic rounding test also confirms that
changing an override still invalidates the snapshot. The second full run was
green before this additional correction; the final full run includes it.

Disabling the locked refresh reproduced both real-transaction failures. Reintroducing
audit timestamps into the hash failed the deterministic rounding assertion. Both
additional mutations were restored before the final build.
