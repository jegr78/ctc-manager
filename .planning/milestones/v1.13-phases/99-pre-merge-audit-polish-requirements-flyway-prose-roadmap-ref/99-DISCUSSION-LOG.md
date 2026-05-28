# Phase 99: Pre-merge audit-polish — Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-25
**Phase:** 99-pre-merge-audit-polish-requirements-flyway-prose-roadmap-ref
**Areas discussed:** FORUM-01 modal scope, 9N-VERIFICATION.md retrofill scope, Phase 93+95 VALIDATION.md frontmatter refresh, Plan structure, FORUM-01 backend YAGNI follow-up

---

## FORUM-01 "Create new Thread..." Modal Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Acceptance-Text korrigieren (Recommended) | REQ FORUM-01 in REQUIREMENTS.md so umschreiben, dass nur Link-existing-Thread + Operator-Workflow (Thread in Discord erstellen, dann linken) als shipped-Scope dokumentiert ist. Backend `createThread()` bleibt ungenutzt; Modal-UI fällt komplett aus dem v1.13-Scope. tech_debt-Item 5 verschwindet ohne UI-Build. | ✓ |
| Modal jetzt bauen | Modal-UI in `season-form.html` implementieren, `DiscordRestClient.createThread()` im Admin-UI verdrahten, IT + Playwright E2E nachziehen. Größerer Scope (eigener Plan), aber REQ-Acceptance bleibt unverändert; tech_debt-Item 5 wirklich geschlossen. | |
| Auf v1.14 verschieben | Modal nicht bauen, REQ-Text nicht ändern. tech_debt-Item bleibt offen, wird als v1.14-Backlog-Eintrag gebucht (Roadmap-Backlog). Phase 99 bleibt rein dokumentarisch, FORUM-01 bleibt 'satisfied (partial)'. | |

**User's choice:** Acceptance-Text korrigieren (Recommended)
**Notes:** Driver decision for D-01..D-03. Operator-workflow becomes the documented shipped-scope; in-app modal-build deferred to v1.14 backlog. Backend `DiscordRestClient.createThread()` stays in code (D-02) — deletion-vs-keep is a v1.14 decision, not Phase 99.

---

## 9N-VERIFICATION.md Retrofill Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Alle 6 retrofill (Recommended) | 92/94/95/96/97/98 — alle nach v1.12-DOCS-01-Precedent (`audit_method: retroactive` Frontmatter, Goal Achievement + Per-Dimension Verdict Table aus existierenden VALIDATION.md + SUMMARY.md + UAT-Ergebnissen abgeleitet). Konsistente Doc-Shape für die gesamte Milestone vor Archive. | ✓ |
| Subset ohne 95 | 5 Files retrofill (92/94/96/97/98), für 95 stattdessen `/gsd-validate-phase 95` Rollup-Stamp triggern (echter Verifier-Lauf, nicht retroactive). Saubere Trennung: 95 hat Substanz-Lücke (phase-level frontmatter stale), die anderen 5 nur Shape-Lücke. | |
| Gar kein Retrofill | tech_debt-Verdict akzeptieren wie er ist. Doc-Shape-Lücke bleibt offen; Phase 99 macht nur Prose-Fix + ROADMAP + Frontmatter. Spart Zeit, akzeptiert aber dass v1.13 anders dokumentiert ist als v1.12. | |

**User's choice:** Alle 6 retrofill (Recommended)
**Notes:** Driver decision for D-11..D-15. Reuses Phase 92 DOCS-01 template (`92-04-PLAN.md` lines 193+) verbatim. Phase 93 skipped from retrofill (existing `93-VERIFICATION.md` is authoritative); its VALIDATION.md frontmatter still gets refreshed via the Frontmatter plan.

---

## Phase 93 + 95 VALIDATION.md Frontmatter Refresh

| Option | Description | Selected |
|--------|-------------|----------|
| Beide inline editieren (Recommended) | 93-VALIDATION.md: `nyquist_compliant: true` (authoritativer Close lebt in 93-VERIFICATION.md PASS). 95-VALIDATION.md: `status: shipped` + `nyquist_compliant: true` (per-plan 95-04-VALIDATION.md ist BUILD SUCCESS). Schnellste Form; ein Commit pro File; kein Re-Verifier-Lauf. | ✓ |
| /gsd-validate-phase 95 ausführen + 93 inline | Für 95 echten Verifier-Lauf, der eine frische Rollup-Stamp produziert (saubere Audit-Trail). 93 bleibt inline (cosmetic), weil VERIFICATION.md authoritativ ist. Mehr Aufwand, aber 95 hat dann vollständigen automatisierten Close. | |
| Beide skippen | Frontmatter bleibt stale. Wir verlassen uns auf VERIFICATION.md (93) bzw. per-plan VALIDATION.md (95) als authoritativen Close. Audit dokumentiert das schon — kein neuer Commit nötig. | |

**User's choice:** Beide inline editieren (Recommended)
**Notes:** Driver decision for D-16..D-18. Both files get inline frontmatter-only edits; no re-verifier runs. Per-plan `95-04-VALIDATION.md` remains the authoritative substantive close for Phase 95.

---

## Plan-Aufteilung der Polish-Items

| Option | Description | Selected |
|--------|-------------|----------|
| Split nach Concern (Recommended) | 4-5 kleine Plans: 99-01 REQUIREMENTS.md Flyway-prose-fix (POST-01 V11→V12, FORUM-01 V12→V13 + ggf. Modal-Acceptance-Rewrite je nach GA-1) / 99-02 .planning/ROADMAP.md v1.13 Progress-Tabelle / 99-03 9N-VERIFICATION.md retrofill / 99-04 93+95 VALIDATION.md frontmatter. Saubere atomare Commits, einfache Rollbacks. | ✓ |
| Ein einziger Polish-Plan | 99-01 macht alles in einem großen Plan + einem Commit-Burst. Kürzere CONTEXT/Planning-Phase, schwerer zu reviewen, ein potenzieller Rollback verwirft alles. | |
| Zwei Plans (Doc-Shape + ggf. Modal) | 99-01 alle Doc-Shape-Polish-Items gebündelt; 99-02 nur falls GA-1='Modal jetzt bauen' für die UI-Implementation. Kompromiss — kleinerer Plan-Overhead, aber Doc-Plan ist immer noch groß. | |

**User's choice:** Split nach Concern (Recommended)
**Notes:** Driver decision for D-19..D-25. Initially resolved to 4 plans; user follow-up (see next section) added Plan 99-05 → final 5 plans. Each plan = one atomic commit on `gsd/v1.13-discord-integration`. Sequential inline, no subagents (CLAUDE.md "Subagent Rules"); execute-phase MUST use `--interactive` per `feedback_chain_inline_milestones.md`.

---

## FORUM-01 Backend YAGNI Follow-up (user-initiated)

**Context:** After the four gray-area decisions above were locked, the user re-opened D-02 with: "Create Thread kann weg - YAGNI". This upgraded the original *defer-to-v1.14* outcome to *delete-now-from-Phase-99*.

| Option | Description | Selected |
|--------|-------------|----------|
| Keep `DiscordRestClient.createThread()` for v1.14 | Defer the deletion-vs-keep decision to v1.14 (whichever phase picks up the modal-build, or a standalone YAGNI sweep). Phase 99 stays docs-only. | |
| Delete `createThread()` now (YAGNI) | Surgical removal in a new Plan 99-05: method + import in `DiscordRestClient.java`, entire `ThreadCreateRequest.java` DTO file, IT method + import in `DiscordRestClientIT.java`. Keep `Thread` record + list endpoints + `ThreadList` (consumed by `DiscordForumService.listThreads()`). | ✓ |

**User's choice:** Delete `createThread()` now (YAGNI)
**Notes:** Driver decision for revised D-02 + new D-23 (Plan 99-05). `grep -rn createThread src/` confirmed only 2 production-code occurrences (method + IT test) and 1 DTO file with no other consumer. Phase 99 is no longer pure-docs — Plan 99-05 changes `src/`, so the end-of-phase test gate (`./mvnw clean verify -Pe2e`) is genuinely enforcing. Expected test-count delta: −1 (the orphan IT method). Re-build path documented in CONTEXT.md "Deferred Ideas" — *if* FORUM-01 modal is ever built, the method comes back via TDD from git history.

---

## Claude's Discretion

- Exact ISO date stamps inside retrofilled `9N-VERIFICATION.md` frontmatter (planner picks; typically run-date).
- Exact wording of the FORUM-01 "operator-workflow" note in the rewritten REQ-66 acceptance text (planner drafts; must communicate (1) no in-app create UI exists, (2) workflow = create-in-Discord → link, (3) link-existing modal is the only UI surface).
- Per-phase SC count and dimension count inside each retrofilled VERIFICATION.md (planner derives from `v1.13-ROADMAP.md` + per-phase `9N-VALIDATION.md`).

## Deferred Ideas

- v1.14 (only if/when relevant): if FORUM-01 in-app create-modal is ever built, the deleted `DiscordRestClient.createThread()` method comes back via TDD; reference the pre-Phase-99 implementation in git history (Phase 96 FORUM-01 commits). Not auto-booked into v1.14 backlog — only schedule it when product priority warrants.
