---
phase: 79
plan: 02a
type: execute
wave: 2
depends_on: [79-01]
files_modified:
  - src/main/java/org/ctc/admin/controller/**
  - src/test/java/org/ctc/admin/controller/**
  - src/main/java/org/ctc/backup/config/**
  - src/test/java/org/ctc/backup/config/**
  - src/main/java/org/ctc/backup/io/**
  - src/test/java/org/ctc/backup/io/**
  - src/main/java/org/ctc/backup/security/**
  - src/test/java/org/ctc/backup/security/**
autonomous: true
requirements: [D-01, D-02, D-03, D-04, D-09, D-10, D-11, D-12, D-13]

must_haves:
  truths:
    - "Each touched Java package compiles cleanly and its tests pass after each per-package commit"
    - "No comment containing any Schutzwortliste keyword (D-13) was deleted"
    - "All dead-code deletions are grep-verified zero references AND lack Spring/JPA/Jackson lifecycle annotations (D-04)"
    - "Behavior is preserved — no public API signatures changed, no URL/endpoint renamed, no Flyway migration touched"
    - "Each package gets its own commit (D-03) with `./mvnw test` BUILD SUCCESS in between"
  artifacts:
    - path: "git log on branch gsd/v1.10-platform-and-backup"
      provides: "Up to 4 per-package atomic commits, one per cleaned package"
      pattern: "refactor\\(79\\): cleanup org\\.ctc\\.(admin\\.controller|backup\\.config|backup\\.io|backup\\.security) package"
  key_links:
    - from: "Cleaned source files"
      to: "JaCoCo coverage report"
      via: "`./mvnw verify` floor 0.82 still met (D-18)"
      pattern: "minimum>0\\.82"
---

<objective>
Wave 2 cleanup sweep — first bundle of leaf packages (zero or near-zero import counts per RESEARCH §"Per-Package Cleanup Ordering"). These packages are cleaned FIRST because they have the lowest blast radius: deleting an unused private helper or thinning a phase-reference comment here cannot ripple through downstream callers.

Packages in scope (in order, one commit each):
1. `org.ctc.admin.controller` — 23 files (import count: 0)
2. `org.ctc.backup.config` — 1 file (import count: 0)
3. `org.ctc.backup.io` — 1 file (import count: 1)
4. `org.ctc.backup.security` — 1 file (import count: 1)

Output: up to 4 atomic commits, each followed by a green `./mvnw test`. Cleanup classes per D-02: comment-thinning (D-09..D-13), dead-code removal (D-04), extract-method (>50 LOC threshold, CD-02 discretion >30 LOC), logic-simplification (CD-03 discretion).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-CONTEXT.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-RESEARCH.md
@CLAUDE.md
@.planning/codebase/CONVENTIONS.md

<interfaces>
<!-- Cleanup classes (D-02) applied uniformly to all per-package commits in this plan -->

**Comment-Thinning (D-09 DELETE categories):**
- Phase-N references: `// Phase 75 D-07`, `// implements GAP-2`, `// see 72-CONTEXT.md`, `// PLAT-06:`, `// SECU-05:`, similar
- Was-comments paraphrasing the next line: `// returns the list`, `// increments counter`, `// build the result`
- Tombstones: `// removed in v1.9`, `// deprecated since 2026-03`
- Boilerplate Javadoc that only restates the method signature

**Comment-Keep (D-10 + D-13 Schutzwortliste — NEVER DELETE if any of these words appears):**
`MariaDB`, `H2`, `JEP`, `CVE`, `race`, `thread-safe`, `TODO`, `HACK`, `WORKAROUND`, `FIXME`, `deadlock`, `OSIV`, `Lombok`, `Unsafe`, `transitiv`, `transitive`, `pitfall`, `auto-commit`, `auditing`, `AuditingEntityListener`

**Test-code (D-11):** Keep `// given` / `// when` / `// then` BDD structure comments. Other test-code comments follow production rules.

**Class-Javadoc (D-12):** Condense to 1-3 lines of responsibility. Strip phase-evolution narratives + cross-phase references. Keep behavioral guarantees (e.g., "Bypasses AuditingEntityListener so imported created_at survives").

**Dead-code (D-04):** Delete a class/method/field ONLY when ALL of:
(a) `grep -rE 'symbolName' src/` finds zero references outside the declaring file
(b) Symbol has NO Spring/JPA/Jackson lifecycle annotation: `@Component`/`@Service`/`@Repository`/`@Bean`/`@Configuration`/`@Entity`/`@MappedSuperclass`/`@JpaListener`/`@EventListener`/`@TransactionalEventListener`/`@PostConstruct`/`@PreDestroy`/`@JsonProperty`/`@JsonCreator`/`@JsonSetter`
(c) Symbol is NOT a JPA-required no-arg constructor / Jackson-required public setter
On uncertainty → LEAVE IT and mark `TBD-verify` in the commit body.

**Extract-Method:** Methods >50 LOC are candidates. CD-02: extract earlier (>30 LOC) when readability clearly improves; leave longer methods alone when splitting forces awkward parameter lists.

**Logic-Simplification:** Streams preferred over loops when transformational (filter/map/collect). Imperative loops STAY when control flow has side effects or early-return.

**Schutzwort grep recipe (run BEFORE deleting any comment block):**
`grep -nE "MariaDB|H2|JEP|CVE|\brace\b|thread-safe|TODO|HACK|WORKAROUND|FIXME|deadlock|OSIV|Lombok|Unsafe|transitiv|pitfall|auto-commit|auditing|AuditingEntityListener" <file>`
If grep finds a hit on a line you planned to delete → skip that comment block.

**Per-package commit pattern (D-03):**
```
refactor(79): cleanup <PACKAGE> package — comment-thinning + dead-code + extract-method + logic-simplify

- N comment-thinning edits (Schutzwortliste honored)
- M dead-code removals (grep-verified zero refs)
- P extract-method refactors
- Q logic-simplifications
```
After each commit: `./mvnw test`. If RED → STOP, `git revert HEAD`, escalate as `NEEDS_CONTEXT`.
</interfaces>
</context>

<critical_constraints>
- You are working on branch `gsd/v1.10-platform-and-backup`. Do NOT run `git stash`, `git checkout`, `git reset`, or switch branches.
- Implement ONLY the tasks listed below. If you find that other files need changes, report `NEEDS_CONTEXT` instead of expanding scope.
- After each per-package commit, run `./mvnw test` and verify it passes BEFORE moving to the next package.
- Schutzwortliste (D-13): Comments containing any of these words must NOT be deleted: MariaDB, H2, JEP, CVE, race, thread-safe, TODO, HACK, WORKAROUND, FIXME, deadlock, OSIV, Lombok, Unsafe, transitiv, transitive, pitfall, auto-commit, auditing, AuditingEntityListener.
- Dead-code removal rule (D-04): Only delete when (a) IDE + grep find zero references AND (b) no Spring/JPA/Jackson lifecycle annotation AND (c) not a JPA no-arg constructor / Jackson public setter. Reflection-invoked methods survive automatically by this rule. On uncertainty → leave it.
- Each package gets EXACTLY ONE commit. Do NOT batch packages in a single commit.
</critical_constraints>

<test_impact>
- Packages touched: `org.ctc.admin.controller` (23 files), `org.ctc.backup.config` (1 file), `org.ctc.backup.io` (1 file), `org.ctc.backup.security` (1 file)
- Test classes likely touched (mirror cleanup): `src/test/java/org/ctc/admin/controller/*Test.java` + `*IT.java`, `src/test/java/org/ctc/backup/config/*Test.java`, `src/test/java/org/ctc/backup/io/*Test.java`, `src/test/java/org/ctc/backup/security/*Test.java`
- Mockito stub updates: NONE expected — cleanup classes (D-02) are behavior-preserving, so existing stubs remain valid.
- Bridge-only test deletions: NONE expected — D-04 forbids deletion of any symbol with even a single test caller (test files are within grep scope). If a test calls a method this plan wants to delete, the method stays.
- Estimated test edit count: 0-5 edits, only for comment-thinning of `// given`/`// when`/`// then` adjacent prose (the BDD markers themselves stay per D-11).
- JaCoCo impact: if any covered code is removed alongside its sole test → coverage stays neutral (smaller denominator + smaller numerator). If covered code is removed WITHOUT its test → coverage drops; this is forbidden per D-04 (would leave a dangling test reference detectable by grep).
</test_impact>

<tasks>

<task type="auto">
  <name>Task 1: Cleanup org.ctc.admin.controller package (1 commit)</name>
  <files>src/main/java/org/ctc/admin/controller/**, src/test/java/org/ctc/admin/controller/**</files>
  <read_first>
    - Every `.java` file in `src/main/java/org/ctc/admin/controller/` (23 files)
    - Mirror test files in `src/test/java/org/ctc/admin/controller/`
    - 79-RESEARCH.md §"Per-Package Cleanup Ordering" (this package is rank 1 — clean FIRST)
  </read_first>
  <action>
For each `.java` file under `src/main/java/org/ctc/admin/controller/` and its mirror test file:

1. **Comment-Thinning pass:** Run `grep -nE "Phase [0-9]+|D-[0-9]+|PLAT-[0-9]+|SECU-[0-9]+|GAP-[0-9]+|implements [A-Z]+-[0-9]+|removed in v" &lt;file&gt;` and for each hit, run the Schutzwort grep on that exact line. If Schutzwort-clean → delete the comment line(s). If Schutzwort hits → keep verbatim. For class-level Javadoc: condense to 1-3 lines of responsibility; strip phase-evolution narratives. For BDD `// given`/`// when`/`// then` in tests: keep verbatim.

2. **Dead-code pass:** For each private method, private field, private inner class: run `grep -rE "\\b&lt;symbolName&gt;\\b" src/` (case-sensitive, word-boundary). If grep finds exactly 1 hit (the declaration itself) AND the symbol has no `@PostConstruct`/`@EventListener`/`@TransactionalEventListener`/Jackson lifecycle annotation → delete. Do NOT touch package-private or public symbols unless grep proves zero external references AND the file has no `@JsonCreator`/`@JsonSetter`.

3. **Extract-Method pass:** For each method &gt;50 LOC (or &gt;30 LOC if readability clearly improves per CD-02), extract a clearly-named private helper. Do NOT extract if it forces awkward parameter lists.

4. **Logic-Simplification pass:** Collapse nested `if/else` where one branch is a simple early return. Replace pure transformational loops (`for/collect`) with streams (`.stream().filter().map().toList()`). Leave loops with side effects or early-return logic.

After all 4 passes are applied across the 23 files: stage the changes (`git add src/main/java/org/ctc/admin/controller/ src/test/java/org/ctc/admin/controller/`), then commit with:
```
refactor(79): cleanup org.ctc.admin.controller package — comment-thinning + dead-code + extract-method + logic-simplify

- &lt;N&gt; comment-thinning edits (Schutzwortliste honored)
- &lt;M&gt; dead-code removals (grep-verified zero refs)
- &lt;P&gt; extract-method refactors
- &lt;Q&gt; logic-simplifications
```
Then run `./mvnw test -Dspring.profiles.active=dev`. If GREEN → continue to Task 2. If RED → `git revert HEAD --no-edit`, then `NEEDS_CONTEXT` with the failing test names.
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS" &amp;&amp; git log -1 --pretty=%B | grep -q "refactor(79): cleanup org\.ctc\.admin\.controller package"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS after the commit
    - Last commit message starts with `refactor(79): cleanup org.ctc.admin.controller package`
    - Commit body lists the 4 cleanup-class counters (N/M/P/Q)
    - No comment containing a Schutzwortliste keyword was deleted (verify: `git diff HEAD^ HEAD -- src/main/java/org/ctc/admin/controller/ | grep '^-' | grep -E "MariaDB|H2|JEP|CVE|Lombok|OSIV|Unsafe|TODO|HACK|WORKAROUND|FIXME|pitfall|auditing"` returns nothing)
    - No public method or `@Controller`/`@RequestMapping`-bearing class was deleted
  </acceptance_criteria>
  <done>Single commit lands cleanly; `./mvnw test` GREEN; Schutzwortliste invariant holds.</done>
</task>

<task type="auto">
  <name>Task 2: Cleanup org.ctc.backup.config + org.ctc.backup.io + org.ctc.backup.security (3 commits)</name>
  <files>src/main/java/org/ctc/backup/config/**, src/test/java/org/ctc/backup/config/**, src/main/java/org/ctc/backup/io/**, src/test/java/org/ctc/backup/io/**, src/main/java/org/ctc/backup/security/**, src/test/java/org/ctc/backup/security/**</files>
  <read_first>
    - All `.java` files in `src/main/java/org/ctc/backup/{config,io,security}/` (1 file each = 3 total)
    - Mirror test files in `src/test/java/org/ctc/backup/{config,io,security}/`
    - 79-RESEARCH.md §"Per-Package Cleanup Ordering" (ranks 3, 4, 5 — clean Early)
  </read_first>
  <action>
Process each of the three packages SEPARATELY, in this order: `backup.config` → `backup.io` → `backup.security`. For each package:

1. Apply the same 4-pass cleanup as Task 1 (comment-thinning, dead-code, extract-method, logic-simplification) on the `.java` files under that single package + its test mirror.

2. Stage ONLY that package's files: `git add src/main/java/org/ctc/backup/&lt;pkg&gt;/ src/test/java/org/ctc/backup/&lt;pkg&gt;/`.

3. Verify with `git status` that no other paths are staged. Commit with `refactor(79): cleanup org.ctc.backup.&lt;pkg&gt; package — comment-thinning + dead-code + extract-method + logic-simplify` body following the Task 1 pattern.

4. Run `./mvnw test -Dspring.profiles.active=dev`. If GREEN → continue to next package. If RED → `git revert HEAD --no-edit` for that package's commit only, then `NEEDS_CONTEXT` with the failing test names.

Special note for `backup.config`: this package contains `BackupObjectMapperConfig` per Phase 72 — load-bearing JEP 498 / Jackson 2 vs Jackson 3 / `transitiv` rationale comments MUST stay (Schutzwort hits). Class-Javadoc may be condensed but the technical-rationale block stays verbatim.

Special note for `backup.io` + `backup.security`: these are 1-file packages — expect the commit diff to be small (a few comment edits + maybe 1-2 helper-method extractions). If no cleanup-eligible edits exist in a single-file package, SKIP the commit for that package and record "no eligible edits" in the SUMMARY — empty commits are NOT permitted (per CLAUDE.md no-empty-commit invariant).
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS" &amp;&amp; git log --pretty=%s -4 | grep -cE "refactor\(79\): cleanup org\.ctc\.backup\.(config|io|security) package" | awk '{ if ($1 >= 1) exit 0; else exit 1 }'</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS at the end
    - 1-3 commits landed (one per package with eligible edits; packages with zero edits are SKIPPED, not empty-committed)
    - Each commit body lists the 4 cleanup-class counters (N/M/P/Q)
    - `BackupObjectMapperConfig` `JEP 498`, `Lombok`, `Unsafe`, `transitiv`, `Jackson 2.x`/`Jackson 3` rationale comments are preserved verbatim (verify: `grep -E "JEP|Lombok|Unsafe|transitiv|Jackson" src/main/java/org/ctc/backup/config/BackupObjectMapperConfig.java` still shows hits)
  </acceptance_criteria>
  <done>Up to 3 atomic commits land; `./mvnw test` GREEN after each; no Schutzwort comment was deleted.</done>
</task>

</tasks>

<verification>
- Up to 4 atomic commits exist on `gsd/v1.10-platform-and-backup` matching `refactor(79): cleanup org.ctc.&lt;pkg&gt; package`
- After every commit, `./mvnw test` was BUILD SUCCESS
- `git diff $(git merge-base HEAD origin/master) HEAD -- src/main/java/org/ctc/admin/controller/ | grep '^-' | grep -E "MariaDB|H2|JEP|CVE|Lombok|OSIV|Unsafe|pitfall|auditing|AuditingEntityListener|TODO|HACK|WORKAROUND|FIXME|deadlock|transitiv"` returns ZERO matches (Schutzwortliste invariant)
- No file under `src/main/resources/db/migration/` was touched (Flyway invariant)
</verification>

<success_criteria>
- 1-4 atomic per-package commits land on `gsd/v1.10-platform-and-backup`
- Each commit's `./mvnw test` was BUILD SUCCESS
- No Schutzwortliste keyword was deleted from any comment
- No public `@Controller`/`@RestController` annotation-bearing class signature changed
- Branch `gsd/v1.10-platform-and-backup` is unchanged (no checkout/reset/stash)
</success_criteria>

<output>
After completion, create `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-02a-SUMMARY.md` per `@$HOME/.claude/get-shit-done/templates/summary.md`. Include: list of per-package commit SHAs, N/M/P/Q counters per package, Schutzwort-grep result (zero deletions confirmed), `./mvnw test` BUILD SUCCESS attestation.
</output>
