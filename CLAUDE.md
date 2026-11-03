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
* **GT7 Data Bundles:** Official catalog imports and string fields can use double quotes, single quotes or backticks. Preserve manufacturer lookup and apostrophes in vehicle names; restrict chunk filenames to the expected locale and safe asset filenames. Keep scraper tests independent of live network data. The sync preview uses independent management lists and retains selections across filtering and pagination. Bulk selection applies to all new entries in its catalog. Keep selection totals visible, disable empty imports and label every import checkbox.
* **Car Metadata:** Official GT7 IDs supply category/drivetrain; never infer classification from names or silently adopt IDs for legacy name matches. GTPlus years are candidates keyed by numeric ID, parsed without executing JavaScript. Accept a four-digit source year only with matching official suffix, or a reviewed ID correction. Preserve accepted years and per-field manual overrides, including explicit unknown years. Native sync forms select metadata updates separately and submit the server preview ID; re-fetch and compare source plus locked local state before applying. Keep backup fields and the read-only data audit aligned.
* **Keep Thymeleaf Templates Lean:** No complex logic (SpEL expressions, collection projections, nested conditions) in templates. Calculations and data preparation belong in the service — templates are for presentation only.
* **No Inline Styles on Buttons:** Instead of `style="..."` on `.btn` elements, always use CSS classes from `admin.css` (`btn-xs`, `btn-sm`, `btn-lg`, `btn-tab`). When refactoring inline styles to CSS classes, always check JavaScript that sets `element.className = '...'` — the new classes must be added there as well.
* **Isolate Test Data Completely:** E2E test data in `TestDataService` must use separate entities with a test prefix (e.g., `T-ALF`, `Test_Alpha_1`, `Test-Season 2026`). Never use real teams, drivers, or seasons for automated tests — these collide with manual tests on imported data.
* **Tag Tests by Category (`@Tag`):** Surefire/Failsafe routing is `@Tag`-based, not filename-based. Every new test class MUST be tagged: `@Tag("integration")` for `*IT.java` (Spring-context ITs), `@Tag("e2e")` for Playwright tests in `org.ctc.e2e.*`. Plain unit tests stay untagged. `@Nested` inner classes inherit their parent's tag. See `.planning/codebase/TESTING.md` "Test Categorization (`@Tag`)" for the full convention. An untagged `*IT.java` will silently run in Surefire under the wrong fork config and may race on shared state.
* **RaceLineup is Source of Truth:** For driver-team assignments (especially sub-teams), always prioritize `RaceLineup`; use `SeasonDriver` only as a fallback for seasons without races. The CSV import determines the correct assignment.
* **Score Aggregation on Result Save:** After every `raceRepository.save(race)` that wrote results (controller, import, or service), call `scoringService.aggregateMatchScores(race)`. Standings depend on aggregated scores on `Match`/`PlayoffMatchup` — never recompute in templates or controllers.
* **Data Audit Mirrors Write Rules:** `DataAuditService` (`/admin/data-audit`) reports historical records that break a consistency rule. It must stay read-only (`@Transactional(readOnly = true)`, no saving service calls such as `SiteSlugService.allocate`). When a write path gains a new consistency rule, add the matching audit check with a synthetic fixture in `DataAuditServiceIT`.
* **Spring-Native over JDK-Built-In:** In this Spring Boot stack, prefer Spring abstractions over JDK-only equivalents: `RestClient` (not `java.net.http.HttpClient`), `MultipartBodyBuilder`, `ClientHttpRequestInterceptor`. Auto-config, observability hooks, and bean lifecycle are already wired — do not build parallel wrappers. JDK `HttpClient` is acceptable only outside Spring context (e.g., standalone CLI tools).
* **Grep All Usages Before Refactor:** Before renaming/changing a method, repository call, or pattern, `grep -rn` the entire `src/` for the symbol AND for structurally similar copies (e.g., `getSeasonDrivers().stream()`-style patterns). Phase plans must include an explicit "audit all usages" task — files_modified lists from planners regularly miss copies.
* **Do Not Modify Flyway Migrations:** Existing `V*__*.sql` files must never be changed after release (Flyway checks checksums). Schema changes must always be a new migration file: `V{N}__{short_description}.sql` (snake_case, English). Maintain H2 + MariaDB compatibility.
* **Classification constraints:** Use connection-independent CASE membership checks in H2/MariaDB migrations. Test valid and invalid values from a new connection after migration finishes. V25 repairs the released car metadata checks without changing V23.

* **Playoff Editors:** Keep creation and manual seeding in native fieldsets with contextual back links and save actions. Every team and seed input must have a unique label. Preserve indexed seed parameter names and disabled controls for frozen brackets. Explain that automatic seeding uses saved seed numbers, or regular-season standings when no seed numbers exist.

* **Match Editors:** Group preview/stream, event roles and walkover into native fieldsets. Keep field errors beside labeled inputs and preserve match-edit test IDs. Walkover selects the forfeiting team. A bye hides and disables the opponent selector while retaining its value for a switch back. Preserve server-side bye handling when JavaScript is unavailable.

* **Driver Sheet Import:** Preserve tab-scoped season, skip and accept parameter names. Label every decision with unique IDs across tabs, and state unchecked behavior explicitly: conflicts replace the team; fuzzy suggestions create a new driver. Errors are skipped rows, and unassigned tabs are skipped on execution. Retain the sheet URL after preview errors.

* **Driver Merge:** Show the source profile to be removed and the target profile to keep before confirmation. Use user-facing record names, explain that duplicates keep the target entry, and distinguish existing alias transfers from source PSN alias creation. Keep the preview read-only, native target validation and final confirmation. Never interpolate driver names into inline JavaScript.

* **Power Rankings Editor:** Keep each team's hidden download ID inside its ordered list item so keyboard, drag and native form submission send the visible order. Provide labeled up/down buttons, boundary focus restoration, position announcements and a reset to the initial rating order. Keep season links and the initial-order download usable without JavaScript. Reordering only changes the exported graphic, never stored ratings or standings.

* **Team Cards Workspace:** Keep season selection and card generation in native forms. Label previews and actions with the team, distinguish missing artwork from available PNGs, and explain that ZIP downloads include existing files only. Generation uses an inline live status, blocks concurrent generation submissions and restores controls on browser history return. Preserve season context after success or failure, and keep downloads usable without JavaScript.

* **Race Import Entry:** Associate shared season and target controls with the active CSV or Sheet form. Disable inactive regular/playoff targets so only one destination is submitted. Keep source selection accessible with pressed states, preserve source input values and guard matchday loading/creation against stale season responses. Inline creation must report errors beside its label and prevent duplicate submissions.

* **Race Import Preview:** Keep driver assignment controls inside the execution form, with unique labels across race tables. Preserve confirmation parameter names and whole-import error blocking. Use neutral assignment states so team colors and error states remain distinct. CSV execution requires the same file to be selected again.

* **Planning Editors:** Generator previews must use the same PhaseTeam roster as generation, with counts and round recommendations prepared by the service per group. Keep generation inputs after both binding and domain errors. Preserve pairing editor test IDs, ISO date/time values and field errors. Saving pairing metadata and publishing its Discord announcement are separate actions.

* **Season car pools:** Existing/new seasons default to `FIXED_CARS`. `CATEGORY_POOL` definitions remain drafts until explicit freeze stores deduplicated, disjoint, nonempty member IDs. Match AND within clauses and OR between clauses using accepted local metadata. Frozen labels/clauses/members cannot change; reopen/mode switches reject saved entry references, and mode changes reject saved fixed cars. Keep fixed assignment URLs guarded by mode. Serialize configuration through `SeasonRepository.findByIdForUpdate`; flush pending transactional changes before refreshing locked state. Season metadata writes use the same lock, and dynamic updates preserve concurrently changed pool fields through other writers. Protect member cars from deletion. RaceCarSelectionService shares read/write entry consumption by season and effective home, excluding the edited race and all byes. Category choices require both entry and car, a frozen same-season entry and exact member ID; both empty remains a draft. Legacy save signatures reject category selections; the native overload accepts the entry ID. Validate planned final assignments for all affected legs before mutation. Source/target seasons lock in UUID order; refresh current race and pairing rows under the locks. Indirect playoff seeding/advancement/revocation and aggregate deletion join the same protocol. Matchday deletion refreshes cached child ownership and current aggregate membership; moved children require a reload before deletion. Dynamic Race/Match/PlayoffMatchup updates preserve concurrent selection/participant state. MariaDB snapshot conflicts become domain retries without changing isolation. Calendar event IDs persist in a fresh transaction after the external call. Configured category CSV/Sheets overwrites preflight the entire import and reuse stable race IDs, mapping distinct home legs without collection-order guesses; reject ambiguous or incomplete mappings before deletions or driver creation. Backups retain category snapshots and the existing restore write-admission checks. Audit bye and missing-home selections without repairing history.
* **Race car presentation:** `RaceCarPresentation` projects the concrete car name and optional saved pool-entry label without evaluating eligibility. Services and page generators prepare the projection and per-race maps. Preserve concrete `carName` and optional `poolEntryLabel` for custom settings templates. Keep concrete-car completeness checks, native/public escaped text and long-label wrapping. Discord setup graphics inherit the context through their existing explicit publication action; calendar pairing/time payloads stay unchanged. Score-banner team columns must shrink and wrap maximum-length short names at phone width.
* **Pool backups:** Schema 9 exports all 42 entity tables, including category entries, clauses, exact members, confidential pick history, private channel bindings and immutable publication attempts/snapshots. Schemas 1 through 6 may omit publication files; schema 7 requires them even when empty. Supported schemas 1 through 3 may omit the three pool files and default missing season fields to fixed/draft. Schema 4 requires those files even when empty. Validate pool ownership, clause types, nonempty/disjoint frozen snapshots, complete race selections and effective-home duplicate consumption before wiping data. Check IDs incrementally; pool preflight retains at most 100,000 compact reference rows and fails with DATA_MISMATCH at that limit. Legacy fixed-only rows are checked without retention. Restore saved membership without reevaluating car metadata. The read-only data audit reports pool inconsistencies without repairing snapshots. Keep `CAR_ELIGIBILITY` information separate from invalid provenance and pool integrity. Unknown category, drivetrain or model year explains which filters cannot match; unknown values remain valid, and the audit never fills them or changes frozen history.
* **Incident reporting:** Canonical contexts belong to exactly one regular Match or PlayoffMatchup; auxiliary Match views resolve to the same playoff identity. Management binds the exact configured guild/channel and explicitly confirms a future reporting deadline. Defaults use the first planned race start in Europe/Berlin plus 24 elapsed hours; missing or ambiguous starts have no invented default. Confirmed cutoffs remain unchanged by scheduling edits. Window and report writes reuse restore writer admission and the season lock, then lock window before report. Recheck current actor scope and server Clock under lock; reject new mutations at or after the cutoff. Keep creator and original party snapshots, immutable revisions/events, human-readable case references and durable request receipts. Incident-bearing race IDs survive unambiguous results-only imports; reject ambiguous mappings before any mutation. Ordinary deletion and identity changes must preserve context/history. Lifecycle guards use current locking reads after season/guild waits; MariaDB snapshot conflicts return a domain retry before any mutation. Schema 8 adds five incident tables with explicit serializers, restorers and preflight; schemas 1 through 7 have no incident state or invented windows. Intake requires an IncidentReportAccess adapter and fails closed while it is absent. This foundation does not register Discord commands, open intake automatically or publish incidents to the website. Native management uses IncidentBindingForm/IncidentDeadlineForm with required version tokens; missing versions are validation errors. Keep the Berlin deadline inputs independent of the legacy London input converter. Preserve submitted date/offset/reason and field errors, use contextual pairing links, escape case text and keep controls usable without JavaScript.
* **Durable incident delivery:** First confirmed submissions and locked deadline closures enqueue separate RECEIPT/DISCLOSURE operations in their producer transaction. Capture the immutable revision and destination once; edits, withdrawal and request replay never enqueue another neutral receipt. The receipt sender posts fixed detail-free text with empty allowed mentions and stable bounded nonce. Nonce deduplication is temporary; uncertain HTTP results and expired claims never retry blindly. Keep SQL transactions outside HTTP, and hold one ImportLockService writer admission through claim, network and outcome commit. Verify token/version/generation/live lease before acknowledgement. The due worker is explicitly opt-in with `app.discord.incidents.delivery-enabled`, batches ten windows/receipts and never opens a window. Strict mutation cutoff holds without it. Management status distinguishes stored reports from public sends. Reconciliation verifies existing receipt channel/bot/content or requires reviewed absence; use trusted principals, version/generation tokens, reason and explicit confirmation. Schema 9 adds the delivery table; schema 8 may omit it. All restored windows persistently pause external delivery. Restore exact operation states/IDs, discard prior operational review grants and require fresh review before resuming the entire context. Detailed thread creation remains a separate unit.
* **Private incident intake:** `/incidents` reuses the existing Gateway connection, bounded workers and ephemeral transport. Resolve exact canonical match-channel scope and current member permissions; picks' private-team-channel policy does not apply. One matching participant role suffices even when the other role is unconfigured. Shared roles require one organization; opposing roles and unrelated shared roles receive fixed private management guidance. Recheck scope and original-party eligibility before private reads and under write locks. Refresh participant/parent role metadata with current read locks after season/guild admission; read-only legacy-channel lookups must not use FOR UPDATE on MariaDB. Query private cases by context/window and creator. Keep drafts transient, expire after 30 minutes, invalidate on restore and bind opaque actions to user/channel/context/race. Preserve reviewed versions and reporting-party candidates through confirmation; durable request replay returns the original receipt. Page complete private text within Discord limits and keep leg numbers visible in truncated choices. The first immutable SUBMITTED event is #290's durable neutral-receipt request; edits and withdrawal never create another. No public notice/thread send belongs in this handler.
* **Confidential picks:** Season opt-in and Race.picksManaged default false. Enrollment occurs explicitly for newly scheduled eligible targets; disabling collection never removes markers or immutable history. PickSubmissionService and PickReviewService acquire restore writer admission before opening their own transaction and reject an existing transaction. Team callers supply a trusted authorized actor, complete typed payload, aggregate version and durable request ID. Replay verifies ownership before returning the original receipt, including after approval or a lost wizard payload. Capture the acceptance Instant once for strict Berlin cutoff comparison and receipt persistence at microsecond precision. Missing deadlines and ambiguous/nonexistent Berlin local times require explicit management opening; return/open survives resubmission, manual close wins, approval locks team edits. Season locking and shared category eligibility serialize reservations by effective home; pending returned/closed picks retain their latest reservation, current race is excluded once and byes consume nothing. Approval revalidates the exact latest revision and atomically applies settings only. Corrections retain actor, reason, time and full payload. Protect original matchday/opponent identity, historical selection references and results-only CSV identity. Schema 5 stores all history with numeric revision pointers and validates replayed state before any restore wipe; schemas 1 through 4 default absent flags off. Readiness reports delivered collection and confidentiality capability. The Gateway defaults off and seasons opt in explicitly. Valid private archives restore under the same preflight and writer-admission rules. The read-only audit reuses backup integrity rules and reports the first private-history inconsistency or unreviewed ambiguous Berlin deadline without repair, automatic opening or payload text.
* **Shared Discord Gateway:** JDA 6.7.0 owns outgoing reconnect/resume and rate limits behind transport-neutral ports. The existing bot identity is reused without message-content intent; enabled defaults false and confidentiality readiness gates startup. Register guild commands only through an explicit management or operator action; never register on connect. The existing management button registers `/picks`; the incident operator procedure registers `/incidents`. Acknowledge privately within three seconds before bounded work; keep follow-ups ephemeral and suppress mentions. Modal opening is immediate. Drop expired continuations after 15 minutes and invalidate their server generation after a committed restore. Pick bindings are exact season/team/guild/channel records; shared match channels and ambiguous pick scopes deny access. Rebuild chooser scopes and use fresh Discord member/channel/role overwrite data for every read. Gateway actors retain their channel context and generation; recheck current binding and permissions inside the existing admitted transaction after season/target locks. Binding administration uses the same season lock. Schema 6 preserves bindings and validates references, numeric IDs and scope uniqueness before wipe; schemas 1 through 5 may omit the file and grant no channels. Keep tokens, callback URIs and private values out of logs. Management setup remains under existing auth/CSRF, and viewing health performs no remote lookup.
* **Pick publication:** Persistent race enrollment governs public projections independently of collection/Gateway switches. Freeze scalar content, labels, template bytes and per-output masks under season locks; commit prepared attempts before bounded webhook HTTP, then confirm snapshots and post mappings in a fresh transaction. Keep uncertainty after lost responses or SQL failure. Settings/lobby response channels must match the captured pairing destination; preview retains its existing webhook-ID alias. Native reconciliation verifies the original webhook message and exact attachment bytes before releasing the captured version; retirement requires an actor, reason, current session and explicit remote-handling acknowledgment, and quarantines the matching edit mapping. Public projections select the latest confirmed content version and union only its masks. Automatic preview edits skip managed settings. Unknown custom-template coverage releases no additional fields. Native opt-in classifies configured unfinished targets, including undated drafts, against actual matching publication evidence and rejects unresolved deliveries. Schema 7 retains both publication tables and exact season catalog joins; legacy-only receipts do not enable the private-archive readiness gate.
* **Time Progression:** `TimeProgressionMode.MULTIPLIER` keeps numeric 0 distinct from `EQUAL_CONDITIONS`, which stores no multiplier. Legacy callers without a mode retain numeric semantics. A null numeric multiplier remains an incomplete draft through both save signatures, so editing other settings stays possible; it never implies Equal Conditions. Validate explicit mode/value pairs before mutation. Graphics use `timeProgressionDisplay`; retain the numeric template variable for existing custom templates. Backups without the field restore as MULTIPLIER.

## OSIV (Open Session in View)

Deliberately enabled (`spring.jpa.open-in-view=true`). The Hibernate session remains open until the end of the HTTP request so that Thymeleaf can render lazy-loaded fields. This is appropriate for this admin application using server-side rendering — no lazy-init workarounds in controllers are necessary.

* **Private picks dialogs:** Keep 30-minute drafts transient, bound to user/guild/channel/race and the version loaded at edit start. Component routes use server-held nonces and allowed-page choices; expire them on restore and reject concurrent session work without blocking initial acknowledgement. Modal opening shows empty input fields immediately; every data-bearing read and final confirmation rechecks access. Continue between modals through private buttons. Enforce exact stage fields, bounded values and Discord component limits. Confirmation retains one durable request ID and the original restore generation through the admitted transaction. Team viewing reads committed revisions through a transactional DTO service; never expose another member's draft. Collection/readiness gates stay unchanged.

* **Picks management review:** Keep overview/detail/correction preparation in MatchdayPickAdminService and state transitions in PickReviewService. Native forms use exact aggregate/revision versions and a canonical SHA-256 settings fingerprint. Bind each form to the process instance and restore counter so restore or restart requires a reload. Compare that fingerprint under the existing season/race locks before approval or correction; differing configured values require explicit replacement consent. Derive the actor from the server principal, with an explicit development actor only for open dev/local profiles. Management writes must not open an outer transaction before PickWriteExecutor admission and never invoke publishing. Keep returned reasons, original approved revisions and complete correction decisions visible. Reuse RaceFormDataService for native category/member refresh, retain rejected values and use scoped checkbox sizing/static form actions on these long forms. An enrolled target changed to a bye remains readable without review actions.

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

* **Validate-Phase Before New Phase (Nyquist Gate).** A phase is NOT complete until `/gsd-validate-phase <N>` reports **Nyquist-compliant** (this project runs with `workflow.nyquist_validation: true` in `.planning/config.json`). The orchestrator MUST NOT propose any next-phase command (`/gsd-discuss-phase`, `/gsd-plan-phase`, or `/gsd-execute-phase` for phase N+1) while the just-completed phase N lacks a VALIDATION with a Nyquist pass — a `PARTIAL` result re-routes to `/gsd-validate-phase <N>`, not forward. This mirrors the Code-Review gate above: stock GSD skills never surface validate-phase in their `offer_next` / `progress` routing (it only self-suggests after `secure-phase`, and the `workflow.validate_after_execute` config flag is dead — referenced by no skill), so the orchestrator must hand-carry it. The canonical phase-close order in EVERY "what's next?" block is: **(1) `/gsd-code-review <N>` → (2) `/gsd-verify-work <N>` (UAT) → (3) `/gsd-validate-phase <N>` → only then the next phase's `/gsd-discuss-phase` / `/gsd-plan-phase`.** List these top-to-bottom, each above the next-phase command, and mark the next-phase command as blocked until review + UAT + validate have passed. Applies to the `/gsd-progress` route, execute-phase closeout, subagent SUMMARY.md next-step outputs, and any orchestrator-authored next-step prose. At milestone close, `/gsd-complete-milestone` is additionally blocked until every phase has a Nyquist-compliant VALIDATION (parallel to the existing REVIEW.md precondition).

## Memory-Aware Subagent Dispatch

Before ANY subagent dispatch in a GSD workflow (`gsd-planner`, `gsd-executor`, `gsd-code-reviewer`, `gsd-verifier`, etc.), the orchestrator MUST:

1. Scan the orchestrator's auto-memory index (`MEMORY.md` in the Claude Code project-memory directory) for entries relevant to the task (UI work → no-inline-styles + playwright + screenshots; tests → clean-build + no-flaky; refactor → grep-all-usages; etc.).
2. Quote the relevant 2–4 memory entries verbatim in the subagent prompt under a `**User Conventions (from memory):**` heading.
3. Include the active milestone branch and the inline-sequential / no-branch-switch invariants.

The reason: subagents do NOT read `MEMORY.md`. Only CLAUDE.md (which they always read) and explicit prompt content reach them. Memory entries that have not yet been promoted to CLAUDE.md must be hand-carried by the orchestrator on every dispatch.

* Keep `mariadb.version` and the direct connector version reference in `pom.xml` so Renovate can
  update runtime driver security fixes independently of Spring Boot. The runtime artifact regression
  check accepts later majors and rejects the known vulnerable connector versions.

## recovery integration contract

* `OutboundPolicy` guards Discord transport, Google SDK access, GT7/YouTube reads and remote file
  downloads. Existing auto-post callbacks also stop before publication work. New external callers
  must obey that policy. `recovery-check` always forces it off and activates authenticated prod
  behavior. Keep production-shaped credential/startup tests and the transport guard independent.
* Recovery tooling lives under `deploy/lib/`. `runtime.py` assembles the shared Compose context;
  sealed units contain no executable recovery tools. Read `deploy/guides/recovery.md` before changing
  capture, probe ownership, storage-generation or integration-resumption semantics. Run
  `bash deploy/tests/recovery.sh` plus native MariaDB qualification for affected runtime paths.
  `deploy/tests/native_files.py` exercises private mode-0700 directory restoration and actual
  filesystem-flush failures against the built Docker image. Keep the final data flush under the
  application owner; the extraction helper retains only `CHOWN` capability.
* Graphics use preinstalled Playwright browsers. `SandboxedHtmlRenderer` disables automatic browser
  downloads before creating the driver, in addition to its existing page-level network restrictions.
* `deploy/lib/update.py` validates exact released candidates and their upgrade contract. Keep the
  source app continuously stopped from capture through restore-check and activation. Preserve the
  selected volume generation and private secret paths. Once activation might migrate the schema,
  fail paused/stopped with a complete recovery path. Never auto-start the old image. Run
  `bash deploy/tests/update.sh` and qualify migration/failure paths with real isolated MariaDB.

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
* **Site Output:** `ctc.site.output-dir` (default: `docs/site`). Generation writes into a sibling `<dir>.generating-<uuid>`, swaps it in only when complete (keeping `<dir>.previous` during the swap) and locks `<dir>.lock`, so the parent directory must be writable.
* **Backup Dirs:** `app.backup.staging-dir`, `app.backup.import-backups-dir` (default: `data/<profile>/…`)
* **Container Paths:** `docker` and `prod` set all four paths below the `ctc-data` volume (`/app/data/{uploads,site,backup-staging,import-backups}`). The uploads directory must not be a mount root and must share a volume with the recovery archives, because restore renames it; no backup directory may lie inside the served upload tree. `ContainerStoragePathsTest` enforces all three.

## CI/CD

* **Push/PR to `master`:** Build, Tests, Playwright E2E, JaCoCo Coverage PR comment.
* **Push `docs/site/**`:** GitHub Pages Deployment.
* **Release:** `release.yml` runs after each completed `CI` or `CodeQL SAST` run of a master push. `scripts/ci/release-gate.sh` releases the revision only when its latest `ci.yml` and `codeql.yml` push runs succeeded, including the jobs `build-and-test`, `dockerfile-noble-pin-guard`, `docker-build` and `Analyze (java-kotlin)`, and master has not moved past it. It reads workflow runs, not check runs, because any workflow token with `checks: write` can forge a check run. CodeQL therefore analyzes every master push. A rerun of a failed check triggers the release again. A run that stopped after the tag push resumes and creates only the missing parts. `ReleaseScriptsTest` covers the gate and `scripts/ci/release-version.sh`.
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
* CTC uses black, white and readable grey surfaces. Admin actions and focus states use yellow (`#ffdd00`). The public website reserves yellow for branding and event headers; navigation, filters and statistics use neutral selection states so they do not compete with team colours.
* Use Conthrax for branding and public page mastheads. Admin headings, controls and data use the system sans-serif font. Keep tabular figures in tables and scoring inputs.
* The admin sidebar keeps native links and server-rendered `aria-current` labels. On mobile, the enhanced menu contains focus and makes the page inert while open; closing restores the trigger. Keep hidden menus inert, clear modal state on viewport changes and history return, and preserve visible navigation without JavaScript. The skip link targets a focusable main element.
* Admin season navigation is prepared by `SeasonWorkspaceService` after the controller fills its model. Derive context from the displayed resource before list filters or form selections. Keep season selection in URLs rather than a shared session so browser tabs remain independent.
* Admin management lists use `management-list.js` for labeled search, status filters, counts and reset. Keep driver sorting over the complete dataset when filters change, reset pagination on filter changes, and sort through native buttons with `aria-sort`. Team search keeps parent and sub-team rows in one group. Standings keep their server-calculated ranks when sorted or filtered. Resolve sort columns from the rendered header position because group and Buchholz columns are conditional, and use unrounded values for average-point sorting. Reveal filter and pagination controls only with JavaScript; preserve all rows and action URLs without it.
* Error views share the error-panel fragment using the existing model fields. Keep HTTP statuses and development-detail visibility unchanged. The native seasons link is always available; reveal the history button only with JavaScript and a different same-origin admin referrer.
* Season, phase and group configuration uses labeled fieldsets, section anchors and contextual save actions. Season metadata and catalog assignments save separately; label transfer searches and actions. Restore season catalog and Discord models on validation errors without replacing submitted metadata. Preserve the immutable phase-type control and its hidden submission value.
* Race and match details put race data and results before publishing tools, with section anchors and direct setup/lineup/results links. Keep publishing endpoints and availability conditions intact. Lineup controls have explicit driver/team labels. Guest rows retain unique label IDs and keyboard focus, can be added after removing all rows, and validate directory selections before submission.
* Race setup uses labeled fieldsets and native car/track selects. Choose category entry before its concrete member, keep used options disabled and labeled, and exclude the edited race from usage checks. Provide native Refresh choices without saving. Preserve submitted context, unavailable options and BindingResult through errors; number fields with rejected raw text temporarily render as editable text. Spring field defaults retain values omitted by disabled options while explicit clears win. Refresh home, matchday and entry changes; cancel old requests and gate DOM updates after the response body resolves. Build option labels through DOM text. Matchday validation retains its season context and visible field errors. Return links retain the matchday or season filter.
* Scoring scheme lists use the common management-list controls. Race scoring positions have native labels, retain focus on add/remove and serialize to the existing point-sequence fields. Keep a native sequence-input fallback without JavaScript. Preserve point values and page titles on validation errors.
* Admin team, driver, car and track editors use labeled fieldsets, section anchors and contextual save actions. Preserve related models and selection options on validation errors. Alias rows use contiguous form indices and retain focus after adding/removing rows. Car/track catalogs share management-list.js with keyboard sorting and pagination. Preserve image previews on validation errors and label upload inputs. Team detail pages display logos; driver assignment rows link to team and season workspaces.
* The admin season overview presents phase work before collapsible season setup. Team editing and replacement use native `<dialog>` elements; reset forms before opening and retain keyboard focus restoration.
* Discord role assignments use a labeled native select with an explicit unassigned option and preserve saved IDs missing from the cache. Reveal optional search only with JavaScript. Searching must not clear the selected value or rebuild the select during a native selection; keep the plain input when the cache is empty.
* The Discord channel archive chooser starts as a native disclosure and enhances into a labeled modal dialog. Keep category IDs, server-selected defaults, CSRF and the archive endpoint intact. Full categories cannot be selected; an unavailable destination disables submission. Closing restores trigger focus and retains the chosen category. The native form must remain usable without JavaScript.
* Public navigation uses a native details menu with neutral current-page and season-context markers. Keep it above page content, bound its height to the viewport and preserve navigation without JavaScript. Escape restores menu focus; outside clicks and leaving the menu close it without moving focus. The skip link must focus the main element, and browser history must not restore an open menu.
* Public matchdays use cards and native driver-result disclosures on mobile, with full result tables available. Detail return links retain phase/group context and lead playoff matchdays to the bracket. Keep mobile results and tables consistent, including canonical driver links.
* Public driver rankings and race histories use native mobile disclosures and full tables, with consistent values, guest markers and profile links. Profile statistics precede race history. Driver history links to canonical matchday pages.
* Public community link cards have one full-card anchor, a visible external destination and a new-tab label. Keep long names and URLs within the mobile viewport.
* Public team and driver directories reuse the same rows on desktop and mobile. Keep explicit table roles when CSS stacks rows, label search and season controls, and use neutral focus states. Reveal filtering controls only with JavaScript; profile and season links remain usable without it.
* Public playoffs use native round disclosures, round anchors and explicit winner labels. Preserve unknown-team placeholders and distinguish missing leg results from zero points. Archive search and chronological sorting must update mobile cards and the full table together, ordering by year and season number. Keep both usable without JavaScript.
* Public season and alltime standings provide native mobile disclosures plus a full table. Keep their values, optional group/Buchholz fields and team links consistent. Generated HTML snapshots must be refreshed after template/layout changes.

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

* **Gate:** `spotbugs-maven-plugin` 4.10.3.0 + `findsecbugs-plugin` 1.14.0 run on every `./mvnw verify` (Medium+HIGH findings block the build). No separate CI job — SpotBugs runs inside the existing `verify` step.
* **Minimum plugin version 4.10.1:** SpotBugs 4.9.8.x does not load the find-sec-bugs detector pack at all (fixed by spotbugs/spotbugs#4191). Never downgrade below 4.10.1 — the security patterns go silent without any build error. A `verify`-phase guard (`scripts/guards/findsecbugs-loaded-guard.sh`) fails the build if `target/spotbugsXml.xml` does not name the detector pack.
* **Suppressions:** Live in `config/spotbugs-exclude.xml`. Every `<Match>` entry MUST have an XML rationale comment with a code-cross-reference to where the intentional pattern lives. No `@SuppressWarnings("all")` ever — use targeted `@SuppressFBWarnings({"SPECIFIC_CODE"}, justification="...")` in source or a `<Match>` entry in the filter file.
* **`lombok.config` invariant:** `lombok.config` at project root sets `lombok.extern.findbugs.addSuppressFBWarnings=true`. Do NOT remove or modify the two SpotBugs-related lines without a new phase that re-baselines suppressions — removing them re-introduces ~40–80 `EI_EXPOSE_REP*` false positives from Lombok-generated entity getters.

### CodeQL SAST (Code Scanning)

* **Gate:** `github/codeql-action@v4` runs on push to `master`, on pull_request against `master`, and on daily 02:00 UTC cron via `.github/workflows/codeql.yml` (language `java-kotlin`, query suite `security-extended`). The gate step runs `scripts/ci/codeql-gate.sh` (tested by `CodeqlGateScriptTest`) and fails the workflow when a new open HIGH or CRITICAL alert appears: on a pull request, an alert number the base branch does not have; on a push, an alert number that was not open before the analysis. Any alert-API failure fails the gate. Scheduled and manual scans run the gate against all open HIGH/CRITICAL alerts and fail until those alerts are resolved or explicitly dismissed. Push/PR checks retain the existing new-alert baseline semantics.
* **Suppressions** live in `.github/codeql/codeql-config.yml` `query-filters`. Every suppressed finding requires a `// CodeQL FP: <rule-id> — <reason>; see docs/security/sast-acceptance.md` source marker on the line directly above the protected method or block, AND a matching table row in `docs/security/sast-acceptance.md`. UI dismissals are equally valid but MUST also be reflected in `sast-acceptance.md` (Update-on-Triage discipline — partial-writes are forbidden).
* **Acceptance doc** at `docs/security/sast-acceptance.md` is the single source of truth for SAST triage decisions: per-pattern sections (SSRF | ZIP-Slip | BCrypt-N/A | Others), per-finding rows with Alert-ID + Rule + Location + Bucket + Rationale + Source-Marker. Every suppression PR MUST include a parallel `sast-acceptance.md` edit. The acceptance doc is the human-readable counterpart to the machine-readable `codeql-config.yml`.

### Checkstyle (Unused-Import Gate)

* **Gate:** `maven-checkstyle-plugin` (Checkstyle core 13.5.0, overridden for Java 25) runs on every `./mvnw verify` — bound to the `validate` phase via `config/checkstyle.xml` with `failOnViolation=true` at severity `error`, no opt-in profile, locally and in CI. The ruleset is intentionally minimal: only `UnusedImports` (with `processJavadoc=true`, so `{@link}`/`@see` references count as usage) and `RedundantImport`. It covers BOTH `src/main/java` and `src/test/java` (`includeTestSourceDirectory=true`). Broader style checks (naming, whitespace, Javadoc-presence) are deliberately out of scope. The plugin/core split is `maven-checkstyle-plugin` 3.6.0 with a `com.puppycrawl.tools:checkstyle:13.5.0` dependency override (the bundled 9.3 cannot parse Java 25). Tradeoff of `processJavadoc=true`: an import referenced ONLY in a Javadoc `{@link}`/`@see` is treated as used and will NOT be flagged — accepted, because `false` produced 25 false positives on Jackson MixIn + IT classes whose imports exist solely for `{@link}` references. A second `validate`-phase exec guard (`checkstyle-gate-guard` in `pom.xml`) protects this config from silent drift (failOnViolation/includeTestSourceDirectory weakening, module removal).
* **Future phases MUST NOT introduce unused or redundant imports** — the build fails in `validate`. This applies to subagents too.
* **One-shot cleanup** is OpenRewrite `org.openrewrite.java.RemoveUnusedImports` run IN ISOLATION: `./mvnw -Prewrite rewrite:run -Drewrite.activeRecipes=org.openrewrite.java.RemoveUnusedImports`. NEVER use the broad `org.ctc.RewriteCleanup` recipe for import cleanup — it activates `CommonStaticAnalysis` (~70 sub-recipes) and would rewrite non-import code. Note: `RemoveUnusedImports` is conservative with static imports and may miss some Checkstyle flags — remove those by hand (import-line deletions only).

### Permanent production deployment

* Recipe fixtures must remain independent of live host port 9092. Mock the port probe only inside the fixture interpreter and retain its exact loopback-address and refusal-before-Compose assertions. Never disable the production port guard for tests.
* `deploy/tests/acceptance.sh` runs all existing deployment fixture suites, including native migration, through `DeploymentScriptsTest`. Keep the aggregate fixture-only. Runtime acceptance and operator procedures live in `docs/deployment/acceptance.md` and `deploy/guides/operations.md`.
* The dedicated rootless Docker unit uses notify readiness, child notifications, mixed termination and no inherited stop timeout. Do not signal Docker and its helpers together or force an early database shutdown.
* The shared Box supplies home LAN DNS through Pi-hole. Routine CTC deployment and testing must not reboot it. Record CTC-only restarts separately from actual host boot evidence.

* `deploy/ctc-manager --directory PATH doctor|status|up|stop` operates the dedicated rootless stack.
* Keep installation configuration as private JSON, never source it as shell code. The configured socket must belong to the current user inside its private runtime directory, normally `/run/user/<uid>/`; reject links, non-sockets, rootful Docker and missing memory enforcement.
* `deploy/compose.yaml` uses authenticated prod, pinned image digests, loopback 9092, no SQL host port and persistent data/log/database volumes. A networkless helper copies private Google credentials into an app-readable read-only volume.
* Deployment mutations share `.ctc-manager.lock` and journal state. Never remove an ambiguous stale lock or target unrelated projects/ports/volumes.
* `scripts/ci/deployment-bundle.py` creates and validates release material. Extend its exact inventory when adding deployment runtime files. `DeploymentScriptsTest` runs the behavioral recipe/archive tests through Maven. Include deploy changes in CI path detection.

### Dependency update policy

* Renovate permits daily updates without a time window or PR throttling and explicitly enables major proposals, including Docker. Major PRs have no dashboard approval gate and are not automerged; existing patch automerge still requires repository checks.
* GitHub vulnerability alerts stay enabled. Renovate OSV alerts and dashboard summaries supplement them for direct Maven dependencies. Hosted Renovate scheduling is controlled by the service; repository schedules cannot force its run frequency.
* Production database references must remain version-and-digest discoverable by Renovate while deployment archives use immutable canonical image identities.

* `.github/workflows/dependency-security.yml` submits the resolved transitive Maven graph daily at 01:30 UTC and on relevant trusted master pushes. Keep its write-scoped submission job off pull-request triggers and retain the action commit pin with an updatable version comment.

### Host provisioning

* Host setup defaults to read-only checks. Run `deploy/setup-host.sh --apply --backup-uuid UUID` from verified operator-owned source, never a CTC-writable helper as host root. Keep privileged native operations limited to the dedicated user/path roots, linger/user service and Serve 9443. Preserve every unrelated Serve/Funnel route.
* Reject subordinate mappings that overlap other allocated ranges or actual host UIDs/GIDs, including numeric CTC owner entries. Keep host memory and expected backup UUID checks fail-closed.
* Release inventories may grow, but verification must still accept complete supported earlier inventories and reject partial additions. Package host setup, its unit/helper and guide together.

* Scheduled maintenance stays local and uses the existing lock and complete recovery proof.
  Keep two daily stands and the first successful stand of the newest Berlin calendar month.
  Pending update recovery remains protected until a verified backup of the active image exists.
  Use one daily backup service/timer and local journal errors. No off-host requirement, alert hook,
  Discord delivery or separate monitoring service belongs to this single-admin deployment.

### Native Mac migration

* Keep native source provenance separate from the normalized supported recovery unit. An unrecorded source app version is null. Verify an existing observer's global PROCESS visibility and matching database before accepting a source freeze; do not add privileges to make it pass.
* Initial cutover accepts only the empty paused qualification target and an exact rehearsed source/unit proof. Persist the authority boundary before target HTTP activation. After possible target writes, stop a failed candidate and require complete state-preserving reverse migration, never stale Mac restart.

### Private Pages publication

* Generate Site captures immutable snapshots under its existing generation lock. Preserve exact snapshot IDs and manifest hashes through preview, confirmation and retries. Keep three recent snapshots plus the requested snapshot.
* Preview responses require existing production authentication and a CSP sandbox without same-origin permission. Preserve the iframe sandbox, local asset support and no-store caching. Dev/open profiles cannot queue publication.
* The host publisher alone reads the owner PAT. Use Contents/Pull requests read-write and Actions read-write, no Checks permission. Read trusted CI jobs through Actions APIs. Never mount credentials or the Docker socket into the app.
* Publication uses the shared installation lock, fixed repository and docs/site-only commits. The Pages workflow must verify the complete approved Git tree before upload. A PR merge alone is not publication success. Startup/recovery suspends pending approvals; uncertain remote outcomes require reconciliation.
