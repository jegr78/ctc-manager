---
status: partial
phase: 19-merge-error-handling
source: [19-VERIFICATION.md]
started: 2026-04-07T19:00:00+02:00
updated: 2026-04-07T19:00:00+02:00
---

## Current Test

[awaiting human testing]

## Tests

### 1. Self-merge via URL manipulation — manual browser test
expected: POST /admin/drivers/{id}/merge/preview?targetId={same_id} redirects to /admin/drivers/{id}/merge and shows a red flash error message in UI
result: [pending]

### 2. Non-existent target via URL manipulation — manual browser test
expected: POST /admin/drivers/{id}/merge/preview?targetId={random-uuid} redirects to /admin/drivers/{id}/merge and shows a red flash error message in UI
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps
