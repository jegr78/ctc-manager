# UAT-02: Legacy Season Visual Smoke (Phase 83 QUAL-05)

## Purpose

Verify that legacy pre-V4 production season data (seasons created and persisted before the v1.9 Season-Phases-and-Groups schema migration) renders correctly on production after the v1.11 deploy.

This is a post-deploy manual smoke test. It cannot be auto-executed during phase work because no staging environment mirrors the real pre-V4 production dataset (see `.planning/phases/83-quality-and-polish-sweep/83-VALIDATION.md` "Manual-Only Verifications" table).

## Pre-Conditions

- v1.11 has been deployed to production (`./mvnw spring-boot:run -Dspring-boot.run.profiles=prod` or the equivalent Docker image is live).
- The production database contains at least one season originally created on schema versions ≤ V3 (i.e. pre-Phase-Groups migration). Confirm via DB: `SELECT id, name, year, number FROM seasons WHERE created_at < '2026-05-01' LIMIT 5;`.
- The operator has admin credentials for the production instance.
- A clean browser session (Chromium recommended) with developer tools available.

## Procedure

Replace `<PRODUCTION_BASE_URL>` with the live production base URL at execution time (e.g. `https://ctc-manager.example.com`).

1. Open `<PRODUCTION_BASE_URL>/admin/seasons`. Confirm the seasons list loads without an error banner.
2. Pick at least one season whose `year < 2026` (legacy pre-V4). Open `<PRODUCTION_BASE_URL>/admin/seasons/{id}`. Save a screenshot to `.screenshots/uat-02/season-{id}-detail.png`.
3. From the season detail page, navigate to the regular phase. Verify the standings table loads and contains at least one row (or the documented "no standings yet" empty state — no stack trace, no `LazyInitializationException`).
4. Save a screenshot of the standings page to `.screenshots/uat-02/season-{id}-standings.png`.
5. Open `<PRODUCTION_BASE_URL>/admin/drivers`. Pick at least one driver who participated in multiple seasons (chip count ≥ 2 on the detail page).
6. Open `<PRODUCTION_BASE_URL>/admin/drivers/{id}`. Save a screenshot to `.screenshots/uat-02/driver-{id}-detail.png`.
7. Open Chromium DevTools → Console tab. Reload each page touched above and capture any errors or warnings to `.screenshots/uat-02/console-log.txt`.
8. Compare each screenshot against the corresponding pre-deploy reference image (taken from the previous milestone PR review) and note any visual regressions.

## Pass Criteria

- All legacy season chips on the seasons list are visible without rendering errors.
- No `lazy-init exception` or `LazyInitializationException`-like text appears in the browser console or rendered HTML on any visited page.
- The standings page loads for at least one pre-V4 season (rows present, or documented empty state).
- Driver detail page chips on `/admin/drivers/{id}` for at least one multi-season driver render in ascending year order (verifies QUAL-01 regression on legacy data).
- DevTools Console contains no new error-level entries compared to the pre-deploy reference run.

## Fail Handling

If any pass criterion fails:

1. Capture the failing screenshot plus the full DevTools Console log to `.screenshots/uat-02/fail-{timestamp}/`.
2. Identify the v1.11 commit suspected of introducing the regression. Inspect `gh pr view <milestone-pr>` and walk commits in reverse chronological order using `git log --oneline gsd/v1.11-tooling-and-cleanup` against the failing entity (Driver, Season, SeasonPhase, etc.).
3. File a follow-up phase (e.g. `phase 88 — UAT-02 regression rollback`) on the next milestone roadmap. The phase must reproduce the failure with a Test-prefix fixture before any production rollback decision.
4. If a rollback is required, prepare a hotfix branch from the last known-good production tag rather than reverting on `master` directly.

## Result Template

The operator fills this block after executing the procedure and copies it into the `.planning/STATE.md` result-slot referenced under "Recording Location" below.

```markdown
- **Date / Time:** _(operator fills, ISO-8601)_
- **Executor:** _(operator name)_
- **Outcome:** _(Pass / Fail)_
- **Screenshots:** `.screenshots/uat-02/`
- **Notes:** _(operator notes — observed warnings, deviations from reference, console anomalies)_
```

If the procedure has not yet been executed, the slot stays as `pending — to be executed after v1.11 production deploy`.

## Recording Location

The result of this UAT is recorded in `.planning/STATE.md` under the `## Pending UATs` section, in the `### UAT-02: Legacy Season Visual Smoke (Phase 83 QUAL-05)` subsection. The operator must update that slot in the same commit that closes the v1.11 milestone PR.
