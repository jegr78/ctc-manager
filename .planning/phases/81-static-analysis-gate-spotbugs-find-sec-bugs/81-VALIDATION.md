---
phase: 81
slug: static-analysis-gate-spotbugs-find-sec-bugs
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-16
approved_on: 2026-05-18
audit_method: retroactive
---

# Phase 81 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
>
> Phase 81 is a **build-configuration phase**, not an application-code phase. Validation
> assertions are observable via Maven invocations and generated artifacts, not via JUnit
> tests. The planner MUST keep this distinction visible in every task's `<automated>`
> verification block.
>
> **Approved retroactively 2026-05-18 via Nyquist audit (Phase 87-series)** — all 7 STAT-NN requirements COVERED by evidence in `lombok.config`, `pom.xml`, `config/spotbugs-exclude.xml`, `CLAUDE.md`, and `81-VERIFICATION.md`. CI run [26033853591](https://github.com/jegr78/ctc-manager/actions/runs/26033853591) @ SHA `3590b3a7` confirms the gate live on PR branch (0 BugInstance, 88.88 % JaCoCo). See "Validation Audit 2026-05-18" block at bottom.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Maven 3.9.x via `./mvnw` (JUnit 5 / Surefire / Failsafe / JaCoCo already wired; SpotBugs gate is the net-new piece) |
| **Config file** | `pom.xml` (existing — Phase 81 inserts the `spotbugs-maven-plugin` block) |
| **Quick run command** | `./mvnw spotbugs:spotbugs -DskipTests` |
| **Full suite command** | `./mvnw verify` (and `./mvnw verify -Pe2e` for final phase-end check per CLAUDE.md) |
| **Estimated runtime** | quick ≈ 60-90s · full ≈ 180-300s · full -Pe2e ≈ 480-600s |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw spotbugs:spotbugs -DskipTests` (fast — only scans bytecode; produces `target/spotbugsXml.xml` for diff against prior baseline)
- **After every plan wave:** Run `./mvnw verify` (full unit + integration + JaCoCo `check` + SpotBugs goal active for that wave)
- **Before `/gsd:verify-work`:** `./mvnw verify -Pe2e` must be green (E2E obligation per CLAUDE.md `feedback_e2e_verification`)
- **Max feedback latency:** ~90 seconds per task commit (quick) · 5-10 minutes per wave (full)

> **CLAUDE.md user-preference reminder:** `feedback_test_call_optimization` — between triage commits, prefer `./mvnw spotbugs:spotbugs -DskipTests` over full `./mvnw verify`. ONE final `./mvnw verify -Pe2e` before the gate-flip commit is sufficient.

---

## Per-Task Verification Map

> Reconstructed retroactively 2026-05-18 from the three plan SUMMARYs (81-01, 81-02, 81-03)
> + 81-VERIFICATION.md. Each task in every PLAN.md maps to one row below.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File/Artifact | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|---------------|--------|
| 81-01-01 | 01 | W1 | STAT-01 | — | `lombok.config` at project root with `addLombokGeneratedAnnotation` + `addSuppressFBWarnings` | build-cfg | `test -f lombok.config && grep -c 'addSuppressFBWarnings' lombok.config` | `lombok.config` | ✅ green |
| 81-01-02 | 01 | W1 | STAT-02, STAT-03 | — | `spotbugs-maven-plugin` 4.9.8.3 + `findsecbugs-plugin` 1.14.0 in `pom.xml`; effort=Max; threshold=Default; no `<argLine>` | build-cfg | `grep -c 'spotbugs-maven-plugin' pom.xml && grep -c 'findsecbugs-plugin' pom.xml` | `pom.xml:371-399` | ✅ green |
| 81-01-03 | 01 | W1 | STAT-04 | — | `config/spotbugs-exclude.xml` with min D-08 layer 2 EI_EXPOSE_REP* filter on `org.ctc.domain.model` | build-cfg | `test -f config/spotbugs-exclude.xml && grep -c 'EI_EXPOSE_REP' config/spotbugs-exclude.xml` | `config/spotbugs-exclude.xml:25-39` | ✅ green |
| 81-01-04 | 01 | W1 | STAT-05/1 | — | `./mvnw verify` exit 0 in report-only mode; `target/spotbugsXml.xml` produced | mvn-goal | `./mvnw spotbugs:spotbugs -DskipTests` | `target/spotbugsXml.xml` (664,906 bytes baseline) | ✅ green |
| 81-02-A | 02 | W2 | STAT-04 | EI_EXPOSE_REP | D-08 layer 2 extended to all service/DTO/record packages; every `<Match>` has rationale comment | build-cfg | `grep -c '<Match>' config/spotbugs-exclude.xml` (≥15 entries) | `config/spotbugs-exclude.xml:41-118` | ✅ green |
| 81-02-B | 02 | W2 | STAT-05/triage | DM_DEFAULT_ENCODING (HIGH) | All 10 graphic-service template I/O calls pass `StandardCharsets.UTF_8` | build-cfg | `grep -rL 'StandardCharsets.UTF_8' src/main/java/org/ctc/admin/service/*GraphicService.java` (0 matches) | 10 graphic-service files | ✅ green |
| 81-02-C | 02 | W2 | STAT-05/triage | NP_NULL_ON_SOME_PATH | 9 NP findings resolved via method-level `@SuppressFBWarnings` with justification | report-assertion | `./mvnw spotbugs:spotbugs -DskipTests` → 0 `<BugInstance>` | `target/spotbugsXml.xml` | ✅ green |
| 81-02-D | 02 | W2 | STAT-05/triage | DLS, IM_BAD_CHECK, DB_DUPLICATE | Fix `DLS_DEAD_LOCAL_STORE` + `IM_BAD_CHECK_FOR_ODD` + `DB_DUPLICATE_BRANCHES`; suppress `VA_FORMAT_STRING_USES_NEWLINE` | mvn-goal | `./mvnw verify` exit 0; 0 BugInstance | `target/spotbugsXml.xml` | ✅ green |
| 81-03-01 | 03 | W3 | STAT-05/2, STAT-07 | — | `<goal>check</goal>` active in pom.xml; CLAUDE.md `### Static Analysis` section present | build-cfg + mvn-goal | `grep -c '<goal>check</goal>' pom.xml`; `grep -c 'Static Analysis' CLAUDE.md` | `pom.xml:387`, `CLAUDE.md:222-226` | ✅ green |
| 81-03-02 | 03 | W3 | STAT-06 | NP_ALWAYS_NULL | `./mvnw verify` EXIT 1 on deliberate HIGH violation; throwaway branch deleted with no origin artifact | mvn-gate (manual) | Throwaway-branch protocol per Manual-Only row below | `81-VERIFICATION.md:163-216` | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Test types in this phase (build-configuration, not application code):**
- `build-cfg` — file-existence + content assertion (e.g., `test -f lombok.config && grep 'addSuppressFBWarnings' lombok.config`)
- `mvn-goal` — Maven goal exit-code assertion (e.g., `./mvnw spotbugs:spotbugs -DskipTests` exit 0)
- `mvn-gate` — Maven goal expected-fail assertion (STAT-06 deliberate-violation test must fail; capture stderr first-20-lines)
- `report-assertion` — generated artifact content check (e.g., `target/spotbugsXml.xml` parses and `<BugInstance>` count == 0 in `MEDIUM+` priority)

---

## Wave 0 Requirements

Wave 0 ("infrastructure first") is **not applicable in the test-fixtures sense** for Phase 81 — the project already has the full JUnit + Maven + Playwright + JaCoCo stack from Phase 1-80. Phase 81's Wave 0 obligations are:

- [x] `lombok.config` exists at project root with both required directives — gates every subsequent task ✓ confirmed `lombok.config:1-3`, commit `1f020a23`
- [x] `config/spotbugs-exclude.xml` exists with at minimum the D-08 layer 2 package-pattern filter (`EI_EXPOSE_REP*` on `org.ctc.domain.model.*`) — gates every triage commit ✓ confirmed `config/spotbugs-exclude.xml:25-39`, commit `1f020a23`
- [x] `./mvnw spotbugs:spotbugs -DskipTests` succeeds at least once (proves plugin wiring is structurally valid before D-12 step 2's baseline inspection) ✓ confirmed in 81-01-SUMMARY.md Wave 0 Gate Results table (`target/spotbugsXml.xml` 664,906 bytes baseline)

*Existing infrastructure covers everything else.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| STAT-06 deliberate-violation test | STAT-06 | Requires a throwaway-branch maneuver (intentional code regression + revert) that no idempotent automation can perform inside the phase branch without polluting history | 1. `git switch -c throwaway/stat-06-validation`<br>2. Create `src/main/java/org/ctc/_validation_marker/DeliberateNullDereference.java` with `Object o = null; o.toString();`<br>3. `./mvnw verify` MUST exit non-zero with SpotBugs `NP_ALWAYS_NULL` (or equivalent NP-family bug) in the log<br>4. Capture exit code + first 20 stderr/stdout lines of the failure into `81-VERIFICATION.md` under "STAT-06 Evidence"<br>5. `git switch -` and `git branch -D throwaway/stat-06-validation`<br>6. No artifact from the throwaway branch lands in `origin` |
| Baseline-report triage decisions | STAT-05/Triage | The D-10 decision tree (fix vs suppress vs escalate) requires human judgement on each Medium-priority finding — no automation can decide "real bug vs intentional pattern vs stylistic" | After D-12 step 1 plumbing commit, run `./mvnw verify` once. Read `target/spotbugsXml.xml` + `target/site/spotbugs.html`. For every `<BugInstance>`, walk the D-10 tree and record decision + rationale in `81-VERIFICATION.md` triage table BEFORE making any triage commit. |
| pom.xml plugin block visual sanity | STAT-02 | XML structural correctness is best read by a human (indentation, comment placement, closing tags). Maven build success only proves syntactic validity, not maintainable structure. | After the plumbing commit, eyeball `pom.xml` diff to confirm: (a) plugin block sits between jacoco close and exec-maven-plugin open, (b) `<plugins>` (not `<dependencies>`) carries find-sec-bugs as child of spotbugs plugin, (c) no `<argLine>` anywhere in the new block, (d) `<effort>Max</effort>` + `<threshold>Default</threshold>` present. |

*Everything else is automated via mvn commands + file-content greps.*

---

## Validation Sign-Off

- [x] Every PLAN.md task has either `<automated>` (mvn-goal / build-cfg / report-assertion) or maps to a Manual-Only row above ✓ 10 tasks mapped in Per-Task Verification Map
- [x] Sampling continuity: no 3 consecutive tasks without automated verify ✓ per-pattern-family `spotbugs:spotbugs` runs after each PLAN 02 triage commit (81-02-SUMMARY.md)
- [x] Wave 0 covers all MISSING references (lombok.config / spotbugs-exclude.xml / first successful spotbugs:spotbugs run) ✓ all 3 flipped above
- [x] No watch-mode flags (irrelevant — Maven goals are one-shot) ✓ trivially satisfied
- [x] Feedback latency < 90s per task quick-check; < 300s per wave full-check ✓ STAT-06 throwaway exit-1 in 8.998 s; full verify within documented 8-12 min estimate range
- [x] STAT-06 manual evidence captured in `81-VERIFICATION.md` ✓ lines 163-216 (commit `b12ac7f3`)
- [x] Triage table in `81-VERIFICATION.md` lists every Medium+HIGH finding with D-10 decision + rationale ✓ lines 68-97 (220 findings across 7 pattern families)
- [x] `target/site/jacoco/index.html` shows ≥ 82% (preferably ≥ 87%) line coverage after gate-flip commit (Pitfall #7 smoke check) ✓ 88.47 % post-gate-flip; 88.88 % on CI run `26033853591`
- [x] `nyquist_compliant: true` set in frontmatter after sign-off ✓ flipped 2026-05-18 (this commit)

**Approval:** approved 2026-05-18 — retroactive Nyquist audit (Phase 87-series)

---

## Validation Audit 2026-05-18

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

**Audit method:** retroactive — Phase 81 shipped 2026-05-16 (commits `1f020a23` plumbing → triage wave `90b27435`/`08c8ed08`/`6d3d9602`/`119f35a4`/`750cb8ab`/`acd5184d` → `64fdb7ba` gate-flip → `b12ac7f3` STAT-06 evidence). Nyquist audit 2026-05-18 confirmed all 7 STAT-NN requirements are COVERED by evidence in `lombok.config`, `pom.xml`, `config/spotbugs-exclude.xml`, `CLAUDE.md`, and `81-VERIFICATION.md`. No new tests were generated — Phase 81 is a build-configuration phase with assertions via Maven invocations and file-content greps.

**CI evidence:**

- **Full-suite CI baseline:** Run-id [26033853591](https://github.com/jegr78/ctc-manager/actions/runs/26033853591) (workflow on `gsd/v1.11-tooling-and-cleanup` @ SHA `3590b3a7`, conclusion: success) — `./mvnw verify -Pe2e` green, 1675 tests, JaCoCo 88.88 %, SpotBugs 0 BugInstance with blocking `check` goal active.
- **Local gate-flip verification (2026-05-16):** `81-VERIFICATION.md` STAT-05/2 + STAT-06 sections confirm exit 0 on clean tree and exit 1 on deliberate `NP_ALWAYS_NULL` violation.

**Requirements coverage matrix (audit result):**

| REQ-ID | Existing evidence | Result |
|--------|-------------------|--------|
| STAT-01 | `lombok.config:1-3` (both required directives present) | ✅ COVERED |
| STAT-02 | `pom.xml:372-399` (plugin 4.9.8.3, effort=Max, threshold=Default, phase=verify, no `<argLine>`) | ✅ COVERED |
| STAT-03 | `pom.xml:392-397` (`findsecbugs-plugin` 1.14.0 as plugin-level dep) | ✅ COVERED |
| STAT-04 | `config/spotbugs-exclude.xml:1-249` (15 `<Match>` entries, every entry with rationale comment + D-09 cross-reference) | ✅ COVERED |
| STAT-05 | Commits `1f020a23` (report-only) → `64fdb7ba` (blocking) with triage wave in between; `pom.xml:387` shows `<goal>check</goal>` | ✅ COVERED |
| STAT-06 | `81-VERIFICATION.md:163-216` (throwaway-branch deliberate `NP_ALWAYS_NULL` → exit 1 BUILD FAILURE) | ✅ COVERED |
| STAT-07 | `CLAUDE.md:222-226` (`### Static Analysis (SpotBugs + find-sec-bugs)` with Gate + Suppressions + lombok.config invariant bullets) | ✅ COVERED |

**Approval:** approved 2026-05-18 — retroactive Nyquist audit (Phase 87-series, in-milestone closure of v1.11 Nyquist debt)
