# CI test partitions

Complete CI is the merge verification authority. The shared Box runs bounded focused checks, not routine full builds. The opt-in full command remains `./mvnw clean verify -Pe2e -Ddocker.available=true`; CI distributes the same test inventory. A passing selected partition is not a complete verification result.

## Inventory and ownership

`prepare-tests` compiles once and uses JUnit Launcher discovery with the existing Surefire/Failsafe naming patterns and inherited tags. It records concrete methods, nested classes, parameterized templates and dynamic factories. Manually invoked tools outside the runner patterns, such as `StandingsGraphicPreview`, remain outside normal discovery. A test that matches a runner name but has incompatible tags fails planning rather than disappearing.

The generated plan assigns each root to exactly one group:

- `core` runs ordinary unit tests and integration classes without MariaDB dependencies.
- `mariadb-1` and `mariadb-2` each run one integration partition. Database dependencies are detected from class names and compiled superclass/container references. Mixed H2/MariaDB classes stay together.
- `browser` uses the existing single-fork browser execution and RANDOM_PORT isolation.

MariaDB roots are assigned longest-first to the lighter partition using `config/ci-test-durations.json`. Its source SHA/run identify the measurement; new classes default to 60 seconds until remeasured. Assignment is generated on every build, so new tests do not require a hand-maintained membership list. Refresh timings from complete green CI reports when a partition becomes uneven. Do not raise fork counts or share mutable databases to compensate for an imbalance.

## Complete verification

All workers download identical compiled classes from the same run. Planning, receipt creation and aggregation reject uncommitted tracked edits or untracked source files, including templates that a class digest cannot cover. Ignored build outputs remain allowed. Their evidence records the tested Git commit/tree, bytecode digest, Actions run/attempt, group, execution events and fresh XML. Each partition uses an explicit Maven lifecycle profile. The database/browser profiles bind only their assigned phases; the core profile retains the full unit phase. These profiles distribute work and do not certify a subset as a full build. Ordinary local Maven execution is unchanged.

A JUnit execution listener records exact IDs and outcomes. Every discovered leaf must finish successfully, and each discovered template/factory must actually execute successful child tests. Duplicate executions, failed/aborted/skipped outcomes, missing methods and unassigned classes fail the evidence check. Both fresh XML and event evidence are required, with equal actual case totals. Counts come from the actual `testcase` elements: the retained successful `BackupRoundTripIT` report has 10 cases despite a suite header of 8 because nested groups share method names. The checker rejects missing cases and case-level failure/error/skip elements even when header counters say zero. Exact JUnit event identities remain the inventory proof.

The required `build-and-test` job runs after all four workers. It rejects missing, failed, skipped or cancelled workers, missing/corrupt coverage inputs and incompatible class/source identities. It rechecks every execution receipt and the union of IDs. Only then does `verify -Pci-aggregate` merge all four JaCoCo execution files and run the existing report, 82% line check, SpotBugs and FindSecBugs guards. It checks source/bytecode identity again after verification. Coverage inputs belong to one exact compilation, not merely a matching branch name. See [JaCoCo merge](https://www.jacoco.org/jacoco/trunk/doc/merge-mojo.html).

Documentation-only changes retain the existing successful required status without starting code workers. A failed or missing path decision fails the aggregate. GitHub treats a skipped job as passing for branch protection, so expected code workers must explicitly report success. See [GitHub job conditions](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-jobs-with-conditions).

CodeQL, parallel Docker build, native recovery checks and workflow cancellation remain independent. The trusted release gate still requires the exact-source `build-and-test` aggregate, Docker checks and CodeQL. No shared JAR dependency serializes Docker behind the tests.

## Measurements and regression checks

`CiPartitionWorkflowTest` runs the Python artifact/job/assignment failure fixtures during the ordinary unit phase and checks required-job/release dependencies. `CiTestInventoryTest` exercises discovery against real compiled tests, including inherited/nested/template/factory cases, and the execution listener's exact outcome format.

Compare the CI critical path and the sum of job durations against the preceding complete run. Record runner allocation/queue time separately from test execution; four independently scheduled jobs may wait on account concurrency. Include cache state, actual context/container counts and all partition case totals. A target below 20 minutes remains a target until complete green CI demonstrates it. No Box or Mac database action is part of this workflow.

Artifact names include the Actions attempt, preserving prior evidence without upload-name collisions. Each expected group downloads by its exact attempt-scoped name into a fixed group directory. Repeat the complete CI workflow when verification must be rerun. Receipts intentionally cannot mix Actions attempts; a failed partition alone cannot reuse evidence from a different attempt.
