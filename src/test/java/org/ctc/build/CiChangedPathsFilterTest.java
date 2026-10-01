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
	private static final Pattern BASEDIR_FILE = Pattern.compile("\\$\\{project\\.basedir}/([^<\\s}]+)");
	private static final Pattern DOCKER_COPY = Pattern.compile("(?m)^(?:COPY|ADD) (?!--)(.+)$");

	@ParameterizedTest
	@ValueSource(strings = {"scripts/guards/no-rerun-guard.sh", "scripts/app.sh", "Dockerfile", "pom.xml", ".gitattributes"})
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

	@ParameterizedTest
	@ValueSource(strings = {"build-and-test", "dockerfile-noble-pin-guard", "docker-build"})
	@SuppressWarnings("unchecked")
	void givenFailedPathCheck_whenARequiredJobRuns_thenItFailsInsteadOfBeingSkipped(String job) throws IOException {
		// given
		var definition = (Map<String, Object>) jobs().get(job);
		var firstStep = ((List<Map<String, Object>>) definition.get("steps")).getFirst();

		// when / then
		assertThat(definition.get("if")).as("%s runs although changes failed", job).isEqualTo("${{ !cancelled() }}");
		assertThat((String) firstStep.get("if")).as("%s first step rejects a failed path check", job)
				.contains("needs.changes.result != 'success'");
		assertThat((String) firstStep.get("run")).as("%s first step fails", job).contains("exit 1");
	}

	static List<String> buildInputs() throws IOException {
		var inputs = new ArrayList<String>();
		String pom = Files.readString(Path.of("pom.xml"));
		EXEC_SCRIPT.matcher(pom).results().map(m -> m.group(1)).forEach(inputs::add);
		BASEDIR_FILE.matcher(pom).results().map(m -> m.group(1))
				.filter(file -> !file.startsWith("target") && !file.startsWith("src/test")).forEach(inputs::add);
		DOCKER_COPY.matcher(Files.readString(Path.of("Dockerfile"))).results()
				.map(m -> List.of(m.group(1).trim().split("\\s+")))
				.flatMap(tokens -> tokens.subList(0, tokens.size() - 1).stream())
				.map(source -> Files.isDirectory(Path.of(source)) ? source + "/any-file" : source)
				.forEach(inputs::add);
		assertThat(inputs).as("build inputs found in pom.xml and Dockerfile")
				.contains("scripts/any-file", "config/checkstyle.xml");
		return inputs;
	}

	private static boolean isCode(String path) throws IOException {
		return codeFilter().stream()
				.anyMatch(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob).matches(Path.of(path)));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> jobs() throws IOException {
		Map<String, Object> workflow = new Yaml().load(Files.readString(Path.of(".github/workflows/ci.yml")));
		return (Map<String, Object>) workflow.get("jobs");
	}

	@SuppressWarnings("unchecked")
	private static List<String> codeFilter() throws IOException {
		var steps = (List<Map<String, Object>>) ((Map<String, Object>) jobs().get("changes")).get("steps");
		String filters = steps.stream().filter(step -> "filter".equals(step.get("id"))).findFirst()
				.map(step -> (String) ((Map<String, Object>) step.get("with")).get("filters")).orElseThrow();
		return (List<String>) ((Map<String, Object>) new Yaml().load(filters)).get("code");
	}
}
