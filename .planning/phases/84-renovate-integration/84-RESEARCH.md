# Phase 84: Renovate Integration - Research

**Researched:** 2026-05-17
**Domain:** DevOps tooling — automated dependency updates via Mend Renovate hosted GitHub App
**Confidence:** HIGH (Renovate schema, Mend hosted, manager behavior) / MEDIUM (java.version native detection caveat, eclipse-temurin tag patterns) / LOW (Mend automerge merge-method)

---

## Summary

Phase 84 is a near-zero-code DevOps phase: one new file (`renovate.json` at repo root), one deletion (`.github/dependabot.yml`), one operator action (install Mend Renovate GitHub App at https://github.com/apps/renovate against `jegr78/ctc-manager`). All eight DEPS-XX requirements are encoded in `renovate.json` via a single `packageRules` array. The CTC-specific safety rules (Guava `-jre` only, Thymeleaf pin, Java LTS-only, eclipse-temurin `-noble` suffix, four grouping rules, two manual-review rules) must be in place BEFORE the GitHub App scans the repo for the first time — D-02 locks pre-commit ordering for exactly this reason.

Three findings shift the implementation away from CONTEXT.md decisions as written and need planner attention:

1. **`<java.version>` is NOT detected by Renovate's native Maven manager.** The Maven manager does not support the `java-version` datasource [VERIFIED: docs.renovatebot.com/java/]. CONTEXT.md D-11 as written (`matchPackageNames: ["java"]` + `matchManagers: ["maven"]`) would be a no-op — Renovate never produces a `<java.version>` PR through Maven manager, so there is no PR to constrain. Either accept the no-op (rely on the `workarounds:javaLTSVersions` preset inherited via `config:recommended`) OR add a `customManagers` block with a regex against `<java.version>` + the `java-version` datasource. **Recommendation:** rely on the `workarounds:javaLTSVersions` preset (already in `config:recommended`); do NOT hand-roll a customManager (community guidance: maintainers explicitly do not support customManager debugging).
2. **`workarounds:javaLTSVersions` preset is already active under `config:recommended`** with regex `/^(?:8|11|17|21|25)(?:\\.|-|$)/` [VERIFIED: docs.renovatebot.com/presets-workarounds/]. CONTEXT.md D-11 wants `/^(?:11|17|21|25|29)(?:\\.|$)/` which differs in two ways: (a) drops Java 8 (correct for CTC — no Java 8 path exists) (b) adds Java 29 (a future LTS not yet released). The simplest configuration is to inherit the preset as-is; Java 8 will never appear because Renovate only proposes newer versions than the current pin (25). Java 29 is irrelevant until 2027. **Recommendation:** accept the preset default, drop D-11's explicit override — fewer custom rules to maintain. If D-11 must be enforced verbatim, the cleanest form is `ignorePresets: ["workarounds:javaLTSVersions"]` + a custom packageRule with `matchDatasources: ["java-version"]`.
3. **`matchPackagePatterns` is deprecated.** Renovate is auto-migrating it to `matchPackageNames` with regex syntax (leading slash) [VERIFIED: github.com/renovatebot/renovate discussions]. CONTEXT.md D-12 through D-17 all use `matchPackagePatterns`. They will work (auto-migrated), but writing the new syntax up front avoids the migration noise. New form: `"matchPackageNames": ["/^org\\.springframework\\.boot:/"]`.

Two additional precision issues:

- **`eclipse-temurin` tag scheme is more complex than D-19's regex captures.** Actual published tags include `25.0.1_8-jdk-noble`, `25.0.2_10-jdk-noble`, and `25.0.0_36-jdk-noble` (underscore-separated build numbers) [VERIFIED: hub.docker.com layers]. D-19's regex `/^(?:25)(?:\\.[0-9.]+)?-(?:jdk|jre)-noble$/` does NOT match the `_8` build suffix and would BLOCK all real published patch tags — defeating the purpose. Corrected regex: `/^25(?:\\.[0-9._]+)?-(?:jdk|jre)-noble$/`. The CI `dockerfile-noble-pin-guard` job is the authoritative gate either way.
- **`vulnerabilityAlerts.enabled: true` does NOT cleanly override package-rule `enabled: false`.** Documented Renovate behavior: vulnerability alerts run through a restricted extraction path and `osvVulnerabilityAlerts` may produce PRs even on disabled packages [VERIFIED: github.com/renovatebot/renovate discussions #40800, #42634]. Practical implication: D-10 + D-08 will USUALLY behave as intended (a Thymeleaf CVE WILL still produce a PR), but the override path is officially "behavior varies." Document as a known limitation.

**Primary recommendation:** Implement CONTEXT.md decisions D-01..D-24 with the four corrections listed above (java.version no-op-or-customManager, eclipse-temurin regex, matchPackagePatterns→matchPackageNames, vulnerabilityAlerts limitation). The phase has zero Java code surface; verification is JSON-schema validity, dependabot.yml absence, Mend onboarding PR creation, and the `dockerfile-noble-pin-guard` CI job remaining green when the first Renovate Dockerfile PR opens.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Dependency-update PR creation | External SaaS (Mend Renovate hosted) | — | Mend GitHub App runs Renovate on Mend infrastructure; reads repo via app permissions; opens PRs against `master`. No CI runner minutes consumed. |
| Renovate configuration | Repo-root config file | — | `renovate.json` at `/renovate.json` is the canonical, IDE-validatable location. Mend reads it on each scan. |
| Dependabot deprecation | Repo-root `.github/` config | — | Deleting `.github/dependabot.yml` is the only mechanism — GitHub has no UI toggle for Dependabot version updates separate from the file. |
| Patch-automerge gate | GitHub Actions CI (`build-and-test`) | — | Renovate's `automergeType: "pr"` waits for required status checks; the existing `build-and-test` job is the gate. Renovate does not touch CI. |
| Dockerfile -noble pin enforcement | GitHub Actions CI (`dockerfile-noble-pin-guard`) | renovate.json `packageRules` (D-19, defense-in-depth) | The CI guard at `ci.yml:78-115` is authoritative; renovate.json regex is belt-and-braces so a misconfigured PR is never even proposed. |
| Branch protection / merge enforcement | GitHub repository settings (branch protection rules on `master`) | — | Renovate respects branch protection — required reviews/checks block automerge until satisfied [VERIFIED: docs.renovatebot.com/key-concepts/automerge/]. |
| Dependency Dashboard | GitHub Issue (auto-created by Renovate) | — | Default-enabled under `config:recommended`. Single tracking issue lists every queued update; no separate UI. |
| Vulnerability alert intake | GitHub Dependency Graph + GHSA database | osvVulnerabilityAlerts (Renovate-native) | Renovate uses GitHub's vulnerability data for Maven; CVE-driven PRs bypass schedule and labels. |

## Standard Stack

### Core

| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| Mend Renovate GitHub App | hosted (Renovate v40+, self-updating) | Run Renovate on Mend infrastructure; open dependency PRs | Free for public repos; zero runner-minute cost; auto-updating; no workflow file needed. [VERIFIED: mend.io/free-developer-tools/renovate/] |
| `renovate.json` | n/a (config file) | Encode CTC-specific safety rules | Canonical config location at repo root; IDE-schema-validatable; loaded on every Renovate scan. |
| `config:recommended` preset | Latest (auto-applied) | Renovate maintainer-curated defaults | Replaces deprecated `config:base`. Includes sensible defaults: groupings, `workarounds:javaLTSVersions`, `:dependencyDashboard`. [VERIFIED: docs.renovatebot.com/presets-config/] |
| `$schema` reference | `https://docs.renovatebot.com/renovate-schema.json` | Enable JSON-schema IDE validation | Standard practice; matches Renovate docs. |

### Supporting (Renovate-internal — no install required)

| Component | Purpose | When Used |
|-----------|---------|-----------|
| Maven manager | Detects `pom.xml` dependencies, parent BOM, `<dependencyManagement>` imports, plugin coords | Auto-runs when `enabledManagers` includes `"maven"`. Handles `<version>` literals + `${prop}` references where prop is in `<properties>`. Does NOT handle `<java.version>` (no `java-version` datasource integration). [VERIFIED: docs.renovatebot.com/modules/manager/maven/] |
| Dockerfile manager | Parses `FROM <image>:<tag>` lines | Auto-runs when `enabledManagers` includes `"dockerfile"`. Uses `docker` versioning by default — treats `-jdk-noble`, `-alpine` suffixes as "compatibility" qualifiers. [VERIFIED: docs.renovatebot.com/modules/manager/dockerfile/] |
| github-actions manager | Parses `uses: org/repo@ref` clauses in `.github/workflows/*.yml` | Auto-runs when `enabledManagers` includes `"github-actions"`. Handles `@v6`, `@4.1.2`, SHA pins (`@abc123...`). |
| `workarounds:javaLTSVersions` preset | Restricts `java-version` datasource to `8\|11\|17\|21\|25` | Active when `config:recommended` is extended (default). Acts at datasource level — applies to any manager that uses `java-version` datasource. [VERIFIED: docs.renovatebot.com/presets-workarounds/] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Mend Renovate GitHub App | Self-hosted via `renovatebot/github-action` workflow | Costs runner minutes; requires version-pinning of Renovate itself; redundant given Mend's free hosted offering for public repos. Rejected per research SUMMARY.md Stream 3. |
| Renovate | Dependabot (existing) | Dependabot lacks: BOM-aware grouping, regex `allowedVersions`, `packageRules` granularity, per-update-type rules, dependency dashboard. CONTEXT.md D-03 locks removal. |
| `renovate.json` at repo root | `.github/renovate.json` | Functionally identical (Renovate searches both); root location is more discoverable and matches DEPS-01 wording. |
| Native Maven manager for `<java.version>` | `customManagers` (regex manager) | Native: relies on inherited `workarounds:javaLTSVersions` preset, no custom config to maintain, but no PR is produced. Custom: PR opens for Java version bumps but customManager debugging is officially community-supported only. Recommend native + preset. |

**Installation:**

```bash
# Phase 84 has NO npm install / pip install / cargo install step.
# 1. Commit renovate.json + delete .github/dependabot.yml in one commit
# 2. Open PR; merge to master (via CI green + assignee jegr78 per CLAUDE.md Git Workflow)
# 3. Repo owner installs the Mend Renovate GitHub App at:
#    https://github.com/apps/renovate/installations/new
#    Select 'jegr78/ctc-manager' (or 'All repositories' for the account)
# 4. Mend creates an onboarding PR within minutes (config validation, no changes since renovate.json already exists)
# 5. Merge onboarding PR (closes DEPS-02)
```

**Version verification:** No npm/pip/cargo packages installed in this phase. The Mend hosted Renovate version is opaque (auto-updating; current Mend v40+ per docs); we never pin it.

## Package Legitimacy Audit

> **Not applicable** — Phase 84 installs zero packages. The only external dependency is the Mend Renovate GitHub App, which is an installed-via-marketplace SaaS, not an npm/pip/cargo package. No slopcheck step needed.

| Package | Registry | Disposition |
|---------|----------|-------------|
| (none) | n/a | n/a |

## Architecture Patterns

### System Architecture Diagram

```
                                                                ┌──────────────────────┐
                                                                │   Mend Renovate      │
   Developer push to master ──────────────┐                     │   (hosted SaaS)      │
                                          │                     │                      │
                                          ▼                     │  Reads:              │
   ┌─────────────────────────┐    ┌──────────────────┐         │  - renovate.json     │
   │  GitHub repo            │    │  GitHub Webhook  │────────▶│  - pom.xml           │
   │  jegr78/ctc-manager     │    │  (Renovate App)  │         │  - Dockerfile        │
   │                         │    └──────────────────┘         │  - .github/workflows │
   │  ▸ renovate.json (NEW)  │                                  │                      │
   │  ▸ pom.xml              │                                  │  Detects new versions│
   │  ▸ Dockerfile           │                                  │  per packageRules    │
   │  ▸ .github/workflows/*  │                                  │                      │
   │  ▸ (no dependabot.yml)  │                                  │  Schedule:           │
   │                         │                                  │  before 6am Monday   │
   └────────────▲────────────┘                                  │                      │
                │                                                │  Vulnerability:      │
                │                                                │  bypass schedule     │
                │                                                └──────────┬───────────┘
                │                                                           │
                │  PR created by renovate[bot]                              │
                │  (branched from master)                                   │
                └───────────────────────────────────────────────────────────┘
                            │
                            ▼
   ┌─────────────────────────────────────────────────────────────────────┐
   │  GitHub Actions CI (existing, unchanged)                            │
   │                                                                     │
   │  ▸ build-and-test job (verify + e2e + JaCoCo)                       │
   │  ▸ dockerfile-noble-pin-guard job (whitelist -noble suffix)         │
   │  ▸ docker-build job                                                 │
   └─────────────────────────────────────────────────────────────────────┘
                            │
                            ▼ all required checks green?
                ┌─────────────────────────┐
                │  Branch Protection      │
                │  on master              │
                └─────┬───────────────┬───┘
                      │               │
                yes   ▼               ▼ no (minor/major)
        ┌──────────────────┐    ┌──────────────────────┐
        │ patch update?    │    │  Manual review       │
        │ → automerge="pr" │    │  required (operator) │
        │ (Renovate clicks │    └──────────────────────┘
        │  merge button)   │
        └──────────────────┘
                ▲
                │ (also: vulnerability fix bypasses schedule
                │  but still requires CI green + branch protection)
```

### Recommended Project Structure

```
ctc-manager/                            # repo root
├── renovate.json                       # NEW — Phase 84 deliverable
├── pom.xml                             # READ-ONLY by Renovate
├── Dockerfile                          # READ-ONLY by Renovate
├── docker-compose.yml                  # No FROM lines — verified no eclipse-temurin
├── docker-compose.prod.yml             # No FROM lines — verified no eclipse-temurin
├── lombok.config                       # Phase 81 artifact, unrelated to Phase 84
├── rewrite.yml                         # Phase 80 artifact, unrelated to Phase 84
├── config/spotbugs-exclude.xml         # Phase 81 artifact
├── .github/
│   ├── dependabot.yml                  # DELETED in Phase 84
│   └── workflows/
│       ├── ci.yml                      # READ-ONLY by Renovate; CI gate for patch automerge
│       ├── release.yml                 # In github-actions manager scope
│       ├── deploy-site.yml             # In github-actions manager scope
│       └── mariadb-migration-smoke.yml # In github-actions manager scope
```

### Pattern 1: Same-commit replacement of conflicting bots

**What:** Add `renovate.json` AND delete `.github/dependabot.yml` in the same atomic commit.
**When to use:** Whenever switching dependency-update bots. Avoids the window where both bots could produce duplicate PRs for the same dependency.
**Source:** Locked by CONTEXT.md D-03; reinforced by research PITFALLS pitfall #5 (rebase storm) and Renovate-Dependabot coexistence guidance.

**Example commit:**
```bash
# Same commit, two files touched
git add renovate.json
git rm .github/dependabot.yml
git commit -m "feat(84): replace Dependabot with Mend Renovate (DEPS-01..DEPS-07)

- Add root renovate.json with CTC-specific packageRules
- DEPS-01: enabledManagers [maven, github-actions, dockerfile]
- DEPS-03: Guava -jre-only allowedVersions regex
- DEPS-04: Thymeleaf 3.1.5.RELEASE pin enforced via enabled:false
- DEPS-05: Java LTS via inherited workarounds:javaLTSVersions preset
- DEPS-06: Spring Boot / Spring Security / Google API / Testcontainers groupings
- DEPS-07: patch automerge with automergeType:pr
- Remove .github/dependabot.yml — same managers (maven/gha/docker) now via Renovate"
```

### Pattern 2: Defense-in-depth (renovate.json regex + CI guard)

**What:** Encode the same `-noble` invariant in BOTH `renovate.json` `allowedVersions` (preventive — Renovate never proposes a bad tag) AND `dockerfile-noble-pin-guard` CI job (detective — fails the build if a bad tag appears).
**When to use:** Any safety-critical pin where the cost of an undetected bad bump is high (here: Playwright/Ubuntu Plucky breakage per release run 25609204039).
**Source:** Phase 81 D-08 (`EI_EXPOSE_REP` + Lombok suppression layered with package-filter); Phase 78 D-noble-pin (Dockerfile + CI guard).

### Pattern 3: Inline `description` field as in-config rationale

**What:** Every `packageRules` entry carries a `description` field that explains WHY in one terse English sentence with file:line cross-references where applicable.
**When to use:** Always. `description` is JSON-native and renders in the Renovate dashboard so operators see the rationale next to the rule.
**Example:**
```json
{
  "matchPackageNames": ["org.thymeleaf:thymeleaf"],
  "enabled": false,
  "description": "Pin 3.1.5.RELEASE — CVE-2026-40478 mitigation (pom.xml:28-31). Vulnerability alerts may still produce PRs."
}
```

### Anti-Patterns to Avoid

- **`automergeType: "branch"`** — Skips PR creation; commits land directly on `master`. No PR object for audit, no CI status visible in PR list, no review trail. Locked OUT by CONTEXT.md D-20 (`"pr"`).
- **`rebaseWhen: "always"`** — Forces Renovate to rebase every PR on every base-branch commit, generating force-pushes and CI re-runs. Documented as the "rebase storm" pitfall. Use `"behind-base-branch"` (the default).
- **Top-level `automerge: true`** — Applies automerge to ALL update types (patch + minor + major), violating REQUIREMENTS.md "Out of Scope" line. Always scope automerge inside a `matchUpdateTypes: ["patch"]` packageRule.
- **Grouping all dependencies together** (`groupName: "all"` at top level) — Single PR for all updates kills the per-bump CI signal. Group by ecosystem (Spring Boot / Spring Security / Testcontainers etc.) not by entire repo.
- **Hand-rolled customManager for `<java.version>`** — Renovate maintainers explicitly do not support customManager debugging (github.com/renovatebot/renovate#35019). Rely on inherited `workarounds:javaLTSVersions` preset.
- **Removing `.github/dependabot.yml` BEFORE introducing `renovate.json`** — Leaves the repo with zero dependency automation between commits. Always same-commit (CONTEXT.md D-03).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Java LTS version enforcement | Custom regex packageRule constraining `matchPackageNames: ["java"]` | Inherited `workarounds:javaLTSVersions` from `config:recommended` | The Maven manager doesn't even detect `<java.version>` natively — the preset already covers any datasource that does. Hand-rolled rule is a no-op. |
| `<java.version>` PR generation | Custom regex against `<java.version>25</java.version>` | Accept the no-op (the inherited LTS preset gates any future support); or use the documented annotation pattern `<!-- renovate: datasource=java-version depName=java -->` | The Renovate Maven manager does not integrate the `java-version` datasource. Hand-rolling produces fragile customManager configs that maintainers won't debug. |
| Spring Boot grouping | Hand-roll `matchPackagePatterns: ["^org\\.springframework\\.boot:"]` group | Inherit `group:springBoot` from `config:recommended` AND add CTC-specific `dependencyDashboardApproval` overlay for major bumps | `config:recommended` already groups Spring Boot; CTC just adds the major-bump gate. |
| Onboarding PR template | Pre-generate the "first PR Renovate will create" | Let Mend produce it after install | If `renovate.json` already exists when the app is installed, Mend's onboarding PR is a validation-only PR ("config validated, here is what I will do"). No edits required. |
| Dependency Dashboard issue | Open a tracking issue manually | Renovate auto-creates one (default-enabled under `config:recommended`) | The dashboard is a single GitHub Issue Renovate maintains automatically — never edit it directly. |
| Self-hosted Renovate cron | Install `renovatebot/github-action` in a workflow | Mend hosted GitHub App | Hosted is free, auto-updates, zero runner-minute cost. Self-hosted re-introduces version-pinning concerns. |

**Key insight:** Renovate ships sensible defaults; resist the urge to over-configure. Every rule added is a rule to maintain. The seven `config:recommended` defaults that matter for CTC (Spring Boot grouping, semver-major separation, dependency dashboard, vulnerability alerts, schedule-respecting behavior, rebase-when-behind, LTS Java) are all already present — CTC's additions are strictly the safety-and-grouping deltas.

## Runtime State Inventory

> Not a rename/refactor phase — Phase 84 introduces a new config file and removes an obsolete one. No string-renames, no migrations.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — Renovate is stateless config (Mend stores its own scan state in their SaaS) | None |
| Live service config | The Mend Renovate GitHub App installation IS the live service config — outside git, lives in Mend's tenant. Re-installable. | Operator installs at https://github.com/apps/renovate/installations/new |
| OS-registered state | None — no local tools registered | None |
| Secrets/env vars | None — Mend app does not require any repo secrets. CI's `secrets.GITHUB_TOKEN` and `secrets.RELEASE_TOKEN` are unaffected. | None |
| Build artifacts | None — `renovate.json` is not consumed by Maven; `./mvnw verify` ignores it. | None |

## Common Pitfalls

### Pitfall 1: `<java.version>` PR never appears (D-11 no-op)

**What goes wrong:** Operator writes the D-11 packageRule expecting it to constrain future Java version bumps. Months later, Java 26 GA's and no Renovate PR appears. Operator assumes the rule worked. Reality: Renovate's Maven manager never detected `<java.version>` in the first place — no PR was ever going to be created with or without the rule. [VERIFIED: docs.renovatebot.com/modules/manager/maven/ — Maven manager does not integrate java-version datasource]
**Why it happens:** D-11 conflates the `java-version` datasource (which the LTS preset gates) with the Maven manager (which doesn't use it for `<java.version>` properties).
**How to avoid:**
- Either accept the no-op (preset gates any future support; current behavior is "no PR")
- Or add `customManagers` with explicit regex + `<!-- renovate: -->` annotation in pom.xml above the `<java.version>` line
- Document the chosen path in `renovate.json` `description` so future-you doesn't re-investigate
**Warning signs:** No `<java.version>` PR ever appears despite new Java releases. The Mend dependency dashboard does not list `java.version` as a tracked dependency.

### Pitfall 2: eclipse-temurin patch tag rejected by D-19 regex

**What goes wrong:** Renovate proposes bumping `eclipse-temurin:25-jdk-noble` to `eclipse-temurin:25.0.1_8-jdk-noble` (a real published tag). D-19's regex `/^(?:25)(?:\\.[0-9.]+)?-(?:jdk|jre)-noble$/` does NOT match `_8` (underscore), so Renovate's `allowedVersions` filter drops the proposal. The repo stays on the floating `25-jdk-noble` tag indefinitely — that's actually fine, but the regex was meant to be permissive, not blocking.
**Why it happens:** eclipse-temurin's tag scheme is `<major>.<minor>.<patch>_<build>-(jdk|jre)-<ubuntu-codename>` not pure SemVer.
**How to avoid:** Use regex `/^25(?:\\.[0-9._]+)?-(?:jdk|jre)-noble$/` — `[0-9._]+` accepts dots AND underscores between the major and the suffix.
**Warning signs:** Dependency Dashboard shows eclipse-temurin as "pending" indefinitely; no PR opens despite new Temurin releases. `dockerfile-noble-pin-guard` continues to pass because no bump arrives.

### Pitfall 3: `vulnerabilityAlerts` does not always override `enabled: false`

**What goes wrong:** Operator assumes the D-10 (Thymeleaf `enabled: false`) rule will be bypassed when a Thymeleaf CVE drops, producing a security PR. Renovate's vulnerability flow runs through a restricted extraction path (`enabledManagers`, `managerFilePatterns`, `enabled: false`) that may not always trigger. [VERIFIED: github.com/renovatebot/renovate discussions #40800, #42634]
**Why it happens:** Renovate's `vulnerabilityAlerts` and `osvVulnerabilityAlerts` flows have known interop bugs with package-rule overrides; the maintainers acknowledge the issue without a firm fix timeline.
**How to avoid:**
- Do NOT rely on Renovate alone for Thymeleaf CVE notification — GitHub's native Dependabot security alerts (separate from Dependabot version updates) remain ON regardless of `.github/dependabot.yml` removal. Verify "Dependabot alerts" is enabled in repo Security settings.
- Document this gap in `renovate.json` D-10 `description`: "Vulnerability alerts may still produce PRs — verify behavior on first CVE."
**Warning signs:** A Thymeleaf CVE is published and 7 days pass with no Renovate PR; check GitHub Security tab for the Dependabot alert (which is independent and reliable).

### Pitfall 4: Dual-bot window during phase rollout

**What goes wrong:** `renovate.json` is committed but `.github/dependabot.yml` is left in place "for a week" to verify Renovate works. Both bots scan; both open PRs for the same artifacts; PR queue duplicates.
**Why it happens:** Operator caution overrides D-03's same-commit invariant.
**How to avoid:** Same-commit removal locked by D-03. If timidity wins, run a one-day test on a feature branch (Renovate's app install can be scoped to a single repo and uninstalled cleanly) BEFORE deleting dependabot.yml; do not run both in production simultaneously.
**Warning signs:** Two PRs open for the same artifact within minutes of each other; one labelled `dependencies` (Dependabot), one labelled `dependencies, renovate` (Renovate).

### Pitfall 5: `matchPackagePatterns` auto-migration noise

**What goes wrong:** `renovate.json` uses `matchPackagePatterns` (CONTEXT.md D-12..D-17). Renovate auto-migrates to `matchPackageNames` and opens a "config migration" PR to update the file. Operator sees an unexpected PR before any dependency PRs and panics.
**Why it happens:** `matchPackagePatterns` is deprecated [VERIFIED: github.com/renovatebot/renovate discussions]. Renovate offers auto-migration as a feature.
**How to avoid:** Write `matchPackageNames` with leading-slash regex syntax from the start: `"matchPackageNames": ["/^org\\.springframework\\.boot:/"]`. Avoids the migration PR entirely.
**Warning signs:** A `chore(config): migrate renovate config` PR opens before any dependency PRs.

### Pitfall 6: Branch protection blocks automerge silently

**What goes wrong:** Renovate's `automergeType: "pr"` requires CI green AND branch protection rules satisfied. If `master` has "required reviews >= 1" enabled, Renovate cannot automerge alone — the PR sits with green CI and no reviewer.
**Why it happens:** Branch protection takes precedence over automerge [VERIFIED: docs.renovatebot.com/key-concepts/automerge/].
**How to avoid:**
- Check current branch protection: `gh api repos/jegr78/ctc-manager/branches/master/protection` — confirm whether "required reviews" is set. If yes, either (a) accept that automerge is effectively manual-review-then-auto-click, or (b) install the `renovate-approve` helper bot (also free) to satisfy the review requirement automatically.
- Document the chosen path in CONTEXT.md before execute-phase.
**Warning signs:** Patch-update PRs sit in green-CI state for hours/days without merging; Renovate logs `automerge blocked by branch protection`.

### Pitfall 7: First scan timing surprises

**What goes wrong:** Operator merges the renovate.json PR, installs the Mend app, and expects the dependency dashboard issue and a flood of update PRs within minutes. Reality: Mend's first scan can take up to an hour for a new install; the first PRs respect the configured schedule (`before 6am on Monday`), so installing on a Friday afternoon could mean NO PRs until next Monday 6 UTC.
**Why it happens:** Mend's scheduler honors `schedule` from PR #1; there's no "always run on install" override.
**How to avoid:**
- Install Mend on a Sunday evening UTC if you want to see results within hours
- OR temporarily comment out `"schedule"` for the first scan, then add it back in a follow-up commit
- OR use the dashboard "Click to rebase" / "Click to create" affordance to trigger on-demand
- Set operator expectations: Phase 84 verification window is up to 72 hours, not "within minutes"
**Warning signs:** Dashboard issue exists but lists zero pending updates 12+ hours after install.

### Pitfall 8: Onboarding-PR confusion when `renovate.json` already exists

**What goes wrong:** Operator expects no onboarding PR (since they pre-committed `renovate.json`). Mend still creates an "Onboarding PR" that says "no changes needed — your renovate.json is valid." Operator wonders if something is wrong.
**Why it happens:** Mend always produces an onboarding PR on first install; when config is pre-existing it's a validation-only PR.
**How to avoid:** Document this in the operator runbook. The onboarding PR satisfies DEPS-02 ("at least one onboarding PR is produced"); merge it (or close without merging) — either action completes the onboarding flow.
**Warning signs:** A PR titled "Configure Renovate" / "Onboarding" exists with zero file changes; this is expected.

## Code Examples

### Recommended `renovate.json` (synthesised from CONTEXT.md + verified Renovate syntax)

```json
{
  "$schema": "https://docs.renovatebot.com/renovate-schema.json",
  "extends": ["config:recommended"],
  "enabledManagers": ["maven", "github-actions", "dockerfile"],
  "timezone": "Europe/Berlin",
  "schedule": ["before 6am on Monday"],
  "prConcurrentLimit": 5,
  "prHourlyLimit": 2,
  "branchConcurrentLimit": 10,
  "labels": ["dependencies", "renovate"],
  "assignees": ["jegr78"],
  "rebaseWhen": "behind-base-branch",
  "dependencyDashboard": true,
  "vulnerabilityAlerts": {
    "enabled": true,
    "labels": ["security", "dependencies"]
  },
  "packageRules": [
    {
      "description": "Guava -jre classifier only — Java 25 VarHandle path in AbstractFuture (pom.xml:177-186)",
      "matchPackageNames": ["com.google.guava:guava"],
      "allowedVersions": "/^[0-9.]+-jre$/"
    },
    {
      "description": "Thymeleaf pinned to 3.1.5.RELEASE — CVE-2026-40478 mitigation (pom.xml:28-31). Vulnerability alerts may still produce PRs.",
      "matchPackageNames": ["org.thymeleaf:thymeleaf"],
      "enabled": false
    },
    {
      "description": "Group Spring Boot starters and BOM updates — major bumps require manual review (Phase 80 OpenRewrite migration trigger)",
      "matchPackageNames": ["/^org\\.springframework\\.boot:/"],
      "groupName": "Spring Boot"
    },
    {
      "description": "Spring Boot major bumps require dashboard approval before PR creation",
      "matchPackageNames": ["/^org\\.springframework\\.boot:/"],
      "matchUpdateTypes": ["major"],
      "dependencyDashboardApproval": true,
      "automerge": false
    },
    {
      "description": "Group Spring Security starters separately from Spring Boot",
      "matchPackageNames": ["/^org\\.springframework\\.security:/"],
      "groupName": "Spring Security"
    },
    {
      "description": "Group Google API client libraries (pom.xml:158-173)",
      "matchPackageNames": ["/^com\\.google\\.apis:google-api-services-/", "/^com\\.google\\.api-client:/", "/^com\\.google\\.auth:/"],
      "groupName": "Google API clients"
    },
    {
      "description": "Group Testcontainers BOM + modules together (pom.xml:35-39)",
      "matchPackageNames": ["/^org\\.testcontainers:/"],
      "groupName": "Testcontainers"
    },
    {
      "description": "OpenRewrite recipe-pack bumps trigger manual `./mvnw -Prewrite rewrite:dryRun` migration runs (Phase 80 D-08)",
      "matchPackageNames": ["/^org\\.openrewrite\\.maven:/", "/^org\\.openrewrite\\.recipe:/"],
      "groupName": "OpenRewrite",
      "dependencyDashboardApproval": true,
      "automerge": false
    },
    {
      "description": "SpotBugs + find-sec-bugs detector packs may introduce new violations — manual review for minor/major (Phase 81 D-10)",
      "matchPackageNames": ["/^com\\.github\\.spotbugs:/", "/^com\\.h3xstream\\.findsecbugs:/"],
      "groupName": "SpotBugs + find-sec-bugs",
      "matchUpdateTypes": ["minor", "major"],
      "automerge": false
    },
    {
      "description": "Playwright minor/major bumps may require Dockerfile -noble base-image audit (Phase 78 release run 25609204039)",
      "matchPackageNames": ["com.microsoft.playwright:playwright"],
      "matchUpdateTypes": ["minor", "major"],
      "automerge": false
    },
    {
      "description": "eclipse-temurin must keep -noble suffix and Java 25 major — Playwright Ubuntu Plucky incompatibility (Dockerfile:2,20). Allows 25, 25.0.1_8, 25.0.2_10 patterns. Belt-and-braces with dockerfile-noble-pin-guard CI job.",
      "matchDatasources": ["docker"],
      "matchPackageNames": ["eclipse-temurin"],
      "allowedVersions": "/^25(?:\\.[0-9._]+)?-(?:jdk|jre)-noble$/"
    },
    {
      "description": "Patch updates automerge after CI passes — auditable via PR object (CONTEXT.md D-20)",
      "matchUpdateTypes": ["patch"],
      "automerge": true,
      "automergeType": "pr"
    }
  ]
}
```

### Verification of `renovate.json` validity

```bash
# Renovate ships a config-validator CLI as part of the renovate npm package.
# Since we are NOT installing renovate locally, use one of two paths:

# Path 1 — JSON schema validation (no Renovate install required):
# IDE auto-validates via $schema field; or use ajv-cli:
npx ajv-cli@5 validate -s <(curl -s https://docs.renovatebot.com/renovate-schema.json) -d renovate.json

# Path 2 — Renovate's own validator (one-shot npx, no global install):
# NOTE: Per CLAUDE.md slop policy avoid `npx --yes`. Use --no-install to fail loudly
# if not installed locally, then make a deliberate choice.
npx --no-install --package renovate -- renovate-config-validator renovate.json

# Path 3 — wait for Mend's onboarding PR; if config is invalid, Mend reports it there.
# Acceptable for first deployment because no code surface depends on the config.
```

### `dockerfile-noble-pin-guard` verification path (DEPS-08 / SC#6)

```bash
# Synthetic-throwaway-PR path (recommended per CONTEXT.md D-24 path 3):
# 1. Create throwaway feature branch
git fetch origin
git checkout -b chore/verify-noble-pin-guard origin/master

# 2. Make a minimal Dockerfile change that EXERCISES the guard logic
#    (any change that passes; we are NOT trying to fail the guard — just trigger it)
#    Example: comment-only change to verify build flow
# Edit Dockerfile to add a no-op comment line

# 3. Push and open PR
git add Dockerfile
git commit -m "chore: verify dockerfile-noble-pin-guard exercises on Dockerfile change"
git push -u origin chore/verify-noble-pin-guard
gh pr create --assignee jegr78 --title "chore: verify noble-pin-guard exercises on Dockerfile-bump PR" --body "Throwaway PR to confirm DEPS-08 verification path. Close without merging."

# 4. Watch CI; confirm dockerfile-noble-pin-guard job runs and passes
gh pr checks --watch

# 5. Close the PR without merging (verification complete)
gh pr close --delete-branch
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `config:base` preset | `config:recommended` preset | Renovate v36 (2023) | CONTEXT.md D-05 already locks `config:recommended` — correct. |
| `matchPackagePatterns` array | `matchPackageNames` with `/regex/` slashes OR globs | Recent Renovate releases (auto-migrating) | CONTEXT.md D-12..D-17 use `matchPackagePatterns`. Recommend rewriting to `matchPackageNames` to avoid auto-migration PR. |
| `regexManagers` config block | `customManagers` config block | Renovate 37 | Not used in this phase; relevant if/when `<java.version>` customManager is added in a future iteration. |
| Dependabot for version updates | Renovate hosted via Mend GitHub App | Project decision per research SUMMARY.md Stream 3 | Phase 84 is the transition itself. |

**Deprecated / outdated:**
- `config:base` — superseded by `config:recommended`. Renovate auto-migrates but a warning surfaces in dashboard.
- `matchPackagePatterns`, `matchDepPatterns`, `matchPackagePrefixes`, `matchDepPrefixes` — superseded by `matchPackageNames` / `matchDepNames` with new pattern syntax (globs + slash-wrapped regex).
- `regexManagers` — renamed to `customManagers` in Renovate 37.

## Project Constraints (from CLAUDE.md)

| Directive | Phase 84 Impact |
|-----------|-----------------|
| Communication: German | Affects discuss-phase / discussion logs only. RESEARCH.md is internal artifact, kept English per CLAUDE.md "Documentation in English". |
| Test coverage: minimum 82% line | Zero Java code changes — JaCoCo 87.80% baseline preserved automatically. Final `./mvnw verify` must still pass. |
| Flyway: no V1 migration changes | Not touched. |
| Profiles: Auth only in prod/docker | Not touched. |
| OSIV: enabled, `@EntityGraph` only | Not touched. |
| Backward compatibility | Not touched. |
| Playwright compile-scope dependency | `renovate.json` D-18 protects Playwright minor/major bumps from automerge — preserves Playwright/Dockerfile coupling invariant. |
| Branch from `origin/master` | Phase 84 planner MUST branch from `origin/master`, target milestone branch `gsd/v1.11-tooling-and-cleanup`. |
| `gh pr create --assignee jegr78` | renovate.json D-22 mirrors this convention via `assignees: ["jegr78"]`. |
| Conventional Commits | Phase 84 commit messages follow `feat(84): ...`, `chore(84): ...`. |
| SpotBugs + find-sec-bugs gate | renovate.json D-17 groups these and gates minor/major behind manual review. |
| `lombok.config` invariant | Not touched. |
| No `git tag` locally | Phase 84 produces no tags; release workflow handles tagging post-merge. |
| Test-call optimization | Phase 84 has zero Java code/tests — ONE final `./mvnw verify` at phase-end is sufficient. |
| Clean Maven Build = Truth | Phase 84 final verification is `./mvnw verify -Pe2e` to confirm renovate.json does NOT somehow break Maven. (It shouldn't — file is not consumed by Maven.) |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Mend Renovate GitHub App remains free for public repos in 2026 | Standard Stack | If pricing changes, fall back to self-hosted action — research SUMMARY.md flagged this contingency. Risk: LOW (no indication of pricing change as of 2026-05-17). |
| A2 | Mend uses `merge` button (not squash) by default; unconfigurable for hosted | Pitfall 6 / Patterns | If Mend squashes, commit history shows `Merge pull request` vs squash subject — purely cosmetic. Risk: NONE (no functional impact). |
| A3 | Renovate's `vulnerabilityAlerts.enabled: true` reliably overrides D-10 `enabled: false` for Thymeleaf CVEs | Pitfall 3 | Maintainer-acknowledged ambiguity. Mitigation: rely on GitHub native Dependabot alerts as backup. Risk: MEDIUM (acknowledged gap). |
| A4 | `eclipse-temurin` will continue publishing `25.X.Y_BB-jdk-noble` patterns | Pitfall 2 | If Adoptium changes the tag scheme (e.g., switches to `25.0.1-jdk-noble` without underscore-build), the corrected D-19 regex still works. Risk: LOW. |
| A5 | Branch protection on `master` does not currently require code-owner reviews | Pitfall 6 | If reviews are required, automerge effectively becomes auto-click-after-review. Operator must verify before relying on automerge. Risk: MEDIUM (state-dependent — must check during planning). |
| A6 | `dockerfile-noble-pin-guard` job remains stable; no edits in Phase 84 | Architecture Map | Locked by CONTEXT.md "What This Phase Does NOT Touch". Risk: NONE. |
| A7 | `workarounds:javaLTSVersions` preset stays included in `config:recommended` | Standard Stack | If Renovate removes it from `config:recommended`, the LTS gate disappears silently and Java 26 PRs could appear. Mitigation: explicitly `extends: ["config:recommended", "workarounds:javaLTSVersions"]` for redundancy. Risk: LOW. |
| A8 | The `customManagers` preset name `customManagers:mavenPropertyVersions` exists and works on annotated pom.xml | Don't Hand-Roll | Only relevant IF the planner chooses to add a customManager for `<java.version>`. Verify at execute time if path is chosen. Risk: NONE for default recommendation (no-op preset reliance). |

## Open Questions

1. **Does the operator want a `<java.version>` PR ever to appear, or accept the no-op?**
   - What we know: Native Maven manager doesn't detect `<java.version>`. The inherited LTS preset gates the `java-version` datasource but is unreachable from `<java.version>` properties.
   - What's unclear: Whether the planner should add a `customManagers` block + `<!-- renovate: -->` annotation in pom.xml.
   - Recommendation: ACCEPT the no-op. Java 26 is non-LTS and we never want it; Java 29 LTS is years away. When Java 29 arrives in 2027, address it as a manual milestone-driven bump, not a Renovate-driven one. Document in renovate.json D-11 entry as "deliberately no-op — Java versions are intentional milestone decisions, not unattended Renovate bumps."

2. **Does branch protection on `master` currently require approving reviews?**
   - What we know: Repository is a solo-maintainer project; CLAUDE.md mandates `gh pr create --assignee jegr78` and `gh pr merge --squash`.
   - What's unclear: Whether the GitHub branch protection rule on `master` requires 1+ approving review (which would block automerge silently).
   - Recommendation: Verify during plan-phase: `gh api repos/jegr78/ctc-manager/branches/master/protection`. If reviews are required, document the implication (D-20 patch automerge effectively becomes "approve-then-auto-click") OR add the `renovate-approve` bot.

3. **Should `renovate.json` use `matchPackagePatterns` verbatim from CONTEXT.md, or rewrite to `matchPackageNames`?**
   - What we know: Both work today; auto-migration happens transparently.
   - What's unclear: Whether the operator wants the migration-PR noise or prefers the modern syntax from PR #1.
   - Recommendation: Use modern `matchPackageNames` with leading-slash regex. Avoids the migration PR; aligns with current Renovate docs and discussions.

4. **Synthetic vs organic vs forced verification path for SC#6?**
   - What we know: CONTEXT.md D-24 enumerates three paths.
   - What's unclear: Which path the planner should commit to in VERIFICATION.md upfront.
   - Recommendation: Path 3 (synthetic throwaway PR) — fastest, deterministic, mirrors Phase 81 D-13 precedent. The Code Examples section above documents the exact `gh` flow.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| `gh` CLI | All git/PR workflow per CLAUDE.md | ✓ (assumed — used throughout project) | n/a | Manual GitHub web UI |
| GitHub Actions runner (ubuntu-latest) | dockerfile-noble-pin-guard | ✓ (CI runs daily) | n/a | None — required |
| Mend Renovate GitHub App | DEPS-02 onboarding PR | ✗ (NOT YET INSTALLED — Phase 84 deliverable) | hosted v40+ | None — phase cannot complete without installation. Self-hosted action is OUT of scope. |
| Maven (`./mvnw`) | Final `./mvnw verify` confirmation | ✓ | 3.9.x via wrapper | None |
| Java 25 (Eclipse Temurin) | `./mvnw verify` | ✓ | 25 (per pom.xml) | None |
| Branch protection state on `master` | Patch automerge behavior | UNKNOWN (must verify with `gh api`) | n/a | If reviews required: install `renovate-approve` bot OR accept manual-approval-then-auto-click |
| Repository admin access for jegr78 | App installation | ✓ (solo maintainer, owner) | n/a | None |

**Missing dependencies with no fallback:**
- Mend Renovate GitHub App installation — this IS the phase deliverable; not a blocker, but the install moment must be scheduled.

**Missing dependencies with fallback:**
- Branch protection state — verify-or-fix in planning.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | None — Phase 84 has zero Java code or test changes. Verification is JSON-schema validity + CI behavior + Mend app behavior. |
| Config file | n/a |
| Quick run command | `./mvnw verify -DskipTests` (sanity) — confirms `renovate.json` doesn't break Maven |
| Full suite command | `./mvnw verify -Pe2e` (final phase-end gate per CLAUDE.md feedback_e2e_verification) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| DEPS-01 | `renovate.json` exists at root with `enabledManagers: ["maven", "github-actions", "dockerfile"]` | structural | `test -f renovate.json && jq '.enabledManagers' renovate.json` | ✅ (after Phase 84 commit 1) |
| DEPS-01 | `renovate.json` parses as valid JSON against $schema | schema-validation | `npx ajv-cli@5 validate -s <(curl -s https://docs.renovatebot.com/renovate-schema.json) -d renovate.json` | ✅ |
| DEPS-02 | Mend Renovate GitHub App installed; onboarding PR exists | operator-action + observation | `gh pr list --search "in:title onboarding author:app/renovate"` | ❌ until operator installs |
| DEPS-03 | `renovate.json` Guava `-jre` allowedVersions rule | structural | `jq '.packageRules[] \| select(.matchPackageNames \| contains(["com.google.guava:guava"]))' renovate.json` | ✅ |
| DEPS-04 | `renovate.json` Thymeleaf `enabled: false` rule | structural | `jq '.packageRules[] \| select(.matchPackageNames \| contains(["org.thymeleaf:thymeleaf"])) \| .enabled == false' renovate.json` | ✅ |
| DEPS-05 | Java LTS constraint active (preset inheritance) | structural | `jq '.extends \| contains(["config:recommended"])' renovate.json` AND verify `workarounds:javaLTSVersions` not in `ignorePresets` | ✅ |
| DEPS-06 | Spring Boot / Spring Security / Google API / Testcontainers groups defined | structural | `jq '[.packageRules[] \| .groupName] \| inside(["Spring Boot", "Spring Security", "Google API clients", "Testcontainers"])' renovate.json` | ✅ |
| DEPS-07 | Patch automerge rule with `automergeType: "pr"` | structural | `jq '.packageRules[] \| select(.matchUpdateTypes == ["patch"]) \| .automerge == true and .automergeType == "pr"' renovate.json` | ✅ |
| DEPS-08 / SC#6 | `dockerfile-noble-pin-guard` passes on a Dockerfile-change PR | CI-job-observation | `gh pr checks <synthetic-pr-number>` shows `dockerfile-noble-pin-guard: pass` | ✅ (after synthetic PR runs) |
| Negative invariant | `.github/dependabot.yml` does NOT exist after Phase 84 | structural | `test ! -f .github/dependabot.yml` | ✅ |

### Sampling Rate

- **Per task commit:** Skip — no per-commit verify needed (zero Java changes). One `jq` structural check per packageRule after writing renovate.json.
- **Per wave merge:** N/A — single-wave phase.
- **Phase gate:** Final `./mvnw verify -Pe2e` green (confirms renovate.json doesn't break Maven build); JSON-schema validation of renovate.json; Mend onboarding PR exists; synthetic-PR run of `dockerfile-noble-pin-guard` green.

### Wave 0 Gaps

- [ ] No new test files needed — Phase 84 has no Java code surface
- [ ] No conftest / shared fixtures needed
- [ ] No framework install needed
- [ ] `jq` and `ajv-cli` are CLI utilities used only at verification time; no project-level dependency

*Existing test infrastructure is sufficient. The structural checks (`jq`, `test -f`, schema validation) live in VERIFICATION.md as shell snippets, not committed test code.*

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V1 Architecture, Design and Threat Modeling | yes | Phase 84 introduces a third-party SaaS (Mend) with write access to PR creation, branches, and (via automerge) the `master` branch. Threat model below. |
| V2 Authentication | no | No auth surface added. Mend authenticates via GitHub App OAuth — operator action, not code. |
| V3 Session Management | no | n/a |
| V4 Access Control | partial | Mend Renovate GitHub App permissions are scoped at install time. Verify scope matches least-privilege at install. |
| V5 Input Validation | no | renovate.json is not user input; it is a maintainer-curated config file. |
| V6 Cryptography | no | n/a — Mend signs its own bot commits with the GitHub App identity. |
| V10 Malicious Code | yes | Mend is a verified GitHub App publisher; risk is dependency-confusion-via-PR (a Renovate PR could propose a malicious version). Mitigation: package rules (D-03 Guava classifier, D-19 Docker suffix); CI gate; manual review for minor/major. |
| V14 Configuration | yes | renovate.json IS the configuration security control. The eight packageRules ARE the V14 controls. |

### Mend Renovate GitHub App Permission Scope (verify at install time)

The Mend Renovate App requests the following permissions (review at https://github.com/apps/renovate during install):

| Permission | Why Renovate Needs It | Risk | Mitigation |
|------------|------------------------|------|------------|
| Read access to code | Read pom.xml, Dockerfile, workflows | LOW — public repo anyway | n/a |
| Read access to metadata | Determine repo settings | LOW | n/a |
| Write access to issues | Create/update Dependency Dashboard issue | LOW | Single auto-managed issue; ignore-by-default |
| Write access to pull requests | Open dependency-update PRs | MEDIUM — PRs can contain arbitrary diffs | CI gate; manual review for non-patch; package rules block known-bad updates |
| Write access to checks | Update CI check states | LOW | Read-only on existing checks; cannot disable required checks |
| Read access to administration | Read branch protection rules | LOW — required so Renovate respects protection | Verify scope at install |
| (Optional) Write access to workflows | Update `.github/workflows/*.yml` action versions | MEDIUM — workflow file changes can alter CI behavior | Manual review for github-actions updates (default per CONTEXT.md D-12 spirit — github-actions are not in any group, all PRs require explicit review) |

### Known Threat Patterns for Renovate Integration

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malicious upstream package release proposed by Renovate (supply-chain attack) | Tampering, Elevation | (a) Patch-automerge gated by CI green; (b) minor/major requires manual review; (c) branch protection on `master` |
| Renovate compromise (Mend account breach) leads to malicious PRs | Tampering | GitHub App OAuth tokens scoped to repo permissions; Mend is SOC2-compliant; revoke via repo settings → Integrations if breach observed |
| Renovate bumps `actions/checkout@v6` to a malicious tag/SHA | Tampering | (a) github-actions PRs are not grouped (each gets its own PR + CI run); (b) major-bump PRs require manual review; (c) consider `pinDigests: true` for github-actions manager (NOT currently in CONTEXT.md — could be added as a follow-up hardening) |
| Renovate disables vulnerability alert path silently | Information Disclosure (delayed) | GitHub native Dependabot alerts remain ON (independent of `.github/dependabot.yml` removal); cross-check Mend dashboard against GitHub Security tab monthly |
| Operator confuses Renovate PR with attacker PR | Spoofing | (a) Renovate bot user is `renovate[bot]` (verified GitHub identity); (b) `labels: ["renovate"]` makes filtering trivial; (c) PRs include source URL in description |
| renovate.json modified to remove safety rules (insider threat) | Tampering | (a) Renovate respects branch protection — config changes require PR; (b) CLAUDE.md "Pull Requests: Always merge via PRs into master; no direct pushes"; (c) RESEARCH.md + CONTEXT.md document intent for audit |
| Eclipse-Temurin tag rotation to non-noble (Plucky regression) | Denial of Service (build break) | (a) D-19 `allowedVersions` regex prevents Renovate from proposing non-`-noble` tags; (b) `dockerfile-noble-pin-guard` CI job blocks any such tag if a human commits it directly |

### Threat Mitigations Already In Place

- **Branch protection on `master`** — verified during planning (Open Question #2)
- **`build-and-test` CI gate** — every Renovate PR runs `./mvnw verify -Pe2e`
- **`dockerfile-noble-pin-guard` CI gate** — every PR touching Dockerfile runs the guard
- **SpotBugs + find-sec-bugs gate** (Phase 81 active) — every Renovate PR runs bytecode static analysis
- **JaCoCo 82% coverage gate** — every Renovate PR must maintain coverage
- **Manual review for minor/major** — locked by CONTEXT.md D-12, D-16, D-17, D-18

## Sources

### Primary (HIGH confidence)

- [Renovate Configuration Options](https://docs.renovatebot.com/configuration-options/) — schema reference, all option semantics
- [Renovate Maven manager docs](https://docs.renovatebot.com/modules/manager/maven/) — Maven manager extraction behavior, BOM import support, java-version datasource limitation
- [Renovate Dockerfile manager docs](https://docs.renovatebot.com/modules/manager/dockerfile/) — Docker tag versioning, suffix-as-compatibility semantics
- [Renovate Java Versions docs](https://docs.renovatebot.com/java/) — java-version datasource, customManager pattern for `<java.version>`
- [Renovate Workarounds presets](https://docs.renovatebot.com/presets-workarounds/) — `workarounds:javaLTSVersions` definition with exact regex `/^(?:8|11|17|21|25)(?:\\.|-|$)/`
- [Renovate Default Presets](https://docs.renovatebot.com/presets-default/) — `config:recommended` composition
- [Renovate Automerge key concept](https://docs.renovatebot.com/key-concepts/automerge/) — `automergeType` semantics, branch protection interaction
- [Renovate Dependency Dashboard](https://docs.renovatebot.com/key-concepts/dashboard/) — auto-creation under `config:recommended`
- [Renovate string pattern matching](https://github.com/renovatebot/renovate/blob/main/docs/usage/string-pattern-matching.md) — glob vs regex syntax for `matchPackageNames`
- [Renovate CustomManager presets](https://docs.renovatebot.com/presets-customManagers/) — `customManagers:mavenPropertyVersions` documentation
- [Mend Renovate Free Developer Tools](https://www.mend.io/free-developer-tools/renovate/) — Mend hosted GitHub App, free for public repos
- [Renovate GitHub App marketplace](https://github.com/marketplace/renovate) — official install page
- [Eclipse Temurin 25.0.1 Adoptium news](https://adoptium.net/news/2025/11/eclipse-temurin-8u472-11029-17017-2109-2501-available/) — confirms 25.0.1 release
- [Eclipse Temurin 25.0.2_10-jdk-noble Docker Hub layer](https://hub.docker.com/layers/library/eclipse-temurin/25.0.2_10-jdk-noble/) — confirms `_<build>` tag scheme

### Secondary (MEDIUM confidence)

- [Renovate Discussion #35542 (leading slashes for matchPackageNames)](https://github.com/renovatebot/renovate/discussions/35542) — confirms regex syntax with `/.../` wrappers
- [Renovate Issue #23326 (config:base → config:recommended)](https://github.com/renovatebot/renovate/issues/23326) — preset rename history
- [Renovate Discussion #30891 (matchPackagePrefixes deprecation)](https://github.com/renovatebot/renovate/discussions/30891) — auto-migration to matchPackageNames
- [Renovate Discussion #35019 (Java version bumps in non-package-manager locations)](https://github.com/renovatebot/renovate/discussions/35019) — confirms maintainer non-support of customManagers
- [Renovate Discussion #40800 (vulnerabilityAlerts and packageRule overrides)](https://github.com/renovatebot/renovate/discussions/40800) — documented vulnerability flow / enabled:false interop ambiguity
- [Renovate Discussion #42634 (vulnerabilityAlerts flow ignores enabledManagers)](https://github.com/renovatebot/renovate/discussions/42634) — same flow caveat from a different angle

### Tertiary (LOW confidence — flagged for validation at execute time)

- Mend automerge merge-method (squash vs merge button) — undocumented; assumes `merge` (cosmetic only)
- Mend onboarding PR exact title/format when `renovate.json` already exists — assumed "Configure Renovate" but Mend may rename
- Mend hosted Renovate version pin — opaque, self-updating (acceptable per research SUMMARY.md)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Mend hosted, renovate.json schema, manager behavior all verified against current docs
- Architecture: HIGH — read-only-input pattern (CI gates / renovate.json / branch protection) is unambiguous
- Pitfalls: HIGH — pitfalls 1-5 are documented Renovate issues; pitfalls 6-8 are operational observations from Renovate community
- java.version detection caveat: MEDIUM — documented in Renovate docs but the exact "no-op" outcome is inferred from "Maven manager does not support java-version datasource" + workaround preset behavior; planner should verify on first Mend scan
- vulnerabilityAlerts override ambiguity: MEDIUM — maintainer-acknowledged but no firm resolution timeline
- eclipse-temurin tag scheme: MEDIUM — verified two patch tag examples (`25.0.1_8`, `25.0.2_10`) but full tag space not enumerated

**Research date:** 2026-05-17
**Valid until:** 2026-06-17 (30 days — Renovate is fast-moving, especially around v40+ changelog cadence and preset definitions)

---

*Phase: 84-renovate-integration*
*Researched: 2026-05-17*
*Ready for planning: yes (with four corrections to CONTEXT.md flagged in Summary)*
