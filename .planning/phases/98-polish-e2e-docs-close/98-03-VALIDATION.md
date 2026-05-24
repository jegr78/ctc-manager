---
phase: 98-polish-e2e-docs-close
plan: 03
nyquist_compliant: pending
last_updated: 2026-05-24
---

# Plan 98-03 VALIDATION — DOCS-03 README + Wiki + MILESTONES.md + REQUIREMENTS.md Resolved-Flip + PR-Body Final

## Scope of Validation

Plan 98-03 is purely documentary and bookkeeping. Artifacts:

1. **Main-repo files** (committed to `gsd/v1.13-discord-integration`):
   - `README.md` — Discord-Integration bullet under `## Features`.
   - `.planning/MILESTONES.md` — new v1.13 entry prepended.
   - `.planning/REQUIREMENTS.md` — 22 REQ-IDs flipped Pending → Resolved.
   - `.planning/STATE.md` — UAT-08 staged + frontmatter updated.
   - `.gitignore` — `.wiki-clone/` entry appended.

2. **Wiki-repo files** (pushed to `ctc-manager.wiki.git`, NOT in main
   repo):
   - `Discord-Integration.md` — new page with canonical paragraph.
   - `Home.md` — sidebar updated.
   - `images/discord-config.png` — embedded screenshot.

3. **GitHub PR body** — final-state via `gh pr edit <num> --body-file -`.

Nyquist Sampling: 0 new `@Test` methods. Validation is gate-based.

## Verification Gates

### Gate 1 — .gitignore Has Wiki-Clone Entry

```bash
grep -c '^\.wiki-clone/$' .gitignore
# MUST be 1
```

### Gate 2 — Main-Repo Working Tree Excludes Wiki-Clone

```bash
git status --porcelain .wiki-clone/ 2>/dev/null | head -1
# MUST be empty (gitignored or non-existent in working tree)
```

### Gate 3 — README.md Discord-Integration Bullet

```bash
grep -c '\*\*Discord Integration\*\*' README.md
# MUST be >= 1

grep -c 'wiki/Discord-Integration' README.md
# MUST be 1

grep -c 'docs/operations/discord-integration.md' README.md
# MUST be >= 1

wc -l README.md
# MUST be > 158 (grew via APPEND from original 158-line baseline)
```

### Gate 4 — MILESTONES.md v1.13 Entry Prepended

```bash
grep -c '^## v1\.13 Discord Integration' .planning/MILESTONES.md
# MUST be 1

# Verify v1.13 is ABOVE v1.12 (descending chronological per PATTERNS):
V13_LINE=$(grep -n '^## v1\.13' .planning/MILESTONES.md | head -1 | cut -d: -f1)
V12_LINE=$(grep -n '^## v1\.12' .planning/MILESTONES.md | head -1 | cut -d: -f1)
[ -n "$V13_LINE" ] && [ -n "$V12_LINE" ] && [ "$V13_LINE" -lt "$V12_LINE" ]
# MUST exit 0
```

### Gate 5 — REQUIREMENTS.md 25/25 Resolved (Pre-Merge per D-98-PLAN-5)

```bash
grep -c '^| .* | 9[2-8] | Pending |$' .planning/REQUIREMENTS.md
# MUST be 0 (no pending v1.13 REQs)

grep -c '^- \[ \]' .planning/REQUIREMENTS.md
# MUST be 0 (no unchecked body checkboxes)

grep -c '^| .* | 9[2-8] | Resolved |$' .planning/REQUIREMENTS.md
# MUST be 25 (22 newly flipped + 3 INFRA-* already Resolved)
```

### Gate 6 — STATE.md UAT-08 Staged + Frontmatter Updated

```bash
grep -c '^### UAT-08:' .planning/STATE.md
# MUST be 1

grep -c 'docs/operations/discord-integration.md` § 7' .planning/STATE.md
# MUST be 1 (UAT-08 procedure cross-references runbook)

grep -c 'stopped_at: Phase 98 closed' .planning/STATE.md
# MUST be 1

grep -c 'completed_phases: 7' .planning/STATE.md
# MUST be 1

grep -c 'percent: 100' .planning/STATE.md
# MUST be 1

grep -c 'status: executing' .planning/STATE.md
# MUST be 1 (UNCHANGED — `/gsd-complete-milestone v1.13` flips this)
```

### Gate 7 — Wiki-Repo Updated

```bash
test -f .wiki-clone/Discord-Integration.md
# MUST exist (or Manual-Fallback documented in 98-03-SUMMARY.md)

grep -c '# Discord Integration' .wiki-clone/Discord-Integration.md 2>/dev/null || echo "MANUAL-FALLBACK"
# >= 1 OR MANUAL-FALLBACK (acceptable per D-98-PLAN-4 manual-fallback pattern)

# Verify Wiki repo HEAD was pushed (if .wiki-clone exists):
if [ -d .wiki-clone/.git ]; then
  ( cd .wiki-clone && git log --oneline origin/master..HEAD 2>/dev/null | wc -l ) 
  # MUST be 0 (HEAD pushed)
fi
```

### Gate 8 — PR-Body Updated, NO Tags, NO Merge

```bash
PR_NUM=$(gh pr list --head gsd/v1.13-discord-integration --json number -q '.[0].number')
test -n "$PR_NUM"
# MUST find an open PR

gh pr view "$PR_NUM" --json body -q '.body' | grep -c 'Milestone v1.13'
# MUST be >= 1

gh pr view "$PR_NUM" --json body -q '.body' | grep -c '25 / 25'
# MUST be 1

gh pr view "$PR_NUM" --json body -q '.body' | grep -c 'feat(v1\.13): discord integration'
# MUST be 1 (squash subject reminder)

! git tag --list 'v1.13*' | grep .
# MUST exit 0 (no local v1.13 tag)

gh pr view "$PR_NUM" --json state -q '.state'
# MUST be "OPEN" (NOT merged)
```

### Gate 9 — Single Atomic Commit per Plan-Convention

```bash
git log -1 --format='%s' | grep -E '^docs\(98-03\):'
# MUST match Conventional-Commits scope 98-03

git log -1 --stat | grep -cE 'README\.md|MILESTONES\.md|REQUIREMENTS\.md|STATE\.md|\.gitignore'
# MUST be 5 (single commit, 5 files)

git branch --show-current
# MUST be gsd/v1.13-discord-integration
```

## Nyquist Compliance

**Not applicable.** Plan 98-03 produces 0 new `@Test` methods.
Validation is gate-based (grep + gh + git).

**Resulting `nyquist_compliant`:** `n/a` (set to `pending` until
plan-validate confirms all 9 gates).

## Decisions Honored

- D-98-WIKI-1 — `.wiki-clone/` clone + push pattern.
- D-98-WIKI-2 — New `Discord-Integration.md` page + `Home.md` sidebar.
- D-98-WIKI-3 — Canonical paragraph identical in README + Wiki.
- D-98-CLOSE-1 — MILESTONES.md v1.13 entry per locked format.
- D-98-CLOSE-2 — REQUIREMENTS.md Pre-Merge-Flip.
- D-98-PLAN-4 — `/gsd-complete-milestone` + squash-merge remain
  User-Manual; no `gh pr merge` and no `git tag` in this plan.
- D-98-PLAN-5 — ALL bookkeeping committed Pre-Merge.
- D-98-E2E-9 — UAT-08 staged in STATE.md.
- D-98-PROD-1 — Scope restricted to README + planning-files +
  `.gitignore` + Wiki-clone.


- D-98-PLAN-2 — implicitly honored (Plan-03 satisfies the decision via the gates above).
- D-98-PLAN-3 — implicitly honored (Plan-03 satisfies the decision via the gates above).
- D-98-TEST-1 — implicitly honored (Plan-03 satisfies the decision via the gates above).

## Outcome Template

When all 9 gates pass, update frontmatter to:
```
nyquist_compliant: n/a
last_updated: <date>
```
and append a `## Outcome` section with:
- PR-Number + body-update timestamp.
- Wiki-push commit-SHA (or Manual-Fallback note).
- REQ-IDs flipped (22 listed).
- Files changed (5 main + 2-3 wiki).
- Confirmation: 0 git tags, 0 PR merges.
</content>
</invoke>