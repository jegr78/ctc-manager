# Phase 49: E2E Site Validation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.

**Date:** 2026-04-17
**Phase:** 49-e2e-site-validation
**Areas discussed:** Test Class Structure, Link Validation, Feature Validation
**Mode:** --auto

---

## Test Class Structure

| Option | Description | Selected |
|--------|-------------|----------|
| New SiteGeneratorE2ETest class | Separate cross-cutting validation, clean separation | ✓ |
| Extend SiteGeneratorServiceTest | Already 990+ tests, would bloat further | |
| Multiple small test classes | Over-engineered for 7 tests | |

**User's choice:** [auto] New class (recommended)

---

## Link Validation Approach

| Option | Description | Selected |
|--------|-------------|----------|
| Files.walk + JSoup + path resolution | Standard Java, comprehensive, no external deps | ✓ |
| Playwright browser-based crawl | Heavy, overkill for static file validation | |
| Simple file existence checks | Misses href resolution logic | |

**User's choice:** [auto] Files.walk + JSoup (recommended)

---

## Feature Validation Scope

| Option | Description | Selected |
|--------|-------------|----------|
| All 6 E2E requirements | Complete coverage of all new features | ✓ |
| Links + Nav only | Partial, misses new features | |
| Links only | Minimal, insufficient | |

**User's choice:** [auto] All 6 requirements (recommended)

## Claude's Discretion

- Sample size for footer checks, helper extraction, assertion grouping

## Deferred Ideas

None
