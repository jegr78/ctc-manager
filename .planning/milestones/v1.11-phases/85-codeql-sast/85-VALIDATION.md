---
phase: 85
slug: codeql-sast
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-17
approved_on: 2026-05-18
audit_method: retroactive
---

# Phase 85 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | None for SAST-XX — Phase 85 has no Java unit/IT/E2E test surface (CI workflow + config files + docs only). Verification is via static-grep checks + workflow-run smoke tests + structural-YAML probes. |
| **Config file** | n/a |
| **Quick run command** | `./mvnw test-compile --no-transfer-progress` (any Java edits via triage-commits) OR `actionlint .github/workflows/codeql.yml` (YAML edits) |
| **Full suite command** | `./mvnw verify -Pe2e --no-transfer-progress` (phase gate per CLAUDE.md `feedback_e2e_verification`) |
| **Estimated runtime** | Quick: ~10s (test-compile) / <1s (actionlint) · Full: ~11 min (v1.10 baseline) · `gh workflow run codeql.yml` observation: 5-15 min per run |

---

## Sampling Rate

- **After every task commit:** Per task-type:
  - YAML/config edits → `actionlint`, `yq eval .`, structural-grep against expected keys
  - Java triage-commit (fix HIGH finding) → `./mvnw test-compile`
  - Source-marker addition → grep for `// CodeQL FP:` presence + matching `sast-acceptance.md` table row (D-19 Update-on-Triage coupling)
  - `sast-acceptance.md` edit → markdown-table well-formedness + Alert-ID presence
- **After every plan wave:** Phase 85 has 3 waves (scaffold → triage → final-enable). After each wave: `gh workflow run codeql.yml --ref gsd/v1.11-tooling-and-cleanup` to confirm baseline reduction.
- **Before `/gsd:verify-work`:** Full suite (`./mvnw verify -Pe2e`) green + `gh workflow run codeql.yml` produces ZERO HIGH/CRITICAL after triage commits + SAST-06 throwaway-branch deliberate-violation test fires the gate as expected (logs captured in `85-VERIFICATION.md`).
- **Max feedback latency:** Structural checks ~1s · actionlint ~1s · `gh workflow run` observation ~5-15 min · `./mvnw verify -Pe2e` ~11 min.

---

## Per-Task Verification Map

> Per-task IDs will be finalized by the planner. The map below is the requirement→verification scaffolding the planner MUST cover. Every SAST-XX requirement and every CONTEXT.md decision that translates to a structural fact in `codeql.yml` / `codeql-config.yml` / `sast-acceptance.md` MUST appear in PLAN.md `acceptance_criteria` blocks with the matching `automated_command`.

| Req / Decision | Behavior | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------------|----------|------------|-----------------|-----------|-------------------|-------------|--------|
| SAST-01 | `.github/workflows/codeql.yml` exists with `on: { push, pull_request, schedule, workflow_dispatch }` after final-enable commit | — | n/a (CI config) | structural | `test -f .github/workflows/codeql.yml && yq -e '.on \| has("push") and has("pull_request") and has("schedule")' .github/workflows/codeql.yml` | ✅ after final-enable | ✅ green |
| SAST-01 | Workflow uses `language: java-kotlin` + `queries: security-extended` | — | n/a | structural | `yq -e '.jobs.analyze.steps[] \| select(.uses == "github/codeql-action/init@v4") \| .with.languages == "java-kotlin" and .with.queries == "security-extended"' .github/workflows/codeql.yml` | ✅ scaffold | ✅ green |
| SAST-01 | Weekly cron at Sunday 02:00 UTC | — | drift detection | structural | `yq -e '.on.schedule[0].cron == "0 2 * * 0"' .github/workflows/codeql.yml` | ✅ final-enable | ✅ green |
| SAST-02 | Manual build step `./mvnw compile --no-transfer-progress -DskipTests` (NOT autobuild) | — | preserves Lombok annotation processing + Playwright compile-scope deps | structural | `yq -e '.jobs.analyze.steps[] \| select(.name == "Build for CodeQL") \| .run \| contains("./mvnw compile") and contains("-DskipTests") and contains("--no-transfer-progress")' .github/workflows/codeql.yml` | ✅ scaffold | ✅ green |
| SAST-03 | Job-level `permissions:` block with `security-events: write`, `contents: read`, `actions: read` | T-9 (workflow permission over-scoping) | least-privilege | structural | `yq -e '.jobs.analyze.permissions \| (."security-events" == "write" and .contents == "read" and .actions == "read")' .github/workflows/codeql.yml` | ✅ scaffold | ✅ green |
| SAST-03 | Workflow-level `permissions:` restrictive (`contents: read` only, NOT default read/write) | T-9 | least-privilege | structural | `yq -e '.permissions.contents == "read" and (.permissions \| length == 1)' .github/workflows/codeql.yml` | ✅ scaffold | ✅ green |
| SAST-04 | Every CodeQL Alert from baseline scan is bucketed (fixed/suppressed/accepted) with table row in `docs/security/sast-acceptance.md` | — | audit trail discoverability | markdown-table grep | `grep -c "^|.*|.*|.*|.*|.*|" docs/security/sast-acceptance.md` ≥ baseline-HIGH/CRITICAL count; AND each `// CodeQL FP:` source marker has a matching `sast-acceptance.md` table row | ✅ after triage commits | ✅ green |
| SAST-04 | Every suppression via codeql-config.yml has a matching non-directive `// CodeQL FP: <rule-id> — <reason>; see docs/security/sast-acceptance.md` source marker | — | code-review discoverability | source-grep | `for rule in $(yq -e '.query-filters[].exclude.id' .github/codeql/codeql-config.yml); do grep -rE "// CodeQL FP: ${rule}" src/main/java/ \|\| echo "MISSING: ${rule}"; done` returns no MISSING lines | ✅ after triage commits | ✅ green |
| SAST-05 | SSRF + ZIP-Slip + BCrypt-N/A sections present in sast-acceptance.md | — | classification per CONTEXT.md D-16 | section grep | `grep -cE "^## (SSRF \(Server-Side Request Forgery\)\|ZIP-Slip \(Archive Path Traversal\)\|BCrypt Password Hashing \(Not Applicable\))" docs/security/sast-acceptance.md` == 3 | ✅ scaffold | ✅ green |
| SAST-05 | BCrypt-N/A section documents the SC#4 tracked deviation explicitly (no PasswordEncoder bean) | — | future-reader clarity | content-grep | `grep -A2 "## BCrypt Password Hashing (Not Applicable)" docs/security/sast-acceptance.md \| grep -E "httpBasic\|PasswordEncoder.*does not"` | ✅ scaffold | ✅ green |
| SAST-06 | Throwaway-branch deliberate-violation test causes the inline-bash SARIF-diff gate to exit 1 | — | gate enforcement proof | log-capture | `85-VERIFICATION.md` contains `gh run view <run-id>` output showing exit 1 + `::error::` annotation with rule.id (e.g. `java/sql-injection`) | ✅ post-final-enable | ✅ green |
| D-04 | codeql-config.yml pre-stages `java/ssrf` + `java/zipslip` + `java/path-injection` excludes (rule-id whole-codebase per D-02 revision) | T-1 + T-2 (FP suppression) | defense-in-depth via Phase 81 SpotBugs gate on same files | structural | `yq -e '[.query-filters[].exclude.id] \| (contains(["java/ssrf"]) and contains(["java/zipslip"]) and contains(["java/path-injection"]))' .github/codeql/codeql-config.yml` | ✅ scaffold | ✅ green |
| D-06 + D-28 | Inline-bash SARIF-diff gate-step is part of `codeql.yml` (not marketplace action) | — | minimum-3rd-party-action policy | structural | `yq -e '.jobs.analyze.steps[] \| select(.name == "Gate on new HIGH/CRITICAL security alerts") \| .run \| contains("gh api") and contains("code-scanning/alerts") and contains("dismissed_at")' .github/workflows/codeql.yml` | ✅ final-enable | ✅ green |
| D-10 | Gate step has `if: github.event_name != 'schedule'` | — | weekly cron does not fail noisily | structural | `yq -e '.jobs.analyze.steps[] \| select(.name == "Gate on new HIGH/CRITICAL security alerts") \| .if == "github.event_name != ${'"'"'schedule${'"'"'"}' .github/workflows/codeql.yml` | ✅ final-enable | ✅ green |
| D-12 (scaffold) | Scaffold-commit has `on: workflow_dispatch:` only (no push/PR/schedule) | — | controlled rollout | structural (transient) | At scaffold-commit time: `yq -e '.on \| keys == ["workflow_dispatch"]' .github/workflows/codeql.yml` | ✅ scaffold-only | ✅ green |
| D-17 | `docs/security/` directory exists and contains `sast-acceptance.md` | — | n/a | structural | `test -d docs/security && test -f docs/security/sast-acceptance.md` | ✅ scaffold | ✅ green |
| D-22 | `github/codeql-action/init@v4` + `github/codeql-action/analyze@v4` floating tags | T-3 (supply-chain) | mitigated by Renovate-managed updates + Phase-84 policy | structural | `grep -E "github/codeql-action/(init\|analyze)@v4" .github/workflows/codeql.yml \| wc -l` == 2 | ✅ scaffold | ✅ green |
| D-23 | All Phase-85 commits land on `gsd/v1.11-tooling-and-cleanup` (no feature branch) | — | milestone-branch invariant per Memory | log-check | `git log --oneline --all --source \| grep "feat\|fix\|chore\|docs.*85" \| grep -v "gsd/v1.11-tooling-and-cleanup"` returns nothing | ✅ continuous | ✅ green |
| D-24 | CLAUDE.md `## Conventions` has new sub-section `### CodeQL SAST (Code Scanning)` | — | discoverability | content-grep | `grep -E "^### CodeQL SAST \(Code Scanning\)" CLAUDE.md` | ✅ scaffold | ✅ green |
| D-25 | CLAUDE.md `## References` has new entries for `docs/security/sast-acceptance.md` + `.github/workflows/codeql.yml` | — | discoverability | content-grep | `grep -E "(sast-acceptance\.md\|\.github/workflows/codeql\.yml)" CLAUDE.md \| wc -l` ≥ 2 | ✅ scaffold | ✅ green |
| D-26 | `actions/setup-java@v5` with `cache: 'maven'` | — | build-speed | structural | `yq -e '.jobs.analyze.steps[] \| select(.uses == "actions/setup-java@v5") \| .with.cache == "maven"' .github/workflows/codeql.yml` | ✅ scaffold | ✅ green |
| D-27 | Workflow-level `concurrency:` block with `cancel-in-progress: true` | — | runner-minute efficiency | structural | `yq -e '.concurrency."cancel-in-progress" == true' .github/workflows/codeql.yml` | ✅ scaffold | ✅ green |
| D-29 | `renovate.json` has `packageRule` for `github/codeql-action` (patch automerge 3d + minor manual-review) | T-3 | controlled update flow | structural | `jq -e '[.packageRules[] \| select(.matchPackageNames \| index("github/codeql-action") // null)] \| length >= 1' renovate.json` | ✅ scaffold | ✅ green |
| Build invariant | New workflow + config files do not break Maven build | — | n/a | sanity-build | `./mvnw test-compile --no-transfer-progress` exits 0 | ✅ continuous | ✅ green |
| Phase gate | Full E2E suite green at phase end | — | regression prevention | full-build | `./mvnw verify -Pe2e --no-transfer-progress` exits 0 | ✅ post-final-enable | ✅ green |
| Phase gate | YAML syntactically valid (actionlint) | — | n/a | linter | `actionlint .github/workflows/codeql.yml` exits 0 | ✅ continuous | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `.github/workflows/codeql.yml` — covers SAST-01, SAST-02, SAST-03, D-10, D-12, D-13, D-22, D-26, D-27 (created in scaffold-commit)
- [x] `.github/codeql/codeql-config.yml` — covers SAST-04 mechanism + D-02-revised (rule-id whole-codebase) + D-04 (created in scaffold-commit)
- [x] `docs/security/sast-acceptance.md` — covers SAST-04, SAST-05 evidence with sections + skeleton tables (created in scaffold-commit; populated in triage-commits)
- [x] `85-VERIFICATION.md` — covers SAST-06 evidence + baseline-triage-table (created by planner, populated by executor)
- [x] CLAUDE.md edits — covers D-24 + D-25 (atomic with scaffold-commit)
- [x] `renovate.json` edit — covers D-29 (atomic with scaffold-commit)
- [x] **No new Java test class** — Phase 85 is CI/config/docs only; existing test surface is untouched

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| GitHub Security tab shows SARIF results | SAST-01 | Requires authenticated GitHub session + UI interaction; cannot be automated from CI without elevated tokens | After `gh workflow run codeql.yml --ref gsd/v1.11-tooling-and-cleanup` completes, open https://github.com/jegr78/ctc-manager/security/code-scanning in browser → confirm alerts appear (or zero alerts after triage) |
| Branch-protection rule "Required status checks: CodeQL Analysis" | (OUT OF SCOPE per D-11 — operator hoheit) | Post-merge manual repo-settings change; not part of Phase-85 PR | Operator action: GitHub repo Settings → Branches → `master` rule → add "CodeQL Analysis / Analyze (java-kotlin)" + "Gate on new HIGH/CRITICAL security alerts" as required status checks. Track in milestone-summary, NOT in 85-VERIFICATION.md |
| SAST-06 throwaway-branch test result | SAST-06 | Requires creating a throwaway branch + draft PR + visual inspection of `gh run view` output | Per CONTEXT.md D-14 procedure: `git switch -c throwaway/sast-06-validation` → create deliberate `java/sql-injection` pattern in `src/main/java/org/ctc/_sast_validation/SastMarker.java` → push → `gh pr create --draft --base gsd/v1.11-tooling-and-cleanup` → wait for CodeQL run → confirm gate-step exit 1 + `::error::` annotation → capture in 85-VERIFICATION.md → close PR + delete branch |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 15s for structural checks (CI observation ~5-15 min is acceptable for SAST-01/SAST-06 — these are workflow-run smoke tests, not unit-test feedback)
- [x] `nyquist_compliant: true` set in frontmatter after planner fills per-task IDs and plan-checker passes

**Approved:** 2026-05-18 (Nyquist retroactive audit — 0 gaps; all SAST-01..SAST-06 green; T-2 operator carry-forward noted, not a gap)

---

## Validation Audit 2026-05-18

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
| Operator carry-forward | 1 (T-2 master branch protection — documented in 85-VERIFICATION.md D-11 and v1.11-MILESTONE-AUDIT.md) |

**Audit method:** retroactive — Phase 85 shipped 2026-05-17 across 4 plans (85-01 scaffold, 85-02 baseline triage, 85-03 final-enable + `./mvnw verify -Pe2e`, 85-04 SAST-06 throwaway-branch deliberate-violation + cleanup). Nyquist audit 2026-05-18 executed every structural check from the Per-Task Verification Map against live branch `gsd/v1.11-tooling-and-cleanup` HEAD. Phase 85 is a CI/config/docs-only phase — no Java unit/IT/E2E test surface. No new JUnit tests were generated; all assertions are `grep`/`python3 yaml`/`jq`/`test -f` structural probes or CI workflow-run log evidence.

**CI evidence:**

- **Full-suite CI baseline:** Run-id [26033853591](https://github.com/jegr78/ctc-manager/actions/runs/26033853591) (workflow on `gsd/v1.11-tooling-and-cleanup` @ SHA `3590b3a7`, conclusion: success) — 1668 tests, JaCoCo 88.88%, SpotBugs 0 BugInstance.
- **SAST-06 throwaway PR #128, run [25996558384](https://github.com/jegr78/ctc-manager/actions/runs/25996558384):** pull_request event, HEAD `f763593e` (fixed gate-step `pr=` query), conclusion: **failure (exit 1)** — `::error::New HIGH/CRITICAL CodeQL alerts introduced: java/sql-injection|src/main/java/org/ctc/_sast_validation/SastMarker.java` — captured in 85-VERIFICATION.md.
- **CodeQL alert #35:** https://github.com/jegr78/ctc-manager/security/code-scanning/35 — Rule `java/sql-injection`, security_severity=high (CVSS 9.8, CWE-89), dismissed `won't fix` post-verification (throwaway branch deleted, alert unreachable from any live ref).

**Requirements coverage matrix (audit result):**

| REQ-ID | Existing evidence | Result |
|--------|-------------------|--------|
| SAST-01 | `codeql.yml` has push/pull_request/schedule/workflow_dispatch triggers; cron `0 2 * * 0`; language `java-kotlin`; queries `security-extended` — all 4 triggers confirmed via grep | ✅ COVERED |
| SAST-02 | Build step: `./mvnw compile --no-transfer-progress -DskipTests -Dspring.profiles.active=dev` (not autobuild); python3 YAML parse confirmed | ✅ COVERED |
| SAST-03 | Job-level permissions: `security-events=write`, `contents=read`, `actions=read`; workflow-level: `contents=read` only (length 1) — python3 YAML parse confirmed | ✅ COVERED |
| SAST-04 | 3 `query-filters` excludes in `codeql-config.yml` + 3 `// CodeQL FP:` source markers in `src/main/java/` + 3 `sast-acceptance.md` table rows (D-19 three-layer invariant verified per plan 85-02 commit `a2034cfb`) | ✅ COVERED |
| SAST-05 | 4 required sections present (`SSRF`, `ZIP-Slip`, `BCrypt-N/A`, `Other Triaged Findings`); BCrypt-N/A section documents `httpBasic(Customizer.withDefaults())` + no `PasswordEncoder` bean (D-05 tracked deviation) | ✅ COVERED |
| SAST-06 | PR #128 run 25996558384 exit 1; `::error::` annotation; `java/sql-injection` alert #35 raised and blocked merge; gate-step bug (`ref=` vs `pr=` query for PR-context alerts) discovered and fixed pre-merge via commit `61ccee5f` — structural tests (`actionlint`/`yq`) would not have caught this semantic bug; exactly the failure mode SAST-06 was designed to catch | ✅ COVERED |

**Approval:** approved 2026-05-18 — retroactive Nyquist audit (Phase 87-series, in-milestone closure of v1.11 Nyquist debt). T-2 operator carry-forward: master branch-protection "Required status checks: Analyze (java-kotlin)" must be set before v1.11 PR merges to master (documented in 85-VERIFICATION.md `D-11 Operator-Hoheit Note` and v1.11-MILESTONE-AUDIT.md `operator_actions_required_before_merge`).
