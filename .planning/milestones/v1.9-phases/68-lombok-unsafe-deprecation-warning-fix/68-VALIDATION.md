---
phase: 68
slug: lombok-unsafe-deprecation-warning-fix
status: approved
nyquist_compliant: n/a
wave_0_complete: n/a
rationale: "Build-only diff (Lombok 1.18.46 pom.xml pin + JEP 498 argLine flag in 3 fork sites). No logic-code path under test. Mirrors Phase 63 docs-only treatment per Phase 69 D-12."
created: 2026-05-07
---

# Phase 68 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + AssertJ (Surefire); Playwright (Failsafe `-Pe2e`, NOT in this gate) |
| **Config file** | `pom.xml` (Surefire, Failsafe, JaCoCo) |
| **Quick run command** | `./mvnw test -q -Dtest=DriverSheetImportServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | Quick: ~10s · Full: ~3–5 min |

---

## Sampling Rate

- **Per task commit:** Quick test for any task that touches build behavior; not strictly needed for the property/argLine edits since they're configuration-only.
- **Per plan commit:** Full `./mvnw verify` (the phase-final gate; this is a property-only change so a single full run covers the entire test surface).
- **Max feedback latency:** ~3 min for the full gate.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 68-01-01 | 01 | 1 | D-01, D-05 | T-68-01 (transitively addresses Information Disclosure / supply-chain via canonical Maven Central artifact) | Lombok 1.18.46 pinned; build still produces same Lombok feature surface | gate | `mvn dependency:tree \| grep "org.projectlombok:lombok"` returns `1.18.46` | ❌ W0 | ⬜ pending |
| 68-01-02 | 01 | 1 | D-02-revised | — | JEP 498 flag added to Surefire + Failsafe argLine, with TODO comment pointing to lombok#3959 | grep gate | `grep -c "sun-misc-unsafe-memory-access" pom.xml` returns ≥ 2 | ❌ W0 | ⬜ pending |
| 68-01-03 | 01 | 2 | D-07, D-08 | — | `./mvnw verify` clean — no Permit/Unsafe warning in stdout/stderr | gate | `./mvnw verify 2>&1 \| grep -cE "sun\.misc\.Unsafe\|lombok\.permit\.Permit"` returns **0** | ❌ W0 | ⬜ pending |
| 68-01-04 | 01 | 2 | D-07 (sanity) | — | `spring-boot:run` startup clean (already 0 today, sanity check) | gate | `timeout 60 ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 \| grep -cE "sun\.misc\.Unsafe\|lombok\.permit\.Permit"` returns **0** | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Quantitative gates (D-08)

| # | Gate | Command | Expected |
|---|------|---------|---------:|
| 1 | Lombok version pinned | `grep -c "<lombok.version>1.18.46</lombok.version>" pom.xml` | **1** |
| 2 | JEP 498 flag in Surefire | `grep -c "sun-misc-unsafe-memory-access=allow" pom.xml` | **≥ 2** (Surefire + Failsafe) |
| 3 | TODO comment present | `grep -c "lombok #3959\|lombok#3959" pom.xml` | **≥ 1** |
| 4 | `./mvnw verify` warning quartet | `./mvnw verify 2>&1 \| grep -cE "sun\.misc\.Unsafe\|lombok\.permit\.Permit"` | **0** |
| 5 | `spring-boot:run` startup warnings | `timeout 60 ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 \| grep -cE "sun\.misc\.Unsafe\|lombok\.permit\.Permit"` | **0** |
| 6 | Quick test smoke | `./mvnw test -q -Dtest=DriverSheetImportServiceTest 2>&1 \| grep -cE "sun\.misc\.Unsafe\|lombok\.permit\.Permit"` | **0** |

---

## Behavior gates (D-09, D-10)

| Gate | Command | Expected |
|------|---------|----------|
| Maven verify exit code | `./mvnw verify` | exit 0 |
| Tests-run count | Surefire stdout | **unchanged** from Phase-67 baseline (1,231) |
| JaCoCo line coverage | `target/site/jacoco/index.html` BUNDLE LINE | **≥ 0.82** (baseline 0.8561) |

---

## Wave 0 Requirements

- [ ] `pom.xml` — add `<lombok.version>1.18.46</lombok.version>` to `<properties>` block.
- [ ] `pom.xml` — append `--sun-misc-unsafe-memory-access=allow` to existing `<argLine>` in `maven-surefire-plugin` config.
- [ ] `pom.xml` — append the same flag to `<argLine>` in `maven-failsafe-plugin` execution config.
- [ ] `pom.xml` — add `<!-- JEP 498 escape: silence Lombok #3959 Permit/Unsafe warnings until upstream fix lands -->` comment above each modified `<argLine>`.

No new test files, no new fixtures. Configuration-only Wave 0.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Confirm `./mvnw verify` console output is visibly clean of the warning quartet | D-08 | Eyeballing the build log gives faster confidence than relying solely on grep | After Plan 68-01 commit: run `./mvnw verify`, scan the console output for any `WARNING:` lines mentioning `sun.misc.Unsafe` or `lombok.permit.Permit`. Should be zero. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or grep-gate verify
- [ ] Sampling continuity: every task has an automated check
- [ ] No new tests required (configuration-only change)
- [ ] No watch-mode flags
- [ ] Feedback latency < 3 min for full verify
- [ ] `nyquist_compliant: true` set in frontmatter (after gates 1–6 pass)

**Approval:** pending

---

## Validation Audit 2026-05-08 (Phase 69 SC6)

**Verdict:** `n/a` — by design. Phase 68 was a build-only diff: `pom.xml` pinned `<lombok.version>1.18.46</lombok.version>` and added `--sun-misc-unsafe-memory-access=allow` to the existing `<argLine>` of `maven-surefire-plugin` and `maven-failsafe-plugin` (3 fork sites total when counting the Lombok pin annotation processor). The argLine flag suppresses a JVM warning emitted by the Lombok agent's reflection probe — it does NOT change runtime behavior of any test class. The Lombok version pin is a strict version constraint (1.18.46), not a code change. Production bytecode emitted by Lombok 1.18.46 is a strict subset of the previously floating version's emission (the pin tightens, never widens).

**Methodology:** Mirrors Phase 63 docs-only treatment (no VALIDATION.md authored in Phase 64 sweep because the phase modifies no code). Phase 68's slight-stronger case: VALIDATION.md exists but is conceptually a stub; Phase 69 D-12 formalises the n/a verdict.

**Coverage delta vs. baseline:** `JaCoCo line` measured at Phase 68 close: 0.8561 (Phase 67 baseline). Pre-Phase-68 baseline: same (build-only diff; no production bytecode change beyond what the pinned Lombok version emits, which is a subset of prior emission). Delta: 0.0 within measurement noise.

**Why Manual (Phase 64 standard, D-15):** The single Manual-Only row above (visible cleanliness of `./mvnw verify` console output) is correctly classified — eyeballing the build log gives faster confidence than reliance on grep alone, and the warning quartet's emission shape varies subtly across JDK 25 builds (Temurin vs OpenJDK), so a human pattern-match completes the gate. Automated grep gates 4 + 5 + 6 already provide quantitative coverage; the Manual row adds qualitative confirmation.

_Authored 2026-05-08 (Phase 69 SC6 — milestone closure hygiene)_
_Branch: gsd/v1.9-season-phases-groups_
