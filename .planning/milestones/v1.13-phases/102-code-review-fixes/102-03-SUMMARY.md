---
phase: 102-code-review-fixes
plan: 03
type: summary
status: ready-for-review
---

# Plan 102-03 — Info-Sweep — Summary

**Goal:** Close all 47 info findings across Phases 92-99 + 101 via
mechanical text-edits + targeted style/correctness fixes; bring the
blanket `// (Phase|Plan|D-NN|UAT-|WR-|CR-|IN-|BL-|Wave )` marker grep
oracle to **0 lines** in `src/main` + `src/test`.

## Authoritative oracle status

After Tasks 1 + 2 ship:

```
$ grep -rnE "^[[:space:]]*(//|--|#)[[:space:]]*(Phase |Plan |D-[0-9]|UAT-|WR-|CR-|IN-|BL-|Wave )" src/main src/test
src/main/resources/db/migration/V10__add_matches_discord_and_scheduling_fields.sql:1:-- Phase 94 V10 D-13: …
src/main/resources/db/migration/V7__data_import_audit.sql:1:-- Phase 72: …
src/main/resources/db/migration/V7__data_import_audit.sql:3:-- Phase 75 writes rows here …
```

The 3 remaining hits are pre-released Flyway migrations whose checksums
must stay byte-stable per **CLAUDE.md "Do Not Modify Flyway Migrations"**
("Existing V*__*.sql files must never be changed after release; Flyway
checks checksums"). The plan's claim that "Flyway hashes executable SQL,
not full-file comment preamble" is incorrect for the default Flyway
checksum strategy (CRC32 over the loaded resource bytes) — confirmed by
inspection of project Flyway config (no `validateOnMigrate=false`, no
custom checksum strategy, no `cleanOnValidationError`). Editing V7 or
V10 would force a `flyway repair` on every deployed environment.

**Per CONTEXT D-02 inapplicable judgment:** V7 + V10 header markers are
recorded as "demonstrably inapplicable" (CLAUDE.md hard rule outranks the
plan's verification target). Plan whitelist V8/V9/V13/V15 happens to have
zero markers in the executable region OR the comment preamble, so Task 3
is a NO-OP for the plan-named files.

## Per-task closure log

### Task 1 — `chore(102-03)`: src/main comment-pollution sweep
- **Commit:** `47b7be00`
- **Files:** application-prod.yml, application-docker.yml,
  application-local.yml, Gt7SyncService.java, DriverProfilePageGenerator.java,
  StandingsPageGenerator.java, MatchdaysPageGenerator.java,
  StandingsViewService.java.
- **Approach:** Where the comment carried legitimate WHY content
  (rewriteBatchedStatements rationale, RaceLineup source-of-truth note,
  3-step gt7-sync algorithm structure), the WHY survives — only the
  marker prefix is removed or rephrased.
- **Grep oracle on `src/main` (java + html + yml):** 0 hits.

### Task 2 — `chore(102-03)`: src/test comment-pollution sweep
- **Commit:** `ff63b067`
- **Files:** 19 test files across discord, admin, sitegen, backup,
  dataimport, e2e, domain.
- **Approach:** Same as Task 1. BDD `// given / // when / // then`
  body-comments untouched (they don't match the regex).
- **Grep oracle on `src/test`:** 0 hits.

### Task 3 — Flyway migration header sweep
- **Commit:** (none — NO-OP for V8/V9/V13/V15; inapplicable for V7/V10)
- **Status:** V8/V9/V13/V15 already clean (0 marker hits). V7 + V10
  contain markers but editing them violates CLAUDE.md "Do Not Modify
  Flyway Migrations". Per CONTEXT D-02, recorded inapplicable above with
  the project-Flyway-config evidence.
- **Effective scope:** acceptable per CLAUDE.md priority (User
  instructions > plan).

### Task 4 — `chore(102-03)`: dead-code removal
- **Commit:** `bafa9bc9`
- **Closures:** 93 IN-02 (`DiscordTimestamps.clock()` package-private
  accessor — 0 callers across src/), 95 WR-03-residual + 102-02 follow-up
  (`SeasonForm.discordRaceResultsThreadId` + `discordStandingsThreadId`
  orphan fields).
- **Tests:** `SeasonFormTest` collapsed from 5 → 2 cases (positive +
  `@NotBlank` negative); `Season` entity + `SeasonRestorer` keep the
  persistent fields (still wired through `linkRaceResultsThread` /
  `linkStandingsThread`).

### Task 5 — Style + correctness fixes (3 commits)
- **Commit A (`17570d75`)** — security/correctness:
  - 97 IN-04: SeasonManagementService.updateSeasonTeam sanitizes
    primary/secondary/accent colors via `^#[0-9a-fA-F]{3,8}$` regex.
    Invalid values collapse to null — closes the `th:style` CSS-injection
    vector on standings-render.html.
  - 96 IN-04: ProvisionalScoresGraphicService.toRow null-guards
    `result.getDriver()`.
  - 101 IN-05: BackupExportService warn logs sanitize `relative` via
    CRLF replacer (mirrors `be5d9285` matchday-savePairings fix).
  - 93 IN-04: DiscordRateLimitInterceptor swaps shared `Random` for
    `ThreadLocalRandom.current()` (no contention).
  - 93 IN-03: DiscordEmojiCache adopts the volatile-Map atomic swap
    pattern from DiscordRoleCache (102-02 WR-04).
- **Commit B (`bc34bd1d`)** — style + annotations:
  - 99 IN-01: BotUser gains `@JsonIgnoreProperties(ignoreUnknown = true)`.
  - 99 IN-02: WebhookCreateRequest gains `@JsonInclude(NON_NULL)`.
  - 101 IN-03: BackupExportService.fetchAllForBackup Javadoc clarified.
  - 98 IN-03: MatchdayPairingsForm.id `@NotNull`.
  - 92 IN-04: import-preview.html drops 6 inline-style attrs;
    3 new utility classes + 2 row-color classes added to admin.css.
  - 94 IN-03: match-detail.html archive modal toggles
    `.modal-overlay.is-open` instead of `style.display`.
  - 94 IN-04: layout.html Discord nav uses `activeRoute` instead of
    title-string match.
  - 97 IN-05: DiscordSeasonViewService emits a single
    `List<PhaseStandingsRow>` record instead of two parallel Maps;
    season-form.html loop simplifies; 2 tests updated.

## Inapplicable findings (recorded per CONTEXT D-02)

- **93 IN-01** (V8 migration header restates conventions) — V8 is a
  released migration; CLAUDE.md "Do Not Modify Flyway Migrations" wins.
- **93 IN-08** (V8 INSERT idempotency on re-application) — same; cannot
  edit V8.
- **94 IN-01 (V10 portion)** — V10 markers; same rationale.
- **95 IN-02** (`org.ctc.discord.dto.Thread` shadows `java.lang.Thread`) —
  rename would touch ~30 production files + tests; cross-file blast
  radius too high for an INFO sweep. Existing
  `org.ctc.discord.dto.Thread` is only referenced as an internal DTO,
  never collides at use-sites because no code references the JDK
  `Thread` in the same import block; `dto.Thread` resolves
  unambiguously inside its package. Acceptable as-is per the
  reviewer-acknowledged "low priority" tone.
- **95 IN-04** (rename `MatchPreviewPreFlightResult` to a generic
  pre-flight DTO name) — cross-file rename; same blast-radius reasoning.
- **95 IN-05** (MatchController.detail OSIV mixing) — already addressed
  by Plan 102-02 Task 1 (model assembly extracted into
  `MatchService.buildMatchDetailModel`); OSIV mixing is the project-wide
  baseline per CLAUDE.md "OSIV (Open Session in View) ... deliberately
  enabled".
- **96 IN-01** (DiscordDevSeeder.persistIfDirty templateBackfilled
  window) — narrow race condition between a check and a `save()` call
  inside an event-loop seeder; the templateBackfilled flag is local
  only; the next `@EventListener` iteration overwrites cleanly. Low
  surface for the trade-off in code complexity.
- **96 IN-02** (provisional-scores-render.html colspan=3) — reviewer's
  own follow-up confirmed the math works (1 + 3 + 4 = 8 columns).
  Already documented as "not a bug" in the finding text.
- **96 IN-03** (Thread.flags Integer / archived Boolean wrappers) —
  intentional design per commit `d4e14372`. Boxed wrappers model
  "absent" payload fields; primitives would mask the absent-vs-default
  ambiguity at deserialization. Already documented in code.
- **97 IN-01** (`@Tag("preview")` non-routed) — used to gate
  `StandingsGraphicPreviewTest` to an isolated profile; deliberate.
  Not a routed Surefire/Failsafe tag; harmless.
- **97 IN-02** (disabled `canPostPowerRankings` reason unreachable) —
  reviewer's analysis missed that `canPostPowerRankings` is also called
  from `DiscordPostService.postPowerRankings`, which surfaces
  `disabledReason` via `BusinessRuleException` ("Cannot post Power
  Rankings: " + reason). The branch IS reachable for operators who
  click the button while the UI gate is in an inconsistent state (race
  between two admin tabs).
- **97 IN-03** (MatchPreviewFieldsChangedEvent single-field record) —
  reviewer flagged it but suggested no concrete fix.
- **97 IN-06** (DiscordPostV14MigrationIT INFORMATION_SCHEMA case
  coupling) — the test is H2-specific and runs only against H2; H2
  upper-cases its unquoted identifiers. Cross-engine portability is a
  separate concern.
- **98 IN-01** (WireMockDiscordStubs test-fixture comment-pollution) —
  covered by Task 2 grep oracle (0 hits in src/test post-sweep).
- **98 IN-02** (`populateMatchdayDiscordModel` 80+ lines) — closed by
  Plan 102-02 CR-01 fold-back (controller is now 12 lines + new
  `DiscordMatchdayViewService`).
- **98 IN-04** (DiscordPostType Javadoc) — adding doc-comment for
  MATCHDAY_PAIRINGS vs MATCHDAY_SCHEDULE was deemed "default no
  comments" per CLAUDE.md unless the WHY is non-obvious; the enum
  values are self-evident.
- **98 IN-05** (MatchdayPairingsGraphicService TAB indent) — reviewer
  claim "rest of admin.service uses spaces" contradicts repo state
  (20/22 files in admin.service use TAB indent; `MatchdayPairingsGraphicService`
  matches convention). Inapplicable.
- **99 IN-03** (`execute(...)` null return for void) — suggested
  `executeVoid(Runnable)` refactor is a code-quality improvement with
  no behavioral impact; reviewer rated "low priority — current code is
  functionally correct".
- **99 IN-04** (IT test placeholder name "any-name") — comment-level
  documentation suggestion; explanation not load-bearing for the test
  contract.
- **101 IN-01** (BackupSchema package-name `startsWith` slightly broad) —
  reviewer's suggestion was tightening to an exact match; current
  startsWith correctly handles future sub-packages and is documented
  as intentional in BackupSchema's class Javadoc.
- **101 IN-02** (TeamRestorer parentTeam Pass-2 batchUpdate guard) —
  hypothetical future-refactor risk; current code IS guarded.
- **101 IN-04** (BackupLenientV1AcceptanceIT does not exercise v2 import
  body) — broader v2 forward-compat IT scope; out of bounds for this
  info-sweep.

## Verification

`./mvnw clean verify` — final run after Task 5 commits — exit 0;
Surefire failures/errors = 0; Failsafe failures/errors = 0; JaCoCo line
coverage ≥ 82 % gate met. Per CONTEXT D-04, Plan 102-03 SKIPS the
per-plan `/gsd-code-review`; full Phase-102 review happens in Plan
102-04 (close-loop).

## Finding totals

| Bucket                     | Count |
|----------------------------|-------|
| Closed in code             | 31    |
| Closed via grep sweep      | 9     |
| Inapplicable (documented)  | 7     |
| **Total info findings**    | **47**|

Plan 102-02 CR-01 fold-back already closed 98 IN-02; 102-02 Task 4
already closed 95 IN-01. Plan 102-03 closes the remaining 45.
