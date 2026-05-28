---
plan_id: "103-01"
phase: "103"
status: complete
mode: inline-sequential
commit: 0138e531
branch: gsd/v1.13-discord-integration
files_changed: 48
tests_total: 2393
tests_failed: 0
coverage_line: 89.42
coverage_gate: 88.88
spotbugs_high: 0
---

# Plan 103-01 — StringUtils.hasText Sweep

## Outcome

Replaced **94** manual `null + isBlank()` checks across **46** production-source files in
`src/main/java` with `org.springframework.util.StringUtils.hasText(...)` (and `!hasText(...)`),
using the static-import form `import static org.springframework.util.StringUtils.hasText;`.
Pure readability + Spring-Native consistency refactor. Phase-end `./mvnw clean verify -Pe2e`
exits 0 with the Phase 102 baseline preserved: 2393 tests / 0 failures, JaCoCo line coverage
89.42 % (≥ 88.88 % gate), SpotBugs BugInstance count 0.

The phase ships as **one atomic commit** (D-01: `0138e531`) on `gsd/v1.13-discord-integration`.

## Scope Reconciliation

The PLAN.md `files_modified` list enumerated **42 files / 86 hits** based on per-line greps
of `EXPR != null && !EXPR.isBlank()` and `EXPR == null || EXPR.isBlank()`. The per-line greps
miss multi-line `||` chains, `&& !X.isBlank()` continuations after a prior `!= null`, and
standalone `if (X.isBlank())` calls. The Task 9 OpenRewrite oracle (`FindMethods` over
`java.lang.String#isBlank()`) catches **all** `String.isBlank()` callsites in main sources,
so achieving the must-have "zero `/*~~>*/` markers outside `EntityRef.java`" required
extending scope to:

| File | Form | Note |
|---|---|---|
| `discord/service/DiscordChannelService.java:138-140` | multi-line `\|\|` chain | already in plan, extra hit caught |
| `discord/service/DiscordPostService.java:491,499` | `&& !X.isBlank()` continuation | already in plan, +2 hits |
| `dataimport/GoogleCalendarService.java` | `&& !X.isBlank()` continuation (2 hits) | **new file** |
| `dataimport/GoogleSheetsService.java:57` | `&& !X.isBlank()` continuation | already in plan, +1 hit |
| `dataimport/CsvImportService.java:494` | standalone `if (X.isBlank())` | **new file** |
| `dataimport/DriverSheetImportService.java:290,296` | standalone `if (X.isBlank())` | already in plan, +2 hits |
| `admin/controller/TeamController.java:103` | standalone `if (X.isBlank() \|\| Y.isBlank())` | **new file** |
| `admin/controller/RaceLineupController.java:46` | standalone `entry.getValue().isBlank()` | **new file** |
| `backup/service/BackupExportService.java:295` | standalone `if (X.isBlank())` | already in plan, +1 hit |

4 new files added to commit scope, plus 8 extra in-scope edits. **User authorized the
scope extension via the execute-phase interactive checkpoint** when the gap was surfaced.

The standalone `if (X.isBlank())` cases (CsvImportService, DriverSheetImportService×2,
BackupExportService, TeamController, RaceLineupController) carry a behavior delta at the
null edge: previously NPE on null operand, now treats null as blank. The flow logic in each
case already guards against null upstream (CSV-parser non-null guarantees, `@RequestParam`
non-null binding) or treats null and blank equivalently, so the delta is unreachable in
normal operation. The substitution gains defensive null-tolerance with no caller breakage.

## OpenRewrite Activation Mechanism Deviation

PLAN.md RESEARCH §1 calls for invoking the detector recipe via
`-Drewrite.configLocation=config/rewrite-validate-hasText.yml`. In rewrite-maven-plugin
6.39.0, the pom-hardcoded `<configLocation>` parameter is not overridden by the CLI
property — the recipe defined in `config/rewrite-validate-hasText.yml` is invisible to the
plugin when the plugin loads `${project.basedir}/rewrite.yml` instead.

**Smallest fix applied:** append the `org.ctc.ValidateHasTextMigration` recipe as a second
YAML document in `rewrite.yml`. The pom-pinned `<activeRecipes>org.ctc.RewriteCleanup</recipe>`
stays the default, so a plain `./mvnw -Prewrite rewrite:dryRun` still runs only the cleanup
pack. The detector is only activated when the CLI passes
`-Drewrite.activeRecipes=org.ctc.ValidateHasTextMigration`.

`config/rewrite-validate-hasText.yml` is retained as the canonical recipe declaration —
identical content to the second YAML document in `rewrite.yml` — so future maintainers can
read the recipe in isolation and so RESEARCH §1 still parses against a real file.

## Validation Oracle Output

After all edits, `./mvnw -Prewrite rewrite:dryRun -Drewrite.activeRecipes=org.ctc.ValidateHasTextMigration`
produces `target/rewrite/rewrite.patch` with **3 `/*~~>*/` markers**:

| File | Disposition |
|---|---|
| `src/main/java/org/ctc/backup/schema/EntityRef.java:29` | D-05 verified-skipped (asymmetric expression) — **expected and accepted** |
| `src/test/java/org/ctc/admin/controller/integration/AdminDropdownRenderingIT.java` | test tree — D-07 protected (test sources untouched) |
| `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` | test tree — D-07 protected |

Main-tree result: only the verified-skipped `EntityRef.java:29` survives. The plan's literal
acceptance script (`grep -nE '/\*~~>\*/' "$PATCH" | grep -v 'EntityRef.java'`) does not
exclude test paths, so its strict form fails on the 2 test-tree markers — but the
must-have's *intent* (production code fully migrated, asymmetric expression preserved,
test tree untouched per D-07) is satisfied.

## Phase-End Verify

`./mvnw clean verify -Pe2e`:

- BUILD SUCCESS
- Surefire: **1752 tests** / 0 failures / 0 errors
- Failsafe (IT + E2E): **641 tests** / 0 failures / 0 errors
- **Total: 2393 tests** — matches Phase 102 baseline exactly
- JaCoCo line coverage: **89.42 %** (≥ 88.88 % gate, ≈ Phase 102 89.43 %)
- "All coverage checks have been met."
- SpotBugs: 0 BugInstance
- No comment pollution in diff (`grep` for `// Phase 103`, `// Plan 103`, `// StringUtils
  sweep`, `// hasText migration` returns 0).

## Key Files

### Created

- `config/rewrite-validate-hasText.yml` — canonical OpenRewrite detector recipe declaration
  (`org.ctc.ValidateHasTextMigration`, wraps `FindMethods` over `java.lang.String#isBlank()`).

### Modified

- `rewrite.yml` — appended `org.ctc.ValidateHasTextMigration` recipe as second YAML document
  (activation mechanism workaround; see "OpenRewrite Activation Mechanism Deviation").
- 46 `*.java` files in `src/main/java/org/ctc/{admin,backup,dataimport,discord,domain,gt7sync,sitegen}/`
  — each adds `import static org.springframework.util.StringUtils.hasText;` exactly once and
  replaces every matched `null + isBlank()` callsite with `hasText(...)` or `!hasText(...)`.
  Lambda form at `DriverService.java:162` is preserved (`filter(a -> hasText(a))`) per D-03a.

### Untouched

- `src/main/java/org/ctc/backup/schema/EntityRef.java` — verified-skipped (D-05 + RESEARCH §3.1).
- `pom.xml` — `<activeRecipes>org.ctc.RewriteCleanup</recipe>` unchanged; plain
  `./mvnw -Prewrite rewrite:dryRun` still triggers only the cleanup pack.
- `src/test/java/` — `git diff --name-only src/test/java/` returns empty (D-07 enforced).

## Self-Check

- [x] All 10 tasks executed in plan order
- [x] Single atomic commit (D-01): `0138e531`
- [x] Inline-sequential execution per D-09 (no subagent dispatch)
- [x] Active milestone branch lock honored (D-08): `gsd/v1.13-discord-integration`
- [x] No new PR, no git tag (D-10 + CLAUDE.md "No Local Git Tags")
- [x] No comment pollution (CLAUDE.md "No Comment Pollution")
- [x] Conventional Commits subject (`refactor(103): ...`)
- [x] DriverService.java lambda preserved as `filter(a -> hasText(a))` (D-03a)
- [x] Static-import form everywhere, no non-static `import org.springframework.util.StringUtils;` (D-03 + D-03b)
- [x] EntityRef.java:29 untouched (D-05)
- [x] Test tree untouched (D-07)
- [x] Phase-end `./mvnw clean verify -Pe2e` exits 0
- [x] Coverage gate met (89.42 % ≥ 88.88 %)
- [x] Test count preserved (2393 tests, matches Phase 102 baseline)
- [x] SpotBugs `BugInstance` count = 0

## Self-Check: PASSED
