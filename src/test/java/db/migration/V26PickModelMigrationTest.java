package db.migration;

import java.sql.DriverManager;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.api.migration.Context;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.*;

class V26PickModelMigrationTest {
    @Test
    void givenChangedMigrationResource_whenComparingChecksums_thenDetectResourceDrift() throws Exception {
        // given
        byte[] sql;
        try (var input = getClass().getResourceAsStream("/db/schema/confidential_picks.sql")) {
            sql = java.util.Objects.requireNonNull(input).readAllBytes();
        }
        var original = isolatedMigration(sql);
        var changed = isolatedMigration((new String(sql, StandardCharsets.UTF_8) + "\n-- checksum drift probe\n").getBytes(StandardCharsets.UTF_8));
        // when / then
        assertThat(changed.getChecksum()).isNotEqualTo(original.getChecksum());
        assertThat(original.getChecksum()).isEqualTo(new V26__AddConfidentialPicks().getChecksum());
    }

    @Test
    void givenMissingMigrationResource_whenInitializing_thenFailBeforeSchemaMutation() {
        // when / then
        assertThatThrownBy(() -> isolatedMigration(null)).isInstanceOf(ExceptionInInitializerError.class)
                .cause().isInstanceOf(IllegalStateException.class).hasMessageContaining("Missing confidential picks migration resource");
    }

    private BaseJavaMigration isolatedMigration(byte[] resource) throws Exception {
        String name = "db.migration.V26__AddConfidentialPicks";
        byte[] code;
        try (var input = getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            code = java.util.Objects.requireNonNull(input).readAllBytes();
        }
        var loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected synchronized Class<?> loadClass(String candidate, boolean resolve) throws ClassNotFoundException {
                if (!candidate.equals(name)) return super.loadClass(candidate, resolve);
                var loaded = findLoadedClass(candidate);
                if (loaded == null) loaded = defineClass(candidate, code, 0, code.length);
                if (resolve) resolveClass(loaded);
                return loaded;
            }

            @Override
            public InputStream getResourceAsStream(String path) {
                if (path.equals("db/schema/confidential_picks.sql")) return resource == null ? null : new ByteArrayInputStream(resource);
                return super.getResourceAsStream(path);
            }
        };
        return (BaseJavaMigration) Class.forName(name, true, loader).getConstructor().newInstance();
    }

    @Test
    void givenLegacyDatabase_whenMigratingAndReconnecting_thenDefaultOffAndEnforcePickIdentityAndLifecycle() throws Exception {
        // given
        String url = "jdbc:h2:mem:pick-migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        String season = UUID.randomUUID().toString();
        String team = UUID.randomUUID().toString();
        String race = UUID.randomUUID().toString();
        String day = UUID.randomUUID().toString();
        String opponent = UUID.randomUUID().toString();
        try (var connection = DriverManager.getConnection(url); var sql = connection.createStatement()) {
            for (String table : List.of("seasons", "teams", "races", "cars", "tracks", "season_car_pool_entries", "matchdays")) {
                sql.execute("CREATE TABLE " + table + " (id UUID PRIMARY KEY)");
            }
            sql.execute("ALTER TABLE races ADD COLUMN matchday_id UUID");
            sql.execute("INSERT INTO seasons VALUES ('" + season + "')");
            sql.execute("INSERT INTO teams VALUES ('" + team + "')");
            sql.execute("INSERT INTO teams VALUES ('" + opponent + "')");
            sql.execute("INSERT INTO matchdays VALUES ('" + day + "')");
            sql.execute("INSERT INTO races VALUES ('" + race + "','" + day + "')");
            // when
            var context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            new V26__AddConfidentialPicks().migrate(context);
        }
        // then
        try (var connection = DriverManager.getConnection(url); var sql = connection.createStatement()) {
            var columns = new ArrayList<String>();
            try (var metadata = connection.getMetaData().getColumns(null, null, "MATCHDAY_PICKS", null)) {
                while (metadata.next()) columns.add(metadata.getString("COLUMN_NAME"));
            }
            assertThat(columns).contains("RACE_ID", "SEASON_ID", "TEAM_ID", "AGGREGATE_VERSION", "LATEST_REVISION_NUMBER", "APPROVED_REVISION_NUMBER");
            try (var rows = sql.executeQuery("SELECT picks_collection_enabled FROM seasons")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isFalse();
            }
            try (var rows = sql.executeQuery("SELECT picks_managed FROM races")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isFalse();
            }
            String pick = UUID.randomUUID().toString();
            String insert = "INSERT INTO matchday_picks (id,race_id,season_id,team_id,original_matchday_id,original_away_team_id) VALUES ('" + pick + "','" + race + "','" + season + "','" + team + "','" + day + "','" + opponent + "')";
            assertThatCode(() -> sql.execute(insert)).doesNotThrowAnyException();
            assertThatCode(() -> {
                sql.execute("UPDATE matchday_picks SET state='SUBMITTED',latest_revision_number=1,aggregate_version=1");
                sql.execute("UPDATE matchday_picks SET state='APPROVED',approved_revision_number=1,aggregate_version=2");
                sql.execute("UPDATE matchday_picks SET state='MISSING',latest_revision_number=NULL,approved_revision_number=NULL,aggregate_version=0");
            }).as("valid state transitions remain writable from a fresh connection").doesNotThrowAnyException();
            assertThatThrownBy(() -> sql.execute(insert.replace(pick, UUID.randomUUID().toString())))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("UQ_PICK_RACE");
            assertThatThrownBy(() -> sql.execute("UPDATE matchday_picks SET state='INVALID'"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("CHK_PICK_STATE");
            assertThatThrownBy(() -> sql.execute("UPDATE matchday_picks SET state='APPROVED'"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("CHK_PICK_POINTERS");
            assertThatThrownBy(() -> sql.execute("UPDATE matchday_picks SET aggregate_version=-1"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("CHK_PICK_VERSION");
            assertThatThrownBy(() -> sql.execute("UPDATE matchday_picks SET manual_open=TRUE,manual_closed=TRUE"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("CHK_PICK_OVERRIDES");
            assertThatThrownBy(() -> sql.execute("UPDATE matchday_picks SET original_away_team_id=team_id"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("CHK_PICK_OPPONENT");
            assertThatThrownBy(() -> sql.execute("UPDATE races SET matchday_id=RANDOM_UUID()"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("FK_PICK_RACE_TARGET");
            assertThatThrownBy(() -> sql.execute("DELETE FROM races"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("FK_PICK_RACE");
        } finally {
            try (var connection = DriverManager.getConnection(url); var sql = connection.createStatement()) {
                sql.execute("SHUTDOWN");
            }
        }
    }
}
