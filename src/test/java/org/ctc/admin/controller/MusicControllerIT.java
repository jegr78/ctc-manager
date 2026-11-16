package org.ctc.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.ctc.domain.repository.MusicTrackRepository;
import org.ctc.domain.service.FileStorageService;
import org.ctc.domain.service.MusicLibraryService;
import org.ctc.media.MediaTool;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@CtcDevSpringBootContext
@AutoConfigureMockMvc
@Tag("integration")
class MusicControllerIT {

    @Autowired MockMvc mvc;
    @Autowired MusicTrackRepository repository;
    @Autowired MusicLibraryService library;
    @Autowired FileStorageService files;
    @Autowired MediaTool media;
    @TempDir Path dir;

    @AfterEach
    void cleanUp() {
        repository.findAll().stream()
                .filter(track -> track.getTitle().startsWith("Test_"))
                .forEach(track -> library.delete(track.getId()));
    }

    @Test
    void givenMp3AndMetadata_whenUpload_thenListedWithDuration() throws Exception {
        // given
        var file = new MockMultipartFile("file", "test-sine.mp3", "audio/mpeg", sineMp3(4));

        // when
        mvc.perform(multipart("/admin/music").file(file)
                        .param("title", "Test_Sine").param("artist", "Test Artist").param("credit", "Test credit line"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/music"))
                .andExpect(flash().attributeExists("successMessage"));

        // then
        var stored = repository.findAll().stream().filter(track -> track.getTitle().equals("Test_Sine")).findFirst().orElseThrow();
        assertThat(stored.getDurationSeconds()).isEqualTo(4);
        assertThat(files.exists(stored.getFilePath())).as("MP3 stored under uploads").isTrue();
        String html = mvc.perform(get("/admin/music")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(html).select("[data-music-track] td").eachText()).contains("Test_Sine", "Test Artist", "0:04");
    }

    @Test
    void givenMissingTitle_whenUpload_thenFormKeepsArtistAndShowsFieldError() throws Exception {
        // given
        var file = new MockMultipartFile("file", "test-sine.mp3", "audio/mpeg", sineMp3(1));

        // when
        String html = mvc.perform(multipart("/admin/music").file(file).param("title", "").param("artist", "Kept Artist"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/music"))
                .andExpect(model().attributeHasFieldErrors("musicTrackForm", "title"))
                .andReturn().getResponse().getContentAsString();

        // then
        var page = Jsoup.parse(html);
        assertThat(page.select("#music-artist").attr("value")).isEqualTo("Kept Artist");
        assertThat(page.select("#music-title-error").text()).isNotBlank();
        assertThat(page.select("#music-title").attr("aria-describedby")).contains("music-title-error");
    }

    @Test
    void givenNonMp3_whenUpload_thenErrorShownAndNothingStored() throws Exception {
        // given
        var file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        long before = repository.count();

        // when
        String html = mvc.perform(multipart("/admin/music").file(file).param("title", "Test_Fake").param("artist", "A"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // then
        assertThat(Jsoup.parse(html).select("#music-file-error").text()).isEqualTo("Only MP3 files are accepted.");
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void givenStoredTrack_whenDelete_thenRemovedWithFile() throws Exception {
        // given
        var track = library.upload(new MockMultipartFile("file", "d.mp3", "audio/mpeg", sineMp3(1)), "Test_Delete", "A", null);

        // when
        mvc.perform(post("/admin/music/" + track.getId() + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/music"));

        // then
        assertThat(repository.existsById(track.getId())).isFalse();
        assertThat(files.exists(track.getFilePath())).as("file deleted after commit").isFalse();
    }

    private byte[] sineMp3(int seconds) throws Exception {
        Path out = dir.resolve("sine-" + seconds + ".mp3");
        media.run(List.of("-f", "lavfi", "-i", "sine=d=" + seconds, "-y", out.toString()), Duration.ofSeconds(30));
        return Files.readAllBytes(out);
    }
}
