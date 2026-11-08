# Discord pick weather selection

Issue #394 adds explicit Preset or Custom weather to new private drafts. The
existing string storage, schema 19, backup format and historic hashes remain
unchanged. No credentials, bot identity, public posting or deployment limits change.

| Requirement | Implementation | Verification |
| --- | --- | --- |
| Preset selects one real code | PickWeatherSelection, PickWeatherWizard | Reject missing, multiple, unknown codes and Preset Random |
| Custom has 6–8 ordered positions | Immutable ordered list and independent pickers | 6, 7 and 8; repeats and `?`; reject shorter/longer sequences |
| Layout rain policy | Choice metadata and locked TrackEnvironmentService admission | Unknown/dry exclude rain; changed rain policy rejects confirmation |
| Discord limits and privacy | Paged single-code menus and existing private transport | Actual SDK pages of 2, 3, 25 and 8 options; one selection, ephemeral, no mentions |
| Replacement and single-slot changes | Recognized defaults, keep buttons, review slot menu | Retain mode/count/codes and unrelated draft values; edit one position |
| Only explicit confirmation writes | Existing admitted submission service | Real Gateway eight-slot persistence, durable replay, changed-policy rejection |
| Historic compatibility | Existing raw field and early receipt replay | Unsupported text is retained in history; no invented replacement defaults; graphics retain order |

Focused policy feedback first passed 43 cases. The complete focused unit selector
then passed 184 cases with no failures, errors or skips, using 512 MiB Maven and
test heaps and one fork. Early compilation exposed a missing import, confirmed by
a clean compilation diagnostic before correction. Initial adapted dialog tests
exposed outdated summary expectations and an attempted off-page selection. The
corrected tests navigate the real page before selecting a code. No test was disabled.

Complete reviewed exact-head CI, transactional H2/MariaDB verification, independent
security review and individual guard experiments are required before merge. Local
selected checks are not release verification. No routine full local Box build runs.

Fifteen individual isolated guard experiments each failed at the intended assertion
when its protection was removed. They cover counts, Preset single choice, Random
scope, known codes, duplicate/order preservation, rain policy, canonical input,
captured-page membership, current-layout checks, replacement defaults, single-slot
editing, final Gateway admission and the locked layout policy. Source files were
never mutated by these experiments; one bounded JVM ran at a time under the host
feedback lock. The database guard used one synthetic H2 layout and a 1024 MiB heap.

Independent review caught three stale Gateway test assumptions after the weather
input changed. Two fixed raw-text expectations now compare the actual payload;
the publication/privacy scenario starts with a distinct canonical eight-slot
Custom sequence. All secrecy, correction, replay and publication assertions remain.
