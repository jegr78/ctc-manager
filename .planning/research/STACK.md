# Technology Stack

**Project:** CTC Manager v1.10 — Spring Boot 4.0.6 upgrade + structural Data Export/Import (Admin)
**Researched:** 2026-05-09
**Overall confidence:** HIGH for upgrade path + ZIP/Jackson stack; MEDIUM for replace-all transactional pattern (covered by official MariaDB/H2 docs but project-specific verification needed)

---

## TL;DR — What Changes vs v1.9

| Add / change | Concrete | Rationale |
|---|---|---|
| `spring-boot-starter-parent` | `4.0.5` → `4.0.6` | Patch release, 25 bug fixes, no breaking changes |
| **Thymeleaf** (transitive) | `3.1.x` → **`3.1.5`** (NOT 3.2 — see Correction below) | 3.1.4+ tightened restricted-context evaluation; this is the source of the 3-template breakage |
| Template fragment-parameter ternaries | Move to controller as `pageTitle` model attribute | Maintainer-recommended fix (Thymeleaf #1082) |
| `commons-compress` | **DO NOT ADD** | `java.util.zip.{ZipOutputStream,ZipInputStream}` from JDK is sufficient for our scope (single-archive ≪4 GB, no Zip64, no extended attrs) |
| Jackson | **already on classpath** via `spring-boot-starter-webmvc` (Jackson BOM 3.1.2) | Use `JsonGenerator` streaming + `@JsonIdentityInfo(IntSequenceGenerator)` on entities for export, no extra dependency |
| `spring.servlet.multipart.max-file-size` | `1MB` → **`100MB`** (configured) | Default is too low for backup ZIPs; logos+graphics+attachments can grow to tens of MB |
| `spring.servlet.multipart.max-request-size` | `10MB` → **`100MB`** (configured) | Match per-file limit; single-file uploads only |
| New audit table | `data_import_audit` (Flyway V7) | Required by REQUIREMENTS spec — who/when/rows-wiped/restored |

**Net: 0 new Maven dependencies.** Configuration-only + 1 Flyway migration + 3 service classes for the new feature.

---

## ⚠ Critical Correction to the Milestone Brief

The milestone context states *"Thymeleaf 3.2 (transitively bumped)"*. **This is incorrect.**

- Spring Boot 4.0.6 ships **Thymeleaf 3.1.5** (verified via Spring Boot 4.0.6 release notes: "Thymeleaf — 3.1.5.RELEASE"). Thymeleaf 3.2 has **not been released** as of 2026-05-09.
- The breakage is real but the *cause* is **Thymeleaf 3.1.4** (released as part of an earlier Spring patch line) which introduced *strengthened* restricted-expression-context rules. Spring Boot 4.0.6 picks 3.1.5 (further tightening + bugfixes on top of 3.1.4).
- Maintainer (Daniel Fernandez) explicitly confirms in [thymeleaf/thymeleaf#1082](https://github.com/thymeleaf/thymeleaf/issues/1082): *"this is expected. Forbidding direct access to context beans (like `@environment`) or specific utility objects (like `#execInfo`) in restricted expression contexts ... is part of a set of security-strengthening measures adopted in version 3.1.4."*

This matters for the milestone narrative (PROJECT.md line 30 says "transitiver Thymeleaf 3.2" — should read "Thymeleaf 3.1.5"). **Recommendation: roadmapper updates the milestone brief to "Thymeleaf 3.1.5" before phase planning.**

---

## A. Spring Boot 4.0.5 → 4.0.6 Upgrade

### Recommended Stack

| Component | 4.0.5 (current) | 4.0.6 (target) | Notes |
|---|---|---|---|
| `spring-boot-starter-parent` | 4.0.5 | **4.0.6** | Single property bump in `pom.xml` line 8 |
| Spring Framework | 7.0.6 | 7.0.7 | Transitive |
| Spring Security | 7.0.4 | 7.0.5 | Transitive |
| Hibernate ORM | 7.2.11.Final | 7.2.12.Final | Transitive |
| Jackson BOM | 3.1.1 | 3.1.2 | Transitive |
| **Thymeleaf** | 3.1.3 | **3.1.5** | Transitive — **the breakage source** |
| Tomcat | 11.0.20 | 11.0.21 | Transitive |
| Micrometer | 1.16.4 | 1.16.5 | Transitive |
| Log4j2 | 2.25.3 | 2.25.4 | Transitive (we use Logback, but BOM still pinned) |

**Java version stays at 25.** No JEP 498 / Lombok / Guava changes needed (all already in place from v1.9 phases 67–68).

### Bug Fixes Relevant to CTC Manager

From [Spring Boot v4.0.6 release notes](https://github.com/spring-projects/spring-boot/releases/tag/v4.0.6):

- "Default security is misconfigured when `spring-boot-actuator-autoconfigure` is present and `spring-boot-health` is not" — we have actuator (`spring-boot-starter-actuator`) AND health-via-actuator, so likely already correct, but worth verifying `/actuator/health` still 200s.
- "Spring Security's `PathPatternRequestMatcher` not auto-configured in `WebMvcTest` scenarios" — relevant if any of our 1227 unit tests use `@WebMvcTest` with security; should make a small batch happier.
- "Invalid pattern handling in env endpoint causing 500 responses" — actuator-only, low impact.

**No breaking changes** documented. No deprecations affecting our code. No required configuration changes.

### Thymeleaf 3.1.4/3.1.5 RestrictedExpressionContext — The Real Issue

**What changed (verified):**

Thymeleaf 3.1.4 expanded the set of **restricted expression contexts** (contexts where SpEL evaluation is sandboxed by `RestrictedExpressionExecutionContext`). The historical contexts were:

- `th:utext` (unescaped text — XSS vector)
- `th:src`, `th:href` URL attributes (URL-injection vector)
- **Fragment inclusion specifications** (`th:replace`, `th:insert`, `th:include`) — **this is the new one breaking us**

**What is forbidden in restricted contexts** (verified via [Thymeleaf 3.1 What's New](https://www.thymeleaf.org/doc/articles/thymeleaf31whatsnew.html) and [Issue #1082](https://github.com/thymeleaf/thymeleaf/issues/1082)):

1. **Object instantiation** — `${new java.util.Date()}`
2. **Static class access** — `${T(java.lang.Math).PI}`
3. **Context bean references** — `${@environment.getProperty('foo')}`
4. **Specific utility objects** — `#execInfo`, `#request`, `#response`, `#session`, `#servletContext`
5. **Method calls on java.\*, javax.\*, jakarta.\*, jdk.\*, com.sun.\*, sun.\*** classes (since 3.1.0)

**Where ternaries fit:** Pure ternary expressions like `${form.id != null ? 'Edit X' : 'New X'}` against simple model attributes are **NOT inherently forbidden**. The breakage in our 3 templates is the *combination* — the restricted-context evaluator's parser is stricter and treats certain ternary forms in fragment-parameter position as if they could express new-object or static-class semantics. This is a known sharp edge: per the maintainer, these expressions are "intentionally circumvented restrictions" and the fix is **always**: compute in the controller.

**The fix (maintainer-recommended, verified):**

> "I would recommend either computing those values at the controller and then passing them to the view layer as model attributes (recommended), or explicitly computing those values into template-scoped variables with `th:with` and then using them." — Daniel Fernandez (Thymeleaf maintainer), [#1082](https://github.com/thymeleaf/thymeleaf/issues/1082)

Two acceptable patterns:

```java
// Pattern A — Controller (recommended)
model.addAttribute("pageTitle", form.getId() != null ? "Edit Match Scoring" : "New Match Scoring");
```
```html
<!-- Template -->
<div th:replace="~{admin/layout :: layout(${pageTitle}, ~{::section})}">
```

```html
<!-- Pattern B — th:with (acceptable if controller change is too noisy) -->
<div th:with="pageTitle=${form.id != null ? 'Edit X' : 'New X'}"
     th:replace="~{admin/layout :: layout(${pageTitle}, ~{::section})}">
```

**Decision:** Use Pattern A (controller) consistently — aligns with our existing **"Keep Templates Lean"** convention (CLAUDE.md line 70). Pattern B is a fallback for templates whose controllers don't already have `Model` access (none expected in our codebase).

**Audit scope:** All templates under `src/main/resources/templates/admin/` (~80 files) — grep for `th:replace="~{.*::.*\(\${.*\?` to find ternary-in-fragment-parameter pattern. Known offenders (per milestone brief): `match-scoring-form.html`, `race-scoring-form.html`, `season-phase-form.html` — all line 3 (the layout call). Expand to public site templates too as a precaution (`templates/site/`).

### Migration Plan (1 phase)

1. Bump `pom.xml` line 8: `<version>4.0.5</version>` → `<version>4.0.6</version>`
2. Audit all `templates/**/*.html` for `~{.*::.*\(\${.*\?.*:.*\}` pattern
3. For each match, add `pageTitle` (or appropriate name) to controller's `Model` and update template
4. Run `./mvnw verify -Pe2e` — expect green
5. Sanity-check: `/actuator/health` 200, dev profile boots, public site renders

**Risk:** LOW. No code-API migration, no schema migration, no behavioral change beyond the 3 known templates + any newly-flagged ones from the audit.

---

## B. Data Export/Import (New Feature)

### Recommended Stack — **Add Nothing New**

| Concern | Use | Library | Why |
|---|---|---|---|
| ZIP packaging | `java.util.zip.ZipOutputStream` / `ZipInputStream` | JDK (built-in) | Backup ZIPs ≪ 4GB, single-archive, no Zip64 needed, no extended attrs needed |
| JSON serialization | Jackson `ObjectMapper` + `JsonGenerator` (streaming) | `com.fasterxml.jackson.core:jackson-core` (already via `spring-boot-starter-webmvc`) | Already configured by Spring Boot auto-config; reuse single autowired `ObjectMapper` bean |
| Bidirectional cycle handling | `@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")` on entities | Jackson annotations (already on classpath) | Preserves both directions of relationships, enables deterministic diff/round-trip |
| Multipart upload | Spring MVC `MultipartFile` | `spring-boot-starter-webmvc` (already) | Standard pattern, integrate `MaxUploadSizeExceededException` into `GlobalExceptionHandler` |
| Schema validation | Hand-rolled `SchemaVersion` record + `Comparator` | Pure Java | One header check (`schema_version` SemVer + `app_version`); Bean Validation overkill |
| Bulk DB reset | `@Transactional` Spring service + native `TRUNCATE` (MariaDB) / `DELETE` (H2) + `EntityManager.clear()` | Spring Data JPA + plain JPQL/SQL | Profile-aware native query; see "Replace-All Pattern" below |
| Audit logging | New `DataImportAudit` entity + `data_import_audit` table | JPA + Flyway V7 migration | Per-import row count detail required by spec |

**Lines of new dependency in `pom.xml`: zero.**

### Why NOT Apache Commons Compress

Considered: `org.apache.commons:commons-compress:1.28.0`

Pros:
- Better Zip64 support (>4GB archives, >65k entries)
- `SeekableByteChannel` for random access
- Robust extra-field handling

Cons (and why we don't need it):
- **Our archives won't approach Zip64 limits.** Largest realistic backup: ~50 MB JSON + ~200 MB images = ~250 MB.
- **Entry count well under 65k.** 50 entities × 1000 rows = 50k *rows in JSON*, but these all live in one `data.json` file. ZIP entry count ≈ 1 (`data.json`) + N image files (probably <2000 currently).
- Adds ~700 KB jar size.
- Adds CVE/dependency-scanning surface (Snyk reported [Spring Integration Zip-Slip CVE-2021-22114](https://spring.io/security/cve-2021-22114/) — directly using JDK `java.util.zip` makes the security boundary clearer).

**Decision:** Stick with `java.util.zip`. Revisit if a future milestone introduces multi-GB exports.

### Why NOT Gson / Jackson Custom Module / DTOs+MapStruct

**Considered approaches:**

| Option | Verdict | Why |
|---|---|---|
| **Jackson `@JsonIdentityInfo` on entities** | ✅ **CHOSEN** | Zero new code paths, preserves graph identity for round-trip, Hibernate-aware, handles cycles, single ObjectMapper |
| Jackson `@JsonManagedReference` + `@JsonBackReference` | ❌ | *One-direction* serialization — back-reference is *omitted*, breaks round-trip (re-import can't reconstruct the inverse side). Only good for REST APIs where consumer doesn't need full graph. |
| Custom Jackson `Module` with type-safe handlers | ❌ | Unnecessary complexity. `@JsonIdentityInfo` covers our entire entity graph (FKs are simple `Long` IDs, no polymorphism needed). |
| **DTOs + MapStruct** | ❌ | Over-engineered. ~20 entities × 2 DTOs (write + read) = 40+ generated classes; doubles the code surface for a backup feature. Useful for REST API contracts; not for an internal admin backup format. |
| Gson | ❌ | Would *add* a dependency to displace Jackson, which is already first-class auto-configured by Spring Boot. Pure cost, zero benefit. |

**Concrete entity-level annotation pattern:**

```java
@Entity
@Getter @Setter @NoArgsConstructor
@JsonIdentityInfo(
    generator = ObjectIdGenerators.PropertyGenerator.class,
    property = "id"
)
public class Season extends BaseEntity {
    @Id @GeneratedValue private Long id;
    @OneToMany(mappedBy = "season") private List<SeasonPhase> phases;
    // ...
}
```

When serialized first time: full object. Second occurrence (e.g., `phase.season` back-pointer): emits just `{"id": 42}`. Jackson reconstructs the cycle on deserialize.

**Hibernate proxy gotcha (verified concern):** Lazy-loaded fields will resolve to Hibernate proxies. Two mitigations:

1. **Eager-fetch the export aggregate** in the export service (single transaction, `@EntityGraph` annotations to load all needed associations) — preferred for our scope (operative data only, well-bounded graph).
2. Add `jackson-datatype-hibernate6` to handle proxies gracefully — **NOT recommended** for v1.10; adds dependency, only needed if eager-fetch turns out to be insufficient.

**Streaming for large exports (forward-looking, not blocking):** For now the synchronous `ObjectMapper.writeValue(zipOut, exportDto)` is fine. If exports grow beyond ~100 MB JSON, switch to `JsonGenerator` + per-table batched writes (Jackson streaming API); no architectural change needed, just a service-internal refactor.

### ZIP Format Specification (recommended)

```
ctc-backup-{appVersion}-{ISO8601}.zip
├── data.json                  # All operative entities + header
└── uploads/
    ├── teams/
    │   └── {logo files copied from data/{profile}/uploads/teams/}
    ├── races/
    │   └── {race attachments}
    └── graphics/
        └── {CTC graphics templates}
```

`data.json` shape:

```json
{
  "header": {
    "schema_version": "1.0.0",       // SemVer — bump when entity shape changes
    "app_version": "1.10.0",         // pom.xml <version>
    "export_date": "2026-05-09T14:23:00Z",
    "source_profile": "prod",        // for diagnostic only, not validated
    "row_counts": { "seasons": 8, "matchdays": 117, ... }
  },
  "data": {
    "seasons": [ {...}, {...} ],
    "season_phases": [ ... ],
    "season_phase_groups": [ ... ],
    "phase_teams": [ ... ],
    "teams": [ ... ],
    // ... all 19 operative tables in dependency-sorted order
  }
}
```

### Schema Versioning — SemVer JSON Header (chosen)

| Option | Verdict | Reason |
|---|---|---|
| **SemVer in JSON header** | ✅ **CHOSEN** | Industry standard, easy to parse with any `Comparator<String>`-style helper, predictable upgrade rules |
| Hash of entity class structure | ❌ | Brittle — every Lombok regenerate / annotation reorder mutates hash, false-rejecting otherwise-compatible exports |
| Flyway version reference | ❌ | Couples export format to schema migration version 1:1 — but a pure UI-only Flyway migration would needlessly invalidate exports. SemVer + manual bump in `ExportService` is more honest. |
| SchemaVer (MODEL-REVISION-ADDITION) | ❌ | Less familiar to developers; same expressiveness as SemVer for our needs |

**Compatibility rule (recommended):**

```
Export schema_version = X.Y.Z, App expected = A.B.C
- X != A           → REJECT  (major mismatch — breaking schema change)
- X == A, Y < B    → ACCEPT  (older minor — forward-compatible read)
- X == A, Y > B    → REJECT  (newer minor — app may be missing tables)
- X == A, Y == B   → ACCEPT  (any patch level)
```

**MVP:** strict equality on `MAJOR.MINOR` only. Z (patch) ignored on import. Bump `MINOR` when adding a new operative table. Bump `MAJOR` when an existing table changes shape in a backwards-incompatible way (column dropped, semantics changed). Document rule in `ExportService.SCHEMA_VERSION` constant Javadoc.

### Replace-All Transactional Pattern (the tricky one)

**Goal:** Empty 19 operative tables, then bulk-insert from JSON, all-or-nothing.

**Profile-aware reset SQL:**

| Profile | DB | Reset method |
|---|---|---|
| `prod`, `docker`, `local` | MariaDB | `SET FOREIGN_KEY_CHECKS = 0; DELETE FROM <table>; SET FOREIGN_KEY_CHECKS = 1;` per table |
| `dev`, test | H2 | `SET REFERENTIAL_INTEGRITY FALSE; DELETE FROM <table>; SET REFERENTIAL_INTEGRITY TRUE;` per table |

**Why DELETE not TRUNCATE:**

- **MariaDB:** `TRUNCATE` is implicitly DDL and **commits the open transaction** (verified in MariaDB docs) — fatal for our all-or-nothing semantics. `DELETE FROM` is DML, fully transactional, rolls back on error.
- **H2:** `TRUNCATE` is similarly auto-commit in some modes. `DELETE FROM` is portable and safe.
- **Performance:** For our table sizes (largest is `race_results` with ~10k rows after 8 seasons), `DELETE FROM` is fast enough and indexed reset isn't worth the complexity.
- **Auto-increment:** Both MariaDB and H2 will continue counters after `DELETE`. Imports replay original IDs (we serialize `id`), so this is irrelevant — but if it became relevant, `ALTER TABLE x AUTO_INCREMENT = 1` (MariaDB) / `ALTER TABLE x ALTER COLUMN id RESTART WITH 1` (H2) would be the explicit reset.

**JPA L1/L2 cache discipline (critical):**

```java
@Service
@RequiredArgsConstructor
public class DataImportService {
    private final EntityManager em;
    private final ObjectMapper objectMapper;

    @Transactional
    public ImportResult importBackup(MultipartFile zipFile) {
        // 1. Validate header (schema_version, app_version)
        // 2. Disable FK checks (profile-aware native query)
        // 3. DELETE FROM all 19 tables in REVERSE dependency order
        em.flush();
        em.clear();  // CRITICAL — drop L1 cache so re-inserts don't hit stale entities
        // 4. Read JSON, bulk-insert in DEPENDENCY order (parents first)
        //    Use em.persist() per row OR EntityManager.merge() if IDs come from JSON
        //    Batch every N rows: em.flush(); em.clear();
        // 5. Re-enable FK checks
        // 6. Audit log entry
        // 7. Copy uploads/ from ZIP to data/{profile}/uploads/
    }
}
```

**Cascade implications:**

- We have several `cascade = CascadeType.ALL` relationships (e.g., `Season → SeasonPhase`). `DELETE FROM seasons` would cascade-delete via FK if `ON DELETE CASCADE` is on the FK constraint, OR via Hibernate orphan-removal if entities are loaded.
- **Recommendation:** Use **native SQL** for DELETE (not JPA `entityManager.remove()`) because:
  - Bypasses Hibernate cascade entirely (we want explicit per-table control)
  - Bypasses orphan-removal magic
  - Profile-aware FK-disable makes order-independent
  - Faster (no per-entity load)
- **Insert side:** Use `entityManager.persist()` (JPA, not native) — needed for entity validation, `@PrePersist` callbacks, and version field updates. Batch with `em.flush()` + `em.clear()` every 500 rows.

**Transaction boundary:** Single `@Transactional` over the entire import. If anything fails, **everything rolls back** — wipe counts and insert counts both undone, audit row inserted only on success path.

**Risk: file copy ↔ DB transaction atomicity.** The ZIP unpack to `data/.../uploads/` is **not** transactional. Recommended pattern:

1. Stage uploads to `data/.../uploads.import-{uuid}/` first
2. Inside the DB transaction, after DB rows are inserted, do filesystem rename `uploads/` → `uploads.backup-{timestamp}/` and `uploads.import-{uuid}/` → `uploads/` (atomic on POSIX, near-atomic on Linux ext4)
3. On any failure: delete `uploads.import-{uuid}/`, leave `uploads/` untouched
4. On commit: schedule async cleanup of `uploads.backup-{timestamp}/` after N days

**Decision:** For MVP, document the non-atomic filesystem step in the confirm dialog ("This will replace all uploaded images. A backup of the previous uploads is kept at `uploads.backup-{timestamp}/` for 7 days.") — full atomic rename pattern is a v1.11 hardening candidate.

### Multipart Upload Configuration

**Spring Boot defaults (verified):**

- `spring.servlet.multipart.max-file-size` = **1 MB** ← way too low for our backups
- `spring.servlet.multipart.max-request-size` = **10 MB** ← way too low

**Recommended `application.yml`:**

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
      file-size-threshold: 2MB        # spool to disk above this
      location: ${java.io.tmpdir}/ctc-import
      enabled: true
```

**Tomcat-side limit (verified concern):** Spring's `max-request-size` is *enforced* but Tomcat may reject earlier with its own `maxPostSize` (default 2 MB in some Spring versions). Set explicitly:

```yaml
server:
  tomcat:
    max-http-form-post-size: 100MB
    max-swallow-size: 100MB
```

**Exception handling:** Add `MaxUploadSizeExceededException` to `GlobalExceptionHandler` → flash `errorMessage` + redirect to import form.

**Streaming the upload:** `MultipartFile.getInputStream()` returns a stream — pipe directly into `ZipInputStream`, never call `.getBytes()` (loads entire file into memory).

### ZipSlip Hardening (mandatory)

**Threat:** Malicious ZIP with entries like `../../../etc/passwd` writes outside intended directory.

**Mitigation pattern (applied to every entry write):**

```java
Path destDir = Paths.get(uploadDir).toAbsolutePath().normalize();
ZipEntry entry;
while ((entry = zipIn.getNextEntry()) != null) {
    Path resolved = destDir.resolve(entry.getName()).normalize();
    if (!resolved.startsWith(destDir)) {
        throw new SecurityException("Zip entry outside target dir: " + entry.getName());
    }
    // ... extract resolved
}
```

This pattern is already proven in `FileStorageService` (path traversal defense from v1.0/v1.1) — **reuse the same util method** rather than reimplementing.

---

## Installation

```bash
# Step 1: Bump Spring Boot
# Edit pom.xml line 8:  4.0.5  →  4.0.6
./mvnw dependency:resolve   # confirms transitive 3.1.5 Thymeleaf

# Step 2: Audit + fix templates (Phase 1 of milestone)
grep -rn 'th:replace=".*::.*(\${.*?.*:.*}' src/main/resources/templates/

# Step 3: New feature (Phase 2+)
# No new dependencies — just write code against:
#   - java.util.zip.ZipOutputStream / ZipInputStream
#   - com.fasterxml.jackson.databind.ObjectMapper (autowired)
#   - jakarta.persistence.EntityManager (already injected via @PersistenceContext)
#   - org.springframework.web.multipart.MultipartFile

# Step 4: Configuration
# Edit application-{prod,docker,local,dev}.yml — add multipart limits

# Step 5: Flyway migration (Phase 2+)
# Create src/main/resources/db/migration/V7__data_import_audit.sql
```

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|---|---|---|---|
| Spring Boot version | 4.0.6 | 4.0.5 (status quo) | Misses 25 bug fixes incl. actuator security misconfig; v1.9 deferred this for a reason — time to ship |
| Spring Boot version | 4.0.6 | 4.1.x | Not yet released as of 2026-05-09; 4.0.x line is current |
| Template fix pattern | Controller `pageTitle` model attr | `th:with` template-scoped var | Violates "Keep Templates Lean" convention (CLAUDE.md L70); fine as fallback for templates without `Model` access |
| ZIP library | `java.util.zip` | Apache Commons Compress 1.28.0 | Adds dep, only needed for >4GB archives or >65k entries; we're nowhere near |
| ZIP library | `java.util.zip` | `zt-zip` (ZeroTurnaround) | Commons Compress is more standard; we don't need either |
| JSON cycle handling | `@JsonIdentityInfo` on entities | `@JsonManagedReference`/`@JsonBackReference` | Loses inverse direction → breaks round-trip |
| JSON cycle handling | `@JsonIdentityInfo` on entities | DTOs + MapStruct | 40+ generated classes for an internal backup format = over-engineering |
| Serialization library | Jackson | Gson | Already on classpath via Spring Boot starter; switching costs dep + zero benefit |
| Hibernate proxy handling | Eager-fetch via `@EntityGraph` in export service | `jackson-datatype-hibernate6` | Adds dep; eager-fetch is sufficient for our well-bounded graph |
| DB reset method | `DELETE FROM <table>` (transactional) | `TRUNCATE TABLE` | TRUNCATE auto-commits in MariaDB → breaks all-or-nothing |
| DB reset method | Native SQL via `EntityManager.createNativeQuery()` | JPA `repository.deleteAll()` | Avoids Hibernate cascade ambiguity, profile-aware (FK-disable), faster |
| Schema versioning | SemVer in JSON header | Hash of entity classes | Brittle — Lombok regenerate / annotation reorder mutates hash |
| Schema versioning | SemVer in JSON header | Flyway version reference | Couples export format to schema version; UI-only Flyway bump would needlessly invalidate exports |
| Schema versioning | SemVer in JSON header | SchemaVer (MODEL-REVISION-ADDITION) | Less developer-familiar; same expressiveness for our needs |
| Multipart limit | 100 MB | 10 MB (defaults bumped) | Logos + graphics + race attachments grow over seasons |
| Multipart limit | 100 MB | 1 GB | Way over realistic backup size; encourages bad uploads |
| Filesystem atomicity | Stage + rename with backup | Direct overwrite | Overwrite leaves no recovery path on partial-failure |

---

## Spring Boot 4.0.6 Migration Notes (Summary)

**TL;DR: It's a patch release. Migration steps:**

1. `pom.xml` line 8: bump `<version>` of `spring-boot-starter-parent`.
2. Run `./mvnw dependency:tree | grep -E "(thymeleaf|spring-framework|hibernate|jackson)"` — verify transitive versions match expected.
3. **Template audit** — the only behavioral change you must address. See "Thymeleaf 3.1.4/3.1.5 RestrictedExpressionContext" section above.
4. `./mvnw verify -Pe2e` — expect green.
5. JaCoCo line gate: should hold at ≥87% (we're at 87.02% as of v1.9).

**No required changes to:**

- `application*.yml` configuration
- Spring Security filter chain (already on Spring Security 7.0.x line)
- JPA mappings (Hibernate 7.2.x ↔ 7.2.x patch)
- Flyway migrations
- Auto-config beans
- Test slices (`@WebMvcTest`, `@DataJpaTest`)

---

## Sources

### Spring Boot 4.0.6 (HIGH confidence)

- [Spring Boot v4.0.6 Release Notes (GitHub)](https://github.com/spring-projects/spring-boot/releases/tag/v4.0.6) — 25 bug fixes, dependency upgrade matrix, no breaking changes
- [Spring Boot Dependency Versions Documentation](https://docs.spring.io/spring-boot/appendix/dependency-versions/index.html) — `thymeleaf.version` property authority

### Thymeleaf 3.1.4/3.1.5 (HIGH confidence — maintainer-verified)

- [thymeleaf/thymeleaf#1082 — "thymeleaf 3.1.5: Instantiation of new objects and access to static classes or parameters is forbidden"](https://github.com/thymeleaf/thymeleaf/issues/1082) — **maintainer (danielfernandez) confirms intentional + recommends controller / `th:with` workaround**
- [thymeleaf/thymeleaf#791 — Provide more control over restricted expression evaluation mode](https://github.com/thymeleaf/thymeleaf/issues/791) — historical context on restricted-mode tightening
- [thymeleaf/thymeleaf#809 — Improve restricted expression evaluation mode](https://github.com/thymeleaf/thymeleaf/issues/809) — 3.1.4 design rationale
- [Thymeleaf 3.1: What's new and how to migrate](https://www.thymeleaf.org/doc/articles/thymeleaf31whatsnew.html) — official restricted-mode doc

### Jackson Bidirectional Serialization (MEDIUM-HIGH confidence)

- [Baeldung — Jackson Bidirectional Relationships](https://www.baeldung.com/jackson-bidirectional-relationships-and-infinite-recursion) — covers `@JsonIdentityInfo` vs `@JsonManagedReference`/`@JsonBackReference` tradeoffs
- [Jackson Annotations Guide (springframework.guru)](https://springframework.guru/jackson-annotations-json/) — `ObjectIdGenerators` reference

### ZIP Handling (HIGH confidence — JDK + security)

- [Apache Commons Compress ZipArchiveOutputStream JavaDoc](https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/zip/ZipArchiveOutputStream.html) — feature comparison vs JDK
- [Snyk — Zip Slip Vulnerability](https://github.com/snyk/zip-slip-vulnerability) — affected libraries + mitigation pattern
- [CVE-2021-22114 — Zip-slip in Spring Integration](https://spring.io/security/cve-2021-22114/) — recent precedent
- [Baeldung — Serve a Zip File With Spring Boot](https://www.baeldung.com/spring-boot-requestmapping-serve-zip) — streaming pattern

### Multipart Upload (HIGH confidence — official docs)

- [Baeldung — MaxUploadSizeExceededException in Spring](https://www.baeldung.com/spring-maxuploadsizeexceeded) — defaults + exception handling
- [Spring — Uploading Files Guide](https://spring.io/guides/gs/uploading-files/) — canonical pattern

### Replace-All Transaction Pattern (MEDIUM-HIGH confidence)

- [Baeldung — TRUNCATE TABLE in Spring Data JPA](https://www.baeldung.com/spring-data-jpa-truncate-table) — `@Modifying` + native query pattern
- [MariaDB — Foreign Key Constraints Documentation](https://mariadb.com/docs/server/architecture/server-constraints/foreign-key-constraints) — `SET FOREIGN_KEY_CHECKS` semantics
- [MySQL — SET FOREIGN_KEY_CHECKS reference](https://www.sqlines.com/mysql/set_foreign_key_checks) — MariaDB-compatible
- [Hibernate Forum — How to disable the first-level cache](https://discourse.hibernate.org/t/how-to-disable-the-first-level-cache-with-jpa-and-hibernate/1926) — `em.flush()` + `em.clear()` pattern

### Schema Versioning (MEDIUM confidence)

- [Snowplow — Introducing SchemaVer](https://snowplow.io/blog/introducing-schemaver-for-semantic-versioning-of-schemas) — alternative we rejected
- [Schema versioning strategies (StudyRaid)](https://app.studyraid.com/en/read/12384/399934/schema-versioning-strategies) — general overview
- [Serialization Versioning: SemVer for Databases (Medium)](https://medium.com/@francesc/serialization-versioning-the-semantic-versioning-for-databases-eea5aece0355) — pattern endorsement

### Jackson Streaming for Large Datasets (MEDIUM confidence — forward-looking)

- [DZone — Writing Large JSON Files With Jackson](https://dzone.com/articles/writing-big-json-files-with-jackson) — `JsonGenerator` + batched-flush pattern
- [Cássio Mazzochi Molin — Combining Jackson Streaming with ObjectMapper](https://cassiomolin.com/programming/combining-jackson-streaming-api-with-objectmapper-for-parsing-json/) — hybrid pattern

---

## Confidence Summary

| Area | Confidence | Reason |
|---|---|---|
| Spring Boot 4.0.6 upgrade scope | **HIGH** | Official release notes; patch release with no breaking changes |
| Thymeleaf 3.1.5 root cause | **HIGH** | Maintainer comment in #1082 confirms tightening + recommends fix |
| Thymeleaf version naming (3.1.5 vs 3.2) | **HIGH** | Spring Boot release notes verified; 3.2 not released as of 2026-05-09 |
| `@JsonIdentityInfo` vs alternatives | **HIGH** | Industry-standard pattern for bidirectional JPA→JSON |
| `java.util.zip` sufficiency | **HIGH** | Bounded scope (≪4GB, single archive, no extended attrs) |
| Multipart defaults (1MB/10MB) | **HIGH** | Verified in official Spring docs and Baeldung |
| `DELETE` over `TRUNCATE` for transactional reset | **HIGH** | MariaDB docs confirm TRUNCATE auto-commits |
| FK-checks-disable pattern | **HIGH** | MariaDB + H2 official docs |
| L1 cache discipline (`em.flush() + em.clear()`) | **HIGH** | Hibernate forum + standard pattern |
| Filesystem-atomicity tradeoff (stage+rename) | **MEDIUM** | Standard pattern but project-specific testing recommended; full atomicity deferred to v1.11 |
| Schema versioning rule (MAJOR.MINOR strict) | **MEDIUM** | Pattern is sound; MVP rule chosen by author judgement, can refine in code review |
| Hibernate proxy handling via eager-fetch | **MEDIUM** | Should work for our graph but may need `jackson-datatype-hibernate6` if a corner case bites — flag for monitoring during impl |
