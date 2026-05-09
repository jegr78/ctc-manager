# Phase 66: Team ShortName Collision Fix (Driver Import) — Research

**Researched:** 2026-05-07
**Domain:** Spring Data JPA derived queries · Mockito stub migration · JaCoCo coverage delta
**Confidence:** HIGH (all claims verified against project code or migrations)

---

## Phase Summary

This is a tightly-scoped backend hotfix for `DriverSheetImportService`. The current `teamRepository.findByShortName(String) -> Optional<Team>` collapses on multi-row matches (parent + sub-team sharing a `shortName`, e.g. parent `ZFS` + sub `ZFS`) and surfaces a 500 (`IncorrectResultSizeDataAccessException`) at the preview crash site (line 296) and four downstream execute call sites (lines 135, 146, 166, 195). CONTEXT.md fully locks the resolution policy (multi-match → prefer parent; single-match → use it; no-match → existing `UNKNOWN_TEAM_CODE` flow). This research adds **only** the planner-relevant implementation pitfalls, the test-stub migration map (with one CONTEXT.md count correction), JaCoCo delta risk, threat-model surface, and the Nyquist Dimension 8 validation architecture.

---

## Implementation Pitfalls

### 1. Spring Data Derivation Correctness — `List<Team> findAllByShortName(String)` ✅

**Verdict:** Safe. Spring Data parses the method name as `findAllBy{Property}` → `SELECT t FROM Team t WHERE t.shortName = :shortName`. The plural `All` keyword is the documented spelling for **list-returning** derived queries (singular `findBy...` returning `List` also works, but the explicit `All` form is conventional and self-documenting).

**Project precedent for `findAllBy...` shape** [VERIFIED: codebase grep]:
- `CarRepository.findAllByOrderByManufacturerAscNameAsc()` — `src/main/java/org/ctc/domain/repository/CarRepository.java:11`
- `TrackRepository.findAllByOrderByNameAsc()` — `src/main/java/org/ctc/domain/repository/TrackRepository.java:10`

Both confirm the project relies on Spring Data derivation for list returns. No `@Query` annotation needed. The generated JPQL uses portable equality semantics (`=`) and runs identically on H2 (test/dev) and MariaDB (local/prod). [VERIFIED: matches the existing `findByShortName` derivation that already runs on both DBs.]

**One detail worth a Javadoc note:** the method name **must be `findAllByShortName`** — not `findByShortNameIn` (different semantics) and not `findByShortNameAll` (Spring Data won't parse). The planner should pin the exact spelling in the plan to avoid bikeshedding.

---

### 2. Test Stub Migration — 18 stubs, not 17 ⚠️ **CONTEXT.md correction**

**Verdict:** CONTEXT.md (D-13, line 26) says "17 mock stubs." [VERIFIED: `grep -n "findByShortName" DriverSheetImportServiceTest.java`] reports **18 stub lines.** The plan must size for 18.

Two stubs use distinct shortNames per row (`"AHR"` vs `"CRL"` in the same test, e.g. lines 484/485, 543/544) — they're independent stubs that each migrate one-for-one. One stub stubs the empty case (`"XYZ"` line 459) — also a one-for-one migration to `findAllByShortName("XYZ") → List.of()`.

**Mock-call expectation pattern (per stub):**
```java
// Before:
when(teamRepository.findByShortName("AHR")).thenReturn(Optional.of(teamAhr));
// After:
when(teamRepository.findAllByShortName("AHR")).thenReturn(List.of(teamAhr));

// Empty case before:
when(teamRepository.findByShortName("XYZ")).thenReturn(Optional.empty());
// After:
when(teamRepository.findAllByShortName("XYZ")).thenReturn(List.of());
```

Full line-by-line map below in **Test Stub Migration Map**.

---

### 3. JaCoCo Coverage Delta Risk — Low, but pin the assertions 📊

**Project gate** [VERIFIED: `pom.xml:241`]: `<minimum>0.82</minimum>` enforced at BUNDLE level on LINE counter via `jacoco:check`. `TestDataService.class` is JaCoCo-excluded [VERIFIED: `pom.xml:204`], so its team-creation code does not contribute to coverage either way.

**Current empirical coverage of `DriverSheetImportService`:** Cannot read directly — no `target/site/jacoco/jacoco.csv` exists in the working tree (would require a fresh `./mvnw verify` run). [ASSUMED — confirm in Wave 0 if planner wants a baseline number, otherwise it's not load-bearing.] The class is one of the longer service classes (525 lines, 5 nested types) with 18 mock-driven unit tests already exercising every preview-bucket branch. The 5 production call sites all currently route through one method (`findByShortName`), so they're already in the covered set.

**Delta from the fix** (cyclomatic complexity 4 helper, 6 instructions ≈ 6 lines):
1. `if (matches.isEmpty()) return Optional.empty();` — covered by existing UNKNOWN_TEAM_CODE test (line 459 stub now returns `List.of()`).
2. `if (matches.size() == 1) return Optional.of(matches.get(0));` — covered by all 17 single-match existing tests (which all migrate to `List.of(team)`).
3. `parent = matches.stream().filter(t -> t.getParentTeam() == null).findFirst()` — covered by the new TDD test (D-11).
4. `if (parent.isPresent()) return parent;` — covered by the new TDD test.
5. `else { log.warn(...); return Optional.of(matches.get(0)); }` — covered by the new defensive multi-parent test (D-12).

**Risk if any branch is uncovered:** Adding ~6 covered lines to a service that today reports (estimated) ≥ 90% line coverage **raises** the bundle-level ratio. The only failure mode is forgetting the multi-parent defensive test, which would leave the `else` branch (1 line) uncovered. That single uncovered line cannot move the BUNDLE ratio below 0.82 by any realistic margin — there are 31k+ instrumented lines in the project [VERIFIED: 877 tests baseline from project memory].

**Recommendation to the planner:** Don't budget a coverage rescue task. The TDD test for D-11 + the defensive test for D-12 are sufficient. If `./mvnw verify` reports < 82% post-fix, root cause is **not** this phase.

---

### 4. Multi-Parent Edge Case — Confirmed unreachable in shipped code paths ✅

**The edge case:** Two teams in `teams` with `parent_team_id IS NULL` and the same `short_name`. The resolver's `else { log.warn(...); return matches.get(0); }` branch handles it.

**Verification across all data sources:**

| Source | Verdict | Evidence |
|---|---|---|
| `V1__initial_schema.sql` | No UNIQUE on `(short_name)` or `(parent_team_id, short_name)` | [VERIFIED: lines 45–57] |
| `V2__add_fk_indexes.sql` | Adds index on `parent_team_id` only — no UNIQUE | [VERIFIED: line `idx_teams_parent_team_id`] |
| `V3__add_season_phase_tables.sql` | No `teams` table changes | [VERIFIED: grep "teams"] |
| `TestDataService.java` (excluded from JaCoCo, used by dev profile) | All 10 parent shortNames distinct: `VRX, SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR`. Sub-teams use suffixed shortNames (`VRX A`, `VRX B`, `SGM B`, `SGM S`, `TBR R`, `TBR B`, `TBR G`). E2E fixtures: `T-ALF, T-BRV, T-BRV 1, T-BRV 2` — also unique per row | [VERIFIED: lines 136–145, 173–185, 859–862] |
| `DevDataSeeder.java` | Just calls `testDataService.seed()` — no teams of its own | [VERIFIED: 33-line file] |

**Conclusion:** The `log.warn` branch is **defensively unreachable in production seed data.** It only fires if a human or external migration inserts two parent teams with the same `shortName` — which is a data-integrity issue. The planner should keep the warning at WARN (not ERROR — the import still succeeds, which is the whole point of the fix). The test for this branch (D-12) is a regression fence, not a real-world flow exercise.

---

### 5. Existing `findByShortName` Test Callers — No Collision Risk ✅

CONTEXT.md (`<deferred>` line 32 + `<canonical_refs>` lines 126–127) states callers in `TeamControllerTest` and `GroupsSeasonE2ETest` use unique test-prefix shortNames. **Verified explicitly:**

| File | Line | shortName | Collision-safe? | Why |
|---|---|---|---|---|
| `TeamControllerTest.java` | 64, 69 | `"MVR"` | ✅ | The test creates `MockMvc Racing` with shortName `MVR` via `/admin/teams/save`, then reads it back. No other team in the test fixture or in DB seed (which test profile starts empty / H2 in-memory) shares this shortName. [VERIFIED: grep — `MVR` appears only at lines 64 and 69 of `TeamControllerTest.java`. TestDataService does not seed `MVR`.] |
| `GroupsSeasonE2ETest.java` | 105–106, 184, 187, 189, 193–196, 285–288 | `T-GA-1, T-GA-2, T-GB-1, T-GB-2` | ✅ | E2E test creates 4 fresh teams via the admin UI with explicitly unique `T-G*` prefixes. Test prefix isolates them from any user data. No sub-teams created. Cannot collide. [VERIFIED: lines 105–106, 184–196.] |

**No changes needed in either file.** Keeping `findByShortName` is the right call (D-04).

---

### 6. Threat Model Surface — Negligible 🛡️

This is a 500→200 fix in a **server-side rendered admin import flow**. Concrete surface analysis:

- **Auth/authz changes:** None. `DriverSheetImportController` and `DriverSheetImportService` are unchanged behaviorally; only the helper method between them changes. The `prod`/`docker` profile auth gate (per CLAUDE.md Constraints) sees the same endpoint with the same method signature.
- **Data leak risk in error messages:** The fix **removes** a 500 stack trace that previously exposed a JPA exception (`NonUniqueResultException`) to the user via `GlobalExceptionHandler`. Net positive — less internal information leaks to authenticated admins.
- **Input validation surface:** The shortName comes from a Google Sheet cell (`cellToString` line 390) and goes into a derived JPQL `=` predicate. Spring Data parameter-binds this; no string concatenation, no SQL injection vector. [VERIFIED: derived query uses `?1` placeholder by Spring Data convention.]
- **Mass assignment / DTO drift:** No DTO changes in this phase.
- **Logging hygiene:** The `log.warn` for the multi-parent edge case will print the `shortName` value (untrusted but already trimmed and bounded by VARCHAR(50)). No PII; team shortNames are public artifact names. Safe to log.

**Net STRIDE assessment:** No new threats introduced; one existing **Information Disclosure** weakness (the 500 stack trace via `GlobalExceptionHandler`) is mitigated as a side-effect.

---

### 7. Validation Strategy — Minimal Viable for a Hotfix ⏱️

See full **Validation Architecture** section below. Key takeaways for the planner:

- The phase is an N=1-test regression fence (D-11) plus mechanical stub migration (D-13) plus one defensive test (D-12). No new integration test (D-14). No new E2E (the existing `DriverSheetImportE2ETest` — if any — transitively exercises the `preview` path).
- The verify command is the standard `./mvnw verify` (Surefire + JaCoCo). No `-Pe2e` needed for the phase gate; final UAT will run `-Pe2e` per project memory.
- Test sampling is fast: a single class invocation `./mvnw test -Dtest=DriverSheetImportServiceTest` runs in < 10s once compiled — viable as a per-commit quick gate.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Mockito 5 + AssertJ |
| Config file | `pom.xml` (Surefire + JaCoCo plugins) |
| Quick run command | `./mvnw test -Dtest=DriverSheetImportServiceTest` |
| Full suite command | `./mvnw verify` |
| E2E command (out of phase scope) | `./mvnw verify -Pe2e` (used at UAT only per memory) |

### Phase Requirements → Test Map

| Req | Behavior | Test Type | Automated Command | File Exists? |
|-----|----------|-----------|-------------------|-------------|
| D-11 | Parent + sub `ZFS` → resolver returns parent | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenTeamsWithSameShortNameParentAndSub_whenPreview_thenResolvesParentTeam` | ❌ Wave 0 — new test |
| D-12 (defensive) | Two `parentTeam IS NULL` matches → first wins, no exception, log.warn | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenTwoParentTeamsWithSameShortName_whenPreview_thenFirstWinsWithoutException` | ❌ Wave 0 — new test |
| Existing UNKNOWN_TEAM_CODE | Empty list → existing error flow | unit (existing, stub migrated) | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenUnknownTeamCode_whenPreview_thenRowErroredWithUnknownTeam` | ✅ exists at line 453 |
| Existing single-match path | List of one → use it (preview + execute) | unit (existing, 17 stubs migrated) | `./mvnw test -Dtest=DriverSheetImportServiceTest` | ✅ all 18 tests, stubs only |
| Repository derivation works on H2 | `findAllByShortName` derives valid JPQL | implicit (transitive) | Existing `@DataJpaTest` / `@SpringBootTest` covers via the import path; no dedicated IT (D-14) | ✅ implicit |
| JaCoCo line coverage ≥ 82% | Fix doesn't regress coverage gate | gate | `./mvnw verify` (jacoco:check) | ✅ gate exists |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=DriverSheetImportServiceTest` (< 10s)
- **Per wave merge:** `./mvnw verify` (full Surefire + JaCoCo gate)
- **Phase gate (before `/gsd-verify-work`):** `./mvnw verify` green + manual UAT once on dev profile with `--auto --chain` driver-sheet-import on a sheet that contains a row whose team-code matches a parent + sub.
- **UAT only:** `./mvnw verify -Pe2e` (Playwright E2E, not required for phase gate per CLAUDE.md test-call-optimization memory)

### Wave 0 Gaps

- ❌ New unit tests in `DriverSheetImportServiceTest.java`:
  - `givenTeamsWithSameShortNameParentAndSub_whenPreview_thenResolvesParentTeam` (D-11)
  - `givenTwoParentTeamsWithSameShortName_whenPreview_thenFirstWinsWithoutException` (D-12)
- No new fixtures/helpers needed — the existing `setupSheetsStub()` and `oneDataRow()` helpers (lines 113, 123) cover the input shape; only the `Team` fixture needs an extra `parentZfs` + `subZfs` pair declared in the test method body (no need to extend `@BeforeEach`).
- No framework install: JUnit 5 + Mockito + AssertJ + Surefire all already configured.
- No new integration test (D-14 explicitly defers).

### Coverage Sanity

- **Pre-fix baseline:** ≥ 82% (gated). [ASSUMED specific value of `DriverSheetImportService` — would require `./mvnw verify` to confirm; not load-bearing per Pitfall #3.]
- **Post-fix expectation:** ≥ 82% (the new lines are covered by the new tests; 17 stub migrations don't affect coverage since they exercise the same branches).
- **Verification:** `./mvnw verify` exits 0 → gate green. `target/site/jacoco/index.html` line count for `DriverSheetImportService` should rise by ~6.

---

## Test Stub Migration Map

**File:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java`

**Total stubs to migrate: 18** (CONTEXT.md says 17 — see Pitfall #2 above; planner should size for 18).

**Find shape:**
```java
when(teamRepository.findByShortName("<X>")).thenReturn(Optional.of(<team>));
// or
when(teamRepository.findByShortName("<X>")).thenReturn(Optional.empty());
```

**Replace shape:**
```java
when(teamRepository.findAllByShortName("<X>")).thenReturn(List.of(<team>));
// or
when(teamRepository.findAllByShortName("<X>")).thenReturn(List.of());
```

**Imports already present in test file:** `java.util.*` is wildcard-imported (line 33) → `List` is already in scope. No import changes needed.

| # | Line | shortName | Replacement RHS | Notes |
|---|------|-----------|----------------|-------|
| 1 | 243 | `"AHR"` | `List.of(teamAhr)` | NEW_DRIVER test |
| 2 | 270 | `"AHR"` | `List.of(teamAhr)` | NEW_ASSIGNMENT test |
| 3 | 294 | `"AHR"` | `List.of(teamAhr)` | NEW_ASSIGNMENT (ambiguous season) |
| 4 | 325 | `"CRL"` | `List.of(teamCrl)` | CONFLICT test |
| 5 | 356 | `"AHR"` | `List.of(teamAhr)` | FUZZY_SUGGESTION test |
| 6 | 386 | `"AHR"` | `List.of(teamAhr)` | UNCHANGED test |
| 7 | 459 | `"XYZ"` | `List.of()` | UNKNOWN_TEAM_CODE — empty case |
| 8 | 484 | `"AHR"` | `List.of(teamAhr)` | DUPLICATE_IN_TAB — first row |
| 9 | 485 | `"CRL"` | `List.of(teamCrl)` | DUPLICATE_IN_TAB — second row (paired with #8) |
| 10 | 511 | `"AHR"` | `List.of(teamAhr)` | MATCH-01 case-insensitive |
| 11 | 543 | `"AHR"` | `List.of(teamAhr)` | MATCH-02 cross-tab — first tab |
| 12 | 544 | `"CRL"` | `List.of(teamCrl)` | MATCH-02 cross-tab — second tab (paired with #11) |
| 13 | 573 | `"AHR"` | `List.of(teamAhr)` | legacy 4-digit tab pattern |
| 14 | 601 | `"AHR"` | `List.of(teamAhr)` | numbered `2025_S2` tab |
| 15 | 672 | `"AHR"` | `List.of(teamAhr)` | group resolution via PhaseTeam |
| 16 | 699 | `"AHR"` | `List.of(teamAhr)` | warning emitted when team not in REGULAR phase |
| 17 | 732 | `"AHR"` | `List.of(teamAhr)` | warning deduplicated per team |
| 18 | 755 | `"AHR"` | `List.of(teamAhr)` | no REGULAR phase → resolvedGroupName null |

**Mechanical migration:** A multi-cursor or `sed`-style replace works since the find shape has only two distinct forms (`Optional.of(<X>)` or `Optional.empty()`). The planner should **not** parametrize this — line-by-line edit keeps reviewability per CLAUDE.md "Code-Review your own changes."

**No call-verification asserts to migrate:** [VERIFIED: grep] no `verify(teamRepository).findByShortName(...)` asserts exist in the test file. Only `when()` stubs.

---

## Threat Model Inputs

For the planner's `<threat_model>` block in `66-01-PLAN.md`:

| STRIDE | Asset | Vector | Mitigation in this phase |
|--------|-------|--------|--------------------------|
| **S** Spoofing | None | n/a | No auth surface change. |
| **T** Tampering | `teams.short_name` value | Sheet-supplied team code | Already mitigated: parameter-bound via Spring Data derivation; no string concatenation. |
| **R** Repudiation | Import audit | Multi-parent edge resolution | `log.warn(...)` records the resolution decision (defensive only). |
| **I** Information Disclosure | JPA stack trace via `GlobalExceptionHandler` | Crash on multi-match | **This phase mitigates** the leak by replacing the 500 with a graceful Optional-returning path. |
| **D** Denial of Service | Driver import flow | Repeated 500s on every preview if collision exists | **This phase mitigates** by making the import resilient to the documented data shape. |
| **E** Elevation of Privilege | None | n/a | No privilege boundary change. |

**Net assessment:** This fix is **risk-reducing.** It removes one Information Disclosure weakness and one Denial-of-Service-via-crash flow. No new attack surface. The planner can mark `threat_model: low-risk hotfix` with mitigations summarized as "derived JPQL parameter binding (existing) + graceful Optional return (new)."

---

## Open Questions

*(Empty — CONTEXT.md is comprehensive. The one CONTEXT.md correction (17 vs 18 stubs) is informational; the resolution policy and plan structure remain valid.)*

---

## Sources

### Primary (HIGH confidence)
- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` — 5 call sites verified at lines 135, 146, 166, 195, 296
- `src/main/java/org/ctc/domain/repository/TeamRepository.java` — 14-line file, only `findByShortName` and `findByShortNameIgnoreCase` declared
- `src/main/java/org/ctc/domain/model/Team.java` — `parentTeam` self-FK + `getParentOrSelf()` confirmed at line 72
- `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` — 18 stub lines enumerated above (CONTEXT.md count was 17)
- `src/main/resources/db/migration/V1__initial_schema.sql` — no UNIQUE on `teams.short_name` confirmed at lines 45–57
- `src/main/resources/db/migration/V2__add_fk_indexes.sql`, `V3__add_season_phase_tables.sql` — confirmed no later UNIQUE constraint added
- `pom.xml` lines 198–249 — JaCoCo BUNDLE LINE coverage ≥ 0.82 gate; `TestDataService.class` excluded
- `src/main/java/org/ctc/admin/TestDataService.java` — 10 distinct parent shortNames + suffixed sub-team shortNames; no duplicate-parent risk in seed data
- `src/main/java/org/ctc/admin/DevDataSeeder.java` — 33-line trivial wrapper, calls `TestDataService.seed()`
- `src/main/java/org/ctc/domain/repository/CarRepository.java`, `TrackRepository.java` — confirm `findAllBy...` derivation precedent
- `src/test/java/org/ctc/admin/controller/TeamControllerTest.java` line 64–69 — verified `"MVR"` is unique
- `src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java` lines 105–196, 285–288 — verified `T-G*` test-prefix shortNames are unique
- `.planning/config.json` — `nyquist_validation: true` confirms Validation Architecture section is required; `security_enforcement` absent → treat as enabled

### Secondary (MEDIUM confidence)
- `CLAUDE.md` § Constraints, § Architectural Principles, § Development Approach — referenced for TDD, JaCoCo gate, branch rules
- Project memory `feedback_test_call_optimization.md` — supports per-commit `./mvnw test -Dtest=...` over full `./mvnw verify`
- Project memory `feedback_e2e_verification.md` — supports `-Pe2e` at UAT only, not phase gate

### Tertiary (LOW confidence — flagged for validation)
- *(none)*

---

## Metadata

**Confidence breakdown:**
- Standard stack (Spring Data derivation, Mockito, JUnit 5): HIGH — codebase has identical patterns.
- Test stub migration map (18 entries): HIGH — line numbers and shortNames verified by grep.
- JaCoCo delta risk: MEDIUM — pre-fix exact coverage of `DriverSheetImportService` not measured; conclusion ("no risk to bundle gate") is robust to wide range of plausible values.
- Multi-parent edge case unreachability in seed data: HIGH — exhaustive review of all `Team` constructor calls in `TestDataService` and `DevDataSeeder`.
- Threat model: HIGH — no privilege/data flow change; surface is fully bounded by existing Spring Data parameter binding.

**Research date:** 2026-05-07
**Valid until:** ~30 days (the codebase moves fast on the v1.9 branch; if the planner hasn't started by 2026-06-07, re-confirm test stub line numbers — file is in active development).

---

## RESEARCH COMPLETE
