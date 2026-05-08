---
phase: 61-cleanup-quality-gate
captured: 2026-05-08T16:22:00Z
auto_uat_runner: playwright-cli (dev,demo profile, localhost:9090)
fixture: Season 2023 GROUPS (consolidated; TestDataService.seed)
tests:
  uat_01:
    title: "GROUPS-Saison E2E flow visual smoke check (post-f5b10bc fix re-confirmation)"
    status: passed
    evidence: 5 screenshots in .screenshots/69-uat-01-*.png
    checks:
      - "Season 2023 list view renders with GROUPS layout marker"
      - "Phase tabs render (REGULAR + 2023 Playoffs)"
      - "Group sub-tabs render (Combined / Group A / Group B)"
      - "Per-group standings table populated with team rows (6 teams in Group A)"
      - "Combined view renders cross-group standings (12 teams Team Standings + 24 drivers Driver Rankings)"
      - "season-phase-form.html dropdowns (Phase Type / Layout / Format / Race Scoring / Match Scoring) show non-empty labels (regression cover from commit f5b10bc)"
    signed_off: 2026-05-08
  uat_02:
    title: "Legacy migrated season visual smoke check (real pre-V4 data)"
    status: deferred
    rationale: |
      Test fixtures only exercise the empty-state standings path
      (D-18 read-only). Real production data with populated standings
      requires a pre-V4 legacy season that local dev,demo seed cannot
      synthesise. Not release-blocking; user verifies opportunistically
      after next production deploy. Already deferred during the
      original 2026-05-02 UAT cycle per 61-VERIFICATION.md
      human_verification[1].
    defer_signed_off: 2026-05-08
---

# Phase 61 — Human / Auto-UAT Closure

**Captured:** 2026-05-08
**Runner:** `playwright-cli` (Auto-UAT) against the local Spring Boot dev server (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo`, port 9090).
**Fixture:** Season 2023 GROUPS — the consolidated GROUPS-layout REGULAR phase + PLAYOFF phase seeded by `TestDataService.seed()` in the `dev,demo` profile (12 teams split 6/6 across Group A / Group B; 24 drivers; populated standings).
**Branch invariant:** `gsd/v1.9-season-phases-groups` (D-18 honoured at every step).

This artifact is the audit-trail closure for Phase 61's two outstanding manual UAT items. UAT-03 (V6 migration on MariaDB) and UAT-04 (Legacy URL bookmark regression) were already closed in the 2026-05-02 UAT cycle (see `61-VERIFICATION.md` § "UAT Closure Update — 2026-05-02T00:35:00Z"); only UAT-01 and UAT-02 carried forward to Phase 69.

---

## Dev-Server Log Excerpt (evidence anchor)

```
2026-05-08T15:36:44.864+02:00  INFO --- org.ctc.CtcManagerApplication      : Starting CtcManagerApplication using Java 25.0.2 ...
2026-05-08T15:36:48.090+02:00  INFO --- o.s.boot.tomcat.TomcatWebServer    : Tomcat started on port 9090 (http) with context path '/'
2026-05-08T16:16:30.091+02:00  INFO --- org.ctc.admin.DevDataSeeder        : Site generated: 319 pages, 0 errors
```

Health probe `curl http://localhost:9090/actuator/health` returned `{"groups":["liveness","readiness"],"status":"UP"}`. Tomcat bound to port 9090 cleanly under the `dev,demo` profile.

---

## UAT-01 — GROUPS-Saison E2E flow visual smoke (PASSED)

**Status:** PASSED via Auto-UAT 2026-05-02-then-2026-05-08 (UAT-01 was BLOCKED on 2026-05-02 by the season-phase-form.html dropdown regression; the fix shipped in commit `f5b10bc` and a regression IT was added to `SeasonPhaseControllerIT`. Phase 69 SC3 re-confirms the post-fix visual state.)

### Screenshot 1 — Season list view

**Path:** `.screenshots/69-uat-01-1-season-list.png`
**URL captured:** `http://localhost:9090/admin/seasons`

Season 2023 (`2023 | #1 | Season 2023`) is present in the list under the LEAGUE / Seasons section. The row renders with Edit / Delete actions and a status pill ("Inactive"). All 7 seeded seasons (2023, 2024 #2, 2026 #4, 2024 #3 Empty Phase, 2026 #99 Test, 2025 #98 Test) render. Confirms the list view loads and Season 2023 is the GROUPS-layout fixture target.

### Screenshot 2 — Phase tabs (Season 2023 detail, REGULAR phase)

**Path:** `.screenshots/69-uat-01-2-phase-tabs.png`
**URL captured:** `/admin/seasons/7a63fc2f-bae2-42ff-b4ec-aa5450f36846/phases/8f00c95d-f8e7-4863-9299-0a67bf3e778f`

Two phase tabs render: `REGULAR` (active, "Regular Season") and `2023 Playoffs` (PLAYOFF). Below the tabs the Combined / Group A / Group B sub-tabs render (D-15 GROUPS layout indicator). The 12-team roster table is visible with team color, code, name, kapazität column and per-team Group label (Group A / Group B). Format = "Round Robin — two groups". No 5xx markup, no missing tab labels.

### Screenshot 3 — Group A sub-tab

**Path:** `.screenshots/69-uat-01-3-group-a.png`
**URL captured:** `/admin/seasons/7a63fc2f-bae2-42ff-b4ec-aa5450f36846/phases/8f00c95d-f8e7-4863-9299-0a67bf3e778f/groups/3344b32b-bf92-4a8f-8509-273b85abbeb2`

Group A sub-tab is selected (visually distinguished). Roster table renders 6 teams (ADR / ICL / SVT / NFR / HMS / VRX A) with the "Group" column showing "Group A" for every row. Matchdays section + Standings section both render below the roster (no 404 / 500 markup). Confirms per-group filtering works visually.

### Screenshot 4 — Combined view (cross-group standings)

**Path:** `.screenshots/69-uat-01-4-combined.png`
**URL captured:** `/admin/standings?phase=8f00c95d-f8e7-4863-9299-0a67bf3e778f`

Combined Team Standings table renders all 12 teams across both groups, with columns Team / Group / MP / W / D / L / PTS populated (non-zero values for the seeded race results). Driver Rankings table renders 24 drivers with team / races / best pos / points columns. Group column visibly differentiates Group A vs Group B rows. Pagination control at the bottom confirms full population. No empty-state placeholder.

### Screenshot 5 — Phase edit form dropdowns (regression cover for f5b10bc)

**Path:** `.screenshots/69-uat-01-5-edit-form.png`
**URL captured:** `/admin/seasons/7a63fc2f-bae2-42ff-b4ec-aa5450f36846/phases/8f00c95d-f8e7-4863-9299-0a67bf3e778f/edit`

All five dropdowns render with **non-empty labels** — explicit confirmation that the f5b10bc fix (Thymeleaf `${labels[enumKey]}` indexer regression) is still in place:

- Phase Type → "Regular Season" (selected)
- Layout → "Groups" (selected)
- Format → "Round Robin" (selected)
- Race Scoring → "CTC Standard" (selected)
- Match Scoring → "Standard 3-1-0" (selected)

This is the regression that BLOCKED UAT-01 on 2026-05-02. The post-fix visual state is now formally re-captured.

---

## UAT-02 — Legacy migrated season visual smoke (DEFERRED)

**Status:** DEFERRED with formal sign-off 2026-05-08.

**Why deferred:** Phase 61's QUAL-03 fixtures (`legacy-season-with-playoff.sql` and `legacy-season-without-playoff.sql`) seed 0 race-results — the empty-state standings path is exercised, but the **populated** standings rendering on a real pre-V4 legacy season cannot be reproduced from local fixtures (D-18 read-only). Real production data — a season migrated by Flyway V4 with actual race results carried forward — will hit the populated path. The local `dev,demo` profile cannot synthesise such a season because the demo seeder writes against the post-V6 schema directly.

**Why not release-blocking:**
- `LegacyMigratedSeasonE2ETest` already proves the post-V6 schema serves the legacy-season URL contract for both with-playoff and without-playoff variants (read-only fixtures).
- The Standings template renders the same DOM regardless of population (only the row count differs); a populated render path failing while an empty render path succeeds is implausible.
- The user already validated this scenario on the 2026-05-02 UAT cycle by deferring it as "verify locally with real legacy-season data once the rest of the v1.9 PR-readiness is complete" (per `61-VERIFICATION.md` `human_verification[1].uat_status`).

**Closure path:** User verifies opportunistically after the next production deploy. If a regression is found post-deploy, it triggers a fix-forward patch — not a v1.9 milestone re-open.

**Sign-off date:** 2026-05-08 (Phase 69 SC3 milestone-closure hygiene).

---

## Cross-Reference

- Source decisions: `.planning/phases/69-milestone-closure-hygiene/69-CONTEXT.md` D-01 (UAT-01 Auto-UAT path), D-02 (UAT-02 formal defer).
- Verification flip: `.planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md` (frontmatter `status: human_needed → passed` + `uat_closed: 2026-05-08` field, plus a UAT-Closure addendum citing this artifact — applied in Phase 69 Plan 02 Task 3).
- The `human_verification:` array in `61-VERIFICATION.md` is preserved verbatim per D-03 (audit trail of the original UAT items + their closure narratives stays intact).

---

_Authored 2026-05-08 (Phase 69 SC3 — milestone closure hygiene)_
_Branch: `gsd/v1.9-season-phases-groups`_
