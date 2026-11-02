package db.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V26__AddConfidentialPicks extends BaseJavaMigration {
    private static final String SQL = loadSql();

    @Override
    public Integer getChecksum() {
        var checksum = new CRC32();
        checksum.update(SQL.getBytes(StandardCharsets.UTF_8));
        return (int) checksum.getValue();
    }

    @Override
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
        try (var input = V26__AddConfidentialPicks.class.getResourceAsStream("/db/schema/confidential_picks.sql")) {
            if (input == null) throw new IllegalStateException("Missing confidential picks migration resource");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
