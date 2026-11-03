package db.migration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V30__AddIncidentDelivery extends BaseJavaMigration {
    private static final String SQL = loadSql();
    @Override public Integer getChecksum() { var crc = new CRC32(); crc.update(SQL.getBytes(StandardCharsets.UTF_8)); return (int) crc.getValue(); }

    @Override
    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification = "Executes only bundled incident_deliveries.sql DDL with a fixed timestamp token rewrite; all copied history values use bound parameters. See docs/security/sast-acceptance.md.")
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        String ddl = connection.getMetaData().getDatabaseProductName().equals("MariaDB") ? SQL.replace("TIMESTAMP(6)", "DATETIME(6)") : SQL;
        try (var statement = connection.createStatement()) { for (String part : ddl.split(";")) if (!part.isBlank()) statement.execute(part); }
        // The previous release's immutable submission events already request a neutral receipt.
        // Seed those operations once during upgrade; importing old archives never invents sends.
        String select = "SELECT r.id AS report_id,r.case_reference,r.party_label,v.id AS revision_id,v.revision_number,v.accepted_at,w.state AS window_state,w.closed_at,r.state AS report_state,r.frozen_revision_number,c.guild_id,c.channel_id FROM incident_reports r JOIN incident_report_revisions v ON v.report_id=r.id JOIN incident_report_windows w ON w.id=r.window_id JOIN incident_match_contexts c ON c.id=w.context_id JOIN incident_report_events e ON e.report_id=r.id AND e.kind='SUBMITTED' AND e.report_version=1 AND e.revision_number=1 WHERE v.revision_number=1 OR (w.state='CLOSED' AND r.state='ACTIVE' AND v.revision_number=r.frozen_revision_number)";
        String insert = "INSERT INTO incident_deliveries (id,report_id,revision_id,operation,operation_key,guild_id,channel_id,nonce,case_reference,party_label,captured_at,state,aggregate_version,attempts,next_attempt_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,'PENDING',0,0,?)";
        try (var read = connection.prepareStatement(select); var rows = read.executeQuery(); var write = connection.prepareStatement(insert)) {
            while (rows.next()) {
                var report = UUID.fromString(rows.getString("report_id")); var revision = UUID.fromString(rows.getString("revision_id"));
                for (String operation : java.util.List.of("RECEIPT", "DISCLOSURE")) {
                    boolean receipt = operation.equals("RECEIPT");
                    if (receipt ? rows.getInt("revision_number") != 1 : !"CLOSED".equals(rows.getString("window_state")) || !"ACTIVE".equals(rows.getString("report_state")) || rows.getInt("revision_number") != rows.getInt("frozen_revision_number")) continue;
                    String nonce = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(java.nio.ByteBuffer.allocate(16).putLong(report.getMostSignificantBits()).putLong(report.getLeastSignificantBits()).array()) + (receipt ? "R" : "D");
                    var captured = rows.getTimestamp(receipt ? "accepted_at" : "closed_at");
                    write.setObject(1, UUID.randomUUID()); write.setObject(2, report); write.setObject(3, revision); write.setString(4, operation); write.setString(5, report + ":" + operation);
                    write.setString(6, rows.getString("guild_id")); write.setString(7, rows.getString("channel_id")); write.setString(8, nonce); write.setString(9, rows.getString("case_reference"));
                    write.setString(10, receipt ? null : rows.getString("party_label")); write.setTimestamp(11, captured); write.setTimestamp(12, captured); write.executeUpdate();
                }
            }
        }
    }
    private static String loadSql() {
        try (var input = V30__AddIncidentDelivery.class.getResourceAsStream("/db/schema/incident_deliveries.sql")) {
            if (input == null) throw new IllegalStateException("Missing incident delivery migration resource");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
