# Seasonal car pool verification

Issue #275 builds on the accepted local metadata from #274. Race selection and consumption
remain the next deliverable, #276. This change includes the nullable race entry reference
needed to protect used pools, and rejects unsupported category selections through the existing
fixed-car writer. CSV overwrite refuses an existing entry reference until the following delivery
can preserve its race identity. It does not infer entries for historical races.

| Requirement | Production path | Positive test | Rejection test | Runtime evidence |
| --- | --- | --- | --- | --- |
| AND within clauses, OR between clauses | SeasonCarPoolService preview | Combined Gr.3 and Gr.4 entries, exact Gr.2/2016 fixture | Unknown required metadata and mismatching year | Focused unit tests and local preview |
| Validate definitions and ownership | saveEntry/removeEntry | Save named entry with ordered clauses | Empty/duplicate clauses, invalid year, foreign entry | Unit tests and native form errors |
| Disjoint nonempty snapshot | freeze | Persist deduplicated memberships | Empty pool/entry, cross-entry overlap, partial-write rollback | Unit tests and persistence IT |
| Frozen definitions remain stable | saveEntry/freeze/reopen | Unused explicit reopen | Frozen edit/remove, used reopen and mode switch | Unit tests, persistence IT and browser |
| Preserve fixed mode | Season defaults and fixed assignment endpoints | Existing fixed pool URLs and draft races | Fixed assignment into category mode, switch with configured race | Existing regression tests and focused tests |
| Serialize writes | SeasonRepository database lock | Fresh locked season state | Concurrent/stale-context configuration cannot bypass freeze | Transactional persistence IT |
| Connection-independent SQL checks | V24/V25 constraints | Valid writes after migration connection closes | Invalid modes, states, categories and drivetrains | JDBC migration test, real Flyway upgrade and repository slices |
| Protect catalog references | CarService delete | Unreferenced car deletion | Snapshot member deletion | Service test and FK IT |
| Native accessible editor | SeasonController and pool DTOs/template | Add/remove clauses and preview, desktop/mobile/no JS | Binding/domain errors retain submitted values/options | Controller tests, Playwright and visual inspection |
| Exact backup and legacy defaults | Backup schema, MixIns, restorers and preflight | Exact frozen membership, supported older fixed-only archive | Missing declared files, foreign/duplicate/invalid references | Backup unit and real archive IT |
| Read-only consistency audit | DataAuditService | Consistent draft/frozen pools | Broken ownership, membership and overlap fixtures | Synthetic integration fixture |

## Implementation checkpoint before final gates

- [x] Inspect current tracker, dependency chain, parallel work and repository rules.
- [x] Create a branch from current origin/master and record the requirement matrix.
- [x] Observe new focused tests failing before implementation.
- [x] Implement and verify persistence, services, editor, backups and audit.
- [x] Verify desktop/mobile and native forms without JavaScript.
- [x] Commit the correct implementation and falsify each new guard individually.
- [x] Complete code review and an independent security review; address all findings.

The remaining delivery gates are the authoritative committed-head clean build, matching-head CI,
confirmed squash merge, Wiki and tracker synchronization, and branch cleanup. Their measured
results and current status are recorded on [issue #275](https://github.com/jegr78/ctc-manager/issues/275).
This file records the implementation checkpoint and reproducible verification paths.

## Evidence

The initial service tests failed for missing pool behavior. Subsequent red tests proved that fixed
assignment URLs and car deletion lacked snapshot guards, stale season writes could thaw a frozen
pool, clause-index overflow produced HTTP 500, removing a clause lost another row's rejected value,
legacy race saves accepted dormant fixed cars in category mode, CSV overwrite deleted entry
references, and direct restore execution accepted an unsupported schema version.

Focused run 3 passed 121 unit tests, 26 integration tests and two browser cases. The expanded run 4
includes CSV and restore guards. Desktop 1440×1050 and mobile 390×844 were inspected with
playwright-cli; all seven clause labels resolve to controls and neither viewport overflowed. Visual
inspection led to scoped fieldset spacing, three desktop columns and a mobile anchor offset.

Logs live in the local task cache under `logs/season-pools-*.log`. Screenshots are ignored files
under `.screenshots/season-pool-*.png`. Final build, review, guard falsification and merge evidence
will be recorded before delivery.


## Review corrections

The independent security review of `81f2a3ff` found incomplete category selections accepted on
restore, excessive JSON retention before duplicate detection, and overflow when appending after a
restored maximal sort index. Regression tests failed first for each scenario. Restore now checks
complete pairs, mode, same-season membership, byes and duplicate consumption by effective home
team. IDs are checked incrementally; schema 4 retains only compact reference fields with a
100,000-row budget. Legacy fixed-only rows require no retention. Draft append normalizes existing
order without changing labels or IDs.

The expanded focused verification passed 159 unit tests, 31 integration tests and two browser
cases. The persistence suite proves rollback after snapshot rows were actually flushed, and a
cached unrelated Discord-link write cannot overwrite frozen state. A real archive with a removed
race entry is rejected at both preview and execution before the original selection changes.

Final visual captures show 1440-pixel desktop and 390-pixel mobile widths without overflow; the
mobile editor anchor starts 72 pixels below the viewport top. Frozen membership displays the two
reviewed local cars. Screenshots are kept outside version control.


The resource follow-up rejects container values and oversized retained text in `accept()` before
accumulation. It copies only validated scalar fields into a fresh small object, so neither nested
payloads nor the original object's map capacity remain retained. Tests first demonstrated rejection
was missing, and that the old retained object changed when its caller mutated the payload. The
bounded copy, immediate duplicate check and row budget now pass those regressions.


The native browser error path initially returned to the document top after rejected freeze.
Both desktop and mobile cases failed the retained-anchor assertion. All pool form actions now
carry their section fragment, including the entry editor for clause actions. The same two
no-JavaScript browser cases and nine controller integration tests then passed, with the pool
section positioned below the mobile menu.


## Individual guard falsification

All 98 individual experiments caused the intended assertion failure with no unexpected JUnit
errors. Each changed file was restored byte-for-byte from its recorded committed checkpoint.
Source SHA-256 values from all manifests match the final production source after rebasing onto
the current release base. The timestamp-based compiler mode was used only for statement-level
mutation loops without signature changes; the authoritative clean build uses the normal compiler
configuration.

- season-pools-service-falsification.json: 30 proved guards.
  category-match, drivetrain-match, year-match, label-null, label-empty, label-length, clauses-null, clauses-empty, clauses-limit, clause-null, clause-category, duplicate-clause, entry-owner, category-mode, frozen-edit, used-entry, fixed-selection-mode-switch, mode-required, reopen-state, empty-pool, empty-membership, overlap, freeze-problems, snapshot-preview, pool-write-lock-call, member-car-deletion, fixed-assignment-mode, legacy-race-category, race-write-lock-call, import-preserves-entry.
- season-pools-backup-falsification.json: 38 proved guards.
  backup-scalar-text-type, backup-scalar-integral, backup-scalar-int-range, backup-scalar-boolean, backup-label-length, backup-reference-length, backup-scalar-copy, backup-duplicate-id, backup-row-budget, backup-legacy-tables, backup-legacy-race-entry, backup-blank-label, backup-negative-sort, backup-year-lower-bound, backup-year-upper-bound, backup-duplicate-clause, backup-clause-limit, backup-draft-members, backup-duplicate-members, backup-overlap-members, backup-missing-clauses, backup-empty-entry, backup-empty-pool, backup-fixed-race-entry, backup-partial-race-pair, backup-race-season-owner, backup-race-member, backup-bye-selection, backup-effective-home, backup-playoff-home, backup-missing-home, backup-duplicate-consumption, backup-foreign-reference, backup-required-fields, backup-fixed-frozen-state, backup-legacy-mode-state, backup-required-id-text, clause-year-validation.
- season-pools-runtime-falsification.json: 30 proved guards.
  lock-pending-flush, lock-refresh, lock-database-write, freeze-transaction, unrelated-dynamic-update, season-details-lock, form-index-limit, form-add-limit, form-remove-range, form-unknown-action, form-reindex-errors, form-numeric-year-field, restore-execute-version, sql-fk_car_pool_entry_season, sql-fk_car_pool_member_entry, sql-fk_car_pool_member_car, sql-fk_car_pool_clause_entry, sql-fk_race_car_pool_entry, sql-chk_season_car_pool_mode, sql-chk_season_car_pool_state, sql-chk_car_pool_clause_category, sql-chk_car_pool_clause_drivetrain, sql-member-uniqueness, sql-clause-year-lower, sql-clause-year-upper, fixed-add-lock-call, fixed-remove-lock-call, saved-race-entry-guard, category-race-car-guard, empty-category-draft.

The independent security pass reported all three findings fixed on `22f97e8f`. The later native
form-anchor correction was checked separately. A subsequent bounded review also confirmed that
the equivalent CASE constraints and scoped select-width rule introduced no security findings.

## Full-build regression corrections

The first complete build exposed obsolete schema expectations in two backup test suites, an H2
constraint failure after the migration connection closed, and mobile overflow from long team
options. A standalone JDBC probe and a new migration regression reproduced the closed-connection
failure. V24 now uses equivalent CASE checks. The repository slices then passed all nine tests.

A long-option fixture reproduced overflow at both viewport widths and identified the season team
select. Scoped width constraints retain the native select within the season workspace. Desktop
1440×1050 and mobile 390×844 screenshots were inspected again; scroll width equals viewport width.
The focused correction checks passed one migration test, 16 integration tests and six browser
tests, including the schema-4 import through confirmation and execution.

The same JDBC probe confirmed that the published V23 metadata checks also retained their creator
connection. Two real Flyway upgrade tests first failed when updating accepted category/drivetrain
values from a new connection. V25 replaces those checks without editing V23. The tests then passed
for accepted, unknown and invalid metadata while preserving the legacy car's identity and image.
