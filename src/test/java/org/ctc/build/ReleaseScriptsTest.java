package org.ctc.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs {@code scripts/ci/release-gate.sh} and {@code scripts/ci/release-version.sh} in a
 * temporary clone with a pushed master, and a fake {@code gh} that serves workflow-run and job JSON the way
 * {@code gh api --paginate --slurp} does, or fails like an API error.
 */
class ReleaseScriptsTest {

	private static final String CI = ".github/workflows/ci.yml";
	private static final String CODEQL = ".github/workflows/codeql.yml";
	private static final String REQUIRED = CI + ":build-and-test\n" + CI + ":docker-build\n" + CODEQL
			+ ":Analyze (java-kotlin)\n";

	@TempDir
	Path dir;

	private Path work;
	private Path bin;

	@BeforeEach
	void createRepository() throws Exception {
		Path origin = dir.resolve("origin.git");
		work = dir.resolve("work");
		bin = Files.createDirectories(dir.resolve("bin"));
		command(dir, "git", "init", "--quiet", "--bare", "--initial-branch=master", origin.toString());
		command(dir, "git", "clone", "--quiet", origin.toString(), work.toString());
		Path gh = bin.resolve("gh");
		Files.writeString(gh, """
				#!/usr/bin/env bash
				[ "$1 $2 $3" = "api --method GET" ] || { echo "unexpected gh call: $*" >&2; exit 2; }
				case "$4" in
				  repos/owner/repo/actions/runs) file=runs ;;
				  repos/owner/repo/actions/runs/*/jobs) file=jobs-$(basename "$(dirname "$4")") ;;
				  *) echo "unexpected path: $4" >&2; exit 2 ;;
				esac
				if [ -f "$FAKE_DIR/$file.fail" ]; then
				  echo "HTTP 502: Bad Gateway" >&2
				  exit 1
				fi
				printf '[%s]' "$(cat "$FAKE_DIR/$file.json")"
				""");
		Files.setPosixFilePermissions(gh, PosixFilePermissions.fromString("rwxr-xr-x"));
		Files.writeString(work.resolve("pom.xml"), pom("1.3.0-SNAPSHOT"));
		git("add", "pom.xml");
		commit("chore: initial commit");
		git("tag", "-a", "v1.2.3", "-m", "Release v1.2.3");
		push();
	}

	@Nested
	class Gate {

		@Test
		void givenAllRequiredJobsSucceeded_whenGated_thenRelease() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");
			greenRuns();

			// when
			var run = gate(sha);

			// then
			assertThat(run.exit()).as("gate exit code: %s", run.output()).isZero();
			assertThat(run.outputs()).as("gate decision").containsEntry("decision", "release");
		}

		@Test
		void givenFailedCiRunAndCancelledCodeqlRun_whenGated_thenSkipNamingEach() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");
			runs(run(1, CI, "completed", "failure"), run(2, CODEQL, "completed", "cancelled"));

			// when
			var run = gate(sha);

			// then
			assertThat(run.exit()).as("gate exit code").isZero();
			assertThat(run.outputs()).as("gate decision").containsEntry("decision", "skip");
			assertThat(run.output()).as("skip notice").contains(CI + "=failure", CODEQL + "=cancelled");
		}

		@Test
		void givenCodeqlStillRunningAndNoRunOfAnotherRequiredWorkflow_whenGated_thenSkip() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");
			runs(run(2, CODEQL, "in_progress", null));

			// when
			var run = gate(sha, REQUIRED + ".github/workflows/other.yml:job\n");

			// then
			assertThat(run.outputs()).as("gate decision").containsEntry("decision", "skip");
			assertThat(run.output()).as("skip notice").contains(CI + "=missing", CODEQL + "=pending",
					".github/workflows/other.yml=missing");
		}

		@Test
		void givenSuccessfulRunWithoutARequiredJob_whenGated_thenSkip() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");
			runs(run(1, CI, "completed", "success"), run(2, CODEQL, "completed", "success"));
			jobs(1, job("build-and-test", "completed", "success"), job("docker-build", "completed", "skipped"));
			jobs(2, job("Analyze (java-kotlin)", "completed", "success"));

			// when
			var run = gate(sha, REQUIRED + CI + ":dockerfile-noble-pin-guard\n");

			// then
			assertThat(run.outputs()).as("gate decision").containsEntry("decision", "skip");
			assertThat(run.output()).as("skip notice").contains("docker-build=skipped",
					"dockerfile-noble-pin-guard=missing").doesNotContain("build-and-test=");
		}

		@Test
		void givenOlderFailedRunAndNewerSuccessfulRun_whenGated_thenTheLatestRunCounts() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");
			greenRuns();
			runs(run(1, CI, "completed", "success"), run(0, CI, "completed", "failure"),
					run(2, CODEQL, "completed", "success"));

			// when
			var run = gate(sha);

			// then
			assertThat(run.outputs()).as("gate decision with a newer successful run").containsEntry("decision", "release");
		}

		@Test
		void givenMasterMovedPastTheRevision_whenGated_thenSkipAsObsolete() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");
			commitAndPush("feat: something newer");
			greenRuns();

			// when
			var run = gate(sha);

			// then
			assertThat(run.outputs()).as("gate decision for an obsolete revision").containsEntry("decision", "skip");
			assertThat(run.output()).as("skip notice").contains("master has moved on");
		}

		@Test
		void givenOnlyReleaseCommitsOfAnEarlierAttemptOnTop_whenGated_thenRelease() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");
			commit("release: v1.2.4");
			commitAndPush("chore: bump version to 1.3.0-SNAPSHOT [skip ci]");
			greenRuns();

			// when
			var run = gate(sha);

			// then
			assertThat(run.outputs()).as("gate decision with only release commits on top")
					.containsEntry("decision", "release");
		}

		@Test
		void givenWorkflowRunsApiFails_whenGated_thenTheGateFailsWithoutDecision() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");
			Files.writeString(bin.resolve("runs.fail"), "");

			// when
			var run = gate(sha);

			// then
			assertThat(run.exit()).as("gate exit code when the API fails").isNotZero();
			assertThat(run.outputs()).as("gate outputs when the API fails").doesNotContainKey("decision");
		}

		@Test
		void givenJobsApiFails_whenGated_thenTheGateFailsWithoutDecision() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");
			greenRuns();
			Files.writeString(bin.resolve("jobs-2.fail"), "");

			// when
			var run = gate(sha);

			// then
			assertThat(run.exit()).as("gate exit code when the jobs API fails").isNotZero();
			assertThat(run.outputs()).as("gate outputs when the jobs API fails").doesNotContainKey("decision");
		}

		private void greenRuns() throws IOException {
			runs(run(1, CI, "completed", "success"), run(2, CODEQL, "completed", "success"));
			jobs(1, job("build-and-test", "completed", "success"), job("docker-build", "completed", "success"),
					job("changes", "completed", "success"));
			jobs(2, job("Analyze (java-kotlin)", "completed", "success"));
		}

		private Run gate(String sha) throws Exception {
			return gate(sha, REQUIRED);
		}

		private Run gate(String sha, String required) throws Exception {
			return script("release-gate.sh", Map.of("SHA", sha, "REQUIRED_JOBS", required, "OWNER_REPO", "owner/repo"));
		}
	}

	@Nested
	class Version {

		@Test
		void givenFixSinceTheLastTag_whenVersionDetermined_thenPatchBump() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");

			// when
			var run = version(sha);

			// then
			assertThat(run.exit()).as("version exit code").isZero();
			assertThat(run.outputs()).as("version outputs").containsEntry("resume", "false")
					.containsEntry("new_version", "1.2.4").containsEntry("next_snapshot", "1.3.0-SNAPSHOT")
					.containsEntry("bump", "patch").doesNotContainKey("should_skip");
		}

		@Test
		void givenFeatAndBreakingChangeFooter_whenVersionDetermined_thenMajorBump() throws Exception {
			// given
			commit("feat: a feature");
			String sha = commitAndPush("refactor: rework the API\n\nBREAKING CHANGE: the old URL is gone");

			// when
			var run = version(sha);

			// then
			assertThat(run.outputs()).as("version outputs").containsEntry("new_version", "2.0.0")
					.containsEntry("bump", "major");
		}

		@Test
		void givenOnlyCiCommitsSinceTheLastTag_whenVersionDetermined_thenSkip() throws Exception {
			// given
			String sha = commitAndPush("ci: tune the workflow");

			// when
			var run = version(sha);

			// then
			assertThat(run.exit()).as("version exit code").isZero();
			assertThat(run.outputs()).as("version outputs").containsEntry("should_skip", "true")
					.doesNotContainKey("new_version");
		}

		@Test
		void givenEarlierAttemptPushedReleaseCommitAndTag_whenVersionDetermined_thenItResumesThatVersion() throws Exception {
			// given
			String sha = commitAndPush("feat: a feature");
			releaseCommit("1.3.0");
			git("tag", "-a", "v1.3.0", "-m", "Release v1.3.0");
			commitAndPush("chore: bump version to 1.4.0-SNAPSHOT [skip ci]");

			// when
			var run = version(sha);

			// then
			assertThat(run.exit()).as("version exit code: %s", run.output()).isZero();
			assertThat(run.outputs()).as("version outputs").containsEntry("resume", "true")
					.containsEntry("new_version", "1.3.0").containsEntry("next_snapshot", "1.4.0-SNAPSHOT");
		}

		@Test
		void givenTaggedReleaseCommitThatChangesMoreThanTheVersion_whenVersionDetermined_thenItFails() throws Exception {
			// given
			String sha = commitAndPush("feat: a feature");
			Files.writeString(work.resolve("Other.java"), "class Other {}");
			git("add", "Other.java");
			releaseCommit("1.3.0");
			git("tag", "-a", "v1.3.0", "-m", "Release v1.3.0");
			push();

			// when
			var run = version(sha);

			// then
			assertThat(run.exit()).as("version exit code for a forged release commit").isNotZero();
			assertThat(run.outputs()).as("version outputs").doesNotContainKey("resume");
			assertThat(run.output()).as("error").contains("is not only the pom.xml version change");
		}

		@Test
		void givenTaggedReleaseCommitWithAnotherPomVersion_whenVersionDetermined_thenItFails() throws Exception {
			// given
			String sha = commitAndPush("feat: a feature");
			Files.writeString(work.resolve("pom.xml"), pom("9.9.9"));
			git("add", "pom.xml");
			commit("release: v1.3.0");
			git("tag", "-a", "v1.3.0", "-m", "Release v1.3.0");
			push();

			// when
			var run = version(sha);

			// then
			assertThat(run.exit()).as("version exit code for a mismatching pom version").isNotZero();
			assertThat(run.outputs()).as("version outputs").doesNotContainKey("resume");
		}

		@Test
		void givenReleaseCommitWithoutItsTag_whenVersionDetermined_thenItDoesNotResume() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");
			releaseCommit("1.2.4");
			push();

			// when
			var run = version(sha);

			// then
			assertThat(run.outputs()).as("version outputs").containsEntry("resume", "false")
					.containsEntry("new_version", "1.2.4");
		}

		@Test
		void givenNonSemverTagAboveTheLastRelease_whenVersionDetermined_thenItIsIgnored() throws Exception {
			// given
			git("tag", "-a", "v9.9.9-rc1", "-m", "Release candidate");
			String sha = commitAndPush("fix: a bug");

			// when
			var run = version(sha);

			// then
			assertThat(run.exit()).as("version exit code: %s", run.output()).isZero();
			assertThat(run.outputs()).as("version outputs").containsEntry("last_tag", "v1.2.3")
					.containsEntry("new_version", "1.2.4");
		}

		private Run version(String sha) throws Exception {
			return script("release-version.sh", Map.of("SHA", sha));
		}
	}

	private void runs(String... runs) throws IOException {
		Files.writeString(bin.resolve("runs.json"), "{\"workflow_runs\":[" + String.join(",", runs) + "]}");
	}

	private void jobs(int runId, String... jobs) throws IOException {
		Files.writeString(bin.resolve("jobs-" + runId + ".json"), "{\"jobs\":[" + String.join(",", jobs) + "]}");
	}

	private static String run(int id, String path, String status, String conclusion) {
		return "{\"id\":%d,\"path\":\"%s\",\"status\":\"%s\",\"conclusion\":%s}".formatted(id, path, status,
				json(conclusion));
	}

	private static String job(String name, String status, String conclusion) {
		return "{\"name\":\"%s\",\"status\":\"%s\",\"conclusion\":%s}".formatted(name, status, json(conclusion));
	}

	private static String json(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	private static String pom(String version) {
		return """
				<project>
				  <parent><version>4.0.0</version></parent>
				  <artifactId>ctc-manager</artifactId>
				  <version>%s</version>
				</project>
				""".formatted(version);
	}

	private void releaseCommit(String version) throws Exception {
		Files.writeString(work.resolve("pom.xml"), pom(version));
		git("add", "pom.xml");
		commit("release: v" + version);
	}

	private String commitAndPush(String message) throws Exception {
		String sha = commit(message);
		push();
		return sha;
	}

	private String commit(String message) throws Exception {
		git("commit", "--quiet", "--allow-empty", "-m", message);
		return git("rev-parse", "HEAD").output().strip();
	}

	private void push() throws Exception {
		git("push", "--quiet", "--follow-tags", "origin", "HEAD:master");
		git("fetch", "--quiet", "origin");
	}

	private Run git(String... args) throws Exception {
		var command = new ArrayList<>(List.of("git"));
		command.addAll(List.of(args));
		var run = command(work, command.toArray(String[]::new));
		assertThat(run.exit()).as("git %s: %s", String.join(" ", args), run.output()).isZero();
		return run;
	}

	private Run script(String name, Map<String, String> env) throws Exception {
		Path output = Files.createTempFile(dir, "github-output", ".txt");
		var environment = new HashMap<>(env);
		environment.put("GITHUB_OUTPUT", output.toString());
		var run = command(work, environment, "bash", Path.of("scripts/ci", name).toAbsolutePath().toString());
		var outputs = new HashMap<String, String>();
		for (String line : Files.readAllLines(output)) {
			int separator = line.indexOf('=');
			outputs.put(line.substring(0, separator), line.substring(separator + 1));
		}
		return new Run(run.exit(), run.output(), outputs);
	}

	private Run command(Path cwd, String... command) throws Exception {
		return command(cwd, Map.of(), command);
	}

	private Run command(Path cwd, Map<String, String> env, String... command) throws Exception {
		var process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true);
		process.environment().putAll(Map.of("PATH", bin + ":" + System.getenv("PATH"), "FAKE_DIR", bin.toString(),
				"GIT_AUTHOR_NAME", "Test", "GIT_AUTHOR_EMAIL", "test@example.org", "GIT_COMMITTER_NAME", "Test",
				"GIT_COMMITTER_EMAIL", "test@example.org", "GIT_CONFIG_GLOBAL", "/dev/null", "GIT_CONFIG_NOSYSTEM", "1"));
		process.environment().putAll(env);
		Process started = process.start();
		assertThat(started.waitFor(60, TimeUnit.SECONDS)).as("%s finished", String.join(" ", command)).isTrue();
		return new Run(started.exitValue(), new String(started.getInputStream().readAllBytes(), StandardCharsets.UTF_8),
				Map.of());
	}

	private record Run(int exit, String output, Map<String, String> outputs) {
	}
}
