# Phase 67: Comment Cleanup Re-Sweep - Context

**Gathered:** 2026-05-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Re-enforce the CLAUDE.md comment policy across the entire repository: *"Default to writing no comments. Only add one when the WHY is non-obvious"*. The v1.9 cluster (Phases 56–66) re-introduced the same kinds of noise comments earlier cleanup phases (20-21 / 53 / 61) had stripped — phase-attribution markers, decorative separators, and WHAT-style narration. This phase sweeps the noise back out, locks the BDD test markers (`// given` / `// when` / `// then`) as a hard exception, and produces a clean baseline.

**Codebase scope sized:**
- `src/main/java` — 182 Java files
- `src/main/resources/templates` — 79 Thymeleaf templates (141 `<!--` HTML comments)
- `src/test/java` — 121 Java files containing 1,899 BDD marker lines across 105 files (MUST preserve)

**Concrete offender patterns confirmed via codebase scout:**
- Decorative section separators: `// ---------------------------------------------------------------------------` — heaviest in `DriverSheetImportServiceTest.java` (40 occurrences), `SiteGeneratorServiceTest.java` (23), `RaceControllerTest.java` (19), `DriverSheetImportService.java` / `RaceService.java` / `MatchdayService.java` (~9 each).
- Phase-attribution comments: `// Phase 62 D-15:` (DriverProfilePageGenerator.java:63), `// Phase 58-05:` (PlayoffServiceTest.java:800, PlayoffSeedingServiceTest.java:294), `// Phase 60.` (GroupsSeasonE2ETest.java:273), `// Phase 61 gap-10:` (SeasonPhaseControllerIT.java:72).
- Artifact references: `// per RESEARCH.md` (DriverProfilePageGenerator.java:77).
- Long Javadoc with embedded migration history (e.g., V5 Flyway migration: *"Originally shipped as V5__nullable_legacy_scoring_columns.sql, but that version used PostgreSQL/H2-only ALTER COLUMN syntax which raises MariaDB error 1064 — production deploys to MariaDB never succeeded. Replaced with this dialect-aware Java migration..."*) — historical context belongs in commits, not source.

**In scope:**
- Sweep all noise comments from production, templates, and tests.
- Preserve WHY comments (workarounds, non-obvious constraints), license headers (none currently), and BDD markers.
- Produce per-directory atomic commits (one per plan).

**Out of scope:**
- `src/main/resources/db/migration/*.sql` — Flyway file headers stay (audit trail).
- `pom.xml` XML comments — build attribution comments stay.
- `package-info.java` — package-level Javadoc is intentional documentation.
- Configuration files (`application*.yml`, `*.properties`) — operator-facing comments stay.
- CI / pre-commit guard automation (deferred — see `<deferred>`).
- Refactoring code along the way (comments-only diff per CLAUDE.md *"Don't add features, refactor, or introduce abstractions beyond what the task requires"*).

</domain>

<decisions>
## Implementation Decisions

### Definition of "noise" — what gets removed

- **D-01:** Decorative separators of any width — `// ----...----`, `// ====...====`, `// ****...****`, `// ###...###`. Replace with a single blank line if the separator was structuring a section, otherwise just delete.
- **D-02:** Phase / PR / issue attribution markers — `// Phase 5X`, `// per Phase X`, `// added in Phase X`, `// removed in Phase X`, `// fix for #N`, `// gap-NN`, `// per RESEARCH.md`, `// per CONTEXT.md`, `// per CLAUDE.md`, `// per ROADMAP.md`. Phase decisions live in commit messages and `.planning/phases/`; embedding them in source rots over time.
- **D-03:** WHAT-style comments that re-state the next line / method body — `// increment counter`, `// loop through items`, `// get the result`, `// return early`. Well-named identifiers already say what.
- **D-04:** Stale TODO / FIXME / XXX whose conditions no longer apply — drop on sight. (Active TODOs stay if they reference a tracked issue.)
- **D-05:** Long Javadoc with embedded migration / ticket history — strip the historical narrative; keep the technical contract (what the method/class does, parameter semantics, return value). Migration "originally shipped as X" prose belongs in git history.

### What to KEEP — explicit allow-list

- **D-06:** BDD test markers `// given`, `// when`, `// then`, `// when / then` (combined for `assertThatThrownBy`) — mandatory per CLAUDE.md § Development Approach. **1,899 lines across 105 test files — DO NOT TOUCH.**
- **D-07:** Javadoc on public APIs that document a non-obvious contract (regulatory invariants, OSIV-aware semantics, transaction boundaries, mutation order requirements). Keep Javadoc on `@Deprecated` symbols (API documentation convention).
- **D-08:** Comments explaining a true workaround — Hibernate quirks, MariaDB-specific dialect notes, framework version pin reasons, race-condition guards. The test: *"would removing this comment confuse a reader who doesn't know the surrounding code's history?"* — if YES, keep.
- **D-09:** Reference comments to currently-active external specs in `docs/superpowers/specs/` IF the spec is the source of truth for the surrounding code (e.g., scoring formulas pulled from `2026-03-29-scoring-legs-design.md`). Stale spec references go.
- **D-10:** `package-info.java` files — out of scope per `<domain>`.
- **D-11:** Thymeleaf parser comments `<!--/* ... */-->` if they're structurally required by the templating engine — only delete if confirmed-to-be-pure-noise.

### Sweep methodology

- **D-12:** **File-by-file pass with judgement, not blanket regex deletion.** Each file:
  1. Read full content.
  2. Identify candidate offenders by pattern (D-01..D-05).
  3. Apply the keep-test (D-06..D-11) per candidate.
  4. Edit out the noise; preserve everything else.
  5. After production-code edits in a service / controller, run `./mvnw test -Dtest=<RelevantTest>` to confirm no behavior change.
- **D-13:** **No automated regex bulk delete.** False-positive risk on Javadoc, license-style headers, and conditional deletions is too high. Per-file judgement keeps the 1,899 BDD markers safe.

### Plan structure

- **D-14:** **Three plans, one per directory** for atomic per-directory commits and clearer review. Each plan ships one commit.
  - **Plan 67-01:** Production sweep — `src/main/java/org/ctc/**` (182 files; expect 30–60 with offenders).
  - **Plan 67-02:** Templates sweep — `src/main/resources/templates/**` (79 files, 141 HTML comments — most are likely needed structural markers; expect 10–20 noise removals).
  - **Plan 67-03:** Tests sweep — `src/test/java/org/ctc/**` (121 files; bulk of decorator separators here — preserve 1,899 BDD lines).
- **D-15:** **Sequential plan execution** (single wave, plans 01 → 02 → 03 in order). Per-directory plans don't overlap on `files_modified`, so worktree-parallel could work — but the cost (3 worktrees, 3 merges) outweighs the benefit for a comments-only sweep. Sequential single-wave execution on main tree, no worktree overhead.

### Test impact

- **D-16:** Comments-only diff per CLAUDE.md "Don't add features, refactor, or introduce abstractions". No production-behavior change is permitted in this phase.
- **D-17:** **Each plan ends with `./mvnw test` (quick) before commit; ONE final `./mvnw verify` at the end of Plan 67-03** per the `feedback_test_call_optimization` memory. JaCoCo coverage cannot regress (Java bytecode lines are unaffected by comment changes).
- **D-18:** **Per-plan diff review gate** — before each plan's commit, the executor runs `git diff --stat` to confirm the change shape (lines deleted >> lines added; no `.java` file shows non-comment changes). Implementation details left to the planner.

### Verification (acceptance criteria for the verifier)

- **D-19:** Quantitative gates after Plan 67-03:
  - `grep -rn "// Phase [56][0-9]" src/main src/test | wc -l` → 0
  - `grep -rn "// per RESEARCH.md\|// per CONTEXT.md\|// per CLAUDE.md\|// per ROADMAP.md" src/main src/test | wc -l` → 0
  - `grep -rn "// gap-[0-9]" src/main src/test | wc -l` → 0
  - `grep -rEn "^[[:space:]]*//[[:space:]]*[-=*#]{20,}" src/main src/test | wc -l` → 0 (or near-0 with documented exceptions)
  - `grep -rn "^	*// given\|^	*// when\|^	*// then" src/test | wc -l` → ≥ 1,899 (BDD markers preserved exactly).
- **D-20:** Behavior-preservation gate: `./mvnw verify` exits 0; tests run count unchanged from pre-phase baseline (1231 from Phase 66 SUMMARY); JaCoCo BUNDLE LINE ≥ 0.82.

### Branch

- **D-21:** Stay on `gsd/v1.9-season-phases-groups`. No branch switch, no stash, no reset. Standard CLAUDE.md branch protection.

### Claude's Discretion

- **D-22:** **Per-file judgement** on borderline Javadoc (e.g., the V5 Flyway migration's history paragraph) — the executor decides per CLAUDE.md *"if removing the comment wouldn't confuse a future reader, don't write it"*. Default toward removal of historical narrative; keep technical contract.
- **D-23:** **Whether to consolidate adjacent kept Javadoc lines** — formatting choice left to executor.
- **D-24:** **Final sweep counts** — exact "noise removed" tally for SUMMARY.md left to executor's tracking.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Repo conventions (binding)

- `CLAUDE.md` § "Doing tasks" — *"Default to writing no comments. Only add one when the WHY is non-obvious: a hidden constraint, a subtle invariant, a workaround for a specific bug, behavior that would surprise a reader. If removing the comment wouldn't confuse a future reader, don't write it."*
- `CLAUDE.md` § "Doing tasks" — *"Don't explain WHAT the code does, since well-named identifiers already do that. Don't reference the current task, fix, or callers ('used by X', 'added for the Y flow', 'handles the case from issue #123'), since those belong in the PR description and rot as the codebase evolves."*
- `CLAUDE.md` § Development Approach — Given-When-Then BDD test markers are MANDATORY (justifies D-06).
- `CLAUDE.md` § Constraints — JaCoCo line coverage ≥ 82 %; no V1 migration changes.
- `CLAUDE.md` § Git Workflow — conventional commits (`refactor:` / `style:` / `docs:` are the right prefixes for this sweep, depending on what dominates each plan).

### Prior cleanup phases (decision lineage)

- `.planning/phases/20-english-messages/` — earlier UI-text + comment cleanup (Phase 20).
- `.planning/phases/21-english-code/` — code comment language sweep (Phase 21).
- `.planning/phases/53-documentation-code-cleanup/` (last v1.6 phase) — documentation cleanup template.
- `.planning/phases/61-cleanup-quality-gate/` — most recent quality-gate phase before this; the comment-policy enforcement that we're now reapplying.

### Phase artifacts

- `.planning/ROADMAP.md` § "Phase 67: Comment Cleanup Re-Sweep" — phase goal, scope (extended to `src/test/java` per user request), out-of-scope (Javadoc on public APIs).
- `.planning/STATE.md` § Roadmap Evolution — entry dated 2026-05-07 with the discovery context (UAT review, BDD-marker preservation rule).

### Project memory anchors

- `feedback_test_call_optimization.md` — informs D-17 (one final `./mvnw verify`, quick `-Dtest=...` between).
- `feedback_grep_all_usages.md` — informs the per-file pass methodology (grep first, then edit).
- `feedback_e2e_verification.md` — `-Pe2e` is UAT-only; not run in this phase's gate.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable assets

- **Conventional grep patterns** for offender detection (already validated by scout):
  ```
  grep -rEn "^[[:space:]]*//[[:space:]]*[-=*#]{20,}" src/main src/test   # decoration
  grep -rn  "// Phase [0-9]+" src/main src/test                         # phase attribution
  grep -rn  "// per RESEARCH\|// per CONTEXT\|// per CLAUDE.md\|// per ROADMAP" src/main src/test   # artifact refs
  grep -rn  "// gap-[0-9]" src/main src/test                            # gap-tracking remnants
  grep -rn  "// fix for #" src/main src/test                            # issue-tracker refs
  ```

### Established patterns

- BDD test structure (CLAUDE.md): `givenContext_whenAction_thenExpectedResult()` method names + `// given` / `// when` / `// then` block comments. **THIS IS THE ONLY ALLOWED INSIDE-METHOD-COMMENT PATTERN IN TESTS.** Anything else inside a test body that re-narrates the code is noise.
- `@Slf4j` + parameterized `log.info("...{}...", arg)` — log statements replace many WHAT comments naturally.
- Thymeleaf parser comments `<!--/*  */-->` are server-stripped — different from `<!-- -->` which is HTML-output. Templates need careful review.

### Integration points

- `./mvnw test -Dtest=<TestClass>` — quick per-file gate for production sweeps that touch a service. Test class naming is `{Service}Test` / `{Controller}Test`.
- `git diff --stat` after each file — confirms diff shape (X+, Y-) and surfaces accidental code changes.
- JaCoCo: comments don't affect bytecode-line coverage. Coverage stays at 0.8561+ baseline (post-Phase-66).

### Heaviest offender concentration (planner sizing)

| File | Decoration `// ---` count |
|---|---|
| `DriverSheetImportServiceTest.java` | 40 |
| `SiteGeneratorServiceTest.java` | 23 |
| `RaceControllerTest.java` | 19 |
| `DriverSheetImportServiceIT.java` | 18 |
| `SeasonPhaseServiceTest.java` | 14 |
| `RaceServiceTest.java` | 11 |
| `V3MigrationTest.java` | 10 |
| `SiteGeneratorE2ETest.java` | 9 |
| `RaceService.java`, `MatchdayService.java` | 9 each |
| `MatchdayServiceTest.java`, `CsvImportServiceTest.java` | 7 each |

These are the top targets in Plans 67-01 (production) and 67-03 (tests).

</code_context>

<specifics>
## Specific Ideas

- **User scope expansion (this session):** `src/test/java` was added explicitly because the user spotted offenders there too. The 1,899 BDD-marker lines must be preserved — they're the project's mandated test structure markers per CLAUDE.md.
- **Auto/chain pipeline:** running under `/gsd-discuss-phase 67 --auto --chain` → auto-advances through plan → execute → verify with no further interactive gating. Plans must be self-sufficient.
- **Recommended-default selections** per `--auto` mode: D-12 file-by-file methodology (vs. blanket regex), D-14 three plans (vs. one big plan or per-package plans), D-15 sequential single-wave (vs. parallel worktrees), D-21 stay-on-current-branch.

</specifics>

<deferred>
## Deferred Ideas

- **CI / pre-commit comment-noise guard** — automated detection (Checkstyle, PMD `CommentSize`, custom regex) is fragile (Javadoc / WHY comments produce false positives). The cleanest defense is the rule already in CLAUDE.md plus periodic re-sweeps. If this becomes a chronic regression, a dedicated "Quality Gate Lock" phase can investigate `pmd:CommentRequired` / `pmd:CommentDefaultAccessModifier` configurations.
- **Javadoc style normalization** — separate from this phase. Some classes use `<p>` / `<ol>` / `<li>` rich Javadoc; others use plain prose. Style alignment is a separate quality concern.
- **License-header policy** — none in repo today; if/when one is adopted, that's a separate phase.
- **Migration-file Javadoc audit** — Flyway Java migrations (V4, V5) have long history-narrating Javadoc. Borderline per D-22; plan-level judgement applies. If preserved, a follow-up phase can move that history to a dedicated `docs/migrations/` notebook.
- **Comment-language audit** — out of scope (Phases 20-21 already English-only; CLAUDE.md restates the rule).
- **Templates HTML-comment audit beyond noise** — semantic review of which `<!-- ... -->` are public-output (visible in browser) vs. server-stripped Thymeleaf comments. If a public-output comment leaks information, that's a security concern for a separate phase.

</deferred>

---

*Phase: 67-comment-cleanup-resweep*
*Context gathered: 2026-05-07*
