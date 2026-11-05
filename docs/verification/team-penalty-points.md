# Team penalty points verification

The native race correction page records full fixed totals after the final driver classification.
The per-leg policy offsets penalties before applying the official-score zero floor. The accepted
examples `80:70` with penalties `0:0`, `20:10`, `15:15`, `100:110` and `100:0` produce
`80:70`, `70:70`, `80:70`, `80:60` and `0:70`. Existing races without revisions keep their
legacy behavior. An explicit correction, including a zero reset, applies the zero floor.

| Behavior | Evidence |
|---|---|
| Policy, negative rejection and integer boundaries | `RacePenaltyTotalsTest` |
| Immutable sequence, metadata, identity and orientation | `RacePenaltyHistoryTest`, `RacePenaltyBackupValidatorTest` |
| Legacy parent/subteam attribution | `RacePenaltyAttributionTest` |
| View overlapping a restore generation | Latch-based `RacePenaltyViewTest` |
| View overlapping another correction or a guest team change | Latch-based `RacePenaltyCorrectionIT` and inherited MariaDB suite; captured totals, history token and scalar fingerprint stay aligned, and the stale save is rejected |
| Revisions, stale inputs, actor/reason validation and write admission | `RacePenaltyCorrectionIT`, inherited MariaDB suite |
| Concurrent managers, stale persistence contexts, reversed legs, guests, result/CSV replacement and standings | `RacePenaltyCorrectionIT`, inherited MariaDB suite |
| Decided-playoff correction and permanent lifecycle protection | `RacePenaltyCorrectionIT`, inherited MariaDB suite |
| Binding errors, CSRF, server actor and escaped private text | `RacePenaltyControllerIT`, `RacePenaltySecurityIT` |
| Regular/standalone-playoff archive round trips, precise future timestamps, legacy defaults, tampering before wipe and DB constraints | `RacePenaltyBackupRoundTripIT`, inherited MariaDB suite |
| Read-only malformed-history audit and safe aggregate skipping | `RacePenaltyAuditIT` |
| Shared score headers independent of the six-row graphic limit | `RacePenaltyGraphicsIT`, including seven home drivers |
| Native desktop/phone correction and public confidentiality | `RacePenaltyE2ETest`, JavaScript disabled at 1440 and 390 pixels |
| Quick-score rejection across legs; empty-leg displays and per-leg save messages | `RacePenaltyCorrectionIT` |
| Graphic regeneration retains its attachment | `RaceGraphicServiceTest` |

The new behavior was observed red before implementation. In particular, raw detail totals stayed
at 80 instead of 70, stale restoration views acquired the new generation, playoff walkover was
available despite a zero revision, forged participant archives passed preview, malformed history
aborted the audit, and a truncated graphic showed 75 instead of 76.

Internal reasons and actors never enter the public race DTO or result/provisional graphic contexts.
Default graphic and native/public screenshots are produced under the ignored `.screenshots/`
directory. Publication is an explicit operator action after regeneration.

The requirement matrix is recorded in issue #342. Final verification uses the repository's full
`./mvnw clean verify -Pe2e -Ddocker.available=true` lifecycle with its coverage and static-analysis
gates. Individual guard falsification and the final reviewed commit are recorded in the PR.

On hosts where `/tmp` and the workspace use different filesystems, allocate the test JVM
temporary directory on the upload filesystem. For a workspace-local run:

```sh
mkdir -p .git/verification-tmp
JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$PWD/.git/verification-tmp" ./mvnw clean verify -Pe2e -Ddocker.available=true
```

This retains the production atomic-upload-swap preflight. Private-pick and penalty archive fixtures
use dedicated H2 databases so complete-database exports cannot inherit another cached context's
category or penalty data. MariaDB subclasses override those URLs with their container properties.

Two concurrent-view regressions were observed as assertion failures before their fix. A correction
committing between score and history reads returned revision 1 with revision-0 totals. A normal
guest-team move between score and fingerprint reads returned old gross points with a new
fingerprint. `ScoringService.RaceScoreSnapshot` now captures the scalar result fields and team
attribution used for both calculations. The view uses the same initialized penalty history for
its displayed totals and revision token. Writer-side validation uses that shared snapshot under
the existing locks. Transaction isolation and the fingerprint field order remain unchanged.
