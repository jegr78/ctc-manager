package org.ctc.gt7sync.environment;

import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.service.*;
import org.ctc.backup.service.BackupArchiveService;
import org.ctc.backup.service.BackupImportService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:environmentimport;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=validate","spring.flyway.locations=classpath:db/migration","logging.config=classpath:logback-test.xml","ctc.outbound.enabled=false"})
@ActiveProfiles("prod") @AutoConfigureMockMvc @Tag("integration") @Import(TrackEnvironmentImportIT.Catalog.class)
class TrackEnvironmentImportIT {
    static final String NAME="Test source layout "+UUID.randomUUID();
    static final String KEY="Europe-Middle East | 24 Heures du Mans Racing Circuit (All Layouts)";
    static final Path ROOT;
    static {try{ROOT=Files.createTempDirectory("ctc-environment-import-");}catch(Exception e){throw new IllegalStateException(e);}}
    @TestConfiguration static class Catalog {
        @Bean @Primary TrackEnvironmentCatalog importTestCatalog(){return new TrackEnvironmentCatalog(List.of(new TrackEnvironmentCatalog.Mapping(NAME,KEY)));}
    }
    @DynamicPropertySource static void paths(DynamicPropertyRegistry r) {
        r.add("app.upload-dir",()->ROOT.resolve("uploads").toString());r.add("app.backup.import-backups-dir",()->ROOT.resolve("backups").toString());r.add("app.backup.staging-dir",()->ROOT.resolve("staging").toString());
    }
    @Autowired TrackRepository tracks;
    @Autowired TrackEnvironmentImportService imports;
    @Autowired TrackEnvironmentService manual;
    @Autowired PickSessionEpoch epoch;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired EntityManager entities;
    @Autowired BackupArchiveService archives;
    @Autowired BackupImportService restores;
    UUID id;
    final java.security.Principal actor=()->"test-manager";
    @BeforeEach void create(){var track=new Track(NAME,"Test country");track.setImageUrl("/uploads/tracks/test-owner.png");id=tracks.saveAndFlush(track).getId();}
    @AfterEach void cleanup(){tracks.deleteById(id);}
    @Test @WithMockUser("test-manager")
    void givenPreview_whenApplyingNativeSelection_thenPersistOnlyEnvironmentAndRenderProvenance()throws Exception {
        var untouched=tracks.saveAndFlush(new Track("Test unselected "+UUID.randomUUID()));
        try {
            var result=mvc.perform(get("/admin/gt7-sync/environment")).andExpect(status().isOk()).andExpect(view().name("admin/track-environment-preview")).andReturn();
            var preview=(TrackEnvironmentImportService.Preview)result.getModelAndView().getModel().get("preview");
            assertThat(preview.entries().stream().filter(e->e.id().equals(id)).findFirst().orElseThrow().selectable()).isTrue();
            mvc.perform(post("/admin/gt7-sync/environment/apply").with(csrf()).param("previewId",preview.id().toString()).param("selectedTracks",id.toString()).header("Sec-Fetch-Site","same-origin").header("Accept-Encoding","gzip, deflate").header("Referer","http://localhost/admin/gt7-sync/environment"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("successMessage","1 layout environments imported"));
            var saved=tracks.findById(id).orElseThrow();assertThat(saved.getTimeOfDayOptions()).hasSize(11);assertThat(saved.getRainCapability()).isEqualTo("RAIN");
            assertThat(saved.getEnvironmentVersion()).isEqualTo(1);assertThat(saved.getEnvironmentSource()).contains(KEY);assertThat(saved.isEnvironmentOverride()).isFalse();
            assertThat(saved.getImageUrl()).isEqualTo("/uploads/tracks/test-owner.png");assertThat(saved.getCountry()).isEqualTo("Test country");
            assertThat(tracks.findById(untouched.getId()).orElseThrow().getEnvironmentVersion()).isZero();
            mvc.perform(get("/admin/tracks/"+id+"/edit")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Imported from")));
            mvc.perform(post("/admin/gt7-sync/environment/apply").with(csrf()).param("previewId",preview.id().toString()).param("selectedTracks",id.toString())).andExpect(flash().attributeExists("errorMessage"));
        }finally{tracks.deleteById(untouched.getId());}
    }
    @Test void givenImportedSource_whenManuallySavingUnknown_thenProtectEvenEmptyValuesFromFutureImports()throws Exception {
        var preview=imports.bundled(actor);imports.apply(preview.id(),List.of(id),actor);var before=manual.view(id);
        manual.save(id,"",TrackRainCapability.UNKNOWN,before.version(),before.generation());
        var saved=tracks.findById(id).orElseThrow();assertThat(saved.isEnvironmentOverride()).isTrue();assertThat(saved.getEnvironmentSource()).isNotNull();
        var next=imports.bundled(actor);assertThat(next.entries().stream().filter(e->e.id().equals(id)).findFirst().orElseThrow().status()).isEqualTo(TrackEnvironmentImportService.Status.MANUAL);
        assertThatThrownBy(()->imports.apply(next.id(),List.of(id),actor)).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);
    }
    @Test void givenCachedPreview_whenAnotherWriterChangesLayout_thenRejectWithoutOverwritingItsValues()throws Exception {
        var preview=imports.bundled(actor);var view=manual.view(id);manual.save(id,"Night",TrackRainCapability.DRY_ONLY,view.version(),view.generation());
        assertThatThrownBy(()->imports.apply(preview.id(),List.of(id),actor)).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);
        assertThat(tracks.findById(id).orElseThrow().getTimeOfDayOptions()).containsExactly("Night");
    }
    @Test @WithMockUser("test-manager")
    void givenForgedOrUnauthenticatedPost_whenApplying_thenEnforceAuthCsrfAndDtoBoundary()throws Exception {
        var preview=imports.bundled(actor);
        mvc.perform(post("/admin/gt7-sync/environment/apply").param("previewId",preview.id().toString()).param("selectedTracks",id.toString())).andExpect(status().isForbidden());
        mvc.perform(post("/admin/gt7-sync/environment/apply").with(csrf()).param("previewId",preview.id().toString()).param("selectedTracks",id.toString()).param("environmentSource","forged"))
            .andExpect(flash().attributeExists("errorMessage"));
        mvc.perform(post("/admin/gt7-sync/environment/apply").with(csrf()).param("previewId","not-a-uuid").param("selectedTracks",id.toString())).andExpect(flash().attributeExists("errorMessage"));
        assertThat(tracks.findById(id).orElseThrow().getEnvironmentVersion()).isZero();
    }
    @Test void givenNoManagementAuthentication_whenLoadingPreview_thenRequireLogin()throws Exception {
        mvc.perform(get("/admin/gt7-sync/environment")).andExpect(status().isUnauthorized());
    }
    @Test @WithMockUser("test-manager")
    void givenOutboundDisabled_whenRefreshing_thenKeepOfflinePreviewUsableAndNeverChangeData()throws Exception {
        mvc.perform(post("/admin/gt7-sync/environment/refresh").with(csrf())).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage"));
        mvc.perform(get("/admin/gt7-sync/environment")).andExpect(status().isOk());assertThat(tracks.findById(id).orElseThrow().getEnvironmentVersion()).isZero();
    }
    @Test void givenSourcePairConstraint_whenWritingInvalidPair_thenDatabaseRejectsIt() {
        assertThatThrownBy(()->jdbc.update("UPDATE tracks SET environment_import_hash=? WHERE id=?","a".repeat(64),id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(tracks.findById(id).orElseThrow().getEnvironmentImportHash()).isNull();
    }
    @Test void givenImportedSource_whenBackupRoundTrip_thenKeepProvenanceAndRejectPreRestorePreview()throws Exception {
        var preview=imports.bundled(actor);imports.apply(preview.id(),List.of(id),actor);var before=tracks.findById(id).orElseThrow();
        var stale=imports.bundled(actor);var out=new java.io.ByteArrayOutputStream();archives.writeZip(out,Instant.now());
        var staged=restores.stage(new MockMultipartFile("file","source-roundtrip.zip","application/zip",out.toByteArray()));restores.execute(staged.stagingId());
        var after=tracks.findById(id).orElseThrow();assertThat(after.getEnvironmentSource()).isEqualTo(before.getEnvironmentSource());assertThat(after.getEnvironmentImportHash()).isEqualTo(before.getEnvironmentImportHash());
        assertThat(after.isEnvironmentOverride()).isFalse();assertThat(after.getTimeOfDayOptions()).containsExactlyElementsOf(before.getTimeOfDayOptions());
        assertThatThrownBy(()->imports.apply(stale.id(),List.of(id),actor)).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);
    }
    @Test void givenMalformedProvenanceInStaging_whenExecutingRestore_thenRejectBeforeDeletingAnyLiveRow()throws Exception {
        var out=new java.io.ByteArrayOutputStream();archives.writeZip(out,Instant.now());
        var rewritten=new java.io.ByteArrayOutputStream();var json=new com.fasterxml.jackson.databind.ObjectMapper();
        try(var input=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(out.toByteArray()));var output=new java.util.zip.ZipOutputStream(rewritten)) {
            java.util.zip.ZipEntry entry;
            while((entry=input.getNextEntry())!=null) {
                byte[] bytes=input.readAllBytes();
                if(entry.getName().equals("data/tracks.json")) {
                    var rows=json.readTree(bytes);
                    for(var row:rows)if(row.path("id").asText().equals(id.toString())) {
                        var object=(com.fasterxml.jackson.databind.node.ObjectNode)row;
                        object.put("environmentSource","null");object.put("environmentImportHash","a".repeat(64));
                    }
                    bytes=json.writeValueAsBytes(rows);
                }
                output.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));output.write(bytes);output.closeEntry();
            }
        }
        long count=tracks.count();var staging=UUID.randomUUID();var path=ROOT.resolve("staging/upload-"+staging+".zip");Files.createDirectories(path.getParent());Files.write(path,rewritten.toByteArray());
        assertThatThrownBy(()->restores.execute(staging)).isInstanceOf(org.ctc.backup.exception.BackupImportException.class)
            .cause().isInstanceOfSatisfying(org.ctc.backup.exception.BackupArchiveException.class,failure->assertThat(failure.reason()).isEqualTo(org.ctc.backup.exception.BackupArchiveException.Reason.DATA_MISMATCH));
        assertThat(tracks.count()).isEqualTo(count);assertThat(tracks.findById(id).orElseThrow().getEnvironmentVersion()).isZero();
    }
}
