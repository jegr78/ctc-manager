# Phase 111: Log-Injection Remediation (CodeQL CWE-117) - Context

**Gathered:** 2026-05-31
**Status:** Ready for planning

<domain>
## Phase Boundary

Close all 29 open CodeQL `java/log-injection` (CWE-117) alerts by routing every
user-controlled value through a central `LogSanitizer` before it reaches a log
statement. The taint path is broken **in source code at the log call site**, not
suppressed via `codeql-config.yml` `query-filters`. Scope is limited to the
`java/log-injection` rule — no other CodeQL rules, no broader logging refactor,
no new suppressions.

The 29 alerts span 17 files in four areas:
- `dataimport/` — `DriverMatchingService` (5), `CsvImportService` (4), `DriverSheetImportService` (2)
- `domain/service/` — `SeasonPhaseService` (2), `SeasonManagementService` (2), `MatchdayService` (2), `TeamManagementService`, `StandingsViewService`, `ScoringService`, `PlayoffService`, `FileStorageService` (1 each)
- `admin/controller/` — `TemplateEditorController` (2), `DriverSheetImportController`, `DriverController` (1 each)
- `backup/` — `BackupImportService`, `ImportLockedWriteRejector`, `BackupUploadExceptionHandler` (1 each)

</domain>

<decisions>
## Implementation Decisions

### Sanitizer Behaviour (SEC-LOG-01)
- **D-01:** `LogSanitizer` replaces **all ISO control characters** (`\p{Cntrl}`, including CR `\r`, LF `\n`, and TAB `\t`) with a single underscore `_`. Rationale: visible (the attack trace stays recognisable in logs), broad coverage beyond just newline forgery, and CodeQL reliably recognises a `replaceAll`-style barrier as breaking the taint path.
- **D-02:** Replacement is **1:1** — each control character becomes one `_` (no run-collapsing). No length cap / truncation (legitimate long values must not be silently cut).
- **D-03:** The unit test pins exact behaviour: CR/LF/TAB and other control chars → `_`; ordinary text (incl. unicode letters) passes through unchanged; `null` input → the literal string `"null"` (mirrors slf4j's default rendering of a null `{}` argument).

### Taint-Fix Strategy (SEC-LOG-02)
- **D-04:** Sanitize **at each log call site** — wrap only the user-controlled argument(s), e.g. `log.debug("Exact match for '{}'", sanitize(searchTerm))`. The "fix at source" wording in SEC-LOG-02 means a real code fix (vs. a config suppression), NOT mutating the value at its entry point.
- **D-05:** Do **not** sanitize-and-reassign at the value's entry point (CSV row field, form field). Those values are frequently reused for matching / repository queries (e.g. `searchTerm`, `row.psnId()`); mutating them would corrupt business logic. Sanitization touches only what is logged.

### Coverage Scope (SEC-LOG-02)
- **D-06:** Fix the **29 flagged arguments plus any sibling user-controlled argument in the same log statement** (same line — effectively free, prevents a future alert on an adjacent arg). Do **not** do a blanket sweep of all logs in the 17 files.
- **D-07:** No `codeql-config.yml` `query-filters` entries are added for these findings (SEC-LOG-03). No `@SuppressFBWarnings` / `// CodeQL FP` markers — these are real fixes, not accepted findings.

### API & Location
- **D-08:** New neutral top-level package **`org.ctc.util`** (the utility is cross-cutting across `admin`, `backup`, `dataimport`, `domain` — it does not belong under `domain`). Single class `LogSanitizer`.
- **D-09:** API: `public static String sanitize(Object value)` — null-safe (null → `"null"`), `Object` overload via `String.valueOf` so the single method covers all flagged arguments (most are already `String`). Used via **static import** (`import static org.ctc.util.LogSanitizer.sanitize;`) for terse call sites.

### Verification
- **D-10:** SEC-LOG-03 is verified via the **existing CodeQL run on the milestone-branch PR (#132)** — `codeql.yml` already runs on `pull_request` against `master`. No separate local CodeQL CLI scan required. The fix push triggers the re-scan; success = 0 open `java/log-injection` alerts.

### Claude's Discretion
- Exact regex / `replaceAll` form inside `LogSanitizer` (researcher should confirm the precise pattern CodeQL recognises as a barrier for `java/log-injection` — replacing newline chars via `String.replaceAll`/`replace` is the documented sanitizer).
- Whether to import `sanitize` statically per file or qualify it — terseness preference, no behavioural impact.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & Roadmap
- `.planning/REQUIREMENTS.md` §SEC-LOG — SEC-LOG-01..04 (acceptance criteria for this phase)
- `.planning/ROADMAP.md` — Phase 111 entry (goal, requirement mapping)

### SAST / CodeQL Conventions
- `docs/security/sast-acceptance.md` — single source of truth for SAST triage; note this phase adds **no** acceptance rows (real fixes, not accepted findings)
- `.github/codeql/codeql-config.yml` — `query-filters`; MUST NOT gain new entries for `java/log-injection` (SEC-LOG-03)
- `.github/workflows/codeql.yml` — CodeQL gate (`security-extended`, runs on PR vs `master`); the milestone-branch PR run is the SEC-LOG-03 verification mechanism
- `CLAUDE.md` §"CodeQL SAST (Code Scanning)" and §"Static Analysis (SpotBugs + find-sec-bugs)" — suppression discipline; the SpotBugs/find-sec-bugs gate must stay green (SEC-LOG-04)

### Live Alert Source
- GitHub Code Scanning alerts, rule `java/log-injection`, state=open (29 alerts) — `gh api repos/:owner/:repo/code-scanning/alerts` is the authoritative list of call sites to fix

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `org.ctc.domain.util.HexColor` — existing util-class style/precedent (static helpers). `LogSanitizer` follows the same static-utility shape but lives in the new neutral `org.ctc.util` package.
- slf4j is used everywhere via Lombok `@Slf4j`; all flagged sites use parameterized `{}` logging — no string concatenation to untangle, just wrap the argument.

### Established Patterns
- Logging convention (CLAUDE.md): `log.info()` for state changes, `log.debug()` for calculations, always parameterized `{}`. Sanitization must preserve the parameterized form — wrap the value, never pre-format the message string.
- Test naming Given-When-Then; unit test for `LogSanitizer` is a plain untagged unit test (no Spring context, no `@Tag`).
- No-comment-pollution rule applies: `LogSanitizer` needs no file-header/Javadoc clutter; one short WHY-comment only if a regex choice is non-obvious.

### Integration Points
- 17 source files across `dataimport/`, `domain/service/`, `admin/controller/`, `backup/{service,lock,exception}` each gain a static-import + sanitize-wrapped argument on flagged log lines.
- Grep-all-usages discipline (CLAUDE.md): the 29 alerts are arguments, not unique lines — planner must enumerate every flagged `(file:line, argument)` pair and check for sibling user-controlled args on the same statement (D-06).

</code_context>

<specifics>
## Specific Ideas

- Sanitized output uses `_` (underscore) as the single replacement char for every control character — deliberately visible so a forged-newline attempt shows up as `Guest__User_Admin` rather than vanishing.
- `LogSanitizer.sanitize(null)` → `"null"` so existing log output for genuinely-null arguments is unchanged.

</specifics>

<deferred>
## Deferred Ideas

- `ISEMPTY-AUDIT` (`.isEmpty()` vs `.isBlank()` semantic audit) and other non-`log-injection` CodeQL/quality items — explicitly out of scope; this phase touches only `java/log-injection`.
- Broader proactive sanitization of all user-controlled logs across the codebase (beyond the 17 flagged files) — rejected as scope creep (D-06); revisit only if future CodeQL runs surface new alerts.

None of the above belong in Phase 111.

</deferred>

---

*Phase: 111-log-injection-remediation-codeql-cwe-117*
*Context gathered: 2026-05-31*
