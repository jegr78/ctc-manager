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

- Issue 296 completed through PR306, merge 4870f9dc. Local committed-head full verification passed 2,987 tests in 7m37s, no failures/errors, 91.60 percent line coverage and no SpotBugs findings. GitHub CI/CodeQL/Docker checks passed. Wiki host setup documentation pushed as 827cd94. Native parent 294 remains open with six remaining children. No new issue was created. The topic branch was removed after exact merged-head comparison.

## Complete recovery units and isolated checks, issue 297

| Requirement | Production path | Positive verification | Failure verification | Runtime evidence |
| --- | --- | --- | --- | --- |
| Authenticated recovery mode | profile group, policy and existing security | recovery activates prod authentication | source flag cannot re-enable outbound | authenticated restored admin reads |
| No integration side effects | Discord/Google/GT7/download/YouTube boundaries | production-shaped real clients stop before transport | cached clients and startup/after-commit calls | isolated app with populated credentials |
| Supported-image admission | image capability and trusted release | supported immutable release accepted | historical/foreign image refused before start | actual pinned image and policy state |
| One shared installation context | launcher/common/config and recovery helper | same owned daemon and lock | foreign daemon, concurrent operation and stale lock reject | rootless native command fixtures |
| Consistent full capture | backup and private staging | exited app, dump, full data/config/secrets | failed stop/dump/full mount never publishes | actual MariaDB and file hashes |
| Preserve previous running state | standalone and update capture modes | standalone restores previous state | interruption and hold-stopped update path | bounded app lifecycle checks |
| Exact private inventory | recovery manifest and validation | complete hashes and provenance | corruption, traversal, links and wrong owner reject | immutable completed unit |
| Isolated import | fresh probe volumes and internal network | DB rows/files/schema reconcile | import failure, shell escape, egress and unsafe env reject | native MariaDB client sandbox |
| Read/graphics proof | authenticated internal HTTP probes | representative records and local graphic | wrong data/auth/health fails | isolated app with no host port |
| Safe live recovery | independent target generation | validated separate storage switch | no confirmation, changed source or failed probe refuses | old storage retained, integrations paused |
| Owned cleanup and evidence | run identity and private journal | only run-owned resources removed | failure leaves diagnostic evidence | no live/courtside resources touched |
| Release compatibility | exact runtime inventory and schema | supported earlier bundles still verify | partial/changed inventories fail | released deployment material |

Execution order: outbound policy and recovery profile; shared runtime/storage identity; manifest and capture; isolated restore-check; independent live recovery; packaging/runbooks and whole-branch review. Preserve existing app ZIP backup behavior and all earlier recipe guards. No Flyway migration or new app user/role model is required.

Ruling: use the existing Bash launcher and shared lock as the operator interface. Keep structured configuration, archive validation, recovery state and fixed native argv in Python helpers, following the established host setup pattern. Docker/systemd remain process supervisors. Centralize daemon/Compose construction so later update and maintenance commands cannot diverge.

Ruling: expose authenticated recovery capability information only in recovery-check mode and bind admission to the actual immutable image. An old image that ignores the profile must fail admission before it receives restored credentials. Preserve source Mac export provenance and migrate copies only into a supported target.

Pre-flight interfaces: config.load/private_path/atomic_json and common.sh lock/Compose are existing shared primitives. Recovery capture produces immutable unit identities and hold-stopped mode for 298. Private state stores local backup/restore evidence for 299. Storage generations must permit fresh 300 targets without deleting the current qualification volumes.

- RecoveryPolicyIT first failed at three intended assertions: startup/manual Discord calls reached the recording transport and the recovery profile used an unintended generated database. After the policy/profile implementation all five cases pass, including existing Basic authentication and configured-database selection. The recording transport prevents real network traffic even when guards are missing. Targeted verify still fails the global coverage threshold because it runs only selected tests; no threshold or check was disabled. Full verification remains the phase-end gate.

- Google recovery checks first failed for availability and attempted read/write calls. Both services now enforce policy even when their SDK clients are already configured, with a typed DISABLED outcome. All eight recovery cases and 55 selected unit/script tests pass; global coverage remains reserved for the full phase-end run. The constructor audit also corrected the inherited test-subclass call found by a reproducible clean test-compile failure.

- Catalog, remote-image and YouTube cases first failed at four intended assertions. All three network-reading services now enforce the same policy; YouTube preserves its configured local fallback. Twelve recovery cases and 56 selected unit/script cases pass. Local file upload behavior remains covered by the existing storage tests.

- The disabled-callback case first observed all three handlers starting publication work. Their early policy checks now pause before repository/publication work. Both policy unit cases and 17 selected integration cases pass, including the existing real transaction/proxy callback tests. The transport guard remains independent of this early pause.

- The capability HTTP case first failed with 404. Recovery-only authenticated actuator info now reports version 1 and the actual outbound-policy state. Fourteen recovery cases and three selected unit/script cases pass. Anonymous capability access remains 401. Historical image admission will additionally verify its immutable image capability before startup.

Recovery storage selection now uses the same structured Compose environment for the existing
launcher and recovery tooling. An optional validated storage generation selects independent
volumes; absent generation preserves existing volume names. `outboundPaused` accepts only a
JSON boolean. Three new behavioral recipe tests failed before implementation and all 21 recipe
tests pass afterward. Recovery configuration writes also flush the containing directory.

Recovery unit validation has exact inventories, streamed SHA-256 checks, private owner/type
checks, bounded strict JSON and a restricted data tar format. Capture orchestration restores
prior application state on handled errors and only publishes validated, flushed units by rename.
The backup destination checks the host receipt's UUID against the mounted backup device and
requires free space. A recovery environment preserves Basic/Discord/Google credentials but
replaces runtime, database and policy settings with trusted values. Historical images without
capability version 1 are refused. All 21 recovery tests, 21 recipe tests and 14 release tests pass;
real native capture and restore qualification remain pending. The first tests for newly added
Python interfaces failed on absent symbols, so their guard falsifications remain required before
final verification rather than being counted as semantic red evidence.

The candidate native recovery run passed on CachyOS rootless Docker with immutable local image
`sha256:10fd5b1755107ffcad40eb40022714a3b5dfd0b575b190de87a4cc7e4df14d38`.
It restored 32 application/fixture tables and files into a second fresh network/volume set,
verified authenticated reads and generated a new team card from restored SeasonTeam data.
MariaDB sandbox mode rejected a client shell escape and invalid SQL. This qualifies unreleased
code, not a fabricated published image/release. Public release admission stays strict.
The graphics test exposed Playwright's separate API driver installer path; the renderer now
prohibits automatic browser downloads before driver initialization. Its new unit assertion first
failed semantically and passes after the fix. Fourteen recovery ITs and the deployment script
contract pass; the targeted Maven run still fails only the expected global coverage threshold.
Live activation tests cover failed isolated proof, preserved predecessor configuration, paused
integrations, activation failure and a secret-flush failure before any active changes.

Native capture also passed against the existing synthetic Box installation while holding the
shared installation lock. It captured 32 tables and complete files/configuration/secrets with the
real source release 1.21.0, restored the app after a handled error, preserved originally stopped
state and exercised hold-stopped capture without an intermediate restart. The historical image
failed explicit full-restore admission. Source Mac data was not accessed.
Independent security review found cleanup aborting on resources registered before failed
creation. The fix distinguishes successful empty resource listings from inspect/daemon failures,
continues independent cleanup, journals remaining resources and writes passed evidence only after
cleanup succeeds. New negative tests reproduced all three prior failures. Self-review also found
a linked secret parent could be followed before validation and an oversized PAX header could be
allocated by tarfile before its metadata limit. Dedicated tests reproduced both paths; validation
now precedes directory creation and raw tar headers are bounded before tarfile reads metadata.
The current recovery suite has 51 passing cases, and the recipe suite has 23 including an actual
SIGTERM/retained-lock/interrupted-journal scenario. Final guard falsifications and canonical full
verification remain required.
The final native candidate run additionally forced app-container creation to fail before Docker
created its object. Cleanup removed every run-owned container, volume and network, and failure
receipts remained failed. The live path now checks an existing running source database for
unexpected writers before probing/activation, including recovery after loss of the app container.
The recovery suite now has 60 passing cases. Final release-tool qualification and live activation
against a genuinely published capable image remain post-merge acceptance work for #297.
All 18 Java guard falsifications reached their intended test assertions. The initial two Google
mutants removed the only checked-IOException call and therefore failed javac instead; clean
test-compile reproduced that diagnostic, and valid conditional bypasses then reached the intended
Google boundary assertions. Those compiler-only attempts are not counted. Every temporary edit
used a stash snapshot and was restored exactly. Deployment guard falsifications additionally
cover the expected parent mount and the effective destination filesystem independently, preventing
nested mounts from redirecting backup writes to another device. The effective destination check
also passed against the actual Box backup filesystem. The positive recovery suite has 66 cases.
Final targeted verification passed 66 recovery cases, 24 recipe cases, 15 release/publisher cases
and 36 host cases. All 53 deployment guard falsifications and all 18 Java falsifications reached
intended assertions after restoring each exact snapshot. The independent security re-review of
5f6fb371 found no remaining material findings. The only upstream advance is the v1.22.0 release
and subsequent pom version bump to 1.23.0-SNAPSHOT. Integrate that base before the canonical full
build. Post-merge #297 acceptance still includes a genuinely published capable-image recovery
unit and live activation; no real Mac cutover is claimed here.
The first canonical build on 36a6543a was deliberately stopped with SIGTERM before completion
when the final acceptance audit identified a missing restored-secret proof. It is not counted as
passing verification. Two new credential-proof tests first failed at five intended assertions:
healthy/authenticated pages alone did not prove readable exact Google bytes or the recreated
MariaDB root login. The probe now checks Google SHA-256 through the actual app user/container
and validates the restored root password with fixed SELECT 1 only. Imported SQL still uses the
unprivileged app database account. All 68 recovery cases pass; native credential qualification,
three added guard falsifications and a fresh complete canonical run remain pending.
Native run07 passed the restored-credential proof, full domain/file/graphic recovery and failed
container-creation cleanup. Its output reports credentialsVerified=true without credential values.
The root proof now explicitly uses TCP to avoid Unix-socket authentication substituting for a
password check. A dedicated bypass-model assertion first failed and passes after forcing TCP.
Native run08 additionally changes only the synthetic root secret after initialization, requires
login rejection, restores it and repeats complete recovery. Its result is still pending here.
Native run08 passed all restored-secret checks, rejected the intentionally wrong root password,
restored 32 tables/files and generated the domain team card. It also rejected sandbox escapes and
invalid SQL, cleaned all resources after failed container creation and left production untouched.
All 57 deployment guard falsifications and 18 Java falsifications reached intended assertions;
the tree was restored after each. Final targeted suites pass 68 recovery, 24 recipe, 15
release/publisher and 36 host cases. Independent security review of 6a9422c5 found no material
findings. The canonical full-build receipt belongs in the PR for the committed final head.
Post-release capable-image admission/live activation remains #297 acceptance work before closure.
The enriched default-branch dependency graph subsequently surfaced four Connector/J advisories
against Spring Boot 4.1.2's managed driver 3.5.10. GitHub identifies 3.5.11 as patched for
GHSA-h599-m3w5-9m7x, GHSA-4jq2-8v3c-97w8, GHSA-fhx5-2xqw-2cgm and
GHSA-grfq-r75j-rw9x. Upstream release: https://github.com/mariadb-corporation/mariadb-connector-j/releases/tag/3.5.11.
The runtime-version regression test first failed on the actual resolved 3.5.10 artifact. An explicit
mariadb.version property and dependency version now select 3.5.11 independently of the Boot BOM
and expose the connector directly to Renovate. The test accepts later minor and major versions.
Targeted security/script/renderer units and all 14 recovery ITs pass with the patched dependency;
the subset lifecycle still stops only at the unchanged global coverage threshold. The packaged
JAR contains mariadb-java-client-3.5.11.jar. Native qualification and canonical verification are
repeated for the changed runtime dependency before rollout.

The patched-driver native run09 passed the same complete recovery, credential, graphic and
failed-creation cleanup checks against MariaDB 13 using candidate image
sha256:ac6f67e6c1110cd1dfec4aeb63db53e3fccc393aed4b7b3d5f2bb6083145f2f3.
The vulnerable 3.5.10 dependency was independently reintroduced after the correct commit; its
runtime-version test failed at the intended assertion and the exact snapshot was restored.
Native Renovate44.198.3 under verified official Node24.22.2 resolves the new property to3.5.11 and
offers stable4.0.0 as a major update. No upper version constraint or major approval gate was added.
The updated runtime dependency requires fresh full verification and CI before the PR becomes ready.


### published recovery qualification corrections

The first v1.23.0 release attempt exposed an absent Chromium executable in the release runner.
The offline renderer intentionally disables browser downloads. Release verification now explicitly
installs Chromium from the selected project's classpath before testing, including resumed builds.
The corrected workflow published the CI-green master revision without changing its release gates.

The public backup command captured the real empty qualification stack with 32 tables. Its isolated
check exposed a mode-0700 data root that the initial synthetic archive did not cover. After chown,
the extraction helper's restricted root user cannot reopen that directory for sync. The final flush
now uses the application owner on a read-only mount without extra capabilities. Native file checks
verify the private root, exact inventory, credential reads, actual syncfs I/O failure and owned
cleanup. The Docker CI job loads its built image and runs those same checks. The full native fixture
also includes a private archive root.

The patched controller passed the genuine v1.23.0 unit in isolation with credentials verified,
outbound integration disabled, no host ports and a local Chromium graphic. This was explicitly an
unreleased host-controller qualification. Published v1.23.1 subsequently passed public backup,
isolated restore, independent-storage live recovery and explicit integration resumption. Final
checks confirmed exact 32-table metadata, restored secrets, paused integrations and preserved
previous volumes. The original Mac installation remains untouched.


## exact release updates, issue 298

| Requirement | Production path | Positive test | Failure test | Runtime evidence |
| --- | --- | --- | --- | --- |
| Select one released, unchanged candidate | update release staging and upgrade contract | exact release/version/hash/notes | absent assets, changed inventory, downgrade, DB major, disk/pull failure | published bundle and image selection |
| Freeze all accepted writes through activation | update coordinator with hold-stopped capture | ordered stop/capture/probe/activate trace | capture/probe error resumes unchanged source only | real MariaDB fixture writes and schema migration |
| Preserve recovery after possible migration | private update journal and protected unit receipt | exact recovery path and identities | unhealthy candidate remains paused/stopped, repeated invocation refuses | failed migrated fixture plus independent-storage restore |
| Share installation ownership and locking | launcher update routing | correct daemon/config, atomic selection | concurrent command, interrupted child/parent | public CLI and private receipt inspection |
| Keep source storage and credentials | immutable installation fields | same volume generation and secret paths | UID change, source identity drift | records/files/credentials after update |

Implementation follows the canonical specification and task sequence in #294/#298.
The first capable recovery release must pass #297 runtime acceptance before update finalization.

### simple local maintenance, #299

The owner reduced the current scope to local backups, two daily states and one monthly state.
No replication, Discord, alert hook or separate monitoring service is required. One daily systemd
service/timer and the existing manual doctor are sufficient. The monthly state is the first successful
backup of the newest Europe/Berlin calendar month. Preserve pending update recovery until a verified
backup of the active image exists. Continue the real Mac migration after local acceptance.

| Requirement | Production path | Positive test | Failure test | Runtime proof |
| --- | --- | --- | --- | --- |
| Local backup without extra configuration | maintenance/capture/restore-check | capture then exact isolated proof | mount, capture and proof errors stay incomplete | scheduled local backup and local journal |
| Two daily and one monthly state | retention plan | exact counts and Berlin month boundary | unknown/corrupt/foreign units and failed operation refuse deletion | dry run and protected update unit |
| Manual status and one timer | doctor/systemd backup pair | fresh local proof and persistent daily schedule | stale proof, missing mount and competing lock | actual timer invocation and failed service log |
| Published bundle compatibility | release inventory | current29 and previous32 inventory | unknown or mixed archive rejected | genuine previous source/new controller update |

## native Mac migration

The initial cutover reuses the complete recovery tools and requires the empty paused qualification installation. Source export preserves native provenance separately from the supported migrated unit. No migration daemon or account system is added.

| Requirement | Production path | Positive test | Refusal and runtime evidence |
|---|---|---|---|
| Supported frozen source | Native exporter and existing global observer | Native 13.0 / schema 6, literal environment overlay | Unsupported schema, wrong paths, missing secrets, hidden or active writers and enabled events fail closed; actual existing observer verified without grants |
| Complete sealed transfer | SQL, normalized file archive and original private configuration | Exact source manifest and artifact hashes | Changed payload, unknown entries and links refused before target containers start |
| Target migration | Existing isolated Probe with a genuine release | Original-column projections and exact files, actual Flyway 6 to 21 | Internal network, no host ports, outbound disabled; source and live target unchanged |
| Supported full recovery | Migrated unit and existing restore-check | Real authentication, credentials and team-card rendering | Exact source/unit proof required; source app version remains unknown when unrecorded |
| Single writer | Authority receipt and activation boundary in full restore | Paused target with original data, then separate backup and acceptance | Conflicting authority and existing real target refused; before-write failure permits unchanged source, after-write failure requires complete reverse migration |

Actual preparation restored the native data into 31 target tables and independently restored a later synthetic team write back onto the Mac. These isolated checks do not mark the production cutover complete.
