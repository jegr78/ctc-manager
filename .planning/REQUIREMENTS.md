# Requirements: CTC Manager

**Defined:** 2026-04-13
**Core Value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## v1.5 Requirements

Requirements for Code Review Fixes milestone. Each maps to roadmap phases.

### Security

- [ ] **SECU-01**: Admin can create/edit matchdays via MatchdayForm DTO instead of direct JPA entity binding
- [ ] **SECU-02**: File download validates resolved path stays within upload directory (path traversal defense)
- [ ] **SECU-03**: AJAX POST requests include CSRF token for prod/docker profile compatibility
- [ ] **SECU-04**: Custom template rendering validates content for SpEL/OGNL injection before execution
- [ ] **SECU-05**: Content-Disposition header uses sanitized filename to prevent header injection

### Data Integrity

- [ ] **DATA-01**: Multi-race CSV import runs within a single transaction (all-or-nothing)
- [ ] **DATA-02**: File download handles null content type from probeContentType gracefully
- [ ] **DATA-03**: Race services handle null home/away teams without NPE (bye matches, unlinked races)
- [ ] **DATA-04**: Driver-team fallback check filters by current season to prevent cross-season misattribution

### Architecture

- [ ] **ARCH-01**: Domain services do not import from admin service layer (layering fix or relocation)
- [ ] **ARCH-02**: Domain services use domain exceptions instead of HTTP-specific ResponseStatusException
- [ ] **ARCH-03**: Controller methods delegate data transformation and business logic to service layer
- [ ] **ARCH-04**: Site generator uses RaceLineup as source of truth for driver-team assignment

### Convention

- [ ] **CONV-01**: PlayoffController.save() validates form input with @Valid and BindingResult
- [ ] **CONV-02**: SeasonTeam and RaceSettings entities exclude lazy associations from toString
- [ ] **CONV-03**: All UI text and code comments use English (no German remnants)
- [ ] **CONV-04**: Race results page uses CSS classes from admin.css instead of inline styles
- [ ] **CONV-05**: Business rule violations log at warn level, not error level

## Future Requirements

(None deferred for this milestone)

## Out of Scope

| Feature | Reason |
|---------|--------|
| CSRF disabled globally | Only fixing missing tokens on AJAX requests, not changing overall CSRF strategy |
| Full SpEL sandbox | Validation-based approach sufficient for admin-only templates |
| Refactor all controller logic | Only fixing identified violations, not comprehensive controller audit |
| Database schema changes | No Flyway migrations needed for these fixes |
| New test infrastructure | Fixes verified by existing test suite + targeted new tests |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| SECU-01 | — | Pending |
| SECU-02 | — | Pending |
| SECU-03 | — | Pending |
| SECU-04 | — | Pending |
| SECU-05 | — | Pending |
| DATA-01 | — | Pending |
| DATA-02 | — | Pending |
| DATA-03 | — | Pending |
| DATA-04 | — | Pending |
| ARCH-01 | — | Pending |
| ARCH-02 | — | Pending |
| ARCH-03 | — | Pending |
| ARCH-04 | — | Pending |
| CONV-01 | — | Pending |
| CONV-02 | — | Pending |
| CONV-03 | — | Pending |
| CONV-04 | — | Pending |
| CONV-05 | — | Pending |

**Coverage:**

- v1.5 requirements: 18 total
- Mapped to phases: 0
- Unmapped: 18

---
*Requirements defined: 2026-04-13*
*Last updated: 2026-04-13 after initial definition*
