# Track layout time and weather options

Issue #388. Track records represent concrete layouts. Configure each layout separately under
**Tracks → Edit → Time & weather**. New tracks expose the same fields during creation.
Enter one verified time per line in game order and select verified rain, verified dry-only or
unknown. Empty times remain explicitly unverified. Lists allow 25 distinct labels of at most
100 characters. Save environment separately from identity and images. Reload when another editor,
a restore or a restart invalidates the form.

Discord uses these labels as a required single selection after track selection and keeps a valid
saved default during replacement. A saved value that is no longer offered needs an explicit new
choice for a replacement submission. Unknown layouts keep a labeled manual field. Final new
confirmation reads current locked metadata and rejects an obsolete or forged time. Original
submission receipts, revisions and management approval do not depend on later catalog changes.

The global reference contains S01–S18 and C01–C06. Verified rain layouts additionally show
R01–R08. Unknown rain does not imply verified dry-only. Weather text and slot ordering remain
unchanged. This change does not add a 32-option Discord select.

## Source investigation

The [GTPlanet reference](https://www.gtplanet.net/forum/threads/time-and-weather-settings-for-all-tracks-and-layouts-in-gran-turismo-7.420661/)
contains its spoiler contents in the returned HTML. The importer validates the first post,
including parent profiles and nested exceptions. It stores only factual layout/time/code lists in
a reviewed bundled reference, so startup, editing and picks work offline. Optional refresh uses the
fixed URL, no redirects, a 10-second timeout and a 1 MiB response limit, respecting outbound suspension.

Under **GT7 Sync → Preview layout environments**, compare current values with source defaults.
Select eligible layouts and use **Apply selected defaults**. Nothing is selected initially.
**Refresh from GTPlanet** loads a new preview without writing settings. The preview identifies the
source edit/retrieval dates and exact profile. It expires after 20 minutes and is bound to the
current manager and management generation. Application rechecks every selected row under a fresh
write lock, validates the whole selection, and commits through restore writer admission.

The reviewed mapping covers119 of123 catalog names. Le Mans and No Chicane both receive11 times
and rain. Spa Full Course has4 times and Spa24h has11; Tokyo South differs from Central/East.
SUGO, Nürburgring Endurance II and Nordschleife Tourist lack a reviewed mapping. The synthetic
Test Circuit also remains unmatched. There is no prefix or fuzzy matching. New/missing layouts
remain manually editable; another source is not required for profiles already explicit here.

Manual saves protect the whole layout environment, including an explicit empty/Unknown choice.
Source imports can fill pristine records or refresh unchanged imported values. Source provenance
and the previous imported fingerprint survive manual edits. Schema20 preserves these fields;
schema19 restores configured values as manual, while schemas<=18 restore pristine Unknown.
Historic pick strings, receipt replay, approval hashes, identities and logos retain their contracts.

## Verification contract

| Behavior | Positive proof | Rejection proof |
| --- | --- | --- |
| Manual metadata | Native create/edit preserves order, identity and logo | Duplicate labels, stale versions/generations and missing CSRF do not mutate |
| Current layout choice | Actual JDA select/default serialization and handler confirmation | Wrong layout time cannot advance; final confirmation refreshes cached state |
| Historic compatibility | Exact receipt replay bypasses new metadata validation | New invalid time creates no revision |
| Recovery | Schema20 retains source, values and override; schema19 protects configured values; schema18 defaults unknown | Malformed metadata is rejected before deleting live rows |
| Database integrity | H2 and explicit MariaDB migration/round trips | Fresh connections reject unknown rain codes and negative versions |
| Audit | Valid and unknown metadata remain readable | Malformed persisted times are reported without repair |
| Native UI | Desktop/mobile and JavaScript-disabled save/reload | Validation retains submitted values and current tokens |

Focused local checks use the bounded helper, one fork and at most 512 MiB Maven / 1024 MiB test
heap. Complete exact-head CI covers the full discovered inventory, coverage, database/browser
partitions, static security and Docker checks. Exact runs and guard falsification evidence are
recorded in the PR. Deployment and selected source application remain explicit operator actions.
