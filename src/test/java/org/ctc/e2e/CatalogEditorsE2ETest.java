package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.TrackRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class CatalogEditorsE2ETest extends PlaywrightConfig {
    @Autowired CarRepository carRepository;
    @Autowired TrackRepository trackRepository;
    private final List<Car> cars = new ArrayList<>();
    private final List<Track> tracks = new ArrayList<>();

    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() {
        teardownPage();
        carRepository.deleteAll(cars);
        trackRepository.deleteAll(tracks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cars", "tracks"})
    void givenCatalogAcrossPages_whenFilterSortEditAndSave_thenResultsAndValidationStayConsistent(String catalog) {
        var prefix = "Test Catalog " + UUID.randomUUID().toString().substring(0, 8);
        for (int index = 0; index < 25; index++) {
            var name = prefix + " " + String.format("%02d", index);
            if (catalog.equals("cars")) cars.add(carRepository.save(new Car("Test Manufacturer", name)));
            else tracks.add(trackRepository.save(new Track(name, "Test Country")));
        }
        page.navigate(url("/admin/" + catalog));
        var search = page.locator("[data-list-search]");
        search.fill("  " + prefix.toUpperCase() + "  ");
        assertThat(page.locator("[data-list-entry]:visible")).hasCount(20);
        assertThat(page.locator("[data-list-count]")).hasText("25 " + catalog + ". Showing 1 to 20.");
        page.locator("[data-list-next]").click();
        assertThat(page.locator("[data-list-entry]:visible")).hasCount(5);
        var column = catalog.equals("cars") ? "2" : "1";
        var header = page.locator("th[data-col='" + column + "']");
        header.locator("button").focus();
        page.keyboard().press("Enter");
        page.keyboard().press("Enter");
        assertThat(header).hasAttribute("aria-sort", "descending");
        assertThat(page.locator("[data-list-page-info]")).hasText("Page 1 of 2");
        assertThat(page.locator("[data-list-entry]:visible").first()).containsText(prefix + " 24");
        search.fill(prefix + " missing");
        assertThat(page.locator("[data-list-empty]")).isVisible();
        page.locator("[data-list-reset]").click();
        assertThat(search).isFocused();
        search.fill(prefix);
        assertThat(page.locator("[data-list-entry]:visible").first()).containsText(prefix + " 24");
        page.locator("[data-list-entry]:visible").first().locator("a.detail-link").click();
        var editorUrl = page.url();
        assertThat(page.locator("label[for='" + (catalog.equals("cars") ? "carImage" : "trackImage") + "']")).hasText("Image file");
        page.locator(".entity-editor-form").evaluate("form => form.noValidate = true");
        page.locator("#name").fill("");
        page.locator(".entity-editor-actions button").click();
        assertThat(page.locator("#name-error")).isVisible();
        assertThat(page.locator("#name")).hasAttribute("aria-invalid", "true");
        page.locator("#name").fill(prefix + " Updated");
        page.locator(".entity-editor-actions button").click();
        assertThat(page).hasURL(url("/admin/" + catalog));
        page.navigate(editorUrl);
        assertThat(page.locator("#name")).hasValue(prefix + " Updated");
    }
}
