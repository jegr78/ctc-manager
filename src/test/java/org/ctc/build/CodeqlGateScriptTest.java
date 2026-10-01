package org.ctc.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs {@code scripts/ci/codeql-gate.sh} against a fake {@code gh} that serves code-scanning
 * alert JSON the way {@code gh api --paginate --slurp} does, or fails like an API error.
 */
class CodeqlGateScriptTest {

	private static final String ALERT_12 = alert(12, "java/ssrf", "high", "src/A.java", false);

	@TempDir
	Path bin;

	@BeforeEach
	void installFakeGh() throws IOException {
		Path gh = bin.resolve("gh");
		Files.writeString(gh, """
				#!/usr/bin/env bash
				for arg in "$@"; do
				  case "$arg" in
				    pr=*) source=pr ;;
				    ref=*) source=ref ;;
				  esac
				done
				if [ -f "$FAKE_DIR/$source.fail" ]; then
				  echo "HTTP 403: Resource not accessible by integration" >&2
				  exit 1
				fi
				printf '[%s]' "$(cat "$FAKE_DIR/$source.json")"
				""");
		Files.setPosixFilePermissions(gh, PosixFilePermissions.fromString("rwxr-xr-x"));
	}

	@Test
	void givenBaseAlertsApiFails_whenPullRequestGated_thenTheGateFails() throws Exception {
		// given
		alerts("pr", ALERT_12);
		Files.writeString(bin.resolve("ref.fail"), "");

		// when
		var run = gate(Map.of("EVENT_NAME", "pull_request"));

		// then
		assertThat(run.exit()).as("exit code when the base lookup fails").isNotZero();
		assertThat(run.output()).as("gate output").doesNotContain("No new HIGH/CRITICAL");
	}

	@Test
	void givenPullRequestAlertsApiFails_whenGated_thenTheGateFails() throws Exception {
		// given
		alerts("ref", ALERT_12);
		Files.writeString(bin.resolve("pr.fail"), "");

		// when
		var run = gate(Map.of("EVENT_NAME", "pull_request"));

		// then
		assertThat(run.exit()).as("exit code when the PR lookup fails").isNotZero();
		assertThat(run.output()).as("gate output").doesNotContain("No new HIGH/CRITICAL");
	}

	@Test
	void givenAdditionalAlertOfTheSameRuleInTheSameFile_whenPullRequestGated_thenItCountsAsNew() throws Exception {
		// given
		alerts("ref", ALERT_12);
		alerts("pr", ALERT_12, alert(13, "java/ssrf", "high", "src/A.java", false));

		// when
		var run = gate(Map.of("EVENT_NAME", "pull_request"));

		// then
		assertThat(run.exit()).as("exit code with a new alert").isEqualTo(1);
		assertThat(run.output()).as("reported alerts").contains("13|java/ssrf|\"src/A.java\"")
				.doesNotContain("12|java/ssrf");
	}

	@Test
	void givenOnlyDismissedOrLowerSeverityNewAlerts_whenPullRequestGated_thenItPasses() throws Exception {
		// given
		alerts("ref", ALERT_12);
		alerts("pr", ALERT_12, alert(14, "java/xss", "critical", "src/B.java", true),
				alert(15, "java/xss", "medium", "src/B.java", false));

		// when
		var run = gate(Map.of("EVENT_NAME", "pull_request"));

		// then
		assertThat(run.exit()).as("exit code without new open HIGH/CRITICAL alerts").isZero();
		assertThat(run.output()).as("gate output").contains("No new HIGH/CRITICAL CodeQL alerts.");
	}

	@Test
	void givenPathWithPipeAndNewline_whenPullRequestGated_thenTheAlertCountsAndPrintsOnOneLine() throws Exception {
		// given
		alerts("ref", ALERT_12);
		alerts("pr", ALERT_12, alert(16, "java/xss", "high", "src/|1|Evil\\n::warning::x.java", false));

		// when
		var run = gate(Map.of("EVENT_NAME", "pull_request"));

		// then
		assertThat(run.exit()).as("exit code with a new alert in an odd path").isEqualTo(1);
		assertThat(run.output()).as("reported alert").contains("16|java/xss|\"src/|1|Evil\\n::warning::x.java\"")
				.doesNotContain("\n::warning::");
	}

	@Test
	void givenPushThatMergedAnAlertFirstSeenOnThePullRequest_whenGated_thenItCountsAsNew() throws Exception {
		// given
		alerts("ref", ALERT_12);
		var baseline = bin.resolve("baseline.txt");
		assertThat(run("snapshot", baseline.toString()).exit()).as("snapshot exit code").isZero();
		alerts("ref", ALERT_12, alert(13, "java/ssrf", "high", "src/A.java", false));

		// when
		var run = gate(Map.of("EVENT_NAME", "push", "BASELINE_FILE", baseline.toString()));

		// then
		assertThat(run.exit()).as("exit code with a new alert on push").isEqualTo(1);
		assertThat(run.output()).as("reported alerts").contains("13|java/ssrf").doesNotContain("12|java/ssrf");
	}

	@Test
	void givenPushWithoutNewAlerts_whenGated_thenItPasses() throws Exception {
		// given
		alerts("ref", ALERT_12);
		var baseline = bin.resolve("baseline.txt");
		run("snapshot", baseline.toString());

		// when
		var run = gate(Map.of("EVENT_NAME", "push", "BASELINE_FILE", baseline.toString()));

		// then
		assertThat(run.exit()).as("exit code on push without new alerts").isZero();
	}

	@Test
	void givenSnapshotApiFails_whenRecorded_thenItFails() throws Exception {
		// given
		Files.writeString(bin.resolve("ref.fail"), "");

		// when
		var run = run("snapshot", bin.resolve("baseline.txt").toString());

		// then
		assertThat(run.exit()).as("snapshot exit code when the API fails").isNotZero();
	}

	private void alerts(String source, String... alerts) throws IOException {
		Files.writeString(bin.resolve(source + ".json"), "[" + String.join(",", alerts) + "]");
	}

	private static String alert(int number, String rule, String severity, String path, boolean dismissed) {
		return """
				{"number":%d,"dismissed_at":%s,"rule":{"id":"%s","security_severity_level":"%s"},
				 "most_recent_instance":{"location":{"path":"%s"}}}""".formatted(
				number, dismissed ? "\"2026-09-01T00:00:00Z\"" : "null", rule, severity, path);
	}

	private Run gate(Map<String, String> env) throws Exception {
		return run(env);
	}

	private Run run(String... args) throws Exception {
		return run(Map.of("EVENT_NAME", "push"), args);
	}

	private Run run(Map<String, String> env, String... args) throws Exception {
		var command = new java.util.ArrayList<>(java.util.List.of("bash", "scripts/ci/codeql-gate.sh"));
		command.addAll(java.util.List.of(args));
		var process = new ProcessBuilder(command).redirectErrorStream(true);
		process.environment().putAll(Map.of("PATH", bin + ":" + System.getenv("PATH"), "FAKE_DIR", bin.toString(),
				"OWNER_REPO", "owner/repo", "PR_NUMBER", "7", "BASE_REF", "master", "HEAD_REF", "master"));
		process.environment().putAll(env);
		Process started = process.start();
		assertThat(started.waitFor(30, TimeUnit.SECONDS)).as("gate finished").isTrue();
		return new Run(started.exitValue(), new String(started.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
	}

	private record Run(int exit, String output) {
	}
}
