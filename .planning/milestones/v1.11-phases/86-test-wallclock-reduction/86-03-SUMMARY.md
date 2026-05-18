---
phase: 86-test-wallclock-reduction
plan: 03
status: complete
date: 2026-05-17
---

# Plan 86-03 — Backup-IT DirtiesContext → ImportLockServiceResetHelper

## Outcome

D-03 cluster fix applied. `ImportLockServiceResetHelper` `@TestComponent` is wired into all 3 backup ITs and called from each IT's `@AfterEach`. Per-method `@DirtiesContext` audit converted 2 of 3 class-level `BEFORE_EACH_TEST_METHOD` annotations to method-level `AFTER_METHOD` on the latch-dependent methods only.

`ImportLockService.unlock()` already exists and is idempotent via `isHeldByCurrentThread()` guard — no production-code edit required, no `forceUnlock()` method added.

## Per-Method Latch Classification

| IT | Method | Latch-dependent? | Final annotation | Rationale |
|---|---|---|---|---|
| ImportConcurrentLockIT | `givenSlowImportRunningOnThreadA_whenThreadBPostsImportExecute_thenThreadBReceivesHttp409AndAuditTableHasExactlyOneSuccessRow` | ✓ yes | class-level `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` retained | All methods latch-dep — class-level is the cleanest expression |
| ImportLockBannerAdviceIT | `givenLockHeld_whenGetAdminSeasons_thenResponseBodyContainsBannerWording` | ✓ yes | method-level `@DirtiesContext(AFTER_METHOD)` | CountDownLatch handshake |
| ImportLockBannerAdviceIT | `givenLockHeld_whenGetSiteIndex_thenBannerWordingAbsent` | ✓ yes | method-level `@DirtiesContext(AFTER_METHOD)` | CountDownLatch handshake |
| ImportLockBannerAdviceIT | `givenLockNotHeld_whenGetAdminSeasons_thenBannerWordingAbsent` | ✗ no | — (bare) | No latch usage — helper.reset() in @AfterEach is sufficient |
| ImportLockedPostRejectorIT | `givenLockHeld_whenPostToAdminTeamsSave_thenHttp503AndBannerWordingInBody` | ✓ yes | method-level `@DirtiesContext(AFTER_METHOD)` | CountDownLatch handshake |
| ImportLockedPostRejectorIT | `givenLockHeld_whenPostToWhitelistedImportExecuteFromSecondClient_thenInterceptorAllowsButControllerReturns409` | ✓ yes | method-level `@DirtiesContext(AFTER_METHOD)` | CountDownLatch handshake |
| ImportLockedPostRejectorIT | `givenLockHeld_whenGetAdminSeasons_thenPassesThrough` | ✓ yes | method-level `@DirtiesContext(AFTER_METHOD)` | CountDownLatch handshake |
| ImportLockedPostRejectorIT | `givenLockNotHeld_whenPostToAdminTeamsSave_thenProceedsNormally` | ✗ no | — (bare) | No latch usage — helper.reset() in @AfterEach is sufficient |

## Context-eviction delta

| IT | Before | After | Δ |
|---|---|---|---|
| ImportConcurrentLockIT | 1 (class BEFORE_EACH × 1 method) | 1 (class BEFORE_EACH × 1 method) | 0 |
| ImportLockBannerAdviceIT | 3 (class BEFORE_EACH × 3 methods) | 2 (method AFTER on 2 of 3) | −1 |
| ImportLockedPostRejectorIT | 4 (class BEFORE_EACH × 4 methods) | 3 (method AFTER on 3 of 4) | −1 |
| **Total** | **8** | **6** | **−2** |

Net 25% reduction in context evictions across this cluster — the latch-free methods no longer trigger a fresh-context rebuild. The cross-IT context-cache key remains identical (same `@SpringBootTest` + `@Import` + `@TestPropertySource` + `@AutoConfigureMockMvc`), so the saved evictions apply to the shared context cache. RESEARCH Assumption A1 ("per-method classification") confirmed by 3-seed evidence — no seed exposed pollution on the latch-free methods.

## 3-Seed Verification Evidence

Run command (Failsafe-only, surefire skipped for speed):

```bash
./mvnw verify -Dtest=DoesNotExist -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test='ImportConcurrentLockIT,ImportLockBannerAdviceIT,ImportLockedPostRejectorIT' \
  -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=<seed> \
  -Djacoco.skip=true
```

| Seed | Tests run | Failures | Errors | Verdict |
|---|---|---|---|---|
| 1234 | 8 | 0 | 0 | BUILD SUCCESS (1:29 min) |
| 5678 | 8 | 0 | 0 | BUILD SUCCESS |
| 9999 | 8 | 0 | 0 | BUILD SUCCESS |

All 24 method invocations (3 seeds × 8 methods) green. No seed-dependent regressions.

## Key files

### Created (Task 1, cherry-picked from worktree at `059810f4` → `2914ad62`)

- `src/test/java/org/ctc/backup/it/ImportLockServiceResetHelper.java` — `@TestComponent` exposing `reset()` that calls `ImportLockService.unlock()`; idempotent via the production-side `isHeldByCurrentThread()` guard

### Modified (Task 2)

- `src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java` — helper injected, `@AfterEach tearDownLock()` added, multi-line Phase-76 comment block compressed to a single-line rationale on the retained class-level `@DirtiesContext`
- `src/test/java/org/ctc/backup/it/ImportLockBannerAdviceIT.java` — helper injected, `@AfterEach tearDownLock()` added, class-level `@DirtiesContext` dropped, method-level `@DirtiesContext(AFTER_METHOD)` added on 2 of 3 methods with rationale comments
- `src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java` — same pattern: helper injected, class-level dropped, method-level on 3 of 4 methods

## Plan-05 Note (context-load count expectation)

Expected direction: **down** for this cluster. The cross-IT context-cache key is shared between the 3 backup ITs; the −2 per-run eviction delta means roughly 2 fewer full Spring-context builds per Failsafe pass. ContextLoadCountListener (Plan 86-01) will quantify the exact delta at the Plan 86-05 measurement gate.

## Issues encountered

None. The helper's `reset()` is documented as a defensive contract (test-thread cannot release a lock held by another thread — the existing `unlock()` no-ops via `isHeldByCurrentThread()`), but the seeds confirm no test relied on it for correctness — proper `releaseLatch.countDown()` in each latch-dep method handles cleanup.

## Follow-ups

None for this plan. Plan 86-05 will record the measured ContextLoadCountListener delta for the backup-IT cluster in `docs/test-performance.md`.
