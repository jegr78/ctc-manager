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

- Real rootless qualification found an inherited unreadable working directory after sudo user switch. Added a failing behavioral test and changed the launcher to enter its private installation before Docker access. Existing graphics use the JVM default timezone, so production explicitly sets Europe/Berlin. Full Maven verification was stopped and will restart on the corrected committed head.

- Real image Chromium screenshot failed with exit 127. ldd confirmed missing libcups/libXcomposite/libXdamage/libXfixes/libXrandr in the previously released image. Dockerfile now installs version-matched Playwright system dependencies and performs a non-root screenshot during every image build. This runtime defect belongs to issue 295, not a deferred follow-up.

- Real CachyOS qualification: dedicated uid1002 rootless Docker reports cgroup2 and enforced memory limits. Health200, unauthenticated admin401 and authenticated admin200. Read-only Google fixture access and DB/file persistence after app recreation passed. courtside stayed healthy and CTC binds only127.0.0.1:9092.
- Dependency-corrected derivative of the exact released app image built on the box and completed its non-root Chromium screenshot in 2.9 seconds. The original image failed the same operation with missing shared libraries.
- The runtime is still a qualification instance with an empty synthetic DB and blank external integration settings. No source Mac data has been migrated or changed.

- Issue295 completed through merged PR303, squashed as e8308ca8. Full committed-head verification passed 2983 tests with zero failures/errors,91.59 percent coverage and zero SpotBugs findings. GitHub CI/CodeQL/Docker image checks all passed. Wiki production recipe updated in5ddb699. Parent294 remains open for its seven remaining children; no new issues created.
## Host provisioning and private ingress, issue 296

| Requirement | Production path | Positive evidence | Failure evidence | Runtime evidence |
| --- | --- | --- | --- | --- |
| Dedicated isolated account | setup-host.sh, lib/host.py | own user/ranges/directories accepted | privileged groups, duplicate UID/GID and foreign directories reject | actual uid1002, private owned paths |
| Rootless startup | systemd/docker-rootless.service | own daemon/linger/user unit | missing tools, wrong socket, no memory enforcement reject | real daemon, cgroup2, boot configuration |
| Preserve shared services | structured setup commands | idempotent own changes only | foreign 9443/9092 mapping refuses before changes | before/after courtside and Serve inventory |
| Tailnet-only HTTPS | explicit Serve 9443 | correct existing private route accepted | Funnel 9443 and unrelated mapping reject | TLS/auth via Tailnet and public-path denial |
| Local backup identity | expected UUID and private root | known mounted device accepted | absent/wrong mount refuses | mounted Btrfs UUID receipt |

Ruling: keep the Bash setup entrypoint small and use a Python helper to validate structured host
metadata and execute fixed native argv. Privileged operations stay explicit and testable. No
configuration is sourced as shell code, and no CTC-owned helper executes with host-root privileges.
Run host setup from the owner's verified source copy, outside the CTC user's writable deployment.


- Host tests established failure before implementation. Dedicated IDs, private directories, mount UUID, resource support and foreign listeners reject without changing shared services.
- Independent security review found subordinate ranges could include real host identities. The helper now checks named/numeric CTC ranges against all host UIDs/GIDs. Three negative cases failed before the correction and pass after it.
- Real setup exposed interface-scoped ss addresses. A production-shaped positive fixture failed before parsing the interface suffix and now passes. Public-address fixtures still reject.
- The host inventory includes total/available RAM and rejects less than 4 GiB total. Its new failure case was observed before implementation.
- Candidate code and independent security reviews found no remaining material findings. 29 host tests and 10 archive plus 3 release-publication tests pass. Legacy v1.21.0 release verification remains successful after inventory growth.
- Actual private Serve 9443 setup and repeated applications passed. Existing courtside 443 Funnel and Mailpit 8443 settings remain unchanged. Mac Tailnet DNS and certificate-verified HTTPS return health 200; unauthenticated admin 401 and authenticated admin 200. LAN 9092/9443 connection attempts time out.
- Restarting only the CTC rootless user daemon preserved the synthetic persistent file and recovered app health. courtside and Mailpit remained 200. Reboot/denied-policy/external-device drills remain final acceptance work; no real Mac data was changed or migrated.

- Expanded host checks to 35 cases and archive checks to 11, plus 3 publication cases. 34 individual falsifications triggered their intended assertions, including both host identity classes, numeric owner entries, shared-route preservation, lock ownership, legacy inventory and executable setup packaging. Every temporary mutation was restored from an exact stash snapshot.
- The Maven DeploymentScriptsTest bridge passed its complete script contracts. A deliberately isolated verify run then failed the repository-wide coverage gate because it ran only that one test; no gate was disabled. Final full verification remains required after upstream security changes land and are integrated.
- Final read-only host inventory passed with 15.52 GiB total and 6.86 GiB available RAM, the expected backup UUID, owned rootless daemon and private Serve 9443 ready.

- Primary GIDs must also be unique and non-root so numeric group aliases cannot bypass the group-name check. The negative case failed before implementation, passed after it and was individually falsified. The independent security recheck found no material findings. Final totals are 36 host cases and 35 individual falsifications; archive/publication cases remain 11/3.
- The exact published v1.21.0 image generated a Chromium screenshot as the app user inside its 2 GiB container. The SQL qualification row also survived the dedicated daemon restart. These remain synthetic target fixtures, separate from all Mac source data.
