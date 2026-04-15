# Phase 36: Audit Remediation - Context

**Gathered:** 2026-04-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Close traceability gaps and remove dead code identified by v1.5 milestone audit. Two specific items:
1. Dead JS code in race-results.html (unused `parts` array with inline style, lines 148-152)
2. REQUIREMENTS.md traceability table update for all v1.5 requirements

</domain>

<decisions>
## Implementation Decisions

### Dead Code Removal
- **D-01:** Remove the entire unused `parts` array block (lines 148-152 in race-results.html), not just line 151. The `parts` variable is declared, populated with inline-style HTML spans, but never used for output — the subsequent code (lines 153-167) builds totals via DOM manipulation with CSS classes (`results-total-value`, `results-total-value--home`, `results-total-value--away`).
- **D-02:** No replacement code needed — the DOM-based approach (lines 153-167) is already the active implementation with proper CSS classes per CONV-04.

### Traceability Updates
- **D-03:** Update all pending requirement entries in REQUIREMENTS.md traceability table to reflect their current verified state, not just CONV-04. This achieves complete audit closure for the v1.5 milestone.
- **D-04:** Mark CONV-04 as "Verified" after dead code removal is confirmed.

### Claude's Discretion
- Exact wording of traceability status updates (Verified/Compliant/etc.)
- Whether to add completion dates to the traceability table entries

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Code Files
- `src/main/resources/templates/admin/race-results.html` — Contains the dead JS code at lines 148-152
- `src/main/resources/static/css/admin.css` — Contains the CSS classes used by the active DOM approach

### Planning Files
- `.planning/REQUIREMENTS.md` — Contains CONV-04 and traceability table to update

</canonical_refs>

<code_context>
## Existing Code Insights

### Dead Code Location
- `race-results.html` lines 148-152: `var parts = []; for (var t in totals) { ... parts.push('<span style="...">') }` — completely unused, output is built via DOM in lines 153-167

### Active Implementation (Already Correct)
- Lines 153-167: DOM-based totals display using `createElement('span')`, `classList.add('results-total-value')`, and BEM modifiers `--home`/`--away`
- CSS classes `results-total-value`, `results-total-value--home`, `results-total-value--away` already defined in admin.css

### Integration Points
- No other files reference the dead code block
- REQUIREMENTS.md traceability table is standalone (no cross-references to update)

</code_context>

<specifics>
## Specific Ideas

No specific requirements — straightforward audit finding closure.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 36-audit-remediation*
*Context gathered: 2026-04-14*
