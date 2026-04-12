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

	private Car car;

	@BeforeEach
	void setUp() {
		car = carRepository.save(new Car("Mazda", "RX-Vision GT3 Concept"));
	}

	// --- GET /admin/cars ---

	@Test
	void whenGetCars_thenReturnsCarsView() throws Exception {
		// when
		mockMvc.perform(get("/admin/cars"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/cars"))
				.andExpect(model().attributeExists("cars"));
	}

	// --- GET /admin/cars/new ---

	@Test
	void whenGetNewCarForm_thenReturnsCarForm() throws Exception {
		// when
		mockMvc.perform(get("/admin/cars/new"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/car-form"))
				.andExpect(model().attributeExists("carForm"));
	}

	// --- GET /admin/cars/{id}/edit ---

	@Test
	void givenExistingCar_whenGetEditForm_thenReturnsCarForm() throws Exception {
		// when
		mockMvc.perform(get("/admin/cars/" + car.getId() + "/edit"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/car-form"))
				.andExpect(model().attributeExists("carForm"));
	}

	// --- POST /admin/cars/save ---

	@Test
	void givenValidCarForm_whenSaveNewCar_thenRedirectsAndPersists() throws Exception {
		// when
		mockMvc.perform(post("/admin/cars/save")
						.param("manufacturer", "Toyota")
						.param("name", "GR Supra Racing Concept"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/cars"))
				.andExpect(flash().attributeExists("successMessage"));

		// then
		assertTrue(carRepository.existsByManufacturerAndName("Toyota", "GR Supra Racing Concept"));
	}

	@Test
	void givenExistingCar_whenSaveUpdatedCar_thenRedirectsAndUpdates() throws Exception {
		// when
		mockMvc.perform(post("/admin/cars/save")
						.param("id", car.getId().toString())
						.param("manufacturer", "Mazda")
						.param("name", "RX-7 GT"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/cars"))
				.andExpect(flash().attributeExists("successMessage"));

		// then
		var updated = carRepository.findById(car.getId()).orElseThrow();
		assertEquals("RX-7 GT", updated.getName());
	}

	@Test
	void givenBlankManufacturer_whenSaveCar_thenReturnsFormWithErrors() throws Exception {
		// when
		mockMvc.perform(post("/admin/cars/save")
						.param("manufacturer", "")
						.param("name", "Some Car"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/car-form"));
	}

	// --- POST /admin/cars/{id}/delete ---

	@Test
	void givenUnreferencedCar_whenDeleteCar_thenRedirectsAndRemoves() throws Exception {
		// when
		mockMvc.perform(post("/admin/cars/" + car.getId() + "/delete"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/cars"))
				.andExpect(flash().attributeExists("successMessage"));

		// then
		assertFalse(carRepository.findById(car.getId()).isPresent());
	}

	@Test
	void givenCarReferencedByRace_whenDeleteCar_thenRedirectsWithErrorAndKeepsCar() throws Exception {
		// given
		var rs = new RaceScoring("CT RS " + java.util.UUID.randomUUID().toString().substring(0, 4), "20,17", null, 0);
		rs = raceScoringRepository.save(rs);
		var ms = new MatchScoring("CT MS " + java.util.UUID.randomUUID().toString().substring(0, 4), 3, 1, 0);
		ms = matchScoringRepository.save(ms);
		var s = new Season("Car Test Season", 2026, 1);
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

		// when
		mockMvc.perform(post("/admin/cars/" + car.getId() + "/delete"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/cars"))
				.andExpect(flash().attributeExists("errorMessage"));

		// then
		assertTrue(carRepository.findById(car.getId()).isPresent());
	}

	// --- POST /admin/cars/{id}/image ---

	@Test
	void givenImageFile_whenUploadCarImage_thenRedirectsAndSetsImageUrl() throws Exception {
		// given
		var imageFile = new org.springframework.mock.web.MockMultipartFile(
				"image", "car.png", "image/png", new byte[]{1, 2, 3});

		// when
		mockMvc.perform(multipart("/admin/cars/" + car.getId() + "/image").file(imageFile))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/cars/" + car.getId() + "/edit"))
				.andExpect(flash().attributeExists("successMessage"));

		// then
		var updated = carRepository.findById(car.getId()).orElseThrow();
		assertNotNull(updated.getImageUrl());
		assertTrue(updated.getImageUrl().contains("car.png"));
	}

	@Test
	void givenBlankName_whenSaveCar_thenReturnsFormWithErrors() throws Exception {
		// when
		mockMvc.perform(post("/admin/cars/save")
						.param("manufacturer", "Toyota")
						.param("name", ""))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/car-form"));
	}
}
