# Phase 11: Template Quality - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-06
**Phase:** 11-template-quality
**Mode:** auto
**Areas discussed:** Dynamic Styles, CSS Class Strategy, Template Priority, Visual Verification

---

## Dynamic Styles Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Keep dynamic th:style for DB values, replace static styles with CSS classes | Recommended — data-driven colors can't be static CSS | ✓ |
| Replace all inline styles including dynamic ones with data-attribute + CSS patterns | More complex, marginal benefit for this codebase | |
| Keep all inline styles as-is, only add semantic classes alongside | Doesn't address the requirement | |

**User's choice:** [auto] Keep dynamic th:style for data-driven values, replace static inline styles with CSS classes (recommended default)
**Notes:** Dynamic th:style is used for team color swatches and winner/loser conditional styling in race-detail.html

---

## CSS Class Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Semantic component classes (.score-card, .modal-overlay, etc.) | Matches existing admin.css pattern (.badge, .empty-state) | ✓ |
| Tailwind-style utility classes (.flex, .gap-2, .p-4, etc.) | Would require many new classes, different paradigm from existing CSS | |
| Mix of both — semantic for components, utility for layout | Reasonable but adds inconsistency | |

**User's choice:** [auto] Semantic component classes in admin.css (recommended default)
**Notes:** admin.css already uses semantic naming (.badge-active, .empty-state, .chip) — continuing this pattern maintains consistency

---

## Template Priority

| Option | Description | Selected |
|--------|-------------|----------|
| season-detail + race-detail first, then remaining, exclude render templates | Matches QUAL-01 priority, keep template-editors.html separate | ✓ |
| All templates at once | Too large for reviewable changes | |
| Only season-detail + race-detail (minimum scope) | Doesn't fully address QUAL-01 "all remaining admin templates" | |

**User's choice:** [auto] P1: season-detail + race-detail, P2: remaining templates, template-editors.html separate plan (recommended default)
**Notes:** template-editors.html has 181 inline styles — warrants its own plan for manageability

---

## Visual Verification

| Option | Description | Selected |
|--------|-------------|----------|
| Playwright screenshots before/after with dev+demo profile | Comprehensive verification, matches success criteria SC-3 | ✓ |
| Manual visual inspection only | Error-prone for 40+ templates | |
| CSS-only diff review without visual check | Misses rendering edge cases | |

**User's choice:** [auto] Playwright screenshots before/after (recommended default)
**Notes:** Success criteria explicitly requires "Visual appearance of all admin pages is unchanged (verified via Playwright screenshots)"

---

## Claude's Discretion

- Exact naming of new CSS classes
- Grouping and ordering within admin.css
- Commit granularity per template

## Deferred Ideas

None — analysis stayed within phase scope.
