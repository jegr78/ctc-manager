---
phase: 11
slug: template-quality
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-06
---

# Phase 11 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Playwright (E2E) |
| **Config file** | `pom.xml` (Surefire + Failsafe) |
| **Quick run command** | `./mvnw verify` |
| **Full suite command** | `./mvnw verify -Pe2e` |
| **Estimated runtime** | ~120 seconds (verify), ~300 seconds (E2E) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw verify`
- **After every plan wave:** Run `./mvnw verify -Pe2e`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 300 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 11-01-01 | 01 | 1 | QUAL-01 | — | N/A | visual | Playwright screenshot diff | ✅ | ⬜ pending |
| 11-01-02 | 01 | 1 | QUAL-01 | — | N/A | grep | `grep -c 'style="' src/main/resources/templates/admin/season-detail.html` returns only th:style counts | ✅ | ⬜ pending |
| 11-01-03 | 01 | 1 | QUAL-01 | — | N/A | grep | `grep -c 'style="' src/main/resources/templates/admin/race-detail.html` returns only th:style counts | ✅ | ⬜ pending |
| 11-02-01 | 02 | 2 | QUAL-01 | — | N/A | grep | No static `style="` in remaining admin templates (excluding *-render.html, template-editors.html) | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. Playwright is already installed and E2E tests exist.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual pixel-identical appearance | QUAL-01 SC-3 | Playwright screenshots need human comparison for subtle differences | Capture before/after screenshots, compare side-by-side |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 300s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
