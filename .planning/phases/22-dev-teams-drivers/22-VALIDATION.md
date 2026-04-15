---
phase: 22
slug: dev-teams-drivers
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-09
---

# Phase 22 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito |
| **Config file** | pom.xml (Surefire + JaCoCo) |
| **Quick run command** | `./mvnw test -pl . -Dtest=TestDataServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl . -Dtest=TestDataServiceTest`
- **After wave complete:** Run `./mvnw verify`
- **Before phase verification:** Full `./mvnw verify` (852+ tests, 82% coverage minimum)

---

## Validation Architecture

### Layer 1: Unit Tests
- TestDataService creates 14+ teams with fictive names
- At least 2 parent teams with 2+ sub-teams each
- 10 drivers per team with fictive names
- No real CTC team names in seed data

### Layer 2: Integration Tests
- Dev profile startup seeds expected data counts
- Team card generation invoked for all teams
- Idempotent seeding (second run skips)

### Layer 3: Visual Verification
- Team cards visible on teams list page (playwright-cli)
- Team detail pages show correct driver count

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Teams seeded | >= 14 |
| Parent teams with sub-teams | >= 2 |
| Drivers per team | exactly 10 |
| Team cards generated | all teams |
| Test coverage | >= 82% |
