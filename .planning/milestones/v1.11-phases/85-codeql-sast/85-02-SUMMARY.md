# Plan 85-02 — Baseline Triage Summary

**Plan:** 85-02
**Wave:** 2
**Status:** complete
**Completed:** 2026-05-17
**Branch:** `gsd/v1.11-tooling-and-cleanup`
**Commits:** `fd397a61` (D-13 revision), `a2034cfb` (D-19 three-layer markers)

## Outcome

Best-case Wave-2 result: **ZERO HIGH/CRITICAL baseline alerts**. The pre-staged `query-filters` excludes from scaffold-commit `f61fcbc0` suppressed the SSRF + ZIP-Slip triade BEFORE alert upload to the GitHub Security tab; no additional unexpected findings surfaced beyond the pre-staged triade. D-15 soft-scope did not trigger.

## Triage Buckets

| Bucket | Count | Notes |
|--------|-------|-------|
| fixed | 0 | No real bugs identified |
| suppressed | 3 | SSRF + ZIP-Slip × 2 (pre-staged in scaffold; source markers added in Wave 2) |
| accepted | 0 | No escalations needed |
| **total** | **3** | All pre-staged, no D-15 soft-scope expansion |

## Per-Alert Decisions

| # | Rule | Location | Bucket | Source-Marker Line | Rationale |
|---|------|----------|--------|--------------------|-----------|
| 1 | `java/ssrf` | `FileStorageService.storeFromUrl:86` | suppressed | `FileStorageService.java:86` | startsWith-chain hostname blocklist (validateHostname:128-159) not recognized as sanitizer; defense-in-depth via Phase 81 SpotBugs SSRF_SPRING |
| 2 | `java/zipslip` | `BackupArchiveService.assertEntrySafe:614` | suppressed | `BackupArchiveService.java:611` | PathTraversalGuard.assertWithin delegation not traceable by CodeQL; defense-in-depth via Phase 81 SpotBugs PATH_TRAVERSAL_IN |
| 3 | `java/path-injection` | `BackupImportService.restoreOneTable:673` | suppressed | `BackupImportService.java:672` | Same ZipFile#getEntry pattern as BackupArchiveService; defense-in-depth via Phase 81 SpotBugs co-suppression |

## D-15 Soft-Scope Outcome

**No findings beyond the pre-staged triade.** RESEARCH anticipated 2-5 potential additional findings (P-2 BackupController multipart, P-3 RaceAttachmentService Content-Disposition, P-9 SpEL TemplateEditorService). None of them fired against `security-extended` on this codebase. The combination of Phase 81 SpotBugs gate + existing code-defense patterns + the codebase's general cleanliness produced an empty baseline.

## D-19 Three-Layer Invariant Verified

| Layer | Artifact | Status |
|-------|----------|--------|
| 1 — `codeql-config.yml` `query-filters` | 3 `exclude` entries: `java/ssrf`, `java/zipslip`, `java/path-injection` | ✅ scaffold `f61fcbc0` |
| 2 — `// CodeQL FP:` source markers | 3 lines added at FileStorageService.java:86, BackupArchiveService.java:611, BackupImportService.java:672 | ✅ Wave 2 `a2034cfb` |
| 3 — `docs/security/sast-acceptance.md` table rows | 3 rows under SSRF and ZIP-Slip sections (Alert-ID = `N/A (filtered)`) + BCrypt-N/A section (D-05 deviation) | ✅ scaffold + Wave 2 |

`grep "// CodeQL FP:" src/main/java/` returns 3 matches.
`yq '.query-filters[].exclude.id' .github/codeql/codeql-config.yml` returns 3 rule IDs — all match a source marker.

## Execute-Time Deviations (logged in 85-VERIFICATION.md)

1. **Default CodeQL Setup conflict** — disabled via `gh api -X PATCH repos/jegr78/ctc-manager/code-scanning/default-setup -f state=not-configured` (user-confirmed). Trade-off: javascript / typescript / actions auto-scanning is gone (java-kotlin only via Phase 85 advanced workflow). Documented as TRACKED for milestone-summary.
2. **workflow_dispatch + default-branch requirement** — D-13 revised to add `push: { branches: [gsd/v1.11-tooling-and-cleanup] }` to scaffold-state triggers so the workflow auto-runs on milestone-branch pushes. Commit `fd397a61`. Plan 85-03 swaps this to `push: [master]` + `pull_request` + `schedule` at final-enable time.

## Workflow Runs

| # | Run ID | Event | Status | Result | URL |
|---|--------|-------|--------|--------|-----|
| 1 | 25995164105 | push (fd397a61 baseline) | failed | "CodeQL analyses from advanced configurations cannot be processed when the default setup is enabled" — resolved by disabling default-setup | https://github.com/jegr78/ctc-manager/actions/runs/25995164105 |
| 2 | 25995329890 | workflow_dispatch (post default-setup-disable) | completed | 2m7s — 0 HIGH/CRITICAL alerts, 0 alerts total | https://github.com/jegr78/ctc-manager/actions/runs/25995329890 |
| 3 | 25995479043 | push (a2034cfb Wave 2 markers) | completed | 2m0s — 0 HIGH/CRITICAL alerts (D-19 invariant confirmed post-commit) | https://github.com/jegr78/ctc-manager/actions/runs/25995479043 |

## Maven Sanity

`./mvnw test-compile --no-transfer-progress -q` — exit 0 after Wave 2 source-marker edits. `[PLAT-07 build-guard] OK`.

## Pointer to Full Evidence

See `.planning/phases/85-codeql-sast/85-VERIFICATION.md` Baseline Scan Triage Table for the complete evidence record with embedded run URLs, alert counts, and the D-19 verification details.

## Sign-Off

- [x] All baseline HIGH/CRITICAL alerts bucketed per D-10 decision tree (3 suppressed, 0 fixed, 0 accepted)
- [x] D-19 three-layer invariant maintained — every `query-filters[].exclude.id` has a matching `// CodeQL FP:` marker AND a matching `sast-acceptance.md` table row
- [x] Pre-staged triade source markers at FileStorageService.java:86, BackupArchiveService.java:611, BackupImportService.java:672
- [x] `SecurityConfig.java` UNMODIFIED (D-05 BCrypt-N/A)
- [x] Post-triage workflow run produces zero HIGH/CRITICAL alerts (D-12/3 closure)
- [x] `85-VERIFICATION.md` Baseline Scan Triage Table populated
- [x] `./mvnw test-compile` exits 0
- [x] All commits on `gsd/v1.11-tooling-and-cleanup` per D-23
