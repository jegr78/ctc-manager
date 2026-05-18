# Phase 80 — Deferred Items

> Discovered during Phase 80 execution; out-of-scope for Phase 80 plans (which
> touch only build config + docs). Each item is filed for a follow-up
> hot-fix / future-phase resolution.

## 2026-05-16 — RESOLVED (FALSE POSITIVE): apparent IT compile error in `BackupSchemaExclusionIT`

**Status:** Resolved — was an IDE-cache artifact, not a real compile error.

**Root cause:**

VS Code's Java Language Server (Eclipse JDT) had written `.class` files into
`target/test-classes/` with `"Unresolved compilation problem"` error markers.
Maven Failsafe loaded these stale `.class` files at runtime instead of
triggering a fresh javac compile, because Plan 80-03's `./mvnw -q verify` ran
WITHOUT a preceding `clean`. The runtime error therefore surfaced the Eclipse
JDT marker — NOT a javac error.

**Diagnostic confirmation (2026-05-16, post Plan 80-03):**

- v1.10 closer commit `45aabfd` CI: success on all three workflows (CI,
  MariaDB Migration Smoke, Push on master)
- `BackupSchemaExclusionIT.java` byte-identical between `45aabfd` and HEAD
- `EntityRef.java` byte-identical between `45aabfd` and HEAD
- pom.xml diff `45aabfd..HEAD` contains ONLY: version bump + new
  `<profile id="rewrite">` (Plan 80-01). No AssertJ / JUnit / Spring Boot /
  Java version change.
- Local + CI toolchain: identical (Eclipse Temurin/Homebrew JDK 25)
- `./mvnw clean test-compile`: compiles cleanly with javac (no errors)
- `./mvnw -Dit.test=BackupSchemaExclusionIT failsafe:integration-test
  failsafe:verify` (isolated, post-clean): `Tests run: 1, Failures: 0,
  Errors: 0` — BUILD SUCCESS

**Telltale sign:** The exact error string `"Unresolved compilation problem"`
is an Eclipse JDT compiler signature, never produced by Maven's invocation
of javac. javac errors look like `error: cannot find symbol` /
`error: incompatible types`, etc. If you ever see `"Unresolved compilation
problem"` in a Maven test output, the next step is `./mvnw clean test-compile`
and re-run — NOT to start editing the source file.

**Lesson saved to user memory** (`feedback_unresolved_compilation_problem.md`):
treat any `"Unresolved compilation problem"` in Maven output as VS Code IDE
cache artifact first; `mvn clean test-compile` is the diagnostic, not a fix.

**No action needed** — Plan 80-04 can proceed with a clean `./mvnw verify`
gate. The original (incorrect) write-up of four AssertJ "fix options" has
been removed; it was drafted before the IDE-cache root cause was identified
and applying any of them would have introduced unnecessary churn into
unrelated, working test code. The history of the misdiagnosis is preserved
in this file's git log (`git log -p -- .planning/phases/80-openrewrite-integration/deferred-items.md`).

**Impact on Plan 80-03 outcomes (reassessed after diagnosis):**

- REWR-01 (dryRun preview without source mutation) — **unaffected** ✓
- REWR-03 (no lifecycle binding / default-build isolation) — **unaffected** ✓
- D-09 (no coverage regression vs 87.80% v1.10 baseline) — **unblocked**;
  Plan 80-04's End-Gate `./mvnw clean verify` will produce the JaCoCo CSV
  the first time around. The previous "deferred" note in
  `80-VERIFICATION.md` should be corrected when Plan 80-04 writes its
  closure.
- Plan 80-04 readiness — **unblocked**; no prerequisite hot-fix needed.

---

*Phase: 80-openrewrite-integration*
*Last updated: 2026-05-16*
