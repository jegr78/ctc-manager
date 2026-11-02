# Category car presentation verification

Issue #277, parent #273. Depends on #276, delivered through PR #320.
GitHub is the tracker; the shipped local GSD milestone is historical.

| Requirement | Production path | Positive test | Error or absence test | Runtime evidence |
| --- | --- | --- | --- | --- |
| Concrete car primary, saved entry secondary | Shared presentation projection prepared by services | Combined MR/RR label plus a specific MR fixture car | Fixed and missing entries have no empty placeholder | Unit display models and native HTML |
| Settings graphics retain completeness | SettingsGraphicService and settings-render | Configured category graphic contains both names | Missing concrete car still rejects generation | Rendered PNG and measured bounds |
| Public pages preserve fixed output | MatchdaysPageGenerator, RaceView, site template | Category race carries and shows both names | Fixed snapshots and concrete car names remain stable | Generated HTML at desktop and mobile |
| Admin detail shows saved choice | RaceService detail model and race-detail | Saved entry label appears beside the concrete car | Missing selection remains readable | Native browser and service/controller tests |
| Safe long labels | Thymeleaf text and scoped wrapping styles | 120-character combined label stays readable | HTML-sensitive label creates no elements or script | Browser errors and layout bounds |
| Explicit publication boundaries | Discord graphic consumers and calendar audit | Existing explicit settings output inherits the display context | No automatic publication or changed endpoints | Caller audit and existing regressions |

## Checklist

- [x] Recheck live issues, dependencies, parent milestones, worktrees, PRs and remote branches.
- [x] Create the behavior branch from current origin/master and record requirements.
- [x] Audit every concrete-car display and its completeness/publication assumptions.
- [x] Write display assertions first and observe intended failures.
- [x] Implement the shared projection and conditional secondary display.
- [x] Verify fixed, missing-car, long-label and HTML-sensitive behavior.
- [x] Inspect admin/public/native desktop/mobile flows and actual settings graphics with playwright-cli.
- [x] Complete code review and independent security review; fix all findings.
- [x] Falsify new logical guards from a correct committed checkpoint and restore exact bytes.
- [x] Run authoritative clean verify -Pe2e on the reviewed committed head.
- [x] Pass matching-head CI, squash merge, reconcile Wiki/tracker and clean the own branch.

## Caller audit

Complete the source-level audit before production edits. Keep Race.car as the shared one-make
vehicle, existing custom-template carName values, local-asset rendering security and explicit
publication actions. Templates must not evaluate clauses or eligibility. No new persistent field,
per-driver car, PP value or external posting automation belongs to this issue.

| Caller | Finding and handling |
| --- | --- |
| SettingsGraphicService/settings-render | Prepare shared car/entry projection; retain carName and concrete-car completeness checks, images and local renderer rules |
| TemplatePreviewService/template-editors | Expose optional poolEntryLabel to the settings preview and document its variable |
| MatchdaysPageGenerator/RaceView/site matchday | Prepare both names; keep existing concrete car property and fixed output |
| site/fragments/match-card | Legacy fragment currently has no source callers; retain optional-label compatibility without changing fixed text |
| RaceService/RaceController detail/list | Prepare one projection or an ID-keyed projection map in the service |
| RaceFormDataService/results | Prepare the same optional context for the existing results heading |
| MatchService/match-detail and MatchdayService/matchday-detail | Prepare race projection maps for their per-leg setup summaries |
| DiscordPostService | Setup publication reads SettingsGraphicService output, so it inherits context through the existing explicit graphic operation |
| RaceCalendarService | Calendar communicates pairing, time and duration, with no concrete-car setup field; preserve its existing payload and explicit action |
| Other graphics/overlays, scoring and standings | No concrete-car display; retain existing behavior and image/completeness contracts |
| Backup/import/audit/selection getCar calls | Integrity and consumption logic, outside presentation; leave unchanged |

The signature audit found only one RaceView construction, the RaceService result/list/detail
records and one calendar-controller fixture construction of RaceDetailData. Audit all actual
usages again when changing those view models. All presentation is based on saved references;
no eligibility evaluation or new remote lookup is introduced.

## Observed behavior

The first focused run executed 24 tests and failed at the two new category-display assertions,
with no errors. The shared projection then made all 24 pass. Native HTTP coverage first failed
six intended assertions, then passed all 11 category/fixed/escaped-label cases. Preview and legacy
fragment coverage produced two assertion failures before implementation, followed by 79 focused
unit/template cases and 11 native HTTP cases passing. Template-selector harness errors are excluded
from the red evidence.

Three browser cases pass. They exercise the five native views and generated public HTML at 1440
and 375 pixels, with JavaScript enabled and disabled, keyboard navigation, a rejected native save,
missing draft selections and a 120-character HTML-sensitive label. The real settings renderer
produces a 1920 by 1080 PNG with both names, wrapped text and no overlap with the next setting.

Manual playwright-cli inspection used an isolated H2 preview on loopback port 19090, with external
credentials removed. Desktop/mobile admin and public views, match/matchday summaries, results,
validation return and the actual PNG were inspected. Screenshots live under ignored `.screenshots/`
with the `category-car-` prefix. The preview and its named browser were stopped afterwards.

The first mobile race-table inspection exposed squeezed team names. Two intended browser assertions
failed before adding a minimum table width and bounded car column. Team names now remain within two
lines, and the named scroll region accepts keyboard scrolling without JavaScript. An initial
requestAnimationFrame-based wait timed out with JavaScript disabled despite native scrolling moving
from 0 to 40 pixels. A locator property assertion verifies that browser state without extending the
timeout. The final focused run passed 9 settings tests and all 3 browser cases.

No publication was triggered and no production service or database was used.

## Review

Self-review covered the full changed read-model paths, all six admin layouts, generated public
pages, custom/default settings templates, caller compatibility and resource cleanup. No production
code finding remained. The review added explicit single/multiple-leg HTTP witnesses and stronger
fixed-output and bundled/legacy-template escaping assertions. All 28 focused settings/generator
tests and 13 native HTTP cases then passed.

An independent read-only security pass reviewed production head
`75e5ffa09a231f07f6b909804631408d83f33f95` against
`8f01037ad160b16459e120ec15f55518e3a7de55` and reported no findings. Subsequent changes add tests and
verification evidence only. Escaped saved text, concrete-car completeness and the existing local
renderer/publication boundaries are preserved.

## Guard falsification

Correct checkpoint `85b00d63976b1bdd752ece42282411fe5c4be3a4` was committed before mutation.
All 24 independent mutations failed at the intended assertions, with zero test errors. The harness
restored each changed file byte for byte from that commit in a finally block; the final worktree
was clean. Initial selector/wait harness errors from implementation are not included.

| Guard group | Independently broken conditions | Assertion witness |
| --- | --- | --- |
| Saved projection | Missing entry and missing concrete car remain null | Prepared settings model and generated empty draft card |
| Settings template | Optional label and escaped label text | Bundled default template, fixed/category fixtures |
| Admin detail | Independent label/value presence and escaped value | Fixed-output absence and HTML-sensitive native HTTP response |
| Admin list/results/match | Presence and escaping in each consumer | Fixed-output absence and HTML-sensitive native HTTP responses |
| Admin matchday | Presence and escaping separately for single/multiple-leg layout | Both native layouts, fixed/category fixtures |
| Generated public matchday | Presence and escaping | Fixed HTML and both browser JavaScript modes |
| Legacy match card | Presence and escaping | Exact text and absent placeholder |
| Layout | Native label wrapping, readable table widths, graphic label wrapping | Browser width/line bounds and real settings PNG |

The falsification ledger is retained locally as `category-presentation-falsification.json`, with
per-case logs under `logs/presentation-guard-*.log`. Layout mutations restore the corresponding
complete rule group. Existing public metadata wrapping remains unchanged; no new business or
selection validation rule was added.

## Delivered

PR #321 merged on 2026-11-02 at 08:17:42 UTC as
`d6f4c4dae94be158cb3ae4e52eb4d2a38f8df430`. The tested head was
`f0749cbf57b897da2aee0f642c27386f765665d0`. Its authoritative clean verification passed 2,245
unit/controller, 866 integration and 214 E2E cases, with zero failures/errors and 92.2124% line
coverage. Only three existing unit deactivations remained. SpotBugs reported zero findings,
FindSecBugs was active, and all six GitHub checks passed. Wiki commit
`2e1c5819cf9d75ead637416476569797a5339c84` was verified remotely. Issue #277 was completed,
parent #273 retained #278, and the delivered local topic branch was removed after exact-head proof.
