# Phase 67: Comment Cleanup Re-Sweep - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents. Decisions are captured in CONTEXT.md.

**Date:** 2026-05-07
**Phase:** 67-comment-cleanup-resweep
**Mode:** `--auto --chain` (no interactive AskUserQuestion calls; recommended option auto-selected per area)
**Areas discussed:** Definition of noise, Allow-list, Sweep methodology, Plan structure, Test impact, Verification gates

---

## Definition of "noise" — what gets removed

| Option | Description | Selected |
|---|---|---|
| Strip decoration + phase attribution + WHAT-style + stale TODO + Javadoc history narrative | Comprehensive sweep per CLAUDE.md "no narrative" rule | ✓ |
| Only strip decoration separators | Conservative — leaves phase-attribution comments | |
| Bulk regex-strip everything starting with `//` | Aggressive — destroys WHY comments | |

**Auto-selected:** Comprehensive (D-01..D-05).
**Rationale:** CLAUDE.md is explicit: WHY comments stay, WHAT/decoration/attribution goes. Conservative options leave the offending population that triggered the user's report.

---

## What to KEEP — explicit allow-list

| Option | Description | Selected |
|---|---|---|
| BDD markers + WHY Javadoc + workaround comments + active spec refs | Preserves CLAUDE.md mandates and structural patterns | ✓ |
| Strip all single-line `//` everywhere; keep only Javadoc | Risks destroying genuine WHY comments | |

**Auto-selected:** Allow-list (D-06..D-11).
**Rationale:** 1,899 BDD-marker lines across 105 test files would break test structure rules if removed. WHY comments are the very thing CLAUDE.md asks for.

---

## Sweep methodology

| Option | Description | Selected |
|---|---|---|
| Per-file judgement pass | Read each file, identify by pattern, edit with judgement | ✓ |
| Mechanical regex bulk-replace | Faster but unsafe — false positives on Javadoc and BDD | |
| Tool-driven (Checkstyle / PMD) | No project precedent + heavy infrastructure | |

**Auto-selected:** Per-file judgement (D-12, D-13).
**Rationale:** False-positive risk is too high for a regex sweep — 1,899 BDD lines and ~30 production WHY-Javadoc paragraphs are at stake.

---

## Plan structure

| Option | Description | Selected |
|---|---|---|
| Three plans (production / templates / tests) | Atomic per-directory commits, clearer review | ✓ |
| Single plan covering everything | One big diff — harder to review |  |
| One plan per package | Too granular for a comments-only sweep | |

**Auto-selected:** Three plans (D-14).
**Rationale:** Per-directory commits map naturally to per-conventional-prefix commits (`refactor:` / `style:` / `test:` per plan). Reviewers can tackle one directory at a time.

---

## Execution model — sequential vs. parallel

| Option | Description | Selected |
|---|---|---|
| Sequential single-wave on main tree | Plans 01 → 02 → 03 in order, no worktree | ✓ |
| Parallel worktrees (one per plan) | 3 worktrees, 3 merges — coordinator overhead | |

**Auto-selected:** Sequential single-wave (D-15).
**Rationale:** Comments-only changes don't benefit from parallelism. Worktree overhead exceeds the savings.

---

## Test impact

| Option | Description | Selected |
|---|---|---|
| Quick `./mvnw test -Dtest=<Class>` per service-touching plan + ONE final `./mvnw verify` | Per project memory `feedback_test_call_optimization.md` | ✓ |
| `./mvnw verify` after every plan | Triple cost, no extra signal | |

**Auto-selected:** Quick + ONE final verify (D-17).
**Rationale:** Comment-only changes can't change behavior; the full gate runs once at the very end. Wide quick checks per plan catch any accidental code-edit slips.

---

## Verification gates

| Option | Description | Selected |
|---|---|---|
| Quantitative grep counts before/after + behavior gate | Falsifiable acceptance criteria | ✓ |
| Manual spot-check only | Can't catch accidental code changes | |

**Auto-selected:** Quantitative + behavior gate (D-19, D-20).
**Rationale:** Each pattern has a clear "before" count from the codebase scout — the verifier can grep again and confirm zero. BDD markers must remain ≥ 1,899.

---

## Claude's Discretion

- **D-22:** Per-file judgement on borderline Javadoc (V5 Flyway migration history paragraph, etc.) — left to executor.
- **D-23:** Whether to consolidate adjacent kept Javadoc lines — formatting choice left to executor.
- **D-24:** Final sweep counts for SUMMARY.md — left to executor's tracking.

## Deferred Ideas

- CI / pre-commit comment-noise guard (Checkstyle / PMD / custom regex) — fragile; defer to a dedicated quality phase if regressions recur.
- Javadoc style normalization (`<p>` / `<ol>` formatting) — separate phase.
- License-header policy — out of scope (no licenses in repo today).
- Migration-file Javadoc audit beyond per-file judgement — defer to dedicated migration-docs phase if needed.
- Comment-language audit — already English-only since Phases 20-21.
- Templates HTML-comment semantic review (visible-in-browser leak audit) — separate security concern.
