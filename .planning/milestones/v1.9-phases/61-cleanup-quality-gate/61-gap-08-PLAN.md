---
plan_id: 61-gap-08
phase: 61-cleanup-quality-gate
title: Defensive over-validation sweep — entire codebase (incl. test) + dead imports
wave: 3
gap_closure: true
autonomous: true
depends_on: [61-gap-06, 61-gap-07]
files_modified:
  - src/main/java/org/ctc/admin/controller
  - src/main/java/org/ctc/admin/service
  - src/main/java/org/ctc/admin
  - src/main/java/org/ctc/domain/service
  - src/main/java/org/ctc/dataimport
  - src/main/java/org/ctc/sitegen
  - src/main/java/org/ctc/gt7sync
  - src/test/java/org/ctc/**
requirements: [MIGR-06, QUAL-01, QUAL-02, QUAL-03]
must_haves:
  - "0 Objects.requireNonNull at internal boundaries (currently already 0 — verify residual)"
  - "0 defensive null-checks classified as 'impossible case' remain in production code"
  - "Dead import statements removed across src/main + src/test"
  - "Test code defensive over-validation (Mockito setUp() boilerplate that asserts framework guarantees) removed"
  - "./mvnw verify -DskipITs BUILD SUCCESS, line coverage >= 82%"
---

<objective>
Final sweep for **G4 (over-validation)** plus dead imports across the full codebase. By the time this plan runs,
61-gap-06 + 61-gap-07 have already addressed the bulk of internal-boundary defensive checks in service layer.
This plan handles the remainder:

- Confirm Objects.requireNonNull count remains 0 (initial planning grep showed 0)
- Sweep test files for setUp()-style framework-guarantee assertions
- Sweep dead imports across the entire codebase (auto-detectable; safe to remove)
- Final classification audit: every remaining null-check anywhere in `src/main` is documented as KEEP or
  flagged for removal

Purpose: G4 codebase-wide cleanup + dead imports.

Output: 0 unjustified defensive validations remain anywhere; coverage stays >= 82%.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/phases/61-cleanup-quality-gate/61-CONTEXT.md
@.planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md
@CLAUDE.md

**Boundary re-statement (CLAUDE.md):**
- "Only validate at system boundaries (user input, external APIs)"
- Trust internal code AND framework guarantees
- Spring's `@RequiredArgsConstructor` + `final` fields = framework guarantees non-null injection. NO defensive
  null-checks needed on Lombok-injected fields.
- Spring's `@Valid` on controller `@ModelAttribute` form binding = framework validates required fields.
- JPA `@NotNull` columns = DB and Hibernate validate non-null on save. Service-layer code can trust.

**Test file boilerplate to REMOVE (over-validation in test code):**
- `setUp()` methods that assert `assertNotNull(service)` immediately after autowiring — Spring would have
  failed @SpringBootTest startup if injection failed
- `verifyNoMoreInteractions(mockObject)` calls in tests where the verifyXXX assertions already cover all
  expected interactions (redundant overhead per CLAUDE.md "trust internal code")
- Tests that catch `Throwable` instead of the specific expected exception type (= no-op assertion)

**Dead imports — codebase-wide:**
- Java compiler does not error on unused imports, but they are dead code
- Detectable via `mvn compile` warnings OR via simple pattern: `import X.Y; .... grep -q '\bY\b' rest of file`
- Most modern IDEs auto-strip these, but cascade migration may have left orphans
</context>

<tasks>

<task id="1" name="Verify residual defensive validation across src/main">
  <action>
    Run a comprehensive grep for ALL forms of defensive validation in `src/main`:

    ```bash
    echo "=== Objects.requireNonNull ===" 
    grep -rn "Objects\.requireNonNull\|requireNonNull(" src/main/java
    echo "=== Defensive null-checks not in 61-gap-06/07 SUMMARYs ===" 
    grep -rn -E "if \([a-zA-Z]+ == null\) (return|throw|continue)" src/main/java
    echo "=== Validate.notNull / Assert.notNull / Preconditions ===" 
    grep -rn -E "Validate\.notNull|Assert\.notNull|Preconditions\.checkNotNull" src/main/java
    echo "=== Defensive isEmpty checks on @NonNull-by-contract collections ===" 
    grep -rn -E "if \([a-zA-Z]+ == null \|\| [a-zA-Z]+\.isEmpty\(\)\)" src/main/java
    ```

    For each hit:
    1. Cross-reference with 61-gap-06-SUMMARY.md and 61-gap-07-SUMMARY.md classification table
    2. If already classified → skip
    3. If new (not classified) → apply boundary-vs-internal classification:
       - User input (controller @RequestParam, file content, sheet rows) → KEEP, document boundary
       - External API response (Google Sheets, GT7 HTTP, file system) → KEEP, document boundary
       - Internal service-to-service or service-to-repository → REMOVE (impossible per framework guarantees)
       - JPA-loaded entity field with `@NotNull` → REMOVE
    4. For REMOVE: TDD-style — write a regression test, then remove, then commit

    For Object.requireNonNull: planning showed 0 hits codebase-wide. Confirm and skip if still 0.

    For dead Validate.notNull / Assert.notNull / Preconditions.checkNotNull: same defensive-removal protocol.

    Commit message per removal: `refactor(61-gap): remove defensive validation at <file:line> (internal contract)`
  </action>
  <read_first>
    - .planning/phases/61-cleanup-quality-gate/61-gap-06-SUMMARY.md (KEEP/REMOVE classification)
    - .planning/phases/61-cleanup-quality-gate/61-gap-07-SUMMARY.md (KEEP/REMOVE classification)
    - CLAUDE.md "validate at system boundaries"
  </read_first>
  <acceptance_criteria>
    - `grep -rn "Objects\.requireNonNull\|requireNonNull(" src/main/java` returns 0 lines
    - Every remaining `if (x == null) ...` line in src/main has a classification entry in SUMMARY (KEEP-boundary, KEEP-sentinel, or DEFERRED with justification)
    - `grep -rn -E "Validate\.notNull|Assert\.notNull|Preconditions\.checkNotNull" src/main/java` returns 0 lines (or every hit is documented as KEEP-boundary)
    - `./mvnw test` BUILD SUCCESS
    - Coverage >= 82%
  </acceptance_criteria>
</task>

<task id="2" name="Sweep test code for over-validation boilerplate">
  <action>
    Step 1 — find redundant `setUp()` framework-guarantee assertions:
    ```bash
    grep -rn -B2 -A2 "assertNotNull\(.*service\|assertNotNull\(.*repository" src/test/java
    ```
    Each hit on a `@Autowired` or `@MockBean`-injected field is over-validation (Spring would have failed
    startup). Remove the `assertNotNull(...)` line.

    Step 2 — find redundant `verifyNoMoreInteractions`:
    ```bash
    grep -rn "verifyNoMoreInteractions" src/test/java
    ```
    For each, check if the test already has explicit `verify(mock).method(...)` calls that account for all
    interactions. If yes → the `verifyNoMoreInteractions` is redundant; remove. If no → it's actually checking
    something useful; KEEP.

    Step 3 — find tests catching `Throwable` or `Exception` (too broad):
    ```bash
    grep -rn -E "catch \((Throwable|Exception) [a-z]+\)" src/test/java
    ```
    Each hit: replace with the specific expected exception type, OR convert to AssertJ
    `assertThatThrownBy(...).isInstanceOf(SpecificException.class)`. CLAUDE.md prefers the AssertJ
    `// when / then` combined pattern.

    Each cleanup: separate commit `refactor(61-gap): remove over-validation in <TestFile>`

    Run after each touched test file:
    ```bash
    ./mvnw test -Dtest='<TouchedTestFile>'
    ```

    **CAUTION:** Some tests use `verifyNoMoreInteractions` to enforce the strict interaction contract
    of the System Under Test (legitimate use of Mockito). Don't blindly remove — read the test intent.
  </action>
  <read_first>
    - Output of the three greps above
    - CLAUDE.md "Test Naming (Given-When-Then)" for exception-test pattern
    - Sample of touched test files (read full body before removal)
  </read_first>
  <acceptance_criteria>
    - `grep -rn "assertNotNull" src/test/java | grep -E "(service|repository|controller|template)\)" | wc -l` decreased to 0 (or every remaining hit verifies a non-injected, non-framework-guaranteed object)
    - `./mvnw test` BUILD SUCCESS
    - Test count UNCHANGED at project level (we're removing assertions inside tests, not @Test methods themselves)
    - Each cleanup is a separate commit
  </acceptance_criteria>
</task>

<task id="3" name="Sweep dead imports across src/main + src/test">
  <action>
    Use Java compiler warnings to enumerate dead imports:

    ```bash
    ./mvnw compile -X 2>&1 | grep "warning.*unused import" | sort -u > /tmp/61-gap-08-dead-imports.txt
    ```

    If the project doesn't surface unused-import warnings (most Maven/javac configs don't by default), use a
    pattern-based detector:

    ```bash
    for f in $(find src/main/java src/test/java -name "*.java"); do
      grep -E "^import" "$f" | sed -E 's/^import\s+(static\s+)?([^;]+);.*/\2/' | while read imp; do
        # extract simple class name (last segment)
        cls=$(echo "$imp" | sed -E 's/.*\.([a-zA-Z][a-zA-Z0-9]*).*/\1/')
        # count uses in body (excluding import line)
        uses=$(grep -v "^import" "$f" | grep -c "\b${cls}\b")
        if [ "$uses" -eq 0 ]; then
          echo "$f: $imp"
        fi
      done
    done > /tmp/61-gap-08-dead-imports.txt
    ```

    Review the list (false positives possible — e.g., wildcard `import a.b.*` won't match the simple-name check;
    also `@Annotation`-only references can be missed by `\bClassName\b` greps).

    For each TRUE positive, remove the import line. Use IDE-style auto-cleanup if possible (IntelliJ "Optimize
    Imports" applied via command-line: `mvn rewrite:run -Drewrite.activeRecipes=org.openrewrite.java.OrderImports`
    if the project uses OpenRewrite — check pom.xml first).

    If neither auto-tool is available, do it manually — the list should be small (cascade migration was disciplined,
    and IntelliJ-saved files have clean imports).

    Commit message: `chore(61-gap): remove dead imports across codebase`
  </action>
  <read_first>
    - pom.xml (check for OpenRewrite or other auto-cleanup plugin)
    - Output of /tmp/61-gap-08-dead-imports.txt
  </read_first>
  <acceptance_criteria>
    - For a sampled set of 10 random files from /tmp/61-gap-08-dead-imports.txt: each listed import is verifiably
      not referenced in the file body (manual sample check)
    - `./mvnw compile` BUILD SUCCESS (no compilation errors from removal)
    - `./mvnw test` BUILD SUCCESS
    - `./mvnw compile -X 2>&1 | grep -c "warning.*unused import"` is 0 (or strictly lower than baseline)
  </acceptance_criteria>
</task>

<task id="4" name="Final verification">
  <action>
    Final verification pass:

    1. Full Surefire + JaCoCo:
       ```bash
       ./mvnw verify -DskipITs
       ```
       Expected: BUILD SUCCESS, coverage >= 82%.

    2. Re-grep for stale validation patterns:
       ```bash
       grep -rn "Objects\.requireNonNull\|Validate\.notNull\|Assert\.notNull\|Preconditions\.checkNotNull" src/main src/test
       ```
       Expected: 0 lines (or all are documented in SUMMARY as KEEP-boundary).

    3. Coverage:
       ```bash
       awk -F, 'NR>1 { line+=$8+$9; missed+=$8 } END { print "Line coverage: " (1 - missed/line) * 100 "%" }' target/site/jacoco/jacoco.csv
       ```
       Expected: >= 82%.

    4. If all green: no commit. If fixes needed: `chore(61-gap): final validation cleanup`
  </action>
  <read_first>
    - Outputs of Tasks 1-3
    - target/site/jacoco/jacoco.csv
  </read_first>
  <acceptance_criteria>
    - `./mvnw verify -DskipITs` BUILD SUCCESS
    - Line coverage >= 82%
    - All grep gates from above return 0 (or hits are KEEP-boundary documented)
  </acceptance_criteria>
</task>

</tasks>

<verification>
- ./mvnw verify -DskipITs BUILD SUCCESS
- Coverage >= 82%
- 0 unjustified defensive validations
- 0 dead imports in compiler warnings
- Test boilerplate (assertNotNull on injected services) removed
</verification>

<success_criteria>
1. Codebase has no Objects.requireNonNull / Validate.notNull / Preconditions.checkNotNull at internal boundaries
2. Test code is free of framework-guarantee assertions
3. Dead imports across src/main + src/test cleared
4. Each cleanup is a separate commit
5. Coverage stays >= 82%
</success_criteria>

<output>
Create `.planning/phases/61-cleanup-quality-gate/61-gap-08-SUMMARY.md`.
</output>
