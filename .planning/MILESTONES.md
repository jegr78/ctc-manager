# Milestones

## v1.13 Discord Integration & Carry-Forwards (Shipped: <YYYY-MM-DD>)

**Phases completed:** 7 phases (92-98), 27 plans (incl. 3 added in 2026-05-25 re-open: 98-05/06/07), 27/27 requirements satisfied
**Tests:** 1635 surefire + 609 failsafe (494 IT + 115 Playwright E2E) — **2244 total**, all green; JaCoCo line coverage **88.99 %** (gate 82 %, Phase-92 baseline 88.88 %, Δ +0.11 pp; v1.12 baseline 88.44 %, Δ +0.55 pp)
**Timeline:** 6 days (2026-05-20 → 2026-05-25)
**Branch:** `gsd/v1.13-discord-integration` (PR #130)
**Final-gate CI:** TBA (lands post-`/gsd-complete-milestone v1.13` + squash-merge); SpotBugs 0 BugInstance; CodeQL gate-step exit 0
**Audit verdict:** TBA (`v1.13-MILESTONE-AUDIT.md` lands post-`/gsd-complete-milestone v1.13`); Nyquist scoreboard 7/0/0 (Phases 92-98)

**Key accomplishments:**

- Phase 92 — Carry-Forwards & Cleanup: UX-01 typed-catch parity on CsvImportController; COV-01 JaCoCo recovery to 88.88 %; CLEAN-01 grep-predicate tightening; DOCS-01 retroactive 89/90/91-VERIFICATION.md; BOOK-01 v1.12-REQUIREMENTS.md flip
- Phase 93 — Discord Foundation: DiscordRestClient + DiscordWebhookClient + sealed DiscordApiException (4 permits) + DiscordRateLimitInterceptor + DiscordEmojiCache + Flyway V8 discord_global_config + /admin/discord-config (4 test buttons)
- Phase 94 — Team Roles + Match Channel Lifecycle: CHAN-01 Flyway V9 teams.discord_role_id; CHAN-02 Flyway V10 matches.discord_* + Create-Channel button + permission-audit; CHAN-03 Archive-Modal with year-category regex + 50-channel limit
- Phase 95 — Match Channel Posts: POST-01 Flyway V11 discord_post + DiscordPostService.postOrEdit + /admin/discord/posts listing; POST-02 Team Cards; POST-03 Settings + Lineups; POST-04 Match Results with stale-detection; POST-05 Schedule embed with auto-edit
- Phase 96 — Provisional Graphic + Forum Threads: GRAFX-01 ProvisionalScoresGraphicService; FORUM-01 Flyway V12 seasons.discord_*_thread_id + Link-existing-Thread modal; FORUM-02 race-result forum-thread post with `?thread_id=` and auto-unarchive
- Phase 97 — Matchday-Level Posts: POST-06 Match Preview Announcement with auto-edit hook on streamLink/teaser change; POST-07a Match Day Results + POST-07b Power Rankings on Matchday-Detail; POST-08 phase-aware Standings + StandingsGraphicService + V14 phase_id FK migration
- Phase 98 — Polish + E2E + Docs + Close (7 plans total — 98-01..98-07): E2E-01 DiscordFullMatchdayLifecycleE2ETest (8-stage Mega-Walkthrough, WireMock-backed); DOCS-02 operator runbook expanded with §§ 1.9 / 2.3 / 6 / 7 + 8 app-UI screenshots + UAT Stages 14+15 procedure; DOCS-03 README + Wiki + MILESTONES + REQUIREMENTS + STATE pre-merge bookkeeping; mobile-viewport `.card` overflow fix (ROADMAP Erfolgskrit 6) including in-milestone polish on `.inline-form` + `.card > table` overflow; Plan 98-04 Schedule-Embed `inline:false` layout polish (Deferred Items § ui_debt closed); **Plan 98-05 POST-09 MATCHDAY_PAIRINGS** hybrid Markdown+PNG on Announcement-Channel (Flyway V15 pick_deadline + scheduled_weekend + template field, `{{ctcEmoji}}` resolution via DiscordEmojiCache long-form, operator-driven Re-Post + Update); **Plan 98-06 POST-10 MATCHDAY_SCHEDULE** pure-multipart PNG sibling button (no schema migration, MAX(match,race) stale-detection, operator-driven, no AFTER_COMMIT hook per D-98-AUTO-1); **Plan 98-07** Bundle-Verify + Live-UAT-Re-Run (Stages 5c+14+15 PASS, all same-messageId PATCH verified) + REQUIREMENTS/ROADMAP/MILESTONES/STATE/runbook pre-merge bookkeeping

**Deferred to next milestone (acknowledged at close):**

- Touch-Target 44 px Audit on `.btn` (CLAUDE.md UI polish; affects 100+ button callsites — too broad for v1.13 polish-sweep)
- 429-rate-limit-path in Mega-Walkthrough E2E (Phase-93 DiscordRateLimitInterceptorIT already covers; DISC-FUTURE ticket if operator-use-case pressure)
- Discord-Setup-Walkthrough.md Wiki-Subseite with annotated portal screenshots (v1.13 scope: text-only portal steps; v1.14 if operator wants portal screenshots)
- Race.dateTime-Trigger for SCHEDULE-Auto-Edit + MATCH_PREVIEW-Auto-Edit (Phase-95 D-95-04a + Phase-97 D-97-PREV-1a deferral)
- K-von-N Settings/Lineups-Posting (Phase-95 deferred)
- /admin/discord/posts Bulk-Re-Post-Button (Phase-95 deferred)

**Post-merge self-resolving (not tech debt):**

- v1.13 milestone PR squash-merge to master (CI release workflow handles `v1.13.0` tag + GitHub Release + Docker images — no local `git tag` per CLAUDE.md "No Local Git Tags")

Known deferred items at close: see `STATE.md` Deferred Items + `v1.13-MILESTONE-AUDIT.md` (lands post-`/gsd-complete-milestone v1.13`).

---

## v1.12 Driver-Import Gap-Closure & Test Performance Round 2 (Shipped: 2026-05-20)

**Phases completed:** 0 phases, 0 plans, 0 tasks

**Key accomplishments:**

- (none recorded)

---

## v1.12 Driver-Import Gap-Closure & Test Performance Round 2 (Shipped: 2026-05-20)

**Phases completed:** 4 phases (88-91), 15 plans, 15/15 requirements satisfied (14 must-have + 1 stretch — UX-01 resolved IN per Phase 91 D-01)
**Diff:** +19 294 / −462 across 127 files (111 commits in milestone range)
**Tests:** 1696 tests passing (Surefire + Failsafe + Playwright E2E); JaCoCo line coverage 88.44 % (gate 82 %, v1.11 baseline 88.88 %, Δ−0.44 pp — flagged for v1.13 cleanup; root cause documented in Plan 91-02 SUMMARY § JaCoCo coverage delta)
**Timeline:** 2 days (2026-05-18 → 2026-05-20)
**Branch:** `gsd/v1.12-driver-import-and-test-perf` (PR #129)
**Final-gate CI:** PERF-06 5-run harvest median Run [26157245962](https://github.com/jegr78/ctc-manager/actions/runs/26157245962) @ SHA `b63a2be1` SUCCESS — E2E step 17:39 (1059s, median of 5 sequential `workflow_dispatch` runs after dropping min+max; variance 18.2 % within D-10 20 % tolerance), Δ−23.3 % vs v1.11 23:00 baseline, SpotBugs 0 BugInstance
**Audit verdict:** passed (`v1.12-MILESTONE-AUDIT.md` will land post-`/gsd-complete-milestone v1.12`); Nyquist scoreboard compliant 4/0/0 (Phases 88+89+90+91 all `nyquist_compliant: true` per D-11 strict)

**Key accomplishments:**

- Phase 88 (Build/Release Unblockers, YAGNI Sweep, Doc-Conventions, Driver-Import Gap-Closure) — CLEAN-01..03 (`@Disabled` sweep + `SiteGeneratorBaselineRefresh` utility + Java-25 AssertJ generic-inference compile fix on `BackupSchemaExclusionIT.java:40`), REL-01..02 (release workflow hardening + retroactive v1.10.0 / v1.11.0 publishes + legacy tag cleanup + `docs/operations/release-runbook.md`), DOCS-01 (canonical `/gsd-` skill-invocation prefix across active planning files, six-file regression fence), DRIV-01..02 (season-aware shortName resolver + GROUPS-layout gate — closes 2 deferred debug sessions from 2026-05-08)
- Phase 89 (PERF Instrumentation + Lever 1) — PERF-01 per-fork `app.backup.staging-dir` + `app.backup.import-backups-dir` + `app.upload-dir` refactor enabling Failsafe `default-it forkCount=2 reuseForks=true`; PERF-02 `ContextCacheKeyFingerprintListener` + sidecar marker + `scripts/test-perf/aggregate-fingerprints.sh`; Wave-4 local median 09:19 = −10.4 % vs. Phase-86 10:24 baseline
- Phase 90 (PERF Consolidation + Module-Split Decision) — PERF-03 composed `@CtcDevSpringBootContext` annotation across 19 outer classes + Surefire cluster collapse `9cefac4c`→`baafff8e` (29 events / 13 classes preserved); PERF-04 `.withReuse(true)` on both MariaDB ITs + `~/.testcontainers.properties` opt-in documented; PERF-05 module-split `defer` verdict + 3 explicit blockers + v1.13 re-evaluation trigger; Wave-5 local median 08:27
- Phase 91 (PERF Re-Harvest + UX-01 + Closer) — PERF-06 CI 5-run median **17:39** harvested per D-17 trigger-equivalence (5 `workflow_dispatch` runs on milestone Draft PR HEAD `b63a2be1`; drop min+max; variance 18.2 %); UX-01 sealed `GoogleApiException` hierarchy (4 typed permits: Transient/Auth/NotFound/Permission) + `GoogleApiExceptionMapper` static helper + flash UX with `errorCategory` BEM badge in `admin.css` + Thymeleaf `layout.html` / `driver-import.html` + `docs/operations/google-integration.md` 5-section operator runbook (Setup / Error Categories / Troubleshooting); milestone close with composite D-07b PR body and v1.12 entry in MILESTONES.md
- JaCoCo line coverage 88.44 % (above 82 % pom gate; −0.44 pp delta vs v1.11 88.88 % attributed to unreachable defensive `catch (GoogleApiException)` blocks required by javac since sealed-exhaustiveness on catch is not yet a Java 25 language feature, plus uncovered service-layer IOException try-catch paths — flagged for v1.13); SpotBugs `BugInstance` 0; CodeQL gate-step exit 0; Flyway V1-V7 unchanged; `EXPORT_ORDER` = 24; `BackupSchema.SCHEMA_VERSION` = 1; D-13 production yml invariant + Flyway-immutable invariant held across all 4 phases

**Deferred to next milestone (acknowledged at close):**

- Test-module-split extraction (`ctc-manager-tests` Maven artifact) — Phase 90 PERF-05 `defer` verdict; v1.13 re-evaluates against PERF-06 CI median 17:39 surfaced in Phase 91
- Secondary cluster consolidation (backup-exception 12-class, admin-security 12-class, AdminWorkflowE2E 7-class buckets) — Phase 90 D-01 conservative
- Wider `@CtcDevSpringBootContext` adoption beyond Phase 90's 5-class `db.migration.**` cluster
- Background-trigger calendar-sync UX surface — UX-01 D-08 keeps non-user-triggered paths in graceful-fallback; future phase if operator demand surfaces
- Retry-with-backoff for `TransientGoogleApiException` — UX surfaces "retry" wording but no auto-retry
- OAuth re-link UI flow / Sheet-ID lookup helper / `@ControllerAdvice` typed-exception handler extraction — all out-of-scope per Phase 91 deferred items
- JaCoCo coverage recovery — add `RaceControllerCalendarTest` + integration tests for Google service IOException paths to recover the 0.44 pp delta vs v1.11 baseline
- QUAL-02 `local`-profile MariaDB manual smoke (carry-forward from v1.11; amber, operator-driven)
- QUAL-05 / UAT-02 legacy-season visual smoke (carry-forward from v1.11; procedure + STATE.md result-slot in place)

**Post-merge self-resolving (not tech debt):**

- v1.12 milestone PR #129 squash-merge to master (CI release workflow handles `v1.12.0` tag + GitHub Release + Docker images via the hardened workflow from Phase 88 REL-01 — no local `git tag` per `feedback_no_local_git_tags`)

Known deferred items at close: see `STATE.md` Deferred Items + `v1.12-MILESTONE-AUDIT.md` (lands post-`/gsd-complete-milestone v1.12`)

---

## v1.11 Tooling Infrastructure & Tech-Debt Sweep (Shipped: 2026-05-18)

**Phases completed:** 8 phases (80-87), 46 plans, 46/46 requirements satisfied
**Diff:** +81 335 / −3 165 across 718 files (216 commits in milestone range)
**Tests:** 1675 tests passing (Surefire + Failsafe + Playwright E2E); JaCoCo line coverage 88.88 % (gate 82 %, v1.10 baseline 87.80 %, +1.08 pp)
**Timeline:** 2 days (2026-05-16 → 2026-05-18)
**Branch:** `gsd/v1.11-tooling-and-cleanup` (PR #122)
**Final-gate CI:** Run [26033853591](https://github.com/jegr78/ctc-manager/actions/runs/26033853591) @ SHA `3590b3a7` SUCCESS — E2E step 23:33 ≤ 24:09 D-06 ceiling, SpotBugs 0 BugInstance, no Surefire fork-channel corruption
**Audit verdict:** passed (`v1.11-MILESTONE-AUDIT.md`); Nyquist scoreboard compliant 8/0/0

**Key accomplishments:**

- OpenRewrite developer-invoked refactoring tool wired (`-Prewrite` profile, `rewrite-spring:6.30.4` + `rewrite-migrate-java:3.34.1` + `rewrite-static-analysis:2.20.0` on plugin classpath, `rewrite.yml` activates `CommonStaticAnalysis` composite with `UpgradeSpringBoot_4_0` documentary tripwire); one-shot cleanup applied to 380 files with D-08 Lombok-triage fallback on 1 file (4-line revert for `RaceService.MethodReferences` regression); JaCoCo +0.33 pp (REWR-01..06)
- SpotBugs 4.9.8.3 + find-sec-bugs 1.14.0 blocking gate (`<goal>check</goal>` verify-bound, `<effort>Max</effort>`, 144 Spring-Security-aware patterns); `lombok.config` (`addLombokGeneratedAnnotation` + `addSuppressFBWarnings`) suppresses Lombok false positives; `config/spotbugs-exclude.xml` with D-08 layer 2 architectural filter extended to all service/DTO/record packages; 220 baseline findings triaged to 0 (197 EI_EXPOSE_REP* architectural-filter + 10 UTF-8 fixes + 9 NP suppressions + 4 misc fixes); STAT-06 throwaway-branch deliberate `NP_ALWAYS_NULL` proves exit 1 on HIGH (STAT-01..07)
- Backup cleanup: 12 Phase-75 REVIEW.md Info/Warning items resolved with atomic per-item commits + `82-BACKLOG-AUDIT.md` ledger; `BackupExecutedByResolver` bean extraction (WR-01); profile-isolated `import-backups-dir`; backup wire-contract guard tests (`BackupSchemaGuardTest`, `BackupRestoreZipOpenCountIT`, `BackupRoundTripIT` 24-entity row-count parity on H2 + MariaDB) (BACK-01..05)
- Quality and Polish sweep: driver-detail chip-order via JPQL `ORDER BY s.year ASC` in `DriverRepository#findDetailById` + repository IT + Playwright smoke; `DevDataSeeder` + `TestDataService` widened to `@Profile({"dev","local"})` for live-MariaDB UAT; per-group matchday-generator UI via `MatchdayGeneratorForm.groupId` + template `th:if="${phase.layout.name() == 'GROUPS'}"` guard + E2E; `StandingsView` record DTO + `StandingsViewService` (`@Transactional(readOnly=true)`) replaces OSIV-lazy controller access with 9 Mockito branch tests; `docs/uat/UAT-02-legacy-season-smoke.md` operator procedure + STATE.md result-slot (QUAL-01..05)
- Renovate Integration: Mend Renovate GitHub App installed (single-repo scope, Free Community plan, Interactive mode); `renovate.json` safety packageRules — Guava `-jre` allowedVersions regex, Thymeleaf `enabled: false` + secondary vulnerability-override (CVE-2026-40478 pin), `config:recommended` LTS preset inheritance, 4 group names (Spring Boot, Spring Security, Google API clients, Testcontainers), eclipse-temurin `-noble` regex with Adoptium underscore-build support, patch automerge `automergeType: "pr"`; synthetic Dockerfile-bump PR #126 exercises `dockerfile-noble-pin-guard` end-to-end; `.github/dependabot.yml` removed in same atomic commit as `renovate.json` introduction (T-5 dual-bot mitigation) (DEPS-01..08)
- CodeQL SAST blocking gate (`.github/workflows/codeql.yml` on push/pull_request/schedule/workflow_dispatch, `java-kotlin` with `security-extended`); 3-layer FP suppression invariant — `codeql-config.yml` `query-filters` (SSRF/ZIP-Slip/path-injection) + `// CodeQL FP: <rule-id>` source markers + `docs/security/sast-acceptance.md` per-finding table; BCrypt-N/A documented as D-05 tracked deviation (no `PasswordEncoder` bean, httpBasic auth); SAST-06 throwaway PR #128 surfaced gate-step semantic bug (PR-context vs branch-context alert query split fix via commit `61ccee5f` — the exact failure mode SAST-06 was designed to catch); CodeQL alert #35 dismissed `won't fix` post-verification (SAST-01..06)
- Test wallclock infrastructure baseline: `ContextLoadCountListener` instruments unique Spring context boots (baseline 81 → post 79 in `docs/test-performance.md`); 3 phase-repository ITs converted from `@SpringBootTest` to `@DataJpaTest` slice; 8 `@DirtiesContext` removed (sitegen cluster fix) + surgical per-method retention on latch-dependent backup-ITs with rationale comments; PERF-04 OR-branch verdict — target ≤7m 50s MISSED (CI median 23:00 ≫ target), architectural blocker documented with 3-lever v1.12 forward path; CI 5-run PR-branch median captured per D-17 trigger-equivalence (PERF-01..05)
- Phase 87 v1.10 Nyquist VALIDATION closure: 8 v1.10 phases (71-76, 78-79) retroactively brought to `status: approved` + `nyquist_compliant: true` via `/gsd-validate-phase` × 8 against archived v1.10 phase directories restored from git ref `60f5f915^`; 6 gap-fill tests landed atomically across 4 plans (5 new test files, 0 impl bugs surfaced, 0 checkpoint escalations); v1.10-MILESTONE-AUDIT scoreboard `partial 1/6/2` → `compliant 9/0/0` (VAL-01..04)
- In-milestone v1.11 Nyquist closure (Option A inline pattern): post-Phase-87 audit surfaced v1.11 itself accumulated same Nyquist debt; 6 v1.11 phases (81-86) retroactively approved + retroactive `86-VERIFICATION.md` created + REQUIREMENTS.md/ROADMAP.md bookkeeping flipped; v1.11 scoreboard `compliant 8/0/0`; pattern mirror — v1.10 → Phase 87 cross-milestone → v1.11 in-milestone same-day closure
- CI Playwright fork-channel corruption fix: `actions/cache@v4` for `~/.cache/ms-playwright` + pre-install all 3 default browsers (Chromium + Firefox + WebKit, ~360 MiB) before Surefire; eliminates `Playwright.create()` mid-Surefire auto-download corrupting fork-channel stdout (commit `5cc76ab9` + `3590b3a7` — diagnosed via dumpstream artifact from broken CI run `26015174076`)
- T-2 master branch protection activated: `required_status_checks.contexts = [build-and-test, dockerfile-noble-pin-guard, docker-build]`, `strict: true`, `enforce_admins: false`, force-pushes/deletions disabled (operator action 2026-05-18, Phase 84 follow-up)

**Deferred to next milestone (acknowledged at close):**

- Driver-Import gap-closure (2 deferred debug sessions from 2026-05-08, moved to `.planning/debug/deferred/`): `shortname-resolver-picks-parent-without-phaseteam` (data-correctness bug, season-aware algorithm documented) + `group-warnings-for-non-groups-seasons` (UI-noise bug, files_to_change documented)
- PERF-FUTURE-01: split `src/test/java/` into separate Maven modules (3-lever forward path in `docs/test-performance.md § v1.12 Forward Path` — per-fork backup-staging-dir, shared `@ContextConfiguration`, Testcontainers reuse)
- QUAL-02 `local`-profile MariaDB manual smoke (amber, operator-driven by Plan 83-02 D-15)
- QUAL-05 UAT-02 live execution against next production deploy (procedure + STATE.md result-slot in place)
- Pre-existing Phase-72 IT compile error (`BackupSchemaExclusionIT.java:40` — AssertJ generic inference Java 25), tracked in `80-deferred-items.md` for separate hot-fix plan

**Post-merge self-resolving (not tech debt):**

- v1.11 milestone PR #122 squash-merge to master (carries 217 commits, 718-file diff dominated by Phase 80 OpenRewrite cleanup + Phase 87 v1.10 archive restoration + Phase 86 test-infra refactors)
- CI release workflow handles tagging post-merge (per `feedback_no_local_git_tags` memory — no local `git tag` on this branch)

Known deferred items at close: see `STATE.md` Deferred Items + `v1.11-MILESTONE-AUDIT.md`

---

## v1.10 Spring Boot 4.0.6 Upgrade & Data Export/Import (Shipped: 2026-05-16)

**Phases completed:** 9 phases (71-79), 50 plans, 39/39 requirements satisfied
**Diff:** +77 362 / −1 224 across 521 files (378 commits in milestone range)
**Tests:** 1652 Surefire unit + 231 Failsafe IT + 36 Playwright E2E; JaCoCo line coverage 87.80 % (gate 82 %, v1.9 baseline 87.02 %)
**Timeline:** 7 days (2026-05-09 → 2026-05-16)
**Branch:** `gsd/v1.10-platform-and-backup`
**Final-gate verify:** `./mvnw verify -Pe2e` BUILD SUCCESS, Maven total 11m 11s, bash wallclock 11m 13s
**Audit verdict:** passed (`v1.10-MILESTONE-AUDIT.md`)

**Key accomplishments:**

- Spring Boot 4.0.5 → 4.0.6 + Thymeleaf 3.1.5.RELEASE absorbed structurally — controller-side `pageTitle` model attributes replace fragment-parameter ternaries across ~80 admin + site templates; `TemplateRenderingSmokeIT` covers every `/admin/**` GET route; `exec-maven-plugin` grep-gate fences regression (PLAT-01..07)
- Backup wire contract locked before any export/import code: `BackupSchema.SCHEMA_VERSION = 1` (monotonic integer), `BackupManifest` record, 24-entity FK-respecting `EXPORT_ORDER` generated from JPA Metamodel via Kahn's algorithm, `@Qualifier("backupObjectMapper")` strict bean co-exists with `@Primary` default, Flyway `V7__data_import_audit.sql` migration runs on H2 + MariaDB, `data_import_audit` structurally excluded from export via package filter (SCHEMA-01..04, IMPORT-08)
- Streaming ZIP export — 24 per-entity Jackson MixIns keep `org.ctc.domain.model` annotation-clean, `BackupExportService` `@Transactional(readOnly=true)` with explicit `@EntityGraph` eager-fetch, `StreamingResponseBody` (no full-dataset buffering), CSRF-protected POST endpoint with ISO-instant `Content-Disposition` filename, admin/backup page wired to sidebar (EXPORT-01..06)
- Replace-all import path — manifest-first read + schema-version refusal BEFORE any DB write, ZIP-Slip + ZipBomb defenses (50 MB/entry, 500 MB total, 50.000 entries cap, `startsWith(uploadDir.toRealPath())` check), multipart limits raised to 100 MB on Spring + Tomcat layers, dedicated `BackupUploadExceptionHandler` `@ControllerAdvice` for `MaxUploadSizeExceededException`, stateless preview state via staging-path re-parse (D-15 v1.8 pattern); single `@Transactional` wipe + restore (FK-reverse native-SQL DELETE → `em.flush() + em.clear()` → `JdbcTemplate.batchUpdate` bypassing `AuditingEntityListener`), `Team.parentTeam = NULL` pre-step, post-commit upload-tree stage-and-rename with 24h `data/.import-backups/<ts>/uploads-old/` recovery (IMPORT-01..07, SECU-01..04)
- Operational hardening — `ImportLockService` `ReentrantLock` singleton + 409 redirect, persistent yellow read-only banner via `@ControllerAdvice`, `ImportLockedWriteRejector` HandlerInterceptor (HTTP 503 on non-import POSTs, whitelist-on-equals), synchronous auto-backup-before-import Step 0.5 with first-match-wins `AutoBackupBeforeImportException` catch-chain (SECU-05..07); 5-section operator runbook `docs/operations/import-runbook.md`
- Quality gates held — `BackupRoundTripIT` runs on H2 AND MariaDB profiles with 24-entity row-count parity + SHA-256 byte-equality on Race + SeasonDriver + Team; `BackupImportRollbackIT` injects mid-restore failure at 50 % → asserts pre-import DB state + `success=false` audit row; `BackupImportMariaDbSmokeIT` (Testcontainers) covers Saison-2023 round-trip; 75-HUMAN-UAT 10/10 PASS (6 visual + 4 operational); README "Backup & Restore" section + GitHub Wiki page pushed to ctc-manager.wiki.git (QUAL-01..05)
- Side-quest Phase 78: Dockerfile pinned `eclipse-temurin:25-{jdk,jre}-noble` (suffix-only) repairs the silently broken release workflow's `playwright install chromium` step on Ubuntu 26.04; CI `dockerfile-noble-pin-guard` job mirrors PLAT-07's `exec-maven-plugin` grep-gate pattern; full `docker build .` runs on every PR + push (PLAT-CI-01..02)
- Phase 79 milestone closer: per-package cleanup across `src/main/java` + `src/test/java` + 24 Jackson MixIns + 24 EntityRestorers + 15 graphic services (Schutzwortliste-protected, atomic commits), Surefire `forkCount=2C` + Failsafe default-it `forkCount=1C` + `excludedGroups=flaky` quarantine, ci.yml concurrency block + `--no-transfer-progress`, plan-SUMMARY frontmatter normalized for phases 56/57/62/64, TESTING.md "Test Invocation Discipline" appended

**Deferred to next milestone (acknowledged at close):**

- 12 REVIEW.md Info/Warning items from Phase 75 (`Map.copyOf` order strip, Step-1-revert `FileAlreadyExistsException`, `executedBy` duplication, `restoreOneTable` opens ZIP 24×, etc.) — v1.11 backup-cleanup mini-phase
- Phase 79 D-06 wallclock-reduction debt: achieved 16.85 % vs ≥ 30 % target; Spring-context startup is structural — architectural test-restructuring needed (Spring-context-per-fork is unavoidable cost without splitting test modules)
- Driver-detail Season-Assignment chip ordering (cosmetic; 75-HUMAN-UAT test 6) — explicit `ORDER BY year` on `Driver.seasonAssignments` query
- `DevDataSeeder` is `@Profile("dev")`-only; live-MariaDB-UAT on `local,demo` requires either profile widening or a separate Saison-2023 fixture-bootstrap
- Nyquist `*-VALIDATION.md` drafts → approved for 6 phases (72-76, 79) + creation for 71 + 78 — optional `/gsd-validate-phase {N}`
- Backlog: OpenRewrite (Phase 999.1), Clean-Code enforcement (999.2), Renovate (999.3), SAST (999.4)

**Post-merge self-resolving (not tech debt):**

- QUAL-05 wiki image embed render — `raw.githubusercontent.com/master/...` URLs resolve on PR merge to master
- PLAT-CI-02 release-workflow run on master observation — by-design post-merge

Known deferred items at close: see `STATE.md` Deferred Items + `v1.10-ROADMAP.md` "Issues Deferred"

---

## v1.9 Season Phases & Groups (Shipped: 2026-05-09)

**Phases completed:** 15 phases (56-70), ~70 plans, 38/38 requirements satisfied
**Diff:** +88 447 / −2 502 across 567 files (442 commits in milestone range)
**Tests:** 1227 unit + 31 Playwright E2E (Failsafe), JaCoCo line coverage 87.02% (gate 82%)
**Timeline:** 14 days (2026-04-26 → 2026-05-09)
**Branch:** `gsd/v1.9-season-phases-groups`
**Live UAT D-22:** Saison 2023 driver import on local MariaDB — 287 new drivers / 357 new assignments / 0 errors

**Key accomplishments:**

- Phase/Group domain model: `SeasonPhase` (REGULAR/PLAYOFF/PLACEMENT) + `SeasonPhaseGroup` + `PhaseTeam` roster replace the flat-season container; group-seasons are expressible without the multi-season workaround (MODEL-01..08, MIGR-01..07)
- Mechanical data migration: Flyway V3-V6 maps every existing season to 1 REGULAR (+ optional PLAYOFF) phase, drops the `season_id` bridge columns and the `playoff_seasons` join-table — legacy seasons stay reachable byte-identically (live UAT 287/357/0 errors confirms)
- Phase-aware domain services: `StandingsService.calculateStandings(phaseId, groupId)` + `DriverRankingService` + `MatchdayGeneratorService` + `PlayoffService(Seeding)` operate exclusively on phase scope with combined-view aggregation; 5 graphics services migrated off the legacy `calculateStandings(seasonId)` bridge (SVC-01..05)
- Admin UI mirrors the new model: slim season form, phase + group CRUD forms, season-detail two-row tabs (phase row + group sub-tab row), per-phase standings + combined view, playoff bracket bound to PLAYOFF phase, driver-import preview with unambiguous `year_S{number}` labels (UI-01..07)
- Public site phase + group awareness: phase-tab row + group-sub-tab row + per-phase URL variants + PLAYOFF tab routing + Phase Breakdown sections on team/driver profiles + alltime aggregation across all phases (D-19 TRACKED BEHAVIOR CHANGE); LEAGUE-only seasons render byte-identically (SITE-01..03 + 9-test `SiteGeneratorPhaseAwarenessIT`)
- Driver import: `findByYearAndNumber(int, int)` resolves `^\d{4}_S\d+$` tabs unambiguously (IMPORT-01..04); Phase 70 inverted the Phase-66 sub-team-wins default — `SeasonDriver.team_id` always points to parent, sub-team split happens per-match via `RaceLineup`; group-resolution UX + `TEAM_NOT_IN_REGULAR_PHASE` warning fully decommissioned; 2 IT regression tests close GAP-70-01 (cross-tab duplicate driver insert)
- Quality gate held: JaCoCo 87.02% (gate 82%), 1227 unit + 31 Playwright E2E green, comment-policy re-sweep across `src/main` + `src/test` (Phase 67), Lombok 1.18.46 + JEP 498 silence the `sun.misc.Unsafe` warnings on Java 25 (Phase 68), Guava 33.4.8-jre override silences the transitive `AbstractFuture` Unsafe warning

**Deferred to next milestone (acknowledged at close):**

- Quality Gate Lock / CI comment-noise guard (Phase 67 D-06 forward commitment) — automated gate (Maven Enforcer / pre-commit hook / CI grep gate) blocking attribution-marker introduction
- UAT-02 (Legacy season visual smoke with real pre-V4 production data) — user verifies opportunistically after next production deploy; local fixtures cover empty-state path only
- WARN-1: per-group matchday generation UI affordance (`SeasonController.generateMatchdays:251` hardcodes `groupId=null`) — Rule-3 deviation documented in Phase 61 QUAL-02
- OBS-3: `StandingsController.java:139` lazy collection (`resolvedPhase.getGroups()`) — OSIV-safe read-only context, style-only
- Plan SUMMARY frontmatter sweep for phases 56/57/62/64 (15 plan SUMMARYs across 4 phases, analog to Phase 69's 17-SUMMARY sweep) — pure bookkeeping
- Stub quick task `260404-jh8-fix-release-workflow-use-release-token-s` (predates v1.9, status `missing`)
- 2 debug sessions (`group-warnings-for-non-groups-seasons`, `shortname-resolver-picks-parent-without-phaseteam`) — both `diagnosed` with hypothesis confirmed; resolutions captured in session docs and superseded by Phase 70

Known deferred items at close: 8 (see STATE.md Deferred Items)

---

## v1.8 Bulk Driver Import from Google Sheets (Shipped: 2026-04-25)

**Phases completed:** 2 phases, 4 plans, ~12 tasks
**PR:** [#116](https://github.com/jegr78/ctc-manager/pull/116) (squash-merged as `042cfbf`)
**Diff:** +11 246 / −539 across 39 files
**Tests:** 1064 total project-wide (+52 from baseline 1011)
**Timeline:** 2 days (2026-04-24 → 2026-04-25)

**Key accomplishments:**

- Stateless preview service (`DriverSheetImportService.preview()`) categorizing Google Sheets driver rows into 6 typed buckets via D-12 waterfall with `SeasonRepository.findByYear(int)` auto-match — 16 unit tests, 98.9% line coverage
- `@Transactional execute()` method with 6-bucket walk, cross-tab driver dedup, per-row Skip/Accept decisions, mutable `ExecuteResult` accumulator — `IOException` wrapped as `IllegalStateException` for proper rollback semantics
- Thin Spring MVC `DriverSheetImportController` (3 handlers: GET form, POST preview, POST execute) + 2 Thymeleaf templates with 6 bucket tables and Skip/Accept checkboxes + entry button on `/admin/drivers` toolbar — zero business logic, zero inline styles, zero `th:utext`
- 21 integration tests (17 happy-path + 4 exception-path) exercising the full GET/POST-preview/POST-execute flow with `@MockitoBean GoogleSheetsService`; JaCoCo 82% line gate met
- Code review found 1 critical (per-tab cache key for FUZZY/accept) + 3 warnings (exception leakage, missing `@Transactional(readOnly=true)`, dead test setup) — all auto-fixed in 4 atomic commits
- Reuse pattern reinforced: `GoogleSheetsService`, `DriverMatchingService` (4-stage fuzzy), and `CsvImportController` preview-state pattern reused without modification — no parallel infrastructure introduced
- Form-binding contract evolved (D-15 override): per-row dynamic keys (`seasonId_<year>`, `skip_<psnId>_<year>`, `accept_<psnId>_<year>`) bound via `@RequestParam` + `Map<String,String>` instead of static DTO

**Known tech debt:**

1. D-15 wording carryover in REQUIREMENTS.md QUAL-03 (override accepted, documented in PROJECT.md)
2. UAT 3 (ambiguous-season banner) verified by template inspection + integration test instead of live Google-Sheet render

---

## v1.5 Code Review Fixes (Shipped: 2026-04-15)

**Phases completed:** 9 phases, 14 plans, 18 tasks

**Key accomplishments:**

- 1. [Rule 1 - Bug] Corrected test assertion for header injection check
- MatchdayController mass assignment vulnerability eliminated by replacing `@ModelAttribute Matchday` (JPA entity) with `@ModelAttribute("form") MatchdayForm` DTO containing only 4 user-editable fields
- layout.html
- One-liner:
- One-liner:
- RaceGraphicService relocated from domain.service to admin.service and TeamCardService decoupled from RaceService — zero admin imports now remain in the domain layer
- One-liner:
- Season grouping/sorting, matchday graphic status computation, and driver merge filtering extracted from three controllers into their respective service methods using TDD
- SiteGeneratorService.toRaceView() now resolves driver-team assignments from RaceLineup entries first, falling back to SeasonDriver only when no lineup entry exists — matching the canonical pattern from RaceFormDataService
- One-liner:
- 1. classList.add count is 2, not 3 as plan stated

---

## v1.3 English Test Data (Shipped: 2026-04-10)

**Phases completed:** 8 phases, 9 plans, 17 tasks

**Key accomplishments:**

- One-liner:
- Replaced 26 German test-data strings and 3 HTML comments with English equivalents, completing the codebase English cleanup started in Phase 20
- TestDataService refactored with 10 fictive racing teams (17 total with sub-teams), 100 fictive drivers (10 per team), and TeamCardService integration with graceful Playwright fallback
- 1. [Rule 1 - Bug] Added @Transactional to integration test class
- 1. [Rule 1 - Bug] JPA flush + detach for aggregateMatchScores
- 1. [Rule 1 - Bug] Fixed integration test assertions for shared H2 database
- One-liner:
- Commit:

---

## v1.2 Driver Merge (Shipped: 2026-04-07)

**Phases completed:** 4 phases (16-19), 5 plans
**Timeline:** 11 days (2026-03-27 → 2026-04-07)
**Requirements:** 14/14 satisfied

**Key accomplishments:**

- Transactional DriverMergeService with FK reassignment across SeasonDriver, RaceLineup, RaceResult, and PsnAlias tables — source PSN-ID preserved as alias on target
- Proactive duplicate detection for all 3 FK tables — source entries dropped instead of reassigned when target already exists in the same season/race, preventing unique-constraint violations
- Read-only MergePreview with per-table reference and duplicate counts for informed merge decisions
- Full merge UI workflow: merge button on driver detail, target selection dropdown, preview table, JavaScript confirm dialog
- Graceful error handling on all merge endpoints with flash redirect (matching executeMerge pattern)

**Known tech debt:** 5 human visual verification items (Phase 18 merge UI), REQUIREMENTS.md checkboxes not updated

---

## v1.1 Codebase Concerns Cleanup (Shipped: 2026-04-07)

**Phases completed:** 10 phases (6-15), 20 plans, 820 tests
**Timeline:** 4 days (2026-04-04 → 2026-04-07)
**Requirements:** 12/12 satisfied

**Key accomplishments:**

- SSRF hostname blocklist + path traversal defense for all FileStorageService write methods (SECU-01, SECU-02)
- 5 controllers use services only, 10 domain services decoupled from admin DTOs (ARCH-01, ARCH-02)
- 25+ catch(Exception e) blocks narrowed to specific exception types across controllers and services (ERRH-01)
- Cross-season alltime standings aggregation with sub-team resolution (FEAT-01)
- TemplateEditorController generic dispatch via TemplateManageable interface (ARCH-03)
- PlayoffService + RaceService split into focused units (ARCH-04, ARCH-05)
- Inline styles replaced with CSS utility classes across all admin templates (QUAL-01)
- Unbounded findAll() scoped or documented (QUAL-02)

**Known tech debt:** 9 human visual verification items (template editor, admin pages after refactor)

---

## v1.0 Technical Debt Cleanup (Shipped: 2026-04-04)

**Phases completed:** 5 phases, 12 plans, 16 tasks

**Key accomplishments:**

- 3 typed exception classes, GlobalExceptionHandler with @ControllerAdvice handling 6 exception types, and admin error page within layout with profile-aware detail display
- Migrated all 135 orElseThrow calls across 21 production files to EntityNotFoundException/ValidationException with entity type and ID in every message
- Extracted 9 methods into 2 focused services from RaceManagementService, reducing dependencies from 20 to 14 with DRY GraphicGenerator pattern
- Renamed God Service to RaceService and rewired RaceController to 3 direct service injections, completing the service split with 744 tests passing
- Flyway V2 migration with 36 FK indexes and 28 @EntityGraph annotations across 11 repositories to eliminate N+1 queries
- SSRF protection via HTTPS-only guard clause in FileStorageService.storeFromUrl() with 4 new unit tests
- Spring Security HTTP Basic Auth for prod/docker with profile-conditional SecurityFilterChain, dev/local permit-all, all 664 tests green
- 403 access-denied page in admin layout with Docker credential wiring for local (admin/ctc-admin) and prod (env vars)

---
