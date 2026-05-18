# Phase 78: Docker Release Image Fix - Context

**Gathered:** 2026-05-11
**Status:** Ready for planning

<domain>
## Phase Boundary

Restore the release workflow's "Build and push Docker image" step to green by pinning both `Dockerfile` stages to a Playwright-compatible Ubuntu **Noble** base image, and add structural guards so this drift cannot silently recur. The phase is intentionally narrow: pin the base image, verify the fix end-to-end locally, and protect the pin going forward.

**In scope:**
- Pin `Dockerfile` stage 1 to `eclipse-temurin:25-jdk-noble` and stage 2 to `eclipse-temurin:25-jre-noble`
- Local verification that `docker build .` succeeds (Playwright `install chromium` step passes) AND the container starts and serves `/actuator/health`
- A CI build-guard that fails when any `FROM eclipse-temurin:` line in `Dockerfile` is not pinned to `-noble` (or the agreed Noble whitelist)
- An inline `Dockerfile` comment explaining why the pin exists (Playwright 1.59.0 / Ubuntu 26.04 incompatibility, Phase 78 reference)
- A new `docker build .` job in `ci.yml` so future Dockerfile regressions fail fast on PR instead of on release
- A green release workflow run on master after merge

**Out of scope:**
- Spring Boot, Java, or Playwright version bumps (criterion 5: pin-only diff)
- Application code changes
- Migrations, REQUIREMENTS.md updates beyond CI/release infra
- SHA256 digest pinning (rejected — see D-01)
- Renovate/Dependabot setup for base-image tracking
- Refactoring of `docker-compose.yml` / `docker-compose.prod.yml` (they consume the built image, not the base directly)

</domain>

<decisions>
## Implementation Decisions

### Pin Tightness
- **D-01:** Pin `Dockerfile` stages to `eclipse-temurin:25-jdk-noble` (stage 1) and `eclipse-temurin:25-jre-noble` (stage 2) — suffix-only, no SHA256 digest. **Why:** Solves the Playwright/Noble incompatibility permanently while still receiving Temurin security patches automatically. Matches roadmap success criterion 1 exactly. Digest pinning rejected because it requires manual patch-tracking overhead disproportionate to a single-app repo.

### Local Verification
- **D-02:** Pre-PR verification has two mandatory steps:
  1. `docker build .` (or `docker compose build`) completes without the `Playwright does not support chromium on ubuntu26.04-x64` error — i.e. the `RUN ... playwright install chromium` step succeeds end-to-end.
  2. `docker compose up app` (or `docker run` of the just-built image) reaches a healthy state and `curl http://localhost:<port>/actuator/health` returns `200 {"status":"UP"}`.
- **D-03:** Team-Card-Generation smoke (rendering a Playwright-driven PNG inside the container) is NOT required pre-PR. The release workflow + post-deploy manual smoke cover that path; build + health is the agreed line for this surgical phase.

### Regression Guards (Belt + Suspenders)
- **D-04:** Add an inline Dockerfile comment above each `FROM eclipse-temurin:...-noble` line explaining the pin: e.g. `# Pinned to -noble: Playwright 1.59.0 does not support Ubuntu 26.04 (Plucky). See Phase 78.` Two comments, one per stage.
- **D-05:** Add a CI build-guard step (in `ci.yml`, mirroring the Phase 71-05 build-guard pattern using `grep -F` / `grep -E`) that:
  - Scans `Dockerfile` for lines matching `^FROM eclipse-temurin:`
  - Fails the job if any such line does NOT end in `-noble` (whitelist-on-suffix approach — easiest to read and review).
  - Failure message points to this CONTEXT.md / Phase 78 rationale.
- **D-06:** The guard is structural, not advisory — it must run on every PR and on push to master so a future contributor who removes the `-noble` suffix cannot merge without explicitly removing/updating the guard.

### PR-Time Docker Build
- **D-07:** Add a new `docker build .` job to `ci.yml` (runs on every PR + push to master). The job:
  - Performs a full `docker build .` (no push, no registry login).
  - Exercises both stages, including the Playwright `install chromium` RUN step — i.e. it would have caught the original Phase 78 failure on PR instead of release.
  - Acceptable cost: +1-3 minutes CI per PR. Trade-off explicitly accepted to get fail-fast on Dockerfile/base-image drift.
- **D-08:** A separate path-filtered or "container-smoke" job is NOT introduced — the full build job is simpler and the PR-time signal is what matters.

### Claude's Discretion
- Exact YAML structure of the new `ci.yml` job (job name, runner, action versions, dependency cache) — planner picks idiomatic patterns consistent with the existing `ci.yml`.
- Exact wording of the build-guard failure message — must reference Phase 78 and the `-noble` pin rationale.
- Whether the build-guard lives as a step inside the new `docker build` job or as a separate, faster `dockerfile-lint`-style job — planner decides based on workflow ergonomics. Both options are acceptable.
- Whether to use `docker/setup-buildx-action` + caching to keep PR build time low — planner judgement.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope and success criteria
- `.planning/ROADMAP.md` §"Phase 78: Docker Release Image Fix" — goal, depends-on, 5 success criteria (already locked, do not relitigate)

### Files this phase touches
- `Dockerfile` — the only application-side file changed (pin + comments)
- `.github/workflows/ci.yml` — new `docker build .` job + build-guard step
- `.github/workflows/release.yml` — read-only reference; this phase verifies that this workflow's "Build and push Docker image" step goes green after merge (criterion 3); do NOT modify

### Project conventions
- `CLAUDE.md` §"Constraints" — Playwright remains a compile-scope dependency, runtime usage for graphics (the reason `chromium` install matters)
- `CLAUDE.md` §"Subagent Rules" — any subagents dispatched during planning/execution must respect branch-protection rules
- `CLAUDE.md` §"Git Workflow" — branch from `origin/master`, PR via `gh`, conventional commits, CI must be green before merge

### Prior-art / pattern source for the build-guard
- Commit `f451ff4` — `fix(71-05): use grep -F for cross-platform build-guard filter` — establishes the build-guard idiom this phase reuses. Planner should grep for the Phase 71-05 build-guard step in `ci.yml` (or its archived plan) and model D-05 after it.

### Failure evidence
- GitHub Actions run `25609204039` (release workflow on master) — the failed `playwright install chromium` invocation that motivated this phase. Error string: `Playwright does not support chromium on ubuntu26.04-x64`.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Phase 71-05 build-guard pattern** in `.github/workflows/ci.yml` — a `grep -F`-based step that fails the job when a forbidden token appears. Mirror this exact pattern for D-05 (whitelist-on-suffix). Avoids re-inventing the cross-platform `grep` portability issue Phase 71-05 already solved.
- **`docker compose up` flow** in `CLAUDE.md` "Commands" section — `docker compose up --build -d` is the documented local entry point; D-02 step 2 reuses this for the health check.
- **Existing `release.yml` docker build step** (lines ~127-141) — single-arg `docker build -t ... .` with no buildx. New `ci.yml` job can stay equally simple unless caching becomes a noticeable pain point.

### Established Patterns
- **Multi-stage Dockerfile**: stage 1 = JDK build, stage 2 = JRE runtime, non-root `ctc` user, healthcheck via `/actuator/health`. Pin must respect both stages (criterion 1).
- **Noble package names**: `Dockerfile` already installs `libasound2t64` (Noble-specific). This is direct evidence the Dockerfile was authored for Noble — the pin is a *restoration* of intended state, not a new constraint (criterion 4).
- **CI uses `./mvnw verify` and `./mvnw verify -Pe2e`** — the new docker-build job is independent of these and should not block them or depend on them.

### Integration Points
- `Dockerfile` is the integration point for D-01, D-04 — two `FROM` lines + two comment lines.
- `.github/workflows/ci.yml` is the integration point for D-05 (build-guard) and D-07 (docker-build job).
- `.github/workflows/release.yml` is the *verification* surface (criterion 3) — phase is done only when the next release run on master after merge passes the Build and push Docker image step.

</code_context>

<specifics>
## Specific Ideas

- The Dockerfile comment phrasing should explicitly name the failing scenario so a future maintainer who challenges the pin sees the cause immediately. Suggested wording: `# Pinned to -noble: Playwright 1.59.0 does not support Ubuntu 26.04 (Plucky). See Phase 78 / CONTEXT.md.`
- The build-guard failure message should be opinionated and self-explanatory, not a generic `grep failed` exit. Example: `❌ Dockerfile uses an unpinned eclipse-temurin tag. All FROM eclipse-temurin: lines must end in -noble (see Phase 78 / .planning/phases/78-docker-release-image-fix/78-CONTEXT.md).`
- User explicitly accepted the +1-3 min CI cost in D-07 — planner should not optimize that away by skipping Playwright install in the PR build. The whole point is that the PR build exercises the exact path that broke in `runs/25609204039`.

</specifics>

<deferred>
## Deferred Ideas

- **SHA256 digest pinning + Renovate/Dependabot for base-image tracking** — rejected for this phase (D-01) as disproportionate to a single-app repo. Revisit if a future Temurin Noble retag breaks the build despite the suffix pin.
- **Team-Card-Generation smoke as a CI gate** (Playwright-driven PNG render inside the container) — out of scope here (D-03). A natural fit for a future "container E2E" phase if release-time regressions become a recurring issue.
- **Path-filter for the new docker-build CI job** (`on.pull_request.paths: [Dockerfile, ...]`) — explicitly NOT taken (D-08). If PR-CI time becomes a complaint, revisit; for now, simple-and-always-on wins.
- **Docker-compose base-image audit** — `docker-compose.yml` and `docker-compose.prod.yml` consume the built application image, not the Temurin base directly, so they are unaffected by this phase. If a future phase introduces sidecar containers that pull `eclipse-temurin:*` directly, the build-guard regex should be extended to scan those files too.

</deferred>

---

*Phase: 78-docker-release-image-fix*
*Context gathered: 2026-05-11*
