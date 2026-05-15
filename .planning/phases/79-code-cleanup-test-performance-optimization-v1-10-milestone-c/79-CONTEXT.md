# Phase 79: Code Cleanup + Test Performance Optimization (v1.10 Milestone Closer) - Context

**Gathered:** 2026-05-15
**Status:** Ready for planning

<domain>
## Phase Boundary

The v1.10 milestone-closer phase. Delivers FOUR streams of work, then audits and archives the milestone:

1. **Full-codebase Code Cleanup (D-1.1):** Sweep across `src/main/java/` AND `src/test/java/` — NOT scoped to `org.ctc.backup` only. Per the user override: "Über den gesamten Meilenstein hinweg haben sich viele unnötige Codepassagen eingeschlichen." All 4 cleanup classes are allowed: comment-thinning (D-3.1), dead-code removal (D-1.4), extract-method + rename (D-1.2), and logic simplification (D-1.2). Per-package atomic commits (D-1.3) with `./mvnw test` after each commit.

2. **Test Performance Optimization (D-2.1..D-2.4):** Test-Independence-Audit FIRST (deterministic, no dirty state, no test-order dependencies — verify via reverse-order run + isolation), THEN parallelization (Surefire `forkCount=2C` + `reuseForks=true`, Failsafe `forkCount=1C` parallel for IT, `@DirtiesContext` audit across the 119 `@SpringBootTest` annotations). Success = baseline measurement + ≥30% wallclock reduction documented in `79-AUTO-UAT.md`. JUnit 5 `@Execution(CONCURRENT)` is OUT (race-risk too high — D-2.1 deferred).

3. **CI / Workflow Hygiene (D-2.4):** `ci.yml` `concurrency: { group: ${github.ref}, cancel-in-progress: true }`, Maven `--no-transfer-progress` + retry-flag idiom, `mariadb-migration-smoke.yml` trigger-path-filter review, flaky-test quarantine mechanism (`@Tag("flaky")` or documented opt-out). PLUS codify the test-invocation discipline (one final `verify -Pe2e` per wave/phase, targeted `-Dtest`/`-Dit.test` for plan validation in between) — links the existing `feedback_test_call_optimization` memory.

4. **Plan-SUMMARY Frontmatter Sweep (D-4.3, carry-over from v1.9):** Bookkeeping fix on `.planning/phases/56-...`, `57-...`, `62-...`, `64-...` SUMMARY frontmatter. Single concession to v1.9 carry-overs; all other v1.9 deferred items stay carried for v1.11+.

5. **Milestone audit + complete (D-4.1, D-4.2):** `/gsd-audit-milestone v1.10` runs as a Phase-79 plan step. Findings = HARD STOP — must be fixed (as additional plan steps OR a Hotfix-Sub-Phase 79.X) before `/gsd-complete-milestone v1.10` runs. Both run inside Phase 79 (archive commits land on the feature branch and ride the squash-merge into master).

**Out of scope** (deferred — see `<deferred>`):
- `pom.xml` `<version>1.8.0-SNAPSHOT</version>` bump (Phase 77 D-16: separate release workflow AFTER Phase 79).
- Raising JaCoCo minimum above `0.82` (Phase 77 D-11 hold; CLAUDE.md constraint).
- v1.9 carry-overs except Plan-SUMMARY-Frontmatter-Sweep (Quality-Gate-Lock, per-group matchday UI, `StandingsController:139` lazy-collection cleanup, UAT-02 legacy season visual smoke) — all stay v1.11 candidates.
- JUnit 5 `@Execution(CONCURRENT)` — race-risk rejected.
- Backup-feature extensions (per-Saison export/import selectivity, verify-only mode, `manifest.sha256`, `/admin/backup/history` UI, `@Scheduled` cleanup of `data/.import-backups/<ts>/`) — all v1.11+ per REQUIREMENTS "Future Requirements".
- Behavior-changing refactors. Cleanup is verhaltens-erhaltend by contract — Backup-Wire-Contract (`SCHEMA_VERSION = 1`, manifest-first, 24-entity topo-sort) is frozen, no signature changes, no public-API renames that would break external callers.

</domain>

<decisions>
## Implementation Decisions

### Cleanup Scope (D-1.x)

- **D-01 — Cleanup-Sweep covers full `src/main/java/` AND `src/test/java/`.** User override of the recommended chirurgischen 2-File-Scope: "Über den gesamten Meilenstein hinweg haben sich viele unnötige Codepassagen eingeschlichen. Daher komplett auf src/main und src/test ausweiten. Das soll ein richtig großes und umfangreiches Cleanup sein." NOT limited to `org.ctc.backup`. The whole project codebase is in scope. Reviewer must accept that the diff will be large.
- **D-02 — All four cleanup classes are allowed:**
  - Comment-Thinning (rule details in D-09..D-12)
  - Dead-Code-Removal (safety rules in D-04)
  - Extract-Method + Rename (when methods >50 LOC OR variable/parameter names obscure intent)
  - Logik-Vereinfachung (collapse nested if/else, replace loops with streams where clearer, shorten Optional chains) — verhaltens-erhaltend, requires test-suite trust
- **D-03 — Per-package atomic commits.** One commit per Java package (e.g., `org.ctc.backup.service`, `org.ctc.domain.service`, `org.ctc.admin.controller`, `org.ctc.backup.serialization`). After each commit: `./mvnw test`. Granular reviewable, easy `git bisect` if a regression slips through, no Big-Bang.
- **D-04 — Dead-code removal: only with safe indicators.** Conservative rule: only delete a class/method/field when (a) IDE + grep find zero references in source AND (b) the symbol carries no `@Component`/`@Service`/`@Repository`/`@Bean`/`@Configuration`/`@Entity`/`@MappedSuperclass`/`@JpaListener`/`@EventListener`/`@TransactionalEventListener`/`@PostConstruct`/`@PreDestroy`/`@JsonProperty`/`@JsonCreator` annotation AND (c) it is not a JPA-required no-arg constructor or a Jackson-required public setter. On uncertainty → leave it, mark in plan as "TBD-verify". Reflection-invoked methods (rare in this codebase) survive automatically by this rule.

### Test Performance (D-2.x)

- **D-05 — All four perf hebels in scope; sequence is Test-Independence FIRST, then parallelization.** Sequence rationale: parallelization that fires before independence is verified produces flaky-positive symptoms that look like configuration bugs but are actually test-isolation bugs. The four hebels are:
  1. **Test-Independence-Audit** (Plan Wave 1) — verify reverse-order test run is green, audit `@DirtiesContext` (only 13 currently — most should be removable for context-cache reuse), audit static state, hunt fixture-pollution between tests.
  2. **Surefire `forkCount=2C` + `reuseForks=true`** (Plan Wave 2 — only after Wave 1 green) — process-level parallelism for Surefire (~1200 unit tests). Mockito + JUnit 5 are thread-safe per process; this is the safe parallelization layer.
  3. **Failsafe `forkCount=1C` parallel** (Plan Wave 2) — process-level parallelism for Failsafe IT. Lower fork ratio because each IT process boots a Spring context. Risk: Testcontainers + port conflicts; mitigate via `-Dport.dynamic` patterns where needed.
  4. **JUnit 5 `@Execution(CONCURRENT)` — REJECTED** (D-2.1 user choice). Thread-level parallelization inside the same JVM has too-high race-risk against the shared Spring context + in-memory H2.
- **D-06 — Success criterion: baseline-measured ≥ 30% wallclock reduction.** Plan Step 1 measures the current `./mvnw verify -Pe2e` wallclock on a clean local checkout (typed as `Baseline: NN.N min` in `79-AUTO-UAT.md`). Phase 79 success requires the post-optimization measurement to be ≤ `Baseline × 0.7` documented in the same file. Concrete and provable; no wishful thinking.
- **D-07 — CI / Workflow hygiene additions:**
  - `ci.yml` adds `concurrency: { group: ${{ github.ref }}, cancel-in-progress: true }` at the workflow level — eliminates wasted parallel runs on the same branch when a new push lands.
  - Maven invocations get `--no-transfer-progress` (clean logs) and a documented re-run-pattern (`./mvnw verify -fae`) for triage.
  - `mariadb-migration-smoke.yml` trigger paths reviewed; if not already path-filtered, add a path-filter so it only runs when relevant files change.
  - Flaky-test quarantine: a small documented mechanism (`@Tag("flaky")` + Surefire/Failsafe `<excludedGroups>flaky</excludedGroups>` for the default build, with an explicit weekly-or-on-demand quarantine run). This is controversial — it CAN hide problems — so the mechanism includes a "max 5 quarantined tests" hard cap and a monthly-review gate. The `mariadb-migration-smoke.yml` is SACRED (Phase 77 D-05) — these changes touch `ci.yml` only.
- **D-08 — Codify test-invocation discipline (links `feedback_test_call_optimization`).** The user's GSD-execute-flow pain ("zu häufig komplette Testläufe wieder und wieder") points at full-build re-runs between waves of the same phase. Phase 79 codifies the existing memory rule into a project-level convention (e.g., a `TESTING.md` section "Test Invocation Discipline"): one final `./mvnw verify -Pe2e` per phase (NOT per wave or per plan), targeted `-Dtest=...` / `-Dit.test=...` for plan-validation between waves. NOT a GSD-orchestrator change (out of CTC scope) — only a documented project convention that future phase-execution sessions can apply.

### Comment Thinning Rule (D-3.x)

- **D-09 — Delete categories:**
  - Phase-N references (`// Phase 75 D-07`, `// implements GAP-2`, `// see 72-CONTEXT.md`) — context lives in `.planning/`, not in source.
  - Was-comments that paraphrase the code (`// returns the list`, `// increments counter`).
  - `// removed in v1.9` markers and similar tombstones.
  - Boilerplate Javadoc that only restates the method signature.
- **D-10 — Keep categories (load-bearing comments):**
  - MariaDB-vs-H2 quirks (`// MariaDB TRUNCATE auto-commits`, `// LONGTEXT for portable JSON`).
  - Workaround / pitfall warnings (`// Hibernate 7 cannot proxy records`, `// JEP 498 escape`, CVE references).
  - Public-API Javadoc on `@Service` / `@Component` / `@Controller` public methods (`@param`/`@return`/`@throws`) — but inspectorisch geprüft: redundant signature-paraphrase Javadoc still goes.
- **D-11 — Test-code keeps `// given` / `// when` / `// then` BDD structure comments** per CLAUDE.md "Test Naming". Other test-code comments follow the same delete-rule as production code. AssertJ messages (`as("...")`) replace explanatory comments where they add diagnostic value.
- **D-12 — Class-level Javadoc condensed to 1-3 lines of responsibility.** Remove phase-evolution narratives, implementation history, and references to upstream/downstream phases. Key behavioral guarantees stay (e.g., "Bypasses AuditingEntityListener so imported `created_at` survives verbatim"). Onboarding-readability beats minimalism here — total deletion is rejected.
- **D-13 — Grep-Schutzwortliste (load-bearing-comment safety net).** Plan documents and the cleanup subagent enforces a protected-keyword list. Comments containing ANY of these words MUST NOT be deleted: `MariaDB`, `H2`, `JEP`, `CVE`, `race`, `thread-safe`, `TODO`, `HACK`, `WORKAROUND`, `FIXME`, `deadlock`, `OSIV`, `Lombok`, `Unsafe`, `transitiv`, `transitive`, `pitfall`, `auto-commit`, `auditing`, `AuditingEntityListener`. Subagent prompts include this list verbatim.

### Milestone Closure (D-4.x)

- **D-14 — Both `/gsd-audit-milestone v1.10` and `/gsd-complete-milestone v1.10` run INSIDE Phase 79 as plan steps.** Sequence: cleanup waves → test-perf waves → CI/workflow waves → frontmatter-sweep wave → audit-milestone wave → fix-findings (if any) → complete-milestone wave. The `/gsd-complete-milestone` archive commits land on the `gsd/v1.10-platform-and-backup` feature branch and ride into master via the squash-merge (D-17). The user accepted this consequence over the alternative of running complete-milestone manually after PR merge.
- **D-15 — Hard-stop on audit findings.** If `/gsd-audit-milestone v1.10` reports any failed UAT, missing requirement, or unresolved deferred-item-with-owner, Phase 79 plan execution PAUSES. Findings are addressed as additional plan steps in Phase 79 OR escalated to a Hotfix-Sub-Phase 79.X if scope demands it. `/gsd-complete-milestone` runs only after the audit comes back clean. No silent carry-over of v1.10 gaps to v1.11.
- **D-16 — Plan-SUMMARY frontmatter sweep is the SINGLE v1.9 carry-over admitted into Phase 79.** Bookkeeping fix on `.planning/phases/56-...`, `57-...`, `62-...`, `64-...` SUMMARY frontmatter (~15 files per PROJECT.md). Low-risk, thematically-aligned (cleanup-spirit). All other v1.9 carry-overs (Quality-Gate-Lock, per-group matchday UI, `StandingsController:139` lazy-collection cleanup, UAT-02) stay carried for v1.11+.
- **D-17 — One large Squash-PR.** `gh pr create --assignee jegr78` produces a single PR titled `chore(79): v1.10 milestone closer — cleanup, test perf, audit`. Per-package atomic commits from D-03 stay in history for `git bisect` (squash-merge collapses them at merge-time, but the PR's commit list remains the bisect surface). Conventional Commits prefix `chore` per CLAUDE.md (cleanup ≠ feat/fix). `gh pr merge --squash --subject "chore(79): ..."` per `feedback_squash_merge_message` memory.

### Verification & Coverage Carry-Forward

- **D-18 — JaCoCo line coverage must stay ≥ 0.82 (Phase 77 D-11 hold).** Cleanup that removes dead code MAY raise the measured coverage (smaller denominator). That is acceptable. Cleanup that removes covered code without removing the corresponding tests is a regression — the per-package `./mvnw test` gate (D-03) catches it. Phase 79 does NOT raise the `pom.xml` minimum above 0.82.
- **D-19 — Final gate is `./mvnw verify -Pe2e` BUILD SUCCESS.** Carries Phase 77 D-13 and `feedback_e2e_verification` memory. Plus baseline-vs-final wallclock comparison from D-06.

### Claude's Discretion

- **CD-01 — Per-package commit ordering.** Cleanup-subagent picks an order that minimizes inter-package risk (e.g., leaves widely-imported `org.ctc.backup.config` for last). Default: alphabetical inside `org.ctc.*`. Can be tuned if a particular package's cleanup blocks others.
- **CD-02 — Extract-method threshold.** D-02 says "methods >50 LOC". Subagent has discretion to extract earlier (>30 LOC) when readability clearly improves, or leave longer methods alone when splitting would force awkward parameter lists.
- **CD-03 — Logic-Vereinfachung pattern catalog.** Subagent picks the local readability-vs-idiom tradeoff per case. Streams are preferred over loops when the loop is purely transformational (filter/map/collect); imperative loops stay when control flow has side effects or early-return logic.
- **CD-04 — `@DirtiesContext`-Audit verdicts.** For each of the 13 current annotations, subagent assesses whether it is genuinely needed (e.g., bean replacement via `@MockBean`) or a defensive add-on. Genuinely-needed ones stay; defensive ones go.
- **CD-05 — Flaky-test quarantine list.** D-07 says "max 5 quarantined tests" hard cap. If the audit identifies more than 5 candidates, subagent picks the 5 most flaky and proposes the rest as fix-priority items in the plan.
- **CD-06 — `ci.yml` concurrency-group placement.** Subagent picks workflow-level vs. job-level concurrency based on which jobs are safe to cancel mid-run. Default: workflow-level with `cancel-in-progress: true`.
- **CD-07 — Plan-SUMMARY-Frontmatter sweep mechanics.** Subagent picks between an in-place edit script vs. manual edits per file based on the actual frontmatter delta detected. Default: manual edits to keep diffs reviewable.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### v1.10 milestone foundation

- `.planning/ROADMAP.md` §"Phase 79" — Goal `[To be planned]` placeholder; this CONTEXT.md is the source of truth for Phase 79's scope until `/gsd-plan-phase 79` writes the PLAN.md.
- `.planning/PROJECT.md` §"Carried over from v1.9 deferred" — D-16 admits Plan-SUMMARY-Frontmatter-Sweep ONLY; rejects the other 4 carry-overs explicitly. Also §"Backup Wire Contract (v1.10)" — frozen, must not be perturbed by cleanup (D-01 verhaltens-erhaltend constraint).
- `.planning/STATE.md` §"Roadmap Evolution" 2026-05-15 entry — Phase 79 added by Phase 77 D-15. STATE.md confirms current branch `gsd/v1.10-platform-and-backup`.
- `.planning/phases/77-final-uat-jacoco-hold-round-trip-test-documentation/77-CONTEXT.md` §D-15, §D-16 — defines Phase 79's preliminary scope (carried forward into D-01..D-17 above) AND the explicit out-of-scope items (`pom.xml` version bump, JaCoCo gate raise, milestone-archive-as-Phase-77-step).
- `.planning/phases/78-docker-release-image-fix/78-CONTEXT.md` — Phase 78 (Dockerfile noble pin) deliverables MUST be included in `/gsd-audit-milestone v1.10` scope per Phase 77 D-15.

### Project conventions (mandatory reading)

- `CLAUDE.md` §"Architectural Principles" — "Default to writing no comments" (D-09 derives from this), "Keep Controllers Thin" + "DTOs instead of Entities" + "No Fallback Calculations" (cleanup must NOT violate these even when shortening methods).
- `CLAUDE.md` §"Constraints" — Coverage ≥ 82 % (D-18); Flyway V1 unchanged (cleanup does NOT touch migrations); OSIV remains enabled; Backward Compatibility (no breaking URL/endpoint changes — public-API renames are out per D-01).
- `CLAUDE.md` §"Test Naming" — D-11 BDD-comment carve-out anchors on this section's Given-When-Then convention.
- `CLAUDE.md` §"Subagent Rules" — Subagents in Phase 79 MUST be `model: opus` or `sonnet` (Haiku is read-only); branch-protection block (no `git stash`/`checkout`/`reset`); post-dispatch validation; "Implement ONLY Task N, report NEEDS_CONTEXT otherwise". `feedback_subagent_stability` memory carries.
- `CLAUDE.md` §"Git Workflow" — Branch from `origin/master`; `gh pr create --assignee jegr78`; `gh pr merge --squash --subject` (D-17); CI must be green before merge.
- `.planning/codebase/TESTING.md` — Test patterns for D-05's @DirtiesContext audit + Independence-Audit; will receive new "Test Invocation Discipline" section per D-08.
- `.planning/codebase/CONVENTIONS.md` — Source of naming/structure conventions cleanup must respect (don't rename packages, keep `{Entity}Repository` / `{Entity}Service` / `{Entity}Controller` shape).
- `.planning/codebase/STRUCTURE.md` — Per-package map; D-03's per-package commit ordering uses this.

### Memories that constrain Phase 79

- `feedback_test_call_optimization` — D-08 codifies this. "Keine mehrfachen mvnw verify, gezielte -Dtest/-Dit.test, EIN finaler verify."
- `feedback_no_inline_styles` — Cleanup MUST NOT regress to inline styles when shortening templates. Templates are out-of-scope-of-cleanup (Phase 79 is Java + tests + config), but if a sweep ever touches a `.html`, this rule applies.
- `feedback_screenshots_folder` — Any UI screenshots Phase 79 captures land in `.screenshots/79/`, never at repo root.
- `feedback_e2e_verification` — Final gate is `./mvnw verify -Pe2e` (D-19).
- `feedback_coverage_strategy` — "82% Minimum, Playwright-Services excluden" (D-18).
- `feedback_subagent_stability` — Carries to D-03's per-package atomic commits (subagents process tasks individually; orchestrator validates after each).
- `feedback_orchestrator_discipline` — D-15 hard-stop on audit findings honors "Frustration ist kein Approval"; D-01's user override of the recommended chirurgischen Scope is a deliberate Architektur-Default decision, not a Process-Gate bypass.
- `feedback_pr_workflow` — D-17 Squash-PR via `gh pr create --assignee jegr78`; CI must be green before merge.
- `feedback_squash_merge_message` — D-17 uses `gh pr merge --squash --subject "chore(79): ..."` for the Conventional-Commit-message.
- `feedback_branch_from_origin` — Phase 79 plans land on the existing `gsd/v1.10-platform-and-backup` branch (per current `git status`); no new branch needed.
- `feedback_grep_all_usages` — D-04's "IDE + grep find zero references" rule directly implements this. Cleanup subagent greps codebase-wide before deleting.

### External tooling references (consulted)

- Maven Surefire `forkCount` documentation — `2C` syntax = "2 forks per CPU core"; `reuseForks=true` = "JVM reuse across test classes within a fork" (saves JVM-startup overhead).
- Maven Failsafe parallel-IT-execution — `forkCount=1C` is the conservative default for ITs because each IT typically boots Spring; higher forks risk port collisions and Testcontainers contention.
- JUnit 5 `@DirtiesContext` semantics — Spring documentation: a test marked `@DirtiesContext` invalidates the cached ApplicationContext, forcing a rebuild for the NEXT test. Audit verdict: only keep when the test deliberately mutates a singleton bean (e.g., bean-replacement via `@MockBean`) and cannot rely on `@MockBean`'s automatic reset.
- GitHub Actions `concurrency` syntax — `concurrency: { group: ${{ github.ref }}, cancel-in-progress: true }` cancels in-progress runs for the same branch when a new push arrives. Documented at `docs.github.com/en/actions/using-jobs/using-concurrency`.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`org.ctc.backup` (84 main + 81 test files, ~7k LOC main)** — The largest cleanup target. `BackupImportService.java` (906 LOC) and `BackupArchiveService.java` (639 LOC) are the two largest files; both are extract-method candidates per D-02. The 22 `<Entity>Restorer` classes follow a tight pattern (Phase 75) — Logik-Vereinfachung opportunity for any boilerplate divergence.
- **`pom.xml` Surefire/Failsafe blocks (lines 260-302)** — Today: NO `forkCount`/`parallel`/`threadCount`/`reuseForks` configured. Greenfield for D-05 perf hebels. The existing `<argLine>@{argLine} --sun-misc-unsafe-memory-access=allow -javaagent:.../mockito-core...jar</argLine>` MUST be preserved when adding fork config (Mockito agent + JEP 498 escape are load-bearing).
- **`@SpringBootTest` annotations (119 occurrences in src/test)** — Spring Test Context Cache scales with `@SpringBootTest` count. The 13 `@DirtiesContext` annotations are the cache-bust hotspots; D-05 audits each. Likely most are removable.
- **`feedback_test_call_optimization` memory** — Already-codified user preference for "ein finaler verify"; D-08 lifts it from memory into a project-level convention.
- **`.github/workflows/ci.yml`** — Target for D-07 concurrency-group + path-filter additions.
- **`.github/workflows/mariadb-migration-smoke.yml`** — SACRED (Phase 77 D-05) — Phase 79 reviews trigger paths for path-filter sanity but does NOT change the workflow body.

### Established Patterns

- **Per-package atomic commits** — D-03 mirrors the `feedback_grep_all_usages` + `feedback_subagent_stability` discipline (small atomic units, validated between, no Big-Bang).
- **Comment-cleanup precedent (Phase 67 — Comment Cleanup Re-Sweep, v1.9)** — Phase 67 already executed a comment-cleanup pass on the v1.9 codebase. D-09..D-13 mirror its rule shape; Phase 79 extends to v1.10 backup code + revisits anything that crept back.
- **Test-perf precedent: NONE in the project history** — Surefire/Failsafe parallelism has never been configured. Phase 79 is the first attempt; D-05 sequence (independence FIRST) is therefore mandatory caution, not a learned reflex.
- **`@DirtiesContext` audit precedent: NONE** — never been audited before. Plan should expect that some of the 13 current annotations are defensive cargo from earlier phases.
- **Conventional Commits** — `chore:` prefix for maintenance per CLAUDE.md. D-17 uses `chore(79):`.
- **GSD audit/complete pattern** — Most recent precedent: v1.9 milestone closure via Phase 69 (Milestone Closure Hygiene). Phase 79 mirrors that shape but adds the cleanup + test-perf streams ahead of audit/complete.

### Integration Points

- **No new Java packages.** Phase 79 only edits existing files; cleanup is by definition non-additive.
- **No new dependencies.** Maven Surefire/Failsafe parallelism is built-in; no new plugins. Concurrency-group and `--no-transfer-progress` are GitHub Actions / Maven CLI features, no plugin install.
- **No new Flyway migrations.** Cleanup is verhaltens-erhaltend (D-01 constraint); database schema is frozen.
- **No new templates, no new CSS.** Templates and CSS are explicitly out of Phase 79 scope (Java + tests + config + .planning/ frontmatter only).
- **No new entities, no new domain logic.** Backup-Wire-Contract is frozen (Phase 77 carry-forward).
- **Touched (modified):**
  - `src/main/java/**/*.java` — cleanup sweep, per D-01..D-04, D-09..D-13.
  - `src/test/java/**/*.java` — cleanup sweep + Independence-Audit + `@DirtiesContext` audit, per D-05, D-09..D-13.
  - `pom.xml` — Surefire/Failsafe `forkCount`/`reuseForks` config, per D-05. JaCoCo gate stays at 0.82 (D-18). `<version>` stays `1.8.0-SNAPSHOT` (out of scope per Phase 77 D-16).
  - `.github/workflows/ci.yml` — concurrency-group + path-filter additions per D-07.
  - `.github/workflows/mariadb-migration-smoke.yml` — trigger-path review only (D-07); body unchanged (Phase 77 D-05 SACRED).
  - `.planning/codebase/TESTING.md` — new "Test Invocation Discipline" section per D-08.
  - `.planning/phases/{56,57,62,64}-.../{...}-SUMMARY.md` — frontmatter sweep per D-16.
  - `.planning/phases/79-.../79-AUTO-UAT.md` — baseline + post-perf wallclock measurements per D-06.
  - `.planning/phases/79-.../79-VERIFICATION.md` — final-gate evidence per D-19.
  - `.planning/MILESTONE-AUDIT-v1.10.md` (or wherever `/gsd-audit-milestone` writes) — audit findings per D-14.
  - `.planning/milestones/v1.10-ROADMAP.md` (or wherever `/gsd-complete-milestone` archives) — milestone archive per D-14.

</code_context>

<specifics>
## Specific Ideas

- **The whole milestone has accumulated cruft.** User direction was explicit: "Über den gesamten Meilenstein hinweg haben sich viele unnötige Codepassagen eingeschlichen." Phase 79 is not a surgical cleanup — it is a deliberately broad sweep across the entire `src/main` + `src/test` tree, justified by the fact that v1.10 added ~7k LOC in `org.ctc.backup` plus touched ~80 templates plus many adjacent services. Reviewer must enter the PR with the expectation of reading a large diff.
- **Independence FIRST is non-negotiable.** Parallelization atop unverified independence yields false-positive regressions that look like config bugs but are isolation bugs — burns days. D-05's sequence is the lesson from countless other projects, applied prophylactically here.
- **`mariadb-migration-smoke.yml` is sacred.** Phase 77 D-05 made this explicit. D-07 reviews its trigger paths only; the workflow body must NOT change.
- **The user's GSD-execute-flow pain is real but mostly out of CTC scope.** "zu häufig komplette Testläufe wieder und wieder" describes the GSD-orchestrator's per-wave verify pattern. Phase 79 cannot fix the GSD orchestrator (different repo). What it CAN do: codify "ein finaler verify per phase" as a documented project convention (D-08) that future phase-execution sessions can reference.
- **Audit-finding hard-stop trumps milestone-completion theatre.** D-15 is deliberate. A "soft" close-with-known-gaps would carry the gaps as v1.11 candidates and the milestone would ship dishonestly. Honest close = audit clean OR fix the findings.
- **One big Squash-PR is the user's accepted choice despite the diff-size.** D-17 is a deliberate trade against the multi-PR alternative (which `feedback_sequential_pr_merge` says causes rebase-hell between PRs). Per-package atomic commits provide the bisect surface; squash-merge collapses them at merge-time.
- **`pom.xml` `<version>1.8.0-SNAPSHOT</version>` is STILL stale and STILL not Phase 79's job.** Phase 77 D-16 + D-18 already locked this; Phase 79 inherits the lock unchanged.

</specifics>

<deferred>
## Deferred Ideas

- **`pom.xml` version bump from `1.8.0-SNAPSHOT` to `1.10.0`** — Phase 77 D-16 + Phase 79 D-01 carry: separate release workflow / `/gsd-ship` invocation AFTER Phase 79 archives the milestone. Neither Phase 77 nor Phase 79 touches the `<version>` tag.
- **JaCoCo minimum raise above `0.82`** — Phase 77 D-11 + Phase 79 D-18 hold the gate at 0.82. If a future milestone wants to ratchet up (e.g., 0.85 or "measured + 0.5pp"), that is a `/gsd-config`-level decision in v1.11+ planning, not a Phase 79 decision. The Quality-Gate-Lock carry-over from v1.9 is therefore explicitly NOT in Phase 79 scope.
- **JUnit 5 `@Execution(CONCURRENT)` thread-level parallelism** — Rejected per D-2.1 / D-05. Race-risk against shared Spring context + in-memory H2 too high. Future revisit only if process-level parallelism (D-05 hebels 2+3) hits a ceiling AND independence audit is provably tight.
- **v1.9 carry-overs other than Plan-SUMMARY-Frontmatter-Sweep** — Quality-Gate-Lock, per-group matchday generation UI affordance, `StandingsController.java:139` lazy-collection style cleanup, UAT-02 (legacy season visual smoke against real pre-V4 production data). All stay v1.11+ candidates per D-16. Documented in PROJECT.md "Carried over from v1.9 deferred"; Phase 79 does NOT inherit.
- **Backup-feature extensions** — Per-Saison export/import selectivity, verify-only import mode, `manifest.sha256` checksum file, `/admin/backup/history` audit-viewer UI, `@Scheduled` cleanup of `data/.import-backups/<ts>/`. All v1.11+ candidates per REQUIREMENTS "Future Requirements". Phase 79 does NOT add features — only cleans up what's there.
- **GSD-orchestrator changes (multi-wave verify reduction)** — D-08 codifies a project convention but cannot change the GSD orchestrator's per-wave behaviour. If the convention proves load-bearing, raise it in the GSD-SDK repo as a separate orchestrator-level enhancement.
- **Templates / CSS / HTML cleanup** — Out of Phase 79 scope. The cleanup sweep is Java + tests + config + planning frontmatter only. If a future cleanup phase wants to sweep `templates/` and `static/css/`, it gets its own phase number.
- **HUMAN-UAT for Phase 79** — None planned (mirrors Phase 77 D-13). Phase 79 is verification + cleanup + audit + archive — all automated. If the milestone audit surfaces a finding that needs human eyes, it becomes a HUMAN-UAT inside the audit-fix sub-step (D-15).

</deferred>

---

*Phase: 79-code-cleanup-test-performance-optimization-v1-10-milestone-c*
*Context gathered: 2026-05-15*
