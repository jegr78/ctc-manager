---
phase: 111
slug: log-injection-remediation-codeql-cwe-117
status: draft
nyquist_compliant: false
wave_0_complete: false
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
| 111-01-* | 01 | 1 | SEC-LOG-01 | T-111-01 (CRLF log forging) | `LogSanitizer.sanitize()` replaces CR/LF/`\R` and other C0 control chars with `_`; `null` → `"null"`; plain text (incl. unicode letters) unchanged | unit | `./mvnw test -Dtest=LogSanitizerTest` | ❌ Wave 0 | ⬜ pending |
| 111-02-* | 02 | 2 | SEC-LOG-02 | T-111-01 | All 29 flagged args (+ same-statement user-controlled siblings) wrapped via `sanitize()`; existing service/controller tests still green | unit + IT | `./mvnw clean verify` | ✅ existing tests cover the 17 files | ⬜ pending |
| 111-03-* | 03 | 3 | SEC-LOG-03 | T-111-01 | CodeQL re-scan on PR #132 reports 0 open `java/log-injection`; no new `query-filters` entries in `codeql-config.yml` (barrier pack only if empirically required) | CI-only | PR #132 CodeQL run (`gh api .../code-scanning/alerts`) | ❌ manual / CI | ⬜ pending |
| 111-0x-* | final | last | SEC-LOG-04 | — | SpotBugs/find-sec-bugs gate exits 0; JaCoCo line coverage ≥ 82% | build gate | `./mvnw clean verify -Pe2e` | ✅ existing gate | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/main/java/org/ctc/util/LogSanitizer.java` — the utility itself
- [ ] `src/test/java/org/ctc/util/LogSanitizerTest.java` — pins SEC-LOG-01 (CR, LF, `\R`, TAB, other C0 control chars → `_`; null → "null"; plain/unicode text unchanged)

*Existing JUnit/AssertJ infrastructure covers all other phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 0 open `java/log-injection` alerts after fix | SEC-LOG-03 | CodeQL runs in CI on the PR, not locally under `mvn verify` | After pushing the fix to `gsd/v1.15-ci-and-race-defaults`, wait for the CodeQL workflow on PR #132, then `gh api repos/:owner/:repo/code-scanning/alerts?state=open` and assert 0 records with `rule.id == java/log-injection`. If alerts persist, the helper-boundary barrier is not recognised → add the `models-as-data` barrier pack and re-scan. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (LogSanitizer + its test)
- [ ] No watch-mode flags
- [ ] Feedback latency < 10s (quick)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
