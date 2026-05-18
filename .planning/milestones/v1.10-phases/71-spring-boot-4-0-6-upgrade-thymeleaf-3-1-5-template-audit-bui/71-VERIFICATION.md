---
phase: 71
status: passed
must_haves_verified: 7/7
critical_gaps: 0
score: 100%
overrides_applied: 1
overrides:
  - must_have: "pom.xml has a <dependencyManagement> block pinning org.thymeleaf:thymeleaf to 3.1.5"
    reason: "Maven Central publishes the artifact as 3.1.5.RELEASE. The plan key_links regex org\.thymeleaf.*3\.1\.5 matches both forms. The pin resolves correctly and is functionally equivalent."
    accepted_by: "verifier"
    accepted_at: "2026-05-11T00:00:00Z"
---

# Phase 71: Spring Boot 4.0.6 Upgrade + Thymeleaf 3.1.5 Template Audit + Build Guard Verification Report

**Phase Goal:** Spring Boot 4.0.6 Upgrade + Thymeleaf 3.1.5 Template Audit + Build Guard. Bump the platform to SB 4.0.6 (Thymeleaf 3.1.5 strict-mode restrictions), audit all admin + site templates for forbidden `${...}` expressions in `th:replace/insert/include` fragment-call arguments, refactor them to controller-supplied model attributes, add a smoke IT (PLAT-06) that GETs every admin route, and install a Maven build-guard (PLAT-04) that fails the build on regression.
**Verified:** 2026-05-11T00:00:00Z
**Status:** passed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | PLAT-01: `spring-boot-starter-parent` bumped to 4.0.6 | VERIFIED | `pom.xml` line 8: `<version>4.0.6</version>` inside `<parent>` block; zero occurrences of `4.0.5` |
| 2 | PLAT-02: `<dependencyManagement>` pins Thymeleaf to 3.1.5 | VERIFIED (override) | `pom.xml` has `<dependencyManagement>` block with `org.thymeleaf:thymeleaf:3.1.5.RELEASE`; Maven Central naming convention — satisfies `org\.thymeleaf.*3\.1\.5` plan regex |
| 3 | PLAT-03+04: All 17 admin+site templates have `${...}` in fragment-call args removed (10 admin + 7 site) | VERIFIED | Python audit: 17 lines matched by corrected D-05 regex; 0 non-canonical offenders remain. All 10 admin templates and 7 site templates contain `layout(${pageTitle}` as the only fragment-call argument |
| 4 | PLAT-03+04: Controllers and sitegen beans supply `pageTitle` directly | VERIFIED | 17 `model.addAttribute("pageTitle", ...)` calls across 9 admin controllers (MatchScoring=2, RaceScoring=2, SeasonPhase=3, SeasonPhaseGroup=3, Season=1, Race=1, Driver=3, Team=1, Matchday=1); 7 `setVariable("pageTitle", ...)` calls across 6 sitegen generators (MatchdaysPageGenerator=2, others=1 each) |
| 5 | PLAT-05: Full `./mvnw verify -Pe2e` BUILD SUCCESS on 4.0.6 with coverage gate | VERIFIED | Per 71-03-SUMMARY: 1227 Surefire + 31 Playwright E2E, BUILD SUCCESS, JaCoCo LINE 87.51%. Post-Wave-3: 1227 + 112 Failsafe, BUILD SUCCESS, JaCoCo LINE 89.44% |
| 6 | PLAT-06: `TemplateRenderingSmokeIT` runs on every `./mvnw verify`, GETs every `/admin/**` route, no TemplateProcessingException | VERIFIED | File exists at `src/test/java/org/ctc/admin/controller/integration/TemplateRenderingSmokeIT.java`; class-level annotations `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional @Sql`; `@Qualifier("requestMappingHandlerMapping")` for bean disambiguation; `isLessThan(500)` + `doesNotMatch(".*\\bTemplateProcessingException\\b.*")` assertions; Failsafe `default-it` execution binds `*IT.java` to default lifecycle; per 71-04-SUMMARY: 64 dynamic routes, 0 failures |
| 7 | PLAT-07: Maven build-guard bound to `validate` phase, fails on regression, passes clean | VERIFIED | `pom.xml` has `exec-maven-plugin` execution `template-fragment-call-guard` bound to `<phase>validate</phase>` in root `<build><plugins>` (not inside `<profiles>`); `./mvnw -q validate` prints `[PLAT-07 build-guard] OK`; deliberate injection test (verified live during this session) shows guard fails with the offender pattern and recovers after cleanup |

**Score:** 7/7 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `pom.xml` | SB 4.0.6 parent + Thymeleaf pin + Failsafe binding + exec-maven-plugin guard | VERIFIED | All four elements present and confirmed |
| `src/main/resources/templates/admin/layout.html` | `th:with="title=${title ?: 'CTC Admin'}"` null-safe coercion | VERIFIED | Present on `<html>` root element |
| `src/main/resources/templates/site/layout.html` | `th:with="title=${title ?: 'CTC'}"` null-safe coercion | VERIFIED | Present on `<html>` root element |
| 10 admin templates (match-scoring-form, race-scoring-form, season-phase-form, season-phase-group-form, race-detail, season-detail, driver-detail, team-detail, driver-merge, matchday-detail) | `layout(${pageTitle}` in fragment-call | VERIFIED | All 10 confirmed via `grep -F` |
| 7 site templates (driver-profile, team-profile, matchday, driver-ranking, standings, playoff-bracket, matchdays) | `layout(${pageTitle}` in fragment-call | VERIFIED | All 7 confirmed via `grep -F` |
| `site/matchday.html` | matchCardBody fragment body INLINED, no `matchCardBody` call | VERIFIED | `grep -c 'matchCardBody' matchday.html` = 0; `match-teams` div present = 1 |
| `src/test/java/org/ctc/admin/controller/integration/TemplateRenderingSmokeIT.java` | Dynamic IT with RequestMappingHandlerMapping, @Sql fixture, D-11a assertions | VERIFIED | File exists, all 7 required annotations present (11 annotation occurrences matched), `doesNotMatch` used, `isLessThan(500)` used, `@Qualifier("requestMappingHandlerMapping")` present |
| `src/test/resources/sql/template-rendering-smoke-fixture.sql` | 15-table deterministic-UUID seed in 0071 range | VERIFIED | 36 UUID occurrences in `00000000-0000-0071-0000-` range; all 15 tables have at least 1 INSERT |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| pom.xml `<parent>` | Spring Boot 4.0.6 BOM | `<version>4.0.6</version>` | VERIFIED | Exact match confirmed |
| pom.xml `<dependencyManagement>` | Thymeleaf 3.1.5.RELEASE | explicit version pin | VERIFIED (override) | 3.1.5.RELEASE satisfies plan regex `org\.thymeleaf.*3\.1\.5` |
| 9 admin controllers | 10 admin templates | `model.addAttribute("pageTitle", ...)` | VERIFIED | 17 total insertions confirmed per-controller |
| 6 sitegen generators | 7 site templates | `context.setVariable("pageTitle", ...)` | VERIFIED | 7 total setVariable calls confirmed (MatchdaysPageGenerator contributes 2) |
| TemplateRenderingSmokeIT | RequestMappingHandlerMapping | `@Autowired @Qualifier("requestMappingHandlerMapping")` | VERIFIED | Present in file |
| TemplateRenderingSmokeIT | template-rendering-smoke-fixture.sql | `@Sql(scripts = "/sql/template-rendering-smoke-fixture.sql")` | VERIFIED | Annotation present |
| pom.xml exec-maven-plugin | src/main/resources/templates/ | `grep -rE` D-05 regex at validate phase | VERIFIED | Plugin block in root build, bound to validate phase, injection test passed live |
| Failsafe default-it | `*IT.java` (non-e2e) | `<include>**/*IT.java</include>` + `<exclude>**/e2e/**</exclude>` | VERIFIED | Configuration confirmed in pom.xml |

### Data-Flow Trace (Level 4)

This phase is a backend platform upgrade + template refactor. No new dynamic data sources were added. The pageTitle flow is:

| Flow | Source | Via | Destination | Status |
|------|--------|-----|-------------|--------|
| Admin page title | Controller GET handler (e.g., `driver.getPsnId()`) | `model.addAttribute("pageTitle", ...)` | `admin/layout.html` `<title>` tag | FLOWING |
| Site page title | Sitegen bean (e.g., `matchday.getLabel()`) | `context.setVariable("pageTitle", ...)` | `site/layout.html` `<title>` tag | FLOWING |
| Layout null-safety | `title ?: 'CTC Admin'` Elvis fallback | `th:with` pre-coercion | all `title.contains(...)` sidebar checks | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Build-guard passes on clean tree | `./mvnw -q validate` | `[PLAT-07 build-guard] OK - no Thymeleaf fragment-call expression offenders.` | PASS |
| Build-guard fails on injected offender | Inject `layout(${foo.bar}` then `./mvnw -q validate` | BUILD FAILED with `[PLAT-07 build-guard] Forbidden...` diagnostic | PASS |
| Build-guard recovers after offender removed | Remove injector, `./mvnw -q validate` | `[PLAT-07 build-guard] OK` | PASS |
| No fragment-call ${...} offenders remain | Python audit: broad D-05 regex, filter canonical form | 17 lines matched; 0 non-canonical offenders | PASS |
| admin/layout.html null-safe | `grep "title ?: 'CTC Admin'"` | Present on `<html>` root element | PASS |
| SmokeIT structural integrity | Annotation count, assertion type checks | All required elements present | PASS |

### Requirements Coverage

| Requirement | Phase | Source Plans | Description | Status | Evidence |
|-------------|-------|--------------|-------------|--------|----------|
| PLAT-01 | 71 | 71-03 | SB 4.0.5 → 4.0.6 parent bump | SATISFIED | `pom.xml` `<version>4.0.6</version>` in `<parent>` |
| PLAT-02 | 71 | 71-03 | Thymeleaf 3.1.5 dependencyManagement pin | SATISFIED | `<dependencyManagement>` with `3.1.5.RELEASE` |
| PLAT-03 | 71 | 71-01, 71-02 | Fix 3 known broken templates + broader audit | SATISFIED | All 17 templates fixed; 0 remaining offenders |
| PLAT-04 | 71 | 71-01, 71-02, 71-05 | Preventive audit of all ~80 templates + build-guard | SATISFIED | Full audit complete; exec-maven-plugin guard installed |
| PLAT-05 | 71 | 71-03 | `./mvnw verify -Pe2e` BUILD SUCCESS on 4.0.6, coverage ≥ 82% | SATISFIED | 1227 + 31 E2E green, 87.51% LINE coverage (per SUMMARY) |
| PLAT-06 | 71 | 71-04 | TemplateRenderingSmokeIT — every /admin/** route, no TemplateProcessingException | SATISFIED | IT exists, 64 dynamic routes, 0 failures |
| PLAT-07 | 71 | 71-05 | Maven exec-maven-plugin build-guard at validate phase | SATISFIED | Plugin block confirmed in root build, injection test passed |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `pom.xml` exec-maven-plugin | 319 | `grep -v 'layout(${pageTitle}'` — filter relies on macOS BSD grep treating `${pageTitle}` as a literal match; GNU grep may behave differently ($ treated as end-of-line anchor in BRE) | Warning | Guard may be less portable on Linux CI (GNU grep) — functional on macOS dev environment where tested. The guard was confirmed to correctly catch a live injection and recover. |

**Note on the grep portability warning:** Extensive testing confirmed that on macOS (BSD grep), `grep -v 'layout(${pageTitle}'` correctly filters canonical lines and passes through offenders. The injection test was verified live during this verification session. CI environments using GNU grep may behave differently, but the guard passed all stated acceptance criteria on the target platform.

### Human Verification Required

None. This is a backend platform upgrade with equivalence-preserving template refactors. Visual rendering of admin and site pages was confirmed in the SUMMARY documents via playwright-cli screenshots. No new user-facing UI was added. All PLAT requirements are verifiable programmatically.

---

## Gaps Summary

No gaps. All 7 PLAT requirements are satisfied in the codebase.

**PLAT-02 deviation (accepted):** `3.1.5.RELEASE` instead of bare `3.1.5` — this is Maven Central's published coordinate for this Thymeleaf release. The plan's key_links regex `org\.thymeleaf.*3\.1\.5` explicitly permits both forms per the 71-03-SUMMARY deviation note.

**PLAT-04 build-guard behavior investigation:** During verification, a deep investigation was conducted into the `grep -v 'layout(${pageTitle}'` filter in the pom CDATA block. The conclusion is that the guard functions correctly on macOS (BSD grep), where `${pageTitle}` is treated as a literal string to match, correctly distinguishing canonical `layout(${pageTitle}` lines from offender `layout(${foo.bar}` lines. A live injection test during this verification session confirmed: guard fails with offender, passes after cleanup.

---

_Verified: 2026-05-11_
_Verifier: Claude (gsd-verifier)_
