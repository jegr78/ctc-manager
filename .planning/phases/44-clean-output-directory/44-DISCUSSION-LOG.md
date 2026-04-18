# Phase 44: Clean Output Directory - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-16
**Phase:** 44-clean-output-directory
**Areas discussed:** Cleanup Strategy, Safety Guards
**Mode:** --auto (all decisions auto-selected)

---

## Cleanup Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Files.walkFileTree bottom-up | Standard Java NIO, SimpleFileVisitor, no external deps | ✓ |
| Apache FileUtils.deleteDirectory | External dependency, one-liner but adds bloat | |
| Delete and recreate root dir | Simpler but risky if CWD is inside the directory | |

**User's choice:** [auto] Files.walkFileTree bottom-up (recommended default)
**Notes:** Standard Java approach, consistent with existing NIO usage in SiteGeneratorService

---

## Safety Guards

| Option | Description | Selected |
|--------|-------------|----------|
| No additional validation | Path from @Value properties, not user input | ✓ |
| Path prefix validation | Check output dir is under project root | |
| Confirmation prompt | Ask before deleting (not applicable for automated generation) | |

**User's choice:** [auto] No additional validation (recommended default)
**Notes:** Output directory configured via application properties per Spring profile — trusted source

## Claude's Discretion

- Logging levels (info vs debug for cleanup operations)
- Private method visibility and naming

## Deferred Ideas

None
