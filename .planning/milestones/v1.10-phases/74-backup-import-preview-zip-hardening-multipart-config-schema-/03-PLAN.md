---
id: "03"
title: "Import preview + confirm form DTOs"
wave: 1
depends_on: []
requirements: [IMPORT-03, IMPORT-04]
files_modified:
  - src/main/java/org/ctc/backup/dto/BackupImportPreview.java
  - src/main/java/org/ctc/backup/dto/EntityRowCount.java
  - src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java
  - src/test/java/org/ctc/backup/dto/BackupImportConfirmFormValidationTest.java
  - src/test/java/org/ctc/backup/dto/BackupImportPreviewTest.java
  - src/test/java/org/ctc/backup/dto/EntityRowCountTest.java
autonomous: true
---

## Objective

Create the three DTO types that make up the wire/binding surface of the Phase 74 backup-import preview + confirm flow:

1. `BackupImportPreview` (record) — read-only view-model that Thymeleaf binds to on `admin/backup-preview.html`. Carries the staging UUID, the original filename + size, the schema-version pair (extracted + currently-expected), a precomputed `schemaMatches` boolean, the per-entity row-count cards (`List<EntityRowCount>`), the count of files under `uploads/`, and the total imported-rows sum across all entities. Per CONTEXT D-21.
2. `EntityRowCount` (record) — one card in the 24-card preview grid. Carries the snake_case `tableName`, a precomputed `humanLabel` (e.g. `Season Phases`), and the `currentRows` / `importedRows` pair. The label is filled by `BackupImportService` (Plan 05) — this record is a pure data carrier. Per CONTEXT D-21.
3. `BackupImportConfirmForm` (Lombok form class) — the form Spring binds the confirm-page POST to. Carries the staging UUID + a `Boolean acknowledged` field guarded by `@NotNull` AND `@AssertTrue` (Jakarta Bean Validation §6.1.1: `@AssertTrue` short-circuits on `null`, so both annotations are mandatory). Per CONTEXT D-10 + D-21 + RESEARCH §Pattern 7 + §Pitfall 3.

This plan ships only the DTOs plus their fast Surefire unit tests. No service, no controller, no template — those land in Plans 05/06/07. The full Spring-binding/`BindingResult` IT (`BackupImportConfirmFormValidationIT`) belongs to Plan 08.

This is Wave 1 with zero dependencies: every other Phase 74 plan that touches Java code (Plan 05 service, Plan 06 controller, Plan 07 templates, Plan 08 ITs) imports from `org.ctc.backup.dto.*`. Shipping these contracts first removes the scavenger-hunt for downstream executors.

## Tasks

<task type="auto" tdd="true">
  <name>Task 1: Create BackupImportPreview + EntityRowCount records</name>

  <read_first>
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md (D-21 verbatim record signatures — authoritative; D-03/D-04/D-05 for which fields each preview surface uses)
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-UI-SPEC.md §"Copywriting Contract" (which fields drive header-block strings — `{originalFilename}`, `{sizeInMB}`, `{uploadFileCount}`, `{totalImportedRows}`, `{schemaVersion}`, plus per-card `{humanLabel}` and `{currentRows} → {importedRows}`)
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md §"BackupImportPreview" + §"EntityRowCount" (record pattern adapted from BackupManifest / EntityRef — record-only, no Lombok, no @JsonProperty)
    - src/main/java/org/ctc/backup/schema/BackupManifest.java (sibling record analog — record style, Javadoc shape, package convention)
    - src/main/java/org/ctc/backup/schema/EntityRef.java (smallest-record analog in the codebase — confirms no Lombok, no annotations on records)
  </read_first>

  <behavior>
    BackupImportPreview record:
    - Constructed with all 9 fields populated. equals/hashCode/toString are inherited from the record contract — no manual overrides.
    - schemaMatches is a stored field (canonical constructor takes it verbatim — NOT computed). Rationale: keeps the record a pure data carrier; the service that builds the preview computes `schemaVersion == currentSchemaVersion` once and passes the result in. Verified in BackupImportPreviewTest.
    - entityCounts is a List<EntityRowCount>; the record does not defensively copy (records cannot enforce immutability of List references — that is by design; the service must hand in a List.copyOf(...) per CLAUDE.md "Architectural Principles" if defensive copying is needed downstream).

    EntityRowCount record:
    - Constructed with all 4 fields populated; equals/hashCode/toString from record contract.
    - humanLabel is whatever the constructor was handed — Plan 05's BackupImportService.toHumanLabel(tableName) produces it. This record holds no logic.
    - Tests: tableName + humanLabel are stored verbatim; currentRows/importedRows are primitive long (not Long) — verified by the field-type accessor signatures.

    Both records:
    - Public top-level types in package `org.ctc.backup.dto`.
    - No @JsonProperty (CONTEXT D-21: template-bound, not serialized over the wire).
    - No Lombok (records are already immutable).
    - Javadoc on each record explaining purpose + which surface consumes it.
  </behavior>

  <action>
    Create `src/main/java/org/ctc/backup/dto/BackupImportPreview.java`:

    Package declaration `org.ctc.backup.dto`. Imports: `java.util.List`, `java.util.UUID`. Public record declaration with the field list per CONTEXT D-21 verbatim:

    ```
    UUID stagingId,
    String originalFilename,
    long fileSizeBytes,
    int schemaVersion,
    int currentSchemaVersion,
    boolean schemaMatches,
    List<EntityRowCount> entityCounts,
    int uploadFileCount,
    long totalImportedRows
    ```

    Javadoc above the record: cite Phase 74 + CONTEXT D-21, name `admin/backup-preview.html` as the Thymeleaf consumer, note that `schemaMatches` is stored (not derived), and reference `BackupImportService.stage(...)` (Plan 05) as the builder. Mirror the doc-comment shape of `BackupManifest.java:8-32` (package-prefix sentence + bullet list of field semantics). Field types are exactly as in D-21 — `long` (primitive) for `fileSizeBytes` and `totalImportedRows`; `int` for `schemaVersion`, `currentSchemaVersion`, `uploadFileCount`; primitive `boolean` for `schemaMatches`.

    Create `src/main/java/org/ctc/backup/dto/EntityRowCount.java`:

    Package declaration `org.ctc.backup.dto`. No imports needed (only `String` + `long`). Public record per CONTEXT D-21 verbatim:

    ```
    String tableName,
    String humanLabel,
    long currentRows,
    long importedRows
    ```

    Javadoc above the record: cite Phase 74 + CONTEXT D-21, name this as one card in the `card-grid` of `admin/backup-preview.html`, note that `humanLabel` is precomputed by `BackupImportService.toHumanLabel(tableName)` (Plan 05) — this record carries no derivation logic (per CLAUDE.md "Keep Thymeleaf Templates Lean" + "No Fallback Calculations"). Mirror Javadoc shape of `EntityRef.java:6-15`. Field types: `String` for the two text fields; primitive `long` for the two count fields.

    Both files use a no-blank-line-before-`package` Java-source convention (matches `BackupManifest.java:1` + `EntityRef.java:1`).

    Create `src/test/java/org/ctc/backup/dto/BackupImportPreviewTest.java`:

    Package `org.ctc.backup.dto`. JUnit 5 (`@Test` from `org.junit.jupiter.api.Test`), AssertJ `assertThat`. Test methods follow CLAUDE.md "Test Naming" `givenContext_whenAction_thenExpectedResult` BDD pattern with `// given` / `// when` / `// then` comments:

    - `givenAllFields_whenConstruct_thenAccessorsReturnSameValues()` — build a record instance with concrete values (UUID.randomUUID(), filename `"backup.zip"`, size 12_345_678L, schemaVersion 1, currentSchemaVersion 1, schemaMatches true, a 2-element List.of(EntityRowCount, EntityRowCount), uploadFileCount 5, totalImportedRows 1_000L); assertThat every accessor returns the value passed in.
    - `givenSchemaMismatch_whenConstruct_thenSchemaMatchesIsCallerSupplied()` — pass schemaVersion=999, currentSchemaVersion=1, schemaMatches=false; assertThat `preview.schemaMatches()` is false. Adds a `.as("schemaMatches is stored, not derived — service computes once and passes in")` rationale annotation per CLAUDE.md test-doc convention.
    - `givenTwoRecordsWithIdenticalFields_whenEquals_thenEqual()` — record contract: two preview instances with the same field values are `.equals(...)` and have the same `hashCode()`. Confirms no field is excluded from the canonical record equality.

    Create `src/test/java/org/ctc/backup/dto/EntityRowCountTest.java`:

    Package `org.ctc.backup.dto`. JUnit 5 + AssertJ.

    - `givenAllFields_whenConstruct_thenAccessorsReturnSameValues()` — build `new EntityRowCount("season_phases", "Season Phases", 42L, 50L)`; assertThat all four accessors return the supplied values.
    - `givenZeroCounts_whenConstruct_thenAccessorsReturnZero()` — `new EntityRowCount("races", "Races", 0L, 0L)`; assertThat both counts are 0L (sanity for the D-03 neutral-pill case).
    - `givenTwoRecordsWithIdenticalFields_whenEquals_thenEqual()` — record-contract equality sanity.
  </action>

  <acceptance_criteria>
    - File `src/main/java/org/ctc/backup/dto/BackupImportPreview.java` exists; `grep -E '^public record BackupImportPreview\(' src/main/java/org/ctc/backup/dto/BackupImportPreview.java` returns exactly one match.
    - File `src/main/java/org/ctc/backup/dto/EntityRowCount.java` exists; `grep -E '^public record EntityRowCount\(' src/main/java/org/ctc/backup/dto/EntityRowCount.java` returns exactly one match.
    - Field order in `BackupImportPreview` is byte-identical to CONTEXT D-21: `grep -E 'UUID stagingId|String originalFilename|long fileSizeBytes|int schemaVersion|int currentSchemaVersion|boolean schemaMatches|List<EntityRowCount> entityCounts|int uploadFileCount|long totalImportedRows' src/main/java/org/ctc/backup/dto/BackupImportPreview.java` returns 9 matches in the listed order.
    - Field order in `EntityRowCount`: `grep -E 'String tableName|String humanLabel|long currentRows|long importedRows' src/main/java/org/ctc/backup/dto/EntityRowCount.java` returns 4 matches in the listed order.
    - Neither record uses Lombok: `grep -E '@Getter|@Setter|@Data|@NoArgsConstructor|@AllArgsConstructor|@RequiredArgsConstructor' src/main/java/org/ctc/backup/dto/BackupImportPreview.java src/main/java/org/ctc/backup/dto/EntityRowCount.java` returns zero matches.
    - Neither record uses `@JsonProperty`: `grep -E '@JsonProperty' src/main/java/org/ctc/backup/dto/BackupImportPreview.java src/main/java/org/ctc/backup/dto/EntityRowCount.java` returns zero matches.
    - `./mvnw -pl . -am test -Dtest='BackupImportPreviewTest,EntityRowCountTest' -DfailIfNoTests=true` passes — both test classes run to green with at least 3 + 3 = 6 test methods total.
  </acceptance_criteria>

  <verify>
    <automated>./mvnw -Dtest='BackupImportPreviewTest,EntityRowCountTest' -DfailIfNoTests=true test</automated>
  </verify>

  <done>
    Two new public records exist in `org.ctc.backup.dto` with the CONTEXT D-21 signatures verbatim. Both have green Surefire unit tests covering constructor → accessors, edge values, and record-contract equality. No Lombok, no Jackson annotations, no derivation logic on the records themselves.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Create BackupImportConfirmForm + Validator unit test</name>

  <read_first>
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md (D-10 verbatim: server-side `@NotNull @AssertTrue Boolean acknowledged`; D-21 form DTO mention)
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-RESEARCH.md (search §Pattern 7 `@AssertTrue` + `@NotNull Boolean` — authoritative reasoning: Jakarta Bean Validation §6.1.1 `@AssertTrue` passes on null; §Pitfall 3 same point with the failure-mode narrative)
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-UI-SPEC.md §"Copywriting Contract" (locked checkbox label string + locked checkbox validation-error string for inline rendering — the message attribute on @AssertTrue must equal the locked UI-SPEC string for direct template binding)
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md §"BackupImportConfirmForm" + §"Pattern G — Lombok form DTO shape" (Lombok `@Getter @Setter @NoArgsConstructor` on the class; `private` fields; Boolean wrapper required so `@NotNull` engages)
    - src/main/java/org/ctc/admin/dto/MatchdayForm.java (canonical Lombok form-DTO analog — class shape, annotation set, `@NotNull` on `UUID`, package import order)
    - src/main/java/org/ctc/backup/dto/BackupImportPreview.java (built in Task 1 — confirms `org.ctc.backup.dto` package convention)
  </read_first>

  <behavior>
    BackupImportConfirmForm (Lombok class, NOT record — Spring data-binding needs a setter for `Boolean` checkbox params, and `@Data`/`@Setter` is the established pattern per `MatchdayForm.java`):

    - `@Getter @Setter @NoArgsConstructor` Lombok annotations on the class (NOT `@Data` — D-21 specifies the explicit annotation triplet matching the project's other form DTOs, and `@Data` adds equals/hashCode/toString which form DTOs do not need).
    - Two private fields:
      1. `private UUID stagingId;` — annotated `@NotNull` (the hidden input is required for the controller to know which staged file to execute against; missing → 400 / field-error).
      2. `private Boolean acknowledged;` — annotated `@NotNull` AND `@AssertTrue` with `message = "You must acknowledge the deletion warning to continue."` (the UI-SPEC-locked inline-error string). The default-message form (the spec's bundle-key form `{backup.import.confirm.acknowledged.required}` mentioned in the planner-supplied target signature) is NOT used because the project has no `messages.properties` infrastructure (deferred per CONTEXT "Deferred Ideas" — i18n bundles).

    Validation behavior (Jakarta Bean Validation §6.1.1):
    - `acknowledged == null` → ONE violation reported against `@NotNull` (NOT `@AssertTrue` — bean validation short-circuits null through `@AssertTrue` silently). The test asserts the violation count + the constraint annotation type.
    - `acknowledged == Boolean.FALSE` → ONE violation reported against `@AssertTrue` (the field is non-null but does not pass the `true` predicate). Test asserts the message string equals the locked UI-SPEC string.
    - `acknowledged == Boolean.TRUE` AND `stagingId != null` → zero violations.
    - `stagingId == null` → ONE violation against `@NotNull` (separate from the `acknowledged` chain; the test verifies the field path is exactly `"stagingId"`).

    Boolean wrapper rationale: primitive `boolean` would default to `false` after binding-on-missing-input, so `@NotNull` would never fire and `@AssertTrue` alone would reject the missing-input case — but a primitive value can't be distinguished from a deliberate `false`, which the spec wants to surface separately for the field-error message. Using `Boolean` keeps the null-vs-false distinction (per RESEARCH §Pitfall 3 verbatim).
  </behavior>

  <action>
    Create `src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java`:

    Package declaration `org.ctc.backup.dto`. Imports (alphabetical, two groups separated by a blank line — matches `MatchdayForm.java:3-8` convention):

    Group 1 (`jakarta`):
    - `jakarta.validation.constraints.AssertTrue`
    - `jakarta.validation.constraints.NotNull`

    Group 2 (`lombok`):
    - `lombok.Getter`
    - `lombok.NoArgsConstructor`
    - `lombok.Setter`

    Group 3 (`java`):
    - `java.util.UUID`

    Class declaration:

    ```
    @Getter @Setter @NoArgsConstructor
    public class BackupImportConfirmForm {

        @NotNull
        private UUID stagingId;

        @NotNull
        @AssertTrue(message = "You must acknowledge the deletion warning to continue.")
        private Boolean acknowledged;
    }
    ```

    Javadoc above the class: cite Phase 74 + CONTEXT D-10 + D-21; document that this class is bound by `BackupController.importExecute(@Valid @ModelAttribute("confirmForm") ...)` (Plan 06) and rendered by `admin/backup-confirm.html` (Plan 07); document the `Boolean`-not-`boolean` reasoning with a one-line reference to `RESEARCH §Pitfall 3` and `Jakarta Bean Validation §6.1.1`; note that the `@AssertTrue` message is the UI-SPEC-locked inline-error string. No other class-level annotations.

    Create `src/test/java/org/ctc/backup/dto/BackupImportConfirmFormValidationTest.java`:

    Package `org.ctc.backup.dto`. Imports include `jakarta.validation.Validation`, `jakarta.validation.Validator`, `jakarta.validation.ConstraintViolation`, `jakarta.validation.constraints.AssertTrue`, `jakarta.validation.constraints.NotNull`, `java.util.Set`, `java.util.UUID`, `org.junit.jupiter.api.BeforeAll`, `org.junit.jupiter.api.Test`, AssertJ `assertThat`.

    Class header:
    ```
    class BackupImportConfirmFormValidationTest {

        private static Validator validator;

        @BeforeAll
        static void setupValidator() {
            validator = Validation.buildDefaultValidatorFactory().getValidator();
        }
        // tests below
    }
    ```

    Test methods (BDD-named per CLAUDE.md):

    1. `givenAcknowledgedNull_whenValidate_thenViolatesNotNull()`
       - `// given` build a form with `setStagingId(UUID.randomUUID())` and leave `acknowledged` null.
       - `// when` `Set<ConstraintViolation<BackupImportConfirmForm>> violations = validator.validate(form);`
       - `// then` filter violations to property path `acknowledged`; assertThat exactly one violation; assertThat its `getConstraintDescriptor().getAnnotation().annotationType().equals(NotNull.class)`. Add `.as("Jakarta §6.1.1 — @AssertTrue passes on null; only @NotNull fires here")`.

    2. `givenAcknowledgedFalse_whenValidate_thenViolatesAssertTrue()`
       - `// given` form with `acknowledged = Boolean.FALSE`, `stagingId` non-null.
       - `// when` validate.
       - `// then` filter to `acknowledged` path; assertThat exactly one violation; assertThat annotation type is `AssertTrue.class`; assertThat message equals the locked UI-SPEC string `"You must acknowledge the deletion warning to continue."`. Add `.as("UI-SPEC §Copywriting Contract locks this string verbatim")`.

    3. `givenAcknowledgedTrue_whenValidate_thenNoViolations()`
       - `// given` form with both `stagingId = UUID.randomUUID()` and `acknowledged = Boolean.TRUE`.
       - `// when` validate.
       - `// then` `assertThat(violations).isEmpty()`.

    4. `givenStagingIdNull_whenValidate_thenViolatesNotNullOnStagingId()`
       - `// given` form with `acknowledged = Boolean.TRUE`, `stagingId` null.
       - `// when` validate.
       - `// then` filter to property path `stagingId`; assertThat exactly one `NotNull` violation. Add `.as("the hidden input on the confirm form is required — the controller cannot resolve the staged file without it")`.

    Helper extraction permissible: a small private `Set<ConstraintViolation<BackupImportConfirmForm>> violationsFor(String path, Set<ConstraintViolation<BackupImportConfirmForm>> all)` keeps the four test methods skinny.

    The test class does NOT use `@SpringBootTest` — it builds the validator from `Validation.buildDefaultValidatorFactory()` directly. This keeps it Surefire-fast (no Spring context boot). The full Spring-binding test with `BindingResult` re-render + `@AssertTrue`-triggers-`field-error`-in-Thymeleaf is `BackupImportConfirmFormValidationIT` in Plan 08 (Failsafe IT under `@SpringBootTest`).
  </action>

  <acceptance_criteria>
    - File `src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java` exists; `grep -E '^public class BackupImportConfirmForm' src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java` returns exactly one match.
    - Class is annotated with `@Getter @Setter @NoArgsConstructor`: `grep -v '^\*' src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java | grep -E '@Getter|@Setter|@NoArgsConstructor'` returns three matches (excluding Javadoc lines).
    - Class is NOT a record: `grep -E '^public record BackupImportConfirmForm' src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java` returns zero matches.
    - `acknowledged` field is `Boolean` (NOT primitive `boolean`): `grep -E 'private Boolean acknowledged' src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java` returns one match; `grep -E 'private boolean acknowledged' src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java` returns zero matches.
    - Both `@NotNull` and `@AssertTrue` are present on `acknowledged`; the `@AssertTrue` carries the locked UI-SPEC message: `grep -E '@AssertTrue\(message = "You must acknowledge the deletion warning to continue."\)' src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java` returns one match.
    - `stagingId` is `UUID` and annotated `@NotNull`: `grep -E 'private UUID stagingId' src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java` returns one match.
    - No `humanLabel` derivation logic, no static helpers, no methods other than what Lombok generates: `grep -v '^\*' src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java | grep -E '(public|private|protected) (static|final).*\(' ` returns zero matches.
    - `./mvnw -Dtest='BackupImportConfirmFormValidationTest' -DfailIfNoTests=true test` passes — all 4 test methods green.
    - Combined run also green: `./mvnw -Dtest='BackupImportPreviewTest,EntityRowCountTest,BackupImportConfirmFormValidationTest' -DfailIfNoTests=true test` succeeds with 10 test methods executed.
  </acceptance_criteria>

  <verify>
    <automated>./mvnw -Dtest='BackupImportPreviewTest,EntityRowCountTest,BackupImportConfirmFormValidationTest' -DfailIfNoTests=true test</automated>
  </verify>

  <done>
    `BackupImportConfirmForm` exists as a Lombok form class in `org.ctc.backup.dto` with `@NotNull` on `stagingId`, `@NotNull` + `@AssertTrue` on `Boolean acknowledged`, and the locked UI-SPEC message string on `@AssertTrue`. Surefire unit test `BackupImportConfirmFormValidationTest` proves the four Jakarta-validation states (null / false / true on `acknowledged`, null on `stagingId`) all behave per CONTEXT D-10 + RESEARCH §6.1.1 — green build.
  </done>
</task>

## Verification

must_haves:
  truths:
    - "BackupImportPreview record exists in org.ctc.backup.dto with the exact 9-field signature from CONTEXT D-21"
    - "EntityRowCount record exists in org.ctc.backup.dto with the exact 4-field signature from CONTEXT D-21"
    - "BackupImportConfirmForm Lombok class exists in org.ctc.backup.dto with @NotNull stagingId + @NotNull @AssertTrue Boolean acknowledged"
    - "@AssertTrue carries the UI-SPEC-locked message 'You must acknowledge the deletion warning to continue.'"
    - "Jakarta validation distinguishes the three states of acknowledged: null → @NotNull violation, false → @AssertTrue violation, true → no violation"
    - "All three Surefire unit tests run green via `./mvnw test`"
  artifacts:
    - path: "src/main/java/org/ctc/backup/dto/BackupImportPreview.java"
      provides: "Read-only view-model for admin/backup-preview.html"
      contains: "public record BackupImportPreview("
    - path: "src/main/java/org/ctc/backup/dto/EntityRowCount.java"
      provides: "One card in the 24-card preview grid"
      contains: "public record EntityRowCount("
    - path: "src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java"
      provides: "@Valid + BindingResult form DTO for the confirm POST"
      contains: "public class BackupImportConfirmForm"
    - path: "src/test/java/org/ctc/backup/dto/BackupImportPreviewTest.java"
      provides: "Record constructor + accessor + equals sanity"
      contains: "givenAllFields_whenConstruct_thenAccessorsReturnSameValues"
    - path: "src/test/java/org/ctc/backup/dto/EntityRowCountTest.java"
      provides: "Record constructor + accessor + equals sanity"
      contains: "givenAllFields_whenConstruct_thenAccessorsReturnSameValues"
    - path: "src/test/java/org/ctc/backup/dto/BackupImportConfirmFormValidationTest.java"
      provides: "Validator matrix proving null / false / true semantics"
      contains: "givenAcknowledgedNull_whenValidate_thenViolatesNotNull"
  key_links:
    - from: "src/main/java/org/ctc/backup/dto/BackupImportPreview.java"
      to: "src/main/java/org/ctc/backup/dto/EntityRowCount.java"
      via: "List<EntityRowCount> entityCounts field"
      pattern: "List<EntityRowCount>"
    - from: "src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java"
      to: "jakarta.validation.constraints.AssertTrue"
      via: "@AssertTrue on Boolean acknowledged with locked message"
      pattern: '@AssertTrue\(message = "You must acknowledge'
    - from: "src/test/java/org/ctc/backup/dto/BackupImportConfirmFormValidationTest.java"
      to: "jakarta.validation.Validation"
      via: "Validation.buildDefaultValidatorFactory().getValidator() in @BeforeAll"
      pattern: "Validation\\.buildDefaultValidatorFactory"

## Notes

**Why records for the two view-model types but Lombok class for the form DTO** — CONTEXT D-21 is explicit: the preview DTOs are immutable read-only carriers (record fits perfectly), but Spring's `WebDataBinder` needs a public no-arg constructor + setters to populate from form parameters (the Lombok `@Getter @Setter @NoArgsConstructor` triplet is the established CTC pattern, see `MatchdayForm.java`). A record cannot be data-bound this way (records have no setters, the canonical constructor requires all args at once). Choosing record-vs-class per CONTEXT D-21's verbatim instruction is non-negotiable.

**Why `humanLabel` lives on the DTO but is filled by the service** — `humanLabel` is precomputed state, not logic (per CLAUDE.md "Keep Thymeleaf Templates Lean" + "No Fallback Calculations"). The mapping function `tableName → humanLabel` (e.g. `"season_phases"` → `"Season Phases"`) lives on `BackupImportService.toHumanLabel(String)` in Plan 05 per CONTEXT "Claude's Discretion" + the planning-context Notes block. This plan ships only the data carrier; the helper is Plan 05's concern. Keeping the DTOs pure means Plan 05 can iterate on the label-mapping strategy (static map vs. `tableName.replace('_', ' ')` + title-case) without touching this plan's files.

**Why no `messages.properties` bundle key on `@AssertTrue`** — CONTEXT "Deferred Ideas" defers i18n explicitly. The planner-supplied target signature suggested `{backup.import.confirm.acknowledged.required}` as a bundle key, but the inline-default-message form (the locked UI-SPEC string) is what ships in Phase 74. If/when v1.11+ adds i18n infrastructure, the message attribute swaps to a bundle key in a one-line change — the test would also pivot to asserting the key resolves through the project's `MessageSource`. For Phase 74 the literal string is correct.

**Why `schemaMatches` is stored, not derived** — CONTEXT D-21's record signature lists `schemaMatches` as a positional field of the canonical constructor. If derivation were required, D-21 would have shown a compact constructor (`public BackupImportPreview { schemaMatches = (schemaVersion == currentSchemaVersion); }`). It does not. The service computes the comparison once and passes the result in. This also keeps the record's `equals`/`hashCode` clean — two previews with the same field tuple are equal, period.

**Why `BackupImportConfirmFormValidationIT` is deferred to Plan 08** — that IT requires the full Spring context (`@SpringBootTest` + `MockMvc` + `BindingResult` re-render through `admin/backup-confirm.html`), which is Failsafe-scoped (slower, integration profile). Plan 08 owns the IT suite. The Plan 03 unit test uses the bare `jakarta.validation.Validator` API to verify the annotations are wired correctly — that is sufficient evidence the DTOs are correct in isolation; the Spring-binding chain is a Plan 06/Plan 08 concern.

**Why a separate Lombok `@Data` annotation is not used** — `@Data` bundles `@Getter @Setter @ToString @EqualsAndHashCode @RequiredArgsConstructor`, but form DTOs in this codebase use the narrower `@Getter @Setter @NoArgsConstructor` triplet (see `MatchdayForm.java`, `SeasonPhaseForm.java`, `DriverForm.java`). Following the project pattern keeps PR review fast and matches Spring's data-binding expectations exactly.

## PLAN COMPLETE 03
