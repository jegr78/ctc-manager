---
phase: 79
plan: 03
type: execute
wave: 3
depends_on: [79-01, 79-02a, 79-02b, 79-02c, 79-02d, 79-02e, 79-02f, 79-02g, 79-02h]
files_modified:
  - pom.xml
autonomous: true
requirements: [D-05, D-07]

must_haves:
  truths:
    - "Surefire is configured with `<forkCount>2C</forkCount><reuseForks>true</reuseForks>` in addition to the existing argLine"
    - "Failsafe `default-it` execution is configured with `<forkCount>1C</forkCount><reuseForks>true</reuseForks>`"
    - "Failsafe `e2e-it` execution retains its current single-fork behavior (Playwright requires single Spring context per port)"
    - "Both Surefire and Failsafe argLines preserve `@{argLine} --sun-misc-unsafe-memory-access=allow -javaagent:...mockito-core.jar` (JEP 498 + Lombok #3959 + Mockito agent invariants)"
    - "Surefire + Failsafe both add `<excludedGroups>flaky</excludedGroups>` (D-07 quarantine mechanism, max-5 cap)"
    - "`./mvnw verify -Pe2e` BUILD SUCCESS with the new fork configuration on H2/dev profile"
    - "JaCoCo line coverage stays ≥ 0.82 (D-18 invariant)"
  artifacts:
    - path: "pom.xml"
      provides: "Surefire + Failsafe fork configuration + flaky-quarantine"
      contains: "forkCount"
    - path: "git log on branch gsd/v1.10-platform-and-backup"
      provides: "Single commit applying Surefire/Failsafe parallelization config"
      pattern: "perf\\(79\\): enable Surefire/Failsafe process-level parallelism"
  key_links:
    - from: "pom.xml Surefire config"
      to: "JaCoCo agent propagation"
      via: "`@{argLine}` late-property evaluation (Surefire late-resolution syntax)"
      pattern: "@\\{argLine\\}"
    - from: "Wave 1 independence audit"
      to: "Wave 3 parallelization gate"
      via: "GREEN verdict in 79-INDEPENDENCE-AUDIT.md"
      pattern: "Independence audit GREEN"
---

<objective>
Wave 3 of Phase 79: enable process-level test parallelism on Surefire (`forkCount=2C reuseForks=true`) AND Failsafe `default-it` (`forkCount=1C reuseForks=true`) AND introduce the `@Tag("flaky")` quarantine mechanism via `<excludedGroups>flaky</excludedGroups>` on both plugins. This is the D-05 hebel 2+3 implementation — gated by Wave 1's independence audit GREEN verdict (Plan 01) and Wave 2's cleanup-suite stability.

Purpose: deliver the wallclock reduction toward D-06's ≥ 30% target. Process-level parallelism is the only D-05-approved layer (`@Execution(CONCURRENT)` thread-level is REJECTED per D-2.1).

Output: 1 atomic commit modifying `pom.xml` (Surefire block at lines 260-271 + Failsafe `default-it` execution block at lines 283-300 + new `<excludedGroups>` element on both). Failsafe `e2e-it` execution is UNCHANGED (Playwright single-port constraint).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-CONTEXT.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-RESEARCH.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md
@CLAUDE.md
@pom.xml

<interfaces>
**Current Surefire block (pom.xml lines 260-271, verified by pre-flight read):**

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <!-- JEP 498 escape: silence Lombok #3959 Permit/Unsafe warnings until upstream fix lands -->
        <argLine>@{argLine} --sun-misc-unsafe-memory-access=allow -javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar</argLine>
        <excludes>
            <exclude>**/e2e/**</exclude>
            <exclude>**/*IT.java</exclude>
        </excludes>
    </configuration>
</plugin>
```

**Target Surefire block:**

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <!-- JEP 498 escape: silence Lombok #3959 Permit/Unsafe warnings until upstream fix lands -->
        <argLine>@{argLine} --sun-misc-unsafe-memory-access=allow -javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar</argLine>
        <!-- Process-level parallelism: 2 forked JVMs per CPU core, reuse fork across test classes.
             Mockito + JUnit 5 unit tests are thread-safe per process; this is the safe layer. -->
        <forkCount>2C</forkCount>
        <reuseForks>true</reuseForks>
        <excludedGroups>flaky</excludedGroups>
        <excludes>
            <exclude>**/e2e/**</exclude>
            <exclude>**/*IT.java</exclude>
        </excludes>
    </configuration>
</plugin>
```

**Current Failsafe `default-it` execution (pom.xml lines 283-300, verified by pre-flight read):**

```xml
<execution>
    <id>default-it</id>
    <goals>
        <goal>integration-test</goal>
        <goal>verify</goal>
    </goals>
    <configuration>
        <!-- JEP 498 escape: silence Lombok #3959 Permit/Unsafe warnings until upstream fix lands -->
        <argLine>@{argLine} --sun-misc-unsafe-memory-access=allow -javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar</argLine>
        <includes>
            <include>**/*IT.java</include>
        </includes>
        <excludes>
            <exclude>**/e2e/**</exclude>
        </excludes>
    </configuration>
</execution>
```

**Target Failsafe `default-it` execution:**

```xml
<execution>
    <id>default-it</id>
    <goals>
        <goal>integration-test</goal>
        <goal>verify</goal>
    </goals>
    <configuration>
        <!-- JEP 498 escape: silence Lombok #3959 Permit/Unsafe warnings until upstream fix lands -->
        <argLine>@{argLine} --sun-misc-unsafe-memory-access=allow -javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar</argLine>
        <!-- Process-level parallelism: 1 forked JVM per CPU core. Conservative vs. Surefire's 2C
             because each IT boots a Spring context (~3-5 s startup). Testcontainers MariaDB ITs
             use @DynamicPropertySource dynamic ports — safe from collision.
             The 3 ImportLock ITs use @DirtiesContext(BEFORE_EACH) — context rebuilds in same fork are safe. -->
        <forkCount>1C</forkCount>
        <reuseForks>true</reuseForks>
        <excludedGroups>flaky</excludedGroups>
        <includes>
            <include>**/*IT.java</include>
        </includes>
        <excludes>
            <exclude>**/e2e/**</exclude>
        </excludes>
    </configuration>
</execution>
```

**Failsafe `e2e-it` execution (pom.xml lines 401-415, in `<profile id="e2e">`):** UNCHANGED. Playwright E2E requires single Spring context per port — adding forks here breaks `RANDOM_PORT` semantics.

**argLine invariant (RESEARCH §"argLine Propagation Pattern"):** `@{argLine}` is late-property evaluation; `${argLine}` is early-evaluation and BREAKS JaCoCo agent propagation in forked JVMs. The existing 3 argLine entries (lines 265, 292, 412) MUST keep the `@{argLine}` syntax — the `forkCount` additions are independent elements and do NOT change argLine syntax.

**Symbol-existence audit (run before editing pom.xml):**
```
grep -n "@{argLine}" pom.xml | wc -l   # MUST be 3 (lines 265, 292, 412)
grep -nE "forkCount" pom.xml | wc -l   # MUST be 0 (greenfield)
grep -nE "reuseForks" pom.xml | wc -l  # MUST be 0 (greenfield)
grep -nE "excludedGroups" pom.xml | wc -l  # MUST be 0 (greenfield)
```
If any of these differs from the expected count → STOP and report `NEEDS_CONTEXT` (Wave 2 cleanup may have inadvertently touched pom.xml; revisit Plan 02h scope).
</interfaces>
</context>

<critical_constraints>
- You are working on branch `gsd/v1.10-platform-and-backup`. Do NOT run `git stash`, `git checkout`, `git reset`, or switch branches.
- Implement ONLY the tasks listed below. If you find that other files need changes, report `NEEDS_CONTEXT` instead of expanding scope.
- After the commit, run `./mvnw verify -Pe2e -Dspring.profiles.active=dev -Ddocker.available=true` AND verify it passes BEFORE handing off.
- Schutzwortliste (D-13): The JEP 498 / Lombok #3959 argLine comment at line 264 + Mockito-agent rationale MUST be preserved verbatim (contains `JEP`, `Lombok`, `Unsafe`).
- argLine invariant (RESEARCH Pitfall 1): `@{argLine}` syntax (at-sign late-evaluation) MUST be preserved on all 3 argLine entries. NEVER change to `${argLine}` (would break JaCoCo agent in forked JVMs).
- Failsafe `e2e-it` execution (lines 401-415, in `<profile id="e2e">`) MUST stay unchanged — Playwright single-port constraint.
- Wave 1 gate: this plan executes ONLY if `.planning/phases/79-.../79-INDEPENDENCE-AUDIT.md` contains the "GREEN" Verdict line. Verify via `grep -q "Independence audit GREEN" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md` before editing. If absent → STOP / `NEEDS_CONTEXT` (Wave 1 has not signed off).
</critical_constraints>

<test_impact>
- Files touched: `pom.xml` ONLY (no Java source code)
- Test classes touched: 0 directly. Indirectly: ALL ~1200 unit tests + ~50 integration tests now run in parallel forks.
- Mockito stub updates: NONE — Mockito is process-isolated per fork; existing stubs work unchanged.
- Bridge-only test deletions: NONE
- Estimated test edit count: 0
- JaCoCo impact: ZERO if `@{argLine}` propagation is preserved (it is). The JaCoCo `prepare-agent` goal injects the agent path into the `argLine` property; Surefire/Failsafe `@{argLine}` resolves it in forked JVMs. RESEARCH Pitfall 1 + 4 cover this exhaustively.
- Flaky-tag impact: 0 tests are currently tagged `@Tag("flaky")` (RESEARCH verified). Adding `<excludedGroups>flaky</excludedGroups>` is a no-op for the default build today; the mechanism is now AVAILABLE for future use (max 5 quarantined tests per D-07 hard cap; monthly-review gate per CD-05).
- Coverage-impact risk: a future test tagged `@Tag("flaky")` covering unique lines would drop coverage. D-07 explicitly notes this; CD-05 cap of 5 is the mitigation.
- Concurrent-fork resource risk: GitHub Actions ubuntu-latest = 2 CPU cores × 7 GB RAM. `2C` Surefire = 4 forked JVMs × ~512 MB = ~2 GB heap — well within CI limits per RESEARCH Open Question Q2. `1C` Failsafe = 2 forked JVMs each booting Spring (~3-5s startup) — `forkCount=1C` is the conservative Testcontainers-safe default per RESEARCH §3.
</test_impact>

<tasks>

<task type="auto">
  <name>Task 1: Verify Wave 1 GREEN gate + pom.xml symbol-existence audit</name>
  <files>pom.xml (read-only verification)</files>
  <read_first>
    - `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md` (Wave 1 GREEN verdict gate)
    - `pom.xml` lines 1-50, 260-302, 401-415 (Surefire/Failsafe targets)
  </read_first>
  <action>
1. Verify the Wave 1 gate: `grep -q "Independence audit GREEN" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md`. If exit code != 0 → STOP, report `NEEDS_CONTEXT` with "Wave 1 independence audit not signed off GREEN".

2. Run the pre-flight symbol-existence audit:
```
grep -c "@{argLine}" pom.xml
grep -cE "<forkCount>" pom.xml
grep -cE "<reuseForks>" pom.xml
grep -cE "<excludedGroups>" pom.xml
```
Expected: 3 / 0 / 0 / 0. If any value differs → STOP, report `NEEDS_CONTEXT` with the deviation (likely a Wave 2 cleanup inadvertently touched pom.xml).

3. Locate the exact line ranges in the current pom.xml: Surefire `<configuration>` is around lines 263-270; Failsafe `<execution id="default-it">` is around lines 284-300; Failsafe `<execution id="e2e-it">` is around lines 401-415. The line numbers may have shifted slightly due to Wave 2 commits but the structure is unchanged. Note actual line ranges for Task 2 patch.

4. Run a clean `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true` to confirm BUILD SUCCESS on the current (pre-parallel) configuration as a sanity check. Record the duration as an "intermediate after-cleanup, before-parallel" datapoint in `79-AUTO-UAT.md` (`## Intermediate Measurements` subsection if it does not exist; append the row). Do NOT make this the final measurement — that is Plan 07's job.
  </action>
  <verify>
    <automated>grep -q "Independence audit GREEN" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md &amp;&amp; [ "$(grep -c "@{argLine}" pom.xml)" = "3" ] &amp;&amp; [ "$(grep -cE "<forkCount>" pom.xml)" = "0" ] &amp;&amp; [ "$(grep -cE "<reuseForks>" pom.xml)" = "0" ]</automated>
  </verify>
  <acceptance_criteria>
    - Wave 1 GREEN verdict is present in 79-INDEPENDENCE-AUDIT.md
    - pom.xml has exactly 3 `@{argLine}` occurrences (Surefire + Failsafe default-it + Failsafe e2e-it)
    - pom.xml has 0 `<forkCount>`, 0 `<reuseForks>`, 0 `<excludedGroups>` (greenfield confirmed)
    - Sanity-check `./mvnw verify -Pe2e` BUILD SUCCESS (recorded as intermediate measurement in 79-AUTO-UAT.md `## Intermediate Measurements`)
  </acceptance_criteria>
  <done>Gate verified; symbol-existence audit GREEN; intermediate measurement recorded.</done>
</task>

<task type="auto">
  <name>Task 2: Apply Surefire + Failsafe parallelization config to pom.xml + commit</name>
  <files>pom.xml</files>
  <read_first>
    - `pom.xml` Surefire block (around lines 260-271) and Failsafe `default-it` execution (around lines 283-300)
    - The two TARGET XML blocks in this plan's `<interfaces>` section (verbatim final state)
    - RESEARCH §"Architecture Patterns 1 + 2" (argLine propagation rationale)
  </read_first>
  <action>
Apply two surgical XML edits to `pom.xml` using the Edit tool (preferring single-string replacement to avoid accidental nearby damage):

**Edit 1 — Surefire block:** Replace the existing Surefire `<configuration>` content (block between `<configuration>` and `</configuration>` for `maven-surefire-plugin`) with the TARGET Surefire block from `<interfaces>`. The new content adds three elements AFTER the existing `<argLine>` element and BEFORE the existing `<excludes>` element: `<forkCount>2C</forkCount>`, `<reuseForks>true</reuseForks>`, `<excludedGroups>flaky</excludedGroups>`. PLUS adds a 2-line technical comment above `<forkCount>` (no Phase-N reference per D-09).

**Edit 2 — Failsafe `default-it` execution:** Replace the existing Failsafe `<execution id="default-it">` `<configuration>` content with the TARGET Failsafe block from `<interfaces>`. The new content adds the same three elements after `<argLine>` and before `<includes>`: `<forkCount>1C</forkCount>`, `<reuseForks>true</reuseForks>`, `<excludedGroups>flaky</excludedGroups>`. PLUS adds a 4-line technical comment above `<forkCount>` explaining the conservative `1C` choice (Testcontainers + Spring context startup rationale; D-09: no Phase-N reference; D-13 Schutzwort-safe).

**DO NOT TOUCH:** the Failsafe `<execution id="e2e-it">` block in `<profile id="e2e">` (around lines 401-415). Playwright single-port constraint — RESEARCH §"Architecture Pattern 1" "Failsafe `e2e-it` execution ... keeps `forkCount` unset (defaults to 1)".

**Post-edit verification before commit:**
```
grep -nE "<forkCount>" pom.xml   # MUST show exactly 2 matches: 2C (Surefire) + 1C (Failsafe default-it)
grep -nE "<reuseForks>true</reuseForks>" pom.xml  # MUST show exactly 2 matches
grep -nE "<excludedGroups>flaky</excludedGroups>" pom.xml  # MUST show exactly 2 matches
grep -c "@{argLine}" pom.xml  # MUST still be 3 (no argLine syntax change)
```

Stage `git add pom.xml`. Verify `git status` shows only `pom.xml` staged. Commit:
```
perf(79): enable Surefire/Failsafe process-level parallelism + flaky-tag quarantine (D-05, D-07)

Surefire (unit tests):
- forkCount=2C, reuseForks=true (process-level parallel)
- excludedGroups=flaky (D-07 quarantine mechanism, max-5 cap per CD-05)

Failsafe default-it (integration tests):
- forkCount=1C, reuseForks=true (Testcontainers-safe; 1 IT JVM per CPU core)
- excludedGroups=flaky

Failsafe e2e-it (Playwright): UNCHANGED (single-port constraint).

argLine `@{argLine}` late-evaluation preserved on all 3 entries (JaCoCo + Mockito-agent + JEP 498 invariants).

Wave 1 (Plan 01) independence audit GREEN — parallelization is unblocked.
Wave 2 (Plans 02a-02h) cleanup committed — pom.xml is greenfield (no fork config).
```

Run `./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true`. If BUILD SUCCESS → record the wallclock duration as `## Intermediate Measurements` row (with-parallel) in `79-AUTO-UAT.md` AND amend the commit body if needed (or append the duration as a follow-up note). If BUILD FAILURE OR JaCoCo &lt; 0.82 → `git revert HEAD --no-edit`, then `NEEDS_CONTEXT` with the failure mode (test failure, JaCoCo gate, OOM, port collision).
  </action>
  <verify>
    <automated>./mvnw verify -Pe2e -Dspring.profiles.active=dev -Ddocker.available=true -q 2>&amp;1 | tail -10 | grep -q "BUILD SUCCESS" &amp;&amp; [ "$(grep -cE "<forkCount>" pom.xml)" = "2" ] &amp;&amp; [ "$(grep -cE "<excludedGroups>flaky</excludedGroups>" pom.xml)" = "2" ] &amp;&amp; [ "$(grep -c "@{argLine}" pom.xml)" = "3" ] &amp;&amp; git log -1 --pretty=%B | grep -q "perf(79): enable Surefire/Failsafe process-level parallelism"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw verify -Pe2e` BUILD SUCCESS with the new fork configuration
    - JaCoCo line coverage ≥ 0.82 (D-18 gate holds)
    - `pom.xml` has exactly 2 `<forkCount>` elements (2C Surefire + 1C Failsafe default-it)
    - `pom.xml` has exactly 2 `<excludedGroups>flaky</excludedGroups>` elements
    - `@{argLine}` count unchanged at 3 (argLine syntax invariant)
    - Failsafe `e2e-it` execution block unchanged (no `<forkCount>` added in `<profile id="e2e">`)
    - Commit `perf(79): enable Surefire/Failsafe process-level parallelism` lands on `gsd/v1.10-platform-and-backup`
    - `79-AUTO-UAT.md` `## Intermediate Measurements` records the with-parallel wallclock duration
  </acceptance_criteria>
  <done>Single commit lands; `./mvnw verify -Pe2e` GREEN under parallel forks; JaCoCo gate holds; argLine invariant preserved.</done>
</task>

</tasks>

<verification>
- 1 atomic commit `perf(79): enable Surefire/Failsafe process-level parallelism` on `gsd/v1.10-platform-and-backup`
- `./mvnw verify -Pe2e` BUILD SUCCESS at HEAD with `forkCount=2C` (Surefire) + `forkCount=1C` (Failsafe default-it)
- `<forkCount>` element count = 2 (Surefire + Failsafe default-it only)
- `<excludedGroups>flaky</excludedGroups>` count = 2 (Surefire + Failsafe default-it only)
- Failsafe `e2e-it` execution block unchanged
- `@{argLine}` syntax preserved on all 3 entries
- JaCoCo line coverage ≥ 0.82
</verification>

<success_criteria>
- 1 atomic commit `perf(79): ...` lands
- `./mvnw verify -Pe2e` BUILD SUCCESS
- All 5 invariants from RESEARCH §1+2 hold (argLine syntax, e2e-it untouched, forkCount/reuseForks/excludedGroups added in correct places)
- JaCoCo gate holds
- `79-AUTO-UAT.md` has the intermediate measurement
- Branch unchanged
</success_criteria>

<output>
After completion, create `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-03-SUMMARY.md` per `@$HOME/.claude/get-shit-done/templates/summary.md`. Include: commit SHA, exact line ranges of the two edits, `<forkCount>` + `<reuseForks>` + `<excludedGroups>` count proofs, `@{argLine}` syntax preservation proof, with-parallel wallclock duration vs baseline (Δ %), JaCoCo coverage value at HEAD.
</output>
