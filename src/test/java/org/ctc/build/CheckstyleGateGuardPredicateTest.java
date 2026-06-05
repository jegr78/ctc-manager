package org.ctc.build;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification of the {@code checkstyle-gate-guard} predicate in
 * {@code scripts/guards/checkstyle-gate-guard.sh}, invoked by the
 * {@code exec-maven-plugin} block in {@code pom.xml}.
 *
 * <p>Runs the SAME grep predicate Maven invokes during the {@code validate} phase
 * against synthetic {@code pom.xml} + {@code config/checkstyle.xml} files in a
 * {@link TempDir}, proving the guard fails the build when the unused-import gate
 * config is weakened (failOnViolation/includeTestSourceDirectory flipped, modules
 * removed) and passes when it is intact. Also pins the self-reference safety of the
 * {@code [[:space:]]*} presence patterns: a pom that contains only the guard's own
 * pattern literals (and not the real config) must still fail.
 */
class CheckstyleGateGuardPredicateTest {

	/** Presence patterns the Maven guard greps; MUST stay in sync with
	 * {@code scripts/guards/checkstyle-gate-guard.sh}.
	 * The {@code [[:space:]]*} between tag and value is what stops the guard's own
	 * literals from self-satisfying the grep against the real pom.xml. */
	private static final String FAIL_ON_VIOLATION_PATTERN = "<failOnViolation>[[:space:]]*true";
	private static final String INCLUDE_TEST_SOURCE_PATTERN = "<includeTestSourceDirectory>[[:space:]]*true";
	private static final String UNUSED_IMPORTS_TOKEN = "UnusedImports";
	private static final String REDUNDANT_IMPORT_TOKEN = "RedundantImport";

	private static final String INTACT_POM =
			"<failOnViolation>true</failOnViolation>\n"
					+ "<includeTestSourceDirectory>true</includeTestSourceDirectory>\n";
	private static final String INTACT_CHECKSTYLE =
			"<module name=\"UnusedImports\"/>\n<module name=\"RedundantImport\"/>\n";

	@Test
	void givenIntactGateConfig_whenGuardRuns_thenPasses(@TempDir Path tmp) throws Exception {
		// given
		writeConfig(tmp, INTACT_POM, INTACT_CHECKSTYLE);

		// when / then
		assertThat(runGuard(tmp)).isZero();
	}

	@Test
	void givenFailOnViolationFlippedToFalse_whenGuardRuns_thenFails(@TempDir Path tmp) throws Exception {
		// given
		writeConfig(tmp,
				"<failOnViolation>false</failOnViolation>\n"
						+ "<includeTestSourceDirectory>true</includeTestSourceDirectory>\n",
				INTACT_CHECKSTYLE);

		// when / then
		assertThat(runGuard(tmp)).isOne();
	}

	@Test
	void givenIncludeTestSourceDirectoryRemoved_whenGuardRuns_thenFails(@TempDir Path tmp) throws Exception {
		// given
		writeConfig(tmp, "<failOnViolation>true</failOnViolation>\n", INTACT_CHECKSTYLE);

		// when / then
		assertThat(runGuard(tmp)).isOne();
	}

	@Test
	void givenUnusedImportsModuleRemoved_whenGuardRuns_thenFails(@TempDir Path tmp) throws Exception {
		// given
		writeConfig(tmp, INTACT_POM, "<module name=\"RedundantImport\"/>\n");

		// when / then
		assertThat(runGuard(tmp)).isOne();
	}

	@Test
	void givenRedundantImportModuleRemoved_whenGuardRuns_thenFails(@TempDir Path tmp) throws Exception {
		// given
		writeConfig(tmp, INTACT_POM, "<module name=\"UnusedImports\"/>\n");

		// when / then
		assertThat(runGuard(tmp)).isOne();
	}

	@Test
	void givenPomContainingOnlyGuardPatternLiterals_whenGuardRuns_thenStillFails(@TempDir Path tmp) throws Exception {
		// given — mimics the production self-reference: the pom holds the grep pattern
		// literals but NOT the real <failOnViolation>true>/<includeTestSourceDirectory>true> config.
		writeConfig(tmp,
				"grep -qE '" + FAIL_ON_VIOLATION_PATTERN + "' pom.xml\n"
						+ "grep -qE '" + INCLUDE_TEST_SOURCE_PATTERN + "' pom.xml\n",
				INTACT_CHECKSTYLE);

		// when / then — the [[:space:]]* patterns must not self-satisfy
		assertThat(runGuard(tmp)).isOne();
	}

	@Test
	void givenGuardScript_whenGuardPatternsExtracted_thenPresentInScriptAndPomWiring() throws Exception {
		String script = Files.readString(Path.of("scripts/guards/checkstyle-gate-guard.sh"));
		String pom = Files.readString(Path.of("pom.xml"));

		assertThat(script)
				.as("scripts/guards/checkstyle-gate-guard.sh must keep its presence patterns in sync with this test")
				.contains(FAIL_ON_VIOLATION_PATTERN)
				.contains(INCLUDE_TEST_SOURCE_PATTERN)
				.contains(UNUSED_IMPORTS_TOKEN)
				.contains(REDUNDANT_IMPORT_TOKEN);
		assertThat(pom)
				.as("pom.xml must keep the checkstyle-gate-guard execution wired to the guard script")
				.contains("checkstyle-gate-guard")
				.contains("scripts/guards/checkstyle-gate-guard.sh");
	}

	private static void writeConfig(Path workdir, String pomContent, String checkstyleContent) throws Exception {
		Files.writeString(workdir.resolve("pom.xml"), pomContent);
		Path checkstyle = workdir.resolve("config/checkstyle.xml");
		Files.createDirectories(checkstyle.getParent());
		Files.writeString(checkstyle, checkstyleContent);
	}

	private static int runGuard(Path workdir) throws Exception {
		String script = "fail=0; "
				+ "grep -qE \"$FOV\" pom.xml || fail=1; "
				+ "grep -qE \"$ITS\" pom.xml || fail=1; "
				+ "grep -q \"$UI\" config/checkstyle.xml || fail=1; "
				+ "grep -q \"$RI\" config/checkstyle.xml || fail=1; "
				+ "exit $fail;";
		ProcessBuilder pb = new ProcessBuilder("bash", "-c", script);
		pb.directory(workdir.toFile());
		pb.environment().put("FOV", FAIL_ON_VIOLATION_PATTERN);
		pb.environment().put("ITS", INCLUDE_TEST_SOURCE_PATTERN);
		pb.environment().put("UI", UNUSED_IMPORTS_TOKEN);
		pb.environment().put("RI", REDUNDANT_IMPORT_TOKEN);
		pb.redirectErrorStream(true);
		return pb.start().waitFor();
	}
}
