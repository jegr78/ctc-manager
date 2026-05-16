# Phase 77: Final UAT + JaCoCo Hold + Round-Trip Test + Documentation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-14
**Phase:** 77-final-uat-jacoco-hold-round-trip-test-documentation
**Areas discussed:** Round-Trip-IT shape & SHA-256 strategy, MariaDB execution path, Documentation layout & WIKI venue, Milestone closure scope

---

## Round-Trip-IT Shape & SHA-256 Strategy

### IT class shape (Q1)

| Option | Description | Selected |
|--------|-------------|----------|
| Extend in-place | Keep BackupRoundTripIT class. Existing 4 manifest tests stay. Add NEW @Test methods for the full round-trip. Matches the existing Javadoc's anchored intent. | ✓ |
| New class, archive old name | Rename current file to BackupManifestRoundTripIT. Create a fresh BackupRoundTripIT. | |
| New class alongside | Keep BackupRoundTripIT. Create BackupFullRoundTripIT alongside. | |

**User's choice:** Extend in-place (Recommended).
**Notes:** Honors the existing Javadoc anchor. Single test class, two `@Nested` profile classes.

### Sample entity selection (Q2)

| Option | Description | Selected |
|--------|-------------|----------|
| Volume + FK + self-FK | Race (largest table, batched writes), SeasonDriver (multi-FK composite), Team (self-FK 2-pass). | ✓ |
| Top 3 by row count | RaceResult, RaceLineup, Race. Biggest tables. | |
| All 22 operative entities | Sample one row per entity. Exhaustive. | |
| Pin to first row of every table | Per-table SHA-256 against the JSON array's first element. | |

**User's choice:** Volume + FK + self-FK (Recommended).
**Notes:** Hits the three most distinct restore code paths (batched insert, FK coercion, 2-pass NULL-then-UPDATE).

### Hash target (Q3)

| Option | Description | Selected |
|--------|-------------|----------|
| Per-row Jackson tree via backupObjectMapper | Query row pre-export, serialize via backupObjectMapper → byte[] → SHA-256. Re-query post-import, same serialization, same hash. | ✓ |
| Raw JSON byte-equal from the ZIP | Capture data/<entity>.json bytes from the ZIP. Re-export post-import. Compare byte-streams. | |
| Per-field assertion (no SHA-256) | assertThat(restored).usingRecursiveComparison().isEqualTo(original). | |

**User's choice:** Per-row Jackson tree via backupObjectMapper (Recommended).
**Notes:** Proves BOTH wire-shape invariance AND AuditingEntityListener bypass preserved created_at/updated_at.

### Row pick (Q4)

| Option | Description | Selected |
|--------|-------------|----------|
| First row by natural ordering | Smallest UUID via repository.findAll(Sort.by('id')).getFirst(). Composite-key first. Parent team with smallest id. | ✓ |
| Hard-coded UUIDs from dev fixture | Pin assertions to fixed UUIDs that DevDataSeeder/TestDataService writes. | |
| Random sample with seeded RNG | Pick 3 random rows per entity using Random(seed=77). | |

**User's choice:** First row by natural ordering (Recommended).
**Notes:** Deterministic across H2 + MariaDB, no fixture-shape coupling beyond "dev profile has data".

---

## MariaDB Execution Path

### Workflow venue (Q1)

| Option | Description | Selected |
|--------|-------------|----------|
| Testcontainers in main ci.yml | Mirror Phase 75 D-16. Honors the 'sacred workflow' memory. | ✓ |
| Touch mariadb-migration-smoke.yml | Add a step to the smoke workflow. Honors REQUIREMENTS literal wording but breaks sacred-workflow memory. | |
| Both — belt-and-suspenders | Testcontainers + a smoke step. More CI minutes. | |

**User's choice:** Testcontainers in main ci.yml (Recommended).
**Notes:** REQUIREMENTS QUAL-02 wording reframed in CONTEXT D-05.

### Test class shape for two engines (Q2)

| Option | Description | Selected |
|--------|-------------|----------|
| One class, two @Nested profile classes | BackupRoundTripIT with @Nested H2Tests + @Nested MariaDbTests. Mirrors SecurityIntegrationTest. | ✓ |
| Two ITs — H2 vs MariaDB | BackupRoundTripIT (H2) + BackupRoundTripMariaDbIT (Testcontainers). Mirrors Phase 75 Execute/Smoke separation. | |
| Single IT with @ParameterizedTest | @ParameterizedTest over profile names. | |

**User's choice:** One class, two @Nested profile classes (Recommended).
**Notes:** Pattern reference: SecurityIntegrationTest. DRY at the test level — engine difference is purely `@ActiveProfiles` + Testcontainers wiring.

### Engine coverage scope (Q3)

| Option | Description | Selected |
|--------|-------------|----------|
| Both engines run full round-trip + SHA-256 | @Nested H2 AND @Nested MariaDB each export→wipe→import + SHA-256. | ✓ |
| Both engines run row-count parity; SHA-256 H2-only | @Nested MariaDB asserts only row counts. Faster CI. | |
| MariaDB-only — H2 is covered by other ITs | Skip @Nested H2; rely on BackupImportExecuteIT. | |

**User's choice:** Both engines run full round-trip + SHA-256 (Recommended).
**Notes:** Catches dialect divergence (BINARY(16) UUID, LONGTEXT JSON, timestamp precision).

---

## Documentation Layout & WIKI Venue

### WIKI venue (Q1)

| Option | Description | Selected |
|--------|-------------|----------|
| docs/wiki/backup-and-restore.md | New docs/wiki/ folder. Versioned with code. | |
| GitHub Wiki (separate ctc-manager.wiki repo) | Push to GitHub Wiki repo. Native 'WIKI' semantics. | ✓ |
| Fold everything into docs/operations/import-runbook.md | Single source of truth. | |
| docs/site/ markdown for the static site | Public-facing audience. | |

**User's choice:** Custom free-text — "Es gibt doch schon ein Wiki im GitHub Projekt. Dieses soll dann entsprechend ergänzt werden" → resolved as GitHub Wiki venue.

### Wiki delivery mechanism (Q2 follow-up)

| Option | Description | Selected |
|--------|-------------|----------|
| Markdown-Draft in repo + Wiki-Push instruction | Draft committed to docs/wiki-drafts/. Operator pushes manually. | |
| Phase 77 pushes directly to Wiki repo | Subagent clones ctc-manager.wiki via gh CLI, commits, pushes. Fully automated. | ✓ |
| Repo-Draft only — manual Wiki editing | Markdown in repo. README cross-links. User edits Wiki by hand. | |

**User's choice:** Phase 77 pushes directly to Wiki repo.
**Notes:** Subagent in a Phase 77 plan step performs the clone + push via `gh` CLI.

### README depth (Q3)

| Option | Description | Selected |
|--------|-------------|----------|
| Short overview + Cross-links | ~30-50 lines. Cross-links to import-runbook.md and Wiki. | ✓ |
| Full content in README | Step-by-step + Recovery + Schema-Version inline. | |
| README pointer only | 5-10 lines linking out. Violates QUAL-05 'step-by-step in README'. | |

**User's choice:** Short overview + Cross-links (Recommended).
**Notes:** Section structure locked in D-09; placement after Features, before Quick Start.

### Screenshots (Q4)

| Option | Description | Selected |
|--------|-------------|----------|
| 3 key screenshots via playwright-cli | /admin/backup, Preview-Screen, Banner state. Embedded in Wiki via raw.githubusercontent URLs. | ✓ |
| README screenshots only, Wiki text-only | README stays text-only. Wiki text-only. | |
| No screenshots | Text-only README + Wiki. Phase Optionalität voll genutzt. | |

**User's choice:** 3 key screenshots via playwright-cli (Recommended).
**Notes:** Stored in .screenshots/77/. Embedded in Wiki via absolute GitHub raw URLs.

---

## Milestone Closure Scope

### Closure scope (Q1)

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 77 = tech only; Milestone-Closure separate | Phase 77 ships IT + Docs + HUMAN-UAT. /gsd-audit-milestone separately afterwards. | |
| Phase 77 does everything inc. Milestone-Audit | Phase 77 ALSO writes v1.10-MILESTONE-AUDIT.md + archives. | |
| Phase 77 makes IT + Docs + Audit, no Archive | Mid-way: Phase 77 writes audit, archive stays manual. | |

**User's choice:** Custom free-text — "Wir haben ja Phase 78 berreits eingeplant und abgeschlossen. Diese muss auf jeden Fall berücksichtigt werden. Danach wäre noch eine neue Phase 79 sinnvoll für Code Cleanup (Clean Code + Kommentar Ausdünnung) und Test Performance Optimierungen. Die neue Phase 79 soll dann als Absckuss des Meilensteins dienen".

**Resolution:** Phase 77 stays narrow (QUAL-01/02/04/05 only). Phase 78 (Docker Fix) is already shipped and counted in v1.10. A NEW Phase 79 will be added for: Code Cleanup + Test Performance Optimization. Phase 79 becomes the actual v1.10 milestone-closer.

### HUMAN-UAT scope (Q2)

| Option | Description | Selected |
|--------|-------------|----------|
| Narrow 77-HUMAN-UAT.md | 5-7 sign-off items. | |
| No HUMAN-UAT — only AUTO-UAT via playwright-cli | QUAL items are automated; AUTO-UAT verifies docs via playwright-cli. | ✓ |
| Mini HUMAN-UAT — only Wiki-Push verification | 1 item: 'User has pushed wiki + page is live'. | |

**User's choice:** No HUMAN-UAT — only AUTO-UAT via playwright-cli.
**Notes:** 77-AUTO-UAT.md replaces HUMAN-UAT; 5-step automated verification checklist defined in D-13.

### JaCoCo strategy (Q3)

| Option | Description | Selected |
|--------|-------------|----------|
| Minimum at 0.82 — measure + log, do not raise | pom.xml stays 0.82. Coverage measured + recorded. | ✓ |
| Measure + raise to 0.85 if buffer allows | Adaptive — raise minimum if measured ≥ 0.85. | |
| Measure — only raise if buffer ≥ 5% | Conditional: ≥ 87% → raise to 0.85; otherwise stay. | |

**User's choice:** Minimum at 0.82 — measure + log, do not raise (Recommended).
**Notes:** Honors `feedback_coverage_strategy` memory. Buffer documented in 77-AUTO-UAT.md.

### Version bump (Q4)

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 79 (Milestone-Closer) does the bump | Phase 77 leaves pom version untouched; Phase 79 bumps. | |
| Phase 77 does the bump, Phase 79 only cleanup | Phase 77 sets 1.10.0 after BUILD SUCCESS. | |
| Neither — separate release workflow afterwards | Both phases leave pom version untouched. /gsd-ship or release script bumps later. | ✓ |

**User's choice:** Neither — separate release workflow afterwards.
**Notes:** Current pom version is `1.8.0-SNAPSHOT` (stale). Phase 77 does NOT fix this; Phase 79 also does NOT bump it. A SEPARATE release workflow after Phase 79 archives the milestone handles the version tag.

---

## Claude's Discretion

- **CD-01:** `@Nested` class names (`H2RoundTripTests` + `MariaDbRoundTripTests` recommended).
- **CD-02:** Helper extraction — private methods first, extract to `RoundTripScenario` only if a second consumer emerges.
- **CD-03:** Banner-screenshot mechanism — `@TestComponent` flipping `ImportLockService.isLocked()` recommended; slow real import via second tab as fallback.
- **CD-04:** Wiki repo auth — assumed `gh auth` is set up; planner verifies in first plan step.
- **CD-05:** README placement — after "Features", before "Quick Start".
- **CD-06:** Screenshot resolution — `playwright-cli` defaults (1280×720).

## Deferred Ideas

- Phase 79 — Code Cleanup + Test Performance Optimization + v1.10 milestone-audit + archive (NEW phase, scope refined when `/gsd-phase add 79` is run).
- `pom.xml` version bump `1.8.0-SNAPSHOT` → release tag — separate release workflow AFTER Phase 79.
- Raising JaCoCo minimum above 0.82 — future milestone decision.
- Per-Saison Export/Import selectivity — v1.11+ (EXPORT-FUT-01 / IMPORT-FUT-01).
- SHA-256 checksum file (`manifest.sha256`) — v1.11+ (EXPORT-FUT-02).
- Verify-Only Import Mode — v1.11+.
- `/admin/backup/history` audit-viewer UI page — v1.11+.
- `@Scheduled` cleanup of `data/.import-backups/<ts>/` — v1.11+ if operational pain warrants.
- HUMAN-UAT for Phase 77 — D-13 replaces with AUTO-UAT; future operational concerns can fold into Phase 79 audit checklist.
- `RoundTripScenario` helper extraction — CD-02 default is "private methods"; extract only if a second consumer emerges.
