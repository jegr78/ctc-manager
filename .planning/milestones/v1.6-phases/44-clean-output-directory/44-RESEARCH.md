# Phase 44: Clean Output Directory - Research

**Researched:** 2026-04-16
**Domain:** Java NIO file system operations — output directory cleanup before static site generation
**Confidence:** HIGH

## Summary

This phase adds a single private method `cleanOutputDirectory(Path outPath)` to `SiteGeneratorService` that is called at the top of `generate()` before `Files.createDirectories(outPath)`. The method uses `Files.walkFileTree()` with a `SimpleFileVisitor` to delete all files and subdirectories bottom-up, then recreates the root directory. If the directory does not yet exist, the method does nothing and creation is delegated to the existing `Files.createDirectories(outPath)` call.

The implementation is pure Java NIO — no external dependencies, no schema changes, no template changes. The path comes from a bound `@Value("${ctc.site.output-dir}")` property already in use; no additional safety validation is needed per the CONTEXT.md decision D-04.

Two new integration tests follow the existing `@TempDir` + `siteGeneratorService.setOutputDir(tempDir.toString())` pattern in `SiteGeneratorServiceTest`, which already has 70+ tests in this style.

**Primary recommendation:** Add `cleanOutputDirectory(Path)` as a private method called at line 60 (before `Files.createDirectories(outPath)`), backed by two new tests that prove (a) stale files are removed and (b) a non-existent directory is created gracefully.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Use `Files.walkFileTree()` with a `SimpleFileVisitor` that deletes files bottom-up (files first, then empty directories). Standard Java NIO — no external dependencies needed.
- **D-02:** After cleaning, recreate the empty root directory with `Files.createDirectories()`. Do not delete the root directory itself (avoids issues if CWD is inside it).
- **D-03:** If the output directory does not exist yet, simply create it — no error, no cleanup needed.
- **D-04:** No additional path validation before deletion. The output directory path comes from `@Value("${ctc.site.output-dir}")` application properties, not user input.

### Claude's Discretion
- Logging: Claude decides appropriate log levels (info for cleanup start, debug for individual deletions)
- Method visibility: private method on `SiteGeneratorService` is sufficient

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CLEAN-01 | Output directory is emptied before page generation begins | `Files.walkFileTree` + `SimpleFileVisitor` bottom-up delete called at top of `generate()` |
| CLEAN-02 | Clean operation handles non-existent output directory gracefully | Guard with `Files.exists(outPath)` before walk; if absent, fall through to existing `Files.createDirectories` |
</phase_requirements>

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Output directory cleanup | Backend Service (`SiteGeneratorService`) | — | Pure file I/O, lives in same service that performs generation; no controller or template involvement |
| Directory re-creation after clean | Backend Service (`SiteGeneratorService`) | — | Delegates to existing `Files.createDirectories(outPath)` call already at line 61 |

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `java.nio.file.Files` | JDK 25 (built-in) | File tree walk, delete, create directories | Standard Java NIO — already imported in `SiteGeneratorService` (`import java.nio.file.*;`) |
| `java.nio.file.SimpleFileVisitor` | JDK 25 (built-in) | Visitor skeleton for `walkFileTree` — override `visitFile` and `postVisitDirectory` | Standard pattern; no third-party dependency needed |

No new Maven dependencies are required.

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `Files.walkFileTree` + `SimpleFileVisitor` | Apache Commons IO `FileUtils.deleteDirectory()` | Commons IO is not on the classpath; adding a dependency for two lines of NIO is unnecessary |
| `Files.walkFileTree` | `Files.walk` stream + `sorted(Comparator.reverseOrder())` | Stream variant is more concise but loses explicit visitor control and requires extra sort; `walkFileTree` is the canonical bottom-up pattern |

---

## Architecture Patterns

### System Architecture Diagram

```
generate() entry point
        |
        v
cleanOutputDirectory(outPath)   <-- NEW (called before anything else)
        |
        +-- outPath exists? --NO--> return (do nothing)
        |
        YES
        |
        v
Files.walkFileTree(outPath, SimpleFileVisitor)
        |
        +-- visitFile()       --> Files.delete(file)
        +-- postVisitDirectory() --> Files.delete(dir)  [skips root]
        |
        v
Files.createDirectories(outPath)   <-- existing line 61 recreates root
        |
        v
... rest of generate() (index, seasons, archive, assets)
```

### Recommended Project Structure

No structural changes. The new method is private on `SiteGeneratorService`.

```
src/main/java/org/ctc/sitegen/
└── SiteGeneratorService.java    # add private cleanOutputDirectory(Path)

src/test/java/org/ctc/sitegen/
└── SiteGeneratorServiceTest.java  # add 2 new @Test methods
```

### Pattern 1: Bottom-Up Directory Deletion via walkFileTree

**What:** Walk file tree in depth-first order; delete files in `visitFile`, delete directories (except root) in `postVisitDirectory`. After walk, recreate root.

**When to use:** When you need to empty a directory without deleting the directory itself (avoids CWD issues), and when you need idiomatic Java NIO without third-party libraries.

**Example:**

```java
// Source: JDK 25 java.nio.file.Files.walkFileTree / SimpleFileVisitor — standard Java NIO [VERIFIED: JDK API]
private void cleanOutputDirectory(Path outPath) throws IOException {
    if (!Files.exists(outPath)) {
        return; // D-03: non-existent dir — createDirectories() below handles creation
    }
    log.info("Cleaning output directory: {}", outPath);
    Files.walkFileTree(outPath, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            log.debug("Deleted file: {}", file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (exc != null) throw exc;
            if (!dir.equals(outPath)) {  // D-02: do not delete root itself
                Files.delete(dir);
                log.debug("Deleted directory: {}", dir);
            }
            return FileVisitResult.CONTINUE;
        }
    });
}
```

**Integration point in `generate()`:**

```java
// Before line 61 (Files.createDirectories):
cleanOutputDirectory(outPath);
Files.createDirectories(outPath); // recreates root after clean (or creates it fresh)
```

### Anti-Patterns to Avoid

- **Deleting the root directory and re-creating it:** Causes issues when the process CWD is inside the directory, and is unnecessary. D-02 mandates keeping the root; only delete its contents.
- **Catching `IOException` silently in `cleanOutputDirectory`:** The method should propagate `IOException` to the caller (`generate()`), which already has the outer `catch (IOException e)` block that records errors in `GenerationResult`.
- **Using `Files.walk` stream without reversing order:** Stream walk is pre-order; deleting a directory before its contents throws `DirectoryNotEmptyException`. Always use `walkFileTree` or reverse-sorted stream for deletion.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Recursive delete | Custom recursive method with `listFiles()` | `Files.walkFileTree` + `SimpleFileVisitor` | JDK handles depth-first traversal, symlink cycles, and error propagation correctly |
| Stale file detection | Diffing old vs new file list | Full clean before regeneration | Simpler, deterministic; avoids edge cases with renamed/moved pages |

**Key insight:** `Files.walkFileTree` with `SimpleFileVisitor` is the idiomatic Java NIO pattern for bottom-up deletion. It handles depth-first traversal correctly and is already part of `java.nio.file.*` which is already imported.

---

## Common Pitfalls

### Pitfall 1: Deleting Root Directory Breaks Subsequent `createDirectories`

**What goes wrong:** If root is deleted and then `Files.createDirectories` fails for any reason, the site generation aborts with no output directory at all.
**Why it happens:** Deleting root + recreating is two operations; a failure between them leaves things in a bad state.
**How to avoid:** Per D-02, skip `Files.delete(dir)` when `dir.equals(outPath)`. After `walkFileTree`, the root directory is empty and `Files.createDirectories` is a no-op (succeeds idempotently).
**Warning signs:** Tests that check the output dir still exists after generation would catch this.

### Pitfall 2: IOException in visitFile Not Propagated

**What goes wrong:** If `visitFile` swallows `IOException`, a partially-locked file on Windows silently prevents deletion and the stale file remains — the test for CLEAN-01 would still pass if the file happened to be unlocked.
**Why it happens:** Developer wraps delete in try/catch to "be safe."
**How to avoid:** Throw `IOException` from `visitFile`; let `generate()`'s outer catch handle it and record it in `GenerationResult`. Propagation is the correct behavior.
**Warning signs:** `result.hasErrors()` would be false even when a deletion failed.

### Pitfall 3: Test Uses Shared Output Dir (Not TempDir)

**What goes wrong:** Cleanup test deletes real `target/site` or `docs/site`, affecting other tests or the local build output.
**Why it happens:** Forgetting to call `siteGeneratorService.setOutputDir(tempDir.toString())` in the test `@BeforeEach`.
**How to avoid:** All new tests inherit `@TempDir` and the `setOutputDir` call from `setUp()` — no change to test setup needed, just write new `@Test` methods within the existing class.
**Warning signs:** CI output directory emptied unexpectedly.

---

## Code Examples

### Test Pattern — Stale File Is Removed (CLEAN-01)

```java
// Source: established @TempDir pattern from SiteGeneratorServiceTest [VERIFIED: codebase grep]
@Test
void givenStaleFile_whenGenerate_thenStaleFileIsRemoved() throws IOException {
    // given — place a stale file in the output dir before generation
    var staleFile = tempDir.resolve("stale-page.html");
    Files.writeString(staleFile, "<html>stale</html>");

    // when
    var result = siteGeneratorService.generate();

    // then
    assertFalse(result.hasErrors(), "Errors: " + result.getErrors());
    assertFalse(Files.exists(staleFile), "Stale file should be removed before generation");
}
```

### Test Pattern — Nested Stale Subdirectory Removed (CLEAN-01)

```java
@Test
void givenStaleNestedDirectory_whenGenerate_thenNestedDirectoryIsRemoved() throws IOException {
    // given — place a stale nested directory in the output dir
    var staleDir = tempDir.resolve("old-season").resolve("old-subdir");
    Files.createDirectories(staleDir);
    Files.writeString(staleDir.resolve("old-page.html"), "<html>old</html>");

    // when
    siteGeneratorService.generate();

    // then
    assertFalse(Files.exists(staleDir), "Stale nested directory should be removed");
    assertFalse(Files.exists(staleDir.getParent()), "Stale parent directory should be removed");
}
```

### Test Pattern — Non-Existent Output Directory (CLEAN-02)

```java
@Test
void givenNonExistentOutputDir_whenGenerate_thenCreatesAndGeneratesPages() throws IOException {
    // given — point to a non-existent subdirectory
    var freshDir = tempDir.resolve("fresh-output");
    // freshDir does not exist
    siteGeneratorService.setOutputDir(freshDir.toString());

    // when
    var result = siteGeneratorService.generate();

    // then
    assertFalse(result.hasErrors(), "Errors: " + result.getErrors());
    assertTrue(result.getPagesGenerated() > 0);
    assertTrue(Files.exists(freshDir.resolve("index.html")));
}
```

---

## Integration Point Detail

`SiteGeneratorService.generate()` currently starts at line 56. The insertion point is:

```
Line 58: Path outPath = Path.of(outputDir);
Line 60: try {
Line 61:     Files.createDirectories(outPath);   ← insert cleanOutputDirectory(outPath) BEFORE this line
```

The outer `catch (IOException e)` at line 94 already handles any IO failures from the entire try block — so `cleanOutputDirectory` propagating `IOException` is correct with no additional catch needed.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test (`@SpringBootTest`) |
| Config file | `pom.xml` Surefire configuration |
| Quick run command | `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest -Dsurefire.failIfNoSpecifiedTests=false` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CLEAN-01 | Stale file in output dir is deleted before generation | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenStaleFile_whenGenerate_thenStaleFileIsRemoved` | ❌ Wave 0 |
| CLEAN-01 | Nested stale subdirectory is fully removed | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenStaleNestedDirectory_whenGenerate_thenNestedDirectoryIsRemoved` | ❌ Wave 0 |
| CLEAN-02 | Non-existent output dir is created gracefully | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenNonExistentOutputDir_whenGenerate_thenCreatesAndGeneratesPages` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=SiteGeneratorServiceTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green (`./mvnw verify`) before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] 3 new `@Test` methods in `SiteGeneratorServiceTest.java` — covers CLEAN-01 and CLEAN-02

*(Test infrastructure is fully in place; only the new test methods need to be added.)*

---

## Environment Availability

Step 2.6: SKIPPED — this phase is purely code/config changes within the existing Java project. No external services, CLIs, or runtimes beyond the already-running Java 25 / Maven toolchain.

---

## Security Domain

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V5 Input Validation | No | Output path comes from application properties (not user input) — D-04 explicitly excludes validation |
| V6 Cryptography | No | No cryptographic operations |

No new threat surface introduced. The cleanup method operates on files written by the same process in a directory controlled by application configuration.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `Files.walkFileTree` visits directories in depth-first (post-order) when `postVisitDirectory` is used, ensuring files are deleted before their parent directories | Code Examples | Deletion would fail with `DirectoryNotEmptyException` — LOW risk, this is documented JDK behavior |

**All other claims in this research were verified against the codebase directly.**

---

## Open Questions

None — the implementation path is fully specified by CONTEXT.md decisions D-01 through D-04.

---

## Sources

### Primary (HIGH confidence)

- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — verified integration point (line 61), existing imports, `GenerationResult` API, logging style
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — verified `@TempDir` + `setOutputDir` test pattern, existing 70+ tests
- `pom.xml` — verified JaCoCo minimum 0.82 [VERIFIED: codebase grep line 241]
- JDK 25 `java.nio.file.Files.walkFileTree` / `SimpleFileVisitor` — standard Java NIO API [VERIFIED: JDK API, no version concerns]

### Secondary (MEDIUM confidence)

- `.planning/phases/44-clean-output-directory/44-CONTEXT.md` — all decisions D-01..D-04 confirmed

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — pure JDK NIO, no third-party involvement
- Architecture: HIGH — single method, single integration point, verified in codebase
- Pitfalls: HIGH — derived from direct code inspection and JDK semantics
- Test patterns: HIGH — identical to 70+ existing tests in the same file

**Research date:** 2026-04-16
**Valid until:** Stable — JDK NIO semantics are stable; no external dependencies
