# Phase 6: Security Hardening - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Protect FileStorageService file upload and URL download operations against path traversal and SSRF attacks. All changes are within FileStorageService.java and its tests.

</domain>

<decisions>
## Implementation Decisions

### SSRF Hostname Validation (SECU-01)
- **D-01:** Blocklist approach -- block private IPs (10.x, 172.16-31.x, 192.168.x), localhost, and link-local addresses. All external HTTPS URLs remain allowed.
- **D-02:** Hostname-only check (string-based), no DNS resolution. InetAddress.getByName() is not needed -- simple enough for an admin-only tool.
- **D-03:** No allowlist configuration. Blocklist is sufficient given the limited callers (Gt7SyncService, CarService, TrackService).

### Path Traversal Protection (SECU-02)
- **D-04:** Apply normalize()+startsWith(uploadDir) check to ALL 3 writing methods: store(), storeImage(), and storeFromUrl(). Defense-in-depth even though storeFromUrl() builds paths internally.
- **D-05:** Follow the existing pattern from delete() (lines 50-54) -- normalize resolved path, verify it starts with uploadDir, reject with log warning if not.

### Claude's Discretion
- Exception type for path traversal rejection (IllegalArgumentException or SecurityException)
- Exact private IP range matching implementation (regex, InetAddress parsing, or manual string check)
- Whether to extract validation into a private helper method or inline in each method

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Security
- `.planning/codebase/CONCERNS.md` -- SSRF Risk (lines 68-74) and Path Traversal (lines 76-82) sections define the exact locations and fix approach
- `src/main/java/org/ctc/domain/service/FileStorageService.java` -- The single file being modified (114 lines). delete() at line 47-61 has the reference pattern for path traversal checks.

### Testing
- `src/test/java/org/ctc/domain/service/FileStorageServiceTest.java` -- Existing test class to extend with security tests

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `delete()` method (line 47-61): Already implements normalize()+startsWith(uploadDir) pattern -- copy this for store(), storeImage(), storeFromUrl()
- `sanitize()` method (line 110-113): Already strips special chars from filenames -- provides first layer of defense

### Established Patterns
- HTTPS-only guard clause in storeFromUrl() (line 84-87): Existing pattern for URL validation. SSRF check extends this.
- UUID-based directory structure: All methods use UUID subdirectories, reducing path traversal risk but not eliminating it.

### Integration Points
- storeFromUrl() callers: Gt7SyncService, CarService, TrackService (all admin-level)
- store() callers: RaceAttachmentService (file upload from admin UI)
- storeImage() callers: CarService, TrackService, TeamManagementService (image upload from admin UI)

</code_context>

<specifics>
## Specific Ideas

No specific requirements -- standard security hardening with clear patterns from existing code.

</specifics>

<deferred>
## Deferred Ideas

None -- discussion stayed within phase scope.

</deferred>

---

*Phase: 06-security-hardening*
*Context gathered: 2026-04-04*
