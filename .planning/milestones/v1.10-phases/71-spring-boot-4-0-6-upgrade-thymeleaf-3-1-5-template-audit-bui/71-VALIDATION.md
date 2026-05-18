---
phase: 71
slug: spring-boot-4-0-6-upgrade-thymeleaf-3-1-5-template-audit-build-guard
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-18
approved_on: 2026-05-18
audit_method: retroactive
---

# Phase 71 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> **Retroactive audit (State B):** generated 2026-05-18 by Phase 87 / Plan 87-01 against artefacts restored from git ref `60f5f915^`. Wave 0 satisfied retroactively — all referenced test files already exist on disk as of the audit.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test 4.0.6 + Playwright 1.55 (compile-scope) |
| **Config file** | `pom.xml` (Surefire `default-test`, Failsafe `default-it`, exec-maven-plugin `template-fragment-call-guard`) |
| **Quick run command** | `./mvnw test -Dtest='TemplateRenderingSmokeIT'` (single dynamic-route IT) |
| **Full suite command** | `./mvnw verify -Pe2e` (Surefire + Failsafe + Playwright + JaCoCo) |
| **Estimated runtime** | ~30 s (single IT) / ~23 min (full suite, Phase-86 CI median) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest='<TouchedClass>'` (per `.planning/codebase/TESTING.md` §Test Invocation Discipline)
- **After every plan wave:** Run `./mvnw verify` (Surefire + Failsafe, no Playwright) for ~9 min feedback
- **Before `/gsd:verify-work`:** `./mvnw verify -Pe2e` must be green; CI median 23:00 (Phase-86 PR-branch baseline per D-17)
- **Max feedback latency:** 600 s (10 min) — the full Surefire+Failsafe loop

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 71-01-01 | 01 | 1 | PLAT-03 / PLAT-04 | — | 10 admin templates use `layout(${pageTitle}` fragment-call, no `${...}` operators in fragment params | integration | `./mvnw test -Dtest='TemplateRenderingSmokeIT'` | ✅ `src/test/java/org/ctc/admin/controller/integration/TemplateRenderingSmokeIT.java` | ✅ green |
| 71-01-02 | 01 | 1 | PLAT-03 / PLAT-04 | — | 9 admin controllers populate `pageTitle` via `model.addAttribute("pageTitle", ...)` (17 call sites) | integration | `./mvnw test -Dtest='TemplateRenderingSmokeIT'` | ✅ `src/test/java/org/ctc/admin/controller/integration/TemplateRenderingSmokeIT.java` | ✅ green |
| 71-01-03 | 01 | 1 | PLAT-04 | — | `admin/layout.html` uses `th:with="title=${title ?: 'CTC Admin'}"` Elvis null-safety | integration | `./mvnw test -Dtest='TemplateRenderingSmokeIT'` | ✅ `src/test/java/org/ctc/admin/controller/integration/TemplateRenderingSmokeIT.java` (asserts no `TemplateProcessingException` on null-title route) | ✅ green |
| 71-01-04 | 01 | 1 | PLAT-05 | — | `./mvnw verify -Pe2e` BUILD SUCCESS post-fix, JaCoCo ≥ 82 % | manual-only (full-suite gate) | `./mvnw verify -Pe2e` | ✅ CI workflow `.github/workflows/ci.yml` + `pom.xml` `jacoco-maven-plugin` `check` goal | ✅ green |
| 71-02-01 | 02 | 1 | PLAT-04 | — | 7 site templates use `layout(${pageTitle}` fragment-call; `site/matchday.html` line 10 fragment-call inlined to remove `matchCardBody(${race})` ARG | integration (transitive) | `./mvnw verify -Pe2e` (Playwright site-flow exercise + sitegen output emission) | ✅ `src/test/java/org/ctc/sitegen/SiteGenServiceIT.java` + `src/test/java/org/ctc/e2e/SiteGenE2ETest.java` | ✅ green |
| 71-02-02 | 02 | 1 | PLAT-04 | — | 6 sitegen page-generator beans populate `context.setVariable("pageTitle", ...)` (7 call sites) | integration | `./mvnw verify -Pe2e` (sitegen emits templated pages) | ✅ `src/test/java/org/ctc/sitegen/SiteGenServiceIT.java` | ✅ green |
| 71-02-03 | 02 | 1 | PLAT-04 / PLAT-05 | — | `site/layout.html` uses `th:with="title=${title ?: 'CTC'}"` Elvis null-safety; full verify green | manual-only (full-suite gate) | `./mvnw verify -Pe2e` | ✅ CI workflow `.github/workflows/ci.yml` | ✅ green |
| 71-03-01 | 03 | 1 | PLAT-01 / PLAT-02 / PLAT-05 | — | `pom.xml` `<parent>` pins `spring-boot-starter-parent` 4.0.6; `<dependencyManagement>` pins `org.thymeleaf:thymeleaf` 3.1.5.RELEASE; full suite green on the new platform | integration (transitive via Spring context boot) | `./mvnw test -Dtest='TemplateRenderingSmokeIT'` (boots SB 4.0.6 context; failure mode = `BeanCreationException` or class-loader error if version not pinned) | ✅ `src/test/java/org/ctc/admin/controller/integration/TemplateRenderingSmokeIT.java` (also: all 1227+ `@SpringBootTest` ITs transitively load the pinned versions) | ✅ green |
| 71-04-01 | 04 | 1 | PLAT-06 | — | SQL fixture seeds 15-table deterministic-UUID minimal-complete graph in `00000000-0000-0071-*` range | integration | `./mvnw test -Dtest='TemplateRenderingSmokeIT'` (loads fixture via `@Sql(scripts = "/sql/template-rendering-smoke-fixture.sql")`) | ✅ `src/test/resources/sql/template-rendering-smoke-fixture.sql` | ✅ green |
| 71-04-02 | 04 | 1 | PLAT-06 | — | `TemplateRenderingSmokeIT` discovers routes dynamically via `RequestMappingHandlerMapping`, GETs every `/admin/**` route, asserts `status < 500` + word-boundary `\bTemplateProcessingException\b` absent | integration | `./mvnw test -Dtest='TemplateRenderingSmokeIT'` | ✅ `src/test/java/org/ctc/admin/controller/integration/TemplateRenderingSmokeIT.java` (64 dynamic routes, `@Tag("integration")`, `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("dev")` + `@Transactional` + `@Sql` + `@Qualifier("requestMappingHandlerMapping")`) | ✅ green |
| 71-05-01 | 05 | 1 | PLAT-07 | — | `pom.xml` registers `exec-maven-plugin` execution `template-fragment-call-guard` bound to `<phase>validate</phase>`; grep regex `th:(replace|insert|include)="~\{[^"]*\(.*\$\{.*\}.*\)\}"` with `grep -vF 'layout(${pageTitle}'` whitelist; exits 1 on offender | structural (pom.xml schema) | `./mvnw -q validate` (prints `[PLAT-07 build-guard] OK`) | ✅ `pom.xml` lines ~400–430 (`<execution><id>template-fragment-call-guard</id>...<phase>validate</phase>`) | ✅ green |
| 71-05-02 | 05 | 1 | PLAT-07 | — | Build-guard fires on deliberately-injected offender (negative test); recovers after cleanup | manual (deliberate-injection smoke documented in 71-05-SUMMARY) | `./mvnw -q validate` against an injected offender pattern | ✅ Live verification recorded in `71-05-SUMMARY.md` §"Deliberate injection test"; structural pom.xml binding is the persistent evidence | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

**Satisfied retroactively** — all referenced test files exist on disk as of Phase 87 / Plan 87-01. Original Phase 71 execution (2026-05-11) shipped the test infrastructure simultaneously with the implementation per the v1.10 phase contract; Wave 0 was completed in-flight rather than as a separate prep wave.

- [x] `src/test/java/org/ctc/admin/controller/integration/TemplateRenderingSmokeIT.java` — covers PLAT-06 (created 71-04 Task 2)
- [x] `src/test/resources/sql/template-rendering-smoke-fixture.sql` — covers PLAT-06 fixture (created 71-04 Task 1)
- [x] `pom.xml` `template-fragment-call-guard` execution — covers PLAT-07 (created 71-05 Task 1)

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual sweep of all admin routes under SB 4.0.6 | PLAT-05 | Pixel-level regression checks for layout drift are not asserted in JUnit; covered by `playwright-cli` manual run | Start `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo`, then `playwright-cli open http://localhost:9090/admin/...` over the 64 routes. Verified live 2026-05-11 per `71-01-SUMMARY.md` Task 4 |
| Deliberate-injection smoke for PLAT-07 | PLAT-07 | One-shot live test of the build-guard; persistent evidence is the pom.xml binding + the SUMMARY narrative | Inject a `<th:block th:replace="~{layout :: layout(${bad ? 'A' : 'B'}, ~{::section})}"/>` into any template; run `./mvnw -q validate`; expect exit 1 + `[PLAT-07 build-guard] Forbidden Thymeleaf fragment-call expression detected`. Verified live 2026-05-11 per `71-05-SUMMARY.md` |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or **post-hoc evidence** (Wave 0 satisfied retroactively — all referenced test files now exist on disk)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (verified post-execution against the 12 Per-Task Map rows above)
- [x] Wave 0 covers all MISSING references **(retroactively — no gap tests required; all PLAT-01..PLAT-07 requirements classified COVERED by `gsd-nyquist-auditor` 2026-05-18)**
- [x] No watch-mode flags (Maven CLI invocations only)
- [x] Feedback latency < 600 s (full Surefire+Failsafe loop ~9 min; quick run `./mvnw test -Dtest='TemplateRenderingSmokeIT'` ~30 s)
- [x] `nyquist_compliant: true` set in frontmatter

---

## Validation Audit 2026-05-18

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

**Classification per PLAT requirement (gsd-nyquist-auditor verdict, retroactive audit):**

| REQ-ID | Verdict | Evidence path |
|--------|---------|---------------|
| PLAT-01 | COVERED (transitive) | `pom.xml` line 8 `<version>4.0.6</version>`; every `@SpringBootTest` boots the pinned platform — failure mode would surface as `BeanCreationException` |
| PLAT-02 | COVERED (transitive) | `pom.xml` `<dependencyManagement>` pins `org.thymeleaf:thymeleaf:3.1.5.RELEASE`; `TemplateRenderingSmokeIT` renders 64 routes — a 3.1.5 SpEL incompatibility would surface as `TemplateProcessingException` |
| PLAT-03 | COVERED | 17 `addAttribute("pageTitle", ...)` call sites across 9 admin controllers; 17 templates match `layout(${pageTitle}`; `TemplateRenderingSmokeIT` exercises all admin GET routes |
| PLAT-04 | COVERED | Audit returns 17 `layout(${pageTitle}` matches and 0 forbidden D-05-regex offenders under `src/main/resources/templates/`; PLAT-07 build-guard fences against regression |
| PLAT-05 | COVERED (CI gate) | `./mvnw verify -Pe2e` BUILD SUCCESS per `71-03-SUMMARY.md` (1227 Surefire + 112 Failsafe + 31 Playwright); JaCoCo LINE 89.44 % >> 82 % gate |
| PLAT-06 | COVERED | `src/test/java/org/ctc/admin/controller/integration/TemplateRenderingSmokeIT.java` — `@Tag("integration")`, dynamic-route discovery via `RequestMappingHandlerMapping`, 64 routes asserted, D-11a word-boundary `\bTemplateProcessingException\b` assertion |
| PLAT-07 | COVERED (structural) | `pom.xml` `exec-maven-plugin` execution `template-fragment-call-guard` bound to `<phase>validate</phase>`; corrected D-05 regex `th:(replace\|insert\|include)="~\{[^"]*\(.*\$\{.*\}.*\)\}"` with `grep -vF 'layout(${pageTitle}'` whitelist; deliberate-injection live smoke recorded in `71-05-SUMMARY.md` |

**Auditor return shape:** `## GAPS FILLED` with 0 generated tests.
**Adversarial stance honored:** every PLAT requirement classified COVERED only after locating a real test file path or pom.xml binding that fails on regression — no requirement was approved on prose alone.

**CI evidence:** Run-id `26008754136` (workflow_dispatch on `gsd/v1.11-tooling-and-cleanup` @ `b7f20b53`, conclusion: success, 2026-05-18T01:30:27Z). Originating v1.10 verification: `71-VERIFICATION.md` `status: passed, must_haves_verified: 7/7, score: 100%` (2026-05-11) — captured during the in-flight Phase 71 execution under Spring Boot 4.0.6 + Thymeleaf 3.1.5.RELEASE.

**Approval:** approved 2026-05-18 — retroactive audit via Phase 87 / Plan 87-01
