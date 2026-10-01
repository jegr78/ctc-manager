package org.ctc.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

/**
 * Classifies changed paths with the {@code code} filter of the CI workflow, the way
 * {@code dorny/paths-filter} does, and checks that every executable build input runs the build.
 */
class CiChangedPathsFilterTest {

	private static final Pattern EXEC_SCRIPT = Pattern.compile("<argument>(scripts/[^<]+)</argument>");
	private static final Pattern DOCKER_COPY = Pattern.compile("(?m)^COPY (?!--)(\\S+) ");

	@ParameterizedTest
	@ValueSource(strings = {"scripts/guards/no-rerun-guard.sh", "scripts/app.sh", "Dockerfile", "pom.xml"})
	void givenChangeToABuildInput_whenClassified_thenTheBuildRuns(String path) throws IOException {
		// when / then
		assertThat(isCode(path)).as("%s runs the build", path).isTrue();
	}

	@ParameterizedTest
	@MethodSource("buildInputs")
	void givenFileUsedByMavenOrDocker_whenItChanges_thenTheBuildRuns(String path) throws IOException {
		// when / then
		assertThat(isCode(path)).as("%s runs the build", path).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"README.md", "docs/operations/discord-integration.md", ".planning/STATE.md", ".gitmessage"})
	void givenDocumentationOnlyChange_whenClassified_thenTheBuildIsSkipped(String path) throws IOException {
		// when / then
		assertThat(isCode(path)).as("%s skips the build", path).isFalse();
	}

	static List<String> buildInputs() throws IOException {
		var inputs = new ArrayList<String>();
		EXEC_SCRIPT.matcher(Files.readString(Path.of("pom.xml"))).results().map(m -> m.group(1)).forEach(inputs::add);
		DOCKER_COPY.matcher(Files.readString(Path.of("Dockerfile"))).results().map(m -> m.group(1))
				.map(source -> Files.isDirectory(Path.of(source)) ? source + "/any-file" : source)
				.forEach(inputs::add);
		assertThat(inputs).as("build inputs found in pom.xml and Dockerfile").contains("scripts/any-file");
		return inputs;
	}

	private static boolean isCode(String path) throws IOException {
		return codeFilter().stream()
				.anyMatch(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob).matches(Path.of(path)));
	}

	@SuppressWarnings("unchecked")
	private static List<String> codeFilter() throws IOException {
		Map<String, Object> workflow = new Yaml().load(Files.readString(Path.of(".github/workflows/ci.yml")));
		var steps = (List<Map<String, Object>>) ((Map<String, Object>) ((Map<String, Object>) workflow.get("jobs"))
				.get("changes")).get("steps");
		String filters = steps.stream().filter(step -> "filter".equals(step.get("id"))).findFirst()
				.map(step -> (String) ((Map<String, Object>) step.get("with")).get("filters")).orElseThrow();
		return (List<String>) ((Map<String, Object>) new Yaml().load(filters)).get("code");
	}
}
