package org.ctc.admin.controller;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.ctc.domain.model.*;
import org.ctc.domain.service.*;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.admin.service.DataAuditService;
import org.ctc.admin.dto.DataAuditFinding.Category;
import org.ctc.backup.service.BackupArchiveService;
import org.ctc.backup.service.BackupImportService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:trackenvironment;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.locations=classpath:db/migration", "logging.config=classpath:logback-test.xml", "ctc.outbound.enabled=false"})
@AutoConfigureMockMvc @ActiveProfiles("prod") @Tag("integration")
class TrackEnvironmentIT {
    static final Path ROOT;
    static { try { ROOT=Files.createTempDirectory("ctc-track-environment-it-"); } catch(Exception e) { throw new IllegalStateException(e); } }
    @DynamicPropertySource static void paths(DynamicPropertyRegistry r) {
        r.add("app.upload-dir", () -> ROOT.resolve("uploads").toString());
        r.add("app.backup.import-backups-dir", () -> ROOT.resolve("backups").toString());
        r.add("app.backup.staging-dir", () -> ROOT.resolve("staging").toString());
    }
    @Autowired MockMvc mvc;
    @Autowired TrackRepository tracks;
    @Autowired TrackEnvironmentService environment;
    @Autowired TrackService trackService;
    @Autowired PickSessionEpoch epoch;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired DataAuditService audit;
    @Autowired BackupArchiveService archives;
    @Autowired BackupImportService imports;
    UUID id;
    final Set<UUID> owned = new HashSet<>();
    @BeforeEach void setUp() {
        var track = new Track("Test environment " + UUID.randomUUID(), "Test country");
        track.setImageUrl("/uploads/tracks/test-environment-owner.png");
        id=tracks.saveAndFlush(track).getId(); owned.add(id);
    }
    @AfterEach void cleanup() { tx.executeWithoutResult(s -> { tracks.deleteAllById(owned); tracks.flush(); }); }
    String edit() { return "/admin/tracks/"+id+"/edit"; }
    String save() { return "/admin/tracks/"+id+"/environment"; }
    void configure() { var view=environment.view(id); environment.save(id,"Night\nSunrise",TrackRainCapability.RAIN,view.version(),view.generation()); }
    void original() { var t=tracks.findById(id).orElseThrow(); assertThat(t.getTimeOfDayOptions()).isEmpty(); assertThat(t.getRainCapability()).isEqualTo("UNKNOWN"); assertThat(t.getEnvironmentVersion()).isZero(); }

    @Test @WithMockUser("test-manager")
    void givenNewLayout_whenCreatingWithNativeFields_thenPersistOrderedOptionsWithoutChangingExistingRecords() throws Exception {
        var name="Test new environment "+UUID.randomUUID();
        mvc.perform(post("/admin/tracks/save").with(csrf()).param("name",name).param("country","Test country")
                .param("timeOfDayText","Sunrise\nNight").param("rainCapability","RAIN"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        var t=tracks.findByName(name).orElseThrow(); owned.add(t.getId());
        assertThat(t.getTimeOfDayOptions()).containsExactly("Sunrise","Night"); assertThat(t.getEnvironmentVersion()).isEqualTo(1);
        original();
    }
    @Test @WithMockUser("test-manager")
    void givenExistingLayout_whenSavingNativeEnvironment_thenReloadMetadataAndPreserveIdentityLogo() throws Exception {
        var v=environment.view(id);
        mvc.perform(post(save()).with(csrf()).param("timeOfDayText","Night\nSunrise").param("rainCapability","RAIN")
                .param("expectedVersion","0").param("expectedGeneration",v.generation()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(edit()+"#track-environment"));
        mvc.perform(get(edit())).andExpect(status().isOk()).andExpect(content().string(containsString("Night\nSunrise")))
                .andExpect(content().string(containsString("R08"))).andExpect(content().string(containsString("test-environment-owner.png")));
        var t=tracks.findById(id).orElseThrow(); assertThat(t.getTimeOfDayOptions()).containsExactly("Night","Sunrise");
        assertThat(t.getCountry()).isEqualTo("Test country"); assertThat(t.getEnvironmentVersion()).isEqualTo(1);
    }
    @Test @WithMockUser("test-manager")
    void givenInvalidNativeOptions_whenSaving_thenRetainInputAndExplainDomainErrorWithoutWrites() throws Exception {
        mvc.perform(post(save()).with(csrf()).param("timeOfDayText","Night\nNight").param("rainCapability","RAIN")
                .param("expectedVersion","0").param("expectedGeneration",epoch.managementGeneration()))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("environmentForm"))
                .andExpect(content().string(containsString("Time of day choices must be distinct")))
                .andExpect(content().string(containsString("Night\nNight")));
        original();
    }
    @Test @WithMockUser("test-manager")
    void givenStaleVersion_whenSaving_thenRejectAndKeepCurrentManualChoices() throws Exception {
        var old=environment.view(id); configure();
        mvc.perform(post(save()).with(csrf()).param("timeOfDayText","Afternoon").param("rainCapability","DRY_ONLY")
                .param("expectedVersion",Long.toString(old.version())).param("expectedGeneration",old.generation()))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("environmentForm"))
                .andExpect(content().string(containsString("environment changed; reload")));
        assertThat(tracks.findById(id).orElseThrow().getTimeOfDayOptions()).containsExactly("Night","Sunrise");
    }
    @Test @WithMockUser("test-manager")
    void givenOldGeneration_whenSaving_thenRequireReloadWithoutChangingTrack() throws Exception {
        mvc.perform(post(save()).with(csrf()).param("timeOfDayText","Night").param("rainCapability","RAIN")
                .param("expectedVersion","0").param("expectedGeneration","old-process:0"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("environmentForm"))
                .andExpect(content().string(containsString("restart or restore")));
        original();
    }
    @Test @WithMockUser("test-manager")
    void givenMissingCsrf_whenSaving_thenDenyBeforeMetadataMutation() throws Exception {
        mvc.perform(post(save()).param("timeOfDayText","Night").param("rainCapability","RAIN")
                .param("expectedVersion","0").param("expectedGeneration",epoch.managementGeneration())).andExpect(status().isForbidden());
        original();
    }
    @Test void givenAnonymousRequest_whenOpeningEditor_thenRequireAuthentication() throws Exception {
        mvc.perform(get(edit())).andExpect(status().isUnauthorized()); original();
    }
    @Test void givenManualMetadata_whenSavingIdentityAndConditionalImage_thenRetainExactEnvironment() {
        configure();
        trackService.save(id,"Test renamed "+id,"Test changed country");
        tx.executeWithoutResult(s -> tracks.fillMissingImage(id,"Test renamed "+id,"/uploads/test-new-logo.png",java.time.LocalDateTime.now()));
        var t=tracks.findById(id).orElseThrow(); assertThat(t.getTimeOfDayOptions()).containsExactly("Night","Sunrise");
        assertThat(t.getRainCapability()).isEqualTo("RAIN"); assertThat(t.getEnvironmentVersion()).isEqualTo(1);
        assertThat(t.getImageUrl()).isEqualTo("/uploads/tracks/test-environment-owner.png");
    }
    @Test void givenCachedIdentity_whenAnotherTransactionSavesMetadata_thenIdentityUpdateCannotOverwriteEnvironment() {
        var independent = new TransactionTemplate(tx.getTransactionManager());
        independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
            var cached = tracks.findById(id).orElseThrow();
            assertThat(cached.getTimeOfDayOptions()).isEmpty();
            independent.executeWithoutResult(other -> {
                var current = tracks.findById(id).orElseThrow();
                current.setTimeOfDayOptions(List.of("Night")); current.setRainCapability("RAIN"); current.setEnvironmentVersion(1);
            });
            trackService.save(id, "Test concurrent identity " + id, "Test changed country");
        });
        var saved = tracks.findById(id).orElseThrow();
        assertThat(saved.getTimeOfDayOptions()).containsExactly("Night");
        assertThat(saved.getRainCapability()).isEqualTo("RAIN");
        assertThat(saved.getEnvironmentVersion()).isEqualTo(1);
    }

    @Test void givenExhaustedVersion_whenSaving_thenRejectInsteadOfWrappingTokenAndChangingMetadata() {
        jdbc.update("UPDATE tracks SET environment_version = ? WHERE id = ?", Long.MAX_VALUE, id);
        var view = environment.view(id);
        assertThatThrownBy(() -> environment.save(id, "Night", TrackRainCapability.RAIN, view.version(), view.generation()))
                .isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class).hasMessageContaining("exhausted");
        var saved = tracks.findById(id).orElseThrow();
        assertThat(saved.getEnvironmentVersion()).isEqualTo(Long.MAX_VALUE);
        assertThat(saved.getTimeOfDayOptions()).isEmpty();
    }

    @Test void givenDifferentLayoutMetadata_whenValidatingNewTime_thenUseCurrentLayoutAndPermitUnknownFallback() {
        tx.executeWithoutResult(s -> environment.requireTimeOfDay(id,"Legacy free text"));
        configure();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> environment.requireTimeOfDay(id,"Afternoon")))
                .isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class).hasMessageContaining("current options");
        tx.executeWithoutResult(s -> environment.requireTimeOfDay(id,"Night"));
    }
    @Test void givenCachedUnknownMetadata_whenAnotherTransactionConfiguresTimes_thenValidateAgainstFreshLockedValues() {
        var independent = new TransactionTemplate(tx.getTransactionManager());
        independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            var cached = tracks.findById(id).orElseThrow(); assertThat(cached.getTimeOfDayOptions()).isEmpty();
            independent.executeWithoutResult(other -> {
                var current = tracks.findById(id).orElseThrow(); current.setTimeOfDayOptions(List.of("Night")); current.setEnvironmentVersion(1);
            });
            environment.requireTimeOfDay(id, "Legacy free text");
        })).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class).hasMessageContaining("current options");
        assertThat(tracks.findById(id).orElseThrow().getTimeOfDayOptions()).containsExactly("Night");
    }

    @Test void givenMalformedPersistedOptions_whenAuditing_thenReportWithoutRepairOrFailure() {
        jdbc.update("UPDATE tracks SET time_of_day_options = ? WHERE id = ?","Night\nNight",id);
        var before=jdbc.queryForObject("SELECT updated_at FROM tracks WHERE id = ?",java.sql.Timestamp.class,id);
        assertThat(audit.audit().findings(Category.TRACK_ENVIRONMENT)).anySatisfy(f -> assertThat(f.subject()).contains(id.toString()));
        assertThat(jdbc.queryForObject("SELECT time_of_day_options FROM tracks WHERE id = ?",String.class,id)).isEqualTo("Night\nNight");
        assertThat(jdbc.queryForObject("SELECT updated_at FROM tracks WHERE id = ?",java.sql.Timestamp.class,id)).isEqualTo(before);
    }
    @Test void givenMigratedSchema_whenWritingInvalidStateFromNewConnection_thenDatabaseRejects() throws Exception {
        for (String change:List.of("rain_capability = 'INVALID'","environment_version = -1")) {
            try (var c=dataSource.getConnection();var statement=c.prepareStatement("UPDATE tracks SET "+change+" WHERE id = ?")) {
                statement.setObject(1,id); assertThatThrownBy(statement::executeUpdate).isInstanceOf(java.sql.SQLException.class);
            }
        }
        original();
    }
    @Test void givenLegacy18Archive_whenRestoring_thenDefaultUnknownWithoutInventingLayoutOptions() throws Exception {
        byte[] legacy = rewriteArchive(18, true, false);
        configure();
        var preview = imports.stage(new MockMultipartFile("file", "legacy-track.zip", "application/zip", legacy));
        imports.execute(preview.stagingId());
        original();
        assertThat(tracks.findById(id).orElseThrow().getImageUrl()).isEqualTo("/uploads/tracks/test-environment-owner.png");
    }

    @Test void givenMalformed19Archive_whenExecutingDirectStaging_thenRejectBeforeWipeAndRetainLiveData() throws Exception {
        configure();
        var sentinel = tracks.saveAndFlush(new Track("Test retained " + UUID.randomUUID(), "Test country"));
        owned.add(sentinel.getId());
        long count = tracks.count();
        UUID staging = UUID.randomUUID();
        Path staged = ROOT.resolve("staging/upload-" + staging + ".zip");
        Files.createDirectories(staged.getParent());
        Files.write(staged, rewriteArchive(19, false, true));
        assertThatThrownBy(() -> imports.execute(staging))
                .isInstanceOf(org.ctc.backup.exception.BackupImportException.class)
                .cause().isInstanceOfSatisfying(org.ctc.backup.exception.BackupArchiveException.class, failure -> {
                    assertThat(failure.reason()).isEqualTo(org.ctc.backup.exception.BackupArchiveException.Reason.DATA_MISMATCH);
                    assertThat(failure.getMessage()).contains("Invalid track environment metadata");
                });
        assertThat(tracks.count()).isEqualTo(count);
        assertThat(tracks.findById(sentinel.getId())).isPresent();
        assertThat(tracks.findById(id).orElseThrow().getTimeOfDayOptions()).containsExactly("Night", "Sunrise");
    }

    private byte[] rewriteArchive(int version, boolean legacy, boolean malformed) throws Exception {
        var out = new ByteArrayOutputStream(); archives.writeZip(out, Instant.now());
        var rewritten = new ByteArrayOutputStream();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        try (var input = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(out.toByteArray()));
             var output = new java.util.zip.ZipOutputStream(rewritten)) {
            java.util.zip.ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                byte[] bytes = input.readAllBytes();
                if (entry.getName().equals("manifest.json")) {
                    var manifest = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(bytes);
                    manifest.put("schema_version", version); bytes = json.writeValueAsBytes(manifest);
                } else if (entry.getName().equals("data/tracks.json")) {
                    var rows = json.readTree(bytes);
                    for (var row : rows) {
                        var object = (com.fasterxml.jackson.databind.node.ObjectNode) row;
                        if (legacy) object.remove(List.of("timeOfDayOptions", "rainCapability", "environmentVersion"));
                        if (malformed && object.path("id").asText().equals(id.toString()))
                            object.putArray("timeOfDayOptions").add("Night").add("Night");
                    }
                    bytes = json.writeValueAsBytes(rows);
                }
                output.putNextEntry(new java.util.zip.ZipEntry(entry.getName())); output.write(bytes); output.closeEntry();
            }
        }
        return rewritten.toByteArray();
    }

    @Test void givenConfiguredMetadata_whenRoundTrippingBackup_thenRetainValuesAndRejectPreRestoreForm() throws Exception {
        configure();var old=environment.view(id);
        var out=new ByteArrayOutputStream(); archives.writeZip(out,Instant.now());
        var preview=imports.stage(new MockMultipartFile("file","track-environment.zip","application/zip",out.toByteArray()));
        imports.execute(preview.stagingId());
        var restored=tracks.findById(id).orElseThrow(); assertThat(restored.getTimeOfDayOptions()).containsExactly("Night","Sunrise");
        assertThat(restored.getRainCapability()).isEqualTo("RAIN");assertThat(restored.getEnvironmentVersion()).isEqualTo(1);
        assertThatThrownBy(() -> environment.save(id,"Afternoon",TrackRainCapability.DRY_ONLY,old.version(),old.generation()))
                .isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class).hasMessageContaining("restart or restore");
    }
}
