# Phase 18: Merge UI - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-07
**Phase:** 18-merge-ui
**Mode:** auto
**Areas discussed:** Merge workflow, Target driver selection, Preview mechanism, Confirmation & success handling

---

## Merge Workflow

| Option | Description | Selected |
|--------|-------------|----------|
| Separate merge page | `/admin/drivers/{id}/merge` — consistent with edit pattern, keeps detail page clean | auto |
| Modal on detail page | Uses existing `.modal-overlay` pattern from season-detail, keeps user on same page | |
| Inline expansion | Expand a section on driver-detail page — minimal navigation | |

**Auto-selected:** Separate merge page (recommended — consistent with existing page-per-action architecture)
**Notes:** Two-step server-side flow: select target → preview with counts → confirm button

---

## Target Driver Selection

| Option | Description | Selected |
|--------|-------------|----------|
| HTML select dropdown | All drivers sorted by PSN-ID, excluding source — simple, no JS needed | auto |
| Searchable autocomplete | Client-side search like drivers.html table — better for large lists | |
| Type-ahead AJAX | Server-side search endpoint — most scalable but adds complexity | |

**Auto-selected:** HTML select dropdown (recommended — driver count is manageable, consistent with form patterns)
**Notes:** Each option shows PSN-ID + nickname for identification

---

## Preview Mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| Two-step server-side | POST form → server returns preview page with counts → confirm | auto |
| AJAX preview | Select target → JS fetch → update preview section without page reload | |
| Combined select+preview | All preview data pre-loaded, JS shows relevant preview on selection | |

**Auto-selected:** Two-step server-side (recommended — consistent with SSR architecture, no AJAX complexity)
**Notes:** New `previewMerge()` service method counts without executing. `MergePreview` record separate from `MergeResult`.

---

## Confirmation & Success

| Option | Description | Selected |
|--------|-------------|----------|
| Confirm button + JS confirm() | POST form + browser confirm dialog — matches delete pattern | auto |
| Type-to-confirm | User types driver name to confirm — maximum safety | |
| Simple button | Just the POST button, no extra safety net | |

**Auto-selected:** Confirm button + JS confirm() (recommended — consistent with existing delete pattern)
**Notes:** Redirect to target driver detail page with flash message after success

---

## Claude's Discretion

- Preview page layout and styling details
- Exact wording of confirmation dialog and flash messages
- Internal implementation of `previewMerge()` method
- Whether to show driver names in preview or just counts

## Deferred Ideas

None — discussion stayed within phase scope
