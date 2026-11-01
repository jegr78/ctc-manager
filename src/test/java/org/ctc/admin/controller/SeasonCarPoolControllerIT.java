package org.ctc.admin.controller;

import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.SeasonCarPoolService;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@CtcDevSpringBootContext
@AutoConfigureMockMvc
@Transactional
@Tag("integration")
class SeasonCarPoolControllerIT {
    @Autowired MockMvc mvc;
    @Autowired TestHelper helper;
    @Autowired SeasonCarPoolService pools;
    @Autowired SeasonCarPoolEntryRepository entries;
    @Autowired CarRepository cars;

    @Test
    void givenNativeSubmission_whenAddingClauseThenSaving_thenDefinitionAndPreviewRemainInWorkspace() throws Exception {
        var season = helper.createSeason("Test Native Pool " + UUID.randomUUID());
        pools.changeMode(season.getId(), CarPoolMode.CATEGORY_POOL);
        mvc.perform(post(base(season) + "/entries/save").param("label", "Combined")
                .param("clauses[0].category", "GR_3").param("clauses[0].drivetrain", "MR").param("action", "add-clause"))
                .andExpect(status().isOk()).andExpect(view().name("admin/season-form"))
                .andExpect(content().string(containsString("clauses[1].category")))
                .andExpect(content().string(containsString("Combined")));
        mvc.perform(post(base(season) + "/entries/save").param("label", "Combined")
                .param("clauses[0].category", "GR_3").param("clauses[0].drivetrain", "MR")
                .param("clauses[1].category", "GR_3").param("clauses[1].drivetrain", "RR").param("action", "save"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/seasons/" + season.getId() + "/edit#carPool"));
        assertThat(season.getCarPoolEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.getLabel()).isEqualTo("Combined");
            assertThat(entry.getClauses()).extracting(SeasonCarPoolClause::getDrivetrain).containsExactly(Drivetrain.MR, Drivetrain.RR);
        });
    }

    @Test
    void givenBindingError_whenSubmitting_thenSubmittedStateAndCatalogOptionsSurvive() throws Exception {
        var season = helper.createSeason("Test Invalid Pool " + UUID.randomUUID());
        pools.changeMode(season.getId(), CarPoolMode.CATEGORY_POOL);
        mvc.perform(post(base(season) + "/entries/save").param("label", "Keep submitted label")
                .param("clauses[0].category", "GR_2").param("clauses[0].drivetrain", "FR").param("clauses[0].modelYear", "invalid"))
                .andExpect(status().isOk()).andExpect(view().name("admin/season-form"))
                .andExpect(model().attributeHasFieldErrors("poolEntryForm", "clauses[0].modelYear"))
                .andExpect(model().attributeExists("allCars", "allTracks", "poolPreview", "poolCategories", "poolDrivetrains"))
                .andExpect(content().string(containsString("Keep submitted label")))
                .andExpect(content().string(containsString("value=\"invalid\"")));
        assertThat(season.getCarPoolEntries()).isEmpty();
    }

    @Test
    void givenFrozenPool_whenForgingSaveOrRemove_thenExistingDefinitionAndSnapshotRemain() throws Exception {
        var season = helper.createSeason("Test Forged Pool " + UUID.randomUUID());
        pools.changeMode(season.getId(), CarPoolMode.CATEGORY_POOL);
        var car = new Car("Test Native Pool " + UUID.randomUUID(), "Member");
        car.setCategory(CarCategory.GR_X);
        car.setModelYear(9876);
        cars.saveAndFlush(car);
        var id = pools.saveEntry(season.getId(), null, "Frozen label", List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_X, null, 9876)));
        pools.freeze(season.getId());
        mvc.perform(post(base(season) + "/entries/save").param("entryId", id.toString()).param("label", "Forged")
                .param("clauses[0].category", "GR_4"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("poolEntryForm"))
                .andExpect(content().string(containsString("frozen")));
        mvc.perform(post(base(season) + "/entries/" + id + "/remove"))
                .andExpect(status().isOk()).andExpect(model().attribute("errorMessage", containsString("frozen")));
        assertThat(entries.findById(id).orElseThrow().getLabel()).isEqualTo("Frozen label");
        assertThat(pools.preview(season.getId()).entries().getFirst().carIds()).containsExactly(car.getId());
    }

    @Test
    void givenOversizedClauseIndex_whenSubmitted_thenReturnValidationErrorBeforeBinding() throws Exception {
        var season = helper.createSeason("Test Pool Limit " + UUID.randomUUID());
        mvc.perform(post(base(season) + "/entries/save").param("label", "Attack")
                .param("clauses[9999].category", "GR_3"))
                .andExpect(status().isBadRequest()).andExpect(view().name("admin/error"))
                .andExpect(model().attribute("message", containsString("32 clauses")));
        assertThat(season.getCarPoolEntries()).isEmpty();
    }

    @Test
    void givenInvalidYearOnSecondClause_whenFirstClauseRemoved_thenRejectedValueIsReindexedAndRetained() throws Exception {
        var season = helper.createSeason("Test Pool Reindex " + UUID.randomUUID());
        pools.changeMode(season.getId(), CarPoolMode.CATEGORY_POOL);
        mvc.perform(post(base(season) + "/entries/save").param("label", "Keep")
                .param("clauses[0].category", "GR_2").param("clauses[1].category", "GR_3")
                .param("clauses[1].modelYear", "invalid").param("action", "remove-clause:0"))
                .andExpect(status().isOk()).andExpect(view().name("admin/season-form"))
                .andExpect(model().attributeHasFieldErrors("poolEntryForm", "clauses[0].modelYear"))
                .andExpect(content().string(containsString("value=\"invalid\"")));
    }

    @Test
    void givenModeOrFreezeError_whenSubmitting_thenWorkspaceExplainsTheRejectedAction() throws Exception {
        var fixture = helper.createFullSeasonFixture("ModeGuard " + UUID.randomUUID().toString().substring(0, 8));
        var car = cars.saveAndFlush(new Car("Test Fixed", "Selected " + UUID.randomUUID()));
        fixture.race().setCar(car);
        mvc.perform(post(base(fixture.season()) + "/mode").param("carPoolMode", "CATEGORY_POOL"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("poolModeForm"))
                .andExpect(content().string(containsString("saved car selection")));
        mvc.perform(post(base(fixture.season()) + "/freeze"))
                .andExpect(status().isOk()).andExpect(model().attribute("errorMessage", containsString("category")));
        assertThat(fixture.season().getCarPoolMode()).isEqualTo(CarPoolMode.FIXED_CARS);
    }

    private static String base(Season season) { return "/admin/seasons/" + season.getId() + "/car-pool"; }
}
