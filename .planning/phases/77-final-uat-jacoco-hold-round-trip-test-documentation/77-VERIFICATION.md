---
phase: 77-final-uat-jacoco-hold-round-trip-test-documentation
verified: 2026-05-15T09:55:00Z
status: human_needed
score: 7/8
overrides_applied: 0
human_verification:
  - test: "MariaDB round-trip IT — local run with Docker daemon"
    expected: "./mvnw -Ddocker.available=true -Dit.test='BackupRoundTripIT$MariaDbRoundTripTests' verify → BUILD SUCCESS (0 failures, row-counts parity, SHA-256 byte-equal on Race + SeasonDriver + Team)"
    why_human: "MariaDbRoundTripTests is gated by @EnabledIfSystemProperty(named='docker.available', matches='true'). CI does NOT pass this flag — the test is SKIPPED in CI by design (D-05). AUTO-UAT item 5 is still 'pending'. The verifier cannot run Testcontainers without a Docker daemon available in this environment."
  - test: "Screenshots render in GitHub Wiki page post-PR-merge"
    expected: "playwright-cli open https://github.com/jegr78/ctc-manager/wiki/Backup-and-Restore → 3 image embeds load successfully from raw.githubusercontent.com/jegr78/ctc-manager/master/.screenshots/77/*.png"
    why_human: "The raw.githubusercontent.com URLs reference the 'master' branch. Screenshots are committed to the 'gsd/v1.10-platform-and-backup' branch but not yet on 'master'. The wiki page already returns HTTP 200, but image embeds will 404 until the PR merges to master. This is known and documented in 77-05-SUMMARY.md key-decisions."
---

# Phase 77: Final UAT + JaCoCo Hold + Round-Trip Test + Documentation — Verification Report

**Phase Goal:** Milestone closure preparation. `./mvnw verify -Pe2e` green on H2; `BackupRoundTripIT` performs export → wipe → import → per-table row-count parity + SHA-256 byte-equality on Race + SeasonDriver + Team; JaCoCo ≥ 82%; README "Backup & Restore" section + GitHub Wiki page documented.

**Verified:** 2026-05-15T09:55:00Z
**Status:** human_needed — 7/8 automated truths VERIFIED; 2 items requiring human/post-merge confirmation
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `BackupRoundTripIT$H2RoundTripTests` exists with full export→wipe→import→row-count-parity+SHA-256 round-trip | VERIFIED | Class at `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` lines 278–467; `@Nested @SpringBootTest @ActiveProfiles("dev")`. Log: `Tests run: 5, Failures: 0` for H2RoundTripTests in final verify run. |
| 2 | `BackupRoundTripIT$MariaDbRoundTripTests` exists with `@EnabledIfSystemProperty(named="docker.available", matches="true")` | VERIFIED | Lines 484–694; `@Nested @SpringBootTest @ActiveProfiles("local") @Testcontainers @EnabledIfSystemProperty(named="docker.available", matches="true")`. Structurally correct. |
| 3 | MariaDbRoundTripTests GREEN on local run with Docker daemon | UNCERTAIN (human needed) | AUTO-UAT item 5 status: `pending`. Log shows `Tests run: 1, Failures: 0, Skipped: 1` (skipped = `docker.available` not set). Local run with `-Ddocker.available=true` not confirmed. |
| 4 | JaCoCo line coverage ≥ 82% with pom.xml minimum unchanged at 0.82 | VERIFIED | AUTO-UAT item 2: "Measured: 88.9% — 6.9% buffer over the 82% gate". `pom.xml` shows `<minimum>0.82</minimum>`. Log: "All coverage checks have been met. BUILD SUCCESS". |
| 5 | `BackupImportRollbackIT` stays passing (zero touch, Phase 75 D-14) | VERIFIED | Zero diff on `BackupImportRollbackIT.java` between base `6a588fd` and HEAD. Log: `Tests run: 2, Failures: 0, Errors: 0` in final verify. |
| 6 | README "## Backup & Restore" section exists, placed after "## Features" and before "## Quick Start" (D-09/CD-05) | VERIFIED | `grep -n "^## " README.md` → Features:15, Backup & Restore:32, Quick Start:61. All five D-09 sub-elements present: description with `/admin/backup`, Export 3-step, Import 3-step + Schema-Version lock blockquote, Recovery pointer to `import-runbook.md`, Full Guide wiki link. |
| 7 | GitHub Wiki page `https://github.com/jegr78/ctc-manager/wiki/Backup-and-Restore` live (HTTP 200) | VERIFIED | `curl -sI -L` → `HTTP/2 200`. Wiki page pushed to `ctc-manager.wiki.git` in commit `2fa2b25`. |
| 8 | Three PNG screenshots committed to `.screenshots/77/` with correct filenames and non-zero size | VERIFIED | `01-backup-page.png` (67709 bytes, PNG 1280×720), `02-preview-screen.png` (69079 bytes, PNG 1280×720), `03-import-banner.png` (74417 bytes, PNG 1280×720). Committed in `91ae243`. Git ls-files confirms tracking. |

**Score:** 7/8 truths verified (1 UNCERTAIN routed to human)

---

### Sacred-File Invariant Matrix

| File | Expected | Diff vs base `6a588fd` | Status |
|------|----------|------------------------|--------|
| `pom.xml` | Zero diff (D-11/D-12/D-16: minimum=0.82, excludes unchanged, version=1.8.0-SNAPSHOT) | `git diff --stat` returns empty | VERIFIED |
| `.github/workflows/mariadb-migration-smoke.yml` | Zero diff (D-05: SACRED) | `git diff --stat` returns empty | VERIFIED |
| `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` | Zero diff (D-14: untouched in Phase 77) | `git diff --stat` returns empty | VERIFIED |
| `docs/operations/import-runbook.md` | Zero diff (Phase 76 runbook untouched) | `git diff --stat` returns empty | VERIFIED |
| No new Flyway migration | No new `V*__*.sql` files | `git diff --name-only HEAD 6a588fd -- src/main/resources/db/migration/` = empty | VERIFIED |
| No new templates/CSS/controllers | No files changed under `src/main/resources/templates/` or `src/main/java/.../controller/` | `git diff --name-only` shows zero `src/main/` files | VERIFIED |

---

### Required Artifacts

| Artifact | Expected (per CONTEXT D-17) | Status | Details |
|----------|-----------------------------|--------|---------|
| `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` | Extended with `@Nested H2RoundTripTests` + `@Nested MariaDbRoundTripTests` | VERIFIED | 695 lines; both nested classes substantively implemented with SHA-256 hashing, row-count capture, `MockMultipartFile` staging, `assertThat(...).containsExactly(preHash)` assertions |
| `README.md` | "Backup & Restore" section per D-09 | VERIFIED | Section at line 32, 30 lines, all 5 D-09 elements present |
| `.screenshots/77/01-backup-page.png` | Non-zero PNG | VERIFIED | 67709 bytes, PNG 1280×720 |
| `.screenshots/77/02-preview-screen.png` | Non-zero PNG | VERIFIED | 69079 bytes, PNG 1280×720 |
| `.screenshots/77/03-import-banner.png` | Non-zero PNG | VERIFIED | 74417 bytes, PNG 1280×720 |
| `.planning/phases/77-.../77-AUTO-UAT.md` | 6-item checklist per D-13 | VERIFIED | Shipped; item 2 (JaCoCo) = `passed`; items 1/3/4/5/6 = `pending` post-merge |
| `ctc-manager.wiki.git` / `Backup-and-Restore.md` | External repo page, HTTP 200 | VERIFIED | Wiki live at `https://github.com/jegr78/ctc-manager/wiki/Backup-and-Restore` (HTTP 200 confirmed) |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `H2RoundTripTests` | `BackupArchiveService.writeZip()` | `backupArchiveService.writeZip(baos, Instant.now())` | VERIFIED | Autowired at line 284; called in `exportToBytes()` helper |
| `H2RoundTripTests` | `BackupImportService.stage()` + `.execute()` | `MockMultipartFile` → `backupImportService.stage(file)` → `backupImportService.execute(preview.stagingId())` | VERIFIED | Lines 357–361 |
| `H2RoundTripTests` | `backupObjectMapper` | `@Qualifier("backupObjectMapper") ObjectMapper` | VERIFIED | Autowired at lines 296–298; used in `hashEntity()` |
| `H2RoundTripTests` | SHA-256 hash comparison | `MessageDigest.getInstance("SHA-256").digest(bytes)` → `assertThat(postHash).containsExactly(preHash)` | VERIFIED | Lines 443–446; assertions at lines 373–402 |
| `README.md` | `docs/operations/import-runbook.md` | `[docs/operations/import-runbook.md](docs/operations/import-runbook.md)` | VERIFIED | Line 53 |
| `README.md` | GitHub Wiki | `[Backup & Restore wiki page](../../wiki/Backup-and-Restore)` | VERIFIED | Line 58 |
| Wiki page | Screenshots | `raw.githubusercontent.com/jegr78/ctc-manager/master/.screenshots/77/*.png` | UNCERTAIN (human needed post-merge) | URLs reference `master` branch; screenshots are on `gsd/v1.10-platform-and-backup` branch. Wiki page pushed; images will 404 until PR merges. Documented in 77-05-SUMMARY.md as known/accepted. |

---

### Data-Flow Trace (Level 4)

Not applicable. Phase 77 delivers test code and documentation only. No new production components render dynamic data.

---

### Behavioral Spot-Checks

| Behavior | Evidence | Status |
|----------|----------|--------|
| `BackupRoundTripIT$H2RoundTripTests` — full round-trip PASS | Log: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` (`BackupRoundTripIT$H2RoundTripTests`); `BUILD SUCCESS` in `/tmp/ctc-77-wave3-verify.log` | PASS |
| `BackupImportRollbackIT` still green | Log: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` (`BackupImportRollbackIT`) | PASS |
| JaCoCo coverage gate held | Log: "All coverage checks have been met." `pom.xml`: `<minimum>0.82</minimum>` | PASS |
| `BackupRoundTripIT$MariaDbRoundTripTests` — skipped (docker.available not set) | Log: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 1` — skipped as expected per D-05 | PASS (design-correct skip) |
| Wiki page HTTP 200 | `curl -sI -L https://github.com/jegr78/ctc-manager/wiki/Backup-and-Restore` → `HTTP/2 200` | PASS |

---

### Probe Execution

No dedicated probe scripts defined for Phase 77.

---

### Requirements Coverage

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|----------|
| QUAL-01 | JaCoCo line coverage ≥ 82% held | SATISFIED | 88.9% measured; `pom.xml` minimum unchanged at 0.82; log: "All coverage checks have been met" |
| QUAL-02 | Round-Trip-IT on H2 AND MariaDB: export→wipe→import→row-count parity + SHA-256 ≥ 3 sample entities | SATISFIED (H2) / UNCERTAIN (MariaDB local run) | H2 class exists with correct contract and passes. MariaDB class exists structurally correct but local run with Docker not yet confirmed (AUTO-UAT item 5 pending). REQUIREMENTS.md literal wording "via mariadb-migration-smoke.yml-CI-Workflow" is reframed per CONTEXT D-05 — Testcontainers-gated IT is the accepted implementation. |
| QUAL-04 | `BackupImportRollbackIT` stays passing (D-14: zero touch) | SATISFIED | Zero diff on the file; log confirms 2/2 tests pass |
| QUAL-05 | README section + GitHub Wiki page with Export/Import/Schema-Version/Recovery workflows | SATISFIED (pending image rendering post-merge) | README section complete; wiki live (HTTP 200); screenshots committed to main repo. Image embeds in wiki will 404 until PR merges to master — by design and documented. |

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `BackupRoundTripIT.java` | 452 | `@SuppressWarnings("unused")` on `awaitAuditRow()` helper | Info | Helper included for parity with `BackupImportMariaDbSmokeIT`; not used in the happy-path round-trip (intentional, follows CD-02 "speculative only if needed"). |

No `TBD`, `FIXME`, `XXX`, `HACK`, or unresolved debt markers found in any file changed during Phase 77.

---

### Out-of-Scope Compliance (D-15, D-16, D-19, D-20)

| Check | Expected | Status |
|-------|----------|--------|
| No milestone audit (`gsd-audit-milestone`) | Commits contain no milestone audit work | VERIFIED |
| No `gsd-complete-milestone` invocation | Commits contain no milestone closure | VERIFIED |
| No `pom.xml` version bump (`1.8.0-SNAPSHOT` stays) | `pom.xml` has zero diff; version confirmed `1.8.0-SNAPSHOT` | VERIFIED |
| No new Flyway migration | Zero diff under `src/main/resources/db/migration/` | VERIFIED |
| No new templates/CSS/controllers | Zero diff under `src/main/resources/templates/`, `src/main/java/.../controller/` | VERIFIED |
| No new Maven dependencies | `pom.xml` unchanged; SHA-256 via JDK `MessageDigest`; Testcontainers already wired | VERIFIED |
| No `77-HUMAN-UAT.md` shipped | File absent in phase directory | VERIFIED |

---

### Test Hotfix Scope (commit `53bc81e`)

The out-of-plan hotfix `test(77): isolate import-backups path + fix async dispatch (test hotfix)` is confined strictly to test files:

- `BackupControllerTest.java` — added `asyncDispatch` handling for `StreamingResponseBody` POST export test (correct fix: mock MVC requires two-step dispatch for streaming responses)
- `BackupImportConfirmFormValidationIT.java` — added `IMPORT_BACKUPS_ROOT` temp dir isolation + `@DynamicPropertySource` + `@AfterEach` cleanup
- `BackupImportExecuteIT.java` — same isolation pattern as above
- `BackupImportMariaDbSmokeIT.java` — added `IMPORT_BACKUPS_ROOT` isolation to `@DynamicPropertySource`

**Scope check:** Zero `src/main/` files changed. All changes are in `src/test/`. Pattern is legitimate test-isolation work (prevents same-second timestamp collision on `data/.import-backups/<ts>/auto-backup-before-import.zip`).

---

### Human Verification Required

#### 1. MariaDB Round-Trip IT — Local Run Confirmation

**Test:** Run `./mvnw -Ddocker.available=true -Dit.test="BackupRoundTripIT\$MariaDbRoundTripTests" verify` with Docker daemon running locally.

**Expected:** BUILD SUCCESS; `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`; assertions confirm row-count parity and SHA-256 byte-equality on Race + SeasonDriver + Team against a live MariaDB:11 Testcontainers instance.

**Why human:** The `@EnabledIfSystemProperty(named="docker.available", matches="true")` gate is intentional (CONTEXT D-05). This test is SKIPPED in CI by design — it requires a local Docker daemon. AUTO-UAT item 5 is marked `pending`. The verifier cannot execute Testcontainers in this environment.

**QUAL-02 implication:** The REQUIREMENTS literal wording "via mariadb-migration-smoke.yml-CI-Workflow" was explicitly reframed in CONTEXT D-05 as "Testcontainers in main ci.yml" — but since CI skips MariaDbRoundTripTests too (no `-Ddocker.available=true`), the only confirmation path is a local run. This local run must be executed before the PR merges.

#### 2. Screenshots Render in Wiki Post-Merge

**Test:** After PR merges to `master`, open `https://github.com/jegr78/ctc-manager/wiki/Backup-and-Restore` in a browser.

**Expected:** All 3 screenshots load successfully (not 404). URLs use `raw.githubusercontent.com/jegr78/ctc-manager/master/.screenshots/77/*.png` — they will resolve correctly once the branch is on `master`.

**Why human:** Screenshots are committed to `gsd/v1.10-platform-and-backup`, not yet on `master`. The wiki page is live but image embeds 404 until the PR merges. This is a known, accepted interim state (documented in 77-05-SUMMARY.md). Verification requires a post-merge browser check.

---

### Gaps Summary

No hard gaps blocking the phase goal — all automated truths pass. Two items require human confirmation:

1. **MariaDB round-trip local run** (AUTO-UAT item 5 pending) — the MariaDB `@Nested` class is correctly implemented and structurally gated, but the local Docker run has not been confirmed. This is the strongest outstanding item: QUAL-02's MariaDB half is unconfirmed at execution level.

2. **Wiki image embeds post-merge** — minor; will self-resolve when PR merges to master. The wiki page and screenshots are both delivered.

---

### Deferred Items

No items deferred to later phases. Phase 79 (milestone closer) is the planned next step per CONTEXT D-15.

---

## Recommendation

1. **Before PR merge:** Run the MariaDB round-trip IT locally (`./mvnw -Ddocker.available=true -Dit.test="BackupRoundTripIT\$MariaDbRoundTripTests" verify`) and update AUTO-UAT item 5 from `pending` to `passed` with evidence.

2. **After PR merge:** Confirm wiki image embeds render (AUTO-UAT items 1, 3, 4, 6) and update the AUTO-UAT checklist to reflect full `6/6 passed`.

3. **Phase 79:** Per CONTEXT D-15, proceed with the new Phase 79 (Code Cleanup + Test Performance Optimization + milestone audit `/gsd-audit-milestone` + `/gsd-complete-milestone`).

---

_Verified: 2026-05-15T09:55:00Z_
_Verifier: Claude (gsd-verifier)_

## VERIFICATION PASSED

All automated must-haves verified. Two items require human confirmation (MariaDB local run + post-merge wiki images) before the PR is merged — these do not block phase goal recognition but must be confirmed as part of the merge checklist.
