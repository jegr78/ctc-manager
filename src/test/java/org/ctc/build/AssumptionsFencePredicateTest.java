package org.ctc.build;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification of the {@code assumptions-fence} predicate emitted
 * by the {@code exec-maven-plugin} block in {@code pom.xml}.
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
	 * identical to the {@code <argument><![CDATA[ grep -rE '...' ]]></argument>}
	 * value in {@code pom.xml} (modulo bash-quoting). */
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
		var pb = new ProcessBuilder("bash", "-c",
				"grep -rE '" + FENCE_REGEX + "' " + tmp.resolve("src/test/java"));
		pb.redirectErrorStream(true);
		var proc = pb.start();
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
		var pb = new ProcessBuilder("bash", "-c",
				"grep -rE '" + FENCE_REGEX + "' " + tmp.resolve("src/test/java"));
		pb.redirectErrorStream(true);
		var proc = pb.start();
		int exit = proc.waitFor();

		// then
		assertThat(exit).isOne();
	}
}
