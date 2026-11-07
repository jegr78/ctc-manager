# Test startup overhead

## Automatic rendering

Normal development startup seeds the fixture and renders its website. `app.seed.generate-site` defaults to `true`, preserving this behavior. Surefire, ordinary Failsafe and browser-test JVMs explicitly set it to `false`. Test fixtures and guest validation still run; only the automatic startup render is omitted. Explicit site-generation, publication and browser scenarios continue to call the real generator. The existing `app.seed.generate-team-cards=false` setting is unchanged.

For an intentional startup-render check, pass `-Dapp.seed.generate-site=true` to the normal Maven lifecycle. Read back the property and actual `Site generated` log before comparing timing. Full final verification remains `./mvnw clean verify -Pe2e -Ddocker.available=true`; no tests, database cases, coverage or security gates are skipped.

## Measurements

The green CI reference `881802b869d8b0a1d7e887df603e4c334079cb5c`, run 42347357346 on a GitHub-hosted Ubuntu runner, completed the build step in 38:11. Its Maven phase summaries show 3,747 unit, 2,371 integration and 257 browser cases, with no failures/errors/skips. The retained XML reports contain 2,369 integration cases. Keep the phase-summary and XML totals separate.

The log contains 206 Spring `Started` messages, with 516.08 summed startup seconds. These times overlap across forks and exclude subsequent `CommandLineRunner` work. The same run logs 202 automatic seeded-site renders, totaling 56,957 pages. Some start from shared database state changed by earlier scenarios; these counts are observed work, not a clean-fixture count or an estimated wallclock saving. #366 separately fixes the largest accumulating fixture suite.

A controlled flag comparison on source `32ffdd76f5e424b7473e25965fd9476817da1756` used the normal `integration-test` lifecycle with `TyrePersistenceIT,TyrePersistenceMariaDbIT` and six associated unit cases. Both runs passed 6 unit and 22 integration cases with zero failures/errors/skips. The host was Linux, 8 logical CPUs and 15 GiB RAM, with warm Maven dependencies and local container images. Each fork requested a 1 GiB heap; Maven requested 512 MiB. Other host workload was not controlled.

| Observation | Startup render enabled | Startup render disabled |
| --- | ---: | ---: |
| H2 class, 11 cases | 48.954 s | 43.548 s |
| MariaDB class, 11 cases | 98.517 s | 88.758 s |
| Automatic seeded-site renders | 2, totaling 638 pages | 0 |
| Successful Spring application starts | 2 | 2 |
| Spring cache counters per integration fork | 1 miss, 144 hits | 1 miss, 144 hits |
| Actual MariaDB container starts | 1, 19.600 s | 1, 15.009 s |

Both modes seeded the same 21 teams, 6 seasons, 108 drivers, 1,536 lineups and 1,462 results per application and retained two guest lineups. The class durations include startup and real persistence assertions. Measured Maven wall time was 168.912 s before and 109.168 s after, but the first run compiled cold output and container startup varied. These whole-command durations do not isolate rendering savings. The demonstrated change removes incidental renders; it does not reduce the number of required application contexts.

A focused class duration is not a whole-build benchmark. Record the complete delivered CI comparison on the PR. A CI run uses a separate runner and cannot serve as a same-host before/after timing.

## Per-fork evidence

`target/test-perf/context-loads-<PID>.txt` retains the existing initialization count. The new `context-loads-<PID>-cache.txt` records actual Spring cache size, hit count and miss count at fork shutdown. These files contain numeric counters, not configuration values. Existing cache-key fingerprint files remain available. CI includes this directory in the test-reports artifact.

The cache statistics use Spring's existing default test-context cache. They do not change cache size, fork count, cache eviction, test ordering or state isolation. Compare the files per fork and execution stage before summing counts. A cache miss is not necessarily one successful application startup.

Spring keys include dynamic property methods and bean overrides, so classes with genuinely different configurations remain separate. Equivalent configurations can continue using `CtcDevSpringBootContext`. See [Spring context caching](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html).

## MariaDB lifecycle decision

Real locking, snapshot-conflict, restore admission and migration coverage remain required. Runtime container starts are measured separately from the number of source declarations. The representative MariaDB run starts one database container in either mode, while both seeded applications previously generated 638 unrelated pages. The first change therefore removes automatic rendering and keeps the database lifecycle intact. The two measured integration forks each reuse one cached context for all 11 cases. H2 and MariaDB intentionally keep distinct datasource configurations; mock/property differences used to test other configurations also remain distinct. No new configuration consolidation or smaller context is necessary for the seeder/cache-marker unit tests, which already use a small `ApplicationContextRunner` and a plain cache respectively.

Persistent cross-build container reuse is not enabled by this change. A future worker-scoped server would need separate class databases/users, proof that classes cannot read each other's schema, and a lifetime extending beyond all cached contexts. Stopping a singleton through JUnit's per-class `@Container` lifecycle would break later contexts. See [Testcontainers manual lifecycle](https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/).

No owner database, Mac test data or Box installation is changed by these test optimizations.
