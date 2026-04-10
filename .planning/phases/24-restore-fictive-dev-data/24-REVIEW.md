---
phase: 24
status: issues_found
files_reviewed: 2
depth: standard
findings:
  critical: 0
  warning: 1
  info: 1
  total: 2
---

## Review: Phase 24 — Restore Fictive Dev Data

Files reviewed:
- `src/main/java/org/ctc/admin/TestDataService.java`
- `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java`

---

### WR-01 — Missing `@Profile` on `TestDataService` [confidence: 85]

**File:** `src/main/java/org/ctc/admin/TestDataService.java`

**Issue:** `TestDataService` is annotated `@Service` with no `@Profile` restriction. Spring registers and instantiates it in every application context — including `prod` and `docker` profiles. All of its injected dependencies (10 repositories + `TeamCardService`) get wired in production unnecessarily.

The `seed()` method has an early-exit guard (`if (seasonRepository.count() > 0)`), but that only prevents data insertion — it does not prevent the bean from being fully instantiated in all profiles.

**Reference:** CLAUDE.md — "Profiles: Auth only for `prod`/`docker`; `dev`/`local` remains without auth." The companion `DemoDataSeeder` correctly uses `@Profile("demo")` as the established pattern.

**Fix:**

```java
@Profile({"dev", "demo"})
@Slf4j
@Service
@RequiredArgsConstructor
public class TestDataService {
```

---

### IR-01 — Missing `// given` comment blocks in test bodies [confidence: 80]

**File:** `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java`, all 8 test methods

**Issue:** All test methods are named with the `givenDevSeed_whenStarted_then...` pattern, explicitly encoding a precondition in the method name. CLAUDE.md mandates `// given / // when / // then` comment blocks in test bodies for structuring. None of the 8 test bodies contain a `// given` comment — they jump directly to `// when`.

The CLAUDE.md exemption for tests without preconditions (`whenAction_thenResult()`) does not apply here — the precondition (dev seed having executed via `DevDataSeeder` at context startup) is deliberately part of each test name.

**Fix:** Add a `// given` comment to each test body:

```java
// given — dev seed executed via DevDataSeeder on context startup (active profile: dev)
// when
long parentCount = teamRepository.findAll().stream()
        ...
// then
assertThat(parentCount).isEqualTo(10);
```
