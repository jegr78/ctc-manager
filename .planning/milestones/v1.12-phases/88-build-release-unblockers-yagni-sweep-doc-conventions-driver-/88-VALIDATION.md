---
phase: 88
slug: build-release-unblockers-yagni-sweep-doc-conventions-driver
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-18
---

# Phase 88 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Derived from `88-RESEARCH.md` § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 5.x (Spring Boot–managed) + Mockito + AssertJ |
| **Config file** | `pom.xml` (Surefire + Failsafe sections) |
| **Quick run command** | `./mvnw test` (Surefire, ~3 minutes) |
| **Full suite command** | `./mvnw verify -Pe2e` (Surefire + Failsafe + Playwright E2E + JaCoCo, CI median 23:00) |
| **Estimated runtime** | Quick: ~180 s · Full: ~23:00 (CI median) |
| **@Tag routing** | Surefire: untagged unit tests · Failsafe: `@Tag("integration")` · `-Pe2e`: `@Tag("e2e")` |

---

## Sampling Rate

- **After every task commit:** Focused class run, per [[test-call-optimization]]:
  `./mvnw test -Dtest='<focused-test-class>'`
  OR `./mvnw -Dit.test='<focused-IT-class>' failsafe:integration-test failsafe:verify`
- **After every plan merge:** Full local build, per [[clean-build-test-only]]:
  `./mvnw clean verify`
- **Phase gate (once, at end of Plan-05 or Plan-06):** Full E2E sweep, per [[e2e-verification]]:
  `./mvnw clean verify -Pe2e`
- **Max feedback latency:** ~180 s (quick) — full suite reserved for plan-merge boundaries

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 88-01-01 | 01 | 1 | CLEAN-01 | — | Build exits 0; JaCoCo CSV emitted | build gate | `./mvnw clean verify && ls target/site/jacoco/jacoco.csv` | n/a | ⬜ pending |
| 88-02-01 | 02 | 2 | CLEAN-02 (a) | — | `@Disabled` GROUPS-SWISS placeholder removed | grep + compile | `grep -n "givenGroupsSwissLayoutSeason" src/test/java; ./mvnw clean test-compile` | ✅ (delete-target) | ⬜ pending |
| 88-02-02 | 02 | 2 | CLEAN-02 (b) | — | `@Disabled` regression-fence removed; coverage retained via Test #7 | grep + JaCoCo CSV delta | `grep -n "givenPreExistingDriverNotMatched" src/test/java; ./mvnw clean verify` | ✅ (delete-target) | ⬜ pending |
| 88-02-03 | 02 | 2 | CLEAN-02 (c) | — | Windows-conditional skip simplified; POSIX assertion unconditional | grep + IT run | `grep -n "isWindows()" src/test/java; ./mvnw -Dit.test='AutoBackupBeforeImportFailureIT' failsafe:integration-test failsafe:verify` | ✅ (simplify-target) | ⬜ pending |
| 88-02-04 | 02 | 2 | CLEAN-03 | — | Standalone utility replaces `@Test @Disabled` baseline-capture | grep + class-exists | `grep -rn "@Disabled" src/test/java \| wc -l == 0; grep -rn "SiteGeneratorBaselineRefresh" src/test/java` | ❌ Wave 0 | ⬜ pending |
| 88-03-01 | 03 | 3 | REL-01 | T-88-01 (input tampering) | `dry-run=true` exercises parser+guard, no side effects | manual dispatch | `gh workflow run release.yml -F dry-run=true --ref gsd/v1.12-driver-import-and-test-perf; gh run watch <id> --exit-status` | ✅ (mutate) | ⬜ pending |
| 88-04-01 | 04 | 4 | DOCS-01 | — | CLAUDE.md "Skill Invocation Naming" paragraph present; strict grep == 0 | grep + read | `grep -c 'Skill Invocation Naming' CLAUDE.md == 1; grep -rn '/gsd:' .planning/PROJECT.md .planning/STATE.md .planning/ROADMAP.md .planning/REQUIREMENTS.md .planning/MILESTONES.md .planning/RETROSPECTIVE.md \| wc -l == 0` | ✅ (mutate) | ⬜ pending |
| 88-05-01 | 05 | 5 | DRIV-01 | — | Resolver season-aware; 4 edge cases covered | unit | `./mvnw test -Dtest='DriverSheetImportServiceTest'` | ✅ (mutate + add) | ⬜ pending |
| 88-05-02 | 05 | 5 | DRIV-02 | — | Service skips warning + lookup on non-GROUPS; `usesGroups` flows to template; new GROUPS-with-missing-PhaseTeam contract test | unit + IT | `./mvnw test -Dtest='DriverSheetImportServiceTest'; ./mvnw -Dit.test='DriverSheetImportServiceIT' failsafe:integration-test failsafe:verify` | ✅ (mutate + add) | ⬜ pending |
| 88-06-01 | 06 | 6 | REL-02 | T-88-02 (irreversible delete), T-88-03 (image tamper) | Retroactive v1.10.0 + v1.11.0 published; legacy short-form tags deleted; runbook documents per-tag operator confirmation | manual operator | `gh release list \| grep -E 'v1\.(10\|11)\.0'; gh api /repos/jegr78/ctc-manager/git/refs/tags \| jq -r '.[].ref' \| grep -E '^refs/tags/v[0-9]+$' \| wc -l == 0` | ❌ runbook NEW | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/ctc/sitegen/util/SiteGeneratorBaselineRefresh.java` — NEW utility class replacing the `@Test @Disabled` shell (Plan-02 / CLEAN-03)
- [ ] `docs/operations/release-runbook.md` — NEW operator runbook (Plan-06 / REL-02)
- [ ] `CLAUDE.md` "Skill Invocation Naming" paragraph — content insertion (Plan-04 / DOCS-01)
- [ ] 6 documentary `/gsd:` rewrites in `.planning/{PROJECT,STATE,ROADMAP,REQUIREMENTS,MILESTONES,RETROSPECTIVE}.md` — text edits to satisfy strict grep (Plan-04 / DOCS-01)
- [ ] `.planning/REQUIREMENTS.md` CLEAN-01 entry — status flip Open → Resolved with cross-ref to Phase-80 deferred-items.md (Plan-01)

*All other infrastructure — JUnit Jupiter, Spring Boot test starter, JaCoCo, Surefire, Failsafe, `@Tag` routing, Playwright — is already in place from v1.11. No framework install needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `gh workflow run release.yml -F dry-run=true` succeeds on the v1.12 PR-branch with parser+guard exercised end-to-end | REL-01 | GitHub Actions runs cannot be triggered from `./mvnw`; requires push to branch + `gh` CLI dispatch | 1. Push Plan-03 commit to `gsd/v1.12-driver-import-and-test-perf`. 2. `gh workflow run release.yml -F dry-run=true --ref gsd/v1.12-driver-import-and-test-perf`. 3. `gh run list --workflow=release.yml --branch=gsd/v1.12-driver-import-and-test-perf --limit=1`. 4. `gh run view <id> --log` — confirm parser + idempotency guard ran; `versions:set`/build/tag-push/release-create/docker-push skipped. |
| v1.10.0 + v1.11.0 GitHub Releases + GHCR images published; legacy short-form tags deleted | REL-02 | One-shot retroactive operation; requires operator confirmation per destructive `gh api -X DELETE` call | Follow `docs/operations/release-runbook.md` step-by-step. Per-tag operator confirmation enforced by runbook. Verification: `gh release list \| grep v1.10.0`, `gh release list \| grep v1.11.0`, `docker manifest inspect ghcr.io/jegr78/ctc-manager:1.10.0`, `gh api /repos/jegr78/ctc-manager/git/refs/tags \| jq -r '.[].ref' \| grep -E '^refs/tags/v[0-9]+$'` returns empty. |
| Driver-import preview template renders `usesGroups`-gated cells correctly (visual check) | DRIV-02 | UI-level rendering — requires running server + browser snapshot | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo`, navigate `http://localhost:9090/admin/driver-import` with a GROUPS season and a LEAGUE season; capture screenshots via `playwright-cli`. Per [[playwright-cli]] memory rule. |

---

## Threat Model References

| Threat ID | Surface | Mitigation |
|-----------|---------|------------|
| T-88-01 | `release.yml` `dry-run` input tampering | `if: inputs.dry_run != true` guards on ALL side-effecting steps (versions:set, package, tag push, `gh release create`, docker push). Parser + Idempotency-Guard + Determine-Version run unconditionally so the new logic is exercised end-to-end. |
| T-88-02 | Legacy-tag deletion via `gh api -X DELETE` (irreversible) | Runbook requires explicit per-tag operator confirmation prompt before each `gh api -X DELETE` call. Targets enumerated up-front (`v1.3`, `v1.5`, `v1.8` per RESEARCH live remote inventory — `v1.6`/`v1.9` already deleted). |
| T-88-03 | Docker image content tampering (wrong version in JAR) | Runbook enforces `versions:set -DnewVersion=1.X.0 -DgenerateBackupPoms=false` BEFORE `./mvnw -DskipTests package` in the historical worktree. Docker image tag is set explicitly to `1.X.0` to match. |
| (no T-id) | `gh release create --generate-notes` content from arbitrary git history | Already vetted by `gh` CLI; no extra mitigation needed. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (5 new files / mutations listed above)
- [ ] No watch-mode flags
- [ ] Feedback latency < 180 s (quick) / 23:00 (full)
- [ ] `nyquist_compliant: true` set in frontmatter after Plan-checker pass

**Approval:** pending
