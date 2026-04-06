# GSD Auto-UAT Skill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a Claude Code skill that automates human UAT verification items using `playwright-cli`, with server lifecycle management and GSD pipeline integration.

**Architecture:** Single SKILL.md file with `<step>` blocks following GSD conventions. Shell commands handle deterministic operations (server start/stop, file parsing). LLM handles non-deterministic operations (test interpretation, result evaluation). Three invocation modes: Phase-UAT, Standalone, Quick-Check.

**Tech Stack:** Claude Code Skill (Markdown), `playwright-cli`, Bash, YAML frontmatter parsing

---

### Task 1: Create SKILL.md with frontmatter and argument parsing

**Files:**
- Create: `.claude/skills/gsd-auto-uat/SKILL.md`

- [ ] **Step 1: Create the skill file with frontmatter and purpose block**

```markdown
---
name: gsd-auto-uat
description: Automate human UAT verification items using playwright-cli. Reads test descriptions from VERIFICATION.md or accepts freeform input, starts the dev server if needed, executes browser-based tests, captures screenshot evidence, and writes results back into the GSD pipeline.
allowed-tools: Bash(playwright-cli:*), Bash(curl:*), Bash(./mvnw:*), Bash(kill:*), Bash(mkdir:*), Bash(ls:*), Bash(find:*)
---

<purpose>
Automate human UAT verification using playwright-cli. Three modes:
- Phase: `/gsd-auto-uat 10` — reads human_verification from VERIFICATION.md
- Standalone: `/gsd-auto-uat "Check that /admin/seasons renders correctly"`
- Quick-Check: `/gsd-auto-uat /admin/teams /admin/seasons`

Starts dev server if needed, runs tests via playwright-cli, captures screenshots, writes results.
</purpose>
```

- [ ] **Step 2: Add the parse_args step**

```markdown
<step name="parse_args" priority="first">
**Determine invocation mode from $ARGUMENTS:**

Classify the input:

1. **Number** (e.g. `10`, `5`) → **Phase-UAT mode**
   - Set `MODE=phase`
   - Set `PHASE_NUM` to the number
   - Find phase directory:
     ```bash
     PHASE_DIR=$(find .planning/phases .planning/milestones -maxdepth 2 -type d -name "${PHASE_NUM}-*" 2>/dev/null | head -1)
     ```
   - If not found, check archived milestones:
     ```bash
     PHASE_DIR=$(find .planning/milestones/*/phases -maxdepth 1 -type d -name "${PHASE_NUM}-*" 2>/dev/null | head -1)
     ```
   - If still not found: report error and stop.

2. **Starts with `/`** (e.g. `/admin/teams /admin/seasons`) → **Quick-Check mode**
   - Set `MODE=quick`
   - Split arguments into URL list

3. **Anything else** (quoted string) → **Standalone mode**
   - Set `MODE=standalone`
   - The entire argument is the test description

Display: `Mode: {MODE} | Target: {phase/URLs/description}`
</step>
```

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/gsd-auto-uat/SKILL.md
git commit -m "feat(auto-uat): add skill with argument parsing step"
```

### Task 2: Add server lifecycle steps

**Files:**
- Modify: `.claude/skills/gsd-auto-uat/SKILL.md`

- [ ] **Step 1: Add the server_ensure step**

Append after the `parse_args` step:

```markdown
<step name="server_ensure">
**Ensure dev server is running on port 9090.**

```bash
if curl -sf http://localhost:9090/actuator/health > /dev/null 2>&1; then
  echo "SERVER_STATUS=already_running"
else
  echo "SERVER_STATUS=not_running"
fi
```

**If `SERVER_STATUS=already_running`:**
- Set `SERVER_STARTED_BY_US=false`
- Display: `Server already running on :9090`

**If `SERVER_STATUS=not_running`:**
- Set `SERVER_STARTED_BY_US=true`
- Start the server:
  ```bash
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo > /tmp/auto-uat-server.log 2>&1 &
  SERVER_PID=$!
  echo "SERVER_PID=$SERVER_PID"
  ```
- Poll until healthy (max 120s):
  ```bash
  for i in $(seq 1 40); do
    if curl -sf http://localhost:9090/actuator/health > /dev/null 2>&1; then
      echo "SERVER_HEALTHY=true"
      break
    fi
    sleep 3
  done
  ```
- If not healthy after 120s: display error, show last 20 lines of `/tmp/auto-uat-server.log`, and stop.
- Display: `Server started (PID: $SERVER_PID), healthy after ~{seconds}s`
</step>
```

- [ ] **Step 2: Add the server_cleanup and close_browser steps at the end**

These steps will be the last two in the skill. Add them now so the full lifecycle is in place:

```markdown
<step name="server_cleanup">
**Stop server if we started it.**

Only execute if `SERVER_STARTED_BY_US=true`:

```bash
if [ "$SERVER_STARTED_BY_US" = "true" ] && [ -n "$SERVER_PID" ]; then
  kill $SERVER_PID 2>/dev/null
  wait $SERVER_PID 2>/dev/null
  echo "Server stopped (PID: $SERVER_PID)"
fi
```

If `SERVER_STARTED_BY_US=false`: display `Server was already running — left untouched`.
</step>

<step name="close_browser">
**Close playwright-cli browser session.**

```bash
playwright-cli close
```
</step>
```

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/gsd-auto-uat/SKILL.md
git commit -m "feat(auto-uat): add server lifecycle and browser cleanup steps"
```

### Task 3: Add test extraction step

**Files:**
- Modify: `.claude/skills/gsd-auto-uat/SKILL.md`

- [ ] **Step 1: Add the extract_tests step**

Insert after `server_ensure`, before `server_cleanup`:

```markdown
<step name="extract_tests">
**Build test list based on mode.**

**Phase-UAT mode (`MODE=phase`):**

Read the VERIFICATION.md file:
```bash
VERIFICATION_FILE=$(find "$PHASE_DIR" -name "*-VERIFICATION.md" -type f 2>/dev/null | head -1)
```

If not found: report error and skip to `server_cleanup`.

Read the file and parse the YAML frontmatter `human_verification` array. Each item has fields:
- `test`: What to do (navigation steps, interactions)
- `expected`: What should happen (observable outcomes)
- `why_human`: Why this was flagged for human testing

**Classify each item** by analyzing `why_human`:

Skip keywords (set `automatable: false`):
- `external API`, `credentials`, `Google Calendar`, `docker`, `production profile`

Execute keywords (set `automatable: true`):
- `visual`, `running server`, `browser`, `form`, `rendering`, `layout`

Default (no keyword match): `automatable: true` — attempt automation, mark `failed` if playwright-cli errors.

**Standalone mode (`MODE=standalone`):**

Create a single test:
- `test`: The full `$ARGUMENTS` string
- `expected`: "Page renders correctly without errors"
- `automatable: true`

**Quick-Check mode (`MODE=quick`):**

For each URL path in `$ARGUMENTS`, create a test:
- `test`: "Navigate to {url}"
- `expected`: "Page loads without errors — no 500 status, no Whitelabel Error Page, no stack trace"
- `automatable: true`

**Display test plan:**

```
## Test Plan

| # | Test | Auto? |
|---|------|-------|
| 1 | Template Editor save/reset | yes |
| 2 | Playoff Bracket View | yes |
| 3 | Race Form + Calendar | no — requires Google Calendar credentials |

Automatable: {N} | Skipped: {M}
```
</step>
```

- [ ] **Step 2: Commit**

```bash
git add .claude/skills/gsd-auto-uat/SKILL.md
git commit -m "feat(auto-uat): add test extraction and classification step"
```

### Task 4: Add browser open and test execution steps

**Files:**
- Modify: `.claude/skills/gsd-auto-uat/SKILL.md`

- [ ] **Step 1: Add the open_browser step**

Insert after `extract_tests`:

```markdown
<step name="open_browser">
**Open browser session for test execution.**

```bash
mkdir -p .screenshots/auto-uat
playwright-cli open http://localhost:9090
```

Take an initial snapshot to confirm the browser is connected:
```bash
playwright-cli snapshot --filename=.screenshots/auto-uat/initial.yaml
```

If the snapshot fails, report error and skip to `server_cleanup`.
</step>
```

- [ ] **Step 2: Add the execute_tests step**

Insert after `open_browser`:

```markdown
<step name="execute_tests">
**Execute each automatable test sequentially.**

For each test where `automatable: true`, in order:

1. **Announce:** Display `Testing {N}/{total}: {test name}`

2. **Plan actions:** Read the `test` field and determine the playwright-cli command sequence needed. Common patterns:
   - "Navigate to /admin/X" → `playwright-cli goto http://localhost:9090/admin/X`
   - "Click on Y" → `playwright-cli snapshot` to find element ref, then `playwright-cli click {ref}`
   - "Fill form field Z" → `playwright-cli fill {ref} "value"`
   - "Verify flash message" → `playwright-cli snapshot` and check for flash text in snapshot output
   - "Check page has no errors" → verify snapshot contains no "Whitelabel Error", "500", or stack trace

3. **Execute:** Run each playwright-cli command. After key interactions, take a snapshot:
   ```bash
   playwright-cli snapshot --filename=.screenshots/auto-uat/test-{id}-{step_name}.yaml
   ```

4. **Screenshot as evidence:**
   ```bash
   playwright-cli screenshot --filename=.screenshots/auto-uat/test-{id}-result.png
   ```

5. **Evaluate:** Compare the snapshot/page state against the `expected` field:
   - If the expected conditions are met → `result: passed` with brief evidence note
   - If conditions are NOT met → `result: failed` with description of what differed
   - If a playwright-cli command errored → `result: failed` with error message

6. **Record:** Store the result (status, evidence, screenshot paths) for the reporting step.

7. **Continue:** Move to the next test regardless of result.

For skipped tests (`automatable: false`): record `result: skipped` with the `why_human` reason.

**After all tests:** Display inline summary:
```
Executed: {N} | Passed: {P} | Failed: {F} | Skipped: {S}
```
</step>
```

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/gsd-auto-uat/SKILL.md
git commit -m "feat(auto-uat): add browser open and test execution steps"
```

### Task 5: Add report writing step

**Files:**
- Modify: `.claude/skills/gsd-auto-uat/SKILL.md`

- [ ] **Step 1: Add the write_reports step**

Insert after `execute_tests`, before `server_cleanup`:

```markdown
<step name="write_reports">
**Write test results to reports and terminal.**

**Phase-UAT mode — write AUTO-UAT.md:**

Create `.planning/phases/{phase_dir}/{phase_num}-AUTO-UAT.md`:

```markdown
---
phase: {phase_name}
executed: {ISO-8601 timestamp}
server_profile: dev,demo
total: {total_count}
passed: {passed_count}
failed: {failed_count}
skipped: {skipped_count}
---

# Auto-UAT Report: Phase {phase_num}

## Results

### {N}. {test_name}
- **Status:** {passed|failed|skipped}
- **Screenshots:** [{screenshot_name}](../../.screenshots/auto-uat/{filename})
- **Evidence:** {evidence_note or skip_reason}
```

One section per test, with all screenshots linked.

**Phase-UAT mode — update HUMAN-UAT.md (if exists):**

```bash
HUMAN_UAT_FILE=$(find "$PHASE_DIR" -name "*-HUMAN-UAT.md" -type f 2>/dev/null | head -1)
```

If found, update each test's `result:` line:
- `result: [pending]` → `result: passed (auto-verified {timestamp})` for passed tests
- `result: [pending]` → `result: failed (auto-verified {timestamp}) — {reason}` for failed tests
- `result: [pending]` → `result: skipped (not automatable) — {reason}` for skipped tests

Update the `## Summary` section:
- Recalculate `passed:`, `issues:` (= failed count), `pending:` (= remaining pending), `skipped:` counts
- Update `status:` in frontmatter: `complete` if no pending/failed, `partial` otherwise
- Update `updated:` timestamp in frontmatter

**All modes — terminal output:**

Display a summary table:

```
## Auto-UAT Results

| # | Test | Status | Evidence |
|---|------|--------|----------|
| 1 | {name} | {status} | {evidence} |

{passed}/{total} passed, {failed} failed, {skipped} skipped
Screenshots: .screenshots/auto-uat/
```
</step>
```

- [ ] **Step 2: Commit**

```bash
git add .claude/skills/gsd-auto-uat/SKILL.md
git commit -m "feat(auto-uat): add report writing step with GSD pipeline integration"
```

### Task 6: Register skill in settings and verify

**Files:**
- Modify: `.claude/settings.local.json` (add skill permissions)

- [ ] **Step 1: Check current settings for skill permissions**

```bash
cat .claude/settings.local.json | grep -A5 "allow"
```

Verify that `Skill(gsd-auto-uat)` patterns are allowed. The `playwright-cli` permissions should already exist. If `gsd-auto-uat` is not listed, add:
- `"Skill(gsd-auto-uat)"`

to the `allow` array.

- [ ] **Step 2: Verify the skill is discoverable**

The skill should appear in Claude Code's skill list because it's in `.claude/skills/gsd-auto-uat/SKILL.md`. Verify the file exists and frontmatter is valid:

```bash
head -5 .claude/skills/gsd-auto-uat/SKILL.md
```

Expected output:
```
---
name: gsd-auto-uat
description: Automate human UAT verification...
allowed-tools: Bash(playwright-cli:*), Bash(curl:*), ...
---
```

- [ ] **Step 3: Commit**

```bash
git add .claude/settings.local.json .claude/skills/gsd-auto-uat/SKILL.md
git commit -m "feat(auto-uat): register skill permissions in settings"
```

### Task 7: End-to-end validation with Phase 10

**Files:**
- Read-only: `.planning/phases/10-service-refactoring/10-VERIFICATION.md`
- Read-only: `.planning/phases/10-service-refactoring/10-HUMAN-UAT.md`

- [ ] **Step 1: Dry-run the skill invocation**

Invoke `/gsd-auto-uat 10` and verify:
1. Mode correctly detected as `phase`
2. Phase directory found
3. VERIFICATION.md parsed — 3 items extracted
4. Classification: 2 automatable (Template Editor, Playoff Bracket), 1 skipped (Race Form + Calendar — Google Calendar)
5. Server starts (or detects running)
6. Browser opens
7. Tests execute with screenshots
8. AUTO-UAT.md written
9. HUMAN-UAT.md updated
10. Server cleaned up

- [ ] **Step 2: Verify Quick-Check mode**

Invoke `/gsd-auto-uat /admin/seasons /admin/teams` and verify:
1. Mode detected as `quick`
2. Two tests created
3. Each URL navigated, screenshot taken
4. No error pages detected
5. Terminal summary displayed

- [ ] **Step 3: Fix any issues found and commit**

```bash
git add .claude/skills/gsd-auto-uat/SKILL.md
git commit -m "fix(auto-uat): address issues from end-to-end validation"
```
