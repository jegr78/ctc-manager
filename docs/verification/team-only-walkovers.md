# Team-only walkovers

Issue #410. The winning team receives the configured race points for positions 1 through 6 once per match, without qualifying or fastest-lap bonuses. The forfeiting team receives zero. Individual driver statistics receive no credit.

| Requirement | Production path | Positive test | Failure-path test | Runtime evidence |
| --- | --- | --- | --- | --- |
| Shared race-point-only calculation and orientation | Domain walkover scoring, standings and match display | Standard 81:0, alternate scoring, both orientations, multiple legs | Invalid forfeiter and missing participants rejected; bye remains distinct | Unit and H2/MariaDB integration checks |
| Consistent admin, public and graphic totals | Official display projections and graphic contexts | Same team total without driver results; w.o. marker retained | Stale stored scores and retained driver results cannot override walkover | Native rendering, site and graphic tests |
| No driver credit | Phase/season/alltime rankings and driver/team profiles | Existing rows excluded for regular and playoff walkovers | Ordinary and DSQ race behavior unchanged; clearing walkover restores legitimate results | Ranking and public profile tests |
| Preserve history and write rules | Match/Playoff transition and score admission | Existing results remain unchanged while excluded; valid reversal reaggregates | History-bearing invalid transitions remain blocked; no new results on walkovers | Integration checks and guard falsification |
| Diagnose inconsistent historical records | Read-only data audit | Walkover driver rows and conflicting saved totals reported | Audit never saves or changes results | Audit fixture checks |
| Preserve archive and publication evidence | Existing serialization and immutable snapshots | Computed display fields excluded from native archives; source fields unchanged | No implicit regeneration, publication or data rewrite | Serialization/restore tests and independent security review |

Tests precede implementation. Focused feedback uses the shared helper with one fork and bounded heaps; complete inventory, MariaDB, browser, coverage and security gates run in CI. Individual guard falsification and final exact-head results are recorded in the PR.

## Focused evidence

The old implementation failed the new no-bonus, driver-credit and graphic assertions. After correction, the focused integration run passed 150 unit and 59 integration cases. The browser scope passed 130 unit cases, the real set/clear transition and the native WalkoverE2ETest. Its desktop screenshot confirmed 81:0 and w.o.; visual inspection found narrow-screen name overlap, corrected with wrapping and a fixed-size score column. The final browser repeat and complete exact-head CI are linked in the PR.

The independent read-only security pass found and verified fixes for a zero-leg CSV admission gap and legacy playoff bracket totals. Follow-up reported no open findings. Native backup serialization excludes all computed score getters. ScoreReaggregationMariaDbIT runs the same service transitions against real MariaDB in CI. Guard falsification results are recorded individually in the PR.

The first complete CI found the existing historical dual-owner playoff fixture and the entity-annotation cleanliness rule. The helper now selects the active playoff owner and its phase scoring, and all projections share its forfeiter/pairing key. Jackson exclusions live in backup MixIns. Additional red tests cover reversed detached-ID markers, historical dual-owner public projections, once-only playoff anchor graphics and provisional/individual write rejection. Explicit Discord walkover posting contains only the team overview.

Final correction feedback passed 73 unit and 20 integration cases, including both reproduced CI failures, historical conflicting markers, MixIn serialization, explicit team-only Discord posting, native write admission and the read-only anchor audit. Follow-up independent review reported no remaining findings. First guard falsification passed eight separate required assertions and restored all source; expanded admission/projection guards are falsified against the final committed source before merge.
