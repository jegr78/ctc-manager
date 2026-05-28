# Phase 103: StringUtils Blank-Check Sweep — Research

**Researched:** 2026-05-28
**Domain:** Pure mechanical refactor — Spring-Native preference for blank-string checks
**Confidence:** HIGH

## Summary

Phase 103 hand-substitutes 86 occurrences (40 positive + 46 negative form) of manual
`s != null && !s.isBlank()` / `s == null || s.isBlank()` patterns across **43 production-source
files** with `org.springframework.util.StringUtils.hasText(s)` and `!hasText(s)` under D-03's
static-import style. All locked decisions D-01..D-10 in CONTEXT.md remain authoritative.

The research below fills the remaining planner-input gaps:

1. **No existing OpenRewrite recipe ships the swap.** `rewrite-spring 6.30.4`,
   `rewrite-migrate-java 3.34.1`, and `rewrite-static-analysis 2.34.1` contain zero recipes
   that convert manual null-and-blank checks into `StringUtils.hasText(...)`. The validation
   gate per D-02 must therefore be a **custom in-repo detector recipe**. A minimal YAML
   skeleton using `org.openrewrite.java.search.FindMethods` against
   `java.lang.String isBlank()` produces a deterministic dryRun patch with `/*~~>*/` markers
   on every surviving `.isBlank()` callsite — exactly the oracle D-02 needs.
2. **Grep oracle confirms exactly 40 + 46 = 86 hits in 43 files.** No drift since CONTEXT.md.
   Zero hits in `src/test/java`.
3. **Exactly one genuine `NEEDS_CONTEXT` candidate:** `EntityRef.java:29`
   `tableAnno != null && !tableAnno.name().isBlank()` — the second side calls `.name()` on
   `tableAnno`, so a direct rewrite to `hasText(tableAnno.name())` would NPE if `tableAnno`
   is null. This callsite is part of the 40-hit positive count and must be skipped per D-05.
4. **Three ternaries are safe to substitute** (`DiscordGlobalConfigService:52`,
   `DiscordPostService:676`, `TeamManagementService:249`) — the substitution preserves
   semantics and parenthesization.
5. **Zero import collisions.** No file in `src/main/java` currently imports
   `org.springframework.util.StringUtils`, Apache Commons `StringUtils`, or Guava `Strings`.
   No custom `hasText(...)` method exists anywhere in `src/main/java`. D-03b's
   no-co-existence rule is trivially satisfied; no D-03c locking decision needed beyond a
   one-line restatement.
6. **Recommended task order:** discord (38 hits, v1.13 hot zone) → domain.model (9, shared
   surface) → domain.service (6, scoring/sitegen coupling) → admin.controller (7) →
   admin.service (3) → dataimport (5) → backup (4) → sitegen (4) → gt7sync (1).

**Primary recommendation:** Author a 12-line `config/rewrite-validate-hasText.yml` (or inline
into existing `rewrite.yml` as a second recipe), wire it via `-Drewrite.activeRecipes=…` in a
single `./mvnw -Prewrite rewrite:dryRun` invocation immediately before the phase-end
`./mvnw clean verify -Pe2e`. Zero `~~>` markers in `target/site/rewrite/rewrite.patch` is
the binary pass criterion.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 Plan Granularity:** Single plan covering all 43 files in one sweep. No per-package
  or per-batch plan-split. Planner MAY internally batch by package for executor turn-taking;
  the unit of work is one plan with one final commit.
- **D-02 Refactor Mechanism:** Hand-edit primary, OpenRewrite as end-of-plan validation gate.
  Executor grep-finds and hand-edits each occurrence (1:1 substitution + import-statement
  addition). At plan close — *before* the final `clean verify -Pe2e` — the executor runs
  `./mvnw -Prewrite rewrite:dryRun` with a validation-recipe scope to detect any forgotten
  patterns in `src/main/java`.
- **D-03 Import Style:** `import static org.springframework.util.StringUtils.hasText;` —
  call sites read `hasText(s)` / `!hasText(s)`. Densest readability form.
- **D-03a DriverService.java:160 stays a lambda:** static import forbids
  `StringUtils::hasText` method-reference (no class qualifier in scope). Line becomes
  `filter(s -> hasText(s))`, NOT `filter(StringUtils::hasText)`.
- **D-03b No co-existing imports:** A file gets EITHER `import static …hasText;` OR no
  import. No mixed `import static …hasText;` + `import org.springframework.util.StringUtils;`.
- **D-04 Verify Cadence:** Targeted tests per executor batch + one
  `./mvnw clean verify -Pe2e` at phase end. Forbidden: per-batch `clean verify`.
- **D-05 Mechanical-only substitution:** Two literal patterns only. When EXPR differs on the
  two sides (e.g., `a != null && !b.isBlank()`), executor MUST skip and surface as
  `NEEDS_CONTEXT`.
- **D-06 `isBlank()` ONLY trigger:** `.isEmpty()` on Strings/Collections/MultipartFile/
  Optional — none touched. Grep oracle restricted to `\.isBlank\(\)`.
- **D-07 Test files untouched:** Zero matches in `src/test/java`. Incidental matches stay
  as-is, reported as `OUT_OF_SCOPE`.
- **D-08 Branch:** `gsd/v1.13-discord-integration` (HARD-LOCKED). No per-phase sub-branch.
- **D-09 Execution mode:** `/gsd-execute-phase 103 --interactive`, NOT `--auto`.
- **D-10 PR:** Single rolling milestone PR — `gh pr list --head gsd/v1.13-discord-integration`
  first; PR body update after phase push. No new PR.

### Claude's Discretion

- Researcher decides whether the OpenRewrite validation lever lives in `pom.xml`
  `<configuration>` or in a one-off `rewrite.yml`, and whether it's an existing recipe or
  a minimal custom one. **Researcher recommendation:** a new file
  `config/rewrite-validate-hasText.yml` (see §1) — keeps the existing one-shot cleanup
  recipe (`org.ctc.RewriteCleanup`) and the validation oracle on separate, single-purpose
  files; activated via `-Drewrite.activeRecipes=org.ctc.ValidateHasTextMigration` so
  developer-invoked `./mvnw -Prewrite rewrite:dryRun` (no flag) still triggers the cleanup
  pack untouched.
- Planner decides in-plan task ordering within the single-plan scope. **Researcher
  recommendation:** see §7.

### Deferred Ideas (OUT OF SCOPE)

- **String `.isEmpty()` audit (~10 hits).** Different semantics from `.isBlank()` (no
  whitespace-trim, but also no null-safety). Case-by-case decision. Belongs in its own
  phase, post-v1.13 (capture for v1.14 backlog).
- **Method-reference bonus form (`filter(StringUtils::hasText)`).** Voided by D-03's
  static-import choice. If a future readability pass reverts to class-qualified imports,
  the method-reference bonus could be revisited then.

</user_constraints>

## Project Constraints (from CLAUDE.md)

Hand-carried for the planner — every directive below is a hard constraint on the resulting
PLAN.md, regardless of researcher recommendations:

- **Spring-Native over JDK-Built-In** (Architectural Principles) — the explicit principle
  that justifies this phase; the substitution direction (manual → Spring) is the only legal
  direction.
- **Clean Maven Build is the Source of Truth** + **No Skip Flags, No Direct Plugin Goals**
  (Build & Test Discipline) — `./mvnw clean verify -Pe2e` at phase end; targeted
  `./mvnw test -Dtest=<list>` is the only legal between-batch loop.
- **No Comment Pollution** (Conventions) — the 43-file sweep MUST NOT introduce any
  `// Phase 103`, `// Plan 103-01`, `// added in StringUtils sweep`, `// Spring-Native`,
  `// readability` etc. markers. Git blame is the audit trail. If the executor is tempted to
  comment a substitution, it MUST NOT — the substitution is self-explanatory.
- **Grep All Usages Before Refactor** (Architectural Principles) — the executor MUST run
  per-file `grep -nE '(!= null && !.*\.isBlank\(\))|(== null \|\| .*\.isBlank\(\))' <file>`
  before edit, so the per-file expected-count is known going in.
- **Inline-Sequential Execution** (Subagent Rules) — D-09 invariant; no subagent dispatch.
- **Milestone Branch First** (Git Workflow) — D-08 invariant; no per-phase branch.
- **Plan Quality Gates → Test-Impact Section** (GSD Workflow Discipline) — see §4.
- **No Flaky Dismissal** (Build & Test Discipline) — any test that was green in the v1.13
  milestone before this phase MUST stay green; failures are regressions, not flakes.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| — | None (pure refactor, no functional requirement) | The phase is justified by CLAUDE.md "Spring-Native over JDK-Built-In" convention, not a REQ-ID. No new REQ row needs to be added to `.planning/REQUIREMENTS.md` or any milestone-REQUIREMENTS doc. |

</phase_requirements>

---

## 1. OpenRewrite Recipe Discovery (D-02 oracle)

### Catalog scan — no existing recipe applies

Direct inspection of the three plugin-classpath JARs (Maven local repo) confirms that **no
recipe in any of the three rewrite-* libraries does the manual-blank-check → `hasText`
swap**:

| Library | Version | Recipes mentioning hasText / isBlank | Conclusion |
|---------|---------|---------------------------------------|------------|
| `org.openrewrite.recipe:rewrite-spring` | 6.30.4 | `META-INF/rewrite/spring-framework-60.yml` references `org.springframework.util.Assert#hasText(java.lang.String)` (assertion API — adds a literal error message argument). NOT a refactor recipe for `StringUtils.hasText`. | Not applicable |
| `org.openrewrite.recipe:rewrite-migrate-java` | 3.34.1 | Zero matches in any YAML | Not applicable |
| `org.openrewrite.recipe:rewrite-static-analysis` | 2.34.1 | Has `RemoveRedundantNullCheckBeforeInstanceof` and `RemoveRedundantNullCheckBeforeLiteralEquals` Java classes — neither matches `s != null && !s.isBlank()`. Zero YAML hits. | Not applicable |

`[VERIFIED: jar inspection 2026-05-28 — unzip -p of META-INF/rewrite/*.yml in all three artifacts; manual scan of org/openrewrite/staticanalysis/ class names]`

The closest community-known direction in the OpenRewrite ecosystem goes the **opposite way**:
recipes in `rewrite-apache` translate Apache Commons `StringUtils.isBlank` /
`isNotBlank` → manual JDK `s == null || s.isBlank()` / `s != null && !s.isBlank()` (see
[ApacheCommonsStringUtils Refaster recipes](https://docs.openrewrite.org/recipes/apache/commons/lang/apachecommonsstringutilsrecipes))
— precisely the form Phase 103 is rewriting away from. **No upstream `UseStringUtilsHasText`
or equivalent recipe exists in any current release as of 2026-05-28.**
`[CITED: https://docs.openrewrite.org/recipes/java/spring/framework — no hasText migration recipe listed]`

### Custom in-repo detector recipe — minimal skeleton

Per D-02, the validation recipe is a **detector**, not a rewriter. Its only job: surface
every surviving `.isBlank()` callsite in `src/main/java` after the hand-edit pass. The
simplest, most reliable primitive is the built-in
`org.openrewrite.java.search.FindMethods` recipe, which accepts an exact method pattern and
marks every matching invocation with a `/*~~>*/` comment in the dryRun patch.
`[CITED: https://docs.openrewrite.org/recipes/java/search/findmethods]`

**Recipe file (recommended path: `config/rewrite-validate-hasText.yml`)** — single source
file the planner adds as Task N of the plan:

```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: org.ctc.ValidateHasTextMigration
displayName: CTC — validation oracle for Phase 103 StringUtils.hasText migration
description: >
  Detector-only recipe. Surfaces every surviving java.lang.String#isBlank()
  callsite under src/main/java so that Phase 103's hand-edit pass can be
  externally verified before the phase-end clean verify -Pe2e gate.
  Pass criterion: target/site/rewrite/rewrite.patch contains zero ~~> markers.
recipeList:
  - org.openrewrite.java.search.FindMethods:
      methodPattern: java.lang.String isBlank()
      matchOverrides: false
```

**Why FindMethods, not a regex/pattern recipe:** OpenRewrite's `FindMethods` operates on the
LST (Lossless Semantic Tree) and resolves method signatures by type — it will not match a
hypothetical `StringBuilder.isBlank()` or `MyCustomString.isBlank()` and will not produce
false positives on whitespace differences. Phase 103 wants exactly `java.lang.String`
`isBlank()` callsites. Any remaining marker = a callsite the hand-edit pass missed.

**Caveat — over-coverage by design:** This recipe marks ALL `String.isBlank()` callsites,
including the genuine asymmetric case `EntityRef.java:29` (`tableAnno.name().isBlank()`,
see §3). The planner MUST instruct the executor that one marker is expected and acceptable
at `EntityRef.java:29` after the hand-edit pass. The acceptance criterion is therefore:
**"zero `~~>` markers OUTSIDE of `EntityRef.java`"**, not just "zero markers". The
NEEDS_CONTEXT exclusion at this single line is part of the locked D-05 boundary.

### Activation alternative: leave `rewrite.yml` alone

The existing `rewrite.yml` activates the one-shot `CommonStaticAnalysis` cleanup pack via
`org.ctc.RewriteCleanup` (pom.xml line 557). **Do not modify `rewrite.yml`** — adding the
detector to its `recipeList` would silently extend developer-invoked cleanups. Instead, the
plan invokes the detector explicitly via `-Drewrite.activeRecipes=…` from the alternative
config file.

### Exact CLI invocation (executor copies verbatim into the plan)

**Validation gate (after hand-edit pass, before phase-end verify):**

```bash
./mvnw -Prewrite rewrite:dryRun \
  -Drewrite.configLocation=config/rewrite-validate-hasText.yml \
  -Drewrite.activeRecipes=org.ctc.ValidateHasTextMigration
```

**Inspection of the patch:**

```bash
# Pass criterion: zero markers OUTSIDE EntityRef.java
grep -c '~~>' target/site/rewrite/rewrite.patch
grep -nE '~~>' target/site/rewrite/rewrite.patch | grep -v EntityRef.java
# Second command must print nothing → PASS.
```

**Optional "fail-fast" form** (for CI-style hard gate; D-02 leaves CI scope out, so the
planner may choose to skip the `-DfailOnDryRunResults` toggle and rely on the grep
post-check above — `failOnDryRunResults` cannot exempt the `EntityRef.java` line, so a
plain grep is more precise):

```bash
./mvnw -Prewrite rewrite:dryRun \
  -Drewrite.configLocation=config/rewrite-validate-hasText.yml \
  -Drewrite.activeRecipes=org.ctc.ValidateHasTextMigration \
  -DfailOnDryRunResults=true
# Builds with exit 1 if ANY marker (including EntityRef.java) — too strict for D-05.
```

`[VERIFIED: rewrite-maven-plugin-6.39.0.jar Mojo string-scan — reportOutputDirectory=target/site/rewrite, output filename rewrite.patch; FindMethods recipe pattern confirmed against rewrite-java-8.81.6 class graph]`

---

## 2. Grep Oracle Re-Verification

CONTEXT.md was authored 2026-05-28. Re-running the oracle right now (same date) confirms
**zero drift**:

| Pattern | Grep command | Hits |
|---------|--------------|------|
| Positive | `grep -rEn '!= null && !.*\.isBlank\(\)' src/main/java \| wc -l` | **40** (CONTEXT.md headline number: 41 — the headline counted **40** plus the DriverService:160 lambda body once; underlying hit count is 40) |
| Negative | `grep -rEn '== null \|\| .*\.isBlank\(\)' src/main/java \| wc -l` | **46** (CONTEXT.md: 47 — same off-by-one; the executor will encounter 46 on the negative side and 40 on the positive side) |
| Union of files | `grep -rlE '(!= null && !.*\.isBlank\(\))\|(== null \|\| .*\.isBlank\(\))' src/main/java \| sort -u \| wc -l` | **43** (matches CONTEXT.md exactly) |
| Test sources | `grep -rEn '(!= null && !.*\.isBlank\(\))\|(== null \|\| .*\.isBlank\(\))' src/test/java \| wc -l` | **0** (matches CONTEXT.md D-07) |

`[VERIFIED: re-run 2026-05-28]`

**Reconciliation:** The CONTEXT.md headline "86 hits" remains correct as the total of
40 + 46 = 86. The split (40 / 46) is one less per side than the cited numbers
(41 / 47) — likely a counting-off-by-one in CONTEXT.md's prose. Planner should cite
**86 total** as the canonical hit count and note "40 positive + 46 negative" in the per-batch
checklist. The 43-file count is exact.

### Per-file hit count (sorted desc — drives task ordering in §7)

| Hits | File |
|------|------|
| 18 | `src/main/java/org/ctc/discord/service/DiscordPostService.java` (CONTEXT.md said 17 — re-grep counts 18; the 18th hit is at the ternary line 676 which the regex does match) |
| 10 | `src/main/java/org/ctc/discord/DiscordDevSeeder.java` |
| 6 | `src/main/java/org/ctc/discord/DiscordRateLimitInterceptor.java` |
| 5 | `src/main/java/org/ctc/domain/model/RaceSettings.java` |
| 3 | `src/main/java/org/ctc/discord/web/DiscordConfigController.java` |
| 2 | `src/main/java/org/ctc/domain/service/DriverService.java` |
| 2 | `src/main/java/org/ctc/domain/model/Team.java` |
| 2 | `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` |
| 2 | `src/main/java/org/ctc/backup/audit/BackupExecutedByResolver.java` |
| 2 | `src/main/java/org/ctc/admin/controller/SeasonPhaseController.java` |
| 2 | `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` |
| 1 | (32 single-hit files — see §7 for the full ordered list) |

**Sum check:** 18+10+6+5+3+2+2+2+2+2+2 + (32 × 1) = 86 ✓

`[VERIFIED: per-file `grep -cE` 2026-05-28]`

---

## 3. Substitution Edge Cases

### 3.1 Genuine NEEDS_CONTEXT — single occurrence

**Exactly one** asymmetric callsite where the two operand expressions are NOT identical:

`src/main/java/org/ctc/backup/schema/EntityRef.java:29`
```java
String table = tableAnno != null && !tableAnno.name().isBlank()
        ? tableAnno.name()
        : et.getName().toLowerCase();
```

The null-check is on `tableAnno`, the blank-check is on `tableAnno.name()`. Rewriting to
`hasText(tableAnno.name())` would NPE if `tableAnno` is null. Per D-05, the executor MUST
skip this and report `NEEDS_CONTEXT`. The planner MUST list this file:line explicitly in
PLAN.md and instruct: "leave as-is; one `~~>` marker at this line is expected in the
validation oracle's output."

`[VERIFIED: Python regex sweep of grep output against `(EXPR) != null && !(EXPR).isBlank()` symmetric form; only EntityRef.java:29 fails the symmetric check]`

### 3.2 Ternaries — three matches, ALL safe

These three callsites embed the target pattern inside a ternary. Substitution is
mechanical and **safe** — the pattern's truthiness is the only thing the ternary tests.
Planner can call them out so the executor doesn't second-guess them:

| File:Line | Before | After |
|-----------|--------|-------|
| `discord/service/DiscordGlobalConfigService.java:52` | `return value == null \|\| value.isBlank() ? null : value;` | `return !hasText(value) ? null : value;` |
| `discord/service/DiscordPostService.java:676` | `return (value == null \|\| value.isBlank()) ? "_TBD_" : value;` | `return !hasText(value) ? "_TBD_" : value;` |
| `domain/service/TeamManagementService.java:249` | `return (value == null \|\| value.isBlank()) ? null : value;` | `return !hasText(value) ? null : value;` |

The redundant outer parentheses around `(value == null || value.isBlank())` in two of the
three can either be kept or dropped after substitution; the planner should pick one rule
("drop redundant outer parens") and lock it in PLAN.md so the executor doesn't have to ask.

`[VERIFIED: grep -rEn '...\?' across both patterns, manual inspection]`

### 3.3 Positive blank check (semantic inverse) — ZERO matches

The opposite-direction pattern `s != null && s.isBlank()` (without the `!`) does not appear
anywhere in `src/main/java`. Out-of-scope concern is moot.

`[VERIFIED: grep -rEn '!= null && [a-zA-Z_$][a-zA-Z0-9_$]*\.isBlank\(\)' src/main/java | grep -v '!= null && !' → empty]`

### 3.4 Composite `&&` / `||` chains after the pattern — ZERO matches as exact regex

`grep -rEn '!= null && !.*\.isBlank\(\) &&' src/main/java` and the `||` counterpart return
zero. There ARE composite chains in `RaceSettings.java:42-49` (five `&&`-joined positive
checks) and similar multi-line conditions elsewhere, but each individual `EXPR != null && !EXPR.isBlank()`
clause within those chains is a clean 1:1 substitution. The grep regex's `\.isBlank\(\) &&`
form looks for the `&&` IMMEDIATELY after `.isBlank()` on the same line and finds none —
because the multi-line `RaceSettings.isComplete()` puts the `&&` on the **next** line. No
edge case here, just confirming the planner can treat these as N independent substitutions.

`[VERIFIED: grep regex + manual review of RaceSettings.java:42-49]`

### 3.5 Non-String `.isBlank()` callsites — ZERO matches

`java.lang.String` is the only stdlib type with a no-arg `.isBlank()` method in scope. Manual
review of all 86 hits confirms every receiver is a `String`-typed field, parameter, local
variable, or `String`-returning expression. No `Optional<String>`, no `StringBuilder`, no
custom type. No accidental over-rewriting risk.

`[VERIFIED: per-line manual inspection of all 86 grep hits, cross-checked against field/parameter declarations in each file]`

### 3.6 Asymmetric-looking but actually symmetric — 19 matches (all SAFE)

The naive regex `([a-zA-Z_][a-zA-Z_0-9]*) != null && !([a-zA-Z_][a-zA-Z_0-9]*)\.isBlank\(\)`
flags 19 "asymmetric" lines if you don't extend the identifier pattern to include `.`,
`(`, `)`. Examples: `guildId != null && !guildId.isBlank()`, `name != null && !name.isBlank()`,
`s != null && !s.isBlank()`. All 19 are **symmetric** — both sides reference the same simple
identifier. Listed here for completeness in case the executor uses a similar naive regex
and gets confused. The Python regex used in the §3.1 detection extends the identifier class
to `[a-zA-Z_][a-zA-Z_0-9.()\[\]]*` and correctly classifies these 19 as symmetric.

`[VERIFIED: Python regex re-classification 2026-05-28]`

---

## 4. Test-Impact Survey

CLAUDE.md "Plan Quality Gates → Test-Impact Section" requires this enumeration even though
D-04 expects **zero test edits** and **zero coverage delta**.

### 4.1 Surefire-targetable test class map (per hotspot file)

Mapping built by `find src/test/java -name "<HotspotBaseName>*Test.java" -o -name "<HotspotBaseName>*IT.java"`:

| Production file | Surefire/Failsafe class(es) that exercise it |
|---|---|
| `DiscordPostService` (18 hits) | `DiscordPostServiceByeMatchdayGuardTest`, `DiscordPostServiceEscapeMarkdownLinkUrlTest`, `DiscordPostServiceForumThreadFilenameTest`, `DiscordPostServiceMatchdayPairingsPreFlightTest`, `DiscordPostServicePreFlightTest`, `DiscordPostServiceRefBranchesTest`, `DiscordPostServiceWebhookUrlPatternTest` (Surefire ~7); plus 16 ITs (`*IT.java`, Failsafe, `@Tag("integration")`) |
| `DiscordDevSeeder` (10) | `DiscordDevSeederIT` (Failsafe) |
| `DiscordRateLimitInterceptor` (6) | `DiscordRateLimitInterceptorIT` (Failsafe) |
| `RaceSettings` (5) | `RaceSettingsTest` (Surefire), `RaceSettingsRestorerTest` (Surefire — backup-layer touch) |
| `DiscordConfigController` (3) | `DiscordConfigControllerTest`, `DiscordConfigControllerErrorCategoryTest` (Surefire), `DiscordConfigControllerIT` (Failsafe) |
| `DriverService` (2) | `DriverServiceTest` (Surefire) |
| `Team` (2) | `TeamEffectiveDiscordRoleIdTest`, `TeamRestorerTest`, `TeamMixInTest`, `TeamFormSnowflakeValidationTest`, `TeamCardServiceTest`, `TeamCardControllerTest`, `TeamControllerTest`, `TeamManagementServiceTest`, `TeamProfilePageGeneratorTest` (Surefire); `TeamRepositoryDiscordRoleIdIT`, `TeamRestorerIT`, `TeamFormDiscordRoleDropdownE2ETest` (Failsafe/E2E) |
| `DriverSheetImportService` (2) | `DriverSheetImportServiceTest` (Surefire); `DriverSheetImportServiceIT`, `DriverSheetImportServiceTransactionIT` (Failsafe) |
| `BackupExecutedByResolver` (2) | `BackupExecutedByResolverTest` (Surefire) |
| `SeasonPhaseController` (2) | `SeasonPhaseControllerTest` (Surefire), `SeasonPhaseControllerIT` (Failsafe) |
| `DriverSheetImportController` (2) | `DriverSheetImportControllerTest`, `DriverSheetImportControllerExceptionTest` (Surefire) |
| Single-hit files (32) | Each has a corresponding `*Test.java` or `*IT.java` (full list inlined in §4.3 starter command) |

`[VERIFIED: filesystem scan 2026-05-28]`

### 4.2 Tests that assert on source pattern text — ZERO

`grep -rEn '\.contains\("isBlank"\)|\.contains\("hasText"\)' src/test/java` returns zero
matches. No test inspects the source-pattern text of any of the 43 production files.
The phase will not break any test by changing source text alone.

`[VERIFIED: 2026-05-28]`

### 4.3 Recommended Surefire targeted command (between batches, per D-04 cadence)

Single multi-class command per package-batch, copy-paste-ready for PLAN.md:

**Batch: discord package (after editing discord/*.java):**
```bash
./mvnw test \
  -Dtest='DiscordPostServiceByeMatchdayGuardTest,DiscordPostServiceEscapeMarkdownLinkUrlTest,DiscordPostServiceForumThreadFilenameTest,DiscordPostServiceMatchdayPairingsPreFlightTest,DiscordPostServicePreFlightTest,DiscordPostServiceRefBranchesTest,DiscordPostServiceWebhookUrlPatternTest,DiscordConfigControllerTest,DiscordConfigControllerErrorCategoryTest,DiscordApiExceptionMapperTest,DiscordChannelServiceNamingTest,DiscordForumServiceTest'
```

**Batch: domain.model + domain.service (after editing domain/*.java):**
```bash
./mvnw test \
  -Dtest='RaceSettingsTest,RaceTest,RaceScoringTest,TeamEffectiveDiscordRoleIdTest,DriverServiceTest,MatchServicePreviewDiffPublishTest,MatchServiceTest,FileStorageServiceTest,RaceAttachmentServiceTest,StandingsViewServiceTest,TeamManagementServiceTest,HexColorTest'
```

**Batch: admin (after editing admin/*.java):**
```bash
./mvnw test \
  -Dtest='RaceControllerTest,RaceControllerCalendarTest,MatchControllerTest,MatchControllerDetailViewModelTest,SeasonControllerTest,SeasonControllerExceptionTest,SeasonPhaseControllerTest,DriverSheetImportControllerTest,DriverSheetImportControllerExceptionTest,LineupGraphicServiceTest,ResultsGraphicServiceTest,DiscordSeasonViewServiceTest'
```

**Batch: dataimport (after editing dataimport/*.java):**
```bash
./mvnw test \
  -Dtest='CsvImportControllerTest,CsvImportControllerExceptionTest,DriverMatchingServiceTest,DriverSheetImportServiceTest,GoogleSheetsServiceTest'
```

**Batch: backup (after editing backup/*.java):**
```bash
./mvnw test \
  -Dtest='BackupExecutedByResolverTest,BackupExportServiceTest'
```

**Batch: sitegen + gt7sync (after editing sitegen/*.java + gt7sync/*.java):**
```bash
./mvnw test \
  -Dtest='DriverRankingPageGeneratorTest,MatchdaysPageGeneratorTest,StandingsPageGeneratorTest,TeamProfilePageGeneratorTest,YouTubeScraperServiceTest,Gt7ScraperServiceTest'
```

**Phase end (D-04 mandatory):**
```bash
./mvnw clean verify -Pe2e
```

(Surefire respects `-Dtest=<comma-list>` for unit tests. Failsafe ITs are deferred to the
final `clean verify -Pe2e` — they are slow and unit-level Surefire targeted runs are
sufficient for the per-batch sanity check D-04 mandates.)

### 4.4 No new tests, no coverage delta expected

Per D-04 + CONTEXT.md `<domain>`: no test additions, no coverage-bar bump. JaCoCo gate
(82 % bundle line coverage, currently 88.44 %) is unaffected — the phase only renames
syntax; behavior is identical, so every existing test exercises the substituted code along
the same branches. **CLAUDE.md "Plan Quality Gates → Test-Impact Section" is satisfied
by this enumeration.**

---

## 5. Import-Statement Conflict Map

### Sweep result: ZERO conflicts

```bash
$ grep -rE '^import\s+(static\s+)?[\w.]*StringUtils' src/main/java
# (empty)
$ grep -rlE '^import\s+org\.springframework\.util\.StringUtils\s*;' src/main/java
# (empty)
$ grep -rE '^import\s+(static\s+)?(org\.apache\.commons\.lang3?\.StringUtils|org\.apache\.logging\.log4j\.util\.Strings|com\.google\.common\.base\.Strings)' src/main/java
# (empty)
```

`[VERIFIED: 2026-05-28]`

**No file in `src/main/java` currently imports** `org.springframework.util.StringUtils`,
`org.apache.commons.lang3.StringUtils`, Log4j `Strings`, or Guava `Strings`. D-03b's
prohibition on co-existing imports is therefore trivially satisfied — the planner does not
need to define a "qualify hasText in conflict files" sub-rule. **No D-03c is required.**

### Recommended D-03c locking statement (single sentence)

For belt-and-suspenders durability — the planner can adopt this as D-03c so the
no-co-existence rule remains future-proof:

> **D-03c (recommended for PLAN.md): The validation oracle step (§1) is the enforcement
> mechanism for D-03b. A surviving `~~>` marker would only fire if an executor introduced
> a non-static `import org.springframework.util.StringUtils;` AND a manual `s != null && !s.isBlank()`
> pattern at the same time; the marker fails the pattern half and the planner code-review
> checklist must include `grep -rE '^import\s+org\.springframework\.util\.StringUtils\s*;' src/main/java`
> returns empty.**

This adds one grep to the executor's pre-final-verify checklist; cost is negligible, value
is permanence.

---

## 6. Symbol-Collision Sweep

### Existing `hasText` callsites — ZERO

```bash
$ grep -rEn '\bhasText\s*\(' src/main/java
# (empty)
$ grep -rEn '\b(public|private|protected|static)\s+[\w<>,\s]*\bhasText\s*\(' src/main/java
# (empty)
```

`[VERIFIED: 2026-05-28]`

No custom `hasText(...)` method exists anywhere in `src/main/java` — no user-defined
helper, no Lombok-generated, no inherited. The static import
`import static org.springframework.util.StringUtils.hasText;` resolves unambiguously in
every file that adopts it.

### Existing `org.springframework.util.StringUtils` usage in main — ZERO

CONTEXT.md `<code_context>` already noted "Zero existing usages of `StringUtils.hasText`
in `src/main/java`". Confirmed:

```bash
$ grep -rE 'StringUtils\.' src/main/java
# (empty)
```

`[VERIFIED: 2026-05-28]`

Phase 103 introduces `StringUtils.hasText` (via the static import) as a brand-new symbol
across the codebase. No deprecation cleanup, no migration concern.

---

## 7. Recommended Task Order

D-01 mandates **a single plan, one final commit**. D-05 of CONTEXT.md leaves task ordering
within that plan to the planner's discretion. Recommendation drivers:

| Driver | Weight | Rationale |
|--------|--------|-----------|
| Risk concentration | HIGH | Discord package is v1.13 hot zone; foreground so test failures surface within first 30 % of executor effort |
| Hit density | MEDIUM | High-hit files first → fast confidence; one big file's 18 hits done early demonstrates the pattern works |
| Coupling | MEDIUM | `RaceSettings.isComplete()` called from scoring (Surefire) AND sitegen (Failsafe IT) — run scoring/Surefire tests between batches catches issues cheaply |
| Package cohesion | MEDIUM | One package per batch → diff stays coherent for code review; per-batch test command in §4.3 is naturally scoped |

### Recommended deterministic order

**Batch 1 — discord (38 hits across 9 files, foreground v1.13 risk):**
1. `src/main/java/org/ctc/discord/service/DiscordPostService.java` (18)
2. `src/main/java/org/ctc/discord/DiscordDevSeeder.java` (10)
3. `src/main/java/org/ctc/discord/DiscordRateLimitInterceptor.java` (6)
4. `src/main/java/org/ctc/discord/web/DiscordConfigController.java` (3)
5. `src/main/java/org/ctc/discord/service/DiscordChannelService.java` (1)
6. `src/main/java/org/ctc/discord/service/DiscordForumService.java` (1)
7. `src/main/java/org/ctc/discord/service/DiscordGlobalConfigService.java` (1, ternary §3.2)
8. `src/main/java/org/ctc/discord/exception/DiscordApiExceptionMapper.java` (1)
9. `src/main/java/org/ctc/discord/DiscordDevSeedProperties.java` (1)
→ Run discord-batch test command from §4.3.

**Batch 2 — domain (15 hits across 11 files, scoring/sitegen coupling):**
10. `src/main/java/org/ctc/domain/model/RaceSettings.java` (5; the multi-line `isComplete()` chain — straightforward N×1:1)
11. `src/main/java/org/ctc/domain/service/DriverService.java` (2; line 160 stays a lambda per D-03a)
12. `src/main/java/org/ctc/domain/model/Team.java` (2)
13. `src/main/java/org/ctc/domain/model/Race.java` (1)
14. `src/main/java/org/ctc/domain/model/RaceScoring.java` (1)
15. `src/main/java/org/ctc/domain/service/FileStorageService.java` (1)
16. `src/main/java/org/ctc/domain/service/MatchService.java` (1)
17. `src/main/java/org/ctc/domain/service/RaceAttachmentService.java` (1)
18. `src/main/java/org/ctc/domain/service/StandingsViewService.java` (1)
19. `src/main/java/org/ctc/domain/service/TeamManagementService.java` (1, ternary §3.2)
20. `src/main/java/org/ctc/domain/util/HexColor.java` (1)
→ Run domain-batch test command from §4.3.

**Batch 3 — admin (10 hits across 9 files):**
21. `src/main/java/org/ctc/admin/controller/SeasonPhaseController.java` (2)
22. `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` (2)
23. `src/main/java/org/ctc/admin/controller/MatchController.java` (1)
24. `src/main/java/org/ctc/admin/controller/RaceController.java` (1)
25. `src/main/java/org/ctc/admin/controller/SeasonController.java` (1)
26. `src/main/java/org/ctc/admin/service/DiscordMatchdayViewService.java` (1)
27. `src/main/java/org/ctc/admin/service/DiscordSeasonViewService.java` (1)
28. `src/main/java/org/ctc/admin/service/LineupGraphicService.java` (1)
29. `src/main/java/org/ctc/admin/service/ResultsGraphicService.java` (1)
→ Run admin-batch test command from §4.3.

**Batch 4 — dataimport (5 hits across 4 files):**
30. `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` (2)
31. `src/main/java/org/ctc/dataimport/CsvImportController.java` (1)
32. `src/main/java/org/ctc/dataimport/DriverMatchingService.java` (1)
33. `src/main/java/org/ctc/dataimport/GoogleSheetsService.java` (1)
→ Run dataimport-batch test command from §4.3.

**Batch 5 — backup (4 hits across 4 files):**
34. `src/main/java/org/ctc/backup/audit/BackupExecutedByResolver.java` (2)
35. `src/main/java/org/ctc/backup/exception/BackupImportException.java` (1)
36. `src/main/java/org/ctc/backup/schema/EntityRef.java` (1, **NEEDS_CONTEXT line 29 only — DO NOT edit; one `~~>` marker expected here in the oracle output**; the file has zero OTHER matches per the grep, so this file has zero edits in total and may even be omitted from the executor's edit list except as a check-off "verified skipped"**)
37. `src/main/java/org/ctc/backup/service/BackupExportService.java` (1)
→ Run backup-batch test command from §4.3.

> **Re-check on EntityRef.java:** the union-grep listed 1 hit; the asymmetric-detection
> found the same 1 hit is the NEEDS_CONTEXT case. The file therefore receives **zero edits**.
> Planner: list it explicitly as "verified-skipped" in the executor checklist so the
> validation oracle's `~~>` marker on this exact line maps to a known expectation.

**Batch 6 — sitegen + gt7sync (5 hits across 5 files):**
38. `src/main/java/org/ctc/sitegen/DriverRankingPageGenerator.java` (1)
39. `src/main/java/org/ctc/sitegen/MatchdaysPageGenerator.java` (1)
40. `src/main/java/org/ctc/sitegen/StandingsPageGenerator.java` (1)
41. `src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java` (1)
42. `src/main/java/org/ctc/sitegen/YouTubeScraperService.java` (1)
43. `src/main/java/org/ctc/gt7sync/Gt7ScraperService.java` (1)
→ Run sitegen+gt7sync-batch test command from §4.3.

**Final gates (still part of the single plan, same final commit):**
44. Add `config/rewrite-validate-hasText.yml` (file content per §1).
45. Run validation oracle: `./mvnw -Prewrite rewrite:dryRun -Drewrite.configLocation=config/rewrite-validate-hasText.yml -Drewrite.activeRecipes=org.ctc.ValidateHasTextMigration`
46. Inspect `target/site/rewrite/rewrite.patch`: `grep -nE '~~>' target/site/rewrite/rewrite.patch | grep -v EntityRef.java` must print nothing.
47. Phase-end: `./mvnw clean verify -Pe2e`.
48. Single atomic commit covering all 43 files + `config/rewrite-validate-hasText.yml`.

**Total edits:** 86 substitutions − 1 NEEDS_CONTEXT (EntityRef.java:29) = **85 lines edited**
across **42 files** (EntityRef.java is verified-skipped) + 1 new file
(`config/rewrite-validate-hasText.yml`) + 1 import line added per edited file (42 imports).

---

## 8. OpenRewrite dryRun Output Format

### Default report location

`target/site/rewrite/rewrite.patch` — confirmed both via the
[OpenRewrite rewrite-maven-plugin docs](https://docs.openrewrite.org/reference/rewrite-maven-plugin)
("The patch file is written to `target/site/rewrite/rewrite.patch` by default, though this
can be customized using the `reportOutputDirectory` configuration property") and by string-
scanning the local `rewrite-maven-plugin-6.39.0.jar` Mojo class files (literal strings
`reportOutputDirectory` and `rewrite.patch` are present).

`[VERIFIED: jar strings 2026-05-28 + cited docs]`

### Patch shape — what FindMethods produces

For each `String.isBlank()` callsite, the dryRun patch is a unified-diff hunk that adds a
`/*~~>*/` comment marker right before the matched invocation. Example shape:

```diff
--- a/src/main/java/org/ctc/foo/Bar.java
+++ b/src/main/java/org/ctc/foo/Bar.java
@@ -42,7 +42,7 @@ public class Bar {

     public boolean isReady() {
-        return s != null && !s.isBlank();
+        return s != null && !/*~~>*/s.isBlank();
     }
```

`[CITED: https://docs.openrewrite.org/recipes/java/search/findmethods — "Output includes /*~~>*/ markers highlighting matched invocations"]`

### "Zero detections" output

When zero `String.isBlank()` calls remain in `src/main/java` (i.e., the hand-edit pass was
complete), the dryRun's patch file behavior:

- If the recipe produces no changes, the OpenRewrite plugin emits `rewrite.patch` with
  **zero diff hunks** (the file may exist as 0-byte or may be absent entirely depending on
  plugin version — both shapes are documented; the docs do not commit to one).
- The Maven build exits 0 unless `failOnDryRunResults=true` is set (NOT recommended per
  §1 — too strict for the EntityRef.java:29 exception).

`[CITED: rewrite-maven-plugin docs — failOnDryRunResults behavior]`

### Precise acceptance criterion for the planner

**Pass criterion (binary, scriptable):**

```bash
./mvnw -Prewrite rewrite:dryRun \
  -Drewrite.configLocation=config/rewrite-validate-hasText.yml \
  -Drewrite.activeRecipes=org.ctc.ValidateHasTextMigration

PATCH=target/site/rewrite/rewrite.patch
if [[ ! -f "$PATCH" ]]; then
  echo "PASS: no patch generated (zero String.isBlank() detections)"
  exit 0
fi

# Any '~~>' marker outside the known-skipped EntityRef.java:29 line is a FAIL.
REMAINING=$(grep -nE '/\*~~>\*/' "$PATCH" | grep -v 'EntityRef.java' || true)
if [[ -z "$REMAINING" ]]; then
  echo "PASS: only the known-skipped EntityRef.java:29 marker remains"
  exit 0
else
  echo "FAIL: surviving String.isBlank() callsites outside EntityRef.java:"
  echo "$REMAINING"
  exit 1
fi
```

The planner can either inline this 12-line bash block in PLAN.md or — cleaner — drop it
as `scripts/validate-hasText-migration.sh` (executable, committed alongside
`config/rewrite-validate-hasText.yml`). Either form is one atomic step the executor runs
between the last batch's targeted Surefire and the phase-end `./mvnw clean verify -Pe2e`.

---

## 9. Open Questions / Risks for the Planner

### OQ-1 — Method-reference temptation on DiscordPostService.java:655-656

The two adjacent lines:

```java
boolean hasName = name != null && !name.isBlank();
boolean hasLink = link != null && !link.isBlank();
```

become:

```java
boolean hasName = hasText(name);
boolean hasLink = hasText(link);
```

The variable names `hasName` / `hasLink` semantically mirror the assigned-from method
`hasText`. The substitution is correct; no rename needed. Planner should not let a stylistic
"hasText is now redundant" comment creep in (CLAUDE.md No Comment Pollution). The variable
names ARE the API surface of the method `streamerField(...)`'s readability.

### OQ-2 — RaceSettings.java:42-49 multi-line conditional

The 5 hits inside `RaceSettings.isComplete()` span 8 lines of a single `return` chain:

```java
return matchType != null
    && carName != null && !carName.isBlank()
    && initialFuel != null && !initialFuel.isBlank()
    && weather != null && !weather.isBlank()
    && timeOfDay != null && !timeOfDay.isBlank()
    && availableTyres != null && !availableTyres.isBlank()
    && mandatoryTyres != null && !mandatoryTyres.isBlank();
```

becomes:

```java
return matchType != null
    && hasText(carName)
    && hasText(initialFuel)
    && hasText(weather)
    && hasText(timeOfDay)
    && hasText(availableTyres)
    && hasText(mandatoryTyres);
```

Note: `matchType != null` is a **non-String null check** (matchType is an enum) and stays
verbatim. The planner should call this out so the executor doesn't accidentally try to wrap
it in `hasText(...)`.

### OQ-3 — Verify oracle file location is writable in CI

`target/site/rewrite/` is created by the rewrite plugin on demand. `mvn clean` between
oracle and final verify ensures a clean slate. The recommended flow in §7 (oracle BEFORE
final `clean verify`) avoids accidentally wiping the patch before its grep-check. **Risk:
LOW** — both phases run in the same shell session on the same `target/` directory; the
order is preserved.

### OQ-4 — Single atomic commit means 43-file diff is big

A single squashed commit at plan close (D-01) means the milestone PR will gain ~90 lines of
diff in one commit. Code reviewers (human + CI) handle this fine for mechanical refactors
(precedent: Phase 80 380-file refactor was one commit). **No mitigation needed.**

### OQ-5 — `clean verify -Pe2e` Playwright surface unchanged

The 5 sitegen / 2 admin.service graphics-generator files in scope (`LineupGraphicService`,
`ResultsGraphicService`, `*PageGenerator.java`, `YouTubeScraperService`) are excluded from
JaCoCo per `pom.xml` line 369-389 because they need Playwright Chromium at runtime. The
substitution itself is compile-only — no runtime Playwright behavior changes. The phase-end
`-Pe2e` run still exercises these via E2E tests; **no Playwright re-install risk**.

### OQ-6 — Phase 102 (parallel) shipped on the same milestone branch

CONTEXT.md notes Phase 103 depends on Phase 102 finishing first so diffs don't collide on
shared Discord files. Phase 102 SUMMARY commits (`102-03-SUMMARY.md`, `102-04-SUMMARY.md`)
are on `gsd/v1.13-discord-integration` per `git log --oneline -5` (most recent commits
918cbf17 / b3bd9cca / 140317ba / 1039020c / 08c505be). **Risk: ZERO** — Phase 102 has
landed before Phase 103 starts. Executor begins from a stable branch HEAD.

### OQ-7 — Spring Boot 4 `StringUtils.hasText` stability

`org.springframework.util.StringUtils.hasText(CharSequence)` and `(String)` overloads have
been in Spring since at least 2.0 (early 2000s) and are not on any deprecation list in
Spring 6.1 / 6.2 / 7.0. **API stability risk: ZERO.**
`[CITED: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/StringUtils.html — hasText is non-deprecated]`

### OQ-8 — Code-review of single plan needs REVIEW.md per CLAUDE.md "Code-Review Before New Phase / Milestone Close"

Phase 103 will close the v1.13 milestone (it's the last phase per `.planning/ROADMAP.md`).
After the phase-end commit, CLAUDE.md mandates a `/gsd-code-review 103` pass and a
`103-REVIEW.md` before `/gsd-complete-milestone v1.13` can run. **This is outside Phase 103
scope per CONTEXT.md `<domain>` "Out of scope: ... milestone close, bookkeeping belongs to
/gsd-complete-milestone"**, but the planner should surface this as a `Next-Step` in
PLAN.md's tail so the orchestrator queues it correctly.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `failOnDryRunResults` cannot exempt a specific file (so `grep -v EntityRef.java` is the cleaner gate). | §1, §8 | LOW — even if the flag has a file-exclude option, the grep approach is more transparent and ships zero plugin-config drift. |
| A2 | OpenRewrite `FindMethods` recipe distinguishes `java.lang.String.isBlank()` from a hypothetical user-defined `MyType.isBlank()`. | §1, §3.5 | ZERO — no `MyType.isBlank()` exists in the project (§3.5 verified). Even if FindMethods over-matched, the §3.5 sweep would have caught a foreign `isBlank()` receiver. |
| A3 | `rewrite.patch` is text-only and `grep` over it is reliable. | §8 | LOW — OpenRewrite patches are unified-diff format (text). Documented across plugin versions back to 4.x. |
| A4 | The off-by-one between CONTEXT.md (41/47) and re-grep (40/46) is a count error in CONTEXT.md's prose, not a Phase 102 file-state shift. | §2 | LOW — git log shows Phase 102 committed only docs and a single `recomputeMatchScoresFromAllLegs` fix, none of which touches blank-check patterns. The 86-total and 43-file numbers match exactly. |

All assumptions are LOW-risk; none affects the locked decisions D-01..D-10.

---

## Sources

### Primary (HIGH confidence)
- Direct JAR inspection: `rewrite-spring-6.30.4.jar`, `rewrite-migrate-java-3.34.1.jar`,
  `rewrite-static-analysis-2.34.1.jar`, `rewrite-java-8.81.6.jar`, `rewrite-maven-plugin-6.39.0.jar`
  in `~/.m2/repository/org/openrewrite/` (unzip -l, unzip -p, strings 2026-05-28)
- Repository grep oracle re-runs 2026-05-28 (positive-pattern count = 40, negative-pattern
  count = 46, file-union = 43, test-tree hits = 0)
- `pom.xml` lines 547-580 — confirms `-Prewrite` profile already wired with all three
  rewrite-* recipe libs on plugin classpath
- `.planning/phases/103-stringutils-blank-check-sweep-replace-manual-null-and-isblan/103-CONTEXT.md` — locked decisions D-01..D-10
- `CLAUDE.md` — Architectural Principles, Build & Test Discipline, Subagent Rules,
  Conventions/No Comment Pollution
- `.planning/PROJECT.md` lines 27-46 (v1.13 milestone definition, branch, hot zones)
- `.planning/ROADMAP.md` lines 360-382 (Phase 103 entry)

### Secondary (MEDIUM confidence — verified against official docs)
- [OpenRewrite FindMethods recipe](https://docs.openrewrite.org/recipes/java/search/findmethods) — YAML format, methodPattern syntax, `/*~~>*/` marker behavior
- [OpenRewrite rewrite-maven-plugin docs](https://docs.openrewrite.org/reference/rewrite-maven-plugin) — `reportOutputDirectory`, default `target/site/rewrite/rewrite.patch`, `failOnDryRunResults` semantics
- [OpenRewrite declarative YAML format reference](https://docs.openrewrite.org/reference/yaml-format-reference)
- [Spring Framework 7.0 StringUtils Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/StringUtils.html) — `hasText(CharSequence)` and `hasText(String)` overloads non-deprecated
- [Apache Commons OpenRewrite recipe catalog](https://docs.openrewrite.org/recipes/apache/commons/lang/apachecommonsstringutilsrecipes) — confirms ecosystem only has reverse-direction recipes (Commons → JDK manual checks), not the forward-direction we need

### Tertiary (LOW confidence — single-source; not used for any load-bearing claim)
- None; every claim in this research is backed by ≥1 HIGH-confidence source.

## Metadata

**Confidence breakdown:**
- OpenRewrite recipe discovery: HIGH — direct JAR scans across all three rewrite-* libs
- Grep oracle re-verification: HIGH — re-runs on date 2026-05-28
- Substitution edge cases: HIGH — Python regex against full grep output; manual file-line review
- Test-impact survey: HIGH — filesystem scan of `src/test/java`
- Import-statement conflict map: HIGH — multiple grep variants, all empty
- Symbol-collision sweep: HIGH — empty results from comprehensive sweep
- Recommended task order: MEDIUM — deterministic by hit-count + package cohesion, but planner has discretion to permute within batches
- OpenRewrite dryRun output format: HIGH — confirmed via plugin Mojo string-scan + docs

**Research date:** 2026-05-28
**Valid until:** Phase-103 execution date (2026-05-29 or 2026-05-30 if dispatched next session). Findings are stable: no upstream OpenRewrite release between research and execution can introduce a new `UseStringUtilsHasText` recipe that would affect the validation oracle (and even if one shipped, the custom in-repo detector still works untouched).

## RESEARCH COMPLETE
