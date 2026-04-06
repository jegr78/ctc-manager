# GSD Auto-UAT Skill Design

**Date:** 2026-04-06
**Status:** Draft
**Location:** `.claude/skills/gsd-auto-uat/SKILL.md`

## Purpose

Automate human UAT verification items from GSD phases using `playwright-cli`. The skill reads structured test descriptions from VERIFICATION.md (or accepts freeform input), starts the dev server if needed, executes browser-based tests via `playwright-cli`, captures screenshot evidence, and writes results back into the GSD pipeline.

## Goals

1. Reduce manual testing effort for `human_needed` verification items
2. Provide screenshot evidence for every test result
3. Stay compatible with the existing GSD pipeline (HUMAN-UAT.md, verify-work)
4. Support standalone usage independent of GSD phases

## Non-Goals

- Replace truly manual tests (external APIs, production profiles, Docker Auth)
- Provide pixel-perfect visual regression testing
- Run in CI (this is a local development tool)

## Invocation Modes

### Mode 1: Phase-UAT

```bash
/gsd-auto-uat 10
```

Reads `human_verification` items from `.planning/phases/{phase}/*-VERIFICATION.md`. Each item becomes a test. Items requiring external dependencies are classified as `automatable: false` and skipped with a reason.

### Mode 2: Standalone

```bash
/gsd-auto-uat "Check that /admin/seasons lists all seasons correctly"
```

The quoted string is interpreted as a single test description. The LLM plans and executes the appropriate `playwright-cli` sequence.

### Mode 3: Quick-Check

```bash
/gsd-auto-uat /admin/teams /admin/seasons /admin/drivers
```

Each URL path gets an implicit test: navigate, verify no error page (no 500, no Whitelabel), take a screenshot.

## Architecture

The skill follows the GSD step-based pattern (like `verify-work.md`) with structured `<step>` blocks. Deterministic operations (server lifecycle, file I/O) are handled by shell commands. Non-deterministic operations (test interpretation, result evaluation) are handled by the LLM.

## Steps

### Step 1: `parse_args`

Parse `$ARGUMENTS` to determine mode:

| Input | Mode | Behavior |
|-------|------|----------|
| Number (e.g. `10`) | Phase-UAT | Load VERIFICATION.md from phase directory |
| Quoted string | Standalone | Treat as single test description |
| URL paths (starting with `/`) | Quick-Check | Navigate + screenshot each URL |

### Step 2: `server_ensure`

Ensure the dev server is running on port 9090.

```bash
# Check if already running
curl -sf http://localhost:9090/actuator/health > /dev/null 2>&1
```

**If not running:**

```bash
# Start in background with demo data
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo &
SERVER_PID=$!

# Poll until healthy (max 120s, every 3s)
for i in $(seq 1 40); do
  curl -sf http://localhost:9090/actuator/health > /dev/null 2>&1 && break
  sleep 3
done
```

Track `SERVER_STARTED_BY_US` flag for cleanup.

**Configuration:**

| Setting | Value | Source |
|---------|-------|--------|
| Port | 9090 | `application-dev.yml` |
| Profile | `dev,demo` | Demo data needed for template/graphic tests |
| Health endpoint | `/actuator/health` | Spring Boot Actuator |
| Startup timeout | 120s | Spring Boot + demo import takes ~40-60s |

### Step 3: `extract_tests`

**Phase-UAT mode:**

Read VERIFICATION.md frontmatter `human_verification` array. Each item has:

```yaml
- test: "Navigate to /admin/tools/template-editors..."
  expected: "Save stores the template and shows success flash..."
  why_human: "Requires running server..."
```

Classify each item by analyzing `why_human`:

| Keywords in `why_human` | Classification |
|------------------------|----------------|
| `external API`, `credentials`, `Google Calendar`, `docker`, `production profile` | `automatable: false` — skip with reason |
| `visual`, `running server`, `browser`, `form`, `rendering`, `layout` | `automatable: true` — execute |
| No matching keywords | `automatable: true` — default to attempting automation; if playwright-cli fails, mark as `failed` with error |

**Standalone mode:** The description becomes a single test with `automatable: true`.

**Quick-Check mode:** Each URL becomes an implicit test: `{test: "Navigate to {url}", expected: "Page loads without errors (no 500, no Whitelabel Error Page)", automatable: true}`.

### Step 4: `open_browser`

```bash
playwright-cli open http://localhost:9090
```

One browser session for all tests. Reuse across the full test run.

### Step 5: `execute_tests`

Sequential execution, one test at a time. For each automatable test:

1. **Plan:** LLM reads `test` and `expected` fields, generates a sequence of `playwright-cli` commands (goto, click, fill, snapshot, etc.)
2. **Execute:** Run each `playwright-cli` command, capturing snapshots after key interactions
3. **Screenshot:** `playwright-cli screenshot --filename=.screenshots/auto-uat/test-{id}-{step}.png`
4. **Evaluate:** LLM compares the snapshot/page state against `expected` — determines `passed` or `failed` with a brief evidence note
5. **Continue:** Move to the next test regardless of result (report-only, no stop-on-failure)

**Error handling:** If a `playwright-cli` command fails (timeout, element not found), the test is marked `failed` with the error message as evidence. Execution continues.

### Step 6: `write_reports`

**AUTO-UAT.md** (Phase-mode only):

Written to `.planning/phases/{phase_num}-{phase_name}/{phase_num}-AUTO-UAT.md`.

```yaml
---
phase: {phase_name}
executed: {ISO-8601 timestamp}
server_profile: dev,demo
total: {count}
passed: {count}
failed: {count}
skipped: {count}
---
```

Body contains per-test results with status, screenshot links, and evidence notes.

**HUMAN-UAT.md update** (Phase-mode, if file exists):

Update `result: [pending]` entries to:
- `result: passed (auto-verified {timestamp})` for passing tests
- `result: failed (auto-verified {timestamp}) — {reason}` for failing tests
- `result: skipped (not automatable) — {reason}` for skipped tests

Update the `Summary` section counts accordingly.

**Terminal output** (all modes):

```
## Auto-UAT Results

| # | Test | Status | Evidence |
|---|------|--------|----------|
| 1 | Template Editor save/reset | passed | Success flash displayed |
| 2 | Playoff Bracket View | passed | Teams and scores visible |
| 3 | Race Form + Calendar | skipped | Requires Google Calendar credentials |

2/3 passed, 0 failed, 1 skipped
Screenshots: .screenshots/auto-uat/
```

**Standalone/Quick-Check mode:** Terminal output only, screenshots in `.screenshots/auto-uat/`.

### Step 7: `server_cleanup`

```bash
# Only stop if we started it
if [ "$SERVER_STARTED_BY_US" = true ] && [ -n "$SERVER_PID" ]; then
  kill $SERVER_PID 2>/dev/null
  wait $SERVER_PID 2>/dev/null
fi
```

### Step 8: `close_browser`

```bash
playwright-cli close
```

## File Structure

```
.claude/skills/gsd-auto-uat/
  SKILL.md              # Skill definition with <step> blocks

.screenshots/auto-uat/  # Screenshot evidence (gitignored)
  test-1-save.png
  test-1-reset.png
  test-2-bracket.png

.planning/phases/10-service-refactoring/
  10-VERIFICATION.md    # Input (existing, read-only)
  10-HUMAN-UAT.md       # Updated with results
  10-AUTO-UAT.md        # New: detailed auto-test report
```

## Automatability Classification

The LLM classifies each `human_verification` item based on its `why_human` field. The classification is deterministic for common patterns:

| Pattern | Auto? | Reason |
|---------|-------|--------|
| Needs running server + browser | Yes | That is exactly what playwright-cli provides |
| Visual layout verification | Yes | Screenshots + snapshot inspection |
| Form submission + flash messages | Yes | playwright-cli fill/click/snapshot |
| Template rendering | Yes | Server with demo data renders templates |
| External API (Google Calendar) | No | Requires live credentials not available in dev |
| Docker/production profile | No | Requires different server configuration |
| Physical device testing | No | Cannot be browser-automated |

## Constraints

- **Port:** Hardcoded to 9090 (dev profile). No port-scanning or dynamic detection.
- **Profile:** Always `dev,demo` for maximum data availability.
- **Browser:** Single browser session, sequential tests. No parallelization.
- **Screenshots:** Always `.screenshots/auto-uat/`. Directory created if missing.
- **Timeout:** 120s for server startup, default playwright-cli timeouts for interactions.
- **No CI:** This is a local developer tool. Not designed for headless CI runs.

## Integration with GSD Pipeline

The skill is standalone but GSD-aware:

- **Input:** Reads the same VERIFICATION.md format that `gsd-verify-work` produces
- **Output:** Updates HUMAN-UAT.md in the format that `gsd-verify-work` reads back
- **No dependency:** GSD is not required. Standalone and Quick-Check modes work without any `.planning/` directory
- **Complementary:** Run `/gsd-auto-uat 10` after `/gsd-verify-work 10` produces `human_needed` status to automatically resolve automatable items
