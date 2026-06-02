# Phase 106: CI Pipeline Optimisation — Research

**Researched:** 2026-05-30
**Domain:** GitHub Actions CI configuration (paths-filter, Docker GHA cache, Maven lifecycle)
**Confidence:** HIGH (all critical decisions verified against official docs or primary sources)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01 (CI-04):** Collapse the double Maven run in `build-and-test` into a single `./mvnw clean verify -Pe2e`. Today the job runs `./mvnw verify` then `./mvnw verify -Pe2e` — the non-E2E suite executes twice. Surefire (unit) runs in `test` before Failsafe (IT + E2E) in `integration-test`/`verify`, so unit failures still abort before E2E — fail-fast is preserved.

**D-02 (CI-04):** Concrete E2E median target ("below 17:39") is set at plan time after measuring the single-run median — not pre-committed in context.

**D-03 (CI-01):** Add a `changes` job using `dorny/paths-filter`. The three required jobs (`build-and-test`, `dockerfile-noble-pin-guard`, `docker-build`) ALWAYS run; their expensive steps are guarded with `if: needs.changes.outputs.code == 'true'`. On a docs-only PR the jobs no-op but still report a real `success` status under their unchanged check names. Job-level `if:` approach was rejected due to branch-protection name-mapping fragility.

**D-04 (CI-02/03):** Docs/planning ignore set: `.planning/**`, root `*.md`, `docs/**` (excluding `docs/site/**`), `.gitmessage`.

**D-05 (CI-02/03):** Always-trigger set: `.github/workflows/**`, `pom.xml`, `Dockerfile`, `src/**`, `mvnw`, `.mvn/**`.

**D-06 (CI-02):** Non-required workflows use top-level `paths-ignore`: `codeql.yml` (add `paths-ignore`) and `mariadb-migration-smoke.yml` (reconcile to same ignore semantics). `deploy-site.yml` (`paths: ['docs/site/**']`) MUST remain untouched.

**D-07 (CI-05):** Add `docker/setup-buildx-action` + `cache-from`/`cache-to: type=gha` to `docker-build` job, using an **isolated cache scope key** so it does not evict Maven and Playwright caches. Benefit applies on code PRs only (docker-build is gated to code changes via D-03).

**D-08 (CI-06):** Defensive scope — no `rerunFailingTestsCount` in pom.xml (confirmed). CI-06 ensures no rerun-count/retry action is ever introduced as a symptom hotfix. Add a lightweight guard and document the quarantine policy.

### Claude's Discretion

- Exact `dorny/paths-filter` filter syntax and output-name wiring
- Whether the `changes` job is a separate job or folded into a reusable filter step
- Concrete `cache-to` mode (`type=gha,mode=max` vs `min`) and scope key string
- Whether the CI-06 guard is a pom enforcer rule, a grep-based workflow guard, or doc-only

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope (CI configuration only).
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CI-01 | `ci.yml` expensive steps run only when code/build files change; required checks always report a status so PRs never deadlock — implemented via `dorny/paths-filter` `changes` job + conditional steps | D-03: step-level `if:` approach confirmed via GitHub docs; jobs always run, steps no-op → genuine `success` |
| CI-02 | Non-required workflows (`codeql.yml`, `mariadb-migration-smoke.yml`) skip docs/planning-only changes via `paths-ignore` | D-06: confirmed `paths-ignore` on non-required workflows is safe; confirmed neither is in required-check set |
| CI-03 | Ignore set defined and applied consistently; mixed code+docs commit still runs full CI | D-04/05: filter syntax with negation (`!docs/site/**`) verified via dorny/paths-filter v4 docs |
| CI-04 | E2E runtime reduced below 17:39 median | D-01: single `./mvnw clean verify -Pe2e` eliminates double Surefire+Failsafe run; Maven lifecycle ordering confirmed |
| CI-05 | Build caching improved for Docker layer caching | D-07: `docker/setup-buildx-action@v4` + `docker/build-push-action@v7` with `type=gha,scope=ctc-docker,mode=max`; isolated scope confirmed |
| CI-06 | No `rerunFailingTestsCount` / retry config ever added; quarantine policy documented | D-08: confirmed 0 occurrences in pom.xml; build guard modelled on existing PLAT-07 and CLEAN-01 patterns |
</phase_requirements>

---

## Summary

Phase 106 modifies only `.github/workflows/*.yml` and `pom.xml` CI configuration. The core work is four independent changes: (1) collapse the double Maven invocation into one `./mvnw clean verify -Pe2e` pass; (2) add a `dorny/paths-filter` `changes` job whose boolean outputs gate expensive steps inside always-running required jobs; (3) add `paths-ignore` to two non-required workflows; and (4) add Docker layer caching via `docker/setup-buildx-action` + `docker/build-push-action` with an isolated GHA cache scope.

The most critical research finding is the **required-check semantics**. A job whose `if:` condition evaluates to `false` reports `skipped`, which GitHub treats as `success` for branch protection — however, this produces a non-deterministic result: the check name disappears from the PR checks list if the job never ran (behaviour differs by GitHub plan/version). The locked decision (D-03) to keep jobs always running and gate only expensive *steps* is the safe, well-documented pattern. When only steps are no-op, the job completes with real `success` under its original check name, and branch protection is satisfied unconditionally.

The Docker GHA cache and `actions/cache` (Maven, Playwright) share the same 10 GB per-repo budget. The `scope=` parameter on `type=gha` does NOT share a key namespace with `actions/cache` keys — they are separate cache backends. `scope` is purely internal to the Docker/BuildKit GHA backend and does not risk evicting Maven or Playwright entries. `mode=max` is recommended for the multi-stage Dockerfile because it caches intermediate layers (JDK build stage), giving the greatest speedup on re-runs.

**Primary recommendation:** Implement all four changes as discrete, testable tasks. Wire `changes` job outputs first (task 1), then gate steps in all three required jobs (task 2), then add `paths-ignore` to non-required workflows (task 3), then add Docker buildx cache (task 4), then add the CI-06 guard (task 5).

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Path-change detection | CI orchestration (changes job) | — | Centralised detection avoids duplicating filter logic per job |
| Required-check gating | CI step level (if: inside job) | — | Job-level gating reports `skipped` not `success`; step-level keeps required check name alive |
| Maven lifecycle ordering | Build tool (Maven) | — | Lifecycle is deterministic: `test` → `integration-test` → `verify`; no CI-layer ordering needed |
| Docker layer caching | CI step (docker/build-push-action + GHA cache backend) | — | GHA cache backend is the only correctly auto-wired option for GitHub-hosted runners |
| Retry/rerun prevention | Build tool guard (pom.xml validate phase) | CI policy doc | Build-time enforcement is stronger than workflow-level assertion |

---

## Standard Stack

### Core (GitHub Actions — all already used in ci.yml or referenced by decisions)

| Action | Version | Purpose | Why Standard |
|--------|---------|---------|--------------|
| `dorny/paths-filter` | `v4.0.1` [VERIFIED: github.com/dorny/paths-filter/releases] | Classify changed files into named boolean outputs | The canonical paths-filter action; v4 upgraded to Node 24 (required by GH, Node 20 deprecated March 2026) |
| `docker/setup-buildx-action` | `v4.1.0` [VERIFIED: github.com/docker/setup-buildx-action] | Configure Docker Buildx builder | Required to enable `type=gha` cache backend on GitHub-hosted runners |
| `docker/build-push-action` | `v7.2.0` [VERIFIED: github.com/docker/build-push-action/releases] | Build Docker image with cache-from/cache-to | Auto-wires `ACTIONS_RESULTS_URL` and `ACTIONS_RUNTIME_TOKEN` for GHA cache API v2; plain `docker build` requires manual variable exposure |
| `actions/cache` | `v4` [ASSUMED] | Maven dependency cache, Playwright browser cache | Already in ci.yml; no change |
| `actions/setup-java` | `v5` [ASSUMED] | JDK + Maven dependency cache | Already in ci.yml with `cache: 'maven'`; no change |

### Confirmed Already Present (no change required)

| Component | Version | Notes |
|-----------|---------|-------|
| Playwright browser cache | `actions/cache@v4`, key `${{ runner.os }}-playwright-1.59.0` | Keep as-is; ordering invariant documented in ci.yml comments |
| Maven dependency cache | `actions/setup-java@v5` with `cache: 'maven'` | Keep as-is |

### Package Legitimacy Audit

> This phase installs no Java dependencies. The only new items are GitHub Actions action references. slopcheck is not applicable (Python tool for npm/PyPI packages). slopcheck installation was blocked by the environment sandbox; all action references below are tagged [ASSUMED] except where verified from official sources.

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `dorny/paths-filter@v4.0.1` | GitHub Actions Marketplace | ~5 yrs | Very high (millions of workflow runs) | github.com/dorny/paths-filter | N/A (not npm/PyPI) | Approved [VERIFIED: github.com/dorny/paths-filter/releases] |
| `docker/setup-buildx-action@v4.1.0` | GitHub Actions Marketplace | ~5 yrs | Very high | github.com/docker/setup-buildx-action | N/A | Approved [VERIFIED: github.com/docker/setup-buildx-action] |
| `docker/build-push-action@v7.2.0` | GitHub Actions Marketplace | ~5 yrs | Very high | github.com/docker/build-push-action | N/A | Approved [VERIFIED: github.com/docker/build-push-action/releases] |

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

---

## Architecture Patterns

### System Architecture Diagram

```
Push / Pull Request
        │
        ▼
┌─────────────────────────────────────────────────────────┐
│  changes job  (dorny/paths-filter@v4.0.1)               │
│  outputs: code='true'|'false'                           │
│  filter: code = src/**, pom.xml, .github/**, Dockerfile │
│          mvnw, .mvn/**                                   │
│  filter: docs = .planning/**, *.md, docs/** !docs/site  │
└─────────────┬───────────────────────────────────────────┘
              │ needs: changes
    ┌─────────┼─────────────────────┐
    ▼         ▼                     ▼
build-and-test  dockerfile-noble-  docker-build
(always runs)   pin-guard          (needs: dockerfile-...)
                (always runs)      (always runs)
    │
    ├─[if code=='true'] Setup JDK + Playwright
    ├─[if code=='true'] Install Playwright browsers
    ├─[if code=='true'] ./mvnw clean verify -Pe2e
    ├─[if code=='true'] JaCoCo PR comment
    └─[always] upload-artifact (test/coverage reports)
    
    dockerfile-noble-pin-guard:
    ├─[if code=='true'] grep Dockerfile noble-pin check
    └─(trivial step — fast even when code='true')
    
    docker-build:
    ├─[if code=='true'] docker/setup-buildx-action@v4.1.0
    ├─[if code=='true'] docker/build-push-action@v7.2.0
    │   cache-from: type=gha,scope=ctc-docker
    │   cache-to:   type=gha,scope=ctc-docker,mode=max
    │   push: false  (CI smoke build, no registry push)
    └─[always] upload-artifact (on failure)
    
Non-required workflows (separate files):
codeql.yml:         paths-ignore: .planning/**, *.md, docs/** !docs/site/**
mariadb-smoke.yml:  paths already set → reconcile to same ignore semantics
deploy-site.yml:    UNTOUCHED (paths: ['docs/site/**'])
```

### Pattern 1: `changes` Job with Step-Level Gating

**What:** A separate `changes` job runs `dorny/paths-filter` and exposes a boolean `code` output. The three required jobs declare `needs: changes` and gate their expensive steps with `if: needs.changes.outputs.code == 'true'`. Cheap steps (`actions/checkout`, upload-on-failure) remain ungated so the job always exits with `success`.

**Why step-level, not job-level:** A job skipped via job-level `if: needs.changes.outputs.code == 'true'` technically also reports `success` for branch protection according to GitHub docs — but in practice the check name may not appear at all in the PR checks list when the job never ran, creating ambiguity. Step-level gating is the universally safe pattern: the job always runs, always emits a check under the expected name, and branch protection is unambiguously satisfied. [CITED: docs.github.com/en/actions/using-jobs/using-conditions-to-control-job-execution]

**Example:**
```yaml
# Source: dorny/paths-filter v4.0.1 README + GitHub Actions docs
jobs:
  changes:
    runs-on: ubuntu-latest
    permissions:
      pull-requests: read
    outputs:
      code: ${{ steps.filter.outputs.code }}
    steps:
      - uses: actions/checkout@v6
      - uses: dorny/paths-filter@v4.0.1
        id: filter
        with:
          filters: |
            code:
              - 'src/**'
              - 'pom.xml'
              - 'Dockerfile'
              - '.github/workflows/**'
              - 'mvnw'
              - '.mvn/**'

  build-and-test:
    needs: changes
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - name: Setup JDK 25
        if: needs.changes.outputs.code == 'true'
        uses: actions/setup-java@v5
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: 'maven'
      # ... all expensive steps gated with same if condition
```

### Pattern 2: `dorny/paths-filter` Negation for docs/** but not docs/site/**

**What:** `paths-filter` supports negation with the `!` prefix. A filter line starting with `!` excludes paths that would otherwise match.

**Note on `paths` vs `paths-ignore` mutual exclusivity:** GitHub's `on.push/pull_request` trigger-level `paths` and `paths-ignore` are **mutually exclusive** — you cannot use both for the same event in one workflow. To exclude a subdirectory from an inclusion pattern (e.g., `docs/**` but not `docs/site/**`), you must use `paths` with a `!docs/site/**` exclusion line, NOT `paths-ignore`. This is relevant for D-06 on `codeql.yml`. [CITED: docs.github.com GitHub Actions workflow syntax]

**Example for `changes` job filter (docs filter, informational):**
```yaml
# Source: dorny/paths-filter v4.0.1 README
filters: |
  code:
    - 'src/**'
    - 'pom.xml'
    - 'Dockerfile'
    - '.github/workflows/**'
    - 'mvnw'
    - '.mvn/**'
  docs:
    - '.planning/**'
    - '*.md'
    - 'docs/**'
    - '!docs/site/**'
    - '.gitmessage'
```

The `code` filter is the one used in step `if:` conditions. The `docs` filter is informational — when `code == 'false'` the PR is docs-only.

**Cross-job output reference:** Downstream jobs access outputs as `needs.changes.outputs.code`. The job must declare `outputs: code: ${{ steps.filter.outputs.code }}` and downstream jobs must declare `needs: changes`. [VERIFIED: github.com/dorny/paths-filter]

### Pattern 3: `paths-ignore` on Non-Required Workflows

**What:** `codeql.yml` and `mariadb-migration-smoke.yml` are NOT in the required-check set. Adding `paths-ignore` at the `on.push/pull_request` level safely skips these workflows on docs-only commits — no branch-protection deadlock risk because no check is expected.

**`codeql.yml` change:** Currently has no path filter. Add `paths-ignore` under both `push` and `pull_request` triggers:
```yaml
on:
  push:
    branches: [master]
    paths-ignore:
      - '.planning/**'
      - '*.md'
      - 'docs/**'
      - '.gitmessage'
  pull_request:
    branches: [master]
    paths-ignore:
      - '.planning/**'
      - '*.md'
      - 'docs/**'
      - '.gitmessage'
  schedule:
    - cron: '0 2 * * 0'        # cron stays untouched — no path filter
  workflow_dispatch:             # manual dispatch stays untouched
```

Note: `docs/site/**` does not need explicit exclusion in `paths-ignore` because `docs/**` already covers the entire `docs/` tree. The `!docs/site/**` negation is only needed when using the `paths` inclusion syntax inside `dorny/paths-filter` filters. For workflow-level `paths-ignore` the full `docs/**` is correct. [CITED: docs.github.com GitHub Actions workflow syntax]

**`mariadb-migration-smoke.yml` change:** Already uses `paths:` (inclusion-based trigger). D-06 says "reconcile to same ignore semantics". The reconciliation means keeping the existing `paths:` trigger (which only fires on migration/config changes) but ensuring that docs-only changes don't trigger it — the existing `paths:` filter already achieves this since it only triggers on `src/main/resources/db/**`, `application*.yml`, `pom.xml`, and the workflow file itself. Research finding: the existing `paths:` trigger already achieves the D-06 intent. The "reconcile" task may be a no-op verification rather than a change. [ASSUMED — requires planner to confirm semantics match D-04 ignore set]

### Pattern 4: Docker GHA Cache with Isolated Scope

**What:** Replace the plain `docker build` command in `docker-build` job with `docker/setup-buildx-action@v4.1.0` + `docker/build-push-action@v7.2.0` with GHA cache. Use `scope=ctc-docker` to isolate from other entries.

**GHA cache budget (10 GB):**
- `actions/cache@v4` (Maven + Playwright) and `type=gha` Docker cache are **different backends**. The `scope=` parameter is internal to the Docker BuildKit GHA backend and does not share the key namespace with `actions/cache` keys. However, both count against the same 10 GB repository cache quota. [CITED: docs.docker.com/build/cache/backends/gha/]
- Playwright browser cache: ~360 MiB (Chromium + Firefox + WebKit per ci.yml comment)
- Maven dependency cache: typically 200–400 MiB for a Spring Boot project
- Docker GHA cache `mode=max`: A full multi-stage build image for this project is in the 500–900 MiB range based on the JDK/JRE base sizes; `mode=max` caches intermediate layers too. Estimated Docker cache footprint: 500–1500 MiB.
- Total estimated usage: well within 10 GB limit. [ASSUMED — exact Docker image size not measured; planner should verify against actual build log]

**`mode=max` vs `mode=min`:** For a multi-stage Dockerfile (JDK build stage + JRE runtime stage), `mode=max` caches all intermediate layers. The JDK compile stage is the most expensive layer; caching it avoids recompilation on re-runs. `mode=min` only caches the final image layers — the JDK stage is lost and recompiled. Recommendation: `mode=max`. [CITED: docs.docker.com/build/cache/backends/gha/]

**The `push: false` constraint:** The `docker-build` CI job is a smoke build — it does not push to a registry. `docker/build-push-action` supports `push: false` for local builds. The GHA cache (`cache-from`/`cache-to`) works regardless of whether `push: true` or `false`. [CITED: docs.docker.com/build/ci/github-actions/cache/]

**Example:**
```yaml
# Source: docs.docker.com/build/ci/github-actions/cache/ + setup-buildx-action docs
- name: Set up Docker Buildx
  if: needs.changes.outputs.code == 'true'
  uses: docker/setup-buildx-action@v4.1.0

- name: Build Docker image (full multi-stage, no push)
  if: needs.changes.outputs.code == 'true'
  uses: docker/build-push-action@v7.2.0
  with:
    context: .
    push: false
    tags: ctc-manager:ci-${{ github.sha }}
    cache-from: type=gha,scope=ctc-docker
    cache-to: type=gha,scope=ctc-docker,mode=max
```

The existing inline `docker build` bash step (which also runs `docker image inspect` and logs) should be replaced. The failure-upload artifact step stays as-is.

### Pattern 5: Maven Single-Lifecycle Ordering

**What:** `./mvnw clean verify -Pe2e` runs Surefire (unit tests) in the `test` phase, then Failsafe `default-it` (integration, `@Tag("integration")`) in `integration-test`/`verify`, then Failsafe `e2e-it` (Playwright E2E, `@Tag("e2e")`) in the `-Pe2e` profile's parallel execution binding.

**How fail-fast is preserved:** Maven lifecycle is sequential within a single invocation. Surefire unit test failures in the `test` phase abort the build before Failsafe's `integration-test` phase begins. IT failures abort before `e2e-it`. E2E tests run last and are the most expensive. If unit tests fail, E2E never starts. [CITED: maven.apache.org/surefire/maven-failsafe-plugin/]

**Current double-run situation in ci.yml (to be fixed by D-01):**
```
Step 1: ./mvnw verify           → runs: compile + test (Surefire) + default-it (Failsafe IT)
Step 2: ./mvnw verify -Pe2e     → runs: compile + test (Surefire again) + default-it (Failsafe IT again) + e2e-it (E2E)
```
After D-01:
```
Step 1: ./mvnw clean verify -Pe2e → runs: compile + test + default-it + e2e-it  (each suite runs once)
```

The `clean` in the single invocation replaces the implicit clean that happened between the two separate `./mvnw` calls. [ASSUMED — the second `./mvnw verify -Pe2e` implicitly recompiles but does not `clean`; the new single invocation adds `clean` explicitly per CLAUDE.md discipline]

**`-Pe2e` profile specifics from pom.xml (verified):**
- Activates a second Failsafe execution `e2e-it` bound to `integration-test`/`verify`
- `<groups>e2e</groups>` — only `@Tag("e2e")` tests
- `<includes>**/e2e/**/*Test.java</includes>` — filename pattern to reach E2E test classes (not `*IT.java` naming)
- Single fork (no `forkCount`) — Playwright requires a single Spring context per RANDOM_PORT
- The default Failsafe `default` execution is bound to `<phase>none</phase>` to avoid Spring Boot parent's anonymous execution conflicting with the explicit `default-it` binding [VERIFIED: pom.xml lines 319-330]

**JaCoCo timing:** JaCoCo `report` and `check` goals are bound to the `verify` phase in the default lifecycle. They run after both `default-it` and `e2e-it` complete. The `madrapps/jacoco-report` step must remain after the single Maven invocation. [VERIFIED: pom.xml JaCoCo configuration]

### Pattern 6: CI-06 Guard — Pom Build Guard (Recommended)

**What:** Add an `exec-maven-plugin` execution in the `validate` phase that greps the pom.xml for `rerunFailingTestsCount` and fails the build if found. This follows the established project pattern of PLAT-07 (Thymeleaf fragment guard) and CLEAN-01 (JUnit Assumptions guard). [VERIFIED: pom.xml lines 455-505]

**Why pom-guard over workflow-guard:** The pom-guard fires on every `./mvnw verify` locally and in CI — it's impossible to bypass without editing the pom. A workflow-only grep would only run in CI and would not catch a developer adding rerun config locally.

**Example:**
```xml
<!-- Source: existing PLAT-07 pattern in pom.xml -->
<execution>
  <id>no-rerun-failsafe-guard</id>
  <phase>validate</phase>
  <goals><goal>exec</goal></goals>
  <configuration>
    <executable>bash</executable>
    <arguments>
      <argument>-c</argument>
      <argument><![CDATA[
if grep -E 'rerunFailingTestsCount|retryCount' pom.xml 2>/dev/null; then
  echo "[CI-06 build-guard] FAIL: rerunFailingTestsCount or retryCount detected in pom.xml.";
  echo "Symptom-hotfixes are forbidden. Fix the root cause. See CLAUDE.md 'No Flaky Dismissal'.";
  exit 1;
fi;
echo "[CI-06 build-guard] OK - no rerun/retry config in pom.xml.";
exit 0;
]]></argument>
        </arguments>
      </configuration>
    </execution>
```

**Quarantine policy doc:** A `docs/ci/FLAKY-TEST-POLICY.md` (or inline in CLAUDE.md or `.planning/codebase/TESTING.md`) should document: (1) `@Tag("flaky")` is the only accepted suppression; (2) it requires a root-cause investigation within one sprint; (3) `rerunFailingTestsCount` / GH Actions retry steps are permanently forbidden; (4) a flaky tag without a linked issue is a build smell.

### Anti-Patterns to Avoid

- **Job-level `if:` on required checks:** Technically `skipped` reports as `success`, but the check may not appear in the PR checks UI, creating confusion. Use step-level gating instead for required jobs.
- **Adding `paths-ignore` to `ci.yml` (the required workflow):** This causes the workflow to skip entirely on docs-only PRs, leaving required checks in `Pending` state and deadlocking merges. The `changes` job + step-level gating is the correct approach.
- **Using `paths` and `paths-ignore` together in one workflow trigger:** They are mutually exclusive per GitHub docs. Use `paths` with `!` negation to achieve both inclusion and exclusion.
- **Using `mode=min` for Docker GHA cache:** For a multi-stage build, `mode=min` only caches the final image layers. The expensive JDK compile stage gets recompiled on every run. Always use `mode=max` for multi-stage Dockerfiles.
- **Plain `docker build` with `--cache-from type=gha`:** Plain `docker build` does not automatically expose `ACTIONS_RESULTS_URL` and `ACTIONS_RUNTIME_TOKEN`. Use `docker/build-push-action` which handles this automatically. [CITED: docs.docker.com/build/cache/backends/gha/]
- **Omitting `scope=` from the Docker GHA cache:** Without `scope=`, the key defaults to `buildkit`, which would be overwritten by any other Docker build in the repo (e.g., release workflow). Always use a specific scope like `scope=ctc-docker`.
- **Removing `checkout` from the no-op path:** `actions/checkout` must remain ungated in all three required jobs — it is needed even on docs-only PRs so the job can run and report success.
- **Adding `--no-transfer-progress` to the single Maven invocation:** This flag is already in the existing Maven steps and should be preserved in the consolidated step.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Detect which files changed | Custom `git diff` script in workflow | `dorny/paths-filter@v4.0.1` | Handles PR vs push context, merge bases, symlinks, renamed files, fork PRs with token fallback |
| Docker layer caching | Custom `--cache-to type=local` with manual restore | `docker/build-push-action@v7.2.0` with `type=gha` | Auto-wires GHA cache API v2 credentials; plain `docker build` requires manual env var exposure |
| Ensure tests run before E2E | Explicit `if:` guards between CI steps | Single Maven lifecycle invocation | Maven lifecycle ordering is deterministic and already enforced by Surefire/Failsafe binding phases |

---

## Common Pitfalls

### Pitfall 1: paths-ignore on a Required Workflow

**What goes wrong:** Adding `on.push.paths-ignore` or `on.pull_request.paths-ignore` to `ci.yml` causes the entire workflow to be skipped on matching commits. The required checks `build-and-test`, `dockerfile-noble-pin-guard`, `docker-build` are never reported to GitHub, leaving them in `Pending` state. Branch protection blocks the PR merge indefinitely.

**Why it happens:** GitHub's path filtering is workflow-level. A skipped workflow generates no check runs at all — not even a `skipped` result. This is different from a job that is skipped (which does report).

**How to avoid:** Never add `paths-ignore` to `ci.yml`. Use the `changes` job + step-level `if:` pattern (D-03).

**Warning signs:** PR checks list shows no status for `build-and-test` on a docs-only commit; merge button is permanently blocked.

### Pitfall 2: `paths` and `paths-ignore` Mutual Exclusivity

**What goes wrong:** Attempting to add `paths-ignore` to a workflow that already uses `paths:` trigger. GitHub rejects the workflow (parse error or unexpected behaviour — the two are mutually exclusive per docs).

**Why it happens:** `mariadb-migration-smoke.yml` currently uses `paths:` (inclusion-based). Adding `paths-ignore` to it without removing `paths` would be invalid.

**How to avoid:** D-06 says "reconcile to the same ignore semantics". Check whether the existing `paths:` trigger already excludes docs-only changes (it does — it only triggers on `src/main/resources/db/**`, `application*.yml`, `pom.xml`, and the workflow file). If existing `paths:` is already correct, D-06 is a no-op for `mariadb-migration-smoke.yml`. Planner must verify the existing trigger against D-04 before writing a change task.

**Warning signs:** Workflow YAML parse error in GitHub Actions; unexpected trigger behaviour.

### Pitfall 3: Playwright Pre-Install Ordering Regression

**What goes wrong:** Moving the `Install Playwright Browsers` step to after the Maven invocation (or inside a conditional that skips it on docs PRs) causes Playwright auto-download inside Surefire JVM forks, corrupting the Surefire stdout channel.

**Why it happens:** The ci.yml has detailed comments explaining this ordering requirement (lines 33-63). The Playwright install must happen BEFORE `./mvnw` to prevent auto-download during Spring context loading in Surefire forks.

**How to avoid:** Gate the Playwright install step and the Maven step together: both must carry the same `if: needs.changes.outputs.code == 'true'` condition. The ordering between the two steps (Playwright first, then Maven) must be preserved. [VERIFIED: ci.yml lines 33-63 comment block]

### Pitfall 4: Docker GHA Cache Evicting Maven/Playwright Caches

**What goes wrong:** Docker `type=gha` cache entries and `actions/cache` entries share the 10 GB repository budget. A large Docker cache could evict recently unused Maven/Playwright entries via LRU eviction.

**Why it happens:** The 10 GB limit is shared across all cache backends (actions/cache + Docker GHA backend). LRU eviction fires when the limit is exceeded.

**How to avoid:** Use `scope=ctc-docker` to identify the Docker cache entries distinctly. Monitor cache sizes in the GitHub Actions cache settings after first warm run. For a Spring Boot multi-stage build, the Docker cache is expected to be 500–1500 MiB; the current Maven (~400 MiB) + Playwright (~360 MiB) leaves ~8 GB margin — no immediate eviction risk. [ASSUMED — exact sizes need verification after first warm run]

### Pitfall 5: `actions/checkout` Missing from No-Op Path

**What goes wrong:** Making `actions/checkout` conditional means the job may exit without a working tree, potentially causing subsequent steps or artifact uploads to fail.

**Why it happens:** Some artifact upload steps reference files in the workspace (e.g., `target/surefire-reports/`). Even on a docs-only no-op run, the job must check out to have a valid workspace for the `if: always()` upload steps.

**How to avoid:** Never gate `actions/checkout` with `if: needs.changes.outputs.code == 'true'`. Checkout must always run.

### Pitfall 6: `changes` Job Missing `permissions: pull-requests: read`

**What goes wrong:** On pull requests from forks, `dorny/paths-filter` uses the GitHub API to fetch changed files. Without `pull-requests: read` permission, the API call fails with 403.

**Why it happens:** GitHub's default token permissions are restrictive for fork PRs. The `changes` job needs explicit permission.

**How to avoid:** Add `permissions: pull-requests: read` to the `changes` job. The top-level `permissions` in `ci.yml` already has `pull-requests: write` (for JaCoCo comment), but job-level permissions override the top level — the `changes` job needs at minimum `pull-requests: read`. [CITED: github.com/dorny/paths-filter README]

---

## Code Examples

### Complete `changes` Job

```yaml
# Source: dorny/paths-filter@v4.0.1 README (github.com/dorny/paths-filter)
  changes:
    runs-on: ubuntu-latest
    permissions:
      pull-requests: read
    outputs:
      code: ${{ steps.filter.outputs.code }}
    steps:
      - uses: actions/checkout@v6
      - uses: dorny/paths-filter@v4.0.1
        id: filter
        with:
          filters: |
            code:
              - 'src/**'
              - 'pom.xml'
              - 'Dockerfile'
              - '.github/workflows/**'
              - 'mvnw'
              - '.mvn/**'
```

### Consolidated Maven Step (D-01)

```yaml
# Source: CLAUDE.md "Build & Test Discipline" + pom.xml -Pe2e profile
      - name: Build, Test, and E2E
        if: needs.changes.outputs.code == 'true'
        run: ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true
```

### Docker Build with GHA Cache (D-07)

```yaml
# Source: docs.docker.com/build/ci/github-actions/cache/
      - name: Set up Docker Buildx
        if: needs.changes.outputs.code == 'true'
        uses: docker/setup-buildx-action@v4.1.0

      - name: Build Docker image (full multi-stage, no push)
        if: needs.changes.outputs.code == 'true'
        uses: docker/build-push-action@v7.2.0
        with:
          context: .
          push: false
          tags: ctc-manager:ci-${{ github.sha }}
          cache-from: type=gha,scope=ctc-docker
          cache-to: type=gha,scope=ctc-docker,mode=max
```

### `codeql.yml` paths-ignore Addition (D-06)

```yaml
# Source: docs.github.com GitHub Actions workflow syntax
on:
  push:
    branches: [master]
    paths-ignore:
      - '.planning/**'
      - '*.md'
      - 'docs/**'
      - '.gitmessage'
  pull_request:
    branches: [master]
    paths-ignore:
      - '.planning/**'
      - '*.md'
      - 'docs/**'
      - '.gitmessage'
  schedule:
    - cron: '0 2 * * 0'
  workflow_dispatch:
```

Note: The existing `gate-on-new-HIGH/CRITICAL` step must not be weakened. It already has `if: github.event_name != 'schedule'`, so it runs on PR and push — the new `paths-ignore` does not affect the gate logic because when the workflow runs (code changed), the gate runs as before. [VERIFIED: codeql.yml lines 56-57]

### CI-06 Guard in pom.xml

```xml
<!-- Source: existing PLAT-07 pattern (pom.xml lines 455-480) -->
<execution>
  <id>no-rerun-failsafe-guard</id>
  <phase>validate</phase>
  <goals><goal>exec</goal></goals>
  <configuration>
    <executable>bash</executable>
    <arguments>
      <argument>-c</argument>
      <argument><![CDATA[
violations=$(grep -E 'rerunFailingTestsCount|retryCount' pom.xml || true);
if [ -n "$violations" ]; then
  echo "[CI-06 build-guard] FAIL: rerunFailingTestsCount or retryCount detected in pom.xml.";
  echo "Symptom-hotfixes are forbidden by CLAUDE.md No Flaky Dismissal.";
  exit 1;
fi;
echo "[CI-06 build-guard] OK - no rerun/retry config in pom.xml.";
exit 0;
]]></argument>
    </arguments>
  </configuration>
</execution>
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Node 20 runtime for `dorny/paths-filter` | Node 24 runtime (v4.0.0+) | March 2026 (v4.0.0) | Node 20 deprecated; v3 will fail on GH runners post-June 2026; must use v4 |
| Docker `docker/build-push-action@v6` | `v7` (latest: `v7.2.0`) | March 2026 | v7 is the current major; v6 still functional but v7 is the recommended install |
| GitHub Cache service API v1 | Cache service API v2 | April 2025 | Buildx >= 0.21.0 required; GHA-hosted runners already provide this |
| `actions/cache@v3` for Playwright | `actions/cache@v4` | 2024 | Already updated in ci.yml; v4 uses Node 20+; no change needed |

**Deprecated/outdated:**
- Node 20-based `dorny/paths-filter@v3`: GitHub will disable Node 20 actions in June 2026; v4 is required.
- Docker GHA cache API v1: shut down April 15, 2025 — `docker/build-push-action@v6` with older Buildx may fail. v7 + setup-buildx v4 ensures API v2 compatibility.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `mariadb-migration-smoke.yml` existing `paths:` trigger already excludes all docs-only changes matching D-04 ignore set | Pattern 3, Pitfall 2 | If the existing `paths:` is too narrow (misses some code paths), D-06 reconcile may need a real change to the workflow |
| A2 | Docker multi-stage image (JDK build stage + JRE runtime) fits within ~1500 MiB in GHA cache with `mode=max`, leaving no eviction risk vs Maven + Playwright entries | Pattern 4, Pitfall 4 | If image is larger than estimated, monitor cache usage and consider `mode=min` or explicitly managing eviction |
| A3 | The second `./mvnw verify -Pe2e` in the current ci.yml does not pass `clean` — it recompiles from compiled classes without wiping `target/`. The D-01 replacement adds explicit `clean`. | Pattern 5 | If the second invocation already has incremental benefits the planner should verify; the explicit `clean` in the replacement is per CLAUDE.md "Build & Test Discipline" and is correct |
| A4 | `actions/cache@v4` and Docker `type=gha` backend share the 10 GB budget but use separate key namespaces — the `scope=` parameter in Docker backend does NOT affect `actions/cache` keys | Pattern 4 | If scopes collide, Maven or Playwright cache entries could be evicted; verify by checking GitHub repository cache settings after implementation |

---

## Open Questions

1. **`mariadb-migration-smoke.yml` D-06 reconcile: change or no-op?**
   - What we know: The existing `paths:` trigger fires only on `src/main/resources/db/**`, `application*.yml`, `pom.xml`, and the workflow file itself. This is narrower than D-04's docs-only ignore set but achieves the same practical effect.
   - What's unclear: D-06 says "reconcile to same ignore semantics" — does this require making the trigger semantically identical to D-04/05, or is the existing `paths:` trigger acceptable as-is?
   - Recommendation: Planner should treat it as a verification task first. If the existing `paths:` trigger is correct by inspection, document it as "no change needed, D-06 satisfied by existing config." Only change if there is a specific gap.

2. **Concrete E2E runtime target (D-02)**
   - What we know: Baseline is 17:39 median. D-01 eliminates a full unit+IT rerun. Expected savings: 3–6 minutes (unit + IT typically take 5–8 minutes). Post-consolidation target should be below ~12 minutes.
   - What's unclear: The exact split between the two Maven invocations in current CI is not in the codebase — it requires reading CI run logs.
   - Recommendation: Planner should note "measure after D-01 implementation; target is below 17:39 per REQUIREMENTS.md CI-04."

3. **`dockerfile-noble-pin-guard` step gating**
   - What we know: The noble-pin guard is a cheap `grep` that runs in < 5 seconds. The CONTEXT.md (D-03) says the step should be conditionally run to demonstrate the pattern, not for runtime savings.
   - What's unclear: Whether it's worth gating a 5-second grep or whether it should remain ungated for simplicity.
   - Recommendation: Gate it with `if: needs.changes.outputs.code == 'true'` for consistency — this makes the D-03 pattern uniform across all three required jobs.

---

## Environment Availability

Step 2.6: SKIPPED (no external tool dependencies — all changes are YAML/pom.xml edits targeting GitHub-hosted runners).

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Maven lifecycle (`./mvnw clean verify -Pe2e`) — JUnit 5 / Surefire / Failsafe |
| Config file | `pom.xml` (Surefire + Failsafe + JaCoCo configuration) |
| Quick run command | `./mvnw test -Dspring.profiles.active=dev` (unit tests only) |
| Full suite command | `./mvnw clean verify -Pe2e -Dspring.profiles.active=dev` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CI-01 | Docs-only commit: required checks report success | Manual: create test PR with only `.planning/` change | `gh pr create` + observe checks | ❌ Wave 0 (manual validation) |
| CI-01 | Code commit: all expensive steps run | Manual: create test PR with `src/` change | `gh pr create` + observe checks | ❌ Wave 0 (manual validation) |
| CI-02 | `codeql.yml` skips on docs-only push | Inspect workflow run in GitHub Actions UI | `gh run list --workflow=codeql.yml` | ❌ Wave 0 (manual validation) |
| CI-03 | Mixed commit triggers full CI | Manual: create PR with both `*.md` and `src/` changes | `gh pr create` + observe checks | ❌ Wave 0 (manual validation) |
| CI-04 | E2E runtime below 17:39 | Observe CI run duration after D-01 | `gh run view <run-id>` | ❌ Wave 0 (measure after implementation) |
| CI-05 | Docker build warm cache hit on 2nd run | Observe "CACHED" lines in Docker build output | `gh run view --log <run-id>` | ❌ Wave 0 (measure after 2nd run) |
| CI-06 | `rerunFailingTestsCount` blocked by pom guard | Unit: add guard text to pom.xml, run `./mvnw validate` | `./mvnw validate` | ❌ Wave 0 (pom guard creation) |

### Sampling Rate

- **Per task commit:** `./mvnw clean test -Dspring.profiles.active=dev` (validates pom.xml syntax, guard execution)
- **Per wave merge:** `./mvnw clean verify -Pe2e -Dspring.profiles.active=dev` (full local suite confirms no regression)
- **Phase gate:** `./mvnw clean verify -Pe2e` green + CI green on a test PR before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] CI validation requires a live PR against GitHub — cannot be automated as a unit test. The planner must include explicit manual-validation steps for CI-01, CI-02, CI-03, CI-04, CI-05 in the plan.
- [ ] CI-06 pom guard is locally testable: `./mvnw validate` will exercise the guard after it is added.

---

## Security Domain

> `security_enforcement` not set to `false` in config.json — section required.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | CI-only changes; no auth code modified |
| V3 Session Management | no | No session code modified |
| V4 Access Control | no | No access control code modified |
| V5 Input Validation | no | No input handling code modified |
| V6 Cryptography | no | No crypto code modified |

### Known Threat Patterns for GitHub Actions Workflows

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Secret exfiltration via `paths-ignore` bypass | Elevation of Privilege | CI-only workflow changes; no secret handling in changed workflows |
| Workflow injection via `github.event.head_commit.message` | Tampering | Not used in the modified workflows |
| CodeQL gate weakening | Tampering | D-06 adds `paths-ignore` to `codeql.yml` but the gate-step (`if: github.event_name != 'schedule'`) is unchanged — the gate still runs when the workflow runs |

The CodeQL `paths-ignore` addition (D-06) requires careful verification: when a docs-only commit skips `codeql.yml`, no SARIF upload happens. This is acceptable because docs changes cannot introduce code scanning findings. The gate-step remains active for code PRs. [VERIFIED: codeql.yml — gate-step at lines 56-116 is inside the `analyze` job, not workflow-level]

---

## Sources

### Primary (HIGH confidence)

- `github.com/dorny/paths-filter` + releases — syntax, negation, cross-job output pattern, v4.0.1 as latest
- `docs.docker.com/build/cache/backends/gha/` — `type=gha` scope parameter, mode=max vs min, GHA cache API v2 requirement
- `docs.docker.com/build/ci/github-actions/cache/` — setup-buildx-action + build-push-action YAML examples
- `docs.github.com/en/actions/using-jobs/using-conditions-to-control-job-execution` — skipped job reports success; step-level vs job-level behavior
- `docs.github.com/en/pull-requests/.../troubleshooting-required-status-checks` — workflow-level skip leaves checks Pending; job-level skip reports success
- `docs.github.com` GitHub Actions workflow syntax — `paths` vs `paths-ignore` mutual exclusivity
- `pom.xml` (project file) — confirmed: no `rerunFailingTestsCount`; single `-Pe2e` profile; PLAT-07/CLEAN-01 build guard pattern
- `ci.yml` (project file) — existing action versions, Playwright install ordering constraint (lines 33-63), required job names, double Maven run structure

### Secondary (MEDIUM confidence)

- `devopsdirective.com/posts/2025/08/github-actions-required-checks-for-conditional-jobs/` — confirms job-level skipped = success; describes step-level gating as the robust pattern for required checks
- `github.blog/changelog/2025-11-20-github-actions-cache-size-can-now-exceed-10-gb-per-repository/` — 10 GB default cache budget confirmed; LRU eviction policy

### Tertiary (LOW confidence — informational only)

- Multiple WebSearch results on Docker GHA cache scope isolation — consistent but not from primary Docker/GitHub docs

---

## Metadata

**Confidence breakdown:**

- Standard stack (action versions): HIGH — verified via official release pages for dorny/paths-filter, docker/setup-buildx-action, docker/build-push-action
- Architecture (paths-filter pattern, step-level gating): HIGH — verified via official GitHub Actions docs
- Docker cache approach: HIGH — verified via official Docker docs
- Maven lifecycle ordering: HIGH — verified via official Maven Failsafe docs + pom.xml inspection
- CI-06 guard pattern: HIGH — based on verified existing PLAT-07/CLEAN-01 patterns in pom.xml
- mariadb-migration-smoke.yml reconcile: MEDIUM — requires planner to verify existing `paths:` trigger against D-04 semantics

**Research date:** 2026-05-30
**Valid until:** 2026-08-30 (stable domain — GitHub Actions action versions should be re-verified before execution if more than 30 days pass)
