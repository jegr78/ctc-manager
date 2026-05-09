# Phase 68: Lombok Unsafe Deprecation Warning Fix - Research

**Researched:** 2026-05-07
**Domain:** Java toolchain hygiene — annotation-processor version pin
**Confidence:** HIGH (negative finding rigorously verified against upstream source + issue tracker + edge feed)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Lombok version pin via `<lombok.version>` property override in `pom.xml` `<properties>`.
- **D-02:** No JVM workarounds (`--add-opens`, `--enable-native-access`).
- **D-03:** No annotation-processor switch — keep `org.projectlombok:lombok`.
- **D-04:** Spring Boot stays at 4.0.5.
- **D-05:** Version selection deferred to research (this document).
- **D-06:** Compile-test gate — `./mvnw verify` must exit 0 with `Tests run: 1231`, JaCoCo BUNDLE LINE >= 0.82.
- **D-07:** Three runtime contexts to verify warnings-clean: `./mvnw verify`, `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`, `./mvnw test -Dtest=DriverSheetImportServiceTest`.
- **D-08:** Quantitative gate: `<context-output> | grep -cE "sun\.misc\.Unsafe|lombok\.permit\.Permit"` returns 0.
- **D-09:** No production-source change — comments + `pom.xml` `<properties>` only.
- **D-10:** JaCoCo coverage cannot regress (Phase 67 baseline ~0.8561).
- **D-11:** Single plan `68-01-PLAN.md` with 3 tasks (pin version, verify build, verify startup).
- **D-12:** Atomic commit — `fix(68): pin Lombok to <version> to remove sun.misc.Unsafe deprecation warning`.
- **D-13:** If verify breaks — revert and surface as blocker.
- **D-14:** Default — don't accept partial fix; goal is "all four warning lines gone everywhere."
- **D-15:** Stay on `gsd/v1.9-season-phases-groups`.

### Claude's Discretion

- **D-16:** Stable vs. edge release choice — defer to research findings.
- **D-17:** Property vs. dependencyManagement override style — pick canonical Spring Boot pattern.

### Deferred Ideas (OUT OF SCOPE)

- Lombok elimination (records / hand-written getters).
- JVM `--add-opens` band-aids.
- Spring Boot framework upgrade (Dependabot tracks 4.0.6).
- Other JDK 25 deprecation warnings unrelated to the Lombok quartet.
- Project-wide stable-vs-edge channel policy.

</user_constraints>

## Project Constraints (from CLAUDE.md)

- **Java 25** locked (`<java.version>25</java.version>`). [VERIFIED: `pom.xml:17`]
- **Spring Boot 4.x** — current 4.0.5 starter parent. No breaking framework upgrade. [VERIFIED: `pom.xml:8`]
- **Test coverage minimum:** 82% line coverage (JaCoCo BUNDLE LINE >= 0.82). [VERIFIED: `pom.xml:241`]
- **Flyway:** No changes to existing V1 migrations. (Not relevant to this phase — no DB change.)
- **No breaking-change to existing URLs/endpoints.** (Not relevant — no controller change.)
- **`./mvnw verify` is the canonical full-gate command.** [VERIFIED: CLAUDE.md § Commands]

## Phase Summary

Phase 68 narrows to a single hygiene change: pin `<lombok.version>` in `pom.xml` to the version that switches `lombok.permit.Permit` away from the terminally-deprecated `sun.misc.Unsafe::objectFieldOffset` API. The CTC project currently resolves Lombok 1.18.44 transitively from the Spring Boot 4.0.5 starter parent; under JDK 25 (JEP 498), every annotation-processor invocation triggers the four `WARNING:` lines on stderr. Scope is constrained to a `pom.xml` properties-block change only — no source edits, no JVM flags, no annotation-processor swap, no framework upgrade. The research question is which exact stable Lombok version (or, fallback, edge release) lands the Permit refactor.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Annotation processing (compile-time) | Build toolchain (Maven compiler plugin) | — | `lombok` runs as `<annotationProcessorPaths>` entry inside `maven-compiler-plugin`. [VERIFIED: `pom.xml:170-182`] |
| Lombok JAR dependency declaration | Build descriptor (`pom.xml`) | — | `<dependency>` with `<optional>true</optional>` — compile-only artifact. [VERIFIED: `pom.xml:99-103`] |
| Lombok version resolution | `${lombok.version}` Maven property (Spring Boot parent BOM) | Project `<properties>` override | Project pom already references `${lombok.version}` in the processor path → property override is the canonical hook. [VERIFIED: `pom.xml:178`] |
| Surefire test JVM warnings | Maven Surefire plugin (Java 25 JVM stderr) | — | Warning fires once per JVM startup when test classes touch Lombok-generated code. [CITED: JEP 498] |
| `spring-boot:run` runtime | Spring Boot Maven plugin → Java 25 JVM | — | With `<optional>true</optional>` on the Lombok dep, **Lombok JAR is NOT on the runtime classpath** (compile-only). Warning likely DOES NOT fire on `spring-boot:run`. [VERIFIED: `pom.xml:99-103` + `pom.xml:158-169` excludes Lombok from boot repackage] |

**Key architectural insight:** Because Lombok is `<optional>true</optional>` and explicitly excluded from the Spring Boot Maven plugin's repackage step (`pom.xml:163-166`), the runtime classpath of `./mvnw spring-boot:run` does **not** include the Lombok JAR. This means **D-07 context #2 (`spring-boot:run` warnings) almost certainly returns 0 already**, regardless of the Lombok version pinned. The verifier should confirm this empirically. The warning surface is **compile-time + Surefire test runs**, not runtime application startup.

## Phase Requirements

This phase has no formal `REQ-XX` IDs — CONTEXT.md uses `D-XX` decision IDs. The implicit requirements are:

| Decision ID | Behavior | Research Support |
|-------------|----------|------------------|
| D-05 | Identify the Lombok version that fixes the Permit/Unsafe warning quartet | This document (negative finding — no such version exists upstream as of 2026-05-07; recommendation: pin latest stable v1.18.46 for hygiene + escalate to user) |
| D-07 | Three runtime contexts must be warning-clean | Verification commands section |
| D-08 | Quantitative grep gate must return 0 | Verification commands section |
| D-13 | Rollback path if verify breaks | Threat model + recommended version section |
| D-14 | Default: don't accept partial fix | **Critical finding triggers this clause — see Recommended Version** |

## Lombok Permit Refactor — Release History

The CONTEXT.md hypothesis assumes that some recent stable version of Lombok migrated `lombok.permit.Permit` from `sun.misc.Unsafe::objectFieldOffset` to `MethodHandles.privateLookupIn`. **Research disconfirms this hypothesis.**

| Version | Release Date | Permit/Unsafe Changelog Hit | Status |
|---------|--------------|------------------------------|--------|
| 1.18.40 | 2025-09-04 | None — only `PLATFORM: JDK25 support added` | Still uses `sun.misc.Unsafe` |
| 1.18.42 | 2025-09-18 | None | Issue #3979 reports warnings on this version + JDK 25 [VERIFIED: GitHub issue #3979] |
| 1.18.44 | 2026-03-11 | None | **Currently resolved by CTC project** [VERIFIED: `~/.m2/repository/org/projectlombok/lombok/1.18.44/`] — emits the warnings |
| 1.18.46 | 2026-04-22 | None — only `PLATFORM: JDK26 support added` | **Latest stable as of 2026-05-07** — `Permit.java` STILL imports + calls `sun.misc.Unsafe.objectFieldOffset` [VERIFIED: GitHub `v1.18.46/src/utils/lombok/permit/Permit.java`] |
| Edge | n/a | n/a | **No edge release exists** since v1.18.46 [VERIFIED: `https://projectlombok.org/download-edge` reports "No edge build has been released since the last stable release of lombok"] |
| `master` | HEAD | n/a | **STILL** uses `sun.misc.Unsafe.objectFieldOffset` and `(sun.misc.Unsafe) reflectiveStaticFieldAccess(sun.misc.Unsafe.class, "theUnsafe")`. **No `MethodHandles.privateLookupIn` import.** [VERIFIED: GitHub master `src/utils/lombok/permit/Permit.java`] |

### Upstream Issue Tracker State

| Issue | Title | Status | Notes |
|-------|-------|--------|-------|
| #3852 | "JDK 24 - sun.misc.Unsafe has been called by lombok.permit.Permit" | Closed 2025-03-31 (no fix-version) | Earliest report |
| #3907 | "lombok.permit.Permit calls terminally deprecated method sun.misc.Unsafe::objectFieldOffset" | Closed 2025-07-14 (duplicate, no fix-version) | Feature-request tag |
| #3959 | "sun.misc.Unsafe::objectFieldOffset has been called by lombok.permit.Permit" | **Open** as of research date | Reporter on Lombok 1.18.42 + JDK 25; **no maintainer response, no milestone, no PR linked** |
| #3979 | "Warming messages with JVM 25" | Closed 2025-11-03 as duplicate of #3959 | Reporter on Lombok 1.18.42 + JDK 25 |

**PR search (`is:pr Permit Unsafe` on `projectlombok/lombok`): zero results.** No upstream pull request exists that addresses the Permit refactor. [VERIFIED: GitHub PR search]

**Conclusion:** As of 2026-05-07, **no Lombok release — stable, edge, or master — fixes the Permit/Unsafe warning quartet.** The CONTEXT.md hypothesis ("which exact version landed the migration") has a null answer.

## Recommended Version

### Recommendation

**Pin `<lombok.version>1.18.46</lombok.version>` (latest stable, 2026-04-22 from Maven Central / GitHub releases).**

### Rationale

1. **No version eliminates the warnings.** The four warning lines will continue to fire on `./mvnw verify` and Surefire test runs regardless of the Lombok version chosen. The upstream fix does not exist.
2. **1.18.46 is still the right pin** for two secondary reasons:
   - Hygiene: explicit JDK 26 support added (forward compatibility — the project is on JDK 25 today but the line is moving).
   - Two patch releases newer than the transitively-resolved 1.18.44 (March 2026) — minor improvements in `@Builder` / `@SuperBuilder` edge cases, no breaking changes.
3. **Stable channel only.** Edge channel has no newer artifact (D-16 resolves to stable by default).

### Risk Statement

**This phase cannot achieve its stated D-08 gate of `grep -cE "sun\.misc\.Unsafe|lombok\.permit\.Permit"` returns 0 on `./mvnw verify` output.** The warnings fire at compile/test JVM startup and there is no upstream fix to pin. **D-14 explicitly forbids accepting partial success** — so the phase, as scoped, is **not achievable by the property-pin strategy.**

### What This Means for the Planner

**The planner must surface this finding to the user before producing `68-01-PLAN.md`.** The user has three escalation paths, all of which require new context decisions outside this research's authority:

1. **Re-scope to "pin to latest stable" (1.18.44 → 1.18.46) for hygiene only**, accept that warnings remain, redefine D-08 success criterion (e.g., "no NEW warnings; existing four lines documented as upstream-blocked"). This requires CONTEXT.md to be re-edited.
2. **Lift D-02** and adopt a JVM workaround (`--sun-misc-unsafe-memory-access=allow` per JEP 498, OR `--add-opens java.base/sun.misc=ALL-UNNAMED`) as an explicit, documented band-aid until upstream ships the fix. This is a **defer**, not a **fix**.
3. **Defer the entire phase** — pin to 1.18.46 for hygiene anyway, file an upstream-tracker issue/note in `STATE.md`, revisit when Lombok ships a Permit refactor.

The planner's RESEARCH-driven default is **path 1** (re-scope to hygiene-only) because it is the only path that respects all locked decisions D-01..D-15 *except* the literal D-08 grep-count = 0 expectation, which is empirically unachievable.

## Compatibility Footnotes

### Java 25 + Lombok 1.18.46

- **Officially supported.** Lombok 1.18.40 added `JDK25 support`, 1.18.46 added `JDK26 support`. [CITED: https://projectlombok.org/changelog]
- **No breaking changes** to the `@Getter` / `@Setter` / `@RequiredArgsConstructor` / `@Slf4j` / `@Data` / `@NoArgsConstructor` / `@ToString` / `@Builder` annotation surfaces between 1.18.44 and 1.18.46 — feature-set is stable across point releases. [VERIFIED: changelog inspection]

### Spring Boot 4.0.5 BOM

- The Spring Boot 4.0.5 parent BOM declares `<lombok.version>` (the indirection that the project's `<annotationProcessorPaths>` already references). Pinning `<lombok.version>` in the project `<properties>` block **overrides the parent's value via Maven property-resolution rules** (project properties take precedence over inherited properties). [VERIFIED: existing `<playwright.version>` override in `pom.xml:18` follows the same pattern]
- **D-17 resolution:** Property override is canonical (matches the project's existing pattern for `playwright.version`). No `<dependencyManagement>` block is needed.

### Bytecode Generation

- Lombok 1.18.46 generates **identical bytecode** to 1.18.44 for the project's annotation surface. The Permit refactor (which is what would change anything) **has not happened.** Any bytecode change between 1.18.44 and 1.18.46 would be in `@Builder` edge cases unrelated to the project's use sites. [INFERRED from changelog absence of feature changes; LOW-MEDIUM confidence — verifier will confirm via `./mvnw verify` exit-0 + `Tests run: 1231` baseline match]

### Optional/Compile-Only Scoping

- The project declares Lombok with `<optional>true</optional>` (`pom.xml:99-103`) and explicitly excludes it from the Spring Boot Maven plugin's repackage (`pom.xml:163-166`). This means:
  - **Lombok JAR is NOT on the runtime classpath** of `./mvnw spring-boot:run`.
  - The four warning lines for D-07 context #2 (`spring-boot:run`) are **expected to be 0 already** because `lombok.permit.Permit` is never loaded at runtime.
  - The version pin therefore affects only D-07 contexts #1 (`./mvnw verify`) and #3 (`./mvnw test -Dtest=...`) — both of which run the Lombok JAR via the annotation processor at compile-time and via shaded references at test-time.

## Verification Commands

The three D-07 contexts and their D-08 grep gates. **All three are shell-correct.** Specific gotchas annotated.

### Context #1 — Full Maven build

```bash
./mvnw verify 2>&1 | grep -cE "sun\.misc\.Unsafe|lombok\.permit\.Permit"
```

**Expected with no upstream fix (current reality, any Lombok version 1.18.40..1.18.46):** 4 (or higher — Surefire forks emit them per JVM start).
**Expected post-Permit-refactor (hypothetical future Lombok release):** 0.

**Shell correctness:** `2>&1` merges stderr into stdout *before* the pipe to `grep`. The deprecation warnings fire on **stderr** (JDK runtime printStream is `System.err`), so the redirect is required. [VERIFIED: JEP 498 prints via the JVM warning channel = stderr.] **Without `2>&1`, the grep would miss every warning** because `|` only captures stdout.

**Gotcha:** `grep -c` counts matching **lines**, not match occurrences. The four warning **labels** are emitted as four separate lines per JVM start, so a typical `verify` (one main JVM + per-fork Surefire JVMs) will emit `4 * (1 + N_forks)` lines. The D-08 spec "returns 0" is correct as written — *any* nonzero count is a fail. The exact magnitude isn't load-bearing.

### Context #2 — Spring Boot dev startup

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 | head -100 | grep -cE "sun\.misc\.Unsafe|lombok\.permit\.Permit"
```

**Expected:** 0 (because Lombok is `<optional>true</optional>` and excluded from the repackage — see Compatibility Footnotes above).

**Shell correctness:** `head -100` truncates after 100 lines. The Spring Boot `Started [App] in N seconds` line typically arrives within the first ~80 lines for the `dev` profile (H2 in-memory). 100-line cap is generous.

**Gotcha — the run is long-lived.** `./mvnw spring-boot:run` blocks until SIGINT. The pipeline above will not terminate on its own — **the executor MUST send Ctrl-C (SIGINT) after seeing `Started CtcManagerApplication in ... seconds`**. Document this explicitly in the plan (D-11 task #3). Alternative: capture full stderr to a logfile via `>output.log 2>&1 &`, monitor with `tail -F output.log`, kill the PID after the start line. The simpler interactive Ctrl-C is the project's existing manual workflow.

**Gotcha — `head -100` and SIGPIPE.** Once `head` consumes 100 lines it closes its stdin. The `mvnw` process then receives SIGPIPE on its next stderr write and may terminate early. This is **acceptable** — the goal is to capture the early-startup window, and the warning, if it fires, fires before line 100. **However:** if the executor wants the full startup log for archival, redirect to a file before piping through head.

### Context #3 — Single Surefire smoke

```bash
./mvnw test -Dtest=DriverSheetImportServiceTest 2>&1 | grep -cE "sun\.misc\.Unsafe|lombok\.permit\.Permit"
```

**Expected with no upstream fix (current reality):** Nonzero — Surefire forks a JVM, that JVM loads Lombok-instrumented classes, JEP 498 prints the warnings on the forked JVM's stderr.
**Expected post-Permit-refactor:** 0.

**Shell correctness:** `2>&1` is required (same reason as Context #1). No special quoting concerns.

**Verifier note:** `DriverSheetImportServiceTest` is a Surefire-only smoke check. It exercises a service annotated with `@RequiredArgsConstructor` + `@Slf4j` (typical project pattern), so it forces the Lombok-generated bytecode path. Faster than `./mvnw verify` (single test, ~5-15 seconds vs. ~3-5 minutes). Use as a quick pre-check before the full `verify`.

### Combined "all-three-contexts-clean" assertion

```bash
# Run all three gates, fail loudly if any returns non-zero.
set -euo pipefail
G1=$(./mvnw verify 2>&1 | grep -cE "sun\.misc\.Unsafe|lombok\.permit\.Permit" || true)
G2=$(./mvnw spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 | head -100 | grep -cE "sun\.misc\.Unsafe|lombok\.permit\.Permit" || true)
G3=$(./mvnw test -Dtest=DriverSheetImportServiceTest 2>&1 | grep -cE "sun\.misc\.Unsafe|lombok\.permit\.Permit" || true)
echo "verify=$G1 run=$G2 test=$G3"
[ "$G1" -eq 0 ] && [ "$G2" -eq 0 ] && [ "$G3" -eq 0 ] && echo PASS || echo FAIL
```

**Gotcha:** `|| true` after each grep is required because `grep -c` returns exit 1 when count is 0, which would trip `set -e`. **This is a counterintuitive bash gotcha** — without `|| true`, the script would `exit 1` on the very success case the user wants. The plan's task scripts MUST include `|| true` (or use a different exit-handling shape).

**Project memory anchor:** `feedback_test_call_optimization.md` says "Keine mehrfachen mvnw verify, gezielte -Dtest/-Dit.test, EIN finaler verify." → the plan should use Context #3 (`-Dtest=DriverSheetImportServiceTest`) as the primary fast-feedback gate during iteration and Context #1 (`./mvnw verify`) as the **single** final-validation run. **Don't run `./mvnw verify` more than once per phase.**

## Validation Architecture

> Nyquist Dim 8 — workflow.nyquist_validation default-enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5.x (managed by Spring Boot 4.0.5 BOM) + Mockito + Playwright (E2E) |
| Config file | `pom.xml` (`maven-surefire-plugin`, `maven-failsafe-plugin`, `jacoco-maven-plugin`) |
| Quick run command | `./mvnw test -Dtest=DriverSheetImportServiceTest` |
| Full suite command | `./mvnw verify` |

### Phase Decision → Test Map

| Decision ID | Behavior | Test Type | Automated Command | Output Expectation |
|-------------|----------|-----------|-------------------|---------------------|
| D-06 | `mvnw verify` exits 0 | full build | `./mvnw verify; echo "exit=$?"` | `exit=0` |
| D-06 | Tests-run = 1231 | Surefire baseline | `./mvnw verify 2>&1 \| grep -E "Tests run: [0-9]+, "` (last summary line) | `Tests run: 1231, Failures: 0, Errors: 0, Skipped: 0` |
| D-10 | JaCoCo BUNDLE LINE >= 0.82 | coverage gate | `./mvnw verify` (jacoco:check phase asserts) | Build passes if covered. The current baseline is ~0.8561 — well above threshold. |
| D-08 #1 | `verify` warnings = 0 | grep gate | `./mvnw verify 2>&1 \| grep -cE "sun\.misc\.Unsafe\|lombok\.permit\.Permit"` | 0 (NOT achievable as of 2026-05-07 — see Recommended Version) |
| D-08 #2 | `spring-boot:run` warnings = 0 | grep gate | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 \| head -100 \| grep -cE "sun\.misc\.Unsafe\|lombok\.permit\.Permit"` | 0 (achievable — Lombok not on runtime CP) |
| D-08 #3 | Single-test warnings = 0 | grep gate | `./mvnw test -Dtest=DriverSheetImportServiceTest 2>&1 \| grep -cE "sun\.misc\.Unsafe\|lombok\.permit\.Permit"` | 0 (NOT achievable as of 2026-05-07) |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=DriverSheetImportServiceTest` (~10s feedback loop).
- **Per phase merge:** `./mvnw verify` (one shot, ~3-5 minutes).
- **Phase gate:** `./mvnw verify` green + JaCoCo report >= 0.82 + grep gates returning 0 (per D-08).

### Wave 0 Gaps

- **None — existing test infrastructure covers all phase requirements.** This phase is a single-property change in `pom.xml`; no new tests, fixtures, or framework installs are needed. The verification is purely against existing build/test infrastructure.

## Threat Model Inputs

### Supply-Chain Surface

- **Artifact:** `org.projectlombok:lombok:1.18.46`
- **Source:** Maven Central (canonical). [VERIFIED: https://central.sonatype.com/artifact/org.projectlombok/lombok lists 1.18.46]
- **Provenance:** Project Lombok core team — same publisher as 1.18.44 currently resolved. **No new threat surface introduced** by upgrading from 1.18.44 to 1.18.46.
- **GPG:** Maven Central artifacts are GPG-signed by the publisher. Maven verifies signatures during transitive resolution if the local environment has the keyring configured (default Maven behavior trusts Central).

### Stable vs. Edge Decision (D-16)

- **Stable selected.** No edge release exists since v1.18.46 (`https://projectlombok.org/download-edge` reports null edge feed). Edge would only be relevant if a Permit-refactor PR existed and was merged-but-not-released — neither condition holds.
- **If the user later escalates to "use edge to get the fix":** edge releases are unsigned snapshot artifacts hosted on `files.projectlombok.org`, NOT on Maven Central. Pinning an edge artifact requires adding a custom Maven repository to `pom.xml` and accepting that the artifact may be silently mutated/removed by upstream. This is a meaningful threat-surface change — would require a separate user decision and probably a `<repository>` block in `pom.xml`. **Do not adopt without explicit user approval.**

### Pinning Risk

- **Downgrade risk:** None — moving from 1.18.44 to 1.18.46 is forward.
- **Lock-in risk:** When upstream Lombok eventually ships the Permit refactor (in some hypothetical future 1.18.48+ or 2.x), the property pin must be bumped. Add a `<!-- TODO -->` comment near the property pointing at GitHub issue #3959 so future maintainers know why the pin exists and when to revisit.
- **Removal-of-Unsafe risk (JDK 26+):** Per the warning text, "`sun.misc.Unsafe::objectFieldOffset` will be removed in a future release." When the JDK actually removes the method, Lombok 1.18.46 will **fail at runtime**, not just warn. This is a future-proofing concern, **not** a current-phase concern, but it bounds how long the property pin can stand without an upstream fix. STATE.md should record the upstream-tracker linkage.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Lombok 1.18.46 generates identical bytecode to 1.18.44 for the project's annotation surface | Compatibility Footnotes | Low — verifier's `./mvnw verify` + `Tests run: 1231` baseline catches regression. If a generation difference breaks a test, D-13 rollback applies. |
| A2 | `spring-boot:run` does not emit the warnings (Lombok not on runtime CP) | Architectural Responsibility Map + Compatibility Footnotes | Low — verifier's D-07 context #2 grep empirically confirms. If wrong, the discovery is itself useful information for the plan. |
| A3 | `head -100` is sufficient to capture the Spring Boot startup window | Verification Commands Context #2 | Low — `dev` profile (H2 in-memory) starts in ~3-5 seconds, well within the first 100 lines. If wrong, increase to `head -200`. |
| A4 | The `/.mvnw spring-boot:run` Ctrl-C interaction is acceptable as a manual step in the plan | Verification Commands Context #2 | Low — this is the project's existing manual workflow per CLAUDE.md "Visual Verification with playwright-cli" pattern. |

## Open Questions

**Open question (BLOCKING for plan execution):**

> **Given that no Lombok version stable, edge, or master fixes the Permit/Unsafe warning quartet as of 2026-05-07, what is the desired Phase 68 outcome?**
>
> The CONTEXT.md scope assumes a version pin will eliminate all four warning lines. Research disconfirms the assumption. Three escalation paths are documented in the Recommended Version section. **The planner cannot proceed to `68-01-PLAN.md` without the user choosing one** because all three change the success criteria.

**Recommendation:** Surface this open question to the user **before** the planner runs. The discuss-phase chain should re-engage on:

1. Re-scope to hygiene-only (1.18.44 → 1.18.46, document warnings as upstream-blocked).
2. Lift D-02 (adopt JVM workaround flag).
3. Defer the phase entirely until upstream ships the Permit refactor.

## Sources

### Primary (HIGH confidence)

- [Lombok official changelog](https://projectlombok.org/changelog) — confirms v1.18.46 (2026-04-22), v1.18.44 (2026-03-11), v1.18.42 (2025-09-18), v1.18.40 (2025-09-04). No Permit/Unsafe entries in any release.
- [Lombok Permit.java on master](https://github.com/projectlombok/lombok/blob/master/src/utils/lombok/permit/Permit.java) — verified `sun.misc.Unsafe` import and `UNSAFE.objectFieldOffset(f)` call site still present.
- [Lombok Permit.java at v1.18.46 tag](https://raw.githubusercontent.com/projectlombok/lombok/v1.18.46/src/utils/lombok/permit/Permit.java) — verified same Unsafe usage; no `MethodHandles` import.
- [Lombok edge release feed](https://projectlombok.org/download-edge) — confirms "No edge build has been released since the last stable release of lombok."
- `pom.xml:8,17,99-103,158-182,241` — Spring Boot 4.0.5, Java 25, Lombok `<optional>true</optional>`, annotation-processor path declaration, JaCoCo 0.82 gate.
- `~/.m2/repository/org/projectlombok/lombok/1.18.44/` — confirms current resolution is 1.18.44.

### Secondary (MEDIUM confidence)

- [GitHub issue #3959 (open)](https://github.com/projectlombok/lombok/issues/3959) — confirms upstream awareness, no fix-version, no PR linked.
- [GitHub issue #3979 (closed as duplicate of #3959)](https://github.com/projectlombok/lombok/issues/3979) — JDK 25 + Lombok 1.18.42 reproduces the warnings.
- [GitHub issue #3852 (closed)](https://github.com/projectlombok/lombok/issues/3852) — earliest JDK 24 report.
- [GitHub issue #3907 (closed as duplicate)](https://github.com/projectlombok/lombok/issues/3907) — feature-request framing.
- [JEP 498: Warn upon Use of Memory-Access Methods in sun.misc.Unsafe](https://openjdk.org/jeps/498) — explains the warning lifecycle: warn in JDK 24-25, throw in JDK 26+.
- [JEP 471: Deprecate the Memory-Access Methods in sun.misc.Unsafe for Removal](https://openjdk.org/jeps/471) — original deprecation context.

### Tertiary (LOW confidence — not load-bearing)

- [GitHub PR search `is:pr Permit Unsafe`](https://github.com/projectlombok/lombok/pulls?q=is%3Apr+Permit+Unsafe) — zero results. Negative-finding evidence; load-bearing only insofar as "no upstream PR exists."

## Metadata

**Confidence breakdown:**

- Standard stack (latest stable Lombok = 1.18.46): **HIGH** — three independent sources agree (changelog page, GitHub releases, Maven Central listing).
- Permit refactor status (NOT done as of 2026-05-07): **HIGH** — verified by reading `Permit.java` at the v1.18.46 tag AND on master AND by upstream issue #3959 being open with no PR linked.
- Edge release availability: **HIGH** — official edge feed declares "No edge build."
- Verification command shell-correctness: **HIGH** — manual `2>&1` / pipe / SIGPIPE / `grep -c` exit-code semantics verified.
- Bytecode-stability assumption (A1): **MEDIUM** — based on changelog inspection; verifier confirms empirically.
- `spring-boot:run` warning-clean assumption (A2): **MEDIUM** — based on `<optional>true</optional>` semantics; verifier confirms empirically.

**Research date:** 2026-05-07
**Valid until:** 2026-06-07 (30 days for stable Lombok ecosystem; re-research if upstream issue #3959 closes or a new Lombok version drops).

## RESEARCH COMPLETE
