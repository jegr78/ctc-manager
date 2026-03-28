package de.ctc.admin.controller;

import de.ctc.domain.model.*;
import de.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class TrackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private RaceRepository raceRepository;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private MatchdayRepository matchdayRepository;

    @Autowired
    private TeamRepository teamRepository;

    private Track track;

    @BeforeEach
    void setUp() {
        track = trackRepository.save(new Track("Tsukuba Circuit", "Japan"));
    }

    // --- GET /admin/tracks ---

    @Test
    void shouldListTracks() throws Exception {
        mockMvc.perform(get("/admin/tracks"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/tracks"))
                .andExpect(model().attributeExists("tracks"));
    }

    // --- GET /admin/tracks/new ---

    @Test
    void shouldShowCreateForm() throws Exception {
        mockMvc.perform(get("/admin/tracks/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/track-form"))
                .andExpect(model().attributeExists("trackForm"));
    }

    // --- GET /admin/tracks/{id}/edit ---

    @Test
    void shouldShowEditForm() throws Exception {
        mockMvc.perform(get("/admin/tracks/" + track.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/track-form"))
                .andExpect(model().attributeExists("trackForm"));
    }

    // --- POST /admin/tracks/save ---

    @Test
    void shouldCreateNewTrack() throws Exception {
        mockMvc.perform(post("/admin/tracks/save")
                        .param("name", "Suzuka Circuit")
                        .param("country", "Japan"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tracks"))
                .andExpect(flash().attributeExists("successMessage"));

        assertTrue(trackRepository.existsByName("Suzuka Circuit"));
    }

    @Test
    void shouldUpdateExistingTrack() throws Exception {
        mockMvc.perform(post("/admin/tracks/save")
                        .param("id", track.getId().toString())
                        .param("name", "Tsukuba Circuit Updated")
                        .param("country", "Japan"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tracks"))
                .andExpect(flash().attributeExists("successMessage"));

        var updated = trackRepository.findById(track.getId()).orElseThrow();
        assertEquals("Tsukuba Circuit Updated", updated.getName());
    }

    @Test
    void shouldRejectBlankName() throws Exception {
        mockMvc.perform(post("/admin/tracks/save")
                        .param("name", "")
                        .param("country", "Japan"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/track-form"));
    }

    // --- POST /admin/tracks/{id}/delete ---

    @Test
    void shouldDeleteTrack() throws Exception {
        mockMvc.perform(post("/admin/tracks/" + track.getId() + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tracks"))
                .andExpect(flash().attributeExists("successMessage"));

        assertFalse(trackRepository.findById(track.getId()).isPresent());
    }

    @Test
    void shouldNotDeleteTrackWhenReferencedByRace() throws Exception {
        var season = seasonRepository.save(new Season("Track Test Season"));
        var matchday = matchdayRepository.save(new Matchday(season, "TT Matchday", 1));
        var home = teamRepository.save(new Team("Home Team", "HOM"));
        var away = teamRepository.save(new Team("Away Team", "AWY"));
        var race = new Race(matchday, home, away);
        race.setTrack(track);
        raceRepository.save(race);

        mockMvc.perform(post("/admin/tracks/" + track.getId() + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tracks"))
                .andExpect(flash().attributeExists("errorMessage"));

        assertTrue(trackRepository.findById(track.getId()).isPresent());
    }
}
