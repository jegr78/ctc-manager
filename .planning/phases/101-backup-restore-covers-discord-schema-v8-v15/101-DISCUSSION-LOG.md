# Phase 101: Backup/Restore covers Discord schema (V8-V15) - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-26
**Phase:** 101-backup-restore-covers-discord-schema-v8-v15
**Areas discussed:** GA-1 Scope of inclusion, GA-2 Cross-guild restore semantics, GA-3 Mechanism + SCHEMA_VERSION-Bump, GA-4 Test coverage shape

**Background discovery surfaced during codebase scout:**

- `MatchRestorer.INSERT_SQL` (line 32-35) covers only V1 columns. Grep across all 4 affected `EntityRestorer` implementations returned zero matches for any V8-V15 column name. Conclusion: silent data loss on round-trip for all V8-V15 fields on existing entities is a regression bug that exists in the current codebase. This finding drove the framing of GA-1 (silent-bug-fix is mandatory regardless of architecture decisions on new entities) and elevated GA-4 (regression-fence tests) from optional to load-bearing.

---

## GA-1: Scope of inclusion

| Option | Description | Selected |
|--------|-------------|----------|
| All three: (a)+(b)+(c) | Fields on existing entities + DiscordGlobalConfig + DiscordPost — Discord becomes fully first-class in backup scope. Aligns with v1.13 promoting Discord to first-class operator state. | ✓ |
| Only (a) + (b): Fields + DiscordGlobalConfig | Fix silent-data-loss + restore operator config. DiscordPost stays excluded — Re-Edit lifecycle is lost after restore (every Re-Post POSTs new instead of PATCHing). | |
| Only (a) + (c): Fields + DiscordPost | Fix silent-data-loss + preserve Re-Edit lifecycle. DiscordGlobalConfig stays out — operator manually re-types 9 fields after restore. | |
| Only (a): Fields-loss-fix only | Minimal-invasive: only the silent-NULL regression fix. Operator workload after restore: 9 config fields + every Re-Post-button across 11 post types. | |

**User's choice:** Alle drei: (a)+(b)+(c)
**Notes:** Consistent with v1.13's "Discord is first-class" thesis. Drives Phase 72 D-15 revision.

---

## GA-2: Cross-guild restore semantics

| Option | Description | Selected |
|--------|-------------|----------|
| Preserve-as-is, single-guild documented | Discord state 1:1 restored regardless of target guild. Operator owns source-guild = target-guild guarantee. Simplest mental model, documented in DOCS-02 runbook. | ✓ |
| Intelligent detection via guild_id comparison | Compare backup `DiscordGlobalConfig.guild_id` with current; mismatch → auto-wipe Discord IDs + yellow banner in preview. | |
| UI toggle in import preview | New checkbox 'Reset Discord state (cross-guild restore)', default false. More operator control, more UI surface, less magic. | |
| Always wipe on import | Discord IDs always NULL on import — defeats GA-1's (b)+(c) inclusion decision. | |

**User's choice:** Preserve-as-is, single-guild documented
**Notes:** Aligns with single-admin, single-league app design and DISC-FUTURE-04 (multi-guild explicitly out-of-scope).

---

## GA-3: Mechanism + SCHEMA_VERSION-Bump

**Sub-decision 1 — Mechanism for inclusion:** locked as Claude's-discretion default to **extend `BackupSchema` package filter** to `org.ctc.domain.model.*` OR `org.ctc.discord.model.*`. Rationale: preserves Phase 72 D-15's structural enforcement philosophy (no marker, no opt-in, no developer memory required) with the minimum possible change. Alternatives considered and rejected: `@Backupable` marker (philosophy reversal), entity-package move (breaks package-by-feature), explicit whitelist (drift risk).

**Sub-decision 2 — SCHEMA_VERSION bump policy** (asked via AskUserQuestion):

| Option | Description | Selected |
|--------|-------------|----------|
| Strict: only v2 accepted | Phase-74 invariant unchanged (strict equality). Pre-v1.13 v1 backups refused with clear error. Operator workflow: one-time fresh v2 export after v1.13 deploy. | |
| Lenient: v1 + v2 accepted, v1 = empty Discord sections | Importer accepts `manifest.json:schema_version IN (1, 2)`. v1 backup → Discord tables empty after restore. v2 backup → full round-trip. Slightly looser wire-contract strictness, but pre-v1.13 backups remain usable. | ✓ |
| Migration: v1 → v2 inline at import time | v1 backups treated as v2 with missing sections = empty arrays. More code complexity (`BackupImportService` v1 branch). Probably too much ceremony for single-admin app. | |

**User's choice:** Lenient: v1 + v2 accepted
**Notes:** Operator-friendly — pre-v1.13 backups remain restorable. Drives D-10 / D-11 in CONTEXT.md.

---

## GA-4: Test coverage shape

| Option | Description | Selected |
|--------|-------------|----------|
| Full first-class (Pflicht + Regression-Fence + Wide) | BackupSchemaGuardTest update + per-field round-trip assertion for all 13 V8-V15 fields + byte-equality on DiscordGlobalConfig + DiscordPost on H2 AND MariaDB-Testcontainer + lenient-v1-acceptance test. ~15-20 new test methods. | ✓ |
| Critical-Path (Pflicht + Silent-Bug-Fence, ohne Wide) | Pflicht updates + per-field regression on V8-V15 fields + byte-equality on new entities (H2 only). Lenient-v1-acceptance test once. ~10 new methods. | |
| Minimal (only Pflicht) | Only BackupSchemaGuardTest update + single happy-path round-trip for new entities. No per-field regression-fence. Risk of silent-NULL bug re-introduction. ~3 new methods. | |

**User's choice:** Full first-class
**Notes:** Consistent with the user's pattern of bold scope decisions for v1.13 Discord work.

---

## Claude's Discretion

The following are flagged in CONTEXT.md `### Claude's Discretion` for the planner / executor to decide:

- MixIn shape for `DiscordPost` (FK references via `@JsonIdentityReference`, `@JsonIgnoreProperties` conventions).
- MixIn shape for `DiscordGlobalConfig` (single-row entity, minimal).
- `EntityTopoSorter` cross-package FK verification — planner should grep / read to confirm Kahn handles `DiscordPost.match` → `Match` correctly when both ends are in the filtered entity set.
- Naming of new restorer classes (`DiscordGlobalConfigRestorer`, `DiscordPostRestorer`) + placement in `src/main/java/org/ctc/backup/restore/entity/`.
- MixIn package location (`src/main/java/org/ctc/backup/serialization/`).
- Lenient version-check implementation shape (`Set.of(1, 2).contains(v)` vs. `SUPPORTED_SCHEMA_VERSIONS` constant).
- D-10 V8-default-row planner action — if the V8 seed row is wiped by replace-all and not auto-resurrected by Flyway, add an explicit post-wipe seed step in `BackupImportService` for v1 imports.
- PROJECT.md "Backup Wire Contract (v1.10)" wire-contract paragraph update (Update-on-Triage discipline).

---

## Deferred Ideas

- **Cross-guild restore tooling** — admin-UI toggle for "Reset Discord state on import". Skipped by D-07; revisit in v1.14+ if deployment model moves to multi-guild.
- **`@Backupable` marker annotation** — explicit opt-in instead of package-filter inclusion. Rejected by D-08; entry point if finer-grained control surfaces.
- **`data_import_audit` inclusion** — Phase 72 D-15 exclusion preserved; entry point if operator wants restore-side audit history.
- **Inline v1 → v2 migration code branch** — rejected as unnecessary complexity given D-11 (v1 imports leave Discord empty already produces the right end state).
- **Encrypted backup ZIP at rest** — webhook_token + role/channel IDs are PII-equivalent after Phase 101. Operator filesystem-level mitigation today; v1.14+ phase for app-level passphrase-encrypted ZIPs.
