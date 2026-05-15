# Phase 79 — Auto UAT

## Wallclock Baseline

| Measurement | Git SHA | Invocation | Duration | Date |
|-------------|---------|-----------|----------|------|
| Baseline (before D-05) | `28d0469` | `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true` | `13m 27s` | 2026-05-15 |
| Final (after D-05) | _TBD by Plan 07_ | _same command_ | _TBD_ | _TBD_ |

**Target: Final ≤ Baseline × 0.7 (D-06)**
(i.e., Final must be ≤ 9m 23s to achieve the ≥ 30% wallclock reduction required by D-06)

### Baseline Run Details

- **Command:** `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true`
- **Build result:** BUILD SUCCESS
- **Total time (Maven):** 13:26 min
- **Wall time (time builtin):** `13m 27.38s` real, `989.00s` user, `148.92s` system, `140%` CPU
- **Tests:** 1227 Surefire unit + 112 Failsafe IT + 36 E2E Playwright
- **JaCoCo:** 289 classes analyzed, all coverage checks met (≥ 82% line)
- **Finished at:** 2026-05-15T17:45:52+02:00
