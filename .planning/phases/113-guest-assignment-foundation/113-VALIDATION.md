---
phase: 113
slug: guest-assignment-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-01
---

# Phase 113 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ + Mockito (existing) |
| **Config file** | `pom.xml` (Surefire/Failsafe; `@Tag`-based routing) |
| **Quick run command** | `./mvnw -Dtest=RaceLineupServiceTest test` (Surefire) / `./mvnw -Dit.test=RaceLineupControllerTest -DfailIfNoTests=false verify` (Failsafe IT) |
| **Full suite command** | `./mvnw clean verify` |
| **Estimated runtime** | quick ~30–60s; full ~5–7 min (E2E +Playwright on phase gate) |

---

## Sampling Rate

- **After every task commit:** Run the relevant quick command (`-Dtest=RaceLineupServiceTest` / `-Dit.test=RaceLineupControllerTest`)
- **After every plan wave:** Run `./mvnw clean verify`
- **Before `/gsd-verify-work`:** `./mvnw clean verify -Pe2e` must be green (full suite incl. Playwright E2E)
- **Max feedback latency:** ~60 seconds (quick), ~7 min (phase gate)

---

## Per-Task Verification Map

> Task IDs are assigned by the planner; rows below map each GUEST requirement to its observable verification. The planner MUST align task IDs to these behaviors.

| Plan area | Wave | Requirement | Behavior verified | Test Type | Automated Command | File Exists | Status |
|-----------|------|-------------|-------------------|-----------|-------------------|-------------|--------|
| Migration V18 | 1 | GUEST-04 | `is_guest` column exists, `NOT NULL`, default false in H2 (+MariaDB-compatible syntax) | integration (migration) | `./mvnw -Dit.test=V18MigrationIT -DfailIfNoTests=false verify` | ❌ W0 (new, follow `V17MigrationIT`) | ⬜ pending |
| Entity/persistence | 1 | GUEST-04 | `is_guest` persists `true` for guest entries, `false` for roster entries | integration (controller/repo) | `./mvnw -Dit.test=RaceLineupControllerTest -DfailIfNoTests=false verify` | ✅ extend | ⬜ pending |
| Controller param parsing | 2 | GUEST-01 | `saveLineup` with `guest_<driverId>=teamId` params persists guest entries with `is_guest=true` | integration (controller) | `./mvnw -Dit.test=RaceLineupControllerTest -DfailIfNoTests=false verify` | ✅ extend | ⬜ pending |
| Lineup form GET | 2 | GUEST-01 | `getLineupData`/model exposes `allDrivers`; GET form renders `<datalist>` of all drivers | integration (controller) | `./mvnw -Dit.test=RaceLineupControllerTest -DfailIfNoTests=false verify` | ✅ extend | ⬜ pending |
| Results auto-derive | 2 | GUEST-02 | `RaceFormDataService.populateDrivers` surfaces guest lineup entries in the results form | unit | `./mvnw -Dtest=RaceFormDataServiceTest test` | ❌ W0 (new) | ⬜ pending |
| Guest removal cascade | 2 | GUEST-03 | Removing a guest cascade-deletes the guest's `RaceResult` and calls `aggregateMatchScores(race)` | unit (service) | `./mvnw -Dtest=RaceLineupServiceTest test` | ✅ extend | ⬜ pending |
| Roster-map guest filter | 2 | GUEST-03 | `getDriverAssignments` excludes `is_guest=true` entries (no guest bleed into roster checkbox map) | unit (service) | `./mvnw -Dtest=RaceLineupServiceTest test` | ✅ extend | ⬜ pending |
| Backup restore | 1 | GUEST-04 | `RaceLineupRestorer` INSERT SQL includes `is_guest` and binds the boolean (old backups default false) | unit | `./mvnw -Dtest=RaceLineupRestorerTest test` | ✅ extend | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/db/migration/V18MigrationIT.java` — GUEST-04 schema persistence (follow `V17MigrationIT` pattern; `@Tag("integration")`)
- [ ] `src/test/java/org/ctc/domain/service/RaceFormDataServiceTest.java` — GUEST-02 guest-auto-derive path (confirm guest lineup entries surface in results form)
- [ ] Extend existing `RaceLineupServiceTest.java`, `RaceLineupControllerTest.java`, `RaceLineupRestorerTest.java` (extension, not creation)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Datalist typeahead UX in the lineup form (select a guest, sub-team select, prefill on re-edit) | GUEST-01/03 | Browser-rendered interaction; visual marking is Phase 115, but functional smoke is worth a manual pass | `./scripts/app.sh start dev`, open `/admin/races/{raceId}/lineup`, add a guest via the datalist, save, reopen — guest row prefilled. (Optional `/gsd-auto-uat 113`.) |

*All requirement-level behaviors above have automated verification; the row here is a functional UX smoke, not a coverage gap.*

---

## Validation Sign-Off

- [ ] All tasks have automated verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (V18MigrationIT, RaceFormDataServiceTest)
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s (quick)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
