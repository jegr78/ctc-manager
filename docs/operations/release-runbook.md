# CTC Manager — Release Catch-up Runbook

Audience: operator (`@jegr78`) of the CTC Manager release pipeline. This runbook covers
the **pre-merge catch-up procedure** for releases that were not produced automatically
by `.github/workflows/release.yml`. Phase 88 / REL-01 hardened that workflow so every
future milestone PR squash-merge to `master` auto-produces the corresponding
`vX.Y.0` artifact set; this runbook only applies to the two historically missed releases
**v1.10.0** (master @ `45aabfd0`) and **v1.11.0** (master @ `598d1431`) plus the cleanup
of the legacy short-form tags `v1.3`, `v1.5`, `v1.8`.

**Timing — must run BEFORE the v1.12 milestone PR squash-merges to `master`.** The
hardened workflow computes the next SemVer by reading the most recent `vX.Y.Z` tag
and bumping based on the Conventional Commit subjects since that tag. With the current
remote tag set ending at `v1.9.0`, a squash-merge of the v1.12 PR would trigger a
minor bump to `v1.10.0` — pointed at the v1.12 HEAD commit, embedding the wrong
version in the JAR + Docker image, and locking out the legitimate `--target 45aabfd0`
retroactive tag (the idempotency guard refuses to recreate a tag, see
`.github/workflows/release.yml` § "Idempotency guard"). Run Sections 2 + 3 first so
`v1.11.0` is the last tag when v1.12 merges; the workflow then bumps correctly to
`v1.12.0` against the v1.12 HEAD commit.

**Strictly remote-only operations.** Per the project's `feedback-no-local-git-tags`
memory rule, never run `git tag -a` locally and never `git push origin v…` — the
workflow + `gh release create` API call own the remote tag side. This runbook uses
`gh release create --target <SHA>` and `gh api -X DELETE` exclusively.

**Destructive-operation discipline.** Section 4 deletes refs on the public GitHub repo
(irreversible). Per `CLAUDE.md` § "Executing actions with care" the deletion loop halts
at each tag and requires the operator to type `yes` before sending the DELETE call.
**This loop MUST NOT be automated by an agent** — operator-only execution.

**Source of truth for the design:** see
`.planning/milestones/v1.12-phases/88-build-release-unblockers-yagni-sweep-doc-conventions-driver-/88-RESEARCH.md`
§ "REL-02 Operator Runbook" for the rationale behind each step.

**Cross-references:**
- `[[no-local-git-tags]]` — every step uses `gh` API calls, never local `git tag -a`.
- `CLAUDE.md` § "Subagent Rules" / "Executing actions with care" — destructive ops require explicit confirmation.
- `.github/workflows/release.yml` — the hardened workflow that auto-produces future releases (Phase 88 / REL-01).

---

## Section 1 — Prerequisites

Before starting:

1. **Personal access token(s)** — note that `gh` and `docker login ghcr.io` can (and on Personal Accounts often MUST) use different tokens:
   - **For `gh release create`**: any token with `repo` write access on `jegr78/ctc-manager`. A fine-grained PAT with `Contents: Read and write` works.
     ```bash
     gh auth status        # must report "Logged in to github.com" with green checks
     ```
     Re-authenticate with `gh auth login --scopes repo` if the token is missing or expired.
   - **For `docker push` to GHCR**: a **classic PAT** with `write:packages` scope. Fine-grained PATs **do not support `write:packages` for Personal Accounts** — they can read but not push package versions. You can verify the limitation with `gh api /users/jegr78/packages/container/ctc-manager`; HTTP 403 from a fine-grained token confirms you need a classic PAT for the docker login step. Create the classic PAT at GitHub → Settings → Developer settings → Personal access tokens → **Tokens (classic)** → Generate new token, scope `write:packages` (auto-selects `read:packages`).

2. **GHCR Docker login** for the operator account using the classic PAT from step 1:
   ```bash
   read -rs GHCR_TOKEN          # paste classic PAT, Enter (no echo)
   echo "$GHCR_TOKEN" | docker login ghcr.io -u jegr78 --password-stdin
   ```
   Expected output: `Login Succeeded`. On macOS Docker Desktop the credential is stored in the system Keychain via the `desktop` `credsStore`, so `~/.docker/config.json` only contains the pointer `"ghcr.io": {}` — that is the normal, secure state, **not** a sign of a missing credential.

3. **Free disk space** for two throwaway worktrees. Each Maven build of the historical SHAs takes ~1 GB under `/tmp/v1.10-build` and `/tmp/v1.11-build`.

4. **Historical SHAs reachable locally**:
   ```bash
   git rev-parse 45aabfd0   # expected: 45aabfd0e2629813c2c275877e5b3921d536eca5
   git rev-parse 598d1431   # expected: 598d1431b350f583478435248401128d1c21cf2e
   ```
   Both should print the full SHA. If either fails, run `git fetch origin` and re-check.

5. **Run from the project root** for the `gh` calls and from each `/tmp/v*-build` worktree for Maven + Docker (CWD discipline — see Pitfall 7 in the design research).

Throughout the runbook, the placeholder `<PROJECT_ROOT>` stands in for the operator's local checkout path (the user's repo lives at `/Users/jegr/Documents/github/ctc-manager`; substitute as needed).

---

## Section 2 — Retroactive v1.10.0 (master @ `45aabfd0`)

Create a throwaway worktree pinned at the v1.10 milestone tip, set the pom version, build the JAR with the correct version embedded, create the GitHub Release via `gh release create --target <SHA>` (which atomically creates the tag + Release page + uploads the JAR), build and push the Docker image to GHCR, then remove the worktree.

**Order matters.** `versions:set` MUST run BEFORE `package` so the JAR + Docker image embed `1.10.0` (not the working pom's SNAPSHOT). Each step asserts its outcome before continuing.

```bash
# Pin the historical commit in an isolated worktree.
git worktree add /tmp/v1.10-build 45aabfd0
cd /tmp/v1.10-build

# Set the pom version, then assert it landed.
./mvnw versions:set -DnewVersion=1.10.0 -DgenerateBackupPoms=false
grep '<version>1.10.0</version>' pom.xml \
  || { echo "FATAL: pom version was not updated"; exit 1; }

# Build the JAR (skip tests — historical commits already shipped through verify).
./mvnw -DskipTests package
ls -lh target/ctc-manager-1.10.0.jar \
  || { echo "FATAL: JAR not produced at expected path"; exit 1; }

# Back to the project root for the gh API call (CWD discipline).
cd <PROJECT_ROOT>

# Atomic remote tag + Release + JAR upload. Targets the historical SHA, not HEAD.
# IMPORTANT: --target requires the FULL SHA (40 chars). Short SHAs are rejected
# with HTTP 422 "Release.target_commitish is invalid" even though git resolves them.
gh release create v1.10.0 \
  --target 45aabfd0e2629813c2c275877e5b3921d536eca5 \
  --title "v1.10.0" \
  --notes-start-tag v1.9.0 \
  --generate-notes \
  /tmp/v1.10-build/target/ctc-manager-1.10.0.jar

# Build + push the Docker image with the same version tag.
cd /tmp/v1.10-build
docker build -t ghcr.io/jegr78/ctc-manager:1.10.0 .
docker push ghcr.io/jegr78/ctc-manager:1.10.0

# Clean up the worktree.
cd <PROJECT_ROOT>
git worktree remove /tmp/v1.10-build
```

Quick verification:

```bash
gh release view v1.10.0 | head -20
docker pull ghcr.io/jegr78/ctc-manager:1.10.0
```

Both commands must succeed.

---

## Section 3 — Retroactive v1.11.0 (master @ `598d1431`)

Identical pattern with one extra precondition: `git fetch --tags origin` BEFORE `gh release create v1.11.0` so the locally-known v1.10.0 tag (created in Section 2) resolves for `--notes-start-tag v1.10.0` and the auto-generated notes span the right commit range.

```bash
# Pull the v1.10.0 tag that Section 2 just created on origin into the local tag set.
cd <PROJECT_ROOT>
git fetch --tags origin
git rev-parse v1.10.0 \
  || { echo "FATAL: v1.10.0 not yet on origin; rerun Section 2 first"; exit 1; }

# Pin the v1.11 milestone tip in an isolated worktree.
git worktree add /tmp/v1.11-build 598d1431
cd /tmp/v1.11-build

# versions:set BEFORE package — same Pitfall 3 ordering as Section 2.
./mvnw versions:set -DnewVersion=1.11.0 -DgenerateBackupPoms=false
grep '<version>1.11.0</version>' pom.xml \
  || { echo "FATAL: pom version was not updated"; exit 1; }

./mvnw -DskipTests package
ls -lh target/ctc-manager-1.11.0.jar \
  || { echo "FATAL: JAR not produced at expected path"; exit 1; }

# Back to project root for the gh call.
cd <PROJECT_ROOT>

# --target requires the FULL SHA (see Section 2 note on HTTP 422 with short SHAs).
gh release create v1.11.0 \
  --target 598d1431b350f583478435248401128d1c21cf2e \
  --title "v1.11.0" \
  --notes-start-tag v1.10.0 \
  --generate-notes \
  /tmp/v1.11-build/target/ctc-manager-1.11.0.jar

# Docker image.
cd /tmp/v1.11-build
docker build -t ghcr.io/jegr78/ctc-manager:1.11.0 .
docker push ghcr.io/jegr78/ctc-manager:1.11.0

# Clean up the worktree.
cd <PROJECT_ROOT>
git worktree remove /tmp/v1.11-build
```

Quick verification:

```bash
gh release view v1.11.0 | head -20
docker pull ghcr.io/jegr78/ctc-manager:1.11.0
```

---

## Section 4 — Legacy short-form tag cleanup

The repository carries pre-2026 short-form tags (`v1.3`, `v1.5`, `v1.8`) that have no corresponding `release:` commit on `master` and confuse the SemVer-sort lookup the hardened workflow uses (`git tag --sort=-version:refname --list 'v[0-9]*.[0-9]*.[0-9]*'`). Delete them via the GitHub git-refs API.

**This section MUST be run interactively by the operator. No agent automation.** Section 4b lists three explicit `gh api -X DELETE` commands — one per legacy tag — that the operator runs by hand. Each invocation is its own confirmation; skip a tag by not running its line.

### 4a. Live-inventory re-verification

Always re-verify the current remote tag set BEFORE deleting anything — the inventory may have shifted since this runbook was written.

```bash
gh api /repos/jegr78/ctc-manager/git/refs/tags | \
  jq -r '.[].ref' | \
  grep -E '^refs/tags/v[0-9]+$'
```

Expected output at runbook-authoring time (2026-05-19):

```
refs/tags/v1.3
refs/tags/v1.5
refs/tags/v1.8
```

`v1.6` and `v1.9` are already absent on the remote and do NOT need to be deleted. If the output does not match the expected three lines, stop and investigate — do not extend the loop below to delete unknown tags.

### 4b. Per-tag deletion — three explicit commands

**The "confirmation" is the act of running each command manually**, one at a time. No interactive prompt loop — interactive `read -p` is a bash builtin with different semantics in zsh (`-p` reads from a coprocess, not as a prompt string), so a portable loop would need shell-specific quoting and `</dev/tty` redirection. Three plain commands are clearer and shell-agnostic.

```bash
gh api -X DELETE /repos/jegr78/ctc-manager/git/refs/tags/v1.3
gh api -X DELETE /repos/jegr78/ctc-manager/git/refs/tags/v1.5
gh api -X DELETE /repos/jegr78/ctc-manager/git/refs/tags/v1.8
```

Operator notes:

- Each command targets a single tag — copy/paste/Enter is the confirmation. Skip a tag by simply not running its line.
- The commands use `gh api -X DELETE`, NOT `git push --delete origin <tag>`. Both work in principle but `gh api` keeps the operation on the remote API surface and matches the rest of this runbook's idiom.
- Successful delete returns HTTP 204 (no output). A tag that is already gone returns HTTP 422 — safe to ignore and move on.
- After the three commands, run the inventory check `gh api /repos/jegr78/ctc-manager/git/refs/tags | jq -r '.[].ref' | grep -E '^refs/tags/v[0-9]+$'` and confirm the output is empty.

---

## Section 5 — Post-runbook verification

```bash
gh release list --limit 20
```
Expected: `v1.10.0` and `v1.11.0` appear in the list with auto-generated notes.

```bash
gh api /repos/jegr78/ctc-manager/git/refs/tags | \
  jq -r '.[].ref' | grep -E '^refs/tags/v[0-9]+$'
```
Expected: empty output (legacy short-form tags gone).

```bash
docker pull ghcr.io/jegr78/ctc-manager:1.10.0
docker pull ghcr.io/jegr78/ctc-manager:1.11.0
```
Expected: both pulls succeed.

If any check fails, re-read the failing section, fix the cited precondition, and retry that section only. Sections 2-4 are independent and idempotent at the section level — re-running a section after a partial failure is safe.

---

## Section 6 — Future-proof releases

After Phase 88 / REL-01 hardening lands (`.github/workflows/release.yml` SemVer-strict tag sort + `fetch-tags: true` + parser + idempotency guard + dry-run gates), every subsequent milestone PR squash-merge to `master` automatically produces the `vX.Y.0` release artifact set:

- Annotated tag pushed by the workflow
- GitHub Release page generated with auto-notes
- JAR uploaded as a Release asset
- Docker image pushed to `ghcr.io/jegr78/ctc-manager:X.Y.0` and `:latest`

The operator does NOT run this runbook for future v1.X.0 releases. The runbook is reserved for retroactive catch-up of historically missed releases. The hardened workflow refuses to recreate an existing tag (idempotency guard fires `::error::Tag vX.Y.0 already exists. Aborting before build.` BEFORE the 19-minute build), so accidental re-runs are bounded.

If a future milestone PR squash-merge fails to produce its release, investigate the run before reaching for this runbook — most regressions will be fixable in the workflow itself.

---

**Last updated:** 2026-05-20 (Phase 91 closure). **Execution status:** Sections 2-5 executed successfully on 2026-05-20 against the v1.12 milestone branch before the PR squash-merge — v1.10.0 + v1.11.0 releases + GHCR images published, legacy short-form tags `v1.3`/`v1.5`/`v1.8` removed. Three runbook bugs discovered during execution were patched in the same session: (a) Section 1 PAT note now distinguishes fine-grained vs classic for `write:packages`; (b) Sections 2 + 3 `gh release create --target` requires the FULL SHA; (c) Section 4b replaced bash-only interactive loop with three explicit `gh api -X DELETE` commands (shell-agnostic). The runbook remains valid for future retroactive catch-ups — do NOT re-run Sections 2-4 for the already-published v1.10.0/v1.11.0 (idempotency guard / 422 errors).
