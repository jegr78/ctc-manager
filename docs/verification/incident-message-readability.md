# Incident message readability

Issue: #401. Canonical case IDs, report revisions, remote IDs and nonce inputs remain unchanged.

| Requirement | Production path | Positive test | Failure path | Runtime evidence |
| --- | --- | --- | --- | --- |
| Compact, stable, unambiguous reference and contextual title | Versioned disclosure projection | Complete UUID encoded without truncation; distinct UUIDs with a shared prefix; title capped at 100 UTF-16 units | Missing or unsupported frozen presentation version rejected | Focused projection tests and CI |
| Season, matchday, pairing and leg without race UUID | Disclosure capture freezes public captions | Caption includes actual season and leg, no invented track/date | Missing/half captions rejected; later renames cannot alter envelope | Disclosure integration tests |
| Clearly separated labels, complete original facts | Readable projection | Bold labels, exact replay text, ordered long Unicode chunks | Invalid Unicode normalized, no split surrogate pairs | Projection tests |
| Less prominent delivery metadata with exact recovery | Publication snapshot and Discord sender | Subtext/spoiler footer; unchanged canonical marker and nonce; exact adoption | Same marker with altered text or title remains unresolved | Sender and recovery integration tests |
| Historical delivery compatibility | Nullable immutable presentation version; backup restore | Null keeps original title, body, footer and chunk count | Persisted envelope cannot be upgraded; unsupported archive metadata rejected before wipe | Backup and migration tests, workflow acceptance |
| Existing confidentiality boundaries | Existing sender authorization and destination verification | Neutral receipt and private/steward role checks retained | Foreign author, destination, role or conflicting remote evidence denied | Existing sender permission tests and independent security review |
| Replay guidance | Existing incident modal | Existing `00:13.500` hint retained | Submitted replay text is never parsed or rewritten | Existing modal contract tests |

The new `INC:` display namespace is disjoint from canonical and legacy `INC-` IDs. The reference uses all 128 UUID bits in 22 URL-safe Base64 characters. It is case-sensitive and never uses a colliding UUID prefix. Older non-UUID or noncanonical-case references retain their complete text. New disclosure envelopes select presentation version 1; existing envelopes retain the legacy representation, including any partially delivered operation. No existing Discord message is edited or resent by the migration.

Local focused verification passed 117 unit cases and 96 integration cases, with no failures or skips. Integration scope included real Discord REST request serialization, exact recovery, H2 constraints and native 48-table backup/restore. The replay-time hint remains covered by the existing modal contract suite. Complete inventory, MariaDB, browser, coverage and security verification belongs to the exact-head CI before merge.

The independent read-only security review found a possible collision between compressed and legacy IDs. The final implementation uses a disjoint `INC:` namespace and preserves lowercase legacy UUID spellings; the reviewer verified the fix with no remaining findings. Regression coverage also rejects a manually ambiguous remote title with another canonical footer. Guard falsification and final CI results are recorded in the PR evidence.

Discord syntax follows its official [Markdown formatting reference](https://support.discord.com/hc/en-us/articles/210298617-Markdown-Text-101-Chat-Formatting-Bold-Italic-Underline) and [spoiler reference](https://support.discord.com/hc/en-us/articles/360022320632-Spoiler-Tags). The production request test pins `-# ||[CTC … PUBLIC:0]||` and empty allowed mentions. No live test case or existing Discord message was edited during verification.
