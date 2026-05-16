---
phase: 80-openrewrite-integration
plan: 02
subsystem: infra
tags: [openrewrite, recipe-config, yaml, build-config, refactoring-tooling]

requires:
  - phase: 80-openrewrite-integration
    plan: 01
    provides: "`<activeRecipes><recipe>org.ctc.RewriteCleanup</recipe></activeRecipes>` and `<configLocation>${project.basedir}/rewrite.yml</configLocation>` in pom.xml — the string contract that this plan's `rewrite.yml` `name:` field satisfies"
provides:
  - "`rewrite.yml` at repo root declaring composite recipe `org.ctc.RewriteCleanup` (D-04, D-05)"
  - "Single-entry `recipeList` activating `org.openrewrite.staticanalysis.CommonStaticAnalysis` (D-04)"
  - "Documentary tripwire (YAML comment block) naming `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` with citation to openrewrite/rewrite#1714 — protects against accidental future activation against the already-Boot-4 codebase"
affects: [80-03, 80-04, 80-05]

tech-stack:
  added: []
  patterns:
    - "Declarative OpenRewrite composite recipe in YAML at repo root (`specs.openrewrite.org/v1beta/recipe` schema) — net-new file shape with no in-repo analog"
    - "Documentary exclusion via YAML-comment tripwire (NOT a YAML field) — OpenRewrite has no recipe-level exclusion mechanism per openrewrite/rewrite#1714 wontfix"

key-files:
  created:
    - rewrite.yml
  modified: []

key-decisions:
  - "D-04: recipe set is minimal — `recipeList` contains exactly one entry, `org.openrewrite.staticanalysis.CommonStaticAnalysis`. No `UpgradeToJava25`, no modernization recipes, no other packs."
  - "D-05 (as corrected by RESEARCH.md §Recipe Selection Detail): the `UpgradeSpringBoot_4_0` exclusion is a documentary YAML-comment tripwire, NOT an `excludedRecipes` / `excludes:` YAML field. OpenRewrite has no built-in sub-recipe exclusion mechanism (openrewrite/rewrite#1714 closed wontfix). Whitelist-only activation via `recipeList` is the sole exclusion mechanism."
  - "D-06: no pre-emptive package-level exclusions (e.g., `org.ctc.domain.model.*`) — Lombok false-positive handling is deferred to the post-hoc dryRun-review workflow defined in D-08."

patterns-established:
  - "OpenRewrite composite-recipe YAML lives at repo root (sibling to pom.xml/mvnw/README.md), NOT under `src/main/resources/` and NOT under `.openrewrite/`. The `<configLocation>${project.basedir}/rewrite.yml</configLocation>` element in pom.xml resolves the path."
  - "Sub-recipe exclusion is documentary — comment blocks citing openrewrite/rewrite#1714 are the only enforcement mechanism. Future maintainers reading `rewrite.yml` see the tripwire before adding recipes to `recipeList`."

requirements-completed: [REWR-04]

duration: 8min
completed: 2026-05-16
---

# Phase 80 Plan 02: rewrite.yml Composite Recipe Summary

**Created `rewrite.yml` at the repo root declaring composite recipe `org.ctc.RewriteCleanup` whose `recipeList` activates only `org.openrewrite.staticanalysis.CommonStaticAnalysis` (D-04), with a documentary YAML-comment tripwire naming `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` (D-05 as corrected per openrewrite/rewrite#1714 — no `excludedRecipes` YAML field exists).**

## Performance

- **Duration:** ~8 min (writing + verification + commit + summary)
- **Completed:** 2026-05-16
- **Tasks:** 2 / 2 (Task 1: write rewrite.yml; Task 2: atomic commit)
- **Files created:** 1 (`rewrite.yml`)
- **Files modified:** 0

## Accomplishments

- `rewrite.yml` exists at repo root (sibling to `pom.xml`, `mvnw`, `README.md`, `compose.yaml`) — matches the `<configLocation>${project.basedir}/rewrite.yml</configLocation>` path Plan 01 wired into the pom.xml `<profile id="rewrite">` block (pom.xml:434).
- YAML document declares the v1beta schema (`type: specs.openrewrite.org/v1beta/recipe`) and the composite recipe `name: org.ctc.RewriteCleanup` — string-identical (case-sensitive) to pom.xml:432's `<recipe>org.ctc.RewriteCleanup</recipe>`.
- `recipeList` has exactly one entry — `org.openrewrite.staticanalysis.CommonStaticAnalysis` (per **D-04**). No `UpgradeToJava25`, no other packs.
- Documentary tripwire comment block after `recipeList` calls out `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` BY NAME with the rationale "codebase already on Boot 4.0.6; this recipe migrates FROM Boot 3 and would produce confusing diffs (see PITFALLS Pitfall 1)". The block cites both the whitelist-only Maven-plugin docs (`https://docs.openrewrite.org/reference/rewrite-maven-plugin`) and the wontfix issue (`https://github.com/openrewrite/rewrite/issues/1714`).
- NO `excludedRecipes:` / `excludes:` / `exclusions:` YAML field — these do not exist in the OpenRewrite v1beta schema per RESEARCH.md §"Recipe Selection Detail" / openrewrite/rewrite#1714. The tripwire is a YAML comment, NOT a YAML field.
- Two-space YAML indentation (matches the repo's `compose.yaml` convention).
- Atomic commit `2003058` on milestone branch `gsd/v1.11-tooling-and-cleanup` (per user override of the plan's `<branch_protection>` `feature/openrewrite-integration` claim — see memory `milestone-branch-zuerst`).

## Verbatim final content of `rewrite.yml`

```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: org.ctc.RewriteCleanup
displayName: CTC Manager — common static analysis cleanup
description: >
  Developer-invoked one-shot cleanup pack for CTC Manager.
  Activates only org.openrewrite.staticanalysis.CommonStaticAnalysis (≈70 sub-recipes).
  See README "## Development" for the dryRun → run workflow.

# OpenRewrite is whitelist-only: any recipe not listed here is inert by construction
# (https://docs.openrewrite.org/reference/rewrite-maven-plugin —
#  "No recipe is run unless explicitly turned on with this setting").
recipeList:
  - org.openrewrite.staticanalysis.CommonStaticAnalysis

# Documentary exclusion list — recipes that are deliberately NOT activated for this project.
# OpenRewrite does not support recipe-level exclusion inside a composite
# (https://github.com/openrewrite/rewrite/issues/1714 wontfix). These entries exist as a
# tripwire for future maintainers: do NOT add them to recipeList above.
#
# org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0  — codebase already on Boot 4.0.6;
#   this recipe migrates FROM Boot 3 and would produce confusing diffs (see PITFALLS Pitfall 1).
```

## Task Commits

Atomic commit on `gsd/v1.11-tooling-and-cleanup`:

- `2003058` — `chore(build): add OpenRewrite composite recipe rewrite.yml` (1 file changed, 22 insertions, 0 deletions). Full SHA: `2003058185eb700f95229f6f9b2d977086a729b8`.

_The plan-metadata commit (`docs(80-02): …`) is separate from this content commit — same separation as Plan 01._

## Files Created/Modified

- **Created:** `/Users/jegr/Documents/github/ctc-manager/rewrite.yml` — 22 lines, two-space YAML indentation, parses cleanly via `python3 yaml.safe_load`.

## Verification

All `<verify>` and `<acceptance_criteria>` checks from PLAN.md passed.

**Task 1 (rewrite.yml structure):**

| # | Check | Expected | Actual |
|---|-------|----------|--------|
| 1 | `test -f rewrite.yml` | exit 0 | exit 0 ✓ |
| 2 | `python3 -c "import yaml; yaml.safe_load(open('rewrite.yml'))"` | exit 0 | exit 0 ✓ |
| 3 | `d.get('name') == 'org.ctc.RewriteCleanup'` | `True` | `True` ✓ |
| 4 | `d.get('type') == 'specs.openrewrite.org/v1beta/recipe'` | `True` | `True` ✓ |
| 5 | `d.get('recipeList') == ['org.openrewrite.staticanalysis.CommonStaticAnalysis']` | `True` | `True` ✓ |
| 6 | `grep -F 'org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0' rewrite.yml` | ≥1 match (in comment) | 1 match in comment ✓ |
| 7 | `grep -F 'openrewrite/rewrite/issues/1714' rewrite.yml` | ≥1 match | 1 match ✓ |
| 8 | `grep -F 'org.openrewrite.staticanalysis.CommonStaticAnalysis' rewrite.yml` | ≥1 | 2 (description + recipeList) ✓ |
| 9 | `grep -E '^[^#]*excludedRecipes' rewrite.yml` | empty | empty ✓ |
| 10 | `grep -E '^[^#]*excludes:' rewrite.yml` | empty | empty ✓ |
| 11 | `grep -A 5 '^recipeList:' rewrite.yml \| grep -F 'UpgradeSpringBoot_4_0'` | empty | empty ✓ |
| 12 | Cross-link: `grep -F 'org.ctc.RewriteCleanup' pom.xml rewrite.yml` | ≥1 in each | pom.xml:432 + rewrite.yml:3 ✓ |

`python3 yaml.safe_load` parsed output (confirmation):
```
{'type': 'specs.openrewrite.org/v1beta/recipe',
 'name': 'org.ctc.RewriteCleanup',
 'displayName': 'CTC Manager — common static analysis cleanup',
 'description': 'Developer-invoked one-shot cleanup pack for CTC Manager. Activates only org.openrewrite.staticanalysis.CommonStaticAnalysis (≈70 sub-recipes). See README "## Development" for the dryRun → run workflow.\n',
 'recipeList': ['org.openrewrite.staticanalysis.CommonStaticAnalysis']}
```

**Task 2 (commit):**

| # | Check | Expected | Actual |
|---|-------|----------|--------|
| 1 | `git rev-parse --abbrev-ref HEAD` | `gsd/v1.11-tooling-and-cleanup` | `gsd/v1.11-tooling-and-cleanup` ✓ |
| 2 | `git log -1 --pretty=%s` | `chore(build): add OpenRewrite composite recipe rewrite.yml` | byte-identical ✓ |
| 3 | `git log -1 --name-only --pretty=` | `rewrite.yml` only | `rewrite.yml` only ✓ |
| 4 | `git status --porcelain` | empty | empty ✓ |

## Decisions Made

None beyond the locked CONTEXT.md decisions D-04, D-05, D-06. Plan executed exactly as written, with the user-mandated branch override (milestone branch `gsd/v1.11-tooling-and-cleanup` instead of the plan's stale `feature/openrewrite-integration` reference — see Deviations).

**Decision references:**
- **D-04 (minimal recipe set):** `recipeList` contains the single entry `org.openrewrite.staticanalysis.CommonStaticAnalysis` and nothing else — verified by check #5.
- **D-05 (composite + documentary exclusion, as corrected by RESEARCH.md):** the YAML file declares the composite via `name:` / `recipeList:`, and the `UpgradeSpringBoot_4_0` "exclusion" is implemented as a YAML-comment tripwire block — verified by checks #6, #7, #9, #10, #11. The literal D-05 wording ("the YAML schema supports recipe-level exclusion inside a composite recipe") is technically incorrect per openrewrite/rewrite#1714 wontfix; the implementation honours the corrected behaviour, not the literal D-05 wording, as explicitly directed by PLAN.md Task 1 action notes.
- **D-06 (no pre-emptive package-level exclusions):** the file contains no `org.ctc.domain.model.*` or similar package globs — verified by inspection of the verbatim content above. Lombok false-positive handling is deferred to Plan 04's dryRun-review workflow (D-08).

## Deviations from Plan

**Single deviation — branch override (user-driven, not a Rule 1/2/3 deviation):**

The plan's `<branch_protection>` block specifies "Active branch: `feature/openrewrite-integration`". The user explicitly overruled this at orchestrator-spawn time: *"Ganz wichtig: alle Änderungen auf dem Meilenstein Branch durchführen. Keine neue Feature Branches anlegen."* The previous `feature/openrewrite-integration` branch had already been deleted; Plan 01's pom.xml work was cherry-picked onto `gsd/v1.11-tooling-and-cleanup` as commit `cd1c1db` before this plan executed. All Plan 02 commits therefore land on `gsd/v1.11-tooling-and-cleanup` directly. This matches the milestone-branch-zuerst memory entry (updated 2026-05-16) and PROJECT.md's `Branch:` footer.

No Rule 1/2/3/4 deviation was triggered — the file content matches the plan's `<action>` specification exactly; only the branch target differs, and that difference is user-mandated.

## Issues Encountered

- The plan's `<branch_protection>` references `feature/openrewrite-integration` 5× (frontmatter `affects` aside). All those references are stale per the user's milestone-branch-zuerst directive and must be read with the milestone-branch override in mind. Plan 03/04/05 will likely carry the same stale `feature/openrewrite-integration` text — apply the same override.

## User Setup Required

None — Plan 02 creates only the declarative recipe config. The plugin classpath was already downloaded during Plan 01's `./mvnw help:effective-pom -P rewrite` invocation, so `./mvnw -Prewrite rewrite:discover` (Plan 03 verification) will not re-download.

## Next Phase Readiness

Plan 03 ready to execute:
- `rewrite.yml` exists at the path the pom.xml `<configLocation>` element resolves to (`${project.basedir}/rewrite.yml` → repo root).
- `name: org.ctc.RewriteCleanup` matches pom.xml:432's `<recipe>` value byte-identically — Plan 03's `./mvnw -Prewrite rewrite:discover` should list `org.ctc.RewriteCleanup` in its output.
- `recipeList` activates `CommonStaticAnalysis` which lives in `rewrite-static-analysis:2.34.1` (transitively on the plugin classpath via `rewrite-spring:6.30.4` per Plan 01's D-03 verification) — Plan 03's `dryRun` will resolve the composite and apply the ~70 sub-recipes.

Plan 04 (cleanup commit IF dryRun produces a non-empty patch) is structurally enabled: any false-positive triage will edit `rewrite.yml` to replace `CommonStaticAnalysis` with its sub-recipe list minus the offender (D-08 + RESEARCH.md "Inline workaround"), since true sub-recipe exclusion is not supported.

Plan 05 (README + CLAUDE.md docs) can also reference `rewrite.yml` via the `[`rewrite.yml`](./rewrite.yml)` markdown link without further changes.

## Self-Check: PASSED

**Files claimed created/modified:**
- ✓ `/Users/jegr/Documents/github/ctc-manager/rewrite.yml` exists (verified pre-commit + post-commit via `test -f rewrite.yml`)
- ✓ This SUMMARY at `/Users/jegr/Documents/github/ctc-manager/.planning/phases/80-openrewrite-integration/80-02-SUMMARY.md` (written via Write tool)

**Commits claimed:**
- ✓ `2003058` exists: `git log --oneline -1 2003058` returns `2003058 chore(build): add OpenRewrite composite recipe rewrite.yml`
- ✓ Full SHA `2003058185eb700f95229f6f9b2d977086a729b8`

**Branch state:**
- ✓ HEAD on `gsd/v1.11-tooling-and-cleanup` (milestone branch — NOT on a feature branch, NOT on `master`)
- ✓ Working tree was clean before this SUMMARY was created

**Cross-link sanity:**
- ✓ `grep -F org.ctc.RewriteCleanup pom.xml rewrite.yml` returns matches in both files (pom.xml:432, rewrite.yml:3)

---
*Phase: 80-openrewrite-integration*
*Plan: 02*
*Completed: 2026-05-16*
*Branch: gsd/v1.11-tooling-and-cleanup (milestone branch, per user override of plan's stale `feature/openrewrite-integration` reference)*
*Content commit: `2003058`*
