---
plan_id: 61-gap-06
phase: 61-cleanup-quality-gate
title: Dead code — domain.service unused privates, dead branches, defensive null-guards
wave: 2
gap_closure: true
autonomous: true
depends_on: [61-gap-01, 61-gap-04]
files_modified:
  - src/main/java/org/ctc/domain/service/StandingsService.java
  - src/main/java/org/ctc/domain/service/SwissPairingService.java
  - src/main/java/org/ctc/domain/service/FileStorageService.java
  - src/main/java/org/ctc/domain/service/PlayoffService.java
  - src/main/java/org/ctc/domain/service/PlayoffSeedingService.java
  - src/main/java/org/ctc/domain/service/RaceFormDataService.java
  - src/main/java/org/ctc/domain/service/DriverRankingService.java
  - src/main/java/org/ctc/domain/service/RaceService.java
  - src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java
  - src/main/java/org/ctc/domain/service/RaceCalendarService.java
requirements: [MIGR-06, QUAL-01, QUAL-02, QUAL-03]
must_haves:
  - "deferred-items.md PlayoffService.playoffSeedRepository unused field is removed OR explicitly retained with rationale"
  - "Defensive null-checks at internal-API boundaries in domain.service either justified (boundary) or removed (CLAUDE.md hard rule)"
  - "Unused private methods in domain.service identified, each removed or NEEDS_CONTEXT-tracked"
  - "./mvnw verify (Surefire + JaCoCo) BUILD SUCCESS, line coverage >= 82%"
  - "No commit removes a method whose absence cannot be proven safe via test or grep"
---

<objective>
Remove dead code from `domain.service` while preserving the 82% JaCoCo gate. Address gaps **G3 (dead code branches)**
and **G4 (over-validation)** in the same plan because they overlap heavily — many "defensive null guards" ARE
the dead branches that need removal.

Scope is narrow because:
- 0 `Objects.requireNonNull` calls exist codebase-wide (verified during planning)
- 0 `@Autowired` fields (Lombok `@RequiredArgsConstructor` + `final` everywhere — already correct per CLAUDE.md)
- ~15 defensive `if (x == null)` lines in domain.service (legitimate boundary checks vs. impossible cases)
- ~42 private methods total in domain.service (need usage audit)
- 1 known-unused field: `PlayoffService.playoffSeedRepository` (deferred-items.md line 7)

Purpose: G3 + G4 cleanup for domain.service, defensively (no removal without proof of safety).

Output: dead code removed atomically; each removal has a regression test or a grep proof of zero callers; coverage stays >= 82%.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/phases/61-cleanup-quality-gate/61-CONTEXT.md
@.planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md
@.planning/phases/61-cleanup-quality-gate/deferred-items.md
@CLAUDE.md

**CLAUDE.md hard rules (binding):**
- "Don't add error handling, fallbacks, or validation for scenarios that can't happen. Trust internal code and framework guarantees. Only validate at system boundaries (user input, external APIs)."
- 82% line coverage minimum (D-21 — never lower)

**Defensive Removal Protocol (for EACH candidate):**
1. Grep target pattern → enumerate candidates
2. For each candidate, grep ALL callers/usages across `src/main` + `src/test`
3. If 0 callers in `src/main` → check git blame for context (was it added intentionally?)
4. If 0 callers + git blame shows it was a transient artifact → safe to remove
5. If 0 callers in `src/main` BUT has callers in `src/test` → the test exercises a path no longer reachable in production. The test SHOULD be removed too (with a comment in the commit explaining the path is unreachable). Document in SUMMARY.
6. Run targeted test → commit
7. NEVER remove if uncertain — document in `deferred-items.md` and report NEEDS_CONTEXT in SUMMARY

**Known boundary cases (KEEP):**
- `FileStorageService.sanitize(filename)` line 117-119 — `filename == null` IS a legitimate boundary check.
  This service is called with user-supplied filenames from upload forms. CLAUDE.md says "validate at system
  boundaries (user input)". KEEP.
- All `} catch (IOException e)` in services touching the filesystem (RaceAttachmentService, FileStorageService,
  TrackService.java:96, CarService.java:96, TeamManagementService.java:269) — IOException IS a real boundary
  case. KEEP.
- All `} catch (DataIntegrityViolationException e)` — Spring re-wraps DB constraint violations; these are
  framework-boundary errors. KEEP.
- `} catch (NumberFormatException e)` in FileStorageService.java:146 — parses user input. KEEP.

**Removal candidates (audit + remove if proven dead):**
- `PlayoffService.playoffSeedRepository` field — deferred-items.md line 7 explicitly tags this as unused.
  Verify zero callers, then remove field + the constructor parameter (Lombok `@RequiredArgsConstructor` will
  regenerate the constructor without it).
- `StandingsService.java:200-201` — `season = seasonRepository.findById(seasonId).orElse(null); if (season == null) return Map.of();`
  This IS post-cascade dead code if every caller passes a UUID resolved from a season that exists. Audit:
  what's the call chain? Is `seasonId` ever derived from user input directly? If yes → boundary, KEEP. If
  always derived from a previous repository.findById success → REMOVE the null-check + use `orElseThrow`.
- `StandingsService.java:270` — `if (homeStanding == null) return;` — audit: is `homeStanding` derived from a
  Map.get() that could miss? If yes → KEEP (Map.get can legitimately return null). If derived from a guaranteed-present
  collection → REMOVE.
- `SwissPairingService.resolveGroup(UUID groupId)` line 201-202 — `if (groupId == null) return null;` —
  this is a no-group sentinel return for LEAGUE-layout phases. Audit: does the caller pass null intentionally?
  If yes → this is a valid sentinel pattern, KEEP. If never null in practice → REMOVE.
- Private methods in services with 0 callers within the same class: enumerate via reflection or static analysis
  (IntelliJ "Find Usages" equivalent: `grep -c "<methodName>(" src/main/java/<ServiceFile>` should be > 1 — the
  declaration plus at least one call).
</context>

<tasks>

<task id="1" name="Audit + remove unused fields/methods in domain.service (deferred-items.md backlog)">
  <action>
    Step 1 — confirm `PlayoffService.playoffSeedRepository` is truly unused:
    ```bash
    grep -rn "playoffSeedRepository" src/main src/test
    ```
    Expected: only the field declaration in PlayoffService line 36 (no read or write callers).

    Step 2 — remove the field. Because `@RequiredArgsConstructor` is class-level, Lombok will rebuild the
    constructor without it on the next compile. No constructor edits needed.

    Step 3 — also audit and remove the corresponding @Mock or @Autowired in test classes:
    ```bash
    grep -rn "playoffSeedRepository" src/test
    ```
    If hits exist, remove them.

    Step 4 — for each other domain.service file in `files_modified`, run usage audit on every private method:
    ```bash
    for f in src/main/java/org/ctc/domain/service/StandingsService.java \
             src/main/java/org/ctc/domain/service/SwissPairingService.java \
             src/main/java/org/ctc/domain/service/PlayoffService.java \
             src/main/java/org/ctc/domain/service/PlayoffSeedingService.java \
             src/main/java/org/ctc/domain/service/RaceFormDataService.java \
             src/main/java/org/ctc/domain/service/DriverRankingService.java \
             src/main/java/org/ctc/domain/service/RaceService.java \
             src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java; do
      echo "=== $f ==="
      grep -oE "private[^(]*\b[a-zA-Z][a-zA-Z0-9]*\s*\(" "$f" | sed -E 's/.*\b([a-zA-Z][a-zA-Z0-9]*)\s*\(/\1/' | while read m; do
        count=$(grep -c "\b${m}\s*(" "$f")
        echo "  $m: $count occurrences in same file"
      done
    done
    ```

    Methods with `count == 1` (only the declaration) are dead-code candidates. For each, verify:
    - Is it referenced by reflection? (e.g., `@EventListener`, `@Scheduled`, `@PostConstruct` — DO NOT remove)
    - Is it referenced from outside via Lombok-generated code? (very rare for private)
    - If neither → remove.

    Each removal MUST be a separate commit so bisect can isolate regressions:
    ```
    refactor(61-gap): remove unused playoffSeedRepository field from PlayoffService
    refactor(61-gap): remove unused private <methodName> from <ServiceName>
    ```

    Run after each removal:
    ```bash
    ./mvnw test -Dtest='<TouchedServiceName>Test'
    ```
    Expected: BUILD SUCCESS. If RED, revert the commit and add to `deferred-items.md`.
  </action>
  <read_first>
    - .planning/phases/61-cleanup-quality-gate/deferred-items.md (line 7 — playoffSeedRepository)
    - All 10 files in files_modified
    - src/test/java/org/ctc/domain/service/PlayoffServiceTest.java (verify no @Mock for playoffSeedRepository)
  </read_first>
  <acceptance_criteria>
    - `grep -rn "playoffSeedRepository" src/main src/test` returns 0 lines
    - For each removed private method: `grep -c "<methodName>" src/main/java/org/ctc/domain/service/<ServiceFile>` returns 0
    - `./mvnw test -Dtest='org.ctc.domain.service.*Test'` is BUILD SUCCESS after EACH commit
    - At least 1 atomic commit per removal (verify via `git log --oneline HEAD~10..HEAD | grep -c "^[a-f0-9]\+ refactor(61-gap)"`)
    - JaCoCo: re-run `./mvnw verify -DskipITs` and confirm line coverage stays >= 82% (if a removal drops coverage below 82%, the removed code WAS reachable via tests — revert and treat as NEEDS_CONTEXT)
  </acceptance_criteria>
</task>

<task id="2" name="Audit defensive null-guards at internal boundaries">
  <action>
    Apply the boundary-vs-internal classification to every defensive `if (x == null)` in the 10 listed files.

    Concrete observed candidates:

    **CASE 1: `StandingsService.java:200-201`**
    ```java
    var season = seasonRepository.findById(seasonId).orElse(null);
    if (season == null) return Map.of();
    ```
    Audit by tracing all callers:
    ```bash
    grep -rn "calculateStandings\|calculate.*Map\|<methodNameContainingThisCheck>" src/main/java
    ```
    Decision tree:
    - If `seasonId` always comes from a controller path-variable validated upstream → REMOVE the null-check;
      use `orElseThrow(() -> new EntityNotFoundException("Season", seasonId))` or trust the cascade.
    - If `seasonId` could be null from any caller → KEEP, but document boundary in Javadoc.

    **CASE 2: `StandingsService.java:270`** — `if (homeStanding == null) return;`
    Audit the prior line — is `homeStanding` from `map.get(...)`? If yes, it's a legitimate map-miss case
    (Java Map.get returns null for unknown keys); KEEP. If from an iteration over a non-null collection
    → REMOVE.

    **CASE 3: `SwissPairingService.resolveGroup` line 201-202** — `if (groupId == null) return null;`
    Audit: in the caller, is null a valid sentinel for "no group / LEAGUE layout"? If yes, this is an
    intentional null-sentinel API; KEEP and document with a one-line Javadoc above the method:
    `/** Resolves a group; returns null when groupId is null (LEAGUE-layout sentinel). */`

    **CASE 4: `FileStorageService.sanitize` line 117-119** — explicitly listed as boundary KEEP in `<context>`. No action.

    **For each REMOVAL:**
    - Write a new test asserting the post-removal invariant: e.g., for CASE 1, a test
      `givenNonExistentSeasonId_whenCalculateStandings_thenThrowsEntityNotFoundException()` (TDD: write the
      test BEFORE removing the null-check; confirm it RED with the old code returning `Map.of()`, then GREEN
      after removal switches to `orElseThrow`).
    - Commit message: `refactor(61-gap): trust internal contracts in <ServiceName>.<method> (remove defensive null-guard)`

    **For each KEEP (boundary or sentinel):**
    - Add a one-line Javadoc on the surrounding method documenting the boundary or sentinel semantics.
    - Commit message: `docs(61-gap): document boundary null-handling in <ServiceName>.<method>`

    Document each disposition in 61-gap-06-SUMMARY.md as a table: `<file:line>` | `KEEP/REMOVE` | rationale.
  </action>
  <read_first>
    - All 10 files in files_modified (re-read after Task 1 changes)
    - CLAUDE.md "validate at system boundaries"
    - .planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md (G4 reasoning)
  </read_first>
  <acceptance_criteria>
    - For each defensive null-check audited, the SUMMARY contains a row classifying it as KEEP (boundary/sentinel) or REMOVE (impossible case)
    - Each REMOVAL has a corresponding TDD-style commit (test added before removal — verify by `git log --oneline HEAD~5..HEAD` showing test-first commits)
    - `./mvnw test -Dtest='org.ctc.domain.service.*Test'` is BUILD SUCCESS at every step
    - JaCoCo line coverage stays >= 82%
    - For each KEEP, the surrounding method has Javadoc explaining the boundary/sentinel
  </acceptance_criteria>
</task>

<task id="3" name="Final verification + JaCoCo re-check">
  <action>
    Run a comprehensive verification pass:

    1. Full Surefire + JaCoCo:
       ```bash
       ./mvnw verify -DskipITs
       ```
       Expected: BUILD SUCCESS. Line coverage >= 82% (record exact percentage in SUMMARY).

    2. Re-grep for residual stale defensive checks:
       ```bash
       grep -rn -E "if \([a-zA-Z]+ == null\) (return|throw|continue)" src/main/java/org/ctc/domain/service
       ```
       Each remaining hit MUST appear in the SUMMARY as either KEEP-boundary or KEEP-sentinel.

    3. Check that no test was accidentally removed:
       ```bash
       grep -c "@Test" src/test/java/org/ctc/domain/service/*.java | awk -F: '{s+=$2} END {print s}'
       ```
       Compare against baseline noted at start of plan.

    4. If all green, commit a final summary commit only if needed (otherwise no commit — work is in Tasks 1-2):
       Commit message: `chore(61-gap): final verification of domain.service dead-code sweep`
  </action>
  <read_first>
    - Outputs of Tasks 1 + 2
    - pom.xml (jacoco threshold)
  </read_first>
  <acceptance_criteria>
    - `./mvnw verify -DskipITs` is BUILD SUCCESS
    - JaCoCo line coverage >= 82% (preferably unchanged or higher; record in SUMMARY)
    - All residual `if (x == null)` lines in domain.service are explicitly classified in SUMMARY
    - Test count in domain.service has not decreased (no accidental test deletion)
  </acceptance_criteria>
</task>

</tasks>

<verification>
- ./mvnw verify -DskipITs BUILD SUCCESS
- JaCoCo line coverage >= 82% with explicit pre/post numbers in SUMMARY
- deferred-items.md PlayoffService.playoffSeedRepository entry resolved (removed) OR escalated
- Each removal is a separate commit (bisect-friendly)
- For each defensive null-check: explicit KEEP/REMOVE classification in SUMMARY
</verification>

<success_criteria>
1. PlayoffService.playoffSeedRepository unused field removed
2. All defensive null-guards in domain.service classified (boundary vs internal)
3. Internal-only null-guards REMOVED with TDD-style regression tests
4. Boundary null-guards KEPT with documenting Javadoc
5. Coverage >= 82%
6. Atomic commits enable bisect on regressions
</success_criteria>

<output>
Create `.planning/phases/61-cleanup-quality-gate/61-gap-06-SUMMARY.md` containing:
- Removed dead code (file:line, why-safe-to-remove, regression test added)
- KEPT defensive checks (file:line, KEEP rationale: boundary or sentinel)
- Pre/post JaCoCo line coverage percentages
- Any NEEDS_CONTEXT items (uncertain removals deferred to deferred-items.md)
</output>
