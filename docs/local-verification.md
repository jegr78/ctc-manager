# Local verification

Use Python 3 on Linux or macOS and the repository Maven wrapper. Focused checks support edits; finalization needs the complete reviewed source, canonical verification and exact-head CI.

## Edit loop

Choose tests for the changed behavior and its failure cases. The helper prints its selected scope, source commit and exact command. It uses the standard unit, integration and browser runners without skip flags or direct test-plugin goals.

```sh
./scripts/test-feedback.py unit PickDeadlinePolicyTest
./scripts/test-feedback.py integration MatchdayPickWindowManagementIT --unit PickDeadlinePolicyTest
./scripts/test-feedback.py integration MatchdayPickWindowManagementMariaDbIT --unit PickDeadlinePolicyTest --docker
./scripts/test-feedback.py browser MatchdayPicksE2ETest --unit PickDeadlinePolicyTest
```

Integration and browser modes run the complete unit phase unless `--unit` explicitly selects associated unit cases. Browser mode uses the existing `e2e` profile; add `--integration ClassNameIT` when the change also needs an integration scenario. Selectors use normal positive Maven patterns, including method selectors. No shell evaluates a selector.

Warm the normal dependency/browser cache first. An optional `--offline` requires all dependencies already cached and fails if anything is missing. `--maven-repo /absolute/cache/path` selects an existing Maven dependency cache. Neither option changes the selected tests. Do not infer a speedup before measuring the same edit loop on the same host.

The helper archives prior test reports and performance markers before starting a focused run, retaining compilation caches. It reads every fresh JUnit XML and Failsafe summary. Counts and outcomes come from actual `testcase` elements as well as failure counters; nested groups with the same method name can have an undercounted suite header. Missing case elements and hidden case-level failures/errors/skips cannot certify success. A nonzero Maven exit, reported failure/error, unexpected skip, missing requested stage or malformed report yields failure. A successful later browser phase cannot hide an earlier integration failure.

Evidence is saved under ignored `.test-feedback/<timestamp>-<PID>/`: the Maven log with JVM option-value echoes omitted, actual suite/count summary, selected command, source identities and verdict. Old reports are retained under `previous-reports/`. Focused output is always labeled focused. It is not release or merge evidence.

## Source and resource boundaries

A focused edit can start from a dirty tree; its tracked diff and untracked source hashes are recorded. Changing the source while the run executes invalidates the result. Commit correct work before negative guard experiments. Mutate isolated copies, check that the intended invariant fails, and retain the correct committed source. Two predicates enforcing the same invariant are one property; a surviving redundant defense is not a demonstrated defect.

One per-user host lock serializes cooperating helper invocations across worktrees. It does not coordinate arbitrary manual Maven commands. Avoid starting other heavy JVM/container/browser runs concurrently. Do not terminate unrelated projects. The November 2026 reference host has eight CPUs and 15 GiB RAM; concurrent CTC and unrelated builds reached load 15 and 13–14 GiB swap. Such runs are useful verification but unsuitable as clean timing comparisons.

The helper requests a 1,024 MiB test/compiler heap through `JAVA_TOOL_OPTIONS` and a 512 MiB Maven heap through `MAVEN_OPTS`. These are per-JVM heap requests, not a total process-memory limit; native memory, metaspace, database containers and browsers need additional headroom. The current POM has no overriding heap argument. Explicit `--test-heap-mib` and `--maven-heap-mib` budgets are recorded. Choose a larger budget only from measured need and available host memory; a heap failure remains a failed run and never removes tests. No Spring cache size or fork count is changed.

Stopping the helper terminates its own Maven process group and records failure. It checks the group independently of the Maven leader and escalates stubborn descendants after a grace period. Interrupted work never becomes a successful receipt. It does not reset data or operate any running deployment.

## Finalization loop

Finish the code review, independent security pass and fixes before the expensive full run. Expose committed work and pending checks in a draft PR rather than leaving the tracker silent. Use focused checks for edits, then commit a clean final source and run:

```sh
./scripts/test-feedback.py full
```

Full mode invokes `./mvnw clean verify -Pe2e --no-transfer-progress -Ddocker.available=true`. Ambient test filters, skip/gate overrides and alternate goals are rejected before Maven starts. Full mode accepts only bounded platform/network options from Maven/JVM environment variables and project configuration; user-settings profile properties cannot disable or filter tests. The record lists accepted option kinds and property names, never their sensitive values. Set JDK 25 in the invoking shell: additional Maven RC files are not loaded by the helper, and Maven's base directory is fixed to the current repository. Environment `#` characters are literal; project JVM configuration is never treated as a shell-comment script. It refuses focused selectors and requires a clean committed tree. Real Docker/MariaDB and preinstalled browser dependencies remain necessary. Existing reports, coverage and static-analysis artifacts are archived; all three test categories must produce fresh passing reports, without skips. The existing 82% line minimum, complete SpotBugs/FindSecBugs evidence and Maven lifecycle must pass. A full local result still requires the normal reviewed exact-head CI before merge.

Run the complete lifecycle once after review for the final phase source, not after every small edit. A relevant source change requires new affected evidence and the repository's final checks. A Git-only rebase can reuse a completed local run only when the entire Git tree is identical, the equivalence is recorded and review/CI cover the new exact head. Never carry evidence across a changed tree by assuming the changed files are harmless.

Record source SHA, host/runner, dependency-cache state, suite counts, context/container starts, elapsed time and competing workload when comparing performance. Keep focused-class and whole-build timings separate. No test or assertion is disabled to improve the reported time.

## Workflow regression tests

`python3 -m unittest discover -s scripts/tests -p test_feedback_workflow.py` uses temporary Git repositories and a fake Maven executable that emits controlled real XML. It covers deferred failures, later-phase masking, stale artifacts, process failure/interruption, empty/skipped/malformed reports, canonical modes/gates, literal selectors, changed source and concurrent helper admission. `FocusedVerificationWorkflowTest` runs this fixture suite in the normal Maven unit lifecycle. These fixtures use no live application, network service or owner database.

## Measured focused examples

The workflow itself was exercised on Linux with 8 logical CPUs, 15 GiB RAM, warm Maven dependencies and preinstalled Chromium. The final unit harness on `4faa50d2` passed all 19 Python workflow fixtures through one ordinary Maven unit test in 28.064 s. A browser example on `3ab2665e` ran `TimeProgressionE2ETest`, associated `TyrePersistenceIT` and six seeder/cache-marker unit cases. All 6 unit, 11 H2 integration and 1 real Chromium case passed without failures/errors/skips in 1:31 Maven time. That command used `browser TimeProgressionE2ETest --unit DevDataSeederTest,ContextLoadCountListenerTest --integration TyrePersistenceIT --offline` plus the local Maven repository path.

The same helper also verified the two-mode startup comparison recorded in [Test startup overhead](test-startup-overhead.md), retaining all 6 unit and 22 H2/MariaDB cases. These are focused examples, not complete verification timings. The recorded commands, fresh XML and source identities distinguish their scope; final CI remains required.

A retained successful `BackupRoundTripIT` XML demonstrates the runner discrepancy: its header says 8 tests, but it contains all 10 passing cases, including same-named H2/MariaDB methods. The helper now counts all 10. Two additional workflow fixtures reproduce this undercount and case outcomes hidden by zero header counters; all 21 Python fixtures pass.
