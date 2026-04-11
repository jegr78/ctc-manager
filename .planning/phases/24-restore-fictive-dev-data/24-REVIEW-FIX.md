---
phase: 24
status: all_fixed
findings_in_scope: 1
fixed: 1
skipped: 0
iteration: 1
---

## Code Review Fix Report: Phase 24

### WR-01 — Missing `@Profile` on `TestDataService` — FIXED

**Commit:** `fix(24): add @Profile("dev") to TestDataService to prevent prod instantiation`

**Change:** Added `@Profile("dev")` annotation and `import org.springframework.context.annotation.Profile` to `TestDataService.java`. The bean is now only instantiated in the `dev` profile, matching `DevDataSeeder` which is its sole consumer.

**File:** `src/main/java/org/ctc/admin/TestDataService.java`

---

### Out of Scope (Info findings — use `--all` to include)

- **IR-01** — Missing `// given` comment blocks in test bodies (severity: info)
