package org.ctc.admin.controller;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
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

	@Autowired
	private SeasonPhaseRepository seasonPhaseRepository;

	private Car car;

	@BeforeEach
	void setUp() {
		car = carRepository.save(new Car("Mazda", "RX-Vision GT3 Concept"));
	}


	@Test
	void whenGetCars_thenReturnsCarsView() throws Exception {
		mockMvc.perform(get("/admin/cars"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/cars"))
				.andExpect(model().attributeExists("cars"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-list-search")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("management-list.js")));
	}


	@Test
	void whenGetNewCarForm_thenReturnsCarForm() throws Exception {
		mockMvc.perform(get("/admin/cars/new"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/car-form"))
				.andExpect(model().attributeExists("carForm"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("entity-editor-actions")));
	}


	@Test
	void givenExistingCar_whenGetEditForm_thenReturnsCarForm() throws Exception {
		mockMvc.perform(get("/admin/cars/" + car.getId() + "/edit"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/car-form"))
				.andExpect(model().attributeExists("carForm"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("entity-editor-actions")));
	}


	@Test
	void givenValidCarForm_whenSaveNewCar_thenRedirectsAndPersists() throws Exception {
		mockMvc.perform(post("/admin/cars/save")
						.param("manufacturer", "Toyota")
						.param("name", "GR Supra Racing Concept"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/cars"))
				.andExpect(flash().attributeExists("successMessage"));

		assertTrue(carRepository.existsByManufacturerAndName("Toyota", "GR Supra Racing Concept"));
	}

	@Test
	void givenExistingCar_whenSaveUpdatedCar_thenRedirectsAndUpdates() throws Exception {
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
	void givenBlankManufacturer_whenSaveCar_thenReturnsFormWithErrors() throws Exception {
		mockMvc.perform(post("/admin/cars/save")
						.param("manufacturer", "")
						.param("name", "Some Car"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/car-form"));
	}


	@Test
	void givenUnreferencedCar_whenDeleteCar_thenRedirectsAndRemoves() throws Exception {
		mockMvc.perform(post("/admin/cars/" + car.getId() + "/delete"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/cars"))
				.andExpect(flash().attributeExists("successMessage"));

		assertFalse(carRepository.findById(car.getId()).isPresent());
	}

	@Test
	void givenCarReferencedByRace_whenDeleteCar_thenRedirectsWithErrorAndKeepsCar() throws Exception {
		var rs = new RaceScoring("CT RS " + java.util.UUID.randomUUID().toString().substring(0, 4), "20,17", null, 0);
		rs = raceScoringRepository.save(rs);
		var ms = new MatchScoring("CT MS " + java.util.UUID.randomUUID().toString().substring(0, 4), 3, 1, 0);
		ms = matchScoringRepository.save(ms);
		var s = new Season("Car Test Season", 2026, 1);
		var season = seasonRepository.save(s);
		var regular = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
		regular.setRaceScoring(rs);
		regular.setMatchScoring(ms);
		regular = seasonPhaseRepository.save(regular);
		var matchday = matchdayRepository.save(new Matchday(regular, "CT Matchday", 1));
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


	@Test
	void givenImageFile_whenUploadCarImage_thenRedirectsAndSetsImageUrl() throws Exception {
		var imageFile = new org.springframework.mock.web.MockMultipartFile(
				"image", "car.png", "image/png", new byte[]{1, 2, 3});

		mockMvc.perform(multipart("/admin/cars/" + car.getId() + "/image").file(imageFile))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/cars/" + car.getId() + "/edit"))
				.andExpect(flash().attributeExists("successMessage"));

		var updated = carRepository.findById(car.getId()).orElseThrow();
		assertNotNull(updated.getImageUrl());
		assertTrue(updated.getImageUrl().contains("car.png"));
	}

	@Test
	void givenBlankName_whenSaveCar_thenReturnsFormWithErrors() throws Exception {
		mockMvc.perform(post("/admin/cars/save")
						.param("manufacturer", "Toyota")
						.param("name", ""))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/car-form"));
	}

    @Test
    void givenEditedCarWithImage_whenSaveBlankName_thenImageAndFieldErrorsRemainVisible() throws Exception {
        car.setImageUrl("/uploads/test-editor-image.png");
        carRepository.save(car);
        mockMvc.perform(post("/admin/cars/save")
                .param("id", car.getId().toString())
                .param("manufacturer", "Test Manufacturer")
                .param("name", ""))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("car"))
                .andExpect(model().attributeHasFieldErrors("carForm", "name"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("test-editor-image.png")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name-error")));
    }
}
