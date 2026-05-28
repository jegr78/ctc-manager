---
phase: 99
slug: pre-merge-audit-polish-requirements-flyway-prose-roadmap-ref
status: shipped
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-26
verified_on: 2026-05-26
audit_method: retroactive
# Reconstructed from 99-{01..05}-SUMMARY.md after phase ship — State B (no prior VALIDATION.md)
---

# Phase 99 — Validation Strategy

> Per-phase validation contract for Phase 99 — Pre-merge audit-polish (REQUIREMENTS + Flyway-prose + ROADMAP refresh + retroactive VERIFICATION.md + FORUM-01 modal scope).

Reconstructed retroactively from per-plan SUMMARY.md artifacts and PLAN `<verify>` blocks after all 5 plans shipped. State B reconstruction per `/gsd-validate-phase` workflow.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test + Playwright (E2E) — already installed |
| **Config file** | `pom.xml` (Surefire + Failsafe + JaCoCo + SpotBugs plugins) |
| **Quick run command** | `grep -nE "<pattern>" <file>` (per-task grep gates — sub-second) |
| **Full suite command** | `./mvnw clean verify -Pe2e` |
| **Estimated runtime** | grep gates: < 1 s each; full suite: ~8.5 min (verified `08:39` on Plan 99-05 close 2026-05-25) |

---

## Sampling Rate

- **After every task commit:** Run the task-specific grep gate (per-task `<verify><automated>` block in the PLAN file).
- **After every plan wave:** Plans 99-01..04 are pure Markdown — grep gates ARE the wave gate (no Maven invocation needed). Plan 99-05 runs `./mvnw verify -Dit.test=DiscordRestClientIT` as targeted-IT after the surgical IT method delete, then `./mvnw clean verify -Pe2e` as the end-of-phase gate.
- **Before `/gsd-verify-work`:** Full suite (`./mvnw clean verify -Pe2e`) must be green — confirmed for Phase 99 on 2026-05-25 (BUILD SUCCESS, 2244 tests, JaCoCo 88.47 %, SpotBugs 0).
- **Max feedback latency:** grep gates < 1 s; targeted IT ~30 s; full clean verify ~8.5 min.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 99-01-01 | 01 | 1 | POST-01 (prose) | — | N/A (docs-only) | grep gate | `grep -c "Flyway V11 \`discord_post\` table" .planning/REQUIREMENTS.md = 0 && grep -c "Flyway V12 \`discord_post\` table" .planning/REQUIREMENTS.md = 1` | ✅ | ✅ green |
| 99-01-02 | 01 | 1 | FORUM-01 (prose + acceptance) | — | N/A (docs-only) | grep gate | `grep -c "Flyway V12 adds \`seasons.discord" = 0 && grep -c "Flyway V13 adds \`seasons.discord_race_results_thread_id\` + \`seasons.discord_standings_thread_id\`" = 1 && grep -c "Create new Thread" = 0 && grep -c "operator-workflow note" = 1` | ✅ | ✅ green |
| 99-01-03 | 01 | 1 | FORUM-01 (audit doc cross-update) | — | N/A (docs-only) | grep gate | `grep -c "satisfied (partial)" .planning/v1.13-MILESTONE-AUDIT.md = 0 && grep -c "YAGNI verdict 2026-05-25" = 1 && grep -c "Plan 99-05" >= 2 && grep -c "Plan 99-01" >= 2` | ✅ | ✅ green |
| 99-02-01 | 02 | 1 | — (ROADMAP doc-shape) | — | N/A (docs-only) | grep gate | `grep -c "v1.13 Discord Integration & Carry-Forwards .*Complete .*2026-05-25" .planning/ROADMAP.md = 1 && grep -c "v1.13 .*In flight" = 0` | ✅ | ✅ green |
| 99-02-02 | 02 | 1 | — (ROADMAP doc-shape) | — | N/A (docs-only) | grep gate | `grep -c "Not started" = 0 && grep -c "In Progress" = 0 && per-phase row grep × 7 = 1 each` | ✅ | ✅ green |
| 99-03-01 | 03 | 1 | — (file-shape) | — | N/A (docs-only) | pre-flight existence | `test -f` × 9 source files (89-VERIFICATION.md precedent + 93-VERIFICATION.md + 6 VALIDATION.md + 92-04-PLAN.md) | ✅ | ✅ green |
| 99-03-02 | 03 | 1 | — (file-shape) | — | N/A (docs-only) | grep gate | `test -f` × 6 new files + `grep -q "Goal Achievement — Success Criteria"` × 6 + `grep -q "Per-Dimension Verdict Table"` × 6 + `grep -q "audit_method: retroactive"` × 6 + score-line shape per phase | ✅ | ✅ green |
| 99-04-01 | 04 | 1 | — (doc-shape) | — | N/A (docs-only) | grep gate | `grep -c "^nyquist_compliant: true" .planning/phases/93-discord-foundation/93-VALIDATION.md = 1 && grep -c "^status: shipped" = 1 && grep -c "^verified_on: 2026-05-25" = 1 && grep -c "93-VERIFICATION.md is authoritative" = 1` | ✅ | ✅ green |
| 99-04-02 | 04 | 1 | — (doc-shape) | — | N/A (docs-only) | grep gate | `grep -c "^nyquist_compliant: true" .planning/phases/95-match-channel-posts/95-VALIDATION.md = 1 && grep -c "^status: shipped" = 1 && grep -c "^verified_on: 2026-05-25" = 1 && grep -c "95-04-VALIDATION.md (BUILD SUCCESS) is authoritative" = 1` | ✅ | ✅ green |
| 99-05-01 | 05 | 1 | FORUM-01 (YAGNI surface) | T-99-05-01 (Tampering) | Pre-edit safety: confirm no unexpected production caller of `createThread()` | grep safety gate | `grep -rn "createThread" src/ \| wc -l ≤ 3 && grep -rn "ThreadCreateRequest" src/ \| wc -l = 5 && grep -c "record ThreadList" src/main/java/org/ctc/discord/DiscordRestClient.java = 1` | ✅ | ✅ green (2 createThread / 5 ThreadCreateRequest — within plan STOP bound; investigated in 99-05-SUMMARY.md) |
| 99-05-02 | 05 | 1 | FORUM-01 (YAGNI surface) | T-99-05-02 (DoS — accidental over-reach into keepers) | Keepers (`listActiveThreads`, `listArchivedThreads`, `record ThreadList`, `createWebhook`) compile & stay intact | grep + compile gate | `grep -c "ThreadCreateRequest" src/main/java/org/ctc/discord/DiscordRestClient.java = 0 && grep -c "createThread" = 0 && grep -c "listActiveThreads" = 1 && grep -c "listArchivedThreads" = 1 && grep -c "createWebhook" = 1 && grep -c "record ThreadList" = 1 && ./mvnw -q compile exits 0` | ✅ | ✅ green |
| 99-05-03 | 05 | 1 | FORUM-01 (YAGNI surface) | T-99-05-03 (Tampering — DTO deletion leaves dangling reference) | DTO file deleted; main compile clean | git rm + compile gate | `test ! -f src/main/java/org/ctc/discord/dto/ThreadCreateRequest.java && ! git ls-files --error-unmatch <path> && ./mvnw -q compile exits 0` | ✅ | ✅ green |
| 99-05-04 | 05 | 1 | FORUM-01 (YAGNI surface) | T-99-05-02 (DoS — accidental over-reach into keeper IT methods) | Remaining 16 ITs in DiscordRestClientIT pass; orphan `given200_whenCreateThread_thenReturnsThread` gone | grep + targeted IT gate | `grep -c "ThreadCreateRequest" src/test/java/org/ctc/discord/DiscordRestClientIT.java = 0 && grep -c "createThread" = 0 && grep -c "given200_whenListActiveThreads" = 1 && grep -c "given200_whenListArchivedThreads" = 1 && grep -c "given401_whenFetchBotUser" = 1 && ./mvnw verify -Dit.test=DiscordRestClientIT -DfailIfNoTests=false exits 0` | ✅ | ✅ green (covered by end-of-phase clean-verify in Task 99-05-05) |
| 99-05-05 | 05 | 1 | FORUM-01 (YAGNI surface) | T-99-05-SC (no supply-chain risk — no dependency change) | Global zero-reference + clean-build + coverage + SpotBugs all green | end-of-phase gate | `grep -rn "createThread" src/ \| wc -l = 0 && grep -rn "ThreadCreateRequest" src/ \| wc -l = 0 && ./mvnw clean verify -Pe2e exits 0 && JaCoCo gate met (≥82% per pom.xml; 88.47% measured) && SpotBugs BugInstance count = 0` | ✅ | ✅ green (BUILD SUCCESS 2026-05-25, 2244 tests, 8:39 wallclock) |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements — Phase 99 introduces zero new test classes, fixtures, or framework dependencies. Plans 99-01..04 are pure Markdown edits validated by grep gates; Plan 99-05 reduces test surface (deletes 1 IT method) and is validated by the unchanged Surefire/Failsafe/JaCoCo/SpotBugs stack.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual confirmation that REQUIREMENTS.md FORUM-01 prose reads naturally after rewrite | FORUM-01 (prose) | Tone discipline (neutral, matter-of-fact) cannot be auto-asserted | Re-read `.planning/REQUIREMENTS.md` line 66 — confirm operator-workflow note flows after the (c) Unlink clause without editorialising. (Verified inline 2026-05-25 during Plan 99-01 ship.) |
| Visual confirmation that 6 new VERIFICATION.md files render correctly in markdown viewers | — (file-shape) | Markdown render-fidelity is not asserted by grep | Open each of `.planning/phases/{92,94,95,96,97,98}-*/9N-VERIFICATION.md` in the planning UI / VS Code preview — confirm tables and headers render. (Verified inline 2026-05-25 during Plan 99-03 ship.) |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies — 14/14 tasks have `<verify><automated>` blocks in their PLAN files.
- [x] Sampling continuity: no 3 consecutive tasks without automated verify — every task has its own grep gate.
- [x] Wave 0 covers all MISSING references — none MISSING; existing infrastructure suffices.
- [x] No watch-mode flags — all commands are single-shot.
- [x] Feedback latency < 8.5 min — grep gates < 1 s; full clean-verify 08:39.
- [x] `nyquist_compliant: true` set in frontmatter.

**Approval:** approved 2026-05-26 (retroactive reconstruction — Phase 99 already shipped 2026-05-25; per-plan grep gates and end-of-phase clean-verify all green).
