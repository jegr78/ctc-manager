package org.ctc.sitegen.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pagessecurity;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate", "logging.config=classpath:logback-test.xml",
        "ctc.site.publication.directory=target/pages-security-${surefire.forkNumber:0}",
        "ctc.site.publication.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class SitePublicationControllerIT {
    @Autowired MockMvc mvc;
    @Autowired SitePublicationService service;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.ctc.sitegen.SiteGeneratorService generator;
    @TempDir Path source;

    private SitePublicationService.Snapshot snapshot() throws Exception {
        Files.writeString(source.resolve("index.html"), "<html><script>window.parent.document.body</script></html>");
        Files.writeString(source.resolve("style.css"), "body{color:red}");
        return service.capture(source);
    }

    @Test
    void givenAnonymous_whenPreviewOrPublish_thenAuthenticationIsRequired() throws Exception {
        var snapshot = snapshot();
        mvc.perform(get("/admin/site-preview/" + snapshot.id() + "/index.html")).andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/site-publication").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void givenOwnerWithoutCsrf_whenPublish_thenRequestIsForbidden() throws Exception {
        mvc.perform(post("/admin/site-publication")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void givenOwner_whenPreviewedDirectly_thenResponseSandboxesGeneratedScriptsAndDisablesCaching() throws Exception {
        var snapshot = snapshot();
        mvc.perform(get("/admin/site-preview/" + snapshot.id() + "/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Security-Policy", "sandbox allow-scripts; default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'none'; form-action 'none'; base-uri 'none'; frame-src 'none'; frame-ancestors 'self'"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("window.parent.document")));
        mvc.perform(get("/admin/site-preview/" + snapshot.id() + "/style.css")).andExpect(status().isOk());
        mvc.perform(get("/admin/site-preview/" + snapshot.id() + "/private.env"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void givenSelectedSnapshot_whenGenerateWorkspaceOpened_thenIdentityAndExplicitApprovalAreShown() throws Exception {
        var snapshot = snapshot();
        var result = mvc.perform(get("/admin/generate").param("snapshot", snapshot.id()))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains(snapshot.manifestSha256(), "sandbox=", "Publish this snapshot");
    }
    @Test
    @WithMockUser
    void givenSelectedSnapshot_whenNextGenerationFails_thenPreviousSelectionRemainsReviewable() throws Exception {
        var snapshot = snapshot();
        var failure = new org.ctc.sitegen.SiteGeneratorService.GenerationResult();
        failure.addError("fixture generation failure");
        org.mockito.Mockito.when(generator.generate()).thenReturn(failure);
        mvc.perform(post("/admin/generate").with(csrf()).param("snapshot", snapshot.id()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/generate?snapshot=" + snapshot.id()));
    }

    @Test
    @WithMockUser
    void givenOwnerConfirmation_whenPublished_thenOnlySelectedSnapshotIsQueued() throws Exception {
        var snapshot = snapshot();
        mvc.perform(post("/admin/site-publication").with(csrf())
                        .param("snapshotId", snapshot.id()).param("manifestSha256", snapshot.manifestSha256()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
        mvc.perform(post("/admin/site-publication").with(csrf()).param("confirmed", "true")
                        .param("snapshotId", snapshot.id()).param("manifestSha256", snapshot.manifestSha256()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/generate?snapshot=" + snapshot.id()));
        assertThat(service.publication().snapshotId()).isEqualTo(snapshot.id());
        assertThat(service.publication().stage()).isEqualTo("queued");
    }

}
