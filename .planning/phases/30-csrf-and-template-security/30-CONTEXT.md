# Phase 30: CSRF and Template Security - Context

**Gathered:** 2026-04-13
**Status:** Ready for planning

<domain>
## Phase Boundary

AJAX POST requests carry CSRF tokens in prod/docker profiles, and custom template rendering rejects dangerous content on both save and preview. Only the existing AJAX POST endpoints and TemplatePreviewService validation are in scope — no new endpoints, no auth changes, no new security features.

</domain>

<decisions>
## Implementation Decisions

### CSRF Token Delivery
- **D-01:** Meta-tag approach: `<meta name="_csrf">` and `<meta name="_csrf_header">` in the layout fragment, populated via Thymeleaf `th:content="${_csrf.token}"` / `${_csrf.headerName}`
- **D-02:** Central `csrfFetch()` wrapper function as inline `<script>` in the layout fragment (directly below the meta tags), available on every admin page automatically
- **D-03:** All existing `fetch()` POST calls (`import.html` create-inline, `template-editors.html` preview) changed to use `csrfFetch()`
- **D-04:** Graceful fallback: `csrfFetch()` checks if meta tag exists — if yes (prod/docker) sets CSRF header, if no (dev/local where CSRF is disabled) proceeds as normal fetch without error

### Template Validation Scope
- **D-05:** `validateTemplateContent()` runs on **both** save and preview endpoints (defense-in-depth) — prevents storing unsafe templates even if preview is never called
- **D-06:** Save endpoint in `TemplateEditorController.save()` calls `templatePreviewService.validateTemplateContent(template)` before `saveTemplate()`
- **D-07:** Template validation runs in **all profiles** (dev, local, prod, docker) — no profile-specific bypass

### Error Feedback
- **D-08:** Generic error messages to the user: "Template contains unsafe expressions" — no details about which pattern was blocked
- **D-09:** Detailed info logged server-side via `log.warn()` with the specific blocked token/pattern
- **D-10:** CSRF rejection: standard Spring Security 403 response, no special JavaScript handler — csrfFetch() wrapper prevents the issue by design

### Claude's Discretion
- SpEL `T()` false-positive handling strategy: Claude decides the best balance between security and usability (current detection matches any 'T' followed by '(' globally, which has false positives with CSS like `translateY()` or normal text like `T (Alpha)`)
- Whether to refine detection to context-sensitive (only within `${...}` or `th:*` attributes) or keep the strict global blocklist

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Security Configuration
- `src/main/java/org/ctc/admin/SecurityConfig.java` — Prod/docker SecurityFilterChain (CSRF enabled by Spring default, needs explicit token handling config)
- `src/main/java/org/ctc/admin/OpenSecurityConfig.java` — Dev/local SecurityFilterChain (CSRF explicitly disabled)

### Template Security
- `src/main/java/org/ctc/admin/service/TemplatePreviewService.java` — Existing `validateTemplateContent()` with BLOCKED_TOKENS list, SpEL T() detection, OGNL @class detection, `__${` pattern detection
- `src/main/java/org/ctc/admin/controller/TemplateEditorController.java` — Save/reset/preview endpoints, preview already catches TemplateSecurityException

### AJAX POST Endpoints (need CSRF tokens)
- `src/main/resources/templates/admin/import.html` L239 — `fetch('/admin/matchdays/create-inline', {method: 'POST', ...})` without CSRF
- `src/main/resources/templates/admin/template-editors.html` L747 — `fetch('/admin/tools/template-editors/' + templateType + '/preview', {method: 'POST', ...})` without CSRF

### Layout (Meta-tag insertion point)
- `src/main/resources/templates/admin/layout.html` — Admin layout fragment where CSRF meta tags and csrfFetch() script will be added

### Conventions
- `.planning/codebase/CONVENTIONS.md` — Naming patterns, controller patterns
- `CLAUDE.md` — Architectural principles, profile constraints

### Requirements
- `.planning/REQUIREMENTS.md` — SECU-03 (CSRF on AJAX POSTs), SECU-04 (SpEL/OGNL validation in template rendering)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `TemplatePreviewService.validateTemplateContent()` — Already validates against SpEL/OGNL/blocked tokens, needs to be called from save endpoint too
- `TemplatePreviewService.TemplateSecurityException` — Already exists for security rejection signaling
- `TemplateEditorController.preview()` — Already catches TemplateSecurityException and returns 400 with generic message

### Established Patterns
- Two-profile SecurityFilterChain: `@Profile({"prod", "docker"})` SecurityConfig vs `@Profile({"dev", "local"})` OpenSecurityConfig
- Flash attributes for form submission feedback: `"successMessage"` / `"errorMessage"`
- Controller delegates to service for validation (consistent with thin controller principle)

### Integration Points
- `layout.html` `<head>` section — insert CSRF meta tags and csrfFetch() inline script
- `import.html` JS — change `fetch()` to `csrfFetch()` for create-inline POST
- `template-editors.html` JS — change `fetch()` to `csrfFetch()` for preview POST
- `TemplateEditorController.save()` — add validateTemplateContent() call before saveTemplate()
- `SecurityConfig` — may need explicit CSRF token handler configuration for Spring Security 6

</code_context>

<specifics>
## Specific Ideas

No specific requirements — follows standard Spring Security CSRF meta-tag pattern and extends existing template validation to the save endpoint.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 30-csrf-and-template-security*
*Context gathered: 2026-04-13*
