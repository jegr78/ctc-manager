---
phase: 67
slug: comment-cleanup-resweep
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-07
---

# Phase 67 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + AssertJ (Surefire); Playwright (Failsafe `-Pe2e`, NOT in this gate) |
| **Config file** | `pom.xml` (Surefire, JaCoCo) |
| **Quick run command** | `./mvnw test` (Surefire only) |
| **Full suite command** | `./mvnw verify` (Surefire + JaCoCo BUNDLE LINE ≥ 0.82 gate) |
| **Estimated runtime** | Quick: ~2–3 min · Full: ~3–5 min |

E2E (`./mvnw verify -Pe2e`) is UAT-only per `feedback_e2e_verification.md`. Not part of this phase's gate.

---

## Sampling Rate

- **Per-file edit (within a plan):** `git diff --stat` per file to confirm comments-only shape; no test re-run required for pure comment changes.
- **After production-code edits in a service/controller:** `./mvnw test -Dtest=<RelevantTest>` to confirm no accidental code change.
- **Per-plan commit:** `./mvnw test` (Surefire only). Quick gate — all tests compile and pass.
- **Phase gate (after Plan 67-03):** ONE `./mvnw verify` for the full Surefire + JaCoCo gate.
- **Max feedback latency:** ~10 s for the smallest `-Dtest=...` quick check.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 67-01-* | 01 | 1 | D-01..D-05 (production sweep) | T-67-01 (info-disclosure mitigation) | No phase / artifact / decoration noise in production | grep gate + behavior gate | `./mvnw test` | ✅ exists | ⬜ pending |
| 67-02-* | 02 | 2 | D-01..D-05 (templates sweep) | — | Templates free of decision-attribution `<!-- -->` markers | grep gate | `./mvnw test` | ✅ exists | ⬜ pending |
| 67-03-* | 03 | 3 | D-01..D-05 + D-06 (tests sweep + BDD preservation) | — | Tests sweep without breaking 1,899 BDD markers | grep gate + BDD-preserve gate + behavior gate | `./mvnw verify` | ✅ exists | ⬜ pending |
| 67-final | gate | 3 | D-19 + D-20 | — | All quantitative + behavior gates pass | gate | `./mvnw verify` | ✅ exists | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Quantitative grep gates (D-19 — verified runnable)

| # | Gate | Command | Expected |
|---|------|---------|---------:|
| 1 | Phase-attribution markers (Phases 50–69) | `grep -rn "// Phase [56][0-9]" src/main src/test \| wc -l` | **0** |
| 2 | Artifact references | `grep -rn "// per RESEARCH.md\|// per CONTEXT.md\|// per CLAUDE.md\|// per ROADMAP.md" src/main src/test \| wc -l` | **0** |
| 3 | Gap-tracking remnants | `grep -rn "// gap-[0-9]" src/main src/test \| wc -l` | **0** |
| 4 | Long decoration separators (≥ 20 chars) | `grep -rEn "^[[:space:]]*//[[:space:]]*[-=*#]{20,}" src/main src/test \| wc -l` | **0** |
| 5 | Short 3-dash sectional separators | `grep -rcE "^\s*// ---" src/main src/test \| awk -F: 'BEGIN{s=0} {s+=$2} END{print s}'` | **0** |
| 6 | BDD-marker preservation (canonical) | `grep -rn "^	*// given\|^	*// when\|^	*// then" src/test \| wc -l` | **≥ 1899** |
| 7 | BDD-marker preservation (looser whitespace) | `grep -rEn "^[[:space:]]*//[[:space:]]*(given\|when\|then\|when / then)" src/test/java \| wc -l` | **≥ 3103** |

False-positive guard: `Gt7SyncService.java:82,103,124` `// Phase 1:` / `// Phase 2:` / `// Phase 3:` are **algorithm-step labels**, NOT phase attribution. Gate #1's regex `// Phase [56][0-9]` correctly excludes them (single-digit phase numbers don't match `[56][0-9]`).

---

## Behavior gates (D-20)

| Gate | Command | Expected |
|------|---------|----------|
| Maven verify exit code | `./mvnw verify` | exit 0 |
| Tests-run count | Surefire stdout | **unchanged** from Phase-66 baseline (1,231) |
| JaCoCo line coverage | `target/site/jacoco/index.html` BUNDLE LINE | **≥ 0.82** (current baseline 0.8561) |

---

## Wave 0 Requirements

None — comments-only diff. Existing test infrastructure covers all phase requirements; no new fixtures, no new tests, no framework install. The D-19 grep gates ARE the new validation surface and they're plain shell pipelines.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Spot-check that V4/V5 Flyway Javadoc retains technical contract | D-22 | Borderline judgement — automated grep can't distinguish "history" from "contract" | After Plan 67-01 commit, read `V4__MigrateSeasonsToPhases.java` and `V5__nullable_legacy_scoring_columns.java` Javadoc. Confirm: each migration's purpose + ordering invariants + dialect notes are preserved; phase attribution and historical narrative are gone. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or grep-gate verify
- [ ] Sampling continuity: each plan's commit is preceded by `./mvnw test` (per CONTEXT.md D-17)
- [ ] No new fixtures needed (Wave 0 = empty)
- [ ] No watch-mode flags
- [ ] Feedback latency < 10s for the smallest quick check; full gate < 5 min
- [ ] `nyquist_compliant: true` set in frontmatter (after Plan 67-03 verifies all D-19 + D-20 gates green)

**Approval:** pending
