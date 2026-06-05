package org.ctc.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification of the {@code assumptions-fence} predicate in
 * {@code scripts/guards/assumptions-fence.sh}, invoked by the
 * {@code exec-maven-plugin} block in {@code pom.xml}.
 *
 * <p>Runs the SAME bash grep predicate that Maven invokes during the
 * {@code validate} phase against synthetic source files in a {@link TempDir},
 * proving the regex shape (and crucially the package discrimination between
 * {@code org.junit.jupiter.api.Assumptions} — forbidden — and
 * {@code org.assertj.core.api.Assumptions} — allowed) is correctly encoded
 * in both shells.
 */
class AssumptionsFencePredicateTest {

	/** The regex the Maven exec-maven-plugin block runs; MUST stay byte-for-byte
	 * identical to the {@code grep -rE '...'} pattern in
	 * {@code scripts/guards/assumptions-fence.sh} (modulo bash-quoting). */
	private static final String FENCE_REGEX =
			"^import\\s+(static\\s+)?org\\.junit\\.jupiter\\.api\\.Assumptions(\\.|;)";

	@Test
	void givenJunitAssumptionsImport_whenPredicateRuns_thenViolationDetected(@TempDir Path tmp) throws Exception {
		// given
		Path src = tmp.resolve("src/test/java/SyntheticPositive.java");
		Files.createDirectories(src.getParent());
		Files.writeString(src,
				"import static org.junit.jupiter.api.Assumptions.assumeFalse;\n"
						+ "class SyntheticPositive { }\n");

		// when
		var proc = runGrep(tmp.resolve("src/test/java"));
		int exit = proc.waitFor();
		String out = new String(proc.getInputStream().readAllBytes());

		// then
		assertThat(exit).isZero();
		assertThat(out).contains("SyntheticPositive.java");
	}

	@Test
	void givenAssertjAssumptionsImport_whenPredicateRuns_thenNoViolation(@TempDir Path tmp) throws Exception {
		// given
		Path src = tmp.resolve("src/test/java/SyntheticNegative.java");
		Files.createDirectories(src.getParent());
		Files.writeString(src,
				"import static org.assertj.core.api.Assumptions.assumeThat;\n"
						+ "class SyntheticNegative { }\n");

		// when
		int exit = runGrep(tmp.resolve("src/test/java")).waitFor();

		// then
		assertThat(exit).isOne();
	}

	@Test
	void givenGuardScript_whenAssumptionsFenceRegexExtracted_thenMatchesFenceRegexConstant() throws Exception {
		String scriptContent = Files.readString(Path.of("scripts/guards/assumptions-fence.sh"));
		Pattern argument = Pattern.compile("grep\\s+-rE\\s+'(\\^import[^']+)'");
		Matcher matcher = argument.matcher(scriptContent);

		assertThat(matcher.find())
				.as("scripts/guards/assumptions-fence.sh must contain the assumptions-fence grep argument")
				.isTrue();
		assertThat(matcher.group(1)).isEqualTo(FENCE_REGEX);

		assertThat(Files.readString(Path.of("pom.xml")))
				.as("pom.xml must keep the assumptions-fence execution wired to the guard script")
				.contains("assumptions-fence")
				.contains("scripts/guards/assumptions-fence.sh");
	}

	private static Process runGrep(Path searchRoot) throws Exception {
		ProcessBuilder pb = new ProcessBuilder("bash", "-c",
				"grep -rE \"$REGEX\" \"$ROOT\"");
		pb.environment().put("REGEX", FENCE_REGEX);
		pb.environment().put("ROOT", searchRoot.toString());
		pb.redirectErrorStream(true);
		return pb.start();
	}
}
