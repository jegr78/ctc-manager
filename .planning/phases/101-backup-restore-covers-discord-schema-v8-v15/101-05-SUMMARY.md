---
phase: 101-backup-restore-covers-discord-schema-v8-v15
plan: 05
status: complete
commits:
  - fcb7de07 fix(101): pin discord_post to end of export order — FK ordering on restore
  - 9b4b9865 test(101): add BackupDiscordFieldRoundTripIT — 13 per-field regression-fence (D-15)
  - 0edea941 test(101): byte-equality on DiscordGlobalConfig + DiscordPost across H2 + MariaDB (D-16)
requirements_addressed:
  - D-13
  - D-15
  - D-16
scope_deviation: yes — production fix for Plan 02 topo-sort gap (see "Scope Deviation")
---

# Plan 101-05 — D-15 + D-16 regression-fence + Plan-02 FK-ordering hotfix

## Outcome

Two new regression fences in place:

1. **`BackupDiscordFieldRoundTripIT`** (D-15) — 14 IT methods (13 per-field positive + 1 nullable negative). Each test seeds a single V8-V15 column with a deterministic T-prefixed value, exports → wipes → imports, then asserts the field round-trips byte-equal.
2. **`BackupRoundTripIT.givenSeededDiscord_…`** (D-16) — new @Test method in both `H2RoundTripTests` and `MariaDbRoundTripTests` nested classes. Seeds 1 `DiscordGlobalConfig` + 3 `DiscordPost` rows (spanning TEAM_CARDS / SCHEDULE / MATCHDAY_PAIRINGS post types), captures pre-export SHA-256 over the row JSON form, runs the full round-trip, and asserts post-import SHA-256 byte-equality on both entity tables.

The byte-equality fence revealed a latent restore-ordering bug that Plan 02 did not anticipate (see "Scope Deviation").

## Scope Deviation — Plan 02 Topo-Sort Bug

Running the new byte-equality test surfaced a FK constraint violation:

```
Referential integrity constraint violation: "FK_DISCORD_POST_MATCH:
PUBLIC.DISCORD_POST FOREIGN KEY(MATCH_ID) REFERENCES PUBLIC.MATCHES(ID)
(UUID '054d1f64-...')"
```

Root cause:

- `DiscordPost` exposes its 5 FK columns (`match_id`, `matchday_id`, `race_id`, `season_id`, `phase_id`) as `@Column UUID`, NOT `@ManyToOne`. Plan 02 RESEARCH §RQ-2 + §Pitfall 3 chose this shape deliberately (operator-trust model, no JPA-managed associations).
- `EntityTopoSorter` only considers `@ManyToOne` / `@OneToOne` JPA-association edges. With no edges, `DiscordPost` has in-degree 0 → topo-sort may place it BEFORE `matches` / `matchdays` / etc.
- Restoring `discord_post` first triggers an FK violation on INSERT (V12/V14 declare the FK with `ON DELETE SET NULL`, which only handles deletes, not inserts).

Plan 02's `<truths>` claim "Discord entities appear early per RESEARCH §RQ-2" missed the inverse implication for restore ordering.

**Fix:** `BackupSchema.initializeExportOrder()` now post-processes the topo-sorted list to **pin `discord_post` to the end** of the export order. Wipe order is the reverse of the export order → `discord_post` is wiped first, which is the correct child-before-parent deletion order. Single-pass insert on the now-correct order succeeds.

A 2-pass attempt inside `DiscordPostRestorer` (mirroring `TeamRestorer`'s self-FK pattern) was tried first but does NOT work — Pass 2 still runs WITHIN the per-table restore call, before other tables are restored. The reorder is the cleanest layer for the fix.

The hotfix lives in commit `fcb7de07`; it is functionally a Plan-02 follow-up but is committed under Plan 05 because that's where it was discovered.

## Changes

| File | Change |
|------|--------|
| `BackupSchema.java` | New `pinDiscordPostLast(List<EntityRef>)` post-processing step; documented constraint. |
| `DiscordPostRestorer.java` | Reverts to single-pass INSERT; Javadoc updated to cite the BackupSchema pin contract. |
| `BackupDiscordFieldRoundTripIT.java` (new) | 14 @Test methods covering 13 V8-V15 fields + 1 nullable case. |
| `BackupRoundTripIT.java` | Imports + `seedDiscordFixture` static helper; autowires + new @Test method in both nested classes; new helper inserts 1 `DiscordGlobalConfig` row (load-or-create with null-guard) + 3 `DiscordPost` rows; new byte-equality @Test asserts SHA-256 byte-equal on `DiscordGlobalConfig` + `DiscordPost`. |

## Verification

- `./mvnw clean test-compile` — green.
- `./mvnw clean verify -Dit.test='BackupDiscordFieldRoundTripIT,BackupRoundTripIT,BackupSchemaGuardTest,BackupArchiveServiceReadIT,BackupLenientV1AcceptanceIT' -DfailIfNoTests=false`:
  - `BackupDiscordFieldRoundTripIT`: `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` (22.92 s).
  - `BackupLenientV1AcceptanceIT`: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` (24.01 s).
  - `BackupSchemaGuardTest`: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` (23.00 s).
  - `BackupArchiveServiceReadIT`: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0` (0.82 s).
  - `BackupRoundTripIT`: nested tests run `tests=8, errors=0, skipped=1, failures=0` — 1 skipped is the opt-in MariaDB tests (docker.available not set). The H2-side `givenSeededDiscord_…` byte-equality test passes in 0.823s. The `givenH2DevFixture_whenExportWipeImport_thenRowCountsEqualAndSampleHashesMatch` Race + SeasonDriver + Team byte-equality test (existing) is also green — the new fixture seeding did not regress prior assertions.
  - JaCoCo coverage at 75% — expected for a targeted-test run; phase-end `clean verify -Pe2e` is the authoritative coverage gate.

## Scope Fence Preserved

- All Flyway migrations untouched.
- Existing Race / SeasonDriver / Team byte-equality assertions in `BackupRoundTripIT` not modified — APPEND-only changes.
- `TestDataService` not modified — the dev fixture is sufficient for parent-entity setup; per-test mutations happen via repository save inside each IT method.
- No new external dependencies.
- No mocking — every IT exercises the real H2 path via `@SpringBootTest`.

## Next

Plan 101-06 — close the documentation deliverable (PROJECT.md wire-contract paragraph,
STATE.md flip, `docs/operations/discord-integration.md` DOCS-02 operator action, README
update for the new schema_version).
