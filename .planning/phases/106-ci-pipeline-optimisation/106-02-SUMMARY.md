---
phase: 106-ci-pipeline-optimisation
plan: 02
status: complete
requirements: [CI-02]
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

## Deviations

- **Pre-existing actionlint finding on the sacred file (not introduced here):**
  `actionlint .github/workflows/mariadb-migration-smoke.yml` exits 1 with a
  shellcheck SC2086 nit at line 74 ("Double quote to prevent globbing"). The file is
  byte-identical to its committed state (`UNCHANGED-OK`), so this predates Phase 106
  and is surfaced only because the locally-installed actionlint (1.7.12) bundles
  shellcheck. The file is marked "sacred — do NOT modify the services block or
  verification steps" (CLAUDE.md memory + file header), so it is left unchanged. The
  plan's "actionlint exits 0" criterion is interpreted per its stated intent ("confirm
  it remains valid and was not accidentally altered") — accidental alteration is ruled
  out by the unchanged diff. Fixing the SC2086 nit, if desired, belongs in a dedicated
  task that re-baselines the sacred file, not this docs-skip plan.

## Notes

Live verification that codeql/mariadb-smoke are absent from a docs-only PR's checks
is the human checkpoint in plan 106-04.
