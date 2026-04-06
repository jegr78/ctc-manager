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
