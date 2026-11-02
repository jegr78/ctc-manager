# Private picks dialogs

Issue #283 implements team entry on the domain and outgoing Gateway delivered in #281 and #282. Collection stays disabled until #285 supplies confidentiality readiness. No database shape, management review action or publication behavior changes here.

## Delivery checklist

- [x] Record requirements and reproduce missing behavior before production changes.
- [x] Implement transient bound sessions, catalog search and staged private entry.
- [x] Wire the handler and SDK components without changing privacy gates.
- [x] Verify durable confirmation, team-wide viewing and rejected stale/unauthorized actions offline.
- [ ] Review code and obtain independent security review; correct findings.
- [ ] Falsify new guards individually and restore exact committed source.
- [ ] Run the canonical committed-head clean verification and matching CI.
- [ ] Merge, reconcile hierarchy and Wiki, and remove the own topic branch.

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
