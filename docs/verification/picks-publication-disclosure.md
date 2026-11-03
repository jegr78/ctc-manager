# Confidential pick publication

Issue #285 preserves the existing management publishing controls. Persistent race enrollment and
confirmed immutable content govern public reads, including when collection or the Gateway is off.
Unenrolled legacy races retain their display rules. Final consumer/readiness activation remains #286.

## Delivery checklist

- [x] Audit settings-bearing outputs and callers.
- [x] Observe privacy and publication assertion failures before implementation.
- [x] Persist immutable content, field masks and delivery outcomes.
- [x] Integrate existing explicit publishing, automatic-edit safety and public projections.
- [x] Add native season opt-in and uncertain-delivery reconciliation.
- [x] Protect internal graphic caches and public asset copying.
- [x] Exercise H2/MariaDB persistence, failure, concurrency, backup and read-only audit paths.
- [x] Update terminology, operations and README documentation.
- [x] Falsify the new guards and complete code plus independent security review.

## Requirements matrix

The witnesses exercise stored public projections, actual templates, physical webhook outputs, native forms and both databases.

| Criterion | Production path | Positive witness | Rejection/failure witness | Runtime proof |
| --- | --- | --- | --- | --- |
| Managed unpublished content is wholly withheld | PickDisclosureService, PublicPickProjection, MatchdaysPageGenerator | Confirmed published projection | Distinctive private car/track/weather absent after submit, approval and internal correction; switches off do not release | Unit projections and actual static HTML/assets |
| Unenrolled legacy display remains available | Public projection legacy branch | Existing public car/track rendering | Collection flag alone never changes managed history/publicity | Existing public presentation fixtures |
| Immutable content version and field masks | Publication snapshot/attempt model and repositories | Lobby fields published with car withheld | No union across different hashes; later partial version never borrows old fields | Domain tests and real persisted snapshots |
| Explicit approved publication only | PickPublicationService and existing management post routes | Exact approved revision or recorded correction captured | Pending/stale/invalid/foreign target denied before network | Service proxies, locks and fake transport |
| Confirm only successful physical output | DiscordPostService and publication outcomes | Successful POST/PATCH creates confirmed snapshot | Known failure/timeout/remote-success-local-failure retain previous public version and durable reconciliation state | WireMock production-format requests plus DB failure injection |
| Collective output tracks each successful bundle | Publication batch coordination and existing collective callers | Confirm only actually sent bundle races | Later failed bundle does not disclose its races or clear prior success | Partial-delivery integration fixtures |
| Automatic updates never release current private settings | DiscordAutoPostListener, autoEditMatchPreviewIfNeeded | Schedule/preview edits retain earlier public content | Current private payload never appears in auto-edit requests | Real post service and recorded POST/PATCH bodies |
| Capture stays exact under concurrent corrections/restore | PickWriteExecutor admission, season/race locking and frozen rendering input | Concurrent correction leaves sent bytes at captured version | No mutable live-entity render, wrong-version confirmation or unbounded restore wait | PickPublicationPersistenceIT/MariaDbIT and PickPublicationDiscordIT |
| Template disclosure is proven per output | SettingsGraphicService, LobbySettingsGraphicService, preview preparation | Built-in settings/lobby rendered fields match their masks | Unknown/custom coverage adds no website release; explicit sends still require approved captured content | Rendered HTML/image and custom-template fixtures |
| Internal graphics cannot become public cached artifacts | Graphic output paths, static export/asset preparation | Internal preview remains usable | Predictable old private PNG paths are not imported into exported HTML/assets | File-content/path assertions and static-site run |
| Every public view uses public data | MatchdaysPageGenerator, DriverProfilePageGenerator, public graphics/calendar projections | Published track/car/time modes display correctly | Driver-history and other indirect entity traversal cannot disclose unpublished settings | Full generated site search and graphic/calendar fixtures |
| Explicit season activation and legacy classification | Season administration and enrolment service | Reviewed already-public evidence or withheld classification permits opt-in | Default off, unclassified configured targets/evidence mismatch deny enable; no automatic historical hide/approval | Native authenticated/CSRF forms and real transactions |
| Durable backup and read-only integrity audit | New migration, BackupSchema/Jackson/restorers/preflight and DataAuditService | New snapshot/attempt round-trips and legacy backups | Invalid masks, provenance, references or outcomes reject before wipe; audit never repairs | H2/MariaDB restore, malformed archive and audit fixtures |
| Existing management UX and changed-since-publication status | Match/race/matchday publication controls and pick review | Existing URLs, native controls and correction/publication distinction | Errors retain context; partial/uncertain results are visible without invented success | MockMvc and desktop/mobile Playwright |

## Output audit

| Output | Behavior |
| --- | --- |
| Public matchdays and driver history | Use PickDisclosureService; never traverse current managed car/track choices |
| Settings, lobby settings and explicit previews | Freeze approved/corrected content and exact rendered files; persist each physical output independently |
| Automatic preview edits | Reload persistent enrollment under season locks and omit managed settings attachments |
| Matchday pairings/schedule, calendar payloads | Contain pairing/time metadata without pick choices |
| Internal settings/lobby previews | Remain usable; internal race-cache paths and symlink aliases cannot become exported logos/assets |
| Builtin templates | Proven settings mask includes all 13 fields; lobby omits car and pool-entry label |
| Custom templates | Exact source captured once; unknown coverage establishes no additional public mask |

There is no collective settings/lobby endpoint or caller in the current repository. Existing
pairing publication is tested in sequence: a later failed physical bundle leaves only the earlier
successful bundle public. No new collective publishing workflow was introduced.

## Verification evidence

The first actual matchday templates exposed the private car/track for fixed and category fixtures.
Both failed at the intended assertions, with no JUnit errors. Initial domain, output authorization,
mask, uncertainty, static-cache and stale-caller witnesses likewise failed before their guards.

The reconciliation witnesses observed 14 intended evidence-check failures and 13 intended HTTP
transport failures, with no JUnit errors. The first five domain-resolution cases failed before
implementation and then passed. Native form routes first produced four expected assertion failures.
The changed-since-publication status witness produced one intended assertion failure.

Focused green evidence so far includes:

- 80 unit cases covering graphics, backup validation and exact message/file evidence.
- 69 integration cases covering native authenticated/CSRF reconciliation, real webhook transport,
  publication outcomes and resolution in H2/MariaDB.
- Full static generation after submit, approval, private correction and collection shutdown, then
  a lobby-only disclosure. Private car/track/weather remain absent until their explicit release.
- Sequential physical-bundle success/failure, concurrent correction during HTTP and unresolved
  verification exclusion. Captured bytes and their public snapshot remain at the original version.
- Publication backup round-trips and read-only integrity checks in H2/MariaDB, retaining field masks,
  the earlier confirmed version and the later uncertain attempt.
- Five native browser cases without JavaScript, at desktop and phone width, plus actual PNG output
  for numeric 0x and Equal Conditions in settings and lobby templates. Screenshots under
  `.screenshots/pick-{collection,publication}-{desktop,mobile}.png` and
  `.screenshots/time-progression-graphics/` were inspected.

The feature PR records the final canonical counts, exact head/base, coverage and CI/CodeQL evidence.

## Required corrections found during implementation

- Season restoration omitted owned car/track join tables. Restored pick history therefore failed
  the read-only audit. Restoring the exact joins and ordering catalog dependencies fixes that
  required round-trip without reevaluating saved membership.
- A legacy receipt alone must not trigger the private-archive readiness gate. Frozen legacy
  season/day identity also remains historical if its round later moves. Both have rejection/acceptance
  witnesses in PickBackupValidatorTest.
- Activation reviews undated unfinished drafts and rejects a live or unresolved legacy output.
  Confirmation after HTTP takes the shared season lock so it cannot race that classification.
- Original publication context needs foreign keys even after a legacy race moves. Both databases
  retain the referenced season, round and teams.
- A possibly accepted 5xx write followed by an unauthorized retry remains uncertain. Redirects and
  ephemeral/deferred responses cannot establish successful publication.
- Custom templates retain the existing explicit publishing path with an empty website-release mask.
  Requiring a builtin template would unnecessarily remove existing management functionality.

## Guard falsification and final review

The independent security pass reviewed `cd18a5b6` and found two medium-severity defects:

- V28 membership checks retained the H2 creator connection. The new connection regression test
  reproduced rejection of valid publication rows after that connection closed. All membership
  checks in this unreleased migration now use the existing CASE pattern.
- Activation and reconciliation GETs captured the restore generation after reading their rows.
  Both services now capture it before the first database read. Two regression tests change the
  epoch during that read and prove that the form retains its original, invalidated generation.

All three new witnesses first failed at their intended assertions, with zero JUnit errors.
The correction passed 18 unit and 68 H2/MariaDB integration cases, with no failures/errors/skips.
Independent re-review cleared both findings on `56c31bf4` with no further findings. Later commits
strengthen two tests without changing production code: uncertain snapshots include a possible
remote message ID, and static-export fixtures generate a participating team profile. The latter
also verifies that the profile-specific asset copier runs.

A final destination check found one additional medium-severity issue: an otherwise successful
settings/lobby response could name a missing or different channel. [Discord permits moving a
webhook](https://docs.discord.com/developers/resources/webhook#modify-webhook), so the captured
pairing channel must match the response before local confirmation. Four HTTP cases first failed
at the intended assertions; all 19 publication-service integration cases then passed. Preview
keeps its existing webhook-ID alias. Independent re-review cleared this correction on `c6c4ed70`.
The channel and public-flags guards were also falsified independently on that corrected source.

All 58 individual mutations reached their intended assertions, with zero JUnit errors. Source was
restored from the committed checkpoint after each probe. Invalid probes that stopped at an import
check were rejected; insufficiently isolated snapshot/profile witnesses were strengthened and rerun.
The table records only qualified final results.

Each probe uses the normal targeted Maven lifecycle. Body-only mutations retain the same APIs and
use `maven.compiler.useIncrementalCompilation=false` to compile changed classes without rebuilding
unchanged tests. This is limited to the isolated probes; the final canonical run uses the standard
clean command. See the [compiler plugin documentation](https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html#useIncrementalCompilation).

| Guard | Witness | Assertion failures |
| --- | --- | --- |
| persistent enrollment | `PickDisclosureServiceTest#givenManagedUnpublishedRace_whenCollectionIsEitherEnabledOrDisabled_thenWithholdEveryChoice` | 2 |
| latest content version | `PickDisclosureServiceTest#givenOlderFullAndNewerLobbyPublication_whenReading_thenNeverMixContentVersionsEvenIfOlderConfirmationIsLater` | 1 |
| confirmed outcomes only | `PickDisclosureServiceTest#givenUnconfirmedStoredAttempt_whenReading_thenKeepItWhollyWithheld` | 1 |
| original target only | `PickDisclosureServiceTest#givenAReassignedTargetWithOldLegacyEvidence_whenReading_thenKeepItsCurrentChoicesWithheld` | 1 |
| one content hash | `PickDisclosureServiceTest#givenConflictingHashesWithinOneVersion_whenReading_thenRejectInsteadOfUnioningDifferentChoices` | 1 |
| one origin | `PickDisclosureServiceTest#givenMixedApprovalAndCorrectionOriginsAtOneSourceVersion_whenReading_thenRejectTheirUnion` | 1 |
| one source version | `PickDisclosureServiceTest#givenConflictingProvenanceVersionsWithinOneContentVersion_whenReading_thenRejectInsteadOfMixingRevisions` | 1 |
| captured content hash | `PickDisclosureServiceTest#givenStoredHashThatDoesNotMatchCapturedContent_whenReading_thenRejectUnverifiedChoices` | 1 |
| lobby omits car | `PickPublicationContentTest#givenLobbyMask_whenProjecting_thenKeepCarAndPoolEntryPrivateWhileShowingOtherFields` | 1 |
| selective settings mask | `PickPublicationContentTest#givenWeatherOnlyMask_whenProjecting_thenNeverCarryOtherPrivateFieldsInsideTheSettingsValue` | 1 |
| known mask bits | `PickPublicationContentTest#givenKnownMask_whenEncodingAndDecoding_thenPreserveEveryFieldAndRejectUnknownBits` | 1 |
| snapshot requires success | `PickPublicationAttemptTest#givenPreparedOrUncertainAttempt_whenCreatingSnapshotWithoutConfirmation_thenReject` | 1 |
| snapshot requires fields | `PickPublicationAttemptTest#givenOutputWithNoProvenFields_whenConfirmed_thenNeverAdvancePublicContent` | 1 |
| uncertain needs resolution | `PickPublicationAttemptTest#givenUncertainDelivery_whenNormalConfirmationArrives_thenRequireExplicitReconciliation` | 1 |
| model resolution reason | `PickPublicationAttemptTest#givenUncertainAttempt_whenAbandoning_thenRequireARecordedManagementReasonAndReleaseTheActiveClaim` | 1 |
| approved state | `PickPublicationAuthorizationTest#givenUnapprovedOrUnrecordedChoices_whenAuthorizing_thenRejectBeforeAnyRenderingOrNetwork` | 1 |
| approved exact payload | `PickPublicationAuthorizationTest#givenUnapprovedOrUnrecordedChoices_whenAuthorizing_thenRejectBeforeAnyRenderingOrNetwork` | 1 |
| current provenance version | `PickPublicationAuthorizationTest#givenUnapprovedOrUnrecordedChoices_whenAuthorizing_thenRejectBeforeAnyRenderingOrNetwork` | 1 |
| exact migration classifications | `PickActivationServiceTest#givenIncompleteOrStaleMigrationReview_whenEnabling_thenRejectBeforeChangingAnyMarker` | 1 |
| public migration evidence | `PickActivationServiceTest#givenIncompleteOrStaleMigrationReview_whenEnabling_thenRejectBeforeChangingAnyMarker` | 3 |
| migration pending output | `PickActivationServiceTest#givenIncompleteOrStaleMigrationReview_whenEnabling_thenRejectBeforeChangingAnyMarker` | 1 |
| migration current generation | `PickActivationServiceTest#givenIncompleteOrStaleMigrationReview_whenEnabling_thenRejectBeforeChangingAnyMarker` | 1 |
| migration read generation | `PickActivationServiceTest#givenRestoreDuringTheReviewRead_whenPreparingActivation_thenBindTheFormToTheGenerationBeforeThoseReads` | 1 |
| publication read generation | `PickPublicationAdminServiceTest#givenRestoreDuringPublicationReviewRead_whenPreparingTheForm_thenRetainItsOriginalGeneration` | 1 |
| custom template conservative mask | `SettingsGraphicServiceTest#givenManagedRaceAndUnknownCustomTemplate_whenPublishing_thenKeepExplicitOutputWithoutInferringPublicFields` | 1 |
| actual image bytes | `PickPublicationEvidenceServiceTest#givenMismatchedOrUnverifiableOutput_whenReconciling_thenRejectWithoutReleasingPrivateValues` | 1 |
| public message flags | `PickPublicationEvidenceServiceTest#givenMismatchedOrUnverifiableOutput_whenReconciling_thenRejectWithoutReleasingPrivateValues` | 2 |
| exact request manifest | `PickPublicationEvidenceServiceTest#givenMismatchedOrUnverifiableOutput_whenReconciling_thenRejectWithoutReleasingPrivateValues` | 1 |
| manifest root shape | `PickPublicationManifestTest` | 1 |
| manifest file count | `PickPublicationManifestTest` | 1 |
| manifest file names | `PickPublicationManifestTest` | 2 |
| resolution current view | `PickPublicationPersistenceIT#givenStaleFormOrRestoreContext_whenReconciling_thenDenyBeforeVerification` | 1 |
| resolution current generation | `PickPublicationPersistenceIT#givenStaleFormOrRestoreContext_whenReconciling_thenDenyBeforeVerification` | 1 |
| resolution in flight exclusion | `PickPublicationPersistenceIT#givenVerificationInProgress_whenTryingToResolveTheSameOutputAgain_thenDenyBothConfirmationAndRetirement` | 1 |
| retirement remote acknowledgment | `PickPublicationPersistenceIT#givenUncertainOutput_whenAbandoning_thenRequireRemoteHandlingAndRetainTheDecisionWithoutReleasingFields` | 1 |
| render input freezing | `PickPublicationPersistenceIT#givenRenderingThatChangesCapturedChoices_whenPublishing_thenRollBackBeforeAnyNetworkOrPreparedAttempt` | 1 |
| remote success sql uncertainty | `PickPublicationPersistenceIT#givenRemoteSuccessAndLocalFailure_whenConfirming_thenPreserveUncertaintyInAnIndependentTransaction` | 1 |
| automatic private attachment skip | `DiscordPostServiceMatchPreviewAutoEditIT` | 2 |
| static race cache | `PickPublicationAssetsIT#givenPrivateCachedGraphicReferencedAsALogo_whenGeneratingTheSite_thenNeverCopyItsBytesOrLinkItsPath` | 3 |
| profile race cache | `PickPublicationAssetsIT#givenPrivateCachedGraphicReferencedAsALogo_whenGeneratingTheSite_thenNeverCopyItsBytesOrLinkItsPath` | 3 |
| attachment host admission | `DiscordWebhookClientIT#givenUnlistedLoopbackAttachmentHost_whenVerifying_thenRejectBeforeReadingEvenAValidImage` | 1 |
| attachment complete size | `DiscordWebhookClientIT#givenWrongSizeOrUnsuccessfulAttachment_whenDownloading_thenRejectInsteadOfTrustingMetadata` | 1 |
| bounded webhook wait | `DiscordWebhookClientIT#givenUnansweredWebhook_whenExecuting_thenBoundTheWaitAndReportAnUncertainTransportFailure` | 1 |
| ambiguous retry remains uncertain | `DiscordWebhookClientIT#givenPossiblyAcceptedFirstWriteAndRejectedRetry_whenExecuting_thenReportDeliveryUncertainty` | 1 |
| redirect is unconfirmed | `DiscordWebhookClientIT#givenRedirectWithAnApparentlyValidMessageBody_whenExecuting_thenNeverClaimSuccessfulDelivery` | 1 |
| send response public flags | `PickPublicationDiscordIT#givenEphemeralOrUnfinishedWebhookResponse_whenPublishing_thenRetainUncertaintyWithoutPublicRelease` | 2 |
| backup schema version | `PickBackupValidatorTest#givenForgedPublicationEvidence_whenValidating_thenRejectBeforeReplacement` | 1 |
| backup content hash | `PickBackupValidatorTest#givenForgedPublicationEvidence_whenValidating_thenRejectBeforeReplacement` | 1 |
| backup request hash | `PickBackupValidatorTest#givenForgedPublicationEvidence_whenValidating_thenRejectBeforeReplacement` | 1 |
| backup source history | `PickBackupValidatorTest#givenForgedPublicationEvidence_whenValidating_thenRejectBeforeReplacement` | 1 |
| legacy restore availability | `PickBackupValidatorTest#givenOnlyUnenrolledLegacyPublicationRecords_whenValidating_thenRetainLegacyRestoreAvailability` | 1 |
| legacy frozen context | `PickBackupValidatorTest#givenFrozenLegacyPublicationWhoseRoundLaterMovedSeason_whenValidating_thenKeepTheCapturedHistoricalIdentity` | 1 |
| h2 source membership | `V28PublicationConnectionMigrationTest` | 1 |
| h2 output membership | `V28PublicationConnectionMigrationTest` | 1 |
| h2 state membership | `V28PublicationConnectionMigrationTest` | 1 |
| h2 active membership | `V28PublicationConnectionMigrationTest` | 1 |
| retain original round | `PickPublicationPersistenceIT#givenFrozenOriginalRoundAfterALegacyRaceMoves_whenDeletingItsOriginalRound_thenRetainTheReferencedPublicationIdentity` | 1 |
| confirmed destination channel | `PickPublicationDiscordIT#givenMissingOrDifferentResponseChannel_whenPublishing_thenKeepTheOutputUncertainWithoutConfirmingItsDestination` | 4 |

## Release gates

Before merge, verify the final committed head against the current upstream base using
`./mvnw clean verify -Pe2e -Ddocker.available=true`, including coverage, Checkstyle, SpotBugs and
the loaded find-sec-bugs guard. Require all exact-head GitHub checks, including CodeQL.
The [issue](https://github.com/jegr78/ctc-manager/issues/285) and its feature PR retain the final
counts and merge evidence.

After the verified merge, reconcile the issue and parent hierarchy, publish the prepared Wiki
update and remove only the verified landed topic branch. No live Discord messages, command
registration or production changes are required or performed by this verification.
