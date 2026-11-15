package org.ctc.gt7sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.CarImage;
import org.ctc.domain.model.CarImageKind;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.service.CatalogImageService;
import org.ctc.domain.service.FileStorageService;
import org.ctc.gt7sync.Gt7ScraperService.CarShot;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.TestImages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@CtcDevSpringBootContext
@AutoConfigureMockMvc
@Tag("integration")
class Gt7CarShotLoadIT {
    @Autowired MockMvc mvc;
    @Autowired CarRepository cars;
    @Autowired CatalogImageService gallery;
    @MockitoBean Gt7ScraperService scraper;
    @MockitoSpyBean FileStorageService files;
    @Value("${app.upload-dir}") String uploadDir;
    UUID car;

    @BeforeEach
    void setUp() throws Exception {
        var entity = new Car("Test", "GT7 shot car " + UUID.randomUUID().toString().substring(0, 8));
        entity.setGt7Id("car365");
        car = cars.saveAndFlush(entity).getId();
        when(scraper.scrapeCarShots("car365")).thenReturn(List.of(new CarShot("2_01", "https://www.gran-turismo.com/s.jpg")));
    }

    @AfterEach
    void cleanUp() {
        gallery.deleteAllForCar(car);
        cars.deleteById(car);
    }

    @Test
    void givenChangedShotOnASecondRequest_whenLoadingAgain_thenStoreTheNewFileAndDeleteTheOldOne() throws Exception {
        // given
        doReturn(TestImages.jpeg(1920, 1080)).when(files).downloadImage(eq("https://www.gran-turismo.com/s.jpg"), eq("gt7.jpg"));
        gallery.uploadCarImages(car, List.of(new org.springframework.mock.web.MockMultipartFile("files", "own.png", "image/png", TestImages.png(10, 10))),
                CarImageKind.OTHER, null);
        mvc.perform(post("/admin/cars/{id}/images/gt7", car))
                .andExpect(flash().attribute("galleryStatus", "GT7 images: added 2_01; replaced none; unchanged none."));
        String first = gt7Path();
        mvc.perform(post("/admin/cars/{id}/images/gt7", car))
                .andExpect(flash().attribute("galleryStatus", "GT7 images: added none; replaced none; unchanged 2_01."));
        doReturn(TestImages.jpeg(1920, 1081)).when(files).downloadImage(eq("https://www.gran-turismo.com/s.jpg"), eq("gt7.jpg"));
        // when
        mvc.perform(post("/admin/cars/{id}/images/gt7", car))
                .andExpect(flash().attribute("galleryStatus", "GT7 images: added none; replaced 2_01; unchanged none."));
        // then
        String second = gt7Path();
        assertThat(second).as("the database points to the new file").isNotEqualTo(first);
        assertThat(file(second)).exists();
        assertThat(file(first)).as("the replaced file is removed after commit").doesNotExist();
        assertThat(gallery.carImages(car)).extracting(CarImage::getHeight).containsExactly(10, 1081);
    }

    private String gt7Path() {
        return gallery.carImages(car).stream().filter(image -> "2_01".equals(image.getSourceKey())).findFirst().orElseThrow().getImagePath();
    }

    private Path file(String url) {
        return Path.of(uploadDir).resolve(url.substring("/uploads/".length()));
    }
}
