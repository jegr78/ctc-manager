# CTC Manager

Community Team Cup — Gran Turismo Racing League Manager

---

## Language

* **Communication:** German
* **Documentation, Code, Comments, and UI Texts:** English

## Constraints

* **Test Coverage:** Minimum 82% line coverage must be maintained.
* **Flyway:** Do not change the existing V1 migration; only new V2+ migrations.
* **Profiles:** Auth only for `prod`/`docker`; `dev`/`local` remains without auth.
* **OSIV:** Remains enabled — only use `@EntityGraph` annotations for optimization.
* **Backward Compatibility:** No breaking changes to existing URLs/endpoints.
* **Playwright:** Remains a compile-scope dependency (Runtime usage for graphics).

## Technology Stack

* **Runtime:** Java 25 (Eclipse Temurin), Maven via `./mvnw`, Spring Boot 4.x
* **DB:** MariaDB (prod/local/docker), H2 (dev/test), Flyway Migrations
* **UI:** Thymeleaf (server-side rendering), no frontend build tool
* **Testing:** JUnit 5, Mockito, Playwright (E2E + Graphic generation)
* **External APIs:** Google Sheets (Race Import), Google Calendar, Jsoup (GT7 Scraping)
* **Build:** Surefire (Unit/Integration), Failsafe + `-Pe2e` (E2E), JaCoCo (Coverage)

## Commands

```bash
# Start Dev Mode
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Dev Mode with GT7 demo data (cars, tracks, images)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo

# Run tests (Unit + Integration + JaCoCo Coverage)
./mvnw verify

# Run tests including Playwright E2E
./mvnw verify -Pe2e

# OpenRewrite: preview recipe-driven refactoring (no file changes)
./mvnw -Prewrite rewrite:dryRun

# OpenRewrite: apply recipes to source files in place
./mvnw -Prewrite rewrite:run

# Open Coverage Report
open target/site/jacoco/index.html

# Local with MariaDB
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Install Playwright Chromium (for Team Card generation + E2E tests)
./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"

# Docker: Local environment (App + MariaDB)
docker compose up --build -d
docker compose down

# Docker: Build image only
docker compose build

# Docker: Production (external DB, configure .env)
docker compose -f docker-compose.prod.yml up -d
```

## Spring Profiles

* `dev` — H2 In-Memory, Port 9090 (Development, Testing)
* `dev,demo` — Same as `dev`, imports all GT7 cars and tracks (with images) on startup for manual testing.
* `local` — Local MariaDB, Port 9091
* `docker` — MariaDB within the Docker network (Host `db`), Port 8080
* `prod` — Cloud DB (Environment Variables)

## Architectural Principles

* **Core Value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.
* **Keep Controllers Thin:** Controllers are only responsible for HTTP handling (receiving requests, calling services, filling models/redirects/flash attributes). No business logic or direct repository access in controllers. Business logic belongs in service classes (`domain.service` or `admin.service`).
* **DTOs instead of Entities in Controllers:** Always bind form inputs (POST/save) via Form DTOs (`admin.dto`), never JPA entities directly via `@ModelAttribute` — protection against Mass Assignment. For template display (GET), entities may be passed to the model (OSIV is active).
* **No Fallback Calculations:** If derived data is missing, do not implement workarounds in templates or controllers. Instead, analyze the data model and service architecture and fix the root cause — data must be written consistently in the correct place.
* **Keep Thymeleaf Templates Lean:** No complex logic (SpEL expressions, collection projections, nested conditions) in templates. Calculations and data preparation belong in the service — templates are for presentation only.
* **No Inline Styles on Buttons:** Instead of `style="..."` on `.btn` elements, always use CSS classes from `admin.css` (`btn-xs`, `btn-sm`, `btn-lg`, `btn-tab`). When refactoring inline styles to CSS classes, always check JavaScript that sets `element.className = '...'` — the new classes must be added there as well.
* **Isolate Test Data Completely:** E2E test data in `TestDataService` must use separate entities with a test prefix (e.g., `T-ALF`, `Test_Alpha_1`, `Test-Season 2026`). Never use real teams, drivers, or seasons for automated tests — these collide with manual tests on imported data.
* **Tag Tests by Category (`@Tag`):** Surefire/Failsafe routing is `@Tag`-based, not filename-based. Every new test class MUST be tagged: `@Tag("integration")` for `*IT.java` (Spring-context ITs), `@Tag("e2e")` for Playwright tests in `org.ctc.e2e.*`. Plain unit tests stay untagged. `@Nested` inner classes inherit their parent's tag. See `.planning/codebase/TESTING.md` "Test Categorization (`@Tag`)" for the full convention. An untagged `*IT.java` will silently run in Surefire under the wrong fork config and may race on shared state.
* **RaceLineup is Source of Truth:** For driver-team assignments (especially sub-teams), always prioritize `RaceLineup`; use `SeasonDriver` only as a fallback for seasons without races. The CSV import determines the correct assignment.
* **Score Aggregation on Result Save:** After every `raceRepository.save(race)` that wrote results (controller, import, or service), call `scoringService.aggregateMatchScores(race)`. Standings depend on aggregated scores on `Match`/`PlayoffMatchup` — never recompute in templates or controllers.
* **Spring-Native over JDK-Built-In:** In this Spring Boot stack, prefer Spring abstractions over JDK-only equivalents: `RestClient` (not `java.net.http.HttpClient`), `MultipartBodyBuilder`, `ClientHttpRequestInterceptor`. Auto-config, observability hooks, and bean lifecycle are already wired — do not build parallel wrappers. JDK `HttpClient` is acceptable only outside Spring context (e.g., standalone CLI tools).
* **Grep All Usages Before Refactor:** Before renaming/changing a method, repository call, or pattern, `grep -rn` the entire `src/` for the symbol AND for structurally similar copies (e.g., `getSeasonDrivers().stream()`-style patterns). Phase plans must include an explicit "audit all usages" task — files_modified lists from planners regularly miss copies.
* **Do Not Modify Flyway Migrations:** Existing `V*__*.sql` files must never be changed after release (Flyway checks checksums). Schema changes must always be a new migration file: `V{N}__{short_description}.sql` (snake_case, English). Maintain H2 + MariaDB compatibility.

## OSIV (Open Session in View)

Deliberately enabled (`spring.jpa.open-in-view=true`). The Hibernate session remains open until the end of the HTTP request so that Thymeleaf can render lazy-loaded fields. This is appropriate for this admin application using server-side rendering — no lazy-init workarounds in controllers are necessary.

## Development Approach

* **TDD (Test-Driven Development):** Write tests first, then implementation. Red → Green → Refactor.
* **BDD (Behavior-Driven Development):** Playwright E2E tests describe expected behavior from the user's perspective.
* **Test Naming (Given-When-Then):** All test methods follow the BDD pattern:
  * Method name: `givenContext_whenAction_thenExpectedResult()`
  * Body: `// given` / `// when` / `// then` comments for structuring.
  * For simple tests without preconditions: `whenAction_thenResult()` is allowed.
  * For exception tests: `// when / then` combined for `assertThatThrownBy`.
* **Feature Sequence:** Unit Tests → Implementation → Integration Tests → E2E Tests.
* Use Superpowers skill `superpowers:test-driven-development`.
* **Visual Verification with `playwright-cli`:** For UI changes (templates, CSS), always use `playwright-cli` to verify results visually. Start the dev server (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`), then inspect affected pages with `playwright-cli open http://localhost:9090/...` (Desktop + Mobile). Skill: `/playwright-cli`.
* **Screenshots go to `.screenshots/`:** `playwright-cli screenshot --filename=.screenshots/<name>.png`. The folder is `.gitignore`'d. Never let screenshots land in the repo root.

### Build & Test Discipline

* **Clean Maven Build is the Source of Truth.** IDE caches (VS Code Java Language Server / Eclipse JDT, IntelliJ `out/`) are NOT a legitimate truth source. If `./mvnw verify` reports a compile error, the first diagnostic step is always `./mvnw clean test-compile` — never edit source first. The string `"Unresolved compilation problem"` in Maven output is an Eclipse JDT signature (NOT javac) and means stale cache until proven otherwise.
* **Always `./mvnw clean verify -Pe2e` for full runs.** Never `./mvnw verify` without `clean` — stale `target/` artifacts (JDT bytecode, `jacoco.exec`, surefire-reports) produce spurious failures and skewed coverage. The recompile costs ~10s; a false-positive Failsafe rerun costs 7+ minutes.
* **No Skip Flags, No Direct Plugin Goals.** Forbidden: `-DskipTests`, `-Dsurefire.skip=true`, `failsafe:integration-test` standalone, `mvn surefire:test` separately. The Maven lifecycle (`clean verify` / `clean verify -Pe2e`) is the only authoritative test run.
* **Targeted Tests during TDD-Red/Green:** Use `-Dtest=ClassName` (Surefire) or `-Dit.test=ClassName -DfailIfNoTests=false` (Failsafe) for tight loops. One single `./mvnw clean verify -Pe2e` at phase end — not per plan, not per task.
* **End-of-Phase Verification:** `./mvnw verify -Pe2e` (including Playwright) before any commit or PR — Unit/IT alone is insufficient.
* **No Flaky Dismissal.** If a test that was green in the previous phase's VERIFICATION.md fails now, it is a REGRESSION caused by current changes — never "pre-existing flaky" or "out of scope" or "backlog". "Flaky" is only valid after a repeated-run-without-own-changes (`git stash` + isolated run) shows inconsistency. "Pre-existing" only after a pre-phase commit shows the same failure. Until proven, investigate immediately.
* **Entity Refactoring Order:** Big entity changes (removing/moving fields) break `DevDataSeeder`/`TestDataService` first — that blocks all `@SpringBootTest`. Always: (1) update seeders, (2) add transitional convenience methods on entities, (3) refactor callers incrementally via TDD.
* **WireMock is not Real-API Coverage.** Any regex/payload/URL-pattern change in external-API code (e.g., Discord) requires a separate unit/IT test pinning the **production** format — not the WireMock-friendly one. WireMock stubs must use query-param assertions (`withQueryParam(...)`), not just `urlPathEqualTo`. For auto-post hooks: never `@MockitoBean` the post service in transactional ITs — the real Spring `@Transactional` proxy must run, or transaction-propagation bugs hide. DB constraints (Unique, FK, Check) need IT against H2/MariaDB, not mocked repositories. See memory `feedback_wiremock_vs_real_api.md` for the five Phase-95 bugs that drove this rule.

## Code Coverage (JaCoCo)

* **Minimum:** documented in `pom.xml` (currently 82% line coverage).
* **Report:** `target/site/jacoco/index.html` after running `./mvnw verify`.
* **CI:** Automatic PR comment with coverage via `madrapps/jacoco-report`.
* **Adjusting Threshold:** Measure first (`jacoco.csv`), then set the minimum — never guess optimistically.
* **Excluded from coverage:** Playwright-dependent services (TeamCardService, LineupGraphicService, ResultsGraphicService, SettingsGraphicService, OverlayGraphicService, MatchResultsGraphicService, PowerRankingsGraphicService, AbstractGraphicService) — runtime-Playwright cannot run under JaCoCo instrumentation.
* **Controller-Test Fixture:** Use `TestHelper.createFullSeasonFixture()` for controller tests that need complex seed data; do not roll bespoke fixtures.

## Git Workflow

* **Default Branch:** `master`
* **Tooling:** `gh` CLI for all GitHub operations (PRs, Issues, etc.).
* **Branching:** Create a separate branch for every feature/fix.
  * Naming: `feature/<short-description>` or `fix/<short-description>`.
  * Always fetch before branching: `git fetch origin && git checkout -b <branch> origin/master`.
  * Never branch from a local `master` that may be behind remote.
* **Milestone Branch First.** New GSD milestones get a dedicated `gsd/v<X>-<slug>` branch BEFORE any planning commits land. `PROJECT.md`'s footer `Branch:` field is authoritative — every planning, discuss, plan, AND execute commit goes to that branch. NEVER create per-phase `feature/...` sub-branches inside a milestone, even if a plan file or CONTEXT.md from earlier phases suggests one (those are historical errors, not precedents). Branch strategy inside an active milestone is HARD-LOCKED — never expose it as an `AskUserQuestion` option, never accept a plan-file override. Hot-fix or genuinely isolated parallel workstreams (outside the milestone sequence) are the only exception.
* **Pull Requests:** Always merge changes via PRs into `master`; no direct pushes.
  * `gh pr create --assignee jegr78` to create (always assign to jegr78).
  * `gh pr merge --squash --subject "<type>(<scope>): <description> (#<PR>)"` — the squash subject MUST be a Conventional Commit (`feat:`, `fix:`, `chore:`, etc.), otherwise Semantic Release does not fire. PR title alone is NOT enough.
  * After merge, clean up local branch: `git switch master && git pull && git branch -d <branch>`.
* **Milestone PR Already Exists.** From milestone start, a single PR tracks the milestone branch. NEVER propose or run `gh pr create` without first running `gh pr list --head <milestone-branch>`. After every phase push, update the PR body via `gh pr edit <num> --body "..."` so it functions as a rolling summary (phase list with ✓ markers, coverage/test-count deltas, key outcomes) — not a Branch-start snapshot. A repository PreToolUse hook (`.claude/hooks/check-pr-exists.sh`) enforces this; the rule still applies when the hook is bypassed.
* **No Local Git Tags.** Never run `git tag -a v…` or `git push origin v…`. The release CI workflow tags after merge — a pre-pushed tag collides with the CI tag and breaks the merge CI run. `/gsd-complete-milestone`: skip the `git_tag` step even if the skill template suggests it. Cleanup if an accidental tag landed: `git push --delete origin <tag>` AND `git tag -d <tag>` (both, otherwise the next push resurrects it).
* **Sequential PR Merges & Rebase.** When dependent PRs squash-merge in sequence, later branches need `git fetch origin && git rebase origin/master` before merge. Push with `--force-with-lease`, never `--force`. Prefer branching all PRs from master (no inter-PR dependency) when possible.
* **Commits:** English Conventional Commits. Full type list (feat/fix/docs/chore/refactor/test/style/perf/ci + `BREAKING CHANGE` footer) and format spec live in [`.gitmessage`](.gitmessage) — Single Source of Truth, also serves as IDE commit-dialog template. Activate locally once via `git config commit.template .gitmessage` so IntelliJ / VS Code / `git commit` (no `-m`) pre-fill the cheat-sheet. Squash-merge subjects MUST follow the same format — PR title alone does NOT trigger Semantic Release.
* **Before PR:**
    1. Ensure tests pass locally with `./mvnw verify`.
    2. Perform a code review of your own changes (`superpowers:code-reviewer`).
    3. Fix found issues and re-test.
* **After PR:**
    1. Check CI build: `gh run list --branch <branch>` / `gh run view <run-id>`.
    2. In case of CI failure: Analyze logs (`gh run view --log-failed`), fix, and push.
    3. PR may only be merged once CI is green.

## Subagent Rules

* **Inline Sequential is the Default for `/gsd-execute-phase`.** Do NOT spawn `Agent(subagent_type="gsd-executor", isolation="worktree", ...)` for plan execution. Work inline on the active milestone branch — read the plan, edit/write directly, atomic-commit per task, write SUMMARY.md, update STATE.md via `gsd-sdk query`. This holds even without an explicit `--interactive` flag. Subagent worktrees in v1.10/v1.11/v1.13 caused branch drift, partial completions, file clobber, and stream-idle timeouts. Read-only researcher agents (Explore, code-explorer) remain allowed. Only spawn writing subagents on explicit user request ("nutze Worktree", "spawne parallele Agents").
* **`--chain` → `execute-phase --interactive`.** When chaining `/gsd-plan-phase` into `/gsd-execute-phase` inside a milestone that locks "sequential inline" (e.g., v1.13 CONTEXT D-05), pass `--interactive`, NOT the default `--auto --no-transition`. The auto-flow uses parallel-wave subagents and violates inline-sequential.
* **Model Selection:** Implementation subagents must use `model: "opus"` or at least `model: "sonnet"`. Haiku is ONLY for read-only tasks (reviews, research). Never for code changes.
* **Branch Protection:** Every subagent prompt must name the active milestone branch and explicitly forbid: no `git stash`, `git checkout`, `git reset`, and no branch switching. Plan files / RESEARCH.md / CONTEXT.md from earlier phases that name a different branch are NOT authoritative — ignore them. This applies to read-only researcher agents too (Phase 68 had a Sonnet researcher silently switch branches during `WebFetch`).
* **Post-Dispatch Validation:** Immediately after EVERY subagent, check: `git branch --show-current`, `git log --oneline -3`, `git diff --stat BASE..HEAD`. If there is a branch or scope discrepancy, immediately `git reset --hard` to the last good commit.
* **Plan Adherence + Scope Whitelist:** Subagent prompts must explicitly state: "Implement ONLY Task N. If other files need adjustment, report `NEEDS_CONTEXT` instead of fixing them yourself." After return, match `git diff --name-only base..HEAD` against the plan's `files_modified` whitelist; revert out-of-scope files automatically.
* **No File Clobber on Shared Files.** When a plan touches a shared file (e.g., `admin.css`, `application.yml`, large templates) that earlier plans also modified, the subagent prompt MUST say: "APPEND your new content after existing content — do NOT rewrite or replace existing sections." Spot-check size (`wc -l`) after merge; Phase 11 lost 73 CSS classes (551 lines) because of a single rewrite.
* **Phase-Overwrite Prevention.** Each phase preserves prior-phase deliverables: (1) leave guard tests that fail if violated; (2) run `./mvnw verify` before starting phase N+1; (3) phase plans MUST list "Do-Not-Touch" areas; (4) executor prompts repeat prior-phase constraints; (5) when modifying a file an earlier phase in the same milestone created or changed, mark it as a risk in the plan.
* **Atomic Tasks:** Tasks in the plan must be individually executable. If a change forces multiple tasks, plan them as a single task.
* **Inline-Mode-on-Retry:** If a plan fails once via subagent dispatch, retry inline — never spawn another subagent for the same plan_id. Phase 60 60-03 and 60-05 confirmed this.
* **Fallback:** If subagents cause issues despite rules, process tasks sequentially yourself.

## GSD Workflow Discipline

* **Wave-Pause for User Feedback.** During `/gsd-execute-phase`, after every wave merge + post-merge test gate, STOP and wait for user feedback before starting the next wave — even in auto-mode. Present wave results (plan-IDs, tests, deviations). The manual stop overrides auto-advance.
* **In-Milestone Polish — No Deferral Across Milestones.** UI/UX debt discovered mid-milestone MUST close within the same milestone, even when not in the original phase scope. Capture it in `.planning/STATE.md` Deferred Items AND add a success criterion to a still-open phase in the same milestone's `v<X>-ROADMAP.md`. "Later in v<X+1>" is forbidden. Pre-existing debt from prior milestones (UAT carryovers, post-deploy operator actions) may cross milestone boundaries — current-milestone discoveries may not.
* **Frustration ≠ Approval.** An open `AskUserQuestion` stays open until the user explicitly clicks an option. Frustration replies, vague follow-ups, or topic-changes are NEVER auto-approve. If the user's answer doesn't directly address the question, re-ask with clearer wording — do not pick a default path. Never collapse two threads ("user is frustrated about speed" + "visual checkpoint open") into one ("auto-approve and go faster").
* **Architecture-Decision vs. Process-Gate.** Architecture decisions (multiple OK answers, reversible, low blast-radius): pick a sensible default with a one-line rationale in commit/SUMMARY, no `AskUserQuestion`. Process gates (visual approval, destructive op, plan-scope violation): ALWAYS block with `AskUserQuestion`, never default.
* **SUMMARY-Owner Defined Upfront.** Decide per plan spawn: `autonomous: true` → agent writes SUMMARY (mandatory in prompt: "SUMMARY.md MUST be committed before you return"). `autonomous: false` with visual checkpoint → orchestrator writes after explicit user approval. `autonomous: false` purely technical → agent writes. Never "agent writes half, orchestrator finishes."
* **Plan Quality Gates (Pre-Execution Audits).** The planner agent MUST run four audits before a plan is accepted:
  1. **Endpoint / Symbol Existence:** every referenced endpoint, service method, CSS class, DTO field — `grep -rn` to verify it exists, OR add an explicit creation task.
  2. **Visual Quality Bar:** UI plans need a `<visual_reference>` block (reference selector, expected weight/padding/state, acceptance criterion as side-by-side screenshot).
  3. **NOT-NULL / Constraint Audit:** DTO-slim or save-signature changes must check entity `@JoinColumn(nullable=false)` AND Flyway NOT NULL — output is an explicit migration task, not an implicit follow-up hotfix.
  4. **Test-Impact Section:** "Remove deprecated method" / "Refactor signature" plans must enumerate test callsites, migration patterns, Mockito strict-mode risks, and bridge-only tests to delete.
* **gsd-auto-uat for UI-Heavy Verification.** For phases with UI-heavy UAT (template render-smoke, new buttons/modals, CSS changes, public-site pages), actively propose `/gsd-auto-uat <phase>` instead of manual playwright-cli. Skip when UAT requires cross-source value reconciliation (the skill doesn't compare).
* **Code-Review Before New Phase / Milestone Close.** Every suggested "next steps" block at the end of a phase MUST check whether the just-completed phase has `*-REVIEW.md`. If missing, `/gsd-code-review <prev-phase>` is the primary recommendation, listed above the next-phase planning command (`/gsd-discuss-phase`, `/gsd-plan-phase`, `/gsd-execute-phase`). The same check applies to `/gsd-progress` route, subagent SUMMARY-owner outputs, and any orchestrator-authored "what's next?" prose. At milestone close, `/gsd-complete-milestone <milestone>` is BLOCKED until every phase in the milestone has a REVIEW.md — propose `/gsd-code-review <range>` (e.g. `92-101`) over reviewless phases first, then a fix phase (per "In-Milestone Polish"), then milestone close. This rule is also reflected in `/gsd-complete-milestone`'s own preconditions; if the skill suggests close without naming reviewless phases, the orchestrator overrides and proposes the review pass first. Subagents writing SUMMARY.md must surface "REVIEW.md missing" as an explicit next-step recommendation when applicable.

## Memory-Aware Subagent Dispatch

Before ANY subagent dispatch in a GSD workflow (`gsd-planner`, `gsd-executor`, `gsd-code-reviewer`, `gsd-verifier`, etc.), the orchestrator MUST:

1. Scan the orchestrator's auto-memory index (`MEMORY.md` in the Claude Code project-memory directory) for entries relevant to the task (UI work → no-inline-styles + playwright + screenshots; tests → clean-build + no-flaky; refactor → grep-all-usages; etc.).
2. Quote the relevant 2–4 memory entries verbatim in the subagent prompt under a `**User Conventions (from memory):**` heading.
3. Include the active milestone branch and the inline-sequential / no-branch-switch invariants.

The reason: subagents do NOT read `MEMORY.md`. Only CLAUDE.md (which they always read) and explicit prompt content reach them. Memory entries that have not yet been promoted to CLAUDE.md must be hand-carried by the orchestrator on every dispatch.

## References

* Design Spec: `docs/superpowers/specs/2026-03-26-ctc-manager-design.md`
* Scoring/Legs Spec: `docs/superpowers/specs/2026-03-29-scoring-legs-design.md`
* Release Management Spec: `docs/superpowers/specs/2026-04-03-release-management-design.md`
* Architecture: `.planning/codebase/ARCHITECTURE.md`
* Conventions: `.planning/codebase/CONVENTIONS.md`
* Stack: `.planning/codebase/STACK.md`
* Structure: `.planning/codebase/STRUCTURE.md`
* Testing: `.planning/codebase/TESTING.md`
* SAST Acceptance: `docs/security/sast-acceptance.md`
* SAST Workflow: `.github/workflows/codeql.yml`
* Orchestrator Memory: Claude Code's auto-memory `MEMORY.md` (project-scoped; lives outside the repo). Subagent dispatch must hand-carry relevant entries — see "Memory-Aware Subagent Dispatch".

---

## Configuration

* **Profile-specific:** `application-{dev,local,docker,prod}.yml`
* **Prod Env Vars:** `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `GOOGLE_CALENDAR_ID`
* **Google Credentials:** `google.sheets.credentials-path` (default: `google-credentials.json`)
* **Upload Dir:** `app.upload-dir` (default: `data/dev/uploads`)
* **Site Output:** `ctc.site.output-dir` (default: `docs/site`)

## CI/CD

* **Push/PR to `master`:** Build, Tests, Playwright E2E, JaCoCo Coverage PR comment.
* **Push `docs/site/**`:** GitHub Pages Deployment.
* **Docker:** Multi-stage Dockerfile (JDK build, JRE runtime), non-root user `ctc`, healthcheck `/actuator/health`.

---

## Conventions

### Naming Patterns

* **Layers:** `org.ctc.domain.model`, `org.ctc.domain.repository`, `org.ctc.domain.service`, `org.ctc.admin.controller`, `org.ctc.admin.dto`, `org.ctc.admin.service`.
* **Entities:** Singular nouns, PascalCase (`Season`, `RaceScoring`).
* **Repositories:** `{Entity}Repository` — Services: `{Domain}Service`.
* **Controllers:** `{Entity}Controller` — DTOs: `{Entity}Form` (form), `{Entity}Dto` (display).
* **Methods:** camelCase, verb-first (`calculatePoints()`).
* **Booleans:** `isSubTeam()`, `isActive()`.
* **DB:** Plural snake_case tables (`seasons`), snake_case columns (`created_at`).

### Lombok Usage

* **Entities:** `@Getter @Setter @NoArgsConstructor`, `@ToString(exclude = ...)`, extend `BaseEntity`.
* **Services/Controllers:** `@RequiredArgsConstructor` (constructor injection via `final`), `@Slf4j`.
* **Annotation Order:** On Spring components use `@Slf4j @Component @RequiredArgsConstructor` (alphabetical — `@Slf4j` first).

### Controller & DTO Patterns

* Form DTOs with `@Valid` + `BindingResult`, entities directly in GET (OSIV active).
* Flash attributes: `"successMessage"` / `"errorMessage"`.

### Logging

* `log.info()` for state changes, `log.debug()` for calculations.
* Always use parameterized `{}` format.

### CSS Guidelines

* Use CSS classes from `admin.css` instead of inline styles: `btn-xs`, `btn-sm`, etc.

### No Comment Pollution

* **Default: no comments.** Code is self-explanatory via naming.
* **Hard-banned in source files (Java, SQL migrations, Thymeleaf templates, YAML, tests):**
  * Phase / Plan / Task / UAT / Wave references (e.g. `Phase 94 V11 UAT-04 follow-up:`, `Plan-94-04 fix:`, `// Wave 2 closeout`). They rot — use git history and PR descriptions instead.
  * File-header comment blocks restating what the file does or repeating conventions (e.g. `Compatible with H2 + MariaDB`, `DO NOT mutate this file after release`). Conventions belong here in CLAUDE.md once, not in every file.
  * `Added for X`, `used by Y`, `called from Z` cross-references — they are greppable.
  * Multi-line Javadoc on obvious getters/setters or one-line methods.
* **Allowed (rare):** single-line comments for non-obvious WHY — hidden constraints, subtle invariants, or workarounds for a specific external bug.
* **When refactoring, remove pollution from touched files.** Do not preserve it "to stay consistent with the file's existing style" — that calcifies the anti-pattern.
* **Applies equally to subagents.** Every subagent prompt that writes code must reference this rule.

### Documentation Maintenance

* **Every feature release updates three places:** `CLAUDE.md` (project-internal conventions), `README.md` (public-facing overview), and the GitHub Wiki (user-facing docs). Feature commits that touch behavior, commands, or configuration must consider all three. The repo CLAUDE.md alone is insufficient.

### Shell Quoting & GitHub CLI

* **zsh interprets `[]` as a glob.** `gh api -F field[]=value` produces `zsh: no matches found` and the command silently doesn't run. Preferred: pass JSON via stdin heredoc — `gh api -X PUT repos/... --input - <<'EOF' { "..." } EOF`. Fallback: single-quote the entire value (`-F 'contexts[]=build'`) or escape (`\[\]`) or `noglob gh api ...`. This applies to any CLI with bracket-array syntax (`hub`, `curl --data-urlencode`).

### Skill Invocation Naming

* **Canonical prefix:** GSD skills are invoked via the dash form `/gsd-<name>`. Examples: `/gsd-plan-phase`, `/gsd-execute-phase`, `/gsd-validate-phase`, `/gsd-verify-work`, `/gsd-new-milestone`, `/gsd-discuss-phase`, `/gsd-research-phase`.
* **Deprecated prefix:** the pre-2026 colon-form prefix is no longer recognised. Replace any historical reference in active planning files with the dash form on sight; archived `.planning/milestones/v*.x-*.md` is left untouched (historical immutability).
* **Regression fence:** active top-level planning files (`.planning/PROJECT.md`, `.planning/STATE.md`, `.planning/ROADMAP.md`, `.planning/REQUIREMENTS.md`, `.planning/MILESTONES.md`, `.planning/RETROSPECTIVE.md`) must contain zero literal colon-form references. Treat any literal colon-form occurrence in those six files as a regression to fix before merging.

### Static Analysis (SpotBugs + find-sec-bugs)

* **Gate:** `spotbugs-maven-plugin` 4.9.8.3 + `findsecbugs-plugin` 1.14.0 run on every `./mvnw verify` (Medium+HIGH findings block the build). No separate CI job — SpotBugs runs inside the existing `verify` step.
* **Suppressions:** Live in `config/spotbugs-exclude.xml`. Every `<Match>` entry MUST have an XML rationale comment with a code-cross-reference to where the intentional pattern lives. No `@SuppressWarnings("all")` ever — use targeted `@SuppressFBWarnings({"SPECIFIC_CODE"}, justification="...")` in source or a `<Match>` entry in the filter file.
* **`lombok.config` invariant:** `lombok.config` at project root sets `lombok.extern.findbugs.addSuppressFBWarnings=true`. Do NOT remove or modify the two SpotBugs-related lines without a new phase that re-baselines suppressions — removing them re-introduces ~40–80 `EI_EXPOSE_REP*` false positives from Lombok-generated entity getters.

### CodeQL SAST (Code Scanning)

* **Gate:** `github/codeql-action@v4` runs on push to `master`, on pull_request against `master`, and on Sunday 02:00 UTC cron via `.github/workflows/codeql.yml` (language `java-kotlin`, query suite `security-extended`). The inline-bash SARIF-diff gate-step fails the workflow on the PR job when a new alert with `security-severity >= 7.0` (HIGH or CRITICAL, CVSS-aligned) appears on the head branch but not on the base branch. The weekly cron run skips the gate-step (drift detection only — alerts surface in the Security tab, no red runs).
* **Suppressions** live in `.github/codeql/codeql-config.yml` `query-filters`. Every suppressed finding requires a `// CodeQL FP: <rule-id> — <reason>; see docs/security/sast-acceptance.md` source marker on the line directly above the protected method or block, AND a matching table row in `docs/security/sast-acceptance.md`. UI dismissals are equally valid but MUST also be reflected in `sast-acceptance.md` (Update-on-Triage discipline — partial-writes are forbidden).
* **Acceptance doc** at `docs/security/sast-acceptance.md` is the single source of truth for SAST triage decisions: per-pattern sections (SSRF | ZIP-Slip | BCrypt-N/A | Others), per-finding rows with Alert-ID + Rule + Location + Bucket + Rationale + Source-Marker. Every suppression PR MUST include a parallel `sast-acceptance.md` edit. The acceptance doc is the human-readable counterpart to the machine-readable `codeql-config.yml`.

### Checkstyle (Unused-Import Gate)

* **Gate:** `maven-checkstyle-plugin` (Checkstyle core 13.5.0, overridden for Java 25) runs on every `./mvnw verify` — bound to the `validate` phase via `config/checkstyle.xml` with `failOnViolation=true` at severity `error`, no opt-in profile, locally and in CI. The ruleset is intentionally minimal: only `UnusedImports` (with `processJavadoc=true`, so `{@link}`/`@see` references count as usage) and `RedundantImport`. It covers BOTH `src/main/java` and `src/test/java` (`includeTestSourceDirectory=true`). Broader style checks (naming, whitespace, Javadoc-presence) are deliberately out of scope.
* **Future phases MUST NOT introduce unused or redundant imports** — the build fails in `validate`. This applies to subagents too.
* **One-shot cleanup** is OpenRewrite `org.openrewrite.java.RemoveUnusedImports` run IN ISOLATION: `./mvnw -Prewrite rewrite:run -Drewrite.activeRecipes=org.openrewrite.java.RemoveUnusedImports`. NEVER use the broad `org.ctc.RewriteCleanup` recipe for import cleanup — it activates `CommonStaticAnalysis` (~70 sub-recipes) and would rewrite non-import code. Note: `RemoveUnusedImports` is conservative with static imports and may miss some Checkstyle flags — remove those by hand (import-line deletions only).
