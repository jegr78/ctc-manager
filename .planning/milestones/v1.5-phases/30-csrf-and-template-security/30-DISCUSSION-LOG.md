# Phase 30: CSRF and Template Security - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-13
**Phase:** 30-csrf-and-template-security
**Areas discussed:** CSRF-Token-Strategie, Template-Validation Scope, Fehler-Feedback, Dev/Local Verhalten

---

## CSRF-Token-Strategie

### CSRF Token Delivery Method

| Option | Description | Selected |
|--------|-------------|----------|
| Meta-Tag + JS-Header | Standard Spring/Thymeleaf Pattern: meta tags in layout, JS reads and sets X-CSRF-TOKEN header | :heavy_check_mark: |
| Cookie-basiert (CookieCsrfTokenRepository) | Spring writes XSRF-TOKEN cookie, JS reads cookie and sends as header | |

**User's choice:** Meta-Tag + JS-Header (Empfohlen)
**Notes:** Standard approach, all AJAX calls benefit automatically.

### JS Utility Function

| Option | Description | Selected |
|--------|-------------|----------|
| Zentrale csrfFetch() Funktion | Wrapper function in shared JS fragment, existing calls change fetch() to csrfFetch() | :heavy_check_mark: |
| Einzeln pro fetch()-Call | Each existing fetch() POST call gets CSRF header manually | |

**User's choice:** Zentrale csrfFetch() Funktion (Empfohlen)
**Notes:** Future-proof for new AJAX calls.

### JS Location

| Option | Description | Selected |
|--------|-------------|----------|
| Thymeleaf-Fragment im Layout | Inline script in layout.html fragment, directly below meta tags | :heavy_check_mark: |
| Separate static JS-Datei | Own file static/admin/js/csrf.js, included via script src in layout | |

**User's choice:** Thymeleaf-Fragment im Layout (Empfohlen)
**Notes:** No extra JS file needed, available on every admin page automatically.

---

## Template-Validation Scope

### Validation on Save

| Option | Description | Selected |
|--------|-------------|----------|
| Save + Preview validieren | Both endpoints validate — prevents storing unsafe templates | :heavy_check_mark: |
| Nur Preview validieren | Status quo — save stores anything, validation only on preview/render | |

**User's choice:** Save + Preview validieren (Empfohlen)
**Notes:** Defense-in-depth approach.

### False Positive Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Kontext-sensitiv verfeinern | T() pattern only within ${...} or th: attributes | |
| Strikte Blocklist beibehalten | Keep current aggressive matching | |
| Claude entscheidet | Researcher/Planner finds best balance | :heavy_check_mark: |

**User's choice:** Claude entscheidet
**Notes:** Deferred to downstream agents for best security/usability balance.

---

## Fehler-Feedback

### Error Message Specificity

| Option | Description | Selected |
|--------|-------------|----------|
| Generisch | Uniform "Template contains unsafe expressions" message | :heavy_check_mark: |
| Detailliert fuer Admin | Names the blocked pattern in the user-facing message | |
| Claude entscheidet | Downstream decides | |

**User's choice:** Generisch (Empfohlen)
**Notes:** No attacker information leakage, details only in server logs.

### CSRF Error Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Standard 403 | Spring Security returns 403, handled by existing error flow | :heavy_check_mark: |
| Spezieller 403-Handler | JS detects 403 and shows "Session expired" message | |

**User's choice:** Standard 403 (Empfohlen)
**Notes:** csrfFetch() wrapper prevents the issue by design.

---

## Dev/Local Verhalten

### csrfFetch() in Dev/Local

| Option | Description | Selected |
|--------|-------------|----------|
| Graceful Fallback | csrfFetch() checks if meta tag exists, skips header if absent | :heavy_check_mark: |
| Meta-Tags immer rendern | Render meta tags in all profiles, even with CSRF disabled | |
| Profil-spezifische Meta-Tags | th:if condition to only render in prod/docker | |

**User's choice:** Graceful Fallback (Empfohlen)
**Notes:** Clean behavior in both environments without template logic.

### Template Validation Profile Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Alle Profile | Validation runs everywhere (dev, local, prod, docker) | :heavy_check_mark: |
| Nur prod/docker | No validation in dev/local for freer development | |

**User's choice:** Alle Profile (Empfohlen)
**Notes:** Consistent behavior, developers see unsafe templates immediately.

---

## Claude's Discretion

- SpEL T() false-positive handling strategy — balance security vs usability for the existing detection pattern

## Deferred Ideas

None — discussion stayed within phase scope
