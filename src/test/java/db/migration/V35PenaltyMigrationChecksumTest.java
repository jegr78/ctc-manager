package db.migration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class V35PenaltyMigrationChecksumTest {
    @Test
    void givenChangedMigrationResource_whenComparingChecksums_thenDetectResourceDrift() throws Exception {
        // given
        byte[] sql;
        try (var input = getClass().getResourceAsStream("/db/schema/race_penalty_revisions.sql")) {
            sql = java.util.Objects.requireNonNull(input).readAllBytes();
        }
        var original = isolatedMigration(sql);
        var changed = isolatedMigration((new String(sql, StandardCharsets.UTF_8) + "\n-- checksum drift probe\n").getBytes(StandardCharsets.UTF_8));
        // when / then
        assertThat(changed.getChecksum()).isNotEqualTo(original.getChecksum());
        assertThat(original.getChecksum()).isEqualTo(new V35__AddRacePenaltyRevisions().getChecksum());
    }

    @Test
    void givenMissingMigrationResource_whenInitializing_thenFailBeforeSchemaMutation() {
        // when / then
        assertThatThrownBy(() -> isolatedMigration(null)).isInstanceOf(ExceptionInInitializerError.class)
                .cause().isInstanceOf(IllegalStateException.class).hasMessageContaining("Missing team penalty migration resource");
    }

    private BaseJavaMigration isolatedMigration(byte[] resource) throws Exception {
        String name = "db.migration.V35__AddRacePenaltyRevisions";
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
                if (path.equals("db/schema/race_penalty_revisions.sql")) return resource == null ? null : new ByteArrayInputStream(resource);
                return super.getResourceAsStream(path);
            }
        };
        return (BaseJavaMigration) Class.forName(name, true, loader).getConstructor().newInstance();
    }

}
