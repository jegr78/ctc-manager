# Private picks dialogs

Issue #283 implements team entry on the domain and outgoing Gateway delivered in #281 and #282. Collection stays disabled until #285 supplies confidentiality readiness. No database shape, management review action or publication behavior changes here.

## Delivery checklist

- [x] Record requirements and reproduce missing behavior before production changes.
- [x] Implement transient bound sessions, catalog search and staged private entry.
- [x] Wire the handler and SDK components without changing privacy gates.
- [x] Verify durable confirmation, team-wide viewing and rejected stale/unauthorized actions offline.
- [x] Review code and obtain independent security review; correct findings.
- [x] Falsify new guards individually and restore exact committed source.
Final gates remain in the PR delivery checklist: the canonical committed-head clean verification, matching CI, actual merge, hierarchy/Wiki reconciliation and removal of the own topic branch. Final counts and the verified SHA are recorded there after execution.

## Requirements matrix

| Requirement | Production path | Positive test | Rejection test | Runtime witness |
| --- | --- | --- | --- | --- |
| Only currently authorized effective-home targets | PickInteractionHandler, PickTargetService, private read service | Multiple seasons, return leg and complete labels | Other channel/user, reorientation, bye/decided/result targets | Offline dispatcher plus real transactional services |
| Drafts are private, transient and expire after 30 minutes | PickDraftSessionStore | Actor/channel/race/version-bound draft | Forged token, wrong owner, expiry boundary, restore generation, capacity | Mutable clock and concurrent actions |
| Local season catalog, large lists and category policy reuse | Private catalog selector and RaceCarSelectionService | Search and pagination, category member selection | Forged/out-of-page/used choice and changed eligibility | More than 25 synthetic cars/tracks |
| All 13 inputs, explicit defaults and full summary | Interaction views and typed PickPayload | Equal Conditions, zero multiplier, Default fuel, None/multiple tyres | Invalid numbers, missing text, mandatory tyres outside available set | Full staged offline transport flow |
| Supported private Discord components | PickGatewayResponse, JdaPickGatewayResponse | Ephemeral menus/buttons, immediate modal, button between modal stages | No modal chaining, no normal reply, bounded components | Serialized production SDK message/modal payload |
| Only explicit final confirmation saves | PickInteractionHandler and PickSubmissionService | Durable receipt after commit and replay identity | Abandonment, stale aggregate, revoked permission, rollback | Real admitted transaction and offline transport |
| Team members retrieve authoritative saved state | Private read service and interaction views | Current payload, author/time, reason, deadline and overrides | Other team and partial draft access | Two synthetic members; lost-response recovery |

## Operator UAT

Automated runs use fake Discord transport and synthetic records. Live UAT requires separately authorized operator test-guild configuration. In that isolated guild, verify private chooser labels, search beyond 25 options, all input stages, full summary, explicit confirmation, second-member viewing, stale replacement conflict, permission revocation and timeout recovery. Confirm every response is ephemeral and that no ordinary channel message appears. Never use real team picks. Production collection remains off until confidentiality and rollout acceptance.

## Focused evidence

- The initial entry test failed at the intended private-chooser assertion with one failure and zero errors. Cross-stage field injection, a blocking concurrent callback and restore-generation adoption were each reproduced at their intended assertions before correction.
- Independent review found that a completed race disappeared from private recovery. A failing target-list assertion reproduced it. Confirmed own history now remains a read-only chooser target; new entries still require the existing scheduled-target policy. The real transactional workflow proves original-receipt replay after a recorded match score.
- The reviewed focused lifecycle passed 93 unit tests and eight integration tests, including fixed and category entry, two members, stale drafts, durable replay, post-deadline correction and revoked/restore sessions. Follow-up guard witnesses add retired-route and withdrawn-choice checks.
- A focused `verify` invocation reached the global coverage gate with only selected tests and failed that gate at 23%. It is not a full verification result. No coverage or analysis gates were changed.
- Production JDA message/modal serialization verifies private menus/buttons, modal selects and the supported component counts. No HTML/CSS template changes require browser layout verification. Live Discord client UAT remains an operator step, with its narrow plan above.

Discord component constraints were checked against the [official interaction documentation](https://github.com/discord/discord-api-docs/blob/main/developers/interactions/receiving-and-responding.mdx), [modal components](https://github.com/discord/discord-api-docs/blob/main/developers/components/using-modal-components.mdx) and the pinned JDA API.

## Review and individual guard witnesses

The independent security pass identified one medium recovery finding. It is corrected and re-reviewed with **No findings** on `6e3b9aa6148ed11d4351f46813f88b0d6fcb238e`. The self-review also checked transaction boundaries, component limits and existing privacy gates. The explicit private transport and existing durable replay contract are retained.

The final focused baseline passed 96 unit tests; the preceding real-service workflow passed eight integration tests. Each of the following 32 mutations was applied separately to the correct committed source, reached the intended assertion failure with zero JUnit errors, and was restored byte-for-byte from that commit. The correct worktree was clean after all probes.

| Guard | Assertion witness | Failures | Errors |
| --- | --- | ---: | ---: |
| draft-lifetime | `PickDraftSessionStoreTest#givenThirtyMinuteLifetime_whenAtBoundaryOrAfterRestore_thenInvalidateWithoutExtendingDeadline` | 1 | 0 |
| draft-expiry | `PickDraftSessionStoreTest#givenThirtyMinuteLifetime_whenAtBoundaryOrAfterRestore_thenInvalidateWithoutExtendingDeadline` | 1 | 0 |
| draft-generation | `PickDraftSessionStoreTest#givenThirtyMinuteLifetime_whenAtBoundaryOrAfterRestore_thenInvalidateWithoutExtendingDeadline` | 1 | 0 |
| draft-guild | `PickDraftSessionStoreTest#givenBoundSession_whenResolvingToken_thenAcceptOnlyExactActorChannelKindAndNonce` | 1 | 0 |
| draft-channel | `PickDraftSessionStoreTest#givenBoundSession_whenResolvingToken_thenAcceptOnlyExactActorChannelKindAndNonce` | 1 | 0 |
| draft-user | `PickDraftSessionStoreTest#givenBoundSession_whenResolvingToken_thenAcceptOnlyExactActorChannelKindAndNonce` | 1 | 0 |
| route-kind | `PickDraftSessionStoreTest#givenBoundSession_whenResolvingToken_thenAcceptOnlyExactActorChannelKindAndNonce` | 1 | 0 |
| route-nonce | `PickDraftSessionStoreTest#givenBoundSession_whenResolvingToken_thenAcceptOnlyExactActorChannelKindAndNonce` | 1 | 0 |
| draft-capacity | `PickDraftSessionStoreTest#givenBoundedStore_whenFull_thenRefuseNewDraftUntilExpiredEntriesAreRemoved` | 1 | 0 |
| nonblocking-session | `PickDraftSessionStoreTest#givenInFlightSessionWork_whenAnotherInteractionArrives_thenRejectImmediatelyWithoutBlockingAcknowledgement` | 1 | 0 |
| retired-route | `PickDraftSessionStoreTest#givenAdvancedDialog_whenOldComponentIsDelivered_thenRejectRetiredRoute` | 1 | 0 |
| stage-fields | `PickInteractionHandlerTest#givenForgedExtraModalFields_whenSubmittingFuel_thenRejectCrossStageChanges` | 1 | 0 |
| final-generation | `PickInteractionHandlerTest#givenRestoreDuringFinalRead_whenNewAuthorityIsIssued_thenNeverAdoptNewGenerationForOldDraft` | 1 | 0 |
| current-choice | `PickInteractionHandlerTest#givenWithdrawnCatalogChoice_whenSelectingOldMenu_thenRejectBeforeAdvancing` | 1 | 0 |
| target-choice | `PickInteractionHandlerTest#givenForgedTargetSelection_whenSelecting_thenDoNotReadAnyPrivateTarget` | 1 | 0 |
| numeric-minimum | `PickInteractionViewsTest#givenInvalidField_whenValidatingStage_thenRejectBeforeSavingDraft` | 1 | 0 |
| numeric-maximum | `PickInteractionViewsTest#givenInvalidField_whenValidatingStage_thenRejectBeforeSavingDraft` | 5 | 0 |
| text-required | `PickInteractionViewsTest#givenInvalidField_whenValidatingStage_thenRejectBeforeSavingDraft` | 2 | 0 |
| text-bound | `PickInteractionViewsTest#givenBoundedCompleteTextAndTyres_whenValidating_thenRetainAllRequiredFields` | 1 | 0 |
| single-value | `PickInteractionViewsTest#givenBoundedCompleteTextAndTyres_whenValidating_thenRetainAllRequiredFields` | 1 | 0 |
| unique-tyres | `PickInteractionHandlerTest#givenInvalidTyres_whenSubmittingFuel_thenRejectWithoutAdvancing` | 1 | 0 |
| none-exclusive | `PickInteractionHandlerTest#givenInvalidTyres_whenSubmittingFuel_thenRejectWithoutAdvancing` | 1 | 0 |
| mandatory-subset | `PickInteractionHandlerTest#givenInvalidTyres_whenSubmittingFuel_thenRejectWithoutAdvancing` | 1 | 0 |
| message-length | `PickInteractionViewsTest#givenInteractiveMessageLimits_whenBuilding_thenRejectOverlongContentAndTooManyControls` | 1 | 0 |
| message-buttons | `PickInteractionViewsTest#givenInteractiveMessageLimits_whenBuilding_thenRejectOverlongContentAndTooManyControls` | 1 | 0 |
| select-limit | `PickInteractionViewsTest#givenInteractiveMessageLimits_whenBuilding_thenRejectOverlongContentAndTooManyControls` | 1 | 0 |
| interactive-expiry | `PickGatewayReplyTest#givenInteractiveContinuation_whenLiveThenExpiredOrReset_thenForwardOnlyLivePrivateComponents` | 1 | 0 |
| interactive-generation | `PickGatewayReplyTest#givenInteractiveContinuation_whenLiveThenExpiredOrReset_thenForwardOnlyLivePrivateComponents` | 1 | 0 |
| interactive-private | `JdaPickGatewayResponseTest#givenPrivateInteractiveReply_whenSending_thenSerializeBoundedButtonsAndMenusWithExplicitPrivacy` | 1 | 0 |
| interactive-mentions | `JdaPickGatewayResponseTest#givenPrivateInteractiveReply_whenSending_thenSerializeBoundedButtonsAndMenusWithExplicitPrivacy` | 1 | 0 |
| history-recovery | `PickTargetServiceTest#givenOwnConfirmedPickAfterRaceDecision_whenListing_thenKeepReadOnlyRecoveryTarget` | 1 | 0 |
| history-read-only | `PickTargetServiceTest#givenOwnConfirmedPickAfterRaceDecision_whenListing_thenKeepReadOnlyRecoveryTarget` | 1 | 0 |
