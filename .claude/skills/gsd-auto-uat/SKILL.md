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
