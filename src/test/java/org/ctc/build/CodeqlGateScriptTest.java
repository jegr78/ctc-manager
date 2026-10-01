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
 * Runs {@code scripts/ci/codeql-gate.sh} against a fake {@code gh} that returns the alert lines
 * the real {@code gh api --jq} call would print, or fails like an API error.
 */
class CodeqlGateScriptTest {

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
				cat "$FAKE_DIR/$source.lines" 2>/dev/null || true
				""");
		Files.setPosixFilePermissions(gh, PosixFilePermissions.fromString("rwxr-xr-x"));
	}

	@Test
	void givenAlertsApiFails_whenGated_thenTheGateFails() throws Exception {
		// given
		Files.writeString(bin.resolve("ref.fail"), "");

		// when
		var run = gate(Map.of("EVENT_NAME", "pull_request"));

		// then
		assertThat(run.exit()).as("exit code when the API fails").isNotZero();
		assertThat(run.output()).as("gate output").doesNotContain("No new HIGH/CRITICAL");
	}

	@Test
	void givenAdditionalAlertOfTheSameRuleInTheSameFile_whenPullRequestGated_thenItCountsAsNew() throws Exception {
		// given
		Files.writeString(bin.resolve("ref.lines"), "12|java/ssrf|src/A.java|2026-09-01T10:00:00Z\n");
		Files.writeString(bin.resolve("pr.lines"),
				"12|java/ssrf|src/A.java|2026-09-01T10:00:00Z\n13|java/ssrf|src/A.java|2026-10-01T09:00:00Z\n");

		// when
		var run = gate(Map.of("EVENT_NAME", "pull_request"));

		// then
		assertThat(run.exit()).as("exit code with a new alert").isEqualTo(1);
		assertThat(run.output()).as("reported alerts").contains("13|java/ssrf|src/A.java")
				.doesNotContain("12|java/ssrf");
	}

	@Test
	void givenPullRequestWithTheBaseAlertsOnly_whenGated_thenItPasses() throws Exception {
		// given
		Files.writeString(bin.resolve("ref.lines"), "12|java/ssrf|src/A.java|2026-09-01T10:00:00Z\n");
		Files.writeString(bin.resolve("pr.lines"), "12|java/ssrf|src/A.java|2026-09-01T10:00:00Z\n");

		// when
		var run = gate(Map.of("EVENT_NAME", "pull_request"));

		// then
		assertThat(run.exit()).as("exit code without new alerts").isZero();
		assertThat(run.output()).as("gate output").contains("No new HIGH/CRITICAL CodeQL alerts.");
	}

	@Test
	void givenPushWithAnAlertFirstSeenInThisAnalysis_whenGated_thenItCountsAsNew() throws Exception {
		// given
		Files.writeString(bin.resolve("ref.lines"),
				"12|java/ssrf|src/A.java|2026-09-01T10:00:00Z\n14|java/xss|src/B.java|2026-10-01T09:05:00Z\n");

		// when
		var run = gate(Map.of("EVENT_NAME", "push", "ANALYSIS_STARTED_AT", "2026-10-01T09:00:00Z"));

		// then
		assertThat(run.exit()).as("exit code with a new alert on push").isEqualTo(1);
		assertThat(run.output()).as("reported alerts").contains("14|java/xss|src/B.java")
				.doesNotContain("12|java/ssrf");
	}

	@Test
	void givenPushWithOnlyOlderAlerts_whenGated_thenItPasses() throws Exception {
		// given
		Files.writeString(bin.resolve("ref.lines"), "12|java/ssrf|src/A.java|2026-09-01T10:00:00Z\n");

		// when
		var run = gate(Map.of("EVENT_NAME", "push", "ANALYSIS_STARTED_AT", "2026-10-01T09:00:00Z"));

		// then
		assertThat(run.exit()).as("exit code on push without new alerts").isZero();
	}

	@Test
	void givenPushWhoseAlertsApiFails_whenGated_thenTheGateFails() throws Exception {
		// given
		Files.writeString(bin.resolve("ref.fail"), "");

		// when
		var run = gate(Map.of("EVENT_NAME", "push", "ANALYSIS_STARTED_AT", "2026-10-01T09:00:00Z"));

		// then
		assertThat(run.exit()).as("exit code when the API fails on push").isNotZero();
	}

	private Run gate(Map<String, String> env) throws Exception {
		var process = new ProcessBuilder("bash", "scripts/ci/codeql-gate.sh").redirectErrorStream(true);
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
