# Simple deploy implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `ctc-manager deploy [VERSION|latest]` updates the box in place with one update unit and automatic rollback; releases stop re-testing and stop committing to `master`.

**Architecture:** A new `deploy/lib/rollout.py` holds `deploy`, `restore`, `switch_release`, `install_host_files` and `remove_legacy`, built on `NativeRuntime` (`deploy/lib/recovery.py`) and `compose_spec` (`deploy/lib/runtime.py`). `update.py`, `live.py`, `migration.py`, the update journal and the Mac migration go. phpMyAdmin moves into `deploy/phpmyadmin`. The reference implementation is CLR's `deploy/lib/rollout.py` (`gitlab.com/community-league-racing/driver-index`).

**Tech Stack:** Python 3 standard library, bash launcher, rootless Docker Compose, systemd user units, GitHub Actions, Maven.

**Spec:** `docs/superpowers/specs/2026-11-07-simple-deploy-design.md`

## Global Constraints

- English code, docs and commit messages; Conventional Commits; no comment pollution (CLAUDE.md "No Comment Pollution").
- Unit format unchanged: `MANIFEST_FIELDS` and `ARTIFACTS` in `recovery.py:11-14` stay exact; update units are `recovery-update-<createdAt>-<id>`.
- `storageGeneration` stays the permanent volume prefix; no new generation is created.
- Health: `compose up -d --wait --wait-timeout 180`.
- `deploy` sets `outboundPaused: true` for the start and restores the previous value only after success; rollback and restore leave it `true`.
- `resume-integrations` keeps `--confirm installationId`.
- Deploy and backup wait up to 960 seconds for the lock; the phpMyAdmin start waits up to 960 seconds as well.
- A deploy never starts from CI, a timer or a hook.
- Tests: `bash deploy/tests/acceptance.sh` for each task; `./mvnw verify` before the pull request (CLAUDE.md "Before PR").

## Review Focus

- First deploy on the box: the 1.51.0 verifier rejects the new bundle and the new code must verify the 1.51.0 tree; a rollback in that first deploy must start 1.51.0 with its state files intact (Task 2, Task 6 `test_rollback_to_legacy_release_keeps_state_files`).
- Flyway migration detection: a failed migration leaves `success=0` rows; any history difference must count as migrated (Task 6 `test_failed_migration_counts_as_migrated`).
- Outbound: after a rollback the app runs with integrations paused, after success with the previous value (Task 6).
- The installed units call `.../ctc-manager --directory /srv/ctc-manager maintenance` until the deploy reinstalls them; a nightly run between switch and unit install must not fail (Task 4 keeps `maintenance` as an alias of `backup` for one release).
- phpMyAdmin stopped before a deploy or backup stays stopped; one that ran starts again even after a failure (Task 3).

---

### Task 1: Release without tests and without master commits

**Files:** `.github/workflows/release.yml`, `scripts/ci/release-version.sh`, `src/test/java/.../ReleaseScriptsTest.java`

- [ ] **Step 1: Tests.** In `ReleaseScriptsTest`: `docs`, `test`, `ci`, `chore`, `refactor`, `style` only give no release; `chore(deps)`, `fix`, `perf` give patch; `feat` minor; `!` or `BREAKING CHANGE` major; the resume and release-commit cases are removed. A test reads `release.yml` and asserts no `git push` to `master`, no `versions:commit`, no `./mvnw verify`, and a `./mvnw -B package -DskipTests` step.
- [ ] **Step 2: Run** `./mvnw test -Dtest=ReleaseScriptsTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** `release-version.sh` computes the version from commits since the last tag with the new table and drops resume and `next_snapshot`. `release.yml` sets the version with `versions:set -DgenerateBackupPoms=false` without committing, runs `package -DskipTests`, builds and pushes the image, creates tag and release with `gh release create --target <sha>`, then packages and publishes the bundle; steps 13, 17 and 18 of today's workflow go. Skip when the commit is no longer the `master` tip.
- [ ] **Step 4: Run** the same test. Expected: PASS.
- [ ] **Step 5: Commit** `ci: release without re-running tests or committing to master`

### Task 2: Bundle and verifier

**Files:** `deploy/lib/release.py`, `deploy/lib/recovery.py:193-221`, `scripts/ci/deployment-bundle.py`, `deploy/ctc-manager-launcher` (new), `ops/phpmyadmin/{service.py,config.user.inc.php,ctc-manager-phpmyadmin.service}` (new, from the box), `deploy/tests/test_bundle.py`

- [ ] **Step 1: Tests:** `test_tree_with_older_inventory_still_verifies`, `test_tree_missing_core_file_is_rejected`, `test_tree_with_unlisted_file_is_rejected`, `test_archive_with_newer_inventory_verifies`, `test_bundle_carries_phpmyadmin_and_launcher`.
- [ ] **Step 2: Run** `bash deploy/tests/release-bundle.sh`. Expected: FAIL.
- [ ] **Step 3: Implement.** `verify` and `installed_release` check files against the tree's own `manifest.json` plus a `CORE` set and safe path prefixes (`deploy/`, `ops/`); `FILES` drives only the build. `FILES` gains `lib/rollout.py`, the launcher and `ops/phpmyadmin/*`, loses `lib/migration.py`, `migrate-from-mac.sh`, `lib/update.py`, `lib/live.py`, `update.sh`, `recovery.sh`, `maintenance.sh`, `guides/migration.md`. The launcher refuses `--d*` and runs `exec /srv/ctc-manager/current/ctc-manager --directory /srv/ctc-manager "$@"`. `service.py` reads its config files from `Path(__file__).resolve().parent`; the unit runs `/srv/ctc-manager/current/ops/phpmyadmin/service.py` and its start waits up to 960 seconds for `.ctc-manager.lock`.
- [ ] **Step 4: Run** `bash deploy/tests/acceptance.sh`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(deploy): bundle phpMyAdmin and the home launcher, verify trees by their own manifest`

### Task 3: Remove journal, update, live restore and Mac migration

**Files:** `deploy/ctc-manager`, `deploy/lib/common.sh`, `deploy/lib/config.py`, `deploy/lib/recovery.py` (`main`), delete `deploy/lib/{update.py,live.py,migration.py,update.sh,recovery.sh,maintenance.sh}`, `deploy/migrate-from-mac.sh`, `deploy/guides/migration.md`, `deploy/tests/{test_update.py,test_migration.py,migration.sh,update.sh,native_update.py}`, `deploy/tests/test_recipe.py`, `deploy/tests/test_recovery.py`

- [ ] **Step 1: Tests** in `test_recipe.py`: `test_mutations_keep_no_journal` (no `state/operation.json` after `up`), `test_removed_operations_are_rejected` for `update-check update restore-check backup-check prune`, and a phpMyAdmin helper test `test_phpmyadmin_restarts_only_if_it_ran` against a fake `systemctl` for a new `phpmyadmin_stopped` context in `rollout.py`.
- [ ] **Step 2: Run** `bash deploy/tests/acceptance.sh`. Expected: new tests FAIL.
- [ ] **Step 3: Implement.** Drop `write_journal` and `config.py journal`; the launcher accepts `deploy backup restore resume-integrations status doctor up stop publish-site` plus `maintenance` as an alias of `backup`, all Python operations run through `rollout.py`; deploy and backup acquire the lock with a 960-second wait. Delete the listed files and their tests.
- [ ] **Step 4: Run** `bash deploy/tests/acceptance.sh`. Expected: PASS.
- [ ] **Step 5: Commit** `refactor(deploy): drop the update journal, live restore and Mac migration`

### Task 4: Backup command and retention

**Files:** `deploy/lib/maintenance.py`, `deploy/systemd/ctc-manager-backup.service`, `deploy/tests/test_maintenance.py`

- [ ] **Step 1: Tests:** `test_retention_keeps_three_newest_update_units` (update units do not count toward the day/month keeps), `test_backup_proves_and_prunes`, `test_update_units_are_named_recovery_update`, retention tests without `update.json` and `protected-recovery.json`.
- [ ] **Step 2: Run** `bash deploy/tests/maintenance.sh`. Expected: FAIL.
- [ ] **Step 3: Implement.** `retention_plan` partitions update units; `prune` folds into `backup`; protected-recovery logic goes; `backup` runs inside `phpmyadmin_stopped`. The backup unit gets `After=ctc-manager-site-publisher.service` (from the box drop-in `ordering.conf`) and runs `backup`.
- [ ] **Step 4: Run** `bash deploy/tests/acceptance.sh`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(deploy): one backup command, keep the three newest update units`

### Task 5: In-place restore

**Files:** `deploy/lib/rollout.py` (new), `deploy/lib/probe.py` (extract `import_database`, `restore_files`, `verify_database`, `verify_files` as functions), `deploy/tests/test_rollout.py` (new)

- [ ] **Step 1: Tests** with a fake runtime: `test_switch_release_updates_symlink_and_config_together`, `test_restore_recreates_volumes_and_restores_dump_data_and_secrets` (order: stop, rm containers, volume rm database and data, copy secrets, switch, db up, import, restore files, app up), `test_restore_refuses_unit_without_matching_release`, `test_restore_refuses_release_without_rollout`, `test_restore_keeps_outbound_paused`.
- [ ] **Step 2: Run** `python3 -m unittest discover -s deploy/tests -p test_rollout.py`. Expected: FAIL.
- [ ] **Step 3: Implement** `restore(root, unit, runtime_for)`; the version is validated as SemVer before building paths.
- [ ] **Step 4: Run** `bash deploy/tests/acceptance.sh`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(deploy): restore a unit in place`

### Task 6: Deploy with rollback

**Files:** `deploy/lib/rollout.py`, `deploy/tests/test_rollout.py`

- [ ] **Step 1: Tests:** success, older or equal version refused, `upgrade.json` minimum refused, UID change refused, database major refused without `--allow-database-upgrade`, backup device missing keeps the app running, failure before the backup changes nothing, `test_history_change_restores_unit`, `test_failed_migration_counts_as_migrated`, failure without history change switches back, failed rollback leaves the app stopped and names `restore --unit`, interrupt rolls back, drill only on the running version, `latest` picks the highest version, outbound restored after success and kept paused after rollback, phpMyAdmin restarted last, `test_rollback_to_legacy_release_keeps_state_files`, Docker output to `state/deploy.log`.
- [ ] **Step 2: Run** the rollout tests. Expected: FAIL.
- [ ] **Step 3: Implement** `deploy(root, version, allow_database_upgrade=False, drill=False, runtime_for, fetch, download, home)` as in the spec. Read the history with `NativeRuntime.query` and the `flyway_schema_history` select including failed rows; call `preflight` only after `up`. Install signal handlers (SIGHUP ignored, SIGTERM raises) and restore them afterwards; run the script on the importable module.
- [ ] **Step 4: Run** `bash deploy/tests/acceptance.sh`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(deploy): one-command deploy with automatic rollback`

### Task 7: Host files and conversion

**Files:** `deploy/lib/rollout.py`, `deploy/tests/test_rollout.py`

- [ ] **Step 1: Tests:** units, phpMyAdmin unit and launcher installed with modes 0600/0700, symlink targets refused, backup drop-in `phpmyadmin.conf` and `ordering.conf` removed; `remove_legacy` deletes other generations' volumes, `secrets/<gen>` directories not referenced by the config, the legacy state files, `/srv/ctc-manager/phpmyadmin` and the marker, Docker cleanup before files, failures only warn; tests patch `Path.home`.
- [ ] **Step 2: Run** the rollout tests. Expected: FAIL.
- [ ] **Step 3: Implement** both functions.
- [ ] **Step 4: Run** `bash deploy/tests/acceptance.sh`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(deploy): install host files and remove legacy state`

### Task 8: Documentation

- [ ] ADR (next number under `docs/adr` or `docs/decisions`, wherever ADRs live), rewrite `deploy/guides/{updates,recovery,backups,operations}.md`, update `CLAUDE.md` sections named in the spec, `README.md`, `upgrade.json` notes, and the first switch from 1.51.0 (download with `curl` and the checksum from the release, extract with the bundle's own `release.py`, run the new launcher). Wiki: list the pages to update in the PR body.
- [ ] `bash deploy/tests/acceptance.sh` and `./mvnw verify`. Expected: PASS.
- [ ] Commit `docs: in-place deploy replaces generations`

### Task 9: Trial on the box (only on the operator's go)

- [ ] After merge and release: first switch, `deploy --drill-rollback`, permission rule for `ssh cachyos-ctc ctc-manager deploy *`, `status`, `doctor`.
