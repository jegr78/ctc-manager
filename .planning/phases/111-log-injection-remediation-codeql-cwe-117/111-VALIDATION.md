---
phase: 111
slug: log-injection-remediation-codeql-cwe-117
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-31
---

# Phase 111 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ (existing) |
| **Config file** | `pom.xml` (Surefire/Failsafe + JaCoCo + SpotBugs/find-sec-bugs) |
| **Quick run command** | `./mvnw test -Dtest=LogSanitizerTest` |
| **Full suite command** | `./mvnw clean verify -Pe2e` |
| **Estimated runtime** | quick ~5s · full ~15min |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest=LogSanitizerTest` (sanitizer unit test) for sanitizer/util tasks; `./mvnw test -Dtest=<TouchedServiceTest>` for call-site wrapping tasks
- **After every plan wave:** Run `./mvnw clean verify`
- **Before `/gsd:verify-work`:** `./mvnw clean verify -Pe2e` must be green
- **Max feedback latency:** quick < 10s

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 111-01-* | 01 | 1 | SEC-LOG-01 | T-111-01 (CRLF log forging) | `LogSanitizer.sanitize()` replaces CR/LF/`\R` and other C0 control chars with `_`; `null` → `"null"`; plain text (incl. unicode letters) unchanged | unit | `./mvnw test -Dtest=LogSanitizerTest` | ✅ `LogSanitizerTest` (14 execs; +C0/DEL via WR-02) | ✅ green |
| 111-02-* | 02 | 2 | SEC-LOG-02 | T-111-01 | All 29 flagged args (+ same-statement user-controlled siblings) wrapped via `sanitize()`; existing service/controller tests still green | unit + IT | `./mvnw clean verify` | ✅ 17 files wrapped; 203 targeted + full suite green | ✅ green |
| 111-03-* | 03 | 3 | SEC-LOG-03 | T-111-01 | CodeQL re-scan on PR #132 reports 0 open `java/log-injection`; no new `query-filters` entries in `codeql-config.yml` (barrier pack only if empirically required) | CI-only | PR #132 CodeQL run (`gh api .../code-scanning/alerts -f ref=refs/pull/132/merge`) | ✅ CI (analysis `506055308b`) | ✅ green (0 alerts) |
| 111-0x-* | final | last | SEC-LOG-04 | — | SpotBugs/find-sec-bugs gate exits 0; JaCoCo line coverage ≥ 82% | build gate | `./mvnw clean verify -Pe2e` | ✅ existing gate | ✅ green (2472 tests, BugInstance 0, coverage met) |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/main/java/org/ctc/util/LogSanitizer.java` — the utility itself
- [x] `src/test/java/org/ctc/util/LogSanitizerTest.java` — pins SEC-LOG-01 (CR, LF, `\R`, TAB, other C0 control chars → `_`; null → "null"; plain/unicode text unchanged)

*Existing JUnit/AssertJ infrastructure covers all other phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 0 open `java/log-injection` alerts after fix | SEC-LOG-03 | CodeQL runs in CI on the PR, not locally under `mvn verify` | ✅ **VERIFIED 2026-05-31.** CodeQL run `26713071793` (success) → analysis `506055308b` at `refs/pull/132/merge`, `results_count: 0`; `gh api … -f state=open -f ref=refs/pull/132/merge` → **0** `java/log-injection`. Barrier pack NOT needed (`\R` auto-recognised across the helper boundary). NB: query the `refs/pull/132/merge` ref — the no-ref default returns master's pre-merge 29 (auto-resolves on squash-merge). |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (LogSanitizer + its test, created in Plan 01; existing JUnit/AssertJ infra needs no scaffolding)
- [x] No watch-mode flags
- [x] Feedback latency < 10s (quick)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-05-31

---

## Validation Audit 2026-05-31 (post-execution)

State A audit after phase execution + code-review fixes (commit `61ca01c1`).

| Metric | Count |
|--------|-------|
| Requirements audited | 4 (SEC-LOG-01..04) |
| Gaps found | 0 |
| Resolved (tests generated) | 0 (none needed) |
| Escalated to manual-only | 0 |

**Outcome:** all 4 requirements COVERED — SEC-LOG-01/02/04 by automated tests + the `clean verify -Pe2e` gate; SEC-LOG-03 by the CI CodeQL run (CI-by-nature, in Manual-Only, now VERIFIED 0 alerts). No new test scaffolding required; the auditor agent was not spawned (zero MISSING gaps).

**Coverage strengthened during the review pass (no gaps, recorded for trail):**
- WR-02 added a `givenC0OrDelControlChar` parameterized test (NUL/BEL/ESC/DEL via int codes) — closes the second-`replaceAll`-pass coverage hole in SEC-LOG-01.
- WR-01 replaced a tautological UUID assertion with `isEqualTo(uuid.toString())`.
- CR-01 ensured `FileStorageService` actually routes its log argument through `LogSanitizer` (was shadowed by a private `sanitize`), so SEC-LOG-02 coverage for that file is now genuine.

`nyquist_compliant: true` retained.
