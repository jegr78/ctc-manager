package org.ctc.gt7sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class Gt7ScraperServiceTest {

    private Gt7ScraperService scraperService;

    @BeforeEach
    void setUp() {
        scraperService = new Gt7ScraperService();
    }

    @Test
    void shouldParseTunersData() throws IOException {
        String tunersJs = loadFixture("gt7/tuners-data.js");
        Map<String, String> map = scraperService.parseTunersJs(tunersJs);

        assertThat(map).containsEntry("tnr13", "Ford");
        assertThat(map).containsEntry("tnr28", "Nissan");
        assertThat(map).containsEntry("tnr3", "Alfa Romeo");
        assertThat(map).containsEntry("tnr43", "Toyota");
        assertThat(map).containsEntry("tnr151", "Roadster Shop");
    }

    @Test
    void shouldParseCarDataWithManufacturerLookup() throws IOException {
        String carsJs = loadFixture("gt7/cars-data.js");
        String tunersJs = loadFixture("gt7/tuners-data.js");
        Map<String, String> manufacturerMap = scraperService.parseTunersJs(tunersJs);
        List<Gt7ScraperService.ScrapedCar> cars = scraperService.parseCarsJs(carsJs, manufacturerMap);

        assertThat(cars).hasSize(5);

        var nissan = cars.stream().filter(c -> c.gt7Id().equals("car102")).findFirst().orElseThrow();
        assertThat(nissan.manufacturer()).isEqualTo("Nissan");
        assertThat(nissan.name()).isEqualTo("Skyline GTS-R (R31) '87");
        assertThat(nissan.imageUrl()).endsWith("car102.png");

        var toyota = cars.stream().filter(c -> c.gt7Id().equals("car205")).findFirst().orElseThrow();
        assertThat(toyota.manufacturer()).isEqualTo("Toyota");
        assertThat(toyota.name()).isEqualTo("Sports 800 '65");
    }

    @Test
    void shouldResolveManufacturerViaLookupForIdenticalNames() throws IOException {
        String carsJs = loadFixture("gt7/cars-data.js");
        String tunersJs = loadFixture("gt7/tuners-data.js");
        Map<String, String> manufacturerMap = scraperService.parseTunersJs(tunersJs);
        List<Gt7ScraperService.ScrapedCar> cars = scraperService.parseCarsJs(carsJs, manufacturerMap);

        // Ford GT LM Race Car Spec II has nameLong == nameShort, but manufacturerId tnr13 → Ford
        var ford = cars.stream().filter(c -> c.gt7Id().equals("car1044")).findFirst().orElseThrow();
        assertThat(ford.manufacturer()).isEqualTo("Ford");
        assertThat(ford.name()).isEqualTo("Ford GT LM Race Car Spec II");
    }

    @Test
    void shouldResolveManufacturerViaLookupFor1932FordRoadster() throws IOException {
        String carsJs = loadFixture("gt7/cars-data.js");
        String tunersJs = loadFixture("gt7/tuners-data.js");
        Map<String, String> manufacturerMap = scraperService.parseTunersJs(tunersJs);
        List<Gt7ScraperService.ScrapedCar> cars = scraperService.parseCarsJs(carsJs, manufacturerMap);

        // "1932 Ford Roadster" has nameShort "Ford Roadster", manufacturerId tnr13 → Ford (not "1932")
        var hotrod = cars.stream().filter(c -> c.gt7Id().equals("car3500")).findFirst().orElseThrow();
        assertThat(hotrod.manufacturer()).isEqualTo("Ford");
        assertThat(hotrod.name()).isEqualTo("Ford Roadster");
    }

    @Test
    void shouldFallbackToNameExtractionWithoutLookup() throws IOException {
        String carsJs = loadFixture("gt7/cars-data.js");
        // No manufacturer map — uses name extraction fallback
        List<Gt7ScraperService.ScrapedCar> cars = scraperService.parseCarsJs(carsJs);

        var nissan = cars.stream().filter(c -> c.gt7Id().equals("car102")).findFirst().orElseThrow();
        assertThat(nissan.manufacturer()).isEqualTo("Nissan");

        var alfa = cars.stream().filter(c -> c.gt7Id().equals("car310")).findFirst().orElseThrow();
        assertThat(alfa.manufacturer()).isEqualTo("Alfa Romeo");
    }

    @Test
    void shouldExtractManufacturerFromLongName() {
        assertThat(Gt7ScraperService.extractManufacturer(
                "Nissan Skyline GTS-R (R31) '87", "Skyline GTS-R (R31) '87"))
                .isEqualTo("Nissan");

        assertThat(Gt7ScraperService.extractManufacturer(
                "Alfa Romeo 4C Gr.3", "4C Gr.3"))
                .isEqualTo("Alfa Romeo");
    }

    @Test
    void shouldHandleIdenticalLongAndShortName() {
        assertThat(Gt7ScraperService.extractManufacturer(
                "Ford GT LM Race Car Spec II", "Ford GT LM Race Car Spec II"))
                .isEqualTo("Ford");
    }

    @Test
    void shouldParseTrackData() throws IOException {
        String tracksJs = loadFixture("gt7/tracks-data.js");
        List<Gt7ScraperService.ScrapedTrack> tracks = scraperService.parseTracksJs(tracksJs);

        assertThat(tracks).hasSize(3);

        var deepForest = tracks.stream().filter(t -> t.id().equals("0457d4")).findFirst().orElseThrow();
        assertThat(deepForest.name()).isEqualTo("Deep Forest Raceway");
        assertThat(deepForest.country()).isEqualTo("Switzerland");
        assertThat(deepForest.baseId()).isEqualTo("c81494");

        var nurburgring = tracks.stream().filter(t -> t.id().equals("12ceac")).findFirst().orElseThrow();
        assertThat(nurburgring.name()).isEqualTo("N\u00fcrburgring Nordschleife");
        assertThat(nurburgring.country()).isEqualTo("Germany");
    }

    @Test
    void shouldBuildCarImageUrl() throws IOException {
        String carsJs = loadFixture("gt7/cars-data.js");
        List<Gt7ScraperService.ScrapedCar> cars = scraperService.parseCarsJs(carsJs);

        var car = cars.getFirst();
        assertThat(car.imageUrl()).startsWith("https://www.gran-turismo.com/common/dist/gt7/carlist/car_thumbnails/");
        assertThat(car.imageUrl()).endsWith(".png");
    }

    @Test
    void shouldExtractScriptSrc() {
        String html = """
                <html><head>
                <script type="module" crossorigin src="/common/dist/gt7/carlist/assets/index-BxK3q7Zy.js"></script>
                </head></html>
                """;
        String src = scraperService.extractScriptSrc(html, "/common/dist/gt7/carlist/assets/index-");
        assertThat(src).isEqualTo("/common/dist/gt7/carlist/assets/index-BxK3q7Zy.js");
    }

    @Test
    void shouldExtractPattern() {
        String indexJs = """
                import("./cars.gb-D4fGh2kL.js")
                """;
        String chunk = scraperService.extractPattern(indexJs, "\"\\.\\/?(cars\\.gb-[^\"]+\\.js)\"");
        assertThat(chunk).isEqualTo("cars.gb-D4fGh2kL.js");
    }

    private String loadFixture(String path) throws IOException {
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new IOException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
