# Phase 48: Landing Page Redesign - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.

**Date:** 2026-04-17
**Phase:** 48-landing-page-redesign
**Areas discussed:** YouTube Scraping, Hero Layout, Tile Design, Nav Adjustment, Test Adaptation
**Mode:** --auto

---

## YouTube Scraping

| Option | Description | Selected |
|--------|-------------|----------|
| YouTubeScraperService + Jsoup | Separate service, testable, regex on page source | ✓ |
| YouTube Data API | Requires API key setup | |
| Hardcoded video ID | No auto-update when trailer changes | |

**User's choice:** [auto] YouTubeScraperService + Jsoup (recommended)

---

## Hero Layout

| Option | Description | Selected |
|--------|-------------|----------|
| Responsive 16:9 iframe | Standard YouTube embed, responsive | ✓ |
| Background video | Complex, autoplay issues | |
| Thumbnail with play button | Extra click, less engaging | |

**User's choice:** [auto] Responsive 16:9 iframe (recommended)

---

## Tile Design

| Option | Description | Selected |
|--------|-------------|----------|
| 3+2 centered grid | Visually balanced for 5 tiles | ✓ |
| 5-column row | Too narrow on mobile | |
| 2+2+1 stacked | Inconsistent column widths | |

**User's choice:** [auto] 3+2 centered grid (recommended)

---

## Navigation Adjustment

| Option | Description | Selected |
|--------|-------------|----------|
| Standings → active season | Clear separation of home vs standings | ✓ |
| Keep current (Standings → index) | Confusing with new landing page | |
| Add separate Home nav item | Redundant with brand link | |

**User's choice:** [auto] Standings → active season (recommended)

---

## Existing Tests

| Option | Description | Selected |
|--------|-------------|----------|
| Remove/adapt old + add new | Clean transition, no dead tests | ✓ |
| Keep old tests alongside new | Contradictory assertions | |
| Only add new, ignore old | Old tests will fail | |

**User's choice:** [auto] Remove/adapt old + add new (recommended)

## Claude's Discretion

- Tile descriptions, hero spacing, season label, CSS transitions

## Deferred Ideas

None
