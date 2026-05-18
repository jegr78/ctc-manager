# Phase 80: OpenRewrite Integration - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-16
**Phase:** 80-openrewrite-integration
**Areas discussed:** Plugin wiring, Recipe configuration, One-shot cleanup commit strategy, Lombok false-positive handling, Plugin dependencies, README/CLAUDE.md docs

**Discussion mode:** auto (user standing instruction to "work without stopping for clarifying questions" — Claude resolved each gray area with the recommended option; user can redirect any decision before plan-phase)

---

## Plugin Wiring — Profile vs. Main Build Section

| Option | Description | Selected |
|--------|-------------|----------|
| A. Main `<build><plugins>` with no `<executions>` | Strict REWR-03 wording. Invoke `./mvnw rewrite:dryRun` directly (no `-P`). `-Prewrite` would be unused. | |
| B. Inside `<profile id="rewrite">` block | Plugin only available when `-Prewrite` is passed. Matches the phase goal name and 3/5 success criteria. Fails-fast on bare `mvn rewrite:run` | ✓ |
| C. Both (plugin in main + profile-scoped recipe activation) | Over-engineered split; no concrete benefit. | |

**Decision rationale:** The phase goal explicitly says "via `-Prewrite` Maven profile", and 3 of 5 success criteria invoke `./mvnw -Prewrite …`. REWR-03's wording ("no `<executions>` binding") is satisfied by profile placement too (zero executions in any scope). Profile-scoping makes the dev UX self-documenting and prevents accidental source mutation by a bare `mvn rewrite:run`.

---

## Recipe Configuration in `rewrite.yml`

| Option | Description | Selected |
|--------|-------------|----------|
| A. Minimal — only `CommonStaticAnalysis` (Boot-4 upgrade excluded) | REWR-04 wording, narrowest first integration. | ✓ |
| B. Maintenance-extended — add `UpgradeToJava25` for idempotent Java-version drift protection | More value per phase, but wider one-shot diff (REWR-05) and more Lombok false-positive surface. | |
| C. Maximal — modernization, security, performance packs | Out of phase scope; explicitly REWR-FUTURE-01. | |

**Decision rationale:** Keep the first integration narrow. REWR-04 names exactly one recipe pack. Extra packs do not satisfy any REWR-* requirement and widen the diff for the mandatory one-shot cleanup commit. Future expansion is captured as REWR-FUTURE-01.

---

## One-Shot Cleanup Commit Strategy (REWR-05)

| Option | Description | Selected |
|--------|-------------|----------|
| A. Single atomic commit for the entire CommonStaticAnalysis cleanup | dryRun patch is the audit trail; meta-recipe stays atomic. | ✓ |
| B. Per-sub-recipe commits for git-bisect-ability | Defeats the meta-recipe value; explodes commit count for marginal bisect benefit. | |
| C. Per-package commits (domain / admin / sitegen / backup) | Arbitrary split; OpenRewrite does not produce per-package diffs natively. | |

**Decision rationale:** `CommonStaticAnalysis` is curated as a single composite; commit it as such. The `target/site/rewrite/rewrite.patch` file produced by dryRun is the human audit trail. Commit message body enumerates the recipe IDs and any manually reverted files.

---

## Lombok-Entity False-Positive Handling

| Option | Description | Selected |
|--------|-------------|----------|
| A. Pre-emptive `org.ctc.domain.model.*` exclusion in `rewrite.yml` | Too coarse — blocks legitimate cleanups like unused-import removal in entities. | |
| B. Post-hoc dryRun review → revert offending sub-recipes (rewrite.yml exclude list) → manual file revert for stragglers | Surgical; dryRun is the existing audit step; matches REWR-05 wording "reviewed for Lombok entity false positives". | ✓ |
| C. Run unfiltered + accept all diffs | Risks marking `@Getter` entities `final` (breaks Hibernate proxying — PITFALLS.md #3). | |

**Decision rationale:** The dryRun step exists specifically for this human review. Coarse package exclusion is over-mitigation. The workflow (D-08) is: dryRun → review → if a sub-recipe misbehaves, add it to `rewrite.yml` exclude + commit that update → re-dryRun → run. Final fallback is manual `git checkout` on 1–3 problem files before the atomic refactor commit.

---

## Plugin Dependencies — Include `rewrite-spring` or Drop It?

| Option | Description | Selected |
|--------|-------------|----------|
| A. Both `rewrite-spring` 6.30.4 + `rewrite-migrate-java` 3.34.1 declared | Required on classpath for `UpgradeSpringBoot_4_0` *exclusion* to be meaningful. Enables future activation as one-line rewrite.yml edit. | ✓ |
| B. Only `rewrite-migrate-java`, drop `rewrite-spring` | We don't activate Spring recipes today, but then the `UpgradeSpringBoot_4_0` exclusion (REWR-04) is a no-op exclusion. | |
| C. Neither — `CommonStaticAnalysis` is in core | Minimal classpath, but loses future activation ergonomics. | |

**Decision rationale:** Plugin dependencies don't leak to compile/runtime classpath, so cost is near zero. Keeping both packs makes the exclusion in REWR-04 semantically meaningful (you can only meaningfully exclude a recipe that is on the classpath) and turns future Spring/Java-version recipe activation into a one-line `rewrite.yml` edit.

---

## README + CLAUDE.md Documentation Surface

| Option | Description | Selected |
|--------|-------------|----------|
| A. New "OpenRewrite (developer-invoked refactoring)" subsection under README `## Development` + two-line append to CLAUDE.md `## Commands` | Discoverable in both onboarding paths. REWR-06 wording satisfied. | ✓ |
| B. README only | Misses Claude-CLI-driven workflows that read CLAUDE.md first. | |
| C. CLAUDE.md only | Misses human contributors reading README first. | |

**Decision rationale:** REWR-06 names README "Development" explicitly. CLAUDE.md `## Commands` is the canonical command catalogue for AI-assisted work and appending two lines is cheap; both surfaces stay in sync.

---

## Claude's Discretion

- Exact `rewrite-maven-plugin` version pin (6.39.0 vs. 6.40.x) — choose latest stable on Maven Central at planning time.
- Wording of the README subsection — match surrounding tone; brevity preferred.
- Whether the dryRun produces zero changes — if codebase is already clean post v1.10, REWR-05 is satisfied by documenting "no-op dryRun" in VERIFICATION.md instead of producing a refactor commit.

## Deferred Ideas

- `UpgradeToJava25` and broader migrate-java recipe activation → REWR-FUTURE-01.
- CTC-specific custom recipes (e.g., enforce `@RequiredArgsConstructor`) → REWR-FUTURE-01.
- CI gate running `rewrite:dryRun` and failing on non-empty diff → explicit out-of-scope per REQUIREMENTS.md Out-of-Scope table.
- `UpgradeSpringBoot_4_0` activation → only when migrating FROM Boot 4, as a NEW future phase.
- Other `rewrite-spring` recipes (non-upgrade) → trivial to enable later since the dependency is already on the plugin classpath (D-03).
