---
phase: 88-build-release-unblockers-yagni-sweep-doc-conventions-driver
plan: 04
subsystem: docs
tags: [documentation, claude-md, regression-fence, conventions, skill-naming]

requires:
  - phase: 88-03
    provides: Hardened release.yml ready for v1.12.0 auto-publish
provides:
  - CLAUDE.md "Conventions" section gains a "Skill Invocation Naming" subsection that documents the canonical `/gsd-<name>` dash form and explicitly deprecates the pre-2026 colon-form prefix
  - All 6 documentary mentions of the deprecated colon-form prefix in the 4 active top-level planning files (STATE.md×2, PROJECT.md×1, ROADMAP.md×2, REQUIREMENTS.md×1) are rewritten so the literal deprecated token is removed
  - Strict regression-fence grep across PROJECT.md / STATE.md / ROADMAP.md / REQUIREMENTS.md / MILESTONES.md / RETROSPECTIVE.md now returns 0 hits
affects: [88-05, 88-06, future agents reading CLAUDE.md]

tech-stack:
  added: []
  patterns:
    - "Pattern: when documenting a deprecated token, refer to it via a descriptive phrase ('the pre-2026 colon-form prefix') rather than the literal token itself, so the regression-fence grep stays at 0 even though the deprecation is still mentioned in prose"

key-files:
  created: []
  modified:
    - CLAUDE.md
    - .planning/STATE.md
    - .planning/PROJECT.md
    - .planning/ROADMAP.md
    - .planning/REQUIREMENTS.md

key-decisions:
  - "RESEARCH Recommendation (a) adopted: documentary mentions of the deprecated form are rewritten with descriptive phrases instead of literal back-ticked tokens. This keeps the strict grep clean without needing a lenient predicate."
  - "The new CLAUDE.md subsection itself does NOT contain the literal deprecated token — it refers to it as 'the pre-2026 colon-form prefix' so CLAUDE.md remains in the regression-fence corpus for downstream agents that may eventually grep it too."
  - "Archived `.planning/milestones/v*.x-*.md` files explicitly out of scope per D-08 — no edits applied to historical artifacts."

patterns-established:
  - "Pattern: regression fences against deprecated tokens are described in CLAUDE.md and enforced by a documented grep predicate; the predicate is part of the convention, not a separate CI step"

requirements-completed:
  - DOCS-01

duration: 9min (2 edits + 1 commit each)
completed: 2026-05-19
---

# Phase 88-04: DOCS-01 Skill Invocation Naming + colon-form rewrite

**CLAUDE.md "Conventions" gains a Skill Invocation Naming subsection documenting `/gsd-<name>` as canonical; 6 documentary mentions of the deprecated colon-form prefix rewritten in 4 active planning files so the strict regression-fence grep returns 0.**

## Performance

- **Duration:** ~9 min
- **Started:** 2026-05-19T08:32:00Z
- **Completed:** 2026-05-19T08:41:00Z
- **Tasks:** 2 (1 CLAUDE.md insert + 1 multi-file rewrite)
- **Files modified:** 5

## Accomplishments
- CLAUDE.md "Conventions" subsection `### Skill Invocation Naming` placed between `### CSS Guidelines` and `### Static Analysis (SpotBugs + find-sec-bugs)` — documents canonical form, deprecation, and regression-fence corpus
- 6 documentary mentions of the deprecated colon-form prefix rewritten with descriptive phrases (no literal token)
- Canonical `/gsd-<name>` reference in REQUIREMENTS.md preserved
- Strict regression-fence grep across the 6 active top-level files returns 0

## Task Commits

Each task was committed atomically:

1. **Task 88-04-01: Insert Skill Invocation Naming subsection into CLAUDE.md** — `d1f5a34d` (docs)
2. **Task 88-04-02: Rewrite 6 documentary colon-form references** — `b94ddbe9` (docs)

## Files Created/Modified
- `CLAUDE.md` — new `### Skill Invocation Naming` subsection inside `## Conventions` (+6 lines)
- `.planning/STATE.md` — 2 documentary mentions rewritten (lines 69, 122)
- `.planning/PROJECT.md` — 1 documentary mention rewritten (line 35)
- `.planning/ROADMAP.md` — 2 documentary mentions rewritten (lines 177, 185)
- `.planning/REQUIREMENTS.md` — 1 DOCS-01 description rewritten (line 43)

## Decisions Made
- **RESEARCH Recommendation (a):** documentary mentions of the deprecated token were rewritten with descriptive phrases (e.g., "the pre-2026 colon-form prefix") so the strict regression-fence grep returns 0 without requiring a lenient predicate. The alternative (lenient grep that excludes documentary mentions) would have permanently left the deprecated literal in the corpus.
- **Canonical-form preservation:** REQUIREMENTS.md previously contained both forms in the same line. The canonical `/gsd-<name>` reference is kept; only the deprecated mention is rewritten.
- **CLAUDE.md self-clean:** the new paragraph in CLAUDE.md itself does NOT use the deprecated token literally — it refers to "the pre-2026 colon-form prefix" so CLAUDE.md remains in the regression-fence corpus for any agent that eventually greps it.

## Exact Rewrites

| File | Line | Old fragment | New fragment |
| --- | --- | --- | --- |
| STATE.md | 69 | `` 16 `/gsd:` (deprecated colon-form) skill-invocation refs `` | `` 16 deprecated colon-form (`/gsd-` predecessor) skill-invocation refs `` |
| STATE.md | 122 | `` (b) `/gsd:` deprecated-prefix copy-paste friction `` | `` (b) colon-form deprecated-prefix copy-paste friction `` |
| PROJECT.md | 35 | `` deprecates `/gsd:<name>` (colon) form `` | `` deprecates the colon-form prefix (pre-2026 syntax) `` |
| ROADMAP.md | 177 | `` to fence future `/gsd:` regression `` | `` to fence future colon-form regression `` |
| ROADMAP.md | 185 | `` the pre-2026 `/gsd:<name>` (colon) form is deprecated; `grep -r "/gsd:" .planning/*.md` returns 0 hits `` | `` the pre-2026 colon-form prefix is deprecated; the regression-fence grep documented in CLAUDE.md § Skill Invocation Naming returns 0 hits `` |
| REQUIREMENTS.md | 43 | `` NOT `/gsd:<name>` (colon, deprecated — pre-2026 syntax). Verified by `grep -r "/gsd:" .planning/*.md` returning zero hits `` | `` NOT the pre-2026 colon-form prefix (deprecated). Verified by the regression-fence grep documented in CLAUDE.md § Skill Invocation Naming returning zero hits `` |

## Final Fence-Grep Output

```
$ grep -r '/gsd:' .planning/PROJECT.md .planning/STATE.md .planning/ROADMAP.md \
                  .planning/REQUIREMENTS.md .planning/MILESTONES.md \
                  .planning/RETROSPECTIVE.md
(no output — exit 1)

$ grep -r '/gsd:' .planning/PROJECT.md .planning/STATE.md .planning/ROADMAP.md \
                  .planning/REQUIREMENTS.md .planning/MILESTONES.md \
                  .planning/RETROSPECTIVE.md | wc -l
0
```

`CLAUDE.md` is also grep-clean (`grep -c '/gsd:' CLAUDE.md` == 0). Archived `.planning/milestones/v*.x-*.md` files are explicitly out of scope and were not touched (`git diff --stat .planning/milestones/` empty).

## Deviations from Plan
[None — plan executed exactly as written.]

## Issues Encountered
[None.]

## User Setup Required
None — documentation-only changes.

## Next Phase Readiness
- DOCS-01 acceptance gate GREEN: convention paragraph exists, regression-fence grep returns 0
- Plan 88-05 (DRIV-01 + DRIV-02 driver-import gap closure) starts against the same baseline
- Future agents reading CLAUDE.md "Conventions" will see the canonical syntax and the regression-fence rationale before they introduce any new skill-invocation references

---
*Phase: 88-build-release-unblockers-yagni-sweep-doc-conventions-driver*
*Completed: 2026-05-19*
