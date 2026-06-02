---
phase: 113
slug: guest-assignment-foundation
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-01
validated: 2026-06-01
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
| Migration V18 | 1 | GUEST-04 | `is_guest` column exists, `NOT NULL`, default false in H2 (+MariaDB-compatible syntax) | integration (migration) | `./mvnw -Dit.test=V18MigrationIT -DfailIfNoTests=false verify` | ✅ `V18MigrationIT` (2 tests) | ✅ green |
| Entity/persistence | 1 | GUEST-04 | `is_guest` persists `true` for guest entries, `false` for roster entries | integration (controller/repo) | `./mvnw -Dtest=RaceLineupControllerTest test` | ✅ `givenGuestParam_whenSaveLineup_thenPersistsGuestEntryWithGuestFlag` | ✅ green |
| Controller param parsing | 2 | GUEST-01 | `saveLineup` with `guest_<driverId>=teamId` params persists guest entries with `is_guest=true` | integration (controller) | `./mvnw -Dtest=RaceLineupControllerTest test` | ✅ `givenGuestParam_whenSaveLineup_…` | ✅ green |
| Lineup form GET | 2 | GUEST-01 | `getLineupData`/model exposes `allDrivers`; GET form renders `<datalist>` of all drivers | integration (controller) | `./mvnw -Dtest=RaceLineupControllerTest test` | ✅ `givenExistingRace_whenGetLineupPage_…` (asserts `guestLineups`,`allDrivers`) | ✅ green |
| Results auto-derive | 2 | GUEST-02 | `RaceFormDataService.populateDrivers` surfaces guest lineup entries in the results form | unit | `./mvnw -Dtest=RaceFormDataServiceTest test` | ✅ `givenLineupWithGuestEntry_whenGetResultsFormData_…` | ✅ green |
| Guest removal cascade | 2 | GUEST-03 | Removing a guest cascade-deletes the guest's `RaceResult` and calls `aggregateMatchScores(race)` | unit (service) | `./mvnw -Dtest=RaceLineupServiceTest test` | ✅ `givenRemovedGuest_…` (+ `givenKeptGuestMovedToDifferentTeam_…` for CR-01 team-change re-aggregation) | ✅ green |
| Roster-map guest filter | 2 | GUEST-03 | `getDriverAssignments` excludes `is_guest=true` entries (no guest bleed into roster checkbox map) | unit (service) | `./mvnw -Dtest=RaceLineupServiceTest test` | ✅ `givenMixedLineup_whenGetDriverAssignments_thenExcludesGuests` + `…GetGuestLineups…` | ✅ green |
| Backup restore | 1 | GUEST-04 | `RaceLineupRestorer` INSERT SQL includes `is_guest` and binds the boolean (old backups default false) | unit | `./mvnw -Dtest=RaceLineupRestorerTest test` | ✅ `givenSampleLineup_…` + `givenPreV18LineupWithoutGuestField_…` | ✅ green |
| Guest team scope (D-07) | 3 | GUEST-01/03 | Guest team must be one of the race's home/away (sub-)teams; foreign team rejected | unit + integration | `./mvnw -Dtest=RaceLineupServiceTest test` | ✅ `givenGuestTeamNotInRace_whenSaveLineup_thenThrowsBusinessRule` (WR-03) | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/java/db/migration/V18MigrationIT.java` — GUEST-04 schema persistence (follows `V17MigrationIT` pattern; `@Tag("integration")`)
- [x] `src/test/java/org/ctc/domain/service/RaceFormDataServiceTest.java` — GUEST-02 guest-auto-derive path (guest lineup entries surface in results form)
- [x] Extended existing `RaceLineupServiceTest.java`, `RaceLineupControllerTest.java`, `RaceLineupRestorerTest.java` (extension, not creation)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Datalist typeahead UX in the lineup form (select a guest, sub-team select, prefill on re-edit) | GUEST-01/03 | Browser-rendered JS interaction (`guest-lineup.js`); not reachable by MockMvc | ✅ **Auto-verified 2026-06-01 via `playwright-cli`** — see `113-AUTO-UAT.md` (4/4: typeahead→hidden `guest_<id>` resolution, sub-team prefill on reopen, clone-row independence, blank-row sub-team default). |

*All requirement-level behaviors above have automated verification; the UX row was auto-verified via playwright-cli — no remaining coverage gap.*

---

## Validation Sign-Off

- [x] All tasks have automated verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (V18MigrationIT, RaceFormDataServiceTest)
- [x] No watch-mode flags
- [x] Feedback latency < 60s (quick)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved (2026-06-01)

## Validation Audit 2026-06-01

| Metric | Count |
|--------|-------|
| Requirements (GUEST-01..04) | 4 |
| Mapped behaviors | 9 |
| COVERED (green automated test) | 9 |
| PARTIAL | 0 |
| MISSING / gaps found | 0 |
| Resolved this audit | 0 (no gaps) |
| Escalated to manual-only | 0 |

All GUEST-01..04 behaviors have green automated verification; the one browser-JS UX item was auto-verified via playwright-cli (113-AUTO-UAT.md). Full `clean verify -Pe2e` green. **Phase 113 is Nyquist-compliant.**
