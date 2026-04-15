# Phase 30: CSRF and Template Security - Research

**Researched:** 2026-04-13
**Domain:** Spring Security CSRF token handling (AJAX), Thymeleaf template injection validation
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Meta-tag approach: `<meta name="_csrf">` and `<meta name="_csrf_header">` in the layout fragment, populated via Thymeleaf `th:content="${_csrf.token}"` / `${_csrf.headerName}`
- **D-02:** Central `csrfFetch()` wrapper function as inline `<script>` in the layout fragment (directly below the meta tags), available on every admin page automatically
- **D-03:** All existing `fetch()` POST calls (`import.html` create-inline, `template-editors.html` preview) changed to use `csrfFetch()`
- **D-04:** Graceful fallback: `csrfFetch()` checks if meta tag exists — if yes (prod/docker) sets CSRF header, if no (dev/local where CSRF is disabled) proceeds as normal fetch without error
- **D-05:** `validateTemplateContent()` runs on **both** save and preview endpoints (defense-in-depth) — prevents storing unsafe templates even if preview is never called
- **D-06:** Save endpoint in `TemplateEditorController.save()` calls `templatePreviewService.validateTemplateContent(template)` before `saveTemplate()`
- **D-07:** Template validation runs in **all profiles** (dev, local, prod, docker) — no profile-specific bypass
- **D-08:** Generic error messages to the user: "Template contains unsafe expressions" — no details about which pattern was blocked
- **D-09:** Detailed info logged server-side via `log.warn()` with the specific blocked token/pattern
- **D-10:** CSRF rejection: standard Spring Security 403 response, no special JavaScript handler — csrfFetch() wrapper prevents the issue by design

### Claude's Discretion

- SpEL `T()` false-positive handling strategy: Claude decides the best balance between security and usability. Current detection matches any 'T' followed by '(' globally, which has false positives with CSS like `translateY()` or normal text like `T (Alpha)`.
- Whether to refine detection to context-sensitive (only within `${...}` or `th:*` attributes) or keep the strict global blocklist.

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SECU-03 | AJAX POST requests include CSRF token for prod/docker profile compatibility | D-01 through D-04: meta-tag + csrfFetch() pattern; SecurityConfig CSRF default; graceful fallback for dev/local |
| SECU-04 | Custom template rendering validates content for SpEL/OGNL injection before execution | D-05 through D-09: validateTemplateContent() called on save; false-positive analysis for T(); TemplateSecurityException already wired in preview |
</phase_requirements>

---

## Summary

Phase 30 addresses two distinct security concerns in the CTC Manager admin interface: (1) AJAX POST requests need CSRF tokens in prod/docker profiles, and (2) custom template saving must validate content for injection patterns before writing to disk.

The CSRF work is purely frontend — adding meta tags to `layout.html` and replacing two `fetch()` calls with a `csrfFetch()` wrapper. Spring Security 6's default CSRF configuration already works with the `X-CSRF-TOKEN` header pattern; no Java changes are needed for SECU-03 beyond the HTML/JS changes.

The template validation work (SECU-04) is a one-line controller change: `TemplateEditorController.save()` must call `templatePreviewService.validateTemplateContent(template)` before `saveTemplate()`. The validation logic and exception class already exist in `TemplatePreviewService`. The key discretion item is the SpEL `T()` detection false-positive problem — the current `containsSpringElTypeAccess()` scans the entire template string for any `T(` pattern, which falsely blocks CSS functions like `translateY()` and `rotateZ()`. Restricting detection to within `${...}` expression contexts eliminates this false positive class while preserving security.

**Primary recommendation:** Two-task phase: (1) CSRF meta tags + csrfFetch() wrapper in layout.html, update two fetch() call sites; (2) add validateTemplateContent() to save() + refine T() detection to be expression-context-aware + add save-validation tests.

---

## Standard Stack

### Core (already in project)

| Library | Version | Purpose | Source |
|---------|---------|---------|--------|
| Spring Security | via Spring Boot 4.0.5 | CSRF protection, SecurityFilterChain | [VERIFIED: pom.xml] |
| Thymeleaf Spring6 | via Spring Boot 4.0.5 | `${_csrf.token}` in template | [VERIFIED: pom.xml] |
| Spring Security Test | via pom.xml test scope | `@WithMockUser`, CSRF test support | [VERIFIED: pom.xml] |
| JUnit 5 + MockMvc | via Spring Boot test | Integration tests | [VERIFIED: pom.xml] |

No new dependencies needed. All required libraries are already in scope.

---

## Architecture Patterns

### CSRF Meta-Tag Pattern (Spring Security Standard)

**What:** Place CSRF token value and header name in `<meta>` tags in the HTML `<head>`. JavaScript reads these at runtime and includes the header on every AJAX POST.

**Spring Security model:** In Spring Security 6, `CsrfToken` is stored as a request attribute. Thymeleaf's Spring Security dialect exposes it as `_csrf` in templates. The `SecurityConfig` does not explicitly configure CSRF — the Spring default is CSRF enabled for all profiles that have `HttpSecurity` configured. `OpenSecurityConfig` (dev/local) explicitly calls `.csrf(csrf -> csrf.disable())`.

**Thymeleaf expression for meta tags:**
```html
<!-- Source: Spring Security docs — standard meta-tag CSRF pattern -->
<meta name="_csrf" th:content="${_csrf.token}">
<meta name="_csrf_header" th:content="${_csrf.headerName}">
```

**Important Spring Security 6 note:** In Spring Security 6, `CsrfToken` uses deferred loading by default. For the Thymeleaf `${_csrf.token}` expression to work, the `CsrfToken` must be available as a request attribute. Spring Security's `CsrfTokenRequestAttributeHandler` ensures this. The existing `SecurityConfig` does not override the CSRF token handling, so the Spring Boot 4.x default applies — which is `XorCsrfTokenRequestAttributeHandler`. This wraps the actual token value in a XOR-masked form. [VERIFIED: SecurityConfig.java — no custom CSRF configuration present]

**Default header name:** `X-CSRF-TOKEN` (Spring Security default). Available via `${_csrf.headerName}`. [ASSUMED — not verified against Spring Security 6.x docs, but consistent with all prior versions and project's existing SecurityConfig pattern]

### csrfFetch() Wrapper Pattern

**What:** A thin JavaScript wrapper around `fetch()` that reads CSRF values from meta tags and injects the appropriate header on POST requests. If the meta tags are absent (dev/local where CSRF is disabled), proceeds without error.

```javascript
// Pattern: inline script block in layout.html, after CSRF meta tags
function csrfFetch(url, options) {
    var csrfToken = document.querySelector('meta[name="_csrf"]');
    var csrfHeader = document.querySelector('meta[name="_csrf_header"]');
    if (csrfToken && csrfHeader) {
        options = options || {};
        options.headers = options.headers || {};
        options.headers[csrfHeader.content] = csrfToken.content;
    }
    return fetch(url, options);
}
```

**Placement:** Inline `<script>` tag directly below the CSRF meta tags, inside the `<head>` section of `layout.html`. Being in `<head>` ensures it is available before any page-specific scripts execute.

**Call site changes:**
- `import.html` L239: `fetch('/admin/matchdays/create-inline', {...})` → `csrfFetch('/admin/matchdays/create-inline', {...})`
- `template-editors.html` L747: `fetch('/admin/tools/template-editors/' + templateType + '/preview', {...})` → `csrfFetch('/admin/tools/template-editors/' + templateType + '/preview', {...})`

The options objects already contain the correct `method: 'POST'` and `headers`/`body` fields — only the function name changes.

### Template Validation on Save

**What:** `TemplateEditorController.save()` currently calls `saveTemplate(template)` directly without validation. Adding `templatePreviewService.validateTemplateContent(template)` before the save call enforces the same validation that `renderPreview()` already enforces on preview.

**Exception handling in save():** `TemplateSecurityException` is a `RuntimeException`. The save method currently only catches `IOException`. There are two valid options:

1. Let `TemplateSecurityException` propagate to `GlobalExceptionHandler` (which catches `Exception.class` and renders `admin/error`)
2. Catch it explicitly in `save()` and redirect with `errorMessage` flash attribute (consistent with how preview handles errors, and with the project's controller pattern of showing flash messages rather than error pages for save operations)

**Recommendation:** Option 2 — catch `TemplateSecurityException` in `save()` and set `errorMessage` flash attribute. This is consistent with the project's controller pattern (CLAUDE.md: "Controllers still catch BusinessRuleException for save/delete operations to show flash error messages instead of an error page"). The user stays on the template editor page with a clear error message rather than being redirected to a generic error page.

```java
// Pattern for save() in TemplateEditorController
try {
    templatePreviewService.validateTemplateContent(template);
    templateServices.get(beanName).saveTemplate(template);
    redirectAttributes.addFlashAttribute("successMessage",
        TEMPLATE_TYPE_TO_LABEL.get(templateType) + " template saved");
} catch (TemplatePreviewService.TemplateSecurityException e) {
    log.warn("Blocked unsafe template save for type {}: {}", templateType, e.getMessage());
    redirectAttributes.addFlashAttribute("errorMessage", "Template contains unsafe expressions");
} catch (IOException e) {
    redirectAttributes.addFlashAttribute("errorMessage", "Save failed: " + e.getMessage());
}
```

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| CSRF token in HTML | Custom token generation | Thymeleaf `${_csrf.token}` | Spring Security already manages token lifecycle, rotation, validation |
| CSRF validation | Custom request filter | Spring Security default CSRF filter | Spring handles token comparison, session binding, 403 response |
| Template security scanning | Regex-based full rewrite | Extend existing `validateTemplateContent()` | Validation logic already tested and in place; only call site change needed |

---

## SpEL T() False-Positive Analysis (Claude's Discretion)

### Problem

`containsSpringElTypeAccess()` scans the entire template string character-by-character for any `T` followed optionally by spaces and then `(`. This matches:

- Legitimate CSS functions: `translateY(10px)`, `translateX(...)`, `rotateZ(...)`, `scaleX(...)`, `matrix(...)` — common in graphic templates
- Normal text: `T (Alpha)`, `T (Home)` — team abbreviations followed by parenthetical text
- Variable names: `${homeTotal}` — the `T` in `Total` would match if followed by `(`... actually no, this scans for `T` at any position, so `Total(` would match but `${homeTotal}` followed by a space then `(` somewhere else would also trigger

### Recommended Fix: Context-Aware T() Detection

Restrict `T()` detection to **within expression contexts** only — i.e., only check for `T(` patterns inside `${...}` blocks or inside `th:*` attribute values.

**Implementation approach:** Scan for `${` blocks and check for SpEL type access only within those bounds. This mirrors how `containsOgnlStaticAccess()` already works (it scans `${...}` blocks for `@` patterns).

```java
// Refined: only flag T() inside ${...} expression contexts
private boolean containsSpringElTypeAccess(String content) {
    int idx = 0;
    while ((idx = content.indexOf("${", idx)) != -1) {
        int end = content.indexOf('}', idx + 2);
        if (end == -1) break;
        String expr = content.substring(idx + 2, end);
        // Check for T( pattern within this expression
        int tIdx = 0;
        while ((tIdx = expr.indexOf('T', tIdx)) != -1) {
            int next = tIdx + 1;
            while (next < expr.length() && expr.charAt(next) == ' ') {
                next++;
            }
            if (next < expr.length() && expr.charAt(next) == '(') {
                return true;
            }
            tIdx++;
        }
        idx = end + 1;
    }
    return false;
}
```

**Security coverage maintained:** SpEL injection via `T()` only works inside Thymeleaf expression contexts (`${...}` or `th:*` attribute values). A `T(` outside an expression is not evaluated by the Thymeleaf engine and poses no risk. Restricting detection to `${...}` contexts correctly targets the actual attack surface.

**Tradeoff:** `th:*` attribute values (e.g., `th:text="${T(java.lang.Runtime)}"`) are covered because the `T(...)` occurs inside the `${...}` block within the attribute. The only gap would be a Thymeleaf `*{T(...)}` selection variable expression — but this project's graphic templates use `${...}` exclusively.

**Existing test coverage:** The test `givenSpringElTypeAccess_whenValidate_thenRejectsTemplate` uses `${T(java.lang.Runtime).getRuntime()}` and `${T( java.lang.Runtime )}` — both will still be caught by the refined detection because the `T(` appears inside `${...}`.

**New tests needed:** A test verifying that `translateY(10px)` in a CSS block does NOT throw `TemplateSecurityException`, to document the false-positive fix as a regression guard.

---

## Common Pitfalls

### Pitfall 1: Spring Security 6 Deferred CsrfToken

**What goes wrong:** `${_csrf.token}` in Thymeleaf renders as empty or null, causing 403 on AJAX POST even after adding meta tags.

**Why it happens:** Spring Security 6 introduced `DeferredCsrfToken` — the token is not loaded until explicitly accessed. If the request attribute is not properly resolved before template rendering, `_csrf` may be a deferred wrapper that doesn't serialize correctly to a string.

**How to avoid:** Spring Boot 4.x auto-configures `CsrfTokenRequestAttributeHandler` (or its XOR variant) which makes the token available as a proper request attribute. No manual configuration needed in `SecurityConfig`. The `${_csrf.token}` Thymeleaf expression calls `.getToken()` on the resolved `CsrfToken` object, which triggers resolution. [ASSUMED — based on Spring Security 6.x behavior patterns]

**Warning signs:** Meta tag renders with empty `content=""` attribute. Test by checking the rendered HTML in the browser.

### Pitfall 2: CSRF Token Missing in MockMvc Tests for Save()

**What goes wrong:** Integration test for `save()` with security validation gets 403 (CSRF rejected) instead of testing the actual logic.

**Why it happens:** `TemplateEditorControllerTest` uses `@ActiveProfiles("dev")` where CSRF is disabled (`OpenSecurityConfig`). Tests pass without CSRF token even after the change. No pitfall for existing tests.

**For new save-validation tests:** Use the existing `dev` profile — no CSRF token needed. If testing prod-profile CSRF behavior, use `SecurityRequestPostProcessors.csrf()` from `spring-security-test` (`.with(csrf())`).

**Warning signs:** Tests fail with 403 in prod profile test without `.with(csrf())`.

### Pitfall 3: validateTemplateContent() Called Twice on Preview

**What goes wrong:** After adding validation to `save()`, the `preview()` path calls `renderPreview()` which calls `processTemplate()` which calls `validateTemplateContent()` — this is correct and intentional (defense-in-depth, D-05). Not a bug, but easy to misread as redundant.

**Why it happens:** `save()` calls `validateTemplateContent()` directly. `preview()` calls `renderPreview()` which internally calls `processTemplate()` which calls `validateTemplateContent()`. Two separate code paths, both validated.

**How to avoid:** Do not remove the `validateTemplateContent()` call from `processTemplate()` when adding the save-path call.

### Pitfall 4: csrfFetch() Overwrites Content-Type Header

**What goes wrong:** When `csrfFetch()` merges headers, it might accidentally clear the `Content-Type` header set by the caller.

**Why it happens:** If the implementation uses `options.headers = { [csrfHeader.content]: csrfToken.content }` (object replacement) instead of extending the existing headers object.

**How to avoid:** Use `Object.assign({}, options.headers, { [csrfHeader.content]: csrfToken.content })` or check before assignment. The `import.html` fetch sets `'Content-Type': 'application/json'` — this must survive the merge.

### Pitfall 5: layout.html Script in `<head>` vs End of `<body>`

**What goes wrong:** Inline script in `<head>` tries to reference DOM elements — fails if evaluated before body renders.

**Why it happens:** `csrfFetch()` is a function definition only (not a DOM query at load time). Queries happen when `csrfFetch()` is called, not when the script is parsed. Safe in `<head>`.

**How to avoid:** Keep `csrfFetch()` as a pure function definition with no top-level DOM access. The meta tag queries inside the function execute only when called (which is after DOM is ready, since existing fetch calls are already inside event handlers).

---

## Code Examples

### layout.html — CSRF Meta Tags + csrfFetch() Script

```html
<!-- Verified pattern: place inside <head>, after existing <link> elements -->
<meta name="_csrf" th:content="${_csrf.token}">
<meta name="_csrf_header" th:content="${_csrf.headerName}">
<script>
function csrfFetch(url, options) {
    var csrfMeta = document.querySelector('meta[name="_csrf"]');
    var headerMeta = document.querySelector('meta[name="_csrf_header"]');
    if (csrfMeta && headerMeta) {
        options = options || {};
        options.headers = Object.assign({}, options.headers, {
            [headerMeta.content]: csrfMeta.content
        });
    }
    return fetch(url, options);
}
</script>
```

### import.html — Update fetch() Call

```javascript
// Before (L239):
fetch('/admin/matchdays/create-inline', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({seasonId: seasonId, label: label})
})

// After:
csrfFetch('/admin/matchdays/create-inline', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({seasonId: seasonId, label: label})
})
```

### template-editors.html — Update fetch() Call

```javascript
// Before (L747):
var response = await fetch('/admin/tools/template-editors/' + templateType + '/preview', {
    method: 'POST',
    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
    body: 'template=' + encodeURIComponent(textarea.value)
});

// After:
var response = await csrfFetch('/admin/tools/template-editors/' + templateType + '/preview', {
    method: 'POST',
    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
    body: 'template=' + encodeURIComponent(textarea.value)
});
```

### TemplateEditorController.save() — Add Validation

```java
@PostMapping("/{templateType}/save")
public String save(@PathVariable String templateType,
                   @RequestParam String template,
                   RedirectAttributes redirectAttributes) {
    String beanName = TEMPLATE_TYPE_TO_BEAN.get(templateType);
    if (beanName == null) {
        redirectAttributes.addFlashAttribute("errorMessage", "Unknown template type");
        return "redirect:/admin/tools/template-editors";
    }
    try {
        templatePreviewService.validateTemplateContent(template);  // NEW
        templateServices.get(beanName).saveTemplate(template);
        redirectAttributes.addFlashAttribute("successMessage",
            TEMPLATE_TYPE_TO_LABEL.get(templateType) + " template saved");
    } catch (TemplatePreviewService.TemplateSecurityException e) {  // NEW
        log.warn("Blocked unsafe template save for type {}: {}", templateType, e.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", "Template contains unsafe expressions");
    } catch (IOException e) {
        redirectAttributes.addFlashAttribute("errorMessage", "Save failed: " + e.getMessage());
    }
    return "redirect:/admin/tools/template-editors?tab=" + templateType;
}
```

---

## Existing Code State (Verified)

### SecurityConfig.java [VERIFIED]

- `@Profile({"prod", "docker"})` — CSRF is **enabled by default** (Spring Security default when not explicitly disabled)
- No explicit `.csrf(...)` configuration call → Spring Security 6 default: CSRF enabled, using `XorCsrfTokenRequestAttributeHandler`
- No changes needed to SecurityConfig for SECU-03

### OpenSecurityConfig.java [VERIFIED]

- `@Profile({"dev", "local"})` — CSRF explicitly disabled: `.csrf(csrf -> csrf.disable())`
- `csrfFetch()` fallback (D-04) handles this: if no `<meta name="_csrf">` tag found (rendered as empty by Thymeleaf when CSRF is disabled), the fetch proceeds without CSRF header

**Wait — important detail:** When CSRF is disabled, Thymeleaf's `${_csrf.token}` may render as empty string or null, not be absent. The meta tag will still be present in the HTML but with `content=""`. The `csrfFetch()` must check that the token value is non-empty, not just that the meta element exists.

**Refined csrfFetch() guard:**
```javascript
function csrfFetch(url, options) {
    var csrfMeta = document.querySelector('meta[name="_csrf"]');
    var headerMeta = document.querySelector('meta[name="_csrf_header"]');
    if (csrfMeta && headerMeta && csrfMeta.content && headerMeta.content) {
        options = options || {};
        options.headers = Object.assign({}, options.headers, {
            [headerMeta.content]: csrfMeta.content
        });
    }
    return fetch(url, options);
}
```

**Alternative (simpler):** Use Thymeleaf's `th:if` to conditionally render the meta tags only when CSRF is active. Spring Security's `CsrfToken` will be null in the request when CSRF is disabled, and Thymeleaf's null-safety means `${_csrf?.token}` won't error. Or: only render meta tags when the `_csrf` object is non-null.

```html
<!-- Only emit meta tags when CSRF is enabled (i.e., _csrf is non-null) -->
<th:block th:if="${_csrf != null}">
    <meta name="_csrf" th:content="${_csrf.token}">
    <meta name="_csrf_header" th:content="${_csrf.headerName}">
</th:block>
```

With this approach, the meta tags are absent in dev/local HTML output. `csrfFetch()` can use the simpler null-check (element not found → no header added). [ASSUMED — need to verify that `_csrf` expression evaluates to null when CSRF is disabled in Spring Boot 4.x/Security 6.x, vs throwing an exception]

### TemplatePreviewService.validateTemplateContent() [VERIFIED]

Current state (package-private, not `public`):
- Blocks BLOCKED_TOKENS list (13 tokens: Runtime, ProcessBuilder, getClass(, etc.)
- Blocks SpEL `T()` via `containsSpringElTypeAccess()` — **full-string scan, false-positive risk**
- Blocks `__${` preprocessing
- Blocks OGNL `@class` via `containsOgnlStaticAccess()` — **already expression-context-aware (scans `${...}` blocks)**
- Throws `TemplateSecurityException` (inner class of TemplatePreviewService)
- Visibility: package-private (`void validateTemplateContent(...)`) — already callable from controller in same package? No — controller is in `org.ctc.admin.controller`, service is in `org.ctc.admin.service`. **Different packages.** The method must be made `public` for the controller to call it.

**Action required:** Change `void validateTemplateContent(String templateContent)` to `public void validateTemplateContent(String templateContent)`.

### TemplateEditorControllerTest.java [VERIFIED]

- No test for save() with unsafe template content → **gap**: new test needed
- Existing test `givenMaliciousTemplate_whenPreview_thenReturnsBadRequest` covers preview path
- Tests use `@ActiveProfiles("dev")` → CSRF disabled → no CSRF tokens needed in test POST requests

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring MockMvc + Spring Security Test |
| Config file | pom.xml (Surefire/Failsafe configuration) |
| Quick run command | `./mvnw test -pl . -Dtest=TemplateEditorControllerTest,TemplatePreviewServiceTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SECU-03 | csrfFetch() in layout adds CSRF header when meta tags present | Unit (JS — manual verification only) | N/A — JS function; covered by integration smoke | N/A |
| SECU-03 | csrfFetch() skips CSRF header when meta tags absent/empty | Unit (JS — manual verification only) | N/A | N/A |
| SECU-04 (save) | save() rejects malicious template with flash error | Integration | `./mvnw test -Dtest=TemplateEditorControllerTest#givenMaliciousTemplate_whenSave_thenRedirectsWithError` | ❌ Wave 0 |
| SECU-04 (save) | save() accepts safe template (unchanged happy path) | Integration | Existing test `givenTemplateContent_whenSaveTeamCardTemplate_thenRedirectsWithSuccess` | ✅ |
| SECU-04 (T() FP) | translateY() in CSS does NOT trigger security rejection | Unit | `./mvnw test -Dtest=TemplatePreviewServiceTest` | ❌ Wave 0 |
| SECU-04 (T() FP) | T( inside ${...} still triggers rejection | Unit | Existing `givenSpringElTypeAccess_whenValidate_thenRejectsTemplate` | ✅ |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=TemplateEditorControllerTest,TemplatePreviewServiceTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** `./mvnw verify` green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `TemplateEditorControllerTest` — add `givenMaliciousTemplate_whenSave_thenRedirectsWithError` test
- [ ] `TemplatePreviewServiceTest` — add `givenCssFunctionTranslateY_whenValidate_thenAcceptsTemplate` test (documents T() false-positive fix)

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V5 Input Validation | yes | `validateTemplateContent()` — existing + save-path extension |
| V3 Session Management | yes (indirect) | CSRF token tied to session — Spring Security manages |
| V4 Access Control | no | No new endpoints; existing auth model unchanged |
| V2 Authentication | no | No auth changes |
| V6 Cryptography | no | No cryptographic operations |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| CSRF on AJAX POST | Spoofing | CSRF token in `X-CSRF-TOKEN` header, validated by Spring Security |
| SpEL injection via Thymeleaf | Tampering/Elevation | validateTemplateContent() blocks T() and reflection tokens |
| OGNL static access | Tampering/Elevation | containsOgnlStaticAccess() blocks @class patterns in ${...} |
| Thymeleaf preprocessing injection (`__${`) | Tampering | Blocked by validateTemplateContent() |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `${_csrf}` evaluates to null (not throws) when CSRF is disabled in Spring Boot 4.x | Existing Code State | If it throws, `th:if="${_csrf != null}"` approach fails; need try-catch or `${_csrf?.token}` null-safe navigation |
| A2 | Default CSRF header name is `X-CSRF-TOKEN` in Spring Security 6.x | Architecture Patterns | If changed, `${_csrf.headerName}` still returns correct value — low risk since we use `${_csrf.headerName}` not a hardcoded string |
| A3 | Spring Boot 4.x default CSRF configuration uses `XorCsrfTokenRequestAttributeHandler` | Architecture Patterns | If different, `${_csrf.token}` may not render correctly in Thymeleaf; easily testable |

---

## Open Questions

1. **Does `${_csrf}` return null or throw when CSRF is disabled?**
   - What we know: `OpenSecurityConfig` disables CSRF; Thymeleaf template renders `${_csrf.token}`
   - What's unclear: Whether Spring Security 6 + Spring Boot 4.x returns null or throws when accessing `_csrf` with CSRF disabled
   - Recommendation: Use `th:if="${_csrf != null}"` guard around meta tags. If `_csrf` throws rather than being null, use Thymeleaf null-safe: `th:if="${_csrf != null and _csrf.token != null}"`. The simplest test: render layout in dev profile and check for meta tags.

2. **Should validateTemplateContent() be made public or kept package-private?**
   - What we know: Method is currently package-private; controller is in a different package (`admin.controller` vs `admin.service`)
   - What's unclear: Whether there's a design preference to keep it internal
   - Recommendation: Make it `public` — it's already tested directly (unit test calls it directly). Making it public is the minimal change needed for the controller call.

---

## Environment Availability

Step 2.6: SKIPPED — no new external dependencies. All required tools (Java 25, Maven, Spring Boot 4.0.5, Spring Security) are already in use by the project.

---

## Sources

### Primary (HIGH confidence)
- `src/main/java/org/ctc/admin/SecurityConfig.java` — CSRF default behavior verified
- `src/main/java/org/ctc/admin/OpenSecurityConfig.java` — CSRF disabled in dev/local confirmed
- `src/main/java/org/ctc/admin/service/TemplatePreviewService.java` — validateTemplateContent() implementation verified
- `src/main/java/org/ctc/admin/controller/TemplateEditorController.java` — save() and preview() endpoint verified
- `src/main/resources/templates/admin/layout.html` — insertion point for CSRF meta tags verified
- `src/main/resources/templates/admin/import.html` L239 — fetch() call site verified
- `src/main/resources/templates/admin/template-editors.html` L747 — fetch() call site verified
- `src/test/java/org/ctc/admin/service/TemplatePreviewServiceTest.java` — existing test coverage verified
- `src/test/java/org/ctc/admin/controller/TemplateEditorControllerTest.java` — existing test coverage verified
- `pom.xml` — Spring Boot 4.0.5, Java 25, all dependencies verified

### Secondary (MEDIUM confidence)
- Spring Security 6 CSRF meta-tag documentation pattern — standard well-established approach

### Tertiary (LOW confidence — see Assumptions Log)
- Spring Boot 4.x `_csrf` null behavior when CSRF is disabled

---

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — all libraries verified in pom.xml
- Architecture (CSRF meta-tag): HIGH — verified code state, standard Spring Security pattern
- Architecture (save validation): HIGH — verified service and controller code
- SpEL T() false-positive fix: HIGH — code analyzed, fix approach confirmed
- Thymeleaf null-csrf behavior: LOW — not verified against current Spring Boot 4.x docs (A1)

**Research date:** 2026-04-13
**Valid until:** 2026-05-13 (stable stack, Spring Security CSRF patterns are long-lived)
