package org.ctc.admin.controller;

import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.RaceService;
import org.ctc.domain.service.SeasonCarPoolService;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CtcDevSpringBootContext
@AutoConfigureMockMvc
@Tag("integration")
class CategoryCarPresentationIT {
    static final String LABEL = "Gr.3 MR + Gr.3 RR";
    @Autowired MockMvc mvc;
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired CarRepository cars;
    @Autowired TrackRepository tracks;
    @Autowired SeasonCarPoolService pools;
    @Autowired RaceService races;
    TestHelper.SeasonFixture fixture;
    UUID mr;
    UUID rr;
    UUID track;
    UUID entry;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            fixture = helper.createFullSeasonFixture("TCP" + suffix);
            mr = car(suffix, "MR");
            rr = car(suffix, "RR");
            track = tracks.saveAndFlush(new Track("Test presentation " + suffix)).getId();
            fixture.season().getTracks().add(tracks.findById(track).orElseThrow());
        });
    }

    @AfterEach
    void cleanUp() {
        if (fixture == null) return;
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow());
            seasons.flush();
            cars.deleteAllById(List.of(mr, rr));
            tracks.deleteById(track);
            teams.deleteAll(List.of(fixture.homeTeam(), fixture.awayTeam()));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"detail", "list", "results", "match", "matchday"})
    void givenSavedCategoryCar_whenViewingSetup_thenShowConcreteCarAndConsumedEntry(String view) throws Exception {
        // given
        configure(true, LABEL);
        // when
        var response = mvc.perform(get(url(view))).andExpect(status().isOk()).andReturn();
        // then
        var document = Jsoup.parse(response.getResponse().getContentAsString());
        assertThat(document.text()).contains("Test presentation", "MR", LABEL);
        assertThat(document.select(".pool-entry-label").text()).contains(LABEL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"detail", "list", "results", "match", "matchday"})
    void givenFixedCar_whenViewingSetup_thenKeepConcreteNameWithoutPoolPlaceholder(String view) throws Exception {
        // given
        configure(false, LABEL);
        // when
        var response = mvc.perform(get(url(view))).andExpect(status().isOk()).andReturn();
        // then
        var document = Jsoup.parse(response.getResponse().getContentAsString());
        assertThat(document.text()).contains("Test presentation", "MR").doesNotContain(LABEL);
        assertThat(document.select(".pool-entry-label")).isEmpty();
    }

    @Test
    void givenHtmlSensitiveEntryLabel_whenViewingSetup_thenRenderTextWithoutCreatingElements() throws Exception {
        // given
        String label = "Gr.3 MR + RR <img src=x onerror='window.injected=1'> & \"choice\"";
        configure(true, label);
        for (var view : List.of("detail", "list", "results", "match", "matchday")) {
            // when
            var response = mvc.perform(get(url(view))).andExpect(status().isOk()).andReturn();
            // then
            var document = Jsoup.parse(response.getResponse().getContentAsString());
            assertThat(document.select(".pool-entry-label").text()).contains(label);
            assertThat(document.select("img[src=x], [onerror]")).isEmpty();
        }
    }

    private void configure(boolean category, String label) {
        tx.executeWithoutResult(status -> {
            if (category) {
                pools.changeMode(fixture.season().getId(), CarPoolMode.CATEGORY_POOL);
                entry = pools.saveEntry(fixture.season().getId(), null, label, List.of(
                        new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, Drivetrain.MR, 9890),
                        new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, Drivetrain.RR, 9890)));
                pools.freeze(fixture.season().getId());
            } else {
                seasons.findById(fixture.season().getId()).orElseThrow().getCars().add(cars.findById(mr).orElseThrow());
            }
        });
        assertThat(races.saveRace(fixture.race().getId(), fixture.matchday().getId(), fixture.homeTeam().getId(),
                fixture.awayTeam().getId(), track, mr, null, 5, 2, 2, 1, "Full", 1,
                TimeProgressionMode.MULTIPLIER, 0, "Clear", "Day", "RS, RM", "RM", entry).success()).isTrue();
    }

    private String url(String view) {
        return switch (view) {
            case "detail" -> "/admin/races/" + fixture.race().getId();
            case "list" -> "/admin/races?seasonId=" + fixture.season().getId();
            case "results" -> "/admin/races/" + fixture.race().getId() + "/results";
            case "match" -> "/admin/matches/" + fixture.match().getId();
            case "matchday" -> "/admin/matchdays/" + fixture.matchday().getId();
            default -> throw new IllegalArgumentException(view);
        };
    }

    private UUID car(String suffix, String drive) {
        var car = new Car("Test presentation " + suffix, drive);
        car.setCategory(CarCategory.GR_3);
        car.setDrivetrain(Drivetrain.valueOf(drive));
        car.setModelYear(9890);
        car.setModelYearOverride(true);
        car.setModelYearSource("Test fixture");
        car.setModelYearEvidence("Test accepted local year");
        return cars.saveAndFlush(car).getId();
    }
}
