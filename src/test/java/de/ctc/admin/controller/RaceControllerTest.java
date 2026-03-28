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
class RaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private MatchdayRepository matchdayRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private RaceRepository raceRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private TrackRepository trackRepository;

    private Season season;
    private Matchday matchday;
    private Team home;
    private Team away;
    private Race race;

    @BeforeEach
    void setUp() {
        season = seasonRepository.save(new Season("Race Test Season"));
        matchday = matchdayRepository.save(new Matchday(season, "RT Matchday", 1));
        home = teamRepository.save(new Team("Home Racing", "HRC"));
        away = teamRepository.save(new Team("Away Racing", "ARC"));
        race = raceRepository.save(new Race(matchday, home, away));
    }

    @Test
    void shouldShowRaceDetail() throws Exception {
        mockMvc.perform(get("/admin/races/" + race.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/race-detail"))
                .andExpect(model().attributeExists("race"));
    }

    // --- POST /admin/races/save ---

    @Test
    void shouldCreateNewRace() throws Exception {
        mockMvc.perform(post("/admin/races/save")
                        .param("matchdayId", matchday.getId().toString())
                        .param("homeTeamId", home.getId().toString())
                        .param("awayTeamId", away.getId().toString())
)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races?matchdayId=" + matchday.getId()))
                .andExpect(flash().attributeExists("successMessage"));
    }

    // --- POST /admin/races/{id}/results ---

    @Test
    void shouldSaveResultsClearAndRepopulate() throws Exception {
        var driver1 = driverRepository.save(new Driver("psn_home1", "HomeDriver1"));
        var driver2 = driverRepository.save(new Driver("psn_away1", "AwayDriver1"));

        mockMvc.perform(post("/admin/races/" + race.getId() + "/results")
                        .param("results[0].driverId", driver1.getId().toString())
                        .param("results[0].position", "1")
                        .param("results[0].qualiPosition", "1")
                        .param("results[0].fastestLap", "true")
                        .param("results[1].driverId", driver2.getId().toString())
                        .param("results[1].position", "2")
                        .param("results[1].qualiPosition", "2")
                        .param("results[1].fastestLap", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races/" + race.getId() + "/results"))
                .andExpect(flash().attributeExists("successMessage"));

        var saved = raceRepository.findById(race.getId()).orElseThrow();
        assertEquals(2, saved.getResults().size());

        // Save again with only one driver to verify clear-and-repopulate
        mockMvc.perform(post("/admin/races/" + race.getId() + "/results")
                        .param("results[0].driverId", driver1.getId().toString())
                        .param("results[0].position", "1")
                        .param("results[0].qualiPosition", "1")
                        .param("results[0].fastestLap", "false"))
                .andExpect(status().is3xxRedirection());

        var updated = raceRepository.findById(race.getId()).orElseThrow();
        assertEquals(1, updated.getResults().size());
    }

    // --- POST /admin/races/{id}/quick-score ---

    @Test
    void quickScoreShouldRedirectToValidReturnUrl() throws Exception {
        mockMvc.perform(post("/admin/races/" + race.getId() + "/quick-score")
                        .param("homeScore", "10")
                        .param("awayScore", "8")
                        .param("returnUrl", "/admin/matchdays/" + matchday.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/matchdays/" + matchday.getId()))
                .andExpect(flash().attributeExists("successMessage"));

        var saved = raceRepository.findById(race.getId()).orElseThrow();
        assertEquals(10, saved.getHomeScore());
        assertEquals(8, saved.getAwayScore());
    }

    @Test
    void quickScoreShouldRejectAbsoluteUrlRedirect() throws Exception {
        mockMvc.perform(post("/admin/races/" + race.getId() + "/quick-score")
                        .param("homeScore", "5")
                        .param("awayScore", "3")
                        .param("returnUrl", "https://evil.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races"));
    }

    @Test
    void quickScoreShouldRejectProtocolRelativeUrlRedirect() throws Exception {
        mockMvc.perform(post("/admin/races/" + race.getId() + "/quick-score")
                        .param("homeScore", "5")
                        .param("awayScore", "3")
                        .param("returnUrl", "//evil.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races"));
    }

    @Test
    void quickScoreShouldFallbackWhenReturnUrlMissing() throws Exception {
        mockMvc.perform(post("/admin/races/" + race.getId() + "/quick-score")
                        .param("homeScore", "7")
                        .param("awayScore", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races"));
    }

    // --- POST /admin/races/{id}/attachments/link ---

    @Test
    void shouldAddValidLink() throws Exception {
        mockMvc.perform(post("/admin/races/" + race.getId() + "/attachments/link")
                        .param("name", "Race Replay")
                        .param("url", "https://youtube.com/watch?v=abc123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races/" + race.getId()))
                .andExpect(flash().attribute("successMessage", "Link added: Race Replay"));
    }

    @Test
    void shouldRejectJavascriptLink() throws Exception {
        mockMvc.perform(post("/admin/races/" + race.getId() + "/attachments/link")
                        .param("name", "XSS Attempt")
                        .param("url", "javascript:alert(1)"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races/" + race.getId()))
                .andExpect(flash().attribute("errorMessage", "Link must start with http:// or https://"));
    }

    @Test
    void shouldRejectDataUriLink() throws Exception {
        mockMvc.perform(post("/admin/races/" + race.getId() + "/attachments/link")
                        .param("name", "Data URI")
                        .param("url", "data:text/html,<script>alert(1)</script>"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races/" + race.getId()))
                .andExpect(flash().attribute("errorMessage", "Link must start with http:// or https://"));
    }

    // --- POST /admin/races/{id}/delete ---

    @Test
    void shouldDeleteRace() throws Exception {
        mockMvc.perform(post("/admin/races/" + race.getId() + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races?matchdayId=" + matchday.getId()))
                .andExpect(flash().attributeExists("successMessage"));

        assertFalse(raceRepository.findById(race.getId()).isPresent());
    }

    // --- GET /admin/races/used-selections ---

    @Test
    void shouldReturnUsedSelectionsAsJson() throws Exception {
        var car = carRepository.save(new Car("Toyota", "GR Supra"));
        season.getCars().add(car);
        seasonRepository.save(season);
        race.setCar(car);
        raceRepository.save(race);

        mockMvc.perform(get("/admin/races/used-selections")
                        .param("seasonId", season.getId().toString())
                        .param("homeTeamId", home.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedCarIds").isArray())
                .andExpect(jsonPath("$.usedCarIds[0]").value(car.getId().toString()))
                .andExpect(jsonPath("$.usedTrackIds").isArray());
    }

    @Test
    void shouldReturnUsedSelectionsExcludingCurrentRace() throws Exception {
        var car = carRepository.save(new Car("Nissan", "GT-R"));
        season.getCars().add(car);
        seasonRepository.save(season);
        race.setCar(car);
        raceRepository.save(race);

        mockMvc.perform(get("/admin/races/used-selections")
                        .param("seasonId", season.getId().toString())
                        .param("homeTeamId", home.getId().toString())
                        .param("excludeRaceId", race.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedCarIds").isEmpty());
    }

    // --- Uniqueness validation ---

    @Test
    void shouldRejectDuplicateCarForSameHomeTeam() throws Exception {
        var car = carRepository.save(new Car("Honda", "NSX"));
        season.getCars().add(car);
        seasonRepository.save(season);
        race.setCar(car);
        raceRepository.save(race);

        // Create a second matchday for the second race
        var matchday2 = matchdayRepository.save(new Matchday(season, "RT Matchday 2", 2));

        mockMvc.perform(post("/admin/races/save")
                        .param("matchdayId", matchday2.getId().toString())
                        .param("homeTeamId", home.getId().toString())
                        .param("awayTeamId", away.getId().toString())
                        .param("carId", car.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage",
                        home.getShortName() + " has already used " + car.getDisplayName() + " this season"));
    }

    // --- Pool validation ---

    @Test
    void shouldRejectCarNotInSeasonPool() throws Exception {
        var car = carRepository.save(new Car("Ferrari", "488"));
        // Intentionally NOT adding car to season pool

        mockMvc.perform(post("/admin/races/save")
                        .param("matchdayId", matchday.getId().toString())
                        .param("homeTeamId", home.getId().toString())
                        .param("awayTeamId", away.getId().toString())
                        .param("carId", car.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "Car is not in this season's pool"));
    }

    @Test
    void shouldRejectTrackNotInSeasonPool() throws Exception {
        var track = trackRepository.save(new Track("Silverstone", "UK"));
        // Intentionally NOT adding track to season pool

        mockMvc.perform(post("/admin/races/save")
                        .param("matchdayId", matchday.getId().toString())
                        .param("homeTeamId", home.getId().toString())
                        .param("awayTeamId", away.getId().toString())
                        .param("trackId", track.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "Track is not in this season's pool"));
    }
}
