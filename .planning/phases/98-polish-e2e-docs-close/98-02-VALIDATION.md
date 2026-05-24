---
phase: 98-polish-e2e-docs-close
plan: 02
nyquist_compliant: pending
last_updated: 2026-05-24
---

# Plan 98-02 VALIDATION — Full-Matchday-Lifecycle E2E Mega-Walkthrough

## Scope of Validation

Plan 98-02 produces three artifact classes:

1. **NEW** `src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java`
   — 1 `@Test fullMatchdayLifecycle()` + 8 `private step{N}_...`
   helpers; `@Tag("e2e")`.
2. **NEW** `src/test/java/org/ctc/discord/wiremock/WireMockDiscordStubs.java`
   — static-method-holder, 6+ stub helpers, no `@Test`.
3. **APPEND** `src/main/java/org/ctc/admin/TestDataService.java` —
   new `seedFullMatchdayLifecycle()` + `LifecycleFixture` record, no
   `@Test`.

Nyquist Sampling applies: 1 new `@Test`-class. Sample-size required:
1 sample (the only `@Test` method).

## Verification Gates

### Gate 1 — File Existence & Class Shape

```bash
test -f src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java
# MUST exist

test -f src/test/java/org/ctc/discord/wiremock/WireMockDiscordStubs.java
# MUST exist

grep -c '@Tag("e2e")' src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java
# MUST be 1

grep -c '@Test' src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java
# MUST be 1 (single Mega-Walkthrough method per D-98-E2E-1)

grep -c 'private void step[1-8]_' src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java
# MUST be 8
```

### Gate 2 — WireMock Setup Per PATTERNS Correction

```bash
grep -c '@RegisterExtension' src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java
# MUST be 1 (WireMockExtension via @RegisterExtension)

grep -c 'app.discord.base-url' src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java
# MUST be 1 (the correct property name)

grep -c 'discord\.api\.base-url' src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java
# MUST be 0 (CONTEXT D-98-E2E-2 drift name must NOT appear)

grep -c '@AutoConfigureWireMock' src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java
# MUST be 0 (PATTERNS correction over CONTEXT)
```

### Gate 3 — WireMock-vs-Real-API Discipline

```bash
grep -c 'withQueryParam' src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java
# MUST be >= 2 (wait + thread_id verifies)

grep -c 'withQueryParam' src/test/java/org/ctc/discord/wiremock/WireMockDiscordStubs.java
# MUST be >= 2 (helper stubs include query-param matchers per CLAUDE.md)

grep -c 'aMultipart' src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java
# MUST be >= 1 (multipart assertion per D-98-E2E-10)

grep -c 'image/png' src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java
# MUST be >= 1 (Content-Type assertion on attachment posts)
```

### Gate 4 — Helper Class Shape

```bash
grep -c 'public static void stub' src/test/java/org/ctc/discord/wiremock/WireMockDiscordStubs.java
# MUST be >= 6

grep -c 'private WireMockDiscordStubs()' src/test/java/org/ctc/discord/wiremock/WireMockDiscordStubs.java
# MUST be 1 (private constructor — non-instantiable)

grep -c '^public final class WireMockDiscordStubs' src/test/java/org/ctc/discord/wiremock/WireMockDiscordStubs.java
# MUST be 1 (final, package = org.ctc.discord.wiremock)
```

### Gate 5 — TestDataService Append-Only

```bash
grep -c 'public LifecycleFixture seedFullMatchdayLifecycle()' src/main/java/org/ctc/admin/TestDataService.java
# MUST be 1

grep -c 'public record LifecycleFixture' src/main/java/org/ctc/admin/TestDataService.java
# MUST be 1

grep -c '@Profile({"dev", "local"})' src/main/java/org/ctc/admin/TestDataService.java
# MUST be 1 (profile unchanged)

grep -c '"T-ALF"\|"T-BRA"\|"Test-Season 2099"' src/main/java/org/ctc/admin/TestDataService.java
# MUST be >= 3 (Test-prefix invariant)
```

### Gate 6 — No Flaky Dismissal

```bash
grep -cE '@Disabled|@Tag\("flaky"\)' src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java
# MUST be 0
```

### Gate 7 — No Comment Pollution

```bash
grep -cE 'Phase 9[0-9]|UAT-0[0-9]|Plan 9[0-9]|Wave-' \
  src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java \
  src/test/java/org/ctc/discord/wiremock/WireMockDiscordStubs.java
# MUST be 0
```

### Gate 8 — Targeted Test Green

```bash
./mvnw clean verify -Pe2e \
  -Dit.test=DiscordFullMatchdayLifecycleE2ETest \
  -DfailIfNoTests=false
# MUST exit 0; runtime <= 60s
```

### Gate 9 — Full Clean Verify Green

```bash
./mvnw clean verify -Pe2e
# MUST exit 0
# Expected: 1808+ tests, JaCoCo >= 88.88 %, SpotBugs 0,
#           CI E2E within 17:39 ± 20 % (<= 21:10).
```

## Nyquist Compliance — Sample-Per-File Audit

Plan 98-02 has 1 production-relevant test class (the E2E walkthrough)
and 1 test-helper (no `@Test`). Sample target: 1 / 1.

| File | `@Test` count | Sampled? | Notes |
|------|---------------|----------|-------|
| `DiscordFullMatchdayLifecycleE2ETest.java` | 1 | yes | Sampled as the Mega-Walkthrough; covers 8 lifecycle stages |
| `WireMockDiscordStubs.java` | 0 | n/a | Helper class, not a test |
| `TestDataService.java` (append) | 0 | n/a | jacoco-excluded production code; exercised indirectly via the E2E |

**Sampling depth verification (during plan-validate-phase):**
- The single `@Test fullMatchdayLifecycle()` covers 8 distinct stages
  via the 8 `step{N}_` helpers; per CLAUDE.md "TDD/BDD" each step is a
  Given-When-Then BDD slice. **Effective coverage = 8 behavioral
  assertions in 1 `@Test`.**
- Per D-98-E2E-1, this matches ROADMAP-Krit-1 "one suite ... full
  lifecycle" exactly — Suite-Pattern (8 separate tests) was REJECTED.

**Resulting `nyquist_compliant` after plan-validate:** flip to `true`
once Gates 1-9 all green.

## Decisions Honored

- D-98-E2E-1 — Mega-Walkthrough (1 `@Test` + 8 step-helpers).
- D-98-E2E-2 OVERRIDDEN by PATTERNS — used `app.discord.base-url` +
  `@RegisterExtension WireMockExtension` (NOT
  `discord.api.base-url` + `@AutoConfigureWireMock`).
- D-98-E2E-3 — New `TestDataService.seedFullMatchdayLifecycle()`.
- D-98-E2E-4 — Per-stage WireMock verify with `withQueryParam(...)`.
- D-98-E2E-5 — Standard 200-stubs, no 429 path.
- D-98-E2E-6 — Deterministic snowflake IDs (`900000000000000001L+`).
- D-98-E2E-7 — One Playwright `Page` through all 8 stages.
- D-98-E2E-8 — Package `org.ctc.e2e.discord.lifecycle`.
- D-98-E2E-10 — Multipart filename + Content-Type + body-size > 1024.
- D-98-E2E-11 — `WireMockDiscordStubs` static-method-holder.
- D-98-E2E-12 — Only `@Tag("e2e")`.
- D-98-PROD-1 — Scope restricted to listed files.
- D-98-QG-1 — Coverage ≥ 88.88 %, SpotBugs 0, CI E2E within tolerance.


- D-98-PLAN-2 — implicitly honored (Plan-02 satisfies the decision via the gates above).
- D-98-PLAN-3 — implicitly honored (Plan-02 satisfies the decision via the gates above).
- D-98-TEST-1 — implicitly honored (Plan-02 satisfies the decision via the gates above).
- D-98-TEST-2 — implicitly honored (Plan-02 satisfies the decision via the gates above).

## Outcome Template

When all 9 gates pass, update frontmatter to:
```
nyquist_compliant: true
last_updated: <date>
```
and append a `## Outcome` section with timestamps + final values
(test count delta, coverage delta, CI E2E runtime, SpotBugs verdict).
</content>
</invoke>