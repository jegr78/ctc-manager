# Phase 37: Critical Link Fixes - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-16
**Phase:** 37-critical-link-fixes
**Areas discussed:** Slug consistency, Driver Ranking navigation, Root path fix, Logo resolution
**Mode:** --auto (all decisions auto-selected from recommended defaults)

---

## Slug Consistency (LINK-01)

| Option | Description | Selected |
|--------|-------------|----------|
| Pass pre-computed slug from service | Service sets `seasonSlug` variable, template uses it directly | ✓ |
| Fix Thymeleaf slugification to match | Replicate Java `slugify()` logic in Thymeleaf `#strings` | |
| Add slugify utility to template | Create a Thymeleaf dialect/utility for slugification | |

**User's choice:** Pass pre-computed slug from service (auto-selected recommended default)
**Notes:** Single source of truth — the `slugify()` method in the service already handles umlauts. Duplicating this logic in Thymeleaf would be fragile and diverge over time.

---

## Driver Ranking Navigation (LINK-02)

| Option | Description | Selected |
|--------|-------------|----------|
| Link to active season's page | Pass `activeSeasonSlug` to layout, link to season-specific DR page | ✓ |
| Generate root-level redirect | Create a root `driver-ranking.html` that redirects to active season | |
| Generate root-level aggregated page | Create a new all-seasons driver ranking at root level | |

**User's choice:** Link to active season's page (auto-selected recommended default)
**Notes:** No new pages needed. The active season is already loaded at the top of `generate()`. Adding a redirect page adds complexity for no benefit.

---

## Root Path Fix (LINK-03)

| Option | Description | Selected |
|--------|-------------|----------|
| Default empty rootPath to "." | When `relativize()` returns empty string, use "." instead | ✓ |
| Append "/" conditionally | Add logic to avoid double-slash in concatenation | |
| Use URI builder utility | Replace string concatenation with a path utility | |

**User's choice:** Default empty rootPath to "." (auto-selected recommended default)
**Notes:** Minimal change, standard convention. `"."` is the correct relative-path representation of "current directory".

---

## Logo Resolution (LINK-04)

| Option | Description | Selected |
|--------|-------------|----------|
| Copy logos to static site assets | Copy files during generation, rewrite URLs in context | ✓ |
| Use absolute admin URLs | Reference logos via full admin app URL | |
| Embed logos as base64 in HTML | Inline logo data directly in the page | |

**User's choice:** Copy logos to static site assets (auto-selected recommended default)
**Notes:** Self-contained static site with no external dependencies. Absolute admin URLs would break if the admin is offline. Base64 bloats HTML unnecessarily.

---

## Claude's Discretion

- Error handling for missing logo files (warn vs. debug)
- Whether Standings nav link needs fixing (currently points to root index which shows standings)
- Internal refactoring to reduce context variable repetition in `writeTemplate()`

## Deferred Ideas

None — discussion stayed within phase scope
