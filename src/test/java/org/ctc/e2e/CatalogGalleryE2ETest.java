package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.options.FilePayload;
import java.util.UUID;
import org.ctc.domain.model.Car;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.service.CatalogImageService;
import org.ctc.testsupport.TestImages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class CatalogGalleryE2ETest extends PlaywrightConfig {
    @Autowired CarRepository cars;
    @Autowired CatalogImageService gallery;
    UUID car;

    @BeforeEach
    void setUp() {
        setupPage();
        car = cars.save(new Car("Test Manufacturer", "Test Gallery " + UUID.randomUUID().toString().substring(0, 8))).getId();
    }

    @AfterEach
    void tearDown() {
        teardownPage();
        gallery.deleteAllForCar(car);
        cars.deleteById(car);
    }

    @Test
    void givenCarEditor_whenUploadingReorderingAndDeletingByKeyboard_thenTheGalleryFollowsWithFocusKept() {
        // given
        page.navigate(url("/admin/cars/" + car + "/edit"));
        assertThat(page.locator("#car-images")).containsText("No images yet.");
        // when
        page.locator("#car-gallery-files").setInputFiles(new FilePayload[] {
                new FilePayload("front.png", "image/png", TestImages.png(1920, 1080)),
                new FilePayload("rear.jpg", "image/jpeg", TestImages.jpeg(1280, 720))});
        page.locator("#car-gallery-kind").selectOption("SCENE");
        page.locator("#car-gallery-label").fill("Launch livery");
        page.locator(".gallery-upload button[type=submit]").click();
        // then
        assertThat(page.locator("#car-images [role=status]")).hasText("2 images added.");
        var items = page.locator(".gallery-item");
        assertThat(items).hasCount(2);
        assertThat(items.first()).containsText("1920×1080");
        assertThat(items.first().locator("img")).hasAttribute("alt", java.util.regex.Pattern.compile("scene, Launch livery, image 1$"));
        assertThat(page.locator("#car-gallery-files")).isFocused();
        // when
        var down = items.first().locator("button[id$='-down']");
        String moved = down.getAttribute("id");
        down.focus();
        page.keyboard().press("Enter");
        // then
        assertThat(page.locator("#car-images [role=status]")).hasText("Image moved to position 2.");
        assertThat(items.nth(1).locator("button[id$='-down']")).hasAttribute("id", moved);
        assertThat(page.locator("#" + moved)).isDisabled();
        assertThat(items.nth(1).locator("button[id$='-up']")).isFocused();
        assertThat(items.first()).containsText("1280×720");
        // when
        items.first().locator("button[id$='-delete']").focus();
        page.keyboard().press("Enter");
        // then
        assertThat(page.locator("#car-images [role=status]")).hasText("Image deleted.");
        assertThat(items).hasCount(1);
        assertThat(items.first()).containsText("1920×1080");
        assertThat(items.first().locator("button[id$='-up']")).isDisabled();
    }

    @Test
    void givenNonImageFile_whenUploading_thenShowTheErrorAndKeepTheGalleryEmpty() {
        // given
        page.navigate(url("/admin/cars/" + car + "/edit"));
        // when
        page.locator("#car-gallery-files").setInputFiles(new FilePayload("notes.png", "image/png", "GIF89a....".getBytes()));
        page.locator(".gallery-upload button[type=submit]").click();
        // then
        assertThat(page.locator("#car-images [role=alert]")).hasText("notes.png is not a PNG, JPEG or WebP image");
        assertThat(page.locator(".gallery-item")).hasCount(0);
    }
}
