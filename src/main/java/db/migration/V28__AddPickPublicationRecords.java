package db.migration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V28__AddPickPublicationRecords extends BaseJavaMigration {
    private static final String SQL = loadSql();

    @Override
    public Integer getChecksum() {
        var checksum = new CRC32();
        checksum.update(SQL.getBytes(StandardCharsets.UTF_8));
        return (int) checksum.getValue();
    }

    @Override
    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification =
            "Executes only bundled /db/schema/pick_publication.sql DDL with a fixed timestamp-token rewrite; "
                    + "no request, archive or database values enter SQL text. See docs/security/sast-acceptance.md.")
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        // UTC values use a timezone-free column and must remain writable beyond MariaDB's TIMESTAMP range.
        String sql = connection.getMetaData().getDatabaseProductName().equals("MariaDB")
                ? SQL.replace("TIMESTAMP(6)", "DATETIME(6)") : SQL;
        try (var statement = connection.createStatement()) {
            for (String part : sql.split(";")) if (!part.isBlank()) statement.execute(part);
        }
    }

    private static String loadSql() {
        try (var input = V28__AddPickPublicationRecords.class.getResourceAsStream("/db/schema/pick_publication.sql")) {
            if (input == null) throw new IllegalStateException("Missing pick publication migration resource");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
