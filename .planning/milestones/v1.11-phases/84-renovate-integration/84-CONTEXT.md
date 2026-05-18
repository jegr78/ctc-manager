# Phase 84: Renovate Integration - Context

**Gathered:** 2026-05-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Install the Mend Renovate GitHub App against `jegr78/ctc-manager` after a root-level `renovate.json` is in place that encodes the eight CTC-specific safety rules (DEPS-01..08). The phase delivers automated dependency-update PRs against `pom.xml`, GitHub Actions `uses:` clauses, and Dockerfile base images, with `packageRules` that PREVENT four classes of unwanted proposals before any of them can land:

1. Guava `-android` classifier variants (DEPS-03 — `allowedVersions` regex restricting to `-jre` only; preserves the Java 25 VarHandle path in `AbstractFuture`)
2. `org.thymeleaf:thymeleaf` bumps (DEPS-04 — explicitly disabled; 3.1.5.RELEASE pin is a CVE-2026-40478 mitigation)
3. Non-LTS Java versions (DEPS-05 — `allowedVersions` regex restricting `java.version` proposals to `^(?:11|17|21|25|29)`)
4. `eclipse-temurin` tags without the `-noble` suffix (defense-in-depth; the existing `dockerfile-noble-pin-guard` CI job is the authoritative gate, this is belt-and-braces)

Replaces the existing `.github/dependabot.yml` (which schedules weekly Maven/github-actions/docker bumps with `open-pull-requests-limit: 5` each) — running both bots in parallel would create duplicate PRs on identical dependencies. The Dependabot config is removed in the same commit that introduces `renovate.json` so PR #1 leaves the repository with exactly one dependency-update bot active. Automerge for `patch` updates is enabled from PR #1 with `automergeType: "pr"` (auditable, no silent branch merges); `minor`/`major` always require manual review.

**In scope:**
- Root-level `renovate.json` covering Maven + GitHub Actions + Dockerfile managers via `enabledManagers: ["maven", "github-actions", "dockerfile"]` (DEPS-01)
- Removal of `.github/dependabot.yml` in the same commit (avoids dual-bot duplicate PRs)
- All seven CTC-specific `packageRules` entries (Guava `-jre` regex, Thymeleaf disabled, Java LTS regex, Spring Boot grouping, Spring Security grouping, Google API grouping, Testcontainers grouping)
- Three additional `packageRules` entries derived from prior phases: OpenRewrite group with manual-review (Phase 80 D-08 migration-trigger pattern), SpotBugs + find-sec-bugs group (Phase 81 plugin-pair), Playwright manual-review (Dockerfile `-noble` compatibility per Phase 78)
- `automerge: true` + `automergeType: "pr"` on `patch` updates (DEPS-07)
- Schedule + concurrency caps so the firehose stays manageable for a solo maintainer
- `vulnerabilityAlerts` enabled so CVE-driven PRs bypass the schedule
- Mend Renovate GitHub App installation against `jegr78/ctc-manager` (DEPS-02)
- Verification that the existing `dockerfile-noble-pin-guard` CI job still passes after Renovate's first Dockerfile-bump PR opens (DEPS-08 / SC#6)

**Out of scope (deliberate):**
- Self-hosted Renovate via GitHub Actions runner (Mend GitHub App is the explicit choice per research SUMMARY.md Stream 3; zero runner-minute cost)
- Renovate dashboard issue curation (DEPS-FUTURE-01 — the dashboard itself is default-enabled and free; curated review queue is deferred until the team grows beyond a single maintainer)
- Major-bump automerge for any dependency class (explicitly OUT of milestone scope per REQUIREMENTS.md "Out of Scope" line "Renovate auto-merge for major bumps")
- Regex managers for pinned property versions like `<playwright.version>1.59.0</playwright.version>` (Renovate handles `<properties>` versions natively when they back a `<version>${prop}</version>` reference; verified via `pom.xml` lines 18 + 206)
- Custom CTC-specific recipe configuration on OpenRewrite recipe-pack bumps (recipe-pack bumps get manual-review treatment, but the manual migration run itself is operator-driven and lives outside Renovate's scope)
- Modifying `.github/workflows/ci.yml` `dockerfile-noble-pin-guard` job (the guard is the authoritative gate; this phase verifies it still passes — never edits it)
- CodeQL SAST workflow (Phase 85 scope)
- Any tech-debt cleanup or wallclock work (Phases 86, 87 scope)

</domain>

<decisions>
## Implementation Decisions

### Bot Selection & Installation

- **D-01:** **Mend Renovate GitHub App, not self-hosted Action.** Research SUMMARY.md Stream 3 locks this: free, zero runner-minute cost, no `renovate.yml` workflow file needed. Self-hosted would also have to maintain a renovate version pin that Mend handles automatically.
- **D-02:** **Pre-commit `renovate.json` BEFORE installing the GitHub App.** Order matters: if the app is installed first against a bare repo, Mend's auto-generated onboarding PR uses default config that does NOT include the CTC-specific `packageRules` (Guava/Thymeleaf/Java/Spring Boot grouping). Pre-committing the curated config guarantees DEPS-03..05 safety rules are active from the FIRST scan, not from the onboarding PR merge. DEPS-02 ("at least one onboarding PR") is still satisfied because Mend produces a config-validation onboarding PR even when `renovate.json` exists — it confirms the config parses and lists what Renovate will do.

### Existing Dependabot Disposition

- **D-03:** **Remove `.github/dependabot.yml` in the same commit that introduces `renovate.json`.** Research PITFALLS Stream 3 documents this as a duplicate-PR pitfall. Existing config schedules three managers (`maven`, `github-actions`, `docker`) weekly Monday with `open-pull-requests-limit: 5` each — Renovate covers all three managers via DEPS-01's `enabledManagers` list, so removal is lossless. Same commit ensures the repository never has both bots active simultaneously. Commit message body must explain "Replace Dependabot with Renovate per Phase 84" so future archaeology is clear.

### Configuration File Location & Schema

- **D-04:** **`renovate.json` at project root** (NOT `.github/renovate.json` or `renovate.json5`). Matches DEPS-01 ("root-level `renovate.json`"). JSON over JSON5 because (a) Renovate's docs use JSON examples; (b) GitHub's `renovate-schema.json` is JSON-validated; (c) the project has no JSON5 elsewhere, no need to introduce a new format.
- **D-05:** Top of file: `"$schema": "https://docs.renovatebot.com/renovate-schema.json"` enables IDE schema validation. `"extends": ["config:recommended"]` as the base preset (replaces deprecated `config:base`; current Renovate canonical recommendation).

### Schedule & PR Firehose Controls

- **D-06:** **`schedule: ["before 6am on Monday"]`** matches the existing Dependabot Monday cadence so the operator's mental model carries over. 6am UTC is off-hours for the maintainer's likely timezone (Europe) — PRs land before the start of the working week.
- **D-07:** **`prConcurrentLimit: 5`** (matches Dependabot's existing `open-pull-requests-limit: 5` per manager → ~15 total, but Renovate groups aggressively so 5 grouped PRs is the realistic ceiling). **`prHourlyLimit: 2`** prevents a recipe-pack-bump cascade. **`branchConcurrentLimit: 10`** so Renovate doesn't pile up unmergeable branches.
- **D-08:** **`vulnerabilityAlerts: { enabled: true, labels: ["security"] }`** so CVE-driven Renovate PRs bypass the Monday schedule and the disabled rules (e.g., a Thymeleaf CVE WILL produce a PR even though the package is otherwise disabled). Standard practice. The `security` label flags these as priority in the GitHub UI.

### Package Rules (Safety + Grouping)

- **D-09:** **Guava — `-jre` classifier only (DEPS-03):**
  ```json
  {
    "matchPackageNames": ["com.google.guava:guava"],
    "allowedVersions": "/^[0-9.]+-jre$/",
    "description": "Pin -jre variants only — Java 25 VarHandle path in AbstractFuture (pom.xml:177-186)"
  }
  ```
  Regex form (`/.../`) is mandatory — bare `"-jre$"` would match literally. Cross-reference to the existing `pom.xml` rationale comment lives in the rule's `description` field.

- **D-10:** **Thymeleaf — fully disabled (DEPS-04):**
  ```json
  {
    "matchPackageNames": ["org.thymeleaf:thymeleaf"],
    "enabled": false,
    "description": "Pin 3.1.5.RELEASE — CVE-2026-40478 mitigation; manual review required for any bump (pom.xml:28-31)"
  }
  ```
  `enabled: false` is the strongest form (no PRs ever). `vulnerabilityAlerts.enabled: true` still allows CVE-driven PRs to surface — that's the correct override path if a future Thymeleaf CVE supersedes the current pin.

- **D-11:** **Java — LTS only (DEPS-05):**
  ```json
  {
    "matchPackageNames": ["java"],
    "matchManagers": ["maven"],
    "allowedVersions": "/^(?:11|17|21|25|29)(?:\\.|$)/",
    "description": "LTS-only (11/17/21/25/29) — pom.xml:17 java.version property"
  }
  ```
  The regex anchors on `\\.|$` so `25` matches both `25` and `25.0.1`. `matchManagers: ["maven"]` restricts the rule to Maven contexts (the Dockerfile manager handles the `eclipse-temurin:25-jdk-noble` tag separately — see D-14).

- **D-12:** **Group: Spring Boot starters (DEPS-06):**
  ```json
  {
    "matchPackagePatterns": ["^org\\.springframework\\.boot:"],
    "groupName": "Spring Boot",
    "matchUpdateTypes": ["major"],
    "automerge": false,
    "dependencyDashboardApproval": true,
    "description": "Major Spring Boot bumps trigger manual OpenRewrite migration runs (Phase 80 precedent)"
  }
  ```
  Plus a complementary rule for non-major Spring Boot updates (grouped, `automerge: true` for patch, manual review for minor). Two rules combined yield: one grouped PR per bump cluster, patch-automerge, minor/major manual-review. Spring Boot parent BOM at pom.xml:7 transitively manages dozens of versions — grouping is essential to avoid a 30-PR avalanche.

- **D-13:** **Group: Spring Security starters (DEPS-06):**
  ```json
  {
    "matchPackagePatterns": ["^org\\.springframework\\.security:"],
    "groupName": "Spring Security"
  }
  ```
  Spring Security has its own release cadence within the Spring ecosystem; grouping separately from "Spring Boot" prevents one Spring Security CVE from bundling with unrelated Boot updates.

- **D-14:** **Group: Google API clients (DEPS-06):**
  ```json
  {
    "matchPackagePatterns": ["^com\\.google\\.apis:google-api-services-", "^com\\.google\\.api-client:google-api-client"],
    "groupName": "Google API clients"
  }
  ```
  Covers `google-api-client`, `google-api-services-sheets`, `google-api-services-calendar` (pom.xml:158-173). These ship as a cohort; ungrouped bumps create reconciliation churn.

- **D-15:** **Group: Testcontainers (DEPS-06):**
  ```json
  {
    "matchPackagePatterns": ["^org\\.testcontainers:"],
    "groupName": "Testcontainers"
  }
  ```
  Testcontainers BOM at pom.xml:35-39 currently pins `${testcontainers.version}` (2.0.5). Renovate updates the BOM `import` correctly — grouping ensures the BOM bump and the testcontainers-mariadb module bump arrive together when needed.

- **D-16:** **Group: OpenRewrite (Phase 80 follow-through):**
  ```json
  {
    "matchPackagePatterns": ["^org\\.openrewrite\\.maven:", "^org\\.openrewrite\\.recipe:"],
    "groupName": "OpenRewrite",
    "automerge": false,
    "dependencyDashboardApproval": true,
    "description": "Recipe-pack bumps trigger manual `./mvnw -Prewrite rewrite:dryRun` migration runs per DEPS-07 last clause"
  }
  ```
  Covers `rewrite-maven-plugin` + `rewrite-spring` + `rewrite-migrate-java` (pom.xml:471-491 inside the `<profile id="rewrite">` block). `dependencyDashboardApproval: true` makes each bump click-to-create on the dashboard rather than automatic — this matches the developer-invoked posture established in Phase 80.

- **D-17:** **Group: SpotBugs + find-sec-bugs (Phase 81 follow-through):**
  ```json
  {
    "matchPackagePatterns": ["^com\\.github\\.spotbugs:", "^com\\.h3xstream\\.findsecbugs:"],
    "groupName": "SpotBugs + find-sec-bugs",
    "matchUpdateTypes": ["minor", "major"],
    "automerge": false,
    "description": "New detector packs can change violation surface — manual review (Phase 81 D-11 precedent)"
  }
  ```
  Patch bumps still automerge by default. Minor+major requires manual review because new SpotBugs/find-sec-bugs detector patterns can introduce new violations against existing code (Phase 81 D-10 fix-vs-suppress posture).

- **D-18:** **Playwright manual-review (Phase 78 follow-through):**
  ```json
  {
    "matchPackageNames": ["com.microsoft.playwright:playwright"],
    "matchUpdateTypes": ["minor", "major"],
    "automerge": false,
    "description": "Playwright version coupled to Dockerfile -noble pin (Phase 78); minor bumps may require Dockerfile audit"
  }
  ```
  `playwright.version` property at pom.xml:18. Playwright 1.59.0 → 1.60.x could require an updated `eclipse-temurin:25-{jdk,jre}-noble` base — the bumps must arrive together or the Docker build breaks (release run 25609204039 precedent).

- **D-19:** **Dockerfile `eclipse-temurin` `-noble` suffix constraint (defense-in-depth for SC#6):**
  ```json
  {
    "matchDatasources": ["docker"],
    "matchPackageNames": ["eclipse-temurin"],
    "allowedVersions": "/^(?:25)(?:\\.[0-9.]+)?-(?:jdk|jre)-noble$/",
    "description": "Pin -noble suffix and Java 25 major — Playwright 1.59.0 Ubuntu Plucky incompatibility (Dockerfile:2,20)"
  }
  ```
  The `dockerfile-noble-pin-guard` CI job at .github/workflows/ci.yml:78-115 is the authoritative gate; this `packageRules` entry is belt-and-braces so Renovate never even proposes a bad tag. SC#6 is still testable because Renovate will propose minor base-image patch updates within `25-*-noble` (e.g., `25-jdk-noble` → `25.0.2-jdk-noble`) and the guard will pass.

- **D-20:** **Patch automerge default (DEPS-07):**
  ```json
  {
    "matchUpdateTypes": ["patch"],
    "automerge": true,
    "automergeType": "pr",
    "description": "Patch updates automerge after CI passes — auditable via PR object"
  }
  ```
  `automergeType: "pr"` (NOT `"branch"`) per research PITFALLS — branch automerges leave no PR object for audit. Patch automerge is configured from PR #1; the phase-goal phrasing "before automerge is enabled" refers to ordering within this phase (safety rules → automerge config) rather than deferring to a later phase. Both arrive in the same `renovate.json`.

### Labels, Assignees, PR Metadata

- **D-21:** **`labels: ["dependencies"]`** preserves the existing Dependabot label so any saved issue/PR filters keep working. Add `"renovate"` as a secondary label so search can disambiguate "which bot opened this" in the post-migration weeks.
- **D-22:** **`assignees: ["jegr78"]`** matches the CLAUDE.md `gh pr create --assignee jegr78` convention. Solo maintainer; explicit assignee surfaces the PR in the assignee inbox view.

### Dependency Dashboard

- **D-23:** **Default-enabled (Renovate creates one tracking issue listing every update).** DEPS-FUTURE-01 explicitly defers "curated review queue" — that's the FUTURE part, not the dashboard itself. The basic dashboard is free, costs zero CI minutes, and provides single-pane-of-glass visibility for the solo maintainer. Leave `dependencyDashboard: true` (default; do not override).

### Verification Strategy (DEPS-08 / SC#6)

- **D-24:** **DO NOT wait for Renovate to organically open a Dockerfile-bump PR before declaring the phase complete.** Renovate's schedule + `-noble` constraint may delay the first Dockerfile PR by days. The planner must structure VERIFICATION.md so the phase can complete in three ways:
  1. **Organic:** Renovate opens a Dockerfile-bump PR within 7 days of app install; `dockerfile-noble-pin-guard` passes → checkbox checked.
  2. **Forced:** Operator manually triggers a Renovate scan via the dashboard "Click to rebase" or the `renovate.json` `dependencyDashboardApproval` mechanism if any `eclipse-temurin` patch exists within the `-noble` window.
  3. **Synthetic:** Operator manually opens a throwaway PR on a feature branch that bumps `Dockerfile` from `eclipse-temurin:25-jdk-noble` to `eclipse-temurin:25-jre-noble` (or a `25.0.1-*-noble` if such a patch is published) to verify the guard passes; PR is closed without merging. This is the Phase 81 D-13 throwaway-branch precedent applied to Phase 84.

  The planner picks the actual completion path based on Renovate's behavior in the first 48 hours after install.

### Claude's Discretion

- Final Mend Renovate app installation moment — before or after the PR that introduces `renovate.json` merges. Recommendation: install AFTER merge so the first scan sees the curated config; the onboarding PR Mend produces will then be a no-op "config validated, here's what I'll do" PR per Renovate docs.
- Exact `description` text on each `packageRules` entry — match the terse english tone of `config/spotbugs-exclude.xml` rationale comments (Phase 81 D-09 precedent).
- Whether the renovate.json `packageRules` array is grouped by topic (Maven safety / Maven grouping / Docker / Catch-all) or flat. Recommendation: topic-grouped with section comments via the JSON `description` field on a leading "marker" rule per section.
- Whether to add a `timezone` field to `renovate.json` (e.g., `"timezone": "Europe/Berlin"`) so the `before 6am Monday` schedule resolves consistently. Recommendation: yes — locks the schedule semantics across operator location changes.
- Whether to suppress the Renovate dashboard issue's auto-creation by setting `dependencyDashboard: false` — recommendation: NO (D-23 keeps it on).
- Whether to include `rebaseWhen: "behind-base-branch"` explicitly (it's the default; explicit-vs-default is style). Recommendation: explicit, to defuse PITFALLS pitfall #5 ("rebase storm").
- Whether to add `regexManagers` for the `<playwright.version>1.59.0</playwright.version>` property (pom.xml:18) — verify with a dryRun-equivalent first. Standard `maven` manager SHOULD detect this when the property name matches the artifact name pattern; if not, regex manager is the fallback.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & Requirements
- `.planning/ROADMAP.md` §"Phase 84: Renovate Integration" — phase goal, depends-on (Phase 81 + Phase 83), 8 requirement IDs (DEPS-01..DEPS-08), 6 success criteria
- `.planning/REQUIREMENTS.md` §"Renovate (DEPS)" — DEPS-01..DEPS-08 line items
- `.planning/REQUIREMENTS.md` §"Future Considerations" — DEPS-FUTURE-01 (curated dashboard review queue, deferred)
- `.planning/REQUIREMENTS.md` §"Out of Scope" — "Renovate auto-merge for major bumps" (explicit ceiling on automerge)
- `.planning/PROJECT.md` §"Current Milestone: v1.11" — Renovate is Stream 3 of the milestone scope; §"Current State (after v1.10 shipped 2026-05-16)" — pinned coords (Guava 33.4.8-jre, Thymeleaf 3.1.5.RELEASE, Java 25, Lombok 1.18.46, Playwright 1.59.0, Testcontainers 2.0.5)

### Research (v1.11 milestone)
- `.planning/research/SUMMARY.md` §"Stream 3 — Renovate" — Mend GitHub App locked, critical packageRules listed, no dependabot.yml-conflict note
- `.planning/research/SUMMARY.md` §"Reconciled Stack Additions" — Mend hosted GitHub App = "hosted v46.x; self-updating; no pom.xml or workflow file required"
- `.planning/research/PITFALLS.md` pitfall #4 — Renovate proposing Guava `-android` variant; `allowedVersions` regex mitigation
- `.planning/research/PITFALLS.md` pitfall #5 — Renovate proposing `java.version=26`; LTS-only `allowedVersions` mitigation
- `.planning/research/PITFALLS.md` pitfall #6 — Renovate bumping Spring Boot parent independently from pinned Thymeleaf; manual-approval mitigation
- `.planning/research/PITFALLS.md` (Renovate `automergeType: "branch"` pitfall) — locked `"pr"` per D-20
- `.planning/research/ARCHITECTURE.md` §"3. Renovate Integration" — config file location order, recommended `renovate.json` skeleton, automerge defaults
- `.planning/research/FEATURES.md` §"Stream 3: Renovate" — table-stakes, differentiators, anti-features (including Dependabot coexistence pitfall and `rebaseWhen` rationale)
- `.planning/research/STACK.md` — Mend Renovate verified hosted-only; no workflow file or pom.xml plugin

### Prior Phase Context (carry-forward decisions)
- `.planning/phases/80-openrewrite-integration/80-CONTEXT.md` D-08 — OpenRewrite recipe-pack bumps trigger manual `rewrite:dryRun` runs; informs D-16 (OpenRewrite group with `dependencyDashboardApproval: true`)
- `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-CONTEXT.md` D-10 — SpotBugs new-detector-pack risk; informs D-17 (SpotBugs+find-sec-bugs group, manual review for minor/major)
- `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-CONTEXT.md` §"Suppression rationale build-guard" — terse-english `description` style for `packageRules`
- Phase 78 (Docker -noble pin) — `.planning/phases/78-docker-release-image-fix/` — establishes the Playwright/Ubuntu-Noble coupling that informs D-18 (Playwright minor/major manual review) + D-19 (Dockerfile -noble allowedVersions regex)

### Codebase Maps (for planning)
- `.planning/codebase/STACK.md` — current pom.xml plugin inventory; identifies which dependency coordinates Renovate will manage
- `.planning/codebase/STRUCTURE.md` — repository layout; confirms `renovate.json` at root is sibling to `pom.xml`, `Dockerfile`, `mvnw`, `rewrite.yml` (Phase 80), `lombok.config` (Phase 81)
- `.planning/codebase/CONVENTIONS.md` §"Git Workflow" — `gh pr create --assignee jegr78` convention; informs D-22 (assignees array)

### Live Build & Config
- `pom.xml` line 7 — `<artifactId>spring-boot-starter-parent</artifactId>` (DEPS-06 Spring Boot grouping target)
- `pom.xml` line 17 — `<java.version>25</java.version>` (DEPS-05 LTS regex target)
- `pom.xml` line 18 — `<playwright.version>1.59.0</playwright.version>` (D-18 Playwright manual-review target)
- `pom.xml` line 19 — `<lombok.version>1.18.46</lombok.version>` (`lombok.config` invariant per Phase 81)
- `pom.xml` lines 28-31 — Thymeleaf 3.1.5.RELEASE pin in `<dependencyManagement>` (DEPS-04 disable target)
- `pom.xml` lines 35-39 — Testcontainers BOM `import` (DEPS-06 Testcontainers grouping target)
- `pom.xml` lines 158-173 — Google API client trio (DEPS-06 Google API grouping target)
- `pom.xml` lines 177-186 — Guava 33.4.8-jre override + rationale comment (DEPS-03 `-jre` regex target)
- `pom.xml` lines 204-208 — Playwright dependency block (D-18 target)
- `pom.xml` lines 372-413 — SpotBugs plugin block + find-sec-bugs plugin-dep (D-17 group target; Phase 81)
- `pom.xml` lines 422-491 — `<profile id="rewrite">` block + OpenRewrite plugin coordinates (D-16 group target; Phase 80)
- `Dockerfile` lines 2-3, 20-21 — `eclipse-temurin:25-{jdk,jre}-noble` pins with rationale comment (D-19 Docker manager target)
- `.github/dependabot.yml` — three-manager weekly config to be REMOVED (D-03)
- `.github/workflows/ci.yml` lines 78-115 — `dockerfile-noble-pin-guard` job (DEPS-08 / SC#6 authoritative gate)
- `.github/workflows/ci.yml` lines 1-77 — `build-and-test` job (the CI gate that Renovate PRs must pass for patch automerge per D-20)
- `.github/workflows/ci.yml` (whole file) — every `uses: actions/<name>@v<N>` line is in scope of Renovate's `github-actions` manager
- `.github/workflows/deploy-site.yml`, `release.yml`, `mariadb-migration-smoke.yml` — additional `uses:` clauses in scope of `github-actions` manager
- `docker-compose.yml`, `docker-compose.prod.yml` — Dockerfile manager scope; verify no `eclipse-temurin` references exist outside `Dockerfile` (sed/awk audit during planning)

### External (Renovate docs)
- https://docs.renovatebot.com/configuration-options/ — `renovate.json` schema reference
- https://docs.renovatebot.com/modules/manager/maven/ — Maven manager behavior (BOM imports, properties, plugins)
- https://docs.renovatebot.com/modules/manager/dockerfile/ — Dockerfile manager behavior (`FROM` line parsing, `allowedVersions` regex)
- https://docs.renovatebot.com/modules/manager/github-actions/ — `uses:` clause parsing
- https://docs.renovatebot.com/configuration-options/#allowedversions — regex `/.../` form required for non-literal matches (D-09, D-11, D-19)
- https://docs.renovatebot.com/configuration-options/#vulnerabilityalerts — CVE-driven PR bypass mechanism (D-08)
- https://docs.renovatebot.com/key-concepts/automerge/ — `automergeType: "pr"` vs `"branch"` semantics (D-20)
- https://docs.renovatebot.com/configuration-options/#dependencydashboardapproval — click-to-create gate semantics (D-16, D-12)
- https://www.mend.io/free-developer-tools/renovate/ — Mend hosted Renovate app installation page (DEPS-02)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`.github/dependabot.yml`** — three-manager weekly Monday config (`maven`, `github-actions`, `docker`) with `open-pull-requests-limit: 5` each. Will be REPLACED, not extended. The Monday cadence is preserved in Renovate D-06 to keep the operator's mental model unchanged. The `dependencies` label is preserved in Renovate D-21 so existing search filters keep working.
- **`.github/workflows/ci.yml` `dockerfile-noble-pin-guard` job (lines 78-115)** — whitelist-on-`-noble`-suffix structural guard. This is the AUTHORITATIVE gate for SC#6; the `renovate.json` `eclipse-temurin` `allowedVersions` regex (D-19) is belt-and-braces. Cross-platform `grep -E` extract + `grep -F -e` filter idiom mirrors pom.xml PLAT-07 (commit f451ff4).
- **`.github/workflows/ci.yml` `build-and-test` job (lines 1-77)** — the CI gate that Renovate `patch` automerge depends on per D-20. No changes to this job; Renovate's `patch` automerge only fires after this job is green on the Renovate-branch PR.
- **`pom.xml` lines 177-186 Guava override block** — already documents the `-jre` rationale (Java 25 VarHandle path; suppresses Unsafe warning). The D-09 `packageRules` entry's `description` field cross-references this comment block.
- **`pom.xml` lines 28-31 Thymeleaf 3.1.5.RELEASE pin** — Phase 71 / v1.10 milestone artifact (CVE-2026-40478 mitigation). The D-10 `enabled: false` rule's `description` field documents the same rationale.
- **`pom.xml` line 17 `<java.version>25</java.version>` property** — Renovate's `maven` manager handles this when the parent BOM exposes `java.version`. D-11 LTS regex pins to 11/17/21/25/29 across the property update path.
- **`Dockerfile` lines 2-3, 20-21 `eclipse-temurin:25-{jdk,jre}-noble` pins with leading rationale comment** — Phase 78 artifact. D-19 `allowedVersions` regex encodes the same `-noble`-suffix invariant as the CI guard.

### Established Patterns
- **Two-bot avoidance** — same shape as the JaCoCo+SpotBugs `argLine` interaction (Phase 81 Pitfall #7): two tools touching the same field create silent conflicts. Resolution is the same: pick ONE, remove the other.
- **`description` field as in-config rationale** — mirrors Phase 81 D-09 (`spotbugs-exclude.xml` XML rationale comments) and Phase 80 D-11 (README rationale block). Every `packageRules` entry MUST carry a one-line `description` with file:line cross-reference where applicable; `description` is JSON-native and renders in the Renovate dashboard so operators see WHY each rule exists.
- **Same-commit invariant pairs** — pom.xml + comment, lombok.config + spotbugs-exclude.xml, dockerfile + ci-guard, and now renovate.json + dependabot.yml-removal. Atomic commits prevent intermediate broken states.
- **Belt-and-braces gates** — Renovate `allowedVersions` regex + `dockerfile-noble-pin-guard` CI job (D-19). Renovate is the prevention layer; the CI guard is the detection layer. Same pattern as Phase 81's `EI_EXPOSE_REP` D-08 (Lombok `@SuppressFBWarnings` emission + package-level filter).
- **English-only file content + Conventional Commits** — applies to `renovate.json` `description` fields and commit messages per CLAUDE.md.
- **Solo-maintainer concurrency tolerance** — `prConcurrentLimit: 5` matches the existing Dependabot ceiling per manager; `prHourlyLimit: 2` is the firehose throttle.

### Integration Points
- **`renovate.json`** — net-new file at repository root, sibling to `pom.xml`, `Dockerfile`, `mvnw`, `rewrite.yml` (Phase 80), `lombok.config` (Phase 81), `docker-compose.yml`. Verified does NOT yet exist (`ls renovate.json` returns NO).
- **`.github/dependabot.yml`** — DELETE in the same commit that creates `renovate.json`. This is the only file in the repo that this phase removes.
- **Mend Renovate GitHub App** — installed via https://github.com/apps/renovate/installations/new — operator-driven, not a file change. The plan must document the install step explicitly (URL + account selection) so it's reproducible.
- **CI gate (`build-and-test` job)** — Renovate's `automergeType: "pr"` patch automerge depends on this job staying green. No changes to the job itself; the integration is one-way (Renovate observes the job, doesn't modify it).
- **`dockerfile-noble-pin-guard` CI job** — verification target for SC#6. Plan must structure the phase verification so this job is exercised by either a real or synthetic Renovate Dockerfile-bump PR (D-24).

### What This Phase Does NOT Touch
- `src/main/**`, `src/test/**` — zero source-code changes.
- `pom.xml` — zero pom.xml edits in this phase (Renovate consumes pom.xml; Phase 84 configures Renovate, not pom.xml).
- `Dockerfile`, `docker-compose*.yml` — zero edits. Renovate proposes Dockerfile edits via PR; Phase 84 verifies the proposal is constrained correctly.
- `.github/workflows/*.yml` — zero edits. `ci.yml` is the authoritative gate for both `patch` automerge (build-and-test) and SC#6 (dockerfile-noble-pin-guard); both are read-only inputs to this phase.
- `lombok.config`, `config/spotbugs-exclude.xml`, `rewrite.yml` — Phase 80/81 artifacts; Renovate's `maven` manager picks up plugin coords in `pom.xml` `<plugins>` blocks but does NOT modify these adjunct config files.
- `CLAUDE.md` — no documentation update required (Renovate is invisible-by-default once configured; the existing `## Git Workflow` and `## Subagent Rules` sections cover the PR-review workflow that already applies to dependency PRs).
- Existing JaCoCo `<excludes>`, SpotBugs `<excludeFilterFile>`, OpenRewrite `<activeRecipes>` — Renovate proposes version bumps for the plugins themselves (per D-16, D-17) but never modifies these configuration payloads.

</code_context>

<specifics>
## Specific Ideas

- **Branch:** Plan-phase will branch from `origin/master` per CLAUDE.md `## Git Workflow`; planner picks the name (e.g., `feature/renovate-integration`). Phase-PR target is the milestone branch `gsd/v1.11-tooling-and-cleanup` per PROJECT.md tail.
- **Commit message format:** Conventional Commits per CLAUDE.md. The plumbing commit message: `feat(84): wire Mend Renovate with CTC safety rules (DEPS-01..DEPS-07)` with body listing each DEPS-XX → packageRule mapping for git-blame archaeology.
- **`renovate.json` skeleton order** (top-to-bottom, mirrors Renovate docs ordering): `$schema` → `extends` → `enabledManagers` → `timezone` → `schedule` → `prConcurrentLimit` / `prHourlyLimit` / `branchConcurrentLimit` → `labels` → `assignees` → `vulnerabilityAlerts` → `rebaseWhen` → `dependencyDashboard` → `packageRules` array. Each `packageRules` entry carries `description` first, then `matchers`, then `enabled` / `automerge` / etc. — readable rule cards.
- **DEPS-08 / SC#6 verification narrative:** Plan must lay out one of D-24's three completion paths AT planning time, not at execute time, so the executor knows whether to wait for an organic PR (path 1) or open a synthetic PR (path 3). Default recommendation: path 3 (synthetic throwaway PR) is FASTEST and identical in outcome to path 1; mirrors Phase 81 D-13 throwaway-branch precedent.
- **Coverage / test obligation:** Zero source changes → JaCoCo coverage unchanged (87.80% v1.10 baseline). E2E test obligation per CLAUDE.md `feedback_e2e_verification`: final phase-end verification still uses `./mvnw verify -Pe2e` even though no Java code changes; confirms `renovate.json` does not somehow break the build (it shouldn't — Renovate config is consumed only by Renovate, not Maven).
- **Operator-checklist file:** The plan may produce `docs/operations/renovate.md` or extend an existing operations doc with the Mend install URL + dashboard URL + "first 48 hours" smoke-check items. Discretion — only if the planner determines that the install step needs a documented runbook beyond the CONTEXT.md trail.
- **Test-runtime efficiency** per `feedback_test_call_optimization`: This phase has ZERO Java code or test changes, so `./mvnw verify` only needs to run ONCE at phase-end. No per-commit `verify` cycles required.

</specifics>

<deferred>
## Deferred Ideas

- **Curated Dependency Dashboard review queue** — DEPS-FUTURE-01 explicitly defers this until the team grows beyond a single maintainer. The basic dashboard issue stays default-enabled (D-23).
- **Renovate auto-merge for `minor` updates** — REQUIREMENTS.md `## Out of Scope` explicitly carves this out for v1.11. Reopen only if a future milestone documents the operator confidence built from observing patch-automerge for ≥3 months.
- **Renovate auto-merge for `major` updates** — REQUIREMENTS.md `## Out of Scope` explicitly excludes this. Major bumps are intentional OpenRewrite migration triggers, not unattended PRs.
- **Self-hosted Renovate via GitHub Actions runner** — research SUMMARY.md Stream 3 rejected this for cost reasons (Mend hosted is free for public repos and self-updating). Reopen only if Mend's hosted service is discontinued or rate-limits affect this repo.
- **Regex managers for `<playwright.version>` / similar version properties** — D-discretion item; only add if the default `maven` manager fails to detect the property→artifact link. Otherwise unnecessary maintenance.
- **`docs/operations/renovate.md` runbook** — discretion item per the specifics section; defer to planner judgment.
- **Removing the `dependencies` label transition** — once the Renovate migration is bedded in and no Dependabot PRs remain in search history, the `renovate` secondary label (D-21) can be dropped to simplify search. Not now; not a phase deliverable.

</deferred>

---

*Phase: 84-renovate-integration*
*Context gathered: 2026-05-17*
