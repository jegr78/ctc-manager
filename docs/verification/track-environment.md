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
contains its spoiler contents in the returned HTML. A feasibility probe parsed the first post and
nested layout exceptions without JavaScript or login. It is a community reference with gaps,
including SUGO, and cannot authorize circuit-wide assumptions. The current feature adds offline
manual maintenance and the common preset catalog. It introduces no live source importer or
automatic mapping. A future importer must preview concrete layout matches, retain provenance,
require explicit confirmation and conditionally protect manually edited values.

## Verification contract

| Behavior | Positive proof | Rejection proof |
| --- | --- | --- |
| Manual metadata | Native create/edit preserves order, identity and logo | Duplicate labels, stale versions/generations and missing CSRF do not mutate |
| Current layout choice | Actual JDA select/default serialization and handler confirmation | Wrong layout time cannot advance; final confirmation refreshes cached state |
| Historic compatibility | Exact receipt replay bypasses new metadata validation | New invalid time creates no revision |
| Recovery | Schema19 full restore retains values/version; schema18 defaults unknown | Malformed metadata is rejected before deleting live rows |
| Database integrity | H2 and explicit MariaDB migration/round trips | Fresh connections reject unknown rain codes and negative versions |
| Audit | Valid and unknown metadata remain readable | Malformed persisted times are reported without repair |
| Native UI | Desktop/mobile and JavaScript-disabled save/reload | Validation retains submitted values and current tokens |

Focused local checks use the bounded helper, one fork and at most 512 MiB Maven / 1024 MiB test
heap. Complete exact-head CI covers the full discovered inventory, coverage, database/browser
partitions, static security and Docker checks. Exact runs and guard falsification evidence are
recorded in the PR. The Box installation is unchanged.
