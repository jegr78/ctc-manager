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
 * temporary clone with a pushed master, and a fake {@code gh} that serves check-run JSON the way
 * {@code gh api --paginate --slurp} does, or fails like an API error.
 */
class ReleaseScriptsTest {

	private static final String REQUIRED = "build-and-test\ndocker-build\nAnalyze (java-kotlin)\n";

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
				if [ -f "$FAKE_DIR/checks.fail" ]; then
				  echo "HTTP 502: Bad Gateway" >&2
				  exit 1
				fi
				printf '[%s]' "$(cat "$FAKE_DIR/checks.json")"
				""");
		Files.setPosixFilePermissions(gh, PosixFilePermissions.fromString("rwxr-xr-x"));
		commit("chore: initial commit");
		git("tag", "-a", "v1.2.3", "-m", "Release v1.2.3");
		push();
	}

	@Nested
	class Gate {

		@Test
		void givenAllRequiredChecksSucceeded_whenGated_thenRelease() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");
			checks(check(1, "build-and-test", "completed", "success"), check(2, "docker-build", "completed", "success"),
					check(3, "Analyze (java-kotlin)", "completed", "success"));

			// when
			var run = gate(sha);

			// then
			assertThat(run.exit()).as("gate exit code").isZero();
			assertThat(run.outputs()).as("gate decision").containsEntry("decision", "release");
		}

		@Test
		void givenFailedPendingCancelledAndMissingChecks_whenGated_thenSkipNamingEach() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");
			checks(check(1, "build-and-test", "completed", "failure"), check(2, "docker-build", "in_progress", null),
					check(3, "Analyze (java-kotlin)", "completed", "cancelled"));

			// when
			var run = gate(sha, REQUIRED + "dockerfile-noble-pin-guard\n");

			// then
			assertThat(run.exit()).as("gate exit code").isZero();
			assertThat(run.outputs()).as("gate decision").containsEntry("decision", "skip");
			assertThat(run.output()).as("skip notice").contains("build-and-test=failure", "docker-build=pending",
					"Analyze (java-kotlin)=cancelled", "dockerfile-noble-pin-guard=missing");
		}

		@Test
		void givenOnlyOneRequiredCheckIsStillRunning_whenGated_thenSkip() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");
			checks(check(1, "build-and-test", "completed", "success"), check(2, "docker-build", "completed", "success"),
					check(3, "Analyze (java-kotlin)", "queued", null));

			// when
			var run = gate(sha);

			// then
			assertThat(run.outputs()).as("gate decision while CodeQL runs").containsEntry("decision", "skip");
		}

		@Test
		void givenFailedCheckThatSucceededOnRerun_whenGated_thenTheLatestRunCounts() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");
			checks(check(9, "build-and-test", "completed", "success"), check(1, "build-and-test", "completed", "failure"),
					check(2, "docker-build", "completed", "success"), check(3, "Analyze (java-kotlin)", "completed", "success"));

			// when
			var run = gate(sha);

			// then
			assertThat(run.outputs()).as("gate decision after a successful rerun").containsEntry("decision", "release");
		}

		@Test
		void givenMasterMovedPastTheRevision_whenGated_thenSkipAsObsolete() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");
			commitAndPush("feat: something newer");
			checks(check(1, "build-and-test", "completed", "success"), check(2, "docker-build", "completed", "success"),
					check(3, "Analyze (java-kotlin)", "completed", "success"));

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
			checks(check(1, "build-and-test", "completed", "success"), check(2, "docker-build", "completed", "success"),
					check(3, "Analyze (java-kotlin)", "completed", "success"));

			// when
			var run = gate(sha);

			// then
			assertThat(run.outputs()).as("gate decision with only release commits on top")
					.containsEntry("decision", "release");
		}

		@Test
		void givenCheckRunsApiFails_whenGated_thenTheGateFailsWithoutDecision() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");
			Files.writeString(bin.resolve("checks.fail"), "");

			// when
			var run = gate(sha);

			// then
			assertThat(run.exit()).as("gate exit code when the API fails").isNotZero();
			assertThat(run.outputs()).as("gate outputs when the API fails").doesNotContainKey("decision");
		}

		private Run gate(String sha) throws Exception {
			return gate(sha, REQUIRED);
		}

		private Run gate(String sha, String required) throws Exception {
			return script("release-gate.sh", Map.of("SHA", sha, "REQUIRED_CHECKS", required, "OWNER_REPO", "owner/repo"));
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
			commit("release: v1.3.0");
			git("tag", "-a", "v1.3.0", "-m", "Release v1.3.0");
			commitAndPush("chore: bump version to 1.4.0-SNAPSHOT [skip ci]");

			// when
			var run = version(sha);

			// then
			assertThat(run.exit()).as("version exit code").isZero();
			assertThat(run.outputs()).as("version outputs").containsEntry("resume", "true")
					.containsEntry("new_version", "1.3.0").containsEntry("next_snapshot", "1.4.0-SNAPSHOT");
		}

		@Test
		void givenReleaseCommitWithoutItsTag_whenVersionDetermined_thenItDoesNotResume() throws Exception {
			// given
			String sha = commitAndPush("fix: a bug");
			commitAndPush("release: v1.2.4");

			// when
			var run = version(sha);

			// then
			assertThat(run.outputs()).as("version outputs").containsEntry("resume", "false")
					.containsEntry("new_version", "1.2.4");
		}

		private Run version(String sha) throws Exception {
			return script("release-version.sh", Map.of("SHA", sha));
		}
	}

	private void checks(String... runs) throws IOException {
		Files.writeString(bin.resolve("checks.json"), "{\"check_runs\":[" + String.join(",", runs) + "]}");
	}

	private static String check(int id, String name, String status, String conclusion) {
		return "{\"id\":%d,\"name\":\"%s\",\"status\":\"%s\",\"conclusion\":%s}".formatted(id, name, status,
				conclusion == null ? "null" : "\"" + conclusion + "\"");
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
