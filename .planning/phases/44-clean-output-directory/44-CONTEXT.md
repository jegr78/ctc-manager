# Phase 44: Clean Output Directory - Context

**Gathered:** 2026-04-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Eliminate stale files by emptying the output directory before generating fresh content. When pages are removed or renamed, old files currently persist because `SiteGeneratorService.generate()` only calls `Files.createDirectories(outPath)` without cleaning existing content.

</domain>

<decisions>
## Implementation Decisions

### Cleanup Strategy
- **D-01:** Use `Files.walkFileTree()` with a `SimpleFileVisitor` that deletes files bottom-up (files first, then empty directories). Standard Java NIO — no external dependencies needed.
- **D-02:** After cleaning, recreate the empty root directory with `Files.createDirectories()`. Do not delete the root directory itself (avoids issues if CWD is inside it).
- **D-03:** If the output directory does not exist yet, simply create it — no error, no cleanup needed.

### Safety
- **D-04:** No additional path validation before deletion. The output directory path comes from `@Value("${ctc.site.output-dir}")` application properties, not user input. The property is already profile-specific (dev: `target/site`, prod: `docs/site`).

### Claude's Discretion
- Logging: Claude decides appropriate log levels (info for cleanup start, debug for individual deletions)
- Method visibility: private method on `SiteGeneratorService` is sufficient

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Site Generator
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — Main service, `generate()` method at line 56 is the entry point. Cleanup goes before line 61 (`Files.createDirectories`).
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — Existing 70+ integration tests, `@TempDir` pattern for isolated output dirs.

### Configuration
- `src/main/resources/application.yml` — `ctc.site.output-dir: docs/site` (default/prod)
- `src/main/resources/application-dev.yml` — `ctc.site.output-dir: target/site` (dev)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SiteGeneratorService.generate()` already uses `Path.of(outputDir)` and `Files.createDirectories()` — cleanup method integrates at the same point
- Tests use `@TempDir` with `service.setOutputDir(tempDir.toString())` — clean isolation per test

### Established Patterns
- Service uses `@Slf4j` for logging with `log.info()` for state changes, `log.debug()` for details
- `GenerationResult` tracks `pagesGenerated` and `errors` — cleanup errors should also be tracked
- `@Transactional(readOnly = true)` on `generate()` — cleanup is file I/O, not affected by transaction

### Integration Points
- Single integration point: `SiteGeneratorService.generate()` method, before `Files.createDirectories(outPath)` at line 61
- No other callers or services need changes

</code_context>

<specifics>
## Specific Ideas

No specific requirements — straightforward infrastructure change with clear implementation path.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 44-clean-output-directory*
*Context gathered: 2026-04-16*
