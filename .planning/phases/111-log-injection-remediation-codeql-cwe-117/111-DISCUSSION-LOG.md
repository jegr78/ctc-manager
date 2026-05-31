# Phase 111: Log-Injection Remediation (CodeQL CWE-117) - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-31
**Phase:** 111-log-injection-remediation-codeql-cwe-117
**Areas discussed:** Sanitizer Behaviour, Taint-Fix Strategy, Coverage Scope, API & Location

---

## Sanitizer Behaviour

| Option | Description | Selected |
|--------|-------------|----------|
| Alle Cntrl → '_' | All ISO control chars (incl. CR/LF/TAB) replaced by '_'. Visible attack trace, broad, CodeQL recognises the replaceAll barrier. | ✓ |
| Nur CR/LF entfernen | Only \r and \n deleted (no placeholder). Minimal, exactly the CWE-117 core, but other control chars pass through. | |
| Cntrl → '_' + Längen-Cap | As recommended plus truncation (~200 chars) against log-flooding. Risk: legitimate long values truncated. | |

**User's choice:** Alle Cntrl → '_' (recommended)
**Notes:** Replacement is 1:1 (no run-collapsing), no length cap. null → "null" to mirror slf4j default.

---

## Taint-Fix Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Am Log-Call wrappen | sanitize() applied directly at each log call. No business-logic mutation; CodeQL sees the barrier right before the sink → most reliable 0-alert guarantee. | ✓ |
| Einmal an der Eingangsstelle | Sanitize value once at entry (CSV row, form field) and pass through. Risky: value also feeds matching/queries — mutation would corrupt logic. | |
| Hybrid | Log-only values at source, multi-use values at call site. More precise but inconsistent pattern across 17 files. | |

**User's choice:** Am Log-Call wrappen (recommended)
**Notes:** "fix at source" in SEC-LOG-02 = real code fix vs. suppression, NOT entry-point mutation.

---

## Coverage Scope

| Option | Description | Selected |
|--------|-------------|----------|
| 29 + Geschwister-Args | The 29 flagged arguments plus any user-controlled sibling arg in the same log statement (same line, ~free). Minimal, CodeQL-verifiable diff. | ✓ |
| Nur strikt die 29 | Only the exactly-flagged arguments. Smallest diff, but an unflagged sibling arg remains a potential future alert. | |
| Voller Datei-Sweep | Proactively sanitize all user-controlled logs in the 17 files. Most sustainable, but large diff and "user-controlled" is partly subjective. | |

**User's choice:** 29 + Geschwister-Args (recommended)
**Notes:** No new query-filters / suppressions (SEC-LOG-03).

---

## API & Location

| Option | Description | Selected |
|--------|-------------|----------|
| org.ctc.util, static sanitize(Object) | New neutral top-level package (cross-cutting). static String sanitize(Object), null-safe (null→"null"), used via static import. | ✓ |
| org.ctc.domain.util (neben HexColor) | Reuse existing util package. Pragmatic but couples admin/backup to domain.util for a layer-neutral utility. | |
| String-only, kein Object-Overload | As recommended but static String sanitize(String) without Object overload. Prevents accidental over-use on arbitrary objects. | |

**User's choice:** org.ctc.util, static sanitize(Object) (recommended)
**Notes:** Class `LogSanitizer`; static import at call sites.

---

## Claude's Discretion

- Exact `replaceAll` regex/form inside `LogSanitizer` (researcher confirms the pattern CodeQL recognises as a `java/log-injection` barrier).
- Static-import vs. qualified call per file — terseness only, no behavioural impact.
- Verification (D-10): rely on the existing CodeQL run on milestone-branch PR #132 (no separate local CLI scan) — accepted as a sensible default at the closing checkpoint.

## Deferred Ideas

- `ISEMPTY-AUDIT` and other non-`log-injection` CodeQL/quality items — out of scope (this phase touches only `java/log-injection`).
- Broader proactive sanitization across the whole codebase (beyond the 17 flagged files) — rejected as scope creep; revisit only if future CodeQL runs surface new alerts.
