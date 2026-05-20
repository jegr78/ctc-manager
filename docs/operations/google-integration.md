# CTC Manager — Google Integration Runbook

Audience: operator (league admin) configuring the Google Sheets bulk-import and
Google Calendar event-sync features. Covers initial setup, the 4 user-visible
error categories surfaced by the admin UI, and recurring troubleshooting
scenarios.

**Cross-references:**

- Config keys in `application-{dev,local,docker,prod}.yml`: `google.sheets.credentials-path`, `google.calendar.id`
- Java surface: [`GoogleSheetsService.java`](../../src/main/java/org/ctc/dataimport/GoogleSheetsService.java), [`GoogleCalendarService.java`](../../src/main/java/org/ctc/dataimport/GoogleCalendarService.java)
- Exception hierarchy: [`org.ctc.dataimport.exception.GoogleApiException`](../../src/main/java/org/ctc/dataimport/exception/GoogleApiException.java) + 4 sealed permits (`TransientGoogleApiException`, `AuthGoogleApiException`, `NotFoundGoogleApiException`, `PermissionGoogleApiException`)
- Mapper: [`GoogleApiExceptionMapper.java`](../../src/main/java/org/ctc/dataimport/exception/GoogleApiExceptionMapper.java)
- Related operator runbooks: [import-runbook.md](import-runbook.md), [release-runbook.md](release-runbook.md)

---

## Setup

The Google Sheets bulk-import and Google Calendar event-sync features both use a
single GCP service-account JSON key. Setup is a one-time operator action per
deployment environment.

1. **Create a GCP project** (or reuse an existing one) and enable both APIs:
   - `Google Sheets API`
   - `Google Calendar API`
2. **Create a service account** in the GCP project. Grant it the default
   `Editor` role (the per-API scopes restrict actual access; the role here is
   only for project membership).
3. **Generate a JSON key** for the service account (IAM → Service accounts →
   `<your-sa>@<project>.iam.gserviceaccount.com` → Keys → Add Key → JSON).
   Download the file and store it at a stable path on the host filesystem,
   for example `/etc/ctc-manager/google-credentials.json`. The file MUST be
   readable by the JVM process and SHOULD be `chmod 600 root:ctc`.
4. **Wire the path into the active profile**'s `application-{profile}.yml`:
   ```yaml
   google:
     sheets:
       credentials-path: /etc/ctc-manager/google-credentials.json
     calendar:
       id: your-calendar-id@group.calendar.google.com
   ```
   The same `credentials-path` is shared by both Sheets and Calendar — there is
   no separate calendar credentials key.
5. **Share each target resource** with the service account principal:
   - Each driver-import sheet: in Google Sheets → Share → add
     `<your-sa>@<project>.iam.gserviceaccount.com` with **Viewer** permissions.
   - The race-event calendar: in Google Calendar → Settings → Share with
     specific people → add the same principal with **Make changes to events**
     permissions.

### Verification

After app boot, check the logs for:

```
INFO  o.c.d.GoogleSheetsService — Google Sheets integration available (credentials: ...)
INFO  o.c.d.GoogleCalendarService — Google Calendar integration available (calendar: ...)
```

If either line is absent or the message is `... not available (no credentials configured)`,
the JSON key path or calendar ID is misconfigured — verify the profile-specific
yml and restart the app.

A quick end-to-end smoke: navigate to `/admin/drivers/import`, paste any valid
sheet URL, click **Preview**. If the integration is wired correctly, the
preview page renders with the tab-by-tab breakdown. If any badge category
appears (see below), follow the matching Troubleshooting entry.

---

## Error Categories

When a Google API call fails, the admin UI renders a categorized badge next to
the error message. The 4 categories are exhaustive — every Google API failure
maps to exactly one of these via the
[`GoogleApiExceptionMapper`](../../src/main/java/org/ctc/dataimport/exception/GoogleApiExceptionMapper.java).

| Category    | Badge color | User-visible message                                          | Trigger                                                                                                                  |
| ----------- | ----------- | ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| TRANSIENT   | Yellow      | `Connection problem — retry`                                  | Network timeout / socket reset; HTTP 408 / 429 (rate limit) / 500 / 502 / 503 / 504; any unrecognised status code        |
| AUTH        | Red         | `Authentication problem — re-link Google account`             | HTTP 401; HTTP 403 with auth-related reason (`authError`, `invalidCredentials`, `unauthorized`); `GeneralSecurityException` (credentials file unreadable / signature failure) |
| NOT_FOUND   | Blue        | `Sheet not found — check ID`                                  | HTTP 404 — the spreadsheet ID or calendar ID does not correspond to an existing resource                                  |
| PERMISSION  | Red         | `Access denied — share the sheet with the service account`    | HTTP 403 with a non-auth reason (`forbidden`, `insufficientPermissions`, other); HTTP 403 with no error details payload   |

The Calendar surface uses identical messages with one variant: the
`createCalendarEvent` endpoint substitutes "calendar" for "sheet" in the
NOT_FOUND and PERMISSION strings (`Calendar not found — check the calendar ID
configuration` / `Access denied — share the calendar with the service account`)
so the operator sees an action-appropriate hint.

The exact user-visible message strings above match the controller's flash text
verbatim. Any future change to a flash string MUST update this table in the
same commit (Update-on-Triage discipline) — controllers and this doc are the
single source of truth for what the operator sees.

---

## Troubleshooting

### "Authentication problem" badge appears immediately on every request

**Most likely cause:** the JSON key file at `google.sheets.credentials-path`
is missing, unreadable by the JVM, corrupted, or expired (service-account
keys do not expire by default, but the project may have been deleted or the
key revoked in GCP IAM).

Checks:

1. `ls -l /etc/ctc-manager/google-credentials.json` — file exists and is
   readable by the JVM user.
2. `jq . /etc/ctc-manager/google-credentials.json` — file is valid JSON
   with the standard service-account fields (`type`, `project_id`,
   `private_key_id`, `private_key`, `client_email`).
3. GCP IAM Console → Service Accounts → the principal → Keys — the key
   ID matches `private_key_id` AND has not been deleted.
4. If the JSON is fine but auth still fails: regenerate a new key in GCP IAM,
   replace the file, and restart the app.

### "Sheet not found" appears for a sheet I just confirmed exists

**Most likely cause:** the URL pasted into the form was for a DIFFERENT sheet
than the one shared with the service account. The Google client returns 404
for any sheet ID the service account cannot READ — even if you (the human
operator) can read it from your browser.

Checks:

1. Open the sheet in your browser. URL bar — confirm the
   `/spreadsheets/d/<ID>/...` portion matches what you pasted.
2. Click **Share** in the sheet → confirm
   `<your-sa>@<project>.iam.gserviceaccount.com` appears with at least
   **Viewer** access. If not, add the service account, wait ~30 seconds for
   propagation, then retry.
3. If the share is correct but 404 persists: the sheet may have been moved
   to a different GCP project / Drive than the service account can see.
   Make a fresh copy of the sheet in a location both you and the service
   account own, then retry.

### "Access denied" but the sheet is "Anyone with the link can view"

**Most likely cause:** "Anyone with the link" sharing on a Google Workspace
sheet often restricts external service accounts. The shared link works for
HUMAN viewers (because they sign in with a Google account) but not for the
service account (which is a non-human principal).

Fix: in Google Sheets → Share → **explicitly add** the service account
principal email (`<your-sa>@<project>.iam.gserviceaccount.com`) with
**Viewer** permissions, alongside the "Anyone with the link" setting. Wait
~30 seconds, then retry.

### "Connection problem" badge persists across multiple retries

**Most likely cause:** rate-limiting by Google. The Sheets API quota
defaults to 60 read requests per minute per project. A driver-import preview
across 5+ tabs can briefly burst beyond this.

Checks:

1. Wait 60 seconds, retry. If a single retry succeeds, you've hit a
   transient quota burst — no action needed.
2. If failures persist: check GCP Console → APIs & Services → Sheets API
   → Quotas. If you're consistently over, request a quota increase OR
   throttle the import (run fewer tabs per preview).
3. If neither: the GCP service may be in a regional outage. Check
   [Google Workspace status](https://www.google.com/appsstatus). If the
   region is degraded, wait for resolution.

### Calendar event creation works, but the event has the wrong time zone

**Most likely cause:** the `GoogleCalendarService` hardcodes `Europe/London`
as the event time zone (see [`GoogleCalendarService.java`](../../src/main/java/org/ctc/dataimport/GoogleCalendarService.java)
field `TIME_ZONE`). League events are scheduled in London time.

If your league operates in a different time zone: change `TIME_ZONE` in
the service and add a corresponding regression test. This is a code change,
not an operator action.

---

**Last updated:** 2026-05-20 (Phase 91 / UX-01).
