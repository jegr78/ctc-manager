# Deployment requirements and execution ledger

Parent: https://github.com/jegr78/ctc-manager/issues/294

## Production recipe and release bundle, issue 295

| Requirement | Production path | Positive evidence | Failure evidence | Runtime evidence |
| --- | --- | --- | --- | --- |
| Dedicated owned configuration | deploy/ctc-manager, lib/config.py | valid private JSON config | symlink, wrong permissions, missing secrets | doctor with dedicated rootless socket |
| Rootless daemon and memory enforcement | lib/common.sh | rootless info and memory limit | rootful daemon, wrong socket | isolated box daemon info |
| Private persistent prod stack | compose.yaml | parsed Compose and recreation | unsafe binding or missing digest | authenticated app, DB/files and Chromium |
| Serialized operations | lib/common.sh | owned lock released | competing live operation refuses | independent command fixture |
| Immutable release material | scripts/ci/deployment-bundle.py | archive validation | altered file, unsafe inventory, invalid digest | actual registry image digest |
| Release retry | release.yml | bundle regenerated from selected tag | no bypass of CI gate | resume existing GitHub release assets |

## Ledger

- Selected issue 295 first. No open PRs or parallel sibling checkout found. Remote base 9000fd7b.
- Work in the current workspace on feature/production-deployment as required by next-issue. Preserve untracked docs/reviews.
- Configuration is private JSON plus a Docker env file. No shell sourcing or eval of configuration.
- Follow the approved task order. No production cutover until the final backup and target restore have passed.

- Recipe tests failed before implementation and now pass, including real Docker Compose configuration parsing.
- Bundle tests failed before implementation and now pass for exact identities, deterministic retry, corruption and unsafe entries.
- Existing release-gate tests exposed BSD paste requiring an explicit stdin operand. Added the portable operand without changing release selection behavior.
- Credentials-init uses the selected app image and its actual ctc UID, so private host credentials remain readable inside the rootless app without making host files public.

- Independent security review: resolved P2 mutable MariaDB identity on release retry by pinning deploy/database-image and refusing changed existing assets. Resolved P2 socket ownership by lstat/type/UID checks inside a private runtime directory. Added behavioral retry and endpoint fixtures.

- Guard falsification passed independently for rootless daemon, memory enforcement, private secrets, socket type, socket owner, operation lock, port collision, archive checksum and immutable release assets. Each mutation failed at its expected assertion and was restored from a stash snapshot or verified clean HEAD.
- Fresh code review at 66c43fea: no material findings. Independent security recheck at 66c43fea: both P2 findings resolved, no remaining material findings.
- Runtime qualification on a real isolated rootless daemon, credential readability, persistent storage and Chromium remains pending before accepting issue 295.
