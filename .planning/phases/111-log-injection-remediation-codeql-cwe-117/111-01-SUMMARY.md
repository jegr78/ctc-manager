---
phase: 111
plan: 01
type: execute
status: complete
requirements: [SEC-LOG-01]
requirements-completed: [SEC-LOG-01]
---

# Plan 111-01 Summary: Central LogSanitizer

## What was built

Created the cross-cutting `LogSanitizer` utility (Wave-0 foundation for the
call-site fixes in Plan 111-02) plus its behaviour-pinning unit test, via TDD
(RED → GREEN, two atomic commits).

## Key files

### Created
- `src/main/java/org/ctc/util/LogSanitizer.java` — `public final class` with
  `private LogSanitizer() {}` and `public static String sanitize(Object value)`.
  Two-pass body: `String.valueOf(value).replaceAll("\\R", "_").replaceAll("[\\x00-\\x08\\x09\\x0E-\\x1F\\x7F]", "_")`.
  The `\\R` first pass is the CodeQL-recognised `java/log-injection` taint barrier
  (covers CR/LF/CRLF/unicode line terminators); the second pass strips remaining
  C0 controls + TAB + DEL → single underscore. One WHY-comment only; no Lombok,
  no Spring, no `@SuppressFBWarnings`. New neutral package `org.ctc.util` (D-08).
- `src/test/java/org/ctc/util/LogSanitizerTest.java` — package-private, NO `@Tag`
  (plain Surefire unit test), AssertJ, Given-When-Then, ParameterizedTest. 7 test
  methods (10 executions) pinning D-01/D-02/D-03.

## Decisions implemented
- **D-01/D-02/D-03** — control chars (CR/LF/TAB/C0/DEL) → single `_`, 1:1 (no
  run-collapse, no length cap), `null` → literal `"null"`, behaviour pinned.
- **D-08** — new top-level `org.ctc.util` package.
- **D-09** — `public static String sanitize(Object)`, null-safe via `String.valueOf`.

## Verification
- `./mvnw test -Dtest=LogSanitizerTest` → **Tests run: 10, Failures: 0, Errors: 0** (GREEN).
- RED confirmed first via compile failure (no `LogSanitizer` symbol).
- Acceptance greps pass: `replaceAll("\\R"` present, `sanitize(Object` signature,
  no Lombok/`@SuppressFBWarnings`, exactly 1 comment line, no `@Tag` in test.

## Notable behaviour
- `\R` matches CRLF as one atomic unit → `"user\r\nINFO"` → `"user_INFO"` (one
  underscore for the pair), confirmed empirically by `givenCrlfPayload...`.

## Commits
- `994fedf1` test(111-01): add behaviour-pinning LogSanitizerTest (RED)
- `10c4886a` feat(111-01): add central LogSanitizer for CWE-117 remediation (GREEN)

## Self-Check: PASSED

## Next
Plan 111-02 — wrap the 29 CodeQL-flagged log arguments (+ same-statement siblings
per D-06) across 17 files via `sanitize()` using static import.
