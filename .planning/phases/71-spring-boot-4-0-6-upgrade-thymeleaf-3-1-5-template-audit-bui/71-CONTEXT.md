# Phase 71: Spring Boot 4.0.6 Upgrade + Thymeleaf 3.1.5 Template Audit + Build Guard - Context

**Gathered:** 2026-05-10
**Status:** Ready for planning

<domain>
## Phase Boundary

Bump Spring Boot 4.0.5 → 4.0.6 and resolve the transitive Thymeleaf 3.1.5 (CVE-2026-40478 SpEL canonicalization hardening) by eliminating every `${...}`-expression in `th:replace/insert/include` fragment-parameter positions across the full template tree (~80 templates). Every affected template moves its title computation to its rendering controller (or sitegen page-generator) as a `pageTitle` model attribute. The phase ships with two regression mechanisms: a `TemplateRenderingSmokeIT` that GETs every `/admin/**` GET route and asserts absence of `TemplateProcessingException`, and a Maven `validate`-phase `exec-maven-plugin` grep gate that fails the build if any future template re-introduces an expression in a fragment-parameter position.

Out of scope: backup export/import feature work (Phase 72+), public-site phase coverage beyond the 3 site-templates currently using `${var}` in fragment params, performance work unrelated to the 4.0.6 bump.

</domain>

<decisions>
## Implementation Decisions

### Template-Audit Scope
- **D-01:** All 9 templates with `${...}` in fragment-parameter positions get the `pageTitle` controller-side fix preemptively, not reactively — maximum forward-compat safety against Thymeleaf 3.2 stricter restricted-mode evaluation.
- **D-02:** 6 admin templates affected: `match-scoring-form.html`, `race-scoring-form.html`, `season-phase-form.html` (3 known ternaries), plus `season-phase-group-form.html` (ternary), `race-detail.html` (string-concat + Elvis), `season-detail.html` (string-concat with nested ternary).
- **D-03:** 3 site templates affected: `driver-profile.html`, `team-profile.html`, `matchday.html` (plain `${var}` references — currently safe under 3.1.5, fixed preemptively for forward-compat).
- **D-04:** Plain `${var}` (pure property access) in fragment params IS allowed under Thymeleaf 3.1.5; only operators, ternaries, Elvis, string-concat, and method calls are restricted. Decision to still fix the 3 site templates is risk-mitigation, not necessity.

### Build-Guard Regex
- **D-05:** Broad regex pattern: `th:(replace|insert|include)=".*\(.*\$\{.*\}.*\)"` — fails the build on ANY `${...}` expression appearing as a fragment-call argument anywhere under `src/main/resources/templates/`. Post-Phase-71 the codebase contains zero such cases, so the regex stays clean by construction.
- **D-06:** Build-guard runs in Maven `validate` phase via `exec-maven-plugin` — fails first, before compile/test, with sub-10s feedback to the contributor.
- **D-07:** Guard reuses the established v1.9 D-06 / Phase-67 forward-commitment pattern for grep gates (no Maven Enforcer custom-rule module, deliberately rejected in REQUIREMENTS Out-of-Scope).

### TemplateRenderingSmokeIT Design
- **D-08:** Routes are discovered dynamically via `RequestMappingHandlerMapping` injected into the IT — iterate every `GET /admin/**` route, no hardcoded list. Self-maintaining: a new admin controller is automatically covered without touching the IT.
- **D-09:** Dedicated test profile + `SmokeITSeeder` (minimal-complete fixture: 1 Season + 1 Phase + 1 Group + 1 Team + 1 Driver + 1 Matchday + 1 Match + 1 Race + 1 RaceResult + scoring rows). NOT `DevDataSeeder` (heavy fixture, slow startup) and NOT `TestDataService` (built for full Playwright flows).
- **D-10:** Parameterized routes (`/admin/seasons/{id}/edit`, `/admin/races/{id}`, etc.) resolve their path variables via `@Sql`-seeded ID constants. The IT maintains a `Map<String, UUID>` keyed by path-variable name (`seasonId → SEASON_SMOKE_ID`, `raceId → RACE_SMOKE_ID`, ...) populated by the `SmokeITSeeder`.
- **D-11:** Assertion contract: `andExpect(status().isOk())` + `andExpect(content().string(not(containsString("TemplateProcessingException"))))`. Word-boundary-anchored on the exception class name to avoid colliding with documentation pages that may contain the word "Exception" in narrative text.

### pageTitle Pattern Implementation
- **D-12:** Direct `model.addAttribute("pageTitle", ...)` inside each affected controller handler method — no shared `@ModelAttribute` pattern, no `PageTitleService` abstraction. Trivial, readable, ~12 line additions across 6 controllers.
- **D-13:** `templates/admin/layout.html` renders the title with an Elvis fallback: `${pageTitle ?: 'CTC Admin'}`. Elvis IS allowed under Thymeleaf 3.1.5 INSIDE the layout body (not as a fragment-call argument). Fallback prevents rendering crashes if a future handler forgets to set `pageTitle` — visible-but-non-crashing degradation, caught by Smoke-IT.
- **D-14:** Site templates' `pageTitle` is set by the corresponding sitegen page-generator bean (Phase 62 D-20: sitegen decomposed into 5 page-generator beans). Each bean adds `context.setVariable("pageTitle", driver.getPsnId() / team.getName() / matchday.getLabel())` next to its existing context population.

### POM Changes
- **D-15:** Single-line parent bump: `<parent>...<version>4.0.6</version>` (no other POM diff required for the bump itself).
- **D-16:** Add `<dependencyManagement><dependencies><dependency><groupId>org.thymeleaf</groupId><artifactId>thymeleaf</artifactId><version>3.1.5</version></dependency></dependencies></dependencyManagement>` block — pin against future transitive bumps to a Thymeleaf version with further SpEL restrictions.
- **D-17:** No new Maven dependencies. `exec-maven-plugin` (Apache Codehaus) is added as a `<build>` plugin (transitive of Spring Boot parent, no new artifact in `<dependencies>`).

### Test Baseline Expectation
- **D-18:** Test count rises from 1227 unit + 31 Playwright (current Roadmap baseline) to ~1227 + N + 31, where N = number of `/admin/**` GET routes discovered by the dynamic SmokeIT. Estimated N ≈ 30-50 routes. JaCoCo line coverage ≥ 82 % gate held against the v1.9 baseline of 87.02 %.

### Claude's Discretion
- Exact list of admin GET routes (discovered by `RequestMappingHandlerMapping` at runtime — no need to enumerate during planning).
- `SmokeITSeeder` UUID constants (well-known UUIDs like `00000000-0000-0000-0000-000000000001` are acceptable).
- Internal structure of `TemplateRenderingSmokeIT` (parametrized `@ParameterizedTest` source method vs. `Stream<DynamicTest>` factory).
- `exec-maven-plugin` execution `<id>` and goal binding details (standard `exec:exec` with `grep -r -E -l` over `src/main/resources/templates/` is sufficient).
- Whether to add a `target/template-audit-report.txt` artefact for human review (nice-to-have, planner decides).

</decisions>

<specifics>
## Specific Ideas

- **Maintainer-recommended fix verified:** Daniel Fernandez (Thymeleaf maintainer) recommends controller-side `pageTitle` model attribute in thymeleaf/thymeleaf#1082 — exactly the pattern this phase implements.
- **Audit is exhaustive, not sampled:** the grep over all 79 templates returned 9 candidates with `${...}` in fragment params (6 admin + 3 site). No latent cases hidden in less-trafficked corners; the grep IS the audit.
- **Smoke-IT is a NEW guarantee, not a regression-only test:** existing `/admin/**` Playwright coverage is sparse (31 E2E tests across the whole app); a dedicated render-every-admin-page IT catches `TemplateProcessingException` regressions on every `./mvnw verify` from now on.
- **Pin-Thymeleaf-3.1.5 is forward-looking:** Spring Boot 4.0.6 already ships 3.1.5 — the `<dependencyManagement>` pin prevents a future Spring Boot patch release from silently pulling in a Thymeleaf with further SpEL restrictions.

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### v1.10 milestone foundation
- `.planning/research/SUMMARY.md` — Synthesized research; critical correction "Spring Boot 4.0.6 ships Thymeleaf 3.1.5, NOT 3.2"; Phase 1 (this phase) rationale and avoided-pitfalls list
- `.planning/research/STACK.md` — Version matrix; rationale for ZERO new Maven dependencies
- `.planning/research/PITFALLS.md` — Pitfalls 1 (Thymeleaf restricted-mode breakage), 11 (build-guard regex pattern), 12 (smoke-IT coverage)
- `.planning/REQUIREMENTS.md` §PLAT-01..07 — All 7 platform requirements covered by this phase, line-by-line acceptance criteria
- `.planning/ROADMAP.md` §"Phase 71" — Goal, depends-on, success criteria, UI-hint=yes

### Project conventions
- `CLAUDE.md` §"Keep Thymeleaf Templates Lean" (L70) — controller-side data preparation is established CTC convention; this phase aligns with it
- `CLAUDE.md` §"Visual Verification with `playwright-cli`" — required for the admin UI verification step after the bump
- `.planning/codebase/CONVENTIONS.md` — Controller patterns, DTO usage, `@ModelAttribute` conventions
- `.planning/codebase/STACK.md` — Current dependency snapshot pre-bump
- `.planning/codebase/TESTING.md` — IT vs. E2E patterns, Failsafe wiring, JaCoCo gate

### Prior-phase context (forward decisions that apply here)
- `.planning/STATE.md` §"Accumulated Context > Decisions" §"[v1.10 start]" — Thymeleaf 3.1.5 (not 3.2) correction, controller-side `pageTitle` chosen
- v1.9 D-06 / Phase 67 — Forward commitment to install a build-time grep guard against the fragment-parameter-ternary pattern; this phase delivers the guard
- Phase 62 D-20 (v1.9) — sitegen decomposed into 5 page-generator beans; site-template `pageTitle` writes land in those beans

### External references (consulted, not on-disk)
- Thymeleaf 3.1.5 release notes (CVE-2026-40478 SpEL canonicalization hardening) — informs the broad regex choice
- thymeleaf/thymeleaf#1082 (Daniel Fernandez maintainer guidance) — informs the `pageTitle` controller-side pattern

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`templates/admin/layout.html`**: shared admin layout fragment `layout(title, content)` — the entry point for the broken pattern; layout itself is not modified beyond adding the `${pageTitle ?: 'CTC Admin'}` Elvis fallback at the title-rendering point.
- **`SiteGeneratorService` + 5 page-generator beans (Phase 62 D-20)**: each generator already populates a Thymeleaf `Context` with entity-scoped variables; `pageTitle` is one more `context.setVariable()` call per affected generator (3 of the 5 generators).
- **`@Sql`-loaded test fixtures (existing pattern in CTC ITs)**: well-trodden mechanism for seeding deterministic UUIDs into ITs without bringing up `DevDataSeeder`.
- **`MockMvc`-based admin ITs (existing pattern)**: e.g., `SeasonControllerIT`, `MatchdayControllerIT` already use `mockMvc.perform(get("/admin/..."))` — `TemplateRenderingSmokeIT` reuses the same harness.

### Established Patterns
- **Controllers thin, services own logic (CLAUDE.md core principle)**: adding a `model.addAttribute("pageTitle", ...)` line is a controller responsibility (presentation prep), aligning with existing convention.
- **English UI text (v1.3 milestone, feedback_ui_language)**: all `pageTitle` values are English strings ("New Match-Scoring", "Edit Race-Scoring", etc.) — mirrors existing template literals exactly.
- **No inline styles, CSS classes only (CLAUDE.md)**: not relevant to this phase, but the constraint is alive — pageTitle changes don't touch styling.
- **Test Naming Given-When-Then (CLAUDE.md)**: `TemplateRenderingSmokeIT` method names follow `givenSmokeFixtureSeeded_whenGet${RouteName}_thenRendersWithoutTemplateProcessingException` or similar parameterized-test-display-name pattern.

### Integration Points
- **`pom.xml`** (root): parent version bump + `<dependencyManagement>` insertion + `exec-maven-plugin` `<build>` registration.
- **6 admin controllers**: `MatchScoringController`, `RaceScoringController`, `SeasonPhaseController`, `SeasonPhaseGroupController` (TBC name during planning), `RaceController` (for race-detail.html), `SeasonController` (for season-detail.html) — each `GET` handler that renders an affected template adds 1 line.
- **3 sitegen page-generator beans**: the beans responsible for driver-profile/team-profile/matchday template rendering get 1 line each.
- **3+6=9 templates**: each broken `th:replace="...layout(${expr}, ...)"` is rewritten to `th:replace="...layout(${pageTitle}, ...)"`. Surgical 1-line diff per template.
- **`templates/admin/layout.html`** and **`templates/site/layout.html`**: title-rendering point gets the `${pageTitle ?: 'CTC Admin'}` (or `${pageTitle ?: 'CTC'}` for site) Elvis fallback.
- **NEW: `src/test/java/org/ctc/admin/controller/TemplateRenderingSmokeIT.java`** + **NEW: `src/test/.../SmokeITSeeder.java`** (or `@Sql` script under `src/test/resources/sql/`).
- **NEW: `pom.xml` `<build><plugins>` exec-maven-plugin execution** bound to `validate` phase, running `grep -r -E 'th:(replace|insert|include)=".*\(.*\$\{.*\}.*\)"' src/main/resources/templates/` (exits 0 on no match = pass, 1 on match = fail).

</code_context>

<deferred>
## Deferred Ideas

- **Public-site rendering smoke-IT** (analog to `TemplateRenderingSmokeIT` but covering site-generated pages) — current scope only adds `/admin/**` smoke; site coverage stays at existing `SiteGeneratorPhaseAwarenessIT` (Phase 62) for now. Candidate for v1.11+ if site-template breakage becomes a pattern.
- **Maven Enforcer custom-rule module** for the fragment-param-expression check — `exec-maven-plugin` grep is the lower-effort path (REQUIREMENTS Out-of-Scope); a custom rule would be its own Maven module. Revisit only if grep proves brittle.
- **Internationalization of `pageTitle`** (`messageSource.getMessage("admin.match-scoring.new.title", ...)`) — all UI text is English by project convention; no i18n need today.
- **`TemplateRenderingSmokeIT` extension to `POST` routes** — current scope is `GET` only (the broken templates render on GET-edit/GET-new screens). POST routes would need form-payload synthesis and are out of scope.

</deferred>

---

*Phase: 71-spring-boot-4-0-6-upgrade-thymeleaf-3-1-5-template-audit-bui*
*Context gathered: 2026-05-10*
