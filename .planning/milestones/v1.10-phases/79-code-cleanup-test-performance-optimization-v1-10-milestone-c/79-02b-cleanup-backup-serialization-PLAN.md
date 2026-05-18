---
phase: 79
plan: 02b
type: execute
wave: 2
depends_on: [79-01]
files_modified:
  - src/main/java/org/ctc/backup/serialization/**
  - src/test/java/org/ctc/backup/serialization/**
autonomous: true
requirements: [D-01, D-02, D-03, D-04, D-09, D-10, D-11, D-12, D-13]

must_haves:
  truths:
    - "All 22+ Jackson MixIn classes under `org.ctc.backup.serialization` compile cleanly after cleanup"
    - "No `@JsonProperty`/`@JsonCreator`/`@JsonSetter`/`@JsonIdentityInfo` annotation was deleted (these are Jackson lifecycle annotations — D-04 hands-off)"
    - "BackupSerializationModule wiring still registers every MixIn (no MixIn class dropped)"
    - "`./mvnw test` BUILD SUCCESS after the single per-package commit"
  artifacts:
    - path: "git log on branch gsd/v1.10-platform-and-backup"
      provides: "Atomic commit refactor(79): cleanup org.ctc.backup.serialization package"
      pattern: "refactor\\(79\\): cleanup org\\.ctc\\.backup\\.serialization package"
  key_links:
    - from: "MixIn classes"
      to: "BackupSerializationModule.registerMixIns()"
      via: "module wiring intact"
      pattern: "setMixIn\\(|registerMixIns"
---

<objective>
Wave 2 cleanup sweep — `org.ctc.backup.serialization` package (25 files: ~22-24 per-entity Jackson MixIns + `BackupSerializationModule`). Rank 2 in RESEARCH ordering (import count 0). One atomic commit per D-03.

Output: 1 atomic commit `refactor(79): cleanup org.ctc.backup.serialization package`, followed by green `./mvnw test`.

Cleanup classes: comment-thinning (D-09..D-13), dead-code (D-04 — extremely conservative here since MixIns ARE 100% reflection-driven by Jackson), extract-method (rare in MixIns), logic-simplification (rare in MixIns — they are mostly annotation skeletons).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-CONTEXT.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-RESEARCH.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-02a-cleanup-leaf-admin-controller-backup-leaves-PLAN.md
@CLAUDE.md

<interfaces>
**Critical for serialization package:** Every MixIn class is REFLECTION-INVOKED by Jackson via `ObjectMapper.setMixIn(Class&lt;?&gt;, Class&lt;?&gt;)`. The class itself, its constructors, and ALL annotation-bearing fields/methods are referenced ONLY at runtime via reflection — `grep` will not find them. Therefore:

- D-04 dead-code rule is INVERTED here: do NOT delete any MixIn class, field, or method that bears a Jackson annotation (`@JsonProperty`, `@JsonCreator`, `@JsonSetter`, `@JsonIdentityInfo`, `@JsonIdentityReference`, `@JsonIgnore`, `@JsonInclude`, `@JsonFormat`, `@JsonAlias`, `@JsonGetter`, `@JsonAnyGetter`, `@JsonAnySetter`, `@JsonRawValue`, `@JsonRootName`).
- Comment-thinning is the dominant cleanup class here. Class-Javadoc + per-field annotation comments can be condensed per D-12.
- `BackupSerializationModule.registerMixIns()` calls MUST stay 1:1 with MixIn class count.

Cleanup procedure inherits from Plan 02a `<interfaces>` block (the same 4 passes; same Schutzwortliste; same commit pattern; same revert-on-RED rule). Only one package = one commit.

Run BEFORE/AFTER the commit to confirm MixIn count invariant:
`grep -c "setMixIn\|addMixIn" src/main/java/org/ctc/backup/serialization/BackupSerializationModule.java`
Capture the number; it MUST be identical before and after the commit.
</interfaces>
</context>

<critical_constraints>
- You are working on branch `gsd/v1.10-platform-and-backup`. Do NOT run `git stash`, `git checkout`, `git reset`, or switch branches.
- Implement ONLY the tasks listed below. If you find that other files need changes, report `NEEDS_CONTEXT` instead of expanding scope.
- After the commit, run `./mvnw test` and verify it passes BEFORE handing off.
- Schutzwortliste (D-13): Comments containing any of these words must NOT be deleted: MariaDB, H2, JEP, CVE, race, thread-safe, TODO, HACK, WORKAROUND, FIXME, deadlock, OSIV, Lombok, Unsafe, transitiv, transitive, pitfall, auto-commit, auditing, AuditingEntityListener.
- Dead-code removal rule (D-04, INVERTED for this package): MixIn classes are 100% reflection-driven by Jackson. Do NOT delete any class, field, or method that bears a Jackson annotation. `grep` will not find runtime reflection callers. On uncertainty → leave it.
- Single commit for the entire `org.ctc.backup.serialization` package per D-03.
</critical_constraints>

<test_impact>
- Packages touched: `org.ctc.backup.serialization` (25 files)
- Test classes likely touched (mirror): `src/test/java/org/ctc/backup/serialization/*Test.java` (existing per-MixIn unit tests + `BackupSerializationModuleTest` + `BackupEntityAnnotationCleanlinessIT`)
- Mockito stub updates: NONE — MixIn tests use real Jackson serialization, no mocks of the MixIn classes themselves
- Bridge-only test deletions: NONE — every MixIn test exists to assert annotations, not to bridge an old API
- Estimated test edit count: 0-3 edits (comment-thinning only on test classes)
- JaCoCo impact: serialization package is comprehensively tested. Cleanup is comment-dominant; coverage delta should be ~0.
</test_impact>

<tasks>

<task type="auto">
  <name>Task 1: Cleanup org.ctc.backup.serialization package (1 commit)</name>
  <files>src/main/java/org/ctc/backup/serialization/**, src/test/java/org/ctc/backup/serialization/**</files>
  <read_first>
    - All `.java` files in `src/main/java/org/ctc/backup/serialization/` (~25 files)
    - All `.java` files in `src/test/java/org/ctc/backup/serialization/`
    - `src/main/java/org/ctc/backup/serialization/BackupSerializationModule.java` specifically — confirm MixIn registration call site
    - Plan 02a `<interfaces>` block for the canonical 4-pass cleanup procedure + Schutzwortliste + commit-pattern
  </read_first>
  <action>
1. **Pre-flight invariant capture:** Run `grep -cE "setMixIn|addMixIn" src/main/java/org/ctc/backup/serialization/BackupSerializationModule.java` and record the number (call it `N_before`).

2. **Comment-Thinning pass (dominant cleanup class here):** For each MixIn file, condense class-Javadoc to 1-3 lines of responsibility (D-12). Strip phase-N reference prefixes (`// Phase 73 Plan 01:`, `// EXPORT-04:`). Strip was-comments paraphrasing the next annotation (`// id is the primary key`). Run Schutzwort grep before every deletion; keep verbatim on any hit. Test files: keep BDD comments (D-11).

3. **Dead-code pass (CONSERVATIVE for this package):** Skip class-level deletions entirely. Within each MixIn, do NOT delete annotation-bearing fields or methods. Private utility helpers (extremely rare in MixIns) may be deleted if D-04 conditions hold (grep zero refs + no lifecycle annotation).

4. **Extract-Method pass:** MixIns are mostly skeletons — usually nothing to extract. If a MixIn has a custom `@JsonCreator` constructor &gt;30 LOC with clearly extractable validation logic, extract a private helper. Otherwise skip.

5. **Logic-Simplification pass:** Usually nothing to simplify in MixIns. `BackupSerializationModule.registerMixIns()` is a single chained call list — leave the order intact (entity-by-entity readability beats a `Stream.of(...)` rewrite per CD-03).

6. **Post-flight invariant verification:** Re-run `grep -cE "setMixIn|addMixIn" src/main/java/org/ctc/backup/serialization/BackupSerializationModule.java` and assert it equals `N_before`. If different → STOP, undo the registration delta, document a MixIn class accidentally dropped, then re-edit.

7. **Stage + commit:** `git add src/main/java/org/ctc/backup/serialization/ src/test/java/org/ctc/backup/serialization/`. Verify `git status` shows only these paths. Commit:
```
refactor(79): cleanup org.ctc.backup.serialization package — comment-thinning + dead-code + extract-method + logic-simplify

- &lt;N&gt; comment-thinning edits (Schutzwortliste honored)
- 0 dead-code removals (MixIns are reflection-driven, D-04 inverted)
- &lt;P&gt; extract-method refactors
- 0 logic-simplifications (MixIns are annotation skeletons)
- MixIn registration count unchanged: N_before == N_after = &lt;count&gt;
```

8. **Verify:** `./mvnw test -Dspring.profiles.active=dev`. If GREEN → done. If RED → `git revert HEAD --no-edit` and `NEEDS_CONTEXT` with the failing test name (likely `BackupSerializationModuleTest` or a per-entity round-trip test).
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS" &amp;&amp; git log -1 --pretty=%B | grep -q "refactor(79): cleanup org\.ctc\.backup\.serialization package" &amp;&amp; git log -1 --pretty=%B | grep -q "MixIn registration count unchanged"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Last commit message starts with `refactor(79): cleanup org.ctc.backup.serialization package`
    - Commit body contains "MixIn registration count unchanged" line with N_before == N_after
    - `grep '^-' git diff HEAD^ HEAD -- src/main/java/org/ctc/backup/serialization/ | grep -E "@JsonProperty|@JsonCreator|@JsonSetter|@JsonIdentityInfo|@JsonIgnore|@JsonInclude|@JsonFormat|@JsonAlias|@JsonGetter|@JsonAnyGetter|@JsonAnySetter|@JsonRawValue|@JsonRootName"` returns ZERO (no Jackson annotation lines deleted)
    - No Schutzwortliste keyword deleted
  </acceptance_criteria>
  <done>Single commit lands; `./mvnw test` GREEN; MixIn registration count invariant holds; zero Jackson annotation lines deleted.</done>
</task>

</tasks>

<verification>
- One atomic commit `refactor(79): cleanup org.ctc.backup.serialization package` exists on `gsd/v1.10-platform-and-backup`
- `./mvnw test` BUILD SUCCESS at HEAD
- MixIn registration call count in `BackupSerializationModule` is unchanged
- Zero Jackson annotation lines were deleted from MixIn files
</verification>

<success_criteria>
- 1 atomic commit lands on `gsd/v1.10-platform-and-backup`
- `./mvnw test` BUILD SUCCESS
- MixIn registration count unchanged (N_before == N_after)
- No Schutzwortliste keyword and no Jackson annotation line deleted
- Branch is still `gsd/v1.10-platform-and-backup`
</success_criteria>

<output>
After completion, create `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-02b-SUMMARY.md` per `@$HOME/.claude/get-shit-done/templates/summary.md`. Include: commit SHA, N/M/P/Q counters, N_before == N_after MixIn registration count, Schutzwort/Jackson-annotation grep proofs.
</output>
