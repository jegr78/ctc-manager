# Category pool release verification

Issue #278 completes parent #273 after #274 through #277 were delivered. Selected from the live
tracker after PR #321 merged. No open PR, active sibling checkout or related remote topic branch
occupies this work. Both issue and parent have no milestone/project assignment; the local GSD
milestone is shipped. Owner-planned host boot evidence in #301 remains excluded.

## Requirements matrix

| Requirement | Production path | Positive witness | Error or absence witness | Runtime proof |
| --- | --- | --- | --- | --- |
| Exact combined pool persistence | Schema 4 export/topology/MixIns/restorers | OR entry, exact-year clauses, provenance, accepted and deliberately unknown years, configured race and stable IDs survive restore | Catalog changes after export cannot recompute membership or clear decisions | Real full archive restore on H2 and MariaDB |
| Reject malformed archives before mutation | Stage and execute preflight, manifest counts, CarPoolBackupValidator | Valid archive restores and retains derived consumption | Missing declared files, foreign entry, dangling member, duplicate usage and wrong counts reject before wipe | Stage/execute boundary witnesses and unchanged live fixture |
| Preserve legacy policy | Supported schema versions and Season/Race defaults | Supported fixed-only backups omit pool fields/files safely | Unsupported versions and category data in older schemas reject | Existing legacy ITs plus focused archive cases |
| Migration and FK compatibility | Released V23/V24/V25 chain, JPA and explicit INSERT lists | Current columns/defaults, valid clauses and memberships work from a fresh connection | Invalid values, missing parents, duplicate members and referenced-parent deletion fail at their named constraint | H2 and real MariaDB schema contract |
| Read-only integrity and eligibility audit | DataAuditService, report DTO and native audit page | Valid pool remains clean; unknown metadata explains eligibility limits as information | Foreign ownership, missing member, overlapping entries, duplicate effective-home consumption and incomplete metadata reported without repair | Synthetic DB snapshots before/after and native page/browser |
| Complete admin workflow | Car metadata review, pool forms, RaceService, presentation and backup | Review candidate, freeze combined pool, save concrete car, another home may use it, edit/delete release consumption, output and backup round trip | Other alternative cannot bypass same-home consumption; native errors retain selections | Synthetic desktop/mobile flow with and without JavaScript |
| Documentation and delivery | README, CLAUDE, Wiki, parent hierarchy | Explain future-season preparation, exact year/unknown values, AND/OR, freeze and whole-entry use | No conversion of real seasons or live Discord posts | Review, guard falsification, full clean verify, CI/CodeQL and verified merge |

## Checklist

- [x] Recheck live tracker, dependencies, parent/milestone chain, worktrees, PRs and remote branches.
- [x] Record requirements before production changes.
- [x] Audit every persistent field, FK, cascade, backup registration, INSERT list and caller.
- [x] Observe intended assertion-red evidence for missing audit behavior; characterize existing contracts.
- [x] Verify combined/provenance/consumption archive round trips on H2 and MariaDB.
- [x] Reject malformed archives at both preflight boundaries without destructive mutation.
- [x] Verify migration constraints from fresh H2 and MariaDB connections.
- [x] Complete read-only integrity and informational eligibility coverage.
- [x] Exercise the complete synthetic native/browser workflow and inspect desktop/mobile with playwright-cli.
- [x] Update README, CLAUDE and user-facing Wiki; link delivered verification to #273.
- [x] Finish code review and independent security review; implement all findings.
- [x] Falsify each new guard from a correct committed checkpoint and restore exact bytes.
- [ ] Run authoritative committed-head clean verify -Pe2e -Ddocker.available=true with all static/coverage gates.
- [ ] Pass matching-head CI/CodeQL, squash merge, reconcile the complete parent chain and clean own topic branch.

## Initial persistence audit

Current upstream uses backup schema 4 and 30 exported entity tables. V24 introduced pools; V25
repaired metadata constraints. No new schema/version is presumed. Released migrations remain
immutable. Pool entry depends on season, clause on entry, member on entry/car, and race on optional
entry. JPA mappings, SQL FKs, ID MixIns, registered restorers and explicit pool/Season/Race/Car
INSERT fields already exist. Export uses the JPA topology; wipe reverses that order. Tests must
prove exact frozen IDs and memberships rather than rerun eligibility after restore.

Existing coverage already checks H2 pool snapshots, metadata provenance/explicit unknowns, generic
MariaDB full backup counts, H2 pool constraints, overlap, effective-home duplicate usage, byes and
missing home. This issue extends the cross-feature contract and fills measured gaps; it does not
repeat earlier persistence implementation or automatically change historical seasons.

## Focused evidence so far

Five new metadata audit cases ran before production changes. The four unknown-field cases failed
at their intended assertions, with zero errors; the complete-metadata absence case passed. After
adding a separate informational eligibility category, all 29 audit-service, 6 existing pool-audit
and 1 native HTTP cases passed. The audit preserves the row byte values even after an explicit
flush. Unknown years remain valid; only applicable filtered clauses exclude them.

The new full archive contract passed all nine cases on H2 and all nine on real MariaDB without
skips, alongside 26 validator unit cases. The valid case preserves combined OR clauses, exact year,
IDs, accepted provenance, an explicitly cleared year and its candidate, exact frozen members and
effective-home consumption. A catalog change after export and a new matching car do not change
the restored snapshot. A fixed season retains its concrete car and no pool-entry reference.

Eight malformed variants are rejected both at stage and after a valid preview's staged bytes are
replaced. Each asserts the specific error reason/message and that wipeAllTables was never called.
A valid restore separately proves that the same spy observes the wipe. Missing declared entry,
clause/member files, foreign entries, dangling members, duplicate usage, wrong counts and unsupported
versions cannot enter the destructive phase.

A fresh connection after the current Flyway chain verifies actual defaults/nullability and named
mode/state/clause/FK/member-uniqueness constraints. Referenced entry/car deletion is refused. These
checks passed on H2 and MariaDB. Five additional historical ownership/membership/state fixtures
passed the read-only audit contract. The combined focused run passed 26 unit and 31 integration
cases without skips. No production migration, backup version or restore rule needed changing.

## Browser and visual evidence

Two complete browser scenarios pass, with JavaScript disabled/enabled. They use the real native
sync service with only the two remote catalog adapters mocked, enrich existing car IDs, retain a
fixed-season race reference, review proposed years, freeze an exact-year MR/RR OR pool, reject
same-home reuse through RR, permit another home, edit/delete and reuse the released choice.
Native forms and output are checked at 1440 and 375 pixels. The regular Generate Settings action
creates the real PNG and its persisted attachment. Full backup/restore preserves the selection,
metadata, frozen membership, consumption and exact PNG bytes. The informational audit page renders
HTML-sensitive unknown-year text without injected nodes. No page errors occurred.

The first complete flow exposed a real score-banner overflow for 16-character team short names:
two intended width assertions failed, with zero errors. Measured offending team columns were about
157–160 pixels wide. Allowing the flex columns to shrink and wrapping their names fixes the phone
layout. A later test-only direct-renderer call left the PNG unreferenced, so the existing referenced-
asset backup correctly omitted it. The harness now uses the regular native graphic action. Those
two file-not-found errors are excluded from assertion-red evidence; no backup behavior changed.

Manual playwright-cli inspection used an isolated H2 preview on loopback 19090 with external
credentials removed. The score banner and escaped informational audit were inspected at desktop
and phone widths. The audit's horizontal region remained within the page, accepted keyboard scroll,
and exposed its review guidance at the right edge. Screenshots use the ignored `.screenshots/`
`category-release-` prefix. Its named browser and preview server were stopped afterwards.

## Legacy and final focused regression

The combined focused run passed 26 validator unit cases, 65 integration cases and the two complete
browser scenarios with zero failures/errors and no skips. It includes 7 legacy V1 cases,
12 manifest cases, 2 schema-mismatch cases, the existing metadata sync contract and all 12 real
MariaDB selection/concurrency cases. The final informational wording explicitly applies to draft
and future pools. A further frozen-member fixture clears an accepted year through the metadata
service, then proves the audit leaves the used entry valid and preserves its membership IDs,
exact-year clause and saved race. All 41 focused audit integration cases pass.

README, CLAUDE and Wiki changes describe the informational distinction and preparing future
seasons. The Wiki also clarifies the existing referenced-asset export boundary. Wiki publication
waits for verified merge. The verification checklist for delivered #277 is reconciled here with
its actual merge and CI evidence.

## Review and guard falsification

Code self-review and an independent read-only security pass reviewed
`bce602e614d8107c9ff2ccd1851f0fad2a0c0b66` against
`9fd15117f1caf2d0e831d4f67752679e8e090a6c`. Neither review retained a finding.
The reviewed caller audit confirms the audit controller and template consume enum sections
without a hard-coded category list, dynamic text remains escaped, and all new fixtures stay
separate from production. The independent pass made no file changes or test executions.

Five new guards were individually broken from that correct committed checkpoint: unknown
category, drivetrain and accepted year; absence of information for complete metadata; and the
score-name wrapping rule. All five produced the intended seven assertion failures with zero
errors. Each source file was restored byte for byte from the commit, and the worktree was clean
before adding this evidence. Existing archive admission and migration constraints were unchanged;
the new archive/schema cases characterize and exercise their shipped contract.

The retained local ledger is `category-release-falsification.json`, with logs under
`logs/category-release-guard-*.log`. The final verification commit adds documentation only after
the reviewed production head.
