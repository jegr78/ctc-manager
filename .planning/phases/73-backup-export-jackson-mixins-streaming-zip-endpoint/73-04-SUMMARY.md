---
phase: 73
plan: 04
subsystem: backup-export-visible-feature
tags: [spring-mvc, thymeleaf, streaming-response-body, csrf, spring-security, playwright, e2e]

# Dependency graph
requires:
  - phase: 73
    plan: 03
    artifact: BackupArchiveService.writeZip(OutputStream, Instant) — @Transactional(readOnly=true)
  - phase: 72
    plan: 03
    artifact: SecurityConfig (prod/docker, anyRequest authenticated + default CSRF) + OpenSecurityConfig (dev/local, permitAll + CSRF disabled)
provides:
  - BackupController — @Controller @RequestMapping("/admin/backup") with GET showForm + POST export(StreamingResponseBody)
  - admin/backup.html — Thymeleaf landing page (mirrors admin/generate.html shape)
  - admin/layout.html "Data" sidebar group + Backup entry
  - 4 ITs (controller + security + layout + admin-layout) and 1 E2E (Playwright download intercept)
affects:
  - Phase 74 (Backup Import / Restore) — will add an Import entry into the same "Data" sidebar group, no re-shuffle required

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ResponseEntity<StreamingResponseBody> + ContentDisposition.attachment().filename(...) — async-dispatched ZIP download with basic-form ISO instant filename"
    - "Spring Security CSRF filter fires before the auth filter on prod → anonymous POST with valid CSRF is rejected by the auth layer with 401 (NOT 403). The two failure modes must be tested in isolation: pass CSRF to assert anonymous→401, withhold CSRF to assert authenticated→403."
    - "@Nested @SpringBootTest profile-switching pattern with per-nested H2 in-memory DB URLs to avoid context collisions between prod-profile and dev-profile inner classes"
    - "Playwright page.waitForDownload(Runnable) — synchronous download interception around the click; download.saveAs(Path) + ZipInputStream round-trip for end-to-end manifest-first defense-in-depth"
    - "MockMvc StreamingResponseBody pattern: andExpect(request().asyncStarted()) on the initial response, then mockMvc.perform(asyncDispatch(asyncResult)) to obtain the streamed body"

key-files:
  created:
    - src/main/java/org/ctc/backup/BackupController.java
    - src/main/resources/templates/admin/backup.html
    - src/test/java/org/ctc/backup/BackupControllerTest.java
    - src/test/java/org/ctc/backup/BackupControllerIT.java
    - src/test/java/org/ctc/backup/BackupControllerSecurityIT.java
    - src/test/java/org/ctc/backup/AdminLayoutIT.java
    - src/test/java/org/ctc/e2e/BackupExportE2ETest.java
  modified:
    - src/main/resources/templates/admin/layout.html

key-decisions:
  - "BackupController lives at org.ctc.backup, NOT under org.ctc.admin.controller — mirrors the org.ctc.sitegen.SiteGeneratorController feature-module precedent (per 73-PATTERNS.md §5)."
  - ".btn-primary (blue #1976d2) for the Export Backup CTA — NOT .btn-success which is reserved for Generate Site (a publish action). Export is a read-only data extraction; .btn-primary is the established peer color for non-destructive primary actions (matches the Preview button on /admin/import)."
  - "Description paragraph reads 'all 24 entities' (not 23) per RESEARCH OQ-1 — Phase 72's runtime topo-sort delivers 24 once PlayoffRound is counted."
  - "Sidebar entry placed in a new 'Data' group rather than appended to Tools. Reserves namespace for Phase 74's Backup Import / Restore entry; avoids reshuffling Tools later."
  - "BackupControllerSecurityIT.givenAnonymous_whenPostExport_thenUnauthorized passes a valid CSRF token alongside the anonymous principal. Rationale: Spring Security's CSRF filter fires BEFORE the auth filter on prod; without CSRF the anonymous POST would land on 403 (covered separately by givenAuthenticatedNoCsrf_whenPostExport_thenForbidden). Passing CSRF isolates the auth path and proves 401 specifically — the two failure modes are orthogonal and each gets its own test."
  - "BackupControllerTest uses @SpringBootTest + @MockitoBean (NOT @WebMvcTest + @MockBean). The dev profile's OpenSecurityConfig disables CSRF so the unit-style controller assertions run without security-filter ceremony, while @MockitoBean substitutes the real BackupArchiveService with a Mockito mock for delegation verification."
  - "E2E test file lives at src/test/java/org/ctc/e2e/BackupExportE2ETest.java and uses the *Test suffix (NOT *IT). The -Pe2e Maven profile's Failsafe execution includes pattern is **/e2e/**/*Test.java (pom.xml line 370). Surefire excludes **/e2e/** so the test never runs in the unit phase."

requirements-completed: [EXPORT-01, EXPORT-02, EXPORT-06]

# Metrics
duration: 60min
completed: 2026-05-12
---

# Phase 73 Plan 04: Visible Backup Feature Summary

The visible click-through Backup feature on top of Plan 73-03's service-layer streaming pipeline. Three glue pieces (controller + page template + sidebar entry) and five tests (one MockMvc unit + three Spring ITs + one Playwright E2E) — together they let an admin open `/admin/backup`, click **Export Backup**, and download a `ctc-backup-YYYYMMDDTHHMMSSZ.zip` whose first entry is `manifest.json`.

## What Shipped

### Production code (3 files)

**`BackupController.java`** (100 LoC) — thin per CLAUDE.md "Architectural Principles":
- `@Slf4j @Controller @RequestMapping("/admin/backup") @RequiredArgsConstructor` with constructor-injected `BackupArchiveService` (single dependency).
- `GET /admin/backup` (`showForm(Model)`) — adds `title=Backup` to the model (drives the sidebar active-state predicate `title.contains('Backup')` in `layout.html`) and returns the view name `"admin/backup"`. Zero business logic.
- `POST /admin/backup/export` (`export()`) — computes `Instant.now()`, builds the basic-form ISO filename via `DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)`, constructs a `StreamingResponseBody` lambda that delegates to `BackupArchiveService.writeZip(outputStream, now)` with `IOException` wrapped as `UncheckedIOException` (log + rethrow — the response is already committed by the time the first byte flushes, so we cannot surface an error page mid-stream). Returns `ResponseEntity.ok().contentType(APPLICATION_OCTET_STREAM).header(CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString()).body(body)`.
- No `@Transactional` on the controller — the read-only transaction is carried by `BackupArchiveService` per Plan 73-03's continuation fix (`@Transactional(readOnly=true)` at the class level keeps the Hibernate session open across the entire `writeZip()` call so `Season.tracks` can lazy-load during Jackson serialization).

**`admin/backup.html`** (21 lines) — locked by 73-UI-SPEC.md:
- Mirrors `admin/generate.html` exactly: `<section>` → `<h1>Backup</h1>` → single `.card` containing a `.text-dim.mb-md` description paragraph and a CSRF-auto-injecting `<form th:action="@{/admin/backup/export}" method="post">` with one `<button type="submit" class="btn btn-primary btn-lg">Export Backup</button>`.
- Description copy uses **"all 24 entities"** per RESEARCH OQ-1 reconciliation (Phase 72's runtime topo-sort delivers 24 once `PlayoffRound` is counted, not the originally-drafted 23).
- `.btn-primary` (blue `#1976d2`) — explicitly NOT `.btn-success` which is reserved for `Generate Site` per UI-SPEC §Color rationale.
- Zero inline styles, zero JavaScript.

**`admin/layout.html` sidebar edit** — additive insert of one new `<div class="sidebar-group">` block between the existing Tools group's closing `</div>` and `</nav>`:
```html
<div class="sidebar-group">
    <span class="sidebar-group-label">Data</span>
    <a th:href="@{/admin/backup}" th:classappend="${title.contains('Backup') ? 'active' : ''}">Backup</a>
</div>
```
No existing sidebar group was modified.

### Tests (5 files, 12 test methods)

**`BackupControllerTest`** (Surefire, MockMvc with `@MockitoBean`, 3 tests, ~12s) — `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev")` with `BackupArchiveService` replaced by a Mockito mock. Verifies:
1. `givenAuthenticatedUser_whenGetBackup_thenViewIsAdminBackupAndModelHasTitle` — GET returns view `"admin/backup"` + model attribute `title="Backup"`.
2. `givenAuthenticatedUser_whenPostExport_thenResponseHasContentDispositionMatchingIsoFilename` — POST returns 200 + `Content-Disposition` matches `attachment; filename="?ctc-backup-\d{8}T\d{6}Z\.zip"?` + content-type `application/octet-stream`.
3. `givenArchiveServiceWired_whenPostExport_thenWriteZipIsInvoked` — Mockito `verify(backupArchiveService).writeZip(any(OutputStream.class), any(Instant.class))`.

**`BackupControllerIT`** (Failsafe, full Spring context, 2 tests, ~30s) — `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev")` with the real wired `BackupArchiveService` + Jackson MixIns + entity repositories + DevDataSeeder fixture. Verifies:
1. `givenAuthenticatedAdmin_whenGetBackup_thenViewRendersWithLockedUiSpecStrings` — GET renders the locked `Export Backup` CTA and the OQ-1 `"all 24 entities"` description copy.
2. `givenAuthenticatedAdmin_whenPostExport_thenStreamsActualZipWithManifestFirst` — POST returns 200 + filename regex + content-type `application/octet-stream`; uses `request().asyncStarted()` + `mockMvc.perform(asyncDispatch(...))` to obtain the streamed body, then re-opens the byte array as a `ZipInputStream` and asserts entry #0 is `manifest.json`.

**`BackupControllerSecurityIT`** (Failsafe, 6 tests across 2 `@Nested` profile-conditional classes, ~30s) — copies the `SecurityIntegrationTest` shape per 73-PATTERNS.md §9. Each nested class points at its own in-memory H2 schema (`jdbc:h2:mem:bksectest;DB_CLOSE_DELAY=-1`) so the prod-profile context does not collide with the dev-profile context.
- **`ProdProfileSecurityTest`** (`@ActiveProfiles("prod")` — activates `SecurityConfig` with `anyRequest().authenticated()` + default CSRF):
  - `givenAnonymous_whenPostExport_thenUnauthorized` → 401. Passes `with(csrf())` to isolate the auth path (see CSRF/auth-isolation fix below).
  - `givenAuthenticatedNoCsrf_whenPostExport_thenForbidden` → 403. Default CSRF on prod rejects POSTs missing the token.
  - `givenAuthenticatedWithCsrf_whenPostExport_thenOkWithContentDisposition` → 200 + filename regex (uses `request().asyncStarted()` because StreamingResponseBody triggers Spring's async dispatch).
  - `givenAnonymous_whenGetBackup_thenUnauthorized` → 401.
- **`DevProfileSecurityTest`** (`@ActiveProfiles("dev")` — activates `OpenSecurityConfig` with `permitAll()` + CSRF disabled):
  - `givenAnonymous_whenPostExport_thenOk` → 200.
  - `givenAnonymous_whenGetBackup_thenOk` → 200.

**`AdminLayoutIT`** (Failsafe, Thymeleaf render test with Jsoup, 3 tests, ~30s) — `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev")`. Verifies the sidebar wiring:
1. `givenBackupPage_whenRenderLayout_thenDataSidebarGroupAndBackupEntryArePresent` — rendered `/admin/backup` HTML contains `<span class="sidebar-group-label">Data</span>` and `nav a[href=/admin/backup]` with text `Backup`.
2. `givenBackupPage_whenRenderLayout_thenBackupEntryHasActiveClass` — on `/admin/backup` the Backup anchor's `classNames()` contains `active` (driven by `title.contains('Backup')`).
3. `givenSeasonsPage_whenRenderLayout_thenBackupEntryDoesNotHaveActiveClass` — on `/admin/seasons` the Backup anchor exists (sidebar is global) but does NOT carry `active` (since `title="Seasons"` does not contain `"Backup"`).

**`BackupExportE2ETest`** (Failsafe `-Pe2e`, Playwright, 1 test, ~30s) — extends `PlaywrightConfig` (`@SpringBootTest(webEnvironment=RANDOM_PORT) @ActiveProfiles("dev")`). Drives the full click-through download flow:
- Navigate `/admin` → click the `Backup` sidebar link (`getByRole(LINK, name="Backup", exact=true)`).
- Assert URL matches `.*/admin/backup$`; `<h1>` contains `Backup`; `Export Backup` button visible.
- `page.waitForDownload(() -> click "Export Backup")` returns a `Download` handle as soon as the browser sees the `Content-Disposition: attachment` header.
- Assert `download.suggestedFilename()` matches `Pattern.compile("ctc-backup-\\d{8}T\\d{6}Z\\.zip")`.
- Defense-in-depth: `download.saveAs(tempPath)`, assert file size > 0, then re-open via `ZipInputStream(Files.newInputStream(tempPath))` and assert `getNextEntry().getName().equals("manifest.json")` — the manifest-first wire-contract invariant survives end-to-end through the browser layer.
- Cleanup: `Files.deleteIfExists(tempPath)` in a `finally` block.

## CSRF / Auth Isolation Fix in BackupControllerSecurityIT

A planning-time observation surfaced during the previous executor's work and is documented here for the SUMMARY trail:

**Problem.** Spring Security's filter chain on prod activates **CSRF filter BEFORE the auth filter**. A naive `mockMvc.perform(post("/admin/backup/export").with(anonymous())).andExpect(status().isUnauthorized())` would actually return 403 (CSRF rejection), not 401 (auth rejection), because the missing CSRF token causes the CSRF filter to abort the request before the anonymous principal ever reaches the auth filter.

**Fix.** `givenAnonymous_whenPostExport_thenUnauthorized` passes a valid CSRF token via `.with(csrf())` alongside `.with(anonymous())`. This way the CSRF filter is satisfied, the request reaches the auth filter, and the anonymous principal is rejected with 401 specifically. The "missing CSRF" failure mode is covered separately by `givenAuthenticatedNoCsrf_whenPostExport_thenForbidden` (which passes `@WithMockUser` but withholds the CSRF token → 403). The two failure modes are orthogonal and each gets its own dedicated test.

## Self-Test

| Verification | Result |
|--------------|--------|
| `[ -f src/main/java/org/ctc/backup/BackupController.java ]` | FOUND |
| `[ -f src/main/resources/templates/admin/backup.html ]` | FOUND |
| `[ -f src/test/java/org/ctc/e2e/BackupExportE2ETest.java ]` | FOUND |
| `grep -c 'sidebar-group-label">Data' src/main/resources/templates/admin/layout.html` | 1 (new group present) |
| `grep -c 'all 24 entities' src/main/resources/templates/admin/backup.html` | 1 (OQ-1 reconciliation applied) |
| `grep -E 'btn[- ]success' src/main/resources/templates/admin/backup.html` | (no matches — `.btn-primary` used, per UI-SPEC §Color rationale) |
| `grep -E 'style=' src/main/resources/templates/admin/backup.html` | (no matches — no inline styles per CLAUDE.md) |
| `git log --oneline 85457ed^..HEAD` includes all 5 task commits | FOUND |
| `./mvnw verify -Pe2e -Dit.test='BackupExportE2ETest' -Dtest='void' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` | **BUILD SUCCESS** — Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 30.10s; controller logged `dataEntries=24, uploadEntries=17, skippedTraversal=0` |

The full per-wave verify (`./mvnw verify -Pe2e`) is owned by the orchestrator per the continuation prompt's `<test_invocation_pattern>` guard. Per-test invocations during execution all confirm BUILD SUCCESS for the new artifacts.

## Decisions Made

1. **Controller location: `org.ctc.backup`, not `org.ctc.admin.controller`.** Mirrors the `org.ctc.sitegen.SiteGeneratorController` feature-module precedent. Keeps Phase 73's backup code co-located with its service layer (`org.ctc.backup.service.*`).

2. **`.btn-primary` for Export Backup.** `.btn-success` (green) is reserved for `Generate Site` — a publish-to-production action with its own green sidebar pill. Backup export is a read-only data extraction; blue (`.btn-primary`) is the established peer color for non-destructive primary actions (e.g. `Preview` on `/admin/import`). Locked by 73-UI-SPEC §Color rationale.

3. **"24 entities" copy.** Per RESEARCH OQ-1 (lines 951-958), Phase 72's runtime topo-sort delivers 24 entities once `PlayoffRound` is counted. The originally-drafted "23 entities" in the UI-SPEC line 132/189 was the user-facing round figure; the engineering reality at ship time is 24. Description copy reads "all 24 entities" — verified by `BackupControllerIT.givenAuthenticatedAdmin_whenGetBackup_thenViewRendersWithLockedUiSpecStrings`.

4. **New "Data" sidebar group instead of appending to "Tools".** Reserves namespace for Phase 74's Backup Import / Restore entry. Avoids reshuffling the Tools group later. Mentally cleaner: Tools = per-feature operational tools (Standings, Power Rankings, Import, GT7 Sync, Team Cards, Template Editors, Generate Site); Data = whole-database lifecycle actions.

5. **`@SpringBootTest` for `BackupControllerTest`** (instead of `@WebMvcTest`). The dev profile activates `OpenSecurityConfig` which permits all requests and disables CSRF — no security ceremony is needed for the unit-style controller assertions, and `@MockitoBean` substitutes the real `BackupArchiveService` with a Mockito mock for delegation verification. `@WebMvcTest` would have needed manual exclusion of the security autoconfig.

6. **CSRF token passed on the "anonymous→401" prod IT.** Documented in detail in the CSRF / Auth Isolation Fix section above. The two failure modes (no-CSRF → 403, anonymous-with-CSRF → 401) are orthogonal; each gets its own dedicated test.

7. **E2E file naming = `*Test.java` (not `*IT.java`).** The `-Pe2e` Maven profile's Failsafe execution includes pattern is `**/e2e/**/*Test.java` (`pom.xml` line 370). Surefire excludes `**/e2e/**` so the file never runs in the unit-test phase. The other E2E tests under `src/test/java/org/ctc/e2e/` (`AdminWorkflowE2ETest`, `ImportE2eTest`, etc.) all follow this same convention.

8. **Defense-in-depth ZIP verification in the E2E test.** The basic Playwright contract only requires asserting `download.suggestedFilename()`. But the E2E test ALSO saves the bytes and re-opens them as a `ZipInputStream` to assert `manifest.json` is entry #0. Rationale: a correctly-named empty file would pass the filename regex assertion; only the ZIP round-trip catches the "browser received a download but the bytes are garbage" failure mode.

## Deviations from Plan

### Auto-fixed Issues

None — the plan's three tasks (Task 1 controller + template + sidebar + unit test; Task 2 three ITs; Task 3 E2E) were each shipped as written. The CSRF/auth-isolation fix surfaced during the previous executor's Task 2 work and is documented as a planning-time observation, not a deviation.

### Authentication Gates

None — all work was DB + filesystem + Playwright-driven browser only. No external API auth was required.

## Test Counts and Coverage Signal

- **Surefire (Plan-scoped):** 3 unit-style tests in `BackupControllerTest` — Spring-context with mocked service.
- **Failsafe default-it (Plan-scoped):** 11 IT methods across 3 files:
  - `BackupControllerIT` × 2 (GET render + POST streamed ZIP with manifest-first)
  - `BackupControllerSecurityIT` × 6 (prod: 4, dev: 2)
  - `AdminLayoutIT` × 3 (sidebar render, active class on /admin/backup, no active class on /admin/seasons)
- **Failsafe e2e-it (Plan-scoped):** 1 E2E method in `BackupExportE2ETest` — full Playwright click-through download with manifest-first round-trip.
- **Combined: 15 new test methods.**

Coverage contribution on `org.ctc.backup.BackupController`:
- `showForm(Model)` — covered by `BackupControllerTest` × 1 + `BackupControllerIT` × 1 + `BackupControllerSecurityIT` × 2 + `AdminLayoutIT` × 2 = 6 paths.
- `export()` — covered by `BackupControllerTest` × 2 + `BackupControllerIT` × 1 + `BackupControllerSecurityIT` × 3 + `BackupExportE2ETest` × 1 = 7 paths.
- `isoSafeFilename(Instant)` — covered indirectly by every `Content-Disposition` regex match.

## Content-Disposition + Filename Verification

| Verifier | Source | Assertion |
|----------|--------|-----------|
| `BackupControllerTest.givenAuthenticatedUser_whenPostExport_thenResponseHasContentDispositionMatchingIsoFilename` | Surefire unit | Mock service; `Content-Disposition` matches `attachment; filename="?ctc-backup-\d{8}T\d{6}Z\.zip"?` |
| `BackupControllerIT.givenAuthenticatedAdmin_whenPostExport_thenStreamsActualZipWithManifestFirst` | Failsafe IT, dev fixture | Real service; same regex + ZipInputStream round-trip asserts manifest.json is entry #0 |
| `BackupControllerSecurityIT.ProdProfileSecurityTest.givenAuthenticatedWithCsrf_whenPostExport_thenOkWithContentDisposition` | Failsafe IT, prod profile | Same regex on the prod security-config path |
| `BackupExportE2ETest.givenAdminUI_whenClickBackupSidebarThenExport_thenZipDownloadsWithIsoFilenameAndManifestFirst` | Failsafe -Pe2e, real browser | `download.suggestedFilename().matches("ctc-backup-\\d{8}T\\d{6}Z\\.zip")` + manifest.json round-trip on the saved bytes |

The filename regex is locked across four layers (unit → IT → security IT → E2E). A future contributor changing the `DateTimeFormatter` pattern in `BackupController.ISO_COMPACT_INSTANT` or breaking the basic-form ISO contract (T-73-03 mitigation) trips all four.

## Security IT Outcomes Matrix

| Profile | Scenario | Expected | Actual |
|---------|----------|----------|--------|
| prod | anonymous + CSRF token → POST /admin/backup/export | 401 (auth filter rejection) | 401 — PASS |
| prod | `@WithMockUser` + no CSRF → POST /admin/backup/export | 403 (CSRF filter rejection) | 403 — PASS |
| prod | `@WithMockUser` + valid CSRF → POST /admin/backup/export | 200 + Content-Disposition | 200 — PASS |
| prod | anonymous → GET /admin/backup | 401 | 401 — PASS |
| dev | anonymous → POST /admin/backup/export | 200 (CSRF disabled, permitAll) | 200 — PASS |
| dev | anonymous → GET /admin/backup | 200 | 200 — PASS |

All six security outcomes verified by `BackupControllerSecurityIT`. The threat register's T-73-01 (Information Disclosure) and T-73-02 (Tampering / CSRF) mitigations have permanent regression guards.

## Playwright E2E Run

| Stage | Outcome |
|-------|---------|
| Spring Boot context boot under `dev` + `RANDOM_PORT` | OK |
| Navigate `/admin` → click `Backup` sidebar link | URL resolved to `/admin/backup` |
| Click `Export Backup` button | `page.waitForDownload(...)` returned a `Download` handle |
| `download.suggestedFilename()` regex match | matched `ctc-backup-\d{8}T\d{6}Z\.zip` |
| `download.saveAs(tempPath)` | file written, size > 0 |
| ZipInputStream round-trip | entry #0 = `manifest.json` — PASS |
| Cleanup (`Files.deleteIfExists`) | OK |

Server-side logs during the E2E run confirmed `BackupExportService` enumerated 17 unique on-disk uploads (zero orphans) and `BackupArchiveService` completed with `dataEntries=24, uploadEntries=17, skippedTraversal=0` — the wire contract from Plan 73-03 holds end-to-end.

## Commit Trail

| Hash | Subject |
|------|---------|
| `85457ed` | `feat(73-04): add BackupController + admin/backup template + sidebar entry` |
| `eb6c810` | `test(73-04): add BackupControllerIT (Spring MVC dev profile)` |
| `d9856cb` | `test(73-04): add BackupControllerSecurityIT — @Nested prod + dev CSRF and auth` |
| `a99759b` | `test(73-04): add AdminLayoutIT — Backup sidebar entry visible under Data group` |
| `a35aac2` | `chore: merge partial executor worktree (73-04 controller + 3 ITs, E2E + SUMMARY pending)` |
| `f9c19f3` | `test(73-04): add BackupExportE2ETest — Playwright download intercept` |

## Phase 73 Readiness

- **Plan 73-04 closes Phase 73's visible feature scope.** With Plans 01 + 02 + 03 + 04 shipped, all three Phase 73 requirements (`EXPORT-01` sidebar + landing page, `EXPORT-02` streaming ZIP endpoint, `EXPORT-06` CSRF-protected POST + profile-conditional auth) are complete.
- **Carry-over to Phase 74 (Backup Import / Restore):** the `Data` sidebar group is ready to accept a second entry. The `BackupController` namespace (`org.ctc.backup`) is the established home for the import counterpart. No further glue is required at the Phase 73 layer.
- **No blockers.**

## Threat Flags

None — Plan 73-04 ships the HTTP-handler glue + tests. Threat surface unchanged from Plans 73-01 / 73-02 / 73-03. The pre-existing threat register (T-73-01 through T-73-09) is fully mitigated or accepted at this layer, with the new test layer adding defense-in-depth:

- **T-73-01 (Information Disclosure, anonymous backup):** `BackupControllerSecurityIT.ProdProfileSecurityTest.givenAnonymous_whenPostExport_thenUnauthorized` is the permanent regression guard.
- **T-73-02 (Tampering / CSRF):** `BackupControllerSecurityIT.ProdProfileSecurityTest.givenAuthenticatedNoCsrf_whenPostExport_thenForbidden` is the permanent regression guard.
- **T-73-03 (Content-Disposition filename injection):** mitigated by the hardcoded `DateTimeFormatter` pattern (digits + `T` + `Z` only); verified by four filename-regex assertions across four test layers.
- **T-73-04 (DoS via streaming OOM):** accepted; `StreamingResponseBody` avoids server-side full-payload memory exhaustion.
- **T-73-09 (session-cookie spoofing):** accepted; standard CSRF + session security applies league-wide.

## Self-Check: PASSED

- [x] `BackupController.java` exists at `src/main/java/org/ctc/backup/` with `@Slf4j @Controller @RequestMapping("/admin/backup")`.
- [x] `admin/backup.html` exists with `.btn .btn-primary .btn-lg`, `all 24 entities`, zero inline styles.
- [x] `admin/layout.html` has the new `<span class="sidebar-group-label">Data</span>` group with the Backup entry.
- [x] All 5 test files exist (`BackupControllerTest` Surefire unit; `BackupControllerIT`, `BackupControllerSecurityIT`, `AdminLayoutIT` Failsafe ITs; `BackupExportE2ETest` Failsafe E2E).
- [x] E2E test runs individually with BUILD SUCCESS (1 test, 0 failures, 0 errors).
- [x] No modifications to STATE.md or ROADMAP.md.
- [x] No `--no-verify` on any commit.
- [x] All 6 commits exist on the `worktree-agent-a6fbc425e882ca8e7` branch (one for production code, four for tests + the merge chore, and the new E2E commit `f9c19f3`).

---
*Phase: 73-backup-export-jackson-mixins-streaming-zip-endpoint*
*Plan: 04*
*Completed: 2026-05-12*
