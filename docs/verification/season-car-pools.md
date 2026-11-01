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
| Protect catalog references | CarService delete | Unreferenced car deletion | Snapshot member deletion | Service test and FK IT |
| Native accessible editor | SeasonController and pool DTOs/template | Add/remove clauses and preview, desktop/mobile/no JS | Binding/domain errors retain submitted values/options | Controller tests, Playwright and visual inspection |
| Exact backup and legacy defaults | Backup schema, MixIns, restorers and preflight | Exact frozen membership, supported older fixed-only archive | Missing declared files, foreign/duplicate/invalid references | Backup unit and real archive IT |
| Read-only consistency audit | DataAuditService | Consistent draft/frozen pools | Broken ownership, membership and overlap fixtures | Synthetic integration fixture |

## Checklist

- [x] Inspect current tracker, dependency chain, parallel work and repository rules.
- [x] Create a branch from current origin/master and record the requirement matrix.
- [x] Observe new focused tests failing before implementation.
- [x] Implement and verify persistence, services, editor, backups and audit.
- [x] Verify desktop/mobile and native forms without JavaScript.
- [ ] Commit the correct implementation and falsify each new guard individually.
- [ ] Complete code review and an independent security review; address all findings.
- [ ] Run the authoritative committed-head `./mvnw clean verify -Pe2e`.
- [ ] Confirm matching-head CI, squash merge and reconcile the entire issue hierarchy.
- [ ] Update README, CLAUDE.md and Wiki; remove the verified merged topic branch.

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
