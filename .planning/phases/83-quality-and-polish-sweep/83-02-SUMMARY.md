---
plan: 83-02
requirements: [QUAL-02]
status: complete
date: 2026-05-17
---

# Plan 83-02 — QUAL-02 DevDataSeeder Profile Widening

## Outcome

`./mvnw spring-boot:run -Dspring-boot.run.profiles=local` now activates the test-data seeder via `DevDataSeeder` + `TestDataService`, eliminating the previously required external Saison-2023 bootstrap step. Both Spring components widened to `@Profile({"dev", "local"})` in the same atomic commit per CONTEXT.md D-09 — DevDataSeeder depends on TestDataService as a `final` constructor-injected field, so single-file widening would have caused `NoSuchBeanDefinitionException` at `local` profile startup.

## Files Modified

| File | Line | Before | After |
|------|------|--------|-------|
| `src/main/java/org/ctc/admin/DevDataSeeder.java` | 12 | `@Profile("dev")` | `@Profile({"dev", "local"})` |
| `src/main/java/org/ctc/admin/TestDataService.java` | 40 | `@Profile("dev")` | `@Profile({"dev", "local"})` |

## Files NOT Modified

- `DemoDataSeeder` — stays `@Profile("demo")` (D-14)
- `application-local.yml` — unchanged (test-data seeding driven by profile annotation, not YAML)
- No Flyway migration, no new dependency, no template change

## Tests

- **Compile gate:** `./mvnw test-compile -q` exit 0 (Lombok regeneration of getters/constructors verified by build-guard)
- **No automated IT added** per CONTEXT.md D-15 (skip discretion) — the bean activation under `local` would require a MariaDB testcontainer or a misleading H2 JDBC override that defeats the test purpose
- **Manual verification** (deferred to operator + final phase-end `./mvnw verify -Pe2e`):
  1. `docker compose up db -d` (local MariaDB)
  2. `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
  3. Expect log line `Seed data created: <N> teams, <N> seasons, ...` (first run) OR `Seed data already present, skipping` (re-run)
  4. Verify `dev` profile end-to-end suite unaffected via Wave-2 `./mvnw verify -Pe2e`

## Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| DevDataSeeder annotated `@Profile({"dev", "local"})` | ✅ |
| TestDataService annotated `@Profile({"dev", "local"})` | ✅ |
| DemoDataSeeder unchanged | ✅ |
| Compile clean | ✅ |
| Single atomic commit | ✅ |
| No prod/docker profile exposure | ✅ (annotations explicitly only `dev` + `local`) |
| Profile-leakage threat T-83-02 mitigated | ✅ (production deploys run `prod` or `docker` profile — neither matches the seeder activation set) |

## Notes

The ROADMAP-SC#2 wording "without requiring a separate Saison-2023 fixture path" referred to an EXTERNAL operator bootstrap path (manual SQL inserts / Admin-UI clicks under `local` profile). RESEARCH.md confirmed no such external artifact exists in the repo. The annotation widening alone constitutes the path-elimination; no further code/docs/data changes are required.
