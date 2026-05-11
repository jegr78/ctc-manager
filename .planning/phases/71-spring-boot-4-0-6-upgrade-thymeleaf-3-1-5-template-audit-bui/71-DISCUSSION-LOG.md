# Phase 71: Spring Boot 4.0.6 Upgrade + Thymeleaf 3.1.5 Template Audit + Build Guard - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-10
**Phase:** 71-spring-boot-4-0-6-upgrade-thymeleaf-3-1-5-template-audit-bui
**Areas discussed:** Audit-Scope, Build-Guard-Regex, TemplateRenderingSmokeIT-Strategy, pageTitle-Pattern

---

## Pre-Discussion Audit Finding

Mechanical grep across all 79 templates (`th:(replace|insert|include)=".*\(.*\$\{.*\}.*\)"`) returned **9 candidates**, not just the 3 known. Broken down:

- **3 known admin (ternary)**: `match-scoring-form.html:3`, `race-scoring-form.html:3`, `season-phase-form.html:3`
- **3 additional admin** (uncovered by audit):
  - `season-phase-group-form.html:3` — ternary (analog to the 3 known)
  - `race-detail.html:3` — `'Race: ' + ${race.homeTeam.shortName} + ' vs ' + ${race.awayTeam?.shortName ?: 'Bye'}` (string-concat + Elvis)
  - `season-detail.html:3` — `'Season: ' + ${season.name} + (phase != null ? ' — ' + ${effectivePhaseLabel} : '')` (string-concat + nested ternary)
- **3 site templates** (plain `${var}`, no operators):
  - `driver-profile.html:3` — `${driver.psnId}`
  - `team-profile.html:3` — `${team.name}`
  - `matchday.html:3` — `${matchday.label}`

Plain `${var}` (pure property access) is currently allowed under Thymeleaf 3.1.5 restricted-mode; only operators/method-calls/external-access are restricted. The 3 site cases are technically safe today.

---

## Audit-Scope: 3 Extra-Funde + Site-Templates

| Option | Description | Selected |
|--------|-------------|----------|
| Alle 6 admin + 3 site preemptiv | All 9 templates get the `pageTitle` controller-side fix. Maximum forward-compat against future Thymeleaf 3.2 stricter mode. ~6 controllers, ~9 templates. | ✓ |
| Alle 6 admin, site bleibt wie ist | Only the 6 admin templates fixed. Site `${var}` cases left alone — currently safe under 3.1.5. SmokeIT proves no breakage. | |
| Nur die 3 bekannten + die 3 admin-Extra, die unter 3.1.5 brechen | Smoke-IT-first on 4.0.6, then fix only what breaks. Minimal-invasive but risks latent data-conditional cases. | |

**User's choice:** Alle 6 admin + 3 site preemptiv (Recommended)
**Notes:** Maximum forward-compat preferred; the cost of fixing 3 extra site templates is trivial vs. the cost of a future 3.2 forced fix run during another feature milestone.

---

## Build-Guard-Regex-Breite

| Option | Description | Selected |
|--------|-------------|----------|
| Breit — jede Expression in Fragment-Param | Pattern: `th:(replace|insert|include)=".*\(.*\$\{.*\}.*\)"` — bans any `${...}` as fragment argument. After Phase 71 the codebase has zero matches; any regression fails build. | ✓ |
| Schmal — nur Ternäre + Elvis | Pattern: `th:(replace|insert|include)=".*\(.*\$\{.*[?:].*\}.*\)"` — only conditional expressions banned. Allows `${var}` plain. More false-negatives. | |
| Breit + Whitelist | Broad regex with `.template-audit-allowlist` file for documented exceptions. Overengineered for single-admin app. | |

**User's choice:** Breit (Recommended)
**Notes:** Pairs with the "fix all 9" audit decision — broad regex is internally consistent and protects against future 3.2 strictness.

| Option | Description | Selected |
|--------|-------------|----------|
| validate (Maven phase) | Runs first, sub-10s fail on regression. Standard for lint/style checks. | ✓ |
| verify | Runs after tests. Late fail, wastes compute. | |
| compile | Between validate and test. No real advantage over validate. | |

**User's choice:** validate (Recommended)
**Notes:** Standard placement for fast-fail static checks.

---

## TemplateRenderingSmokeIT-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| Dynamisch via RequestMappingHandlerMapping | IT iterates all `GET /admin/**` routes; self-maintaining as new controllers ship. | ✓ |
| Hardcoded Liste im IT | Explicit list of admin URLs; easy to read but high drift risk. | |
| Hybrid — dynamisch + Exception-Liste | Dynamic discovery + manual exception list. Middle-ground complexity. | |

**User's choice:** Dynamisch via RequestMappingHandlerMapping (Recommended)
**Notes:** Self-updating is the right call given the broad-regex build-guard — both mechanisms are forward-looking.

| Option | Description | Selected |
|--------|-------------|----------|
| test-Profil + dedizierter SmokeITSeeder | Minimal-complete fixture (1 of each entity). Fast startup, clean isolation. | ✓ |
| dev-Profil + DevDataSeeder | Rich fixture, slow startup, risky on data changes. | |
| test-Profil + TestDataService | Reuses E2E fixture, possibly too heavyweight. | |

**User's choice:** test-Profil + SmokeITSeeder (Recommended)
**Notes:** Aligns with feedback_test_data_isolation — IT has its own isolated fixture, doesn't pollute or depend on E2E test data.

| Option | Description | Selected |
|--------|-------------|----------|
| @Sql/IT-Setup + dynamic {id} resolution via path-var-name map | Seeded UUID constants, IT maps `seasonId → SEASON_SMOKE_ID` etc. | ✓ |
| Routes mit Path-Vars skippen | Skip parameterized routes entirely. Huge coverage gap on /edit pages. | |
| Reflection-basierter Service-Lookup-Resolver | Service-context lookup per path-var-name. Lots of code. | |

**User's choice:** @Sql + path-var-name map (Recommended)
**Notes:** Pragmatic — explicit map of known path-var names (seasonId, raceId, matchdayId, ...) covers the live route surface.

| Option | Description | Selected |
|--------|-------------|----------|
| HTTP 200 + Body !contains TemplateProcessingException | Exact PLAT-06 wording; word-anchored on exception class name. | ✓ |
| HTTP 200 + Body !contains "Exception" | Stricter but risks colliding with legitimate "Exception" usage. | |
| HTTP 200 + Log-Capture auf ERROR-Level | Slf4j capture; more setup, more robust to body changes. | |

**User's choice:** HTTP 200 + Body !contains TemplateProcessingException (Recommended)
**Notes:** Word-anchored on the specific exception class, matches PLAT-06 literally.

---

## pageTitle-Pattern-Implementierung

| Option | Description | Selected |
|--------|-------------|----------|
| Direktes model.addAttribute pro Methode | Each affected handler explicitly sets `model.addAttribute("pageTitle", ...)`. Trivial, readable. | ✓ |
| Zentrale @ModelAttribute pro Controller | Spring resolves automatically per controller class. Elegant but subtle path-var binding bugs. | |
| Dedizierter PageTitleService | New bean. Overengineered for trivial title computation. | |

**User's choice:** Direktes model.addAttribute (Recommended)
**Notes:** Aligns with "thin controllers, no abstraction beyond what the task requires" project principle.

| Option | Description | Selected |
|--------|-------------|----------|
| layout.html `${pageTitle ?: 'CTC Admin'}` | Elvis fallback in layout body (legal under 3.1.5 outside fragment-param context). Crash-safe. | ✓ |
| Smoke-IT erzwingt pageTitle via Reflection-Assertion | Strict, but reflection tests are brittle. | |
| Kein Fallback, harte Convention | Empty browser title if forgotten. | |

**User's choice:** Elvis fallback in layout (Recommended)
**Notes:** Defense-in-depth — if a future handler forgets pageTitle, smoke-IT reveals the empty title visually while the page still renders.

| Option | Description | Selected |
|--------|-------------|----------|
| Page-Generator-Bean (sitegen) | Each of the 3 affected generators adds `context.setVariable("pageTitle", ...)`. Local, clear ownership. | ✓ |
| Zentraler SiteTitleHelper | Overengineered for trivial titles. | |
| `th:with` am body-Tag | Doesn't work — `th:replace` on html-Tag is evaluated before any element-scope `th:with`. | |

**User's choice:** Page-Generator-Bean (Recommended)
**Notes:** Phase 62 D-20 decomposed sitegen into 5 page-generator beans — the right place for site-template pageTitle.

---

## Claude's Discretion

- Exact list of admin GET routes (RequestMappingHandlerMapping discovers at runtime — no enumeration needed during planning)
- SmokeITSeeder UUID constant values (well-known UUIDs like `00000000-0000-0000-0000-000000000001` acceptable)
- `TemplateRenderingSmokeIT` internal structure (`@ParameterizedTest` vs. `Stream<DynamicTest>` factory)
- `exec-maven-plugin` execution `<id>` and goal binding details
- Optional `target/template-audit-report.txt` artefact
- Site layout fallback string ('CTC Admin' vs. 'CTC' vs. 'CTC Manager' — planner decides)

## Deferred Ideas

- Public-site rendering smoke-IT (analog to TemplateRenderingSmokeIT but covering sitegen pages) — candidate for v1.11+
- Maven Enforcer custom-rule module — explicitly out of scope (REQUIREMENTS Out-of-Scope)
- pageTitle internationalization (`messageSource.getMessage(...)`) — no i18n need, all UI is English
- TemplateRenderingSmokeIT extension to POST routes — current scope is GET only
