package db.migration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V37__ExtendPickTyres extends BaseJavaMigration {
    private static final String SQL = loadSql();

    @Override
    public Integer getChecksum() {
        var checksum = new CRC32();
        checksum.update(SQL.getBytes(StandardCharsets.UTF_8));
        return (int) checksum.getValue();
    }

    @Override
    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification =
            "Executes only bundled /db/schema/pick_tyres_v37.sql DDL with a fixed H2 column-alter keyword rewrite; "
                    + "no request, archive or database values enter SQL text. See docs/security/sast-acceptance.md.")
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        String sql = switch (connection.getMetaData().getDatabaseProductName()) {
            case "H2" -> SQL.replace("MODIFY COLUMN", "ALTER COLUMN");
            case "MariaDB" -> SQL;
            default -> throw new IllegalArgumentException("Unsupported database for tyre migration");
        };
        try (var statement = connection.createStatement()) {
            for (String part : sql.split(";")) if (!part.isBlank()) statement.execute(part);
        }
    }

    private static String loadSql() {
        try (var input = V37__ExtendPickTyres.class.getResourceAsStream("/db/schema/pick_tyres_v37.sql")) {
            if (input == null) throw new IllegalStateException("Missing tyre migration resource");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
