---
id: "07"
title: "BackupStagingCleanup — startup sweep listener"
wave: 2
depends_on: ["01"]
requirements: []
files_modified:
  - src/main/java/org/ctc/backup/service/BackupStagingCleanup.java
  - src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java
autonomous: true
---

## Objective

Implement the **startup-sweep listener** locked in CONTEXT D-17: a Spring `@Component` named `BackupStagingCleanup` that listens to `ApplicationReadyEvent` and deletes every file matching `upload-*.zip` from the configured staging directory (`app.backup.staging-dir`, written into `application.yml` by Plan 01). This is the operational safety net for the stateless staging-file pattern (D-15 / D-18): if a previous JVM died between `BackupImportService.stage()` and the user clicking Cancel/Confirm — or if a hard kill (`SIGKILL`, container restart, OOM) skipped the per-request `Files.delete` in `BackupController` — those orphaned ZIPs are reaped at the next boot. The component runs **once per JVM startup**, after the embedded web server is ready (so file-system access is safe), emits a single `INFO` log line `Cleared {N} stale staging files` (the operational signal an admin needs to confirm the sweep ran), and degrades to a no-op when the staging dir does not yet exist (first-ever boot before any import has been attempted — `BackupImportService.stage()` will `Files.createDirectories(...)` on demand per D-15). Phase 74 ships only the startup sweep + reject-delete; a `@Scheduled` periodic sweep is explicitly deferred to v1.11 (D-17 rationale: admin uploads 1–2 per week, scheduled cleanup is over-engineered for this cadence). Plan 07 is intentionally a **single-component, no-controller** plan: it owns one production class plus one integration test, and depends on Plan 01 solely for the `app.backup.staging-dir` YAML key.

## Tasks

<task id="74-07-01">
  <title>Create `BackupStagingCleanup` `@Component` with `@EventListener(ApplicationReadyEvent.class)` sweep over the staging directory</title>

  <action>
  Create a new file `src/main/java/org/ctc/backup/service/BackupStagingCleanup.java` in the existing `org.ctc.backup.service` package (sibling to `BackupArchiveService.java`, `BackupExportService.java`). The class must follow these exact decisions:

  1. **Class header (annotations, in this order, top-to-bottom):** `@Component`, `@Slf4j`, `@RequiredArgsConstructor`. Use Lombok constructor injection — no explicit constructor body is needed because the only injected dependency is configuration via `@Value`, not a bean. Match the pattern in `org/ctc/admin/DevDataSeeder.java` (only existing startup-lifecycle bean in the codebase): `@Slf4j` + `@Component` + `@RequiredArgsConstructor`, no `@Profile` annotation (the sweep runs in every profile — `dev`, `local`, `docker`, `prod` — because every profile resolves `app.backup.staging-dir` via the YAML default added in Plan 01).

  2. **Package declaration:** `package org.ctc.backup.service;`. Class is package-public (no `public` modifier on the class). Spring component-scanning picks it up via the `@SpringBootApplication` scan root at `org.ctc`.

  3. **Field — single configuration injection:** Declare a `private final Path stagingDir;` field annotated with `@Value("${app.backup.staging-dir}")` on the constructor parameter (Lombok-generated). Because `@Value` does not accept a default expression on the field with `@RequiredArgsConstructor`, do NOT rely on a default expression here — Plan 01 has already added the key to `application.yml` with the literal value `data/${spring.profiles.active}/backup-staging`, so the bean factory will resolve a concrete `Path` at startup. Spring auto-converts the resolved string to `java.nio.file.Path` via its default `StringToPathConverter`. Do NOT use `String` and `Paths.get(...)` inside the method — using `Path` directly removes the conversion code at the call site.

     Concrete field declaration (place inside the class body, above the listener method):

     ```
     @Value("${app.backup.staging-dir}")
     private final Path stagingDir;
     ```

     The `@Value` annotation goes on the field (Lombok will lift it onto the generated constructor parameter — verified in `BackupArchiveService.java:64` for an identical `@Value` constructor-injection pattern that uses `@Qualifier`).

  4. **Listener method — exact signature:**

     ```
     @EventListener(ApplicationReadyEvent.class)
     void sweepStagingDir() { ... }
     ```

     Package-private (no `public` modifier — Spring's `@EventListener` registration works on package-private methods, and CTC convention keeps method visibility tight where no external caller exists). Return type `void`. No parameters (we do not need the `ApplicationReadyEvent` payload). Method name `sweepStagingDir` — descriptive of the single operation, matches the operational vocabulary used in CONTEXT D-17 ("startup-sweep").

  5. **Method body — exact control flow:**

     a. **Early-return when the directory does not exist.** Use `Files.isDirectory(stagingDir)` (NOT `Files.exists` — `exists` would return `true` for a regular file named `backup-staging`, which would then blow up in `Files.list`). On the early-return branch: do NOT log anything at INFO. Per D-17 the operational signal is `Cleared {N} stale staging files`; emitting that line at first boot when the dir does not exist yet would be misleading (`Cleared 0` when there was nothing to clear because the dir was absent). Emit no log at all on this branch (the cleanup is genuinely a no-op).

     b. **List the directory inside a try-with-resources block.** `Files.list(stagingDir)` returns a `Stream<Path>` that wraps an open `DirectoryStream`; it MUST be closed. Use `try (Stream<Path> stream = Files.list(stagingDir)) { ... }`. Failure to use try-with-resources here leaks an OS file handle on every startup — non-negotiable.

     c. **Filter predicate — exact regex / string match:** Filter for entries whose file name (`p.getFileName().toString()`) BOTH starts with `upload-` AND ends with `.zip`. Use two `.startsWith` / `.endsWith` checks chained in a `Predicate<Path>` — NOT a regex (no `.matches("upload-.*\\.zip")` — regex compilation is allocation-heavy and unnecessary for two literal anchors). Concretely:

        ```
        p -> p.getFileName().toString().startsWith("upload-")
          && p.getFileName().toString().endsWith(".zip")
        ```

        This pattern is the inverse of `BackupImportService.stage()`'s file-naming convention (Plan 05): staged uploads are written as `upload-{uuid}.zip`. Any file in the staging dir NOT matching this pattern (e.g. a user-dropped `notes.txt`, a `.gitkeep`, a forgotten test fixture) is left untouched — the sweep owns only its own naming convention.

     d. **Deletion with per-file error containment.** Map each surviving `Path` through a helper `private int deleteOrLog(Path p)` that attempts `Files.delete(p)` inside a try/catch. Return `1` on successful delete, `0` if `Files.delete` throws `IOException`. The catch logs at WARN level with the parameterized message `"Failed to delete stale staging file {}: {}"` and the two args `(p, e.getMessage())` — NOT `e` as the third arg (do not log the full stack trace for a routine file-system permission failure; the message is enough operational signal, and a full stack trace pollutes the boot log on every startup if the issue persists).

        Aggregate the per-file `int` return values with `mapToInt(this::deleteOrLog).sum()` to obtain the total deleted count.

     e. **Single INFO log line on success.** After the stream terminal operation, log exactly:

        ```
        log.info("Cleared {} stale staging files", deleted);
        ```

        Use the parameterized `{}` placeholder (CONVENTIONS.md L57-67 + CLAUDE.md "Logging" section both mandate parameterized SLF4J). The literal English string is part of CONTEXT D-17 (line 70 of `74-CONTEXT.md`) — this exact phrasing is what the IT in Task 74-07-02 asserts against.

     f. **Outer catch for `IOException` from `Files.list`.** Wrap the try-with-resources in an outer `try { ... } catch (IOException e) { ... }` that logs at WARN: `"Failed to sweep staging directory {}: {}"` with args `(stagingDir, e.getMessage())`. This branch fires if the directory exists but is not readable (permission denied, file-system unmount mid-boot, etc.) — extremely rare, but the sweep must not abort startup. The method swallows the exception (no rethrow) — D-17 makes the sweep advisory, not critical: app startup proceeds either way.

  6. **No `@Scheduled` annotation, no other lifecycle hooks.** Do NOT add `@PostConstruct`, do NOT add `@Scheduled(fixedDelay=...)`, do NOT implement `CommandLineRunner` or `ApplicationRunner`. The single `@EventListener(ApplicationReadyEvent.class)` is the only entry point. (RESEARCH §"D-17" / §"Pattern 8" verified `ApplicationReadyEvent` fires AFTER `@PostConstruct`, AFTER `ContextRefreshedEvent`, and AFTER the embedded web server reports ready — this is the canonical Spring Boot signal for safe file-system bootstrap work.)

  7. **No threading, no `@Async`.** The sweep runs synchronously on the Spring event-dispatch thread that publishes `ApplicationReadyEvent` (the main bootstrap thread). With ≤ a handful of files in the staging dir (D-17 expects 1–2 uploads per week), the operation is sub-millisecond and does not measurably delay startup. Avoid `@Async` complexity.

  8. **Imports (exact list, sorted, no wildcards):**

     ```
     import java.io.IOException;
     import java.nio.file.Files;
     import java.nio.file.Path;
     import java.util.stream.Stream;

     import org.springframework.beans.factory.annotation.Value;
     import org.springframework.boot.context.event.ApplicationReadyEvent;
     import org.springframework.context.event.EventListener;
     import org.springframework.stereotype.Component;

     import lombok.RequiredArgsConstructor;
     import lombok.extern.slf4j.Slf4j;
     ```

     Match the project's standard import ordering: `java.*` first, then `org.springframework.*`, then `lombok.*` last. Verified in `BackupArchiveService.java`.

  9. **Javadoc on the class.** Add a brief class-level Javadoc summarising the role and citing D-17:

     ```
     /**
      * Startup safety net for the stateless backup-import staging-file pattern (D-15 / D-18).
      *
      * <p>On {@link ApplicationReadyEvent}, walks {@code app.backup.staging-dir} and deletes
      * every {@code upload-*.zip} file. Reaps orphans left by a previous JVM that died
      * between {@code BackupImportService.stage()} and the per-request cleanup in
      * {@code BackupController} — see CONTEXT D-17 (Phase 74).
      *
      * <p>A scheduled periodic sweep is deferred to v1.11 — startup-sweep plus the
      * per-request reject-delete is enough for the 1–2-uploads-per-week cadence.
      */
     ```

  10. **Do NOT modify `BackupImportService`, `BackupController`, `application.yml`, or any other file.** Plan 01 already wrote the staging-dir YAML key; Plan 05 will create `BackupImportService`. This task ships exactly one new file: `BackupStagingCleanup.java`. The Wave-2 ordering guarantees Plan 01 lands before this task runs.
  </action>

  <read_first>
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md` — D-17 verbatim (line 70: startup-sweep mandate, `Cleared {N} stale staging files` log format, no `@Scheduled` clause); D-15 (line 67-68: `app.backup.staging-dir` default and on-demand directory creation); D-18 (line 71: stateless re-parse rationale that motivates the sweep). **Authoritative** for naming, log strings, and scope boundaries.
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-RESEARCH.md` — §"Pattern 8: `ApplicationReadyEvent` Listener for Startup Cleanup" (lines 606-650): full implementation skeleton, event-ordering rationale (fires AFTER `@PostConstruct`, AFTER web server ready), confidence HIGH. §"D-17" alternatives-rejected table (line 174) confirms `@Scheduled` was explicitly considered and rejected for Phase 74.
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md` — row for `BackupStagingCleanup` (lines 14): only startup-runner analog in the repo is `DevDataSeeder` (different event — `CommandLineRunner` — but same `@Component + @Slf4j + @RequiredArgsConstructor` shape).
    - `src/main/java/org/ctc/admin/DevDataSeeder.java` — exact shape analog for `@Component` + `@Slf4j` + `@RequiredArgsConstructor` lifecycle bean (pattern-only; uses `CommandLineRunner.run`, not `@EventListener`). Mimic the annotation stacking and logging style.
    - `src/main/java/org/ctc/backup/service/BackupArchiveService.java` — sibling service in the same `org.ctc.backup.service` package; verify package layout, import-ordering convention, and the existing `@Slf4j` + parameterized-`{}` info-logging idiom (lines 94-95, 135-136). NOTE: `BackupArchiveService` uses an explicit constructor for `@Qualifier`; this plan uses `@RequiredArgsConstructor` + `@Value` (no qualifier needed).
    - `src/main/resources/application.yml` — verify that Plan 01 has already added `app.backup.staging-dir: data/${spring.profiles.active}/backup-staging` (the key this component reads). If it is missing, this task cannot proceed — the Wave-2 ordering guarantees it is present.
  </read_first>

  <acceptance_criteria>
    1. File `src/main/java/org/ctc/backup/service/BackupStagingCleanup.java` exists and compiles under `./mvnw -q compile`.
    2. The class is annotated, in this exact set: `@Component`, `@Slf4j`, `@RequiredArgsConstructor`. No `@Profile`, no `@Service`, no `@Transactional`.
    3. The class declares exactly ONE field: `private final Path stagingDir` annotated with `@Value("${app.backup.staging-dir}")`. The field type is `java.nio.file.Path` (not `String`).
    4. The class declares exactly ONE public/package-private listener method named `sweepStagingDir`, annotated with `@EventListener(ApplicationReadyEvent.class)`, returning `void`, taking no parameters.
    5. The class may declare ONE additional `private` helper method named `deleteOrLog(Path p)` returning `int` (returns `1` on successful delete, `0` on `IOException`). Anything beyond these two methods is not part of the locked design.
    6. The listener method body emits exactly ONE of these two outcomes on a normal startup:
       (a) When `Files.isDirectory(stagingDir)` is `false`: returns silently (no INFO log at all on this branch).
       (b) When the directory exists and is readable: logs exactly `log.info("Cleared {} stale staging files", deleted);` once, where `deleted` is the count of files matching `upload-*.zip` that were successfully deleted.
    7. The filter predicate matches ONLY files whose name both starts with the literal prefix `upload-` AND ends with the literal suffix `.zip`. Files such as `keepme.txt`, `.gitkeep`, or any other name are NOT deleted.
    8. Per-file delete failures are caught individually inside `deleteOrLog`, logged at WARN with the parameterized message `"Failed to delete stale staging file {}: {}"` and do NOT propagate up — one unreadable file does not abort the sweep.
    9. An outer `IOException` from `Files.list(stagingDir)` is caught at the method-body level, logged at WARN with `"Failed to sweep staging directory {}: {}"`, and is NOT rethrown — the sweep is advisory and must not abort app startup.
    10. The `Files.list(stagingDir)` call is wrapped in try-with-resources (no leaked `DirectoryStream` file handle).
    11. The class contains NO `@Scheduled`, NO `@PostConstruct`, NO `@Async`, NO `CommandLineRunner`/`ApplicationRunner` implementation. The single `@EventListener` is the only lifecycle hook.
    12. No other file is modified by this task (verifiable by `git diff --name-only HEAD~1` showing only the new file).
  </acceptance_criteria>

  <verify>
    <automated>./mvnw -q -Dtest=NONE compile</automated>
  </verify>

  <done>The new `BackupStagingCleanup` class exists in `org.ctc.backup.service`, compiles cleanly, and follows D-17 verbatim — single `@EventListener(ApplicationReadyEvent.class)` method, `upload-*.zip` filter, `Cleared {N} stale staging files` INFO log on success, silent no-op when the directory does not exist, WARN-and-continue on any `IOException`. No other file is touched.</done>
</task>

<task id="74-07-02">
  <title>Add `BackupStagingCleanupIT` integration test covering the four startup-sweep scenarios from D-17 / CONTEXT</title>

  <action>
  Create a new file `src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java`. This is a **Failsafe** integration test — naming suffix `IT` makes Maven Failsafe execute it during `./mvnw verify`. Do NOT name it `*Test` (that would route to Surefire and run during unit-test phase before the Spring context is ready). Match the suffix convention used in sibling tests under `src/test/java/org/ctc/backup/` (e.g. `BackupControllerIT`, `BackupRepositoryEntityGraphIT`).

  Test class structure:

  1. **Annotations (top of class, in this order):** `@SpringBootTest`, `@ActiveProfiles("dev")`. Do NOT add `@AutoConfigureMockMvc` (this test does not use `MockMvc`). Do NOT use `@DataJpaTest` (we need the full context so the `BackupStagingCleanup` bean is created and its event listener is registered with the application context). Do NOT add `@Transactional` (file-system effects must be visible across the test method boundary).

  2. **Spring Boot's `OutputCaptureExtension`.** Add `@ExtendWith(OutputCaptureExtension.class)` from the package `org.springframework.boot.test.system`. Each test method then accepts a `CapturedOutput output` parameter (also from `org.springframework.boot.test.system`) and asserts against `output.getOut()` / `output.getAll()`. Verified available in Spring Boot 4.x (the project depends on `spring-boot-test` transitively via `spring-boot-starter-test`). NOTE: This pattern has no existing usage in the CTC codebase — this IT introduces it. Document at the top of the file with a short Javadoc explaining the choice.

  3. **Staging-dir override via `@DynamicPropertySource`.** Use a `static @TempDir Path tempStagingDir` field plus a `@DynamicPropertySource` method that registers `app.backup.staging-dir` to `tempStagingDir.toString()`. JUnit 5's `@TempDir` on a `static` field is initialised BEFORE Spring's `DynamicPropertyRegistry` callback runs — verified standard pattern. Concrete declaration:

     ```
     @TempDir
     static Path tempStagingDir;

     @DynamicPropertySource
     static void overrideStagingDir(DynamicPropertyRegistry registry) {
         registry.add("app.backup.staging-dir", () -> tempStagingDir.toString());
     }
     ```

     This guarantees the Spring context boots with the temp dir as the staging dir — completely isolating the test from `data/dev/backup-staging`.

  4. **`@Autowired ApplicationContext context;`** — to programmatically re-publish `ApplicationReadyEvent` from within each test method. This is the documented mechanism for re-triggering the listener under test (the listener fires once during context bootstrap, BEFORE the test's `@BeforeEach` runs, so each test must re-publish the event AFTER seeding fixture files).

  5. **`@Autowired BackupStagingCleanup cleanup;`** — kept as a sanity reference so a missing-bean misconfiguration surfaces as a clear Spring wiring error rather than a silent zero-deletion.

  6. **`@BeforeEach` does not seed files** — each test method seeds its own fixture and triggers the event itself, because the test scenarios diverge (some seed files, some don't, some seed unrelated files). Each `@Test` method ends by calling `context.publishEvent(new ApplicationReadyEvent(...))` to re-fire the listener. Use the four-arg constructor:

     ```
     context.publishEvent(new ApplicationReadyEvent(
         (SpringApplication) null, new String[]{}, (ConfigurableApplicationContext) context, java.time.Duration.ZERO));
     ```

     The `SpringApplication` arg may be `null` (the listener does not use it); the `ConfigurableApplicationContext` cast is the established re-publish idiom (`ApplicationReadyEvent` source field). If Spring 4.x's `ApplicationReadyEvent` ctor signature differs, fall back to `context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], (ConfigurableApplicationContext) context, java.time.Duration.ZERO))` — verify against the Spring Boot 4.x Javadoc.

  7. **Four test methods, BDD `givenX_whenY_thenZ` naming** (per `CLAUDE.md` Test Naming section):

     **Test 1 — three stale files all deleted, log line emitted:**
     ```
     givenThreeStaleStagingFiles_whenApplicationReady_thenAllDeletedAndCountLogged(CapturedOutput output)
     ```
     - `// given`: write 3 dummy files named `upload-{uuid1}.zip`, `upload-{uuid2}.zip`, `upload-{uuid3}.zip` into `tempStagingDir` using `Files.write(path, new byte[]{ 0x50, 0x4B, 0x05, 0x06 })` (4-byte minimal-ZIP EOCD signature so the file content is not totally empty — though the sweep does not read content, only names). Use `java.util.UUID.randomUUID()` to generate the suffixes to prove the filter handles arbitrary UUIDs.
     - `// when`: clear `output` if needed (the bootstrap-time sweep already logged once during context startup against an empty dir — `output.getAll()` captures EVERYTHING since context start). Use `output.toString()` snapshots before/after, or check that the test's expected log line appears the expected number of times. Re-publish `ApplicationReadyEvent` as described above.
     - `// then`:
       - `assertThat(Files.list(tempStagingDir).count()).isZero()` — all three files gone.
       - `assertThat(output.getAll()).contains("Cleared 3 stale staging files")` — exact log line.

     **Test 2 — empty staging dir, zero-count log line:**
     ```
     givenEmptyStagingDir_whenApplicationReady_thenLogsZeroCleared(CapturedOutput output)
     ```
     - `// given`: do nothing (the `@TempDir` is empty by default).
     - `// when`: re-publish `ApplicationReadyEvent`.
     - `// then`: `assertThat(output.getAll()).contains("Cleared 0 stale staging files")`. (Pins the contract that an empty dir still emits the operational signal — the admin sees the sweep ran.)

     **Test 3 — staging dir does not exist, silent no-op:**
     ```
     givenStagingDirDoesNotExist_whenApplicationReady_thenNoCleanupLogEmitted(CapturedOutput output)
     ```
     - `// given`: `Files.deleteIfExists(tempStagingDir)` to remove the temp dir before re-firing. (Note: `@TempDir` creates the dir before injection, so we explicitly delete it here.) Assert with `assertThat(Files.isDirectory(tempStagingDir)).isFalse()` as a precondition guard so the test fails clearly if the delete didn't take.
     - `// when`: capture the `output.getAll()` length BEFORE the re-publish (`int outputLengthBefore = output.getAll().length();`) — this is the baseline. Then re-publish the event.
     - `// then`: assert that the substring `"Cleared "` does NOT appear in `output.getAll().substring(outputLengthBefore)` (the slice of output AFTER the re-publish). This pins the early-return branch: when the dir doesn't exist, NO `Cleared` log line is emitted on the new event.

     **Test 4 — unrelated file survives, matching file deleted:**
     ```
     givenUnrelatedFileAndOneStaleStagingFile_whenApplicationReady_thenOnlyStagingFileDeleted(CapturedOutput output)
     ```
     - `// given`: write `tempStagingDir.resolve("keepme.txt")` with content `"do not delete"` (any non-empty bytes); write `tempStagingDir.resolve("upload-" + UUID.randomUUID() + ".zip")` with the 4-byte ZIP EOCD signature.
     - `// when`: re-publish `ApplicationReadyEvent`.
     - `// then`:
       - `assertThat(Files.exists(tempStagingDir.resolve("keepme.txt"))).isTrue()` — non-matching file untouched.
       - Assert the list of `upload-*.zip` files in the dir is empty: count via `Files.list(tempStagingDir).filter(p -> p.getFileName().toString().startsWith("upload-")).count()` equals `0`.
       - `assertThat(output.getAll()).contains("Cleared 1 stale staging files")` — exact count.

  8. **Imports (sorted):**

     ```
     import java.io.IOException;
     import java.nio.file.Files;
     import java.nio.file.Path;
     import java.time.Duration;
     import java.util.UUID;
     import java.util.stream.Stream;

     import org.junit.jupiter.api.Test;
     import org.junit.jupiter.api.extension.ExtendWith;
     import org.junit.jupiter.api.io.TempDir;
     import org.springframework.beans.factory.annotation.Autowired;
     import org.springframework.boot.SpringApplication;
     import org.springframework.boot.context.event.ApplicationReadyEvent;
     import org.springframework.boot.test.context.SpringBootTest;
     import org.springframework.boot.test.system.CapturedOutput;
     import org.springframework.boot.test.system.OutputCaptureExtension;
     import org.springframework.context.ApplicationContext;
     import org.springframework.context.ConfigurableApplicationContext;
     import org.springframework.test.context.ActiveProfiles;
     import org.springframework.test.context.DynamicPropertyRegistry;
     import org.springframework.test.context.DynamicPropertySource;

     import static org.assertj.core.api.Assertions.assertThat;
     ```

  9. **Class-level Javadoc** explaining the test surface: cite D-17 by name, list the four scenarios, note that `OutputCaptureExtension` is the chosen mechanism because no existing CTC test asserts log output.

  10. **No `@DirtiesContext`.** Re-publishing `ApplicationReadyEvent` is idempotent — the listener has no in-memory state to clean up between tests. Avoid `@DirtiesContext` because it forces a full context restart between tests (≈ +5 s per test) and is unnecessary.
  </action>

  <read_first>
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md` — D-17 verbatim (line 70) — the log string and "all `upload-*.zip` files deleted" semantics are the test's pinned contract; the validation entry at line 113 names this IT and its four-scenario coverage.
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-VALIDATION.md` — `BackupStagingCleanupIT` row (line 50): "3 stale `upload-*.zip` fixtures → `ApplicationReadyEvent` → all deleted + 1 info-log".
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-RESEARCH.md` — §"Pattern 8" (lines 606-650): event-listener semantics; §"Phase-74 focused IT run" (line 1107): the exact `./mvnw failsafe:integration-test ... -Dit.test=...,BackupStagingCleanupIT,...` invocation the developer will use locally.
    - `src/test/java/org/ctc/backup/BackupControllerIT.java` — `@SpringBootTest` + `@ActiveProfiles("dev")` shape; verify Spring Boot 4.x package paths for `SpringBootTest`, `ActiveProfiles`.
    - `src/test/java/org/ctc/backup/serialization/BackupEntityAnnotationCleanlinessIT.java` — additional `@SpringBootTest` IT shape in the `backup` test tree.
    - `src/main/java/org/ctc/backup/service/BackupStagingCleanup.java` — the class under test, written in Task 74-07-01.
    - `CLAUDE.md` — Test Naming section (`givenX_whenY_thenZ()` BDD convention) and `// given` / `// when` / `// then` comment structure.
  </read_first>

  <acceptance_criteria>
    1. File `src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java` exists and compiles under `./mvnw -q test-compile`.
    2. Class name ends with `IT` (Failsafe-routed); class is annotated with `@SpringBootTest` + `@ActiveProfiles("dev")` + `@ExtendWith(OutputCaptureExtension.class)`.
    3. A `static @TempDir Path tempStagingDir` field is declared at class level.
    4. A `@DynamicPropertySource` static method registers `app.backup.staging-dir` to `tempStagingDir.toString()`.
    5. Exactly FOUR `@Test` methods exist with the following names: `givenThreeStaleStagingFiles_whenApplicationReady_thenAllDeletedAndCountLogged`, `givenEmptyStagingDir_whenApplicationReady_thenLogsZeroCleared`, `givenStagingDirDoesNotExist_whenApplicationReady_thenNoCleanupLogEmitted`, `givenUnrelatedFileAndOneStaleStagingFile_whenApplicationReady_thenOnlyStagingFileDeleted`.
    6. Each test method has a `CapturedOutput output` parameter, re-publishes `ApplicationReadyEvent` via `ApplicationContext.publishEvent(...)`, and asserts against `output.getAll()` for the relevant log line.
    7. Test 1 asserts the exact substring `Cleared 3 stale staging files` in captured output AND that `Files.list(tempStagingDir).count()` is zero after the sweep.
    8. Test 2 asserts the exact substring `Cleared 0 stale staging files` in captured output for an empty dir.
    9. Test 3 asserts that NO `Cleared` substring appears in the slice of captured output produced AFTER the re-published event (the early-return branch is silent).
    10. Test 4 asserts that `keepme.txt` exists after the sweep AND that no `upload-*.zip` file remains AND that captured output contains `Cleared 1 stale staging files`.
    11. The test executes successfully under `./mvnw failsafe:integration-test failsafe:verify -Dit.test=BackupStagingCleanupIT` — all four tests pass.
    12. No file outside `src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java` is modified by this task.
  </acceptance_criteria>

  <verify>
    <automated>./mvnw failsafe:integration-test failsafe:verify -Dit.test=BackupStagingCleanupIT</automated>
  </verify>

  <done>`BackupStagingCleanupIT` exists with the four BDD-named tests, each test re-publishes `ApplicationReadyEvent`, and the four scenarios from D-17 (three stale files deleted with `Cleared 3` log, empty dir with `Cleared 0` log, missing dir with no log, unrelated file untouched while one staging file deleted with `Cleared 1` log) all pass under Failsafe.</done>
</task>

## Verification

### must_haves

**Truths:**
- A fresh JVM startup with three orphaned `upload-{uuid}.zip` files in `app.backup.staging-dir` reaps all three, leaves the directory empty, and emits exactly one `INFO`-level log line `Cleared 3 stale staging files`. (Per D-17: the operational signal the admin uses to confirm the safety net ran; this is the primary failure mode the sweep exists to handle — JVM died mid-import.)
- A fresh JVM startup with an empty `app.backup.staging-dir` emits exactly one `INFO`-level log line `Cleared 0 stale staging files`. (Per D-17: the zero-count log is intentional — admins see the sweep ran even on a quiet day; absence of the line would be ambiguous between "sweep ran, nothing to do" and "sweep did not run, bug".)
- A fresh JVM startup BEFORE the staging directory has ever been created (first ever boot of a brand-new install) does not log anything related to the sweep and does not throw. (Per D-15: `BackupImportService.stage()` creates the directory on demand; the sweep must tolerate its absence; the silent no-op is intentional to avoid a misleading `Cleared 0` line when the dir literally does not exist yet.)
- A fresh JVM startup with both an unrelated file (`keepme.txt`) and a valid `upload-{uuid}.zip` in the staging dir deletes only the `upload-*.zip` and leaves `keepme.txt` untouched. (Pins the filter contract: the sweep owns its own naming convention `upload-*.zip` and never touches anything else — a defensive boundary that prevents future regressions if someone manually drops a debug file into the staging dir.)
- A `Files.delete` failure on one staged file does not abort the sweep — the remaining files are still attempted, and the count log reflects only successful deletions. (Per D-17 + CTC convention: the sweep is advisory; a permission-denied error on one file must not break startup.)
- The sweep listener runs in every profile that boots the full Spring context (`dev`, `local`, `docker`, `prod`) — no `@Profile` annotation restricts it. (Operational safety net is universal; D-17 makes no profile carve-out.)
- The listener fires AFTER the embedded web server is ready and AFTER all `@PostConstruct` and `ContextRefreshedEvent` work — guaranteeing the file system is in its post-bootstrap state when the sweep walks the directory. (Per RESEARCH §"Pattern 8" + Spring Boot reference §1.4: `ApplicationReadyEvent` is the LAST startup event; the choice rules out a race against `Files.createDirectories` calls in `@PostConstruct` of other beans.)

**Artifacts:**
- `src/main/java/org/ctc/backup/service/BackupStagingCleanup.java` — new file, single `@Component` class, single `@EventListener(ApplicationReadyEvent.class)` method, single `@Value`-injected `Path` field. Min ≈ 30 source lines (Javadoc + imports + class body). Compiles under `./mvnw -q compile`.
- `src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java` — new file, `@SpringBootTest` IT with four `@Test` methods exercising the four D-17 scenarios. Min ≈ 100 source lines.

**Test Classes:**
- `BackupStagingCleanupIT` — owns Wave 2; covers D-17 verbatim; runs under Failsafe via `./mvnw failsafe:integration-test failsafe:verify -Dit.test=BackupStagingCleanupIT`. No other test class exercises the startup-sweep listener; no test in Phase 73 or earlier needs amendment.

### Reachability check

- **Entity (the staging dir):** Created on demand by Plan 05's `BackupImportService.stage()` via `Files.createDirectories(stagingDir)` (per D-15). On first boot before any import, the sweep's `Files.isDirectory` early-return covers absence. Reachable.
- **Workflow (the startup event):** `ApplicationReadyEvent` is published unconditionally by Spring Boot's `SpringApplication.run(...)` after the web server reports ready. No user action triggers it; the Spring framework provides the trigger. Reachable.
- **Config flag (`app.backup.staging-dir`):** Written into `application.yml` by Plan 01 with default `data/${spring.profiles.active}/backup-staging`. The `@Value` injection in this component is the consumer. Reachable.
- **UI surface:** None. This is a backend operational concern with no UI; the only user-observable signal is the INFO log line in the boot log, which is the documented operational signal in D-17.

## Notes

### Why no per-request scheduled sweep (D-17 explicit deferral to v1.11)

CONTEXT D-17 line 70 explicitly rejects `@Scheduled` for Phase 74. The rationale (verbatim): "this is admin-only feature with 1-2 uploads/week — startup sweep + reject-delete is enough; if leaks ever become a problem, a scheduled sweep is a one-line v1.11 add." Plan 07 honours this verbatim. No `@EnableScheduling`, no `@Scheduled(fixedDelay=...)`, no `TaskScheduler` bean. The two safety mechanisms shipped in Phase 74 are: (a) `BackupController` does a per-request `Files.delete` on reject/cancel (Plan 04 + Plan 05 territory — not this plan); (b) `BackupStagingCleanup` reaps anything (a) missed at the next boot. This plan owns mechanism (b).

### Why `OutputCaptureExtension` and not a custom `ListAppender<ILoggingEvent>`

Two alternatives were considered for asserting the `Cleared {N} stale staging files` log line:

1. **Logback `ListAppender<ILoggingEvent>`** — programmatically attach an appender to the `BackupStagingCleanup` SLF4J logger inside `@BeforeEach`, drain it in `@AfterEach`, assert against the captured events. Pros: full structural access (level, args, throwable). Cons: requires writing ≈ 30 lines of appender plumbing per test class; couples the test to Logback specifically; no existing CTC test does this.

2. **Spring Boot `OutputCaptureExtension`** — adds `@ExtendWith(OutputCaptureExtension.class)` and a `CapturedOutput` parameter. Captures `stdout` (which is where Logback's `STDOUT` appender writes by default in Spring Boot). Pros: zero plumbing; assertion is a simple `String.contains`; spec-stable across Spring Boot versions. Cons: assertions are stringly-typed (matches on the formatted log line, not on the structured event).

Choice: option 2. The test contract from D-17 is the exact STRING `Cleared {N} stale staging files` (operational human-readable signal), so a string match is semantically correct. No existing CTC test introduces this extension — this IT is the first; the extension is part of `spring-boot-test`, which is already on the test classpath via `spring-boot-starter-test`, so no `pom.xml` change is needed.

### Why test-method-driven event re-publish instead of `@DirtiesContext` per test

The `@EventListener(ApplicationReadyEvent.class)` fires ONCE during Spring context bootstrap — BEFORE any `@BeforeEach` runs. If the test method seeded fixture files in `@BeforeEach`, those files would be created AFTER the listener already ran (against an empty dir) and would survive to the next test (cross-test pollution). Two options:

1. **`@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)`** — forces a full context restart between tests, which re-fires the listener after `@BeforeEach`. Cost: ≈ +5 seconds per test method = +20 seconds for four tests = unacceptable.

2. **Programmatic re-publish via `ApplicationContext.publishEvent(...)`** — the listener fires synchronously on every published event of the matching type; the test seeds fixture files first, then explicitly re-fires the event, and asserts the post-sweep state. Cost: zero. Documented as a valid Spring testing pattern (the `@EventListener` is registered to the application context, not to the bootstrap sequence specifically).

Choice: option 2. The trade-off is that each test method also has to clear the captured output's pre-existing content from the bootstrap-time sweep — handled in Tests 1/2/3 by either tolerating the bootstrap log (`output.getAll()` still contains the expected substring, plus possibly an extra `Cleared 0` from the bootstrap run, both are accepted as long as the assertions pass) or by slicing `output.getAll()` after capturing a length baseline (used in Test 3 specifically because Test 3 asserts the ABSENCE of a log line).

### Why `Path` injection (not `String`) for the staging dir

Spring's default `StringToPathConverter` resolves the YAML string value into a `java.nio.file.Path` at bean-creation time. Using `Path` directly:

- Removes `Paths.get(stagingDirRaw)` boilerplate at the call site (one line saved, but more importantly: zero risk of inconsistent path normalisation across the production class and the test).
- Plays nicely with `@TempDir Path` in the IT — both production and test path manipulation use the same type.
- The default conversion does NOT call `.toAbsolutePath().normalize()` automatically. For the sweep's purpose this is fine — `Files.list(stagingDir)` and `Files.isDirectory(stagingDir)` work on relative paths against the current working directory (which is the project root in `./mvnw spring-boot:run`). If absolute-path semantics ever become required (e.g. logging the full resolved path for operational debugging), add `.toAbsolutePath()` at the field-initialisation site — a one-line follow-up that does not affect this plan's contract.

### Why a `keepme.txt` test case exists (Test 4)

The filter contract `startsWith("upload-") && endsWith(".zip")` is asymmetric: it accepts `upload-X.zip` but also `upload-anything-anywhere.zip`. A future regression could broaden the filter (e.g. someone simplifies it to `endsWith(".zip")` for "all ZIPs"), which would silently start deleting unrelated files (test fixtures, manually placed backups, etc.). Test 4 pins the prefix-AND-suffix conjunction so any such regression fails immediately. The asymmetric naming (`keepme.txt`, not `keepme.zip`) is deliberate: a `.txt` file proves the suffix predicate; the same predicate would also protect a `.zip` file without the `upload-` prefix (covered structurally, not as a separate test method to keep the test count at four per the planning context).

### Out of scope (deferred to other phases)

- The per-request reject-delete in `BackupController` — Plan 04 / Plan 05 territory (Cancel button, reject after schema-version mismatch).
- The actual `BackupImportService.stage()` method that writes the `upload-{uuid}.zip` files — Plan 05.
- An admin UI surface for "show last sweep count" or "trigger sweep on demand" — explicitly deferred; D-17 prescribes the INFO log as the sole admin signal.
- A `@Scheduled` periodic sweep — v1.11 (D-17 deferral).

## PLAN COMPLETE 07
