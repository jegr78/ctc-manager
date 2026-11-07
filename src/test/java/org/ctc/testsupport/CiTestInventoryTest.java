package org.ctc.testsupport;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.launcher.TestIdentifier;
import static org.assertj.core.api.Assertions.assertThat;

class CiTestInventoryTest {
    @Test
    void givenCompiledCanonicalTests_whenDiscoveringThePlan_thenIncludeInheritedNestedAndDynamicCases(
            @TempDir Path directory) throws Exception {
        // when
        Path inventory = directory.resolve("inventory.tsv");
        CiTestInventory.main(new String[]{inventory.toString()});
        List<String> rows = Files.readAllLines(inventory);
        // then
        assertThat(rows).anyMatch(row -> row.startsWith("org.ctc.build.CiPartitionWorkflowTest\tunit\t"));
        assertThat(rows).anyMatch(row -> row.startsWith("org.ctc.sitegen.SiteGeneratorFixtureIsolationIT\tintegration\tfalse\t"));
        assertThat(rows).anyMatch(row -> row.startsWith("org.ctc.domain.service.TyrePersistenceMariaDbIT\tintegration\ttrue\t"));
        assertThat(rows).anyMatch(row -> row.startsWith("org.ctc.e2e.TimeProgressionE2ETest\tbrowser\t"));
        assertThat(rows).noneMatch(row -> row.startsWith("org.ctc.tools.StandingsGraphicPreview\t"));
        List<String> ids = rows.stream().map(row -> row.split("\t")[4])
                .map(encoded -> new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)).toList();
        assertThat(ids).anyMatch(id -> id.contains("/[nested-class:"));
        assertThat(ids).anyMatch(id -> id.contains("/[test-template:"));
        assertThat(ids).anyMatch(id -> id.contains("/[test-factory:"));
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void givenFinishedAndSkippedTests_whenRecordingEvidence_thenKeepExactIdsAndOutcomes(
            @TempDir Path directory) throws Exception {
        // given
        var descriptor = new AbstractTestDescriptor(UniqueId.forEngine("junit-jupiter")
                .append("class", "SyntheticCase").append("method", "example()"), "synthetic") {
            @Override public TestDescriptor.Type getType() { return TestDescriptor.Type.TEST; }
        };
        var identifier = TestIdentifier.from(descriptor);
        String previous = System.getProperty("ctc.ci.partition");
        var listener = new CiExecutionListener(directory);
        try {
            System.setProperty("ctc.ci.partition", "core");
            // when
            listener.executionFinished(identifier, TestExecutionResult.successful());
            listener.executionSkipped(identifier, "synthetic skip");
            // then
            Path output = directory.resolve("ci-events-core-" + ProcessHandle.current().pid() + ".tsv");
            List<String> lines = Files.readAllLines(output);
            String encoded = Base64.getEncoder().encodeToString(identifier.getUniqueId().getBytes(StandardCharsets.UTF_8));
            assertThat(lines).containsExactly("SyntheticCase\tSUCCESSFUL\ttrue\t" + encoded,
                    "SyntheticCase\tSKIPPED\ttrue\t" + encoded);
        } finally {
            if (previous == null) System.clearProperty("ctc.ci.partition");
            else System.setProperty("ctc.ci.partition", previous);
        }
    }
}
