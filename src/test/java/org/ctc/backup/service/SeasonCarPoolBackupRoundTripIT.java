package org.ctc.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.ctc.TestHelper;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupImportException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.RaceCarSelectionService;
import org.ctc.domain.service.RaceService;
import org.ctc.domain.service.SeasonCarPoolService;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@CtcDevSpringBootContext
@Tag("integration")
class SeasonCarPoolBackupRoundTripIT {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Path ROOT = SitegenTestDir.create("pool-backup-release");
    static {
        try { Files.createDirectories(ROOT.resolve("uploads")); }
        catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry properties) {
        properties.add("app.upload-dir", () -> ROOT.resolve("uploads").toString());
        properties.add("app.backup.staging-dir", () -> ROOT.resolve("staging").toString());
        properties.add("app.backup.import-backups-dir", () -> ROOT.resolve("imports").toString());
        properties.add("ctc.site.output-dir", () -> ROOT.resolve("site").toString());
    }
    @Autowired DataSource dataSource;
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired CarRepository cars;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired RaceRepository races;
    @Autowired SeasonCarPoolEntryRepository entries;
    @Autowired SeasonCarPoolService pools;
    @Autowired RaceService raceService;
    @Autowired RaceCarSelectionService selections;
    @Autowired BackupArchiveService archives;
    @MockitoSpyBean BackupImportService imports;
    TestHelper.SeasonFixture category;
    TestHelper.SeasonFixture fixed;
    UUID mr;
    UUID rr;
    UUID unknown;
    UUID entry;
    UUID oneMakeEntry;
    UUID foreignSeason;
    UUID foreignEntry;
    List<UUID> memberIds;
    List<UUID> clauseIds;
    List<UUID> newCars = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            category = helper.createFullSeasonFixture("TPBC" + suffix);
            fixed = helper.createFullSeasonFixture("TPBF" + suffix);
            mr = car(suffix, Drivetrain.MR, 9891, false);
            rr = car(suffix, Drivetrain.RR, 9891, false);
            unknown = car(suffix, Drivetrain.MR, null, true);
            pools.changeMode(category.season().getId(), CarPoolMode.CATEGORY_POOL);
            entry = pools.saveEntry(category.season().getId(), null, "Gr.3 MR + Gr.3 RR", List.of(
                    new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, Drivetrain.MR, 9891),
                    new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, Drivetrain.RR, 9891)));
            oneMakeEntry = pools.saveOneMakeEntry(category.season().getId(), null, "Specific model", unknown);
            pools.freeze(category.season().getId());
            var frozen = entries.findById(entry).orElseThrow();
            memberIds = frozen.getMembers().stream().map(SeasonCarPoolMember::getId).toList();
            clauseIds = frozen.getClauses().stream().map(SeasonCarPoolClause::getId).toList();
            fixed.season().getCars().add(cars.findById(rr).orElseThrow());
        });
        assertThat(save(category, mr, entry).success()).isTrue();
        assertThat(save(fixed, rr, null).success()).isTrue();
        clearInvocations(imports);
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            for (var fixture : List.of(category, fixed)) {
                helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow());
            }
            if (foreignSeason != null) helper.deleteSeasonCascade(seasons.findById(foreignSeason).orElseThrow());
            seasons.flush();
            cars.deleteAllById(List.of(mr, rr, unknown));
            cars.deleteAllById(newCars);
            teams.deleteAllById(List.of(category.homeTeam().getId(), category.awayTeam().getId(),
                    fixed.homeTeam().getId(), fixed.awayTeam().getId()));
        });
    }

    @Test
    void givenCombinedFrozenPoolAndReviewedMetadata_whenRestoringAfterCatalogChanges_thenKeepExactSnapshotAndConsumption() throws Exception {
        // given
        byte[] archive = export();
        tx.executeWithoutResult(status -> {
            var changed = cars.findById(mr).orElseThrow();
            changed.setCategory(CarCategory.GR_4);
            changed.setDrivetrain(Drivetrain.FR);
            changed.setModelYear(9892);
            var cleared = cars.findById(unknown).orElseThrow();
            cleared.setModelYear(9891);
            cleared.setModelYearOverride(false);
            newCars.add(car("after-export", Drivetrain.MR, 9891, false));
        });
        // when
        var preview = imports.stage(upload(archive));
        imports.execute(preview.stagingId());
        // then
        verify(imports).wipeAllTables(anyMap());
        tx.executeWithoutResult(status -> {
            var restored = entries.findById(entry).orElseThrow();
            var restoredOneMake = entries.findById(oneMakeEntry).orElseThrow();
            assertThat(restoredOneMake.getOneMakeCar().getId()).isEqualTo(unknown);
            assertThat(restoredOneMake.getClauses()).isEmpty();
            assertThat(restoredOneMake.getMembers()).extracting(member -> member.getCar().getId()).containsExactly(unknown);
            assertThat(restored.getSeason().getId()).isEqualTo(category.season().getId());
            assertThat(restored.getLabel()).isEqualTo("Gr.3 MR + Gr.3 RR");
            assertThat(restored.getMembers()).extracting(SeasonCarPoolMember::getId).containsExactlyInAnyOrderElementsOf(memberIds);
            assertThat(restored.getMembers()).extracting(member -> member.getCar().getId()).containsExactlyInAnyOrder(mr, rr);
            assertThat(restored.getClauses()).extracting(SeasonCarPoolClause::getId).containsExactlyElementsOf(clauseIds);
            assertThat(restored.getClauses()).extracting(SeasonCarPoolClause::getDrivetrain).containsExactly(Drivetrain.MR, Drivetrain.RR);
            assertThat(restored.getClauses()).allSatisfy(clause -> {
                assertThat(clause.getCategory()).isEqualTo(CarCategory.GR_3);
                assertThat(clause.getModelYear()).isEqualTo(9891);
            });
            var accepted = cars.findById(mr).orElseThrow();
            assertThat(accepted.getCategory()).isEqualTo(CarCategory.GR_3);
            assertThat(accepted.getDrivetrain()).isEqualTo(Drivetrain.MR);
            assertThat(accepted.getModelYear()).isEqualTo(9891);
            assertThat(accepted.getModelYearSource()).isEqualTo("Test accepted source");
            assertThat(accepted.getModelYearEvidence()).isEqualTo("Test numeric-ID source review");
            assertThat(accepted.isModelYearOverride()).isFalse();
            var cleared = cars.findById(unknown).orElseThrow();
            assertThat(cleared.getModelYear()).isNull();
            assertThat(cleared.isModelYearOverride()).isTrue();
            assertThat(cleared.getModelYearSource()).isEqualTo("MANUAL");
            assertThat(cleared.getModelYearEvidence()).isEqualTo("Deliberately cleared test year");
            assertThat(cleared.getProposedModelYear()).isEqualTo(9891);
            assertThat(cleared.getProposedModelYearEvidence()).isEqualTo("Test candidate, not accepted");
            assertThat(cars.findById(newCars.getFirst())).isEmpty();
            var race = races.findById(category.race().getId()).orElseThrow();
            assertThat(race.getCar().getId()).isEqualTo(mr);
            assertThat(race.getCarPoolEntry().getId()).isEqualTo(entry);
            assertThat(selections.availability(category.season().getId(), category.homeTeam().getId(), null).consumedEntryIds()).contains(entry);
            assertThat(selections.validate(category.season().getId(), category.homeTeam().getId(), null, entry, rr).valid()).isFalse();
            assertThat(selections.validate(category.season().getId(), category.awayTeam().getId(), null, entry, rr).valid()).isTrue();
            var fixedRace = races.findById(fixed.race().getId()).orElseThrow();
            assertThat(fixedRace.getCar().getId()).isEqualTo(rr);
            assertThat(fixedRace.getCarPoolEntry()).isNull();
            assertThat(fixedRace.getMatchday().getSeason().getCarPoolMode()).isEqualTo(CarPoolMode.FIXED_CARS);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-entry-file", "missing-clause-file", "missing-member-file", "foreign-entry", "dangling-member", "duplicate-use", "wrong-count", "unsupported-version", "dangling-one-make-car", "wrong-one-make-member", "multiple-active-seasons"})
    void givenMalformedCategoryArchive_whenStagedOrExecuted_thenRejectBeforeAnyWipe(String kind) throws Exception {
        // given
        if (kind.equals("foreign-entry")) {
            tx.executeWithoutResult(status -> {
                foreignSeason = helper.createSeason("Test foreign pool " + UUID.randomUUID()).getId();
                pools.changeMode(foreignSeason, CarPoolMode.CATEGORY_POOL);
                foreignEntry = pools.saveEntry(foreignSeason, null, "Test foreign entry", List.of(
                        new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, null, 9891)));
                pools.freeze(foreignSeason);
            });
        }
        byte[] valid = export();
        byte[] broken = malformed(valid, kind);
        var reason = kind.equals("unsupported-version") ? BackupArchiveException.Reason.SCHEMA_MISMATCH : BackupArchiveException.Reason.DATA_MISMATCH;
        String message = switch (kind) {
            case "missing-entry-file" -> "Missing entry reference";
            case "missing-clause-file" -> "Pool entry has no clauses";
            case "missing-member-file" -> "Frozen pool entry is empty";
            case "foreign-entry" -> "Race pool entry belongs to a different season";
            case "dangling-member" -> "Missing car reference";
            case "dangling-one-make-car" -> "Missing oneMakeCar reference";
            case "wrong-one-make-member" -> "Frozen entries overlap";
            case "duplicate-use" -> "Duplicate entry consumption";
            case "wrong-count" -> "season_car_pool_members: manifest announces";
            case "multiple-active-seasons" -> "more than one active season";
            default -> "999";
        };
        // when / then
        assertThatThrownBy(() -> imports.stage(upload(broken))).isInstanceOfSatisfying(BackupArchiveException.class,
                error -> {
                    assertThat(error.reason()).isEqualTo(reason);
                    assertThat(error.getMessage()).contains(message);
                });
        verify(imports, never()).wipeAllTables(anyMap());
        var preview = imports.stage(upload(valid));
        Files.write(ROOT.resolve("staging/upload-" + preview.stagingId() + ".zip"), broken);
        clearInvocations(imports);
        assertThatThrownBy(() -> imports.execute(preview.stagingId())).isInstanceOf(BackupImportException.class).cause()
                .isInstanceOfSatisfying(BackupArchiveException.class, error -> {
                    assertThat(error.reason()).isEqualTo(reason);
                    assertThat(error.getMessage()).contains(message);
                });
        verify(imports, never()).wipeAllTables(anyMap());
        tx.executeWithoutResult(status -> {
            var saved = races.findById(category.race().getId()).orElseThrow();
            assertThat(saved.getCar().getId()).isEqualTo(mr);
            assertThat(saved.getCarPoolEntry().getId()).isEqualTo(entry);
            assertThat(entries.findById(entry).orElseThrow().getMembers()).extracting(member -> member.getCar().getId())
                    .containsExactlyInAnyOrder(mr, rr);
            assertThat(races.findById(fixed.race().getId()).orElseThrow().getCar().getId()).isEqualTo(rr);
        });
        imports.deleteStagingFile(preview.stagingId());
    }

    @Test
    void givenMigratedPoolSchema_whenFreshConnectionChecksOwnershipAndConstraints_thenRejectInvalidRowsAndReferencedDeletes() throws Exception {
        // given
        try (var connection = dataSource.getConnection()) {
            // when / then
            Map<String, Integer> nullable = new LinkedHashMap<>();
            Map<String, String> defaults = new LinkedHashMap<>();
            try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, null, null)) {
                while (columns.next()) {
                    String key = (columns.getString("TABLE_NAME") + "." + columns.getString("COLUMN_NAME")).toLowerCase(Locale.ROOT);
                    nullable.put(key, columns.getInt("NULLABLE"));
                    defaults.put(key, columns.getString("COLUMN_DEF"));
                }
            }
            assertThat(defaults.get("seasons.car_pool_mode")).contains("FIXED_CARS");
            assertThat(defaults.get("seasons.car_pool_state")).contains("DRAFT");
            for (var column : List.of("seasons.car_pool_mode", "seasons.car_pool_state", "season_car_pool_entries.season_id",
                    "season_car_pool_entries.label", "season_car_pool_clauses.category", "season_car_pool_members.entry_id", "season_car_pool_members.car_id"))
                assertThat(nullable.get(column)).as(column + " is required").isZero();
            for (var column : List.of("races.car_pool_entry_id", "season_car_pool_clauses.drivetrain", "season_car_pool_clauses.model_year"))
                assertThat(nullable.get(column)).as(column + " remains optional").isEqualTo(1);
            rejects(connection, "UPDATE seasons SET car_pool_mode='INVALID' WHERE id=?", "chk_season_car_pool_mode", category.season().getId());
            rejects(connection, "UPDATE seasons SET car_pool_state='INVALID' WHERE id=?", "chk_season_car_pool_state", category.season().getId());
            rejects(connection, "INSERT INTO season_car_pool_entries (id,season_id,label,sort_index) VALUES (?,?,'Test orphan',0)",
                    "fk_car_pool_entry_season", UUID.randomUUID(), UUID.randomUUID());
            rejects(connection, "INSERT INTO season_car_pool_members (id,entry_id,car_id) VALUES (?,?,?)",
                    "fk_car_pool_member_entry", UUID.randomUUID(), UUID.randomUUID(), mr);
            rejects(connection, "INSERT INTO season_car_pool_members (id,entry_id,car_id) VALUES (?,?,?)",
                    "fk_car_pool_member_car", UUID.randomUUID(), entry, UUID.randomUUID());
            rejects(connection, "INSERT INTO season_car_pool_members (id,entry_id,car_id) VALUES (?,?,?)",
                    "uq_car_pool_member", UUID.randomUUID(), entry, mr);
            rejects(connection, "INSERT INTO season_car_pool_members (id,entry_id,car_id) VALUES (?,?,?)",
                    "null", UUID.randomUUID(), entry, null);
            rejects(connection, "UPDATE season_car_pool_clauses SET category='INVALID' WHERE entry_id=?", "chk_car_pool_clause_category", entry);
            rejects(connection, "UPDATE season_car_pool_clauses SET drivetrain='INVALID' WHERE entry_id=?", "chk_car_pool_clause_drivetrain", entry);
            for (var year : List.of(999, 10000)) rejects(connection, "UPDATE season_car_pool_clauses SET model_year=? WHERE entry_id=?",
                    "chk_car_pool_clause_year", year, entry);
            rejects(connection, "DELETE FROM season_car_pool_entries WHERE id=?", "foreign key", entry);
            rejects(connection, "UPDATE season_car_pool_entries SET one_make_car_id=? WHERE id=?", "fk_car_pool_entry_one_make", UUID.randomUUID(), oneMakeEntry);
            rejects(connection, "DELETE FROM cars WHERE id=?", "foreign key", mr);
            try (var query = connection.prepareStatement("SELECT car_id FROM season_car_pool_members WHERE entry_id=?")) {
                query.setObject(1, entry);
                try (var rows = query.executeQuery()) {
                    var ids = new ArrayList<UUID>();
                    while (rows.next()) ids.add(UUID.fromString(rows.getString(1)));
                    assertThat(ids).containsExactlyInAnyOrder(mr, rr);
                }
            }
        }
    }

    private void rejects(Connection connection, String sql, String constraint, Object... values) {
        assertThatThrownBy(() -> {
            try (var statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
                statement.executeUpdate();
            }
        }).isInstanceOf(SQLException.class).satisfies(error ->
                assertThat(error.getMessage().toLowerCase(Locale.ROOT)).contains(constraint));
    }

    private RaceService.SaveResult save(TestHelper.SeasonFixture fixture, UUID car, UUID poolEntry) {
        return raceService.saveRace(fixture.race().getId(), fixture.matchday().getId(), fixture.homeTeam().getId(),
                fixture.awayTeam().getId(), null, car, null, 5, null, null, null, null, null,
                TimeProgressionMode.MULTIPLIER, 0, null, null, null, null, poolEntry);
    }

    private UUID car(String suffix, Drivetrain drive, Integer year, boolean cleared) {
        var car = new Car("Test pool backup " + suffix, drive + " " + UUID.randomUUID());
        car.setCategory(CarCategory.GR_3);
        car.setDrivetrain(drive);
        car.setModelYear(year);
        car.setModelYearOverride(cleared);
        car.setModelYearSource(cleared ? "MANUAL" : "Test accepted source");
        car.setModelYearEvidence(cleared ? "Deliberately cleared test year" : "Test numeric-ID source review");
        if (cleared) {
            car.setProposedModelYear(9891);
            car.setProposedModelYearEvidence("Test candidate, not accepted");
        }
        return cars.saveAndFlush(car).getId();
    }

    private byte[] export() throws IOException {
        var output = new ByteArrayOutputStream();
        archives.writeZip(output, Instant.now());
        return output.toByteArray();
    }

    private MockMultipartFile upload(byte[] bytes) {
        return new MockMultipartFile("file", "test-category-release.zip", "application/zip", bytes);
    }

    private byte[] malformed(byte[] valid, String kind) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(valid))) {
            ZipEntry file;
            while ((file = zip.getNextEntry()) != null) files.put(file.getName(), zip.readAllBytes());
        }
        var manifest = (ObjectNode) JSON.readTree(files.get("manifest.json"));
        if (kind.startsWith("missing-")) {
            String table = switch (kind) {
                case "missing-entry-file" -> "season-car-pool-entries";
                case "missing-clause-file" -> "season-car-pool-clauses";
                default -> "season-car-pool-members";
            };
            files.remove("data/" + table + ".json");
        } else if (kind.equals("multiple-active-seasons")) {
            var rows = (ArrayNode) JSON.readTree(files.get("data/seasons.json"));
            ((ObjectNode) rows.get(0)).put("active", true);
            ((ObjectNode) rows.get(1)).put("active", true);
            files.put("data/seasons.json", JSON.writeValueAsBytes(rows));
        } else if (kind.equals("dangling-one-make-car") || kind.equals("wrong-one-make-member")) {
            boolean definition = kind.equals("dangling-one-make-car");
            String file = definition ? "data/season-car-pool-entries.json" : "data/season-car-pool-members.json";
            var rows = (ArrayNode) JSON.readTree(files.get(file));
            for (var row : rows) {
                if (row.path(definition ? "id" : "entry").asText().equals(oneMakeEntry.toString())) {
                    ((ObjectNode) row).put(definition ? "oneMakeCar" : "car", definition ? UUID.randomUUID().toString() : mr.toString());
                }
            }
            files.put(file, JSON.writeValueAsBytes(rows));
        } else if (kind.equals("wrong-count")) {
            var counts = (ObjectNode) manifest.get("table_counts");
            counts.put("season_car_pool_members", counts.path("season_car_pool_members").asLong() + 1);
        } else if (kind.equals("unsupported-version")) {
            manifest.put("schema_version", 999);
        } else {
            String file = kind.equals("dangling-member") ? "data/season-car-pool-members.json" : "data/races.json";
            var rows = (ArrayNode) JSON.readTree(files.get(file));
            if (kind.equals("dangling-member")) ((ObjectNode) rows.get(0)).put("car", UUID.randomUUID().toString());
            else {
                for (var row : rows) if (row.path("id").asText().equals(category.race().getId().toString())) {
                    if (kind.equals("foreign-entry")) ((ObjectNode) row).put("carPoolEntry", foreignEntry.toString());
                    else {
                        rows.add(((ObjectNode) row).deepCopy().put("id", UUID.randomUUID().toString()));
                        var counts = (ObjectNode) manifest.get("table_counts");
                        counts.put("races", counts.path("races").asLong() + 1);
                    }
                    break;
                }
            }
            files.put(file, JSON.writeValueAsBytes(rows));
        }
        files.put("manifest.json", JSON.writeValueAsBytes(manifest));
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
