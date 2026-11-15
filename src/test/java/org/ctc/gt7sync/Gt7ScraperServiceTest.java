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
		scraperService = new Gt7ScraperService(org.ctc.TestHelper.enabledOutboundPolicy());
	}

    @Test
    void givenMismatchedOrNonCanonicalIds_whenParsing_thenRecordsCannotBeJoinedToOtherCars() {
        // given / when / then
        for (String record : java.util.List.of(
                "car7:{id:`car8`,nameLong:`Test Car`,nameShort:`Car`}",
                "car0:{id:`car0`,nameLong:`Test Car`,nameShort:`Car`}",
                "car07:{id:`car07`,nameLong:`Test Car`,nameShort:`Car`}",
                "car9999999999:{id:`car9999999999`,nameLong:`Test Car`,nameShort:`Car`}")) {
            assertThat(scraperService.parseCarsJs(record)).isEmpty();
        }
    }

    @Test
    void givenOfficialMetadata_whenParsing_thenClassificationUsesFieldsInsteadOfNames() throws IOException {
        // given
        String js = loadFixture("gt7/official-metadata.js");
        // when
        var cars = scraperService.parseCarsJs(js);
        // then
        var amg = cars.stream().filter(car -> car.gt7Id().equals("car3531")).findFirst().orElseThrow();
        assertThat(amg.category()).isEqualTo(org.ctc.domain.model.CarCategory.GR_3);
        assertThat(amg.drivetrain()).isEqualTo(org.ctc.domain.model.Drivetrain.FR);
        var unknown = scraperService.parseCarsJs("car7:{id:`car7`,nameLong:`Test Gr.3 Road Car`,nameShort:`Gr.3 Road Car`,carClass:`---`,driveTrain:`RR`}").getFirst();
        assertThat(unknown.category()).isNull();
        assertThat(unknown.drivetrain()).isEqualTo(org.ctc.domain.model.Drivetrain.RR);
        assertThat(org.ctc.domain.model.Drivetrain.fromSource("AWD")).isEqualTo(org.ctc.domain.model.Drivetrain.FOUR_WD);
    }

    @Test
    void givenEscapedApostrophe_whenParsing_thenNameRemainsIntact() {
        // given
        String js = "car7:{id:'car7',nameLong:'Test Driver\\'s Car',nameShort:'Driver\\'s Car'}";
        // when / then
        assertThat(scraperService.parseCarsJs(js).getFirst().name()).isEqualTo("Driver's Car");
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

    @Test
    void givenProductionCarListBundle_whenSelectingCarShots_thenKeepOnly2kModulesOfThatCarBySlot() throws IOException {
        // given
        String indexJs = loadFixture("gt7/carlist-index-car-shots.js");
        // when
        var modules = scraperService.carShotModules(indexJs, "365");
        // then
        assertThat(modules).as("4K entries and car3656 are ignored").containsExactly(
                Map.entry("1_01", "car365_1_01-3T8vbnCx.js"), Map.entry("1_02", "car365_1_02-BbK93QWo.js"),
                Map.entry("2_01", "car365_2_01-Cks__gEF.js"), Map.entry("2_02", "car365_2_02-BfkyQpB5.js"),
                Map.entry("3_01", "car365_3_01-CiZNavAe.js"));
        assertThat(scraperService.carShotModules(indexJs, "3656")).containsOnlyKeys("1_01", "1_02", "2_01", "2_02", "3_01").containsEntry("1_01", "car3656_1_01-DTZP2K4l.js");
        assertThat(scraperService.carShotModules(indexJs, "999")).isEmpty();
        String fourKOnly = "\"../../generated-assets/images/car_shots/4k/car77_1_01.jpg\":()=>H(()=>import(`./car77_1_01-Ab.js`),[])";
        assertThat(scraperService.carShotModules(fourKOnly, "77")).as("a slot without a 2K entry is not loaded").isEmpty();
    }

    @Test
    void givenProductionShotModule_whenReadingIt_thenReturnTheHashedImageUrl() throws IOException {
        // given
        String module = loadFixture("gt7/carlist-car-shot-module.js");
        // when / then
        assertThat(scraperService.carShotImageUrl(module, "365", "2_01"))
                .isEqualTo("https://www.gran-turismo.com/common/dist/gt7/carlist/assets/car365_2_01-CbsitXT5.jpg");
        assertThat(scraperService.carShotImageUrl(module, "365", "1_01")).as("the module must name the requested slot").isNull();
        assertThat(scraperService.carShotImageUrl("var e=`https://evil.example/car365_2_01-x.jpg`;", "365", "2_01")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "36a", "365.*", "../1"})
    void givenNonNumericGt7Id_whenSelectingCarShots_thenRejectIt(String gt7Id) {
        // when / then
        assertThatThrownBy(() -> scraperService.carShotModules("", gt7Id)).isInstanceOf(IllegalArgumentException.class);
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
