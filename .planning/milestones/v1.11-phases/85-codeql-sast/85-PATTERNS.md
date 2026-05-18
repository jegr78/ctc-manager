# Phase 85: CodeQL SAST - Pattern Map

**Mapped:** 2026-05-17
**Files analyzed:** 4 NEW + 2 MODIFIED + 3 potential triage targets + 1 transient throwaway = 10
**Analogs found:** 8 / 10 (2 net-new file types — codeql-config.yml + transient SastMarker.java — have no in-repo analog; reference external GitHub canonical)

---

## File Classification

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `.github/workflows/codeql.yml` (NEW) | ci-workflow / standalone-job | event-driven (push/PR/cron/dispatch) → bash + gh-api | `.github/workflows/ci.yml` + `.github/workflows/mariadb-migration-smoke.yml` | exact (sibling-workflow + inline-bash gate-step pattern) |
| `.github/codeql/codeql-config.yml` (NEW) | static-analysis config | declarative YAML consumed by `codeql-action/init@v4` | NONE in repo — external `github/codeql/.github/codeql/codeql-config.yml` per RESEARCH C-01 | no in-repo analog (net-new file type); `config/spotbugs-exclude.xml` is the closest semantic sibling (filter-with-rationale) but different format (XML vs YAML) |
| `docs/security/sast-acceptance.md` (NEW) | docs / audit-log | markdown sections + per-finding tables (write-on-triage) | `docs/uat/UAT-02-legacy-season-smoke.md` (placement) + `config/spotbugs-exclude.xml` lines 215-247 (rationale-text reuse) | role-match (net-new `docs/<topic>/<file>.md` placement); rationale-text near-verbatim reusable |
| `.planning/phases/85-codeql-sast/85-VERIFICATION.md` (NEW, planner-created) | phase-verification log | markdown evidence-capture | `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-VERIFICATION.md` | exact (same template) |
| `CLAUDE.md` (MODIFIED) | project-conventions | docs sub-section + reference-list append | existing `### Static Analysis (SpotBugs + find-sec-bugs)` block lines 220-224 + `## References` block lines 159-168 | exact (literal sub-section sibling) |
| `renovate.json` (MODIFIED) | dependency-management config | JSON packageRule append | existing `packageRules[]` block (Phase-84) lines 18-107 | exact (GitHub-Actions packageRule pattern at lines 79-99) |
| `src/main/java/org/ctc/domain/service/FileStorageService.java` (POTENTIAL triage edit) | service / SSRF defense | single-line `// CodeQL FP:` comment above `validateHostname` method or `storeFromUrl` method | existing `// PATH_TRAVERSAL defense:` comments at lines 125-127 + `// SSRF defense:` comment block | exact (existing SpotBugs-rationale comments at the exact same lines) |
| `src/main/java/org/ctc/backup/service/BackupArchiveService.java` (POTENTIAL triage edit) | service / ZIP-Slip defense | single-line `// CodeQL FP:` comment above `assertEntrySafe` method | existing `// PATH_TRAVERSAL defense:` comment at lines 611-613 | exact (existing SpotBugs-rationale comment at the exact same line) |
| `src/main/java/org/ctc/backup/service/BackupImportService.java` (POTENTIAL triage edit) | service / ZIP-Slip defense | single-line `// CodeQL FP:` comment above ZIP-extraction call site | sibling pattern: `BackupArchiveService.java:611-613` | role-match (same defense pattern, different file) |
| `src/main/java/org/ctc/_sast_validation/SastMarker.java` (TRANSIENT — throwaway-branch only, NEVER lands) | deliberate-violation test stub | request-response (deliberate SQLi or path-traversal) | Phase-81 throwaway-branch precedent — no in-repo analog | role-match (Phase-81 D-13 template, not a code file to model) |

---

## Pattern Assignments

### `.github/workflows/codeql.yml` (NEW — ci-workflow, event-driven)

**Analog A:** `.github/workflows/ci.yml` lines 1-32 (workflow header, concurrency, permissions, checkout + setup-java)
**Analog B:** `.github/workflows/mariadb-migration-smoke.yml` lines 1-68 (standalone-workflow shape, inline-bash steps with `set -euo pipefail`, single-job pattern, `::error::` annotation)

**Header + concurrency + workflow-level permissions** (`ci.yml` lines 1-15 — copy structure, NOT the triggers):

```yaml
name: CI                                              # → "CodeQL SAST"

on:
  push:
    branches: [ master, main ]                        # → SCAFFOLD: workflow_dispatch: only; FINAL-ENABLE: push, pull_request, schedule, workflow_dispatch
  pull_request:
    branches: [ master, main ]

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}     # → copy verbatim (D-27)
  cancel-in-progress: true

permissions:
  contents: read                                      # → keep ONLY `contents: read` at workflow level (D-26; drop pull-requests:write — CodeQL needs security-events:write at job level only)
  pull-requests: write                                # → DROP for codeql.yml — SAST-03 mandates restrictive workflow-level
```

**Job-level permissions block** (NEW pattern not in ci.yml; planner authors per CONTEXT.md `<specifics>` block lines 290-297):

```yaml
jobs:
  analyze:
    name: Analyze (java-kotlin)
    runs-on: ubuntu-latest
    permissions:
      security-events: write
      contents: read
      actions: read
```

Rationale: `security-events: write` is the SARIF-upload privilege (RESEARCH lines 403-407). Job-level (not workflow-level) per Stream-4 mandate.

**Checkout + setup-java with Maven cache** (`ci.yml` lines 22-30 — copy near-verbatim):

```yaml
      - name: Checkout
        uses: actions/checkout@v6                     # → copy verbatim (D-22 floating-major)

      - name: Setup JDK 25
        uses: actions/setup-java@v5
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: 'maven'                              # → copy verbatim (D-26)
```

**Inline-bash with `set -euo pipefail` + `::error::` annotation pattern** (`mariadb-migration-smoke.yml` lines 73-118 — model the gate-step structure on this):

```yaml
      - name: Wait for /actuator/health = UP
        run: |
          set -u
          for i in $(seq 1 60); do
            ...
            if ! kill -0 "$APP_PID" 2>/dev/null; then
              echo "::error::App process died before becoming healthy"
              tail -200 app.log
              exit 1
            fi
            ...
          done
```

Apply this pattern to the SARIF-diff gate step:
- `set -euo pipefail` at the top
- `gh api ... --paginate --jq` for the alert fetch (pre-installed on `ubuntu-latest`, no install step needed per RESEARCH lines 247-251)
- `comm -23 <(echo "$HEAD") <(echo "$BASE")` for set-difference
- `echo "::error::"` annotation on fail, `exit 1`
- Conditional skip via `if: github.event_name != 'schedule'` (D-10)

**Build step pattern** (mirror `mariadb-migration-smoke.yml` line 70-71 `./mvnw package -DskipTests -B -q`):

```yaml
      - name: Build for CodeQL
        run: ./mvnw compile --no-transfer-progress -DskipTests -Dspring.profiles.active=dev
```

Per SAST-02: manual build (NOT `autobuild`) to preserve Lombok annotation processing + Playwright compile-scope dependency.

---

### `.github/codeql/codeql-config.yml` (NEW — static-analysis config)

**Analog:** NONE in this repo (no `.github/codeql/` directory exists yet — confirmed via `ls .github/`). Closest external canonical: `github/codeql/.github/codeql/codeql-config.yml` (cited in RESEARCH C-01 lines 157-158).

**Semantic sibling for rationale-comment pattern:** `config/spotbugs-exclude.xml` lines 215-247.

**Rationale-as-XML-comment pattern from `config/spotbugs-exclude.xml`** (lines 215-227 — reuse text verbatim, just translate to YAML comment + the new D-02-REVISED rule-id-only schema):

```xml
<!-- ========== Intentional-pattern suppressions (D-11) ========== -->

<!-- FileStorageService.storeFromUrl(): SSRF hostname blocklist (validateHostname method,
     lines 125-153) implements a blocklist via if/startsWith chains covering localhost,
     127.x, 10.x, 192.168.x, 169.254.x, and 172.16-31.x ranges.
     find-sec-bugs cannot recognize startsWith-chain blocklists as SSRF sanitizers
     (only allowlist-style sanitizers are recognized). The defense is intentional.
     See FileStorageService.java:87-103 and :125-153 for the full defense implementation. -->
<Match>
    <Class name="org.ctc.domain.service.FileStorageService"/>
    <Method name="storeFromUrl"/>
    <Bug pattern="SSRF_SPRING,SSRF"/>
</Match>
```

**Translate to codeql-config.yml** (per CONTEXT.md D-02 REVISED — rule-id-only, whole-codebase scope; per RESEARCH C-01 the `where:` field does NOT exist and the CONTEXT.md `<specifics>` skeleton lines 326-338 IS BROKEN — drop `where:`):

```yaml
name: ctc-manager-codeql-config
queries:
  - uses: security-extended
query-filters:
  # SSRF: FileStorageService.storeFromUrl uses startsWith hostname blocklist
  # (validateHostname, FileStorageService.java:125-153) which CodeQL cannot recognize
  # as a sanitizer. Defense-in-depth via Phase 81 SpotBugs gate (config/spotbugs-exclude.xml
  # SSRF_SPRING,SSRF entry) covers the same site. See docs/security/sast-acceptance.md.
  - exclude:
      id: java/ssrf
  # ZIP-Slip: BackupArchiveService.assertEntrySafe (BackupArchiveService.java:608-628)
  # + BackupImportService ZIP-extraction chain delegate to PathTraversalGuard.assertWithin
  # which CodeQL cannot trace. Defense-in-depth via Phase 81 SpotBugs gate
  # (config/spotbugs-exclude.xml PATH_TRAVERSAL_IN entries). See docs/security/sast-acceptance.md.
  - exclude:
      id: java/zipslip
  - exclude:
      id: java/path-injection
# Note: actual rule IDs (java/ssrf vs java/server-side-request-forgery) confirmed against
# the baseline workflow_dispatch run before final-enable commit.
```

**Schema invariant from RESEARCH C-01:** supported filter keys are `id, kind, precision, tags, problem.severity, security-severity, name`. NO `where:`, NO `paths:`. Per-path filtering for compiled Java is officially only achievable via `advanced-security/filter-sarif` (rejected per D-06).

---

### `docs/security/sast-acceptance.md` (NEW — docs / audit-log)

**Analog A:** `docs/uat/UAT-02-legacy-season-smoke.md` (placement pattern — Phase-83 QUAL-05 precedent for net-new `docs/<topic>/<file>.md`)

**Placement pattern from `docs/uat/UAT-02-legacy-season-smoke.md`** (line 1):

```markdown
# UAT-02: Legacy Season Visual Smoke (Phase 83 QUAL-05)
```

Format: `docs/<topic>/<artifact-id>-<short-name>.md`. For Phase 85: `docs/security/sast-acceptance.md` (no artifact-id prefix needed — it's the single canonical file for this topic, not a numbered series).

**Analog B:** `config/spotbugs-exclude.xml` lines 215-247 (rationale-text — reuse VERBATIM in the Markdown table `Rationale` column).

**Rationale-text reuse pattern** (`config/spotbugs-exclude.xml` lines 217-222):

```xml
<!-- FileStorageService.storeFromUrl(): SSRF hostname blocklist (validateHostname method,
     lines 125-153) implements a blocklist via if/startsWith chains covering localhost,
     127.x, 10.x, 192.168.x, 169.254.x, and 172.16-31.x ranges.
     find-sec-bugs cannot recognize startsWith-chain blocklists as SSRF sanitizers
     (only allowlist-style sanitizers are recognized). The defense is intentional.
     See FileStorageService.java:87-103 and :125-153 for the full defense implementation. -->
```

**Translate to a Markdown table row** in the SSRF section (per CONTEXT.md D-16 schema):

```markdown
| Alert-ID | Rule | Location | Bucket | Rationale | Source-Marker |
|----------|------|----------|--------|-----------|---------------|
| TBD-baseline | java/ssrf | FileStorageService.storeFromUrl:87 | suppressed | startsWith-chain hostname blocklist (validateHostname, lines 125-153) covering localhost, 127.x, 10.x, 192.168.x, 169.254.x, 172.16-31.x not recognized as sanitizer by CodeQL; intentional defense, unit-tested; defense-in-depth via Phase 81 SpotBugs SSRF_SPRING,SSRF entry | FileStorageService.java:86 |
```

**Top-of-document scaffold pattern** (per CONTEXT.md `<specifics>` lines 348-377 — use that block as the literal scaffold-commit content, including the SSRF / ZIP-Slip / BCrypt-N/A / Other sections per D-16 + D-18).

---

### `.planning/phases/85-codeql-sast/85-VERIFICATION.md` (NEW, planner-skeleton, executor-populated)

**Analog:** `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-VERIFICATION.md`

Phase-81 template carries: pre-flight checklist, per-requirement evidence pointers (SAST-XX → file/line/test), throwaway-branch-test capture section, final `./mvnw verify -Pe2e` log excerpt section. Phase-85 mirrors this template, with sections specialized for:
- SAST-01..SAST-05 structural-check evidence (yq + grep commands captured)
- SAST-06 throwaway-branch deliberate-violation evidence (`gh run view` first 30 lines + Security tab alert screenshot pointer)
- Baseline-triage decision table (per Phase-81-D-10 decision tree applied to live baseline findings)

Planner writes the skeleton; executor populates after each wave.

---

### `CLAUDE.md` (MODIFIED — project-conventions)

**Analog:** existing `### Static Analysis (SpotBugs + find-sec-bugs)` sub-section at lines 220-224.

**Existing pattern** (lines 220-224):

```markdown
### Static Analysis (SpotBugs + find-sec-bugs)

* **Gate:** `spotbugs-maven-plugin` 4.9.8.3 + `findsecbugs-plugin` 1.14.0 run on every `./mvnw verify` (Medium+HIGH findings block the build). No separate CI job — SpotBugs runs inside the existing `verify` step.
* **Suppressions:** Live in `config/spotbugs-exclude.xml`. Every `<Match>` entry MUST have an XML rationale comment with a code-cross-reference to where the intentional pattern lives. No `@SuppressWarnings("all")` ever — use targeted `@SuppressFBWarnings({"SPECIFIC_CODE"}, justification="...")` in source or a `<Match>` entry in the filter file.
* **`lombok.config` invariant:** `lombok.config` at project root sets `lombok.extern.findbugs.addSuppressFBWarnings=true`. Do NOT remove or modify the two SpotBugs-related lines without a new phase that re-baselines suppressions — removing them re-introduces ~40–80 `EI_EXPOSE_REP*` false positives from Lombok-generated entity getters.
```

**Pattern characteristics to mirror in the new `### CodeQL SAST (Code Scanning)` sub-section:**
- Heading style: `### <Tool> (<Purpose>)` — Phase-85 → `### CodeQL SAST (Code Scanning)`
- Bullet 1: `**Gate:**` clause naming the workflow/action versions, triggers, and what blocks the build
- Bullet 2: `**Suppressions:**` clause naming the config file + the invariant rule for how suppressions are recorded (parallel-edit rule — D-19 Update-on-Triage)
- Bullet 3: an invariant / Do-Not-Touch clause OR an acceptance-doc-pointer clause (per D-24 bullet 3, this is the `sast-acceptance.md` discovery bullet)
- Insert position: directly AFTER line 224 (end of SpotBugs sub-section), BEFORE the `---` separator at line 225 / before the next `##` block

**Existing References-block pattern** (lines 159-168):

```markdown
## References

* Design Spec: `docs/superpowers/specs/2026-03-26-ctc-manager-design.md`
* Scoring/Legs Spec: `docs/superpowers/specs/2026-03-29-scoring-legs-design.md`
* Release Management Spec: `docs/superpowers/specs/2026-04-03-release-management-design.md`
* Architecture: `.planning/codebase/ARCHITECTURE.md`
* Conventions: `.planning/codebase/CONVENTIONS.md`
* Stack: `.planning/codebase/STACK.md`
* Structure: `.planning/codebase/STRUCTURE.md`
* Testing: `.planning/codebase/TESTING.md`
```

**Pattern:** `* <Topic>: \`<path-to-md-or-yml-file>\`` — append per D-25:

```markdown
* SAST Acceptance: `docs/security/sast-acceptance.md`
* SAST Workflow: `.github/workflows/codeql.yml`
```

Insert at line 169 (after the existing `* Testing:` line) so the new entries are sibling-flat.

---

### `renovate.json` (MODIFIED — dependency-management config)

**Analog:** existing `packageRules[]` entries lines 79-99 (Playwright + eclipse-temurin GitHub-Actions / Docker pattern; SpotBugs detector-pack pattern at lines 79-88).

**Existing pattern — patch automerge** (line 102-106):

```json
{
  "description": "Patch updates automerge after CI passes — auditable via PR object (NOT branch automerge per D-20). Branch protection on master is the gating layer.",
  "matchUpdateTypes": ["patch"],
  "automerge": true,
  "automergeType": "pr"
}
```

**Existing pattern — manual-review-only via `dependencyDashboardApproval` + `automerge: false`** (lines 43-48):

```json
{
  "description": "Spring Boot major bumps require dashboard approval before PR creation — major bumps are intentional OpenRewrite migration triggers (Phase 80 D-08).",
  "matchPackageNames": ["/^org\\.springframework\\.boot:/"],
  "matchUpdateTypes": ["major"],
  "dependencyDashboardApproval": true,
  "automerge": false
}
```

**Phase-85 packageRule to append** (per CONTEXT.md D-29 — two new objects in the `packageRules[]` array, inserted BEFORE the catch-all patch rule at line 101-106):

```json
{
  "description": "GitHub CodeQL Action patch updates automerge after 3-day cooldown (Phase 85 D-29). GitHub manages safe rollouts under @v4.",
  "matchPackageNames": ["github/codeql-action"],
  "matchUpdateTypes": ["patch"],
  "automerge": true,
  "minimumReleaseAge": "3 days"
},
{
  "description": "GitHub CodeQL Action minor/major updates via Dependency Dashboard approval (Phase 85 D-29) — minor bumps may add new security-extended queries that introduce new findings.",
  "matchPackageNames": ["github/codeql-action"],
  "matchUpdateTypes": ["minor", "major"],
  "dependencyDashboardApproval": true
}
```

Schema invariant: comma-separated objects, double-quoted keys, no trailing comma after the last element. Keep the description-field convention (every rule has a `"description"` field with phase-reference + rationale — see lines 20, 25, 30, etc.).

---

### `src/main/java/org/ctc/domain/service/FileStorageService.java` (POTENTIAL triage edit)

**Analog:** existing `// SSRF defense:` block at lines 125-127 (already in this same file — the comment block above `validateHostname`).

**Existing comment pattern** (lines 125-127):

```java
// SSRF defense: find-sec-bugs cannot recognize startsWith-chain hostname blocklists as
// sanitizers. This method is the suppressed sanitizer. See config/spotbugs-exclude.xml
// FileStorageService SSRF_SPRING,SSRF entry for the corresponding suppression rationale.
private void validateHostname(String sourceUrl) {
```

**Phase-85 CodeQL FP marker pattern to add** (per D-03 fixed format — ONE line, directly above `storeFromUrl` at line 86 OR above `validateHostname` at line 128; pick the line CodeQL flags):

```java
// CodeQL FP: java/ssrf — startsWith-chain hostname blocklist (validateHostname, lines 128-159) not recognized as sanitizer; see docs/security/sast-acceptance.md
public String storeFromUrl(String subDir, UUID entityId, String sourceUrl, String filename) throws IOException {
```

Coexists with the existing `// SSRF defense:` block — does NOT replace it. The existing comment documents the SpotBugs suppression; the new `// CodeQL FP:` marker documents the CodeQL suppression. Both layers preserve the defense-in-depth rationale per D-02-REVISED rationale #1.

---

### `src/main/java/org/ctc/backup/service/BackupArchiveService.java` (POTENTIAL triage edit)

**Analog:** existing `// PATH_TRAVERSAL defense:` block at lines 611-613 (same file — comment block above `assertEntrySafe`).

**Existing comment pattern** (lines 611-613):

```java
// PATH_TRAVERSAL defense: PathTraversalGuard.assertWithin() is the sanitizer; find-sec-bugs
// cannot trace the defense through the delegated utility call. See config/spotbugs-exclude.xml
// BackupArchiveService PATH_TRAVERSAL_IN entry for the corresponding suppression rationale.
private static void assertEntrySafe(ZipEntry entry, Path stagingRoot,
        int currentEntryCount, long currentInflatedBytes) {
```

**Phase-85 CodeQL FP marker pattern to add** (per D-03 — ONE line directly above the existing `// PATH_TRAVERSAL defense:` block, or directly above the `assertEntrySafe` method signature; pick the line CodeQL flags):

```java
// CodeQL FP: java/zipslip — PathTraversalGuard.assertWithin delegation not traceable by CodeQL; see docs/security/sast-acceptance.md
// PATH_TRAVERSAL defense: PathTraversalGuard.assertWithin() is the sanitizer; find-sec-bugs
// cannot trace the defense through the delegated utility call. See config/spotbugs-exclude.xml
// BackupArchiveService PATH_TRAVERSAL_IN entry for the corresponding suppression rationale.
private static void assertEntrySafe(ZipEntry entry, Path stagingRoot,
        int currentEntryCount, long currentInflatedBytes) {
```

Rule-id may be `java/path-injection` instead of `java/zipslip` — confirm against the live baseline scan before committing the marker.

---

### `src/main/java/org/ctc/backup/service/BackupImportService.java` (POTENTIAL triage edit)

**Analog:** `BackupArchiveService.java:611-613` (sibling-file, same defense pattern — but NOTE: BackupImportService does NOT currently have a `// PATH_TRAVERSAL defense:` comment block; the comment block exists only in BackupArchiveService).

**Phase-85 CodeQL FP marker pattern to add** (per D-03 — single-line, above the ZIP-extraction call site near `zf.getEntry(entryPath)` at line 672, or wherever CodeQL flags the path-injection sink):

```java
// CodeQL FP: java/path-injection — assertEntrySafe + PathTraversalGuard.assertWithin defense not traceable; see docs/security/sast-acceptance.md
ZipEntry entry = zf.getEntry(entryPath);
```

Rule-id determined by live baseline. If multiple sites in this file flag, ONE marker per finding (not one marker per file).

---

### `src/main/java/org/ctc/_sast_validation/SastMarker.java` (TRANSIENT — throwaway-branch only)

**Analog:** Phase-81 D-13 throwaway-branch precedent (referenced in CONTEXT.md D-14 and RESEARCH); no in-repo Java analog because the file lives only on `throwaway/sast-06-validation` and is deleted before merge.

**Pattern (deliberate-violation stub — planner picks SQLi vs path-traversal):**

```java
package org.ctc._sast_validation;

// SAST-06 verification stub — DELIBERATE violation for CodeQL gate verification.
// NEVER lands on gsd/v1.11-tooling-and-cleanup or master. See 85-VERIFICATION.md for procedure.
public final class SastMarker {
    public static String unsafeQuery(java.sql.Connection conn, jakarta.servlet.http.HttpServletRequest req) throws java.sql.SQLException {
        String sql = "SELECT * FROM x WHERE id = " + req.getParameter("id"); // java/sql-injection
        try (var st = conn.createStatement(); var rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
```

**Procedure invariant per CONTEXT.md D-14:** transient branch + draft PR + capture gate-step `exit 1` + close PR + delete branch. NO commit lands on the milestone branch. Verified by post-test `git log --all --oneline | grep _sast_validation` returns nothing on the milestone branch.

---

## Shared Patterns

### Pattern S-1: Inline-bash `set -euo pipefail` + `::error::` annotation

**Source:** `.github/workflows/mariadb-migration-smoke.yml` lines 73-118
**Apply to:** `codeql.yml` SARIF-diff gate step (single inline-bash block, ~20-25 lines, per D-06)

```yaml
      - name: Wait for /actuator/health = UP
        run: |
          set -u
          for i in $(seq 1 60); do
            if curl -fsS http://localhost:9091/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
              echo "App healthy after ${i} polls"
              exit 0
            fi
            ...
          done
          echo "::error::App did not become healthy within 5 minutes"
          tail -200 app.log
          exit 1
```

**Adapt for SARIF-diff gate** (per CONTEXT.md `<specifics>` lines 379-408 — refined by planner):

```yaml
      - name: Gate on new HIGH/CRITICAL security alerts
        if: github.event_name != 'schedule'
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          set -euo pipefail
          # ... gh api fetch + jq projection + comm -23 set-difference + ::error:: on fail
```

Conditional skip on `schedule` event per D-10. `GH_TOKEN: ${{ github.token }}` is the standard `gh` auth pattern for in-workflow API calls.

---

### Pattern S-2: Rationale-as-comment with code-cross-reference

**Source:** `config/spotbugs-exclude.xml` lines 215-247 (XML comments above every `<Match>` entry) AND in-source `// <pattern> defense:` blocks (FileStorageService.java:125-127, BackupArchiveService.java:611-613)
**Apply to:** every `codeql-config.yml` `query-filters` entry (YAML comment) AND every `// CodeQL FP:` source marker AND every `sast-acceptance.md` table row

**Existing XML pattern** (spotbugs-exclude.xml lines 240-247):

```xml
<!-- BackupArchiveService path-traversal defenses: assertEntrySafe() (lines 608-623) delegates
     to PathTraversalGuard.assertWithin() which calls toAbsolutePath().normalize().startsWith().
     find-sec-bugs cannot trace the defense through the delegated utility class.
     See BackupArchiveService.java:608-623 and PathTraversalGuard.java for the defense. -->
<Match>
    <Class name="org.ctc.backup.service.BackupArchiveService"/>
    <Bug pattern="PATH_TRAVERSAL_IN"/>
</Match>
```

**Three-layer translation for Phase 85:**

1. YAML comment in `codeql-config.yml`:
   ```yaml
   # ZIP-Slip: BackupArchiveService.assertEntrySafe (BackupArchiveService.java:608-628) delegates
   # to PathTraversalGuard.assertWithin which CodeQL cannot trace. Defense-in-depth via Phase 81
   # SpotBugs gate (config/spotbugs-exclude.xml PATH_TRAVERSAL_IN entry). See docs/security/sast-acceptance.md.
   - exclude:
       id: java/zipslip
   ```
2. Source marker in `BackupArchiveService.java`:
   ```java
   // CodeQL FP: java/zipslip — PathTraversalGuard.assertWithin delegation not traceable; see docs/security/sast-acceptance.md
   ```
3. Table row in `docs/security/sast-acceptance.md`:
   ```markdown
   | TBD-baseline | java/zipslip | BackupArchiveService.assertEntrySafe:614 | suppressed | PathTraversalGuard.assertWithin delegation not traceable by CodeQL; defense via toAbsolutePath().normalize().startsWith(); Phase 81 SpotBugs PATH_TRAVERSAL_IN co-suppression | BackupArchiveService.java:611 |
   ```

D-19 Update-on-Triage discipline: ALL THREE must change in the same commit. No partial-write.

---

### Pattern S-3: `gh api` with `--paginate --jq` projection

**Source:** RESEARCH lines 247-251 + CONTEXT.md `<specifics>` lines 388-398 (no in-repo `gh api` usage in existing workflows; pre-installed on `ubuntu-latest`)
**Apply to:** SARIF-diff gate step in `codeql.yml`

```bash
gh api -X GET "repos/${OWNER_REPO}/code-scanning/alerts" \
  -f state=open -f severity=critical,high \
  --paginate --jq '
    .[] | select(.dismissed_at == null
      and (.rule.security_severity_level // "" | IN("high","critical")))
    | "\(.rule.id)|\(.most_recent_instance.location.path)"
  '
```

Per D-28: key on `(rule.id, most_recent_instance.location.path)` — commit-sha-agnostic; strict-filter `state=open AND dismissed_at=null AND security_severity_level IN("high","critical")`. Use `--paginate` to avoid silent truncation at the default page size of 30.

---

### Pattern S-4: Per-finding triage table (Alert-ID + Rule + Location + Bucket + Rationale + Source-Marker)

**Source:** CONTEXT.md D-16 schema
**Apply to:** `docs/security/sast-acceptance.md` — every `## <Pattern>` section (SSRF, ZIP-Slip, Other) has this table; BCrypt-N/A section is text-only per D-18

```markdown
| Alert-ID | Rule | Location | Bucket | Rationale | Source-Marker |
|----------|------|----------|--------|-----------|---------------|
| #1234    | java/ssrf | FileStorageService.storeFromUrl:87 | suppressed | <reuse spotbugs-exclude.xml rationale text verbatim with file:line cross-reference> | FileStorageService.java:86 |
```

Buckets per D-16: `fixed` / `suppressed` / `accepted`. Alert-ID populated post-baseline-scan (UI alert number); scaffold-commit table rows use `TBD-baseline` placeholder.

---

### Pattern S-5: Conventional Commits with phase scope

**Source:** CLAUDE.md `## Git Workflow > Commits` lines 102-118 + recent log (e.g. `04ef5387 docs(84): Phase 84 close ...`)
**Apply to:** every Phase-85 commit on `gsd/v1.11-tooling-and-cleanup`

```
feat(85): scaffold CodeQL workflow (workflow_dispatch only, gate disabled)
chore(85): suppress java/ssrf on FileStorageService (FP rationale)
fix(85): triage <rule-id> on <ClassName>
feat(85): activate CodeQL gate on push + pull_request
docs(85): update sast-acceptance.md with baseline alert IDs
```

Per CONTEXT.md `<specifics>` line 425 — final phase-verification commit and the gate-activation commit both use `feat(85):` prefix.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `.github/codeql/codeql-config.yml` | static-analysis config | declarative YAML | Net-new file type; no `.github/codeql/` dir exists in repo. Closest semantic sibling (`config/spotbugs-exclude.xml`) provides the rationale-comment pattern but not the YAML schema. Reference external canonical `github/codeql/.github/codeql/codeql-config.yml` per RESEARCH C-01. |
| `src/main/java/org/ctc/_sast_validation/SastMarker.java` | deliberate-violation test stub | request-response (SQLi) | Transient throwaway-branch artifact; no in-repo analog by design. Phase-81 D-13 throwaway-branch procedural template applies; no Java code pattern to model on. |

For both files, planner should reference the RESEARCH.md sections rather than copy in-repo patterns.

---

## Metadata

**Analog search scope:**
- `.github/workflows/` (4 existing workflow files — ci.yml, release.yml, deploy-site.yml, mariadb-migration-smoke.yml)
- `.github/codeql/` (does not exist — confirmed)
- `config/spotbugs-exclude.xml` (full file — Phase-81 suppression file with rationale-comment pattern)
- `docs/` (uat/, security/ does not exist yet — confirmed)
- `renovate.json` (full file — Phase-84 packageRules)
- `CLAUDE.md` (Static Analysis section + References section)
- `src/main/java/org/ctc/domain/service/FileStorageService.java` (lines 80-160 — SSRF defense + existing `// SSRF defense:` comment block)
- `src/main/java/org/ctc/backup/service/BackupArchiveService.java` (lines 595-630 — `assertEntrySafe` + existing `// PATH_TRAVERSAL defense:` comment block)
- `src/main/java/org/ctc/backup/service/BackupImportService.java` (grep-only, ZIP-extraction sites)
- `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/` (VERIFICATION.md analog reference)

**Files scanned:** 9 in-repo + 1 external canonical (`github/codeql/.github/codeql/codeql-config.yml` per RESEARCH cite)

**Pattern extraction date:** 2026-05-17
