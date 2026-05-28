---
phase: 94
plan: 03
slug: team-roles-match-channel-lifecycle
status: shipped
shipped: 2026-05-22
requirement: CHAN-03
---

# Plan 94-03 — CHAN-03 Archive modal + DiscordCategoryResolver + UAT-04 staged (Phase 94 close)

Closed CHAN-03 inline on `gsd/v1.13-discord-integration` (Wave 3; depends on Plan 94-02 having shipped the outer modal container + `ArchiveCategory` stub record + `MatchController` dependencies + 7 Match Discord fields). Plan 94-03 close = Phase 94 close. No new Flyway migration: `DiscordCategoryResolver` reads live Discord data and never persists.

Delivered the operator-facing archive flow that closes the channel lifecycle: a `DiscordCategoryResolver` service discovers archive categories via a regex over the guild's channel list, derives `currentChannelCount` from `parent_id` cross-references, sorts by `num` ASC, and picks the highest-num-with-room as the default modal selection. `MatchController` gains a single new endpoint `POST /{id}/move-to-archive` (CSRF-protected, `@RequestParam String categoryId`) that PATCHes `parent_id` on the Discord channel and propagates typed errors through the existing `applyErrorFlash` BEM-badge helper from Plan 94-02. The Plan 94-02 modal container is filled with a Thymeleaf radio-button list + empty-list warning banner.

UAT-04 (Live-Discord Channel Lifecycle Smoke) is staged in `.planning/STATE.md § Pending UATs` with the inline 7-step operator procedure — required before Phase 95 plans 95-02/03/04 start (per CONTEXT D-12).

## Files modified

| File | Change |
|------|--------|
| `src/main/java/org/ctc/discord/service/DiscordCategoryResolver.java` | New `@Slf4j @Service @RequiredArgsConstructor`. Compiled `Pattern ARCHIVE_NAME = "^Match Days Archive (?<year>\\d{4})(?: \\((?<num>\\d+)\\))?$"` + class-private `CATEGORY_TYPE = 4` and `CATEGORY_LIMIT = 50`. Public `resolveArchiveCategoriesFor(int year) throws DiscordApiException` (filter type-4 → regex match → year filter → count from `parent_id` cross-ref → sort ASC by num → `Stream.toList()`). Public `defaultSelection(List)` returns the highest-num entry with `currentChannelCount < CATEGORY_LIMIT`, `Optional.empty()` if all full. |
| `src/main/java/org/ctc/domain/service/MatchService.java` | `getDetailData(UUID)` body replaced — calls `discordCategoryResolver.resolveArchiveCategoriesFor(LocalDate.now(clock).getYear())` + `defaultSelection(...)` and propagates both into `MatchDetailData`. Try/catch `DiscordApiException` degrades gracefully: log.warn + return empty list + null default-id (modal renders the all-full warning banner). New dependencies via `@RequiredArgsConstructor`: `DiscordCategoryResolver` + `java.time.Clock` (reuses Phase 93 `DiscordConfig systemClock` bean). |
| `src/main/java/org/ctc/admin/controller/MatchController.java` | New `@PostMapping("/{id}/move-to-archive")` endpoint with `@RequestParam(required = false) String categoryId`. Body: `BusinessRuleException` when `match.getDiscordChannelId() == null` (flash `not-found`); `DiscordCategoryFullException(CATEGORY_FULL_MESSAGE, null)` when `categoryId` blank/null (semantic equivalence per CONTEXT § Specifics 3 — empty selection = no valid target → flash `category-full`); happy path calls `discordRestClient.modifyChannel(channelId, new ChannelModifyRequest(null, categoryId))` → flash success. Typed-catch reuses `applyErrorFlash` from Plan 94-02. New dependency `DiscordRestClient` via existing `@RequiredArgsConstructor`. |
| `src/main/resources/templates/admin/match-detail.html` | Modal body filled inside Plan-94-02 outer container: `h3.modal-title` "Move Channel to Archive", Thymeleaf form action `/admin/matches/{id}/move-to-archive`, `th:if="${archiveCategories.isEmpty()}"` `.alert-warning` with runbook anchor (Phase 98 DOCS-02 fills `docs/operations/discord-integration.md`), `th:each` radio-button block with `name="categoryId"` + `th:checked` honouring `defaultSelectionId`, label `{name} — {count}/50`, Confirm (`data-testid='archive-confirm'`, `th:disabled` when list empty) + Cancel (CSS only, no inline styles). Modal trigger/cancel switched to `style.display='flex'/'none'` aligning with `season-detail.html` convention — Plan 94-02's `classList.add('is-open')` left modal hidden (no matching `.modal-overlay.is-open` CSS rule). |
| `.planning/STATE.md` | New `### UAT-04: Live-Discord Channel Lifecycle Smoke (Phase 94 CHAN-01/02/03)` block under Pending UATs with the inline 7-step procedure (refresh roles → set category → assign team roles → create channel → audit-fail + cleanup → move-to-archive happy → category-full). Status `pending operator action — required before Phase 95 plans 95-02/03/04 start`. `Current Position` + `Session Continuity` + frontmatter `progress.completed_plans=10` updated to reflect Phase 94 close (3 of 3 plans shipped). |

## Tests added (5)

| Test class | `@Tag` | Methods | Coverage |
|------------|--------|---------|----------|
| `DiscordCategoryResolverTest` | untagged | 6 (Mockito) | Regex without/with suffix → num=1 / num=N; year mismatch excluded; sort ASC by num; `defaultSelection` empty when all 50; `defaultSelection` returns highest-num with room. |
| `DiscordCategoryResolverWireMockIT` | `integration` | 4 | Type-filter (only `type==4` matching regex returned, count derived from siblings); 47-channel count derivation; multi-year filter (2025 vs 2026); multi-num sort. |
| `DiscordChannelArchiveServiceWireMockIT` | `integration` | 3 (MockMvc) | Happy PATCH with `matchingJsonPath("$.parent_id")`; 404 Unknown Channel → flash `not-found`; Discord-side code 30013 → flash `category-full`. |
| `MatchControllerMoveToArchiveErrorCategoryTest` | untagged | 6 (4 param + 2 plain) | BEM-mapping for all 4 Category enum values; blank `categoryId` → CATEGORY_FULL flash + no outbound PATCH; missing `discordChannelId` → not-found flash + no outbound PATCH. |
| `ArchiveModalE2ETest` | `e2e` | 5 (Playwright + WireMock) | Modal-open with 2 categories renders both radio buttons with counts and pre-selects the highest-num-with-room; all-full 50/50 case has radio visible but unchecked; empty-list shows warning banner + disabled Confirm; submit-roundtrip verifies `$.parent_id` PATCH and success flash; mobile 375×667 has no horizontal overflow. |

Plan 94-02 `MatchDetailControllerE2ETest` (4 cases) continues to pass — the resolver returns the empty/no-categories degenerate case via the test's lack of WireMock listChannels stub.

## Quality gates (local `./mvnw clean verify -Pe2e` on commit `b86bd07f`)

- BUILD SUCCESS in **8:56 min**.
- JaCoCo line coverage **89.49 %** (8324 covered / 9302 total) — above v1.11 baseline 88.88 %.
- SpotBugs **0 BugInstances**.
- `BackupSchemaGuardTest` green at `EXPORT_ORDER.length == 24` + `SCHEMA_VERSION == 1` — Plan 94-03 added no new entity.
- Flyway V1-V10 unchanged; Plan 94-03 adds NO new migration.
- Branch identity `gsd/v1.13-discord-integration` preserved end-to-end. No subagents, no worktrees ([[feedback-inline-sequential-execution]]).

## Threats addressed / carried forward

| Threat ID | Disposition | Proof |
|-----------|-------------|-------|
| T-93-03 carry-forward closure | accept (no change) | The PATCH only updates `parent_id`; permission-overwrites set in 94-02's `createMatchChannel` remain unchanged. Discord propagates category permission-defaults via lookups, NOT by overwriting the channel's own overwrites. |
| T-93-04 carry-forward | accept | Modal renders are operator-rare; `DiscordRateLimitInterceptor` (Phase 93) handles bursts. No `listChannels` caching per CONTEXT D-02 — counts must be live. |
| T-91-02-IL carry-forward | mitigate | `applyErrorFlash` helper from Plan 94-02 routes all Discord exceptions through hardcoded `_MESSAGE` constants. `BusinessRuleException` echoes its own hardcoded string ("Match has no Discord channel to archive."); no upstream Discord-exception text leaks. Proven by `MatchControllerMoveToArchiveErrorCategoryTest`. |
| T-94-03-01 (Tampering on `categoryId`) | mitigate | No regex validation — any snowflake-shaped string is passed to Discord; Discord rejects malformed IDs. Empty/blank → typed `DiscordCategoryFullException` → `category-full` flash. No injection surface (Spring binds `String` → no SpEL/SQL paths). |
| T-94-03-02 (Spoofing via regex) | accept | Anchored `^...$` regex + named groups defends against partial-match injection. Worst case: operator sees a misnamed category in the modal; selecting it just PATCHes to the wrong parent. Discord-side authorisation guards the actual move. |

## Decisions made during execution

- **D-Modal-Display-Switch (operator-fix during template author work).** Plan 94-02 shipped the modal trigger button with `onclick="document.getElementById('archiveModal').classList.add('is-open')"`. The admin.css `.modal-overlay` rule has `display: none;` and no matching `.is-open` variant — clicking the trigger never showed the modal. Plan 94-03 switched both open + cancel handlers to `style.display='flex'/'none'`, aligning with the existing `season-detail.html` modal convention.
- **D-Mocked-Verify-Throws-Exception (test compile fix).** `MatchControllerMoveToArchiveErrorCategoryTest` declares `throws Exception` on the two non-parameterized methods because Mockito's `verify(restClient).modifyChannel(...)` invokes the method through cglib — the compiler sees the checked `DiscordApiException` declaration on `modifyChannel` even though Mockito proxies it.

## Wave-pause artifacts

Screenshots under `.screenshots/94-03/` (gitignored locally):
- `match-detail-desktop.png` — match detail page Desktop 1280×800 (default state: no Discord channel → Move-to-Archive button hidden, Create-Channel button disabled).
- `match-detail-mobile.png` — same page at Mobile 375×667 — `.discord-actions` cluster wraps cleanly, no horizontal overflow.
- `archive-modal-empty-desktop.png` — Move-Channel-to-Archive modal in the empty-list state (via Playwright eval `style.display='flex'`): title "Move Channel to Archive" + `.alert-warning` banner "All archive categories are full — see runbook." + disabled Confirm + Cancel buttons.
- `archive-modal-empty-mobile.png` — same modal at Mobile 375×667.

The happy-path "modal-open with radio buttons + counts + pre-selected default" state is covered programmatically by `ArchiveModalE2ETest` (Playwright + WireMock listChannels stub returning 2 archive categories) — live screenshots require an operator's test guild with archive categories. UAT-04 step 6 captures this state operator-side.

PR #130 body appended with Plan 94-03 row (REQ CHAN-03, commit SHA pending push, Phase 94 close note). Awaiting:
1. `/gsd-validate-phase 94` — Nyquist sampling across all 23 test classes; flip `nyquist_compliant: true` in 94-VALIDATION.md frontmatter.
2. Operator UAT-04 staging — 7-step live-Discord smoke against test guild before `/gsd-discuss-phase 95`.
