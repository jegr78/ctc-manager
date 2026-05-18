---
phase: 80-openrewrite-integration
plan: 01
subsystem: infra
tags: [maven, openrewrite, build-config, profile, refactoring-tooling]

requires:
  - phase: 79-spring-boot-4-upgrade
    provides: stable Boot 4.0.6 baseline that makes the `UpgradeSpringBoot_4_0` documentary exclusion meaningful
provides:
  - "`<profile id=\"rewrite\">` Maven profile wiring `rewrite-maven-plugin:6.39.0` (D-01, D-02)"
  - "Plugin classpath with `rewrite-spring:6.30.4` + `rewrite-migrate-java:3.34.1` (D-03)"
  - "`activeRecipes` pointing to composite `org.ctc.RewriteCleanup` (string contract for Plan 02's `rewrite.yml`)"
  - "`configLocation` pointing to `${project.basedir}/rewrite.yml` (sibling file slot for Plan 02)"
affects: [80-02, 80-03, 80-04, 80-05]

tech-stack:
  added: [org.openrewrite.maven:rewrite-maven-plugin:6.39.0, org.openrewrite.recipe:rewrite-spring:6.30.4, org.openrewrite.recipe:rewrite-migrate-java:3.34.1]
  patterns:
    - "Profile-scoped plugin with zero <executions> — stricter form of isolation than the existing `versions-maven-plugin` (no executions but in default <build>)"
    - "Plugin-level `<dependencies>` block extending the plugin classpath with recipe packs"

key-files:
  created: []
  modified:
    - pom.xml

key-decisions:
  - "D-01: plugin scoped exclusively to `<profile id=\"rewrite\">` (never in main `<build><plugins>`) — bare `./mvnw rewrite:run` without `-Prewrite` fails fast with 'plugin not found' instead of silently mutating source"
  - "D-02: pin rewrite-maven-plugin to 6.39.0 (verified live latest stable on Maven Central per RESEARCH.md; ARCHITECTURE.md's '6.40.0' does not exist)"
  - "D-03: declare rewrite-spring:6.30.4 + rewrite-migrate-java:3.34.1 on the plugin — required for `UpgradeSpringBoot_4_0` documentary exclusion (Plan 02) to reference a recipe that is actually on the classpath; transitively pulls rewrite-static-analysis:2.34.1 which hosts `CommonStaticAnalysis`"

patterns-established:
  - "Maven profile insertion convention: sibling inside `<profiles>` block, `<id>`-only opt-in (no `<activation>`), TAB-indented, mirrors `e2e` profile shape"
  - "Plugin-classpath extension via `<plugin><dependencies>` — Phase 81's SpotBugs profile may reuse the same shape for SpotBugs contrib plugins"

requirements-completed: [REWR-03]

duration: 3min
completed: 2026-05-16
---

# Phase 80 Plan 01: OpenRewrite Plugin Wiring Summary

**`rewrite-maven-plugin` 6.39.0 wired into a brand-new `<profile id="rewrite">` block in pom.xml with zero `<executions>`, `rewrite-spring:6.30.4` + `rewrite-migrate-java:3.34.1` on the plugin classpath, and the `activeRecipes` pointing to composite `org.ctc.RewriteCleanup` (which Plan 02 declares in `rewrite.yml`).**

## Performance

- **Duration:** 3 min (170s wall-clock)
- **Started:** 2026-05-16T08:38:18Z
- **Completed:** 2026-05-16T08:41:08Z
- **Tasks:** 3 / 3 (Task 1: branch creation, Task 2: pom.xml edit, Task 3: atomic commit)
- **Files modified:** 1 (pom.xml)

## Accomplishments

- Feature branch `feature/openrewrite-integration` created off `origin/master` (SHA `45aabfd0e2629813c2c275877e5b3921d536eca5`); HEAD at commit `33dd5a3`.
- New `<profile id="rewrite">` block inserted into pom.xml at lines **422–456** (post-insertion line range), mirroring the structural shape of the existing `<profile id="e2e">` (now at lines 389–421, unchanged).
- Plugin coordinates pinned: `org.openrewrite.maven:rewrite-maven-plugin:6.39.0` per **D-02** (latest stable on Maven Central, verified live 2026-05-07 per RESEARCH.md §"Maven Profile Structure"; ARCHITECTURE.md's "6.40.0" citation does not exist on Maven Central).
- Plugin-classpath dependencies declared per **D-03**: `org.openrewrite.recipe:rewrite-spring:6.30.4` + `org.openrewrite.recipe:rewrite-migrate-java:3.34.1`. The transitive `rewrite-static-analysis:2.34.1` (home of `CommonStaticAnalysis`) is documented via an XML comment inside `<dependencies>` referencing the rewrite-spring-6.30.4.pom verification on Maven Central.
- `<configuration>` block carries `<activeRecipes><recipe>org.ctc.RewriteCleanup</recipe></activeRecipes>`, `<configLocation>${project.basedir}/rewrite.yml</configLocation>`, and `<exportDatatables>false</exportDatatables>`.
- Zero `<executions>` on the plugin per **D-01 / REWR-03** — verified via `xmllint --xpath "count(...)"` returning `0`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Create feature branch off origin/master** — no commit (branch creation only). Branch `feature/openrewrite-integration` HEAD = `origin/master` = `45aabfd`. `git status --porcelain` clean.
2. **Task 2: Append `<profile id="rewrite">` block to pom.xml** — staged as part of Task 3 commit (combined per plan: Task 2 produces the file edit, Task 3 stages + commits it).
3. **Task 3: Commit pom.xml wiring** — `33dd5a3` (`chore(build)`).

**Single atomic commit on `feature/openrewrite-integration`:**
- `33dd5a3` — `chore(build): wire OpenRewrite plugin into rewrite profile` (35 insertions, 0 deletions, 1 file)

_No plan-metadata commit yet — STATE.md / ROADMAP.md / SUMMARY.md updates land in a separate `docs(80-01): …` commit (see below)._

## Files Created/Modified

- `pom.xml` — added 35-line `<profile id="rewrite">` block at lines 422–456 (sibling to the existing `<profile id="e2e">`). Existing `e2e` profile and all other content byte-identical. TAB-indented to match the surrounding file.

## Verification

All `<verify>` and `<acceptance_criteria>` checks from PLAN.md passed:

**Task 1:**
- `git rev-parse --abbrev-ref HEAD` → `feature/openrewrite-integration` ✓
- `git status --porcelain` → empty ✓
- `git merge-base --is-ancestor origin/master HEAD` → exit 0 ✓

**Task 2 (xmllint + grep — the three xmllint checks referenced in the plan `<output>`):**

| # | Check | Expected | Actual |
|---|-------|----------|--------|
| 1 | `xmllint --xpath "count(//profile[id='rewrite'])"` | `1` | `1` ✓ |
| 2 | `xmllint --xpath "count(//profile[id='rewrite']//plugin[artifactId='rewrite-maven-plugin'])"` | `1` | `1` ✓ |
| 3 | `xmllint --xpath "count(//profile[id='rewrite']//executions)"` | `0` | `0` ✓ |
| 4 | `xmllint --noout pom.xml` (well-formed) | exit 0 | exit 0 ✓ |
| 5 | `grep -Fc '<version>6.39.0</version>' pom.xml` | `>=1` | `1` ✓ |
| 6 | `grep -Fc 'org.ctc.RewriteCleanup' pom.xml` | `>=1` | `1` ✓ |
| 7 | `grep -Fc '<groupId>org.openrewrite.recipe</groupId>' pom.xml` | `>=2` | `2` ✓ |
| 8 | `grep -Fc 'rewrite-spring' pom.xml` | `>=1` | `3` ✓ (occurrences across artifactId, comment, etc.) |
| 9 | `grep -Fc 'rewrite-migrate-java' pom.xml` | `>=1` | `1` ✓ |
| 10 | `grep -Fc '<configLocation>${project.basedir}/rewrite.yml</configLocation>' pom.xml` | `=1` | `1` ✓ |
| 11 | `xmllint --xpath "count(//profile[id='e2e'])"` | `1` (untouched) | `1` ✓ |
| 12 | `./mvnw help:effective-pom -P rewrite` → `grep -c 'rewrite-maven-plugin'` | `>=1` | `2` ✓ |

**Default-build isolation sanity-check (D-10 / REWR-03 — out-of-scope for Plan 01's formal verify, but executed to confirm no regression):**

```
./mvnw help:effective-pom -Dmaven.help.format=xml  # no -P flag
xmllint --xpath "count(//project/build//plugin[artifactId='rewrite-maven-plugin'])"
# → 0 (plugin absent from ACTIVE build when no -P rewrite)

./mvnw help:effective-pom -P rewrite -Dmaven.help.format=xml
xmllint --xpath "count(//project/build//plugin[artifactId='rewrite-maven-plugin'])"
# → 1 (plugin present in active build when -P rewrite)
```

This confirms Pitfall 80-C is structurally precluded: the plugin cannot be invoked without `-Prewrite`.

**Task 3:**
- `git rev-parse --abbrev-ref HEAD` → `feature/openrewrite-integration` ✓
- `git log -1 --pretty=%s` → `chore(build): wire OpenRewrite plugin into rewrite profile` ✓ (subject byte-identical to plan-locked string)
- `git log -1 --stat` → `pom.xml` (35 insertions, 0 deletions) ✓
- `git diff --cached --name-only` → empty ✓
- `git status --porcelain` → empty ✓
- `git log feature/openrewrite-integration ^origin/master --oneline` → exactly 1 commit (`33dd5a3`) ✓

## Decisions Made

None beyond the locked CONTEXT.md decisions D-01, D-02, D-03. Plan executed exactly as written.

**Decision references:**
- **D-01 (profile-scoped plugin, no executions):** implemented as `<profile id="rewrite"><build><plugins><plugin>…</plugin></plugins></build></profile>` with NO `<executions>` element anywhere — verified by xmllint check #3 returning `0`.
- **D-02 (plugin pin 6.39.0):** `<version>6.39.0</version>` declared on the plugin — verified by grep check #5.
- **D-03 (plugin deps rewrite-spring:6.30.4 + rewrite-migrate-java:3.34.1):** both declared inside `<plugin><dependencies>` — verified by grep checks #7, #8, #9.

## Deviations from Plan

None — plan executed exactly as written. No deviation rules invoked.

## Issues Encountered

- **Read-tool stale-file warning on first Edit attempt:** the `Edit` tool reported "File has been modified since read" on the initial insertion attempt. Re-reading pom.xml around the insertion window confirmed the file was unchanged. Re-running the Edit with a larger context anchor (the unique `</configuration></execution></executions></plugin></plugins></build></profile></profiles></project>` tail sequence) succeeded. No file content was lost or corrupted; this was a tool-state quirk, not a project issue.
- **Initial `mvnw -q help:effective-pom 2>/dev/null` produced an empty file:** `-q` plus stderr suppression caused the output file to be empty. Re-running with `-Doutput=/tmp/effective-pom-rewrite.xml` and without stderr redirection produced the expected 377880-byte file. Verification check #12 then passed (`grep -c 'rewrite-maven-plugin'` = 2).

## User Setup Required

None — Plan 01 changes only the build configuration; no external services, env vars, or secrets are introduced. The plugin classpath is downloaded into `~/.m2/repository` on first `./mvnw -Prewrite ...` invocation (standard Maven cache behaviour).

## Next Phase Readiness

Plan 02 ready to execute:
- `rewrite.yml` will declare the composite recipe `org.ctc.RewriteCleanup` whose `recipeList` matches the `<recipe>` string in pom.xml (case-sensitive contract: `org.ctc.RewriteCleanup`).
- `configLocation` resolves to `${project.basedir}/rewrite.yml` at plugin-invocation time, so Plan 02's net-new file lands at the correct path automatically.
- Plugin classpath already carries `rewrite-spring` and `rewrite-migrate-java`, so Plan 02's documentary `UpgradeSpringBoot_4_0` exclusion comment references a recipe that is actually on the classpath (verified earlier; documented in the XML comment inside `<dependencies>`).

Plan 03 (REWR-03 isolation verification) is now structurally pre-verifiable: the default-build isolation sanity-check above demonstrates the property holds; Plan 03 will codify the three structural verification commands from RESEARCH.md §"Verification Approach" as the formal acceptance gate.

## Self-Check: PASSED

**Files claimed created/modified:**
- ✓ `/Users/jegr/Documents/github/ctc-manager/pom.xml` — exists (verified pre-commit), `git diff origin/master pom.xml` shows 35 insertions
- ✓ `/Users/jegr/Documents/github/ctc-manager/.planning/phases/80-openrewrite-integration/80-01-SUMMARY.md` — this file (written by execute step)

**Commits claimed:**
- ✓ `33dd5a3` exists: `git log --oneline -1 33dd5a3` returns `33dd5a3 chore(build): wire OpenRewrite plugin into rewrite profile`

**Branch state:**
- ✓ HEAD on `feature/openrewrite-integration` (not on `gsd/v1.11-tooling-and-cleanup`, not on `master`)
- ✓ Working tree was clean before this SUMMARY.md was created

---
*Phase: 80-openrewrite-integration*
*Plan: 01*
*Completed: 2026-05-16*
*Branch: feature/openrewrite-integration*
*Wiring commit: `33dd5a3`*
