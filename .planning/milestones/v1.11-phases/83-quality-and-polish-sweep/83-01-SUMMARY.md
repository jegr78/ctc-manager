---
plan: 83-01
requirements: [QUAL-01]
status: complete
date: 2026-05-17
---

# Plan 83-01 — QUAL-01 Driver-Detail Season-Assignment Chip Order

## Outcome

Driver-detail admin page (`/admin/drivers/{id}`) Season-Assignment chips now render in ascending year order, with secondary sort by season number for split-year seasons. The ORDER BY is enforced at the SQL layer via a new JPQL fetch query on `DriverRepository.findDetailById(UUID)` — the original plan's `@OrderBy` annotation approach was rejected by Hibernate 7 (nested entity-association paths unsupported in JPA `@OrderBy`), so the user chose the Repository-Query strategy.

## Files Modified / Created

| File | Change |
|------|--------|
| `src/main/java/org/ctc/domain/repository/DriverRepository.java` | + `findDetailById(UUID)` JPQL with `LEFT JOIN FETCH d.seasonDrivers/sd.season/sd.team WHERE d.id = :id ORDER BY s.year ASC, s.number ASC` |
| `src/main/java/org/ctc/domain/service/DriverService.java` | + `findDetailById(UUID)` `@Transactional(readOnly = true)` delegating to repo |
| `src/main/java/org/ctc/admin/controller/DriverController.java` | `detail(...)` switched from `findById(id)` to `findDetailById(id)` |
| `src/test/java/org/ctc/domain/repository/DriverRepositoryOrderIT.java` | NEW — 2 `@Test` methods, `@Tag("integration")`, GIVEN/WHEN/THEN |
| `src/test/java/org/ctc/e2e/DriverDetailSmokeE2ETest.java` | NEW — 1 `@Test` method, `@Tag("e2e")`, captures `.screenshots/qual-01-driver-detail-order.png` |
| `.planning/phases/83-quality-and-polish-sweep/83-CONTEXT.md` | D-04 revised to v3 (Repository-Query) — documents the three iterations (startDate → @OrderBy → Repository-Query) and why each prior approach failed |

## Files NOT Modified

- `src/main/java/org/ctc/domain/model/Driver.java` — entity stays clean; no `@OrderBy`, no getter override
- `src/main/resources/templates/admin/driver-detail.html` — template iteration unchanged (`th:each="sd : ${driver.seasonDrivers}"` now iterates the SQL-ordered list)
- `DriverService.findById(...)` — all other callers (edit flow, assignToSeason, save, delete) keep using the simple finder

## Tests

| Test | Result | Evidence |
|------|--------|----------|
| `DriverRepositoryOrderIT.givenMultiSeasonDriver_whenFindDetailById_thenSeasonDriversAreOrderedByYearAsc` | PASS | Inserts seasons in 2025/2023/2024 order, asserts reload yields [2023, 2024, 2025] |
| `DriverRepositoryOrderIT.givenSplitYearSeasons_whenFindDetailById_thenSeasonDriversAreOrderedByYearThenNumber` | PASS | Inserts 2024-2 before 2024-1, asserts reload yields [number 1, number 2] |
| `DriverDetailSmokeE2ETest.givenMultiSeasonDriver_whenViewDetailPage_thenSeasonAssignmentChipsRenderInAscendingYearOrder` | PASS | Seeds 2024/2025/2026 driver, navigates to detail page, asserts chip text years are ascending; screenshot `.screenshots/qual-01-driver-detail-order.png` captured |

Failsafe report: `target/failsafe-reports/failsafe-summary.xml` — `<completed>4</completed> <errors>0</errors> <failures>0</failures>`.

SQL emitted by Hibernate (from H2 dev profile log):
```sql
... from drivers d1_0
left join season_drivers sd1_0 on d1_0.id=sd1_0.driver_id
left join seasons s1_0 on s1_0.id=sd1_0.season_id
left join teams t1_0 on t1_0.id=sd1_0.team_id
where d1_0.id=?
order by s1_0.season_year, s1_0.season_number
```

ROADMAP-SC#1 wording ("explicit `ORDER BY year ASC`") is satisfied at the SQL layer.

## Deviations from Plan

The original 83-01-PLAN.md task list assumed `@OrderBy("season.year ASC, season.number ASC")` on the `Driver.seasonDrivers` field would emit the SQL `ORDER BY`. Hibernate 7 rejected the literal annotation with `PathResolutionException: Unable to resolve path 'season.year'` at EntityManagerFactory startup — JPA `@OrderBy` does NOT support nested entity-association paths (only direct fields of the target entity, `SeasonDriver` in this case). A first executor attempt (commits `43de0996`, `1e515ce1`, `847a78bf`, `9dfd5321`, since reset) used a Java-side getter override on `Driver.getSeasonDrivers()` — that approach worked at runtime but violated SC#1's SQL-level wording and introduced a subtle mutation-via-getter bug (the getter returned a transient sorted copy, not the JPA-managed collection).

The user chose Repository-Query as the final approach (2026-05-17): cleanest separation, SQL-level ORDER BY, no entity mutation, consistent with the QUAL-04 service-layer pattern. The 4 previous commits were reset via `git reset --hard 218b5d0f`.

## Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| Driver entity unchanged (no `@OrderBy`, no getter override) | ✅ `Driver.java` shape identical to pre-Phase-83 |
| SQL ORDER BY emitted at the database layer | ✅ Hibernate log confirms `ORDER BY s1_0.season_year, s1_0.season_number` |
| Repository IT verifies primary sort (year ASC) | ✅ 1 test passes |
| Repository IT verifies secondary sort (number ASC within same year) | ✅ 1 test passes |
| Playwright smoke confirms chip-list visual order | ✅ 1 test passes; screenshot captured |
| Driver-detail template unchanged | ✅ `driver-detail.html` not in modification list |
| No new Flyway migration | ✅ no `V*.sql` touched |
| No `pom.xml` change | ✅ no new dependency |
| Commits on milestone branch `gsd/v1.11-tooling-and-cleanup` | ✅ no sub-branch created |
