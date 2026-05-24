---
phase: 97
slug: matchday-level-posts
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-24
---

# Phase 97 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source: `97-RESEARCH.md` § "Validation Architecture (Nyquist)".

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test + WireMock 3 + Playwright |
| **Config file** | `pom.xml` (Surefire + Failsafe + JaCoCo) |
| **Quick run command** | `./mvnw -Dtest=<ClassName> test` (Surefire) or `./mvnw -Dit.test=<ClassName> -DfailIfNoTests=false verify -Pe2e` (Failsafe) |
| **Full suite command** | `./mvnw clean verify -Pe2e` |
| **Estimated runtime** | ~17–19 minutes full suite (per v1.12 CI baseline + Phase 97 ~8-12 WireMock-IT + 3 E2E + 3 Mobile-Sweep deltas) |

---

## Sampling Rate

- **After every task commit:** Run targeted `./mvnw -Dtest=<TouchedClassName> test` or `./mvnw -Dit.test=<TouchedITName> -DfailIfNoTests=false verify` for the file changed.
- **After every plan wave:** Run `./mvnw clean verify` (Unit + Integration; skip Playwright if no template/CSS change in the wave).
- **Before `/gsd-validate-phase 97`:** Full suite `./mvnw clean verify -Pe2e` must be green.
- **Max feedback latency:** ~30s targeted; ~5 min `clean verify` (no `-Pe2e`); ~17–19 min full `clean verify -Pe2e`.

---

## Per-Task Verification Map

> Concrete task IDs are written by `gsd-planner` per plan. The map below pins the **verification-type per area** so the planner can fill task IDs while preserving sampling continuity.

| Area | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | Status |
|------|------|------|-------------|------------|-----------------|-----------|-------------------|--------|
| MATCH_PREVIEW Markdown body | 97-01 | 1 | POST-06 | SSRF (DiscordHostValidator already wired) | discord.com host only via positive whitelist | unit | `./mvnw -Dtest=MatchPreviewMarkdownBuilderTest test` | ⬜ pending |
| `MatchService.save` Pre/Post-Diff publish | 97-01 | 1 | POST-06 | — | `MatchPreviewFieldsChangedEvent` published only on `streamLink`/`discordTeaser` diff (null-safe) | unit | `./mvnw -Dtest=MatchServicePreviewDiffPublishTest test` | ⬜ pending |
| `DiscordPostService.postMatchPreview` multipart POST + 4 sealed exception permits | 97-01 | 1 | POST-06 | T-93 4-permit DiscordApiException | transient/auth/not-found/permission all handled | integration | `./mvnw -Dit.test=DiscordPostServiceMatchPreviewIT -DfailIfNoTests=false verify` | ⬜ pending |
| `DiscordPostService.autoEditMatchPreviewIfNeeded` PATCH (no `@MockitoBean` on post-service per WireMock-vs-Real-API discipline) | 97-01 | 1 | POST-06 | — | row-existence check + Webhook-PATCH or no-op; real `@Transactional` proxy runs in IT | integration | `./mvnw -Dit.test=DiscordPostServiceMatchPreviewAutoEditIT -DfailIfNoTests=false verify` | ⬜ pending |
| `MatchController.postMatchPreview` POST endpoint + flash error surfacing | 97-01 | 1 | POST-06 | — | 5 distinct disabled-tooltip strings per pre-flight predicate; flash error on Webhook failure | integration | `./mvnw -Dit.test=MatchControllerPostMatchPreviewIT -DfailIfNoTests=false verify` | ⬜ pending |
| Match-Detail button visibility + label transitions (Post → Re-Post) + Mobile-Sweep | 97-01 | 1 | POST-06 | — | button hidden when pre-flight fails; tooltip shown; mobile viewport renders cluster column-stack | e2e | `./mvnw -Dit.test=MatchDetailPreviewButtonE2ETest -DfailIfNoTests=false verify -Pe2e` | ⬜ pending |
| `MatchdayResultsGraphicService.generateResults` byte[] reuse | 97-02 | 1 | POST-07 | — | existing byte[] return — no new variant; reuse verified | unit | `./mvnw -Dtest=MatchdayResultsGraphicServiceContractTest test` (smoke — Playwright excluded from JaCoCo) | ⬜ pending |
| `DiscordPostService.postMatchdayResults` POST-07a + thread_id forum-target | 97-02 | 1 | POST-07 | — | `?thread_id=` overload reused from Phase 96; queryParam asserted on WireMock stub | integration | `./mvnw -Dit.test=DiscordPostServiceMatchdayResultsIT -DfailIfNoTests=false verify` | ⬜ pending |
| `DiscordPostService.postPowerRankings` POST-07b + no `allMatchesFinal` gate | 97-02 | 1 | POST-07 | — | pre-flight loosens vs POST-07a; reflects current `SeasonTeam.rating` desc | integration | `./mvnw -Dit.test=DiscordPostServicePowerRankingsIT -DfailIfNoTests=false verify` | ⬜ pending |
| `MatchdayController` 2 POST endpoints + per-button pre-flight | 97-02 | 1 | POST-07 | — | 2 independent endpoints; distinct pre-flight per button | integration | `./mvnw -Dit.test=MatchdayControllerPostEndpointsIT -DfailIfNoTests=false verify` | ⬜ pending |
| Matchday-Detail NEW Discord Actions card with 2 buttons + Mobile-Sweep | 97-02 | 1 | POST-07 | — | 2 buttons in `.discord-actions--posts`; mobile column-stack via admin.css 221-228; stale-detection yellow-signal | e2e | `./mvnw -Dit.test=MatchdayDetailDiscordActionsE2ETest -DfailIfNoTests=false verify -Pe2e` | ⬜ pending |
| V14 `add_discord_post_phase_id` migration (H2 + MariaDB) | 97-03 | 1 | POST-08 | — | FK `ON DELETE SET NULL` + index; existing post-types unaffected (NULL phase_id) | integration | `./mvnw -Dit.test=DiscordPostV14MigrationIT -DfailIfNoTests=false verify` | ⬜ pending |
| `StandingsGraphicService` per PhaseLayout (1 PNG / N PNGs) | 97-03 | 1 | POST-08 | — | REG-no-groups=1; REG-groups=N (sorted by SeasonPhaseGroup.sortIndex); PLAYOFF/PLACEMENT=1 | unit (contract; Playwright runtime excluded from JaCoCo) | `./mvnw -Dtest=StandingsGraphicServiceContractTest test` | ⬜ pending |
| `DiscordPostService.postStandings(season, phase)` multipart + identity-key `(channelId, STANDINGS, seasonId, phaseId)` | 97-03 | 1 | POST-08 | — | one row per `(season_id, phase_id)`; Re-Post replaces N attachments atomically | integration | `./mvnw -Dit.test=DiscordPostServiceStandingsIT -DfailIfNoTests=false verify` (all 4 phase-layout combinations) | ⬜ pending |
| `SeasonController.postStandings` phase-selector form-DTO binding + `@NotNull phaseId` | 97-03 | 1 | POST-08 | Mass-Assignment (DTO not entity per CLAUDE.md "Keep Controllers Thin") | form binding via `PostStandingsForm`, never entity; `@NotNull phaseId` rejected on missing | integration | `./mvnw -Dit.test=SeasonControllerPostStandingsIT -DfailIfNoTests=false verify` | ⬜ pending |
| `StandingsService.hasNewerResultsSincePhaseScoped` stale-detection | 97-03 | 1 | POST-08 | — | MAX(RaceResult.updatedAt WHERE phase=?) > standingsPost.updatedAt; per-phase scope | integration | `./mvnw -Dit.test=StandingsServicePhaseScopedStaleDetectionIT -DfailIfNoTests=false verify` | ⬜ pending |
| `DiscordPostRef.SeasonRef` widening compile-check (Phase 96 FORUM-02 callsite passes `phaseId=null`) | 97-03 | 1 | POST-08 | — | sealed-switch exhaustiveness preserved; Phase 96 callsites still compile | unit | `./mvnw -Dtest=DiscordPostRefSeasonRefWidenedTest test` | ⬜ pending |
| Season-form Post Standings button + per-phase dropdown + Mobile-Sweep | 97-03 | 1 | POST-08 | — | dropdown lists existing `SeasonPhase`; auto-hide when N=1; mobile responsive | e2e | `./mvnw -Dit.test=SeasonFormStandingsButtonE2ETest -DfailIfNoTests=false verify -Pe2e` | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

> The planner replaces each Area row with one or more concrete `{N}-{plan}-{task}` IDs that map 1:1 to PLAN.md tasks. Sampling continuity: no plan should have 3 consecutive task-commits without at least one `<automated>` block from the column above.

---

## Wave 0 Requirements

Phase 97 is an extension of an existing, fully-tested test infrastructure (Surefire / Failsafe / WireMock / Playwright already wired since Phases 92-96). **No Wave 0 setup required.** Each Plan's first task adds a fresh test class against the existing infrastructure.

- [x] JUnit 5 / Mockito / Spring Boot Test in `pom.xml` (existing)
- [x] WireMock 3 fixture pattern from `DiscordPostServiceScheduleIT` (existing Phase 95)
- [x] Playwright E2E pattern from `org.ctc.e2e.discord.*` (existing Phase 95/96)
- [x] `MatchDetailE2ETestBase` / `MatchdayDetailE2ETestBase` / `SeasonFormE2ETestBase` pattern (existing or inline-reusable)

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| UAT-07 Step 1 — Match Preview multipart-POST lands in announcement-webhook channel with Settings.png + Lineups.png side-by-side | POST-06 | Live Discord-Guild required; bot-token + webhook out of WireMock scope | CONTEXT.md § Q-97-meta-uat D-97-10 Step 1 |
| UAT-07 Step 2 — MATCH_PREVIEW auto-PATCH within ~5s after `streamLink` edit; Discord `(edited)` indicator shows | POST-06 | Same; live latency observation | CONTEXT.md § Q-97-meta-uat D-97-10 Step 2 |
| UAT-07 Step 3 — Same auto-PATCH after `discordTeaser` edit | POST-06 | Same | CONTEXT.md § Q-97-meta-uat D-97-10 Step 3 |
| UAT-07 Step 4 — POST-07a PNG lands in race-results-forum-thread; auto-unarchive triggered if archived | POST-07 | Live forum-thread state observation | CONTEXT.md § Q-97-meta-uat D-97-10 Step 4 |
| UAT-07 Step 5 — POST-07a button flips to "Update Match Day Results" yellow-signal after RaceResult update; Re-PATCH succeeds | POST-07 | Stale-detection visual cue | CONTEXT.md § Q-97-meta-uat D-97-10 Step 5 |
| UAT-07 Step 6 — POST-07b POST lands sequentially in same forum-thread after `SeasonTeam.rating` update; reflects new rating order | POST-07 | Operator-curated rating order verification | CONTEXT.md § Q-97-meta-uat D-97-10 Step 6 |
| UAT-07 Step 7 — POST-08 PNG lands in standings-forum-thread; per-phase identity preserved | POST-08 | Live thread placement | CONTEXT.md § Q-97-meta-uat D-97-10 Step 7 |
| UAT-07 Step 8 — POST-08 button flips to "Update Standings" yellow-signal after RaceResult; Re-PATCH succeeds | POST-08 | Stale-detection visual cue | CONTEXT.md § Q-97-meta-uat D-97-10 Step 8 |
| UAT-07 Step 9 — `/admin/discord/posts` filter by season shows all new post-types with `attachments_replaced_at` after Re-Post | POST-06/07/08 | Admin-UI lookup; pixel verification | CONTEXT.md § Q-97-meta-uat D-97-10 Step 9 |
| Iterative visual approval of `StandingsGraphicService` per phase-layout | POST-08 | Pixel-positioning + iterative design loop (`[[feedback-graphic-pixel-positioning]]` + `[[feedback-graphic-design-iteration]]`) | Plan 97-03 Wave 2+ — `playwright-cli` screenshots per layout step → operator approval before next commit |

*UAT-07 (live operator-guild Matchday-Posts Lifecycle) is staged in STATE.md as a Pending UAT — runs after Phase 97 closes and before `/gsd-execute-phase 98` start, per D-97-10. Phase 97 itself closes when WireMock-IT + Playwright-E2E suite is green.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies (Wave 0 not needed — existing infra)
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify (planner enforces during task breakdown)
- [ ] Wave 0 covers all MISSING references (N/A — existing infra)
- [ ] No watch-mode flags (Maven CLI only; no `mvn -Dtest=Foo watch`)
- [ ] Feedback latency < 30s targeted / < 19min full
- [ ] `nyquist_compliant: true` set in frontmatter (after plan-checker approves task-to-area mapping)

**Approval:** pending — awaiting `gsd-planner` task-ID fill-in + `gsd-plan-checker` Dimension 8 review.
