---
phase: 79
plan: 02d
type: execute
wave: 2
depends_on: [79-01]
files_modified:
  - src/main/java/org/ctc/admin/service/**
  - src/test/java/org/ctc/admin/service/**
  - src/main/java/org/ctc/sitegen/**
  - src/test/java/org/ctc/sitegen/**
autonomous: true
requirements: [D-01, D-02, D-03, D-04, D-09, D-10, D-11, D-12, D-13]

must_haves:
  truths:
    - "Playwright AbstractGraphicService hierarchy is preserved (15 graphic services depend on it)"
    - "Sitegen 7 `@DirtiesContext`-bearing test classes are unchanged (Plan 01 verdict KEEP-mandatory)"
    - "`SiteProperties.outputDir` setter is unchanged (sitegen tests depend on its existence — D-04 has a test-caller, do not delete)"
    - "`./mvnw test` BUILD SUCCESS after each per-package commit"
  artifacts:
    - path: "git log on branch gsd/v1.10-platform-and-backup"
      provides: "2 atomic commits, one per cleaned package"
      pattern: "refactor\\(79\\): cleanup org\\.ctc\\.(admin\\.service|sitegen) package"
  key_links:
    - from: "Graphic services + sitegen pages"
      to: "Existing E2E tests"
      via: "behavior-preserving cleanup"
      pattern: "AbstractGraphicService|SiteGeneratorService"
---

<objective>
Wave 2 cleanup sweep — two mid-rank packages from RESEARCH ordering:

1. `org.ctc.admin.service` — 19 files (import count 14). Playwright-based graphic generation services (3 abstract base + 12 concrete). JaCoCo excludes most of these (`AbstractGraphicService`, `TeamCardService`, etc. per pom.xml lines 314-326) — cleanup does NOT impact coverage floor.
2. `org.ctc.sitegen` — ~16 files (import count 13). Generators that mutate `SiteProperties.outputDir` per the 7 mandatory `@DirtiesContext` annotations from Plan 01.

Output: 1-2 atomic per-package commits. Cleanup classes per D-02.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-CONTEXT.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-RESEARCH.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-02a-cleanup-leaf-admin-controller-backup-leaves-PLAN.md
@CLAUDE.md
@.planning/codebase/STRUCTURE.md

<interfaces>
**Sitegen `@DirtiesContext` invariant (from Plan 01 verdict — 7 mandatory KEEPs):**

The following 7 test classes have `@DirtiesContext` because they call `siteProperties.setOutputDir(tempDir.toString())` in `@BeforeAll` / `@BeforeEach`. The annotation MUST stay. The setter on `SiteProperties` MUST stay. The mutation pattern is a known sitegen-test convention — do NOT replace with `@TestPropertySource` (out of Phase 79 scope per CONTEXT.md D-07/D-15 rationale).

- `DriverProfilePageGeneratorTest`
- `DriverRankingPageGeneratorTest`
- `MatchdaysPageGeneratorTest`
- `SiteGeneratorE2ETest`
- `SiteGeneratorPhaseAwarenessIT`
- `StandingsPageGeneratorTest`
- `TeamProfilePageGeneratorTest`

**JaCoCo-excluded classes in admin.service (pom.xml lines 314-326):**
- `AbstractGraphicService`, `TeamCardService`, `LineupGraphicService`, `ResultsGraphicService`, `SettingsGraphicService`, `OverlayGraphicService`, `MatchResultsGraphicService`, `PowerRankingsGraphicService`, `PlayoffRoundOverviewGraphicService`, `PlayoffRoundScheduleGraphicService`, `PlayoffRoundResultsGraphicService`

These are excluded from coverage because they use Playwright (compile-scope dependency, runtime-only). Cleanup edits on these classes do NOT affect the 0.82 JaCoCo floor.

**Schutzwort hotspots in these packages:**
- `org.ctc.admin.service`: Playwright graphic services often mention `Lombok`, `pitfall` (CSS pixel positioning), `thread-safe` (Playwright is single-threaded)
- `org.ctc.sitegen`: `OSIV`, `auditing`, `MariaDB` (for SHA-256 byte-equality round-trips)

**Cleanup procedure** inherits from Plan 02a `<interfaces>` block.
</interfaces>
</context>

<critical_constraints>
- You are working on branch `gsd/v1.10-platform-and-backup`. Do NOT run `git stash`, `git checkout`, `git reset`, or switch branches.
- Implement ONLY the tasks listed below. If you find that other files need changes, report `NEEDS_CONTEXT` instead of expanding scope.
- After each per-package commit, run `./mvnw test` and verify it passes BEFORE moving to the next package.
- Schutzwortliste (D-13): Comments containing any of these words must NOT be deleted: MariaDB, H2, JEP, CVE, race, thread-safe, TODO, HACK, WORKAROUND, FIXME, deadlock, OSIV, Lombok, Unsafe, transitiv, transitive, pitfall, auto-commit, auditing, AuditingEntityListener.
- Dead-code removal rule (D-04): The 7 sitegen `@DirtiesContext` annotations + their associated `siteProperties.setOutputDir()` calls ARE referenced from test code via reflection-equivalent Spring lifecycle. Do NOT delete the setter even if non-test source has zero callers. Reflection-invoked methods survive automatically.
- Each package gets EXACTLY ONE commit. Empty-edit packages SKIP the commit (no empty commits).
</critical_constraints>

<test_impact>
- Packages touched: `org.ctc.admin.service` (19 files), `org.ctc.sitegen` (~16 files including model subpackage)
- Test classes likely touched: ~30 unit + IT tests across both packages, plus all 7 sitegen `@DirtiesContext` tests
- Mockito stub updates: NONE — graphic services are tested with Playwright headless real-rendering (no mocks)
- Bridge-only test deletions: NONE
- Estimated test edit count: 5-15 (mostly comment-thinning; BDD markers preserved)
- JaCoCo impact: admin.service classes are mostly EXCLUDED from coverage (pom.xml lines 314-326); sitegen classes are INCLUDED — coverage delta must stay ≥ 0.82 (D-18). If a sitegen helper method is extracted, the JaCoCo report still covers the original line count under the same method-boundary.
- `@DirtiesContext` invariant: 7 sitegen classes have it; this plan MUST NOT touch the annotation lines.
</test_impact>

<tasks>

<task type="auto">
  <name>Task 1: Cleanup org.ctc.admin.service package (1 commit)</name>
  <files>src/main/java/org/ctc/admin/service/**, src/test/java/org/ctc/admin/service/**</files>
  <read_first>
    - All `.java` files in `src/main/java/org/ctc/admin/service/` (19 files including the 3 `Abstract*GraphicService` base classes)
    - Mirror test files in `src/test/java/org/ctc/admin/service/`
    - `pom.xml` lines 311-328 (JaCoCo excludes for graphic services — confirms coverage-floor-irrelevance for excluded files)
    - Plan 02a `<interfaces>` for canonical 4-pass procedure
  </read_first>
  <action>
Apply the 4-pass cleanup to all `.java` files under `src/main/java/org/ctc/admin/service/` + their test mirrors. SPECIFIC INVARIANTS:

- `AbstractGraphicService` `public abstract` method signatures MUST stay (15+ concrete subclasses depend on them).
- `@Component`/`@Service` annotations on graphic services stay (Spring-injected).
- Playwright pixel-positioning comments (`feedback_graphic_pixel_positioning` memory) — many of these contain `pitfall` Schutzwort and SPECIFIC pixel-value rationale. KEEP verbatim.
- `TemplatePreviewService` is JaCoCo-INCLUDED — extra caution on the dead-code pass here.
- Big methods are candidates for extract-method per CD-02; `TeamCardService` and `AbstractMatchdayGraphicService` are likely targets. Apply &gt;30 LOC threshold only when readability clearly improves.

Stage `git add src/main/java/org/ctc/admin/service/ src/test/java/org/ctc/admin/service/`. Confirm with `git status`. Commit `refactor(79): cleanup org.ctc.admin.service package` with the 4-counter body. Run `./mvnw test -Dspring.profiles.active=dev`. RED → revert + `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS" &amp;&amp; git log -1 --pretty=%B | grep -q "refactor(79): cleanup org\.ctc\.admin\.service package"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Commit `refactor(79): cleanup org.ctc.admin.service package` lands
    - No public abstract method signature changed on the 3 `Abstract*GraphicService` base classes
    - No Schutzwortliste keyword deleted (especially `pitfall`, `Lombok`, `thread-safe`)
    - `@Component`/`@Service` annotations preserved across the 15+ graphic services
  </acceptance_criteria>
  <done>Single commit lands; `./mvnw test` GREEN; abstract hierarchy + Schutzwort invariants hold.</done>
</task>

<task type="auto">
  <name>Task 2: Cleanup org.ctc.sitegen package (1 commit)</name>
  <files>src/main/java/org/ctc/sitegen/**, src/test/java/org/ctc/sitegen/**</files>
  <read_first>
    - All `.java` files in `src/main/java/org/ctc/sitegen/` (~16 files including `model/` subpackage)
    - Mirror test files (especially the 7 `@DirtiesContext`-bearing tests)
    - Plan 01 `<interfaces>` 10-row `@DirtiesContext` audit table (the 7 sitegen rows)
    - Plan 02a `<interfaces>` for canonical 4-pass procedure
  </read_first>
  <action>
Apply the 4-pass cleanup to all `.java` files under `src/main/java/org/ctc/sitegen/` (including `model/` subpackage) + their test mirrors. SPECIFIC INVARIANTS:

- The 7 `@DirtiesContext` annotations in the test mirror MUST NOT be touched. Verify with `grep -n "@DirtiesContext" src/test/java/org/ctc/sitegen/*.java` BEFORE editing tests — note the line numbers. After editing, re-grep and confirm identical line numbers (or all 7 annotations still present at their declarations).
- `SiteProperties.outputDir` setter MUST stay (called by the 7 tests). Do NOT delete even if grep shows zero non-test callers — the test-caller is enough.
- `SiteGeneratorService` is a Phase 62 helper-extracted orchestrator (568 LOC); per RESEARCH §"Per-Package Cleanup Ordering" comment, helper extractions are out-of-scope. Extract-method here applies only to ANY methods &gt;50 LOC NOT already extracted by Phase 62. CD-02 discretion at &gt;30 LOC applies only when readability clearly improves.
- The 4 view records in `org.ctc.sitegen.model` (`PhaseTabView`, `GroupSubTabView`, `PhaseBreakdownEntry`, `GenerationContext`) — records are immutable; cleanup limited to Javadoc condensation and any unused inner helpers.

Stage `git add src/main/java/org/ctc/sitegen/ src/test/java/org/ctc/sitegen/`. Confirm with `git status`. Commit `refactor(79): cleanup org.ctc.sitegen package` with the 4-counter body. Run `./mvnw test -Dspring.profiles.active=dev`. RED → revert + `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS" &amp;&amp; git log -1 --pretty=%B | grep -q "refactor(79): cleanup org\.ctc\.sitegen package" &amp;&amp; [ "$(grep -l "@DirtiesContext" src/test/java/org/ctc/sitegen/*.java 2>/dev/null | wc -l)" -ge 7 ]</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Commit `refactor(79): cleanup org.ctc.sitegen package` lands
    - All 7 `@DirtiesContext` annotations in `src/test/java/org/ctc/sitegen/*.java` are PRESENT (grep finds ≥7 files containing the annotation)
    - `SiteProperties.outputDir` setter is PRESENT (`grep -q "setOutputDir" src/main/java/org/ctc/sitegen/SiteProperties.java`)
    - No Schutzwortliste keyword deleted
  </acceptance_criteria>
  <done>Single commit lands; `./mvnw test` GREEN; 7-sitegen-`@DirtiesContext` invariant holds; `setOutputDir` setter preserved.</done>
</task>

</tasks>

<verification>
- Up to 2 atomic commits on `gsd/v1.10-platform-and-backup` for `admin.service`/`sitegen`
- `./mvnw test` BUILD SUCCESS at HEAD
- 7 sitegen `@DirtiesContext` annotations unchanged
- `SiteProperties.outputDir` setter preserved
- No `Abstract*GraphicService` abstract method signature changed
</verification>

<success_criteria>
- 1-2 atomic per-package commits land
- `./mvnw test` BUILD SUCCESS
- All Schutzwort + Spring/JPA + `@DirtiesContext` invariants hold
- Branch unchanged
</success_criteria>

<output>
After completion, create `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-02d-SUMMARY.md` per `@$HOME/.claude/get-shit-done/templates/summary.md`. Include: per-package commit SHAs, N/M/P/Q counters, `@DirtiesContext` line-number-stability proof, Schutzwort-grep result.
</output>
