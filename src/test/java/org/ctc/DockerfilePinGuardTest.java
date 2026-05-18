package org.ctc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * In-process structural guard for the eclipse-temurin Ubuntu Noble base-image pin in the Dockerfile.
 *
 * <p>Mirrors the {@code dockerfile-noble-pin-guard} job in {@code .github/workflows/ci.yml} but runs
 * inside every {@code ./mvnw verify}, so a regression of the pin surfaces locally without a CI
 * round-trip. The CI workflow grep gate remains the contractual fail-fast on PR; this JUnit guard
 * is the structural duplicate that runs in the project's own test suite.
 *
 * <p>Rationale: Playwright 1.59.0 does not support Ubuntu 26.04 (Plucky), which the bare
 * {@code eclipse-temurin:25-jre} tag silently rotated to in release run 25609204039 (cf. v1.10
 * Phase 78). Both Dockerfile stages must therefore end in {@code -noble}.
 *
 * <p>Plain unit test (Surefire-routed via the default untagged group) — no Spring context, no
 * dependencies, ~50 ms wall-clock.
 */
class DockerfilePinGuardTest {

	private static final String DOCKERFILE_PATH = "Dockerfile";
	private static final String FROM_PREFIX = "FROM eclipse-temurin:";
	private static final String NOBLE_SUFFIX = "-noble";

	@Test
	@DisplayName("givenDockerfile_whenInspectingFromLines_thenAllEclipseTemurinTagsArePinnedToNoble")
	void givenDockerfile_whenInspectingFromLines_thenAllEclipseTemurinTagsArePinnedToNoble() throws Exception {
		// given
		String content = Files.readString(Path.of(DOCKERFILE_PATH));

		// when
		List<String> fromTemurinLines = content.lines()
				.map(String::stripLeading)
				.filter(line -> line.startsWith(FROM_PREFIX))
				.toList();

		// then
		assertThat(fromTemurinLines)
				.as("Dockerfile must declare exactly two 'FROM eclipse-temurin:' lines (build + runtime stages)")
				.hasSize(2);

		assertThat(fromTemurinLines)
				.as("Every 'FROM eclipse-temurin:' line must be pinned to '%s' "
						+ "(Playwright 1.59.0 does not support Ubuntu 26.04 / Plucky — see v1.10 Phase 78)", NOBLE_SUFFIX)
				.allSatisfy(line -> {
					String tagSpec = line.substring(FROM_PREFIX.length()).split("\\s+", 2)[0];
					assertThat(tagSpec)
							.as("FROM-tag '%s' must end with '%s'", tagSpec, NOBLE_SUFFIX)
							.endsWith(NOBLE_SUFFIX);
				});
	}

	@Test
	@DisplayName("givenDockerfile_whenInspectingBothStages_thenBuildAndRuntimePinsArePresent")
	void givenDockerfile_whenInspectingBothStages_thenBuildAndRuntimePinsArePresent() throws Exception {
		// given
		String content = Files.readString(Path.of(DOCKERFILE_PATH));

		// when / then — the exact pinned forms shipped by Phase 78 must remain
		assertThat(content)
				.as("Build stage must be pinned to eclipse-temurin:25-jdk-noble (Phase 78 PLAT-CI-01)")
				.contains("FROM eclipse-temurin:25-jdk-noble AS build");
		assertThat(content)
				.as("Runtime stage must be pinned to eclipse-temurin:25-jre-noble (Phase 78 PLAT-CI-01)")
				.contains("FROM eclipse-temurin:25-jre-noble");
	}
}
