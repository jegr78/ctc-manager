package org.ctc.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.CarImageKind;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.service.CatalogImageService;
import org.ctc.media.job.MediaJobExecutor;
import org.ctc.media.job.MediaJobKind;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.TestImages;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@CtcDevSpringBootContext
@AutoConfigureMockMvc
@Tag("integration")
class CarCutoutControllerIT {
    @Autowired MockMvc mvc;
    @Autowired CarRepository cars;
    @Autowired CatalogImageService gallery;
    @Autowired MediaJobExecutor jobs;
    UUID car;
    final CountDownLatch release = new CountDownLatch(1);
    final String blocker = "matchday:" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        car = cars.saveAndFlush(new Car("Test", "Test cutout controller " + UUID.randomUUID().toString().substring(0, 8))).getId();
        gallery.uploadCarImages(car, List.of(new MockMultipartFile("files", "s.jpg", "image/jpeg", TestImages.jpeg(320, 180))),
                CarImageKind.STUDIO, null);
    }

    @AfterEach
    void cleanUp() throws Exception {
        release.countDown();
        for (String scope : List.of("car:" + car, blocker)) {
            for (int i = 0; i < 300 && jobs.status(scope).map(s -> s.running()).orElse(false); i++) TimeUnit.MILLISECONDS.sleep(100);
        }
        gallery.deleteAllForCar(car);
        cars.deleteById(car);
    }

    @Test
    void givenCarWithStudio_whenEditing_thenCreateCutoutButtonIsOffered() throws Exception {
        // when
        String html = mvc.perform(get("/admin/cars/" + car + "/edit")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        // then
        var form = Jsoup.parse(html).selectFirst("form[action$='/images/cutout']");
        assertThat(form).as("cutout form").isNotNull();
        assertThat(form.select("button").text()).isEqualTo("Create cutout");
    }

    @Test
    void givenRunningMediaJob_whenPostingCutout_thenBusyErrorShown() throws Exception {
        // given
        jobs.submit(MediaJobKind.TRAILER, blocker, ctx -> release.await(30, TimeUnit.SECONDS));

        // when / then
        mvc.perform(post("/admin/cars/" + car + "/images/cutout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/cars/" + car + "/edit#car-images"))
                .andExpect(flash().attribute("galleryError", "Another media job is running."));
    }

    @Test
    void givenIdleExecutor_whenPostingCutout_thenJobStartedAndStatusShownOnEdit() throws Exception {
        // when
        mvc.perform(post("/admin/cars/" + car + "/images/cutout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("galleryStatus", "Creating the cutout in the background. Reload the page to see it."));

        // then
        String html = mvc.perform(get("/admin/cars/" + car + "/edit")).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(html).select("[data-cutout-status]").text()).startsWith("Cutout:");
    }
}
