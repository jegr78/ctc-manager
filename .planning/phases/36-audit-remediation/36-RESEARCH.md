# Phase 36: Audit Remediation - Research

**Researched:** 2026-04-14
**Domain:** Dead code removal (JavaScript), REQUIREMENTS.md traceability documentation
**Confidence:** HIGH

## Summary

Phase 36 has two independent, non-overlapping tasks. First, remove 5 lines of dead JavaScript from `race-results.html` (lines 148-152: a `var parts = []` block that builds HTML strings with inline styles that are immediately overwritten and never rendered). Second, update the REQUIREMENTS.md traceability table to reflect the actual completion state of all 18 v1.5 requirements across phases 28-35.

Both tasks are documentation/cleanup operations requiring no new logic, no new tests, and no architectural changes. The dead code was identified during v1.5 milestone audit as a residue left by Phase 34 Plan 02 — that plan extracted inline styles and refactored JS `.style.*` calls to `classList.add()` but left behind the now-unreachable `parts.push()` string-building loop. The REQUIREMENTS.md traceability table was last updated on 2026-04-14 with "Pending" status for 11 requirements that have since been implemented in phases 28-34.

**Primary recommendation:** Remove lines 148-152 from race-results.html (the second `var parts = []` block only — the first parts block at lines 137-141 is active code). Then update REQUIREMENTS.md traceability to mark all verified requirements with "Verified" and assign correct phase numbers.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Remove the entire unused `parts` array block (lines 148-152 in race-results.html), not just line 151. The `parts` variable is declared, populated with inline-style HTML spans, but never used for output — the subsequent code (lines 153-167) builds totals via DOM manipulation with CSS classes (`results-total-value`, `results-total-value--home`, `results-total-value--away`).
- **D-02:** No replacement code needed — the DOM-based approach (lines 153-167) is already the active implementation with proper CSS classes per CONV-04.
- **D-03:** Update all pending requirement entries in REQUIREMENTS.md traceability table to reflect their current verified state, not just CONV-04. This achieves complete audit closure for the v1.5 milestone.
- **D-04:** Mark CONV-04 as "Verified" after dead code removal is confirmed.

### Claude's Discretion
- Exact wording of traceability status updates (Verified/Compliant/etc.)
- Whether to add completion dates to the traceability table entries

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CONV-04 | Race results page uses CSS classes from admin.css instead of inline styles | Dead code at lines 148-152 is the last inline style remnant; CSS classes already in admin.css (lines 1934-1939); removing the block fully closes CONV-04 |
</phase_requirements>

## Standard Stack

This phase requires no library additions. The work is:
- HTML/JavaScript file edit (Thymeleaf template)
- Markdown file edit (REQUIREMENTS.md)

No installation, no new dependencies.

## Architecture Patterns

### Dead Code Location (VERIFIED: direct file inspection)

The file `src/main/resources/templates/admin/race-results.html` contains two separate `var parts` declarations inside the `calcPoints()` function:

**Block 1 — ACTIVE (lines 137-141): Do NOT remove**
```javascript
var parts = [];
if (rp > 0) parts.push(rp + 'R');
if (qp > 0) parts.push(qp + 'Q');
if (fp > 0) parts.push(fp + 'FL');
breakdown.textContent = parts.length > 0 ? parts.join('+') : '';
```
This block builds the per-driver breakdown label (e.g., `10R+5Q`) and is actively used.

**Block 2 — DEAD (lines 148-152): Remove entirely**
```javascript
var parts = [];
for (var t in totals) {
    var isHome = (t === homeTeam);
    parts.push('<span style="font-weight:700; color:' + (isHome ? 'var(--accent)' : 'var(--white)') + ';">' + totals[t] + '</span>');
}
```
This block builds HTML strings into `parts` and then `parts` is never used. The very next line (`totalsEl.textContent = ''`) begins the DOM-based approach that supersedes it. The `parts` array falls out of scope unused.

**Active implementation (lines 153-168): Preserve**
```javascript
totalsEl.textContent = '';
var first = true;
for (var t in totals) {
    if (!first) {
        var sep = document.createElement('span');
        sep.textContent = ' : ';
        sep.classList.add('results-total-sep');
        totalsEl.appendChild(sep);
    }
    var span = document.createElement('span');
    span.textContent = totals[t];
    span.classList.add('results-total-value');
    span.classList.add((t === homeTeam) ? 'results-total-value--home' : 'results-total-value--away');
    totalsEl.appendChild(span);
    first = false;
}
```
This is the correct implementation using CSS classes from admin.css. No changes needed here.

### CSS Classes Already in Place (VERIFIED: direct file inspection)

File: `src/main/resources/static/admin/css/admin.css` lines 1934-1939:
```css
.results-totals-row { border-top: 2px solid var(--border); }
.results-totals-label { text-align: right; font-weight: 600; color: var(--white); }
.results-total-value { font-weight: 700; font-size: 16px; }
.results-total-value--home { color: var(--accent); }
.results-total-value--away { color: var(--white); }
.results-total-sep { color: var(--text-dim); }
```
All CSS classes referenced by the active DOM code exist. No admin.css changes needed.

### Traceability Update (VERIFIED: phase SUMMARY.md files)

The REQUIREMENTS.md traceability table was written with "Pending" for 11 requirements. Based on phase SUMMARY.md files, all 11 have been implemented:

| Requirement | Phase That Implemented It | Evidence |
|-------------|--------------------------|---------|
| SECU-03 | Phase 30 (Plan 01) | 30-01-SUMMARY.md: `requirements: [SECU-03]`, csrfFetch wrapper + CSRF meta tags |
| SECU-04 | Phase 30 (Plan 02) | 30-02-SUMMARY.md: template-save-validation, context-aware SpEL detection |
| DATA-01 | Phase 31 (Plan 01) | 31-01-SUMMARY.md: `provides: [atomic-csv-import]`, two-phase validate-then-import |
| DATA-03 | Phase 31 (Plan 02) + Phase 35 | 31-02-SUMMARY.md: null-safe bye race handling; 35-01-SUMMARY.md: null-safe toRaceView |
| DATA-04 | Phase 31 (Plan 02) | 31-02-SUMMARY.md: `provides: [season-filtered-driver-team]`, season-scoped SeasonDriver fallback |
| ARCH-01 | Phase 32 (Plan 01) | 32-01-SUMMARY.md: `requirements-completed: [ARCH-01]`, RaceService decoupled |
| ARCH-02 | Phase 32 (Plan 02) | 32-02-SUMMARY.md: `requirements: [ARCH-02]`, domain exceptions replacing ResponseStatusException |
| ARCH-03 | Phase 33 (Plan 01) | 33-01-SUMMARY.md: `requirements-completed: [ARCH-03]` |
| ARCH-04 | Phase 33 (Plan 02) | 33-02-SUMMARY.md: `requirements-completed: [ARCH-04]`, RaceLineup-first in SiteGeneratorService |
| CONV-01 | Phase 34 (Plan 01) | 34-01-SUMMARY.md: `provides: [CONV-01]`, PlayoffController @Valid + BindingResult |
| CONV-04 | Phase 34 (Plan 02) + Phase 36 | 34-02-SUMMARY.md: `provides: [CONV-04]`, CSS class extraction; Phase 36 removes remaining dead code |

Note on DATA-03: Phase 31 Plan 02 addressed null safety in race form data services; Phase 35 Plan 01 addressed null safety in SiteGeneratorService.toRaceView(). Both contribute; the phase column should reflect both.

### Anti-Patterns to Avoid

- **Do not remove the first `parts` block (lines 137-141):** It is active code producing the per-driver breakdown labels ("10R+5Q"). Only the second `var parts` block (lines 148-152) is dead.
- **Do not change the active DOM loop (lines 153-168):** It is already correct with CSS classes.
- **Do not change admin.css:** All required CSS classes are already present.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Traceability status labels | Custom status taxonomy | Use existing: "Verified", "Compliant (no change needed)", "Pending" — already established in REQUIREMENTS.md |

## Runtime State Inventory

Step 2.5: SKIPPED — This is not a rename/refactor/migration phase. The work is removal of dead code and documentation update with no runtime state implications.

## Environment Availability

Step 2.6: No external dependencies beyond the standard project stack (Maven, JDK 25). The dead code removal is a pure text edit. The traceability update is a Markdown edit.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Maven (`./mvnw`) | Test run after dead code removal | Yes (project-standard) | Wrapper in repo | — |
| Java 25 | Maven build | Yes (project-standard) | Temurin | — |

## Common Pitfalls

### Pitfall 1: Removing the Wrong `parts` Block
**What goes wrong:** Removing lines 137-141 (the first `parts` block) instead of lines 148-152 (the second). This breaks the per-driver breakdown label display.
**Why it happens:** There are two `var parts = []` declarations in the same function scope. The dead one is the second (inside the "Team totals" comment section).
**How to avoid:** Remove only lines 148-152 — the block starting with `// Team totals` comment at line 146, variable declaration at 148, for-loop at 149-152.
**Warning signs:** If after the edit, `calcPoints()` no longer shows "10R+5Q" breakdown labels under driver points, the wrong block was removed.

### Pitfall 2: Forgetting the Blank Line After Removal
**What goes wrong:** After removing lines 148-152, the code jumps abruptly from `totals[team] = ...` to `totalsEl.textContent = ''` with no visual separation.
**How to avoid:** Leave or add a blank line between `totals[team] = (totals[team] || 0) + total;` (end of row loop) and `totalsEl.textContent = ''` (start of totals display). The `// Team totals` comment at line 146 should be preserved.

### Pitfall 3: Marking Requirements as Pending That Are Actually Compliant
**What goes wrong:** Confusing "Compliant (no change needed)" requirements (CONV-02, CONV-03, CONV-05) with "Verified" requirements. Compliant means the requirement was already satisfied before any work; Verified means implementation was done in a phase.
**How to avoid:** The three compliant requirements stay as "Compliant (no change needed)". All others that had implementation done become "Verified".

## Code Examples

### Dead Code Block to Remove (VERIFIED: direct file inspection)

Lines 148-152 in `src/main/resources/templates/admin/race-results.html`:

```javascript
// REMOVE THESE 5 LINES:
            var parts = [];
            for (var t in totals) {
                var isHome = (t === homeTeam);
                parts.push('<span style="font-weight:700; color:' + (isHome ? 'var(--accent)' : 'var(--white)') + ';">' + totals[t] + '</span>');
            }
```

After removal, line 153 (`totalsEl.textContent = '';`) follows directly after line 147 (`var totalsEl = document.getElementById('teamTotals');`), with the `// Team totals` comment remaining at line 146.

### Expected REQUIREMENTS.md Traceability Table After Update

```markdown
| Requirement | Phase | Status |
|-------------|-------|--------|
| SECU-01 | Phase 29 | Verified |
| SECU-02 | Phase 28 | Verified |
| SECU-03 | Phase 30 | Verified |
| SECU-04 | Phase 30 | Verified |
| SECU-05 | Phase 28 | Verified |
| DATA-01 | Phase 31 | Verified |
| DATA-02 | Phase 28 | Verified |
| DATA-03 | Phase 31, Phase 35 | Verified |
| DATA-04 | Phase 31 | Verified |
| ARCH-01 | Phase 32 | Verified |
| ARCH-02 | Phase 32 | Verified |
| ARCH-03 | Phase 33 | Verified |
| ARCH-04 | Phase 33 | Verified |
| CONV-01 | Phase 34 | Verified |
| CONV-02 | Phase 34 | Compliant (no change needed) |
| CONV-03 | Phase 34 | Compliant (no change needed) |
| CONV-04 | Phase 34, Phase 36 | Verified |
| CONV-05 | Phase 34 | Compliant (no change needed) |
```

Coverage summary after update:
- v1.5 requirements: 18 total
- Verified: 15 (all except the 3 compliant ones)
- Compliant (no change needed): 3 (CONV-02, CONV-03, CONV-05)
- Pending verification: 0
- Unmapped: 0

## Validation Architecture

nyquist_validation is enabled (present in config.json and not false).

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Surefire (unit/integration), Failsafe -Pe2e (E2E) |
| Config file | pom.xml (Surefire/Failsafe plugins) |
| Quick run command | `./mvnw verify` |
| Full suite command | `./mvnw verify -Pe2e` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | Notes |
|--------|----------|-----------|-------------------|-------|
| CONV-04 | Dead JS removed, no inline style in teamTotals rendering | Manual visual / grep | `grep -n 'style=' src/main/resources/templates/admin/race-results.html` | No unit test needed — dead code deletion, verified by grep showing 0 matches (excluding `<style>` block) |
| Traceability | REQUIREMENTS.md reflects verified state | Documentation review | N/A — Markdown review | No automated test; verified by reading file |

### Sampling Rate
- **Per task commit:** `./mvnw verify` (906 tests, coverage check)
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green + grep confirms 0 inline styles on elements (excluding `<style>` tag) before closing

### Wave 0 Gaps
None — no new test files needed. The dead code removal is verified by grep. Existing 906-test suite guards against regressions.

## Security Domain

This phase introduces no new endpoints, authentication paths, or data handling. Security domain: NOT APPLICABLE.

The inline style string `var(--accent)` and `var(--white)` in the dead code block are CSS custom property references (not user data), so removal has no XSS implications.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | All 11 "Pending" requirements in REQUIREMENTS.md were implemented in phases 28-35 | Architecture Patterns / Traceability Update | If a requirement was not actually implemented, marking it "Verified" would be incorrect. Mitigated: each claim is backed by specific SUMMARY.md file evidence. |

**All other claims verified by direct file inspection of race-results.html (lines 137-168), admin.css (lines 1934-1939), and phase SUMMARY.md files.**

## Open Questions

None — scope is fully determined by CONTEXT.md decisions D-01 through D-04. No ambiguity remains.

## Sources

### Primary (HIGH confidence)
- `src/main/resources/templates/admin/race-results.html` — Direct inspection, lines 137-168 confirmed
- `src/main/resources/static/admin/css/admin.css` — Direct inspection, lines 1934-1939 confirmed
- `.planning/phases/34-convention-fixes/34-02-SUMMARY.md` — Confirms Phase 34 CONV-04 work scope
- `.planning/phases/30-35/*/SUMMARY.md` — Confirms all 11 pending requirements implemented
- `.planning/REQUIREMENTS.md` — Current state of traceability table

### Secondary (MEDIUM confidence)
- `.planning/phases/36-audit-remediation/36-CONTEXT.md` — User decisions D-01 through D-04

## Metadata

**Confidence breakdown:**
- Dead code identification: HIGH — verified by direct line-by-line inspection of race-results.html
- CSS class availability: HIGH — verified by direct inspection of admin.css
- Traceability update content: HIGH — each requirement backed by specific SUMMARY.md evidence
- No-test-needed assessment: HIGH — dead code removal is a deletion, not new logic

**Research date:** 2026-04-14
**Valid until:** 2026-05-14 (stable domain — file locations and content won't change)
