---
phase: 106-ci-pipeline-optimisation
plan: 01
status: complete
requirements: [CI-01, CI-03, CI-04, CI-05]
files_modified: [.github/workflows/ci.yml]
commit: 8413a28c
---

# 106-01 Summary — ci.yml rework

## What was built

Reworked `.github/workflows/ci.yml`:

- **`changes` job** (D-03): `dorny/paths-filter@v4.0.1`, `runs-on: ubuntu-latest`,
  job-level `permissions: pull-requests: read` (fork-PR API access, Pitfall 6),
  `outputs: code: ${{ steps.filter.outputs.code }}`. `code` filter = `src/**`,
  `pom.xml`, `Dockerfile`, `.github/workflows/**`, `mvnw`, `.mvn/**` (D-05, safe
  superset). `docs` filter (informational only) = `.planning/**`, `*.md`, `docs/**`,
  `!docs/site/**`, `.gitmessage` (D-04).
- **Step-gating** (D-03): `build-and-test`, `dockerfile-noble-pin-guard`, and
  `docker-build` all `needs: changes` and gate their expensive steps with
  `if: needs.changes.outputs.code == 'true'`. `actions/checkout` and both
  `if: always()` upload-artifact steps stay ungated → the three required checks
  always reach a real `success` on docs-only PRs (CI-01).
- **Single Maven run** (D-01): the two prior steps (`./mvnw verify` +
  `./mvnw verify -Pe2e`) collapsed into one "Build, Test, and E2E" step running
  `./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev
  -Ddocker.available=true` (CI-04 — non-E2E suite now runs once, not twice).
- **Buildx Docker cache** (D-07): `docker-build` now uses
  `docker/setup-buildx-action@v4.1.0` + `docker/build-push-action@v7.2.0` with
  `cache-from/to type=gha,scope=ctc-docker,mode=max` — isolated cache scope so it
  cannot evict the Maven/Playwright `actions/cache` namespace (Pitfall 4, CI-05).
  `needs: [changes, dockerfile-noble-pin-guard]` preserves the noble-pin dependency.

Playwright cache + install ordering (install BEFORE Maven) and all existing WHY
comment blocks preserved. No marker comments introduced.

## Verification

- `actionlint .github/workflows/ci.yml` → exit 0.
- Exactly one Maven invocation, and it is `./mvnw clean verify -Pe2e` (grep: 1
  `clean verify -Pe2e`, 1 total `./mvnw verify` = the same step, no standalone).
- All three required jobs declare `needs: changes`; `docker-build` declares
  `needs: [changes, dockerfile-noble-pin-guard]`.
- `actions/checkout` steps carry no `if:` guard; both `always()` uploads ungated.

## Deviations

None.

## Notes

Live-PR behaviour (docs-only success vs skipped, mixed-PR full run, warm cache,
runtime < 17:39) is the human checkpoint in plan 106-04 — not automatable here.
