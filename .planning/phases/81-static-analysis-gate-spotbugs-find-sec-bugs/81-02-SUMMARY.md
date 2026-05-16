---
phase: 81
plan: 02
status: complete
created: 2026-05-16
---

# Plan 81-02 — Baseline Triage Summary

## What was built

PLAN 02 walked the 220-finding SpotBugs baseline (produced by PLAN 01's report-only commit) through the D-10 fix-vs-suppress decision tree and brought the report to **zero BugInstance entries**, clearing the path for PLAN 03's gate-flip commit. Six atomic commits land on the phase feature branch: one architectural-filter extension, one D-09 source-comment cross-reference batch, three triage commits per pattern family, and one cascading test cleanup.

The triage strategy was approved by the user before execution started: extend the D-08 layer 2 architectural pattern filter to cover the same Lombok/record false-positive shape across all service packages' inner record/DTO carrier classes — a deliberate, documented deviation from CONTEXT.md D-08 (which originally scoped the filter to `org.ctc.domain.model.*` only). The baseline showed 197 of 220 findings (89.5%) were that same shape on inner record-like classes in `domain.service.*`, `admin.service.*`, `backup.service.*`, `dataimport`, `gt7sync`, plus top-level record packages (`admin.dto`, `backup.dto`, `backup.schema`, `backup.audit`, `backup.event`, `sitegen.model`) and inner records in `admin.controller.*`.

## Key files modified

| Path | Change | Why |
| ---- | ------ | --- |
| `config/spotbugs-exclude.xml` | +47 lines | 8 additional `<Match>` entries — package-regex filters for service-DTO inner records and top-level record packages; cross-reference comments for D-11 entries; 2 stylistic suppressions (`VA_FORMAT_STRING_USES_NEWLINE`, `NP_NULL_ON_SOME_PATH`-on-guarded-paths) with method-specific rationale |
| `src/main/java/org/ctc/admin/service/AbstractMatchdayGraphicService.java` | +UTF_8 | DM_DEFAULT_ENCODING HIGH fix on `loadDefaultTemplate` |
| `src/main/java/org/ctc/admin/service/AbstractPlayoffRoundGraphicService.java` | +UTF_8 | DM_DEFAULT_ENCODING HIGH fix |
| `src/main/java/org/ctc/admin/service/LineupGraphicService.java` | +UTF_8 | DM_DEFAULT_ENCODING HIGH fix |
| `src/main/java/org/ctc/admin/service/MatchResultsGraphicService.java` | +UTF_8 | DM_DEFAULT_ENCODING HIGH fix |
| `src/main/java/org/ctc/admin/service/OverlayGraphicService.java` | +UTF_8 | DM_DEFAULT_ENCODING HIGH fix |
| `src/main/java/org/ctc/admin/service/PowerRankingsGraphicService.java` | +UTF_8 | DM_DEFAULT_ENCODING HIGH fix |
| `src/main/java/org/ctc/admin/service/ResultsGraphicService.java` | +UTF_8 | DM_DEFAULT_ENCODING HIGH fix |
| `src/main/java/org/ctc/admin/service/SettingsGraphicService.java` | +UTF_8 | DM_DEFAULT_ENCODING HIGH fix |
| `src/main/java/org/ctc/admin/service/TeamCardService.java` | +UTF_8 + NP suppress | DM_DEFAULT_ENCODING fix + NP_NULL_ON_SOME_PATH suppress on guaranteed-non-null asset path |
| `src/main/java/org/ctc/admin/service/TemplatePreviewService.java` | +UTF_8 | DM_DEFAULT_ENCODING HIGH fix |
| `src/main/java/org/ctc/backup/service/BackupArchiveService.java` | +D-09 comment + NP suppress | D-09 SSRF/ZIP-Slip cross-reference + NP guaranteed-non-null suppress on `extractUploadsTo` |
| `src/main/java/org/ctc/backup/service/BackupImportService.java` | +NP suppress | NP guaranteed-non-null suppress on `execute` + `reparse` |
| `src/main/java/org/ctc/domain/service/DriverRankingService.java` | -4 lines | DLS_DEAD_LOCAL_STORE fix — removed unused `phaseTeamByTeamId` local |
| `src/main/java/org/ctc/domain/service/FileStorageService.java` | +D-09 comments | SSRF blocklist + path-traversal defense cross-reference comments |
| `src/main/java/org/ctc/domain/service/MatchService.java` | 1-char change | IM_BAD_CHECK_FOR_ODD fix (`== 1` → `!= 0`) |
| `src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java` | -2 / +1 lines | DB_DUPLICATE_BRANCHES fix (collapsed identical branches) |
| `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` | +NP suppress | NP guaranteed-non-null suppress on `copyAssets` + `copyLogoToAssets` |
| `src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java` | +NP suppress | NP guaranteed-non-null suppress on `copyLogoToAssets` |
| `src/main/java/org/ctc/sitegen/TemplateWriter.java` | +NP suppress | NP guaranteed-non-null suppress on `write` |
| `src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java` | -10 lines | Mockito strict-stubbing cleanup — removed 10 now-unused `phaseTeamRepository.findByPhaseId` stubs that became unreachable after the DLS fix |
| `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-VERIFICATION.md` | +Triage Table | STAT-05 Triage Table populated; Final Verification block recorded |

**Total commits on phase branch (Wave 2):** 6 atomic + 1 amend (cleanup-roll-up).

## Commits

- `90b27435` `chore(81-02): extend D-08 layer 2 filter for service-DTO inner records and record packages (STAT-04, STAT-05/triage)`
- `08c8ed08` `chore(81-02): add SpotBugs cross-reference comments for D-11 pre-staged suppressions (D-09)`
- `6d3d9602` `fix(81-02): explicit UTF-8 charset in graphic service template loaders (DM_DEFAULT_ENCODING, STAT-05/triage)`
- `119f35a4` `chore(81-02): suppress NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE on guaranteed-non-null paths (STAT-05/triage)`
- `750cb8ab` `fix(81-02): resolve DLS_DEAD_LOCAL_STORE, IM_BAD_CHECK_FOR_ODD, DB_DUPLICATE_BRANCHES; suppress VA_FORMAT_STRING_USES_NEWLINE (STAT-05/triage)`
- `acd5184d` `test(81-02): drop now-unused phaseTeamRepository stub in DriverRankingServiceTest`

## Verification

| Check | Result |
| ----- | ------ |
| `./mvnw spotbugs:spotbugs -DskipTests` | exit 0, **0 BugInstance** in `target/spotbugsXml.xml` |
| `./mvnw verify` | exit 0, BUILD SUCCESS (≈ 9 min 22 s) |
| Surefire tests | 1381 passed, 0 errors, 4 skipped |
| JaCoCo line coverage | **88.03%** (≥ 82% minimum, ≈ v1.10 baseline 87.80%) |
| Pitfall #7 (`@{argLine}` count in pom.xml) | **3** (unchanged from PLAN 01) |
| `@SuppressWarnings("all")` ban | 0 matches in `src/main`, `src/test` |
| D-09 rationale invariant | every `<Match>` in `config/spotbugs-exclude.xml` has preceding XML comment |

## Requirements addressed

- **STAT-04** — `config/spotbugs-exclude.xml` extended with rationale-documented suppressions (D-09 invariant honored); architectural filter follows D-08 layer 2 shape across an expanded scope.
- **STAT-05** (triage portion) — atomic triage commits land between PLAN 01's plumbing commit and PLAN 03's gate-flip commit per D-12 step 3 choreography; STAT-05 Triage Table populated in `81-VERIFICATION.md`.

## Decisions

- **CONTEXT.md D-08 layer 2 deliberately extended** (user-approved Option 1 before triage started). Original scope: `org.ctc.domain.model.*` only. New scope: same Lombok/record false-positive shape on inner record/DTO classes across all service packages plus top-level record packages. Rationale identical to original D-08 layer 2 — Lombok-generated accessors on immutable data carriers cannot meaningfully expose internal representation. Cited in `81-VERIFICATION.md` "Strategy approved by user before triage start" note for audit trail.
- **NP_NULL_ON_SOME_PATH dispositions chose suppress over fix** (D-10/3). Each call path was inspected and confirmed to operate on paths guaranteed non-null by upstream guards (configured `app.upload-dir`, `ctc.site.output-dir`, uploaded-file presence checks). Refactoring to add belt-and-braces null guards would obscure the actual contract; suppression with method-specific rationale is the more honest approach.
- **VA_FORMAT_STRING_USES_NEWLINE on `TemplatePreviewService.buildPlaceholderCard` chose suppress** (D-10/4 stylistic). SVG content emitted inside a Base64 data URI requires literal `\n` (XML/SVG standard); `%n` would emit `\r\n` on Windows JVMs and corrupt the Base64 payload. Suppression with rationale.

## Deviations

- **CONTEXT.md D-08 layer 2 scope expansion** — see Decisions above. Documented in `81-VERIFICATION.md` triage table notes and in this SUMMARY. The original D-08 was deliberately narrow; runtime baseline revealed the same false-positive shape across the broader codebase. User explicitly approved expansion ("Option 1") before triage execution started.
- **Cascading test cleanup** — the DLS fix on `DriverRankingService.calculateRankingForPhase` triggered Mockito strict-stubbing failures on 7 tests that had their own `phaseTeamRepository.findByPhaseId` stubs (now unreachable since the production code no longer calls that repository method on this path). The cleanup is mechanical and committed as `test(81-02): drop now-unused phaseTeamRepository stub in DriverRankingServiceTest`.

## What's next

PLAN 03 — gate-flip commit (`<goal>spotbugs</goal>` → `<goal>check</goal>`), STAT-06 deliberate-violation evidence on a throwaway branch, and CLAUDE.md `## Conventions` paragraph documenting the gate per D-14. The final `./mvnw verify -Pe2e` E2E run (per CLAUDE.md `feedback_e2e_verification`) lands inside PLAN 03 to keep test-call optimization honest.

## Self-Check: PASSED

- All success criteria met (filter extension, all HIGH fixes, all Medium dispositions documented, zero remaining BugInstance, coverage ≥ 82%, argLine integrity, no @SuppressWarnings("all"), no STATE/ROADMAP edits in worktree)
- No drift from CLAUDE.md conventions (Conventional Commits, no local git tags, English-only)
- D-08 deviation explicitly documented with user-approval audit trail
