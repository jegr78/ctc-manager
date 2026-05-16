---
phase: 79
plan: 02g
type: execute
wave: 2
depends_on: [79-01]
files_modified:
  - src/main/java/org/ctc/admin/dto/**
  - src/test/java/org/ctc/admin/dto/**
  - src/main/java/org/ctc/backup/*.java
  - src/test/java/org/ctc/backup/*.java
  - src/main/java/org/ctc/admin/*.java
  - src/test/java/org/ctc/admin/*.java
autonomous: true
requirements: [D-01, D-02, D-03, D-04, D-09, D-10, D-11, D-12, D-13]

must_haves:
  truths:
    - "All admin Form DTOs continue to bind correctly via `@Valid` + `BindingResult` (CLAUDE.md §`Controller & DTO Patterns`)"
    - "`BackupController` endpoints `/admin/backup`, `/admin/backup/export`, `/admin/backup/import-preview`, `/admin/backup/import-execute` are unchanged"
    - "`DevDataSeeder`, `DemoDataSeeder`, `TestDataService`, `SecurityConfig`, `OpenSecurityConfig`, `WebConfig` profile-gating logic is unchanged"
    - "`./mvnw test` BUILD SUCCESS after each per-package commit"
  artifacts:
    - path: "git log on branch gsd/v1.10-platform-and-backup"
      provides: "Up to 3 atomic commits"
      pattern: "refactor\\(79\\): cleanup org\\.ctc\\.(admin\\.dto|backup|admin) (root )?package"
  key_links:
    - from: "admin.dto Form classes"
      to: "Controller @ModelAttribute bindings"
      via: "field-name + @NotNull/@Size validation annotations"
      pattern: "@Valid|@NotNull|@Size|@NotBlank"
---

<objective>
Wave 2 cleanup sweep — three Late-rank packages that have many incoming imports (high blast radius — cleaned AFTER leaf packages so callers are already tidied):

1. `org.ctc.admin.dto` — 22 files (import count 28). Form DTOs + display DTOs / records.
2. `org.ctc.backup` (root package) — 1 file (import count 65). Likely the top-level package-info or a single root class.
3. `org.ctc.admin` (root package) — 6 files (import count 42). `DevDataSeeder`, `DemoDataSeeder`, `TestDataService`, `SecurityConfig`, `OpenSecurityConfig`, `WebConfig`.

Output: 1-3 atomic per-package commits. Cleanup classes per D-02 with extreme caution on `SecurityConfig`/`OpenSecurityConfig` (Spring Security wiring).
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
**admin.dto invariants:**
- Form classes (`SeasonForm`, `RaceForm`, `RaceResultForm`, ...) are bound via `@ModelAttribute` + `@Valid` in controllers. Field names + validation annotation values (`@NotNull`, `@NotBlank`, `@Size(min=...)`, `@Email`, `@Pattern(regexp=...)`) are part of the controller contract. DO NOT rename fields or change validation values.
- Display DTOs / records (`MatchdayDto`, `RankedTeamData`, `SeasonDriverGroupDto`, ...) are used by Thymeleaf templates. Field names are referenced from `*.html` files. DO NOT rename.
- Pre-flight: `grep -c "@Valid\|@NotNull\|@NotBlank\|@Size\|@Email\|@Pattern" src/main/java/org/ctc/admin/dto/*.java` — record N_validations_before. Post-flight: count MUST be unchanged.

**Profile-gating invariants for admin root files (CLAUDE.md §"Constraints"):**
- `SecurityConfig`: `@Profile("prod | docker")` — auth ON.
- `OpenSecurityConfig`: `@Profile("dev | local")` — permit-all.
- These two profile annotations are LOAD-BEARING. DO NOT touch.
- `DevDataSeeder`: `@Profile("dev")`.
- `DemoDataSeeder`: `@Profile("dev & demo")` or similar — preserves the `dev,demo` profile pattern.
- `TestDataService`: used by E2E tests for isolated test entities with `T-`/`Test_`/`Test-` prefixes (CLAUDE.md §"Architectural Principles").

**`org.ctc.backup` root package:**
- Single file (per RESEARCH §"Per-Package Cleanup Ordering" import-count 65, file count 1). Likely `package-info.java` or a root `BackupConfiguration` class. Pure comment-thinning candidate.

**Cleanup procedure** inherits from Plan 02a `<interfaces>` block.
</interfaces>
</context>

<critical_constraints>
- You are working on branch `gsd/v1.10-platform-and-backup`. Do NOT run `git stash`, `git checkout`, `git reset`, or switch branches.
- Implement ONLY the tasks listed below. If you find that other files need changes, report `NEEDS_CONTEXT` instead of expanding scope.
- After each per-package commit, run `./mvnw test` and verify it passes BEFORE moving to the next package.
- Schutzwortliste (D-13): Comments containing any of these words must NOT be deleted: MariaDB, H2, JEP, CVE, race, thread-safe, TODO, HACK, WORKAROUND, FIXME, deadlock, OSIV, Lombok, Unsafe, transitiv, transitive, pitfall, auto-commit, auditing, AuditingEntityListener.
- Dead-code removal rule (D-04): Form DTO fields are referenced from controller `@ModelAttribute` parameters AND Thymeleaf templates (`th:field`, `${form.fieldName}`). Reflection-equivalent. DO NOT delete a Form DTO field even if Java grep shows zero callers — check Thymeleaf template references with `grep -rE "form\.&lt;fieldName&gt;|th:field=\"\*\{&lt;fieldName&gt;\}\"" src/main/resources/templates/` before any deletion.
- `@Profile` annotations on SecurityConfig/OpenSecurityConfig/DevDataSeeder/DemoDataSeeder are LOAD-BEARING per CLAUDE.md §"Constraints" ("Auth only for prod/docker; dev/local remains without auth"). DO NOT touch.
- Each package gets EXACTLY ONE commit.
</critical_constraints>

<test_impact>
- Packages touched: `admin.dto` (22 files), `backup` root (1 file), `admin` root (6 files) = 29 source files
- Test classes likely touched: form validation tests (`SeasonFormTest`, `RaceFormTest`, ...), `SecurityIntegrationTest`, `DevDataSeederTest`, `BackupController*Test`
- Mockito stub updates: NONE — DTOs are POJOs/records; SecurityConfig is tested via real Spring context (`@SpringBootTest`)
- Bridge-only test deletions: NONE
- Estimated test edit count: 0-10 (mostly comment-thinning)
- JaCoCo impact: admin.dto + admin root are well-tested. ~0 delta expected. `TestDataService` is excluded from JaCoCo per pom.xml line 314.
- Thymeleaf-template-binding risk: Form DTO field rename would break templates silently (template rendering fails at runtime, not compile-time). This plan FORBIDS Form DTO field renames.
</test_impact>

<tasks>

<task type="auto">
  <name>Task 1: Cleanup org.ctc.admin.dto package (1 commit)</name>
  <files>src/main/java/org/ctc/admin/dto/**, src/test/java/org/ctc/admin/dto/**</files>
  <read_first>
    - All 22 `.java` files in `src/main/java/org/ctc/admin/dto/`
    - Mirror test files
    - Plan 02a `<interfaces>` for the 4-pass procedure
  </read_first>
  <action>
Apply the 4-pass cleanup. SPECIFIC INVARIANTS:

1. **Pre-flight validation count:**
```
grep -cE "@Valid|@NotNull|@NotBlank|@Size|@Email|@Pattern|@Min|@Max|@Future|@Past|@DecimalMin|@DecimalMax" src/main/java/org/ctc/admin/dto/*.java | awk -F: '{s+=$2} END{print s}'
```
Record `N_validations_before`.

2. **Comment-thinning:** Strip Phase-N prefixes. Form DTOs often have `@Valid`-related Javadoc — keep `@param`/`@return`/`@throws` if present, condense general prose.

3. **Dead-code:** EXTRA CAUTION. Before deleting ANY Form DTO field, run `grep -rE "form\.&lt;fieldName&gt;|th:field=\"\*\{&lt;fieldName&gt;\}\"|th:value=\"\$\{form\.&lt;fieldName&gt;\}\"" src/main/resources/templates/`. If grep finds ANY hit → DO NOT delete the field. Display DTOs / records: same rule, grep templates for the field name.

4. **Extract-method:** Form DTOs are typically simple POJOs/records — usually nothing to extract. Display DTOs with helper methods (e.g., `computeDisplayName()`) are candidates if &gt;30 LOC and readability clearly improves.

5. **Logic-simplification:** Standard. Validation annotation values stay verbatim.

6. **Post-flight validation count:**
```
grep -cE "@Valid|@NotNull|@NotBlank|@Size|@Email|@Pattern|@Min|@Max|@Future|@Past|@DecimalMin|@DecimalMax" src/main/java/org/ctc/admin/dto/*.java | awk -F: '{s+=$2} END{print s}'
```
MUST equal `N_validations_before`.

Stage `git add src/main/java/org/ctc/admin/dto/ src/test/java/org/ctc/admin/dto/`. Commit `refactor(79): cleanup org.ctc.admin.dto package — comment-thinning, validation-count preserved` with body including `Validation annotation count unchanged: N_before == N_after = <count>`. `./mvnw test -Dspring.profiles.active=dev`. RED → revert + `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS" &amp;&amp; git log -1 --pretty=%B | grep -q "refactor(79): cleanup org\.ctc\.admin\.dto package" &amp;&amp; git log -1 --pretty=%B | grep -q "Validation annotation count unchanged"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Commit lands
    - Validation annotation count unchanged
    - No Form DTO field renamed (verified by grep of Thymeleaf templates still finding all references after the commit)
    - No Schutzwortliste keyword deleted
  </acceptance_criteria>
  <done>Single commit lands; tests GREEN; Form DTO field invariants hold.</done>
</task>

<task type="auto">
  <name>Task 2: Cleanup org.ctc.backup root package (1 commit)</name>
  <files>src/main/java/org/ctc/backup/*.java, src/test/java/org/ctc/backup/*.java</files>
  <read_first>
    - Confirm package contents: `ls src/main/java/org/ctc/backup/*.java 2>/dev/null` (file count should be ~1 per RESEARCH ordering rank 19)
    - Mirror test files in `src/test/java/org/ctc/backup/*.java` (non-recursive)
    - Plan 02a `<interfaces>` for the 4-pass procedure
  </read_first>
  <action>
Apply the 4-pass cleanup to the file(s) directly under `src/main/java/org/ctc/backup/` (NOT subpackages — those are owned by Plans 02a-02f). Likely 1 file: either `package-info.java` or a root `BackupConfiguration`/`BackupApplication`-style class.

SPECIFIC CAVEAT: glob `src/main/java/org/ctc/backup/*.java` is NON-RECURSIVE. Stage explicitly: `git add src/main/java/org/ctc/backup/*.java src/test/java/org/ctc/backup/*.java` (the shell will only expand to top-level files). Confirm with `git status` that NO subpackage files are staged (those are owned by Plans 02a-02f). If git status shows subpackage files in the staging area, `git reset HEAD &lt;path&gt;` to unstage and re-run.

Commit `refactor(79): cleanup org.ctc.backup root package` with the 4-counter body. SKIP commit if no eligible edits (1-file package with terse code). `./mvnw test -Dspring.profiles.active=dev`. RED → revert + `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Either a commit `refactor(79): cleanup org.ctc.backup root package` lands OR the SUMMARY records "no eligible edits"
    - No subpackage files (those owned by Plans 02a-02f) appear in this commit's diff
    - No Schutzwortliste keyword deleted
  </acceptance_criteria>
  <done>Commit lands (or skipped); tests GREEN; no subpackage scope creep.</done>
</task>

<task type="auto">
  <name>Task 3: Cleanup org.ctc.admin root package (1 commit) — Profile-gating critical</name>
  <files>src/main/java/org/ctc/admin/*.java, src/test/java/org/ctc/admin/*.java</files>
  <read_first>
    - `src/main/java/org/ctc/admin/DevDataSeeder.java`
    - `src/main/java/org/ctc/admin/DemoDataSeeder.java`
    - `src/main/java/org/ctc/admin/TestDataService.java`
    - `src/main/java/org/ctc/admin/SecurityConfig.java`
    - `src/main/java/org/ctc/admin/OpenSecurityConfig.java`
    - `src/main/java/org/ctc/admin/WebConfig.java`
    - Mirror test files at `src/test/java/org/ctc/admin/*.java` (non-recursive — NOT subpackages)
    - Plan 02a `<interfaces>` for the 4-pass procedure
    - CLAUDE.md §"Constraints" §"Spring Profiles" §"Architectural Principles" (TestDataService isolation, profile boundaries)
  </read_first>
  <action>
Apply the 4-pass cleanup to the 6 files directly under `src/main/java/org/ctc/admin/` (non-recursive). SPECIFIC INVARIANTS:

1. **SecurityConfig + OpenSecurityConfig:** `@Profile("prod | docker")` vs `@Profile("dev | local")` annotations are LOAD-BEARING. DO NOT touch the `@Profile` annotation values or the `@Configuration`/`@EnableWebSecurity` annotations. SecurityFilterChain bean definitions stay. Comments adjacent to authorization rules MUST be preserved if they contain Schutzwort hits (especially `CVE`, `auth`, `csrf`).

2. **DevDataSeeder + DemoDataSeeder:** `@Profile` annotations stay. `@PostConstruct` / `CommandLineRunner` lifecycle methods stay. Comments explaining the seeded test data (e.g., "creates Saison 2023 with 8 teams" — operational documentation) stay. PerformancePerformance.

3. **TestDataService:** Test-prefix invariant (CLAUDE.md §"Architectural Principles" — `T-ALF`, `Test_Alpha_1`, `Test-Season 2026`). DO NOT change any prefix. Methods called from E2E tests are reflection-equivalent for the cleanup purpose.

4. **WebConfig:** `@Configuration` + upload-serving config. `addResourceHandlers` / `addInterceptors` methods stay.

Stage `git add src/main/java/org/ctc/admin/*.java src/test/java/org/ctc/admin/*.java` (non-recursive). Confirm with `git status` no subpackage paths leak in (those are owned by Plans 02a/02d). Commit `refactor(79): cleanup org.ctc.admin root package — profile-gating preserved` with the 4-counter body. `./mvnw test -Dspring.profiles.active=dev`. RED → revert + `NEEDS_CONTEXT`. Watch `SecurityIntegrationTest` carefully — it asserts the prod/dev profile gate.
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS" &amp;&amp; grep -q "@Profile" src/main/java/org/ctc/admin/SecurityConfig.java &amp;&amp; grep -q "@Profile" src/main/java/org/ctc/admin/OpenSecurityConfig.java</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Commit `refactor(79): cleanup org.ctc.admin root package` lands (or SUMMARY records "no eligible edits")
    - `@Profile` annotations on SecurityConfig + OpenSecurityConfig preserved (grep finds both)
    - TestDataService prefix-invariants intact (no rename of `T-` / `Test_` / `Test-` patterns)
    - No subpackage files in this commit's diff
    - No Schutzwortliste keyword deleted
  </acceptance_criteria>
  <done>Commit lands (or skipped); tests GREEN; SecurityConfig/OpenSecurityConfig profile-gating + TestDataService prefixes intact.</done>
</task>

</tasks>

<verification>
- Up to 3 atomic commits land on `gsd/v1.10-platform-and-backup`
- `./mvnw test` BUILD SUCCESS after each
- admin.dto validation annotation count unchanged
- No Form DTO field renamed (Thymeleaf-template grep still finds all references)
- `@Profile` annotations on Security{,Open}Config preserved
- TestDataService prefix-invariants intact
- No subpackage files touched by Tasks 2/3 (those are owned by Plans 02a-02f)
</verification>

<success_criteria>
- 1-3 atomic per-package commits land
- `./mvnw test` BUILD SUCCESS
- All Form DTO field + validation + profile-gating + TestDataService invariants hold
- All Schutzwort keywords intact
- Branch unchanged
</success_criteria>

<output>
After completion, create `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-02g-SUMMARY.md` per `@$HOME/.claude/get-shit-done/templates/summary.md`. Include: per-package commit SHAs, N/M/P/Q counters, validation count invariant, `@Profile` annotation grep proofs, no-subpackage-leakage confirmation.
</output>
