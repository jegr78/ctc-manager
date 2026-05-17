# Phase 86: Test Wallclock Reduction - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-17
**Phase:** 86-Test Wallclock Reduction
**Areas discussed:** @DirtiesContext-Audit-Strategie, @DataJpaTest-Pilot-Scope, Wallclock-Messung & Baseline-Trust, Blocker-Fallback-Pfad

---

## @DirtiesContext-Audit-Strategie

### Audit-Modus

| Option | Description | Selected |
|--------|-------------|----------|
| Default-Remove + Random-Order-Verify | Treat each @DirtiesContext as tech debt; remove, verify 3× random seeds, keep if all green | ✓ |
| Konservativ: Per-File-Analyse zuerst | Identify shared-state source per class first, then decide | |
| Cluster-basiert: Sitegen-7 zuerst, Backup-3 separat | Tackle the sitegen homogeneous cluster first, backup ITs separately | |

### Verify-Loop

| Option | Description | Selected |
|--------|-------------|----------|
| 3 verschiedene Seeds, gleiche Phase | 3× `./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed={1234, 5678, 9999}` | ✓ |
| 1 Random-Run + voller verify -Pe2e am Ende | 1 run per removal, single final E2E gate | |
| Vollständige Surefire+Failsafe pro Annotation | `./mvnw verify` per annotation; strict but expensive | |

### Backup-ITs (BEFORE_EACH_TEST_METHOD trio)

| Option | Description | Selected |
|--------|-------------|----------|
| ImportLockService-Reset-Bean statt @DirtiesContext | Replace with `@AfterEach` lock-reset helper; biggest single-source win | ✓ |
| Konservativ: Annotation behalten mit Rationale-Kommentar | Keep with explanatory comment per PERF-01's "retained" path | |
| Class-Level statt Method-Level prüfen | Move from BEFORE_EACH to BEFORE_CLASS where viable | |

### Sitegen-Cluster (7 tests)

| Option | Description | Selected |
|--------|-------------|----------|
| Root-Cause-Fix: per-test SiteOutputDir + @TempDir | Bind `ctc.site.output-dir` to per-test `@TempDir`; one refactor drops all 7 annotations | ✓ |
| Iterativ: pro Test entfernen + verifizieren | One-by-one, miss the shared root cause | |
| Investigations-Spike zuerst | Diagnostic plan first to find shared state | |

---

## @DataJpaTest-Pilot-Scope

### Primary Pilot

| Option | Description | Selected |
|--------|-------------|----------|
| PhaseTeamRepositoryIT | Smallest schema, clearest like-for-like win | ✓ |
| SeasonPhaseGroupRepositoryIT | Mid-complexity (Phase + Group + Match aggregate) | |
| SeasonPhaseRepositoryIT | Richest D-22 magic-naming coverage; complex pilot | |

### Pilot Scope (after pilot lands clean)

| Option | Description | Selected |
|--------|-------------|----------|
| Strikt bei 1 Pilot, Rest in v1.12 | Minimal PERF-03 fulfillment, pattern doc only | |
| Opportunistisch: 2-3 weitere wenn time budget | Conditional on under-budget execution | |
| Alle 3 domänen-PhaseRepository-ITs konvertieren | PhaseTeam + SeasonPhase + SeasonPhaseGroup all converted | ✓ |

### Phase 58 Precedent-Comment Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Kommentar entfernen + Phase-86-Entscheidung in TESTING.md kodifizieren | Update conventions doc with the new standard | |
| Kommentar zu Historie-Hinweis umwandeln | Reformulate as historical note pointing to docs/test-performance.md | |
| Kommentar einfach mit Code entfernen | Drop with the @SpringBootTest annotation; commit message carries the story | ✓ |

### Auditing in @DataJpaTest

| Option | Description | Selected |
|--------|-------------|----------|
| @Import(JpaAuditingConfig.class) | Dedicated `@TestConfiguration` with `@EnableJpaAuditing` | ✓ |
| Per-Test entscheiden — Auditing default OFF | Accept default disabled, tests inject timestamps manually | |
| Investigations-Plan zuerst | Pre-check whether tests actually need auditing | |

---

## Wallclock-Messung & Baseline-Trust

### Baseline-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| Lokale Re-Baseline VOR Audit | 3× `time ./mvnw clean verify -Pe2e` on v1.11-master, median = Phase 86 baseline | ✓ |
| Phase-79-Zahl als Gate akzeptieren | Use 11m 11s directly without re-measurement | |
| CI-Baseline statt lokal | 3 GitHub-Actions runs on master before any change | |

### CI-Median-Methodik

| Option | Description | Selected |
|--------|-------------|----------|
| 3 Runs, strikter Median ohne Filter | Plain PERF-05 wording: 3 runs, median, done | |
| 5 Runs, Min+Max droppen, Median der 3 mittleren | Robust against cold-cache and runner noise | ✓ |
| 3 Runs, alle drei müssen unter 7m 50s sein | Hardest gate: worst-case threshold | |

### Gate-Hardware

| Option | Description | Selected |
|--------|-------------|----------|
| CI ist Source of Truth, lokal nur Indikator | CI median is the verdict; local = direction sense | ✓ |
| Lokal + CI gemeinsam, beide unter Schwelle | Both must clear ≤7m 50s | |
| Nur lokal, CI nur als Sanity-Check | Local matches v1.10 baseline hardware | |

### Context-Count-Messung (PERF-02)

| Option | Description | Selected |
|--------|-------------|----------|
| Spring `ContextLoadCountListener` + Logging | Custom `ApplicationContextInitializer` + AtomicInteger + marker file | ✓ |
| Spring TestContext Cache Stats per `@Endpoint` | Use TCF's built-in ContextCache stats via DEBUG logging | |
| Manuelles Zählen via @SpringBootTest-Grep | Static count + ContextConfiguration diff | |

---

## Blocker-Fallback-Pfad

### v1.12-Pfad-Format

| Option | Description | Selected |
|--------|-------------|----------|
| Top-3 strukturelle Hebel mit Kosten/Nutzen-Schätzung | Per-lever: description, Wallclock delta estimate, effort, risks | ✓ |
| Ein einziger empfohlener Pfad mit Begründung | Single recommended v1.12 phase | |
| Generische Spike-Empfehlung | Point to `/gsd:spike v1.12-test-performance` | |

### Failsafe-Singleton-Race (data/dev/backup-staging/)

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 86: Audit + Doku, Fix als v1.12-Empfehlung | Document as Top-1 v1.12 lever, don't refactor in Phase 86 | ✓ |
| Phase 86 öffnet per-fork staging-dir Plan | Tackle the race in Phase 86 itself | |
| Phase 86 nur @DirtiesContext + @DataJpaTest, ignoriert Staging | Strict scope, ignore staging-dir | |

### Realistic Expectation

| Option | Description | Selected |
|--------|-------------|----------|
| Realistic-Optimistic: ~20-25% (8m30s-9m), Blocker-Pfad als sicherer Fallback | Honest documentation as default outcome | ✓ |
| Optimistic: ~35-40% (6m30s-7m), Gate erreichbar | Aggressive assumptions, gate achievable | |
| Pessimistic: ~10-15% (9m30s-10m), Blocker-Doku als Hauptdeliverable | Phase 86 primary output is documentation | |

### Plan-Scope-Limit

| Option | Description | Selected |
|--------|-------------|----------|
| Soft Cap: ~6 Plans / 2 Waves | If trajectory shows gate unreachable by plan 3-4, plans 5-6 close with blocker doc | ✓ |
| Hard Cap: 4 Plans, fertig | Strict: re-baseline, audit, conversion, re-measure | |
| Kein Cap, fertig wenn alle PERFs erfüllt | Free-form, risk Phase-79-style 17 plans | |

---

## Claude's Discretion

- Exact wording and section structure of `docs/test-performance.md`
- Specific seed values within the (1234/5678/9999) family or substitutions thereof
- Location of `JpaAuditingConfig` (test-support package vs. domain.repository test root) — pick what matches existing test-support conventions

## Deferred Ideas

- Per-fork `data/dev/backup-staging/` refactor — Top-1 v1.12 lever
- Shared `@SpringBootTest` context strategy via explicit `@ContextConfiguration` classes — Top-2 v1.12 lever
- Testcontainers MariaDB reuse (`withReuse(true)`) — Top-3 v1.12 lever, relevant once MariaDB ITs exist
- Wider `@DataJpaTest` migration beyond the 3 Phase repository ITs (e.g., RaceRepository, DriverRepository, SeasonRepository)
- Spring TCF Cache stats DEBUG logging as an alternative or complement to the custom ContextLoadCountListener
