# Phase 103: StringUtils Blank-Check Sweep — Context

**Gathered:** 2026-05-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace the manual blank-check pattern `s != null && !s.isBlank()` (~40 occurrences) and `s == null || s.isBlank()` (~46 occurrences) across 43 production-source files with `org.springframework.util.StringUtils.hasText(s)` (and its negation `!hasText(s)`). Pure readability + Spring-Native consistency refactor — **no behavior change, no new functionality, no coverage delta expected**. Aligns with CLAUDE.md "Spring-Native over JDK-Built-In" principle. Confirmed scope (grep, 2026-05-28): 86 hits / 43 files in `src/main/java`, zero hits in `src/test/java`.

**In scope:**
- All `src/main/java` files matching either pattern (43 files; package distribution: domain 11 / discord 9 / admin 9 / sitegen 5 / dataimport 4 / backup 4 / gt7sync 1)
- The DriverService.java:160 method-reference candidate is in scope but its target form depends on D-03 (see Implementation Decisions)

**Out of scope:**
- The ~10 String `.isEmpty()` callsites — different semantics (null-unsafe), case-by-case decision in a separate phase
- Collection / `MultipartFile` / `Optional` `.isEmpty()` — explicitly excluded (different semantics)
- Test sources (`src/test/java`) — zero matches; nothing to do
- CLAUDE.md updates — Spring-Native principle already documented; this phase implements existing convention, not defines new one
- Coverage-bar bump — no behavior change → no new tests
- PR description update, git tag, milestone close — bookkeeping belongs to `/gsd-complete-milestone v1.13`, not this phase

</domain>

<decisions>
## Implementation Decisions

### Plan Granularity
- **D-01:** **Single plan covering all 43 files in one sweep.** No per-package or per-batch plan-split. Rationale: pure mechanical 1:1 substitution, lowest possible behavioral-change risk, maximally coherent diff, one atomic commit at plan close. The planner MAY internally batch by package for executor turn-taking, but the unit of work is one plan with one final commit.

### Refactor Mechanism
- **D-02:** **Hand-edit primary, OpenRewrite as end-of-plan validation gate.** The planner instructs the executor to grep-find and hand-edit each occurrence (1:1 substitution + import-statement addition). At plan close — *before* the final `clean verify -Pe2e` — the executor runs `./mvnw -Prewrite rewrite:dryRun` with a validation-recipe scope to detect any forgotten `s != null && !s.isBlank()` / `s == null || s.isBlank()` survivors in `src/main/java`. Researcher MUST investigate whether (a) an existing OpenRewrite recipe in `rewrite-spring 6.30.4` / `rewrite-migrate-java 3.34.1` covers this swap and (b) if not, what a minimal in-repo validation recipe would look like. Recipe = validation oracle, not the editor of record.

### Import Style
- **D-03:** **Static import: `import static org.springframework.util.StringUtils.hasText;`** — call sites read as `hasText(s)` / `!hasText(s)`. Rationale: densest form, the user explicitly chose this over the class-qualified variant.
- **D-03a:** **DriverService.java:160 stays a lambda, NOT a method reference.** Static import forbids the `StringUtils::hasText` method-reference form (no class qualifier in scope). The roadmap's "method-reference bonus" is therefore voided by D-03 — the line becomes `filter(s -> hasText(s))`, not `filter(StringUtils::hasText)`. Planner MUST surface this in the plan to prevent an executor from "improving" it back to a method-reference (which would require a second class-qualified import on top of the static one).
- **D-03b:** **Co-existing imports are forbidden in the same file.** A file gets EITHER `import static …hasText;` OR (in the no-occurrence case) no import at all. No mixed `import static …hasText;` + `import org.springframework.util.StringUtils;` — that defeats D-03's readability rationale.

### Verify Cadence
- **D-04:** **Targeted tests per executor batch + one `./mvnw clean verify -Pe2e` at phase end.** Inside the single plan, the executor may run `./mvnw test -Dtest=<affected>` (Surefire-targeted, ~30 s) after each file-batch to catch compilation/import regressions cheaply. The phase-end gate is exactly one `./mvnw clean verify -Pe2e` (~10 min full + Playwright) — green is the merge oracle. **Forbidden:** per-batch `clean verify` (CLAUDE.md "No Skip Flags" + cost discipline — 7× full verify would burn ~70 min for zero behavioral risk).

### Edge Cases & Substitution Rules
- **D-05:** **Mechanical-only substitution.** The two literal patterns:
  - `EXPR != null && !EXPR.isBlank()` → `hasText(EXPR)`
  - `EXPR == null || EXPR.isBlank()` → `!hasText(EXPR)`
  - The `EXPR` is the SAME tree on both sides — when the two sides reference different expressions (e.g. `a != null && !b.isBlank()`), the executor MUST skip and surface it as `NEEDS_CONTEXT`. Such cases are not in the 86-hit count and indicate genuinely different intent.
- **D-06:** **`isBlank()` is the ONLY trigger.** `.isEmpty()` on Strings, on Collections, on `MultipartFile`, on `Optional` — none of these get touched. The grep oracle is restricted to `\.isBlank\(\)`.
- **D-07:** **Test files stay untouched.** Zero matches in `src/test/java` (verified). Any incidental match the executor finds in `src/test/java` is to be left as-is and reported as `OUT_OF_SCOPE`.

### Branch & Execution Mode (carried forward — not re-asked)
- **D-08:** Active milestone branch `gsd/v1.13-discord-integration` (HARD-LOCKED per CLAUDE.md "Milestone Branch First"). No per-phase sub-branch.
- **D-09:** Inline-sequential execution (CLAUDE.md "Subagent Rules"). `/gsd-execute-phase 103 --interactive` is the mandatory invocation, not `--auto`.
- **D-10:** Single rolling milestone PR (`gh pr list --head gsd/v1.13-discord-integration` first; PR body update after phase push). No new PR for this phase.

### Claude's Discretion
- Researcher decides whether the OpenRewrite validation lever lives in `pom.xml` `<configuration>` or in a one-off `rewrite.yml`, and whether it's an existing recipe (`org.openrewrite.java.spring.framework.UseStringUtilsHasText`-style) or a minimal custom one. Planner reflects the choice as an explicit task.
- Planner decides the in-plan task ordering (e.g., domain → discord → admin → sitegen → dataimport → backup → gt7sync, or any other deterministic order) within the single-plan scope.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Definition
- `.planning/ROADMAP.md` Phase 103 entry — goal, hotspots, scope, special-case bonus
- `.planning/PROJECT.md` "v1.13 Discord Integration & Carry-Forwards" — milestone context, branch, current state

### Architectural Constraints (project-wide)
- `CLAUDE.md` "Architectural Principles" → "Spring-Native over JDK-Built-In" — the principle this refactor implements (RestClient, MultipartBodyBuilder examples; same reasoning extends to `StringUtils.hasText` over manual null-and-blank)
- `CLAUDE.md` "Build & Test Discipline" — clean-build authority, no-skip-flags, end-of-phase verify rule (drives D-04)
- `CLAUDE.md` "Subagent Rules" — inline-sequential default for `/gsd-execute-phase` in v1.13+ (drives D-09), file-clobber prevention on shared files, plan-adherence whitelist
- `CLAUDE.md` "GSD Workflow Discipline" → "Plan Quality Gates" — endpoint/symbol existence check, test-impact section requirement (planner must enumerate test callsites that exercise the refactored files, even though no test edits are expected)
- `CLAUDE.md` "Git Workflow" → "Milestone Branch First" — no per-phase branch (drives D-08), single rolling PR (drives D-10)
- `CLAUDE.md` "Conventions" → "No Comment Pollution" — refactor MUST NOT add phase/plan markers in source or test comments
- `CLAUDE.md` "Memory-Aware Subagent Dispatch" — orchestrator hand-carries relevant memory entries into subagent prompts

### Build Tooling
- `pom.xml` `<plugins>` section — OpenRewrite `rewrite-maven-plugin 6.39.0` with `rewrite-spring 6.30.4` + `rewrite-migrate-java 3.34.1` already configured (relevant for D-02 recipe discovery)

### Prior Phase Continuity
- `.planning/phases/102-*/102-SUMMARY.md` — Phase 102 close-loop pattern (info-sweep + warn-and-persist + activeRoute migration); relevant only for branch state, not scope
- `.planning/STATE.md` — current position, completed-plan tally, milestone status

[No new ADRs / specs are introduced by this phase — it is convention-application, not convention-definition.]

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `org.springframework.util.StringUtils` — already on classpath (Spring Boot 4 core). Zero existing usages of `StringUtils.hasText` in `src/main/java` confirmed by grep — this phase introduces it everywhere.
- OpenRewrite plugin already wired in `pom.xml` (`-Prewrite` profile) → `rewrite:dryRun` is available without further setup (D-02 validation gate).

### Established Patterns
- Spring-Native preference is the documented convention (`CLAUDE.md` "Spring-Native over JDK-Built-In" — `RestClient` over `java.net.http.HttpClient`, etc.). This phase extends the same reasoning to blank-check utility selection.
- File-by-file refactor with hand-edit + per-batch `mvn test -Dtest=…` is the established v1.11/v1.13 cadence for mechanical sweeps (see Phase 91/92/102 patterns).
- The single-plan-per-phase form is precedented for pure-refactor phases (Phase 102-03 info-sweep used a comparable structure).

### Integration Points
- **DiscordPostService.java (17 hits)** — fresh code from v1.13 Phases 93–98. Highest concentration. Planner should foreground this file in the executor task list so any regression surfaces early in the verify run.
- **DiscordDevSeeder.java (10) / DiscordRateLimitInterceptor.java (6)** — also v1.13 Discord-package files; same early-foreground reasoning.
- **RaceSettings.isComplete() (5 hits in one method)** — domain model, called from scoring + sitegen. Verify run must exercise scoring paths (Surefire `RaceSettingsTest` + Failsafe scoring ITs).
- **DriverService.java:160 + DriverService.java:129** — confirmed by line-grep. The `:160` line gets the lambda treatment per D-03a; `:129` is a standard `== null || .isBlank()` → `!hasText(…)` rewrite.

### Hotspot Inventory (planner foreground list)
- `DiscordPostService.java` — 17
- `DiscordDevSeeder.java` — 10
- `DiscordRateLimitInterceptor.java` — 6
- `RaceSettings.isComplete()` (RaceSettings.java) — 5
- 39 further files with 1-3 occurrences

</code_context>

<specifics>
## Specific Ideas

- The user explicitly chose **static import + dense `hasText(s)` call sites** over the class-qualified `StringUtils.hasText(s)` form (D-03). This is a readability decision — the planner and code-reviewer should not interpret the static-import choice as casual or accidental.
- The user explicitly chose **OpenRewrite as a post-hand-edit validation oracle** rather than as the primary refactor mechanism (D-02). The reasoning is determinism *combined with* visual human review on each substitution — neither tool alone satisfies the bar.
- The user explicitly chose **one plan, one final commit, one phase-end verify** (D-01 + D-04). No granular per-package commits, no per-package verify. The phase is treated as a single mechanical unit of work.

</specifics>

<deferred>
## Deferred Ideas

- **String `.isEmpty()` audit (~10 hits).** Different semantics from `.isBlank()` (no whitespace-trim, but also no null-safety). Case-by-case decision — each callsite may want `hasText`, may want `isEmpty`, may want `Optional`. Belongs in its own phase, post-v1.13 (capture for v1.14 backlog).
- **Method-reference bonus form (`filter(StringUtils::hasText)`).** Voided by D-03's static-import choice. If a future readability pass reverts to class-qualified imports, the method-reference bonus could be revisited then.

</deferred>

---

*Phase: 103-stringutils-blank-check-sweep-replace-manual-null-and-isblan*
*Context gathered: 2026-05-28*
