---
phase: 79
plan: 02h
type: execute
wave: 2
depends_on: [79-01]
files_modified:
  - src/main/java/org/ctc/domain/service/**
  - src/test/java/org/ctc/domain/service/**
  - src/main/java/org/ctc/domain/exception/**
  - src/test/java/org/ctc/domain/exception/**
  - src/main/java/org/ctc/domain/repository/**
  - src/test/java/org/ctc/domain/repository/**
  - src/main/java/org/ctc/domain/model/**
  - src/test/java/org/ctc/domain/model/**
autonomous: true
requirements: [D-01, D-02, D-03, D-04, D-09, D-10, D-11, D-12, D-13, D-18]

must_haves:
  truths:
    - "Every JPA `@Entity` retains: `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, all `@Column`, all `@ManyToOne`/`@OneToMany`/`@ManyToMany`, lifecycle `@PrePersist`/`@PreUpdate`, no-arg constructor"
    - "Every repository interface retains its `extends JpaRepository<Entity, UUID>` signature and all custom finder methods"
    - "Every `@Service` class retains its `@Transactional` boundaries (read-only / default / propagation)"
    - "ScoringService `aggregateMatchScores()` is unchanged (CLAUDE.md memory `feedback_score_aggregation`)"
    - "JaCoCo coverage at HEAD stays ≥ 0.82 (D-18) — this is the most coverage-sensitive Wave 2 plan"
    - "`./mvnw test` BUILD SUCCESS after each per-package commit"
  artifacts:
    - path: "git log on branch gsd/v1.10-platform-and-backup"
      provides: "Up to 4 atomic commits, one per cleaned package"
      pattern: "refactor\\(79\\): cleanup org\\.ctc\\.domain\\.(service|exception|repository|model) package"
  key_links:
    - from: "Domain entities"
      to: "Flyway V1 schema"
      via: "no schema change (Flyway invariant per CLAUDE.md §Constraints)"
      pattern: "@Entity|@Column"
---

<objective>
Wave 2 cleanup sweep — the four LAST-rank packages from RESEARCH ordering. These have the HIGHEST import counts in the codebase (147 for `domain.repository`, 264 for `domain.model`) — cleaned LAST so all upstream callers are already tidied before the foundation is touched.

Packages in scope (in this order):
1. `org.ctc.domain.service` — 25 files (import count 68)
2. `org.ctc.domain.exception` — 3 files (import count 52)
3. `org.ctc.domain.repository` — 24 files (import count 147)
4. `org.ctc.domain.model` — 29 files (import count 264 — TOP rank)

Output: 1-4 atomic per-package commits. Cleanup classes per D-02 with EXTREME caution on entities (JPA reflection invariants are most numerous here).
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
@.planning/codebase/CONVENTIONS.md
@.planning/codebase/STRUCTURE.md

<interfaces>
**JPA Entity invariants (CLAUDE.md §"Conventions" §"Lombok Usage" + §"Constraints" `feedback_entity_refactoring` memory):**

For every `@Entity` class in `src/main/java/org/ctc/domain/model/`:
- `@Entity`, `@Table(name = "...")` — frozen (Flyway schema invariant)
- `@Id`, `@GeneratedValue(strategy = ...)`, `@UuidGenerator` if used — frozen
- All `@Column(name = "...", nullable = ..., length = ...)` — frozen
- All `@ManyToOne`, `@OneToMany`, `@ManyToMany`, `@OneToOne` + their `@JoinColumn`/`@JoinTable` annotations — frozen
- `@MappedSuperclass` on `BaseEntity` — frozen
- `@EntityListeners(AuditingEntityListener.class)` on `BaseEntity` — Schutzwort-protected (`auditing`, `AuditingEntityListener`)
- Lombok `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`, `@ToString(exclude = ...)` — frozen (CLAUDE.md convention)
- `@Enumerated(EnumType.STRING)` on enum-typed fields — frozen

For every Spring Data repository:
- `extends JpaRepository<Entity, UUID>` — frozen
- Custom finder methods with Spring Data naming conventions (`findBySeasonIdOrderBy...`) — frozen (callers depend on them)
- `@EntityGraph(attributePaths = ...)` annotations on finders — frozen (Phase 73 EXPORT-05 + CLAUDE.md OSIV-optimization rule)

For every domain service:
- `@Service` annotation — frozen
- `@Transactional(...)` boundaries — frozen
- Public methods called from controllers / other services — frozen signatures (signature change = breaking change = out of Phase 79 scope per D-01 verhaltens-erhaltend)

**Specifically-protected services (memory + code-context hits):**
- `ScoringService.aggregateMatchScores()` — `feedback_score_aggregation` memory rule
- `StandingsService.java` line 139 — lazy-collection cleanup is v1.11+ deferred (CONTEXT.md §"Out of scope"); DO NOT touch
- `RaceLineupService` — RaceLineup source-of-truth invariant (CLAUDE.md §"Architectural Principles")
- `FileStorageService` — upload-dir contract (`app.upload-dir`)

**Schutzwort hotspots:**
- `domain.model`: `auditing`, `AuditingEntityListener`, `OSIV`, `Lombok`
- `domain.repository`: `OSIV`, `auditing` (EntityGraph rationale)
- `domain.service`: `OSIV`, `transactional`, `auditing`, `Lombok`, `race`, `thread-safe` (concurrent CRUD)
- `domain.exception`: typically minimal Schutzwort surface

**JaCoCo floor (D-18):** This plan touches ~81 source files; the coverage report is sensitive to ANY dead-code deletion that orphans test coverage. Run `./mvnw verify` (not just `./mvnw test`) AFTER each commit AS WELL — `./mvnw verify` executes the JaCoCo check gate. If coverage drops below 0.82 → `git revert HEAD`, escalate as `NEEDS_CONTEXT`.

**Cleanup procedure** inherits from Plan 02a `<interfaces>` block.
</interfaces>
</context>

<critical_constraints>
- You are working on branch `gsd/v1.10-platform-and-backup`. Do NOT run `git stash`, `git checkout`, `git reset`, or switch branches.
- Implement ONLY the tasks listed below. If you find that other files need changes, report `NEEDS_CONTEXT` instead of expanding scope.
- After each per-package commit, run `./mvnw verify` (NOT just `./mvnw test`) — this plan must respect the JaCoCo ≥ 0.82 gate per D-18.
- Schutzwortliste (D-13): Comments containing any of these words must NOT be deleted: MariaDB, H2, JEP, CVE, race, thread-safe, TODO, HACK, WORKAROUND, FIXME, deadlock, OSIV, Lombok, Unsafe, transitiv, transitive, pitfall, auto-commit, auditing, AuditingEntityListener.
- Dead-code removal rule (D-04): Entities use JPA reflection for getters/setters/no-arg-constructor. Hibernate uses bytecode enhancement for lazy fetching. DO NOT delete any field on an `@Entity` even if grep shows zero callers — it may be loaded from DB via column mapping. On uncertainty → leave it.
- Flyway invariant (CLAUDE.md §"Constraints"): NEVER touch `src/main/resources/db/migration/V*__*.sql`. Schema changes must be a new migration file — but this plan does NOT add migrations.
- StandingsService.java line 139 lazy-collection cleanup is DEFERRED to v1.11+ per CONTEXT.md §"Out of scope" (D-16 carry-over list). DO NOT touch.
- Each package gets EXACTLY ONE commit.
</critical_constraints>

<test_impact>
- Packages touched: `domain.service` (25 files), `domain.exception` (3 files), `domain.repository` (24 files), `domain.model` (29 files) = 81 source files
- Test classes likely touched: ~100+ unit + IT tests across all 4 packages — this is the broadest test-surface in Wave 2
- Mockito stub updates: SOME — domain services have many Mockito-stubbed repository/service collaborators. Mockito-strict-mode risk is HIGHEST here. Expect 5-15 unused-stub removals after extract-method passes.
- Bridge-only test deletions: Possibly 1-2 — older `*Test.java` files testing methods deleted in earlier phases (v1.5/v1.8/v1.9). Apply D-04 strictly; if a test exists, the method has a reflection-equivalent caller (the test itself) and the method stays.
- Estimated test edit count: 10-30 (comment-thinning + Mockito stub cleanup)
- JaCoCo impact: **HIGHEST SENSITIVITY**. `domain.service`/`domain.model`/`domain.repository` are JaCoCo-INCLUDED (no excludes in pom.xml). Any private-helper deletion that orphans test coverage drops the percentage. Use `./mvnw verify` (with JaCoCo gate) after EACH commit instead of `./mvnw test`.
- Flyway risk: ZERO — this plan touches no `.sql` files.
</test_impact>

<tasks>

<task type="auto">
  <name>Task 1: Cleanup org.ctc.domain.service package (1 commit)</name>
  <files>src/main/java/org/ctc/domain/service/**, src/test/java/org/ctc/domain/service/**</files>
  <read_first>
    - All 25 `.java` files in `src/main/java/org/ctc/domain/service/`
    - Mirror test files
    - Plan 02a `<interfaces>` for the 4-pass procedure
    - CLAUDE.md `feedback_score_aggregation` memory (ScoringService invariant)
    - CONTEXT.md §"Out of scope" StandingsController.java:139 lazy-collection (DEFERRED)
  </read_first>
  <action>
Apply the 4-pass cleanup. SPECIFIC PROCEDURE:

1. **Comment-thinning:** Strip Phase-N prefixes. OSIV-related comments (Schutzwort: `OSIV`) and `@Transactional`-rationale comments stay verbatim.

2. **Dead-code:** D-04 conservative. `@Service` annotations + `@Transactional` boundaries STAY. Private helpers with grep-zero refs are deletion candidates. Public methods called from controllers (grep `src/main/java/org/ctc/admin/controller/` for the method name) STAY.

3. **Extract-method:** PRIMARY OPPORTUNITY in this package. Long-method services include `SeasonManagementService`, `SwissPairingService`, `PlayoffService`, `StandingsService`. Apply >50 LOC threshold (CD-02 discretion >30 LOC). Extract private helpers WITHIN the same class — do NOT cross class boundaries (would change public API).

4. **Logic-simplification:** Streams over loops for pure transformations. Imperative loops STAY when control flow has side effects or early-return logic (common in scoring/standings calculations).

5. **Protected services — DO NOT TOUCH the method bodies of:**
   - `ScoringService.aggregateMatchScores()` (feedback memory)
   - `StandingsService.java` line 139 lazy-collection (deferred to v1.11+)
   - `RaceLineupService` method signatures (RaceLineup source-of-truth)

Stage `git add src/main/java/org/ctc/domain/service/ src/test/java/org/ctc/domain/service/`. Confirm with `git status`. Commit `refactor(79): cleanup org.ctc.domain.service package` with the 4-counter body. Run `./mvnw verify -Dspring.profiles.active=dev` (full verify with JaCoCo gate, NOT just `./mvnw test`). If RED OR JaCoCo &lt; 0.82 → `git revert HEAD --no-edit` and `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>./mvnw verify -Dspring.profiles.active=dev -q 2>&amp;1 | tail -5 | grep -q "BUILD SUCCESS" &amp;&amp; git log -1 --pretty=%B | grep -q "refactor(79): cleanup org\.ctc\.domain\.service package"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw verify` BUILD SUCCESS (includes JaCoCo gate ≥ 0.82)
    - Commit lands
    - `ScoringService.aggregateMatchScores()` method body unchanged
    - `StandingsService.java` line 139 region unchanged (lazy-collection deferred)
    - No `@Service` or `@Transactional` annotation deleted
    - No Schutzwortliste keyword deleted (especially `OSIV`, `transactional`, `auditing`, `Lombok`)
  </acceptance_criteria>
  <done>Commit lands; `./mvnw verify` GREEN (including JaCoCo gate); protected services + invariants hold.</done>
</task>

<task type="auto">
  <name>Task 2: Cleanup org.ctc.domain.exception package (1 commit)</name>
  <files>src/main/java/org/ctc/domain/exception/**, src/test/java/org/ctc/domain/exception/**</files>
  <read_first>
    - `src/main/java/org/ctc/domain/exception/EntityNotFoundException.java`
    - `src/main/java/org/ctc/domain/exception/BusinessRuleException.java`
    - `src/main/java/org/ctc/domain/exception/ValidationException.java`
    - Mirror test files
    - Plan 02a `<interfaces>` for the 4-pass procedure
  </read_first>
  <action>
Apply the 4-pass cleanup to the 3 exception classes. SPECIFIC INVARIANTS: every exception is mapped to an HTTP status in `GlobalExceptionHandler` (404 / 409 / 400 per STRUCTURE.md). Constructors `(String message)`, `(String message, Throwable cause)` STAY. `serialVersionUID` STAYS if present. `@ResponseStatus(...)` annotation STAYS if present.

Stage `git add src/main/java/org/ctc/domain/exception/ src/test/java/org/ctc/domain/exception/`. Commit `refactor(79): cleanup org.ctc.domain.exception package` with the 4-counter body (likely all 0 except comment-thinning). SKIP commit if no eligible edits. `./mvnw verify -Dspring.profiles.active=dev`. RED OR JaCoCo &lt; 0.82 → revert + `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>./mvnw verify -Dspring.profiles.active=dev -q 2>&amp;1 | tail -5 | grep -q "BUILD SUCCESS"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw verify` BUILD SUCCESS
    - Commit lands (or SUMMARY records "no eligible edits")
    - All 3 exception classes still present with their constructors
    - No `@ResponseStatus` deleted (if present)
    - No Schutzwortliste keyword deleted
  </acceptance_criteria>
  <done>Commit lands (or skipped); `./mvnw verify` GREEN; exception class set + constructors intact.</done>
</task>

<task type="auto">
  <name>Task 3: Cleanup org.ctc.domain.repository package (1 commit)</name>
  <files>src/main/java/org/ctc/domain/repository/**, src/test/java/org/ctc/domain/repository/**</files>
  <read_first>
    - All 24 `.java` files in `src/main/java/org/ctc/domain/repository/`
    - Mirror test files
    - Plan 02a `<interfaces>` for the 4-pass procedure
    - Phase 73 plan 02 (`@EntityGraph` attributes) — `BackupRepositoryEntityGraphIT` is the integration gate
  </read_first>
  <action>
Apply the 4-pass cleanup to the 24 repository interfaces. SPECIFIC INVARIANTS:

1. **Interface signatures STAY.** Every method declared in a Spring Data repository is a contract for the framework — Spring generates the implementation via reflection. Even unused methods with grep-zero callers may be there for future use (or invoked dynamically). D-04 (b) lifecycle-annotation check is INVERTED for interface methods: assume they have a reflection-invoked caller and DO NOT delete.

2. **`@EntityGraph(attributePaths = {...})` annotations on finder methods STAY** — they implement OSIV-optimization per CLAUDE.md §"OSIV" and Phase 73 EXPORT-05.

3. **Custom `@Query(...)` annotations STAY** — these are explicit SQL/JPQL contracts.

4. **Cleanup is dominated by comment-thinning:** Strip Phase-N prefixes from Javadoc, condense class-level Javadoc per D-12. Schutzwort hits stay verbatim.

5. **`findAllForBackup()` methods (Phase 73 plan 02) STAY** — they are called from `BackupExportService`.

Stage `git add src/main/java/org/ctc/domain/repository/ src/test/java/org/ctc/domain/repository/`. Commit `refactor(79): cleanup org.ctc.domain.repository package` with the 4-counter body (likely 0 dead-code, 0 extract-method, 0 logic-simplify — interfaces have no method bodies). `./mvnw verify -Dspring.profiles.active=dev`. RED OR JaCoCo &lt; 0.82 → revert + `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>./mvnw verify -Dspring.profiles.active=dev -q 2>&amp;1 | tail -5 | grep -q "BUILD SUCCESS" &amp;&amp; git log -1 --pretty=%B | grep -q "refactor(79): cleanup org\.ctc\.domain\.repository package" &amp;&amp; [ "$(grep -c "findAllForBackup" src/main/java/org/ctc/domain/repository/*.java)" -gt 0 ]</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw verify` BUILD SUCCESS
    - Commit lands
    - Every repository still `extends JpaRepository<..., UUID>`
    - `@EntityGraph` annotations preserved (verify: `grep -c "@EntityGraph" src/main/java/org/ctc/domain/repository/*.java | awk -F: '{s+=$2} END{print s}'` before-vs-after is identical)
    - `findAllForBackup()` methods PRESENT
    - No Schutzwortliste keyword deleted
  </acceptance_criteria>
  <done>Commit lands; `./mvnw verify` GREEN; repository contract surface intact.</done>
</task>

<task type="auto">
  <name>Task 4: Cleanup org.ctc.domain.model package (1 commit) — JPA entities, MAXIMAL CAUTION</name>
  <files>src/main/java/org/ctc/domain/model/**, src/test/java/org/ctc/domain/model/**</files>
  <read_first>
    - All 29 `.java` files in `src/main/java/org/ctc/domain/model/` (20 entities + 2 enums + `BaseEntity` + supporting types)
    - Mirror test files
    - Plan 02a `<interfaces>` for the 4-pass procedure
    - CLAUDE.md §"Conventions" §"Lombok Usage" (Entity Lombok annotations are mandatory)
  </read_first>
  <action>
Apply the 4-pass cleanup — MOST CONSERVATIVE PACKAGE OF THIS PLAN. SPECIFIC PROCEDURE:

1. **Pre-flight invariant capture:**
```
grep -c "@Entity" src/main/java/org/ctc/domain/model/*.java | awk -F: '{s+=$2} END{print s}'  # N_entities_before
grep -c "@Column" src/main/java/org/ctc/domain/model/*.java | awk -F: '{s+=$2} END{print s}'  # N_columns_before
grep -c "@ManyToOne\|@OneToMany\|@ManyToMany\|@OneToOne" src/main/java/org/ctc/domain/model/*.java | awk -F: '{s+=$2} END{print s}'  # N_relations_before
```
Record all three.

2. **Comment-thinning ONLY:** This is the dominant cleanup class for entities. Strip Phase-N prefixes (`// Phase 56 D-02:`, `// SeasonPhase added in v1.9`). Class-Javadoc condensed to 1-3 lines per D-12. Schutzwort-protected comments (`auditing`, `AuditingEntityListener`, `OSIV`, `Lombok`, `MariaDB`, `H2`, `LONGTEXT`) STAY verbatim.

3. **NO dead-code removal in this package.** Entity fields are mapped to DB columns via reflection — grep cannot prove zero usage. Lombok-generated getters/setters are reflection-equivalent. Skip the dead-code pass entirely. Record `0 dead-code removals — entity package, D-04 inverted` in the commit body.

4. **NO extract-method.** Entity methods are typically: Lombok-generated (do not extract), `@PrePersist`/`@PreUpdate` lifecycle (do not extract — Hibernate-callback contract), or simple convenience methods (`isSubTeam()`, `isActive()`). Skip the extract-method pass entirely.

5. **NO logic-simplification.** Same reasoning. Skip.

6. **Post-flight invariant verification:**
```
grep -c "@Entity" src/main/java/org/ctc/domain/model/*.java | awk -F: '{s+=$2} END{print s}'  # N_entities_after
grep -c "@Column" src/main/java/org/ctc/domain/model/*.java | awk -F: '{s+=$2} END{print s}'  # N_columns_after
grep -c "@ManyToOne\|@OneToMany\|@ManyToMany\|@OneToOne" src/main/java/org/ctc/domain/model/*.java | awk -F: '{s+=$2} END{print s}'  # N_relations_after
```
ALL THREE counts MUST equal the pre-flight values. If any differs → STOP, undo the deletion.

Stage `git add src/main/java/org/ctc/domain/model/ src/test/java/org/ctc/domain/model/`. Commit `refactor(79): cleanup org.ctc.domain.model package — comment-thinning only, JPA mappings frozen` with body:
```
- &lt;N&gt; comment-thinning edits (Schutzwortliste honored)
- 0 dead-code removals (entity package, D-04 inverted)
- 0 extract-method refactors (Lombok-generated + lifecycle methods)
- 0 logic-simplifications (entity behavior frozen)
- @Entity count unchanged: N_before == N_after = &lt;count&gt;
- @Column count unchanged: N_before == N_after = &lt;count&gt;
- @ManyToOne/@OneToMany/@ManyToMany/@OneToOne count unchanged: N_before == N_after = &lt;count&gt;
```
Run `./mvnw verify -Dspring.profiles.active=dev`. RED OR JaCoCo &lt; 0.82 → `git revert HEAD --no-edit` and `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>./mvnw verify -Dspring.profiles.active=dev -q 2>&amp;1 | tail -5 | grep -q "BUILD SUCCESS" &amp;&amp; git log -1 --pretty=%B | grep -q "refactor(79): cleanup org\.ctc\.domain\.model package" &amp;&amp; git log -1 --pretty=%B | grep -q "@Entity count unchanged"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw verify` BUILD SUCCESS (including JaCoCo gate ≥ 0.82)
    - Commit `refactor(79): cleanup org.ctc.domain.model package` lands
    - `@Entity` / `@Column` / relation-annotation counts unchanged (3 invariants in commit body)
    - No Schutzwortliste keyword deleted
    - No file under `src/main/resources/db/migration/` touched
  </acceptance_criteria>
  <done>Commit lands; `./mvnw verify` GREEN; all 3 entity-annotation counts invariant; Flyway untouched.</done>
</task>

</tasks>

<verification>
- Up to 4 atomic commits land on `gsd/v1.10-platform-and-backup`
- `./mvnw verify` BUILD SUCCESS at HEAD (JaCoCo gate ≥ 0.82 holds — D-18 invariant)
- Entity-annotation counts (Entity / Column / Relations) unchanged
- `@EntityGraph` annotation count on repository methods unchanged
- `ScoringService.aggregateMatchScores()` body unchanged
- `StandingsController.java:139` lazy-collection region unchanged (deferred to v1.11+)
- No Flyway migration touched
</verification>

<success_criteria>
- 1-4 atomic per-package commits land
- `./mvnw verify` BUILD SUCCESS after each (JaCoCo gate ≥ 0.82 holds)
- All entity-annotation / repository-EntityGraph / service-Transactional invariants hold
- All Schutzwort keywords intact
- Flyway migrations untouched (CLAUDE.md §"Constraints")
- Branch unchanged
</success_criteria>

<output>
After completion, create `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-02h-SUMMARY.md` per `@$HOME/.claude/get-shit-done/templates/summary.md`. Include: per-package commit SHAs, N/M/P/Q counters, all entity-annotation invariant proofs (Entity/Column/Relations before/after), `@EntityGraph` count proof, JaCoCo % at HEAD.
</output>
