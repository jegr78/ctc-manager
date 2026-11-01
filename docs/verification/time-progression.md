# Explicit time progression, issue #280

## Requirements and verification matrix

| Criterion | Production path | Positive test | Error-path test | Runtime evidence |
| --- | --- | --- | --- | --- |
| Distinguish Equal Conditions and 0x | RaceSettings, RaceService | mode completeness/display, save and form data tests | incompatible pairs rejected before mutation | focused Maven tests and full E2E run |
| Preserve legacy 0, 6, 60 and incomplete null | V22 migration, compatibility save overload | migration/restore integration tests | null stays incomplete | H2 migration and restore |
| Preserve mode through editing | RaceForm, controller, form data | native form round trip | submitted form retained on domain/binding error | desktop/mobile browser inspection |
| Render both graphics correctly | settings/lobby services and templates | mode-aware template values | no nullx for incomplete settings | generated graphics and template tests |
| Back up explicit mode | MixIn, restorer | exact Equal Conditions restoration | invalid combinations rejected | JDBC restore integration test |

The legacy numeric template variable remains available. Custom templates should use
`timeProgressionDisplay` for a mode-aware value including the multiplier suffix.
Existing backups without a mode restore as MULTIPLIER. No supported schema versions
are added by this additive field.

## Workflow checklist

1. Inspect open issues, PRs, remote branches and other checkouts.
2. Select an eligible independent issue. This delivery selects #280 under #279.
3. Audit multiplier usages and all save/data constructors.
4. Observe focused tests fail against legacy behavior, then implement storage,
   save validation, editor and both graphics.
5. Verify migration, legacy restoration, explicit mode round trip and desktop/mobile editing.
6. Falsify new guards individually and restore the correct committed files.
7. Complete code/CI review and an independent security review.
8. Run `./mvnw clean verify -Pe2e` on the final committed head.
9. Merge after green CI, reconcile #280 and parent #279, and clean the local branch.

## Review

The code review and independent security pass found a negative multiplier bypass
through the legacy save overload. Both explicit and legacy calls now reject
negative multipliers before any repository access. Legacy null drafts remain
incomplete. Regression tests check the rejection message and absence of reads.

The new audit category reports inconsistent mode/value pairs without repairing
historical records. Restoring backups rejects contradictory explicit mode/value
pairs; missing legacy mode fields default to MULTIPLIER.

## Local environment

The host has no installed JDK and its existing Maven repository is root-owned.
Validation uses a task-local Temurin 25 installation and a writable Maven cache.
The test temporary directory is on `/home`, alongside uploads, because `/tmp` is
a separate tmpfs and cannot satisfy atomic restore moves. These are execution
environment settings, not application changes or weakened restore checks.
