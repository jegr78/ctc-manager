package org.ctc.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.ctc.TestHelper;
import org.ctc.admin.dto.MatchdayGraphicData;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.service.FileStorageService;
import org.ctc.domain.service.MusicLibraryService;
import org.ctc.media.MediaTool;
import org.ctc.media.job.MediaJobExecutor;
import org.ctc.media.job.MediaJobKind;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.trailer.TrailerChecklistItem;
import org.ctc.trailer.TrailerPlan;
import org.ctc.trailer.TrailerPlanService;
import org.ctc.trailer.TrailerSceneRenderer;
import org.ctc.trailer.TrailerScope;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@CtcDevSpringBootContext
@AutoConfigureMockMvc
@Tag("integration")
@Transactional
class TrailerControllerIT {
    @Autowired MockMvc mvc;
    @Autowired TestHelper helper;
    @Autowired MediaJobExecutor jobs;
    @Autowired FileStorageService files;
    @Autowired MusicLibraryService music;
    @Autowired MediaTool media;
    @MockitoBean TrailerPlanService plans;
    @MockitoBean TrailerSceneRenderer renderer;

    private final CountDownLatch release = new CountDownLatch(1);
    private Matchday matchday;
    private Path outputDir;

    @BeforeEach
    void setUp() {
        matchday = helper.createFullSeasonFixture("Test_TrailerCtl").matchday();
        outputDir = files.resolve("/uploads/trailers/matchday/" + matchday.getId());
    }

    @AfterEach
    void cleanUp() throws Exception {
        release.countDown();
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (jobs.status("matchday:" + matchday.getId()).map(s -> s.running()).orElse(false)
                || jobs.status("test:busy").map(s -> s.running()).orElse(false)) {
            if (System.nanoTime() > deadline) throw new AssertionError("media job did not finish");
            Thread.sleep(50);
        }
        if (Files.exists(outputDir)) {
            try (var walk = Files.walk(outputDir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    @Test
    void givenBlockers_whenGetMatchday_thenChecklistLinksAndStartDisabled() throws Exception {
        // given
        planned(plan(List.of(
                new TrailerChecklistItem("Setup of HOM vs AWY leg 1 is not published", "/admin/races/" + UUID.randomUUID()))));

        // when
        var html = mvc.perform(get("/admin/matchdays/" + matchday.getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // then
        var section = Jsoup.parse(html).selectFirst("#matchday-trailer");
        assertThat(section).as("trailer section").isNotNull();
        assertThat(section.select("[data-trailer-blockers] a").text()).isEqualTo("Setup of HOM vs AWY leg 1 is not published");
        assertThat(section.selectFirst("button[type=submit]").hasAttr("disabled")).isTrue();
    }

    @Test
    void givenReady_whenPostTrailer_thenRedirectWithStatusRunning() throws Exception {
        // given
        planned(plan(List.of()));
        heldRenderer();
        uploadMusic();

        // when
        mvc.perform(post("/admin/matchdays/" + matchday.getId() + "/trailer").param("musicId", ""))
                .andExpect(redirectedUrl("/admin/matchdays/" + matchday.getId() + "#matchday-trailer"))
                .andExpect(flash().attribute("successMessage", "Rendering the trailer in the background."));

        // then
        mvc.perform(get("/admin/matchdays/" + matchday.getId() + "/trailer/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true));
    }

    @Test
    void givenBusy_whenPostTrailer_thenErrorMessage() throws Exception {
        // given
        planned(plan(List.of()));
        uploadMusic();
        jobs.submit(MediaJobKind.CUTOUT, "test:busy", ctx -> release.await(30, TimeUnit.SECONDS));

        // when / then
        mvc.perform(post("/admin/matchdays/" + matchday.getId() + "/trailer").param("musicId", ""))
                .andExpect(redirectedUrl("/admin/matchdays/" + matchday.getId() + "#matchday-trailer"))
                .andExpect(flash().attribute("errorMessage", "Another media job is running."));
    }

    @Test
    void givenOutputs_whenDownloadVideo_thenVideoMp4AttachmentNamedCtcTrailerSeasonMatchday() throws Exception {
        // given
        writeOutputs("createdAt=2026-11-16T10\\:00\\:00Z\nmusic=Test Song\nname=ctc-trailer-test-season-test-md1\n");

        // when / then
        mvc.perform(get("/admin/matchdays/" + matchday.getId() + "/trailer/video"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("video/mp4"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"ctc-trailer-test-season-test-md1.mp4\""))
                .andExpect(content().bytes("mp4".getBytes()));
    }

    @Test
    void givenUnknownFile_whenDownload_then404() throws Exception {
        // given
        writeOutputs("createdAt=2026-11-16T10\\:00\\:00Z\nmusic=Test Song\nname=ctc-trailer\n");

        // when / then
        mvc.perform(get("/admin/matchdays/" + matchday.getId() + "/trailer/trailer.properties")).andExpect(status().isNotFound());
    }

    @Test
    void givenMalformedTrailerMetadata_whenGetMatchday_thenPageRendersWithoutLatestTrailer() throws Exception {
        // given
        planned(plan(List.of()));
        writeOutputs("music=Test Song\n");

        // when
        var html = mvc.perform(get("/admin/matchdays/" + matchday.getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // then
        assertThat(Jsoup.parse(html).select("#matchday-trailer a[href$=/trailer/video]")).isEmpty();
    }

    private void planned(TrailerPlan plan) {
        when(plans.plan(any())).thenReturn(plan);
        when(plans.checklist(any())).thenReturn(plan);
    }

    private void writeOutputs(String properties) throws Exception {
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("trailer.mp4"), "mp4");
        Files.writeString(outputDir.resolve("layers.zip"), "zip");
        Files.writeString(outputDir.resolve("youtube.txt"), "text");
        Files.writeString(outputDir.resolve("trailer.properties"), properties);
    }

    private void heldRenderer() throws Exception {
        doAnswer(call -> {
            release.await(30, TimeUnit.SECONDS);
            throw new java.io.IOException("Released by the test");
        }).when(renderer).render(any(), any(), any());
    }

    private void uploadMusic() throws Exception {
        Path mp3 = Files.createTempFile("trailer-ctl", ".mp3");
        media.run(List.of("-f", "lavfi", "-i", "sine=d=1", "-c:a", "libmp3lame", "-y", mp3.toString()), Duration.ofSeconds(30));
        music.upload(new MockMultipartFile("file", "song.mp3", "audio/mpeg", Files.readAllBytes(mp3)), "Test_TrailerCtl Song", "Test", null);
        Files.delete(mp3);
    }

    private TrailerPlan plan(List<TrailerChecklistItem> blockers) {
        var overview = new MatchdayGraphicData("Test MD1", "Test Season", "2026", null, null, List.of());
        return new TrailerPlan(new TrailerScope.MatchdayScope(matchday.getId()), "Test MD1", "Test Season", overview, List.of(), blockers,
                Set.of());
    }
}
