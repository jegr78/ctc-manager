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

## Workflow checklist

- [x] Audit scraper records, preview constructors, sync/save calls and backup fields.
- [x] Observe failing parser, merge, preview and manual-form tests before implementation.
- [x] Implement bounded source parsing, validation and field-level provenance.
- [ ] Verify identities, manual overrides, selection state and preview consistency end to end.
- [ ] Verify migration/restore and desktop/mobile/native form behavior.
- [ ] Review code and security, fix findings and falsify new guards individually.
- [ ] Run committed-head `clean verify -Pe2e`, wait for CI and merge.
- [ ] Reconcile #274 and parent #273, update Wiki and remove the landed branch.

## Captured source qualification

The parser was run against the saved real HTML and official bundle before UI
integration: 583 stock candidates, all 580 official IDs matched, 454 candidate
years. Engine-swap variants were excluded. Automated tests use reduced offline
fixtures rather than this changing live response or hard-coded catalog totals.
