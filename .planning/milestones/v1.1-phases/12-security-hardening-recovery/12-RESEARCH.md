# Phase 12: Security Hardening Recovery - Research

**Researched:** 2026-04-06
**Domain:** Java security — SSRF hostname validation, path traversal protection (FileStorageService)
**Confidence:** HIGH

---

## Project Constraints (from CLAUDE.md)

- **Test Coverage:** Minimum 82% line coverage (JaCoCo, `LINE / COVEREDRATIO / 0.82`)
- **TDD:** Write tests first, then implementation. Red → Green → Refactor.
- **Test naming:** `givenContext_whenAction_thenExpectedResult()` with `// given / when / then` comments.
- **Feature sequence:** Unit Tests → Implementation → Integration Tests → E2E Tests.
- **No breaking changes** to existing URLs/endpoints.
- **OSIV enabled** — no relevance to this phase.
- **Flyway:** No DB schema changes in this phase.
- Technology: Java 25, Spring Boot 4.x, JUnit 5, Mockito, `./mvnw verify`

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SECU-01 | FileStorageService.storeFromUrl() validates hostname — private IPs, localhost and internal networks blocked | Exact implementation recovered from commit 84e8896; test suite fully recovered from commit 5b3a58b |
| SECU-02 | FileStorageService.store() and storeImage() check path traversal with normalize()+startsWith(uploadDir) | Exact implementation recovered from commit 84e8896; test suite fully recovered from commit 5b3a58b |
</phase_requirements>

---

## Summary

Phase 6 implemented SSRF hostname validation and path traversal protection in `FileStorageService`
(commit `84e8896`). The worktree file-clobber regression in commit `5b3a58b` (refactor(10-02))
deleted the entire `FileStorageService` diff — removing three private validation methods and their
call sites — and also deleted the 11 corresponding security test cases from
`FileStorageServiceTest`. The service and its test file still exist; they were reset to their
pre-Phase-6 state, not deleted outright.

The recovery is a surgical re-application of the exact code from commit `84e8896`. No design
decisions need re-made; this phase restores known-good code. The original implementation was
verified against all 765 tests and JaCoCo coverage. Re-adding the 11 tests brings the suite back
to its previous count and restores coverage on the new lines.

**Primary recommendation:** Cherry-pick the exact diff from `84e8896` back into
`FileStorageService.java` and restore the 11 test methods from `FileStorageServiceTest.java` (also
recovered verbatim from the diff in `5b3a58b`). One plan, two tasks (tests first, then
implementation).

---

## What Was Lost (Regression Analysis)

### Lost from FileStorageService.java [VERIFIED: git show 5b3a58b]

Three private methods deleted entirely:

```java
// LOST — must restore
private void validateHostname(String sourceUrl) { ... }
private void validatePathWithinUploadDir(Path target) { ... }
private void validateNoPathTraversal(String filename) { ... }
```

Six call-site lines deleted:

| Method | Lost calls |
|--------|-----------|
| `store()` | `validateNoPathTraversal(file.getOriginalFilename())` before resolve; `validatePathWithinUploadDir(target)` before transferTo |
| `storeFromUrl()` | `validateHostname(sourceUrl)` after HTTPS check; `validatePathWithinUploadDir(dir)` after resolve dir; `validatePathWithinUploadDir(target)` after resolve target |
| `storeImage()` | `validateNoPathTraversal(file.getOriginalFilename())` after validate; `validatePathWithinUploadDir(target)` before transferTo |

### Lost from FileStorageServiceTest.java [VERIFIED: git show 5b3a58b]

11 test methods deleted (lines 247–369 in post-Phase-6 file):

**SECU-01 (SSRF) tests — 8 methods:**
- `givenLocalhostUrl_whenStoreFromUrl_thenThrowsIllegalArgument`
- `givenLoopbackIpUrl_whenStoreFromUrl_thenThrowsIllegalArgument`
- `givenPrivateIp10_whenStoreFromUrl_thenThrowsIllegalArgument`
- `givenPrivateIp172_whenStoreFromUrl_thenThrowsIllegalArgument`
- `givenPrivateIp192_whenStoreFromUrl_thenThrowsIllegalArgument`
- `givenLinkLocalUrl_whenStoreFromUrl_thenThrowsIllegalArgument`
- `givenIpv6LoopbackUrl_whenStoreFromUrl_thenThrowsIllegalArgument`
- `givenPublicUrl_whenStoreFromUrl_thenSsrfCheckPasses` (passes through hostname check, expects IOException from actual download)

**SECU-02 (path traversal) tests — 4 methods:**
- `givenPathTraversalFilename_whenStore_thenThrowsIllegalArgument` (filename `../../../etc/passwd.png`)
- `givenPathTraversalFilename_whenStoreImage_thenThrowsIllegalArgument`
- `givenAbsolutePathFilename_whenStore_thenThrowsIllegalArgument` (filename `/etc/passwd.png`)
- `givenPathTraversalSubDir_whenStoreFromUrl_thenThrowsIllegalArgument` (subDir `../escape`)

---

## Current State of FileStorageService [VERIFIED: file read]

`src/main/java/org/ctc/domain/service/FileStorageService.java` — 114 lines.

**What currently exists (and is NOT affected by recovery):**
- `validate(MultipartFile)` — content-type + extension + size + non-null filename checks
- `delete(String url)` — already has path-traversal guard (`startsWith(uploadDir)`) — this was NOT lost
- HTTPS-only check in `storeFromUrl()` — NOT lost
- `sanitize(String filename)` — NOT lost
- Constructor resolves uploadDir to absolute normalized path — NOT lost

**What is currently MISSING (must be restored):**
- `validateHostname()` method — SSRF protection for `storeFromUrl()`
- `validatePathWithinUploadDir()` method — normalized path containment check
- `validateNoPathTraversal()` method — defense-in-depth filename check
- All 6 call sites listed above

The `delete()` method already has its own inline path guard. This is pre-existing and not affected.

---

## Standard Stack

No new libraries needed. The implementation uses only:

| Component | Already Present | Notes |
|-----------|----------------|-------|
| `java.net.URI` | Yes — JDK | Used for hostname extraction |
| `java.nio.file.Path.normalize()` + `startsWith()` | Yes — JDK | Path containment check |
| `String.startsWith()` / `equals()` | Yes — JDK | IP prefix matching |
| JUnit 5 + AssertJ | Yes | `assertThatThrownBy()` pattern |
| `MockMultipartFile` | Yes — Spring Test | Used in existing test file |

**Installation:** None required.

---

## Architecture Patterns

### Pattern 1: Defense-in-depth filename check (validateNoPathTraversal)

```java
// Source: git show 84e8896
private void validateNoPathTraversal(String filename) {
    if (filename != null && (filename.contains("..") || filename.startsWith("/"))) {
        log.warn("Attempted path traversal in filename: {}", filename);
        throw new IllegalArgumentException("Path traversal detected in filename: " + filename);
    }
}
```

Called BEFORE `sanitize()` in both `store()` and `storeImage()`. Purpose: fail fast on obvious
traversal attempts before sanitization (defense-in-depth — sanitization alone would neutralize
`..` but explicit rejection is clearer and logged).

### Pattern 2: Normalized path containment (validatePathWithinUploadDir)

```java
// Source: git show 84e8896
private void validatePathWithinUploadDir(Path target) {
    Path normalized = target.toAbsolutePath().normalize();
    if (!normalized.startsWith(uploadDir)) {
        log.warn("Attempted path traversal: {}", target);
        throw new IllegalArgumentException("Path traversal detected: " + target);
    }
}
```

Note: `uploadDir` is already resolved to `toAbsolutePath().normalize()` in the constructor, so
the `startsWith` comparison is between two fully normalized absolute paths. Called on the resolved
`dir` (before `createDirectories`) and on the resolved `target` (before writing).

### Pattern 3: SSRF hostname blocklist (validateHostname)

```java
// Source: git show 84e8896
private void validateHostname(String sourceUrl) {
    String hostname = java.net.URI.create(sourceUrl).getHost();
    if (hostname == null) {
        throw new IllegalArgumentException("URL hostname blocked: <null>");
    }
    hostname = hostname.toLowerCase();
    if (hostname.equals("localhost") || hostname.equals("[::1]")) {
        log.warn("Blocked SSRF attempt to internal host: {}", hostname);
        throw new IllegalArgumentException("URL hostname blocked: " + hostname);
    }
    if (hostname.startsWith("127.") || hostname.startsWith("10.") || hostname.startsWith("192.168.")
            || hostname.startsWith("169.254.")) {
        log.warn("Blocked SSRF attempt to internal host: {}", hostname);
        throw new IllegalArgumentException("URL hostname blocked: " + hostname);
    }
    if (hostname.startsWith("172.")) {
        String[] octets = hostname.split("\\.");
        if (octets.length >= 2) {
            try {
                int secondOctet = Integer.parseInt(octets[1]);
                if (secondOctet >= 16 && secondOctet <= 31) {
                    log.warn("Blocked SSRF attempt to internal host: {}", hostname);
                    throw new IllegalArgumentException("URL hostname blocked: " + hostname);
                }
            } catch (NumberFormatException e) {
                // Not a numeric IP, allow
            }
        }
    }
}
```

Blocking list: `localhost`, `[::1]` (IPv6 loopback), `127.x.x.x` (loopback), `10.x.x.x`
(RFC-1918 Class A), `172.16-31.x.x` (RFC-1918 Class B), `192.168.x.x` (RFC-1918 Class C),
`169.254.x.x` (link-local / AWS metadata endpoint). Called in `storeFromUrl()` immediately after
the HTTPS check.

### Decision from STATE.md [VERIFIED: STATE.md]

Phase 6 decision was: "String-based SSRF hostname blocklist without DNS resolution;
defense-in-depth path traversal with raw filename + resolved path checks." This is the locked
design — no re-evaluation needed.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| SSRF protection | Custom DNS resolver | String-based prefix/equals checks (already decided in Phase 6) |
| Path containment | Custom string parsing | `Path.normalize().toAbsolutePath().startsWith()` — JDK standard |

---

## Common Pitfalls

### Pitfall 1: Restoring call sites in wrong order within store()

**What goes wrong:** `validateNoPathTraversal` must run BEFORE `sanitize()` — otherwise the
traversal sequence `..` is already replaced by `_` and the check becomes a no-op.

**How to avoid:** In `store()` and `storeImage()`, the call order is:
1. `validate(file)` (existing)
2. `validateNoPathTraversal(file.getOriginalFilename())` ← restore here
3. `sanitize(...)` on the resolved filename
4. `validatePathWithinUploadDir(target)` ← restore after target is resolved

### Pitfall 2: Missing validatePathWithinUploadDir on the directory path in storeFromUrl()

**What goes wrong:** Only checking `target` but not `dir` allows a crafted `subDir` parameter to
escape the upload root by creating a directory outside it before the file is written there.

**How to avoid:** Restore BOTH calls in `storeFromUrl()`:
- `validatePathWithinUploadDir(dir)` after `dir = uploadDir.resolve(subDir).resolve(...)`
- `validatePathWithinUploadDir(target)` after `target = dir.resolve(safeName)`

### Pitfall 3: uploadDir absolute normalization mismatch

**What goes wrong:** `validatePathWithinUploadDir` calls `target.toAbsolutePath().normalize()` and
compares against `uploadDir`. If `uploadDir` were NOT already absolute/normalized, the
`startsWith` check could produce false positives.

**How to avoid:** The constructor already does `Paths.get(uploadDir).toAbsolutePath().normalize()`
— this is correct and must not be changed. The validation method relies on this invariant.

### Pitfall 4: IPv4-mapped IPv6 addresses (172.x.x.x second-octet edge)

**What goes wrong:** 172.32.x.x (second octet = 32) is NOT in the RFC-1918 range; only
172.16.0.0–172.31.255.255 is private. The boundary condition `secondOctet >= 16 && secondOctet <= 31`
is correct — off-by-one would block or allow incorrectly.

**How to avoid:** Copy the implementation exactly from the recovery source. Do not retype.

---

## Test Coverage Impact

**Current test count:** ~773 tests (per memory/project status)
**Tests lost by regression:** 11 tests
**Tests restored:** 11 tests
**Net after recovery:** ~784 tests

All 11 test methods are available verbatim in the `git show 5b3a58b` diff (the removal diff
contains the exact content of the tests). They use only `MockMultipartFile`, `assertThatThrownBy`,
`UUID.randomUUID()` — all already imported in the existing test file.

The new code lines in `FileStorageService` (three private methods + 6 call sites = ~60 lines) are
fully exercised by the 11 tests. Coverage should remain above 82%.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ |
| Config file | `pom.xml` (Surefire + Failsafe) |
| Quick run command | `./mvnw test -pl . -Dtest=FileStorageServiceTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SECU-01 | localhost blocked | unit | `./mvnw test -Dtest=FileStorageServiceTest#givenLocalhostUrl*` | Wave 0 (restore) |
| SECU-01 | 127.x loopback blocked | unit | `./mvnw test -Dtest=FileStorageServiceTest#givenLoopbackIpUrl*` | Wave 0 |
| SECU-01 | 10.x private IP blocked | unit | `./mvnw test -Dtest=FileStorageServiceTest#givenPrivateIp10*` | Wave 0 |
| SECU-01 | 172.16-31.x private IP blocked | unit | `./mvnw test -Dtest=FileStorageServiceTest#givenPrivateIp172*` | Wave 0 |
| SECU-01 | 192.168.x private IP blocked | unit | `./mvnw test -Dtest=FileStorageServiceTest#givenPrivateIp192*` | Wave 0 |
| SECU-01 | 169.254.x link-local blocked | unit | `./mvnw test -Dtest=FileStorageServiceTest#givenLinkLocalUrl*` | Wave 0 |
| SECU-01 | [::1] IPv6 loopback blocked | unit | `./mvnw test -Dtest=FileStorageServiceTest#givenIpv6LoopbackUrl*` | Wave 0 |
| SECU-01 | public URL passes SSRF check | unit | `./mvnw test -Dtest=FileStorageServiceTest#givenPublicUrl*` | Wave 0 |
| SECU-02 | `../` path traversal in store() rejected | unit | `./mvnw test -Dtest=FileStorageServiceTest#givenPathTraversalFilename_whenStore*` | Wave 0 |
| SECU-02 | `../` path traversal in storeImage() rejected | unit | `./mvnw test -Dtest=FileStorageServiceTest#givenPathTraversalFilename_whenStoreImage*` | Wave 0 |
| SECU-02 | absolute path in store() rejected | unit | `./mvnw test -Dtest=FileStorageServiceTest#givenAbsolutePathFilename*` | Wave 0 |
| SECU-02 | `../escape` subDir in storeFromUrl() rejected | unit | `./mvnw test -Dtest=FileStorageServiceTest#givenPathTraversalSubDir*` | Wave 0 |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=FileStorageServiceTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] Restore 11 test methods into `FileStorageServiceTest.java` (exact content from commit `5b3a58b` diff)
- [ ] Restore 3 private methods + 6 call sites into `FileStorageService.java` (exact content from commit `84e8896` diff)

*(No new test infrastructure needed — `FileStorageServiceTest.java` already exists with correct imports)*

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V5 Input Validation | yes | `validateNoPathTraversal()`, `validateHostname()` |
| V5.2 Sanitization | yes | `sanitize()` already present + new path containment check |
| V12 File Upload | yes | `validatePathWithinUploadDir()`, `validateNoPathTraversal()` |
| V10 Malicious Code | partial | SSRF blocks server-side request forgery via URL imports |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SSRF via storeFromUrl() | Elevation of Privilege / Information Disclosure | Hostname blocklist (`validateHostname`) |
| Path traversal in store() | Tampering / Information Disclosure | `validateNoPathTraversal` + `validatePathWithinUploadDir` |
| Path traversal via subDir param | Tampering | `validatePathWithinUploadDir(dir)` before createDirectories |
| AWS metadata endpoint (169.254.169.254) | Elevation of Privilege | Blocked by link-local prefix check |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| — | — | — | — |

**All claims are VERIFIED** — implementation and tests recovered verbatim from git history.
No assumed knowledge relied upon.

---

## Sources

### Primary (HIGH confidence)

- `git show 84e8896` — exact implementation added in Phase 6 [VERIFIED: git]
- `git show 5b3a58b` — exact implementation removed in regression + exact tests removed [VERIFIED: git]
- `src/main/java/org/ctc/domain/service/FileStorageService.java` — current file state [VERIFIED: file read]
- `src/test/java/org/ctc/domain/service/FileStorageServiceTest.java` — current test state [VERIFIED: file read]
- `.planning/REQUIREMENTS.md` — SECU-01 and SECU-02 definitions [VERIFIED: file read]
- `STATE.md` — Phase 6 design decision (string-based blocklist, no DNS) [VERIFIED: file read]

---

## Metadata

**Confidence breakdown:**
- What was lost: HIGH — exact diffs from git history
- What to restore: HIGH — verbatim code recovery, no design work needed
- Test coverage impact: HIGH — exact test methods recovered from diff
- Implementation correctness: HIGH — previously passing with 765 tests

**Research date:** 2026-04-06
**Valid until:** N/A — recovery phase, not a library research
