# Pitfalls Research

**Domain:** Spring Boot 4.0.5 → 4.0.6 platform upgrade + structural ZIP-based Export/Import for an existing JPA/Thymeleaf admin app (CTC Manager)
**Researched:** 2026-05-09
**Confidence:** HIGH for upgrade pitfalls (cross-checked Spring Boot release notes + CVE-2026-40478 advisories), HIGH for ZIP/JPA pitfalls (well-trodden territory), MEDIUM for Hibernate-7-specific defaults (sparse 4.0.6-specific public material as of cutoff)

---

## Reading Note: Thymeleaf Version Mismatch in Milestone Context

The milestone description references "transitiver Thymeleaf 3.2." Verified against the [Spring Boot 4.0.6 release announcement](https://spring.io/blog/2026/04/23/spring-boot-4-0-6-available-now/) and the GitHub release page: **Spring Boot 4.0.6 ships with Thymeleaf 3.1.5.RELEASE**, not 3.2. There is no Thymeleaf 3.2 GA release as of the research date.

The v1.9 template breakage was almost certainly caused by **CVE-2026-40478 (Thymeleaf 3.1.4 / 3.1.5 SpEL canonicalization fix)** — `ExpressionUtils.normalize()` now rejects keywords like `new` and `T(` regardless of the whitespace separator, and the new `containsExternalAccess()` check is far stricter about what is allowed inside fragment-parameter expressions evaluated in restricted mode. Ternary expressions are not banned per se, but the entire fragment-parameter expression now goes through the harder filter, and any sub-expression that even *looks* like external access (object construction, type reference, certain `#`-utility access patterns) is rejected.

This document treats the upgrade as **3.1.x → 3.1.5** (i.e., the CVE-fix bump). All `Thymeleaf 3.2` references in the original question are mapped to `Thymeleaf 3.1.5` here.

Sources:
- [Spring Boot 4.0.6 available now](https://spring.io/blog/2026/04/23/spring-boot-4-0-6-available-now/)
- [Endor Labs — How a whitespace character broke Thymeleaf's expression sandbox (CVE-2026-40478)](https://www.endorlabs.com/learn/its-about-thyme-how-a-whitespace-character-broke-thymeleafs-expression-sandbox-cve-2026-40478)
- [GitLab Advisory — CVE-2026-40478](https://advisories.gitlab.com/maven/org.thymeleaf/thymeleaf/CVE-2026-40478/)

---

## Critical Pitfalls

### Pitfall 1: Thymeleaf 3.1.5 fragment-parameter restricted-mode regression beyond the 3 known templates

**What goes wrong:**
The known v1.9 breakage is `th:replace="...layout(${form.id != null ? 'A' : 'B'}, ...)"` in 3 templates. After the CVE-2026-40478 hardening, **any** fragment-parameter expression that the new `containsExternalAccess()` filter dislikes (object construction, type references, certain method chains on objects whose class is not on the allow-list) raises `TemplateProcessingException` at *render* time, not at startup. Coverage is partial because not every template renders in a unit test — only the routes hit by `MockMvc` integration tests + the 31 Playwright E2E checks render Thymeleaf. Templates exercised only in dev (e.g., admin-only edit forms behind specific filter combinations) will silently break in production.

**Why it happens:**
- The fix changed the filter from "only ASCII space after `new`" to whitespace-canonicalized + broader keyword set.
- Restricted-mode applies to fragment parameters specifically (`th:replace`/`th:insert` `(...)` arguments) — not to body expressions — so audits that grep `th:text` find nothing.
- Coverage assumes Thymeleaf compile-time validation, but Thymeleaf templates are compiled lazily on first render.

**How to avoid:**
- **Mechanical audit (Phase ~1 of milestone):** Grep all 80 templates for the pattern `th:(replace|insert|include)=".*\(.*\$\{.*\}.*\)"` — every parameterized fragment expression is in the danger zone, not just ternaries.
- **Move computation into the controller, not the template.** The known-breakage fix pattern should be: controller writes `model.addAttribute("layoutVariant", form.getId() != null ? "A" : "B")` and the template uses a plain variable reference: `th:replace="...layout(${layoutVariant}, ...)"`. Plain variable references in fragment parameters remain allowed.
- **Force every admin route through MockMvc once** in a `TemplateRenderingSmokeIT` that just GETs each `/admin/**` URL and asserts 200 (or 3xx for redirects) and a non-null body. This catches lazy-render breakage at CI time. Pair with a `DEV_DATA_SEEDED=true` precondition so optional pages (e.g., season-detail with a phase) are reachable.
- **Pin the `org.thymeleaf:thymeleaf` version once** in `<dependencyManagement>` after the upgrade so that a future patch can't silently regress this. Currently no override.

**Warning signs:**
- New 500 errors in `/admin/error` flash with `org.thymeleaf.exceptions.TemplateProcessingException: Restricted expression`.
- Test suite passes, but a manual click on a less-used admin page throws.
- Logs contain `containsExternalAccess` or `RestrictedExpressionEvaluator` in the stack trace.

**Phase to address:** **Earliest phase** of the milestone — before the Export/Import work even starts. Upgrade is a prerequisite. Template audit should be the first plan, with a CI smoke-render as the gate.

---

### Pitfall 2: Spring Security 7 CSRF-on-by-default does not bite us — but the new Import endpoint must be designed assuming CSRF is enforced

**What goes wrong:**
Spring Security 7 (transitively bundled in SB 4.0.6) flips CSRF protection to **on by default**. The CTC Manager already has `csrf().disable()` globally (per CLAUDE.md and the v1.0/v1.5 decisions table — "CSRF disabled globally, tokens on AJAX"), so the upgrade itself does not break existing endpoints. **However:** the import endpoint is a `multipart/form-data` POST with a destructive side effect (Replace-All wipe). If a future security-hardening pass re-enables CSRF, multipart is a known footgun: the standard CSRF filter runs **before** Spring's `MultipartFilter` parses the body, so the CSRF token (if placed inside the multipart body rather than a header or form field outside the file part) is invisible at the time the filter checks. The recommended fix per Spring Security docs is to register `MultipartFilter` *before* the security filter chain, but doing so means the multipart parser runs unauthenticated, exposing temp-file disk usage to anyone who can reach the URL.

**Why it happens:**
- CSRF token convention: hidden form field. With `enctype="multipart/form-data"`, the token is buried inside the body, and the security filter cannot read it without consuming the stream.
- Re-enabling CSRF is on the v1.10+ tech-debt radar implicitly because the codebase already has CSRF on AJAX POSTs — the import form is a non-AJAX `<form>` POST, so it's the odd one out.

**How to avoid:**
- **For v1.10:** Send the CSRF token as a **request header** `X-CSRF-TOKEN` from the import form's submit-handler JavaScript (matches the pattern from v1.0/v1.5 AJAX POSTs). Add the token via `<meta name="_csrf"...>` and a small fetch wrapper. Even though CSRF is currently disabled globally, this future-proofs the endpoint for whenever CSRF gets re-enabled.
- **Never** put a multipart endpoint that wipes data on a route reachable by GET or by a CSRF-exempt POST.
- The Replace-All endpoint should require a **two-step confirmation** (preview → confirm with a one-time `confirmToken` from the preview response). This is independent of CSRF and provides defense in depth against accidental clicks and CSRF-bypass attempts.

**Warning signs:**
- A future PR re-enables CSRF globally → Import upload starts returning 403 silently.
- A penetration test reveals that Replace-All can be triggered via a CSRF link if CSRF is ever re-enabled but the multipart filter ordering is wrong.

**Phase to address:** **During Import endpoint design** — bake the header-based CSRF token + two-step confirmation into the controller from day one. Avoid the cost of retrofitting later.

Sources:
- [Spring Security CSRF docs — Multipart section](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Java Code Geeks — Spring Boot 4 Migration: Breaking Changes, New Defaults](https://www.javacodegeeks.com/2026/05/spring-boot-4-migration-breaking-changes-new-defaultsand-what-actually-broke.html)

---

### Pitfall 3: Hibernate 7 lazy-loading proxy serialization — `LazyInitializationException` and `hibernateLazyInitializer` JSON pollution

**What goes wrong:**
The CTC Manager has OSIV enabled (`spring.jpa.open-in-view=true`) so Thymeleaf can lazily-load fields during render. When the **Export service** serializes ~20 entities to `data.json`, two failure modes appear:

1. **LazyInitializationException** — if serialization happens **after** the request transaction has closed (e.g., inside a `StreamingResponseBody`, an `@Async` task, or a `@Scheduled` job), OSIV's session is gone. Lazy collections (`Season.matchdays`, `Matchday.matches`, etc.) explode on first touch.
2. **`{"hibernateLazyInitializer": {...}, "handler": {...}}` pollution** — when Jackson encounters an uninitialized proxy and is **not** configured to handle Hibernate types, it serializes the proxy's internal fields rather than the entity's fields. The `data.json` becomes unparseable on import (or worse: parseable but semantically wrong).
3. **Infinite recursion** — bidirectional relationships (`Season ↔ Matchday`, `Matchday ↔ Match`, `Match ↔ Race`, `Team ↔ SeasonDriver`, `Driver ↔ PsnAlias`) cause `StackOverflowError` without `@JsonIdentityInfo` or `@JsonBackReference`/`@JsonManagedReference`.

**Why it happens:**
- OSIV ≠ "always serializable." OSIV closes the session at request boundary; any work done after `View.render()` returns is outside the session.
- Jackson by default serializes all public getters, including the `getHibernateLazyInitializer()` getter that Hibernate's bytecode-enhanced proxies expose.
- `@ToString(exclude = ...)` masks circular refs in logs, but Jackson does not honor `@ToString.Exclude` — separate annotation needed.

**How to avoid:**
- **Use a DTO layer for export, never serialize entities directly.** Define `ExportPackageDto` containing one record per entity table (`SeasonExport`, `MatchdayExport`, etc.) with primitive types + UUID FK references. This sidesteps proxies, lazy loading, and circular refs entirely. Cost: ~20 small records, but they double as the schema-version contract.
- If a DTO layer is genuinely too expensive (the answer is no — the milestone is about making this easy long-term), the fallback is `com.fasterxml.jackson.datatype:jackson-datatype-hibernate6` + `@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")` on every involved entity. The hibernate6 module exists for Hibernate 6/7 (jackson-datatype-hibernate is at the time of research updated for Jackson 3.x in the SB 4 line — verify it pulls in cleanly with the SB 4.0.6 BOM before relying on it). **DTO is strongly preferred** because it survives schema refactoring and entity reshape.
- **Force-initialize within a transaction.** The export service method must be `@Transactional(readOnly = true)` and must **eagerly fetch** every association it needs via `JOIN FETCH` queries or `@EntityGraph` (the codebase already has 28 `@EntityGraph` annotations — extend the pattern). Streaming the response body is fine if the transaction wraps the streaming write, but the cleanest design is: load → close transaction → emit JSON from in-memory DTOs.
- **Guard against LazyInit explicitly in tests:** export integration test must run with `@Transactional(propagation = NOT_SUPPORTED)` to *force* the no-OSIV scenario.

**Warning signs:**
- `data.json` contains `"hibernateLazyInitializer"` or `"handler"` fields → wrong serialization config.
- `LazyInitializationException: could not initialize proxy` in test logs → missing transaction or fetch.
- Round-trip test (export → import) fails with NPE on a child collection → child was never serialized because parent's proxy short-circuited.
- File size 100x expected → infinite recursion before stack overflow kills it.

**Phase to address:** **DTO-layer phase** — defines the wire format and double-checks against Pitfall 5 (schema versioning). Must be done before any export logic.

Sources:
- [Baeldung — Jackson Bidirectional Relationships](https://www.baeldung.com/jackson-bidirectional-relationships-and-infinite-recursion)
- [FasterXML/jackson-datatype-hibernate](https://github.com/FasterXML/jackson-datatype-hibernate)
- [Mastering Hibernate 7: Proxies and LazyInitializationException](https://ankurm.com/mastering-hibernate-7-proxies-and-the-lazyinitializationexception/)

---

### Pitfall 4: Replace-All transaction strategy on MariaDB vs H2 — TRUNCATE behavior diverges, FK constraint handling diverges

**What goes wrong:**
The "wipe operative tables in a transaction, then restore" plan reads like one SQL statement, but it has three crucial database-engine-specific quirks:

1. **MariaDB TRUNCATE vs FK constraints:** MariaDB refuses TRUNCATE on a table that is referenced by FOREIGN KEYs from other tables, *even if the referencing tables are also being truncated in the same transaction*. The workaround is `SET FOREIGN_KEY_CHECKS=0` for the session, then TRUNCATE, then `SET FOREIGN_KEY_CHECKS=1`. **TRUNCATE in MariaDB also issues an implicit COMMIT** — meaning you cannot wrap TRUNCATE in a JPA transaction at all. If your import fails after TRUNCATE, the data is **gone** with no rollback.
2. **H2 syntax difference:** H2 uses `SET REFERENTIAL_INTEGRITY FALSE` instead of `SET FOREIGN_KEY_CHECKS=0`. The same Java code cannot run on both.
3. **DELETE vs TRUNCATE:** `DELETE FROM` honors FK constraints (CASCADE / RESTRICT) and is transactional on both engines. It is **slower** for big tables but recovers gracefully. TRUNCATE is faster but breaks atomicity.

**Why it happens:**
- TRUNCATE looks atomic but is DDL-flavored on most engines.
- The `cascade = CascadeType.ALL` + `orphanRemoval = true` configuration on JPA collections (per CONVENTIONS.md `Season.matchdays`) means JPA-level deletes via `repository.deleteAll()` cascade through the object graph in a way that does not match raw `DELETE FROM seasons` — the JPA path is safer but slow and orders matter.
- Tests run on H2 (transactional), production runs on MariaDB. Bugs that manifest only on MariaDB are caught only by the MariaDB Smoke workflow / live UAT.

**How to avoid:**
- **Use `@Transactional` + `EntityManager.createNativeQuery("DELETE FROM ...").executeUpdate()` in dependency-reverse order**, not TRUNCATE. Order: `race_results`, `race_lineups`, `playoff_seeds`, `playoff_matchups`, `playoffs`, `races`, `matches`, `matchdays`, `phase_teams`, `season_phase_groups`, `season_phases`, `season_drivers`, `psn_alias`, `season_teams`, `seasons`, `drivers`, `teams`. (Validate the topological order against the v1.9 FK graph — there are 36 FK indexes per PROJECT.md.)
- Wrap the entire wipe + restore in **one** `@Transactional(propagation = REQUIRES_NEW)` boundary. If insertion fails, JPA rolls back and the database is restored to pre-import state. **Cost:** the rollback log has to hold all the deleted rows; for a 100MB DB, MariaDB may need increased `innodb_buffer_pool_size` and `max_allowed_packet`. Document this as a constraint, not a bug.
- **Do not use raw TRUNCATE** unless the milestone explicitly accepts "import failure = data loss" semantics. The user asked about "atomicity" specifically — DELETE is the only answer here.
- **Lock wait timeout:** `SET innodb_lock_wait_timeout = 600` for the import session in MariaDB; long deletion + insertion can otherwise abort at the default 50s.
- **Disable JPA L1 cache mid-flight:** `entityManager.clear()` before the bulk insert phase to prevent stale cached versions of soon-to-be-replaced entities. Spring Batch convention is `entityManager.flush(); entityManager.clear();` every N rows during long imports.
- **H2-vs-MariaDB safety net:** wrap the FK-disable statement in a JPA `nativeQuery` only if absolutely necessary (e.g., self-referential FK on `teams.parent_team_id` blocks DELETE in dependency order). If you must, use a `Dialect` check: detect `org.hibernate.dialect.H2Dialect` vs `MariaDBDialect` from `entityManagerFactory.getProperties()` and choose the syntax. Test on **both** profiles in CI.

**Warning signs:**
- Import works on H2 dev tests, fails on local MariaDB → FK-order or syntax bug.
- Partial import where a few entities exist but most are missing → TRUNCATE auto-commit happened.
- "Lock wait timeout exceeded" in logs → no `innodb_lock_wait_timeout` bump.
- Replace-All succeeds, but `created_at`/`updated_at` show the import time, not the original creation time → see Pitfall 6.

**Phase to address:** **Replace-All transaction phase** — must be its own discrete phase with both H2 and MariaDB integration tests. The MariaDB Smoke workflow (already in CI per v1.9) must be extended to cover this path.

Sources:
- [MariaDB Foreign Key Constraints documentation](https://mariadb.com/docs/server/architecture/server-constraints/foreign-key-constraints)
- [H2 — Disable foreign keys / SET REFERENTIAL_INTEGRITY](http://h2-database.66688.n3.nabble.com/Disable-foreign-keys-td1613356.html)
- [Hibernate Community — Delete breaks referential integrity](https://forum.hibernate.org/viewtopic.php?p=2483520)

---

### Pitfall 5: JPA Auditing (`@CreatedDate` / `@LastModifiedDate` / `@Version`) overwriting imported timestamps

**What goes wrong:**
Every entity in CTC Manager extends `BaseEntity` which has `@CreatedDate created_at`, `@LastModifiedDate updated_at`, and `@Version version` (per CONVENTIONS.md and the question context). On import, the JSON contains the **original** timestamps — but `AuditingEntityListener` runs on `@PrePersist` and **overwrites** `createdAt` with `LocalDateTime.now()`. Same for `@LastModifiedDate` on update path. The imported data preserves IDs and FKs but the `created_at` becomes "today," destroying the historical record. `@Version` has the same issue: Hibernate may bump it from the imported value to 0 (default) on persist.

**Why it happens:**
- `AuditingEntityListener` is registered via `@EntityListeners(AuditingEntityListener.class)` on `BaseEntity` and fires unconditionally on `EntityManager.persist()`.
- There is **no** built-in "preserve original timestamps" toggle — you can only disable the entire `AuditorAware`/auditing config or temporarily detach the listener.
- `@GeneratedValue(strategy = GenerationType.UUID)` on `BaseEntity.id` may *also* regenerate the UUID on persist if the entity is in a "transient" state. JPA conventionally treats id-set-but-version-null as detached, but behavior varies between Hibernate versions. **Test this explicitly.**

**How to avoid:**
- **Use `EntityManager.merge()`, not `persist()`, for imports** — merge respects existing IDs and existing `@Version`. But `merge` returns a new managed entity; you must use the returned reference, not the input. And merge still fires `@PreUpdate`, which still triggers `@LastModifiedDate`.
- **Best approach: wipe-and-bulk-insert via native SQL**, bypassing JPA entirely for the restore phase. A `JdbcTemplate.batchUpdate(String sql, List<Object[]> args)` ignores all JPA listeners. The cost: you write ~20 native INSERT statements once, but they are mechanically derivable from the schema.
- **Alternative: temporarily disable auditing.** Inject `AuditingHandler` (Spring Data JPA bean) and call `setDateTimeProvider(() -> Optional.empty())` for the duration of the import; restore the default after. **This is fragile** — concurrent admin requests during import could lose their audit timestamps. Lock the app to a single in-flight import (see Pitfall 8).
- For `@Version`: native INSERT with the imported version value works. JPA `merge` honors it but only if the entity is in the right state — risky.
- Add an integration test: `givenExportWithCreatedAt2024_whenImport_thenCreatedAtIsStill2024()`.

**Warning signs:**
- Round-trip test passes on entity equality but fails on `created_at` field comparison.
- All imported rows show `created_at = updated_at = <import-time>`.
- `@Version` shows 0 across all imported rows after a Replace-All from a long-lived export.

**Phase to address:** **Same phase as Pitfall 4 (Replace-All transaction).** The two are entangled — the choice of native SQL vs JPA persist is the same decision.

Sources:
- [Baeldung — Auditing with JPA, Hibernate, and Spring Data JPA](https://www.baeldung.com/database-auditing-jpa)
- [Spring Data JPA Auditing reference](https://docs.spring.io/spring-data/jpa/docs/1.7.0.DATAJPA-580-SNAPSHOT/reference/html/auditing.html)

---

### Pitfall 6: ZIP Slip path traversal on import + ZipBomb DoS on import

**What goes wrong:**
The naive ZIP unpacking pattern is:
```java
try (ZipInputStream zis = new ZipInputStream(uploadedStream)) {
    ZipEntry e;
    while ((e = zis.getNextEntry()) != null) {
        Path target = uploadDir.resolve(e.getName());  // VULN
        Files.copy(zis, target);
    }
}
```
A malicious ZIP entry `../../etc/passwd` writes outside `uploadDir`. Even worse, on a CTC admin app, `../../docs/site/` overwrites the public-static-site output, or `../../google-credentials.json` overwrites credentials. The CTC Manager already has a `path-traversal defense` for `FileStorageService` (per PROJECT.md SECU-02, v1.1) — the Import flow must reuse that defense, not re-implement.

A separate but related attack is the **ZipBomb**: a 1KB ZIP that decompresses to 10TB. Spring's default multipart limit is 10MB per the existing `application.yml` — that *limits the ZIP file size*, but the unpacked content can still be 1000:1 compressed. Without a per-entry size cap, the import can exhaust disk before the loop notices.

**Why it happens:**
- `ZipEntry.getName()` returns whatever the producer wrote — fully attacker-controlled.
- Java's `Path.resolve()` does not check that the result is inside the base directory.
- `ZipInputStream` does not enforce per-entry size limits; you must read with a counter and break.

**How to avoid:**
- **Reuse `FileStorageService.store()`** — per PROJECT.md SECU-02, it has path-traversal defense built in. The import service should pass each entry's name + content to `FileStorageService.store(name, inputStream)`, **not** unpack to disk directly.
- **Validate entry names independently:** before reading, reject entries whose canonicalized path escapes the target directory:
  ```java
  Path target = uploadDir.resolve(entry.getName()).normalize();
  if (!target.startsWith(uploadDir.toRealPath())) throw new ValidationException("Path traversal in ZIP");
  ```
- **Reject absolute paths in entry names:** `if (entry.getName().startsWith("/") || entry.getName().contains("..")) throw ...`
- **Per-entry size cap:** wrap the `ZipInputStream` read in a `BoundedInputStream` (Apache Commons IO) or count bytes manually; cap at, e.g., 50MB per file. Reject anything larger with HTTP 413.
- **Total decompressed size cap:** track cumulative bytes read across all entries; cap at, e.g., 500MB (configurable). Stop on cap and roll back.
- **Cap entry count:** ~10,000 entries should be plenty for the ~20-entity export. Reject ZIPs with >50,000 entries (defends against malformed central directory).
- **Reject duplicate names** in the same archive (defends against case-folding tricks on case-insensitive filesystems).
- **Bump `spring.servlet.multipart.max-file-size` to e.g. 500MB** in `application-prod.yml` for the import endpoint. Add `spring.servlet.multipart.location` so temp files don't fill `/tmp` on a small-disk container. Per the SB docs, `file-size-threshold` controls when files spill from memory to disk — set to 1MB to keep the JVM heap safe.

**Warning signs:**
- Static analysis flags `ZipEntry.getName()` usage without `Path.normalize()` + `startsWith` check (Find-Sec-Bugs has a rule for this).
- A test ZIP with `../foo` in entry names succeeds without throwing → defense missing.
- Disk fills during import test → no per-entry or total-size cap.

**Phase to address:** **Import phase, very early** — security-critical, hard to retrofit safely once the integration test suite is built around the unsafe path.

Sources:
- [SEI CERT IDS04-J — Safely extract files from ZipInputStream](https://wiki.sei.cmu.edu/confluence/display/java/IDS04-J.+Safely+extract+files+from+ZipInputStream)
- [Snyk Zip Slip vulnerability database](https://github.com/snyk/zip-slip-vulnerability)
- [Android Developers — Zip Path Traversal](https://developer.android.com/privacy-and-security/risks/zip-path-traversal)
- [Spring Boot multipart properties](https://runebook.dev/en/articles/spring_boot/application-properties/application-properties.web.spring.servlet.multipart.max-file-size)

---

### Pitfall 7: Schema-version drift between export and import — silent corruption

**What goes wrong:**
Developer adds a non-null column to `Match` (e.g., `forfeit_winner_id`). Schema bump V7 ships; production migrates. A user imports a backup taken **before** V7. Three failure modes:

1. **Strict failure:** Import deserializes JSON, JPA persist fails with `NULL not allowed for column FORFEIT_WINNER_ID`. Replace-All has already wiped the operative tables, so the database is empty. **Catastrophic data loss.**
2. **Silent corruption:** Import succeeds because the import service uses `merge` with default values for missing fields. But the imported data is now semantically wrong — the new field has a meaningless default. Users discover months later.
3. **Reverse drift:** User imports a *newer* backup into an *older* schema (e.g., dev `→` prod migration where prod hasn't deployed yet). JSON contains fields the schema doesn't have; Jackson `FAIL_ON_UNKNOWN_PROPERTIES = true` throws (per the [Spring Boot 4.0.6 fix to Jackson `use-jackson2-defaults`](https://github.com/spring-projects/spring-boot/releases) — be explicit about this default).

**Why it happens:**
- `schema_version` in the export header is **not auto-bumped** when a developer adds a Flyway migration. Manual coupling = easy to forget.
- "Forward-compat" and "backward-compat" are different and usually require different strategies.
- The Replace-All pre-wipe order is hostile to incremental rollback — the schema mismatch is detected too late.

**How to avoid:**
- **Tie `schema_version` to the latest Flyway migration** at export time: query `SELECT MAX(version) FROM flyway_schema_history` and stamp into the manifest. Forces every Flyway migration to bump the version mechanically.
- **Strict equality for v1.10 MVP** — reject any ZIP whose `schema_version` differs from the running app's version, with HTTP 400 + clear message: "Backup is from schema V5; this server is on V7. Run an older deploy to import this backup, then re-export." This is the explicit milestone constraint per the question. Document as the strict contract.
- **Read the manifest BEFORE wiping.** Order:
  1. Open ZIP, read `manifest.json` (must be the first entry — enforce this in the export writer)
  2. Validate `schema_version` against current
  3. **Only then** start the Replace-All transaction
- **Add a guard test:** `givenExportWithSchemaVersion5_whenImportOnVersion7_thenRejectsWithoutWipe()` — verify the database row count is unchanged after the rejection.
- **Long-term migration path** (not v1.10): a `MigrationStep` chain that reads the manifest version and applies in-memory transforms before persist. Out of scope per milestone.
- **Be explicit about Jackson defaults:** SB 4.0.6 fixed an issue where `use-jackson2-defaults` enabled `FAIL_ON_UNKNOWN_PROPERTIES`. The export/import pair should set this **explicitly** in an `ObjectMapper` bean to avoid surprises across patch versions:
  ```java
  ObjectMapper exportMapper = JsonMapper.builder()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)  // strict on import
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)    // ISO-8601 timestamps
      .build();
  ```

**Warning signs:**
- `schema_version` in code is hardcoded → next migration silently breaks.
- Import endpoint reads `data.json` before checking manifest → wipe order is wrong.
- Round-trip test only runs on the same version → forward-compat regression goes undetected.

**Phase to address:** **Schema-versioning phase**, before Replace-All implementation. The version-check guard must be the *first* thing the import controller does.

---

### Pitfall 8: Concurrent import + edit + auto-save races — "import while user is editing a matchday"

**What goes wrong:**
Admin A clicks Import. Admin B is concurrently entering race results. While the import transaction holds `seasons`, `matches`, `races` locks, B's auto-save tries to UPDATE `race_results` and waits. After the import commits with new IDs, B's UPDATE either:
- Targets a deleted ID → silent no-op, B's work is lost
- Holds a stale FK reference → constraint violation, B sees a 500

Worse: two admins both click Import in the same minute. The second import's wipe runs *during* the first import's restore, deleting half-restored rows. End state: data is corrupted in a way the manifest can't detect.

**Why it happens:**
- The CTC admin is "single-admin" by intent (per PROJECT.md), but the auth model (HTTP Basic, shared credential) does not enforce singletonship.
- Long-running write transactions are uncommon in normal admin flow → the codebase has no concurrency primitives ready.
- "Import" is naturally treated as an atomic admin-only action, but the controller is just another `@PostMapping`.

**How to avoid:**
- **Application-level lock via `ReentrantLock` static singleton** in an `ImportLockService` bean:
  ```java
  if (!importLock.tryLock(0, TimeUnit.SECONDS)) {
      throw new BusinessRuleException("Another import is already running. Wait for it to finish.");
  }
  ```
  Released in a `try/finally`. This is sufficient for a single-instance deploy. **Document that horizontal scaling breaks this** — for v1.10, accept that.
- **Read-only mode flag during import:** set a flag in `ApplicationContext` (or a static volatile) that an `@ControllerAdvice`-based filter checks; reject all `/admin/.*POST.*` requests with HTTP 503 "App is temporarily read-only during import" except for the import endpoint itself. Audit log readers should still work. This is heavier but eliminates Admin B's auto-save corruption window.
- **Banner during import:** the admin layout displays a persistent yellow banner "Import in progress, do not save" via a controller-advice flag.
- **Audit log entry for Import start AND completion** (separate rows) — if the app crashes mid-import, the audit log surfaces the half-done state on next startup.
- **Documented operational runbook:** "Before clicking Import, announce to other admins. For backups before Import, take an OS-level mysqldump too." This is a process control, not a code control, but the milestone should produce the runbook as part of the deliverables.

**Warning signs:**
- Two import requests overlap in audit log → no lock.
- A user reports "I saved my matchday but it's gone" → import collided with auto-save.
- Import endpoint takes 30+ seconds → window large enough for B to hit it.

**Phase to address:** **Import phase, mid-implementation** — after the basic flow works, before the milestone ships. Easy to add late (drop-in lock service), but the read-only-mode flag wants to be designed in early.

---

### Pitfall 9: Auditing/operational scope split — audit log inside or outside the export?

**What goes wrong:**
The CTC Manager has audit-style data (per the milestone requirement: "Audit-Log-Eintrag beim Import"). The question for v1.10 is: when Replace-All wipes the operative tables, **does the audit log get wiped too**? Two failures:

1. **Wipe the audit log:** The very record that says "Admin X imported on YYYY-MM-DD" is gone. There's no trail of what happened. If the import was bad, recovery is blind.
2. **Don't wipe, but include the audit log in the export:** A circular causality — the export contains audit entries from the *previous* state, restoring those onto a wiped audit log creates a confusing mix of past and present audit events.

**Why it happens:**
- The "scope" decision is binary but easy to defer.
- Backup-and-restore conventions for production systems (e.g., PostgreSQL pg_dump) wipe everything — but those systems have separate WAL/log infrastructure.

**How to avoid:**
- **Decision (recommend): the audit log is OUTSIDE operational scope.** It is **not** wiped on Replace-All, and **not** included in the export. The audit log is a journal of admin actions on this specific deployment; restoring it from another deployment makes no sense. Bake this into the model: a separate `audit_events` table that is *not* in the export entity list, *not* truncated, with its own retention policy.
- Document explicitly in `PROJECT.md` Decisions table: "Audit log is operational metadata, lives outside Export/Import scope. Replace-All preserves audit trail."
- **Test it:** `givenAuditLog_whenReplaceAllImport_thenAuditLogPreserved()` and `givenExport_thenZipDoesNotContainAuditEntries()`.
- A new audit entry **is** written by the import itself (per the milestone requirement) — wiped-rows-per-table + restored-rows-per-table. That entry survives the next import.

**Warning signs:**
- Audit log table is in the entity-export list → wrong scope.
- After a test import, the import event is missing from the audit log → audit was wiped.
- Restoring an old export adds audit entries from the old environment to the current → audit was in scope.

**Phase to address:** **Scope-decision phase, very early** (first plan of the milestone). The decision flows into the entity list, the wipe order, and the test matrix.

---

### Pitfall 10: File system race conditions on `data/dev/uploads/` — concurrent writes during export/import

**What goes wrong:**
The `app.upload-dir` defaults to `data/dev/uploads/`. During **export**, the service walks this directory to add files into the ZIP. During **import**, files are extracted into the same directory (Replace-All means existing files there get deleted first). Five failure modes:

1. **Concurrent admin upload** during export: a new logo lands in `uploads/` after the file list is built but before the ZIP closes. The ZIP either skips it (silent inconsistency) or includes a half-written file (corruption).
2. **Concurrent admin upload during import:** new file is overwritten by a stale file from the ZIP. Admin doesn't notice until next page render.
3. **Symlinks in upload dir:** Should `Files.walk` follow them on export? If yes, attacker who can drop a symlink (e.g., compromised credentials) reads any file the JVM can read. If no, legitimate symlinks (e.g., shared logo across teams) are silently skipped.
4. **Disk space exhaustion during export:** the ZIP is written to a tmp file before the response stream flushes. A 10GB upload directory needs 10GB free on the tmp partition (which on Docker is often `/tmp`, often small). User sees "successful" build but fails at HTTP send.
5. **Existing files NOT in import:** Replace-All says delete. But what if the running app has a file `uploads/team-logos/foo.png` that the admin uploaded *between* the backup and now? Import deletes it. **Acknowledge this in the warning dialog.**
6. **File handle leaks:** export writes ZIP via `ZipOutputStream`; if an exception fires mid-write, `try-with-resources` is required for both the ZIP and every `Files.newInputStream` per-entry. Manual close → leak under JVM GC pressure.

**Why it happens:**
- POSIX has no notion of "snapshot the filesystem"; you can read it, but it can change underneath you.
- Symlink following is the default for `Files.walk` on Java unless you pass `LinkOption.NOFOLLOW_LINKS`.
- Disk-space and file-handle issues are typical "works in dev (5MB)/breaks in prod (5GB)" problems.

**How to avoid:**
- **Hold the same import lock during export** (Pitfall 8). Export should also reject concurrent admin uploads via the read-only-mode flag. This makes export atomic w.r.t. user actions.
- **Stream the ZIP directly to the response** via `StreamingResponseBody`, never to a tmp file. JVM heap stays low; disk usage is zero. **Beware:** if `StreamingResponseBody` runs after the request transaction closes, see Pitfall 3 — load DTOs first, then stream.
- **Do not follow symlinks by default:** `Files.walk(path, FileVisitOption.FOLLOW_LINKS)` → `Files.walk(path)` (default is no-follow). Document the choice.
- **`try-with-resources` for everything** — every `Files.newInputStream`, every `ZipOutputStream`, every `ZipInputStream`. Code review checklist item.
- **Check free disk space before export:** `Files.getFileStore(uploadDir).getUsableSpace()` should be at least 2x the upload-dir size (slack for compression overhead). Reject with a clear error otherwise.
- **For import, "extra files" warning in the preview screen:** before wiping, list files currently in `uploads/` that are NOT in the import ZIP. The preview shows them, the user must explicitly acknowledge "delete these." This raises the bar on accidental data loss.

**Warning signs:**
- Export ZIP has 0-byte file entries → write race.
- Production export fails at 95% with "no space left on device" → temp-file path active.
- Files appear in import that the user didn't expect → no preview-extra-files warning.
- `lsof` on the JVM shows stale ZIP file handles after a failed export → leaked resources.

**Phase to address:** **Late-mid milestone** — needs basic export/import to exist first (Pitfalls 3, 4, 6), then layer file-system concurrency on top.

---

## Spring Boot 4.0.5 → 4.0.6 Specific Pitfalls (B+C in the question)

### Pitfall 11: Hibernate 7.2 entity equality / lazy-loading subtle changes

**What goes wrong:**
Hibernate 7 has changed bytecode-enhancement defaults (BytGuddy-driven) and various lazy-loading semantics. The CTC Manager relies on default JPA proxy lazy loading (no Hibernate enhancer plugin in `pom.xml`). Two risks:
- Two-entity equality: `entity1.equals(entity2)` semantics for proxies vs initialized entities can change subtly between Hibernate versions. The codebase uses `@ToString(exclude = ...)` but has no explicit `equals/hashCode` on entities — they fall back to `Object.equals`, which is reference equality. **This is fine** as long as the codebase never compares entities by content; verify with a grep.
- `OpenInViewInterceptor` deprecation: Spring has been signaling OSIV-deprecated for years. Spring Framework 7 / Boot 4 may emit a stronger warning. The `application.yml` already sets `logging.level.org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor: ERROR` to suppress one warning — verify the suppression path is still valid in 4.0.6.

**Why it happens:**
- Default behavior shifts in patch versions are rare but happen (e.g., flush-mode defaults).
- Suppression of warnings via `logging.level` keys can become stale when the underlying class moves package.

**How to avoid:**
- **Run `./mvnw verify -Pe2e` after the upgrade** — JaCoCo + 31 Playwright tests cover the major code paths. If they pass, equality semantics are likely intact.
- **Check application startup logs** for new WARN/ERROR lines after the bump. Anything mentioning `OpenEntityManagerInViewInterceptor`, `LazyInitializationException`, or `enable_lazy_load_no_trans` is a smoke signal.
- **Don't add `equals`/`hashCode` defensively** — Lombok `@EqualsAndHashCode` on JPA entities is a well-known footgun (triggers lazy loading, breaks proxy equality). Status quo (reference equality) is correct for the CTC pattern.

**Warning signs:**
- New WARN logs about session/proxy after upgrade.
- A previously-passing entity-comparison test now fails.

**Phase to address:** **Upgrade phase**, as part of the post-upgrade verify suite. No standalone work needed.

---

### Pitfall 12: Maven Surefire/Failsafe argLine + JEP 498 + new Mockito version interaction

**What goes wrong:**
The `pom.xml` pins:
```xml
<argLine>@{argLine} --sun-misc-unsafe-memory-access=allow -javaagent:.../mockito-core-${mockito.version}/mockito-core-${mockito.version}.jar</argLine>
```
Spring Boot 4.0.6 may bump Mockito Core (managed via parent BOM). The argLine references `${mockito.version}` — but **`mockito.version` is not declared as a project property** (only `playwright.version` and `lombok.version` are). It comes from the parent BOM. After the bump, the path resolves to a new directory; if the local Maven cache is empty, Surefire fails with `mockito-core-X.Y.Z.jar not found`.

Separately, JEP 498 `--sun-misc-unsafe-memory-access=allow` was added in Java 24 and removed in a future Java release. Java 25 is fine; Java 26 will be a problem. **Out of scope for v1.10**, but flag for the deferred-list.

**Why it happens:**
- Implicit BOM-derived properties are fragile across version bumps.
- The argLine is plumbing that breaks silently — only on a clean Maven cache.

**How to avoid:**
- **After the bump, run `./mvnw verify` from a clean cache** at least once (in CI, `mvn -U`). This is already the CI behavior; flag if it changes.
- **Make `mockito.version` explicit** in `<properties>`, sourced from the new BOM value. Cost: one line, locks down the path.
- **Alternative: drop the `-javaagent` argLine flag entirely** — Mockito 5.x emits a warning about agent self-attachment on Java 21+ but works without the explicit flag in most setups. Test whether the suite passes without it. If yes, simplify.

**Warning signs:**
- CI passes locally, fails in fresh Docker container with `mockito-core-X.Y.Z.jar not found`.
- A different developer's machine fails with the same error.

**Phase to address:** **Upgrade phase**, post-bump smoke. Five-minute fix if it bites.

---

### Pitfall 13: Jackson 3.1.2 default-mapper changes (`use-jackson2-defaults` fix)

**What goes wrong:**
SB 4.0.6 fixed a bug where `use-jackson2-defaults` incorrectly enabled `FAIL_ON_UNKNOWN_PROPERTIES`. In 4.0.5, that defaulted to `true` (strict); in 4.0.6, it defaults to `false` (lenient) **if `use-jackson2-defaults` is set**. The current codebase doesn't have a JSON-rendering controller path, so this likely doesn't affect existing behavior. But the **import code path** depends on this being strict (so that an incoming JSON with extra fields is rejected). Pitfall 7 already calls for explicit `ObjectMapper` configuration — this confirms why.

**Why it happens:**
- Jackson defaults flip subtly across patch versions.
- Implicit behavior is the enemy of import contracts.

**How to avoid:**
- **Define an explicit `@Bean ObjectMapper exportImportMapper()`** that does not rely on Spring Boot defaults. Set every relevant feature explicitly: `FAIL_ON_UNKNOWN_PROPERTIES = true`, `WRITE_DATES_AS_TIMESTAMPS = false`, `FAIL_ON_NULL_FOR_PRIMITIVES = true`, plus a `JavaTimeModule` for `LocalDateTime`. Inject this mapper into the `ExportService` and `ImportService` by qualifier.

**Warning signs:**
- Import accepts a malformed JSON it should have rejected → mapper not strict.
- Date round-trips lose the time-zone or fractional seconds → no `JavaTimeModule`.

**Phase to address:** **Same as Pitfall 7 (schema versioning)** — same `ObjectMapper` bean serves both.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Serialize entities directly with Jackson + `@JsonIdentityInfo` | Skip the DTO layer (saves 1-2 days) | Every schema refactor breaks the export format; circular-ref bugs reappear; `hibernateLazyInitializer` JSON pollution | **Never** in v1.10 — DTO pays itself back the first time an entity changes |
| Use `TRUNCATE` instead of `DELETE FROM` for Replace-All | 5x faster wipe on big tables | Implicit COMMIT on MariaDB → no transaction → import failure = data loss | **Never** without an explicit pre-wipe `mysqldump`-equivalent |
| Skip path-traversal validation on ZIP entries | -10 lines of code | Critical security vulnerability (Zip Slip) | **Never** |
| Hardcode `schema_version = "1.0"` in code | -1 line in the manifest writer | Next migration silently breaks; corrupted imports | **Never** — wire to Flyway `MAX(version)` |
| Wipe-but-don't-restore on partial import failure | Simpler error path | Catastrophic: silent data loss on the next admin click | **Never** — wrap in `@Transactional(REQUIRES_NEW)` |
| Skip the import preview screen, go straight to confirm | Faster UX for small imports | One mis-click destroys all data | Only in tests via direct service call, never in UI |
| Allow concurrent imports without locking | Simpler controller | Two simultaneous imports interleave wipes → garbled state | **Never** — `ReentrantLock` is 5 lines |
| `@JsonIgnore` to break circular refs | No DTO layer needed | Lossy export — the ignored side disappears from the round-trip | When the ignored side is genuinely derivable (e.g., back-pointer); **document it** |
| Disable JPA auditing globally for the whole app | Imports preserve timestamps | All future creates lose `createdAt` | **Never** — disable scoped to the import transaction only |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Spring Multipart + ZIP upload | Default 10MB limit, fails on real backups | Bump `max-file-size` and `max-request-size` to e.g. 500MB; set `file-size-threshold` to 1MB so large uploads spill to disk; configure `spring.servlet.multipart.location` to a known-large partition |
| Jackson + Hibernate proxies | `{"hibernateLazyInitializer": ...}` pollution in JSON | Use a DTO layer; if entity-direct, use `jackson-datatype-hibernate6` matched to Hibernate 7 + Jackson 3.x in SB 4 |
| H2 vs MariaDB FK constraint disable | `SET FOREIGN_KEY_CHECKS=0` on H2 fails silently (unsupported) | Detect dialect; use `SET REFERENTIAL_INTEGRITY FALSE` for H2, `SET FOREIGN_KEY_CHECKS=0` for MariaDB; better: don't use either, use DELETE in dependency order |
| Spring Data JPA Auditing during import | `@CreatedDate` overwrites imported timestamp on persist | Use native SQL INSERT via `JdbcTemplate.batchUpdate` (bypasses listeners); or scope-disable auditing via `AuditingHandler.setDateTimeProvider(() -> Optional.empty())` for the import transaction only |
| StreamingResponseBody + transactions | Writing to response after transaction closes → LazyInitializationException | Load DTOs inside transaction, then stream from in-memory list; or extend transaction with `@Transactional(propagation = REQUIRES_NEW)` on the streaming method (not always reliable) |
| Spring Security 7 CSRF + multipart | Multipart filter runs after security filter → token in body invisible | Send token as `X-CSRF-TOKEN` header from JS; or register `MultipartFilter` before security chain (less secure) |
| Flyway + audit table inclusion | Audit table created by V2-V6 ends up in entity scope and gets wiped | Mark audit table out of scope in entity list; document in `PROJECT.md` Decisions |
| Thymeleaf 3.1.5 + restricted expressions in fragment params | Existing template breaks at render time, not startup | Move computation to controller; use plain variable references in fragment params; CI smoke-render every admin route |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Loading all entities into memory before serializing | OOM on full export of large season history | Stream entities via `Stream<T>` from repository; emit JSON entries one at a time; `entityManager.detach(entity)` after each | At ~10k matchdays / ~100k race lineups (a few full seasons) |
| Bulk INSERT one row at a time via JPA persist | 60-second import on small-MB DB; transaction log explodes | Use `JdbcTemplate.batchUpdate(sql, batch)` with batch size 500; or `entityManager.flush(); clear();` every 50 entities | At ~500 entities total |
| TRUNCATE-then-INSERT in MariaDB without `innodb_lock_wait_timeout` bump | Random "Lock wait timeout exceeded" with low admin concurrency | Bump session-level timeout to 600s for the import session; ensure no other writes during import | At ~10s import duration with concurrent reads |
| Holding the import lock during export | Concurrent admin actions block while a 30s export runs | Separate locks for export (read-only, allow concurrent) and import (write, exclusive) | At any concurrent multi-admin scenario |
| Re-rendering all Thymeleaf templates without caching after upgrade | Slow first-request after restart; lambda warm-up issue | Default Thymeleaf template cache is on; verify `spring.thymeleaf.cache=true` in prod profile (default is true) | Always — but a `cache=false` somewhere in dev profile leaking to prod |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Trusting ZIP entry names without canonicalization | Zip Slip — arbitrary file write outside upload dir, including overwrite of `google-credentials.json` | Reuse `FileStorageService.store()` (already has path-traversal defense per SECU-02); independently validate every entry's resolved path is `startsWith(uploadDir.toRealPath())` |
| No per-entry decompressed-size cap | ZipBomb DoS — disk fill, OOM | Wrap stream read in `BoundedInputStream`; cap per entry (50MB) and total (500MB) |
| Import endpoint without authentication | Unauthenticated wipe-and-restore in prod | Endpoint behind `@PreAuthorize` or the existing `prod`/`docker` profile Basic Auth — verify the `/admin/**` route guard covers it |
| Logging entity contents on import (PSN-IDs, driver nicknames) | PII in logs | Log only counts and IDs, not entity contents; existing logging conventions already do this |
| Export ZIP downloadable via guessable URL | Credential-less download of the entire DB | Endpoint behind admin auth; `Content-Disposition: attachment` (no inline render); no public archive of past exports |
| Replace-All available via GET (e.g., from a confirmation link) | Accidental wipe via a clicked link | POST-only; require a one-time confirm token from the preview response; CSRF header check (defense in depth even with CSRF disabled globally) |
| Schema-version check after wipe | Catastrophic data loss on incompatible import | Validate manifest **before** entering the wipe transaction; reject early with HTTP 400 |
| Symlink-following on export | Information disclosure if an attacker can plant a symlink | `Files.walk(path)` without `FOLLOW_LINKS` (default behavior — verify) |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| One-click Replace-All without preview | Mis-click destroys all operative data | Mandatory preview screen showing wipe-counts-per-table + restore-counts-per-table; require typing "DELETE" to confirm |
| Generic error message on import failure | "Something went wrong" — admin has no recovery path | Specific errors per failure mode: schema-mismatch shows current vs imported version; ZIP-malformed shows entry name; FK-violation shows table name |
| No progress indication on large imports | Admin assumes the request hung, refreshes, triggers a second import | `StreamingResponseBody` for export; for import, a per-table progress log emitted to a server-sent-events stream or a polling endpoint |
| No backup-before-import safety net | "I just clicked Import and lost data" — no undo | The Replace-All endpoint takes an automatic export ZIP **first**, stores in `data/.import-backups/<timestamp>/backup.zip`, then proceeds. Document the recovery: "manually re-import the backup zip" |
| Silent skip of orphan files on import | Admin doesn't notice a file the export had is missing | Preview screen lists files-in-DB-not-in-ZIP and files-in-ZIP-not-in-DB |
| Schema mismatch error worded for developers | "schema_version 5 does not match 7" — admin doesn't know what to do | "This backup is from an older app version. Either deploy app version v1.9 to import it, or get a fresh backup from this version." |
| No "what will be deleted" preview for files | Admin doesn't realize their newer logo will be overwritten | Preview screen shows file diff (new/modified/deleted), with timestamps |

---

## "Looks Done But Isn't" Checklist

- [ ] **Thymeleaf template audit:** Looks done if `./mvnw verify` passes. **But:** verify a CI smoke-render IT GETs every admin route — many templates only render under specific data conditions.
- [ ] **Export DTO layer:** Looks done if `data.json` is produced. **But:** verify (a) no `hibernateLazyInitializer` in the JSON, (b) no infinite recursion on bidirectional refs, (c) the file is parseable by `jq`, (d) every operative entity from the milestone scope has a record.
- [ ] **Replace-All transaction:** Looks done if local-H2 round-trip works. **But:** verify on local **MariaDB** that (a) FK order is correct, (b) `created_at` is preserved, (c) `@Version` is preserved, (d) failure mid-restore rolls back the wipe.
- [ ] **ZIP unpack:** Looks done if a valid ZIP imports. **But:** verify a malicious ZIP with `../etc/passwd`, a 1KB-to-1GB-decompressed entry, a duplicate-name entry, an absolute-path entry are all rejected.
- [ ] **Schema version check:** Looks done if exported version matches. **But:** verify that an older-version ZIP is rejected **before** the wipe runs (counter-test: row count unchanged after rejection).
- [ ] **Import preview:** Looks done if it shows entity counts. **But:** verify it also shows file diffs (new/modified/deleted), and that the confirm token from the preview is required (no direct-confirm bypass).
- [ ] **Audit log entry:** Looks done if an audit row is written. **But:** verify it survives a subsequent Replace-All (audit out-of-scope) and that the entry contains wiped-counts AND restored-counts (separate fields, not just total).
- [ ] **Concurrent import lock:** Looks done if a single test passes. **But:** verify two concurrent calls one returns 409 with a clear message; verify the lock is released on exception (try/finally).
- [ ] **CSRF on import endpoint:** Looks done if the existing `csrf().disable()` is intact. **But:** verify the import form submits an `X-CSRF-TOKEN` header from JS so future CSRF re-enable doesn't break it.
- [ ] **Spring Boot 4.0.6 upgrade:** Looks done if all 1227+31 tests pass. **But:** verify the new WARN/ERROR log lines on first startup are checked manually (Hibernate 7 may chatter about previously-silent things).
- [ ] **JaCoCo 82% gate:** Looks done if the gate passes. **But:** verify that I/O-heavy services (export/import) are not excluded silently — they should be tested via integration tests that count toward line coverage.
- [ ] **MariaDB Smoke workflow:** Looks done if it ran on the upgrade PR. **But:** verify it ran the **import** path, not just the schema-validate path.

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Replace-All wipe succeeded but restore failed (TRUNCATE auto-commit) | HIGH — data loss | Restore from `mysqldump` taken before the import; if no dump, recover from the auto-backup ZIP (Pitfall 10 mitigation); else, data is gone |
| Schema-version mismatch detected after wipe | HIGH — data loss | Same as above; the version check should make this impossible (Pitfall 7) |
| Concurrent import collision | HIGH — corrupted state | Take MariaDB to read-only; `mysqldump` what's left; restore from latest known-good backup |
| Thymeleaf template breakage in production | MEDIUM | Roll back the Spring Boot version pin to 4.0.5; investigate the broken template; fix and redeploy |
| Hibernate 7 lazy-init explosion in export | LOW | Wrap export service in `@Transactional`; switch to DTO layer if not already |
| ZIP Slip exploitation (after deploy) | HIGH | Audit `uploads/` and parent dirs for unexpected files; re-introduce the path-traversal check; redeploy; rotate any potentially-leaked credentials |
| `created_at` overwritten on import | MEDIUM | If the imported ZIP is still on disk, re-import via native-SQL path that preserves timestamps; else, the original `created_at` is recoverable only from the export ZIP |
| Audit log wiped on Replace-All | MEDIUM | One-time data loss; from now on, scope audit out of Replace-All (Pitfall 9) |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1 — Thymeleaf 3.1.5 fragment-parameter regression | **Phase 1: SB 4.0.6 upgrade + template audit** | CI smoke-render IT GETs every `/admin/**` route, asserts non-500; manual click-through of less-used pages |
| 2 — Spring Security 7 CSRF + multipart import | **Phase 4: Import endpoint design** | `X-CSRF-TOKEN` header sent from JS; two-step preview→confirm token in controller test |
| 3 — Hibernate proxies + Jackson serialization | **Phase 3: Export DTO layer** | Round-trip integration test; assert no `hibernateLazyInitializer` in `data.json`; `@Transactional(NOT_SUPPORTED)` test forces no-OSIV |
| 4 — Replace-All transaction (TRUNCATE / FK / dialects) | **Phase 5: Replace-All transaction** | H2 + MariaDB integration tests; mid-restore failure rolls back; live MariaDB UAT |
| 5 — JPA Auditing overwrite | **Phase 5 (same as 4)** | `givenExportWithCreatedAt2024_whenImport_thenCreatedAtIsStill2024()`; native-SQL path bypasses listeners |
| 6 — ZIP Slip + ZipBomb | **Phase 4: Import endpoint** | Malicious-ZIP unit tests (`../`, absolute path, duplicate name, oversize entry); Spring multipart limits configured |
| 7 — Schema version drift | **Phase 2: Schema versioning + ObjectMapper bean** | Pre-wipe version check; `givenSchemaV5Export_whenImportOnV7_thenRejectsBeforeWipe()` |
| 8 — Concurrent import / read-only mode | **Phase 6: Operational hardening** | Two-concurrent-call test → 409 on second; banner in admin layout; runbook |
| 9 — Audit log scope decision | **Phase 0 (kickoff): Scope decision** | Audit table NOT in entity list; `PROJECT.md` Decisions row added; preserved-after-import test |
| 10 — File system races / disk space / symlinks | **Phase 6 (same as 8)** | StreamingResponseBody export; `try-with-resources` review; preview shows file diff; auto-backup before import |
| 11 — Hibernate 7 entity equality / OSIV deprecation | **Phase 1 (upgrade verify)** | `./mvnw verify -Pe2e` passes; new WARN/ERROR log lines reviewed |
| 12 — Surefire argLine + Mockito version drift | **Phase 1** | Clean-cache CI run after the bump; explicit `mockito.version` property if it bites |
| 13 — Jackson defaults | **Phase 2 (same as 7)** | Explicit `ObjectMapper` bean; round-trip test with extra-fields JSON rejected |

**Recommended phase ordering:**

0. **Scope decision** (audit log scope, what's "operative") — 1 plan, 0.5 day
1. **SB 4.0.6 upgrade + Thymeleaf 3.1.5 template audit** — 2-3 plans, 2 days. Unblocks everything else.
2. **Schema versioning + explicit ObjectMapper bean** — 1 plan, 1 day. Defines the wire contract.
3. **Export DTO layer + DTO→JSON serialization** — 2-3 plans, 2 days. No wipe yet.
4. **Import controller skeleton: ZIP unpack, schema check, preview screen, security hardening** — 2 plans, 2 days. No wipe yet, just preview.
5. **Replace-All transaction (DELETE in FK order, native SQL restore, audit timestamp preservation, MariaDB live UAT)** — 2-3 plans, 3 days. The riskiest phase.
6. **Operational hardening: import lock, read-only banner, auto-backup-before-import, file-system race protection, runbook** — 2 plans, 1.5 days.
7. **Final UAT + JaCoCo gate hold + docs update** — 1 plan, 1 day.

Total estimate: ~10-13 days of focused work for the milestone, matching the v1.9 cadence.

---

## Sources

- **Spring Boot 4.0.6 release**:
  - [Spring Boot 4.0.6 available now (announcement)](https://spring.io/blog/2026/04/23/spring-boot-4-0-6-available-now/)
  - [Spring Boot Releases on GitHub](https://github.com/spring-projects/spring-boot/releases)
- **Thymeleaf CVE-2026-40478 (the actual cause of the v1.9 breakage)**:
  - [Endor Labs — How a whitespace character broke Thymeleaf's expression sandbox](https://www.endorlabs.com/learn/its-about-thyme-how-a-whitespace-character-broke-thymeleafs-expression-sandbox-cve-2026-40478)
  - [GitLab Advisory — CVE-2026-40478](https://advisories.gitlab.com/maven/org.thymeleaf/thymeleaf/CVE-2026-40478/)
  - [DailyCVE — CVE-2026-40478 (High)](https://dailycve.com/thymeleaf-ssti-cve-2026-40478-high/)
  - [Thymeleaf Issue #809 — Improve restricted expression evaluation mode](https://github.com/thymeleaf/thymeleaf/issues/809)
- **Spring Security 7 CSRF + multipart**:
  - [Spring Security CSRF reference](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
  - [Java Code Geeks — Spring Boot 4 Migration: Breaking Changes, New Defaults](https://www.javacodegeeks.com/2026/05/spring-boot-4-migration-breaking-changes-new-defaultsand-what-actually-broke.html)
- **JPA / Hibernate / Jackson serialization**:
  - [Baeldung — Jackson Bidirectional Relationships](https://www.baeldung.com/jackson-bidirectional-relationships-and-infinite-recursion)
  - [FasterXML/jackson-datatype-hibernate](https://github.com/FasterXML/jackson-datatype-hibernate)
  - [Mastering Hibernate 7: Proxies and LazyInitializationException](https://ankurm.com/mastering-hibernate-7-proxies-and-the-lazyinitializationexception/)
  - [Baeldung — Auditing with JPA, Hibernate, and Spring Data JPA](https://www.baeldung.com/database-auditing-jpa)
- **ZIP Slip + multipart**:
  - [SEI CERT IDS04-J — Safely extract files from ZipInputStream](https://wiki.sei.cmu.edu/confluence/display/java/IDS04-J.+Safely+extract+files+from+ZipInputStream)
  - [Snyk Zip Slip vulnerability database](https://github.com/snyk/zip-slip-vulnerability)
  - [Android Developers — Zip Path Traversal](https://developer.android.com/privacy-and-security/risks/zip-path-traversal)
  - [Baeldung — MaxUploadSizeExceededException in Spring](https://www.baeldung.com/spring-maxuploadsizeexceeded)
- **Database transaction / TRUNCATE**:
  - [MariaDB Foreign Key Constraints documentation](https://mariadb.com/docs/server/architecture/server-constraints/foreign-key-constraints)
  - [H2 — Disable foreign keys](http://h2-database.66688.n3.nabble.com/Disable-foreign-keys-td1613356.html)
- **CTC Manager internal context**:
  - `/Users/jegr/Documents/github/ctc-manager/.planning/PROJECT.md` (v1.9 close, decisions table)
  - `/Users/jegr/Documents/github/ctc-manager/.planning/MILESTONES.md`
  - `/Users/jegr/Documents/github/ctc-manager/.planning/codebase/CONVENTIONS.md` (BaseEntity, OSIV, DTO patterns)
  - `/Users/jegr/Documents/github/ctc-manager/.planning/codebase/TESTING.md` (`@Transactional` rollback, MariaDB Smoke, JaCoCo 82% gate)
  - `/Users/jegr/Documents/github/ctc-manager/pom.xml` (SB 4.0.5 → target 4.0.6, no `mockito.version` property, JEP 498 argLine)
  - `/Users/jegr/Documents/github/ctc-manager/src/main/resources/application.yml` (multipart 10MB, OSIV warning suppression)
  - `/Users/jegr/Documents/github/ctc-manager/src/main/resources/application-prod.yml` (`ddl-auto: validate`)
  - CLAUDE.md (CSRF policy, SECU-02 path traversal, RaceLineup source-of-truth)

---

*Pitfalls research for: CTC Manager v1.10 — Spring Boot 4.0.6 upgrade + ZIP-based Export/Import*
*Researched: 2026-05-09*
*Confidence: HIGH on upgrade pitfalls (cross-checked release notes + CVE advisories), HIGH on JPA/ZIP pitfalls (well-documented territory), MEDIUM on Hibernate 7.2-specific behavior changes (limited public material). Where confidence is MEDIUM, the recommendation is to verify in the upgrade-phase smoke test rather than rely on the assertion.*
