package org.ctc.gt7sync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Gt7ScraperServiceTest {

	private Gt7ScraperService scraperService;

	@BeforeEach
	void setUp() {
		scraperService = new Gt7ScraperService();
	}

	@Test
	void whenParseTunersJs_thenReturnsManufacturerMap() throws IOException {
		String tunersJs = loadFixture("gt7/tuners-data.js");
		Map<String, String> map = scraperService.parseTunersJs(tunersJs);

		assertThat(map).containsEntry("tnr13", "Ford");
		assertThat(map).containsEntry("tnr28", "Nissan");
		assertThat(map).containsEntry("tnr3", "Alfa Romeo");
		assertThat(map).containsEntry("tnr43", "Toyota");
		assertThat(map).containsEntry("tnr151", "Roadster Shop");
	}

	@Test
	void givenManufacturerLookup_whenParseCarsJs_thenReturnsCorrectManufacturerAndName() throws IOException {
		String carsJs = loadFixture("gt7/cars-data.js");
		String tunersJs = loadFixture("gt7/tuners-data.js");
		Map<String, String> manufacturerMap = scraperService.parseTunersJs(tunersJs);

		List<Gt7ScraperService.ScrapedCar> cars = scraperService.parseCarsJs(carsJs, manufacturerMap);

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
		String carsJs = loadFixture("gt7/cars-data.js");
		String tunersJs = loadFixture("gt7/tuners-data.js");
		Map<String, String> manufacturerMap = scraperService.parseTunersJs(tunersJs);

		List<Gt7ScraperService.ScrapedCar> cars = scraperService.parseCarsJs(carsJs, manufacturerMap);

		var ford = cars.stream().filter(c -> "car1044".equals(c.gt7Id())).findFirst().orElseThrow();
		assertThat(ford.manufacturer()).isEqualTo("Ford");
		assertThat(ford.name()).isEqualTo("Ford GT LM Race Car Spec II");
	}

	@Test
	void given1932FordRoadster_whenParseCarsJs_thenResolvesManufacturerViaLookup() throws IOException {
		String carsJs = loadFixture("gt7/cars-data.js");
		String tunersJs = loadFixture("gt7/tuners-data.js");
		Map<String, String> manufacturerMap = scraperService.parseTunersJs(tunersJs);

		List<Gt7ScraperService.ScrapedCar> cars = scraperService.parseCarsJs(carsJs, manufacturerMap);

		var hotrod = cars.stream().filter(c -> "car3500".equals(c.gt7Id())).findFirst().orElseThrow();
		assertThat(hotrod.manufacturer()).isEqualTo("Ford");
		assertThat(hotrod.name()).isEqualTo("Ford Roadster");
	}

	@Test
	void givenNoManufacturerMap_whenParseCarsJs_thenFallsBackToNameExtraction() throws IOException {
		String carsJs = loadFixture("gt7/cars-data.js");

		List<Gt7ScraperService.ScrapedCar> cars = scraperService.parseCarsJs(carsJs);

		var nissan = cars.stream().filter(c -> "car102".equals(c.gt7Id())).findFirst().orElseThrow();
		assertThat(nissan.manufacturer()).isEqualTo("Nissan");

		var alfa = cars.stream().filter(c -> "car310".equals(c.gt7Id())).findFirst().orElseThrow();
		assertThat(alfa.manufacturer()).isEqualTo("Alfa Romeo");
	}

	@Test
	void whenExtractManufacturer_thenExtractsFromLongName() {
		assertThat(Gt7ScraperService.extractManufacturer(
				"Nissan Skyline GTS-R (R31) '87", "Skyline GTS-R (R31) '87"))
				.isEqualTo("Nissan");

		assertThat(Gt7ScraperService.extractManufacturer(
				"Alfa Romeo 4C Gr.3", "4C Gr.3"))
				.isEqualTo("Alfa Romeo");
	}

	@Test
	void givenIdenticalLongAndShortName_whenExtractManufacturer_thenReturnsFirstToken() {
		assertThat(Gt7ScraperService.extractManufacturer(
				"Ford GT LM Race Car Spec II", "Ford GT LM Race Car Spec II"))
				.isEqualTo("Ford");
	}

	@Test
	void whenParseTracksJs_thenReturnsCorrectTrackData() throws IOException {
		String tracksJs = loadFixture("gt7/tracks-data.js");
		List<Gt7ScraperService.ScrapedTrack> tracks = scraperService.parseTracksJs(tracksJs);

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
		String carsJs = loadFixture("gt7/cars-data.js");
		List<Gt7ScraperService.ScrapedCar> cars = scraperService.parseCarsJs(carsJs);

		var car = cars.getFirst();
		assertThat(car.imageUrl()).startsWith("https://www.gran-turismo.com/common/dist/gt7/carlist/car_thumbnails/");
		assertThat(car.imageUrl()).endsWith(".png");
	}

	@Test
	void whenExtractScriptSrc_thenReturnsMatchingScriptPath() {
		String html = """
				<html><head>
				<script type="module" crossorigin src="/common/dist/gt7/carlist/assets/index-BxK3q7Zy.js"></script>
				</head></html>
				""";

		String src = scraperService.extractScriptSrc(html, "/common/dist/gt7/carlist/assets/index-");

		assertThat(src).isEqualTo("/common/dist/gt7/carlist/assets/index-BxK3q7Zy.js");
	}

	@Test
	void whenExtractPattern_thenReturnsMatchingChunkFilename() {
		String indexJs = """
				import("./cars.gb-D4fGh2kL.js")
				""";

		String chunk = scraperService.extractPattern(indexJs, "\"\\.\\/?(cars\\.gb-[^\"]+\\.js)\"");

		assertThat(chunk).isEqualTo("cars.gb-D4fGh2kL.js");
	}

    @ParameterizedTest
    @ValueSource(strings = {"\"", "'", "`"})
    void givenQuotedDataImports_whenExtractChunkFilename_thenRecognizesCarsManufacturersAndTracks(String quote) {
        var indexJs = "import(" + quote + "./cars.gb-40_LojsM.js" + quote + ");"
                + "import(" + quote + "./tuners.gb-zrIjzZI5.js" + quote + ");"
                + "import(" + quote + "./tracks.gb-aBC123.js" + quote + ");";
        assertThat(scraperService.extractChunkFilename(indexJs, "cars.gb")).isEqualTo("cars.gb-40_LojsM.js");
        assertThat(scraperService.extractChunkFilename(indexJs, "tuners.gb")).isEqualTo("tuners.gb-zrIjzZI5.js");
        assertThat(scraperService.extractChunkFilename(indexJs, "tracks.gb")).isEqualTo("tracks.gb-aBC123.js");
    }

    @Test
    void givenDifferentLocaleOrUnsafePath_whenExtractChunkFilename_thenRejectsUnrelatedImports() {
        assertThatThrownBy(() -> scraperService.extractChunkFilename("import(`./cars.us-build.js`)", "cars.gb"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> scraperService.extractChunkFilename("import(`./cars.gb-../outside.js`)", "cars.gb"))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"", "'", "`"})
    void givenQuotedDataFields_whenParseCatalogs_thenRetainsManufacturerNamesAndTrackMetadata(String quote) {
        var tuners = "var e={tnr13:{id:" + quote + "tnr13" + quote + ",name:" + quote + "Ford" + quote + "}};";
        var cars = "var e={car1044:{id:" + quote + "car1044" + quote + ",nameLong:" + quote + "Ford GT LM Race Car Spec II" + quote
                + ",nameShort:" + quote + "Ford GT LM Race Car Spec II" + quote + ",manufacturerId:" + quote + "tnr13" + quote + "}};";
        var manufacturerMap = scraperService.parseTunersJs(tuners);
        assertThat(manufacturerMap).containsEntry("tnr13", "Ford");
        assertThat(scraperService.parseCarsJs(cars, manufacturerMap)).singleElement()
                .satisfies(car -> {
                    assertThat(car.manufacturer()).isEqualTo("Ford");
                    assertThat(car.name()).isEqualTo("Ford GT LM Race Car Spec II");
                    assertThat(car.imageUrl()).endsWith("car1044.png");
                });
        var tracks = "var e={a:{baseId:" + quote + "c81494" + quote + ",id:" + quote + "0457d4" + quote
                + ",nameLong:" + quote + "Deep Forest Raceway" + quote + ",countryName:" + quote + "Switzerland" + quote + "}};";
        assertThat(scraperService.parseTracksJs(tracks)).singleElement()
                .satisfies(track -> {
                    assertThat(track.name()).isEqualTo("Deep Forest Raceway");
                    assertThat(track.country()).isEqualTo("Switzerland");
                    assertThat(track.baseId()).isEqualTo("c81494");
                });
    }

    @Test
    void givenBacktickCarNamesWithApostrophes_whenParseCars_thenPreservesFullName() {
        var cars = "var e={car102:{id:`car102`,nameLong:`Nissan Skyline GTS-R (R31) '87`,nameShort:`Skyline GTS-R (R31) '87`,manufacturerId:`tnr28`}};";
        assertThat(scraperService.parseCarsJs(cars, Map.of("tnr28", "Nissan"))).singleElement()
                .satisfies(car -> assertThat(car.name()).isEqualTo("Skyline GTS-R (R31) '87"));
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
