# Phase 88: Build/Release Unblockers, YAGNI Sweep, Doc-Conventions & Driver-Import Gap-Closure - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-18
**Phase:** 88-Build/Release Unblockers, YAGNI Sweep, Doc-Conventions & Driver-Import Gap-Closure
**Areas discussed:** CLEAN-01 Reality Check & Scope, Plan-Slicing & Wave-Sequencing, REL-01 + REL-02 Strategie, DRIV-01 + DRIV-02 Sequencing

---

## CLEAN-01 Reality Check & Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Downgrade auf "Verify-Only" | `./mvnw clean verify` + JaCoCo CSV als acceptance, kein Source-Edit. REQUIREMENTS.md update zu "resolved as Phase 80 JDT-cache false positive". | ✓ |
| Permanenter Guard via typed-witness | `.extracting(EntityRef::entityClass, type(Class.class))` o.ä. um JDT-Cache strukturell zu blockieren. | |
| Permanenter Guard via `@SuppressWarnings` | Targeted `@SuppressWarnings("unchecked")` mit Rationale-Kommentar. | |

**User's choice:** Downgrade auf "Verify-Only".
**Notes:** Findings vor Discussion: (a) `./mvnw clean test-compile` exit 0 in current workspace; (b) `.planning/milestones/v1.11-phases/80-openrewrite-integration/deferred-items.md` dokumentiert den Fehler bereits als RESOLVED (Eclipse JDT-Cache-Artefakt, kein javac-Fehler); (c) memory rule [[clean-maven-build-authority]] wurde *wegen* genau dieses Vorfalls formuliert. Die "Guard"-Optionen würden ein IDE-Problem im Source-Code adressieren, was die Lesson contradicts.

---

## Plan-Slicing & Wave-Sequencing

### Slicing-Frage

| Option | Description | Selected |
|--------|-------------|----------|
| 6 Plans, themen-gebundelt | Plan-01 CLEAN-01 verify-only · Plan-02 CLEAN-02+03 · Plan-03 REL-01 · Plan-04 DOCS-01 · Plan-05 DRIV-01+02 · Plan-06 REL-02. Atomare Commits, minimaler Test-Rewrite-Churn. | ✓ |
| 8 Plans, 1:1 zur REQ | Jeder REQ ein Plan. Verletzt deferred-debug-Empfehlung "sequence so test rewrite handles both contracts without churn" (2x DriverSheetImportServiceTest-Rewrite, 2x `@Disabled`-grep). | |
| 4 Plans, gröber gebundelt | CLEAN-Bundle, REL-Bundle, DOCS, DRIV-Bundle. CLEAN-Bundle mischt verify-only mit source-edit; REL-Bundle hat Dependency-Bruch in der Mitte (REL-02 braucht REL-01 *gemerged*). | |

**User's choice:** 6 Plans, themen-gebundelt.

### Wave-Frage

| Option | Description | Selected |
|--------|-------------|----------|
| 3 Waves | Wave-1 CLEAN-01 allein · Wave-2 Plans 02/03/04/05 parallel (disjunkte File-Surfaces) · Wave-3 REL-02. | |
| 4 Waves, vorsichtiger | Wave-1 CLEAN-01 · Wave-2 CLEAN-02+03 · Wave-3 REL-01+DOCS-01+DRIV parallel · Wave-4 REL-02. | |
| 2 Waves, aggressiver | Wave-1 CLEAN-01+DOCS-01 · Wave-2 alle anderen 4 parallel. | |
| Alles sequentiell | Keine parallelen Waves; jeder Plan einer nach dem anderen. | ✓ (Other) |

**User's choice (free text):** "keine parallelen Waves, alles sequentiell wegen schlechten Erfahrungen aus den letzten Umsetungsphasen".
**Notes:** Aligns mit [[wave-pause]] (Wave-Pause-Disziplin) und [[subagent-stability]] (Phase-60 Worktree-Watchdog Lessons). Plan-Reihenfolge im CONTEXT.md gemäß natürlicher Dependency-Ordering: CLEAN-01 → CLEAN-02+03 → REL-01 → DOCS-01 → DRIV-01+02 → REL-02 (REL-02 letzter, weil retroaktive Releases auf hardened workflow aufbauen sollten).

---

## REL-01 + REL-02 Strategie

### REL-01 Verification

| Option | Description | Selected |
|--------|-------------|----------|
| Synthetic `dry-run: true` workflow_dispatch input | `workflow_dispatch` mit `dry-run: boolean` Input; side-effecting Steps via `if: inputs.dry_run != true` gegated. Determine-Version + Idempotency-Guard laufen voll durch. | ✓ |
| Separate `release-dry-run.yml` Workflow | Copy-paste der ersten 5 Steps in eigenen Workflow. Drift-Risiko bei Änderungen an release.yml. | |
| Trust-on-Next-Master-Merge | Nur Logic-Review + Bash-Script-Tests; echter Release-Test ist der erste master squash-merge nach REL-01. | |

**User's choice:** Synthetic `dry-run: true` workflow_dispatch input.

### REL-02 Catch-up Mechanismus

| Option | Description | Selected |
|--------|-------------|----------|
| `gh release create --target <SHA>` Runbook | Pro Version `gh release create vX.Y.0 --target <SHA>` (remote tag + Release in einer Operation, kein lokales push); JAR-Build via `git worktree`; Docker-Push aus Worktree. Legacy-Tag-Delete via `gh api -X DELETE /repos/.../git/refs/tags/<tag>`. | ✓ |
| Throwaway `retroactive-release.yml` Workflow | Neuer einmaliger Workflow mit `version` + `target_sha` Inputs; nach REL-02 wieder löschen. | |
| Manuelle `git tag` via Push-from-Branch | Operator macht `git tag` + `git push origin <tag>` lokal. Verletzt [[no-local-git-tags]] — explizit nicht akzeptabel. | |

**User's choice:** `gh release create --target <SHA>` Runbook.
**Notes:** Respektiert die [[no-local-git-tags]]-Regel strikt (operator macht NIEMALS lokales `git tag` + push). Legacy-Tag-Delete via `gh api` ist reine remote-only Operation; per-Tag-Confirmation im Runbook für die destruktive Op (CLAUDE.md "Executing actions with care").

---

## DRIV-01 + DRIV-02 Sequencing

### Interne Reihenfolge

| Option | Description | Selected |
|--------|-------------|----------|
| Resolver erst, dann Layout-Gate | Schritt-1 DRIV-01 (resolver season-aware + 5 call sites + Test #16/#17 invertiert + neuer Fallback-Test) · Schritt-2 DRIV-02 (Layout-Gate + `TabPreview.usesGroups` + Template-Gate + GROUPS-with-missing-PhaseTeam-Test). Test-Rewrite einmalig pro Concern. | ✓ |
| Layout-Gate erst, dann Resolver | Warning-Noise verschwindet sofort; aber Test #16/#17 müssten 2x angefasst werden. Verletzt deferred-debug "sequence so test rewrite handles both contracts without churn". | |
| Atomar in einem Commit | Beide REQs in einem Commit. Weniger Test-Rewrite-Churn aber schwierige Bisect-Auflösung; gegen "atomic commits for atomic problems". | |

**User's choice:** Resolver erst, dann Layout-Gate.

### Legacy-Fallback `regularPhase == null`

| Option | Description | Selected |
|--------|-------------|----------|
| Parent-precedence beibehalten | Fall through zu Phase-66 D-06 parent-precedence + WARN-log. Preserve Phase-66 Semantik für legacy Saisons. | ✓ |
| Strikte Fehler-Semantik | `throw BusinessRuleException(...)`. Bricht pre-V4 legacy data. | |
| Suggest-Season-Aware-Resolver | Best-Effort `seasonPhaseService.findRegularPhase` + Operator-Choice in Preview. YAGNI. | |

**User's choice:** Parent-precedence beibehalten.

---

## Claude's Discretion

Areas where the planner picks within stated constraints:

- **CLEAN-03 utility shape** — `CommandLineRunner` Spring bean (`./mvnw exec:java`) vs. `main()`-style helper class. Planner picks closer-to-existing-pattern.
- **DRIV-02 template fallback** — `—` placeholder vs. hidden Group cell vs. "n/a" text. Planner picks.
- **REL-02 runbook format** — numbered list with embedded `gh`/`docker` commands vs. shell script template. Planner picks.
- **CLAUDE.md DOCS-01 paragraph wording** — exact prose; constraints: documents canonical `/gsd-<name>` form, deprecates `/gsd:<name>`, grep verification noted.
- **Test #7 enhancement for CLEAN-02 (b)** — only if JaCoCo delta after Plan-02 shows regression.
- **`docs/operations/release-runbook.md` placement** — new file vs. extending existing operations doc.

## Deferred Ideas

- Preemptive structural guard against JDT-cache regression on `BackupSchemaExclusionIT` (typed-witness / `@SuppressWarnings`) — rejected per [[clean-maven-build-authority]].
- Throwaway `retroactive-release.yml` Workflow für REL-02 — runbook is simpler and reusable.
- Strict `BusinessRuleException` für DRIV-01 wenn `regularPhase == null` — would break pre-V4 legacy data.
- Aggressive parallel waves — sequential execution preferred per user's recent multi-wave experience.
- PERF-related work — out of Phase 88 scope (Phases 89-91).
- UX-01 Google API error UX — Phase 91 stretch.
