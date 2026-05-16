---
phase: 81
slug: static-analysis-gate-spotbugs-find-sec-bugs
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-16
---

# Phase 81 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
>
> Phase 81 is a **build-configuration phase**, not an application-code phase. Validation
> assertions are observable via Maven invocations and generated artifacts, not via JUnit
> tests. The planner MUST keep this distinction visible in every task's `<automated>`
> verification block.

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

> Populated by the planner during PLAN.md generation. Each task in every PLAN.md must
> map to one row below. Task IDs follow `81-{plan}-{task}` shape (e.g., `81-01-03`).

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| _TBD by planner_ | _PLAN.md_ | _W_ | STAT-XX | _ref_ | _expected_ | build-cfg | _`./mvnw ...`_ | _✅/❌_ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Test types in this phase (build-configuration, not application code):**
- `build-cfg` — file-existence + content assertion (e.g., `test -f lombok.config && grep 'addSuppressFBWarnings' lombok.config`)
- `mvn-goal` — Maven goal exit-code assertion (e.g., `./mvnw spotbugs:spotbugs -DskipTests` exit 0)
- `mvn-gate` — Maven goal expected-fail assertion (STAT-06 deliberate-violation test must fail; capture stderr first-20-lines)
- `report-assertion` — generated artifact content check (e.g., `target/spotbugsXml.xml` parses and `<BugInstance>` count == 0 in `MEDIUM+` priority)

---

## Wave 0 Requirements

Wave 0 ("infrastructure first") is **not applicable in the test-fixtures sense** for Phase 81 — the project already has the full JUnit + Maven + Playwright + JaCoCo stack from Phase 1-80. Phase 81's Wave 0 obligations are:

- [ ] `lombok.config` exists at project root with both required directives — gates every subsequent task
- [ ] `config/spotbugs-exclude.xml` exists with at minimum the D-08 layer 2 package-pattern filter (`EI_EXPOSE_REP*` on `org.ctc.domain.model.*`) — gates every triage commit
- [ ] `./mvnw spotbugs:spotbugs -DskipTests` succeeds at least once (proves plugin wiring is structurally valid before D-12 step 2's baseline inspection)

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

- [ ] Every PLAN.md task has either `<automated>` (mvn-goal / build-cfg / report-assertion) or maps to a Manual-Only row above
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (lombok.config / spotbugs-exclude.xml / first successful spotbugs:spotbugs run)
- [ ] No watch-mode flags (irrelevant — Maven goals are one-shot)
- [ ] Feedback latency < 90s per task quick-check; < 300s per wave full-check
- [ ] STAT-06 manual evidence captured in `81-VERIFICATION.md`
- [ ] Triage table in `81-VERIFICATION.md` lists every Medium+HIGH finding with D-10 decision + rationale
- [ ] `target/site/jacoco/index.html` shows ≥ 82% (preferably ≥ 87%) line coverage after gate-flip commit (Pitfall #7 smoke check)
- [ ] `nyquist_compliant: true` set in frontmatter after sign-off

**Approval:** pending
