---
quick_id: 260529-dc2
slug: favicon-noresource-404
description: Fix /favicon.ico 404 and NoResourceFoundException misclassification
date: 2026-05-29
status: complete
---

# Quick Task 260529-dc2: Favicon /favicon.ico 404 + NoResourceFoundException handling

## Problem

`data/dev/logs/app.log` recorded an `ERROR "Unhandled exception"` with a full
stacktrace for `org.springframework.web.servlet.resource.NoResourceFoundException:
No static resource favicon.ico for request '/favicon.ico'`.

Two root causes:

1. **No root `favicon.ico`.** The app only ships `static/admin/img/favicon.svg`
   (linked from `admin/layout.html`) and the public site uses the CTC logo. The
   browser's automatic root-level `/favicon.ico` probe — issued from contexts
   without an HTML `<head>` (e.g. a directly opened generated `overlay.png`
   image tab) — had no target.
2. **`NoResourceFoundException` misclassified.** Under Spring Framework 7
   (Spring Boot 4) `NoResourceFoundException extends jakarta.servlet.ServletException`
   (no longer `ResponseStatusException`). The `@ControllerAdvice` therefore fell
   through to `handleGeneral(Exception)`, logging every missing static resource
   as `ERROR` + stacktrace and returning HTTP 500 instead of 404.

## Tasks

### Task 1 — NoResourceFoundException → 404 (TDD)
- **files:** `GlobalExceptionHandler.java`, `GlobalExceptionHandlerTest.java`
- **action:** Add `@ExceptionHandler(NoResourceFoundException.class)` returning a
  404 `admin/error` view and logging at `debug` (not `ERROR`/stacktrace). Drive
  via a failing unit test first.
- **verify:** `./mvnw test -Dtest=GlobalExceptionHandlerTest`
- **done:** Missing static resources resolve to 404 without an ERROR stacktrace.

### Task 2 — Provide root favicon.ico
- **files:** `src/main/resources/static/favicon.ico`
- **action:** Render a 32×32 `favicon.ico` faithful to `favicon.svg` (dark
  rounded square + cyan lightning bolt) via Python stdlib (zlib) — no new
  dependency. Spring Boot serves it automatically at `/favicon.ico`.
- **verify:** `GET /favicon.ico` → 200.
- **done:** Root favicon probe succeeds in every context.

### Task 3 — Integration coverage
- **files:** `StaticResourceErrorHandlingIT.java`
- **action:** `@Tag("integration")` `@SpringBootTest` `dev` MockMvc IT asserting
  `/favicon.ico` → 200 and a missing static resource → 404 (not 500).
- **verify:** `./mvnw clean verify -Pe2e`
- **done:** Both fixes pinned end-to-end; coverage gate held.

## must_haves

- truths:
  - Missing static resources return HTTP 404, not 500.
  - `NoResourceFoundException` is logged at `debug`, never `ERROR` with stacktrace.
  - `/favicon.ico` returns 200.
- artifacts:
  - `src/main/resources/static/favicon.ico`
  - `GlobalExceptionHandler.handleNoResourceFound`
  - `StaticResourceErrorHandlingIT`
- key_links:
  - `src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java`
  - `src/test/java/org/ctc/admin/controller/StaticResourceErrorHandlingIT.java`
