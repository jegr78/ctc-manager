---
quick_id: 260529-dc2
slug: favicon-noresource-404
description: Fix /favicon.ico 404 and NoResourceFoundException misclassification
date: 2026-05-29
status: complete
---

# Quick Task 260529-dc2 — Summary

## Outcome

Both root causes behind the `data/dev/logs` favicon stacktrace are fixed:

1. **`NoResourceFoundException` now returns 404, logged at `debug`.** A dedicated
   `@ExceptionHandler(NoResourceFoundException.class)` in `GlobalExceptionHandler`
   replaces the accidental `handleGeneral(Exception)` fall-through that logged an
   `ERROR` stacktrace and returned HTTP 500. This applies to **all** missing
   static resources, not just the favicon.
2. **Root `favicon.ico` added.** A 32×32 `static/favicon.ico` faithful to
   `favicon.svg` (dark rounded square + cyan lightning bolt), rendered via Python
   stdlib (no new dependency). Spring Boot serves it at `/favicon.ico`, so the
   browser's automatic root probe succeeds in every context.

## Commits

| Hash | Type | Subject |
|------|------|---------|
| `0bdb0009` | fix | return 404 for missing static resources |
| `29c481d1` | feat | add root favicon.ico |
| `791c945d` | test | pin favicon 200 and missing-resource 404 |

## Verification

- `./mvnw clean verify -Pe2e` → **BUILD SUCCESS**, all tests green (Surefire +
  Failsafe + Playwright E2E).
- JaCoCo: "All coverage checks have been met" (≥ 89.43 % baseline held).
- `GlobalExceptionHandlerTest` 9/9, `StaticResourceErrorHandlingIT` 2/2.
- Favicon rendering visually confirmed against `favicon.svg`.

## Notes

- Implemented inline on the active milestone branch `gsd/v1.14-team-card-redesign`
  per CLAUDE.md inline-sequential lock (no executor subagent / worktree).
- Isolated bugfix unrelated to the v1.14 team-card redesign; co-located on the
  milestone branch at the user's explicit request.
