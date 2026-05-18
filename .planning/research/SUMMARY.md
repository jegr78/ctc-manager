# Project Research Summary

**Project:** CTC Manager — v1.11 Tooling Infrastructure & Tech-Debt Sweep
**Domain:** Java 25 / Spring Boot 4 / Maven tooling addition onto a production single-module project
**Researched:** 2026-05-16
**Confidence:** HIGH (stack versions, CodeQL, Renovate, pitfalls) / MEDIUM (OpenRewrite Spring Boot 4 recipe maturity, clean-code scope at bootstrap)

---

## Executive Summary

CTC Manager enters v1.11 as a mature, architecturally clean codebase (~17k LOC prod, ~25k LOC test, 87.80% JaCoCo coverage) with zero existing static-analysis tooling. The milestone has two distinct work streams: (1) adding four new developer tooling layers (OpenRewrite, clean-code enforcement, Renovate, CodeQL SAST), and (2) clearing concrete carried-over tech-debt from v1.10 and v1.9. Because the repo is **public on GitHub**, CodeQL is available free with full cross-function taint tracking — this is the single most consequential scope decision: FEATURES.md's Semgrep recommendation was based on an incorrect private-repo assumption and is overridden.

The recommended approach is incremental and gate-aware. Every new tooling stream runs parallel to the existing `./mvnw verify` gate rather than replacing it, except SpotBugs (which joins the verify phase after the existing JaCoCo and Failsafe executions). OpenRewrite is strictly developer-invoked and never bound to the Maven lifecycle. CodeQL lives in a separate `codeql.yml` workflow. Renovate runs as the Mend-hosted GitHub App with zero runner-minute cost. The tech-debt items are well-understood concrete fixes — no research uncertainty there.

The primary risks are all in the first clean-code phase: Lombok false positives will produce triple-digit violations on first SpotBugs runs if `lombok.config` and exclusion filters are not in place before the gate is enabled. The correct mitigation sequence is `lombok.config` first, then gate configuration in report-only mode, then baseline violation inventory, then enable `failOnError`. The second major risk is the OpenRewrite recipe set: the `UpgradeSpringBoot_4_0` composite recipe must NOT be used on a codebase already at Boot 4 — only targeted maintenance recipes should be activated. Both risks are fully preventable with correct phase sequencing.

---

## Key Findings

### Reconciled Stack Additions

Four new tooling layers are added to the project. No new Java packages or application dependencies are introduced — all changes are to `pom.xml` build plugins, CI workflow files, and project-root config files.

**Core tool coordinates (all versions verified against Maven Central and official release pages, 2026-05-16):**

| Tool | GroupId | ArtifactId | Version | Notes |
|------|---------|-----------|---------|-------|
| `rewrite-maven-plugin` | `org.openrewrite.maven` | `rewrite-maven-plugin` | **6.39.0** | STACK.md verified; ARCHITECTURE.md cited 6.40.0 (2026-05-07) — confirm current at implementation time |
| `rewrite-spring` | `org.openrewrite.recipe` | `rewrite-spring` | **6.30.4** | Plugin dependency only (not project dependency) |
| `rewrite-migrate-java` | `org.openrewrite.recipe` | `rewrite-migrate-java` | **3.34.1** | Plugin dependency only |
| `spotbugs-maven-plugin` | `com.github.spotbugs` | `spotbugs-maven-plugin` | **4.9.8.3** | Explicit Java 25 JDK support |
| `findsecbugs-plugin` | `com.h3xstream.findsecbugs` | `findsecbugs-plugin` | **1.14.0** | SpotBugs plugin — 144 Spring security patterns |
| `codeql-action` | GitHub Actions | `github/codeql-action@v4` | **v4** (floating tag) | Current patch v4.35.5; Java 25 explicit since CodeQL 2.23.1 |
| Renovate | Mend GitHub App | — | hosted v46.x | Self-updating; no pom.xml or workflow file required |

**What NOT to use:**

| Avoid | Why |
|-------|-----|
| Checkstyle | Naming/formatting already enforced via CLAUDE.md; Lombok false positives require extensive suppression for zero net value |
| PMD | Overlaps with Java 25 compiler warnings and existing review discipline; Lombok false positives; category-level includes silently add rules on version bumps |
| Semgrep CE | Single-function taint tracking only after Dec 2024 licensing change — misses multi-step SSRF/ZIP-Slip chains. Moot since repo is public and CodeQL is free |
| `codeql-action@v3` | Deprecated December 2026; Java 25 improvements (CodeQL 2.23.1+) in v4 only |
| OpenRewrite bound to `verify` lifecycle | Rewrites source files in-place on every build — destructive in CI |

### Reconciled Feature Recommendations

**Stream 1 — OpenRewrite:**
- Plugin in `<build><plugins>` with NO `<executions>` binding (developer-invoked only)
- Active recipes: `UpgradeToJava25` (idempotent maintenance), targeted cleanup recipes
- NEVER activate `UpgradeSpringBoot_4_0` — this is a FROM-Boot-3 migration recipe; codebase is already at Boot 4.0.6
- `mvn rewrite:dryRun` always before `mvn rewrite:run`; inspect patch file at `target/site/rewrite/rewrite.patch`

**Stream 2 — Clean Code (SpotBugs only):**
- `lombok.config` with `lombok.addLombokGeneratedAnnotation = true` AND `lombok.extern.findbugs.addSuppressFBWarnings = true` created BEFORE SpotBugs gate is enabled
- `spotbugs-exclude.xml` excluding: 11 Playwright graphic services, test classes, `EI_EXPOSE_REP*` on `org.ctc.domain.model.*`
- `findsecbugs-plugin` 1.14.0 as SpotBugs plugin dependency
- Bound to `verify` phase, declared AFTER JaCoCo in `pom.xml`
- Bootstrap: report-only first → inventory → configure exclusions → enable `failOnError`

**Stream 3 — Renovate:**
- Mend Renovate GitHub App (not self-hosted GitHub Action) — free, zero runner cost
- Critical `packageRules` required before enabling automerge: Guava locked to `-jre` variants; Thymeleaf pin protected; Java version LTS-only; GitHub Actions manual-review only
- No `.github/dependabot.yml` exists — confirmed, no conflict

**Stream 4 — SAST (CodeQL):**
- Separate `.github/workflows/codeql.yml` — NOT added to `ci.yml`
- Language: `java-kotlin`; queries: `security-extended`
- Build: `./mvnw compile --no-transfer-progress -DskipTests` (not autobuild)
- Job-level `permissions: security-events: write`
- Triage mode first — three known false-positive patterns to mark upfront: SSRF blocklist in `FileStorageService`, BCrypt in `SecurityConfig`, ZIP-Slip defense in `BackupImportService`

**Tech-debt streams (no research required — concrete known fixes):**
1. 12 REVIEW.md Info/Warning items from Phase 75 backup cleanup
2. Driver-detail Season-Assignment chip ordering (`ORDER BY year`)
3. `DevDataSeeder` `@Profile` widening for `local,demo`
4. Per-group matchday generation UI affordance (`SeasonController:251`)
5. `StandingsController.java:139` lazy collection style cleanup
6. UAT-02 legacy season visual smoke (post-deploy verification)
7. Phase 79 D-06 wallclock-reduction (`@DirtiesContext` audit, context key consolidation, selective slice conversion)
8. Nyquist `*-VALIDATION.md` drafts for 6 phases (72-76, 79) + creation for phases 71 + 78

### Architecture Approach

All four tooling streams operate on config files, `pom.xml`, and CI workflow files — no new Java source packages. Key architectural constraint: OpenRewrite never binds to the Maven verify lifecycle; SpotBugs joins verify after JaCoCo; CodeQL runs in a completely separate workflow.

**Component map after v1.11:**

| Component | Location | Change |
|-----------|----------|--------|
| SpotBugs gate | `pom.xml` `<build><plugins>` | NEW — bound to `verify`, after JaCoCo |
| OpenRewrite plugin | `pom.xml` `<build><plugins>` | NEW — no lifecycle binding |
| `spotbugs-exclude.xml` | Project root | NEW |
| `lombok.config` | Project root | NEW (does not exist yet — must be created) |
| `rewrite.yml` | Project root | NEW — recipe config |
| `renovate.json` | Project root | NEW |
| `codeql.yml` | `.github/workflows/` | NEW standalone workflow |
| `ci.yml` | `.github/workflows/` | UNCHANGED (SpotBugs runs inside existing verify) |

**Maven verify phase execution order after v1.11 (declaration order in pom.xml):**
1. `failsafe:integration-test` (existing)
2. `jacoco:report` (existing)
3. `jacoco:check` 82% gate (existing)
4. `failsafe:verify` (existing)
5. `spotbugs:check` (NEW — declared after JaCoCo)

### Watch Out For (Top 10 CTC-Specific Pitfalls)

1. **OpenRewrite `UpgradeSpringBoot_4_0` composite recipe on an already-Boot-4 codebase** — will attempt to re-add `spring-boot-starter-web`, remove modular starters, produce confusing pom.xml diffs. Only use targeted maintenance recipes. Always `mvn rewrite:dryRun` first, inspect patch before `mvn rewrite:run`.

2. **SpotBugs `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` mass false positives on Lombok `@Getter` for JPA collection fields** — 24 entities with `@OneToMany` collections = 40-80 false-positive violations on first run. Mitigate by adding both lines to `lombok.config` BEFORE enabling the SpotBugs gate, plus `EI_EXPOSE_REP*` suppression in `spotbugs-exclude.xml` for `org.ctc.domain.model.*`.

3. **JaCoCo `argLine` overwrite by new Maven plugins** — if any new plugin declares a plain `<argLine>` string (not `@{argLine} ...`), it overwrites JaCoCo's late-evaluated agent injection and coverage drops to 0%. SpotBugs does not require `<argLine>`. Verify coverage after every new plugin addition.

4. **Renovate proposing Guava `-android` variant** — Guava's `-jre`/`-android` suffix is not a standard Maven qualifier; Renovate may sort incorrectly. Requires explicit `allowedVersions` constraint in `renovate.json` before enabling patch automerge.

5. **Renovate proposing `java.version=26`** — Java 26 is non-LTS. Requires LTS-only `allowedVersions` regex in `renovate.json`.

6. **Renovate bumping Spring Boot parent independently from the pinned Thymeleaf version** — `thymeleaf:3.1.5.RELEASE` pin is a CVE-2026-40478 mitigation. Must require manual approval for `org.thymeleaf:*` in `renovate.json`.

7. **CodeQL false positive on `FileStorageService.storeFromUrl()` SSRF blocklist** — custom `Set.contains(hostname)` blocklists are not recognized CodeQL sanitizers. Triage this alert as intentional before enabling any blocking gate.

8. **`BackupSchema.SCHEMA_VERSION` incremented on non-wire-format changes during Phase 82** — the 12 REVIEW.md items are all internal I/O improvements, not wire-format changes. SCHEMA_VERSION must remain at `1`. Incrementing it breaks import of all existing production backup ZIPs.

9. **`@DirtiesContext` removal during wallclock-reduction poisoning Spring contexts** — some annotations compensate for missing `@AfterEach` cleanup (e.g., `ImportLockService` lock state). Each removal must be individually verified with three random-order Surefire runs.

10. **OpenRewrite plugin in the main `<build><plugins>` block without profile isolation is NOT the pitfall here** — the plugin CAN be in `<build><plugins>` as long as there are no `<executions>` binding it to a Maven lifecycle phase. Without `<executions>`, Maven will not auto-execute OpenRewrite goals; they remain explicitly invoked only. The pitfall is adding `<executions>` with a phase binding.

---

## Conflicts Resolved

### Conflict 1: SAST Tool Choice

**Disagreement:** FEATURES.md recommends Semgrep CE. STACK.md and ARCHITECTURE.md recommend CodeQL.

**Root cause:** FEATURES.md assumed the repo is private and concluded CodeQL requires a paid GHAS license (~$30/committer/month). This is WRONG.

**Ground truth:** `jegr78/ctc-manager` is **public on GitHub** (verified via `gh repo view`). CodeQL Code Scanning is completely free for public repos with full cross-function semantic taint tracking.

**Decision: CodeQL via `github/codeql-action@v4`.**

Semgrep CE after December 2024 provides only single-function taint tracking — it cannot trace the multi-step SSRF chain through `FileStorageService` or ZIP-Slip through `BackupImportService`. Since the cost barrier does not exist, Semgrep CE has no advantage here.

### Conflict 2: Clean Code Scope (Checkstyle + PMD vs. SpotBugs only)

**Disagreement:** STACK.md says SpotBugs only. FEATURES.md, ARCHITECTURE.md, and PITFALLS.md assume all three tools.

**Decision: SpotBugs only (no Checkstyle, no PMD).**

Rationale for this Lombok-heavy single-team project:
- **Checkstyle:** Naming/formatting enforced via CLAUDE.md + code reviews. Lombok annotation stacks violate standard Checkstyle rulesets (`DesignForExtension`, `MagicNumber`, annotation-per-line) requiring extensive suppression for zero marginal value.
- **PMD:** Highest-value rules already caught by Java 25 compiler `-Xlint` and existing discipline. Category-level includes silently add rules on PMD version bumps, breaking the gate without code changes. Test isolation prefixes (`"Test-Season 2026"`, `"T-ALF"`) trigger `AvoidDuplicateLiterals` as false positives on every test class.
- **SpotBugs:** Analyzes compiled bytecode — catches null dereferences, resource leaks, and security patterns that source-only tools cannot see. `findsecbugs-plugin` adds 144 Spring-specific security patterns directly relevant to this codebase's attack surface.

ARCHITECTURE.md's three-tool recommendation is overridden. STACK.md's SpotBugs-only recommendation is correct for this specific project.

### Conflict 3: Phase Ordering

Four conflicting orderings across research files. **Decision: PITFALLS.md ordering with adjustments.**

| Phase | Stream | Rationale |
|-------|--------|-----------|
| 80 | OpenRewrite | Establishes recipe tooling before quality gate; allows proactive cleanup pass |
| 81 | SpotBugs gate | `lombok.config` precondition established here; gate active before Renovate PRs start arriving |
| 82 | Backup cleanup | Concrete known fixes, independent of tooling; clear test gates (BackupRoundTripIT) |
| 83 | Quality/polish sweep | Four concrete carryover items; low risk, no dependencies |
| 84 | Renovate | After build gate is clean; incoming PRs hit a meaningful CI gate |
| 85 | CodeQL SAST | Additive, separate workflow; less noise after SpotBugs cleanup |
| 86 | Test wallclock reduction | Architectural work; last to avoid gate ambiguity during implementation |
| 87 | Nyquist VALIDATION closure | Documentation; must follow all code changes |

FEATURES.md ordering (Renovate first) is rejected — Renovate PRs should hit a clean, gated build. ARCHITECTURE.md ordering (Clean Code first) is correct in spirit but prescribes all three tools; adjusted to SpotBugs only.

### Conflict 4: OpenRewrite Lifecycle Binding

All four research outputs agree OpenRewrite must NOT be bound to the Maven verify lifecycle. This is codified as a hard constraint:

- Plugin in `<build><plugins>` with NO `<executions>` block
- Invoked only via explicit `mvn rewrite:dryRun` or `mvn rewrite:run` commands
- No CI job for OpenRewrite (developer productivity tool, not quality gate)

---

## Implications for Roadmap

Phase numbering continues from Phase 79. All 12 v1.11 streams mapped:

### Phase 80: OpenRewrite Integration
**Rationale:** Recipe tooling baseline before quality gate is active. Proactive cleanup reduces initial SpotBugs violation count.
**Delivers:** `rewrite-maven-plugin` in pom.xml, `rewrite.yml` with curated recipe list, developer workflow documented
**Avoids:** Pitfall 1 (Boot-4 composite recipe), Pitfall 3 (structural recipe interference with Lombok entities), Pitfall 10 (plugin auto-execution in default build)
**Research flag:** Standard patterns — complete in STACK.md.

### Phase 81: SpotBugs + find-sec-bugs Gate
**Rationale:** Only clean-code tool appropriate for this codebase. `lombok.config` precondition must be first deliverable.
**Delivers:** `lombok.config` (new), `spotbugs-exclude.xml` (new), `spotbugs-maven-plugin` in pom.xml, SpotBugs gate green in CI
**Avoids:** Pitfall 4 (EI_EXPOSE_REP mass false positives), Pitfall 7 (JaCoCo argLine overwrite)
**Research flag:** Standard patterns — complete in STACK.md.

### Phase 82: Backup Cleanup (Phase 75 REVIEW.md Items)
**Rationale:** 12 concrete known fixes, clear test gates, independent of tooling streams.
**Delivers:** All 12 REVIEW.md items resolved; `BackupRoundTripIT` passing before and after on pre-existing backup ZIPs; SCHEMA_VERSION remains 1
**Avoids:** Pitfall 8 (SCHEMA_VERSION increment), Pitfall 9 (restoreOneTable single-pass re-ordering bug)
**Research flag:** No research needed.

### Phase 83: Quality and Polish Sweep
**Rationale:** Four carryover items from v1.9/v1.10, all small and independent. Grouped to clear backlog efficiently.
**Delivers:** Driver chip ordering, DevDataSeeder @Profile widening, SeasonController:251 UI affordance, StandingsController:139 cleanup
**Research flag:** No research needed.

### Phase 84: Renovate Automated Dependency Updates
**Rationale:** After build gate is clean, incoming Renovate PRs hit meaningful CI.
**Delivers:** `renovate.json` at project root, Mend GitHub App installed, Dependency Dashboard active, critical package rules in place before automerge enabled
**Avoids:** Pitfall 4 in Renovate context (Guava android), Pitfall 5 (Thymeleaf pin), Pitfall 6 (Java 26), Pitfall 7 (GitHub Actions misaligned bumps)
**Research flag:** Standard patterns — complete in STACK.md.

### Phase 85: CodeQL SAST Integration
**Rationale:** Additive, non-blocking, separate workflow. Less false-positive noise after OpenRewrite and SpotBugs improvements.
**Delivers:** `.github/workflows/codeql.yml`, initial triage complete, all intentional patterns marked in Security tab
**Avoids:** Pitfall 12 (SSRF blocklist false positive), Pitfall 13 (BCrypt false positive), Pitfall 14 (ZIP-Slip defense false positive)
**Research flag:** Standard patterns — complete codeql.yml in STACK.md.

### Phase 86: Test Wallclock Reduction
**Rationale:** Phase 79 achieved 16.85% (target ≥30%). Architectural test-infrastructure work — placed last to avoid gate ambiguity.
**Delivers:** @DirtiesContext audit, context key consolidation if >5 unique contexts, selective @DataJpaTest conversion, forkCount increase if H2 URL isolation confirmed safe
**Avoids:** Pitfall 17 (@DirtiesContext removal poisoning), Pitfall 18 (H2 file-based URL cross-fork collision)
**Research flag:** ARCHITECTURE.md has complete Options A-E analysis. No external research needed.

### Phase 87: Nyquist VALIDATION Closure
**Rationale:** Documentation of completed work. Must follow all code changes.
**Delivers:** VALIDATION.md approved for phases 72-76 + 79; created for phases 71 + 78
**Research flag:** No research needed.

### Phase Ordering Rationale

```
80 OpenRewrite  -->  81 SpotBugs  -->  82 Backup Cleanup  -->  83 Polish Sweep
    (tool setup)       (gate active)      (concrete fixes)       (carryover)

84 Renovate  -->  85 CodeQL  -->  86 Wallclock  -->  87 Nyquist
  (clean CI)     (additive)     (arch work)       (docs)
```

UAT-02 (legacy season visual smoke) is a post-deploy verification step, not a development phase. It should be a checklist item triggered by the next production deployment.

### Research Flags

All phases have complete implementation guidance in the research files. No phase requires a separate `/gsd-research-phase` invocation.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack versions | HIGH | All verified against Maven Central and official release pages as of 2026-05-16. Minor version divergence (6.39.0 vs 6.40.0 for rewrite-maven-plugin) — confirm current at Phase 80 execution. |
| SAST tool choice | HIGH | CodeQL free for public repos is GitHub-documented. Ground truth override applied. |
| SpotBugs-only clean code scope | HIGH | Checkstyle/PMD exclusion based on well-documented Lombok false-positive patterns and existing CLAUDE.md convention discipline. |
| OpenRewrite recipe maturity | MEDIUM | Spring Boot 4 community recipes published December 2025 — still maturing. `UpgradeSpringBoot_4_0` behavior on already-Boot-4 codebase documented from pitfall analysis, not from real project experiment. |
| Renovate configuration | HIGH | Maven manager and package rules are well-established patterns. |
| Pitfall prevention | HIGH | SpotBugs/Lombok pitfalls cross-referenced from SpotBugs issue tracker; JaCoCo argLine pitfall from documented Maven property evaluation; backup pitfalls from project-specific domain knowledge. |
| Wallclock reduction outcomes | MEDIUM | Options A-C are well-documented techniques; actual gains are architecture-specific and depend on @DirtiesContext count and context key variation, which requires a live audit to determine. |

**Overall confidence: HIGH** on tooling setup (Phases 80-85) and tech-debt fixes (82-83). MEDIUM on wallclock reduction outcome (Phase 86).

### Gaps to Address During Planning

1. **OpenRewrite recipe set final selection:** Confirm via `mvn rewrite:dryRun` on a clean branch before activating. Some recipes may produce unexpected diffs on CTC-specific patterns. Resolve at Phase 80 start.

2. **SpotBugs initial violation count after lombok.config mitigation:** Research predicts 40-80 EI_EXPOSE_REP* false positives. Actual count after mitigation is unknown. Bootstrap strategy (report-only first) handles this — Phase 81 plan must not assume "zero violations" before the baseline inventory.

3. **@DirtiesContext actual count in current test suite:** Determines whether Options A and C are worth pursuing in Phase 86. Resolve at Phase 86 start: `grep -rn "@DirtiesContext" src/test/java/`.

4. **Renovate onboarding PR timing:** The Mend GitHub App requires a manual installation step by the repo owner (jegr78). The onboarding PR is created by Renovate on first run and must be merged before configuration takes effect. Must be scheduled as part of Phase 84.

---

## Sources

### Primary (HIGH confidence)
- OpenRewrite latest module versions: https://docs.openrewrite.org/reference/latest-versions-of-every-openrewrite-module
- OpenRewrite Maven plugin docs: https://docs.openrewrite.org/reference/rewrite-maven-plugin
- SpotBugs Maven plugin releases: https://github.com/spotbugs/spotbugs-maven-plugin/releases
- find-sec-bugs releases: https://github.com/find-sec-bugs/find-sec-bugs/releases
- CodeQL Java 25 support: https://github.blog/changelog/2025-09-26-codeql-2-23-1-adds-support-for-java-25-typescript-5-9-and-swift-6-1-3/
- codeql-action v4: https://github.com/github/codeql-action/releases
- Mend Renovate GitHub App: https://github.com/marketplace/renovate
- Renovate Maven manager: https://docs.renovatebot.com/modules/manager/maven/
- Renovate configuration options: https://docs.renovatebot.com/configuration-options/
- GitHub CodeQL for compiled languages: https://docs.github.com/en/code-security/code-scanning/creating-an-advanced-setup-for-code-scanning/codeql-code-scanning-for-compiled-languages

### Secondary (MEDIUM confidence)
- Semgrep CE single-function limitation post-Dec 2024: https://konvu.com/compare/semgrep-vs-codeql
- SpotBugs EI_EXPOSE_REP Lombok false positive: https://github.com/spotbugs/spotbugs-gradle-plugin/issues/731
- Checkstyle Lombok generated code issue: https://github.com/checkstyle/checkstyle/issues/13508
- Renovate Maven BOM parent conflict: https://github.com/renovatebot/renovate/issues/15170
- OpenRewrite issue #1407: Delombok before publishing (open/deferred)
- Zalando Engineering Blog: Spring Boot test optimization (DirtiesContext impact, context caching)

---

*Research completed: 2026-05-16*
*Ready for roadmap: yes*
*Milestone: v1.11 Tooling Infrastructure & Tech-Debt Sweep*
*Phases: 80-87 (continuing from Phase 79)*
