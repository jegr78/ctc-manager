# Manual Swiss rounds

Issue #409. Manual pairings record historical truth; automatic pairing admission stays unchanged.

| Requirement | Production path | Positive test | Failure-path test | Runtime evidence |
| --- | --- | --- | --- | --- |
| Phase/group-scoped manual round entry before earlier results | Swiss round read model and native editor | Exact external pairings in multiple open rounds; league and group scopes | Wrong format, phase/group, duplicate round, invalid number | Service integration and native controller |
| Whole-round validation | Shared round policy | Even and odd roster, one explicit bye, missing participant summary, rematch warning | Self/duplicate/foreign participants, duplicate pair, invalid bye; row errors retain submitted values | Pure policy plus rendered controller |
| Historical replacement identities | Round membership uses owned season succession and concrete group places | Preserve actual predecessor team IDs while future generator resolves their opponents | Foreign/cyclic succession or predecessor/successor occupying one round place twice | Policy and service integration |
| Atomic fresh writes with restore/stale protection | Writer admission, season lock, scope fingerprint and generation | Concurrent edit serializes, stable unchanged Match/Race IDs | Stale scope/generation and restore contention cause no partial writes | H2 and real MariaDB concurrency |
| Preserve history-bearing identity | Lock reasons and lifecycle guards | Unchanged protected rows retained while other rows change | Driver results, quick scores, walkover, lineup, picks, incident, penalty, DSQ prohibit rewrite/removal | Integration fixtures and independent security review |
| Maintain configured legs and orientation | Match/Race creation and in-place change | Cross-match swaps; one/two legs; bye one leg | Invalid assignments roll back before mutation | Service integration |
| No implicit side effects | Manual round write and automatic generator | Saved opponents feed normal generator and standings | No publication, next-round generation, picks opening or invented results | Integration/mock interaction checks |
| Read-only diagnostics and native usability | Data Audit and round form | Duplicate usage reported; desktop/mobile editable rows and back links | Audit leaves records unchanged; no horizontally inaccessible controls | Audit integration and browser screenshots |

Run focused helper checks with bounded heaps locally. Full exact-head inventory, H2, both MariaDB groups, browser, coverage and security gates run in CI. Commit correct source before independently falsifying each new guard. Preserve archive schema because this workflow only writes existing entities.

## Focused evidence

- Initial policy probe: 13 expected failures and two passing cases before implementation; all 15 policy cases then passed.
- Initial native workspace probe returned 404 before the new route existed. Current native forms cover readable row errors, retained values, prefill and malformed phase input.
- Final desktop/mobile run: 41 unit, 63 integration and one Chromium case passed. Screenshots were inspected at 1280 and 390 pixels; scoped checkbox/action spacing was corrected and rechecked. Data Audit's 37 cases passed and retain their rollback-only synthetic fixtures.
- Expanded guard run: 67 unit and 29 integration cases passed, including ten concurrency/admission cases. The same concurrency root is inherited by a real MariaDB container test in complete CI.
- Automatic append-order probe failed at the expected `sortIndex` assertion before the correction. Stored custom ordering now precedes the next generated round; the normal completion gate remains in place.
- Independent read-only security review identified shared-lock gaps for metadata/artifact writers and missing live warnings. All were corrected and checked. The old automatic phase-refresh concern was investigated with a lock-wait configuration test; current owning-season refresh preserves fresh phase configuration.
- Correct source is committed before isolated negative probes. Their exact assertions, reviewed head/base, complete CI inventory and merge evidence are recorded in the PR. Focused runs alone do not certify completion.

The external Challonge page remains unavailable to the investigation. Exact user-supplied historical pairings can be recorded, but no claim is made that the 2026 final table was independently compared with that source. No schema migration, automatic external import or deployment is included.

## Full-suite corrections

The first complete CI found two integration differences: MariaDB rejected an artifact writer's repeatable-read snapshot after a lock wait, and a transactional group-creation fixture exposed Hibernate's duplicate recursive phase refresh. Artifact writers now start READ_COMMITTED transactions while retaining existing locking/conflict rejection. Phase admission uses a current scalar locking ownership read, preserving pending roster/group entities. The previously failing group-controller cases and Swiss writes passed together: 52 unit and 28 H2 integration cases. The updated real MariaDB cases remain required in complete CI.

A separate audit regression showed that LEAGUE membership must include the complete phase roster even if old group assignments remain. The narrowed test failed before the audit filter was aligned with the editor, then passed. The focused correction run passed 47 unit and 12 integration cases. Both manual and automatic append-order overflow are explicitly rejected without creating a round.
