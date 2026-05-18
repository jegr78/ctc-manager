# Pitfalls Research

**Domain:** v1.11 Tooling Infrastructure & Tech-Debt Sweep — OpenRewrite, Checkstyle/PMD/SpotBugs, Renovate, SAST (CodeQL/Semgrep), Backup cleanup, and test-wallclock reduction on a Lombok-heavy Spring Boot 4 / Java 25 Maven codebase
**Researched:** 2026-05-16
**Confidence:** HIGH for Lombok/static-analysis interactions (well-documented, cross-checked official docs + SpotBugs/Checkstyle issue trackers), HIGH for Renovate BOM pitfalls (documented Renovate issue), HIGH for CodeQL Java 25 support (GitHub Changelog confirmed), MEDIUM for OpenRewrite Spring Boot 4 recipe maturity (fast-moving ecosystem), MEDIUM for test-wallclock reduction (architecture-specific)

---

## Critical Pitfalls

### Pitfall 1: OpenRewrite `UpgradeSpringBoot_4_0` recipe runs against a codebase already on Spring Boot 4

**What goes wrong:**
The composite `UpgradeSpringBoot_4_0` (Community Edition) recipe includes sub-recipes that try to do the migration TO Boot 4 (namespace rewriting, property renaming, modular-starters migration). Running it against a codebase already on Boot 4.0.6 does not fail cleanly — some sub-recipes apply no-ops silently, but others (like `MigrateToModularStarters`) may detect the existing modular-starter `spring-boot-starter-webmvc` and attempt to re-add it, or detect a `spring-boot-starter-web` parent that no longer exists and emit a confusing diff. The result is a patch that looks superficially correct but introduces redundant dependencies or removes the wrong starter.

**Why it happens:**
OpenRewrite recipes inspect current pom.xml state and assume they are in the migration path. The Boot 4 composite recipe does not check `<parent><version>` against "already 4.0.x" before applying every sub-step. The `MigrateToModularStarters` recipe in particular replaces `spring-boot-starter-web` with per-feature starters — CTC Manager already uses the modular starters (`spring-boot-starter-webmvc`, `spring-boot-starter-thymeleaf`, etc.), so the recipe becomes a no-op or a conflict.

**How to avoid:**
- Do NOT apply `UpgradeSpringBoot_4_0` — it is for migrating FROM Boot 3 to Boot 4. The correct recipe set for CTC Manager is the maintenance/housekeeping category: `org.openrewrite.java.migrate.UpgradeToJava25` (idempotent, applies only if not already on Java 25 patterns), and targeted style/cleanup recipes.
- Start with `mvn rewrite:dryRun` before any `rewrite:run`. Inspect `target/site/rewrite/rewrite.patch` for surprises.
- Pin a specific recipe set in `pom.xml` `<activeRecipes>` (not the compound migration recipe).
- Phase 80 (OpenRewrite setup) must define an explicit allow-list of recipe IDs and a dryRun-only CI gate with `<failOnDryRunResults>true</failOnDryRunResults>`.

**Warning signs:**
- Diff shows `spring-boot-starter-web` being added back to pom.xml when it was intentionally removed.
- Recipe removes `spring-boot-starter-webmvc` because it looks like a migration artifact from `spring-boot-starter-web`.
- `rewrite:dryRun` produces a large patch file for pom.xml alone.

**Phase to address:** Phase 80 (OpenRewrite integration) — must run `dryRun` and inspect patch before ever running `rewrite:run`.

---

### Pitfall 2: OpenRewrite `LombokValToFinalVar` recipe rewrites test code in ways that conflict with Lombok `val` usage patterns

**What goes wrong:**
The `LombokValToFinalVar` recipe converts all Lombok `val x = expr` to Java `final var x = expr`. CTC Manager uses `var` (not `val`) in production code already, but if any older test files use `lombok.val`, the recipe rewrites them. This is safe on its own, but if `rewrite:run` is part of a CI gate (`failOnDryRunResults=true`) and a developer adds a new Lombok `val` in a test, the build fails with a cryptic "recipe would make changes" message rather than "use final var".

**Why it happens:**
Developers adding test code may instinctively use `val` from muscle memory or IDE templates. The OpenRewrite CI gate surfaces this as a recipe-would-change-files failure, not as a compile error or test failure.

**How to avoid:**
- Add a Checkstyle rule or PMD rule banning `import lombok.val` — provides a comprehensible build error instead.
- Alternatively, accept `val` and exclude `LombokValToFinalVar` from the active recipe set, since CTC Manager code already uses Java `var` everywhere in production.
- Phase 80 plan must choose ONE enforcement point for this, not both OpenRewrite and Checkstyle.

**Warning signs:**
- CI fails on the `rewrite:dryRun` gate with "would change `LombokValToFinalVar`" in test files.
- Developers confused by build failure that looks like a pom.xml problem but is actually a test file issue.

**Phase to address:** Phase 80 — decide upfront whether to include or exclude `LombokValToFinalVar` in the recipe set.

---

### Pitfall 3: OpenRewrite annotation processing — recipes parse source annotations, not bytecode, and miss Lombok-generated structure

**What goes wrong:**
OpenRewrite operates on source files using its own LST (Lossless Semantic Trees). It reads `@Getter`, `@Setter`, `@RequiredArgsConstructor` annotations on classes but does NOT see the generated getters, setters, or constructors as actual source nodes. This means:
- Recipes that look for "unused fields" may flag Lombok-annotated `private final` fields injected by `@RequiredArgsConstructor` as "unreferenced" (false positive deletion risk with aggressive cleanup recipes).
- Recipes that look for "add missing builder pattern" may try to add an explicit constructor when `@RequiredArgsConstructor` already covers it.
- `RemoveSuperfluous...` recipes may remove `@NoArgsConstructor` from entity classes that Hibernate requires.

**Why it happens:**
OpenRewrite's Java model does not run the Lombok annotation processor before building its LST. Issue [openrewrite/rewrite#1407](https://github.com/openrewrite/rewrite/issues/1407) documents the "delombok before publishing" request as open/deferred.

**How to avoid:**
- Never run structural refactoring recipes (anything in the `org.openrewrite.java.cleanup.*` suite) on Lombok-annotated classes without reviewing the diff in detail.
- Specifically exclude `@Entity` classes from any "remove unused field" or "add constructor" recipes via `<exclusions>` in the plugin config.
- Run `mvn rewrite:dryRun` and manually verify every class under `org.ctc.domain.model.*` in the patch.
- Phase 80 must add an explicit note in the plan: "review every entity change in the dryRun diff before merging."

**Warning signs:**
- Patch removes `@NoArgsConstructor` from an entity that has no explicit no-arg constructor.
- Patch adds an explicit constructor to a service class already covered by `@RequiredArgsConstructor`.
- After `rewrite:run`, Hibernate startup fails with "No default constructor" for an entity.

**Phase to address:** Phase 80.

---

### Pitfall 4: SpotBugs `EI_EXPOSE_REP` / `EI_EXPOSE_REP2` false positives on every Lombok `@Getter` for `List` fields in JPA entities

**What goes wrong:**
SpotBugs 4.7.0+ detects `EI_EXPOSE_REP` and `EI_EXPOSE_REP2` in Lombok-generated getter bytecode. Every entity that has a `@OneToMany` collection and a Lombok `@Getter` will trigger this warning: the generated `getMatchdays()` method returns a direct reference to the internal `ArrayList`, which SpotBugs flags as exposing internal mutable state. CTC Manager has 24 entities, most with at least one collection — this produces ~40–80 false-positive violations on first run. If the gate is set to `<threshold>Low</threshold>` / `<failOnError>true</failOnError>`, the build breaks immediately.

**Why it happens:**
SpotBugs analyzes bytecode. Lombok's `@Getter` generates `return this.field;` directly — no defensive copy. SpotBugs is technically correct that the internal list is exposed, but for a OSIV-enabled JPA application where Hibernate manages the collection, returning the Hibernate-proxy list is the intended behavior. A defensive copy would break lazy loading.

**How to avoid:**
- Add `lombok.extern.findbugs.addSuppressFBWarnings = true` to `lombok.config` (project root). This makes Lombok add `@SuppressFBWarnings({"EI_EXPOSE_REP","EI_EXPOSE_REP2"})` to every generated getter/setter automatically.
- Additionally create a `spotbugs-exclude.xml` filter that suppresses `EI_EXPOSE_REP*` on all classes under `org.ctc.domain.model.*`.
- Phase 81 (Clean Code gates) must add `lombok.config` BEFORE enabling the SpotBugs `<failOnError>` gate.

**Warning signs:**
- SpotBugs report shows 40+ violations all in entity getter methods.
- All violations are `EI_EXPOSE_REP` or `EI_EXPOSE_REP2`, none of them in handwritten code.
- `mvn spotbugs:check` fails on the first run without any manually written suspicious code.

**Phase to address:** Phase 81 — `lombok.config` must be the first deliverable before SpotBugs gate is enabled.

---

### Pitfall 5: Checkstyle fails on Lombok-generated code that Checkstyle's scanner cannot distinguish from hand-written code

**What goes wrong:**
Checkstyle inspects source files, not bytecode, and sees only the annotations — not the generated methods. However, it DOES see the annotation itself and may flag annotation placement rules (e.g., `@Getter @Setter @NoArgsConstructor` on a single line violates a "one annotation per line" rule). More critically, if Checkstyle's `MagicNumber` rule is enabled, it flags constants in entity field initializers (`new ArrayList<>(0)` — the `0` is a magic number). The `DesignForExtension` rule will flag entity methods like `isSubTeam()` and `getParentOrSelf()` as "non-final non-abstract methods in non-final class" since entities are not `final`.

**Why it happens:**
Standard Checkstyle ruleset templates (Google Java Style, Sun Coding Standards) were written for non-Lombok code. Applying them verbatim to a Lombok-heavy codebase generates dozens of violations on the first run that are either false positives or not actionable without restructuring the entity model.

**How to avoid:**
- Use a custom `checkstyle.xml` that explicitly disables or configures: `DesignForExtension` (or excludes `org.ctc.domain.model.*`), `MagicNumber` (allow initializers in field declarations), and `AnnotationOnSameLine` (Lombok stacks annotations by convention).
- Add `lombok.addLombokGeneratedAnnotation = true` to `lombok.config` so Lombok marks generated elements with `@LombokGenerated`. CheckStyle 10.x respects this and skips generated nodes.
- Start with `<failOnViolation>false</failOnViolation>` (report-only) for the first phase, fix genuine violations, then enable gate.
- Phase 81 plan must baseline the violation count on `mvn checkstyle:checkstyle` BEFORE enabling `checkstyle:check`.

**Warning signs:**
- First `mvn checkstyle:check` produces 200+ violations.
- Most violations are in entity files, not in service or controller files.
- Violation types include `DesignForExtension`, `MagicNumber`, `FinalLocalVariable`, or annotation ordering on `@Entity` classes.

**Phase to address:** Phase 81 — configure `checkstyle.xml` and `lombok.config` together before enabling the gate.

---

### Pitfall 6: PMD rule packs updating and generating 200 new violations that block the build

**What goes wrong:**
When `maven-pmd-plugin` is configured with `<failOnViolation>true</failOnViolation>` and a PMD ruleset, a PMD version bump (triggered by Renovate or manually) can add new rules to existing rulesets. PMD 7.x added dozens of new rules compared to PMD 6.x. If the plugin is pinned to a PMD version via `<dependency>` in the plugin config and Renovate bumps it, the next `mvn verify` fails with 100+ new violations against clean code.

**Why it happens:**
PMD rulesets (`category/java/bestpractices.xml`, `category/java/design.xml`) are additive across versions — new rules land in existing categories. Opting into a whole category means opting into every future rule added to that category.

**How to avoid:**
- Use a local `pmd-ruleset.xml` that explicitly lists individual rule references (`<rule ref="category/java/bestpractices.xml/UnusedLocalVariable"/>`) rather than including entire category files.
- Pin the PMD version explicitly in the `maven-pmd-plugin` `<dependency>` block and upgrade it as a deliberate decision, not via Renovate auto-merge.
- When a PMD upgrade lands, run `mvn pmd:pmd` with `<failOnViolation>false</failOnViolation>` first to audit new violations before re-enabling the gate.
- Phase 81 must create `src/pmd/pmd-ruleset.xml` with explicit rule references.

**Warning signs:**
- CI fails after a Renovate PMD bump PR is merged.
- All new violations appeared in code that has not changed.
- `mvn pmd:pmd` report shows violations only in existing, previously-passing files.

**Phase to address:** Phase 81 — define explicit rule file, not category files. Renovate's PMD bumps must require manual approval.

---

### Pitfall 7: JaCoCo `argLine` is overwritten when SpotBugs or other new plugins add their own `<argLine>` configuration

**What goes wrong:**
CTC Manager's pom.xml already threads JaCoCo through both Surefire and Failsafe via `@{argLine}` late-evaluation. If the SpotBugs Maven plugin, PMD plugin, or Checkstyle plugin is added in the `<build><plugins>` section WITH an `<argLine>` configuration that sets a plain string (not `@{argLine} ...`), Maven will overwrite the property before JaCoCo's `prepare-agent` goal can set it. Result: JaCoCo collects no data, coverage drops to 0%, and the 82% gate fails.

**Why it happens:**
Maven property evaluation is phase-ordered. `@{argLine}` works because JaCoCo sets `argLine` in the `initialize` phase, and `@{...}` forces re-evaluation at test time. But if a new plugin declares `<argLine>-Xmx512m</argLine>` (a static string), it wins and erases JaCoCo's agent injection string.

**How to avoid:**
- None of SpotBugs, PMD, or Checkstyle Maven plugins require an `<argLine>` configuration — they run as source or bytecode scanners, not as JVM agents. Do NOT add `<argLine>` to these plugins.
- After adding new plugins, verify coverage still reports correctly: `./mvnw verify` and check `target/site/jacoco/index.html` shows non-zero numbers.
- If a plugin requires JVM flags (e.g., spotbugs fork mode), use `<jvmArgs>` not `<argLine>`.

**Warning signs:**
- Coverage drops to near 0% after adding a new Maven plugin.
- `target/jacoco.exec` is 0 bytes or missing.
- JaCoCo reports "no class files found" or "no execution data found."

**Phase to address:** Phase 81 — verify JaCoCo coverage after each new plugin is added.

---

### Pitfall 8: Renovate proposes bumping `<guava.version>` to a `33.x-android` variant instead of `33.x-jre`

**What goes wrong:**
Guava ships two artifact classifiers: `33.4.8-jre` and `33.4.8-android`. CTC Manager explicitly pins `33.4.8-jre` to get the VarHandle-based `AbstractFuture` (Java 9+ path) and suppress Unsafe warnings on Java 25. Renovate's default versioning for Guava treats the `-jre`/`-android` suffix as a version qualifier. A Renovate PR may suggest `33.x.y-android` if the android variant has a higher patch version, or it may suggest `34.0.0-jre` without realizing that a `34.0.0` release is not yet stable.

**Why it happens:**
Renovate uses Maven Central version metadata to find newer versions. Guava's unusual version scheme (`x.y.z-jre` vs `x.y.z-android`) can confuse Renovate's semantic versioning comparator. The `-jre` suffix is not a standard Maven qualifier — it is part of the version string, which Renovate may not handle correctly when sorting.

**How to avoid:**
- In `renovate.json`, add a `packageRule` for `com.google.guava:guava` that pins `allowedVersions` to only `-jre` variants: `"/^\\d+\\.\\d+(\\.\\d+)?-jre$/"`.
- Alternatively, add Guava to the `ignoreDependencies` list and manage it manually (it changes rarely).
- Phase 83 (Renovate setup) must include this rule in the initial `renovate.json`.

**Warning signs:**
- Renovate PR proposes `guava` version ending in `-android`.
- Renovate PR proposes a Guava version that removes the JRE/VarHandle path.
- CI build after Renovate merge re-introduces the `AbstractFuture$UnsafeAtomicHelper` Unsafe warning in logs.

**Phase to address:** Phase 83 — `renovate.json` must include Guava classifier constraint before enabling auto-merge.

---

### Pitfall 9: Renovate bumps `<spring-boot.version>` (the parent version) independently from managed dependency properties

**What goes wrong:**
CTC Manager uses `spring-boot-starter-parent` as the POM parent. Spring Boot's parent BOM manages versions for dozens of dependencies. If Renovate bumps the parent version (`4.0.6` → `4.1.0`) in one PR and ALSO creates separate PRs for individual managed dependencies (e.g., `jackson-datatype-jsr310`, `h2`, `mariadb-java-client`), merging the parent PR may silently downgrade those individual dependencies back to the Boot BOM version, undoing the individual bumps. Conversely, if individual bumps land first and then the Boot parent PR is merged, the Boot BOM may declare a newer version that supersedes the individual overrides but with incompatible API changes.

**Why it happens:**
Renovate treats `<parent><version>` and `<dependencyManagement>` and individual `<version>` overrides as separate update opportunities. It does not automatically understand that merging the Boot parent will transitively change all BOM-managed versions. The `config:recommended` preset uses `group:springBoot` to group the parent + matching starters, but this grouping is not always complete, and manual overrides in `<dependencyManagement>` (like the `thymeleaf` pin to `3.1.5.RELEASE`) are NOT included in the group.

**How to avoid:**
- Enable `group:springBoot` in `renovate.json` via the `config:recommended` preset — this groups `spring-boot-*` updates into a single PR.
- Add an explicit `packageRule` for `org.thymeleaf:thymeleaf` (the pinned version) with `enabled: false` or a manual schedule. The Thymeleaf pin to `3.1.5.RELEASE` is a deliberate CVE-2026-40478 mitigation — Renovate must not touch it without human review.
- Add `org.thymeleaf:*` to the manual-approval group.
- Phase 83 must document all manual version overrides in the Renovate PR template as a checklist.

**Warning signs:**
- Renovate creates PRs for `spring-boot-starter-*` individually without grouping.
- Renovate proposes removing the `thymeleaf` pin from `<dependencyManagement>`.
- After merging a Boot parent bump, `mvn dependency:tree` shows Thymeleaf at a version higher than `3.1.5.RELEASE`.

**Phase to address:** Phase 83.

---

### Pitfall 10: Renovate bumps `<java.version>` property and proposes Java 26

**What goes wrong:**
Renovate has a `java-version` datasource that can detect `<java.version>25</java.version>` and propose bumping to Java 26. Java 26 is a non-LTS release (April 2026 GA). CTC Manager's Dockerfile is pinned to `eclipse-temurin:25-{jdk,jre}-noble` and CI runners use Java 25. A Renovate PR bumping `java.version` to 26 without corresponding Dockerfile updates would create a version mismatch between the Maven compiler target and the runtime image, causing silent compatibility issues or Docker build failures.

**Why it happens:**
Renovate's `java-version` datasource defaults to all available versions including non-LTS. Without an `allowedVersions` constraint, it proposes the highest available version.

**How to avoid:**
- Add a `packageRule` in `renovate.json` limiting Java updates to LTS-only: `"allowedVersions": "/^(?:8|11|17|21|25)(?:\\.|-|$)/"`
- Group `java.version` with the Dockerfile `eclipse-temurin:` image version so they move together in a single PR.
- Alternatively, add `java.version` to `ignoreDeps` and manage Java upgrades manually (they are infrequent milestone-level decisions).
- Phase 83 must include this constraint.

**Warning signs:**
- Renovate PR proposes `<java.version>26</java.version>`.
- Renovate PR changes `java.version` without touching `Dockerfile` or `ci.yml`.
- CI `dockerfile-noble-pin-guard` fails after Renovate merge because Docker image still says `25`.

**Phase to address:** Phase 83.

---

### Pitfall 11: Renovate bumps GitHub Actions `uses:` pins including `actions/setup-java` without `java-version` parameter alignment

**What goes wrong:**
The CI workflow uses `actions/setup-java` to set up the JDK. Renovate's `github-actions` manager detects the action version (`v4` → `v4.x`) and proposes version bumps. Separately, if `setup-java` moves from v3 to v4, the `java-version` input format may change (e.g., `java-version: '25'` vs `java-version: '25.0'`). More critically, Renovate may bump `actions/setup-java` at a time when the `java-version` input is parameterized by `${{ matrix.java }}`, which breaks the update heuristic. For CTC Manager's `dockerfile-noble-pin-guard`, a Renovate bump to the `ubuntu-latest` runner or `docker/build-push-action` can change the Docker API version and silently break the guard.

**Why it happens:**
Renovate's `github-actions` manager bumps action SHA or tag versions without understanding the semantic relationship between action version and its input schema changes.

**How to avoid:**
- Pin GitHub Actions to major versions only (`@v4`) and add Renovate `packageRules` for `actions/*` to require manual review.
- Set `"automerge": false` for `github-actions` manager in `renovate.json`.
- Phase 83 should configure Renovate to use separate PR labels for GitHub Actions updates to make them visually distinct and require manual approval.

**Warning signs:**
- CI fails after a Renovate GitHub Actions PR is merged with "Input 'java-version' format" error.
- `dockerfile-noble-pin-guard` CI job fails unexpectedly after a runner bump.
- Docker `build .` job fails with Docker API version mismatch.

**Phase to address:** Phase 83.

---

### Pitfall 12: CodeQL flags intentional SSRF blocklist code as "missing SSRF protection" (false negative/positive inversion)

**What goes wrong:**
CTC Manager has a custom SSRF hostname blocklist in `FileStorageService.storeFromUrl()`. CodeQL's `java/ssrf` query detects `HttpURLConnection` or `OkHttpClient` calls that are not protected by CodeQL-recognized sanitizers. The custom blocklist pattern (a `Set.of(...)` membership check on the resolved hostname) is NOT a CodeQL-recognized sanitizer. Result: CodeQL flags `storeFromUrl()` as SSRF-vulnerable even though it IS protected. Conversely, if CodeQL does not flag it, developers may assume the protection is sufficient — but a new code path that bypasses the blocklist could appear and go undetected.

**Why it happens:**
CodeQL's sanitizer recognition is based on known sanitizer patterns (allowlisting, regex matching on trusted-host lists). A custom `Set.contains(hostname)` blocklist is the inverse pattern (blocklist rather than allowlist) and is not in CodeQL's sanitizer catalog. CodeQL conservatively treats it as no sanitizer.

**How to avoid:**
- Add a `@SuppressWarnings("java:S5144")` (Semgrep) or a CodeQL per-query filter for the specific `storeFromUrl` method that explains the intentional design.
- For CodeQL, add a `codeql-config.yml` query filter that excludes `storeFromUrl` from the SSRF check by file path or method name.
- Document in a `SECURITY.md` comment block WHY the blocklist approach was chosen and what its limits are (to inform future reviewers).
- Phase 84 (SAST setup) must configure query exclusions BEFORE enabling the CodeQL gate.

**Warning signs:**
- CodeQL alerts show `storeFromUrl` in the SSRF category.
- The alert points to the `openConnection()` call, not to any new code.
- Developers suppress the alert without understanding the design — knowledge is lost.

**Phase to address:** Phase 84.

---

### Pitfall 13: CodeQL flags `BCryptPasswordEncoder` usage as "weak password hashing" or "hardcoded credential"

**What goes wrong:**
Some CodeQL queries (particularly community rules) flag `BCryptPasswordEncoder` as a potential concern if they do not recognize it as a strong hasher, or if the cost factor is read from a property file (which looks like a "hardcoded credential" to naive query patterns). Semgrep community rules have a similar issue: rules matching `password` in variable names can false-positive on `BCryptPasswordEncoder` beans.

**Why it happens:**
Generic SAST rules matching on keyword patterns (`password`, `secret`, `credential`) without semantic understanding of the BCrypt API will fire on any `@Bean` method that constructs a `PasswordEncoder` or reads a `bcrypt.strength` property.

**How to avoid:**
- Use CodeQL's standard `java/insecure-randomness` and `java/hardcoded-password-in-code` queries — these are semantically aware of `BCryptPasswordEncoder` and do NOT flag it.
- If using Semgrep, use the `p/spring` ruleset which has Spring Security awareness, not the generic `p/java` ruleset.
- Add a `semgrep.yml` exclusion for `BCryptPasswordEncoder` class name matches.
- Phase 84 must baseline all SAST findings on first run and mark intentional ones as accepted false positives in the tool's triage UI before enabling the blocking gate.

**Warning signs:**
- CodeQL or Semgrep report shows `SecurityConfig` or `PasswordEncoder` bean in a "sensitive data" category.
- Alert points to `new BCryptPasswordEncoder(strength)` as "hardcoded credential."
- Multiple alerts appear in `SecurityConfig.java` that all relate to the intentional auth setup.

**Phase to address:** Phase 84.

---

### Pitfall 14: CodeQL or Semgrep flags the ZIP-Slip and ZipBomb defense code as "path traversal" or "zip handling" vulnerability

**What goes wrong:**
CTC Manager's backup import has an explicit ZIP-Slip defense: `if (!entryPath.startsWith(uploadDir.toRealPath()))`. CodeQL's `java/zipslip` query may still flag the `ZipInputStream.getNextEntry().getName()` call as a ZIP-Slip source, even though the defense exists, IF CodeQL cannot trace the sanitization path through the `startsWith` check. This produces a false positive that blocks the gate.

**Why it happens:**
CodeQL's taint tracking for ZIP-Slip sanitization requires the check to be in a specific form (`toRealPath().startsWith(...)` or `normalize().startsWith(...)`). CTC Manager uses `startsWith(uploadDir.toRealPath())` — the direction of the `startsWith` call matters. If the path is `entryAbsPath.startsWith(basePath)` instead of `basePath` being the prefix, CodeQL may not recognize the sanitization pattern and still flags it.

**How to avoid:**
- Verify the `startsWith` direction matches CodeQL's expected sanitization pattern BEFORE running CodeQL for the first time. The correct form is `resolvedEntry.startsWith(canonicalBase)` where `resolvedEntry` is the tainted value and `canonicalBase` is the trusted base — exactly the current form.
- Add a code comment explaining the ZIP-Slip defense strategy that will help human reviewers if SAST still flags it.
- If CodeQL still flags it, suppress the specific alert via `@SuppressWarnings` + a CodeQL alert dismissal (not a global query disable).
- Phase 84: run CodeQL in report-only mode first and triage each alert before enabling block-on-alert.

**Warning signs:**
- CodeQL alert category is `ZipSlip` pointing to `BackupImportService.restoreOneTable`.
- The alert points to the `entry.getName()` call, which is the taint source.
- Suppressing without investigation removes a real defense signal.

**Phase to address:** Phase 84.

---

### Pitfall 15: `BackupSchema.SCHEMA_VERSION` incremented when fixing the 12 REVIEW.md items

**What goes wrong:**
The Phase 75 REVIEW.md lists 12 improvement items (e.g., `Map.copyOf` order strip, `executedBy` duplication, `restoreOneTable` ZIP-open × 24). ALL of these are implementation-quality improvements that do NOT change the wire format. If a developer increments `SCHEMA_VERSION = 1` to `SCHEMA_VERSION = 2` while fixing them (on the grounds that "the internal ZIP reading strategy changed"), import of existing backup ZIPs fails with "schema version mismatch refused before DB write" — a catastrophic data-loss risk for users with production backups.

**Why it happens:**
`SCHEMA_VERSION` is described in PROJECT.md as "a monotonic int bumped on every wire-incompatible schema change." A developer may interpret "changing ZIP reading from 24 opens to 1 open" as a schema change, but it is NOT — the ZIP layout (per-entity files, manifest-first, entity filenames) is unchanged. The on-disk format is identical; only the reading code changed.

**How to avoid:**
- Before any backup code commit, run `BackupRoundTripIT` and `BackupImportRollbackIT` on a backup ZIP created with the PREVIOUS code. If these pass, the wire contract is preserved and `SCHEMA_VERSION` must NOT change.
- Add a comment block on the `SCHEMA_VERSION = 1` constant: `// INCREMENT ONLY for wire-format changes (ZIP layout, manifest fields, entity JSON shape, EXPORT_ORDER additions). Internal I/O optimization (e.g., single-pass ZIP read) is NOT a wire change.`
- Phase 82 (Backup cleanup) plan must include `BackupRoundTripIT` as a phase-gate test and explicitly state "SCHEMA_VERSION stays at 1."

**Warning signs:**
- A PR changes `SCHEMA_VERSION` but the diff shows no changes to `BackupManifest`, `EntityRef.fileName`, `EXPORT_ORDER`, or entity JSON field names.
- `BackupRoundTripIT` passes (round-trip works) but SCHEMA_VERSION changed anyway.
- A developer's commit message says "bump schema version for ZIP optimization."

**Phase to address:** Phase 82.

---

### Pitfall 16: Fixing `restoreOneTable` ZIP-opens-24× introduces a re-ordering bug that breaks `EXPORT_ORDER` invariant

**What goes wrong:**
The current `restoreOneTable` opens the ZIP archive once per entity (24 times). The fix is to open the ZIP once and stream all entities in order. However, `EXPORT_ORDER` is a topological sort: importing entities out of order violates FK constraints. If the single-pass implementation reads entries in ZIP iteration order (which is correct — ZIP entries are ordered as written) and the ZIP was written in `EXPORT_ORDER` by `BackupExportService`, this is safe. But if the single-pass implementation uses a different iteration strategy (e.g., reads all entry names into a `Map` and then iterates the `EXPORT_ORDER` list, looking up entries by name), and one entry name has a case difference or whitespace issue, the lookup silently skips that entity — causing a partial restore.

**Why it happens:**
The `EntityRef.fileName` derives entity filenames from `@Table(name=...)` via snake_case-to-kebab-case. A case mismatch in a `Map` lookup (e.g., `race-results.json` vs `race_results.json`) would silently return `null` and skip restoration.

**How to avoid:**
- The correct single-pass approach is: stream the ZIP entries in iteration order (which IS `EXPORT_ORDER` order as written by export) and match each entry name to an `EntityRef` at import time. Do NOT build a `HashMap<entryName, ZipEntry>` and look up — keep the streaming order.
- After the fix, `BackupRoundTripIT` must verify row counts for ALL 24 entities, not just the 3 spot-checked (Race, SeasonDriver, Team). The existing SHA-256 byte-equality check on 3 entities is sufficient for those but does not catch skipped entities with zero rows.
- Phase 82 must expand `BackupRoundTripIT` assertions to cover all 24 entities' row counts if they are not already.

**Warning signs:**
- `BackupRoundTripIT` passes but `mvn verify` shows a lower-than-expected entity row count for `PlayoffRound` or `PsnAlias` after restore.
- Import completes without error but a full DB dump shows missing rows for a low-dependency entity.

**Phase to address:** Phase 82.

---

### Pitfall 17: `@DirtiesContext` audit during test-wallclock reduction accidentally removes necessary context resets

**What goes wrong:**
The test-wallclock reduction target (≥ 30% from 16.85% achieved) requires consolidating Spring contexts. An audit of `@DirtiesContext` usage aims to remove unnecessary resets. However, some `@DirtiesContext` annotations exist because a test modifies global Spring state (e.g., `FeatureFlags`, `ImportLockService` lock state, `@Value`-injected properties). Removing `@DirtiesContext` from these tests causes subsequent tests in the same fork to receive a poisoned context — a Spring bean that holds the locked import state from a previous test, causing intermittent `409 Import Lock` failures.

**Why it happens:**
`@DirtiesContext` is often added as a bandage for test isolation rather than proper test cleanup. The audit cannot distinguish "this `@DirtiesContext` is unnecessary overhead" from "this `@DirtiesContext` is compensating for missing `@AfterEach` cleanup."

**How to avoid:**
- Before removing any `@DirtiesContext`, identify WHY it was added by searching git history or comments.
- For `ImportLockService`: ensure each test that acquires the lock releases it in `@AfterEach`. Do NOT rely on `@DirtiesContext` for lock cleanup.
- For `FeatureFlags` or `@Value` fields: use `ReflectionTestUtils.setField()` + restore in `@AfterEach` instead of context reset.
- Run `./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234` three times after removing a `@DirtiesContext` to verify ordering independence.
- Phase 85 (test-wallclock) must treat each `@DirtiesContext` removal as a separate commit with targeted test runs before merging.

**Warning signs:**
- After removing `@DirtiesContext`, a test that previously passed starts intermittently failing with `409` or `503`.
- Failure is order-dependent: `./mvnw test -Dsurefire.runOrder=alphabetical` passes but `random` fails.
- The `ImportLockedWriteRejector` fires unexpectedly in tests that do not test the import lock.

**Phase to address:** Phase 85.

---

### Pitfall 18: Spring context cache poisoning between Surefire forks with `forkCount=2` and shared H2 database URL

**What goes wrong:**
Surefire `forkCount=2` spawns 2 JVMs. Both JVMs use the dev profile with H2 in-memory database. H2 in-memory databases with the same URL (`jdbc:h2:mem:ctcdb`) are shared within the same JVM but NOT across JVMs — each fork gets its own isolated H2 instance. This is currently safe. However, if any IT test uses `@DynamicPropertySource` to override the H2 URL to a file-based URL (e.g., `jdbc:h2:file:./target/testdb`) for some cross-fork scenario, BOTH forks write to the SAME file and Flyway's migration lock will deadlock.

**Why it happens:**
H2 in-memory URLs are JVM-scoped. File-based H2 URLs are OS-scoped. The distinction is invisible in the URL string until production failures reveal it.

**How to avoid:**
- Never use file-based H2 URLs in tests. The existing `BackupImportMariaDbSmokeIT` correctly uses Testcontainers for the MariaDB scenario — this is the right pattern.
- If new backup or import ITs need a persistent-across-test-method store, use Testcontainers MariaDB (already available in the test scope) rather than file-based H2.
- Phase 85: if test-wallclock reduction involves restructuring IT forks, verify H2 URL isolation with `./mvnw verify -Dsurefire.forkCount=4` to stress-test the isolation.

**Warning signs:**
- Flyway deadlock error in IT logs: "Waiting for changelog lock."
- Tests pass individually but fail in parallel.
- `target/testdb.mv.db` appears in the working directory during test runs.

**Phase to address:** Phase 85.

---

### Pitfall 19: OpenRewrite `rewrite-maven-plugin` in `<build>` triggers on every `./mvnw verify` call if not isolated to a separate profile

**What goes wrong:**
If the `rewrite-maven-plugin` is added to the main `<build><plugins>` block (not in a profile), the `rewrite:dryRun` or `rewrite:check` goal may run on every `mvn verify` invocation, adding 20–60 seconds to the build per run. At CI scale (every PR + push), this accumulates. If `<failOnDryRunResults>true</failOnDryRunResults>` is set and any recipe produces a diff, CI fails on the `verify` target — blocking the entire development workflow until the recipe is applied.

**Why it happens:**
The plugin configuration documentation shows examples that add the plugin to the main build. Teams then wire `rewrite:check` to the `verify` phase. But `check` mode runs recipes and fails if any diff would be produced — this is only appropriate in a dedicated "code quality" CI step, not in the standard `mvn verify` loop.

**How to avoid:**
- Add `rewrite-maven-plugin` inside a dedicated Maven profile (e.g., `<profile id="rewrite">`) that is NOT activated by default.
- Run recipes explicitly: `./mvnw rewrite:dryRun -Prewrite` or `./mvnw rewrite:run -Prewrite`.
- Wire the CI gate as a separate workflow step or workflow job that uses `-Prewrite -DfailOnDryRunResults=true`.
- This keeps `./mvnw verify` at its current ~2-minute wall clock.

**Warning signs:**
- `./mvnw verify` output includes "Running OpenRewrite recipes..." on every invocation.
- CI wall-clock increases by 30–60 seconds for every PR.
- Developers stop running `./mvnw verify` locally because it's too slow.

**Phase to address:** Phase 80 — plugin must be in a profile, not the default build.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Enable Checkstyle/PMD with `<failOnViolation>false</failOnViolation>` permanently | No build break during rollout | Rules accumulate but never get fixed; baseline creep | Only in the first week of rollout, then enable gate |
| Use entire PMD category includes (`category/java/bestpractices.xml`) | Less config | PMD version bumps add rules silently, break CI | Never — always use explicit rule references |
| Add `@SuppressWarnings("all")` to suppress SpotBugs on an entity class | Eliminates false positives quickly | Suppresses real future bugs too | Never — use targeted `@SuppressFBWarnings` with specific bug code |
| Skip `rewrite:dryRun` and run `rewrite:run` directly | Faster | Recipes may rewrite production code unexpectedly; no diff to review | Never in CI; acceptable in local throwaway branches |
| Increment `SCHEMA_VERSION` for non-wire-format internal changes | "Feels right" | Breaks import of all existing production backup ZIPs | Never — only for actual wire format changes |
| Set `@DirtiesContext` on every IT | Easy test isolation | Context restarts multiply, 2× wallclock | Acceptable as a temporary debugging aid, but must be cleaned up |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| SpotBugs + Lombok | Enable SpotBugs before adding `lombok.config` | Add `lombok.extern.findbugs.addSuppressFBWarnings = true` to `lombok.config` FIRST, then enable SpotBugs gate |
| Checkstyle + Lombok | Apply Google or Sun Checkstyle config verbatim | Start with a custom `checkstyle.xml` that excludes `DesignForExtension` for entity classes; add `lombok.addLombokGeneratedAnnotation = true` to `lombok.config` |
| Renovate + Spring Boot parent | Rely on default Renovate grouping | Explicitly configure `group:springBoot` and separately protect the Thymeleaf version pin |
| Renovate + Guava | Let Renovate auto-manage Guava version | Add `allowedVersions` regex to block `-android` variants |
| CodeQL + custom security code | Run CodeQL and accept all findings | Run in report-only mode first; triage intentional patterns (SSRF blocklist, ZIP-Slip defense) before enabling blocking gate |
| OpenRewrite + existing Spring Boot 4 | Apply `UpgradeSpringBoot_4_0` recipe | Only apply targeted maintenance recipes; never apply the Boot migration composite recipe on a codebase already at Boot 4 |
| JaCoCo + new Maven plugins | Add plugins without verifying coverage still works | After every new plugin, verify `target/site/jacoco/index.html` shows non-zero coverage |
| Semgrep + Spring Security | Use `p/java` generic ruleset | Use `p/spring` ruleset which understands Spring Security annotations and BCrypt |

---

## "Looks Done But Isn't" Checklist

- [ ] **OpenRewrite setup:** `rewrite-maven-plugin` is in a profile, not the default build — verify `./mvnw verify` does NOT include recipe execution output.
- [ ] **SpotBugs gate:** `lombok.config` has `lombok.extern.findbugs.addSuppressFBWarnings = true` AND a `spotbugs-exclude.xml` for `EI_EXPOSE_REP*` on domain model — verify zero violations in entity package BEFORE enabling `<failOnError>true</failOnError>`.
- [ ] **Checkstyle gate:** Custom `checkstyle.xml` excludes `DesignForExtension` for entity package AND `lombok.config` has `lombok.addLombokGeneratedAnnotation = true` — verify violation count is in double digits (not triple) on first `mvn checkstyle:check` run.
- [ ] **PMD ruleset:** `src/pmd/pmd-ruleset.xml` exists with individual rule references — verify `cat pom.xml | grep category/java/` shows zero category-level includes.
- [ ] **Renovate Guava:** `renovate.json` has a `packageRule` for `com.google.guava:guava` restricting to `-jre` variants — verify by reading the `allowedVersions` regex.
- [ ] **Renovate Thymeleaf pin:** `renovate.json` disables or requires manual approval for `org.thymeleaf:thymeleaf` — verify that Thymeleaf does NOT appear in auto-merge rules.
- [ ] **SCHEMA_VERSION:** `BackupSchema.SCHEMA_VERSION` is still `1` after all Phase 82 backup cleanup commits — verify with `grep SCHEMA_VERSION src/main/java/org/ctc/backup/schema/BackupSchema.java`.
- [ ] **Backup round-trip:** `BackupRoundTripIT` passes on a backup ZIP created before the cleanup changes were applied — verify by creating a backup on current master, then running the test against the backup after Phase 82 changes.
- [ ] **JaCoCo coverage after tooling phase:** `target/site/jacoco/index.html` still shows ≥ 82% (preferably ≥ 86%) after all tooling plugins are added — verify by running `./mvnw verify` and checking the jacoco report.
- [ ] **CodeQL initial triage:** Every CodeQL alert has been manually reviewed and marked as either "true positive (fix it)" or "intentional (suppress with comment)" — verify the GitHub Security tab shows zero unreviewed alerts.

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| OpenRewrite Boot-4 composite recipe misapplication | Phase 80 | `mvn rewrite:dryRun -Prewrite` shows no pom.xml starter additions |
| OpenRewrite Lombok structural interference | Phase 80 | Diff of dryRun patch for all `org.ctc.domain.model.*` files reviewed manually |
| OpenRewrite in default build (not profile) | Phase 80 | `./mvnw verify` output does not include "Running OpenRewrite" |
| SpotBugs EI_EXPOSE_REP false positives | Phase 81 | `mvn spotbugs:check` shows zero violations in entity package |
| Checkstyle Lombok/DesignForExtension false positives | Phase 81 | `mvn checkstyle:check` shows < 20 violations total on first run |
| PMD category-level includes breaking on version bump | Phase 81 | `src/pmd/pmd-ruleset.xml` uses explicit rule refs only |
| JaCoCo argLine overwrite by new plugins | Phase 81 | Coverage remains ≥ 86% after all plugin additions |
| Renovate Guava `-android` variant proposals | Phase 83 | `renovate.json` includes Guava classifier constraint |
| Renovate Boot parent + Thymeleaf pin conflict | Phase 83 | `renovate.json` protects Thymeleaf pin from auto-update |
| Renovate Java 26 proposal | Phase 83 | `renovate.json` LTS-only `allowedVersions` for `java.version` |
| Renovate GitHub Actions misaligned bumps | Phase 83 | GitHub Actions updates require manual review in `renovate.json` |
| CodeQL SSRF blocklist false positive | Phase 84 | SSRF blocklist alert marked as intentional in GitHub Security tab |
| CodeQL BCrypt false positive | Phase 84 | No SecurityConfig alerts in GitHub Security tab after triage |
| CodeQL ZIP-Slip defense false positive | Phase 84 | ZIP-Slip alert (if present) dismissed with explanation |
| SCHEMA_VERSION increment on non-wire changes | Phase 82 | `grep SCHEMA_VERSION` output is `1` after all backup cleanup |
| `restoreOneTable` single-pass re-ordering bug | Phase 82 | `BackupRoundTripIT` verifies all 24 entity row counts |
| `@DirtiesContext` removal poisoning contexts | Phase 85 | Three random-order Surefire runs pass after each removal |
| H2 file-based URL cross-fork collision | Phase 85 | No `target/*.mv.db` files appear during `./mvnw verify` |

---

## Sources

- [OpenRewrite Lombok Best Practices recipe](https://docs.openrewrite.org/recipes/java/migrate/lombok/lombokbestpractices)
- [OpenRewrite Issue #1407: Delombok source before publishing](https://github.com/openrewrite/rewrite/issues/1407)
- [OpenRewrite: LombokValToFinalVar recipe](https://docs.openrewrite.org/recipes/java/migrate/lombok/lombokvaltofinalvar)
- [OpenRewrite: Migrate to Spring Boot 4.0 (Community Edition)](https://docs.openrewrite.org/recipes/java/spring/boot4/upgradespringboot_4_0-community-edition)
- [OpenRewrite: MigrateToModularStarters Community Edition](https://docs.openrewrite.org/recipes/java/spring/boot4/migratetomodularstarters-community-edition)
- [OpenRewrite dryRun CI gate: `failOnDryRunResults`](https://docs.openrewrite.org/reference/rewrite-maven-plugin)
- [SpotBugs Issue #731: EI_EXPOSE_REP false positive for Lombok auto-generated code](https://github.com/spotbugs/spotbugs-gradle-plugin/issues/731)
- [SpotBugs Issue #3471: SuppressFBWarnings on record fields flagged as useless in 4.9.2](https://github.com/spotbugs/spotbugs/issues/3471)
- [Checkstyle Issue #13508: checkstyle not taking lombok generated code](https://github.com/checkstyle/checkstyle/issues/13508)
- [Checkstyle: SuppressionFilter](https://checkstyle.sourceforge.io/filters/suppressionfilter.html)
- [Renovate Issue #15170: Maven property versions managed by parent POM](https://github.com/renovatebot/renovate/issues/15170)
- [Renovate Issue #8248: Avoid upgrade when version is managed by BOM](https://github.com/renovatebot/renovate/issues/8248)
- [Renovate Java Versions documentation](https://docs.renovatebot.com/java/)
- [Renovate: Automated Dependency Updates for GitHub Actions](https://docs.renovatebot.com/modules/manager/github-actions/)
- [GitHub Changelog: CodeQL 2.23.1 adds Java 25 support](https://github.blog/changelog/2025-09-26-codeql-2-23-1-adds-support-for-java-25-typescript-5-9-and-swift-6-1-3/)
- [GitHub Changelog: CodeQL 2.25.4 (May 2026)](https://github.blog/changelog/2026-05-12-codeql-2-25-4-adds-swift-6-3-1-support-improvements-to-c-and-java-and-more/)
- [PMD Maven Plugin: Violation Checking and failOnViolation](https://maven.apache.org/plugins/maven-pmd-plugin/examples/violationChecking.html)
- [JaCoCo and Surefire argLine: late-evaluation pattern](http://www.devll.org/blog/2020/java/jacoco-argline.html)
- [Semgrep: Java semantic detection](https://semgrep.dev/docs/semgrep-code/java)

---
*Pitfalls research for: v1.11 Tooling Infrastructure & Tech-Debt Sweep (CTC Manager)*
*Researched: 2026-05-16*
