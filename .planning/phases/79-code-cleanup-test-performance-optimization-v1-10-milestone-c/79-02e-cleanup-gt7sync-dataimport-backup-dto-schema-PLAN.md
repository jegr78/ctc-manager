---
phase: 79
plan: 02e
type: execute
wave: 2
depends_on: [79-01]
files_modified:
  - src/main/java/org/ctc/gt7sync/**
  - src/test/java/org/ctc/gt7sync/**
  - src/main/java/org/ctc/dataimport/**
  - src/test/java/org/ctc/dataimport/**
  - src/main/java/org/ctc/backup/dto/**
  - src/test/java/org/ctc/backup/dto/**
  - src/main/java/org/ctc/backup/schema/**
  - src/test/java/org/ctc/backup/schema/**
autonomous: true
requirements: [D-01, D-02, D-03, D-04, D-09, D-10, D-11, D-12, D-13]

must_haves:
  truths:
    - "`BackupSchema.SCHEMA_VERSION = 1` constant + `EXPORT_ORDER` topo-sorted list are unchanged"
    - "`BackupManifest` record component names + `@JsonProperty(\"snake_case\")` annotations are unchanged (wire contract)"
    - "Google Sheets / Calendar / Jsoup integration points in `gt7sync` + `dataimport` remain functional"
    - "`./mvnw test` BUILD SUCCESS after each per-package commit"
  artifacts:
    - path: "git log on branch gsd/v1.10-platform-and-backup"
      provides: "Up to 4 atomic commits"
      pattern: "refactor\\(79\\): cleanup org\\.ctc\\.(gt7sync|dataimport|backup\\.dto|backup\\.schema) package"
  key_links:
    - from: "BackupSchema constants"
      to: "Wire contract (Phase 72 + 73 + 74 + 75)"
      via: "unchanged SCHEMA_VERSION + EXPORT_ORDER"
      pattern: "SCHEMA_VERSION = 1|EXPORT_ORDER"
---

<objective>
Wave 2 cleanup sweep — four mid-rank packages from RESEARCH ordering:

1. `org.ctc.gt7sync` — 4 files (import count 4). Jsoup scraping + sync orchestration.
2. `org.ctc.dataimport` — 7 files (import count 8). CSV import, Google Sheets, Google Calendar, driver matching.
3. `org.ctc.backup.dto` — 4 files (import count 6). DTO records (`BackupBundle`, `BackupPreview`, ...).
4. `org.ctc.backup.schema` — 4 files (import count 8). `BackupSchema`, `EntityRef`, `EntityTopoSorter`, `BackupManifest` — frozen wire contract.

Output: 1-4 atomic per-package commits. Cleanup classes per D-02; `backup.schema` is exceptionally conservative (wire contract is frozen per CONTEXT.md §"Out of scope" verhaltens-erhaltend constraint).
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
**Wire-contract invariants (Phase 72/73/74/75 — frozen per CONTEXT.md):**

- `BackupSchema.SCHEMA_VERSION = 1` — integer constant; do NOT change the value or the field name.
- `BackupSchema.EXPORT_ORDER` — `List<EntityRef>` covering all 24 operative entity classes in topo-sorted order. Do NOT change the order or any element.
- `BackupManifest` record components (per Phase 72 plan 02): `schemaVersion`, `appVersion`, `exportDate`, `tableCounts`. Each component has a `@JsonProperty("snake_case_key")` per the wire contract. Do NOT change component names or `@JsonProperty` values.
- `EntityRef` record — `name`, `entityClass`, `repositoryClass` field names; do NOT rename.
- `EntityTopoSorter.sort()` — Kahn's algorithm; do NOT refactor unless extraction is purely internal (no behavior change).

**D-04 forbid-list for these packages:**
- `@JsonProperty`, `@JsonCreator`, `@JsonAlias` (Jackson reflection — invisible to grep)
- Records' canonical constructors (Java records auto-generate; explicit canonical-constructor rewrites are FORBIDDEN if they would change validation)
- `@Service`, `@Component`, `@Repository` annotations on gt7sync/dataimport classes
- `@Scheduled` annotations if present in gt7sync (Spring lifecycle)
- `GoogleSheetsService.read*()` public methods — called via external configuration

**Schutzwort hotspots:**
- `gt7sync`: `Jsoup`, `scraping`, `WORKAROUND` (HTML structure changes)
- `dataimport`: `Google Sheets`, `auditing`, `CVE` (sheet access tokens), `WORKAROUND`
- `backup.dto`: usually minimal Schutzwort surface
- `backup.schema`: `MariaDB`, `H2`, `LONGTEXT`, `auditing`, `auto-commit`, `transitiv`/`transitive` (FK ordering)

**Cleanup procedure** inherits from Plan 02a `<interfaces>` block.
</interfaces>
</context>

<critical_constraints>
- You are working on branch `gsd/v1.10-platform-and-backup`. Do NOT run `git stash`, `git checkout`, `git reset`, or switch branches.
- Implement ONLY the tasks listed below. If you find that other files need changes, report `NEEDS_CONTEXT` instead of expanding scope.
- After each per-package commit, run `./mvnw test` and verify it passes BEFORE moving to the next package.
- Schutzwortliste (D-13): Comments containing any of these words must NOT be deleted: MariaDB, H2, JEP, CVE, race, thread-safe, TODO, HACK, WORKAROUND, FIXME, deadlock, OSIV, Lombok, Unsafe, transitiv, transitive, pitfall, auto-commit, auditing, AuditingEntityListener.
- Dead-code removal rule (D-04): Only delete when (a) grep finds zero references AND (b) no Spring/JPA/Jackson lifecycle annotation AND (c) not a JPA no-arg constructor / Jackson public setter. Records' canonical constructors are auto-generated; do NOT add explicit canonical constructors that would change validation. On uncertainty → leave it.
- Wire-contract invariant: `BackupSchema.SCHEMA_VERSION = 1`, `BackupSchema.EXPORT_ORDER`, `BackupManifest` record component names + `@JsonProperty` keys are frozen. Do NOT change any of these.
- Each package gets EXACTLY ONE commit.
</critical_constraints>

<test_impact>
- Packages touched: `gt7sync` (4 files), `dataimport` (7 files), `backup.dto` (4 files), `backup.schema` (4 files) = 19 source files total
- Test classes likely touched: ~25 unit + IT tests across the 4 packages (including `BackupSchemaTopoIT`, `BackupManifestSerializationTest`, `BackupSchemaExclusionIT`)
- Mockito stub updates: NONE
- Bridge-only test deletions: NONE
- Estimated test edit count: 0-10 comment-thinning edits
- JaCoCo impact: backup.schema is comprehensively tested (Phase 72); ~0 delta. gt7sync + dataimport include some lightly tested code paths — cleanup must NOT delete a method whose deletion would drop coverage below 0.82.
</test_impact>

<tasks>

<task type="auto">
  <name>Task 1: Cleanup org.ctc.gt7sync package (1 commit)</name>
  <files>src/main/java/org/ctc/gt7sync/**, src/test/java/org/ctc/gt7sync/**</files>
  <read_first>
    - All `.java` files in `src/main/java/org/ctc/gt7sync/` (4 files)
    - Mirror test files
    - Plan 02a `<interfaces>` for the 4-pass cleanup procedure
  </read_first>
  <action>
Apply the 4-pass cleanup. SPECIFIC INVARIANTS: keep Jsoup CSS-selector comments verbatim (Schutzwort: `WORKAROUND`, `pitfall` for HTML structure quirks). `@Service`/`@Component` annotations stay. Stage `git add src/main/java/org/ctc/gt7sync/ src/test/java/org/ctc/gt7sync/`. Commit `refactor(79): cleanup org.ctc.gt7sync package` with the 4-counter body. `./mvnw test -Dspring.profiles.active=dev`. RED → revert + `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS" &amp;&amp; git log -1 --pretty=%B | grep -q "refactor(79): cleanup org\.ctc\.gt7sync package"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Commit lands
    - No Schutzwortliste keyword deleted
    - `@Service`/`@Component` annotations preserved
  </acceptance_criteria>
  <done>Commit lands; tests GREEN; integrations intact.</done>
</task>

<task type="auto">
  <name>Task 2: Cleanup org.ctc.dataimport package (1 commit)</name>
  <files>src/main/java/org/ctc/dataimport/**, src/test/java/org/ctc/dataimport/**</files>
  <read_first>
    - All `.java` files in `src/main/java/org/ctc/dataimport/` (7 files)
    - Mirror test files
    - Plan 02a `<interfaces>` for the 4-pass procedure
    - `feedback_racelineup_source_of_truth` memory rule (from CLAUDE.md §"Architectural Principles" — RaceLineup is source of truth)
  </read_first>
  <action>
Apply the 4-pass cleanup. SPECIFIC INVARIANTS: Google Sheets API token / credentials comments stay (Schutzwort: `CVE`, `auditing`). `CsvImportService` two-phase preview-execute pattern is established per CLAUDE.md §"Architectural Principles" — preserve. `DriverMatchingService` PSN-ID-matching comments often contain `WORKAROUND` for fuzzy-match edge cases → preserve. `RaceLineup` references in code paths stay (RaceLineup source-of-truth invariant). Stage `git add src/main/java/org/ctc/dataimport/ src/test/java/org/ctc/dataimport/`. Commit `refactor(79): cleanup org.ctc.dataimport package` with the 4-counter body. `./mvnw test -Dspring.profiles.active=dev`. RED → revert + `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS" &amp;&amp; git log -1 --pretty=%B | grep -q "refactor(79): cleanup org\.ctc\.dataimport package"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Commit lands
    - No Schutzwortliste keyword deleted
    - Preview-execute pattern intact in `CsvImportService`
  </acceptance_criteria>
  <done>Commit lands; tests GREEN; CSV import flow + Google integrations intact.</done>
</task>

<task type="auto">
  <name>Task 3: Cleanup org.ctc.backup.dto package (1 commit)</name>
  <files>src/main/java/org/ctc/backup/dto/**, src/test/java/org/ctc/backup/dto/**</files>
  <read_first>
    - All `.java` files in `src/main/java/org/ctc/backup/dto/` (4 files: `BackupBundle`, `BackupPreview`, ...)
    - Mirror test files
    - Plan 02a `<interfaces>` for the 4-pass procedure
  </read_first>
  <action>
Apply the 4-pass cleanup. SPECIFIC INVARIANTS: records' canonical constructors are auto-generated by the compiler — do NOT introduce explicit canonical constructors that change behavior. Record component names + types are part of the API surface used by `BackupExportService`/`BackupImportService` — do NOT rename. Stage `git add src/main/java/org/ctc/backup/dto/ src/test/java/org/ctc/backup/dto/`. Commit `refactor(79): cleanup org.ctc.backup.dto package` with the 4-counter body. SKIP commit if no eligible edits exist (records are typically terse). `./mvnw test -Dspring.profiles.active=dev`. RED → revert + `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Either a commit `refactor(79): cleanup org.ctc.backup.dto package` lands OR the SUMMARY records "no eligible edits"
    - No record component renamed
    - No Schutzwortliste keyword deleted
  </acceptance_criteria>
  <done>Commit lands (or skipped + documented); tests GREEN; record API surfaces intact.</done>
</task>

<task type="auto">
  <name>Task 4: Cleanup org.ctc.backup.schema package (1 commit) — WIRE-CONTRACT-FROZEN</name>
  <files>src/main/java/org/ctc/backup/schema/**, src/test/java/org/ctc/backup/schema/**</files>
  <read_first>
    - `src/main/java/org/ctc/backup/schema/BackupSchema.java`
    - `src/main/java/org/ctc/backup/schema/EntityRef.java`
    - `src/main/java/org/ctc/backup/schema/EntityTopoSorter.java`
    - `src/main/java/org/ctc/backup/schema/BackupManifest.java`
    - Mirror test files (`BackupSchemaTopoIT`, `BackupManifestSerializationTest`, `BackupSchemaExclusionIT`)
    - Plan 02a `<interfaces>` for the 4-pass procedure
  </read_first>
  <action>
Apply the 4-pass cleanup — MOST CONSERVATIVE PACKAGE OF THIS PLAN. SPECIFIC INVARIANTS (verbatim from `<interfaces>`):

- `BackupSchema.SCHEMA_VERSION = 1` — DO NOT change.
- `BackupSchema.EXPORT_ORDER` order + content — DO NOT change.
- `BackupManifest` record components (`schemaVersion`, `appVersion`, `exportDate`, `tableCounts`) + their `@JsonProperty("snake_case")` annotations — DO NOT change.
- `EntityRef` record fields — DO NOT rename.
- `EntityTopoSorter.sort()` Kahn's-algorithm logic — only internal helper extraction allowed; no algorithm change.

Pre-flight invariant grep (run BEFORE any edit):
```
grep -E "SCHEMA_VERSION = 1\b" src/main/java/org/ctc/backup/schema/BackupSchema.java
grep -c "@JsonProperty" src/main/java/org/ctc/backup/schema/BackupManifest.java
```
Capture both values. Re-run AFTER editing — values MUST be identical.

Cleanup is dominated by comment-thinning (D-09 phase-N prefixes like `// Phase 72 plan 01:`, `// GAP-5:`, `// SCHEMA-01`). Schutzwort-protected comments (`MariaDB`, `H2`, `LONGTEXT`, `auditing`, `transitiv` for FK ordering) stay verbatim. Class-Javadoc on `BackupSchema` may be condensed to 1-3 lines per D-12, but the canonical wire-contract description ("integer schema versioning, manifest-first, FK topo-sorted 24 entities") stays.

Stage `git add src/main/java/org/ctc/backup/schema/ src/test/java/org/ctc/backup/schema/`. Commit `refactor(79): cleanup org.ctc.backup.schema package — comment-thinning + wire-contract-preserved` with body:
```
- &lt;N&gt; comment-thinning edits (Schutzwortliste honored)
- 0 dead-code removals (wire-contract frozen)
- 0 extract-method refactors (algorithm frozen)
- 0 logic-simplifications (wire-contract frozen)
- SCHEMA_VERSION = 1 invariant: confirmed
- BackupManifest @JsonProperty count unchanged: N_before == N_after = &lt;count&gt;
```
`./mvnw test -Dspring.profiles.active=dev`. RED → revert + `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS" &amp;&amp; grep -Eq "SCHEMA_VERSION = 1\b" src/main/java/org/ctc/backup/schema/BackupSchema.java &amp;&amp; git log -1 --pretty=%B | grep -q "SCHEMA_VERSION = 1 invariant: confirmed"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Commit `refactor(79): cleanup org.ctc.backup.schema package` lands (or SUMMARY records "no eligible edits")
    - `SCHEMA_VERSION = 1` invariant preserved
    - `BackupManifest` `@JsonProperty` count unchanged
    - `EXPORT_ORDER` list unchanged (no entity added/removed/reordered)
    - No Schutzwortliste keyword deleted
  </acceptance_criteria>
  <done>Commit lands (or skipped); tests GREEN; wire-contract invariants hold.</done>
</task>

</tasks>

<verification>
- Up to 4 atomic commits land on `gsd/v1.10-platform-and-backup`
- `./mvnw test` BUILD SUCCESS after each
- Wire-contract invariants hold: `SCHEMA_VERSION = 1`, `EXPORT_ORDER`, `BackupManifest` record components + `@JsonProperty` keys
- `RaceLineup` source-of-truth pattern intact in dataimport
- All Schutzwort keywords intact across the 4 packages
</verification>

<success_criteria>
- 1-4 atomic per-package commits land
- `./mvnw test` BUILD SUCCESS
- All wire-contract invariants hold
- All Schutzwort + lifecycle invariants hold
- Branch unchanged
</success_criteria>

<output>
After completion, create `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-02e-SUMMARY.md` per `@$HOME/.claude/get-shit-done/templates/summary.md`. Include: per-package commit SHAs, N/M/P/Q counters, `SCHEMA_VERSION = 1` + `@JsonProperty` count proofs, Schutzwort-grep result.
</output>
