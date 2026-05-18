---
phase: 78
slug: docker-release-image-fix
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-18
approved_on: 2026-05-18
audit_method: retroactive
---

# Phase 78 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> **Retroactive audit (State B):** generated 2026-05-18 by Phase 87 / Plan 87-07 against artefacts restored from git ref `60f5f915^`. Phase 78 is a **reactive surgical phase** (Dockerfile pin only — no domain code, no Java production tier). Wave 0 satisfied retroactively — the in-flight Phase 78 shipped the pin + structural CI guards simultaneously with the implementation; the new in-process JUnit guard generated under Plan 87-07 / Task 3 is the structural duplicate that makes the pin programmatically verifiable inside the Maven test suite.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Surefire, untagged unit; no Spring context, no Maven profile) + GitHub Actions (`.github/workflows/ci.yml`) |
| **Config file** | `pom.xml` (Surefire `default-test`); `.github/workflows/ci.yml` (`dockerfile-noble-pin-guard` + `docker-build` jobs) |
| **Quick run command** | `./mvnw test -Dtest='DockerfilePinGuardTest'` (file-IO only, no Spring context) |
| **Full suite command** | `./mvnw verify -Pe2e` (Surefire + Failsafe + Playwright + JaCoCo); CI median 23:00 (Phase-86 PR-branch baseline per D-17) |
| **Estimated runtime** | ~80 ms (single Surefire unit) / ~23 min (full suite, Phase-86 CI median) |

**Phase 78 framing:** Test Infrastructure is intentionally light. Phase 78 produced no domain code, no Spring beans, no JPA entities — the entire phase surface is `Dockerfile` + `.github/workflows/ci.yml`. Manual-Only Verifications (CI workflow grep gate + release-workflow post-merge run on master) provide the **primary** coverage; the in-process `DockerfilePinGuardTest` (Plan 87-07 / Task 3) is the **structural duplicate** that surfaces pin regressions locally without a CI round-trip.

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest='DockerfilePinGuardTest'` (~80 ms; immediate feedback on Dockerfile pin regressions)
- **After every plan wave:** Run `./mvnw verify` (Surefire + Failsafe, no Playwright); the new guard is part of the Surefire group, so it runs unconditionally
- **Before `/gsd:verify-work`:** `./mvnw verify -Pe2e` must be green; CI median 23:00 (Phase-86 PR-branch baseline per D-17). The CI `dockerfile-noble-pin-guard` + `docker-build` jobs also run on every PR.
- **Max feedback latency:** ~5 s for the quick guard run (cold Maven JVM); ~80 ms for warm-Surefire incremental runs
- **Per-plan composite:** `grep -E '^status: approved$' .planning/milestones/v1.10-phases/78-*/78-VALIDATION.md`

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 78-01-01 | 01 | 1 | PLAT-CI-01 | T-87-07-PIN | `Dockerfile` stage 1 declares `FROM eclipse-temurin:25-jdk-noble AS build` with inline Phase-78 rationale comment (D-01, D-04) | unit (structural) | `./mvnw test -Dtest='DockerfilePinGuardTest'` | ✅ `Dockerfile` line 3 + `src/test/java/org/ctc/DockerfilePinGuardTest.java` (Plan 87-07 / Task 3 gap-fill) | ✅ green |
| 78-01-02 | 01 | 1 | PLAT-CI-01 | T-87-07-PIN | `Dockerfile` stage 2 declares `FROM eclipse-temurin:25-jre-noble` with inline Phase-78 rationale comment (D-01, D-04) | unit (structural) | `./mvnw test -Dtest='DockerfilePinGuardTest'` | ✅ `Dockerfile` line 21 + `src/test/java/org/ctc/DockerfilePinGuardTest.java` (Plan 87-07 / Task 3 gap-fill) | ✅ green |
| 78-01-03 | 01 | 1 | PLAT-CI-01 | T-87-07-PIN | `docker build .` completes locally without `Playwright does not support chromium on ubuntu26.04-x64`; container reaches `/actuator/health` 200 `{"status":"UP"}` (D-02 local verification) | manual-only (full `docker build` requires Docker daemon; performed live 2026-05-11) | `docker build -t ctc-manager:phase78-local .` then `docker run -p 18080:8080 ...` + `curl /actuator/health` | ✅ Image digest `sha256:f61a295c112668ce1e4bc7d1a9d95d5a3bfe8e988fabdbcd647518bf52de25ab` recorded in `78-03-SUMMARY.md` §"D-02 Local Verification" (build + health + HTTP 200 + Chrome for Testing 147.0.7727.15 confirmed) | ✅ green |
| 78-02-01 | 02 | 1 | PLAT-CI-02 | T-87-07-PIN | `.github/workflows/ci.yml` `dockerfile-noble-pin-guard` job: whitelist-on-suffix grep gate (`grep -E '^FROM eclipse-temurin:'` extract + `grep -v -F -e '-noble'` filter); fails CI if any FROM line is not `-noble`-pinned (D-05, D-06) | manual-only (CI workflow assertion) + structural duplicate via Surefire | `gh run list --workflow=CI --branch <branch>` + `./mvnw test -Dtest='DockerfilePinGuardTest'` (in-process structural duplicate) | ✅ `.github/workflows/ci.yml` job `dockerfile-noble-pin-guard` lines 73-111 + `src/test/java/org/ctc/DockerfilePinGuardTest.java` (Plan 87-07 / Task 3) | ✅ green |
| 78-02-02 | 02 | 1 | PLAT-CI-02 | T-87-07-PIN | `.github/workflows/ci.yml` `docker-build` job: full `docker build .` on every PR + push to master, exercising stage-2 `playwright install chromium` RUN step end-to-end (D-07, D-08); `needs: dockerfile-noble-pin-guard` gate (cheap guard before expensive build) | manual-only (CI workflow assertion; full Docker build cannot run in Surefire) | `gh run list --workflow=CI --branch <branch>` (look for `docker-build` job ✅) | ✅ `.github/workflows/ci.yml` job `docker-build` lines 113-142; PR-branch CI run 26008754136 on `gsd/v1.11-tooling-and-cleanup` @ `b7f20b53` (2026-05-18) — both `docker-build` and `dockerfile-noble-pin-guard` green | ✅ green |
| 78-03-01 | 03 | 1 | PLAT-CI-01 / PLAT-CI-02 | — | `.planning/REQUIREMENTS.md` registers PLAT-CI-01 + PLAT-CI-02 with Phase-78 traceability rows; ROADMAP Phase 78 Requirements line flipped from `TBD` to `PLAT-CI-01, PLAT-CI-02` | manual-only (doc consistency) | `grep -E 'PLAT-CI-0[12]' .planning/milestones/v1.10-REQUIREMENTS.md` | ✅ `.planning/milestones/v1.10-REQUIREMENTS.md` lines 76-77 (PLAT-CI bullets); lines 159-160 (Traceability rows mapping both to Phase 78); line 175 (Coverage row) | ✅ green |
| 78-03-02 | 03 | 1 | PLAT-CI-02 | T-87-07-RENOVATE | Release workflow's "Build and push Docker image" step reaches green on next push to master (success criterion 3 — by-design post-merge deferral per `78-VERIFICATION.md` `human_verification`) | manual-only (post-merge release-workflow observation) | `gh run list --workflow=Release --branch master --limit 1` | ✅ Plan-02 `docker-build` CI job structurally duplicates the failing step on every PR (already green on `gsd/v1.11-tooling-and-cleanup` CI run 26008754136); post-merge release-workflow observation is the contractual final signal — captured for `gh pr merge --squash` close-out | ✅ green (pre-merge proxy via `docker-build` job); post-merge release-workflow observation tracked in `78-VERIFICATION.md` |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

**Satisfied retroactively** — Phase 78 was a reactive surgical fix shipped 2026-05-11 (pin-only diff in `Dockerfile`, additive `dockerfile-noble-pin-guard` + `docker-build` jobs in `.github/workflows/ci.yml`, REQUIREMENTS/ROADMAP doc registrations). The original phase execution shipped the **structural** test infrastructure simultaneously with the pin per the v1.10 phase contract; Wave 0 was completed in-flight rather than as a separate prep wave. The in-process JUnit guard (`DockerfilePinGuardTest`) was added under Plan 87-07 / Task 3 to make the pin **programmatically verifiable inside the Maven test suite** as a structural duplicate of the CI workflow grep gate.

- [x] `Dockerfile` — pinned to `eclipse-temurin:25-jdk-noble` (build) + `eclipse-temurin:25-jre-noble` (runtime), inline rationale comments on both FROM lines (shipped 78-01)
- [x] `.github/workflows/ci.yml` `dockerfile-noble-pin-guard` job — whitelist-on-suffix grep gate (shipped 78-02)
- [x] `.github/workflows/ci.yml` `docker-build` job — full `docker build .` on every PR + push (shipped 78-02)
- [x] `src/test/java/org/ctc/DockerfilePinGuardTest.java` — in-process Surefire guard, structural duplicate of the CI grep gate (Plan 87-07 / Task 3 gap-fill, 2 scenarios, ~80 ms)

*Existing infrastructure (CI workflow grep gate + full docker-build job) covered all phase requirements at v1.10 close; the in-process JUnit guard added under Plan 87-07 is the value-additive structural duplicate.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `docker build .` end-to-end succeeds (Playwright chromium download on Ubuntu Noble runtime stage) | PLAT-CI-01 | Full Docker daemon required — cannot run in Surefire. The CI `docker-build` job (`needs: dockerfile-noble-pin-guard`) is the structural duplicate on every PR. | `docker build -t ctc-manager:phase78-local .` then `docker run -p 18080:8080 ctc-manager:phase78-local` + `curl http://localhost:18080/actuator/health` → expect HTTP 200 `{"status":"UP"}`. Originally captured 2026-05-11 per `78-03-SUMMARY.md` §"D-02 Local Verification" (image digest `sha256:f61a295c...`). |
| `.github/workflows/ci.yml` `dockerfile-noble-pin-guard` job runs on every PR + push | PLAT-CI-02 | CI workflow assertion — the workflow file itself IS the structural guard. The in-process `DockerfilePinGuardTest` is the JUnit duplicate; the workflow execution is the contractual signal. | `gh run list --workflow=CI --branch <branch>` then inspect for the `dockerfile-noble-pin-guard` job conclusion ✅. Run 26008754136 (workflow_dispatch on `gsd/v1.11-tooling-and-cleanup` @ `b7f20b53`, 2026-05-18) confirms green. |
| `.github/workflows/ci.yml` `docker-build` job runs `docker build .` on every PR + push | PLAT-CI-02 | Full Docker daemon required — cannot run in Surefire. The job uses `needs: dockerfile-noble-pin-guard` so the cheap grep gate fails fast before the expensive build is invoked. | `gh run list --workflow=CI --branch <branch>` then inspect for the `docker-build` job conclusion ✅. Same run as above (26008754136) confirms green. |
| Release workflow's "Build and push Docker image" step succeeds on next push to master (Phase 78 success criterion 3) | PLAT-CI-02 | By-design post-merge deferral — `.github/workflows/release.yml` triggers only on `push: master`, not on feature-branch PRs. The Plan-02 `docker-build` CI job is the pre-merge structural duplicate. See `78-VERIFICATION.md` `human_verification` block. | After PR squash-merges to `master`: `gh run list --workflow=Release --branch master --limit 1` → expect `completed` with conclusion `success`; within that run, the `Build and push Docker image` step (the step that failed in run 25609204039) must reach and pass — no `Playwright does not support chromium on ubuntu26.04-x64` in the step log. Tracked in `78-VERIFICATION.md` `human_verification[0]`. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or **post-hoc evidence** (Wave 0 satisfied retroactively; PLAT-CI-01 covered by `DockerfilePinGuardTest` + CI `dockerfile-noble-pin-guard`; PLAT-CI-02 covered by CI `docker-build` job + post-merge release-workflow observation tracked in `78-VERIFICATION.md`)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (every Per-Task Map row above has either an automated Surefire command or a CI workflow assertion citation)
- [x] Wave 0 covers all MISSING references **(retroactively — 1 gap test landed under Plan 87-07 / Task 3 to programmatically duplicate the CI grep gate inside Maven; PLAT-CI-01 + PLAT-CI-02 classified COVERED by `gsd-nyquist-auditor` 2026-05-18)**
- [x] No watch-mode flags (Maven CLI invocations only; CI workflow jobs run on standard GitHub Actions triggers)
- [x] Feedback latency < 600 s (quick run `./mvnw test -Dtest='DockerfilePinGuardTest'` ~80 ms; full Surefire+Failsafe loop ~9 min; CI median 23:00)
- [x] `nyquist_compliant: true` set in frontmatter

---

## Validation Audit 2026-05-18

| Metric | Count |
|--------|-------|
| Gaps found | 1 |
| Resolved | 1 |
| Escalated | 0 |

**Classification per PLAT-CI requirement (gsd-nyquist-auditor verdict, retroactive audit):**

| REQ-ID | Verdict (pre-audit) | Gap closed by | Verdict (post-audit) | Evidence path |
|--------|---------------------|---------------|----------------------|---------------|
| PLAT-CI-01 | PARTIAL — pin verified by CI workflow grep gate (`dockerfile-noble-pin-guard`) only; no in-process Java assertion | `DockerfilePinGuardTest` (Plan 87-07 / Task 3, 2 scenarios) — reads `Files.readString(Path.of("Dockerfile"))` and asserts (a) every `FROM eclipse-temurin:` line ends in `-noble`, (b) the exact pinned forms `25-jdk-noble` (build) + `25-jre-noble` (runtime) remain | COVERED | `Dockerfile` lines 3 + 21 (suffix-pinned to `-noble`); `src/test/java/org/ctc/DockerfilePinGuardTest.java` (Surefire unit, 0.080 s exec, BUILD SUCCESS); `.github/workflows/ci.yml` `dockerfile-noble-pin-guard` lines 73-111 |
| PLAT-CI-02 | COVERED (via CI) | (no new test — Manual-Only classification preserved) | COVERED | `.github/workflows/ci.yml` `dockerfile-noble-pin-guard` lines 73-111 (whitelist-on-suffix grep gate, mirroring PLAT-07 commit `f451ff4`); `docker-build` lines 113-142 (`needs: dockerfile-noble-pin-guard`, full multi-stage `docker build .`); CI run 26008754136 on `gsd/v1.11-tooling-and-cleanup` @ `b7f20b53` (2026-05-18) — both jobs ✅. A workflow-level structural-guard-test parsing `ci.yml` was considered and explicitly rejected — tying the Java test suite to YAML structure would be brittle to legitimate workflow refactors without proportional value over the workflow execution itself. |

**Auditor return shape:** `## GAPS FILLED` with 1 generated test (DockerfilePinGuardTest, 2 scenarios).
**Adversarial stance honored:** Per-requirement adversarial probes were applied during the audit (e.g. "if someone removes the CI workflow grep gate, do we still catch a future regression?" → triggered the JUnit guard creation). PLAT-CI-02 Manual-Only classification was retained only after weighing the YAML-coupling risk of a workflow-structural-guard test against the value of in-process duplication; the actual CI workflow execution provides the contractual signal.

**Predicted vs actual gap profile (87-RESEARCH.md §"Phase 78"):** Predicted 1-2 tests (HIGH likelihood). Actual delivery: **1 test, 2 scenarios** (conservative end of the predicted range). Matches the auditor's reactive-surgical phase framing.

**CI evidence:** Pre-merge CI run `26008754136` (workflow_dispatch on `gsd/v1.11-tooling-and-cleanup` @ `b7f20b53`, 2026-05-18) — both `dockerfile-noble-pin-guard` and `docker-build` jobs green; the new `DockerfilePinGuardTest` is part of the Surefire `default-test` execution and will run on every subsequent CI invocation. Originating v1.10 verification: `78-VERIFICATION.md` `status: passed, score: 5/5 success criteria verified (criterion 3 PENDING POST-MERGE by design)` (2026-05-11).

**Wallclock impact (CONTEXT D-06 guard):** +0.080 s incremental Surefire cost for the new guard, well within the 5% regression headroom (24:09 − 23:00 = 69 s headroom across the milestone; this plan consumes ~0.1 s).

**JaCoCo impact (CONTEXT D-07):** Negligible — the guard exercises file-IO only, no production-code coverage delta.

**No `@DirtiesContext` added (Phase 86 D-03 / PERF-FUTURE-02 invariant preserved):** the guard has no Spring context.

**Approval:** approved 2026-05-18 — retroactive audit via Phase 87 / Plan 87-07 (State B — created new VALIDATION.md from template; no prior draft existed in `60f5f915^`)
