---
phase: 81-static-analysis-gate-spotbugs-find-sec-bugs
reviewed: 2026-05-16T17:00:00Z
depth: standard
files_reviewed: 24
files_reviewed_list:
  - CLAUDE.md
  - config/spotbugs-exclude.xml
  - lombok.config
  - pom.xml
  - src/main/java/org/ctc/admin/service/AbstractMatchdayGraphicService.java
  - src/main/java/org/ctc/admin/service/AbstractPlayoffRoundGraphicService.java
  - src/main/java/org/ctc/admin/service/LineupGraphicService.java
  - src/main/java/org/ctc/admin/service/MatchResultsGraphicService.java
  - src/main/java/org/ctc/admin/service/OverlayGraphicService.java
  - src/main/java/org/ctc/admin/service/PowerRankingsGraphicService.java
  - src/main/java/org/ctc/admin/service/ResultsGraphicService.java
  - src/main/java/org/ctc/admin/service/SettingsGraphicService.java
  - src/main/java/org/ctc/admin/service/TeamCardService.java
  - src/main/java/org/ctc/admin/service/TemplatePreviewService.java
  - src/main/java/org/ctc/backup/service/BackupArchiveService.java
  - src/main/java/org/ctc/backup/service/BackupImportService.java
  - src/main/java/org/ctc/domain/service/DriverRankingService.java
  - src/main/java/org/ctc/domain/service/FileStorageService.java
  - src/main/java/org/ctc/domain/service/MatchService.java
  - src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java
  - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
  - src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java
  - src/main/java/org/ctc/sitegen/TemplateWriter.java
  - src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java
findings:
  critical: 0
  warning: 3
  info: 2
  total: 5
status: issues_found
---

# Phase 81: Code Review Report

**Reviewed:** 2026-05-16T17:00:00Z
**Depth:** standard
**Files Reviewed:** 24
**Status:** issues_found

## Summary

Phase 81 wires the SpotBugs + find-sec-bugs static analysis gate and resolves every finding
that the baseline scan exposed. The build configuration changes (pom.xml, lombok.config,
config/spotbugs-exclude.xml) are structurally correct: Pitfall #7 is clean (no `<argLine>` in
the SpotBugs plugin block), `@{argLine}` appears exactly three times, and lombok.config carries
both required SpotBugs lines. All DM_DEFAULT_ENCODING fixes use `StandardCharsets.UTF_8`
(constant, not string literal). The IM_BAD_CHECK_FOR_ODD fix in MatchService uses the correct
`!= 0` form. The DLS_DEAD_LOCAL_STORE removal in DriverRankingService is behaviorally inert
because `resolveTeamFromLineup` was already the active code path; the follow-on test cleanup
correctly removes now-redundant stubs without over-pruning.

Three warnings were found — one test that does not actually exercise the code path its name
implies (regularPhaseTeamIds priority), one set of stale line-number references in filter
comments, and one non-volatile mutable singleton field — plus two info-level items.

## Warnings

### WR-01: `givenMultiPhaseSeason_whenAggregateAcrossPhases` never exercises the regular-phase-priority branch

**File:** `src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java:380-417`

**Issue:** The test stubs `seasonPhaseService.findByType(season.getId(), PhaseType.REGULAR)`
to return `Optional.of(regular)`, which causes `aggregateAcrossPhases` to call
`phaseTeamRepository.findByPhaseId(regular.getId())`. That call is not stubbed, so Mockito
returns an empty `List`. As a result `regularPhaseTeamIds` is always an empty `Set` inside
`attributeTeamFromRegularOrLineup`, meaning the `filter(rl -> regularPhaseTeamIds.contains(...))` 
branch at `DriverRankingService.java:181` never matches. The code falls straight through to the
`orElseGet(() -> lineups.get(0).getTeam())` fallback on every run of this test.

The test comment says "REGULAR team (D-08 via RaceLineup)" and `assertThat(team).isEqualTo(tnr)`
does pass — but it passes via the _fallback_ path, not via the regular-phase priority path the
test is supposed to validate. A future regression that silently breaks the priority filter
(e.g., wrong team-ID comparison) would not be caught by this test.

**Fix:** Add a stub for `phaseTeamRepository.findByPhaseId(regular.getId())` that returns a
`PhaseTeam` whose team ID matches `tnr.getId()`, so the `filter(...)` actually selects the
regular-phase team. Then assert that the result changes when a _different_ team is used in the
playoff lineup, proving the priority selection is exercised.

```java
// In givenMultiPhaseSeason_whenAggregateAcrossPhases test, add:
var phaseTeamEntry = new PhaseTeam(regular, tnr);
when(phaseTeamRepository.findByPhaseId(regular.getId()))
        .thenReturn(List.of(phaseTeamEntry));
```

---

### WR-02: Stale line-number references in spotbugs-exclude.xml comments

**File:** `config/spotbugs-exclude.xml:138-144`

**Issue:** Two filter rationale comments contain line references that do not match the actual
source code, violating the D-09 "code-cross-reference" invariant stated in CLAUDE.md.

- `TeamCardService.generateCard` comment says `"See TeamCardService.java:101-102"`. The null
  dereference (`outputFile.getParent()`) is at line 106; lines 101-102 are `Path outputFile =`
  and a blank line.

- `BackupArchiveService.extractUploadsTo` comment says `"See BackupArchiveService.java:500-502"`.
  The guard is at line 503 and the dereference is at line 504; lines 500-501 are the NP comment
  itself.

These are documentation-only inaccuracies — the filtering logic is method-scoped and works
correctly regardless — but they directly violate the invariant that every `<Match>` entry must
have a "code-cross-reference to where the intentional pattern lives".

**Fix:** Update the cross-reference comments to reflect the actual line numbers:

```xml
<!-- TeamCardService.generateCard: outputFile.getParent() at line 106.
     ...
     See TeamCardService.java:102-106. -->

<!-- BackupArchiveService.extractUploadsTo: target.getParent() at line 504
     is called only inside the if (target.getParent() != null) guard at line 503 —
     ...
     See BackupArchiveService.java:500-504. -->
```

---

### WR-03: Non-volatile mutable fields in a Spring singleton (`TemplatePreviewService`)

**File:** `src/main/java/org/ctc/admin/service/TemplatePreviewService.java:36-39`

**Issue:** `TemplatePreviewService` is a `@Service`-annotated Spring singleton (one shared
instance across all HTTP threads). It uses a lazy-initialization pattern on four instance
fields (`cachedFontBase64`, `cachedLogoBase64`, `cachedCommentatorBase64`,
`cachedVsBadgeBase64`) without `volatile` or `synchronized`:

```java
private String cachedFontBase64;    // line 36 — not volatile

private String getFontBase64() {
    if (cachedFontBase64 == null) {           // line 269
        cachedFontBase64 = encodeClasspathResource(...);  // line 270
    }
    return cachedFontBase64;
}
```

Under the Java Memory Model, a write to a non-volatile field by one thread is not guaranteed
to be visible to reads by other threads. Two concurrent requests can both observe
`cachedFontBase64 == null` and both execute the encoding, which is wasteful but not harmful
(idempotent). The more subtle risk is that a partially-constructed `String` reference could
theoretically be observed by a second thread on architectures that permit reordering — though
in practice the JVM/CPU usually prevents this for simple String assignments.

The values are large immutable classpath-resource blobs set once and read many times, which
is exactly the pattern `volatile` is designed for.

**Fix:** Declare the four fields `volatile`:

```java
private volatile String cachedFontBase64;
private volatile String cachedLogoBase64;
private volatile String cachedCommentatorBase64;
private volatile String cachedVsBadgeBase64;
```

## Info

### IN-01: `containsSpringElTypeAccess` skips only ASCII space, not all whitespace

**File:** `src/main/java/org/ctc/admin/service/TemplatePreviewService.java:362`

**Issue:** The T()-expression detector skips whitespace between `T` and `(` using a loop that
only matches the space character `' '` (0x20). A tab character (`\t`) between `T` and `(` —
as in `T\t(java.lang.Runtime)` — would not be skipped, so the `if (expr.charAt(next) == '(')` 
check would fail and the expression would not be detected as a SpEL type access.

However, there is a functional safety net: `"Runtime"` is in `BLOCKED_TOKENS`, and any SpEL
type access using `java.lang.Runtime` would be caught by the token check at line 335 before
`containsSpringElTypeAccess` is even reached. The gap only matters for class names not covered
by any blocked token.

**Fix:** Replace `' '` with `Character.isWhitespace(...)` for robustness:

```java
while (next < expr.length() && Character.isWhitespace(expr.charAt(next))) {
    next++;
}
```

---

### IN-02: `ClassPathResource` import via fully-qualified name in two graphic services

**File:** `src/main/java/org/ctc/admin/service/MatchResultsGraphicService.java:134`
**File:** `src/main/java/org/ctc/admin/service/ResultsGraphicService.java:110`

**Issue:** Both `loadDefaultTemplate()` methods instantiate `ClassPathResource` using its
fully-qualified class name (`new org.springframework.core.io.ClassPathResource(...)`) instead of
using the `import` that is already present at line 1 of their parent class hierarchy. All other
graphic services in the same package use the simple name. This is an inconsistency that makes
the code look like the import was accidentally omitted.

```java
// MatchResultsGraphicService.java line 134 — uses FQCN
var resource = new org.springframework.core.io.ClassPathResource(DEFAULT_TEMPLATE);

// Other graphic services — uses simple name (correct)
var resource = new ClassPathResource(DEFAULT_TEMPLATE);
```

**Fix:** Add `import org.springframework.core.io.ClassPathResource;` at the top of both files
and replace the fully-qualified references with the simple class name.

---

_Reviewed: 2026-05-16T17:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
