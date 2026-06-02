---
phase: 116-german-comment-sweep
reviewed: 2026-06-02T14:00:00Z
depth: standard
files_reviewed: 23
files_reviewed_list:
  - src/main/java/org/ctc/backup/restore/entity/RaceResultRestorer.java
  - src/main/resources/application.yml
  - src/main/resources/logback-spring.xml
  - src/main/resources/static/admin/css/admin.css
  - src/main/resources/templates/admin/lineup-render.html
  - src/main/resources/templates/admin/match-results-render.html
  - src/main/resources/templates/admin/matchday-detail.html
  - src/main/resources/templates/admin/overlay-render.html
  - src/main/resources/templates/admin/provisional-scores-render.html
  - src/main/resources/templates/admin/results-render.html
  - src/main/resources/templates/admin/season-detail.html
  - src/main/resources/templates/admin/settings-render.html
  - src/main/resources/templates/admin/team-card-render.html
  - src/test/java/org/ctc/backup/restore/entity/RaceResultRestorerTest.java
  - src/test/java/org/ctc/backup/service/BackupExportServiceIT.java
  - src/test/java/org/ctc/backup/service/BackupImportExecuteIT.java
  - src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java
  - src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java
  - src/test/java/org/ctc/backup/service/FailAtTableInjector.java
  - src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java
  - src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java
  - src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java
  - src/test/java/org/ctc/e2e/LegacyMigratedSeasonE2ETest.java
findings:
  critical: 0
  warning: 1
  info: 1
  total: 2
status: issues_found
---

# Phase 116: Code Review Report

**Reviewed:** 2026-06-02
**Depth:** standard
**Files Reviewed:** 23
**Status:** issues_found

## Summary

Phase 116 was a German comment sweep across 23 files (13 main-source, 10 test). The core
invariant — comment text only, zero behavioral change — holds across all 23 files. Every diff
line is a comment line (`//`, `/* */`, Javadoc `*`, `<!-- -->`, YAML `#`). No CSS rule,
Thymeleaf expression, YAML key/value, Java code, string literal, assertion, or locator was
changed. The `PlayoffRestorerTest` "Saison 2023 Playoffs" string-literal pair is confirmed
absent from the diff. The `seedSaison2023()` code identifier is preserved in
`BackupImportMariaDbSmokeIT`. Zero German remains in any comment across the 23 files;
documented exclusions (track names, string literals, functional umlaut-regex in `SiteSlugger`)
are all correct.

The two target admin.css banned markers (`Phase 60` + `Phase 91 / UX-01`) were removed. The
`DriverSheetImportServiceIT` `Phase 60` + German inline comment was removed (116-03 straggler
fix). The `logback-spring.xml` eight XML comments (missed by the inventory scan) were
translated correctly. Translation accuracy is high throughout; redundant decorative labels were
removed rather than translated, per the reduction policy.

One **Warning** was identified: 9 of the 23 touched test files retain pre-existing `Phase N` /
`Plan-N` class-level Javadoc markers that should have been removed when the files were touched,
per CLAUDE.md "No Comment Pollution" — "When refactoring, remove pollution from touched files."
The phase selectively applied this rule (correctly removing the two explicit double-violations in
admin.css and the straggler in DriverSheetImportServiceIT) but did not extend it to the
structurally identical class-header markers in the backup and E2E test files.

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: Pre-existing `Phase N` / `Plan-N` class-level Javadoc markers left in 9 touched files

**Files:**
- `src/test/java/org/ctc/backup/restore/entity/RaceResultRestorerTest.java:20` — `Phase 75 / Plan 04`
- `src/test/java/org/ctc/backup/service/BackupExportServiceIT.java:21` — `Phase 73-03`
- `src/test/java/org/ctc/backup/service/BackupImportExecuteIT.java:41` — `Phase 75 / Plan 06`
- `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java:41,47,121` — `Phase 75 / Plan 10` (multiple)
- `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java:47` — `Phase 75 / Plan 09`
- `src/test/java/org/ctc/backup/service/FailAtTableInjector.java:10` — `Phase 75 / Plan 09`
- `src/test/java/org/ctc/dataimport/DriverSheetImportServiceIT.java:35,228` — `Phase 59 Plan 03`, `Phase 70 D-09`
- `src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java:59` — `Phase 60 UI-01`
- `src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java:37` — `as of Phase 60`

**Issue:** CLAUDE.md "No Comment Pollution" states hard-banned markers include Phase/Plan/Task/UAT/Wave
references, and adds: "When refactoring, remove pollution from touched files. Do not preserve it
'to stay consistent with the file's existing style' — that calcifies the anti-pattern." All nine
files above were modified during Phase 116 (commits `a20dd918`, `5cb852a2`) and therefore fell
within the "touched files" cleanup obligation. The phase correctly removed the `Phase 60` marker
from `DriverSheetImportServiceIT:249` and the `Phase 60`/`Phase 91` markers from `admin.css`
(both were explicitly listed as double-violations in the CONTEXT), but the structurally identical
class-level Javadoc headers in the backup test suite and E2E tests were left intact. The
inconsistency means the "remove on touch" rule was applied to ad-hoc `//` markers but not to
Javadoc class headers — a distinction the rule does not make.

Note: `QUAL-02`, `ROADMAP-SC3`, `D-15`, and `RESEARCH Assumption A1` are requirement/decision
codes intentionally preserved per the CONTEXT decision ("not on a German line") — those are out of
scope and correctly untouched.

**Fix:** Remove the `Phase N / Plan N —` prefixes from the opening line of each class-level Javadoc
in the nine files above, retaining the descriptive remainder. For example:

```java
// Before (RaceResultRestorerTest.java:20):
 * Phase 75 / Plan 04 — Unit test for {@link RaceResultRestorer}.

// After:
 * Unit test for {@link RaceResultRestorer}.
```

```java
// Before (BackupImportRollbackIT.java:47):
 * Phase 75 / Plan 09 — primary regression net for success-criterion-3 of the phase:

// After:
 * Primary regression net for backup restore rollback:
```

Inline body references (`// Append the Phase 75 RESEARCH §10 batch-rewrite flag`) that cross-reference
specific investigation rationale may be retained if they still communicate an actionable WHY
(the RESEARCH §10 flag comment explains why `rewriteBatchedStatements=true` is appended); pure
`Phase N` class-header labels convey only provenance and should be dropped.

## Info

### IN-01: OSIV rationale comment in `application.yml` collapsed to a single ~195-character line

**File:** `src/main/resources/application.yml:28`

**Issue:** The original 3-line German comment was translated into a single English comment line
of approximately 195 characters. The project has no enforced line-length limit (Checkstyle only
checks `UnusedImports` + `RedundantImport`) so this is not a build issue. However, the comment
now reads as a single run-on sentence with an em-dash separation of two thoughts, making it
slightly harder to scan at a glance than the original three-sentence structure.

```yaml
# Current (one line, ~195 chars):
    # OSIV: the Hibernate session stays open until the end of the HTTP request so Thymeleaf can render lazy-loaded fields — a deliberate choice for this server-rendered admin app (no REST API).
```

**Fix (optional):** Wrap at the em-dash to restore the "what / why" split without adding a third line:
```yaml
    # OSIV: the Hibernate session stays open until the end of the HTTP request so Thymeleaf
    # can render lazy-loaded fields — deliberate choice for this server-rendered admin app (no REST API).
```
This is a purely cosmetic suggestion; the current comment is accurate and unambiguous.

---

_Reviewed: 2026-06-02_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
