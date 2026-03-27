package de.ctc.admin.controller;

import de.ctc.domain.model.Season;
import de.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SeasonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SeasonRepository seasonRepository;

    @Test
    void shouldListSeasons() throws Exception {
        mockMvc.perform(get("/admin/seasons"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/seasons"))
                .andExpect(model().attributeExists("seasons"));
    }

    @Test
    void shouldShowNewSeasonForm() throws Exception {
        mockMvc.perform(get("/admin/seasons/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/season-form"))
                .andExpect(model().attributeExists("season"));
    }

    @Test
    void shouldCreateSeason() throws Exception {
        mockMvc.perform(post("/admin/seasons/save")
                        .param("name", "MockMvc Test Season")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/seasons"))
                .andExpect(flash().attributeExists("successMessage"));

        var saved = seasonRepository.findByName("MockMvc Test Season");
        assertTrue(saved.isPresent());
        assertTrue(saved.get().isActive());
    }

    @Test
    void shouldRejectBlankName() throws Exception {
        mockMvc.perform(post("/admin/seasons/save")
                        .param("name", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/season-form"));
    }

    @Test
    void shouldEditSeason() throws Exception {
        var season = seasonRepository.save(new Season("Edit Test"));

        mockMvc.perform(get("/admin/seasons/" + season.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/season-form"))
                .andExpect(model().attribute("season", hasProperty("name", is("Edit Test"))));
    }

    @Test
    void shouldShowSeasonDetail() throws Exception {
        var season = seasonRepository.save(new Season("Detail Test"));

        mockMvc.perform(get("/admin/seasons/" + season.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/season-detail"))
                .andExpect(model().attributeExists("season"));
    }

    @Test
    void shouldDeleteSeason() throws Exception {
        var season = seasonRepository.save(new Season("Delete Test"));

        mockMvc.perform(post("/admin/seasons/" + season.getId() + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/seasons"));

        assertFalse(seasonRepository.findById(season.getId()).isPresent());
    }

}
