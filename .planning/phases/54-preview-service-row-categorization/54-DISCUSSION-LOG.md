# Phase 54: Preview Service & Row Categorization - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in 54-CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-24
**Phase:** 54-preview-service-row-categorization
**Areas discussed:** Season-Auto-Match, Preview-Datenmodell, Cross-Tab-Driver-Identität, Error-Encoding, Duplicate-Handling, SC-Deviation

---

## Gray Area Selection

| Option | Description | Selected |
|--------|-------------|----------|
| Season-Auto-Match | Tab-name "YYYY" → Season resolution; ROADMAP SC#3 references non-matching finder methods | ✓ |
| Preview-Datenmodell | Structure of DriverSheetImportPreview / TabPreview; impacts Phase-55 template rendering & re-fetch strategy | ✓ |
| Cross-Tab-Driver-Identität | Same new PSN in multiple tabs → single Driver on execute (SC#4) | ✓ |
| Error-Encoding | Plain strings vs enum; which ERROR categories to cover | ✓ |

**User's choice:** All 4 areas selected.

---

## Season-Auto-Match

### Q1: How should tab "YYYY" resolve to a Season when multiple seasons exist for that year?

| Option | Description | Selected |
|--------|-------------|----------|
| findByYear + uniq | New `findByYear(int)`. Exactly 1 → auto-select; 0 or ≥2 → null (admin overrides in Phase-55 dropdown). | ✓ |
| findByYear + lowest # | findByYear → pick lowest number when ambiguous. | |
| Strict Name-Match | Match only if `Season.name == tabName` (rarely true). | |

**User's choice:** findByYear + uniq
**Notes:** Safest — avoids wrong-season mis-assignments when admin forgets to override.

### Q2: What happens when year is ambiguous?

| Option | Description | Selected |
|--------|-------------|----------|
| null + UI-Hinweis | suggestedSeasonId=null + TabPreview.ambiguousReason (e.g. "Multiple seasons for year 2024"). | ✓ |
| null ohne Grund | suggestedSeasonId=null, no additional field. | |
| Most-recent pick | Pick season with highest number on ambiguity. | |

**User's choice:** null + UI-Hinweis
**Notes:** Transparent failure mode; Phase-55 UI gets enough info to render a helpful hint.

---

## Preview-Datenmodell

### Q1: How should the 6 buckets be structured per TabPreview?

| Option | Description | Selected |
|--------|-------------|----------|
| Typisierte Listen | TabPreview has 6 typed fields (List<NewDriverRow>, List<ConflictRow>, ...). Each row type carries only bucket-relevant fields. | ✓ |
| Map<Bucket,List> | Map<Bucket, List<PreviewRow>> where PreviewRow has all fields nullable. | |
| Flat + Bucket-Enum | Flat List<PreviewRow> with bucket-enum field; template groups. | |

**User's choice:** Typisierte Listen
**Notes:** Maximum type safety; simple Thymeleaf templates; straightforward assertions in unit tests.

### Q2: How does Phase-55 rebuild preview-state between preview submit and execute?

| Option | Description | Selected |
|--------|-------------|----------|
| Re-fetch (CsvImport) | Sheet is re-fetched and re-categorized on execute; user decisions as form-params. Mirrors existing CsvImportController. | ✓ |
| SessionAttributes | Persist preview in HTTP session. | |
| Hidden form fields | Full preview embedded as hidden inputs. | |

**User's choice:** Re-fetch (CsvImport)
**Notes:** QUAL-04 demands "no new parallel mechanism". Design-spec assumption of @SessionAttributes was wrong — actual pattern is stateless re-fetch.

---

## Cross-Tab-Driver-Identität

### Q1: How is "same new PSN in multiple tabs" represented in Preview + Execute?

| Option | Description | Selected |
|--------|-------------|----------|
| Naiv + Execute-Dedup | Each tab independently buckets PSN as NEW_DRIVER. Execute dedups by psnId at commit. | ✓ |
| First-NEW, rest-ASSIGN | First tab → NEW_DRIVER; subsequent → NEW_ASSIGNMENT referencing a pending driver. | |
| Cross-Tab Summary | Extra summary block above tabs. | |

**User's choice:** Naiv + Execute-Dedup
**Notes:** Keeps preview stateless; matches the re-fetch execute pattern. SC#4 satisfied on commit.

### Q2: User accepts fuzzy in Tab 2023 but marks "new" in Tab 2024 for same PSN — what happens?

| Option | Description | Selected |
|--------|-------------|----------|
| Per-row unabhängig | Each tab's decision wins locally; two drivers may result — user owns the decision. | ✓ |
| Cross-tab einheitlich | Fuzzy-accept for a PSN propagates to all tabs. | |
| Validation-Error | Contradictions abort execute. | |

**User's choice:** Per-row unabhängig
**Notes:** Matches the form-per-row UX; no JS needed for cross-tab enforcement.

---

## Error-Encoding

### Q1: How should ERROR-bucket reasons be encoded in PreviewRow?

| Option | Description | Selected |
|--------|-------------|----------|
| Enum + EN-String | `ErrorReason` enum with hard-coded English `message()`. | ✓ |
| Plain String | `String reason` field with English text directly. | |
| Enum + i18n keys | Enum with messages.properties keys + Thymeleaf resolution. | |

**User's choice:** Enum + EN-String
**Notes:** Assertable in tests via enum value; UI is English-only per CLAUDE.md.

### Q2: Which ERROR categories are covered in Phase 54? (multi-select)

| Option | Description | Selected |
|--------|-------------|----------|
| BLANK_PSN_ID | Column A empty. SC#5 explicit. | ✓ |
| UNKNOWN_TEAM_CODE | Team short code not in DB. SC#5 explicit. | ✓ |
| BLANK_TEAM_CODE | Column C empty. Own reason code for clearer UX. | ✓ |
| DUPLICATE_IN_TAB | Same PSN multiple times in one tab. | ✓ |

**User's choice:** All four.

---

## Duplicate-Handling

### Q: How are duplicate PSNs in the same tab handled?

| Option | Description | Selected |
|--------|-------------|----------|
| Erste gewinnt | First occurrence buckets normally; rest → ERROR/DUPLICATE_IN_TAB with their own rawTeamCode. | ✓ |
| Alle als ERROR | All occurrences of a duplicated PSN go to ERROR. | |
| Last-write wins | Latest occurrence buckets normally, earlier discarded silently. | |

**User's choice:** Erste gewinnt
**Notes:** Preserves intent; admin sees which rows were dropped.

---

## SC-Deviation Handling

### Q: ROADMAP SC#3 calls for findByName/findByDisplayLabel — neither fits. How to handle?

| Option | Description | Selected |
|--------|-------------|----------|
| CONTEXT dokumentiert Abweichung | CONTEXT.md records the findByYear-based implementation; verifier accepts; ROADMAP SC#3 tightened after phase completes. | ✓ |
| ROADMAP jetzt updaten | Update ROADMAP.md SC#3 before planning. | |
| Beide Methoden doch impl. | Add findByName/findByDisplayLabel even though functionally useless. | |

**User's choice:** CONTEXT dokumentiert Abweichung
**Notes:** D-13 in CONTEXT.md is authoritative. Post-phase cleanup will adjust ROADMAP wording.

---

## Claude's Discretion

Items deferred to research / planning discretion (not asked):

- Internal data structure for "seen PSNs within a tab" duplicate check (Set vs Map).
- Exact record naming (e.g. `NewDriverRow` vs `NewDriverEntry`) — kept consistent across buckets but not user-locked.
- Whether to cache team / season lookups per-tab or query repeatedly.
- Test helper / builder shape for the 9+ given-when-then scenarios.

## Deferred Ideas

- Cross-tab fuzzy-decision propagation master checkbox.
- i18n of ErrorReason messages.
- Configurable fuzzy threshold (currently 0.8 in DriverMatchingService).
- SeasonDriverRepository finder verification — researcher confirms exact method signature.
- Post-phase ROADMAP SC#3 text correction (D-13).
