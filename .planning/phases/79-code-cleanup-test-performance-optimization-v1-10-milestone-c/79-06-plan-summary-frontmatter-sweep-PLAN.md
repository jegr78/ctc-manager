---
phase: 79
plan: 06
type: execute
wave: 5
depends_on: [79-03]
files_modified:
  - .planning/milestones/v1.9-phases/56-model-schema-foundation/56-01-SUMMARY.md
  - .planning/milestones/v1.9-phases/56-model-schema-foundation/56-02-SUMMARY.md
  - .planning/milestones/v1.9-phases/56-model-schema-foundation/56-03-SUMMARY.md
  - .planning/milestones/v1.9-phases/56-model-schema-foundation/56-04-SUMMARY.md
  - .planning/milestones/v1.9-phases/56-model-schema-foundation/56-05-SUMMARY.md
  - .planning/milestones/v1.9-phases/57-data-migration/57-01-SUMMARY.md
  - .planning/milestones/v1.9-phases/57-data-migration/57-02-SUMMARY.md
  - .planning/milestones/v1.9-phases/57-data-migration/57-03-SUMMARY.md
  - .planning/milestones/v1.9-phases/62-public-site-phases-groups/62-00-SUMMARY.md
  - .planning/milestones/v1.9-phases/62-public-site-phases-groups/62-01-SUMMARY.md
  - .planning/milestones/v1.9-phases/62-public-site-phases-groups/62-02-SUMMARY.md
  - .planning/milestones/v1.9-phases/62-public-site-phases-groups/62-03-SUMMARY.md
  - .planning/milestones/v1.9-phases/62-public-site-phases-groups/62-04-SUMMARY.md
  - .planning/milestones/v1.9-phases/62-public-site-phases-groups/62-05-SUMMARY.md
  - .planning/milestones/v1.9-phases/62-public-site-phases-groups/62-06-SUMMARY.md
  - .planning/milestones/v1.9-phases/62-public-site-phases-groups/62-07-SUMMARY.md
  - .planning/milestones/v1.9-phases/64-nyquist-validation-sweep/64-01-SUMMARY.md
autonomous: true
requirements: [D-16]

must_haves:
  truths:
    - "Every SUMMARY.md in the 4 target phases (56, 57, 62, 64) has the canonical frontmatter schema: `phase`, `plan`, `type`, `wave`, `status`, `completed`"
    - "Frontmatter parses cleanly as YAML on every file (no broken indentation, no unquoted strings with colons)"
    - "Heterogeneous schemas (some with `subsystem`, some with `dependency_graph`, some with `plan_id` instead of `plan`) are normalized to the canonical schema"
    - "Body content below the frontmatter is UNCHANGED — this is a bookkeeping fix only"
  artifacts:
    - path: "git log on branch gsd/v1.10-platform-and-backup"
      provides: "Single docs-commit sweeping 17 SUMMARY frontmatter blocks"
      pattern: "docs\\(79\\): normalize plan-SUMMARY frontmatter for phases 56/57/62/64"
  key_links:
    - from: "SUMMARY frontmatter"
      to: "GSD tooling (`gsd-sdk query frontmatter.validate`)"
      via: "valid YAML + canonical schema"
      pattern: "phase:|plan:|type:|wave:|status:|completed:"
---

<objective>
Wave 6 of Phase 79: the SINGLE v1.9 carry-over admitted into Phase 79 per D-16. Fix the heterogeneous SUMMARY frontmatter on the 17 archived SUMMARY files across 4 phases (Phase 56, 57, 62, 64) to use the canonical schema (`phase`, `plan`, `type`, `wave`, `status`, `completed`).

Purpose: pre-Phase-79 SUMMARY files used a variety of frontmatter shapes — some include `subsystem`, `tags`, `dependency_graph` blocks; some use `plan_id: 64-01` instead of `plan: "01"`; some quote `plan` ("01"), others don't (01). This Phase 79 closer normalizes all 17 files so future tooling (`gsd-sdk query frontmatter.validate --schema summary`) parses them uniformly.

Output: 1 docs-commit applying the canonical schema to all 17 files. Body content is UNCHANGED. Trivial. Can run in parallel with Plan 04 and Plan 05 (different files).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-CONTEXT.md
@CLAUDE.md

<interfaces>
**Canonical SUMMARY frontmatter schema (target — matches existing template `$HOME/.claude/get-shit-done/templates/summary.md` conventions):**

```yaml
---
phase: <phase_dir_slug>   # e.g., "56-model-schema-foundation" — string, may stay as the pre-existing value
plan: "<NN>"              # e.g., "01" — QUOTED string (YAML coerces unquoted "01" to integer 1)
type: execute             # always "execute" for these legacy SUMMARYs (no TDD plans in v1.9)
wave: <N>                 # integer — keep pre-existing value if present, otherwise derive from RESEARCH (per-phase planning logs)
status: complete          # always "complete" for archived phases
completed: <YYYY-MM-DD>   # keep pre-existing if present, otherwise from ROADMAP.md phase-shipped dates
---
```

**OPTIONAL but PRESERVED fields (do NOT delete if present):**
- `subsystem: <module>` (Phase 56/57/62 SUMMARYs use this)
- `tags: [...]` (Phase 56/62 use this)
- `dependency_graph:` block (Phase 57/62 use this — `requires` / `provides` / `affects` sub-keys)
- `tech_stack:` block (Phase 57 uses this)
- `requires` / `provides` / `affects` top-level keys (Phase 56/62 use these — separate from `dependency_graph`)

**Heterogeneity inventory (sampled from `head -20` of representative files):**

| Phase | File | Current schema issues |
|---|---|---|
| 56 | `56-01-SUMMARY.md` | Has `phase`, `plan`, `subsystem`, `tags`, `requires`/`provides`/`affects`. **Issue:** `plan: 01` unquoted (parses as integer 1, not string "01"). |
| 57 | `57-01-SUMMARY.md` | Has `phase`, `plan: "01"` (quoted), `subsystem`, `tags`, `dependency_graph`, `tech_stack`. **Issue:** none significant — already close to canonical, but `type`/`wave`/`status`/`completed` may be missing. |
| 62 | `62-00-SUMMARY.md` | Has `phase`, `plan: 00` unquoted, `subsystem`, `type: execute`, `wave: 1`, `tags`, `requires`/`provides`. **Issue:** unquoted `plan`. |
| 64 | `64-01-SUMMARY.md` | Uses `plan_id: 64-01` AND `phase: 64` AND `phase_name`. **Issue:** uses `plan_id` instead of `plan`; missing `wave`; uses `mode: retroactive` (preserve). |

**Schema normalization rules (per file, applied minimally — bookkeeping fix only):**

1. If `plan` is unquoted (e.g. `plan: 01`) → quote it (`plan: "01"`).
2. If `plan_id: 64-01` style is used → ADD `plan: "01"` (keep `plan_id` if present for backward-compatibility).
3. If `type:` missing → ADD `type: execute`.
4. If `wave:` missing → infer from the phase's planning log if available, otherwise OMIT (do not invent a wave number for retroactive phases like 64).
5. If `status:` missing → ADD `status: complete` (all 4 phases are SHIPPED per ROADMAP.md v1.9 section).
6. If `completed:` missing → ADD from ROADMAP.md per-phase shipped dates: 56=2026-04-26, 57=2026-04-27, 62=2026-05-07, 64=2026-05-07.

**DO NOT DELETE OR RENAME** any existing field. The schema is ADDITIVE/QUOTE-FIXING only.

**Source of truth for `completed:` dates (from ROADMAP.md v1.9-shipped section):**
- Phase 56: 2026-04-26
- Phase 57: 2026-04-27
- Phase 62: 2026-05-07
- Phase 64: 2026-05-07

**Mechanics decision (CD-07 default — manual edits to keep diffs reviewable):** Apply edits one file at a time using the Edit tool. Do NOT batch-rewrite via a script; that would obscure the per-file diff. 17 files × ~6 minimal edits per file = manageable in one task.
</interfaces>
</context>

<critical_constraints>
- You are working on branch `gsd/v1.10-platform-and-backup`. Do NOT run `git stash`, `git checkout`, `git reset`, or switch branches.
- Implement ONLY the tasks listed below. If you find that other files need changes, report `NEEDS_CONTEXT` instead of expanding scope.
- After the commit, the only verification needed is YAML parses cleanly on each file (no build invocation required).
- Schutzwortliste (D-13): N/A — frontmatter-only edits, no comment content touched.
- Body content (everything after the closing `---` line) is UNCHANGED. ZERO body-line diffs.
- Single commit per D-16.
</critical_constraints>

<test_impact>
N/A — `.planning/` Markdown-only changes. No Java/XML/YAML source touched. No test added/removed/renamed. JaCoCo impact: 0. CI impact: 0.
</test_impact>

<tasks>

<task type="auto">
  <name>Task 1: Normalize frontmatter on 17 SUMMARY files + commit</name>
  <files>.planning/milestones/v1.9-phases/56-model-schema-foundation/56-{01,02,03,04,05}-SUMMARY.md, .planning/milestones/v1.9-phases/57-data-migration/57-{01,02,03}-SUMMARY.md, .planning/milestones/v1.9-phases/62-public-site-phases-groups/62-{00,01,02,03,04,05,06,07}-SUMMARY.md, .planning/milestones/v1.9-phases/64-nyquist-validation-sweep/64-01-SUMMARY.md</files>
  <read_first>
    - Read every target file's frontmatter (top ~30 lines until the closing `---`)
    - This plan's `<interfaces>` schema-normalization rules + per-phase `completed:` dates
    - `$HOME/.claude/get-shit-done/templates/summary.md` (canonical schema reference, if accessible)
  </read_first>
  <action>
For each of the 17 target SUMMARY.md files, apply the schema-normalization rules from `<interfaces>` minimally:

**Workflow per file:**

1. Read the file's frontmatter block (everything between the opening `---` and the next `---`).
2. Apply the 6 normalization rules from `<interfaces>` to ONLY the frontmatter, in the order listed:
   - Quote `plan` if unquoted
   - Add `plan: "NN"` if `plan_id` is the only plan identifier present (keep `plan_id`)
   - Add `type: execute` if missing
   - Add `wave: N` if missing AND a wave number can be inferred from the phase's per-phase planning log (omit silently if retroactive/no-wave-available — Phase 64 is a single-plan retroactive sweep, no wave inference)
   - Add `status: complete` if missing
   - Add `completed: <date>` if missing — use the per-phase date from `<interfaces>`
3. PRESERVE all other existing keys (`subsystem`, `tags`, `dependency_graph`, `tech_stack`, `requires`/`provides`/`affects`, `mode`, `title`, `phase_name`) verbatim.
4. PRESERVE body content (everything after the closing `---`) byte-identical.
5. Use the Edit tool with the EXACT current frontmatter block as `old_string` and the normalized block as `new_string`. This ensures the diff is reviewable per CD-07.

**Phase-specific notes (applied within the file-by-file workflow):**

- **Phase 56 (5 files, 56-01..56-05):** Already have most fields; PRIMARY FIX is quoting `plan: 01` → `plan: "01"` and adding `type: execute` + `status: complete` + `completed: 2026-04-26` if missing.
- **Phase 57 (3 files, 57-01..57-03):** Already have `plan: "01"` (quoted); PRIMARY FIX is adding `type: execute` + `wave: N` (if inferable from 57-PLAN.md log) + `status: complete` + `completed: 2026-04-27` if missing.
- **Phase 62 (8 files, 62-00..62-07):** Already have `type: execute` + `wave: N`; PRIMARY FIX is quoting `plan: 00`/`plan: 01` → `plan: "00"`/`plan: "01"` and adding `status: complete` + `completed: 2026-05-07` if missing.
- **Phase 64 (1 file, 64-01):** Uses `plan_id: 64-01`; ADD `plan: "01"` (keeping `plan_id`), ADD `type: execute`, ADD `status: complete` (note: file already has `status: complete` — verify; if absent, add), ADD `completed: 2026-05-07` if missing.

**Per-file YAML-parse verification (after each Edit):**
```
python3 -c "import yaml; f=open('<filepath>'); content=f.read(); end=content.index('---', 4); fm=content[4:end]; yaml.safe_load(fm); print('YAML OK')"
```
Must print "YAML OK" for each file. If ParseError → undo that file's edit, fix the syntax, retry.

**Body-untouched verification (after editing all 17 files, BEFORE staging):**
```
git diff --stat .planning/milestones/v1.9-phases/56*/*-SUMMARY.md .planning/milestones/v1.9-phases/57*/*-SUMMARY.md .planning/milestones/v1.9-phases/62*/*-SUMMARY.md .planning/milestones/v1.9-phases/64*/*-SUMMARY.md
```
Inspect that the line-add / line-remove counts per file are small (≤ 7 each — corresponding to the maximum 6 frontmatter normalization rules + 1 line for the new closing `---` if any). If any file shows large line-changes → STOP, inspect for accidental body-edit.

**Stage + commit:** `git add` the 17 files explicitly (do NOT use `git add .`):
```
git add .planning/milestones/v1.9-phases/56-model-schema-foundation/*-SUMMARY.md \
        .planning/milestones/v1.9-phases/57-data-migration/*-SUMMARY.md \
        .planning/milestones/v1.9-phases/62-public-site-phases-groups/*-SUMMARY.md \
        .planning/milestones/v1.9-phases/64-nyquist-validation-sweep/64-01-SUMMARY.md
```
Confirm `git status` shows EXACTLY 17 files staged (5 + 3 + 8 + 1). Commit:
```
docs(79): normalize plan-SUMMARY frontmatter for phases 56/57/62/64 (D-16)

Bookkeeping carry-over from v1.9 deferred (CONTEXT.md §D-16: SINGLE v1.9 carry-over
admitted into Phase 79). 17 archived SUMMARY files updated to canonical schema:

- Phase 56 (5 files): quote `plan`, add `type`/`status`/`completed: 2026-04-26`
- Phase 57 (3 files): add `type`/`wave`/`status`/`completed: 2026-04-27`
- Phase 62 (8 files): quote `plan`, add `status`/`completed: 2026-05-07`
- Phase 64 (1 file): add `plan` alongside `plan_id`, `type`/`status`/`completed: 2026-05-07`

Body content byte-identical. All 17 files parse cleanly as YAML.

Other v1.9 carry-overs (Quality-Gate-Lock, StandingsController:139 lazy-collection,
per-group matchday UI, UAT-02) remain DEFERRED to v1.11+ per D-16.
```

Do NOT run `./mvnw verify` — this is a `.planning/` docs-only commit.
  </action>
  <verify>
    <automated>[ "$(git diff --cached --name-only | grep -cE 'milestones/v1\.9-phases/.*SUMMARY\.md$')" = "17" ] || [ "$(git log -1 --name-only --pretty=format:'' | grep -cE 'milestones/v1\.9-phases/.*SUMMARY\.md$')" = "17" ]</automated>
  </verify>
  <acceptance_criteria>
    - 17 SUMMARY files staged + committed (5 + 3 + 8 + 1)
    - Every file's frontmatter parses cleanly as YAML (Python yaml.safe_load test)
    - Every file has at minimum: `phase`, `plan` (quoted), `type: execute`, `status: complete`, `completed: <date>`
    - Body content (post-frontmatter) is byte-identical for all 17 files (verify with `git diff --stat` showing only small line counts)
    - No other file touched (commit includes ONLY the 17 SUMMARY files)
    - Commit `docs(79): normalize plan-SUMMARY frontmatter for phases 56/57/62/64 (D-16)` lands on `gsd/v1.10-platform-and-backup`
  </acceptance_criteria>
  <done>Single commit lands; 17 files normalized; YAML parses; body untouched.</done>
</task>

</tasks>

<verification>
- 1 atomic commit on `gsd/v1.10-platform-and-backup`
- 17 SUMMARY files staged in that commit (verifiable via `git log -1 --name-only`)
- Frontmatter YAML parses cleanly on every file
- No body-content delta on any file
</verification>

<success_criteria>
- 1 atomic docs-commit lands
- 17 SUMMARY files normalized to canonical schema
- Body content unchanged
- Branch unchanged
</success_criteria>

<output>
After completion, create `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-06-SUMMARY.md` per `@$HOME/.claude/get-shit-done/templates/summary.md`. Include: commit SHA, list of 17 normalized files, YAML-parse proof per file, body-byte-identical proof.
</output>
