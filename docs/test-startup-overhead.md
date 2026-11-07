# Test startup overhead

## Automatic rendering

Normal development startup seeds the fixture and renders its website. `app.seed.generate-site` defaults to `true`, preserving this behavior. Surefire, ordinary Failsafe and browser-test JVMs explicitly set it to `false`. Test fixtures and guest validation still run; only the automatic startup render is omitted. Explicit site-generation, publication and browser scenarios continue to call the real generator. The existing `app.seed.generate-team-cards=false` setting is unchanged.

For an intentional startup-render check, pass `-Dapp.seed.generate-site=true` to the normal Maven lifecycle. Read back the property and actual `Site generated` log before comparing timing. Full final verification remains `./mvnw clean verify -Pe2e -Ddocker.available=true`; no tests, database cases, coverage or security gates are skipped.

## Measurements

The green CI reference `881802b869d8b0a1d7e887df603e4c334079cb5c`, run 42347357346 on a GitHub-hosted Ubuntu runner, completed the build step in 38:11. Its Maven phase summaries show 3,747 unit, 2,371 integration and 257 browser cases, with no failures/errors/skips. The retained XML reports contain 2,369 integration cases. Keep the phase-summary and XML totals separate.

The log contains 206 Spring `Started` messages, with 516.08 summed startup seconds. These times overlap across forks and exclude subsequent `CommandLineRunner` work. The same run logs 202 automatic seeded-site renders, totaling 56,957 pages. Some start from shared database state changed by earlier scenarios; these counts are observed work, not a clean-fixture count or an estimated wallclock saving. #366 separately fixes the largest accumulating fixture suite.

Record after timings and actual counts on the delivered PR. A focused class duration is not a whole-build benchmark. Concurrent local builds and other host workload must be reported; a CI run uses a separate runner and cannot serve as a same-host before/after timing.

## Per-fork evidence

`target/test-perf/context-loads-<PID>.txt` retains the existing initialization count. The new `context-loads-<PID>-cache.txt` records actual Spring cache size, hit count and miss count at fork shutdown. These files contain numeric counters, not configuration values. Existing cache-key fingerprint files remain available. CI includes this directory in the test-reports artifact.

The cache statistics use Spring's existing default test-context cache. They do not change cache size, fork count, cache eviction, test ordering or state isolation. Compare the files per fork and execution stage before summing counts. A cache miss is not necessarily one successful application startup.

Spring keys include dynamic property methods and bean overrides, so classes with genuinely different configurations remain separate. Equivalent configurations can continue using `CtcDevSpringBootContext`. See [Spring context caching](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html).

## MariaDB lifecycle decision

Real locking, snapshot-conflict, restore admission and migration coverage remain required. Runtime container starts are measured separately from the number of source declarations. Persistent cross-build container reuse is not enabled by this change. A future worker-scoped server would need separate class databases/users, proof that classes cannot read each other's schema, and a lifetime extending beyond all cached contexts. Stopping a singleton through JUnit's per-class `@Container` lifecycle would break later contexts. See [Testcontainers manual lifecycle](https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/).

No owner database, Mac test data or Box installation is changed by these test optimizations.
