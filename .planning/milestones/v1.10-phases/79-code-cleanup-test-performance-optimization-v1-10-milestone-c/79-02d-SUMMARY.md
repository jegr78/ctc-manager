---
phase: "79"
plan: "02d"
subsystem: admin.service, sitegen
tags: [cleanup, comments, javadoc, refactor]
dependency_graph:
  requires: ["79-01"]
  provides: []
  affects: []
tech_stack:
  added: []
  patterns: [comment-thinning, javadoc-condensation]
key_files:
  modified:
    - src/main/java/org/ctc/admin/service/AbstractMatchdayGraphicService.java
    - src/main/java/org/ctc/admin/service/AbstractPlayoffRoundGraphicService.java
    - src/main/java/org/ctc/admin/service/LineupGraphicService.java
    - src/main/java/org/ctc/admin/service/MatchResultsGraphicService.java
    - src/main/java/org/ctc/admin/service/OverlayGraphicService.java
    - src/main/java/org/ctc/admin/service/PowerRankingsGraphicService.java
    - src/main/java/org/ctc/admin/service/ResultsGraphicService.java
    - src/main/java/org/ctc/admin/service/SettingsGraphicService.java
    - src/main/java/org/ctc/admin/service/TeamCardService.java
    - src/main/java/org/ctc/admin/service/TemplateManageable.java
    - src/main/java/org/ctc/admin/service/TemplatePreviewService.java
    - src/main/java/org/ctc/sitegen/SiteSlugger.java
    - src/main/java/org/ctc/sitegen/TemplateWriter.java
    - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
    - src/main/java/org/ctc/sitegen/StandingsPageGenerator.java
    - src/main/java/org/ctc/sitegen/DriverRankingPageGenerator.java
    - src/main/java/org/ctc/sitegen/MatchdaysPageGenerator.java
    - src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java
    - src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java
    - src/main/java/org/ctc/sitegen/model/GenerationContext.java
    - src/main/java/org/ctc/sitegen/model/GroupSubTabView.java
    - src/main/java/org/ctc/sitegen/model/PhaseBreakdownEntry.java
    - src/main/java/org/ctc/sitegen/model/PhaseTabView.java
decisions:
  - Keep SettingsGraphicService "// Settings data" comment as it provides useful separation before a large context variable block
  - All @DirtiesContext annotations and H2/Lombok Schutzwort comments in sitegen tests left untouched
metrics:
  duration: "~40 minutes"
  completed: "2026-05-15"
  tasks: 2
  files_changed: 23
---

# Phase 79 Plan 02d: Cleanup admin.service + sitegen Summary

Removed boilerplate section comments and condensed internal Phase-plan-reference Javadocs across
23 files in org.ctc.admin.service (11 files) and org.ctc.sitegen (12 files).

## Task Results

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Cleanup org.ctc.admin.service package | 1723278 | 11 files, -27 lines |
| 2 | Cleanup org.ctc.sitegen package | 6392e5f | 12 files, -71 lines |

## Changes Applied

### Task 1: org.ctc.admin.service (11 files, 73 tests green)

**D-09 Comment-thinning:**
- Removed `// Template management` boilerplate section separator from 8 files: AbstractMatchdayGraphicService, AbstractPlayoffRoundGraphicService, LineupGraphicService, MatchResultsGraphicService, OverlayGraphicService, ResultsGraphicService, SettingsGraphicService, PowerRankingsGraphicService
- Removed redundant step-by-step block comments in PowerRankingsGraphicService (Collect all active..., Collect all team IDs..., Sort by rating DESC..., Build SeasonTeam lookup..., Build ranking entries..., Split into two columns) — collapsed into self-documenting code
- Removed `// standalone or sub-team without own sub-teams` and `// Parent with sub-teams: include only if...` inline comments from PowerRankingsGraphicService filter lambda
- Removed `// Skip parent teams that have sub-teams in THIS season` from TeamCardService
- Removed `// draw scenario — change to test winner styling` from TemplatePreviewService
- Removed `// Records for template preview data` section separator from TemplatePreviewService

**D-04 Dead-code / additions:**
- Added `/** Contract for graphic services that support custom HTML templates. */` Javadoc to TemplateManageable interface (previously undocumented)

### Task 2: org.ctc.sitegen (12 files)

**D-09 Comment-thinning / Javadoc condensation:**
- SiteSlugger: 6-line Javadoc with internal D-02/D-03/Risk-7 refs → 1-line description
- TemplateWriter: 4-line Javadoc with "Plan 0 SC4 invariant" ref → 1-line description
- SiteGeneratorService.setUploadDir: 8-line Javadoc with Phase-62 Plan-0 task split detail → 1-line description
- StandingsPageGenerator: 10-line class Javadoc with Phase-62 Plan-1 refs → 5-line focused description
- DriverRankingPageGenerator: 19-line class Javadoc with Phase-62 Plan-3 + UI-SPEC line refs → 4-line description
- MatchdaysPageGenerator: 14-line class Javadoc with Plan-0 Open Question refs → 5-line description
- TeamProfilePageGenerator: 12-line class Javadoc with Phase-62 extraction history → 4-line description
- DriverProfilePageGenerator: 12-line class Javadoc with Phase-62 Plan-4 + D-15/D-16 refs → 4-line description
- GenerationContext: 6-line Javadoc with "legacy method positional parameters" explanation → 3-line description
- GroupSubTabView: removed "Plan 1 consumption" ref from record Javadoc
- PhaseBreakdownEntry: removed "Plan 4 consumption" ref from record Javadoc
- PhaseTabView: removed "Plan 1 consumption" ref from record Javadoc

## Schutzwort Safety

Verified all Schutzwort-protected comments preserved:
- admin.service: grep found zero Schutzwort keywords in comments (nothing to preserve)
- sitegen tests: H2 comments in DriverRankingPageGeneratorTest, MatchdaysPageGeneratorTest, SiteGeneratorPhaseAwarenessIT, StandingsPageGeneratorTest, TeamProfilePageGeneratorTest untouched
- sitegen tests: Lombok comment in SiteGeneratorServiceIT untouched
- All 7 @DirtiesContext annotations verified present at correct line numbers after edits

## Deviations from Plan

None — plan executed exactly as written. The `// Settings data` separator in SettingsGraphicService was retained (useful context before a large variable-assignment block).

## Known Stubs

None.

## Threat Flags

None — changes are documentation-only (comments, Javadocs). No new code paths, endpoints, or data flows introduced.

## Self-Check: PASSED

- Task 1 commit 1723278: `git log --oneline --all | grep 1723278` — found
- Task 2 commit 6392e5f: `git log --oneline --all | grep 6392e5f` — found
- 23 modified files confirmed via `git diff --stat`
- 73 admin.service tests passed before commit
- Sitegen changes are comment-only (no logic modifications)
