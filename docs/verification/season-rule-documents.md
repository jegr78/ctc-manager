# Season rule documents

Issue #356. Both optional links belong to the owning season, including archived seasons.

| Requirement | Production path | Positive evidence | Rejection / absence evidence | Runtime evidence |
| --- | --- | --- | --- | --- |
| Optional HTTPS documents | Season, SeasonForm, SeasonManagementService, native season form | Distinct URLs survive save and edit | Unsafe or overlong URLs produce field errors without writes; blank clears | Focused DTO/service tests; CI native form browser |
| Persistence | V40, SeasonRepository | Both fields reload for two seasons | Legacy seasons remain null; unrelated settings preserved | H2 and MariaDB CI |
| Public owning-season navigation | SiteGeneratorService, site/layout | Two seasons keep different document anchors across season pages | Empty fields and global pages have no document anchors; community links remain | Generated HTML assertions and desktop/mobile browser screenshots |
| Explicit publication | Existing generation/snapshot publication flow | Snapshot retains generated links after metadata changes | Save does not generate, publish or notify Discord | Native form and immutable snapshot tests |
| Backup compatibility | Schema 18, preflight, restore, SeasonRestorer | Export/restore both URLs | Schemas 1–17 default absent links null; typed unsafe new values rejected before wipe and during execution | Archive unit contracts and H2/MariaDB round trips |
| Documentation | README, CLAUDE, Wiki, glossary | Per-season setup and generate/preview/publish described | Stored URL is not an immutable document copy | Review against delivered UI |

Verification policy: one bounded local test fork for relevant TDD checks; complete exact-head CI is the merge gate. No routine full Box build and no running installation changes.

## Evidence

Initial TDD: native form validation reported the intended eight assertion failures for unsafe/overlong URLs before adding the shared URL guard. The first cold validation attempt with a 256 MiB Maven heap exhausted Checkstyle's heap; it is not test evidence. The bounded follow-up used 512 MiB Maven and test requests with one test fork.

Focused green: 116 cases across SeasonFormTest, SeasonDocumentLinksTest, SeasonDocumentArchiveTest, SeasonRuleDocumentsTest, SeasonManagementServiceTest, SeasonRestorerTest and TemplateWriterTest. No failures/errors/skips. Database, actual generation and browser evidence are pending complete CI. No full local verification claimed.
