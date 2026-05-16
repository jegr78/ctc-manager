# Feature Research

**Domain:** Tooling Infrastructure — OpenRewrite, Clean-Code Enforcement, Renovate, SAST (CTC Manager v1.11)
**Researched:** 2026-05-16
**Confidence:** HIGH (OpenRewrite, CodeQL/Semgrep licensing, Renovate Maven support via official docs) / MEDIUM (SpotBugs+Lombok interaction — open GitHub issues, no authoritative definitive fix documented) / HIGH (Checkstyle/PMD Maven plugin configuration)

---

## Scope

Four independent tooling streams introduced in v1.11. Each stream is analyzed with its own table-stakes / differentiator / anti-feature breakdown and developer-workflow description. The streams are:

1. **OpenRewrite** — recipe-based automated refactoring (Java version upgrades, Spring Boot migration, code style)
2. **Clean-Code Enforcement** — Checkstyle + PMD + SpotBugs as Maven verify gates
3. **Renovate** — recurring automated PRs for pom.xml + workflow file dependency bumps
4. **Security SAST** — CodeQL or Semgrep static security analysis in CI

The existing tech-debt items in v1.11 (Phase 75 REVIEW.md cleanup, polish sweep, test wallclock reduction, Nyquist VALIDATION docs) require no feature research — they are already-understood implementation tasks.

---

## Stream 1: OpenRewrite

**Complexity: MEDIUM**

OpenRewrite is an AST-aware automated refactoring platform. It transforms source code and build files using composable recipes, without requiring any code changes by hand. The Maven plugin (`rewrite-maven-plugin`) integrates directly into the existing Maven lifecycle.

The project is **already on Java 25 and Spring Boot 4**, so the primary use of OpenRewrite here is:
- Applying `CommonStaticAnalysis` and best-practices recipes to clean up existing code patterns
- Establishing the `dryRun` gate so future Sprint Boot 5 or Java 26/27 upgrades are automated
- Enabling custom project-specific recipes for things like enforcing naming conventions

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| `rewrite:dryRun` as CI gate | Without dryRun, OpenRewrite has no enforcement role; it's just a one-shot local tool | LOW | `failOnDryRunResults=true` in pom.xml plugin config; outputs `target/site/rewrite/rewrite.patch` |
| `rewrite:run` local apply | Developers need a way to actually apply recipe fixes locally before pushing | LOW | `./mvnw rewrite:run` — modifies files in-place, commit the result |
| Active recipe selection | The plugin is useless without at least one recipe selected; must choose the right set | LOW | Configured in `<activeRecipes>` in pom.xml; recipe artifact added as plugin `<dependency>` |
| `CommonStaticAnalysis` recipe | Covers 60+ common Java code quality issues — the most valuable day-one recipe | MEDIUM | Requires `org.openrewrite.recipe:rewrite-static-analysis` as plugin dependency |
| Spring Boot best-practices recipe | `SpringBoot3BestPracticesOnly` (not the upgrade recipe) applies Spring-specific code patterns | LOW | Part of `rewrite-spring` artifact; does NOT trigger version upgrades — safe to run on existing SB4 code |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| `UpgradeToJava25` recipe pre-staged | Project is on Java 25 already; having the recipe configured means Java 26/27 upgrade is a one-command operation | LOW | Recipe from `rewrite-migrate-java`; no harm running dryRun now — confirms zero changes needed, proving readiness |
| Spring Boot 4 → future community recipe | `UpgradeSpringBoot_4_0` recipe is composite and covers ~30 sub-recipes including Hibernate 7.1+, Spring Security 7+; pre-staging means next upgrade is automated | MEDIUM | Uses `rewrite-spring:6.30.4`+; community edition = free, Moderne Source Available License |
| PR annotation workflow | Second GitHub Actions job downloads the patch file from dryRun and posts line-level code suggestions to PR as review comments — operator sees clickable one-click-accept fixes | HIGH | Requires split workflow (untrusted/trusted) for security; significant YAML complexity; nice-to-have, not required for the gate to work |
| Custom project recipe (YAML) | Team can write a `rewrite.yml` at the project root to encode CTC-specific patterns (e.g., require `@Slf4j` on all service classes, ban `System.out`) | LOW | No Java required; purely declarative YAML; zero build overhead |

### Anti-Features

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Import-order recipe (`OrderImports`) | Looks like low-hanging fruit | Rewrites every existing import block in the codebase at once, producing a giant noisy diff that makes code review of actual logic changes impossible | Only enable if the project adopts Checkstyle import-order rules at the same time, and then enable both on the same phase so the cleanup is one batch commit |
| `dryRun` bound to `verify` phase (always-on gate) | Seems like the logical enforcement point | Makes `./mvnw verify` significantly slower for every developer — OpenRewrite parses and analyzes all source files on every build | Run dryRun as a separate CI job only, not bound to the local verify lifecycle; keep local builds fast |
| Spring Boot 4 → 5 upgrade recipe run immediately | OpenRewrite can do it | Spring Boot 5 is not released yet (as of 2026-05-16); running the community recipe now would apply speculative migrations against a moving target | Pre-stage the recipe config but gate it behind a comment-flag; activate only after SB5 GA |
| Using `rewrite:run` in CI (auto-apply on PR) | Zero-effort code improvement | CI auto-committing into open PRs creates merge conflicts, breaks branch protection, and makes it impossible to reason about what changed in a PR | Use dryRun + PR comment suggestions; human applies the fix before merging |

### Developer Workflow

**Day-to-day (zero friction):** `./mvnw rewrite:dryRun` run as a separate CI job on every PR. If it produces a non-empty patch, the job fails and the PR is blocked. The developer runs `./mvnw rewrite:run` locally, reviews the diff (`git diff`), commits the changes, pushes again.

**What the operator sees on a blocked PR:** CI check `openrewrite-dryrun` fails. In the job log: list of files that would be changed with descriptions of each violation (e.g., "Use try-with-resources instead of finally block in BackupExportService.java:142"). The `target/site/rewrite/rewrite.patch` file is uploaded as a CI artifact.

**Dependencies on existing CTC infrastructure:**
- No conflict with JaCoCo — OpenRewrite runs as a separate Maven execution, not in the test lifecycle
- No conflict with the `exec-maven-plugin` grep-gate (PLAT-07) — that grep runs at `verify`, OpenRewrite runs in its own job
- Maven cache in `ci.yml` (actions/setup-java cache: maven) already caches the OpenRewrite recipe JARs

---

## Stream 2: Clean-Code Enforcement (Checkstyle + PMD + SpotBugs)

**Complexity: MEDIUM**

Three complementary static analysis tools with distinct roles:

- **Checkstyle:** Formatting and naming conventions (whitespace, braces, Javadoc, import order, line length)
- **PMD:** Code-smell patterns (dead code, empty catch blocks, too-complex methods, missing braces, unnecessary object creation)
- **SpotBugs:** Bug pattern detection (null dereferences, resource leaks, incorrect synchronization, serialization issues, security vulnerabilities)

All three have Maven plugins that bind to the `verify` phase via a `check` goal and fail the build on violations.

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Build-breaking gate (`check` goal) | Without fail-on-violation, the tools are reports that developers ignore | LOW | Checkstyle: `checkstyle:check` goal; PMD: `pmd:check` goal; SpotBugs: `spotbugs:check` goal |
| Console output of violations | Developers need to see what failed without reading XML reports | LOW | `<consoleOutput>true</consoleOutput>` in Checkstyle; PMD outputs to console by default; SpotBugs needs `<effort>Max</effort>` + `<threshold>Low</threshold>` |
| Phase binding at `verify` | Tests must pass first; compilation must succeed before style checks run | LOW | Bind all three plugins to the `verify` phase, not `validate` — avoids spurious parse errors on code javac would have rejected anyway |
| Rule-file committed to repo | Rules must be versioned; ad-hoc per-developer configs diverge immediately | LOW | `src/main/resources/checkstyle.xml` (or project root); PMD: `pmd-ruleset.xml`; SpotBugs: `spotbugs-exclude.xml` |
| SpotBugs `excludeFilterFile` | SpotBugs has known false positives on Lombok-generated code (`EI_EXPOSE_REP`, `EI_EXPOSE_REP2` from `@Value` + `@Singular`) | LOW | Add `spotbugs-exclude.xml` with suppression for `EI_EXPOSE_REP` on classes annotated with Lombok annotations; also suppress `SBSC_USE_STRINGBUFFER_CONCATENATION` which fires on Lombok-generated `toString()` patterns |
| `lombok.addLombokGeneratedAnnotation = true` in `lombok.config` | SpotBugs 4.7+ added `@javax.annotation.processing.Generated` detection — if Lombok stamps generated code with this annotation, SpotBugs can skip it | LOW | Add single line to `lombok.config` at project root; HIGH confidence this reduces false-positive noise |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Google Java Style as Checkstyle base | Well-maintained, widely known ruleset; covers indentation, braces, naming, import order without requiring custom XML from scratch | LOW | `com.puppycrawl.tools:checkstyle` bundles `google_checks.xml`; reference via `<configLocation>google_checks.xml</configLocation>`; customize line length to 140 (Spring Boot open-source standard) |
| PMD `pmd.xml` pointing at `category/java/bestpractices.xml` + `category/java/errorprone.xml` only | Narrower than `rulesets/java/quickstart.xml`; avoids the design and code-style categories that generate noise on a mature codebase | MEDIUM | Start narrow — it's much easier to add rules than to remove them after developers are used to passing CI |
| SpotBugs `find-sec-bugs` plugin | Adds security-focused bug patterns (SSRF, XSS, SQL injection, insecure deserialization, XXE) on top of base SpotBugs | MEDIUM | Add `com.h3xstream.findsecbugs:findsecbugs-plugin` as a SpotBugs plugin; ~150 Java security rules; MEDIUM confidence on false-positive rate for Spring Boot apps |
| Checkstyle `suppressions.xml` for generated/migration packages | Exclude `org.ctc.backup.io` (24 MixIns auto-generated patterns) and `org.ctc.backup.restore` (24 EntityRestorers) from Checkstyle line-length checks | LOW | `<suppressionsLocation>checkstyle-suppressions.xml</suppressionsLocation>` in Checkstyle config |

### Anti-Features

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Checkstyle import-order enforcement on existing code | Looks like a quick win for consistency | The current codebase has ~42 Java files in `org.ctc.*`; enforcing import order retroactively creates a massive noise PR that touches every file and breaks `git blame` for all history | Either skip import-order entirely (use OpenRewrite `OrderImports` as a one-time cleanup first), or suppress ImportOrder in Checkstyle and treat it as an OpenRewrite concern |
| SpotBugs nullness analysis (NP_* rules at `Low` threshold) | Sounds valuable for catching null bugs | With Lombok `@NonNull` and Spring's `@Nullable`/`@NonNull` mix, the NP_* rules at Low threshold fire hundreds of false positives on service layer constructor parameters | Keep SpotBugs threshold at `Medium` or `High` for the initial rollout; lower only after baseline is clean |
| PMD `AvoidDuplicateLiterals` rule | Catches repeated string literals | The test codebase has many repeated string constants like `"Test-Season 2026"` and `"T-ALF"` which are intentional isolation prefixes — the rule would fire on every test class | Suppress `AvoidDuplicateLiterals` in the PMD ruleset or scope it to `src/main/java` only via a separate execution |
| Running all three tools on every local `./mvnw verify` | Seems like complete local parity with CI | Adds 30-60 seconds to every local verify run; developers will start skipping verify or being frustrated by the feedback loop | In `pom.xml`, bind the three `check` goals to the `verify` phase but make them CI-only via a `-Pstatic-analysis` profile; developers run `./mvnw verify` for tests, CI adds `-Pstatic-analysis` |
| SpotBugs on test classes | Comprehensive coverage | SpotBugs fires `DM_DEFAULT_ENCODING`, `RV_RETURN_VALUE_IGNORED`, and `OBL_UNSATISFIED_OBLIGATION` on MockMvc test patterns that are intentionally ignoring return values | Exclude `src/test/**` from SpotBugs via `<sourceDirectory>` configuration or `<excludeFilterFile>` pattern |

### Developer Workflow

**On a clean PR:** All three `check` goals run as part of `./mvnw verify` (or the CI job). Green — no output except the normal build log.

**On a failing PR:** CI `build-and-test` job fails at the verify phase. The console log shows:
- Checkstyle: file path + line number + rule name + violation description (e.g., `BackupExportService.java:42: 'method def modifier' has incorrect indentation level 2, expected level should be 4`)
- PMD: file + line + rule category + message (e.g., `BackupImportService.java:139 EmptyCatchBlock: Avoid empty catch blocks`)
- SpotBugs: Bug pattern code + class + method + description (e.g., `EI_EXPOSE_REP2 in BackupManifest.getTableCounts()`)

The developer fixes the violation locally, runs `./mvnw verify` (or the specific tool: `./mvnw checkstyle:check`), confirms green, commits.

**Dependencies on existing CTC infrastructure:**
- The `exec-maven-plugin` grep-gate at verify already runs before compile — static analysis plugins must be sequenced after compile (verify phase is correct)
- JaCoCo coverage gate and static analysis gates are both in `verify`; they are independent and can both fail in the same build run (`-fae` flag shows all failures)
- Lombok 1.18.46 with `--sun-misc-unsafe-memory-access=allow` JVM arg already in Surefire/Failsafe; SpotBugs runs in its own fork and needs the same arg added to `<jvmArgs>` in spotbugs-maven-plugin config
- The `lombokGeneratedAnnotation` in `lombok.config` is a project-root file — if it doesn't exist, create it; if it does exist, add the line

---

## Stream 3: Renovate

**Complexity: LOW**

Renovate is a bot that opens automated PRs whenever a new version of a dependency is released. It processes `pom.xml` and GitHub Actions workflow files, groups related updates, and respects a configurable schedule. Setup requires: (1) installing the Renovate GitHub App on the repository, (2) committing a `renovate.json` configuration file.

Renovate is distinct from Dependabot (GitHub's built-in dependency updater). They can coexist but create duplicate PRs for the same dependency — if the project uses Dependabot already, it should be disabled before enabling Renovate.

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Automatic PR creation on new versions | The core function; without it, Renovate is not running | LOW | Triggered by GitHub App install + `renovate.json` presence in repo root |
| `pom.xml` dependency version extraction | The project's dependencies are in `pom.xml`; Renovate must read them | LOW | Renovate's Maven manager detects `pom.xml` automatically; handles `<version>`, `<parent>` versions, plugin versions, and BOM `import` scope |
| GitHub Actions workflow file updates | `ci.yml`, `release.yml`, `deploy-site.yml`, `mariadb-migration-smoke.yml` use pinned action versions (e.g., `actions/checkout@v6`) — these must also be bumped | LOW | Renovate's `github-actions` manager handles `.github/workflows/*.yml` automatically |
| Semver-aware PR separation | Major version bumps (e.g., Spring Boot 4 → 5) must not be merged automatically; they need explicit review | LOW | Default Renovate behavior: separate PRs per major/minor; `separateMajorMinor: true` is default |
| Onboarding PR | First PR Renovate opens is the onboarding PR proposing a `renovate.json` — must be merged for Renovate to start working | LOW | If `renovate.json` is committed manually before app install, the onboarding PR is skipped |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Spring Boot BOM grouping | Spring Boot manages dozens of transitive versions via BOM; Renovate should create one PR for the BOM bump, not 30 PRs for each transitive | LOW | `groupName: "Spring Boot"` + `matchPackagePrefixes: ["org.springframework.boot:"]` in `packageRules` |
| Monday-only schedule | Keeps CI clear during active development; batch updates arrive at the start of the week as a predictable ritual | LOW | `"schedule": ["before 9am on Monday"]` in `renovate.json` |
| Patch automerge after CI passes | Patch-level bumps (e.g., Guava 33.4.8 → 33.4.9) are low-risk; auto-merging them eliminates toil | LOW | `matchUpdateTypes: ["patch"]`, `automerge: true` — CI tests gate the merge |
| Security update bypass | CVE-triggered Renovate PRs ignore the Monday schedule and open immediately | LOW | Default Renovate behavior when `vulnerabilityAlerts` is enabled — no config required |
| Group all Playwright updates | Playwright version is coupled to the Dockerfile `eclipse-temurin` noble pin; grouping Playwright + Dockerfile base image updates ensures they arrive together | MEDIUM | Custom `packageRules` entry grouping `com.microsoft.playwright:playwright` + the Dockerfile base image |

### Anti-Features

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Major version automerge | Eliminates even more toil | Major bumps (Java 25 → 26, Spring Boot 4 → 5) require intentional migration work, not just version bumping — automerging them silently would break the build or introduce behavioral changes | Keep major PRs open for manual review; they are the trigger to run OpenRewrite migration recipes |
| `automergeType: "branch"` (silent merges) | Reduces PR noise | Merges without a PR means CI runs, but no human sees the change; if a patch bump introduces a subtle behavioral regression, there's no PR to review or revert | Use `automergeType: "pr"` — the PR is auto-merged only after CI is green, but there is still a PR object in GitHub history for auditability |
| Enabling Renovate alongside existing Dependabot | Using both tools | Creates duplicate PRs for the same dependency; confusing which one to merge; they can conflict on the same branch | Check `.github/dependabot.yml` — if it exists, remove or disable it before enabling Renovate |
| `rebaseWhen: "always"` | Keeps Renovate PRs rebased on master | Causes Renovate to force-push its branches constantly, generating excessive CI runs and notifications; on an active repo this creates noise | Use `rebaseWhen: "behind-base-branch"` (the default) — only rebase when the base branch has new commits that conflict |
| Updating `pom.xml` property versions (`<spring-boot.version>3.x`) | Renovate can do it with regex manager | Requires a custom regex manager config with datasource comments above each property — significant maintenance overhead for each pinned property | Keep version properties managed by the Spring Boot parent BOM; properties that are already resolved via BOM import don't need regex manager treatment |

### Developer Workflow

**Typical week:** On Monday morning (per schedule), Renovate opens 1-3 PRs:
- One grouped PR: "Update Spring Boot" (minor or patch bump for `spring-boot-starter-parent`)
- One PR per non-grouped library with a new version (e.g., "Update Guava to 33.4.9")
- GitHub Actions workflow action bumps (e.g., "Update actions/checkout to v7")

**PR structure:** Each Renovate PR includes a description with release notes, links to the changelog, and the specific diff in `pom.xml`. CI runs automatically. If all tests are green and it's a patch bump with automerge enabled, it merges automatically. The operator gets a GitHub notification of the auto-merge.

**On a major bump PR (e.g., Java 26):** PR stays open until the operator reviews it, runs OpenRewrite migration recipes locally, and squash-merges it manually.

**Dependencies on existing CTC infrastructure:**
- Renovate reads `pom.xml` — no changes to pom.xml needed for discovery
- The `ci.yml` concurrency block prevents multiple Renovate PRs from running CI simultaneously — this is already correct behavior
- The Dockerfile `noble-pin-guard` CI job ensures that if Renovate bumps the `eclipse-temurin` base image version in the Dockerfile, the guard will catch any unpinned `-noble` suffix issue
- If Dependabot is not configured (no `.github/dependabot.yml`), there is no conflict
- Renovate GitHub App requires installation at `github.com/apps/renovate` — this is a one-time manual step by the repo owner

---

## Stream 4: Security SAST (CodeQL vs Semgrep)

**Complexity: LOW (Semgrep OSS) / HIGH (CodeQL with GHAS)**

**Critical licensing finding:** CodeQL code scanning in **private repositories requires GitHub Advanced Security (GHAS)**, which is a paid license (~$30/committer/month). The CTC Manager repo is a private single-admin repo — CodeQL is not free here.

**Recommendation: Use Semgrep OSS.** Semgrep Community Edition is free for unlimited private repositories, zero cost, no token required for the OSS CLI scan mode. Trade-off: Semgrep CE performs single-file analysis only (no cross-file dataflow); CodeQL performs full semantic dataflow analysis. For a single-team admin app, Semgrep's 3,000+ community rules covering Java security patterns (SSRF, SQL injection, path traversal, XSS, insecure deserialization) are sufficient for the SAST goal.

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| PR-triggered scan | SAST that only runs on push to master provides no early warning; must run on every PR | LOW | Semgrep: trigger on `pull_request` event in workflow; CodeQL: same |
| Java security rules | The codebase is Java — language-specific rules for SSRF, SQL injection, path traversal, deserialization are required | LOW | Semgrep: `--config p/java` or `--config p/java.lang.security`; community ruleset includes Spring-specific patterns |
| GitHub Actions workflow security scan | The CI workflows themselves (`ci.yml`, `release.yml`) should be scanned for command injection (untrusted `${{ github.event.head_commit.message }}` interpolation) | LOW | Semgrep: `--config p/github-actions`; free ruleset includes common Actions injection patterns |
| Build-blocking on HIGH severity | LOW/MEDIUM findings should not block PRs (too much noise initially); only HIGH severity stops the merge | LOW | Semgrep: `--severity ERROR` flag in CI command; SARIF upload to GitHub Security tab for all severities |
| SARIF report upload | Findings must be visible in GitHub Security tab, not just in CI logs | LOW | `upload-sarif` action from `github/codeql-action/upload-sarif@v3`; requires `security-events: write` permission on the workflow |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| `find-sec-bugs` rules via Semgrep community | Spring Boot-specific patterns: exposed actuators, weak crypto, HTTP response splitting, open redirect — covers the exact attack surface the CTC Manager admin interface has | LOW | Already included in `p/java` ruleset; no extra configuration |
| Scheduled full scan in addition to PR scan | PR scan covers changed files; weekly full scan covers the entire codebase including files not recently touched | LOW | Add `schedule: cron` trigger to the SAST workflow; weekly Saturday scan; upload all findings to Security tab |
| SARIF to GitHub Security tab | Findings accumulate in the Security > Code scanning alerts tab, not just transient CI logs; operator can triage, dismiss with justification, and track which findings are addressed | LOW | Requires `security-events: write` + `github/codeql-action/upload-sarif@v3` action |
| Suppress known false positives via `.semgrepignore` | Similar to `.gitignore`; suppress specific findings that are verified-safe so they don't re-appear on every PR | LOW | Add `.semgrepignore` at project root; suppress by file path pattern or rule ID |

### Anti-Features

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| CodeQL for private repo | Deeper semantic analysis, native GitHub integration | Requires GHAS license (~$30/committer/month); private repo with 1 committer is ~$30/month minimum; overkill for a single-admin app | Semgrep OSS covers the same rule surface at zero cost; if the repo ever becomes public, switch to CodeQL for free |
| Semgrep `--config auto` (auto-detect) | Scans everything automatically | `auto` mode sends metadata to Semgrep servers to select rules — this is a data-sharing decision; also slower than explicit rule selection | Use explicit `--config p/java --config p/github-actions` to control exactly which rules run and avoid server calls |
| Blocking PRs on MEDIUM and LOW findings | Thorough coverage | With 3,000+ community rules, a new Java project scan will typically produce 10-50 LOW/MEDIUM findings on first run — blocking PRs on these would halt all development immediately | Start with `--severity ERROR` (HIGH only) as the blocking threshold; upload all severities to Security tab for visibility without blocking; tighten after the baseline is clean |
| Semgrep Cloud platform token (SEMGREP_APP_TOKEN) | Enables cross-file analysis and Pro rules | Requires creating a Semgrep account and storing a secret; cross-file analysis and 20,000 Pro rules are overkill for a single-admin internal app | Semgrep OSS CLI (`semgrep scan --config p/java`) runs fully locally in CI with no external calls; sufficient for this project size |
| Running SAST in the same CI job as `build-and-test` | Simpler workflow | SAST scan adds 30-60 seconds; if it fails it's unclear whether the failure is tests or SAST; harder to understand in the PR checks list | Run SAST as a separate GitHub Actions job (`sast` job) so failures are clearly attributed |

### Developer Workflow (Semgrep OSS)

**On a clean PR:** `sast` CI job runs `semgrep scan -q --sarif --config p/java --config p/github-actions --severity ERROR`, outputs clean SARIF, uploads to Security tab. Job is green. Developer sees a separate green check "SAST / semgrep" in the PR checks.

**On a failing PR (HIGH severity finding):** `sast` job fails. In the CI log:
```
[HIGH] path/to/File.java:123 (java.lang.security.audit.ssrf.UrlOpenStream) 
  Possible SSRF via user-controlled URL
  Found: new URL(userInput).openStream()
```

The developer inspects the finding, fixes the code pattern, pushes. Or, if it's a false positive (e.g., the URL is validated by the SSRF hostname blocklist before use), adds a `# nosemgrep: java.lang.security.audit.ssrf.UrlOpenStream` inline suppression comment.

**SARIF findings in Security tab:** All findings (including MEDIUM and LOW) accumulate in GitHub Security > Code scanning alerts. The operator can triage them independently of whether they blocked a PR. Findings are linked to the specific line of code and include the rule description.

**Dependencies on existing CTC infrastructure:**
- The `sast` job needs `permissions: security-events: write, contents: read` — these are currently not in `ci.yml`; the CI workflow must add a `sast` job with these permissions, or the top-level `permissions` block must be extended (it currently has `contents: read, pull-requests: write`)
- The existing CI concurrency block cancels in-progress runs on the same ref — the SAST job will benefit from this automatically
- Semgrep scan does not require a running application, Maven, or JDK — it analyzes source files directly; the `sast` job can run in parallel with `build-and-test`, not after it
- No conflict with JaCoCo, OpenRewrite, or static analysis Maven plugins — Semgrep is a separate tool that does not touch the Maven lifecycle

---

## Cross-Stream Feature Dependencies

```
Renovate
    └──produces──> Dependency bump PRs
                       └──requires──> Clean-Code gates to pass (Checkstyle/PMD/SpotBugs)
                       └──requires──> SAST to pass (Semgrep)
                       └──requires──> OpenRewrite dryRun to pass

OpenRewrite dryRun gate
    └──depends on──> Selected active recipes (must be chosen first)
    └──enhances──> Clean-Code (CommonStaticAnalysis fixes what Checkstyle/PMD/SpotBugs would flag)

SpotBugs (find-sec-bugs plugin)
    └──overlaps with──> Semgrep SAST (both find security patterns)
    └──note──> SpotBugs finds patterns in compiled bytecode; Semgrep finds patterns in source text; complementary

Checkstyle/PMD/SpotBugs
    └──requires──> lombok.config with addLombokGeneratedAnnotation=true
    └──requires──> spotbugs-exclude.xml for Lombok false positives
```

---

## Phase Sizing Estimates

| Stream | Estimated Phase Effort | Key Tasks |
|--------|----------------------|-----------|
| OpenRewrite | 1 phase, LOW-MEDIUM | Add plugin to pom.xml, select active recipes, add CI job, run cleanup batch, commit results |
| Clean-Code (Checkstyle + PMD + SpotBugs) | 1-2 phases, MEDIUM | Configure all 3 plugins with correct rules, fix initial violations (may be a separate cleanup wave), add suppression files for Lombok |
| Renovate | 1 phase, LOW | Install GitHub App, commit renovate.json with grouping+schedule, verify onboarding PR, disable Dependabot if present |
| Security SAST (Semgrep) | 1 phase, LOW | Add `sast` job to ci.yml with Semgrep scan + SARIF upload, fix initial HIGH findings (likely 0-2 given existing security hardening) |

---

## Sources

- [OpenRewrite Maven Plugin docs](https://docs.openrewrite.org/reference/rewrite-maven-plugin) — HIGH confidence, official
- [Migrate to Java 25 recipe](https://docs.openrewrite.org/recipes/java/migrate/upgradetojava25) — HIGH confidence, official
- [CommonStaticAnalysis recipe](https://docs.openrewrite.org/recipes/staticanalysis/commonstaticanalysis) — HIGH confidence, official
- [Spring Boot 4.0 upgrade recipe](https://docs.openrewrite.org/recipes/java/spring/boot4/upgradespringboot_4_0-community-edition) — HIGH confidence, official
- [OpenRewrite dryRun as CI gate](https://www.moderne.ai/blog/stop-breaking-ci-annotate-prs-with-openrewrite-recipe-fixes-as-quality-gate) — MEDIUM confidence, Moderne blog
- [Renovate Maven manager docs](https://docs.renovatebot.com/modules/manager/maven/) — HIGH confidence, official
- [Renovate Java docs](https://docs.renovatebot.com/java/) — HIGH confidence, official
- [Renovate configuration options](https://docs.renovatebot.com/configuration-options/) — HIGH confidence, official
- [Apache Maven Checkstyle Plugin usage](https://maven.apache.org/plugins/maven-checkstyle-plugin/usage.html) — HIGH confidence, official
- [SpotBugs Maven plugin](https://spotbugs.readthedocs.io/en/stable/maven.html) — HIGH confidence, official
- [SpotBugs false positive with Lombok @Singular](https://github.com/spotbugs/spotbugs/issues/2140) — MEDIUM confidence, GitHub issue
- [PMD Java support and version compatibility](https://pmd.github.io/pmd/pmd_languages_java.html) — HIGH confidence, official (PMD 7.x supports Java 25+)
- [CodeQL private repo requires GHAS](https://docs.github.com/en/code-security/code-scanning/introduction-to-code-scanning/about-code-scanning-with-codeql) — HIGH confidence, official GitHub docs
- [Semgrep CE free for private repos](https://appsecsanta.com/semgrep) — MEDIUM confidence, third-party review (corroborated by Semgrep OSS LGPL-2.1 license)
- [Semgrep vs CodeQL comparison](https://konvu.com/compare/semgrep-vs-codeql) — MEDIUM confidence, third-party analysis
- [CodeQL GitHub Actions permissions](https://github.com/github/codeql-action) — HIGH confidence, official

---

*Feature research for: v1.11 Tooling Infrastructure (OpenRewrite, Clean-Code, Renovate, SAST)*
*Researched: 2026-05-16*
