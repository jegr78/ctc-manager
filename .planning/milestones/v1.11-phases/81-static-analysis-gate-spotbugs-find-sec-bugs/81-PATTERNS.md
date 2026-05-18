# Phase 81: Static Analysis Gate (SpotBugs + find-sec-bugs) — Pattern Map

**Mapped:** 2026-05-16
**Files analyzed:** 6 (2 net-new, 4 modified)
**Analogs found:** 4 / 6 (two files are net-new with no in-repo analog)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `lombok.config` | build-config (annotation-processor key-value) | read by Lombok APT at compile time | none — net-new file format at repo root | no analog |
| `config/spotbugs-exclude.xml` | suppression-config (SpotBugs filter XML) | read by `spotbugs-maven-plugin` at analysis time | none — no in-repo SpotBugs filter exists | no analog |
| `pom.xml` | build-config (main `<build><plugins>` block, verify-bound) | Maven lifecycle — `verify` phase always-on | `pom.xml` lines 303–355 (`jacoco-maven-plugin`) + lines 356–384 (`exec-maven-plugin`) | exact — same project file, same `<build><plugins>` block |
| `CLAUDE.md` | documentation / agent-instruction index | static read by agent runtime | `CLAUDE.md` lines 215–217 (`### CSS Guidelines` block) + Phase 80's OpenRewrite paragraph appended in the same section | exact — same project file, append-only edit in `## Conventions` |
| `src/main/java/org/ctc/domain/service/FileStorageService.java` | source (security hardening cross-ref comment) | request-response (file I/O) | `pom.xml:367–378` (`exec-maven-plugin` inline rationale) for comment style | role-match (rationale-comment pattern) |
| `src/main/java/org/ctc/backup/service/BackupArchiveService.java` | source (security hardening cross-ref comment) | batch / file I/O | same as FileStorageService analog above | role-match |

---

## Pattern Assignments

### 1. `lombok.config` (build-config, compile-time annotation-processor)

**Role:** Project root Lombok config. Pins this directory as the authoritative config root and enables two SpotBugs false-positive mitigations across all 24 JPA entities.

**Analog:** None. Closest spiritual neighbour is `rewrite.yml` (also a flat tool-config file at the project root, added in Phase 80), but the schema is completely different. Follow Lombok configuration docs directly.

**Content — exact literal (do not alter):**
```
config.stopBubbling = true
lombok.addLombokGeneratedAnnotation = true
lombok.extern.findbugs.addSuppressFBWarnings = true
```

**Key constraints for the planner:**
- `config.stopBubbling = true` is belt-and-braces: marks this as the authoritative Lombok config for the whole project subtree. Required at the project root (Maven module root).
- `lombok.addLombokGeneratedAnnotation = true` causes Lombok to annotate generated methods with `@lombok.Generated`, which suppresses them from code-coverage tools (JaCoCo respects this annotation). Side benefit: keeps coverage numbers clean from generated code.
- `lombok.extern.findbugs.addSuppressFBWarnings = true` causes Lombok to emit `@SuppressFBWarnings` on generated methods. This is the primary Lombok-false-positive mitigation for `EI_EXPOSE_REP*` findings on `@Getter`-generated collection accessors across all 24 JPA entities. The package-level filter in `spotbugs-exclude.xml` (D-08 layer 2) is belt-and-braces for synthetic accessor edge cases.
- File location: repository root, sibling to `pom.xml`, `mvnw`, `mvnw.cmd`, `rewrite.yml`.
- `lombok.version=1.18.46` (pom.xml line 19) supports both config keys since Lombok 1.18.20+.

---

### 2. `config/spotbugs-exclude.xml` (suppression-config, SpotBugs filter XML)

**Role:** SpotBugs exclusion filter. Declares three class-level exclusions (D-08 layer 1), one package-level pattern exclusion (D-08 layer 2), and three intentional-hardening suppressions (D-11, revised per RESEARCH.md C-01 and F-02).

**Analog:** None in-repo. Schema defined at https://spotbugs.readthedocs.io/en/latest/filter.html. The rationale-comment discipline is modeled after the `exec-maven-plugin` inline comments at `pom.xml:367–378`.

**Rationale-comment style — analog `pom.xml:367–378`:**
```xml
<argument><![CDATA[
violations=$(grep -rE 'th:(replace|insert|include)="~\{[^"]*\(.*\$\{.*\}.*\)\}"' src/main/resources/templates/ | grep -vF 'layout(${pageTitle}' || true);
if [ -n "$violations" ]; then
  echo "[PLAT-07 build-guard] Forbidden Thymeleaf fragment-call expression detected (Plan 05):";
  ...
  echo "See .planning/phases/71-*/71-CONTEXT.md Decisions D-05 + D-12 for the canonical fix.";
  exit 1;
fi;
...
]]></argument>
```
Every `<Match>` entry in the XML file MUST have an XML comment immediately above it following the pattern: class/method + pattern + one-sentence rationale + file:line cross-reference where the actual defense lives. No blank `<Match>` entries ever.

**Full reference shape (verbatim from RESEARCH.md Pattern 3 — verified against live source):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<FindBugsFilter
    xmlns="https://github.com/spotbugs/filter/3.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="https://github.com/spotbugs/filter/3.0.0
                        https://raw.githubusercontent.com/spotbugs/spotbugs/master/spotbugs/etc/findbugsfilter.xsd">

    <!-- ========== Class-level excludes (D-08 layer 1) ========== -->

    <!-- Spring Boot entry point — no business logic, always excluded from analysis -->
    <Match>
        <Class name="org.ctc.CtcManagerApplication"/>
    </Match>

    <!-- @Profile("dev") test-data provider — E2E tested only, not subject to static analysis gate -->
    <Match>
        <Class name="org.ctc.admin.TestDataService"/>
    </Match>

    <!-- @Profile("dev,demo") demo-data seeder — manual-test-only, not subject to gate -->
    <Match>
        <Class name="org.ctc.admin.DemoDataSeeder"/>
    </Match>

    <!-- ========== Pattern-on-package excludes (D-08 layer 2) ========== -->

    <!-- EI_EXPOSE_REP / EI_EXPOSE_REP2 on all 24 JPA entities:
         Lombok @Getter generates return this.field for @OneToMany collections.
         SpotBugs flags this as exposing internal mutable state. For an OSIV-enabled
         JPA application, returning the Hibernate-proxy list IS the intended behavior —
         a defensive copy would break lazy loading. Belt-and-braces with
         lombok.extern.findbugs.addSuppressFBWarnings=true in lombok.config (which
         emits @SuppressFBWarnings at method level); this filter catches synthetic
         accessor methods that Lombok cannot annotate. See CLAUDE.md Conventions
         section for the lombok.config invariant. -->
    <Match>
        <Package name="org.ctc.domain.model"/>
        <Bug pattern="EI_EXPOSE_REP,EI_EXPOSE_REP2"/>
    </Match>

    <!-- ========== Intentional-pattern suppressions (D-11) ========== -->

    <!-- FileStorageService.storeFromUrl(): SSRF hostname blocklist (validateHostname method,
         lines 125-153) implements a blocklist via if/startsWith chains covering localhost,
         127.x, 10.x, 192.168.x, 169.254.x, and 172.16-31.x ranges.
         find-sec-bugs cannot recognize startsWith-chain blocklists as SSRF sanitizers
         (only allowlist-style sanitizers are recognized). The defense is intentional.
         See FileStorageService.java:87-103 and :125-153 for the full defense implementation. -->
    <Match>
        <Class name="org.ctc.domain.service.FileStorageService"/>
        <Method name="storeFromUrl"/>
        <Bug pattern="SSRF_SPRING,SSRF"/>
    </Match>

    <!-- FileStorageService path-traversal defenses: validatePathWithinUploadDir (line 156-162)
         and validateNoPathTraversal (line 164-169) use toAbsolutePath().normalize().startsWith()
         and contains("..") checks respectively. find-sec-bugs PATH_TRAVERSAL_IN detects the
         unresolved path usage at the call sites before the validation call — it cannot trace
         the defense through separate private methods. The defense is intentional and unit-tested.
         See FileStorageService.java:156-169 for the full defense implementation. -->
    <Match>
        <Class name="org.ctc.domain.service.FileStorageService"/>
        <Bug pattern="PATH_TRAVERSAL_IN"/>
    </Match>

    <!-- BackupArchiveService path-traversal defenses: assertEntrySafe() (line 608-623) delegates
         to PathTraversalGuard.assertWithin() which calls toAbsolutePath().normalize().startsWith().
         find-sec-bugs cannot trace the defense through the delegated utility class.
         See BackupArchiveService.java:608-623 and PathTraversalGuard.java for the defense. -->
    <Match>
        <Class name="org.ctc.backup.service.BackupArchiveService"/>
        <Bug pattern="PATH_TRAVERSAL_IN"/>
    </Match>

</FindBugsFilter>
```

**Critical divergences from CONTEXT.md D-11 (RESEARCH.md corrections apply):**
- Do NOT include a `HARD_CODE_PASSWORD` suppression for `SecurityConfig.passwordEncoder` — that method does not exist (RESEARCH.md C-01). `SecurityConfig` uses `httpBasic` with no `PasswordEncoder` bean.
- The `PATH_TRAVERSAL_IN` suppression targets `BackupArchiveService` (not `BackupImportService.restoreOneTable`) — the ZIP-Slip defense lives in `BackupArchiveService.assertEntrySafe()` at lines 608–623 (RESEARCH.md F-02).

**Triage-time additions (D-12 step 3 — not pre-staged, added after baseline run):**
Any additional `<Match>` entries discovered during baseline triage MUST also follow the rationale-comment format above. Common candidates per RESEARCH.md:
- `RR_NOT_CHECKED` on `Files.deleteIfExists()` calls in `BackupArchiveService` best-effort cleanup methods: rationale "intentional best-effort cleanup — failure is already caught by IOException handler"
- `DM_DEFAULT_ENCODING` on `new ZipFile(staged.toFile())` in `BackupImportService`: rationale "backup ZIPs produced by same JVM; entry names are ASCII-only entity table names; platform encoding is UTF-8 on all production JVMs (Java 25 default)"
- Graphic service findings: suppress per-method or per-pattern, never per class-level blanket

---

### 3. `pom.xml` — insert `spotbugs-maven-plugin` block (main `<build><plugins>`)

**Role:** Always-on `verify`-phase static analysis gate in the main build (NOT a profile).

**Insertion point:** Between `pom.xml` line 355 (`</plugin>` closing JaCoCo block) and line 356 (`<plugin>` opening exec-maven-plugin block). The new block inserts into the same `<build><plugins>` section — NOT inside `<profiles>`.

**Primary analog — JaCoCo plugin block (`pom.xml:303–355`):**

Structure to copy:
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.14</version>
    <configuration>
        <excludes>...</excludes>
    </configuration>
    <executions>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>...</configuration>
        </execution>
    </executions>
</plugin>
```

The SpotBugs plugin follows the same outer shape: `<groupId>/<artifactId>/<version>/<configuration>/<executions>`. It adds a `<dependencies>` block (plugin-level, not project-level) for the `findsecbugs-plugin` detector pack — this is the structural difference from JaCoCo.

**Secondary analog — exec-maven-plugin block (`pom.xml:356–384`):**

The inline rationale-comment pattern inside `<configuration>/<arguments>` is the reference for why the planner annotates the goal as STAT-05/1 → STAT-05/2 in a comment:
```xml
<argument>-c</argument>
<argument><![CDATA[
  violations=$(grep -rE ...
  ...
  echo "[PLAT-07 build-guard] OK - no Thymeleaf fragment-call expression offenders.";
]]></argument>
```
The SpotBugs block's inline comment on the `<goal>` element mirrors this "rationale inside configuration" discipline.

**OPPOSITE analog — `<profile id="rewrite">` block (`pom.xml:422–456`):**
This is what Phase 81 must NOT produce. The rewrite plugin is profile-scoped (`-Prewrite`), developer-invoked, and has no `<executions>`. SpotBugs is verify-bound, always-on, and has an explicit `<execution>` in `<phase>verify</phase>`.

**Full reference shape (from RESEARCH.md Pattern 1 — planner should use this verbatim for the plumbing commit, starting with `<goal>spotbugs</goal>`):**
```xml
			<!-- STAT-02: SpotBugs bytecode static analysis gate — verify-bound, always-on.
			     find-sec-bugs 1.14.0 adds 144 Spring Security-aware patterns (SSRF, path traversal, etc.).
			     Insertion point: after jacoco:check, before exec template-fragment-call-guard.
			     NO <argLine> ever — SpotBugs is a bytecode scanner, not a JVM agent (Pitfall #7). -->
			<plugin>
				<groupId>com.github.spotbugs</groupId>
				<artifactId>spotbugs-maven-plugin</artifactId>
				<version>4.9.8.3</version>
				<configuration>
					<effort>Max</effort>
					<threshold>Default</threshold>
					<failOnError>true</failOnError>
					<excludeFilterFile>${project.basedir}/config/spotbugs-exclude.xml</excludeFilterFile>
				</configuration>
				<executions>
					<execution>
						<id>spotbugs-check</id>
						<phase>verify</phase>
						<goals>
							<!-- STAT-05/1: report-only — flip to <goal>check</goal> after baseline triage -->
							<goal>spotbugs</goal>
						</goals>
					</execution>
				</executions>
				<dependencies>
					<!-- STAT-03: find-sec-bugs detector pack — 144 Spring Security-aware patterns -->
					<dependency>
						<groupId>com.h3xstream.findsecbugs</groupId>
						<artifactId>findsecbugs-plugin</artifactId>
						<version>1.14.0</version>
					</dependency>
				</dependencies>
			</plugin>
```

**For the gate-flip commit (STAT-05/2), change only one line:**
```xml
						<!-- STAT-05/2: blocking gate — fails build on Medium+HIGH violations -->
						<goal>check</goal>
```

**Critical anti-patterns — these MUST NOT appear in the SpotBugs block:**
- No `<argLine>` of any kind (D-04 / Pitfall #7). JaCoCo's `@{argLine}` at pom.xml lines 258, 289, 405 must remain the only `argLine` in the file.
- No `<activation>` element (this is not a profile).
- No placement inside `<profiles>` (this is main-build, not profile-scoped).

**Indentation:** Literal tabs matching the surrounding `pom.xml` (three tabs for `<plugin>`, four tabs for `<groupId>`, etc.). Verified at JaCoCo block lines 303–355.

---

### 4. `CLAUDE.md` — append SpotBugs paragraph to `## Conventions` section (after `### CSS Guidelines`)

**Role:** Agent-instruction documentation. The new paragraph documents the SpotBugs gate, the suppression-file workflow, and the `lombok.config` invariant so future agents (and human maintainers) understand the constraints.

**Insertion point:** After the existing `### CSS Guidelines` block (currently ending at `CLAUDE.md` line 217):
```
### CSS Guidelines

* Use CSS classes from `admin.css` instead of inline styles: `btn-xs`, `btn-sm`, etc.
```
The new content appends as a new `###` sub-section after this line, before `## References` (which begins further down the file).

**Analog — existing `### CSS Guidelines` block format (`CLAUDE.md:215–217`):**
```markdown
### CSS Guidelines

* Use CSS classes from `admin.css` instead of inline styles: `btn-xs`, `btn-sm`, etc.
```

The pattern: `###` sub-heading, then one or more `* **Bold keyword:**` bullet points, English, terse, with inline code for specific names. Match this format exactly.

**Reference shape for the new paragraph (three bullet points per D-14):**
```markdown
### Static Analysis (SpotBugs + find-sec-bugs)

* **Gate:** `spotbugs-maven-plugin` 4.9.8.3 + `findsecbugs-plugin` 1.14.0 run on every `./mvnw verify` (Medium+HIGH findings block the build). No separate CI job — SpotBugs runs inside the existing `verify` step.
* **Suppressions:** Live in `config/spotbugs-exclude.xml`. Every `<Match>` entry MUST have an XML rationale comment with a code-cross-reference to where the intentional pattern lives. No `@SuppressWarnings("all")` ever — use targeted `@SuppressFBWarnings({"SPECIFIC_CODE"}, justification="...")` in source or a `<Match>` entry in the filter file.
* **`lombok.config` invariant:** `lombok.config` at project root sets `lombok.extern.findbugs.addSuppressFBWarnings=true`. Do NOT remove or modify the two SpotBugs-related lines without a new phase that re-baselines suppressions — removing them re-introduces ~40–80 `EI_EXPOSE_REP*` false positives from Lombok-generated entity getters.
```

**Tone check:** Match the existing bullet-point style: starts with bold keyword + colon, English, imperative where giving a constraint ("MUST", "Do NOT"), inline code for specific names and values.

---

### 5. `FileStorageService.java` — code-cross-reference inline comments (D-09)

**Role:** Source comment cross-reference. The suppression entries in `config/spotbugs-exclude.xml` for this file cite line numbers; those line numbers must have visible comment blocks in the source so a reviewer can find the defense without opening the filter file.

**Current state:** The defense methods already exist (verified by live read above):
- `storeFromUrl` (lines 86–103): SSRF target — `validateHostname` called at line 91
- `validateHostname` (lines 125–153): blocklist defense
- `validatePathWithinUploadDir` (lines 156–162): `toAbsolutePath().normalize().startsWith(uploadDir)` defense
- `validateNoPathTraversal` (lines 164–169): `contains("..")` check

**What needs to change:** Add a short comment block at the top of `validateHostname` and `validatePathWithinUploadDir` cross-referencing the suppression, e.g.:
```java
// SSRF defense: find-sec-bugs cannot recognize startsWith-chain hostname blocklists as
// sanitizers. This method is the suppressed sanitizer. See config/spotbugs-exclude.xml
// FileStorageService SSRF_SPRING,SSRF entry for the corresponding suppression rationale.
private void validateHostname(String sourceUrl) { ...
```

**Analog — rationale comment style in pom.xml (`pom.xml:290–292`):**
```java
<!-- Tag-based test routing (see TESTING.md "Test Categorization"):
     run @Tag("integration") tests, exclude @Tag("e2e") and @Tag("flaky").
     Filename-based includes/excludes have been removed in favor of tags. -->
```
Pattern: single-line or multi-line inline comment, English, references the named decision document and the specific config location.

**Important:** If the baseline SpotBugs report shows zero findings for these patterns (because the filter file suppresses them cleanly at analysis time), the source comments are still required per D-09 so a code reviewer can understand the suppression without opening the filter file.

---

### 6. `BackupArchiveService.java` — code-cross-reference inline comment (D-09)

**Role:** Same as FileStorageService above — a short cross-reference comment at the `assertEntrySafe` method.

**Current state (verified by live read, lines 608–623):**
```java
private static void assertEntrySafe(ZipEntry entry, Path stagingRoot,
        int currentEntryCount, long currentInflatedBytes) {
    ...
    if (!entry.isDirectory()) {
        PathTraversalGuard.assertWithin(stagingRoot, entry.getName());
    }
}
```
The `assertEntrySafe` Javadoc already documents the ZIP-Slip defense purpose (line 83 per grep output). A short inline cross-reference comment at the method body, referencing the `spotbugs-exclude.xml` suppression, is sufficient:
```java
// PATH_TRAVERSAL defense: PathTraversalGuard.assertWithin() is the sanitizer; find-sec-bugs
// cannot trace the defense through the delegated utility call. See config/spotbugs-exclude.xml
// BackupArchiveService PATH_TRAVERSAL_IN entry for the corresponding suppression rationale.
private static void assertEntrySafe(...) { ...
```

**Match quality:** same rationale-comment pattern as FileStorageService analog above.

---

## Shared Patterns

### Tabs-not-spaces in `pom.xml`
**Source:** `pom.xml` entire file (verified at lines 303–384).
**Apply to:** the new SpotBugs plugin block insertion.
**Excerpt evidence:**
```xml
			<plugin>              ← 3 tabs
				<groupId>...      ← 4 tabs
					<execution>   ← 5 tabs
```
Any space-indented insertion will diverge visually from the surrounding file.

### Rationale-as-comment discipline
**Source:** `pom.xml:367–378` (`exec-maven-plugin` CDATA script with inline rationale).
**Apply to:** every `<Match>` entry in `config/spotbugs-exclude.xml`, the `<goal>` comment in the SpotBugs plugin block, and the source cross-reference comments in `FileStorageService.java` and `BackupArchiveService.java`.
**Core pattern:** comment names the decision context, the specific code location, and why this is intentional (not a missed fix).

### English-only content
**Source:** `CLAUDE.md` `## Language` section.
**Apply to:** all new files — `lombok.config` (already English), `config/spotbugs-exclude.xml` (XML comments must be English), `CLAUDE.md` paragraph (English), source cross-reference comments (English).

### Conventional Commits with scope
**Source:** `CLAUDE.md` `## Git Workflow`.
**Apply to:** every commit in this phase.
- Plumbing commit: `feat(81): wire SpotBugs report-only baseline (STAT-01..STAT-04, STAT-05/1)`
- Triage suppression commits: `chore(81): suppress <PatternName> on <ClassName> with rationale`
- Triage fix commits: `fix(81): fix <issue> in <ClassName> flagged by SpotBugs`
- Gate-flip commit: `feat(81): activate SpotBugs blocking gate (STAT-05/2, STAT-06)`
- Docs commit: `docs(81): update CLAUDE.md Conventions with SpotBugs gate (STAT-07)`

### Pre-PR verification gate
**Source:** `CLAUDE.md` `## Git Workflow` → "Before PR".
**Apply to:** final pre-PR verification.
- Per RESEARCH.md F-01 / Pitfall B: use `./mvnw spotbugs:spotbugs -DskipTests` for fast feedback between triage commits.
- Final verification: `./mvnw verify -Pe2e` (one full run with E2E per CLAUDE.md `feedback_e2e_verification`).
- Coverage post-wiring must be within ±0.5pp of the v1.10 baseline (87.80%), and at minimum ≥ 82%.

### Profile-vs-main-build distinction (OPPOSITE pattern reference)
**Source:** `pom.xml:422–456` (`<profile id="rewrite">`) — Phase 80's work.
**Apply to:** understand what NOT to do. The rewrite plugin has no `<executions>`, lives in a `<profile>`, and is `-Prewrite`-activated only. SpotBugs is the OPPOSITE: main `<build><plugins>`, explicit `<execution>` bound to `<phase>verify</phase>`, always runs without any flag.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `lombok.config` | build-config (Lombok APT key-value pairs) | read by Lombok annotation processor at `javac` time | No `lombok.config` exists in the repo (confirmed: RESEARCH.md F-06). No other flat key-value tool-config at project root serves as an analog — `compose.yaml` is Docker Compose schema, `rewrite.yml` is OpenRewrite YAML. Planner should use the exact three-line content from CONTEXT.md Specific Ideas / RESEARCH.md Pattern 2 verbatim. |
| `config/spotbugs-exclude.xml` | suppression-config (SpotBugs FindBugsFilter XML) | read by `spotbugs-maven-plugin` at analysis time | No SpotBugs filter file exists in the repo (confirmed: RESEARCH.md F-05/F-06). The `config/` directory itself is net-new. Planner should use the full XML from RESEARCH.md Pattern 3 as the pre-staged skeleton, then augment during triage with additional `<Match>` entries. |

---

## Metadata

**Analog search scope:** `pom.xml` (main `<build><plugins>` block, lines 290–387; `<profiles>` block, lines 388–459), `CLAUDE.md` (lines 188–217), Phase 80 PATTERNS.md, `FileStorageService.java` (lines 80–169), `BackupArchiveService.java` (lines 605–624).
**Files scanned:** 7 (CONTEXT.md, RESEARCH.md, pom.xml, CLAUDE.md, Phase 80 PATTERNS.md, FileStorageService.java, BackupArchiveService.java).
**Pattern extraction date:** 2026-05-16
**Phase directory:** `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/`
