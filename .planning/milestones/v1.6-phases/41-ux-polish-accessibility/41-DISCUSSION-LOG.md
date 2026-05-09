# Phase 41: UX Polish & Accessibility - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-16
**Phase:** 41-ux-polish-accessibility
**Areas discussed:** Winner-Highlight, Scroll-Indikator, Footer-Design, Hover & Transitions

---

## Winner-Highlight

| Option | Description | Selected |
|--------|-------------|----------|
| Subtiler Background | Accent background + text color on winner team name. Consistent with .bracket-team.winner and .nav-link-active | ✓ |
| Left-Border-Akzent | 3px accent left border on entire match card | |
| Winner-Badge | Star or "W" badge next to winner team name | |

**User's choice:** Subtiler Background (Empfohlen)
**Notes:** Consistent with existing design system patterns (.bracket-team.winner, .nav-link-active)

---

## Scroll-Indikator

| Option | Description | Selected |
|--------|-------------|----------|
| Gradient-Fade | Right-edge gradient overlay on table-wrap, pure CSS | ✓ |
| Scroll-Pfeil-Hint | Arrow/text hint that appears on load, needs JS for hide-after-scroll | |
| Styled Scrollbar | Custom webkit scrollbar in accent color, no Firefox support | |

**User's choice:** Gradient-Fade (Empfohlen)
**Notes:** Pure CSS approach preferred, no JavaScript needed

---

## Footer-Design

| Option | Description | Selected |
|--------|-------------|----------|
| Kompakt einzeilig | Links in one row (Top · Archive · Season), branding text below | ✓ |
| Zwei-Spalten | Left: nav links, Right: branding + back-to-top | |
| Minimal nur Links | Replace branding text entirely with just links | |

**User's choice:** Kompakt einzeilig (Empfohlen)
**Notes:** Minimalist, fits the dark design theme

---

## Hover & Transitions

| Option | Description | Selected |
|--------|-------------|----------|
| Gezielt ergänzen | Add transitions only where missing (tr, footer links). Keep existing 0.2s transitions. cursor:pointer on a, label, [role=button] | ✓ |
| Globaler Reset | Global transition on all interactive elements. Risk of overriding specific transitions | |

**User's choice:** Gezielt ergänzen (Empfohlen)
**Notes:** Targeted approach preserves existing carefully tuned transitions

## Claude's Discretion

- Scroll indicator gradient width/opacity
- Skip-link styling details
- aria-expanded on nav toggle
- Footer separator styling
- CSS class names for driver-profile inline style replacements
- cursor:pointer on .match-card elements

## Deferred Ideas

None
