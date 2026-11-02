# Management pick review

Issue #284 provides internal review, deadline exceptions and explicit approval of confidential picks. Existing domain decisions and immutable history remain authoritative. Publishing and collection activation remain separate deliverables in #285 and #286.

## Delivery checklist

- [x] Record requirements and observe intended assertion-red tests.
- [x] Add native overview/detail forms and versioned DTOs using existing management authentication/CSRF.
- [x] Guard explicit replacement and concurrent settings changes inside the approval transaction.
- [x] Show immutable history, overrides and approved correction provenance.
- [x] Verify actual transactions, rejected actions, no publishing and database rollback.
- [x] Verify keyboard/no-JavaScript forms and desktop/mobile layout.
- [x] Complete self-review and independent security review; falsify new guards individually.

Final verification, CI, actual merge, tracker/Wiki reconciliation and branch removal are tracked in the PR delivery checklist.

## Requirements matrix

| Requirement | Production path | Positive test | Rejection test | Runtime witness |
| --- | --- | --- | --- | --- |
| Full exact target, current revision and saved history | MatchdayPickAdminService and review templates | Missing/submitted/returned/approved overview and detail | Missing or foreign filter context, wrong target | Real H2 history and browser rendering |
| Native context-preserving management forms | MatchdayPickController and form DTO | Season/matchday filters, links, reason and back navigation | Binding/domain errors retain inputs and errors | MockMvc plus no-JavaScript Playwright |
| Authenticated management and CSRF | Existing security chain and server-derived actor | Authenticated internal POST | Anonymous/CSRF-less POST, supplied actor spoof | Production-profile security IT |
| Late missing opening and correction without new date | Existing PickReviewService decisions | Open/return/close exact target with reason | Approved lock, blank reason and stale version | Real service proxies and team-write policy |
| Exact atomic approval with explicit replace decision | PickReviewService and RaceSettingsApplicationService | Latest revision applies car/track/settings only | Stale revision, differing settings without consent, concurrently changed settings, invalid/result/reoriented target | Transaction/rollback IT and unchanged schedule/lineup assertions |
| Corrections preserve approval and original revision | Existing correction domain service and review history | Actor/reason/time/full correction payload visible | Ordinary editing cannot unlock approved team state | Existing race editor integration and history fixtures |
| Review never publishes | Review services and controller | Approval and management decisions save internally | Any Discord side effect or unintended public action | Real DiscordPostService spy and fake outbound transport |
| Existing management layout | New templates and contextual links | Labeled fieldsets/actions/history on desktop/mobile | Long values, hidden errors, overflow, missing focus labels | Screenshots in ignored .screenshots/ and browser tests |

## Focused evidence

The first approval test reproduced silent replacement of a different configured car with one intended assertion failure and zero JUnit errors. Approval now requires explicit replacement consent and compares the submitted settings fingerprint under the existing target locks. Four additional domain tests cover exact consent, stale approval/correction snapshots and identical preconfigured content.

Focused native-management transactions cover manual opening/closure, correction return, stale approval, exact settings transfer with an existing lineup, explicit replacement, concurrent settings change, results/decided targets, a reoriented return leg and a failure after settings SQL flush. A real DiscordPostService spy observes no invocation. Production-profile tests preserve authentication and CSRF for every new write endpoint.

Playwright runs with JavaScript disabled on desktop 1440x1000 and mobile 390x844. The form tests exercise keyboard approval, missing consent, raw numeric error retention, full correction history and native category/member refresh. The initial checkbox-size assertions failed on both viewports with zero JUnit errors; scoped checkbox styling corrected them. Long values fit the page. Screenshots and rendered HTML remain in ignored .screenshots/.

CLI visual inspection uses those actual rendered HTML snapshots and current application assets through a loopback-only read-only preview. Desktop/mobile decision screenshots were inspected alongside the native server workflow screenshots. The preview never submits a management action. No live Discord command, channel or message is used.

Self-review reproduced a marked, missing target changed to a bye. Its overview assertion failed before correction with zero JUnit errors. Such targets now remain readable and offer no review decision. Current returned reasons are visible in the status block as well as immutable history. Long correction forms use static actions to avoid covering mobile inputs.

Every approval/correction caller was audited. The existing rollback fixture with a deliberately different setup now supplies explicit replacement consent and its reviewed fingerprint, retaining its assertion that settings SQL executes before the forced decision failure. No production persistence shape changed. Existing backup/audit rules continue to validate saved revisions and corrections; consent and fingerprint are request-level checks.

Collection and confidentiality readiness remain disabled. Publication and the later changed-since-publication indication belong to the existing disclosure deliverable #285.

The independent security review found no additional issue beyond the identified restore-generation case. Old approval and correction forms were each reproduced as wrongly accepted after a restore event, at intended HTTP-status assertions with two failures and zero JUnit errors. Management forms now retain the generation captured before their read. A generation-bound management actor is checked after target locks inside the existing admitted transaction. Trusted internal callers retain the original one-argument actor contract.

MariaDB concurrency cases require `-Ddocker.available=true`; only the explicit Docker-enabled invocation is used as evidence below.

The explicit Docker-enabled lifecycle passed 92 unit tests, 44 integration tests and four browser tests with zero failures/errors/skips. Both H2 and MariaDB concurrency classes ran all ten cases, including the deliberately failing approval commit. The generation-bound UI forms were refused after restore; the positive reload cases also exercise the production form preparers. No persistence or deployment setting was relaxed.

A security delta review identified that a restore followed by application restart reset the numeric epoch, potentially accepting an old browser form. The new regression reproduced that case with one intended assertion failure and zero JUnit errors. Management forms now carry an opaque per-instance UUID plus restore counter, compared under target locks. The Gateway numeric epoch contract remains unchanged. Old-process management context is rejected and a freshly prepared context is accepted.

The process-bound context follow-up passed 38 unit tests, 24 HTTP integration tests and four browser tests with zero failures/errors/skips. The security profile and native desktop/mobile forms ran with the new opaque context.

## Individual guard witnesses

Self-review and independent security re-review completed with no remaining findings on production head `c962bb4d70d72843b75c0aee5057b8050ea4c7fa`. Both restore findings were corrected and retested before the canonical run.

Each guard was broken separately from its correct committed checkpoint and restored byte-for-byte afterward. Every selected test failed at an intended assertion with zero JUnit errors. Integration-test lifecycle exit codes alone were not used to qualify a witness.

| Guard | Selected behavioral test | Assertion failures / JUnit errors |
| --- | --- | --- |
| Checkpoint | `18ad4622298749496f7937d1262e45235e4d827a` | |
| configured-setup | `PickSetupSnapshotTest#givenEmptySetup_whenCapturing_thenTreatDefaultTimeModeAsUnconfigured` | 1 / 0 |
| exact-setup | `PickReviewServiceTest#givenDifferingPreconfiguredSettings_whenApprovingWithoutReplaceConsent_thenPreserveManagementWork` | 1 / 0 |
| replace-consent | `PickReviewServiceTest#givenDifferingPreconfiguredSettings_whenApprovingWithoutReplaceConsent_thenPreserveManagementWork` | 1 / 0 |
| approval-snapshot | `PickReviewServiceTest#givenInternalSettingsChangedAfterReview_whenApprovingWithOldSnapshot_thenPreserveBothSides` | 1 / 0 |
| correction-snapshot | `PickReviewServiceTest#givenApprovedSettingsChangedAfterCorrectionForm_whenSavingCorrection_thenRejectStaleSnapshot` | 1 / 0 |
| fingerprint-complete | `PickSetupSnapshotTest#givenCapturedCompleteSetup_whenAnyIndividualFieldChanges_thenInvalidateReviewFingerprint` | 1 / 0 |
| fingerprint-boundaries | `PickSetupSnapshotTest#givenDifferentValuesWithSeparators_whenFingerprinting_thenKeepExactFieldBoundariesAndIdentity` | 1 / 0 |
| request-metadata | `MatchdayPickAdminServiceTest#givenMissingOrMalformedVersionSnapshot_whenPosting_thenRejectBeforeResolvingActorOrWriting` | 1 / 0 |
| production-principal | `MatchdayPickAdminServiceTest#givenProductionWithoutPrincipal_whenPostingDecision_thenRejectBeforeDomainMutation` | 1 / 0 |
| principal-nonblank | `MatchdayPickAdminServiceTest#givenBlankOrOverlongServerPrincipal_whenPosting_thenDenyWithoutInventingDevelopmentIdentity` | 1 / 0 |
| principal-bound | `MatchdayPickAdminServiceTest#givenBlankOrOverlongServerPrincipal_whenPosting_thenDenyWithoutInventingDevelopmentIdentity` | 1 / 0 |
| restore-under-lock | `PickTargetServiceTest#givenManagementActorFromOldRestoreGeneration_whenLocking_thenRejectInsideAdmittedTransactionAfterTargetLocks` | 1 / 0 |
| capture-generation | `MatchdayPickReviewIT#givenManagementFormOpenedBeforeRestore_whenPostingAfterRestore_thenRejectWithoutChangingRestoredState` | 2 / 0 |
| review-generation | `MatchdayPickReviewIT#givenManagementFormOpenedBeforeRestore_whenPostingAfterRestore_thenRejectWithoutChangingRestoredState` | 2 / 0 |
| correction-generation | `MatchdayPickReviewIT#givenManagementFormOpenedBeforeRestore_whenPostingAfterRestore_thenRejectWithoutChangingRestoredState` | 1 / 0 |
| approved-read-only | `MatchdayPickReviewIT#givenValidCurrentRevisionAndLineup_whenApproving_thenApplyOnlySettingsAndPreserveSchedulePairingAndLineup` | 1 / 0 |
| ineligible-read-only | `MatchdayPickReviewIT#givenEnrolledMissingTargetChangedToBye_whenLoadingReview_thenKeepOverviewReadableWithoutOfferingDecisions` | 1 / 0 |
| bye-readable | `MatchdayPickReviewIT#givenEnrolledMissingTargetChangedToBye_whenLoadingReview_thenKeepOverviewReadableWithoutOfferingDecisions` | 1 / 0 |
| Checkpoint | `c962bb4d70d72843b75c0aee5057b8050ea4c7fa` | |
| management-startup-instance | `PickTargetServiceTest#givenManagementFormFromPreviousProcess_whenLockingAfterRestoreAndRestart_thenRejectOldContextAndAcceptFreshForm` | 1 / 0 |
| management-restore-counter | `PickTargetServiceTest#givenManagementActorFromOldRestoreGeneration_whenLocking_thenRejectInsideAdmittedTransactionAfterTargetLocks` | 1 / 0 |
