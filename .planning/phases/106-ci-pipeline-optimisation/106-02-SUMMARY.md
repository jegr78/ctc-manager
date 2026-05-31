---
phase: 106-ci-pipeline-optimisation
plan: 02
status: complete
requirements: [CI-02]
requirements-completed: [CI-02]
files_modified: [.github/workflows/codeql.yml]
commit: dd07fe89
---

# 106-02 Summary — codeql.yml paths-ignore + mariadb-smoke no-op

## What was built

- **Task 1 (codeql.yml):** Added a `paths-ignore` block to both `on.push` and
  `on.pull_request` (each keeps `branches: [master]`) listing exactly `.planning/**`,
  `*.md`, `docs/**`, `.gitmessage` (D-04). Workflow-level `paths-ignore` uses the
  full `docs/**` — the `!docs/site/**` negation is only a dorny/paths-filter
  construct, not valid here (RESEARCH Pattern 3). `schedule` (cron) and
  `workflow_dispatch` left with NO path filter so scheduled/manual scans still cover
  the whole tree. The `analyze` job, `Initialize CodeQL`, `Build for CodeQL`, and the
  "Gate on new HIGH/CRITICAL security alerts" step (+ its
  `if: github.event_name != 'schedule'`) are byte-for-byte unchanged.

- **Task 2 (mariadb-migration-smoke.yml):** VERIFICATION ONLY — no edit. Confirmed
  verdict: **D-06 for mariadb-migration-smoke.yml is a confirmed NO-OP.** Its existing
  `paths:` inclusion filter (`src/main/resources/db/migration/**`,
  `src/main/java/db/migration/**`, `src/main/resources/application*.yml`, `pom.xml`,
  `.github/workflows/mariadb-migration-smoke.yml`) already means any docs-only change
  in the D-04 ignore set NEVER triggers the workflow. Because `paths:` and
  `paths-ignore:` are mutually exclusive, adding a second filter would be invalid and
  unnecessary. File left untouched (`git diff --quiet` → UNCHANGED-OK).

## Verification

- `actionlint .github/workflows/codeql.yml` → exit 0.
- `codeql.yml` has 2 `paths-ignore` blocks; `paths:` not used on either trigger
  (mutual-exclusivity respected); gate-step + `if:` unchanged.
- `git diff --quiet .github/workflows/mariadb-migration-smoke.yml` → UNCHANGED-OK.

## Deviations / Follow-up

- **Pre-existing shellcheck SC2086 surfaced + fixed by user agreement (separate commit).**
  During the Task 2 actionlint verification, the locally-installed actionlint (1.7.12,
  bundled shellcheck — GitHub CI runs no shellcheck over this file) flagged a
  **pre-existing** SC2086 (info) at the app-start step: `>> $GITHUB_ENV` unquoted. The
  file was byte-identical at that point (`UNCHANGED-OK`), so this predated Phase 106.
  The file is marked "sacred", so rather than touch it unilaterally I surfaced the
  finding and the proposed fix to the user. The user approved fixing it now as a
  standalone commit (not folded into the docs-skip plan). Fix applied: quote the
  redirect target → `>> "$GITHUB_ENV"` (no behavioural change; GITHUB_ENV is a
  GitHub-provided path without spaces/globs). The services block and Flyway
  verification steps remain untouched. After the fix,
  `actionlint .github/workflows/mariadb-migration-smoke.yml` exits 0.
  Commit: `5c32b557` (`ci: quote $GITHUB_ENV in mariadb-migration-smoke (shellcheck SC2086)`).
  Note: this commit edits the file Task 2 declared a no-op — the D-06/paths no-op
  verdict still stands (no `paths`/`paths-ignore` change was made); only the unrelated
  SC2086 style nit was corrected.

## Notes

Live verification that codeql/mariadb-smoke are absent from a docs-only PR's checks
is the human checkpoint in plan 106-04.
