---
phase: 79
plan: 04
type: execute
wave: 4
depends_on: [79-03]
files_modified:
  - pom.xml
  - .github/workflows/ci.yml
autonomous: true
requirements: [D-07, D-09, D-10, D-13, D-20]

must_haves:
  truths:
    - "`pom.xml` Phase-N reference comments at lines 20-24, 34-36, 82-91, 203-211, 273-280 are condensed per RESEARCH §`pom.xml Comment Cleanup Inventory`"
    - "JEP 498 / Lombok #3959 / Mockito-agent rationale comments at pom.xml lines 253, 264, 291, 411 are PRESERVED verbatim (Schutzwortliste hits)"
    - "`.github/workflows/ci.yml` adds workflow-level `concurrency: { group: ${{ github.workflow }}-${{ github.ref }}, cancel-in-progress: true }` block per RESEARCH §7 (CD-06 Discretion applied — see below)"
    - "CD-06 Discretion applied: workflow-prefix `${{ github.workflow }}-${{ github.ref }}` added to group expression to prevent cross-workflow cancellations within the same branch (e.g., `ci.yml` cancelling `mariadb-migration-smoke.yml`). D-07's literal `${{ github.ref }}` is the minimum; CD-06 grants discretion on workflow-level vs job-level placement, which we extend to group-expression refinement."
    - "`./mvnw verify` Maven invocations in ci.yml gain the `--no-transfer-progress` flag (D-07)"
    - "`ci.yml` Phase-78 inline comments are condensed per RESEARCH §`ci.yml Comment Cleanup Inventory` (rationale preserved, Phase-N tags stripped)"
    - "`.github/workflows/mariadb-migration-smoke.yml` BODY is UNTOUCHED (SACRED per Phase 77 D-05 + RESEARCH §`mariadb-migration-smoke.yml LEAVE ENTIRELY`)"
    - "D-07 trigger-paths review for `mariadb-migration-smoke.yml`: Task 2.5 executes `grep -n \"paths:\" .github/workflows/mariadb-migration-smoke.yml`. If a `paths:` filter is present (RESEARCH expectation) → verdict recorded as `D-07 trigger-paths review: PASS — path-filter present, no changes needed`. If NO path-filter is present → `NEEDS_CONTEXT` escalation (the `add if missing` path is factually excluded by Phase 77 D-05 SACRED rule, but MUST be explicitly checked and documented rather than assumed)."
    - "`./mvnw verify -Pe2e` BUILD SUCCESS after the build-config edits"
    - "`actionlint .github/workflows/ci.yml` passes (or YAML-syntax check via `yamllint` if actionlint unavailable)"
  artifacts:
    - path: "pom.xml"
      provides: "Cleaned comments, JEP/Lombok/Unsafe rationale preserved"
      contains: "JEP 498"
    - path: ".github/workflows/ci.yml"
      provides: "Workflow-level concurrency block + --no-transfer-progress flag + Phase-78 comment thinning"
      contains: "concurrency:"
    - path: "git log on branch gsd/v1.10-platform-and-backup"
      provides: "Two atomic commits (pom.xml + ci.yml) per D-20; mariadb-migration-smoke.yml trigger-paths verdict recorded in 79-04-SUMMARY.md (no commit on the file itself — its body is SACRED)"
      pattern: "chore\\(79\\): cleanup (pom\\.xml|\\.github/workflows/ci\\.yml)"
  key_links:
    - from: "pom.xml comment edits"
      to: "Schutzwortliste (D-13)"
      via: "JEP/Lombok/Unsafe/MariaDB/H2/transitiv hits preserved"
      pattern: "JEP 498|transitiv"
    - from: "ci.yml concurrency block"
      to: "build-and-test + dockerfile-noble-pin-guard + docker-build jobs"
      via: "workflow-level cancel-in-progress (CD-06 default; group-expression refined per CD-06 Discretion to workflow-prefix)"
      pattern: "cancel-in-progress: true"
    - from: "Task 2.5 grep on mariadb-migration-smoke.yml"
      to: "D-07 trigger-paths review verdict recorded in 79-04-SUMMARY.md"
      via: "read-only grep; no file edit (SACRED body invariant per Phase 77 D-05)"
      pattern: "paths:"
---

<objective>
Wave 4 of Phase 79: cleanup build-configuration files per D-20. Two atomic commits — one for `pom.xml`, one for `.github/workflows/ci.yml`. `mariadb-migration-smoke.yml` is SACRED per Phase 77 D-05 and its body stays UNTOUCHED. A read-only D-07 trigger-paths review on `mariadb-migration-smoke.yml` produces a documented verdict but no file edit.

Purpose: D-20 user addition — pom.xml + ci.yml carry significant Phase-N comment debt (3 explicit Phase-N refs in pom.xml at lines 20, 34, 273; dense Phase-78 inline comments in ci.yml at lines 68-72, 89-93, 99, 108-114). The cleanup classes that apply to YAML/XML are: comment-thinning (D-09) + dead-config-removal (D-04 analog) — extract-method and logic-simplification are Java-only concepts and NOT applicable.

D-07 mandates two things: (a) workflow-level concurrency on `ci.yml` (Task 2) AND (b) a trigger-paths review for `mariadb-migration-smoke.yml` (Task 2.5) — the latter is read-only because Phase 77 D-05 declares the file body SACRED. Task 2.5 explicitly documents the review verdict instead of assuming the path-filter is present.

Output: 2 atomic commits, each followed by `./mvnw verify` (pom.xml) or `actionlint`/YAML-lint (ci.yml). PLUS the D-07 ci.yml additions: workflow-level `concurrency` block + `--no-transfer-progress` on Maven invocations. PLUS a Task 2.5 trigger-paths verdict line in 79-04-SUMMARY.md (no commit on the smoke-workflow file — its body is SACRED).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-CONTEXT.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-RESEARCH.md
@CLAUDE.md
@pom.xml
@.github/workflows/ci.yml
@.github/workflows/mariadb-migration-smoke.yml

<interfaces>
**pom.xml Comment Cleanup Inventory (RESEARCH §`pom.xml Comment Cleanup Inventory` — verified line-by-line):**

| Lines (approximate) | Current content | Action | Target content |
|---|---|---|---|
| 20-24 | `<!-- Phase 75 Plan 10: Testcontainers MariaDB ... Phase 77 hotfix: bumped 1.21.3 → 2.0.5 ... -->` | **PARTIAL REWRITE** | Strip Phase tags. Keep technical rationale (Schutzwort `MariaDB`, `transitiv` style): `<!-- Spring Boot 4.0.x does NOT manage Testcontainers BOM. Pinned to 2.0.5: Testcontainers 1.x sends Docker API 1.32 which is rejected by Docker Engine 29+ (requires API >= 1.40). Testcontainers 2.x ships API 1.44. See gh:testcontainers/testcontainers-java#11235. -->` |
| 34-36 | `<!-- Phase 75 Plan 10: Testcontainers BOM aligns testcontainers + junit-jupiter + mariadb modules on a single coherent version. -->` | **CONDENSE** | `<!-- Testcontainers BOM aligns testcontainers, junit-jupiter, and mariadb modules on a single coherent version. -->` |
| 82-91 | Jackson `Phase 72 plans 02 + 03` rationale block | **REWRITE** | Strip Phase-N prefix. Keep Jackson 2 vs Jackson 3 + `transitively` Schutzwort: `<!-- Required for BackupManifest wire-contract serialization (Instant ISO-8601 strings via backupObjectMapper). Spring Boot 4 auto-configures Jackson 3 (tools.jackson.*); the backup module uses Jackson 2.x ObjectMapper (transitively via flyway-core) which does NOT register JavaTimeModule by default. Version managed by spring-boot-starter-parent (Jackson 2.21.x). -->` |
| 203-211 | `<!-- Phase 75 Plan 10: Testcontainers MariaDB dependencies ... -->` | **CONDENSE** | `<!-- Testcontainers MariaDB for BackupImportMariaDbSmokeIT. Uses @DynamicPropertySource to override spring.datasource.url at @SpringBootTest startup so Flyway runs against the live engine. Auto-detects host Docker daemon; works locally + on GitHub Actions runners. -->` |
| 253 | `<!-- JEP 498 escape: silence Lombok #3959 Permit/Unsafe warnings until upstream fix lands -->` (compiler) | **KEEP AS-IS** | Schutzwort triple: JEP / Lombok / Unsafe |
| 264 | Same JEP 498 comment (Surefire) | **KEEP AS-IS** | Same |
| 273-280 | `<!-- PLAT-06: bind Failsafe in the default lifecycle ... -->` | **REWRITE** | Strip `PLAT-06:` prefix. Keep technical rationale (RESEARCH Pitfall 6): `<!-- Bind Failsafe in the default lifecycle so *IT.java integration tests run on every ./mvnw verify. Without this binding, *IT.java files are only picked up by explicit -Dtest=... invocations. Configuration is placed at execution level (not plugin level) so the e2e profile's execution can use independent filters without Maven profile <configuration> merging. -->` |
| 291 | Same JEP 498 comment (Failsafe default-it) | **KEEP AS-IS** | Same |
| 411 | Same JEP 498 comment (Failsafe e2e-it) | **KEEP AS-IS** | Same |

**Note:** Wave 3 (Plan 03) added a 2-4-line technical comment above `<forkCount>` in Surefire (line ~264 area) and Failsafe default-it (line ~291 area). These comments were authored without Phase-N references per D-09 and stay verbatim.

**ci.yml Comment Cleanup Inventory (RESEARCH §`ci.yml Comment Cleanup Inventory` — verified):**

| Lines | Current content | Action | Target content |
|---|---|---|---|
| 69-72 | `# Phase 78: Structural guard ... D-05 ... commit f451ff4` | **REWRITE** | `# Structural guard for the eclipse-temurin -noble suffix pin in Dockerfile.` + line 2: `# Fails if any FROM eclipse-temurin: line does not end in -noble.` + line 3: `# Whitelist-on-suffix approach. Cross-platform grep idiom mirrors pom.xml template-fragment-call-guard (commit f451ff4).` |
| 89-93 | Inline shell-script `#` comments | **KEEP MOSTLY** | Strip only `(the exact portability trap commit f451ff4 documented for the Phase 71-05 build-guard)` ref. Keep the cross-platform grep explanation (technical rationale). |
| 96 | `# Whitelist: every FROM eclipse-temurin: ...` | **KEEP AS-IS** | Pure technical |
| 98 | `echo "Reason: Playwright 1.59.0 does not support Ubuntu 26.04 (Plucky), which the bare '25-jre' tag silently rotated to in release run 25609204039."` | **KEEP AS-IS** | LOAD-BEARING `pitfall`/`Playwright`/`Ubuntu` rationale — D-10 + D-13 |
| 99 | `echo "See .planning/phases/78-docker-release-image-fix/78-CONTEXT.md (decisions D-01, D-05, D-06)."` | **REPLACE** | `echo "See Dockerfile and its comment block for the -noble pin rationale."` |
| 108-114 | `# Phase 78: Exercises docker build ... D-07 ... D-08` | **REWRITE** | `# Exercises docker build . on every PR + push to master so Dockerfile / base-image regressions fail fast on PR instead of on release.` + `# In particular, exercises stage 2's playwright install chromium RUN step — the exact step that failed in release run 25609204039 before the -noble pin.` + `# Full build job, always on. Acceptable cost: +1-3 minutes CI per PR.` |

**ci.yml D-07 ADDITIONS (separate from comment cleanup):**

1. **Workflow-level concurrency block** — insert AFTER the `on:` block (around line 8) and BEFORE the `permissions:` block (around line 9). The group expression uses `${{ github.workflow }}-${{ github.ref }}` (CD-06 Discretion applied: D-07's literal `${{ github.ref }}` is the minimum; the workflow-prefix prevents cross-workflow cancellations within the same branch — e.g., `ci.yml` cancelling `mariadb-migration-smoke.yml`. CD-06 grants discretion on workflow-level vs job-level placement, which we extend to group-expression refinement):
```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

2. **`--no-transfer-progress` on all `./mvnw verify` invocations** in ci.yml (currently at lines 29 + 37): rewrite to include the flag.
   - Line 29 current: `run: ./mvnw verify -Dspring.profiles.active=dev -Ddocker.available=true`
   - Line 29 target: `run: ./mvnw verify --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true`
   - Line 37 current: `run: ./mvnw verify -Pe2e -Dspring.profiles.active=dev -Ddocker.available=true`
   - Line 37 target: `run: ./mvnw verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true`

**mariadb-migration-smoke.yml: BODY SACRED — DO NOT EDIT.** Per Phase 77 D-05 + RESEARCH §"mariadb-migration-smoke.yml LEAVE ENTIRELY". The D-07 trigger-paths review is **read-only** and produces a documented verdict in 79-04-SUMMARY.md (Task 2.5). RESEARCH §`mariadb-migration-smoke.yml LEAVE ENTIRELY` states the file is already filtered, so the expected verdict is `PASS — path-filter present`. If the grep finds no `paths:` filter, the executor MUST escalate `NEEDS_CONTEXT` (NOT add a path filter — the file body is SACRED and the orchestrator must decide whether to amend Phase 77 D-05 or accept the gap).

**Symbol-existence pre-flight audit:**
```
grep -nE "Phase [0-9]+|PLAT-[0-9]+" pom.xml | head -10
grep -nE "JEP 498" pom.xml | wc -l   # MUST be 4 (compiler line 253, Surefire 264, Failsafe default-it 291, Failsafe e2e-it 411)
grep -nE "Phase 78" .github/workflows/ci.yml | head -5
grep -cE "concurrency:" .github/workflows/ci.yml   # MUST be 0 (greenfield)
grep -cE "no-transfer-progress" .github/workflows/ci.yml   # MUST be 0 (greenfield)
grep -n "paths:" .github/workflows/mariadb-migration-smoke.yml   # Task 2.5 — read-only review; expected: 1+ hits (path-filter present)
```
</interfaces>
</context>

<critical_constraints>
- You are working on branch `gsd/v1.10-platform-and-backup`. Do NOT run `git stash`, `git checkout`, `git reset`, or switch branches.
- Implement ONLY the tasks listed below. If you find that other files need changes, report `NEEDS_CONTEXT` instead of expanding scope.
- After EACH commit, run `./mvnw verify -Pe2e -Dspring.profiles.active=dev -Ddocker.available=true` (NOT just `./mvnw test`) — per D-20: "Each commit followed by `./mvnw verify` (NOT just `test`) — pom changes can break the whole build, workflow changes need at minimum YAML-syntax validation."
- Schutzwortliste (D-13): JEP 498 / Lombok #3959 / Unsafe rationale comments at pom.xml lines 253, 264, 291, 411 + transitiv/Jackson 2 vs Jackson 3 block + MariaDB/H2/Testcontainers/Docker-API rationale + ci.yml Playwright-Ubuntu-26.04-Plucky pitfall (line 98) MUST be preserved.
- `mariadb-migration-smoke.yml` BODY is SACRED — do NOT touch any line in this file. Task 2.5 is read-only `grep -n "paths:"`. If the file lacks a `paths:` filter → `NEEDS_CONTEXT` (do NOT add one).
- Two ATOMIC commits per D-20: one for pom.xml, one for ci.yml. Do NOT batch both files in a single commit. Task 2.5 produces NO commit — only a verdict line in 79-04-SUMMARY.md.
</critical_constraints>

<test_impact>
N/A — config-only changes. No Java source code touched. No test added/removed/renamed.

- Files touched (write): `pom.xml`, `.github/workflows/ci.yml`
- Files read (read-only): `.github/workflows/mariadb-migration-smoke.yml` (Task 2.5)
- Mockito stub updates: NONE
- Bridge-only test deletions: NONE
- Estimated test edit count: 0
- JaCoCo impact: 0 (no Java change)
- CI-cancellation behavior (RESEARCH Pitfall 5): direct pushes to master are forbidden per CLAUDE.md; `cancel-in-progress` is safe under PR-only merge workflow.
- Test-required-status-check interaction (RESEARCH Assumption A1): cancelled run reports as "cancelled" not "failed"; branch protection treats them differently; the throwaway-PR verification step is folded into Plan 07 final-gate check.
</test_impact>

<tasks>

<task type="auto">
  <name>Task 1: pom.xml comment cleanup (1 commit)</name>
  <files>pom.xml</files>
  <read_first>
    - `pom.xml` lines 1-100 (Phase-N comment hotspots 20-24, 34-36, 82-91)
    - `pom.xml` lines 200-300 (Phase-N + PLAT-06 comments at 203-211, 273-280; JEP comments at 253, 264, 291)
    - `pom.xml` lines 400-420 (JEP comment at 411)
    - RESEARCH §"pom.xml Comment Cleanup Inventory" (verbatim line-by-line action table)
    - This plan's `<interfaces>` pom.xml table
  </read_first>
  <action>
1. **Pre-flight symbol-existence audit:**
```
grep -c "JEP 498" pom.xml   # MUST be 4
grep -c "@{argLine}" pom.xml   # MUST be 3 (Wave 3 preserved this)
grep -nE "Phase 7[2-7]|PLAT-06" pom.xml | head -10
```
If `JEP 498` count != 4 OR `@{argLine}` count != 3 → STOP / `NEEDS_CONTEXT`.

2. **Apply 5 surgical Edit-tool replacements** to pom.xml, one per inventory row (PARTIAL REWRITE / CONDENSE / REWRITE / REWRITE / REWRITE). Source-of-truth for each target string is the `<interfaces>` pom.xml table in this plan. The 4 `JEP 498` KEEP-AS-IS comments are NOT edited. The Wave 3-added technical comments above `<forkCount>` are NOT edited.

3. **Schutzwort-grep verification** on the diff:
```
git diff pom.xml | grep '^-' | grep -E "MariaDB|H2|JEP|CVE|Lombok|OSIV|Unsafe|pitfall|auditing|AuditingEntityListener|TODO|HACK|WORKAROUND|FIXME|deadlock|transitiv|transitive|auto-commit"
```
MUST return ZERO matches. If any → STOP, undo that edit, retry preserving the Schutzwort hit.

4. **Build verification:** Run `./mvnw verify -Pe2e -Dspring.profiles.active=dev -Ddocker.available=true`. If BUILD FAILURE → STOP / `NEEDS_CONTEXT` (likely XML-parse error from a botched edit).

5. **Stage + commit:** `git add pom.xml`. Verify `git status` shows ONLY `pom.xml`. Commit:
```
chore(79): cleanup pom.xml comments (D-20)

- Strip Phase-N references from 5 comment blocks (lines ~20, ~34, ~82, ~203, ~273)
- Preserve JEP 498 / Lombok / Unsafe / Mockito-agent rationale (4 KEEP-AS-IS comments)
- Preserve Jackson 2.x vs Jackson 3 transitively-via-flyway-core rationale
- Preserve Testcontainers + MariaDB + Docker API + LONGTEXT technical context
- Preserve PLAT-06 Failsafe-binding rationale (Phase tag stripped, technical why kept)
- No <argLine>, <forkCount>, <reuseForks>, <excludedGroups> values changed
```
  </action>
  <verify>
    <automated>./mvnw verify -Pe2e -Dspring.profiles.active=dev -Ddocker.available=true -q 2>&amp;1 | tail -10 | grep -q "BUILD SUCCESS" &amp;&amp; [ "$(grep -c "JEP 498" pom.xml)" = "4" ] &amp;&amp; [ "$(grep -c "@{argLine}" pom.xml)" = "3" ] &amp;&amp; git log -1 --pretty=%B | grep -q "chore(79): cleanup pom\.xml comments"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw verify -Pe2e` BUILD SUCCESS (JaCoCo ≥ 0.82 gate holds)
    - 4 `JEP 498` comments preserved verbatim
    - 3 `@{argLine}` entries preserved (no argLine syntax change)
    - 2 `<forkCount>` + 2 `<reuseForks>true</reuseForks>` + 2 `<excludedGroups>flaky</excludedGroups>` from Wave 3 preserved
    - No Schutzwort keyword deleted (grep proof)
    - Commit `chore(79): cleanup pom.xml comments (D-20)` lands
  </acceptance_criteria>
  <done>pom.xml comment cleanup commit lands; `./mvnw verify -Pe2e` GREEN; all comment-keep invariants hold.</done>
</task>

<task type="auto">
  <name>Task 2: ci.yml cleanup + concurrency block + --no-transfer-progress (1 commit)</name>
  <files>.github/workflows/ci.yml</files>
  <read_first>
    - `.github/workflows/ci.yml` (full 139 lines)
    - RESEARCH §"ci.yml Comment Cleanup Inventory" (line-by-line action table)
    - RESEARCH §"7. CI Concurrency Group Configuration" (concurrency block placement)
    - This plan's `<interfaces>` ci.yml table + D-07 ADDITIONS block + CD-06 Discretion note on group expression
  </read_first>
  <action>
1. **Pre-flight symbol-existence audit:**
```
grep -cE "concurrency:" .github/workflows/ci.yml   # MUST be 0 (greenfield)
grep -cE "no-transfer-progress" .github/workflows/ci.yml   # MUST be 0 (greenfield)
grep -cE "Phase 78" .github/workflows/ci.yml   # likely 3 hits
```
If `concurrency:` != 0 OR `no-transfer-progress` != 0 → STOP / `NEEDS_CONTEXT`.

2. **D-07 ADDITION 1 — workflow-level concurrency block (with CD-06 Discretion-refined group expression):** Insert AFTER the `on:` block (current lines 3-7) and BEFORE the `permissions:` block (current line 9). Add the 3-line block exactly:
```yaml

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

```
Rationale (must_haves.truths line 4): D-07 specifies the literal `${{ github.ref }}` as the minimum group. CD-06 Discretion is applied here to prefix the expression with `${{ github.workflow }}-` so a `ci.yml` run does NOT cancel an in-flight `mariadb-migration-smoke.yml` run on the same branch (cross-workflow cancellation hazard). The Discretion is justified by CD-06's grant of workflow-level vs job-level placement choice, which we extend to group-expression refinement. The verbatim D-07 alternative (`group: ${{ github.ref }}`) is also acceptable per the original decision text — but the workflow-prefixed form is what this plan implements and documents.

3. **D-07 ADDITION 2 — `--no-transfer-progress`:** Edit line 29 from `./mvnw verify -Dspring.profiles.active=dev -Ddocker.available=true` to `./mvnw verify --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true`. Edit line 37 from `./mvnw verify -Pe2e -Dspring.profiles.active=dev -Ddocker.available=true` to `./mvnw verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true`.

4. **Apply 4 comment-thinning edits** per the `<interfaces>` ci.yml table (lines 69-72 REWRITE, 89-93 KEEP MOSTLY-strip-one-phrase, 99 REPLACE, 108-114 REWRITE). The Playwright-Ubuntu-26.04-Plucky `echo` at line 98 is **KEEP AS-IS** (Schutzwort `pitfall` context).

5. **Schutzwort + load-bearing-context verification:**
```
grep -q "Playwright 1.59.0 does not support Ubuntu 26.04 (Plucky)" .github/workflows/ci.yml   # MUST be present (D-10 load-bearing)
grep -q "release run 25609204039" .github/workflows/ci.yml   # MUST be present (concrete failure reference)
grep -q "\-noble" .github/workflows/ci.yml   # MUST be present (whitelist target)
grep -cE "Phase 78|D-0[5-8]" .github/workflows/ci.yml   # SHOULD be 0 (all phase tags stripped)
grep -cE "concurrency:|cancel-in-progress: true" .github/workflows/ci.yml   # MUST be 2 (1 line each)
grep -cE "no-transfer-progress" .github/workflows/ci.yml   # MUST be 2 (lines 29, 37)
```

6. **YAML-syntax check:** Run `actionlint .github/workflows/ci.yml` if available. If actionlint is not installed (likely), fall back to `python3 -c 'import yaml; yaml.safe_load(open(".github/workflows/ci.yml"))'` to confirm the YAML parses. If parse error → STOP, fix.

7. **Build verification:** Run `./mvnw verify -Pe2e -Dspring.profiles.active=dev -Ddocker.available=true` (this verifies pom.xml + indirectly that the workflow YAML did not break the Maven build — though ci.yml itself is not exercised locally; the GH Actions runner exercises it). If BUILD FAILURE → STOP / `NEEDS_CONTEXT`.

8. **Stage + commit:** `git add .github/workflows/ci.yml`. Verify `git status` shows ONLY `.github/workflows/ci.yml`. Commit:
```
chore(79): cleanup ci.yml comments + add concurrency + --no-transfer-progress (D-07, D-20)

D-07 additions:
- Workflow-level concurrency: group=${{ github.workflow }}-${{ github.ref }}, cancel-in-progress: true (CD-06 Discretion: workflow-prefix added to prevent cross-workflow cancellation; D-07 literal ${{ github.ref }} is the minimum)
- --no-transfer-progress on both ./mvnw verify invocations (build-and-test + e2e-tests)

D-20 comment thinning:
- Strip Phase-78 / D-05 / D-07 / D-08 prefix tags from 4 comment blocks
- Preserve Playwright Ubuntu 26.04 Plucky pitfall rationale (line 98 — load-bearing per D-10)
- Preserve cross-platform grep idiom rationale (technical why kept, commit f451ff4 ref kept)
- Replace 78-CONTEXT.md cross-ref with a Dockerfile-pointer

mariadb-migration-smoke.yml: UNTOUCHED (SACRED per Phase 77 D-05 + RESEARCH verdict LEAVE ENTIRELY). D-07 trigger-paths review is performed read-only in Task 2.5 and recorded in 79-04-SUMMARY.md.
```

9. **Verify mariadb-migration-smoke.yml invariant:**
```
git diff HEAD^ HEAD -- .github/workflows/mariadb-migration-smoke.yml | wc -l   # MUST be 0 (file untouched)
```
If non-zero → `NEEDS_CONTEXT` (the file was inadvertently staged).
  </action>
  <verify>
    <automated>./mvnw verify -Pe2e -Dspring.profiles.active=dev -Ddocker.available=true -q 2>&amp;1 | tail -10 | grep -q "BUILD SUCCESS" &amp;&amp; [ "$(grep -cE "concurrency:" .github/workflows/ci.yml)" = "1" ] &amp;&amp; [ "$(grep -cE "no-transfer-progress" .github/workflows/ci.yml)" = "2" ] &amp;&amp; grep -q "Playwright 1.59.0 does not support Ubuntu 26.04 (Plucky)" .github/workflows/ci.yml &amp;&amp; [ "$(git diff HEAD^ HEAD -- .github/workflows/mariadb-migration-smoke.yml | wc -l)" = "0" ]</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw verify -Pe2e` BUILD SUCCESS
    - ci.yml has `concurrency:` block at workflow level (group = workflow + ref per CD-06 Discretion, cancel-in-progress: true)
    - ci.yml has `--no-transfer-progress` on both `./mvnw verify` invocations
    - 4 Phase-N comment thinnings applied per inventory
    - Playwright-Ubuntu-26.04-Plucky `echo` line preserved verbatim (Schutzwort `pitfall`)
    - `release run 25609204039` reference preserved (concrete failure ref)
    - `mariadb-migration-smoke.yml` UNTOUCHED (git diff size = 0)
    - YAML parses cleanly
    - Commit `chore(79): cleanup ci.yml comments + add concurrency + --no-transfer-progress` lands with CD-06 Discretion rationale in body
  </acceptance_criteria>
  <done>ci.yml commit lands; `./mvnw verify -Pe2e` GREEN; mariadb-migration-smoke.yml untouched; load-bearing context preserved; CD-06 Discretion documented in commit body.</done>
</task>

<task type="auto">
  <name>Task 2.5: D-07 trigger-paths review on mariadb-migration-smoke.yml (read-only verdict, NO commit on the file)</name>
  <files>.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-04-SUMMARY.md (verdict line only; file written by SUMMARY-author at plan close, not by this task in isolation)</files>
  <read_first>
    - `.github/workflows/mariadb-migration-smoke.yml` (full file — READ ONLY, NO EDITS)
    - RESEARCH §`mariadb-migration-smoke.yml LEAVE ENTIRELY` (expected: file is already path-filtered)
    - CONTEXT.md §"D-07" (full text of the decision — note D-07 says "review trigger-paths AND add a filter if missing"; the "add if missing" path is factually excluded by Phase 77 D-05 SACRED rule)
  </read_first>
  <action>
This task is **read-only**. It produces a verdict line, NOT a file edit on `mariadb-migration-smoke.yml`. The Phase 77 D-05 SACRED rule forbids any body change to this workflow.

1. **Run the trigger-paths grep:**
```
grep -n "paths:" .github/workflows/mariadb-migration-smoke.yml
```
Capture stdout verbatim. Also capture the surrounding 5 lines of context for each hit:
```
grep -n -B1 -A5 "paths:" .github/workflows/mariadb-migration-smoke.yml
```

2. **Branch on the grep result:**

   **Branch A — `paths:` filter PRESENT (RESEARCH expectation, expected verdict):**
   Record verdict in the executor's working notes (for inclusion in 79-04-SUMMARY.md at plan close):
   ```
   D-07 trigger-paths review (Task 2.5): PASS — path-filter present at lines <N1>, <N2>, ... in .github/workflows/mariadb-migration-smoke.yml.
   Captured filter scope (verbatim grep -A5 output):
   <paste grep output>
   Verdict: PASS — no changes needed. The "add if missing" branch of D-07 is not exercised because the file already has a path-filter. The file body remains SACRED per Phase 77 D-05.
   ```
   Proceed to Task 3 / plan close.

   **Branch B — `paths:` filter ABSENT (grep returns empty):**
   STOP. Do NOT add a `paths:` filter (Phase 77 D-05 SACRED rule). Report `NEEDS_CONTEXT` to the orchestrator with this exact message:
   ```
   NEEDS_CONTEXT: D-07 trigger-paths review found NO `paths:` filter in .github/workflows/mariadb-migration-smoke.yml.

   D-07 instructs: "review trigger-paths AND add a filter if missing".
   Phase 77 D-05 SACRED rule forbids any body change to this file.
   These two rules conflict.

   Cannot proceed unilaterally. Orchestrator decision required:
   (a) amend Phase 77 D-05 to permit a trigger-paths-only edit, OR
   (b) accept the gap and document it in 79-VERIFICATION.md as a deferred D-07 sub-item, OR
   (c) schedule a Phase-77 hotfix to add the path-filter (out of Phase 79 scope).
   ```

3. **Record the verdict for the SUMMARY:** Regardless of branch, save the captured grep output + the chosen verdict ("PASS — path-filter present" or "NEEDS_CONTEXT — escalated") to a temp note (e.g., write to `/tmp/79-04-task25-verdict.txt`). The SUMMARY-author at plan close will copy this verdict verbatim into `79-04-SUMMARY.md` under a `## D-07 Trigger-Paths Review (Task 2.5)` section.

4. **No `git add`, no commit in this task.** The `mariadb-migration-smoke.yml` file is NOT staged. Confirm with:
```
git status -- .github/workflows/mariadb-migration-smoke.yml   # MUST show nothing (file untouched)
```
  </action>
  <verify>
    <automated>test -f /tmp/79-04-task25-verdict.txt &amp;&amp; grep -qE "PASS — path-filter present|NEEDS_CONTEXT — escalated" /tmp/79-04-task25-verdict.txt &amp;&amp; [ "$(git status --porcelain -- .github/workflows/mariadb-migration-smoke.yml | wc -l)" = "0" ]</automated>
  </verify>
  <acceptance_criteria>
    - `grep -n "paths:" .github/workflows/mariadb-migration-smoke.yml` was executed and the result captured
    - Verdict is one of: "PASS — path-filter present" OR "NEEDS_CONTEXT — escalated"
    - If PASS: captured grep output is recorded for SUMMARY inclusion
    - If absent: `NEEDS_CONTEXT` was raised with the exact message above; NO `paths:` filter was added to the file
    - `mariadb-migration-smoke.yml` body has ZERO modifications (git status clean for this path)
    - Verdict line is queued for 79-04-SUMMARY.md (under `## D-07 Trigger-Paths Review (Task 2.5)`)
    - This task produces NO git commit (the SACRED file is read-only)
  </acceptance_criteria>
  <done>Trigger-paths review verdict recorded; mariadb-migration-smoke.yml body untouched; SUMMARY-author has the verdict line to insert at plan close.</done>
</task>

</tasks>

<verification>
- 2 atomic commits land on `gsd/v1.10-platform-and-backup` (`chore(79): cleanup pom.xml comments` + `chore(79): cleanup ci.yml comments + add concurrency + --no-transfer-progress`)
- `./mvnw verify -Pe2e` BUILD SUCCESS at HEAD
- pom.xml: 4 JEP 498 + 3 `@{argLine}` + 2 `<forkCount>` + 2 `<reuseForks>true</reuseForks>` + 2 `<excludedGroups>flaky</excludedGroups>` invariants
- ci.yml: `concurrency:` workflow-level block (1) + 2 `--no-transfer-progress` + Playwright-Ubuntu-Plucky rationale + release-run-25609204039 ref preserved
- ci.yml commit body documents CD-06 Discretion for the workflow-prefixed group expression
- `mariadb-migration-smoke.yml`: zero edits
- 79-04-SUMMARY.md (written at plan close) includes the `## D-07 Trigger-Paths Review (Task 2.5)` section with the captured grep output + verdict
</verification>

<success_criteria>
- 2 atomic commits land
- `./mvnw verify -Pe2e` BUILD SUCCESS
- All Schutzwort / load-bearing-context invariants hold
- mariadb-migration-smoke.yml body untouched
- D-07 trigger-paths review verdict recorded in 79-04-SUMMARY.md
- CD-06 Discretion for ci.yml group expression documented in commit body
- Branch unchanged
</success_criteria>

<output>
After completion, create `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-04-SUMMARY.md` per `@$HOME/.claude/get-shit-done/templates/summary.md`. Include: both commit SHAs, per-file diff sizes, JEP/argLine/forkCount counts in pom.xml, concurrency + no-transfer-progress + Playwright-pitfall preservation proofs in ci.yml, mariadb-migration-smoke.yml zero-diff proof, AND a `## D-07 Trigger-Paths Review (Task 2.5)` section containing the captured `grep -n -B1 -A5 "paths:" .github/workflows/mariadb-migration-smoke.yml` output and the verdict line ("PASS — path-filter present, no changes needed" or "NEEDS_CONTEXT — escalated"), AND a `## CD-06 Discretion` section documenting the workflow-prefixed group-expression refinement (must_haves.truths line 4).
</output>
