package org.ctc.gt7sync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Gt7ScraperServiceTest {

	private Gt7ScraperService scraperService;

	@BeforeEach
	void setUp() {
		scraperService = new Gt7ScraperService();
	}

	@Test
	void whenParseTunersJs_thenReturnsManufacturerMap() throws IOException {
		// when
		String tunersJs = loadFixture("gt7/tuners-data.js");
		Map<String, String> map = scraperService.parseTunersJs(tunersJs);

		// then
		assertThat(map).containsEntry("tnr13", "Ford");
		assertThat(map).containsEntry("tnr28", "Nissan");
		assertThat(map).containsEntry("tnr3", "Alfa Romeo");
		assertThat(map).containsEntry("tnr43", "Toyota");
		assertThat(map).containsEntry("tnr151", "Roadster Shop");
	}

	@Test
	void givenManufacturerLookup_whenParseCarsJs_thenReturnsCorrectManufacturerAndName() throws IOException {
		// given
		String carsJs = loadFixture("gt7/cars-data.js");
		String tunersJs = loadFixture("gt7/tuners-data.js");
		Map<String, String> manufacturerMap = scraperService.parseTunersJs(tunersJs);

		// when
		List<Gt7ScraperService.ScrapedCar> cars = scraperService.parseCarsJs(carsJs, manufacturerMap);

		// then
		assertThat(cars).hasSize(5);

		var nissan = cars.stream().filter(c -> "car102".equals(c.gt7Id())).findFirst().orElseThrow();
		assertThat(nissan.manufacturer()).isEqualTo("Nissan");
		assertThat(nissan.name()).isEqualTo("Skyline GTS-R (R31) '87");
		assertThat(nissan.imageUrl()).endsWith("car102.png");

		var toyota = cars.stream().filter(c -> "car205".equals(c.gt7Id())).findFirst().orElseThrow();
		assertThat(toyota.manufacturer()).isEqualTo("Toyota");
		assertThat(toyota.name()).isEqualTo("Sports 800 '65");
	}

	@Test
	void givenIdenticalLongAndShortName_whenParseCarsJs_thenResolvesManufacturerViaLookup() throws IOException {
		// given
		String carsJs = loadFixture("gt7/cars-data.js");
		String tunersJs = loadFixture("gt7/tuners-data.js");
		Map<String, String> manufacturerMap = scraperService.parseTunersJs(tunersJs);

		// when
		List<Gt7ScraperService.ScrapedCar> cars = scraperService.parseCarsJs(carsJs, manufacturerMap);

		// then
		// Ford GT LM Race Car Spec II has nameLong == nameShort, but manufacturerId tnr13 → Ford
		var ford = cars.stream().filter(c -> "car1044".equals(c.gt7Id())).findFirst().orElseThrow();
		assertThat(ford.manufacturer()).isEqualTo("Ford");
		assertThat(ford.name()).isEqualTo("Ford GT LM Race Car Spec II");
	}

	@Test
	void given1932FordRoadster_whenParseCarsJs_thenResolvesManufacturerViaLookup() throws IOException {
		// given
		String carsJs = loadFixture("gt7/cars-data.js");
		String tunersJs = loadFixture("gt7/tuners-data.js");
		Map<String, String> manufacturerMap = scraperService.parseTunersJs(tunersJs);

		// when
		List<Gt7ScraperService.ScrapedCar> cars = scraperService.parseCarsJs(carsJs, manufacturerMap);

		// then
		// "1932 Ford Roadster" has nameShort "Ford Roadster", manufacturerId tnr13 → Ford (not "1932")
		var hotrod = cars.stream().filter(c -> "car3500".equals(c.gt7Id())).findFirst().orElseThrow();
		assertThat(hotrod.manufacturer()).isEqualTo("Ford");
		assertThat(hotrod.name()).isEqualTo("Ford Roadster");
	}

	@Test
	void givenNoManufacturerMap_whenParseCarsJs_thenFallsBackToNameExtraction() throws IOException {
		// given
		String carsJs = loadFixture("gt7/cars-data.js");

		// when
		// No manufacturer map — uses name extraction fallback
		List<Gt7ScraperService.ScrapedCar> cars = scraperService.parseCarsJs(carsJs);

		// then
		var nissan = cars.stream().filter(c -> "car102".equals(c.gt7Id())).findFirst().orElseThrow();
		assertThat(nissan.manufacturer()).isEqualTo("Nissan");

		var alfa = cars.stream().filter(c -> "car310".equals(c.gt7Id())).findFirst().orElseThrow();
		assertThat(alfa.manufacturer()).isEqualTo("Alfa Romeo");
	}

	@Test
	void whenExtractManufacturer_thenExtractsFromLongName() {
		// when / then
		assertThat(Gt7ScraperService.extractManufacturer(
				"Nissan Skyline GTS-R (R31) '87", "Skyline GTS-R (R31) '87"))
				.isEqualTo("Nissan");

		assertThat(Gt7ScraperService.extractManufacturer(
				"Alfa Romeo 4C Gr.3", "4C Gr.3"))
				.isEqualTo("Alfa Romeo");
	}

	@Test
	void givenIdenticalLongAndShortName_whenExtractManufacturer_thenReturnsFirstToken() {
		// when / then
		assertThat(Gt7ScraperService.extractManufacturer(
				"Ford GT LM Race Car Spec II", "Ford GT LM Race Car Spec II"))
				.isEqualTo("Ford");
	}

	@Test
	void whenParseTracksJs_thenReturnsCorrectTrackData() throws IOException {
		// when
		String tracksJs = loadFixture("gt7/tracks-data.js");
		List<Gt7ScraperService.ScrapedTrack> tracks = scraperService.parseTracksJs(tracksJs);

		// then
		assertThat(tracks).hasSize(3);

		var deepForest = tracks.stream().filter(t -> "0457d4".equals(t.id())).findFirst().orElseThrow();
		assertThat(deepForest.name()).isEqualTo("Deep Forest Raceway");
		assertThat(deepForest.country()).isEqualTo("Switzerland");
		assertThat(deepForest.baseId()).isEqualTo("c81494");

		var nurburgring = tracks.stream().filter(t -> "12ceac".equals(t.id())).findFirst().orElseThrow();
		assertThat(nurburgring.name()).isEqualTo("N\u00fcrburgring Nordschleife");
		assertThat(nurburgring.country()).isEqualTo("Germany");
	}

	@Test
	void whenParseCarsJs_thenCarImageUrlIsWellFormed() throws IOException {
		// when
		String carsJs = loadFixture("gt7/cars-data.js");
		List<Gt7ScraperService.ScrapedCar> cars = scraperService.parseCarsJs(carsJs);

		// then
		var car = cars.getFirst();
		assertThat(car.imageUrl()).startsWith("https://www.gran-turismo.com/common/dist/gt7/carlist/car_thumbnails/");
		assertThat(car.imageUrl()).endsWith(".png");
	}

	@Test
	void whenExtractScriptSrc_thenReturnsMatchingScriptPath() {
		// given
		String html = """
				<html><head>
				<script type="module" crossorigin src="/common/dist/gt7/carlist/assets/index-BxK3q7Zy.js"></script>
				</head></html>
				""";

		// when
		String src = scraperService.extractScriptSrc(html, "/common/dist/gt7/carlist/assets/index-");

		// then
		assertThat(src).isEqualTo("/common/dist/gt7/carlist/assets/index-BxK3q7Zy.js");
	}

	@Test
	void whenExtractPattern_thenReturnsMatchingChunkFilename() {
		// given
		String indexJs = """
				import("./cars.gb-D4fGh2kL.js")
				""";

		// when
		String chunk = scraperService.extractPattern(indexJs, "\"\\.\\/?(cars\\.gb-[^\"]+\\.js)\"");

		// then
		assertThat(chunk).isEqualTo("cars.gb-D4fGh2kL.js");
	}

	private String loadFixture(String path) throws IOException {
		try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
			if (is == null) {
				throw new IOException("Fixture not found: " + path);
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
