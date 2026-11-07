package org.ctc.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

public final class CiExecutionListener implements TestExecutionListener {
    private final Path directory;

    public CiExecutionListener() {
        this(Path.of("target/test-perf"));
    }

    CiExecutionListener(Path directory) {
        this.directory = directory;
    }

    @Override
    public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
        record(identifier, result.getStatus().name());
    }

    @Override
    public void executionSkipped(TestIdentifier identifier, String reason) {
        record(identifier, "SKIPPED");
    }

    private synchronized void record(TestIdentifier identifier, String status) {
        String partition = System.getProperty("ctc.ci.partition");
        if (partition == null) return;
        if (!partition.matches("core|mariadb-[12]|browser")) throw new IllegalStateException("Unknown CI partition");
        String root = CiTestInventory.root(identifier);
        if (root == null) return;
        Path path = directory.resolve("ci-events-" + partition + "-" + ProcessHandle.current().pid() + ".tsv");
        String id = Base64.getEncoder().encodeToString(identifier.getUniqueId().getBytes(StandardCharsets.UTF_8));
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, root + "\t" + status + "\t" + identifier.isTest() + "\t" + id + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
