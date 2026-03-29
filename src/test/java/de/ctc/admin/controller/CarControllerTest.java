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
class CarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private RaceRepository raceRepository;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private MatchdayRepository matchdayRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private RaceScoringRepository raceScoringRepository;

    @Autowired
    private MatchScoringRepository matchScoringRepository;

    private Car car;

    @BeforeEach
    void setUp() {
        car = carRepository.save(new Car("Mazda", "RX-Vision GT3 Concept"));
    }

    // --- GET /admin/cars ---

    @Test
    void shouldListCars() throws Exception {
        mockMvc.perform(get("/admin/cars"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/cars"))
                .andExpect(model().attributeExists("cars"));
    }

    // --- GET /admin/cars/new ---

    @Test
    void shouldShowCreateForm() throws Exception {
        mockMvc.perform(get("/admin/cars/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/car-form"))
                .andExpect(model().attributeExists("carForm"));
    }

    // --- GET /admin/cars/{id}/edit ---

    @Test
    void shouldShowEditForm() throws Exception {
        mockMvc.perform(get("/admin/cars/" + car.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/car-form"))
                .andExpect(model().attributeExists("carForm"));
    }

    // --- POST /admin/cars/save ---

    @Test
    void shouldCreateNewCar() throws Exception {
        mockMvc.perform(post("/admin/cars/save")
                        .param("manufacturer", "Toyota")
                        .param("name", "GR Supra Racing Concept"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/cars"))
                .andExpect(flash().attributeExists("successMessage"));

        assertTrue(carRepository.existsByManufacturerAndName("Toyota", "GR Supra Racing Concept"));
    }

    @Test
    void shouldUpdateExistingCar() throws Exception {
        mockMvc.perform(post("/admin/cars/save")
                        .param("id", car.getId().toString())
                        .param("manufacturer", "Mazda")
                        .param("name", "RX-7 GT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/cars"))
                .andExpect(flash().attributeExists("successMessage"));

        var updated = carRepository.findById(car.getId()).orElseThrow();
        assertEquals("RX-7 GT", updated.getName());
    }

    @Test
    void shouldRejectBlankManufacturer() throws Exception {
        mockMvc.perform(post("/admin/cars/save")
                        .param("manufacturer", "")
                        .param("name", "Some Car"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/car-form"));
    }

    // --- POST /admin/cars/{id}/delete ---

    @Test
    void shouldDeleteCar() throws Exception {
        mockMvc.perform(post("/admin/cars/" + car.getId() + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/cars"))
                .andExpect(flash().attributeExists("successMessage"));

        assertFalse(carRepository.findById(car.getId()).isPresent());
    }

    @Test
    void shouldNotDeleteCarWhenReferencedByRace() throws Exception {
        var rs = new RaceScoring("CT RS " + java.util.UUID.randomUUID().toString().substring(0, 4), "20,17", null, 0);
        rs = raceScoringRepository.save(rs);
        var ms = new MatchScoring("CT MS " + java.util.UUID.randomUUID().toString().substring(0, 4), 3, 1, 0);
        ms = matchScoringRepository.save(ms);
        var s = new Season("Car Test Season");
        s.setRaceScoring(rs);
        s.setMatchScoring(ms);
        var season = seasonRepository.save(s);
        var matchday = matchdayRepository.save(new Matchday(season, "CT Matchday", 1));
        var home = teamRepository.save(new Team("Home Team", "HOM"));
        var away = teamRepository.save(new Team("Away Team", "AWY"));
        var match = matchRepository.save(new Match(matchday, home, away));
        var race = new Race();
        race.setMatchday(matchday);
        race.setMatch(match);
        race.setCar(car);
        raceRepository.save(race);

        mockMvc.perform(post("/admin/cars/" + car.getId() + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/cars"))
                .andExpect(flash().attributeExists("errorMessage"));

        assertTrue(carRepository.findById(car.getId()).isPresent());
    }
}
