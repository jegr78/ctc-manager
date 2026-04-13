---
phase: 28-raceattachment-security
asvs_level: 1
audited: 2026-04-13
result: SECURED
threats_total: 3
threats_closed: 3
threats_open: 0
---

# Security Audit — Phase 28: RaceAttachment Security

## Result: SECURED

**Phase:** 28 — raceattachment-security
**Threats Closed:** 3/3
**ASVS Level:** 1

## Threat Verification

| Threat ID | Category | Disposition | Evidence |
|-----------|----------|-------------|----------|
| T-28-01 | Elevation of Privilege | mitigate | `RaceAttachmentService.java:89-93` — `uploadDirPath.normalize()`, `file.startsWith(uploadDirPath)`, returns 400 on violation |
| T-28-02 | Tampering | mitigate | `RaceAttachmentService.java:110` — `replaceAll("[\\r\\n\";]", "_")` applied to `attachment.getName()` before header construction |
| T-28-03 | Denial of Service | mitigate | `RaceAttachmentService.java:107` — `(probed != null) ? probed : "application/octet-stream"` prevents NPE in `MediaType.parseMediaType()` |

## Test Coverage

All three mitigations are covered by dedicated unit tests in
`src/test/java/org/ctc/domain/service/RaceAttachmentServiceTest.java`:

| Test Method | Threat Covered |
|-------------|---------------|
| `givenPathTraversalUrl_whenDownloadAttachment_thenReturnsBadRequest` | T-28-01 |
| `givenNullProbeContentType_whenDownloadAttachment_thenUsesOctetStream` | T-28-03 |
| `givenFilenameWithInjectionChars_whenDownloadAttachment_thenHeaderIsSanitized` | T-28-02 |
| `givenNonExistentFile_whenDownloadAttachment_thenReturnsNotFound` | coverage gap (not a registered threat) |

## Defense-in-Depth Notes (Informational)

The implementation includes one additional guard not in the threat register:

- **URL prefix check** (`RaceAttachmentService.java:85-88`): `!url.startsWith("/uploads/")` returns 400 before path resolution. This pre-validates the URL format as a first layer before the path traversal boundary check. It is not a registered threat — it strengthens T-28-01 defense without introducing new risk.

## Unregistered Flags

None. SUMMARY.md `## Threat Flags` section reports no unregistered flags.

## Accepted Risks Log

None — all threats in this phase carry `mitigate` disposition. No risks accepted.
