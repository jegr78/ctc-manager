# Phase 80 — Deferred Items

> Discovered during Phase 80 execution; out-of-scope for Phase 80 plans (which
> touch only build config + docs). Each item is filed for a follow-up
> hot-fix / future-phase resolution.

## 2026-05-16 — Pre-existing IT compile error in `BackupSchemaExclusionIT`

**Discovered during:** Plan 80-03 Task 1 (`./mvnw -q verify`)
**File:** `src/test/java/org/ctc/backup/schema/BackupSchemaExclusionIT.java:40`
**Lineage:** Created in Phase 72 plans 01 (Wave 0 RED) + 04 (Wave 3 GREEN);
byte-identical between v1.10 closer commit `45aabfd` and current
`gsd/v1.11-tooling-and-cleanup` HEAD. NOT modified by any Plan 80-* commit.

**Symptom:**

```
[ERROR] Errors:
[ERROR]   BackupSchemaExclusionIT.givenSpringContext_whenGetExportOrder_thenDataImportAuditIsNotPresent:40
  Unresolved compilation problem:
  The method doesNotContain(Class<capture#1-of ?>...) in the type
  AbstractIterableAssert<capture#2-of ?,List<? extends Class<capture#1-of ?>>,
  Class<capture#1-of ?>,ObjectAssert<Class<capture#1-of ?>>>
  is not applicable for the arguments (Class<DataImportAudit>)

[ERROR] Tests run: 231, Failures: 0, Errors: 1, Skipped: 3
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-failsafe-plugin:3.5.5:verify
```

**Likely root cause:** Java 25 + AssertJ generic-inference interaction.
`EntityRef::entityClass` returns `Class<? extends BaseEntity>` (or similar
bounded wildcard), and `.extracting(...)` produces an
`AbstractIterableAssert<…, Class<capture#1-of ?>, …>`. The `doesNotContain`
overload then receives an argument of type `Class<DataImportAudit>`, which
the compiler cannot prove compatible with the captured wildcard.

**Suggested fixes (any one):**

1. **`.containsAnyOf(...)` inversion → `.allMatch(c -> !c.equals(DataImportAudit.class))`**
   — sidesteps the capture problem entirely.
2. **Cast to raw `Class` array at the call site:** `.doesNotContain((Class<?>) DataImportAudit.class)`
   — narrows the inference to a single bounded type.
3. **Change `EntityRef.entityClass()` return type** to `Class<?>` (instead
   of a bounded wildcard) — broader change with potential ripple effects on
   other callers, NOT recommended in a hot-fix.
4. **Use `extracting(EntityRef::entityClass, InstanceOfAssertFactories.iterable(Class.class))`**
   — verbose but unambiguous.

**Scope to assess in the fix plan:** check whether any other
`*IT.java`/`*Test.java` uses the same `EntityRef::entityClass + doesNotContain(Class<X>)`
shape — if so, the fix should be applied consistently. A grep for
`\.extracting\(EntityRef::entityClass\)\s*\n?\s*\.doesNotContain` over
`src/test/java/` would surface them.

**Why deferred:**

- Plan 80-03's `<plan_scope>` explicitly forbids `src/main/java/**` +
  `src/test/java/**` changes ("If `git diff --quiet` fails after
  `rewrite:dryRun`, STOP and report `NEEDS_CONTEXT` — dryRun must be
  read-only").
- The fix attempt limit (3 per task) is also intended to prevent
  scope-creep into pre-existing problems.
- Phase 80 is build-config + docs only; touching test source for an
  unrelated compile error would violate the phase boundary.

**Impact on Plan 80-03 outcomes:**

- REWR-01 (dryRun preview without source mutation) — **unaffected** ✓
- REWR-03 (no lifecycle binding / default-build isolation) — **unaffected** ✓
- D-09 (no coverage regression vs 87.80% v1.10 baseline) — **deferred**
  (Failsafe halt prevented JaCoCo CSV; 80-VERIFICATION.md captures this).
- Plan 04 readiness — **unaffected** (Plan 04 will run `rewrite:run` then
  re-verify; the pre-existing IT compile error must be resolved before
  Plan 04 can prove its own coverage gate, but THAT is Plan 04's problem
  to surface to the user, not 80-03's).

**Suggested next-action:** Either (a) a `fix(72):` hot-fix commit applying
fix option 1 or 2 above on the current `gsd/v1.11-tooling-and-cleanup`
branch BEFORE Plan 80-04 executes; or (b) defer to a dedicated Phase 87
clean-up plan and document the workaround there.

---

*Phase: 80-openrewrite-integration*
*Last updated: 2026-05-16*
